package ma.stickman.studio.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

// ============================================================================================
// 1.3.6 «مساعد يعرف ويبحث»: the tools the chat assistant can ask for in its reply (<<tool:kind|arg>>). The phone
// runs them (no key, no cost), hands the results back as data, and the assistant answers with them:
//   search  – Wikipedia (Arabic, then English): facts, people, dates, with the page link
//   news    – Google News (Morocco, Arabic): this week's events, with source and date
//   fetch   – the text of one web page the user gave
//   read    – the studio's own files: a project's script / SEO / sources, the last topics, the log of videos
// Results are data from outside: the system prompt tells the assistant never to follow instructions found in them.
// ============================================================================================
object ChatTools {
    private val client = Net.client.newBuilder().readTimeout(20, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()
    private const val UA = "TaleSpark/1.3 (Android; studio assistant)"

    data class Tool(val kind: String, val arg: String)

    private val TAG = Regex("<<tool:([a-z]+)\\|([^>]*)>>")
    fun find(text: String): List<Tool> = TAG.findAll(text).map { Tool(it.groupValues[1], it.groupValues[2].trim()) }.filter { it.arg.isNotBlank() }.toList()
    fun strip(text: String) = TAG.replace(text, "").replace(Regex("<<tool[^>]*$"), "").trimEnd()

    /** What the line above the reply says while a tool runs. */
    fun label(t: Tool) = when (t.kind) {
        "search" -> "يبحث في ويكيبيديا: ${t.arg}"
        "news" -> "يقرأ الأخبار: ${t.arg}"
        "fetch" -> "يقرأ الصفحة…"
        "read" -> "يقرأ ملفات المشروع…"
        else -> "يعمل…"
    }

    private fun get(url: String, ua: String = UA): String? = runCatching {
        client.newCall(Request.Builder().url(url).header("User-Agent", ua).build()).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    }.getOrNull()

    private const val BROWSER = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    suspend fun run(ctx: Context, studio: Studio, t: Tool, ledger: List<SpendEntry>): String = withContext(Dispatchers.IO) {
        val out = runCatching {
            when (t.kind) {
                "search" -> wiki("ar", t.arg) ?: wiki("en", t.arg) ?: "لا نتيجة في ويكيبيديا."
                "news" -> news(t.arg) ?: "لا أخبار."
                "fetch" -> page(t.arg) ?: "تعذّرت قراءة الصفحة."
                "read" -> read(studio, t.arg, ledger)
                else -> "أداة غير معروفة."
            }
        }.getOrElse { "تعذّر: ${it.message ?: it.javaClass.simpleName}" }
        "### ${t.kind}: ${t.arg}\n" + out.take(5000)
    }

    private fun wiki(lang: String, q: String): String? {
        val u = "https://$lang.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=${enc(q)}&gsrlimit=3&prop=extracts|info" +
            "&inprop=url&exintro=1&explaintext=1&exlimit=3&exchars=900&format=json&utf8=1"
        val pages = JSONObject(get(u) ?: return null).optJSONObject("query")?.optJSONObject("pages") ?: return null
        val list = pages.keys().asSequence().map { pages.getJSONObject(it) }.sortedBy { it.optInt("index", 9) }.toList()
        if (list.isEmpty()) return null
        return list.joinToString("\n\n") { p -> "- ${p.optString("title")} (${p.optString("fullurl")}):\n${p.optString("extract").trim()}" }
    }

    /**
     * 1.3.7: the news tool reads the ARTICLES, not only headlines (1.3.6 gave Google News titles only, so the assistant
     * never found a score and kept searching until its answer was cut). Hespress' own search gives direct article
     * links: the two best articles are read (their paragraphs), the others are listed; Google News adds headlines.
     */
    private fun news(q: String): String? {
        val parts = mutableListOf<String>()
        val hs = get("https://www.hespress.com/?s=${enc(q)}", BROWSER)
        val links = hs?.let { h ->
            Regex("""<a[^>]+href="(https://www\.hespress\.com/[^"]+\.html)"[^>]*title="([^"]+)"""").findAll(h)
                .map { java.net.URLDecoder.decode(it.groupValues[1], "UTF-8") to unescape(it.groupValues[2]).trim() }
                .distinctBy { it.first }.take(6).toList()
        }.orEmpty()
        links.take(2).forEach { (u, t) ->
            article(u)?.let { parts.add("### $t (هسبريس)\n$u\n$it") }
        }
        if (links.size > 2) parts.add("مقالات أخرى في هسبريس (اقرأ أيًّا منها بـ fetch):\n" +
            links.drop(2).joinToString("\n") { "- ${it.second} — ${it.first}" })
        val xml = get("https://news.google.com/rss/search?q=${enc("$q when:30d")}&hl=ar&gl=MA&ceid=MA:ar")
        if (xml != null) {
            val items = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(xml).take(6).map { m ->
                fun tag(n: String) = Regex("<$n[^>]*>(.*?)</$n>", RegexOption.DOT_MATCHES_ALL).find(m.groupValues[1])?.groupValues?.get(1)
                    ?.replace("<![CDATA[", "")?.replace("]]>", "")?.trim().orEmpty()
                "- ${unescape(tag("title"))} — ${tag("pubDate").take(16)}"
            }.toList()
            if (items.isNotEmpty()) parts.add("عناوين أخرى (أخبار جوجل، بلا نص):\n" + items.joinToString("\n"))
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
    }

    /** The paragraphs of a news article (its <p> blocks), without menus and related links. */
    private fun article(url: String): String? {
        val html = get(url, BROWSER) ?: return null
        val ps = Regex("(?s)<p[^>]*>(.*?)</p>").findAll(html)
            .map { unescape(it.groupValues[1].replace(Regex("(?s)<[^>]+>"), " ")).replace(Regex("\\s+"), " ").trim() }
            .filter { it.length > 60 }.toList()
        if (ps.isEmpty()) return null
        val sb = StringBuilder()
        for (p in ps) { if (sb.length + p.length > 2200) break; sb.append(p).append("\n") }
        return sb.toString().trim()
    }

    private fun page(url: String): String? {
        if (!url.startsWith("http")) return null
        article(url)?.takeIf { it.length > 400 }?.let { return it }
        val html = get(url, BROWSER) ?: return null
        val body = html.replace(Regex("(?is)<(script|style|noscript|svg|nav|footer|header)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?s)<[^>]+>"), " ")
        return unescape(body).replace(Regex("\\s+"), " ").trim().take(4500)
    }

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<")
        .replace("&gt;", ">").replace("&nbsp;", " ")

    /** read|project:<slug> · read|topics · read|log · read|spend */
    private suspend fun read(studio: Studio, what: String, ledger: List<SpendEntry>): String {
        val w = what.trim()
        return when {
            w.startsWith("project:") -> {
                val slug = w.removePrefix("project:").trim().lowercase()
                if (!Regex("[a-z0-9][a-z0-9-]*").matches(slug)) return "اسم مشروع غير صالح."
                val sc = runCatching { studio.gh.text("projects/$slug/script.json") }.getOrNull()
                val lines = sc?.let { s ->
                    val o = JSONObject(s)
                    val a = o.optJSONArray("scenes") ?: JSONArray()
                    "العنوان: ${o.optString("title")}\nالسرد:\n" + (0 until a.length()).joinToString("\n") { i -> "${i + 1}. " + a.getJSONObject(i).optString("narration") }
                } ?: "لا سيناريو لهذا المشروع."
                val seo = runCatching { studio.gh.text("projects/$slug/seo.txt") }.getOrNull()?.take(1500)
                val src = runCatching { studio.gh.text("projects/$slug/sources.md") }.getOrNull()?.take(1200)
                val cost = ledger.filter { it.slug == slug }.sumOf { it.cost }.takeIf { it > 0 }?.let { "التكلفة المسجلة: %.2f$".format(java.util.Locale.US, it) }
                listOfNotNull(lines, seo?.let { "حزمة النشر:\n$it" }, src?.let { "المصادر:\n$it" }, cost).joinToString("\n\n")
            }
            w == "topics" -> runCatching {
                val a = JSONObject(studio.gh.text("cloud/state/topics.json")).optJSONArray("topics") ?: JSONArray()
                (0 until a.length()).joinToString("\n") { i -> a.getJSONObject(i).let { "${it.optInt("n")}. ${it.optString("title")} — ${it.optString("hook")}" } }
            }.getOrDefault("لا قائمة مواضيع.")
            w == "log" -> runCatching { studio.gh.text("videos/LOG.md").orEmpty().lines().takeLast(40).joinToString("\n") }.getOrDefault("لا سجل.")
            w == "spend" -> ledger.takeLast(40).joinToString("\n") { e ->
                "- ${java.time.Instant.ofEpochMilli(e.at).toString().take(16)} ${e.step} ${e.slug.ifBlank { "-" }} %.2f$".format(java.util.Locale.US, e.cost)
            }.ifBlank { "لا إنفاق مسجل." }
            else -> "لا أعرف «$w». المتاح: project:<slug> و topics و log و spend."
        }
    }
}

/** 1.3.6: what the assistant keeps about the user between conversations (<<action:remember|…>>), on the phone only. */
class ChatMemory(ctx: Context) {
    private val sp = ctx.getSharedPreferences("chat_memory", Context.MODE_PRIVATE)
    private val _items = kotlinx.coroutines.flow.MutableStateFlow(read())
    val items: kotlinx.coroutines.flow.StateFlow<List<String>> = _items

    private fun read(): List<String> = runCatching {
        val a = JSONArray(sp.getString("items", "[]")); (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    private fun write(l: List<String>) { _items.value = l; sp.edit().putString("items", JSONArray(l).toString()).apply() }

    fun add(fact: String) {
        val f = fact.trim().take(200)
        if (f.length < 4 || _items.value.any { it.equals(f, true) }) return
        write((_items.value + f).takeLast(40))
    }
    fun remove(fact: String) = write(_items.value - fact)

    /** The suggestions already shown (so the next ones are new): kept for 3 days. */
    fun shown(): Set<String> = runCatching {
        val o = JSONObject(sp.getString("shown", "{}")); val now = System.currentTimeMillis()
        o.keys().asSequence().filter { now - o.getLong(it) < 3 * 86_400_000L }.toSet()
    }.getOrDefault(emptySet())
    fun markShown(texts: List<String>) {
        val o = runCatching { JSONObject(sp.getString("shown", "{}")) }.getOrDefault(JSONObject()); val now = System.currentTimeMillis()
        texts.forEach { if (!o.has(it)) o.put(it, now) }
        o.keys().asSequence().toList().forEach { if (now - o.getLong(it) > 4 * 86_400_000L) o.remove(it) }
        sp.edit().putString("shown", o.toString()).apply()
    }
}
