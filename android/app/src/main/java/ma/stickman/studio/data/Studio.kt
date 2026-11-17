package ma.stickman.studio.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Everything the screens do, in one place: start cloud steps, read their results, move files. */
class Studio(private val ctx: Context, val prefs: Prefs) {
    val gh = GitHub({ prefs.githubToken }, { prefs.settings.value })
    val local = LocalStore(ctx)

    private val s get() = prefs.settings.value
    private fun commonInputs() = mapOf("model" to s.model, "budget" to "%.2f".format(java.util.Locale.US, s.budget))

    // ---------- the three cloud steps ----------
    suspend fun startTopics(extra: String) {
        val id = gh.dispatch("brain.yml", commonInputs() + mapOf("step" to "topics", "extra" to extra))
            ?: throw ApiError(0, "لم يبدأ التشغيل على GitHub")
        prefs.setJob(Job(Stage.TOPICS_RUNNING, id, "brain.yml", startedAt = System.currentTimeMillis()))
    }

    suspend fun startScript(t: Topic, voice: String, extra: String) {
        val id = gh.dispatch("brain.yml", commonInputs() + mapOf("step" to "script", "n" to t.n.toString(), "voice" to voice, "extra" to extra))
            ?: throw ApiError(0, "لم يبدأ التشغيل على GitHub")
        prefs.setJob(Job(Stage.SCRIPT_RUNNING, id, "brain.yml", t.slug, t.title, System.currentTimeMillis()))
        local.currentAxis = t.axis
    }

    /** 1.3.6: a video from a topic the user wrote in the chat: the script step without the list of ten.
     *  [topic] = {"title", "hook", "angle", "sensitivity", "wish"}; brain.yml reads it (n = custom:<slug>). */
    suspend fun startCustom(slug: String, topic: org.json.JSONObject, voice: String) {
        prefs.journeys.reset()   // its own journey: the last list of topics is not part of this video
        val id = gh.dispatch("brain.yml", commonInputs() + mapOf("step" to "script", "n" to "custom:$slug", "voice" to voice, "extra" to topic.toString()))
            ?: throw ApiError(0, "لم يبدأ التشغيل على GitHub")
        prefs.setJob(Job(Stage.SCRIPT_RUNNING, id, "brain.yml", slug, topic.optString("title"), System.currentTimeMillis()))
        local.currentAxis = topic.optString("axis", "history")
    }

    /** Series (1.1.2): the script of the next part, built from the series.json of the part just made. */
    suspend fun startNextPart(prev: String, series: SeriesInfo, voice: String) {
        val id = gh.dispatch("brain.yml", commonInputs() + mapOf("step" to "script", "n" to "next:$prev", "voice" to voice, "extra" to ""))
            ?: throw ApiError(0, "لم يبدأ التشغيل على GitHub")
        prefs.setJob(Job(Stage.SCRIPT_RUNNING, id, "brain.yml", series.nextSlug, series.nextTitle, System.currentTimeMillis()))
    }

    private fun produceInputs(slug: String, extra: String, mode: String, prepRun: Long = 0) = mapOf(
        "slug" to slug, "extra" to extra, "model" to s.model, "mode" to mode,
        "budget" to "%.2f".format(java.util.Locale.US, (s.budget * 0.8f).coerceAtLeast(0.5f)),
        "opts" to s.videoOpts,
    ) + (if (prepRun > 0) mapOf("prep_run" to prepRun.toString()) else emptyMap())

    /** Sends the voice and the sheets in one commit, then starts the production (or only the preview). */
    suspend fun uploadAndProduce(slug: String, title: String, files: List<Uri>, extra: String, previewFirst: Boolean,
                                 progress: (Int, Int) -> Unit) {
        prefs.setJob(prefs.job.value.copy(stage = Stage.UPLOADING, slug = slug, title = title, error = ""))
        val stamp = System.currentTimeMillis()
        val payload = files.mapIndexed { i, uri -> val (bytes, ext) = read(uri); "${stamp}_${i + 1}.$ext" to bytes }
        // 1.1.2: a draft release instead of a commit, so the voice and the sheets never enter the git history.
        // If the release cannot be written (an old token without that right), the commit still works as before.
        try { gh.putInbox(slug, payload, progress) }
        catch (e: ApiError) {
            if (e.code != 403 && e.code != 404) throw e
            gh.putFiles(payload.map { (n, b) -> "inbox/$slug/$n" to b }, "phone: ${files.size} inputs for $slug", progress)
        }
        startProduce(slug, title, extra, if (previewFirst) "prepare" else "full")
    }

    /** full / prepare (preview first) / revise (edit after delivery, same voice). */
    suspend fun startProduce(slug: String, title: String, extra: String, mode: String) {
        val id = gh.dispatch("produce.yml", produceInputs(slug, extra, mode)) ?: throw ApiError(0, "لم يبدأ التشغيل على GitHub")
        prefs.setJob(Job(if (mode == "prepare") Stage.PREVIEWING else Stage.PRODUCING, id, "produce.yml", slug, title, System.currentTimeMillis()))
    }

    /** The user approved the preview: render it from that preparation (no AI step, no new upload). */
    suspend fun startRender(slug: String, title: String, prepRun: Long) {
        val id = gh.dispatch("produce.yml", produceInputs(slug, "", "render", prepRun)) ?: throw ApiError(0, "لم يبدأ التشغيل على GitHub")
        prefs.setJob(Job(Stage.PRODUCING, id, "produce.yml", slug, title, System.currentTimeMillis()))
    }

    // ---------- night production (1.1.3) ----------
    /** The night settings and what the last night made (cloud/state/night.json). */
    suspend fun night(): Pair<ma.stickman.studio.work.NightPlan, ma.stickman.studio.work.NightLast?> {
        val o = gh.text("cloud/state/night.json")?.let { runCatching { org.json.JSONObject(it) }.getOrNull() }
        return ma.stickman.studio.work.NightPlan.from(o) to ma.stickman.studio.work.NightLast.from(o?.optJSONObject("last"))
    }

    /** Saves the settings for the cloud, and moves the start time of night.yml so the videos are ready on time. */
    suspend fun saveNight(plan: ma.stickman.studio.work.NightPlan) {
        val cur = gh.text("cloud/state/night.json")?.let { runCatching { org.json.JSONObject(it) }.getOrNull() }
        gh.putFile("cloud/state/night.json", plan.json(cur?.optJSONObject("last")).toString(1).toByteArray(), "phone: night settings")
        val wf = gh.text(".github/workflows/night.yml") ?: throw ApiError(404, "night.yml غير موجود على GitHub")
        val next = wf.replace(Regex("""- cron: "[^"]*"(\s+# night-start)"""), "- cron: \"${plan.cron()}\"$1")
        if (next != wf) gh.putFile(".github/workflows/night.yml", next.toByteArray(), "phone: night start ${plan.cron()} UTC")
    }

    /** A test night now (1 video), to see the whole chain once. */
    suspend fun nightNow(count: Int) = gh.dispatch("night.yml", mapOf("count" to count.toString()))

    // ---------- preview (mode prepare) ----------
    fun previewDir(slug: String) = File(ctx.cacheDir, "preview/$slug")

    suspend fun downloadPreview(runId: Long, slug: String): Boolean {
        previewDir(slug).deleteRecursively()
        return gh.downloadArtifact(runId, "preview-$slug", previewDir(slug))
    }

    fun localPreview(slug: String): PreviewInfo? {
        val d = previewDir(slug)
        val sheet = File(d, "_preview_sheet.jpg").takeIf { it.exists() }
        val checks = d.listFiles { f -> f.name.startsWith("_sheet") && f.name.endsWith("_check.png") }?.sortedBy { it.name } ?: emptyList()
        if (sheet == null && checks.isEmpty()) return null
        val prod = runCatching { org.json.JSONObject(File(d, "produce.json").readText()) }.getOrNull()
        val idx = runCatching { org.json.JSONObject(File(d, "_preview_index.json").readText()) }.getOrNull()?.let {
            SheetIndex(it.optInt("cols", 6), it.optInt("w", 180), it.optInt("h", 320), it.optInt("gap", 6), it.optInt("n"))
        }
        return PreviewInfo(sheet, checks, prod?.optString("notes").orEmpty(), prod?.optString("checks").orEmpty(), idx)
    }

    // ---------- checking the files before sending ----------
    data class FileCheck(val audio: Boolean, val seconds: Int?, val width: Int?, val height: Int?) {
        /** A phone screenshot is much taller than a ChatGPT sheet (2:3). */
        val looksLikeScreenshot get() = width != null && height != null && height > width * 1.85
        val looksLikeSheet get() = width != null && height != null && !looksLikeScreenshot && width >= 700
    }

    fun inspect(uri: Uri): FileCheck {
        val (_, mime) = describe(uri)
        val audio = mime.startsWith("audio") || mime == "video/webm"
        if (audio) {
            val secs = runCatching {
                android.media.MediaMetadataRetriever().run {
                    try { setDataSource(ctx, uri); extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()?.div(1000)?.toInt() }
                    finally { release() }
                }
            }.getOrNull()
            return FileCheck(true, secs, null, null)
        }
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, o) } }
        return FileCheck(false, null, o.outWidth.takeIf { it > 0 }, o.outHeight.takeIf { it > 0 })
    }

    suspend fun cleanup(slug: String) { gh.dispatch("cleanup.yml", mapOf("slug" to slug)) }

    /** The phone is where the TikTok numbers arrive: it sends all of them to cloud/state/performance.json for the coach.
     *  1.3.4: moved here from the screen so the screenshot reader (work/ShotWatch.kt) can send them in the background. */
    suspend fun pushPerformance() {
        val all = org.json.JSONObject()
        local.videos.value.filter { it.hasStats }.forEach { v ->
            all.put(v.slug, org.json.JSONObject().put("title", v.title).put("axis", v.axis).put("date", v.date)
                .put("duration_s", v.durationS).put("views", v.views).put("likes", v.likes).put("comments", v.comments)
                .put("shares", v.shares).put("saves", v.saves).put("avg_watch_s", v.avgWatchS ?: org.json.JSONObject.NULL)
                .put("completion_pct", v.completionPct ?: org.json.JSONObject.NULL)
                .put("drop_s", v.dropS ?: org.json.JSONObject.NULL).put("fyp_pct", v.fypPct ?: org.json.JSONObject.NULL)
                .put("published_at", v.publishedAt).put("stats_at", v.statsAt))
        }
        gh.putFile("cloud/state/performance.json", all.toString(2).toByteArray(), "coach: TikTok numbers from the phone")
    }

    // ---------- script, live status, network ----------
    /** script.json of the project (scenes, words, shots), kept in the cache once read. */
    suspend fun scriptInfo(slug: String): ScriptInfo? {
        val f = File(ctx.cacheDir, "script/$slug.json")
        val text = runCatching { gh.text("projects/$slug/script.json") }.getOrNull()?.also { f.parentFile?.mkdirs(); f.writeText(it) }
            ?: f.takeIf { it.exists() }?.readText() ?: return null
        return runCatching { Parse.script(text) }.getOrNull()
    }

    /** 1.3.0: tell the running agent (it reads control.json before every turn): switch to [model] and / or [pause].
     *  [onEmpty] = what to do when today's Coarena money runs out. Old runs are dropped (the 10 newest kept). */
    suspend fun control(runId: Long?, model: String? = null, pause: Boolean? = null, onEmpty: String? = null) {
        repeat(3) { attempt ->
            try {
                val (text, sha) = gh.branchFile("control.json", "status")
                val doc = text?.let { runCatching { org.json.JSONObject(it) }.getOrNull() } ?: org.json.JSONObject()
                onEmpty?.let { doc.put("on_empty", it) }
                if (runId != null && runId > 0) {
                    val runs = doc.optJSONObject("runs") ?: org.json.JSONObject()
                    val r = runs.optJSONObject(runId.toString()) ?: org.json.JSONObject()
                    model?.let { r.put("model", it) }
                    pause?.let { r.put("pause", it) }
                    r.put("at", System.currentTimeMillis())
                    runs.put(runId.toString(), r)
                    val keep = runs.keys().asSequence().toList().sortedByDescending { runs.optJSONObject(it)?.optLong("at") ?: 0 }.take(10)
                    doc.put("runs", org.json.JSONObject().also { n -> keep.forEach { k -> n.put(k, runs.get(k)) } })
                }
                gh.putBranchFile("control.json", "status", doc.toString(), sha, "control ${runId ?: ""}")
                return
            } catch (e: ApiError) {
                if (attempt == 2 || (e.code != 409 && e.code != 422)) throw e   // someone moved the file: read it again
            }
        }
    }

    /** What the cloud agent is doing right now (branch `status`), only when it belongs to [runId]. */
    suspend fun live(runId: Long): LiveStatus? = runCatching { gh.text("live.json", "status")?.let { Parse.live(it) } }
        .getOrNull()?.takeIf { it.runId == runId }

    fun online(): Boolean {
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    // ---------- cover from the video ----------
    /** [n] frames spread over the video, small, to choose the cover. */
    suspend fun frames(f: File, n: Int = 8): List<Pair<Long, android.graphics.Bitmap>> = withContext(Dispatchers.IO) {
        val r = android.media.MediaMetadataRetriever()
        try {
            r.setDataSource(f.path)
            val dur = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: return@withContext emptyList()
            (1..n).mapNotNull { i ->
                val us = dur * 1000 * i / (n + 1)
                val b = if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(us, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 360, 640)
                        else r.getFrameAtTime(us)
                b?.let { us to it }
            }
        } catch (e: Exception) { emptyList() } finally { r.release() }
    }

    suspend fun saveCover(f: File, us: Long): Boolean = withContext(Dispatchers.IO) {
        val r = android.media.MediaMetadataRetriever()
        val bmp = try { r.setDataSource(f.path); r.getFrameAtTime(us, android.media.MediaMetadataRetriever.OPTION_CLOSEST) } catch (e: Exception) { null } finally { r.release() }
            ?: return@withContext false
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, f.nameWithoutExtension + "_cover.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ستوديو المحتوى")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
            ctx.contentResolver.openOutputStream(uri)!!.use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }
            true
        }.getOrDefault(false)
    }

    // ---------- app update ----------
    fun updateFile() = File(ctx.cacheDir, "update/StickmanStudio.apk")

    // ---------- results ----------
    suspend fun topics(): TopicsFile? = gh.text("cloud/state/topics.json")?.let { Parse.topics(it) }
    suspend fun handoff(): Handoff? = gh.text("cloud/state/current.json")?.let { Parse.handoff(it) }
    suspend fun log(): List<LogEntry> = gh.text("videos/LOG.md")?.let { Parse.log(it) } ?: emptyList()

    fun videoDir(slug: String) = File(ctx.filesDir, "videos/$slug")
    fun localVideo(slug: String): File? = File(videoDir(slug), "$slug.mp4").takeIf { it.exists() && it.length() > 0 }
    fun localResult(slug: String): VideoResult? =
        File(videoDir(slug), "result.json").takeIf { it.exists() }?.let { runCatching { Parse.result(it.readText()) }.getOrNull() }

    /** Downloads the finished video of a produce run (mp4, thumbnail, result.json). */
    suspend fun download(runId: Long, slug: String, progress: (Float) -> Unit, onVideoStart: (File) -> Unit = {}): Boolean {
        // an edit after delivery: keep the version before it for the player's before/after
        localVideo(slug)?.let { old -> File(videoDir(slug), "$slug.prev.mp4").also { it.delete(); old.renameTo(it) } }
        val ok = runCatching { gh.downloadArtifact(runId, "video-$slug", videoDir(slug), progress, onVideoStart) }
            .getOrElse { e -> restorePrev(slug); throw e }
        if (!ok) restorePrev(slug)
        // Storage: once the video is in the phone's gallery, the copy on GitHub is not needed (the night
        // janitor removes the others after 3 days).
        if (ok && s.autoSaveGallery) localVideo(slug)?.let { if (saveToGallery(it)) gh.deleteArtifact(runId, "video-$slug") }
        return ok
    }

    private fun restorePrev(slug: String) {
        val prev = File(videoDir(slug), "$slug.prev.mp4")
        if (prev.exists() && localVideo(slug) == null) prev.renameTo(File(videoDir(slug), "$slug.mp4"))
    }

    fun thumb(slug: String): File? = File(videoDir(slug), "$slug.jpg").takeIf { it.exists() }

    fun localVideos(): List<File> = File(ctx.filesDir, "videos").listFiles()?.mapNotNull { d -> File(d, "${d.name}.mp4").takeIf { it.exists() } }
        ?.sortedByDescending { it.lastModified() } ?: emptyList()

    suspend fun saveToGallery(f: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, f.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ستوديو المحتوى")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val col = if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                      else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val uri = ctx.contentResolver.insert(col, values) ?: return@runCatching false
            ctx.contentResolver.openOutputStream(uri)!!.use { out -> f.inputStream().use { it.copyTo(out) } }
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0)
                ctx.contentResolver.update(uri, values, null, null)
            }
            true
        }.getOrDefault(false)
    }

    // ---------- files from the phone ----------
    /** Copies a shared/picked file into the app (share permissions expire when the app is closed). */
    suspend fun import(uri: Uri): Uri = withContext(Dispatchers.IO) {
        if (uri.scheme == "file") return@withContext uri
        val (name, mime) = describe(uri)
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: name.substringAfterLast('.', "bin")
        val base = name.substringBeforeLast('.').replace(Regex("[^\\p{L}\\p{N}_ -]"), "_").take(60).ifBlank { "file" }
        val dir = File(ctx.cacheDir, "inbox").apply { mkdirs() }
        val out = File(dir, "${System.currentTimeMillis()}_$base.$ext")
        ctx.contentResolver.openInputStream(uri)!!.use { i -> out.outputStream().use { i.copyTo(it) } }
        Uri.fromFile(out)
    }

    fun clearImported() { File(ctx.cacheDir, "inbox").deleteRecursively() }

    fun describe(uri: Uri): Pair<String, String> {
        if (uri.scheme == "file") {
            val f = File(uri.path!!)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: ""
            return f.name.substringAfter('_') to mime
        }
        var name = uri.lastPathSegment ?: "file"
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) name = it.getString(0) ?: name
            }
        }
        return name to (ctx.contentResolver.getType(uri) ?: "")
    }

    private suspend fun read(uri: Uri): Pair<ByteArray, String> = withContext(Dispatchers.IO) {
        val (name, mime) = describe(uri)
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?: name.substringAfterLast('.', "").lowercase().ifBlank { if (mime.startsWith("audio")) "wav" else "png" }
        val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        if (bytes.size > 45 * 1024 * 1024) throw ApiError(413, "الملف «$name» أكبر من 45 ميغابايت")
        bytes to (if (ext == "mpeg") "mp3" else ext)
    }
}
