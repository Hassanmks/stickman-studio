package ma.stickman.studio.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import ma.stickman.studio.work.ShotWatch

// 1.3.4: asks once for the photo permission the TikTok screenshot reader needs, then starts waiting for screenshots.
fun shotsGranted(ctx: Context) = ShotWatch.granted(ctx)

@Composable
fun rememberShotsPermission(vm: AppViewModel): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        vm.armShots()
        if (!ok) vm.toastShots()
    }
    return { launcher.launch(ShotWatch.permission) }
}
