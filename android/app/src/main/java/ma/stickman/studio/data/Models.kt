package ma.stickman.studio.data

import org.json.JSONArray
import org.json.JSONObject

data class Topic(
    val n: Int, val axis: String, val title: String, val hook: String, val whyNow: String,
    val realPhotos: String, val sensitivity: String, val slug: String,
    val hookScore: Int = 0, val similarTo: String = "",
    /** 1.1.2: a big topic told in 2-3 parts (each a full story); partTitles = the title of every part. */
    val parts: Int = 1, val partTitles: List<String> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().put("n", n).put("axis", axis).put("title", title).put("hook", hook)
        .put("why_now", whyNow).put("real_photos", realPhotos).put("sensitivity", sensitivity).put("slug", slug)
        .put("hook_score", hookScore).put("similar_to", similarTo)
        .put("parts", parts).put("part_titles", JSONArray(partTitles))

    /** Real photos look available (the agent writes it in words). */
    val photosOk get() = realPhotos.isNotBlank() && !Regex("لا تتوفر|غير متوفر|نادرة|قليلة|لا توجد").containsMatchIn(realPhotos)
    val sensitive get() = sensitivity.isNotBlank() && !Regex("^\\s*(لا شيء|لا|منخفضة|ضعيفة)").containsMatchIn(sensitivity)
}

data class TopicsFile(val date: String, val topics: List<Topic>, val notes: String)

data class Handoff(
    val slug: String, val title: String, val voiceName: String, val voiceText: String,
    val imagePrompts: List<String>, val chatgptImages: Int, val realPhotos: List<String>,
    val fluentImages: List<String>, val checks: String, val notes: String,
)

data class Seo(val description: String, val pinned: String, val cover: String, val keywords: String, val bestTime: String)

data class VideoResult(val slug: String, val title: String, val durationS: Double, val sync: String, val seo: Seo, val notes: String,
                       val matchRate: Double? = null, val costUsd: Double? = null, val costTotal: Double? = null,
                       val costBy: Map<String, Double> = emptyMap(), val scriptMinutes: Double? = null,
                       val timeline: List<ShotTime> = emptyList(), val series: SeriesInfo? = null) {
    /** What the whole video cost (script + production), or the production alone for older runs. */
    val totalCost get() = costTotal ?: costUsd
}

/** A part of a series (projects/<slug>/series.json): which part, how many, the titles, the slug of part 1. */
data class SeriesInfo(val base: String, val title: String, val part: Int, val parts: Int, val partTitles: List<String>) {
    val hasNext get() = part < parts
    val nextTitle get() = partTitles.getOrNull(part)?.takeIf { it.isNotBlank() } ?: "الجزء ${part + 1}"
    val nextSlug get() = "$base-p${part + 1}"
}

/** One shot of the rendered video with its real times (result.json "timeline"), for the player. */
data class ShotTime(val t0: Double, val t1: Double, val scene: Int, val shot: Int, val text: String)

/** One scene of script.json, for the time line and the scene reader. */
data class SceneInfo(val narration: String, val keywords: List<String>, val shots: Int, val words: Int, val startS: Double)

data class ScriptInfo(val scenes: List<SceneInfo>, val words: Int, val shots: Int, val estSeconds: Int, val images: Int)

/** What the preparation shows before rendering (mode prepare): the numbered stills and the agent's notes. */
data class PreviewInfo(val sheet: java.io.File?, val checks: List<java.io.File>, val notes: String, val checksLine: String,
                       val index: SheetIndex? = null)

/** Where every still sits on the preview sheet (cloud/prep.py writes _preview_index.json). */
data class SheetIndex(val cols: Int, val w: Int, val h: Int, val gap: Int, val n: Int)

data class Step(val name: String, val status: String, val conclusion: String?, val startedAt: String = "", val completedAt: String = "")

data class RunInfo(
    val id: Long, val workflow: String, val status: String, val conclusion: String?, val url: String,
    val createdAt: String, val steps: List<Step> = emptyList(), val title: String = "", val updatedAt: String = "",
    /** 1.2.5: when the first GitHub machine began (null while the run still waits in the queue). */
    val workStartedAt: String? = null,
) {
    val done get() = status == "completed"
    val ok get() = conclusion == "success"
}

/** Live progress the cloud agent writes on the branch `status` (live.json). */
data class LiveStatus(val runId: Long, val step: String, val turn: Int, val model: String, val costUsd: Double, val updated: Long,
                      val events: List<LiveEvent>, val plan: List<PlanItem> = emptyList(), val summary: String = "", val note: String = "",
                      // 1.3.0: the user's choice the agent is using, dollars per model in this step, and a pause
                      val choice: String = "", val byModel: Map<String, Double> = emptyMap(), val paused: PauseInfo? = null)

/** Why and where a step paused (cloud/agent.py writes it). reason: manual · low_balance. */
data class PauseInfo(val reason: String, val turn: Int, val why: String, val at: Long)

/** One line of the live log (1.1.4): kind = its icon; detail = the command / path (monospace when opened);
 *  result = one-line outcome; ok = null while unknown; dur in seconds; thumb = a tiny JPEG (base64). */
data class LiveEvent(val t: Long, val text: String, val kind: String = "", val detail: String = "", val result: String = "",
                     val ok: Boolean? = null, val dur: Double? = null, val thumb: String = "")

data class PlanItem(val text: String, val status: String)

data class LogEntry(val date: String, val slug: String, val title: String)

data class Credit(val label: String, val usage: Double, val limit: Double?, val remaining: Double?, val freeTier: Boolean)

data class ModelInfo(val id: String, val name: String, val inPrice: Double, val outPrice: Double, val free: Boolean)

data class Release(val tag: String, val version: String, val notes: String, val assetUrl: String, val size: Long)

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }

object Parse {
    fun topic(t: JSONObject, i: Int = 0) = Topic(t.optInt("n", i + 1), t.optString("axis"), t.optString("title"), t.optString("hook"),
        t.optString("why_now"), t.optString("real_photos"), t.optString("sensitivity"), t.optString("slug"),
        t.optInt("hook_score", 0).coerceIn(0, 5), t.optString("similar_to"),
        t.optInt("parts", 1).coerceIn(1, 3), t.optJSONArray("part_titles").strings())

    fun series(o: JSONObject?): SeriesInfo? = o?.takeIf { it.optInt("parts", 1) > 1 }?.let {
        SeriesInfo(it.optString("series"), it.optString("title"), it.optInt("part", 1), it.optInt("parts", 1), it.optJSONArray("part_titles").strings())
    }

    fun topics(s: String): TopicsFile {
        val o = JSONObject(s)
        val arr = o.optJSONArray("topics") ?: JSONArray()
        return TopicsFile(o.optString("date"), (0 until arr.length()).map { topic(arr.getJSONObject(it), it) }, o.optString("notes"))
    }

    fun handoff(s: String): Handoff {
        val o = JSONObject(s)
        return Handoff(
            o.optString("slug"), o.optString("title"), o.optString("voice_name", "Enzo"), o.optString("voice_text"),
            o.optJSONArray("image_prompts").strings(), o.optInt("chatgpt_images"), o.optJSONArray("real_photos").strings(),
            o.optJSONArray("fluent_images").strings(), o.optString("checks"), o.optString("notes"),
        )
    }

    fun result(s: String): VideoResult {
        val o = JSONObject(s)
        val seo = o.optJSONObject("seo") ?: JSONObject()
        val st = o.optJSONObject("stats") ?: JSONObject()
        fun num(k: String) = if (st.has(k) && !st.isNull(k)) st.optDouble(k).takeIf { !it.isNaN() } else null
        val by = st.optJSONObject("cost_by")?.let { b -> b.keys().asSequence().associateWith { b.optDouble(it) } } ?: emptyMap()
        return VideoResult(
            o.optString("slug"), o.optString("title"), o.optDouble("duration_s", 0.0), o.optString("sync"),
            Seo(seo.optString("description"), seo.optString("pinned_comment"), seo.optString("cover_text"),
                seo.optString("keywords"), seo.optString("best_time")),
            o.optString("notes"), num("match_rate"), num("cost_usd"), num("cost_total_usd"), by, num("script_minutes"),
            o.optJSONArray("timeline")?.let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { x ->
                ShotTime(x.optDouble("t0"), x.optDouble("t1"), x.optInt("scene"), x.optInt("shot"), x.optString("text")) } } } ?: emptyList(),
            series(o.optJSONObject("series")),
        )
    }

    /** script.json -> scenes with their spoken length estimated at 2.5 words a second (before the voice exists). */
    fun script(s: String): ScriptInfo {
        val o = JSONObject(s)
        val arr = o.optJSONArray("scenes") ?: JSONArray()
        var t = 0.0
        var images = 0
        val scenes = (0 until arr.length()).map { i ->
            val sc = arr.getJSONObject(i)
            val text = sc.optString("narration")
            val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
            val shots = sc.optJSONArray("shots")
            if (shots != null) for (k in 0 until shots.length()) {
                val v = shots.optJSONObject(k)?.optJSONObject("visual") ?: continue
                images += if (v.optString("type") == "group") (v.optJSONArray("items")?.length() ?: 0) else if (v.optString("type") in setOf("emoji", "map")) 1 else 0
            }
            SceneInfo(text, sc.optJSONArray("keywords").strings(), shots?.length() ?: 1, words, t).also { t += words / 2.5 + 0.35 }
        }
        return ScriptInfo(scenes, scenes.sumOf { it.words }, scenes.sumOf { it.shots }, t.toInt(), images)
    }

    fun live(s: String): LiveStatus {
        val o = JSONObject(s)
        val ev = o.optJSONArray("events") ?: JSONArray()
        val plan = o.optJSONArray("plan") ?: JSONArray()
        return LiveStatus(o.optLong("run_id"), o.optString("step"), o.optInt("turn"), o.optString("model"), o.optDouble("cost_usd", 0.0),
            o.optLong("updated"), (0 until ev.length()).map { ev.getJSONObject(it).let { e ->
                LiveEvent(e.optLong("t"), e.optString("text"), e.optString("kind"), e.optString("detail"), e.optString("result"),
                    if (e.has("ok")) e.optBoolean("ok") else null, if (e.has("dur")) e.optDouble("dur") else null, e.optString("thumb"))
            } },
            (0 until plan.length()).mapNotNull { plan.optJSONObject(it) }.map { PlanItem(it.optString("text"), it.optString("status", "todo")) },
            o.optString("summary"), o.optString("note"), o.optString("choice"),
            o.optJSONObject("by_model")?.let { bm -> bm.keys().asSequence().associateWith { bm.optDouble(it, 0.0) } } ?: emptyMap(),
            o.optJSONObject("paused")?.let { p -> PauseInfo(p.optString("reason"), p.optInt("turn"), p.optString("why"), p.optLong("at")) })
    }

    /** `- 2026-11-12 | slug | title` lines of videos/LOG.md, newest first. */
    fun log(md: String): List<LogEntry> = md.lines().mapNotNull { line ->
        val p = line.removePrefix("-").split("|").map { it.trim() }
        if (line.trimStart().startsWith("-") && p.size >= 3 && p[0].matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) LogEntry(p[0], p[1], p[2]) else null
    }.reversed()
}
