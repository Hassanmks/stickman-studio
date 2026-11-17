package ma.stickman.studio.madar

import android.content.Context
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** What the app keeps between launches (plain prefs: the pairing token only opens this user's own PC). */
class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("madar", Context.MODE_PRIVATE)
    var hosts: String get() = p.getString("hosts", "") ?: ""; set(v) = p.edit().putString("hosts", v).apply()
    var port: Int get() = p.getInt("port", 8765); set(v) = p.edit().putInt("port", v).apply()
    var token: String get() = p.getString("token", "") ?: ""; set(v) = p.edit().putString("token", v).apply()
    var mode: String get() = p.getString("mode", "auto") ?: "auto"; set(v) = p.edit().putString("mode", v).apply()
    var seconds: Int get() = p.getInt("seconds", 90); set(v) = p.edit().putInt("seconds", v).apply()
    var topic: String get() = p.getString("topic", "") ?: ""; set(v) = p.edit().putString("topic", v).apply()
    var angle: String get() = p.getString("angle", "") ?: ""; set(v) = p.edit().putString("angle", v).apply()
    var reply: String get() = p.getString("reply", "") ?: ""; set(v) = p.edit().putString("reply", v).apply()
    var voice: String get() = p.getString("voice", "") ?: ""; set(v) = p.edit().putString("voice", v).apply()
    var voiceName: String get() = p.getString("voiceName", "") ?: ""; set(v) = p.edit().putString("voiceName", v).apply()
    var sheet: String get() = p.getString("sheet", "") ?: ""; set(v) = p.edit().putString("sheet", v).apply()
    var job: String get() = p.getString("job", "") ?: ""; set(v) = p.edit().putString("job", v).apply()
    val paired get() = token.isNotBlank() && hosts.isNotBlank()

    /** talespark://pair?hosts=a,b&port=8765&token=... (printed by host/host.py, opened on the phone or pushed by adb) */
    fun pair(u: Uri): Boolean {
        val t = u.getQueryParameter("token") ?: return false; val h = u.getQueryParameter("hosts") ?: return false
        token = t; hosts = h; port = u.getQueryParameter("port")?.toIntOrNull() ?: 8765; return true
    }
}

/** The TaleSpark host on the PC (host/host.py): the first address that answers is used (home Wi-Fi or Tailscale). */
class Host(private val s: Store) {
    private val http = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).writeTimeout(300, TimeUnit.SECONDS).build()
    private var base: String? = null
    private val json = "application/json; charset=utf-8".toMediaType()

    private fun bases() = s.hosts.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { if (it.startsWith("http")) it else "http://$it:${s.port}" }
    private fun call(r: Request.Builder): JSONObject {
        val res = http.newCall(r.header("X-Token", s.token).build()).execute()
        val body = res.body?.string() ?: "{}"
        val o = runCatching { JSONObject(body) }.getOrElse { JSONObject().put("error", body.take(200)) }
        if (!res.isSuccessful) throw HostError(o.optString("error", "خطأ ${res.code}"))
        return o
    }
    class HostError(m: String) : Exception(m)

    /** status of the PC, or null when no address answers */
    fun status(): JSONObject? {
        for (b in listOfNotNull(base) + bases()) {
            val o = runCatching { call(Request.Builder().url("$b/status")) }.getOrNull()
            if (o != null) { base = b; return o }
        }
        base = null; return null
    }
    private fun url(p: String): String = (base ?: run { status(); base } ?: throw HostError("الحاسوب غير متاح الآن")) + p

    fun newJob(topic: String, reply: String, seconds: Int, mode: String): JSONObject =
        call(Request.Builder().url(url("/jobs")).post(JSONObject().put("topic", topic).put("reply", reply).put("seconds", seconds).put("mode", mode).toString().toRequestBody(json)))
    fun upload(path: String, ctx: Context, uri: Uri, type: String): JSONObject {
        val body = object : RequestBody() {
            override fun contentType() = type.toMediaType()
            override fun contentLength() = runCatching { ctx.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L }.getOrDefault(-1L)
            override fun writeTo(sink: BufferedSink) { ctx.contentResolver.openInputStream(uri)!!.source().use { sink.writeAll(it) } }
        }
        return call(Request.Builder().url(url(path)).put(body))
    }
    fun run(id: String, force: Boolean, mode: String) = call(Request.Builder().url(url("/jobs/$id/run")).post(JSONObject().put("force", force).put("mode", mode).toString().toRequestBody(json)))
    fun approve(id: String) = call(Request.Builder().url(url("/jobs/$id/approve")).post("{}".toRequestBody(json)))
    fun job(id: String) = call(Request.Builder().url(url("/jobs/$id")))
    fun download(path: String, to: File, progress: (Float) -> Unit = {}): File {
        val res = http.newCall(Request.Builder().url(url(path)).header("X-Token", s.token).build()).execute()
        if (!res.isSuccessful) throw HostError("تعذّر التنزيل (${res.code})")
        val total = res.body!!.contentLength(); var got = 0L; val tmp = File(to.path + ".part")
        res.body!!.byteStream().use { inp -> tmp.outputStream().use { out -> val buf = ByteArray(1 shl 16)
            while (true) { val n = inp.read(buf); if (n < 0) break; out.write(buf, 0, n); got += n; if (total > 0) progress(got.toFloat() / total) } } }
        tmp.renameTo(to); return to
    }
}
