package ma.stickman.studio.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** A produced video as the phone remembers it: numbers of the production and (typed by the user) of TikTok. */
data class VideoStat(
    val slug: String, val title: String, val axis: String, val date: String,
    val durationS: Double = 0.0, val costUsd: Double? = null, val matchRate: Double? = null, val minutes: Double? = null,
    val views: Long = 0, val likes: Long = 0, val comments: Long = 0,
    // 1.2.0: the numbers the coach learns from, the coach's own score, and what the user marked
    val shares: Long = 0, val saves: Long = 0, val avgWatchS: Double? = null, val completionPct: Double? = null,
    val score: Double? = null, val starred: Boolean = false, val publishedAt: Long = 0, val statsAt: Long = 0,
    // 1.3.4: from the analytics screenshot: the second where most viewers leave, and the share from the For You feed
    val dropS: Double? = null, val fypPct: Double? = null,
) {
    /** Published but no TikTok numbers yet. */
    val waitingStats get() = publishedAt > 0 && statsAt == 0L && views == 0L
    val hasStats get() = views > 0 || statsAt > 0

    fun toJson() = JSONObject().put("slug", slug).put("title", title).put("axis", axis).put("date", date)
        .put("duration_s", durationS).put("cost", costUsd ?: JSONObject.NULL).put("match", matchRate ?: JSONObject.NULL)
        .put("minutes", minutes ?: JSONObject.NULL).put("views", views).put("likes", likes).put("comments", comments)
        .put("shares", shares).put("saves", saves).put("avg_watch_s", avgWatchS ?: JSONObject.NULL)
        .put("completion_pct", completionPct ?: JSONObject.NULL).put("score", score ?: JSONObject.NULL)
        .put("starred", starred).put("published_at", publishedAt).put("stats_at", statsAt)
        .put("drop_s", dropS ?: JSONObject.NULL).put("fyp_pct", fypPct ?: JSONObject.NULL)

    companion object {
        private fun JSONObject.num(k: String) = if (isNull(k)) null else optDouble(k).takeIf { !it.isNaN() }
        fun from(o: JSONObject) = VideoStat(o.optString("slug"), o.optString("title"), o.optString("axis"), o.optString("date"),
            o.optDouble("duration_s", 0.0), o.num("cost"), o.num("match"),
            o.num("minutes"), o.optLong("views"), o.optLong("likes"), o.optLong("comments"),
            o.optLong("shares"), o.optLong("saves"), o.num("avg_watch_s"), o.num("completion_pct"), o.num("score"),
            o.optBoolean("starred"), o.optLong("published_at"), o.optLong("stats_at"), o.num("drop_s"), o.num("fyp_pct"))
    }
}

/** Files waiting to be sent when the internet comes back. */
data class Draft(val slug: String, val title: String, val files: List<String>, val extra: String, val previewFirst: Boolean)

/**
 * Everything the app keeps for itself in one small JSON file (filesDir/local.json): the produced videos and
 * their numbers, how long each cloud step took (for honest time estimates), saved and hidden topics,
 * the topic of the running production, and a draft waiting for the network.
 */
class LocalStore(ctx: Context) {
    private val file = File(ctx.filesDir, "local.json")
    private var root: JSONObject = runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() }

    private val _videos = MutableStateFlow(readVideos())
    val videos: StateFlow<List<VideoStat>> = _videos
    private val _saved = MutableStateFlow(readSaved())
    val saved: StateFlow<List<Topic>> = _saved
    private val _hidden = MutableStateFlow(readHidden())
    val hidden: StateFlow<Set<String>> = _hidden

    // 1.3.5 (speed): written on a background thread, the newest content wins (data/DiskWriter.kt)
    private fun save() { DiskWriter.later(file) { synchronized(this) { root.toString() } } }

    // ---------- videos ----------
    private fun readVideos(): List<VideoStat> {
        val a = root.optJSONArray("videos") ?: return emptyList()
        return (0 until a.length()).map { VideoStat.from(a.getJSONObject(it)) }.sortedByDescending { it.date }
    }

    fun upsertVideo(v: VideoStat) {
        val list = readVideos().filterNot { it.slug == v.slug } + v
        root.put("videos", JSONArray(list.map { it.toJson() }))
        save(); _videos.value = readVideos()
    }

    fun video(slug: String) = _videos.value.firstOrNull { it.slug == slug }

    fun setPerformance(slug: String, views: Long, likes: Long, comments: Long) {
        video(slug)?.let { upsertVideo(it.copy(views = views, likes = likes, comments = comments)) }
    }

    /** All TikTok numbers at once (from a screenshot or typed); missing values keep what was there. */
    fun setStats(slug: String, s: JSONObject) {
        val v = video(slug) ?: return
        fun l(k: String, old: Long) = if (s.has(k) && !s.isNull(k)) s.optLong(k, old) else old
        fun d(k: String, old: Double?) = if (s.has(k) && !s.isNull(k)) s.optDouble(k).takeIf { !it.isNaN() } ?: old else old
        upsertVideo(v.copy(views = l("views", v.views), likes = l("likes", v.likes), comments = l("comments", v.comments),
            shares = l("shares", v.shares), saves = l("saves", v.saves), avgWatchS = d("avg_watch_s", v.avgWatchS),
            completionPct = d("completion_pct", v.completionPct), dropS = d("drop_s", v.dropS), fypPct = d("fyp_pct", v.fypPct), statsAt = System.currentTimeMillis(),
            publishedAt = if (v.publishedAt == 0L) System.currentTimeMillis() else v.publishedAt))
    }

    fun toggleStar(slug: String) { video(slug)?.let { upsertVideo(it.copy(starred = !it.starred)) } }
    fun markPublished(slug: String) { video(slug)?.let { if (it.publishedAt == 0L) upsertVideo(it.copy(publishedAt = System.currentTimeMillis())) } }
    fun setScore(slug: String, score: Double) { video(slug)?.let { if (it.score != score) upsertVideo(it.copy(score = score)) } }

    fun removeVideo(slug: String) {
        root.put("videos", JSONArray(readVideos().filterNot { it.slug == slug }.map { it.toJson() }))
        save(); _videos.value = readVideos()
    }

    /** Videos of the last 7 days, their total cost and average production time. */
    fun week(): Triple<Int, Double, Double?> {
        val since = LocalDate.now().minusDays(7).toString()
        val w = _videos.value.filter { it.date >= since }
        val mins = w.mapNotNull { it.minutes }
        return Triple(w.size, w.sumOf { it.costUsd ?: 0.0 }, mins.takeIf { it.isNotEmpty() }?.average())
    }

    // ---------- 1.2.5: every finished cloud run as GitHub measured it (data/Timing.kt) ----------
    @Volatile private var timingCache: List<TimingRec>? = null
    fun timings(): List<TimingRec> = timingCache ?: (root.optJSONArray("timings")?.timingList() ?: emptyList()).also { timingCache = it }
    @Synchronized fun addTiming(t: TimingRec) {
        val list = (timings().filterNot { it.runId == t.runId } + t).sortedBy { it.start }.takeLast(150)
        root.put("timings", JSONArray(list.map { it.toJson() })); timingCache = list; save()
    }

    fun averageCost(): Double? = _videos.value.mapNotNull { it.costUsd }.takeLast(10).takeIf { it.isNotEmpty() }?.average()

    // ---------- how long the cloud steps take (seconds, last 8 of each) ----------
    fun addDuration(stage: String, seconds: Long) {
        if (seconds < 20) return
        val d = root.optJSONObject("durations") ?: JSONObject().also { root.put("durations", it) }
        val a = d.optJSONArray(stage) ?: JSONArray()
        val list = ((0 until a.length()).map { a.getLong(it) } + seconds).takeLast(8)
        d.put(stage, JSONArray(list)); save()
    }

    /** Typical time of a step from the last runs (median), or null before the first one. */
    fun typical(stage: String): Long? {
        val a = root.optJSONObject("durations")?.optJSONArray(stage) ?: return null
        val list = (0 until a.length()).map { a.getLong(it) }.sorted()
        return list.getOrNull(list.size / 2)
    }

    // ---------- topics ----------
    private fun readSaved(): List<Topic> {
        val a = root.optJSONArray("saved") ?: return emptyList()
        return (0 until a.length()).map { Parse.topic(a.getJSONObject(it), it) }
    }

    fun toggleSaved(t: Topic): Boolean {
        val now = readSaved()
        val keep = now.none { it.slug == t.slug }
        val list = if (keep) now + t else now.filterNot { it.slug == t.slug }
        root.put("saved", JSONArray(list.map { it.toJson() })); save(); _saved.value = readSaved()
        return keep
    }

    private fun readHidden(): Set<String> {
        val a = root.optJSONArray("hidden") ?: return emptySet()
        return (0 until a.length()).map { a.getString(it) }.toSet()
    }

    fun hide(slug: String, on: Boolean = true) {
        val s = (if (on) readHidden() + slug else readHidden() - slug).toList().takeLast(200)
        root.put("hidden", JSONArray(s)); save(); _hidden.value = readHidden()
    }

    /** Axis of the topic now in production (history / event / other), to file the video under it later. */
    var currentAxis: String
        get() = root.optString("axis", "")
        set(v) { root.put("axis", v); save() }

    // ---------- draft for the network ----------
    var draft: Draft?
        get() = root.optJSONObject("draft")?.let { o ->
            val f = o.optJSONArray("files") ?: JSONArray()
            Draft(o.optString("slug"), o.optString("title"), (0 until f.length()).map { f.getString(it) }, o.optString("extra"), o.optBoolean("preview"))
        }
        set(d) {
            if (d == null) root.remove("draft") else root.put("draft", JSONObject().put("slug", d.slug).put("title", d.title)
                .put("files", JSONArray(d.files)).put("extra", d.extra).put("preview", d.previewFirst))
            save()
        }

    // ---------- ready to publish (1.1.3): night videos waiting for the one tap on TikTok ----------
    private val _publish = MutableStateFlow(readPublish())
    val publish: StateFlow<List<String>> = _publish
    private fun readPublish(): List<String> = root.optJSONArray("publish")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
    fun addToPublish(slug: String) {
        root.put("publish", JSONArray((readPublish() - slug) + slug)); save(); _publish.value = readPublish()
    }
    fun removeFromPublish(slug: String) {
        root.put("publish", JSONArray(readPublish() - slug)); save(); _publish.value = readPublish()
    }

    // ---------- backup ----------
    fun export(): JSONObject = JSONObject(root.toString()).apply { remove("draft") }
    fun import(o: JSONObject) {
        root = o; save()
        _videos.value = readVideos(); _saved.value = readSaved(); _hidden.value = readHidden()
    }
}
