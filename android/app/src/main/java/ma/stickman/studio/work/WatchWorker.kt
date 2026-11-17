package ma.stickman.studio.work

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ma.stickman.studio.MainActivity
import ma.stickman.studio.R
import ma.stickman.studio.StudioApp
import ma.stickman.studio.data.Stage
import ma.stickman.studio.widget.StatusWidget
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Notifications of the app: the live progress (with a cancel button) and the "ready" messages. */
object Notes {
    const val PROGRESS = 2
    const val DONE = 1
    const val REMIND = 3

    private fun allowed(ctx: Context) = (ctx.applicationContext as StudioApp).studio.prefs.settings.value.notifications &&
        (android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    private fun open(ctx: Context) = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Ongoing notification with a progress bar, the current step and a "cancel" button. */
    fun progress(ctx: Context, title: String, step: String, done: Int, total: Int, eta: String) {
        if (!allowed(ctx)) return
        val cancel = PendingIntent.getBroadcast(ctx, 5, Intent(ctx, CancelReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, StudioApp.CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_studio).setContentTitle(title).setContentText(step)
            .setSubText(eta).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setProgress(total.coerceAtLeast(1), done.coerceAtMost(total), total == 0)
            .setContentIntent(open(ctx)).addAction(0, "إلغاء", cancel)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(PROGRESS, n) }
    }

    fun clearProgress(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(PROGRESS)

    fun ready(ctx: Context, t: Pair<String, String>, id: Int = DONE, tap: PendingIntent? = null) {
        if (!allowed(ctx)) return
        val n = NotificationCompat.Builder(ctx, StudioApp.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_studio).setContentTitle(t.first).setContentText(t.second)
            .setStyle(NotificationCompat.BigTextStyle().bigText(t.second))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(tap ?: open(ctx)).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }
}

/** "Cancel" on the progress notification: stops the GitHub run without opening the app. */
class CancelReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        val studio = (ctx.applicationContext as StudioApp).studio
        CoroutineScope(Dispatchers.IO).launch {
            try {
                studio.prefs.job.value.runId.takeIf { it > 0 }?.let { studio.gh.cancel(it) }
                Notes.clearProgress(ctx)
                Notes.ready(ctx, "أُلغي التشغيل" to "يمكنك إعادة المحاولة من التطبيق.")
            } finally { pending.finish() }
        }
    }
}

/** Follows the running cloud step while the app is closed: live notification, then the state and a message. */
class WatchWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val studio = (applicationContext as StudioApp).studio
        val job = studio.prefs.job.value
        if (job.runId == 0L || job.stage !in RUNNING) { Notes.clearProgress(applicationContext); return Result.success() }
        val end = System.currentTimeMillis() + 8 * 60_000          // stay under the 10 min worker limit
        val title = when (job.stage) {
            Stage.TOPICS_RUNNING -> "يقترح 10 مواضيع"; Stage.SCRIPT_RUNNING -> "يكتب السيناريو"
            Stage.PREVIEWING -> "يجهّز المعاينة"; else -> "ينتج الفيديو"
        } + (job.title.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
        while (System.currentTimeMillis() < end) {
            val run = runCatching { studio.gh.run(job.runId) }.getOrNull()
            if (run != null && !run.done) {
                val steps = run.steps.filterNot { ma.stickman.studio.ui.StepNames.hidden(it.name) }
                val cur = steps.firstOrNull { it.status == "in_progress" }?.name?.let { ma.stickman.studio.ui.StepNames.label(it) } ?: "في الطابور"
                val ls = if (cur.contains("الذكاء")) studio.live(job.runId) else null
                val live = ls?.events?.lastOrNull { it.kind != "think" }?.text
                // 1.2.5: the same honest estimate as the app screen (GitHub's clock, measured runs of the same kind)
                val e = ma.stickman.studio.data.Timing.estimate(studio.local, job.stage.name, run, ls?.model, plan = ls?.plan.orEmpty())
                val eta = "${ma.stickman.studio.data.Eta.dur(e.elapsedS)} · ${e.line()}"
                Notes.progress(applicationContext, title, live ?: cur, steps.count { it.status == "completed" }, steps.size, eta)
            }
            if (run != null && run.done) {
                Notes.clearProgress(applicationContext)
                val current = studio.prefs.job.value
                if (current.runId != job.runId) return Result.success()   // the app already handled it
                if (current.stage == Stage.PAUSED) return Result.success()   // 1.3.0: paused from the app (no checkpoint needed)
                runCatching { ma.stickman.studio.data.Timing.record(studio.local, job, run, studio.live(job.runId)) }
                if (run.ok) {
                    val next = when (job.stage) {
                        Stage.TOPICS_RUNNING -> Stage.TOPICS_READY
                        Stage.SCRIPT_RUNNING -> Stage.SCRIPT_READY
                        Stage.PREVIEWING -> Stage.PREVIEW_READY
                        else -> Stage.DONE
                    }
                    studio.prefs.setJob(current.copy(stage = next))
                    Notes.ready(applicationContext, when (next) {
                        Stage.TOPICS_READY -> "المواضيع العشرة جاهزة" to "افتح التطبيق واختر موضوعًا."
                        Stage.SCRIPT_READY -> "السيناريو جاهز" to "انسخ نص الصوت وأمر الصور، ثم أرسل الملفات."
                        Stage.PREVIEW_READY -> "المعاينة جاهزة" to "راجع اللقطات ووافق ليبدأ التصيير" + (ma.stickman.studio.data.Timing.usual(studio.local, "PRODUCING", true)?.let { " (المعتاد نحو ${ma.stickman.studio.data.Eta.dur(it)})" } ?: "") + "."
                        else -> "الفيديو جاهز 🎬" to (current.title.ifBlank { current.slug })
                    })
                } else if (run.conclusion == "cancelled" && studio.live(job.runId)?.paused != null) {
                    // 1.3.0: stopped with a checkpoint, not given up
                    PauseLogic.markPaused(applicationContext, studio, current, studio.live(job.runId)!!.paused!!)
                    Notes.ready(applicationContext, "توقّف مؤقتًا" to (studio.prefs.job.value.error + ". افتح التطبيق لتستأنف."))
                } else {
                    val step = run.steps.firstOrNull { it.conclusion == "failure" }?.name?.let { ma.stickman.studio.ui.StepNames.label(it) }
                    studio.prefs.setJob(current.copy(stage = Stage.FAILED, failedFrom = job.stage,
                        error = if (run.conclusion == "cancelled") "أُلغي التشغيل."
                                else "توقّف التشغيل على GitHub" + (step?.let { " عند: $it" } ?: ".")))
                    Notes.ready(applicationContext, "تعذّر الإكمال" to "افتح التطبيق لرؤية السبب وإعادة المحاولة.")
                }
                StatusWidget.update(applicationContext)
                return Result.success()
            }
            StatusWidget.update(applicationContext)
            delay(20_000)
        }
        enqueue(applicationContext, chain = true)        // still running: keep watching
        return Result.success()
    }

    companion object {
        val RUNNING = setOf(Stage.TOPICS_RUNNING, Stage.SCRIPT_RUNNING, Stage.PREVIEWING, Stage.PRODUCING)
        fun enqueue(ctx: Context, delaySec: Long = 0, chain: Boolean = false) {
            val req = OneTimeWorkRequestBuilder<WatchWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(delaySec, TimeUnit.SECONDS).build()
            // From inside the worker, append (replacing would cancel the worker that is enqueuing).
            WorkManager.getInstance(ctx).enqueueUniqueWork("watch",
                if (chain) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE, req)
        }
    }
}

/** Sends the files saved while there was no internet, as soon as it comes back. */
class UploadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val studio = (applicationContext as StudioApp).studio
        val d = studio.local.draft ?: return Result.success()
        val uris = d.files.map { java.io.File(it) }.filter { it.exists() }.map { android.net.Uri.fromFile(it) }
        if (uris.isEmpty()) { studio.local.draft = null; return Result.success() }
        return try {
            studio.uploadAndProduce(d.slug, d.title, uris, d.extra, d.previewFirst) { _, _ -> }
            studio.local.draft = null
            studio.clearImported()
            WatchWorker.enqueue(applicationContext)
            Notes.ready(applicationContext, "عاد الاتصال وأُرسلت الملفات" to "بدأ الإنتاج: ${d.title}")
            Result.success()
        } catch (e: Exception) { if (runAttemptCount < 5) Result.retry() else Result.failure() }
    }

    companion object {
        fun enqueue(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("upload", ExistingWorkPolicy.REPLACE, req)
        }
    }
}

/** Publishing plan (1.1.2): at the chosen hour, a nudge on days that have no new video yet, with how far the week
 *  is from its goal. Runs once a day (WorkManager keeps it across reboots). */
class DailyNudge(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as StudioApp
        val s = app.studio.prefs.settings.value
        if (!s.dailyNudge) return Result.success()
        val today = java.time.LocalDate.now(ZoneId.of("Africa/Casablanca"))
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val dates = app.studio.local.videos.value.map { it.date.take(10) }
        if (today.toString() in dates) return Result.success()
        val week = dates.count { it >= monday.toString() && it <= today.toString() }
        val left = (s.weeklyGoal - week).coerceAtLeast(0)
        val daysLeft = 8 - today.dayOfWeek.value
        Notes.ready(applicationContext, "خطة النشر 📅" to (if (left == 0) "بلغت هدف الأسبوع ($week). فيديو إضافي اليوم يقوّي القناة."
            else "بقي $left من هدف ${s.weeklyGoal} هذا الأسبوع، و$daysLeft أيام. اقترح مواضيع اليوم؟"), Notes.REMIND + 1)
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context, on: Boolean, hour: Int) {
            val wm = WorkManager.getInstance(ctx)
            if (!on) { wm.cancelUniqueWork("nudge"); return }
            val zone = ZoneId.of("Africa/Casablanca")
            val now = LocalDateTime.now(zone)
            var t = now.toLocalDate().atTime(LocalTime.of(hour, 0))
            if (t.isBefore(now.plusMinutes(1))) t = t.plusDays(1)
            val delay = t.atZone(zone).toInstant().toEpochMilli() - System.currentTimeMillis()
            val req = androidx.work.PeriodicWorkRequestBuilder<DailyNudge>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
            wm.enqueueUniquePeriodicWork("nudge", androidx.work.ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}

/** A reminder at the best time to post (read from the SEO pack, e.g. "20:00" or "بين 19:00 و21:00"). */
class Reminder(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Notes.ready(applicationContext, "حان أفضل وقت للنشر ⏰" to (inputData.getString("title") ?: "انشر فيديوك الآن."), Notes.REMIND)
        return Result.success()
    }

    companion object {
        /** The first hh:mm in the text, today or tomorrow, Moroccan time. */
        fun time(best: String): LocalDateTime? {
            val m = Regex("(\\d{1,2})[:h.](\\d{2})").find(best.replace('٫', ':')) ?: Regex("(\\d{1,2})\\s*(?:ساعة|h)").find(best)
            val h = m?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..23 } ?: return null
            val min = m.groupValues.getOrNull(2)?.toIntOrNull()?.takeIf { it in 0..59 } ?: 0
            val zone = ZoneId.of("Africa/Casablanca")
            val now = LocalDateTime.now(zone)
            var t = now.toLocalDate().atTime(LocalTime.of(h, min))
            if (t.isBefore(now.plusMinutes(5))) t = t.plusDays(1)
            return t
        }

        fun schedule(ctx: Context, best: String, title: String) {
            val t = time(best) ?: return
            val zone = ZoneId.of("Africa/Casablanca")
            val delayMs = t.atZone(zone).toInstant().toEpochMilli() - System.currentTimeMillis()
            val req = OneTimeWorkRequestBuilder<Reminder>().setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("title" to "انشر الآن: $title")).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("remind", ExistingWorkPolicy.REPLACE, req)
        }
    }
}

/** 1.2.0: two days after a video is published, ask for its TikTok numbers (the coach learns from them). */
class StatsReminder(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val slug = inputData.getString("slug") ?: return Result.success()
        val ctx = applicationContext
        val studio = (ctx as StudioApp).studio
        val v = studio.local.video(slug) ?: return Result.success()
        if (v.hasStats) return Result.success()
        // 1.3.4: one notification for all the videos still waiting, and a tap opens TikTok itself when the studio
        // reads the screenshot by itself (otherwise the projects screen, as before)
        val waiting = studio.local.videos.value.filter { it.waitingStats }
        val auto = studio.prefs.settings.value.autoStats && ShotWatch.granted(ctx)
        val tiktok = if (auto) ShotWatch.openTikTok(ctx) else null
        val title = if (waiting.size > 1) "${waiting.size} فيديوهات تنتظر أرقامها 📈" else "كيف أدى «${v.title}»؟ 📈"
        val body = if (tiktok != null) "اضغط هنا فيُفتح تيك توك. افتح تحليلات الفيديو وخذ لقطة شاشة، وأنا أقرؤها وحدي." +
                (if (waiting.size > 1) " لقطة لكل فيديو." else "")
            else "أرسل لقطة شاشة لإحصاءاته في تيك توك من «المشاريع»، فيتعلّم المدرّب ما نجح."
        if (tiktok != null) ShotWatch.arm(ctx)
        Notes.ready(ctx, title to body, Notes.REMIND + 2, tiktok)
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context, slug: String, title: String) {
            val req = OneTimeWorkRequestBuilder<StatsReminder>().setInitialDelay(48, TimeUnit.HOURS)
                .setInputData(workDataOf("slug" to slug, "title" to title)).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("stats-$slug", ExistingWorkPolicy.REPLACE, req)
        }
    }
}
