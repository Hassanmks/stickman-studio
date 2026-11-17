package ma.stickman.studio.work

import android.Manifest
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ma.stickman.studio.StudioApp
import ma.stickman.studio.data.Claude
import ma.stickman.studio.data.Insights
import java.util.concurrent.TimeUnit

// ============================================================================================
// 1.3.4 «أرقام تيك توك بلقطة واحدة». The phone (Redmi / HyperOS and most Android skins) names every screenshot
// after the app it was taken in: Screenshot_2026-10-30-18-24-55-969_com.zhiliaoapp.musically.png. So the studio
// wakes up when a new picture lands (a WorkManager content trigger, no service running), looks ONLY at TikTok
// screenshots, ONLY while a published video waits for its numbers, reads them with the same reader as the manual
// photo, matches the video by itself and says the verdict in a notification. Other pictures are never opened.
// ============================================================================================
class ShotWatch(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        try { look(applicationContext) } catch (_: Throwable) { } finally { arm(applicationContext, after = true) }
        return Result.success()
    }

    companion object {
        private const val NAME = "tiktok-shots"
        val TIKTOK = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
        private val PERMISSION = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        val permission: String get() = PERMISSION

        fun granted(ctx: Context) = ContextCompat.checkSelfPermission(ctx, PERMISSION) == PackageManager.PERMISSION_GRANTED

        /** Waits for the next new picture (or stops waiting when the setting is off / the permission is missing). */
        fun arm(ctx: Context, after: Boolean = false) {
            val studio = (ctx.applicationContext as StudioApp).studio
            val wm = WorkManager.getInstance(ctx)
            if (!studio.prefs.settings.value.autoStats || !granted(ctx)) { wm.cancelUniqueWork(NAME); return }
            // the first time: only screenshots taken from now on (never the old ones)
            if (studio.prefs.shotSeen == 0L) studio.prefs.setShotSeen(System.currentTimeMillis() / 1000)
            val c = Constraints.Builder()
                .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
                .setTriggerContentUpdateDelay(4, TimeUnit.SECONDS).setTriggerContentMaxDelay(40, TimeUnit.SECONDS).build()
            val req = OneTimeWorkRequestBuilder<ShotWatch>().setConstraints(c).build()
            // from inside the worker: queue the next wait after this one; from the app: keep a wait already armed
            wm.enqueueUniqueWork(NAME, if (after) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP, req)
        }

        /** TikTok itself (to take the analytics screenshot), or null when it is not installed. */
        fun openTikTok(ctx: Context): PendingIntent? {
            val i = TIKTOK.firstNotNullOfOrNull { ctx.packageManager.getLaunchIntentForPackage(it) } ?: return null
            return PendingIntent.getActivity(ctx, 7, i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private suspend fun look(ctx: Context) {
            val studio = (ctx.applicationContext as StudioApp).studio
            val seen = studio.prefs.shotSeen
            val shots = mutableListOf<Pair<Long, Long>>()  // id, date added
            var newest = seen
            val sel = "${MediaStore.Images.Media.DATE_ADDED} > ? AND ${MediaStore.Images.Media.DISPLAY_NAME} LIKE 'Screenshot%' AND (" +
                TIKTOK.joinToString(" OR ") { "${MediaStore.Images.Media.DISPLAY_NAME} LIKE '%$it%'" } + ")"
            ctx.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED), sel, arrayOf(seen.toString()),
                "${MediaStore.Images.Media.DATE_ADDED} ASC")?.use { c ->
                while (c.moveToNext()) { shots += c.getLong(0) to c.getLong(1); newest = maxOf(newest, c.getLong(1)) }
            }
            if (shots.isEmpty()) return
            studio.prefs.setShotSeen(newest)
            val videos = studio.local.videos.value
            if (!Insights.waiting(videos)) return          // nothing waits for numbers: the screenshots are not opened
            val p = studio.prefs
            val keys = Claude.Keys(p.coarenaKey, p.geminiKey, p.openRouterKey)
            var saved = 0
            for ((id, _) in shots.takeLast(4)) {
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                val b64 = Claude.imageB64(ctx, uri) ?: continue
                val j = runCatching { Claude.readStats(keys, b64) }.getOrNull() ?: continue
                if (!j.optBoolean("is_analytics", false)) continue
                if (listOf("views", "likes", "avg_watch_s", "completion_pct").all { j.isNull(it) || !j.has(it) }) continue
                val v = Insights.match(studio.local.videos.value, j)
                if (v == null) {
                    Notes.ready(ctx, "أي فيديو هذا؟ 🤔" to "قرأتُ لقطة تحليلات من تيك توك، ولم أعرف لأي فيديو هي. افتح «المشاريع» واختره، ثم أرسل اللقطة نفسها.", Notes.REMIND + 3)
                    continue
                }
                studio.local.setStats(v.slug, j)
                saved++
                val now = studio.local.video(v.slug) ?: v
                Notes.ready(ctx, "أرقام «${v.title}» 📈" to Insights.verdict(now, studio.local.videos.value) + ". تعلّم منها المدرّب.", Notes.REMIND + 4)
            }
            if (saved > 0 && p.githubToken.isNotBlank()) runCatching { studio.pushPerformance() }
        }
    }
}
