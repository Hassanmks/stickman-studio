package ma.stickman.studio.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

// ============================================================================================
// 1.3.5 «رحلة الفيديو»: one clock and one bill for a whole video, from the moment the topics are asked for to the
// finished video. Every cloud step is its own GitHub run, and the live money figure («ماذا يفعل الآن») starts
// again from zero in each of them (even inside one production run, each AI part writes its own). The journey
// keeps the list of runs of this video and the highest figure seen for each part, then prefers the spend ledger
// (cloud/state/ledger, one exact file per paid run) once it has the run.
// ============================================================================================

/** [start]/[end] = phone clock (ms); end = 0 while the video is not finished. [seen] = "run:part" → highest $ seen live. */
data class Journey(val start: Long = 0, val end: Long = 0, val slug: String = "", val title: String = "",
                   val runs: List<Long> = emptyList(), val seen: Map<String, Double> = emptyMap()) {
    val active get() = start > 0

    fun toJson() = JSONObject().put("start", start).put("end", end).put("slug", slug).put("title", title)
        .put("runs", JSONArray(runs)).put("seen", JSONObject(seen as Map<*, *>))

    companion object {
        fun from(s: String?): Journey = runCatching {
            val o = JSONObject(s!!)
            val r = o.optJSONArray("runs") ?: JSONArray()
            val se = o.optJSONObject("seen") ?: JSONObject()
            Journey(o.optLong("start"), o.optLong("end"), o.optString("slug"), o.optString("title"),
                (0 until r.length()).map { r.getLong(it) }, se.keys().asSequence().associateWith { se.optDouble(it) })
        }.getOrElse { Journey() }
    }
}

class JourneyStore(private val sp: SharedPreferences) {
    private val _j = MutableStateFlow(Journey.from(sp.getString("journey", null)))
    val journey: StateFlow<Journey> = _j

    @Synchronized private fun put(j: Journey) {
        if (j == _j.value) return
        _j.value = j; sp.edit().putString("journey", j.toJson().toString()).apply()
    }

    /** The next run starts a journey of its own. */
    fun reset() = put(Journey())

    /** Every change of the job: a new run joins the journey; asking for topics (or a script after a finished video,
     *  e.g. the next part) starts a new one; the video being ready closes it. */
    fun onJob(old: Job, new: Job) {
        var j = _j.value
        val now = System.currentTimeMillis()
        if (new.runId > 0 && new.runId != old.runId && new.runId !in j.runs) {
            val fresh = new.stage == Stage.TOPICS_RUNNING || !j.active ||
                (new.stage == Stage.SCRIPT_RUNNING && old.stage in setOf(Stage.IDLE, Stage.DONE))
            j = if (fresh) Journey(start = if (new.startedAt > 0) new.startedAt else now, runs = listOf(new.runId))
                else j.copy(runs = j.runs + new.runId, end = 0)
        }
        if (j.active && new.slug.isNotBlank() && new.slug != j.slug) j = j.copy(slug = new.slug)
        if (j.active && new.title.isNotBlank() && new.title != j.title) j = j.copy(title = new.title)
        if (j.active && new.stage == Stage.DONE && j.end == 0L) j = j.copy(end = now)
        put(j)
    }

    /** The live figure of the step running now («ماذا يفعل الآن»), kept per run and per AI part. */
    fun onLive(l: LiveStatus) {
        val j = _j.value
        if (!j.active || l.runId !in j.runs) return
        val k = "${l.runId}:${l.step}"
        if (l.costUsd > (j.seen[k] ?: 0.0)) put(j.copy(seen = j.seen + (k to l.costUsd)))
    }
}

object JourneyMath {
    data class Leg(val label: String, val seconds: Long?, val cost: Double?, val model: String, val running: Boolean)
    data class Total(val elapsedS: Long, val machineS: Long, val userS: Long, val cost: Double, val legs: List<Leg>, val exact: Boolean)

    private val LABEL = mapOf("TOPICS_RUNNING" to "المواضيع", "SCRIPT_RUNNING" to "السيناريو",
        "PREVIEWING" to "التجهيز والمعاينة", "PRODUCING" to "التصيير والإنهاء")
    private val STEP = mapOf("topics" to "المواضيع", "script" to "السيناريو", "produce" to "الإنتاج")

    /** The whole video so far: time (now − start, or the end), the cloud's share, and the money of every run. */
    fun total(j: Journey, timings: List<TimingRec>, ledger: List<SpendEntry>, job: Job, now: Long = System.currentTimeMillis()): Total? {
        if (!j.active) return null
        val legs = j.runs.map { run ->
            val t = timings.firstOrNull { it.runId == run }
            val led = ledger.firstOrNull { it.run == run }
            val live = j.seen.filterKeys { it.startsWith("$run:") }.values.sum().takeIf { it > 0 }
            val running = run == job.runId && t == null
            val label = t?.let { LABEL[it.stage] } ?: led?.let { STEP[it.step] }
                ?: if (running) LABEL[job.stage.name] ?: "الخطوة الحالية" else "خطوة"
            val secs = t?.totalS ?: if (running && job.startedAt > 0) (now - job.startedAt) / 1000 else null
            Leg(label, secs, led?.cost ?: live ?: t?.costUsd, t?.model.orEmpty().ifBlank { led?.byModel?.maxByOrNull { it.value }?.key.orEmpty() }, running)
        }
        val elapsed = (((if (j.end > 0) j.end else now) - j.start) / 1000).coerceAtLeast(0)
        val machine = legs.sumOf { it.seconds ?: 0 }.coerceAtMost(elapsed)
        val exact = j.runs.all { r -> ledger.any { it.run == r } || timings.none { it.runId == r && it.paid } }
        return Total(elapsed, machine, elapsed - machine, legs.sumOf { it.cost ?: 0.0 }, legs, exact)
    }

    /** What a whole video usually costs here (topics + script + production), from the ledger. */
    fun usual(ledger: List<SpendEntry>): Double? {
        val v = Budget.videos(ledger).map { it.second }.sorted()
        val mid = if (v.isEmpty()) return null else v[v.size / 2]
        return mid + (Budget.stepCost(ledger, "topics") ?: 0.0)
    }
}
