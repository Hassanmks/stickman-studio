package ma.stickman.studio.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToLong

// 1.2.5 «أرقام صادقة»: every time the app shows comes from GitHub's own clock (run and step timestamps), never
// from when the phone was opened; every estimate says what it is based on, and is checked against what happened.

/** One finished cloud run, measured by GitHub. [paid] = a strong model did the AI part (not the free fallback). */
data class TimingRec(
    val stage: String, val slug: String, val title: String, val runId: Long,
    val start: Long, val end: Long, val queueS: Long, val steps: Map<String, Long>,
    val paid: Boolean, val ok: Boolean, val model: String, val costUsd: Double?, val predictedS: Long?,
) {
    val totalS get() = (end - start) / 1000

    fun toJson() = JSONObject().put("stage", stage).put("slug", slug).put("title", title).put("run", runId)
        .put("start", start).put("end", end).put("queue", queueS).put("steps", JSONObject(steps as Map<*, *>))
        .put("paid", paid).put("ok", ok).put("model", model).put("cost", costUsd ?: JSONObject.NULL)
        .put("pred", predictedS ?: JSONObject.NULL)

    companion object {
        fun from(o: JSONObject): TimingRec {
            val st = o.optJSONObject("steps") ?: JSONObject()
            return TimingRec(o.optString("stage"), o.optString("slug"), o.optString("title"), o.optLong("run"),
                o.optLong("start"), o.optLong("end"), o.optLong("queue"), st.keys().asSequence().associateWith { st.optLong(it) },
                o.optBoolean("paid", true), o.optBoolean("ok", true), o.optString("model"),
                if (o.isNull("cost")) null else o.optDouble("cost").takeIf { !it.isNaN() },
                if (o.isNull("pred") || !o.has("pred")) null else o.optLong("pred"))
        }
    }
}

/** What the running screen and the notification say about time. All in seconds. */
data class Eta(
    val elapsedS: Long,          // since GitHub started the run (its clock, not the phone's)
    val queuedS: Long?,          // waiting for a GitHub machine (null once the work began)
    val lowS: Long?, val highS: Long?,   // remaining, as a range
    val overS: Long?,            // past the usual time by this much
    val usualS: Long?,           // usual total of this step
    val basis: String,           // in Arabic: what the estimate rests on
    val fraction: Float,         // progress 0..1
) {
    /** One honest line, e.g. «بقي 4 إلى 6 د (من آخر 5 تشغيلات)». */
    fun line(): String = when {
        queuedS != null -> "ينتظر خادمًا من GitHub منذ ${dur(queuedS)}"
        overS != null && overS > 30 -> "تجاوز المعتاد بـ ${dur(overS)} (المعتاد نحو ${dur(usualS ?: 0)})"
        highS == null -> basis
        highS < 45 -> "قارب الانتهاء"
        else -> "بقي ${range(lowS ?: highS, highS)} ($basis)"
    }

    companion object {
        fun dur(s: Long): String = when {
            s < 60 -> "$s ث"
            s < 3600 -> if (s % 60 == 0L || s >= 600) "${(s + 30) / 60} د" else "%d:%02d د".format(s / 60, s % 60)
            else -> "%d س %d د".format(s / 3600, (s % 3600) / 60)
        }
        private fun range(a: Long, b: Long): String {
            val lo = (a / 60.0).roundToLong().coerceAtLeast(1); val hi = (b / 60.0).roundToLong().coerceAtLeast(lo)
            return if (lo == hi) "نحو $hi د" else "$lo إلى $hi د"
        }
    }
}

object Timing {
    /** First estimates, measured on GitHub on 2026-11-14 with a strong model (successful runs only). They are
     *  used until this phone has 2 runs of its own for the step, and the screen says so («تقدير أولي»). */
    private const val PAID_VIDEO = 0.15
    private val ONE_AI_JOB = setOf("TOPICS_RUNNING", "SCRIPT_RUNNING")
    private val SEED = mapOf("TOPICS_RUNNING" to 170L, "SCRIPT_RUNNING" to 720L, "PREVIEWING" to 330L, "PRODUCING" to 900L)

    fun parse(iso: String): Long? = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()

    fun isPaid(model: String) = !model.contains(":free")

    private fun median(l: List<Long>) = l.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
    private fun pct(l: List<Long>, p: Double) = l.sorted().let { if (it.isEmpty()) null else it[((it.size - 1) * p).roundToLong().toInt()] }

    /** Successful past runs of the same step on the same kind of model, newest last (at most 10). */
    fun history(local: LocalStore, stage: String, paid: Boolean) =
        local.timings().filter { it.stage == stage && it.ok && it.paid == paid && it.totalS > 20 }.takeLast(10)

    fun usual(local: LocalStore, stage: String, paid: Boolean): Long? =
        median(history(local, stage, paid).map { it.totalS }) ?: SEED[stage].takeIf { paid }

    /** The estimate for a running step, from GitHub's timestamps and this phone's measured history. */
    fun estimate(local: LocalStore, stage: String, run: RunInfo?, model: String?, now: Long = System.currentTimeMillis(),
                 plan: List<PlanItem> = emptyList()): Eta {
        val paid = model?.let { isPaid(it) } ?: true
        val created = run?.let { parse(it.createdAt) }
        val workStart = run?.workStartedAt?.let { parse(it) }
        val elapsed = ((now - (created ?: now)) / 1000).coerceAtLeast(0)
        val queued = run != null && !run.done && workStart == null
        if (queued) return Eta(elapsed, elapsed, null, null, null, null, "", 0.02f)
        // 1.3.1: the same model first (Argon writes a script in ~12 min, Terra in ~5), the same kind of model otherwise
        val hist = history(local, stage, paid).let { all -> all.filter { model != null && it.model == model }.takeIf { it.size >= 2 } ?: all }
        val totals = hist.map { it.totalS }
        val usual = median(totals) ?: SEED[stage].takeIf { paid }
        val basis = when {
            totals.size >= 2 -> "حسب آخر ${totals.size} تشغيلات مماثلة"
            usual != null -> "تقدير أولي"
            else -> "لا تقدير بعد: أول تشغيل بهذا النموذج"
        }
        if (usual == null) {
            val steps = run?.steps?.filterNot { it.name.startsWith("§") } ?: emptyList()
            val f = if (steps.isEmpty()) 0.03f else (steps.count { it.status == "completed" } + 0.5f) / steps.size
            return Eta(elapsed, null, null, null, null, null, basis, f.coerceIn(0.03f, 0.97f))
        }
        // remaining from the steps when this step's history knows them, else from the whole
        val stepMed: Map<String, Long> = hist.flatMap { it.steps.entries }.groupBy({ it.key }, { it.value })
            .mapValues { (_, v) -> median(v) ?: 0L }
        var rem: Long? = null
        val steps = run?.steps?.filter { it.name in stepMed } ?: emptyList()
        if (hist.size >= 2 && steps.isNotEmpty()) {
            rem = steps.sumOf { s ->
                val m = stepMed[s.name] ?: 0L
                when (s.status) {
                    "completed" -> 0L
                    "in_progress" -> (m - ((now - (parse(s.startedAt) ?: now)) / 1000)).coerceAtLeast(0)
                    else -> m
                }
            }
        }
        var center = rem ?: (usual - elapsed).coerceAtLeast(0)
        // 1.3.1: the agent's own plan says how far it really is («بقي 1 إلى 3 د» at minute 3 of a 15-minute script
        // came from the old guess alone). Once one item is done, never promise less than its pace implies.
        if (plan.size >= 3) {
            val done = plan.count { it.status == "done" } + 0.5 * plan.count { it.status == "now" }
            if (done >= 1.0) center = maxOf(center, (elapsed * (plan.size - done) / done).toLong())
        }
        val spread = if (totals.size >= 3) (((pct(totals, 0.75) ?: usual) - (pct(totals, 0.25) ?: usual)) / 2).coerceAtLeast(30)
                     else (usual * 0.25).toLong().coerceAtLeast(30)
        val over = (elapsed - (pct(totals, 0.75)?.takeIf { totals.size >= 3 } ?: (usual * 1.2).toLong())).takeIf { it > 0 }
        val f = (elapsed.toFloat() / (elapsed + center).coerceAtLeast(1)).coerceIn(0.03f, 0.97f)
        return if (over != null) Eta(elapsed, null, null, null, over, usual, basis, 0.97f)
        else Eta(elapsed, null, (center - spread).coerceAtLeast(0), center + spread, null, usual, basis, f)
    }

    /** Store a finished run, measured by GitHub, with what the app had predicted for it. */
    fun record(local: LocalStore, job: Job, run: RunInfo, live: LiveStatus?, slug: String = job.slug) {
        if (local.timings().any { it.runId == run.id }) return
        val start = parse(run.createdAt) ?: return
        val end = parse(run.updatedAt) ?: System.currentTimeMillis()
        val work = run.workStartedAt?.let { parse(it) }
        val model = live?.model.orEmpty()
        val paid = isPaid(model)
        val steps = run.steps.filter { it.startedAt.isNotBlank() && it.completedAt.isNotBlank() && !it.name.startsWith("§") }
            .associate { s -> s.name to (((parse(s.completedAt) ?: 0) - (parse(s.startedAt) ?: 0)) / 1000).coerceAtLeast(0) }
        local.addTiming(TimingRec(job.stage.name, slug, job.title, run.id, start, end, work?.let { ((it - start) / 1000).coerceAtLeast(0) } ?: 0,
            // a production runs several AI jobs; the live figure holds only the last one, so its money comes
            // from the video's result (cloud stats) instead, never from a partial number
            steps, paid, run.ok, model, live?.costUsd?.takeIf { it > 0 && job.stage.name in ONE_AI_JOB }, usual(local, job.stage.name, paid)))
    }

    // ---------- the whole video ----------
    data class Leg(val label: String, val seconds: Long, val costUsd: Double?, val byUser: Boolean, val model: String = "")
    /** [complete] = measured from the topics on; false when the first steps ran before 1.2.5 (then the user's time is unknown). */
    data class Project(val legs: List<Leg>, val totalS: Long, val machineS: Long, val userS: Long, val queueS: Long, val complete: Boolean)

    private val LABEL = mapOf("TOPICS_RUNNING" to "المواضيع", "SCRIPT_RUNNING" to "السيناريو",
        "PREVIEWING" to "التجهيز والمعاينة", "PRODUCING" to "التصيير والبوابات")

    /** From the 10 topics to the finished video: the cloud's time, the user's time between steps, and the cost. */
    fun project(local: LocalStore, slug: String, productionCost: Double? = null): Project? {
        val all = local.timings().filter { it.ok }.sortedBy { it.start }
        val prod = all.filter { it.slug == slug && it.stage in setOf("PREVIEWING", "PRODUCING") }
        if (prod.isEmpty()) return null
        val first = prod.first()
        val script = all.lastOrNull { it.stage == "SCRIPT_RUNNING" && it.end <= first.start && (it.slug == slug || it.slug.isBlank()) }
        val topics = script?.let { s -> all.lastOrNull { it.stage == "TOPICS_RUNNING" && it.end <= s.start } }
        val chain = listOfNotNull(topics, script) + prod
        val legs = mutableListOf<Leg>()
        chain.forEachIndexed { i, r ->
            if (i > 0) {
                val gap = (r.start - chain[i - 1].end) / 1000
                if (gap > 20) legs += Leg(if (r.stage == "PRODUCING" && chain[i - 1].stage == "PREVIEWING") "مراجعتك للمعاينة"
                    else if (r.stage == "SCRIPT_RUNNING") "اختيارك للموضوع" else "الصوت والصور (أنت)", gap, null, true)
            }
            val cost = if (r.stage in ONE_AI_JOB) r.costUsd else if (r === prod.last()) productionCost else null
            legs += Leg(LABEL[r.stage] ?: r.stage, r.totalS, cost, false, r.model)
        }
        val machine = legs.filterNot { it.byUser }.sumOf { it.seconds }
        val user = legs.filter { it.byUser }.sumOf { it.seconds }
        return Project(legs, (chain.last().end - chain.first().start) / 1000, machine, user, chain.sumOf { it.queueS }, topics != null && script != null)
    }

    /** How far the app's own time estimates were from what GitHub measured (mean error, %), last 10 runs. */
    fun accuracy(local: LocalStore): Pair<Int, Int>? {
        val l = local.timings().filter { it.ok && it.predictedS != null && it.totalS > 0 }.takeLast(10)
        if (l.size < 2) return null
        val err = l.map { abs(it.predictedS!! - it.totalS).toDouble() / it.totalS }.average()
        return (err * 100).toInt() to l.size
    }

    /** This week: finished videos, their average full time (topic to video), machine time, and the slowest step. */
    data class Week(val videos: Int, val avgTotalS: Long?, val avgMachineS: Long?, val avgUserS: Long?, val slowest: String?)

    fun week(local: LocalStore): Week {
        val since = System.currentTimeMillis() - 7L * 86_400_000
        val slugs = local.timings().filter { it.stage == "PRODUCING" && it.ok && it.end >= since }.map { it.slug }.distinct()
        val ps = slugs.mapNotNull { project(local, it) }.filter { it.complete }
        val legs = ps.flatMap { p -> p.legs.filterNot { it.byUser } }.groupBy { it.label }.mapValues { (_, v) -> v.map { it.seconds }.average() }
        return Week(slugs.size, ps.takeIf { it.isNotEmpty() }?.map { it.totalS }?.average()?.toLong(),
            ps.takeIf { it.isNotEmpty() }?.map { it.machineS }?.average()?.toLong(),
            ps.takeIf { it.isNotEmpty() }?.map { it.userS }?.average()?.toLong(),
            legs.maxByOrNull { it.value }?.let { "${it.key} (${Eta.dur(it.value.toLong())})" })
    }

    /** «Coarena left today buys about A to B videos», from the real full cost of the last videos. */
    fun videosLeft(local: LocalStore, left: Double?, latest: Pair<String, Double>? = null): String {
        // only videos whose AI ran on a paid model (free-model fallbacks cost cents and would promise far too many)
        val known = local.videos.value.mapNotNull { v -> v.costUsd?.takeIf { it >= PAID_VIDEO }?.let { v.slug to it } }
        val costs = (listOfNotNull(latest?.takeIf { it.second >= PAID_VIDEO }) + known).distinctBy { it.first }.take(5).map { it.second }
        if (left == null) return "رصيد Coarena غير معروف الآن."
        if (costs.isEmpty()) return "المتبقي في Coarena اليوم: %.2f$. لا توجد بعد تكلفة فيديو مكتمل لحساب العدد.".format(java.util.Locale.US, left)
        val lo = costs.minOrNull()!!; val hi = costs.maxOrNull()!!
        val a = (left / hi).toInt(); val b = (left / lo).toInt()
        val n = if (a == b) "نحو $a" else "بين $a و$b"
        return ("تكلفة الفيديو الكامل عندك: %s (آخر %d فيديو بنموذج مدفوع). المتبقي في Coarena اليوم %.2f$، يكفي %s فيديو.").format(java.util.Locale.US,
            if (lo == hi) "%.2f$".format(java.util.Locale.US, lo) else "%.2f$ إلى %.2f$".format(java.util.Locale.US, lo, hi), costs.size, left, n)
    }
}

internal fun JSONArray.timingList() = (0 until length()).mapNotNull { optJSONObject(it) }.map { TimingRec.from(it) }
