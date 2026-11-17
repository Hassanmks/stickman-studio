package ma.stickman.studio.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

// 1.3.3 «رصيد الذكاء الاصطناعي»: what the money buys today, live. The cloud writes one small file per paid step
// (cloud/state/ledger/<run>-<step>.json, by cloud/ledger.py); the phone keeps a copy of every file it has read,
// so a refresh only lists the folder and downloads the new ones. The model order comes from cloud/ladders.json,
// the same file the cloud agent reads.

/** One paid cloud step. [step] = topics · script · produce. */
data class SpendEntry(val run: Long, val at: Long, val step: String, val slug: String, val cost: Double,
                      val by: Map<String, Double>, val byModel: Map<String, Double>, val minutes: Double?)

/** The Coarena model order per task (cloud/ladders.json). */
data class Ladders(val script: List<String>, val default: List<String>, val economy: List<String>) {
    companion object {
        val FALLBACK = Ladders(
            listOf("claude-sonnet-5-5", "claude-opus-5-5", "gemini-4-argon", "gpt-6-1-sol", "gpt-6-1-terra", "kimi-k3-1-pro"),
            listOf("claude-sonnet-5-5", "claude-opus-5-5", "gpt-6-1-terra", "gemini-4-argon", "gpt-6-1-sol", "kimi-k3-1-pro", "gemini-3-8-flash"),
            listOf("gpt-6-1-terra", "gpt-6-1-sol", "claude-sonnet-5-5", "gemini-4-argon", "kimi-k3-1-pro", "gemini-3-8-flash"))

        fun parse(t: String): Ladders? = runCatching {
            val o = JSONObject(t)
            fun l(k: String, d: List<String>) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } }?.takeIf { it.isNotEmpty() } ?: d
            Ladders(l("script", FALLBACK.script), l("default", FALLBACK.default), l("economy", FALLBACK.economy))
        }.getOrNull()
    }

    /** (script ladder, other steps ladder) for the user's choice (auto / economy / coarena:<id>), null outside Coarena. */
    fun forChoice(choice: String): Pair<List<String>, List<String>>? = when {
        choice == "economy" -> economy to economy
        choice.startsWith("coarena:") -> choice.removePrefix("coarena:").let { f -> (listOf(f) + script.filter { it != f }) to (listOf(f) + default.filter { it != f }) }
        choice == "auto" || choice == "coarena" -> script to default
        else -> null
    }
}

class BudgetStore(ctx: Context) {
    private val file = File(ctx.filesDir, "ledger.json")
    private var cache: MutableMap<String, SpendEntry>? = null

    @Synchronized fun entries(): List<SpendEntry> = load().values.sortedBy { it.at }

    @Synchronized private fun load(): MutableMap<String, SpendEntry> = cache ?: runCatching {
        val o = JSONObject(file.readText()); o.keys().asSequence().associateWith { parse(o.getJSONObject(it)) }.toMutableMap()
    }.getOrElse { mutableMapOf() }.also { cache = it }

    /** Lists the cloud folder and downloads the files this phone has not read yet; drops the ones the cloud pruned. */
    suspend fun sync(gh: GitHub): Int {
        val names = gh.dir("cloud/state/ledger").filter { it.endsWith(".json") }
        if (names.isEmpty()) return 0
        val have = synchronized(this) { load().keys.toSet() }
        var added = 0
        for (n in names.filter { it !in have }) {
            val t = runCatching { gh.text("cloud/state/ledger/$n") }.getOrNull() ?: continue
            val e = runCatching { parse(JSONObject(t)) }.getOrNull() ?: continue
            synchronized(this) { load()[n] = e }; added++
        }
        synchronized(this) {
            val m = load(); val keep = names.toSet()
            m.keys.retainAll(keep)
            runCatching { file.writeText(JSONObject().apply { m.forEach { (k, v) -> put(k, toJson(v)) } }.toString()) }
        }
        return added
    }

    companion object {
        private fun map(o: JSONObject?): Map<String, Double> = o?.keys()?.asSequence()?.associateWith { o.optDouble(it, 0.0) } ?: emptyMap()
        fun parse(o: JSONObject) = SpendEntry(o.optLong("run"), runCatching { java.time.OffsetDateTime.parse(o.getString("at")).toInstant().toEpochMilli() }.getOrDefault(0L),
            o.optString("step"), o.optString("slug"), o.optDouble("cost", 0.0), map(o.optJSONObject("by")), map(o.optJSONObject("by_model")),
            o.optDouble("min").takeIf { !it.isNaN() })
        fun toJson(e: SpendEntry) = JSONObject().put("run", e.run).put("at", Instant.ofEpochMilli(e.at).toString()).put("step", e.step)
            .put("slug", e.slug).put("cost", e.cost).put("by", JSONObject(e.by)).put("by_model", JSONObject(e.byModel)).apply { e.minutes?.let { put("min", it) } }
    }
}

/** The numbers the card and its sheet show, computed once per refresh. */
object Budget {
    val MOROCCO: ZoneId = ZoneId.of("Africa/Casablanca")
    private fun median(l: List<Double>) = l.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }

    /** Typical cost of a step: the median of its latest 3 paid runs. Runs under $0.10 went to a free fallback when
     *  Coarena was off, and older runs used models that are off now, so neither tells what a step costs today. */
    fun stepCost(e: List<SpendEntry>, step: String): Double? = median(e.filter { it.step == step && it.cost >= 0.10 }.takeLast(3).map { it.cost })
    fun stepCount(e: List<SpendEntry>, step: String) = e.count { it.step == step }
    fun stepMinutes(e: List<SpendEntry>, step: String): Double? = median(e.filter { it.step == step }.takeLast(8).mapNotNull { it.minutes })

    /** A whole video = its script + its production (topics are shared by ten ideas, shown apart). */
    fun perVideo(e: List<SpendEntry>): Double? {
        val s = stepCost(e, "script"); val p = stepCost(e, "produce")
        return if (s == null && p == null) null else (s ?: 1.2) + (p ?: 0.4)
    }

    /** Dollars per provider on one day (Morocco's calendar). */
    fun day(e: List<SpendEntry>, d: LocalDate): Map<String, Double> = e.filter { Instant.ofEpochMilli(it.at).atZone(MOROCCO).toLocalDate() == d }
        .flatMap { it.by.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }

    /** Dollars per provider since 00:00 UTC (Coarena's day). */
    fun sinceReset(e: List<SpendEntry>, provider: String): Double {
        val start = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return e.filter { it.at >= start }.sumOf { it.by[provider] ?: 0.0 }
    }

    /** The last [n] days, oldest first: (day, dollars per provider). */
    fun lastDays(e: List<SpendEntry>, n: Int = 7): List<Pair<LocalDate, Map<String, Double>>> {
        val today = LocalDate.now(MOROCCO)
        return (n - 1 downTo 0).map { today.minusDays(it.toLong()).let { d -> d to day(e, d) } }
    }

    /** Whole videos (script + production of one slug), dearest first. */
    fun videos(e: List<SpendEntry>): List<Pair<String, Double>> = e.filter { it.slug.isNotBlank() && it.step in setOf("script", "produce") }
        .groupBy { it.slug }.filter { (_, v) -> v.any { it.step == "produce" } && v.any { it.step == "script" } }
        .map { (k, v) -> k to v.sumOf { it.cost } }.filter { it.second >= 0.10 }.sortedByDescending { it.second }   // free fallback runs are not a price

    /** Mean dollars per run of each Coarena model (from runs that recorded it). */
    fun models(e: List<SpendEntry>): List<Triple<String, Double, Int>> = e.flatMap { it.byModel.entries.map { m -> m.key.removePrefix("coarena/") to m.value } }
        .filter { it.second > 0 }.groupBy({ it.first }, { it.second }).map { (k, v) -> Triple(k, v.average(), v.size) }.sortedByDescending { it.third }

    /** Milliseconds until Coarena's day restarts (00:00 UTC). */
    fun msToReset(now: Long = System.currentTimeMillis()): Long =
        LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - now

    /** When today's money runs out at today's pace (epoch ms), or null when it lasts until the reset. */
    fun runsOutAt(used: Double, left: Double, now: Long = System.currentTimeMillis()): Long? {
        val start = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val hours = (now - start) / 3_600_000.0
        if (used < 0.05 || hours < 0.5 || left <= 0) return null
        val rate = used / hours                       // dollars per hour so far today
        val out = now + (left / rate * 3_600_000).toLong()
        return out.takeIf { it < now + msToReset(now) }
    }

    fun dur(ms: Long): String {
        val m = (ms / 60_000).coerceAtLeast(0); val h = m / 60
        return if (h > 0) "$h س ${m % 60} د" else "$m د"
    }

    /** Readable name of a Coarena model. */
    fun modelName(id: String): String = when (id.removePrefix("coarena/")) {
        "claude-sonnet-5-5" -> "Claude Sonnet 5.5"; "claude-opus-5-5" -> "Claude Opus 5.5"
        "gemini-4-argon" -> "Gemini 4 Argon"; "gpt-6-1-terra" -> "GPT-6.1 Terra"; "gpt-6-1-sol" -> "GPT-6.1 Sol"
        "kimi-k3-1-pro" -> "Kimi K3.1 Pro"; "gemini-3-8-flash" -> "Gemini 3.8 Flash"
        else -> id.removePrefix("coarena/")
    }

    fun stepName(step: String) = when (step) { "topics" -> "المواضيع"; "script" -> "السيناريو"; "produce" -> "الإنتاج"; else -> step }
}
