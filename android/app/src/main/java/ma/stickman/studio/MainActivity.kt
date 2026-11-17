package ma.stickman.studio

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as SysSettings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import ma.stickman.studio.ui.AppShell
import ma.stickman.studio.ui.AppViewModel
import ma.stickman.studio.ui.Feedback
import ma.stickman.studio.ui.LocalFeedback
import ma.stickman.studio.ui.LockScreen
import ma.stickman.studio.ui.ProvideTilt
import ma.stickman.studio.ui.theme.StudioTheme

object BuildConfigInfo { val VERSION: String = BuildConfig.VERSION_NAME }

class MainActivity : FragmentActivity() {
    private val vm: AppViewModel by viewModels()
    /** Locked until the fingerprint / face is checked (only when the user turned the lock on). */
    private var locked by mutableStateOf(false)
    private var lastStop = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        useHighestRefreshRate()
        if (Build.VERSION.SDK_INT >= 33) registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
            .launch(Manifest.permission.POST_NOTIFICATIONS)
        handleShare(intent)
        if (BuildConfig.DEBUG) intent?.getStringExtra("demo")?.let { st ->
            fun asset(n: String) = runCatching { assets.open("demo/$n").bufferedReader().readText() }.getOrNull()
            vm.demo(ma.stickman.studio.data.Stage.valueOf(st), asset("topics.json"), asset("current.json"), asset("result.json"))
        }
        locked = vm.settings.value.lock && savedInstanceState == null && !vm.demo
        setContent {
            val s by vm.settings.collectAsState()
            // animations turned off in the phone's developer / accessibility settings count as "reduce motion"
            val systemNoMotion = remember {
                SysSettings.Global.getFloat(contentResolver, SysSettings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }
            StudioTheme(s.theme, s.reduceMotion || systemNoMotion, s.highContrast, s.fontScale) {
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = s.theme == "light"
                val view = LocalView.current
                val fb = remember(s.haptics, s.sounds) { Feedback(view, s.haptics, s.sounds) }
                CompositionLocalProvider(LocalFeedback provides fb) {
                    ProvideTilt(s.tilt && !s.reduceMotion && !systemNoMotion) {
                        // text outside a Surface takes this colour (a transparent Scaffold would give black)
                        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides androidx.compose.material3.MaterialTheme.colorScheme.onBackground) {
                            Box(Modifier.fillMaxSize()) {
                                if (ma.stickman.studio.ui.PlayerHub.pip) ma.stickman.studio.ui.PipVideo()
                                else if (locked) LockScreen { unlock() } else AppShell(vm)
                            }
                        }
                    }
                }
            }
        }
        if (locked) unlock()
    }

    /** Fingerprint, face or the phone's PIN. Without any of them on the phone, the lock cannot work: open. */
    private fun unlock() {
        val auth = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) { locked = false; return }
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { locked = false }
        }).authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("افتح TaleSpark")
            .setSubtitle("بالبصمة أو الوجه أو رمز الهاتف").setAllowedAuthenticators(auth).build())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    override fun onStop() { super.onStop(); lastStop = System.currentTimeMillis() }

    /** Picture in picture: only the video is drawn while the window is small (ui/VideoPlayer.kt). */
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        ma.stickman.studio.ui.PlayerHub.pip = isInPictureInPictureMode
        if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            ma.stickman.studio.ui.PlayerHub.player?.pause()   // the small window was closed
        }
    }

    override fun onResume() {
        super.onResume()
        // lock again after 2 minutes in the background
        if (vm.settings.value.lock && !locked && lastStop > 0 && System.currentTimeMillis() - lastStop > 120_000) { locked = true; unlock() }
        vm.refresh()
    }

    /** Files shared from AI Studio, ChatGPT or the file manager go straight to the "files" list. */
    private fun handleShare(i: Intent?) {
        i ?: return
        val uris: List<Uri> = when (i.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        uris.forEach { u -> runCatching { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        vm.addFiles(uris)
        i.action = null
    }

    /** Ask the display for its fastest mode (90/120/144 Hz) so animations stay perfectly smooth. */
    private fun useHighestRefreshRate() {
        val d = if (Build.VERSION.SDK_INT >= 30) display else @Suppress("DEPRECATION") windowManager.defaultDisplay
        val best = d?.supportedModes?.filter { it.physicalWidth == d.mode.physicalWidth && it.physicalHeight == d.mode.physicalHeight }
            ?.maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.also { it.preferredDisplayModeId = best.modeId }
        window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
    }
}
