package ma.stickman.studio.data

import org.json.JSONObject

// ============================================================================================
// 1.2.0 «المدرّب»: what the cloud coach (cloud/coach.py) learned, read from cloud/state/coach.json.
// Every video gets a score (0-100) from fixed criteria; the published ones also bring their TikTok numbers;
// the coach turns both into lessons (with their evidence) and picks the next experiment.
// ============================================================================================

data class CoachScore(val slug: String, val title: String, val date: String, val score: Double, val weakest: List<String>)

data class Lesson(val text: String, val evidence: String, val strength: String)

data class Experiment(val variable: String, val value: String, val why: String)

data class CoachInfo(
    val updated: String, val scores: List<CoachScore>, val avgLast5: Double?, val avgPrev5: Double?,
    val lessons: List<Lesson>, val next: Experiment?, val review: String, val withStats: Int,
) {
    /** Change of the average score of the last 5 videos against the 5 before them, in percent. */
    val trendPct: Double? get() = if (avgLast5 != null && avgPrev5 != null && avgPrev5 > 0) (avgLast5 - avgPrev5) / avgPrev5 * 100 else null

    companion object {
        fun parse(s: String): CoachInfo {
            val o = JSONObject(s)
            val sc = o.optJSONArray("scores")
            val ls = o.optJSONArray("lessons")
            fun num(k: String) = if (o.isNull(k)) null else o.optDouble(k).takeIf { !it.isNaN() }
            return CoachInfo(
                o.optString("updated"),
                (0 until (sc?.length() ?: 0)).map { sc!!.getJSONObject(it) }.map { x ->
                    val w = x.optJSONArray("weakest")
                    CoachScore(x.optString("slug"), x.optString("title"), x.optString("date"), x.optDouble("score", 0.0),
                        (0 until (w?.length() ?: 0)).map { w!!.getString(it) })
                },
                num("avg_last5"), num("avg_prev5"),
                (0 until (ls?.length() ?: 0)).map { ls!!.getJSONObject(it) }.map { Lesson(it.optString("text"), it.optString("evidence"), it.optString("strength")) },
                o.optJSONObject("next_experiment")?.let { Experiment(it.optString("variable"), it.optString("value"), it.optString("why")) },
                o.optString("review"), o.optInt("with_stats"),
            )
        }
    }
}
