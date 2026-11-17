package ma.stickman.studio.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import ma.stickman.studio.StudioApp
import ma.stickman.studio.data.Job
import ma.stickman.studio.data.PauseInfo
import ma.stickman.studio.data.Stage
import ma.stickman.studio.data.Studio
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * 1.3.0 «أوقف مؤقتًا واستأنف»: a step that stopped with a checkpoint (the user's pause, or today's Coarena money
 * ran out) waits in Stage.PAUSED. Resuming re-runs the cancelled jobs of the SAME run: the agent reads its
 * checkpoint (an artifact of that run) and continues from the turn where it stopped; finished jobs are kept.
 */
object PauseLogic {
    /** The next moment Coarena's daily money comes back (00:00 UTC), plus two minutes. */
    fun nextReset(): ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC).toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).plusMinutes(2)

    fun markPaused(ctx: Context, studio: Studio, job: Job, p: PauseInfo) {
        val why = (if (p.reason == "low_balance") "نفد رصيد Coarena اليوم" else "أوقفتَه مؤقتًا") +
            (if (p.turn > 0) " · عند الخطوة ${p.turn}" else "")
        studio.prefs.setJob(job.copy(stage = Stage.PAUSED, failedFrom = job.stage, error = why))
        if (p.reason == "low_balance" && studio.prefs.settings.value.autoResume) scheduleAtReset(ctx)
    }

    fun scheduleAtReset(ctx: Context) {
        val wait = (nextReset().toInstant().toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(60_000)
        val req = OneTimeWorkRequestBuilder<ResumeWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(wait, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("resume", ExistingWorkPolicy.REPLACE, req)
        studio(ctx).prefs.setResumeAt(System.currentTimeMillis() + wait)
    }

    fun cancelScheduled(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork("resume")
        studio(ctx).prefs.setResumeAt(0)
    }

    private fun studio(ctx: Context) = (ctx.applicationContext as StudioApp).studio

    /** Clear the pause, optionally switch the model, re-run the stopped jobs and watch again. */
    suspend fun resume(ctx: Context, studio: Studio, job: Job, model: String? = null): Boolean {
        if (job.runId <= 0) return false
        studio.control(job.runId, model = model, pause = false)
        val ok = runCatching { studio.gh.rerunFailed(job.runId) }.recoverCatching { studio.gh.rerunAll(job.runId) }.isSuccess
        if (!ok) return false
        // the run reads "completed" for a moment after the request: wait until it is queued again
        for (i in 0 until 10) { delay(2000); if (runCatching { !studio.gh.run(job.runId).done }.getOrDefault(false)) break }
        studio.prefs.setJob(job.copy(stage = job.failedFrom, error = "", startedAt = System.currentTimeMillis()))
        cancelScheduled(ctx)
        WatchWorker.enqueue(ctx, delaySec = 20)
        return true
    }
}

/** Continues a step that paused because today's Coarena money ran out, right after the daily reset. */
class ResumeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val studio = (applicationContext as StudioApp).studio
        val job = studio.prefs.job.value
        if (job.stage != Stage.PAUSED) return Result.success()
        return if (PauseLogic.resume(applicationContext, studio, job)) {
            Notes.ready(applicationContext, "استُؤنف العمل" to "تجدّد رصيد Coarena، فأكمل من حيث توقف: ${job.title.ifBlank { job.slug }}")
            Result.success()
        } else Result.retry()
    }
}
