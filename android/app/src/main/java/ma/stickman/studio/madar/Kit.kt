package ma.stickman.studio.madar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * TaleSpark 2.0 «مَدار»: the Gemini prompt and the script check, on the phone with no internet (assets/madar_kit.json,
 * written by engine/scripts/app_kit.mjs from the same template and rules as engine/scripts/script_check.mjs).
 */
class Kit(ctx: Context) {
    // first: the properties below already call bare(), which needs it (initialisation order: the 2.0.1 crash)
    private val har = Regex("[\\u064B-\\u0652\\u0670]")
    private val k = JSONObject(ctx.assets.open("madar_kit.json").bufferedReader().readText())
    private val rules = k.getJSONObject("rules")
    private val wps = rules.getDouble("words_per_second")
    private fun set(a: JSONArray) = (0 until a.length()).map { a.getString(it) }.toSet()
    private val places = set(k.getJSONArray("places")); private val lanes = set(k.getJSONArray("lanes"))
    private val a3 = set(k.getJSONArray("a3")); private val numw = set(k.getJSONArray("numw")); val top = set(k.getJSONArray("top"))
    private val fx = set(rules.getJSONArray("fx")); private val dialect = set(rules.getJSONArray("dialect")).map { bare(it) }.toSet()
    private val ambErr = set(rules.getJSONArray("ambiguous_error"))
    private val amb = rules.getJSONObject("ambiguous").let { o -> o.keys().asSequence().associateWith { o.getString(it) } }
    private val forbidden = rules.getJSONArray("forbidden").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    data class Obj(val name: String, val kind: String, val ar: List<List<String>>, val desc: String, val have: Boolean)
    private val objs = k.getJSONArray("objects").let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
        Obj(o.getString("name"), o.getString("kind"), o.getJSONArray("ar").let { w -> (0 until w.length()).map { w.getString(it).split(Regex("\\s+")).map(::bare) } }, o.getString("desc"), o.getBoolean("have")) } }
    /** 2.2: the objects a script names, first mention of each: (object, the word as written) */
    fun objectsIn(lines: List<String>): List<Pair<Obj, String>> {
        val out = mutableListOf<Pair<Obj, String>>()
        val ws = lines.flatMap { it.split(Regex("\\s+")).filter(String::isNotBlank) }; val st = ws.map(::stems)
        for (o in objs) for (kw in o.ar) { val at = st.indices.firstOrNull { i -> kw.indices.all { j -> i + j < st.size && kw[j] in st[i + j] } }
            if (at != null) { out.add(o to ws[at].filter { it.isLetter() }); break } }
        return out
    }
    val sections: List<String> = rules.getJSONArray("sections").let { a -> (0 until a.length()).map { a.getString(it) } }

    fun prompt(topic: String, seconds: Int, angle: String = ""): String = k.getString("prompt")
        .replace("{{TOPIC}}", topic.trim()).replace("{{ANGLE}}", if (angle.isBlank()) "" else "الزاوية: ${angle.trim()}")
        .replace("{{SECONDS}}", seconds.toString()).replace("{{WORDS_MIN}}", Math.round(seconds * wps * .9).toString())
        .replace("{{WORDS_MAX}}", Math.round(seconds * wps * 1.08).toString()).replace(Regex("\n{3,}"), "\n\n")

    fun fixPrompt(r: Report): String = k.getString("fix").replace("{{PROBLEMS}}", r.problems.joinToString("\n") { "- " + it.text })

    /** one ChatGPT sheet for the missing objects; each says its own camera (moving = straight from above, still = 3/4 aerial) */
    fun sheetPrompt(names: List<String>): String {
        val n = names.size; val cols = if (n <= 2) n else if (n <= 4) 2 else 3; val rows = (n + cols - 1) / cols
        return k.getString("sheet").replace("{{N}}", "$n").replace("{{COLS}}", "$cols").replace("{{ROWS}}", "$rows")
            .replace("{{OBJECTS}}", names.mapIndexed { i, x -> val o = objs.firstOrNull { it.name == x }
                "${i + 1}. " + (if (o == null) x else (if (o.kind == "move") "TOP VIEW. " else "AERIAL VIEW. ") + o.desc) }.joinToString("\n"))
    }

    // ------------------------------------------------------------------ the reply
    data class Plan(val n: Int?, val place: String?, val countries: List<String>, val number: String?, val craft: String?, val lane: String?, val fx: String?, val title: String?, val raw: String)
    data class Reply(val found: List<String>, val script: List<String>, val sources: List<String>, val notes: List<String>, val plan: List<Plan>)
    data class Problem(val error: Boolean, val line: Int, val text: String)
    data class Report(val ok: Boolean, val words: Int, val seconds: Int, val lines: Int, val sheets: List<String>, val problems: List<Problem>)

    fun parse(text: String): Reply {
        val head = Regex("^[\\s#*_>\\-]*\\[?\\s*(${sections.joinToString("|")})\\s*\\]?[\\s*_:：]*$")
        val S = linkedMapOf<String, MutableList<String>>(); var cur: String? = null
        // the Gemini app copies its answer as markdown: \[السكربت\], \*, \- … the backslashes go first (2.1.1)
        val clean = text.replace("\r", "").replace(Regex("""\\([\[\]*_#>.()|!+\-])"""), "$1")
        for (raw in clean.split("\n")) {
            val m = head.find(raw.trim()); if (m != null) { cur = m.groupValues[1]; S.getOrPut(cur) { mutableListOf() }; continue }
            cur?.let { S[it]!!.add(raw) }
        }
        // 2.1.1: the script alone (no section headers at all) is a script too: the director then works from the words
        if (S.isEmpty()) S[sections[0]] = clean.split("\n").toMutableList()
        fun lines(k: String) = (S[k] ?: emptyList()).map { it.trim() }.filter { it.isNotEmpty() }
        val script = lines(sections[0]).map { it.replace(Regex("^\\s*(?:[-*•]\\s+|\\d+[.)]\\s+)"), "").replace("**", "").replace("...", "…").trim() }.filter { it.isNotEmpty() }
        val plan = lines(sections[3]).filter { '|' in it }.map(::planLine)
        return Reply(sections.filter { S.containsKey(it) }, script, lines(sections[1]), lines(sections[2]), plan)
    }

    private val keys = mapOf("مكان" to "place", "دول" to "countries", "الدول" to "countries", "رقم" to "number", "مجسم" to "craft", "مسار" to "lane", "مؤثر" to "fx", "عنوان" to "title")
    private fun planLine(l: String): Plan {
        val parts = l.replace("**", "").split("|").map { it.trim() }; val o = mutableMapOf<String, String>()
        for (p in parts.drop(1)) {
            val m = Regex("^([^:：]+)[:：]\\s*(.*)$").find(p) ?: continue; val key = keys[m.groupValues[1].trim()] ?: continue
            val v = m.groupValues[2].trim(); if (v.isEmpty() || v == "-" || v == "—") continue; o[key] = v.replace("`", "")
        }
        return Plan(Regex("\\d+").find(parts[0])?.value?.toInt(), o["place"], o["countries"]?.split(Regex("[،,\\s]+"))?.map { it.trim().uppercase() }?.filter { it.isNotEmpty() } ?: emptyList(),
            o["number"], o["craft"], o["lane"], o["fx"], o["title"], l)
    }

    // ------------------------------------------------------------------ the checks (same rules as script_check.mjs)
    fun bare(w: String) = w.replace(har, "").replace("ـ", "").replace(Regex("[أإآ]"), "ا").replace('ة', 'ه').replace('ى', 'ي').filter { it.isLetterOrDigit() }
    private fun stems(w: String): Set<String> {
        val o = linkedSetOf(bare(w))
        for (x in o.toList()) if (x.length > 3 && x[0] in "وفبكل") o.add(x.substring(1))
        for (x in o.toList()) if (x.length > 4 && (x.startsWith("ال") || x.startsWith("لل"))) o.add(x.substring(2))
        return o
    }

    fun check(r: Reply, seconds: Int?): Report {
        val P = mutableListOf<Problem>(); fun add(e: Boolean, l: Int, t: String) { P.add(Problem(e, l, t)) }
        // 2.2.0: the script alone is the normal reply; sections are checked only when a reply has them
        if (!(r.found == listOf(sections[0]) && r.plan.isEmpty())) for (s in sections) if (s !in r.found) add(s == sections[0], 0, "القسم [$s] غير موجود في الرد.")
        val sc = r.script; val nW = sc.joinToString(" ").split(Regex("\\s+")).count { it.isNotBlank() }; val est = nW / wps
        if (seconds != null && (est < seconds * .8 || est > seconds * 1.2))
            add(false, 0, "الطول $nW كلمة، أي نحو ${est.toInt()} ثانية، والمطلوب نحو $seconds ثانية (بين ${Math.round(seconds * wps * .9)} و${Math.round(seconds * wps * 1.08)} كلمة).")
        sc.forEachIndexed { i, l ->
            val L = i + 1
            Regex("[0-9٠-٩]+(?:[.,٫][0-9٠-٩]+)?%?").findAll(l).map { it.value }.toList().takeIf { it.isNotEmpty() }?.let { add(true, L, "السطر $L: الرقم ${it.joinToString("، ")} مكتوب بالأرقام؛ اكتبه كلمات كما يُنطق.") }
            if (!Regex("[.!?؟…][\"»”)]*$").containsMatchIn(l)) add(true, L, "السطر $L لا ينتهي بعلامة وقف (. أو ؟ أو ! أو …).")
            for (s in l.split(Regex("(?<=[.!?؟…])\\s+"))) { val n = s.split(Regex("\\s+")).count { it.isNotBlank() }; val mx = rules.getInt("max_words_per_sentence")
                if (n > mx) add(false, L, "السطر $L: جملة من $n كلمة؛ قسّمها (الحد $mx).") }
            for (run in l.split(Regex("[،,؛;:.!?؟…]"))) { val ws = run.split(Regex("\\s+")).filter { it.isNotBlank() }; val mx = rules.getInt("max_words_without_comma")
                if (ws.size > mx) add(false, L, "السطر $L: ${ws.size} كلمة دون أي علامة وقف («${ws.take(4).joinToString(" ")} …»)؛ أضف فاصلة عند موضع النَّفَس.") }
            for (w in l.split(Regex("\\s+"))) {
                val st = stems(w).firstOrNull { it in amb.keys }
                if (st != null && !har.containsMatchIn(w)) add(st in ambErr, L, "السطر $L: «${w.filter { it.isLetter() || har.matches(it.toString()) }}» تحتمل أكثر من نطق؛ شكّلها: ${amb[st]}.")
                if (stems(w).any { it in dialect }) add(true, L, "السطر $L: «$w» كلمة عامية؛ استعمل الفصحى.")
            }
            val low = l.replace(har, "")
            for (f in forbidden) if (low.contains(f.getString("find").replace(har, ""))) add(true, L, "السطر $L: «${f.getString("find")}» (${f.getString("why")})؛ البديل: ${f.getString("say")}.")
        }
        if (sc.isNotEmpty()) {
            val first = sc.first(); val last = sc.last()
            if (!Regex("[?؟!]").containsMatchIn(first) && first.split(Regex("\\s+")).none { w -> stems(w).any { it in numw } })
                add(false, 1, "السطر الأول ليس خطّافاً: اجعله سؤالاً أو حقيقة فيها رقم أو مكان محدد.")
            fun content(s: String) = s.split(Regex("\\s+")).map { bare(it).replace(Regex("^(و|ف|ب|ل)?(ال)?"), "") }.filter { it.length >= 4 }.toSet()
            if ((content(last) intersect content(first)).isEmpty()) add(false, sc.size, "السطر الأخير لا يعيد صدى الخطّاف؛ الحلقة المغلقة تجعل الإعادة متصلة (كرّر كلمة مفتاحية من السطر الأول).")
        }
        if (sections[3] in r.found) {
            if (r.plan.size != sc.size) add(true, 0, "خطة اللقطات فيها ${r.plan.size} سطراً والسكربت ${sc.size} سطراً؛ يلزم سطر لكل سطر.")
            r.plan.forEachIndexed { i, p ->
                val L = p.n ?: (i + 1)
                if (p.place != null && !p.place.startsWith("جديد") && p.place !in places) add(false, L, "خطة اللقطات $L: المكان ${p.place} غير موجود في قاعدة الأماكن؛ سيُؤطَّر على الدول بدلاً منه.")
                for (c in p.countries) if (c !in a3) add(false, L, "خطة اللقطات $L: الرمز $c ليس رمز دولة معروفاً (ISO الثلاثي).")
                if (p.craft != null && p.craft !in top) add(false, L, "خطة اللقطات $L: المجسم ${p.craft} ليس جاهزاً؛ يلزم لوحة ChatGPT (منظر علوي) قبل الإنتاج.")
                if (p.lane != null && p.lane !in lanes) add(false, L, "خطة اللقطات $L: المسار ${p.lane} غير موجود؛ سيُرسم بلا مسار.")
                if (p.fx != null && p.fx !in fx) add(false, L, "خطة اللقطات $L: المؤثر «${p.fx}» غير معروف.")
                if (p.title != null && p.title.split(Regex("\\s+")).size > 4) add(false, L, "خطة اللقطات $L: العنوان «${p.title}» أطول من أربع كلمات.")
            }
        }
        if (r.sources.isEmpty() && sections[1] in r.found) add(false, 0, "قسم المصادر فارغ: كل رقم يحتاج مصدراً.")
        val found = objectsIn(sc); val miss = found.filter { !it.first.have }
        if (miss.isNotEmpty()) add(false, 0, "مجسمات غير موجودة بعد: ${miss.joinToString("، ") { "«" + it.second + "»" }}. لوحة ChatGPT واحدة تضيفها إلى المكتبة (مرة واحدة فقط).")
        val sheets = (miss.map { it.first.name } + r.plan.mapNotNull { it.craft }.filter { it !in top }).distinct()
        return Report(P.none { it.error }, nW, est.toInt(), sc.size, sheets, P)
    }

    /** a reply worth catching from the clipboard: it has the script section */
    /** what to catch from the clipboard on return: Gemini's script (Arabic narration, several lines, punctuation), never our own prompts */
    fun looksLikeReply(t: String?): Boolean {
        if (t == null || t.length < 120 || t.contains("أنت كاتب سكربتات") || t.contains("{{") || t.contains("راجعتُ السكربت")) return false
        if (t.contains(sections[0]) && t.contains(sections[3])) return true
        val lines = t.lines().filter { it.isNotBlank() }; val arabic = t.count { it in '؀'..'ۿ' }
        return lines.size >= 3 && arabic > t.length * .5 && Regex("[.!؟…]").containsMatchIn(t)
    }
}
