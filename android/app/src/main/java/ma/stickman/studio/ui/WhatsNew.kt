@file:OptIn(ExperimentalMaterial3Api::class)

package ma.stickman.studio.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ma.stickman.studio.BuildConfigInfo
import ma.stickman.studio.ui.theme.ReplyStyle
import org.json.JSONObject

// ============================================================================================
// 1.2.0 «ما الجديد»: read from assets/changelog.json (no list hidden in the code any more), shown once after an
// update as a bottom sheet; every item can take you straight to the feature («جرّبه الآن»); older versions fold
// below, and the coach's newest lesson closes the sheet.
// ============================================================================================

data class ChangeItem(val icon: String, val title: String, val text: String, val action: String, val label: String)
data class ChangeVersion(val version: String, val date: String, val title: String, val items: List<ChangeItem>)

fun readChangelog(ctx: Context): List<ChangeVersion> = runCatching {
    val o = JSONObject(ctx.assets.open("changelog.json").bufferedReader().use { it.readText() })
    val vs = o.getJSONArray("versions")
    (0 until vs.length()).map { vs.getJSONObject(it) }.map { v ->
        val it2 = v.getJSONArray("items")
        ChangeVersion(v.optString("version"), v.optString("date"), v.optString("title"), (0 until it2.length()).map { it2.getJSONObject(it) }.map { x ->
            ChangeItem(x.optString("icon"), x.optString("title"), x.optString("text"), x.optString("action"), x.optString("label"))
        })
    }
}.getOrElse { emptyList() }

@Composable
fun WhatsNewSheet(vm: AppViewModel, goTab: (Int) -> Unit, done: () -> Unit) {
    val ctx = LocalContext.current
    val all = remember { readChangelog(ctx) }
    val current = all.firstOrNull { it.version == BuildConfigInfo.VERSION } ?: all.firstOrNull()
    var archive by remember { mutableStateOf(false) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun act(a: String) {
        done()
        when (a) {
            "chat" -> { goTab(0); vm.newChat() }
            "projects" -> goTab(1)
            "settings" -> goTab(2)
            "coach" -> vm.showCoach = true
        }
    }
    ModalBottomSheet(onDismissRequest = done, sheetState = state, containerColor = MaterialTheme.colorScheme.background) {
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SparkMark(26.dp, active = true); Spacer(Modifier.width(10.dp))
                    Column {
                        Text("الجديد في ${current?.version ?: BuildConfigInfo.VERSION}", style = MaterialTheme.typography.headlineSmall)
                        current?.title?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            current?.items?.forEachIndexed { i, it -> item { ChangeRow(it, Modifier.stagger(i)) { act(it.action) } } }
            vm.coach?.lessons?.firstOrNull()?.let { l -> item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp)) {
                    Text("ما تعلّمه المدرّب هذا الأسبوع", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(l.text, style = ReplyStyle.copy(color = MaterialTheme.colorScheme.onSurface))
                }
            } }
            item { GoldButton("رائع، لنبدأ") { done() } }
            val older = all.filter { it.version != current?.version }
            if (older.isNotEmpty()) {
                item {
                    Text(if (archive) "أخفِ الإصدارات السابقة" else "كل الإصدارات السابقة (${older.size})", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.clip(RoundedCornerShape(8.dp)).pressable { archive = !archive }.padding(6.dp))
                }
                if (archive) older.forEach { v ->
                    item { Text("${v.version} · ${v.title}", style = MaterialTheme.typography.titleSmall) }
                    v.items.forEach { it -> item { ChangeRow(it.copy(action = ""), Modifier) {} } }
                }
            }
        }
    }
}

@Composable
private fun ChangeRow(it: ChangeItem, modifier: Modifier, onTry: () -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            FxIcon(it.icon, 22.dp, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(it.title, style = MaterialTheme.typography.titleSmall)
            Text(it.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (it.action.isNotBlank() && it.label.isNotBlank())
                Text("${it.label} ←", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).pressable(onClick = onTry).padding(vertical = 4.dp, horizontal = 2.dp))
        }
    }
}
