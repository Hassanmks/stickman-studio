package ma.stickman.studio.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

class ApiError(val code: Int, message: String) : IOException(message)

object Net {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    val JSON = "application/json; charset=utf-8".toMediaType()

    fun Response.bodyText(): String = body?.string() ?: ""

    fun check(r: Response): String {
        val text = r.bodyText()
        if (!r.isSuccessful) {
            val msg = runCatching { JSONObject(text).optString("message") }.getOrNull().orEmpty().ifBlank { text.take(300) }
            throw ApiError(r.code, msg)
        }
        return text
    }
}

/** GitHub REST calls the app needs: workflows, runs, artifacts, files and the OpenRouter secret. */
class GitHub(private val token: () -> String, private val settings: () -> Settings) {
    private val base get() = "https://api.github.com/repos/${settings().owner}/${settings().repo}"

    private fun req(url: String, raw: Boolean = false, tok: String = token()) = Request.Builder().url(url)
        .header("Authorization", "Bearer $tok")
        .header("Accept", if (raw) "application/vnd.github.raw+json" else "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("Cache-Control", "no-cache")

    private suspend fun get(url: String, raw: Boolean = false): String = withContext(Dispatchers.IO) {
        Net.client.newCall(req(url, raw).build()).execute().use { Net.check(it) }
    }

    private suspend fun send(method: String, url: String, body: JSONObject?): Pair<Int, String> = withContext(Dispatchers.IO) {
        val rb = (body?.toString() ?: "").toRequestBody(Net.JSON)
        Net.client.newCall(req(url).method(method, if (method == "GET") null else rb).build()).execute().use {
            val text = with(Net) { it.bodyText() }
            if (!it.isSuccessful) {
                val msg = runCatching { JSONObject(text).optString("message") }.getOrNull().orEmpty().ifBlank { text.take(300) }
                throw ApiError(it.code, msg)
            }
            it.code to text
        }
    }

    /** Returns the login; checks that the token can push to the repository. */
    suspend fun verify(candidate: String): String = withContext(Dispatchers.IO) {
        val login = Net.client.newCall(req("https://api.github.com/user", tok = candidate).build()).execute().use {
            JSONObject(Net.check(it)).getString("login")
        }
        val perms = Net.client.newCall(req(base, tok = candidate).build()).execute().use {
            JSONObject(Net.check(it)).optJSONObject("permissions")
        }
        if (perms?.optBoolean("push") != true) throw ApiError(403, "الرمز لا يملك صلاحية الكتابة على المستودع")
        login
    }

    suspend fun dispatch(workflow: String, inputs: Map<String, String>): Long? {
        val since = Instant.now().minusSeconds(15)
        val body = JSONObject().put("ref", "main").put("inputs", JSONObject(inputs)).put("return_run_details", true)
        val (code, text) = send("POST", "$base/actions/workflows/$workflow/dispatches", body)
        if (code == 200) runCatching { JSONObject(text).optLong("workflow_run_id") }.getOrNull()?.takeIf { it > 0 }?.let { return it }
        // Older API: no run id in the answer, so find the run that just appeared.
        repeat(12) {
            kotlinx.coroutines.delay(2500)
            findRun(workflow, since)?.let { return it }
        }
        return null
    }

    private suspend fun findRun(workflow: String, since: Instant): Long? {
        val arr = JSONObject(get("$base/actions/workflows/$workflow/runs?event=workflow_dispatch&per_page=5"))
            .getJSONArray("workflow_runs")
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            if (Instant.parse(r.getString("created_at")).isAfter(since)) return r.getLong("id")
        }
        return null
    }

    suspend fun run(id: Long): RunInfo {
        val r = JSONObject(get("$base/actions/runs/$id"))
        var workStart: String? = null
        val steps = runCatching {
            val jobs = JSONObject(get("$base/actions/runs/$id/jobs?per_page=100")).getJSONArray("jobs")
            workStart = (0 until jobs.length()).map { jobs.getJSONObject(it) }
                .filter { it.optString("status") != "queued" && it.optString("status") != "waiting" }
                .mapNotNull { it.optString("started_at").takeIf { t -> t.isNotBlank() && t != "null" } }.minOrNull()
            // produce runs several jobs (prepare, 8 render parts, finish). Show the steps of all of them in order;
            // the 8 parallel parts become one line, and a job that has not started yet shows as one waiting line.
            val all = (0 until jobs.length()).map { jobs.getJSONObject(it) }
            fun st(o: JSONObject) = o.optString("status")
            fun cc(o: JSONObject) = o.optString("conclusion").ifBlank { null }?.takeIf { it != "null" }
            val out = mutableListOf<Step>()
            val parts = all.filter { it.optString("name").startsWith("parts") }
            val shown = all.filter { it.optString("name") != "gate" }   // 1.1.4: the morning check job is not the user's business
            val sections = shown.map { it.optString("name").substringBefore(" (") }.distinctBy { if (it.startsWith("parts")) "parts" else it }.size > 1
            shown.forEach { j ->
                val name = j.optString("name")
                if (sections && (!name.startsWith("parts") || j === parts.first())) out += Step("§$name", st(j), cc(j))
                if (name.startsWith("parts")) {
                    if (j !== parts.first()) return@forEach
                    val status = when { parts.all { st(it) == "completed" } -> "completed"
                        parts.any { st(it) == "in_progress" || st(it) == "completed" } -> "in_progress"; else -> "queued" }
                    val conc = when { parts.any { cc(it) == "failure" } -> "failure"
                        status == "completed" -> parts.map { cc(it) }.firstOrNull { it != "success" } ?: "success"; else -> null }
                    val ps = parts.mapNotNull { it.optString("started_at").takeIf { t -> t.isNotBlank() && t != "null" } }.minOrNull().orEmpty()
                    val pe = if (status == "completed") parts.mapNotNull { it.optString("completed_at").takeIf { t -> t.isNotBlank() && t != "null" } }.maxOrNull().orEmpty() else ""
                    out += Step("Render parts", status, conc, ps, pe)
                    return@forEach
                }
                val s = j.optJSONArray("steps") ?: JSONArray()
                if (s.length() == 0) out += Step("job $name", st(j), cc(j))
                else (0 until s.length()).map { s.getJSONObject(it) }
                    .forEach { out += Step(it.optString("name"), it.optString("status"), it.optString("conclusion").ifBlank { null }?.takeIf { c -> c != "null" },
                        it.optString("started_at").takeIf { t -> t != "null" }.orEmpty(), it.optString("completed_at").takeIf { t -> t != "null" }.orEmpty()) }
            }
            // jobs that wait on others are not listed yet: keep their place so the progress bar stays honest
            if (all.any { it.optString("name") == "prepare" }) {
                if (parts.isEmpty()) { out += Step("§parts", "queued", null); out += Step("Render parts", "queued", null) }
                if (all.none { it.optString("name") == "finish" }) { out += Step("§finish", "queued", null); out += Step("job finish", "queued", null) }
            }
            out
        }.getOrDefault(emptyList())
        return RunInfo(r.getLong("id"), r.optString("name"), r.optString("status"),
            r.optString("conclusion").ifBlank { null }?.takeIf { it != "null" }, r.optString("html_url"),
            r.optString("run_started_at").takeIf { it.isNotBlank() && it != "null" } ?: r.optString("created_at"),
            steps, r.optString("display_title"), r.optString("updated_at"), workStart)
    }

    /** Conclusion of every job of a run by name (prepare, finish, "parts (3)"...). */
    suspend fun jobs(id: Long): Map<String, String?> {
        val arr = JSONObject(get("$base/actions/runs/$id/jobs?per_page=100")).getJSONArray("jobs")
        return (0 until arr.length()).map { arr.getJSONObject(it) }.associate {
            it.optString("name") to it.optString("conclusion").ifBlank { null }?.takeIf { c -> c != "null" } }
    }

    /** Resume a failed run where it stopped: GitHub runs only its failed jobs again, with the files the
     *  successful jobs already made (preparation, render parts). */
    suspend fun rerunFailed(id: Long) { send("POST", "$base/actions/runs/$id/rerun-failed-jobs", JSONObject()) }

    suspend fun cancel(id: Long) { runCatching { send("POST", "$base/actions/runs/$id/cancel", null) } }

    suspend fun recentRuns(workflow: String, n: Int = 20): List<RunInfo> {
        val arr = JSONObject(get("$base/actions/workflows/$workflow/runs?per_page=$n")).getJSONArray("workflow_runs")
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            RunInfo(it.getLong("id"), it.optString("name"), it.optString("status"),
                it.optString("conclusion").ifBlank { null }?.takeIf { c -> c != "null" }, it.optString("html_url"),
                it.optString("created_at"), title = it.optString("display_title"), updatedAt = it.optString("updated_at"))
        }
    }

    /** Raw text of a file on a branch (main by default), or null when it does not exist. */
    suspend fun text(path: String, ref: String = "main"): String? = try { get("$base/contents/$path?ref=$ref", raw = true) }
        catch (e: ApiError) { if (e.code == 404) null else throw e }

    /** 1.3.3: the file names in a folder of the repository (empty when it does not exist). */
    suspend fun dir(path: String, ref: String = "main"): List<String> = try {
        val a = org.json.JSONArray(get("$base/contents/$path?ref=$ref"))
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.takeIf { o -> o.optString("type") == "file" }?.optString("name") }
    } catch (e: ApiError) { if (e.code == 404) emptyList() else throw e }

    /** Newest app release on GitHub (the repository is private: the token can read it), or null. */
    suspend fun latestRelease(): Release? = try {
        val o = JSONObject(get("$base/releases/latest"))
        val assets = o.optJSONArray("assets") ?: JSONArray()
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") }
        apk?.let { Release(o.optString("tag_name"), o.optString("tag_name").removePrefix("v"), o.optString("body"), it.getString("url"), it.optLong("size")) }
    } catch (e: ApiError) { if (e.code == 404) null else throw e }

    /** Downloads a release file (the API answers with a redirect to the storage, which OkHttp follows). */
    suspend fun downloadAsset(url: String, out: File, progress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        Net.client.newCall(req(url).header("Accept", "application/octet-stream").build()).execute().use { r ->
            if (!r.isSuccessful) throw ApiError(r.code, "تعذّر تنزيل التحديث")
            val total = (r.body?.contentLength() ?: 0L).coerceAtLeast(1)
            var read = 0L
            r.body!!.byteStream().use { input -> out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                while (true) { val n = input.read(buf); if (n < 0) break; o.write(buf, 0, n); read += n; progress((read.toFloat() / total).coerceAtMost(1f)) }
            } }
        }
    }

    /** 1.3.0: a small text file on another branch (the phone's control.json on `status`); returns (text, sha). */
    suspend fun branchFile(path: String, branch: String): Pair<String?, String?> = try {
        val o = JSONObject(get("$base/contents/$path?ref=$branch"))
        String(Base64.decode(o.optString("content").replace("\n", ""), Base64.DEFAULT)) to o.optString("sha")
    } catch (e: ApiError) { if (e.code == 404) null to null else throw e }

    suspend fun putBranchFile(path: String, branch: String, text: String, sha: String?, message: String) {
        val body = JSONObject().put("message", message).put("branch", branch)
            .put("content", Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP))
        if (!sha.isNullOrBlank()) body.put("sha", sha)
        send("PUT", "$base/contents/$path", body)
    }

    /** 1.3.0: re-run a whole run (when GitHub refuses to re-run only its cancelled jobs). */
    suspend fun rerunAll(id: Long) { send("POST", "$base/actions/runs/$id/rerun", JSONObject()) }

    suspend fun putFile(path: String, bytes: ByteArray, message: String) {
        val body = JSONObject().put("message", message).put("branch", "main")
            .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
        try { send("PUT", "$base/contents/$path", body) } catch (e: ApiError) {
            if (e.code != 422) throw e
            val sha = JSONObject(get("$base/contents/$path?ref=main")).getString("sha")
            send("PUT", "$base/contents/$path", body.put("sha", sha))
        }
    }

    /** Puts several files on main in ONE commit (Git Data API): one blob per file, then tree, commit and ref.
     *  Each request is retried, so a short network drop does not lose an upload of several megabytes. */
    suspend fun putFiles(files: List<Pair<String, ByteArray>>, message: String, progress: (Int, Int) -> Unit = { _, _ -> }) {
        suspend fun <T> retry(block: suspend () -> T): T {
            var wait = 1500L
            repeat(4) { i ->
                try { return block() } catch (e: IOException) {
                    if (e is ApiError && e.code in 400..499 && e.code != 409 && e.code != 429) throw e
                    if (i == 3) throw e
                    kotlinx.coroutines.delay(wait); wait *= 2
                }
            }
            error("unreachable")
        }
        val blobs = files.mapIndexed { i, (path, bytes) ->
            progress(i, files.size)
            val sha = retry {
                JSONObject(send("POST", "$base/git/blobs", JSONObject()
                    .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP)).put("encoding", "base64")).second).getString("sha")
            }
            path to sha
        }
        progress(files.size, files.size)
        repeat(3) { attempt ->       // the ref may move (a cloud commit) between reading it and updating it
            val head = retry { JSONObject(get("$base/git/ref/heads/main")).getJSONObject("object").getString("sha") }
            val baseTree = retry { JSONObject(get("$base/git/commits/$head")).getJSONObject("tree").getString("sha") }
            val tree = JSONArray()
            blobs.forEach { (p, sha) -> tree.put(JSONObject().put("path", p).put("mode", "100644").put("type", "blob").put("sha", sha)) }
            val treeSha = retry { JSONObject(send("POST", "$base/git/trees", JSONObject().put("base_tree", baseTree).put("tree", tree)).second).getString("sha") }
            val commit = retry { JSONObject(send("POST", "$base/git/commits", JSONObject().put("message", message)
                .put("tree", treeSha).put("parents", JSONArray().put(head))).second).getString("sha") }
            try { send("PATCH", "$base/git/refs/heads/main", JSONObject().put("sha", commit).put("force", false)); return }
            catch (e: ApiError) { if (e.code != 422 || attempt == 2) throw e }
        }
    }

    /** The phone's files go to the DRAFT release `inbox-<slug>` (1.1.2): private, outside the git history, deleted
     *  by the cloud once the video is made (cloud/media.py). Earlier files of the same project are replaced, so the
     *  release always holds exactly this upload. Each file is retried on a network drop. */
    suspend fun putInbox(slug: String, files: List<Pair<String, ByteArray>>, progress: (Int, Int) -> Unit = { _, _ -> }) {
        val tag = "inbox-$slug"
        val list = JSONArray(get("$base/releases?per_page=100"))
        var rel = (0 until list.length()).map { list.getJSONObject(it) }.firstOrNull { it.optString("tag_name") == tag && it.optBoolean("draft") }
        if (rel == null) rel = JSONObject(send("POST", "$base/releases", JSONObject().put("tag_name", tag).put("name", "inbox $slug")
            .put("draft", true).put("body", "Temporary storage of the studio (not a release).")).second)
        val old = rel!!.optJSONArray("assets") ?: JSONArray()
        for (i in 0 until old.length()) runCatching { send("DELETE", "$base/releases/assets/${old.getJSONObject(i).getLong("id")}", null) }
        val up = rel.getString("upload_url").substringBefore("{")
        files.forEachIndexed { i, (name, bytes) ->
            progress(i, files.size)
            val type = when (name.substringAfterLast('.').lowercase()) {
                "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "webp" -> "image/webp"; else -> "application/octet-stream" }
            var wait = 1500L
            for (attempt in 0 until 4) {
                try {
                    withContext(Dispatchers.IO) {
                        Net.client.newCall(req("$up?name=${java.net.URLEncoder.encode(name, "UTF-8")}")
                            .post(bytes.toRequestBody(type.toMediaType())).build()).execute().use { Net.check(it) }
                    }
                    break
                } catch (e: IOException) {
                    if ((e is ApiError && e.code in 400..499 && e.code != 429) || attempt == 3) throw e
                    kotlinx.coroutines.delay(wait); wait *= 2
                }
            }
        }
        progress(files.size, files.size)
    }

    /** Downloads the artifact `name` of a run and unzips it into [dir]. Returns false when it is missing. */
    suspend fun downloadArtifact(runId: Long, name: String, dir: File, progress: (Float) -> Unit = {},
                                 onVideoStart: (File) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        val arr = JSONObject(get("$base/actions/runs/$runId/artifacts")).getJSONArray("artifacts")
        val a = (0 until arr.length()).map { arr.getJSONObject(it) }.firstOrNull { it.getString("name") == name && !it.optBoolean("expired") }
            ?: return@withContext false
        val total = a.optLong("size_in_bytes", 0).coerceAtLeast(1)
        dir.mkdirs()
        Net.client.newCall(req(a.getString("archive_download_url")).build()).execute().use { r ->
            if (!r.isSuccessful) throw ApiError(r.code, "تعذّر تنزيل الفيديو")
            var read = 0L
            val counting = object : java.io.FilterInputStream(r.body!!.byteStream()) {
                override fun read(b: ByteArray, off: Int, len: Int): Int =
                    super.read(b, off, len).also { if (it > 0) { read += it; progress((read.toFloat() / total).coerceAtMost(1f)) } }
            }
            ZipInputStream(counting).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    if (!e.isDirectory) {
                        val out = File(dir, e.name.substringAfterLast('/'))
                        val video = out.name.endsWith(".mp4") && !out.name.contains(".hook")   // 1.3.8: the alternative hook is not the main video
                        if (video) { out.delete(); ma.stickman.studio.data.Growing.state[out.path] = false }
                        try {
                            out.outputStream().use { o ->
                                if (!video) zip.copyTo(o)
                                else {   // the player can start once the first megabyte (the mp4 index) is written
                                    val buf = ByteArray(64 * 1024); var n: Int; var told = false; var written = 0L
                                    while (zip.read(buf).also { n = it } > 0) {
                                        o.write(buf, 0, n); written += n
                                        if (!told && written > 1_000_000) { o.flush(); told = true; onVideoStart(out) }
                                    }
                                    if (!told) onVideoStart(out)
                                }
                            }
                        } finally { if (video) ma.stickman.studio.data.Growing.state[out.path] = true }
                    }
                    e = zip.nextEntry
                }
            }
        }
        true
    }

    /** Deletes the artifact [name] of a run (storage on GitHub). Returns false when it is not there. */
    suspend fun deleteArtifact(runId: Long, name: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val arr = JSONObject(get("$base/actions/runs/$runId/artifacts")).getJSONArray("artifacts")
            val a = (0 until arr.length()).map { arr.getJSONObject(it) }.firstOrNull { it.getString("name") == name } ?: return@runCatching false
            Net.client.newCall(req("$base/actions/artifacts/${a.getLong("id")}").delete().build()).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /** Stores a repository secret (GitHub requires it sealed with the repository public key). */
    suspend fun setSecret(name: String, value: String) {
        val pk = JSONObject(get("$base/actions/secrets/public-key"))
        val sealed = SealedBox.seal(value.toByteArray(), Base64.decode(pk.getString("key"), Base64.DEFAULT))
        send("PUT", "$base/actions/secrets/$name", JSONObject()
            .put("encrypted_value", Base64.encodeToString(sealed, Base64.NO_WRAP)).put("key_id", pk.getString("key_id")))
    }
}

object Coarena {
    /** 1.2.9: the model ids Coarena has switched on right now (its own list). */
    suspend fun models(key: String): Set<String> = withContext(Dispatchers.IO) {
        val r = Request.Builder().url("https://coarena.ai/api/v1/models").header("Authorization", "Bearer $key").build()
        Net.client.newCall(r).execute().use {
            val a = JSONObject(Net.check(it)).optJSONArray("data") ?: org.json.JSONArray()
            (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.optString("id") }.toSet()
        }
    }

    /** Today's Coarena balance for a key: (used, left) in dollars. Also checks that the key works. */
    suspend fun today(key: String): Pair<Double, Double> = withContext(Dispatchers.IO) {
        val r = Request.Builder().url("https://coarena.ai/api/v1/limits").header("Authorization", "Bearer $key").build()
        Net.client.newCall(r).execute().use {
            if (it.code == 401 || it.code == 403) throw ApiError(it.code, "مفتاح Coarena غير صالح. انسخه من جديد من صفحة Keys.")
            val t = JSONObject(Net.check(it)).getJSONObject("data").getJSONObject("today")
            t.optDouble("used_usd", 0.0) to t.optDouble("left_usd", 0.0)
        }
    }
}

object Gemini {
    /** Checks an AI Studio key by listing its models (no model call, no quota used). */
    suspend fun check(key: String) = withContext(Dispatchers.IO) {
        val r = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1")
            .header("x-goog-api-key", key).build()
        Net.client.newCall(r).execute().use {
            if (it.code == 400 || it.code == 401 || it.code == 403)
                throw ApiError(it.code, "مفتاح Gemini غير صالح. انسخه من جديد من صفحة المفاتيح في AI Studio.")
            Net.check(it)
        }
    }
}

object OpenRouter {
    suspend fun credit(key: String): Credit = withContext(Dispatchers.IO) {
        val r = Request.Builder().url("https://openrouter.ai/api/v1/key").header("Authorization", "Bearer $key").build()
        Net.client.newCall(r).execute().use {
            if (it.code == 401) throw ApiError(401, "مفتاح OpenRouter غير صالح. انسخه من جديد من صفحة المفاتيح.")
            val d = JSONObject(Net.check(it)).getJSONObject("data")
            Credit(d.optString("label"), d.optDouble("usage", 0.0),
                if (d.isNull("limit")) null else d.optDouble("limit"),
                if (d.isNull("limit_remaining")) null else d.optDouble("limit_remaining"),
                d.optBoolean("is_free_tier"))
        }
    }

    /** Models that can use tools and see images (what the cloud agent needs), cheapest first. */
    suspend fun models(): List<ModelInfo> = withContext(Dispatchers.IO) {
        Net.client.newCall(Request.Builder().url("https://openrouter.ai/api/v1/models").build()).execute().use {
            val arr = JSONObject(Net.check(it)).getJSONArray("data")
            (0 until arr.length()).map { i -> arr.getJSONObject(i) }.filter { m ->
                val params = m.optJSONArray("supported_parameters")?.toString() ?: ""
                val mods = m.optJSONObject("architecture")?.optJSONArray("input_modalities")?.toString() ?: ""
                "\"tools\"" in params && "\"image\"" in mods && !m.getString("id").endsWith(":batch")
            }.map { m ->
                val p = m.optJSONObject("pricing") ?: JSONObject()
                val inP = p.optString("prompt", "0").toDoubleOrNull() ?: 0.0
                val outP = p.optString("completion", "0").toDoubleOrNull() ?: 0.0
                ModelInfo(m.getString("id"), m.optString("name"), inP * 1e6, outP * 1e6, inP == 0.0 && outP == 0.0)
            }.filter { it.inPrice >= 0 && it.outPrice >= 0 && !it.id.startsWith("openrouter/") }   // routers have no fixed price
                .sortedBy { it.inPrice }
        }
    }
}
