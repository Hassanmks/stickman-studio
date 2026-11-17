package ma.stickman.studio.data

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

// ============================================================================================
// 1.3.4 «أرقام تيك توك بلقطة واحدة»: which published video a TikTok analytics screenshot belongs to, a one-line
// verdict on its numbers against the channel's own average, and the smart suggestions on the home screen.
// Everything here only reads the phone's own list of videos (LocalStore); nothing leaves the phone from here.
// ============================================================================================
object Insights {
    private val zone: ZoneId = ZoneId.of("Africa/Casablanca")

    /** Arabic words without diacritics, hamza forms or punctuation, for a loose comparison of a caption and a title. */
    private fun words(t: String): Set<String> = t.lowercase(Locale.ROOT)
        .replace(Regex("[\\u064B-\\u0652\\u0640]"), "")
        .replace(Regex("[أإآ]"), "ا").replace('ة', 'ه').replace('ى', 'ي')
        .split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }.toSet()

    private fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    /** The published videos a screenshot may belong to (newest first). */
    fun candidates(videos: List<VideoStat>, now: Long = System.currentTimeMillis()) =
        videos.filter { it.publishedAt > 0 && now - it.publishedAt < 60L * 86_400_000 }.sortedByDescending { it.publishedAt }

    /** True while the screenshot reader should look at TikTok screenshots at all. */
    fun waiting(videos: List<VideoStat>, now: Long = System.currentTimeMillis()) =
        videos.any { it.publishedAt > 0 && (it.statsAt == 0L || now - it.publishedAt < 14L * 86_400_000) }

    /**
     * Which video the numbers belong to: the caption against the title and the hook, the post date and the length;
     * when nothing tells them apart, the newest video still waiting for its numbers. Null = cannot tell.
     */
    fun match(videos: List<VideoStat>, read: JSONObject, hooks: Map<String, String> = emptyMap()): VideoStat? {
        val c = candidates(videos)
        if (c.isEmpty()) return null
        val cap = words(read.optString("caption").takeIf { it != "null" }.orEmpty())
        val posted = read.optString("posted").takeIf { it.length == 10 }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val dur = if (read.isNull("duration_s")) null else read.optDouble("duration_s").takeIf { !it.isNaN() && it > 0 }
        val scored = c.map { v ->
            var s = 0.0
            if (cap.isNotEmpty()) {
                val t = words(v.title) + words(hooks[v.slug].orEmpty())
                if (t.isNotEmpty()) s += 6.0 * cap.intersect(t).size / minOf(cap.size, t.size).coerceAtLeast(1)
            }
            if (posted != null && kotlin.math.abs(day(v.publishedAt).toEpochDay() - posted.toEpochDay()) <= 1) s += 2.0
            if (dur != null && v.durationS > 0 && kotlin.math.abs(v.durationS - dur) <= 3) s += 2.0
            v to s
        }.sortedByDescending { it.second }
        val best = scored.first()
        val second = scored.getOrNull(1)?.second ?: 0.0
        if (best.second >= 2.0 && best.second - second >= 1.0) return best.first
        // nothing on the image tells them apart: the only (or newest) video still waiting for its numbers
        return c.firstOrNull { it.statsAt == 0L } ?: if (c.size == 1) c.first() else null
    }

    private fun median(l: List<Double>) = l.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }

    fun views(n: Long): String = when {
        n >= 1_000_000 -> "%.1f مليون".format(Locale.US, n / 1_000_000.0).replace(".0 ", " ")
        n >= 10_000 -> "${n / 1000} ألف"
        n >= 1000 -> "%.1f ألف".format(Locale.US, n / 1000.0).replace(".0 ", " ")
        else -> "$n"
    }

    private fun times(r: Double) = when {
        r >= 2.5 -> "%.0f مرات".format(Locale.US, r)
        r >= 1.85 -> "مرتين"
        r >= 1.35 -> "مرة ونصف"
        else -> "%.0f%%".format(Locale.US, (r - 1) * 100)
    }

    /** "أكمله 41%، أفضل من متوسطك بمرة ونصف" — against the other published videos (completion first, then views). */
    fun verdict(v: VideoStat, all: List<VideoStat>): String {
        val others = all.filter { it.slug != v.slug && it.hasStats }
        val parts = mutableListOf<String>()
        if (v.views > 0) parts += "${views(v.views)} مشاهدة"
        v.completionPct?.let { parts += "أكمله %.0f%%".format(Locale.US, it) }
        v.dropS?.let { parts += "يغادر أغلبهم عند الثانية %.0f".format(Locale.US, it) }
        val head = parts.joinToString("، ").ifBlank { "حُفظت أرقامه" }
        val avgC = median(others.mapNotNull { it.completionPct })
        val avgV = median(others.filter { it.views > 0 }.map { it.views.toDouble() })
        val cmp = when {
            v.completionPct != null && avgC != null && avgC > 0 -> v.completionPct / avgC
            v.views > 0 && avgV != null && avgV > 0 -> v.views / avgV
            else -> null
        }
        val tail = when {
            cmp == null -> if (others.isEmpty()) "أول فيديو يتعلم منه المدرّب" else ""
            cmp >= 1.1 -> "أفضل من متوسطك بـ${times(cmp)}"
            cmp <= 0.9 -> "أقل من متوسطك بـ%.0f%%".format(Locale.US, (1 - cmp) * 100)
            else -> "في مستوى متوسطك"
        }
        return listOf(head, tail).filter { it.isNotBlank() }.joinToString("، ")
    }

    /** A suggestion on the home screen: send [text] to the assistant, or (open = "tiktok") open TikTok. */
    data class Suggestion(val fx: String, val text: String, val open: String = "")

    /**
     * Up to three suggestions that come from the channel's own numbers (the static ones fill the rest):
     * a video waiting for its numbers, a video that beat the average (a part 2?), one that lost viewers early,
     * and a gap in publishing.
     */
    fun suggestions(videos: List<VideoStat>, now: Long = System.currentTimeMillis()): List<Suggestion> {
        val out = mutableListOf<Suggestion>()
        val pub = videos.filter { it.publishedAt > 0 }.sortedByDescending { it.publishedAt }
        pub.firstOrNull { it.statsAt == 0L && now - it.publishedAt > 24 * 3_600_000L }?.let {
            out += Suggestion("chart", "أرقام «${it.title}» لم تصل: افتح تيك توك وخذ لقطة", "tiktok")
        }
        val withStats = pub.filter { it.views > 0 }
        val med = median(withStats.map { it.views.toDouble() })
        if (withStats.size >= 3 && med != null && med > 0) {
            withStats.take(6).maxByOrNull { it.views }?.takeIf { it.views >= 1.5 * med }?.let {
                out += Suggestion("sparkles", "«${it.title}» تفوّق على متوسطك: هل نصنع جزءًا ثانيًا؟")
            }
        }
        val medC = median(pub.mapNotNull { it.completionPct })
        pub.firstOrNull { it.completionPct != null }?.let { last ->
            if (medC != null && pub.count { it.completionPct != null } >= 3 && last.completionPct!! < 0.85 * medC)
                out += Suggestion("bulb", "«${last.title}» فقد مشاهديه مبكرًا: كيف أقوّي الخطّاف القادم؟")
        }
        pub.firstOrNull()?.let { last ->
            val days = (now - last.publishedAt) / 86_400_000
            if (days >= 3) out += Suggestion("sparkles", "لم أنشر منذ $days أيام: ما أفضل موضوع الآن حسب أرقامي؟")
        }
        return out.take(3)
    }
}
