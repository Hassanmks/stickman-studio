package ma.stickman.studio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import ma.stickman.studio.madar.Kit
import ma.stickman.studio.madar.MadarApp
import ma.stickman.studio.madar.Store
import ma.stickman.studio.ui.theme.StudioTheme

/**
 * TaleSpark 2.0 «مَدار» (2026-11-17): one screen, the whole path of a map film: topic -> Gemini writes the script and
 * the shot plan -> the check -> the voice from ElevenLabs -> (a ChatGPT sheet for a new moving object) -> the PC makes
 * the film -> the gallery. Same package and signing key as 1.3.8, so it installs over it.
 */
class MainActivity : ComponentActivity() {
    private lateinit var store: Store
    private var shared by mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = Store(this); val kit = Kit(this)
        handle(intent)
        ma.stickman.studio.ui.Sfx.init(this)
        setContent {
            // the look of TaleSpark 1.x: its theme, sounds and haptics (ui/Fx.kt, ui/Components.kt)
            StudioTheme(store.theme, false) {
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
                    store.theme == "light" || (store.theme == "system" && !androidx.compose.foundation.isSystemInDarkTheme())
                val view = androidx.compose.ui.platform.LocalView.current
                val fb = androidx.compose.runtime.remember(store.haptics, store.sounds) { ma.stickman.studio.ui.Feedback(view, store.haptics, store.sounds) }
                androidx.compose.runtime.CompositionLocalProvider(ma.stickman.studio.ui.LocalFeedback provides fb,
                    androidx.compose.material3.LocalContentColor provides androidx.compose.material3.MaterialTheme.colorScheme.onBackground) {
                    MadarApp(store, kit, shared) { shared = emptyList() }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }

    /** talespark://pair?... pairs this phone with the PC; files shared to TaleSpark are the voice (audio) or a sheet (image) */
    private fun handle(i: Intent?) {
        i ?: return
        val d = i.data
        if (i.action == Intent.ACTION_VIEW && d != null && d.scheme == "talespark") {
            Toast.makeText(this, if (store.pair(d)) "اقترن الهاتف بالحاسوب" else "رابط الاقتران غير صالح", Toast.LENGTH_LONG).show(); return
        }
        shared = when (i.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
            else -> emptyList()
        }
    }
}
