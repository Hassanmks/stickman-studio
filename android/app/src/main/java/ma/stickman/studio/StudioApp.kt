package ma.stickman.studio

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import ma.stickman.studio.data.Prefs
import ma.stickman.studio.data.Studio
import ma.stickman.studio.ui.Sfx

class StudioApp : Application() {
    lateinit var studio: Studio
        private set

    override fun onCreate() {
        super.onCreate()
        studio = Studio(this, Prefs(this))
        Sfx.init(this)
        // 1.3.4: wait for TikTok analytics screenshots (does nothing when the setting is off or the permission missing)
        runCatching { ma.stickman.studio.work.ShotWatch.arm(this) }
        getSystemService(NotificationManager::class.java).apply {
            createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = "إشعار عند اكتمال المواضيع أو السيناريو أو الفيديو، وتذكير بوقت النشر"
            })
            createNotificationChannel(NotificationChannel(CHANNEL_PROGRESS, getString(R.string.channel_progress), NotificationManager.IMPORTANCE_LOW).apply {
                description = "شريط التقدّم أثناء العمل على السحابة، مع زر الإلغاء"
                setShowBadge(false)
            })
        }
    }

    companion object {
        const val CHANNEL = "production"
        const val CHANNEL_PROGRESS = "progress"
    }
}
