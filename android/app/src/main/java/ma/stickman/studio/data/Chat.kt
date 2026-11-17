package ma.stickman.studio.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

// ============================================================================================
// 1.2.0: the conversation on the home screen. The assistant answers straight from the phone (Claude through the
// user's Coarena key, streamed word by word); heavy work (topics, script, render) still runs on GitHub, started
// by an action button the assistant proposes. Conversations live in filesDir/chats.json.
// ============================================================================================

/** One message. images = small JPEGs (base64) the user attached; only the newest turns keep them. */
data class ChatMsg(val role: String, val text: String, val images: List<String> = emptyList(), val t: Long = System.currentTimeMillis())

data class Conversation(val id: String, val title: String, val updated: Long, val messages: List<ChatMsg>, val starred: Boolean = false)

/** A button the assistant proposed in its reply (<<action:kind|arg|...>>). */
data class ChatAction(val kind: String, val args: List<String>) {
    val label: String get() = when (kind) {
        "topics" -> "اقترح 10 مواضيع" + (args.firstOrNull()?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
        "resume" -> "تابع آخر سيناريو جاهز"
        "open" -> "افتح الفيديو"
        "perf" -> "أضف أرقام هذا الفيديو"
        "saveperf" -> "احفظ هذه الأرقام"
        "projects" -> "افتح المشاريع"
        "coach" -> "افتح المدرّب"
        "settings" -> "افتح الإعدادات"
        // 1.3.6
        "video" -> "ابدأ السيناريو: " + args.firstOrNull().orEmpty()
        "follow" -> args.firstOrNull().orEmpty()
        "remember" -> "تذكّرتُ: " + args.firstOrNull().orEmpty()
        else -> kind
    }
}

object ChatText {
    private val ACTION = Regex("<<action:([a-z]+)((?:\\|[^>|]*)*)>>")

    /** The reply without its action tags, and the actions in order. */
    fun split(text: String): Pair<String, List<ChatAction>> {
        val acts = ACTION.findAll(text).map { m -> ChatAction(m.groupValues[1], m.groupValues[2].split("|").drop(1).map { it.trim() }) }.toList()
        // while streaming, hide a tag that is not closed yet
        val clean = ChatTools.strip(ACTION.replace(text, "")).replace(Regex("<<[^>]*$"), "").trimEnd()
        return clean to acts
    }
}

class ChatStore(ctx: Context) {
    private val file = File(ctx.filesDir, "chats.json")
    private val _all = MutableStateFlow(read())
    val all: StateFlow<List<Conversation>> = _all

    private fun read(): List<Conversation> = runCatching {
        val a = JSONArray(file.readText())
        (0 until a.length()).map { i -> a.getJSONObject(i).let { o ->
            val m = o.optJSONArray("messages") ?: JSONArray()
            Conversation(o.optString("id"), o.optString("title"), o.optLong("updated"), (0 until m.length()).map { k -> m.getJSONObject(k).let { x ->
                val im = x.optJSONArray("images") ?: JSONArray()
                ChatMsg(x.optString("role"), x.optString("text"), (0 until im.length()).map { im.getString(it) }, x.optLong("t"))
            } }, o.optBoolean("starred"))
        } }.sortedByDescending { it.updated }
    }.getOrElse { emptyList() }

    @Synchronized private fun write(list: List<Conversation>) {
        // keep images only in the 2 newest conversations (they are big); text is kept for 200 conversations
        val keep = list.sortedByDescending { it.updated }.take(200).mapIndexed { i, c ->
            if (i < 2) c else c.copy(messages = c.messages.map { it.copy(images = emptyList()) })
        }
        _all.value = keep
        // 1.3.5 (speed): the JSON is built and written on a background thread (the list is immutable)
        DiskWriter.later(file) {
            JSONArray(keep.map { c ->
                JSONObject().put("id", c.id).put("title", c.title).put("updated", c.updated).put("starred", c.starred)
                    .put("messages", JSONArray(c.messages.map { m ->
                        JSONObject().put("role", m.role).put("text", m.text).put("t", m.t).put("images", JSONArray(m.images))
                    }))
            }).toString()
        }
    }

    fun get(id: String) = _all.value.firstOrNull { it.id == id }

    fun save(c: Conversation) = write(_all.value.filterNot { it.id == c.id } + c)
    fun delete(id: String) = write(_all.value.filterNot { it.id == id })
    fun star(id: String) { get(id)?.let { save(it.copy(starred = !it.starred, updated = it.updated)) } }
    fun new(): Conversation = Conversation(UUID.randomUUID().toString().take(12), "", System.currentTimeMillis(), emptyList())
}

object Claude {
    const val MODEL = "claude-sonnet-5-5"
    /** 1.2.1: Coarena's money works on every model it has on. When Claude is switched off there (400 model_off,
     *  2026-11-14), the next model on the same key answers instead of leaving Coarena (same order as the agent). */
    // 1.2.8: after Claude, the fast and cheap one for conversation (model-bench.yml, 2026-11-14)
    val COARENA_MODELS = listOf(MODEL, "claude-opus-5-5", "gpt-6-1-terra", "gemini-4-argon", "gpt-6-1-sol", "kimi-k3-1-pro", "gemini-3-8-flash")
    private const val MODEL_OFF = 460  // internal code: this model is off or unavailable on Coarena, try the next one
    private const val URL = "https://coarena.ai/api/v1/messages"
    private val client = Net.client.newBuilder().readTimeout(180, TimeUnit.SECONDS).build()

    /** A photo from the phone as a small JPEG (longest side 1280 px), base64. */
    fun imageB64(ctx: Context, uri: Uri): String? = runCatching {
        val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / sample > 2560) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        val k = 1280f / maxOf(bmp.width, bmp.height)
        val sc = if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true) else bmp
        val out = ByteArrayOutputStream(); sc.compress(Bitmap.CompressFormat.JPEG, 82, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    private fun content(m: ChatMsg): Any {
        if (m.images.isEmpty()) return m.text
        val a = JSONArray()
        m.images.forEach { b64 ->
            a.put(JSONObject().put("type", "image").put("source", JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", b64)))
        }
        a.put(JSONObject().put("type", "text").put("text", m.text.ifBlank { "(صورة)" }))
        return a
    }

    /**
     * Streams a reply: [onText] gets the whole text so far after each piece. Returns the final text.
     * Coarena's Anthropic-style endpoint; when it answers without a stream, the full JSON is read instead.
     */
    suspend fun stream(key: String, system: String, history: List<ChatMsg>, maxTokens: Int = 1500, model: String = MODEL,
                       onText: (String) -> Unit): String =
        withContext(Dispatchers.IO) {
            if (key.isBlank()) throw ApiError(401, "أضف مفتاح Coarena من الإعدادات ← الحساب والاتصالات، ثم أعد المحاولة.")
            // the newest images only (older turns go as text), to keep requests small
            val lastImg = history.indexOfLast { it.images.isNotEmpty() }
            val msgs = JSONArray(history.mapIndexed { i, m ->
                JSONObject().put("role", m.role).put("content", content(if (i == lastImg) m else m.copy(images = emptyList())))
            })
            val body = JSONObject().put("model", model).put("max_tokens", maxTokens).put("stream", true)
                .put("system", system).put("messages", msgs)
            val req = Request.Builder().url(URL).header("x-api-key", key).header("anthropic-version", "2023-06-01")
                .post(body.toString().toRequestBody(Net.JSON)).build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) {
                    val t = r.body?.string().orEmpty()
                    val msg = runCatching { JSONObject(t).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
                    if (t.contains("model_off") || t.contains("model_unavailable") || t.contains("turned off"))
                        throw ApiError(MODEL_OFF, "model off: $model")
                    throw ApiError(r.code, when {
                        r.code == 401 || r.code == 403 -> "مفتاح Coarena غير صالح أو انتهى. حدّثه من الإعدادات."
                        r.code == 429 || msg.contains("usage limit", true) || msg.contains("regain access", true) ->
                            "بلغ Claude حدّ الاستعمال الذي ضبطته في حساب Coarena" +
                                (Regex("(\\d{4}-\\d{2}-\\d{2})").find(msg)?.groupValues?.get(1)?.let { "، ويعود في $it" } ?: "") +
                                ". ارفع الحد من لوحة Coarena، أو أضف مفتاح Gemini في الإعدادات ليجيب بدلًا منه."
                        else -> "تعذّر الرد (${r.code}). ${msg.take(160)}"
                    })
                }
                val ctype = r.header("Content-Type").orEmpty()
                val src = r.body!!.source()
                if (!ctype.contains("event-stream")) {
                    val o = JSONObject(src.readUtf8())
                    val arr = o.optJSONArray("content") ?: JSONArray()
                    val text = (0 until arr.length()).map { arr.getJSONObject(it) }.filter { it.optString("type") == "text" }.joinToString("") { it.optString("text") }
                    onText(text); return@use text
                }
                val sb = StringBuilder()
                while (!src.exhausted()) {
                    val line = src.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty() || data == "[DONE]") continue
                    val o = runCatching { JSONObject(data) }.getOrNull() ?: continue
                    when (o.optString("type")) {
                        "content_block_delta" -> o.optJSONObject("delta")?.takeIf { it.optString("type") == "text_delta" }?.let {
                            sb.append(it.optString("text")); onText(sb.toString())
                        }
                        "error" -> throw ApiError(500, o.optJSONObject("error")?.optString("message") ?: "خطأ في البث")
                    }
                }
                sb.toString()
            }
        }

    data class Keys(val coarena: String, val gemini: String, val openRouter: String)

    /** Free vision models on OpenRouter, the last resort (same list as the cloud agent). */
    private val FREE = listOf("nvidia/nemotron-3.5-super-vl:free", "particle-ai/particle-1:free", "dots-studio/dots-3-note-preview:free")

    /**
     * Claude first; when its daily money or the account's own usage cap is reached, Gemini (key on the phone),
     * then free models. [onSwitch] tells the user once, in Arabic, which model answers instead.
     */
    suspend fun answer(k: Keys, system: String, history: List<ChatMsg>, maxTokens: Int = 1500, onSwitch: (String) -> Unit = {},
                       onText: (String) -> Unit): String {
        var last: Throwable? = null
        if (k.coarena.isNotBlank()) {
            var told = false
            var claudeOff = false
            for (m in COARENA_MODELS) {
                if (claudeOff && m.startsWith("claude")) continue   // one Claude off on Coarena means all of them (2026-11-14)
                try {
                    if (!told && (last as? ApiError)?.code == MODEL_OFF) {
                        told = true; onSwitch("Claude موقوف في Coarena الآن: يجيب ${ma.stickman.studio.ui.prettyModel(m)} بالرصيد نفسه")
                    }
                    return stream(k.coarena, system, history, maxTokens, m, onText)
                } catch (e: ApiError) {
                    last = e
                    if (e.code != MODEL_OFF) break   // money or key problem: leave Coarena
                    if (m.startsWith("claude")) claudeOff = true
                }
            }
            if ((last as? ApiError)?.code == MODEL_OFF)
                last = ApiError(503, "كل نماذج Coarena المفضلة موقوفة الآن (ليست مشكلة رصيد).")
        }
        if (k.gemini.isNotBlank()) {
            try {
                if (last != null) onSwitch("رصيد Coarena غير متاح الآن: يجيب Gemini")
                return openAi("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", k.gemini,
                    listOf("gemini-3.5-flash", "gemini-3-flash-preview"), system, history, maxTokens, onText)
            } catch (e: ApiError) { last = e }
        }
        if (k.openRouter.isNotBlank()) {
            try {
                if (last != null) onSwitch("Coarena وGemini غير متاحين الآن: يجيب نموذج مجاني")
                return openAi("https://openrouter.ai/api/v1/chat/completions", k.openRouter, FREE, system, history, maxTokens, onText)
            } catch (e: ApiError) { last = e }
        }
        throw last ?: ApiError(401, "أضف مفتاح Coarena أو Gemini من الإعدادات ← الحساب والاتصالات.")
    }

    /** An OpenAI-style streaming chat (Gemini's compatible endpoint, OpenRouter). Tries the models in order. */
    private suspend fun openAi(url: String, key: String, models: List<String>, system: String, history: List<ChatMsg>, maxTokens: Int,
                               onText: (String) -> Unit): String = withContext(Dispatchers.IO) {
        val lastImg = history.indexOfLast { it.images.isNotEmpty() }
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        history.forEachIndexed { i, m ->
            val c: Any = if (i == lastImg) JSONArray().apply {
                m.images.forEach { put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$it"))) }
                put(JSONObject().put("type", "text").put("text", m.text.ifBlank { "(صورة)" }))
            } else m.text
            msgs.put(JSONObject().put("role", m.role).put("content", c))
        }
        var err: ApiError? = null
        for (model in models) {
            val body = JSONObject().put("model", model).put("max_tokens", maxTokens).put("stream", true).put("messages", msgs)
            val req = Request.Builder().url(url).header("Authorization", "Bearer $key").post(body.toString().toRequestBody(Net.JSON)).build()
            try {
                client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) throw ApiError(r.code, "تعذّر الرد من النموذج الاحتياطي (${r.code}).")
                    val src = r.body!!.source()
                    val sb = StringBuilder()
                    while (!src.exhausted()) {
                        val line = src.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data.isEmpty() || data == "[DONE]") continue
                        val o = runCatching { JSONObject(data) }.getOrNull() ?: continue
                        o.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content")?.takeIf { it.isNotEmpty() && it != "null" }?.let {
                            sb.append(it); onText(sb.toString())
                        }
                    }
                    if (sb.isBlank()) throw ApiError(502, "رد فارغ")
                    return@withContext sb.toString()
                }
            } catch (e: ApiError) { err = e }
        }
        throw err ?: ApiError(502, "تعذّر الرد")
    }

    /** Reads a TikTok analytics screenshot and returns its numbers (null where not visible). */
    suspend fun readStats(keys: Keys, b64: String): JSONObject {
        // 1.3.4: also says whether it IS the analytics of one video, and what identifies that video (the screenshot
        // reader matches it to a project by itself), plus where viewers leave and the For You share.
        val sys = "You read TikTok / Reels / Shorts analytics screenshots. Reply with ONE JSON object only, no prose: " +
            "{\"is_analytics\":bool,\"views\":int|null,\"likes\":int|null,\"comments\":int|null,\"shares\":int|null,\"saves\":int|null," +
            "\"avg_watch_s\":number|null,\"completion_pct\":number|null,\"new_followers\":int|null," +
            "\"drop_s\":number|null,\"fyp_pct\":number|null,\"caption\":string|null,\"posted\":\"YYYY-MM-DD\"|null,\"duration_s\":number|null}. " +
            "is_analytics = true only for the analytics / insights page of ONE video (not a feed, a profile or a comment list). " +
            "Convert 1.2K to 1200 and 3,4 M to 3400000. completion_pct = 'watched full video' percent. " +
            "drop_s = the second where the retention curve falls the most (null if no curve). fyp_pct = the 'For You' share of traffic sources. " +
            "caption = the video's caption or title text exactly as shown. posted = its post date if shown. duration_s = its length if shown. " +
            "Use null when a value is not on the image."
        val txt = answer(keys, sys, listOf(ChatMsg("user", "Extract the numbers.", listOf(b64))), 600) {}
        val j = txt.substring(txt.indexOf('{').coerceAtLeast(0), (txt.lastIndexOf('}') + 1).coerceAtLeast(1))
        return JSONObject(j)
    }
}
