package ma.stickman.studio.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import ma.stickman.studio.MainActivity
import ma.stickman.studio.R
import ma.stickman.studio.StudioApp
import ma.stickman.studio.data.Stage

/** Home-screen widget: what runs now in the cloud, or what waits for the user. Tap = open the app. */
class StatusWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = draw(ctx, mgr, ids)

    companion object {
        fun update(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, StatusWidget::class.java))
            if (ids.isNotEmpty()) draw(ctx, mgr, ids)
        }

        /** The sentence for each stage, also used by the "next step" card of the home screen. */
        fun line(stage: Stage, title: String): Pair<String, Int> = when (stage) {
            Stage.IDLE -> "اضغط لتبدأ فيديو جديدًا" to R.drawable.fx_sparkles
            Stage.PAUSED -> "متوقف مؤقتًا: اضغط لتستأنف" to R.drawable.fx_hourglass
            Stage.TOPICS_RUNNING -> "يبحث ويقترح 10 مواضيع…" to R.drawable.fx_magnifier
            Stage.TOPICS_READY -> "المواضيع جاهزة: اختر واحدًا" to R.drawable.fx_bulb
            Stage.SCRIPT_RUNNING -> "يكتب السيناريو: $title" to R.drawable.fx_scroll
            Stage.SCRIPT_READY, Stage.UPLOADING -> "ينتظر الصوت والصور منك" to R.drawable.fx_mic
            Stage.PREVIEWING -> "يجهّز المعاينة…" to R.drawable.fx_eyes
            Stage.PREVIEW_READY -> "المعاينة جاهزة: راجعها ووافق" to R.drawable.fx_eyes
            Stage.PRODUCING -> "ينتج الفيديو: $title" to R.drawable.fx_film
            Stage.DONE -> "الفيديو جاهز للنشر" to R.drawable.fx_party
            Stage.FAILED -> "لم يكتمل التشغيل: افتح لإعادة المحاولة" to R.drawable.fx_warning
        }

        private fun draw(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val job = (ctx.applicationContext as StudioApp).studio.prefs.job.value
            val (text, icon) = line(job.stage, job.title.ifBlank { job.slug })
            val open = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val v = RemoteViews(ctx.packageName, R.layout.widget_status).apply {
                setTextViewText(R.id.widget_text, text)
                setImageViewResource(R.id.widget_icon, icon)
                setOnClickPendingIntent(R.id.widget_root, open)
            }
            ids.forEach { mgr.updateAppWidget(it, v) }
        }
    }
}
