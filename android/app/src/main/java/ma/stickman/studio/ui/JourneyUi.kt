@file:OptIn(ExperimentalMaterial3Api::class)

package ma.stickman.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ma.stickman.studio.data.Eta
import ma.stickman.studio.data.JourneyMath
import ma.stickman.studio.ui.theme.Gold
import java.util.Locale

// ============================================================================================
// 1.3.5 «رحلة الفيديو»: one line that never starts again from zero — the time since the topics were asked for and
// the money of this whole video so far — with every step in a sheet. Its own one-second clock lives here, so
// only this line redraws each second (not the screen around it).
// ============================================================================================

private fun usd(v: Double) = "%.2f$".format(Locale.US, v)

@Composable
fun JourneyLine(vm: AppViewModel, modifier: Modifier = Modifier, compact: Boolean = false) {
    val j by vm.journey.collectAsState()
    val job by vm.job.collectAsState()
    if (!j.active) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(j.end) { if (j.end == 0L) while (true) { delay(1000); now = System.currentTimeMillis() } }
    val t = remember(j, job, vm.ledger, vm.timingsVersion, now / 5000) { JourneyMath.total(j, vm.local.timings(), vm.ledger, job, now) } ?: return
    // the live figure of the step running now is added at once (it is kept in the journey on the next poll)
    var open by remember { mutableStateOf(false) }
    val elapsed = if (j.end > 0) t.elapsedS else ((now - j.start) / 1000).coerceAtLeast(0)
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
        .pressable { open = true }.padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 9.dp), verticalAlignment = Alignment.CenterVertically) {
        FxIcon("stopwatch", 16.dp); Spacer(Modifier.width(6.dp))
        Text((if (j.end > 0) "المدة الكاملة " else "منذ البداية ") + Eta.dur(elapsed), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        FxIcon("coin", 16.dp); Spacer(Modifier.width(6.dp))
        Text("هذا الفيديو " + usd(t.cost), style = MaterialTheme.typography.labelLarge, color = Gold)
    }
    if (open) JourneySheet(vm, t, elapsed, j.title.ifBlank { job.title }) { open = false }
}

@Composable
private fun JourneySheet(vm: AppViewModel, t: JourneyMath.Total, elapsed: Long, title: String, dismiss: () -> Unit) {
    val usual = remember(vm.ledger) { JourneyMath.usual(vm.ledger) }
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = MaterialTheme.colorScheme.background) {
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("رحلة هذا الفيديو", style = MaterialTheme.typography.headlineSmall)
                if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(Eta.dur(elapsed), "منذ طلب المواضيع", "calendar", Modifier.weight(1f))
                    StatTile(Eta.dur(t.machineS), "وقت السحابة", "gear", Modifier.weight(1f))
                    StatTile(usd(t.cost), "المصروف كله", "coin", Modifier.weight(1f), Gold)
                }
            }
            item { Text("كل خطوة", style = MaterialTheme.typography.titleMedium) }
            t.legs.forEach { l -> item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.label + if (l.running) " (تعمل الآن)" else "", style = MaterialTheme.typography.bodyMedium,
                            color = if (l.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        if (l.model.isNotBlank()) Text(l.model.substringAfter('/'), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    l.seconds?.let { Text(Eta.dur(it) + "   ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(l.cost?.let { usd(it) } ?: "—", style = MaterialTheme.typography.titleSmall, color = Gold)
                }
            } }
            item {
                Text("وقتك أنت بين الخطوات (الاختيار والصوت والصور والمراجعة): " + Eta.dur(t.userS), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                usual?.let { u ->
                    val r = if (u > 0) t.cost / u else 1.0
                    Text("متوسط فيديوهاتك كاملة: ${usd(u)}" + when { t.cost == 0.0 -> ""; r > 1.15 -> "، وهذا الفيديو أغلى حتى الآن"; r < 0.85 -> "، وهذا الفيديو أرخص حتى الآن"; else -> "، وهذا الفيديو في المعدل" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (t.exact) "المبالغ من سجل الإنفاق (مقاسة من حساب المزوّد)." else "المبالغ حية من الخطوات، وتُثبَّت من سجل الإنفاق بعد كل خطوة.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
