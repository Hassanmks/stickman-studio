package ma.stickman.studio.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import androidx.work.NetworkType
import ma.stickman.studio.R
import ma.stickman.studio.StudioApp
import ma.stickman.studio.data.VideoStat
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/** What the user set for the night production (cloud/state/night.json + the cron line of night.yml). */
data class NightPlan(val enabled: Boolean = false, val hour: Int = 7, val minute: Int = 0, val count: Int = 1,
                     val days: Set<Int> = (1..7).toSet(), val sound: Boolean = true, val voice: String = "male") {
    /** ~5 min of script per video (one after the other) + ~15 min for all the productions at once + room for the topics and
     *  GitHub's own delay on scheduled runs: the run starts that long before the ready time. */
    val leadMinutes get() = 45 + 6 * count   // 1.1.3: the scripts one after the other, the productions all at once

    /** The cron line in UTC for night.yml, from the phone's own clock rules for Morocco. */
    fun cron(): String {
        val zone = ZoneId.of("Africa/Casablanca")
        val ready = LocalDate.now(zone).atTime(LocalTime.of(hour, minute)).atZone(zone)
        var start = ready.minusMinutes(leadMinutes.toLong()).withZoneSameInstant(ZoneOffset.UTC)
        if (start.minute % 15 == 0) start = start.plusMinutes(3)   // the busy :00/:15 marks start late on GitHub
        return "${start.minute} ${start.hour} * * *"
    }

    fun json(last: JSONObject?): JSONObject = JSONObject().put("enabled", enabled).put("hour", hour).put("minute", minute)
        .put("count", count).put("days", JSONArray(days.sorted())).put("voice", voice).put("sound", sound)
        .put("ready", "%02d:%02d".format(hour, minute))
        .put("utc_offset_min", java.time.ZonedDateTime.now(ZoneId.of("Africa/Casablanca")).offset.totalSeconds / 60).apply { if (last != null) put("last", last) }

    companion object {
        fun from(o: JSONObject?): NightPlan {
            if (o == null) return NightPlan()
            val d = o.optJSONArray("days")
            return NightPlan(o.optBoolean("enabled"), o.optInt("hour", 7), o.optInt("minute", 0), o.optInt("count", 1).coerceIn(1, 3),
                d?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: (1..7).toSet(), o.optBoolean("sound", true),
                o.optString("voice", "male"))
        }
    }
}

/** One video of the last night (night.json "last"). */
data class NightVideo(val slug: String, val title: String, val ok: Boolean)
data class NightLast(val date: String, val runId: Long, val videos: List<NightVideo>, val finished: String) {
    companion object {
        fun from(o: JSONObject?): NightLast? = o?.let {
            val a = it.optJSONArray("videos") ?: JSONArray()
            NightLast(it.optString("date"), it.optLong("run_id"), (0 until a.length()).map { i -> a.getJSONObject(i) }
                .map { v -> NightVideo(v.optString("slug"), v.optString("title"), v.optBoolean("ok")) }, it.optString("finished"))
        }
    }
}

/**
 * At the ready time: brings the night's videos to the phone (gallery), deletes them from GitHub, files them in
 * «ready to publish», and rings (alarm sound when the user chose it). If the night run is not finished yet it
 * looks again every 15 minutes, for up to 3 hours.
 */
class NightWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as StudioApp
        val studio = app.studio
        val tries = inputData.getInt("try", 0)
        val text = runCatching { studio.gh.text("cloud/state/night.json") }.getOrNull()
        val o = text?.let { runCatching { JSONObject(it) }.getOrNull() }
        val plan = NightPlan.from(o)
        val last = NightLast.from(o?.optJSONObject("last"))
        val today = LocalDate.now(ZoneId.of("Africa/Casablanca")).toString()
        if (last == null || last.date != today) {
            if (tries < 12 && plan.enabled) later(applicationContext, 15, tries + 1) else schedule(applicationContext, plan)
            return Result.success()
        }
        var got = 0
        for (v in last.videos.filter { it.ok }) {
            if (studio.localVideo(v.slug) != null) { got++; continue }
            val ok = runCatching { studio.download(last.runId, v.slug, {}) }.getOrDefault(false)
            if (ok) {
                got++
                val r = studio.localResult(v.slug)
                studio.local.upsertVideo(VideoStat(v.slug, r?.title?.ifBlank { null } ?: v.title, "history", today,
                    r?.durationS ?: 0.0, r?.totalCost, r?.matchRate, null))
                studio.local.addToPublish(v.slug)
            }
        }
        val failed = last.videos.count { !it.ok }
        val msg = when {
            got == 0 && failed > 0 -> "لم يكتمل أي فيديو الليلة ($failed توقّف عند بوابة جودة). افتح التطبيق لترى السبب."
            got == 0 -> "لم يُنتج شيء الليلة."
            else -> "$got ${if (got == 1) "فيديو جاهز" else "فيديوهات جاهزة"} للنشر في معرضك" + (if (failed > 0) "، و$failed لم يجتز بوابات الجودة." else ".")
        }
        ring(applicationContext, "صباح الخير ☀️ الإنتاج الليلي", msg, plan.sound)
        schedule(applicationContext, plan)
        return Result.success()
    }

    companion object {
        const val CHANNEL_ALARM = "night_alarm"
        private const val WORK = "night-fetch"

        /** Next fetch at the ready time (today if still ahead, else tomorrow). */
        fun schedule(ctx: Context, plan: NightPlan) {
            val wm = WorkManager.getInstance(ctx)
            if (!plan.enabled) { wm.cancelUniqueWork(WORK); return }
            val zone = ZoneId.of("Africa/Casablanca")
            val now = LocalDateTime.now(zone)
            var t = now.toLocalDate().atTime(LocalTime.of(plan.hour, plan.minute))
            if (t.isBefore(now.plusMinutes(1))) t = t.plusDays(1)
            val delay = t.atZone(zone).toInstant().toEpochMilli() - System.currentTimeMillis()
            enqueue(ctx, delay, 0)
        }

        private fun later(ctx: Context, minutes: Long, tries: Int) = enqueue(ctx, minutes * 60_000, tries)

        private fun enqueue(ctx: Context, delayMs: Long, tries: Int) {
            val req = OneTimeWorkRequestBuilder<NightWorker>().setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(androidx.work.workDataOf("try" to tries))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, req)
        }

        /** The morning notification: with the alarm sound (it rings even in "silent at night" focus only if allowed). */
        fun ring(ctx: Context, title: String, text: String, loud: Boolean) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            val channel = if (loud) CHANNEL_ALARM else StudioApp.CHANNEL
            if (loud && nm.getNotificationChannel(CHANNEL_ALARM) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL_ALARM, "منبّه الإنتاج الليلي", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "صوت منبّه في الصباح حين تكون فيديوهات الليل جاهزة"
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    enableVibration(true)
                })
            }
            val open = android.app.PendingIntent.getActivity(ctx, 7, android.content.Intent(ctx, ma.stickman.studio.MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK), android.app.PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(ctx, channel).setSmallIcon(R.drawable.ic_stat_studio).setContentTitle(title)
                .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true).setContentIntent(open).build()
            runCatching { NotificationManagerCompat.from(ctx).notify(9, n) }
        }
    }
}
