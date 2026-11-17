@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ma.stickman.studio.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ma.stickman.studio.data.PreviewInfo
import ma.stickman.studio.ui.theme.Gold
import ma.stickman.studio.ui.theme.Good

/** Cuts every numbered still out of the preview sheet (cloud/prep.py says where they are). */
private suspend fun stills(p: PreviewInfo): List<ImageBitmap> = withContext(Dispatchers.IO) {
    val idx = p.index ?: return@withContext emptyList()
    val f = p.sheet ?: return@withContext emptyList()
    val sheet: Bitmap = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 })
        ?: return@withContext emptyList()
    (0 until idx.n).mapNotNull { i ->
        val x = idx.gap + (i % idx.cols) * (idx.w + idx.gap)
        val y = idx.gap + (i / idx.cols) * (idx.h + idx.gap)
        if (x + idx.w > sheet.width || y + idx.h > sheet.height) null
        else Bitmap.createBitmap(sheet, x, y, idx.w, idx.h).asImageBitmap()
    }
}

/** Pinch to zoom and move a still; one finger still swipes the pager while it is not zoomed. */
private fun Modifier.pinchZoom(scale: () -> Float, set: (Float, Offset) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val e = awaitPointerEvent()
            val multi = e.changes.size > 1
            if (multi || scale() > 1.01f) {
                set((scale() * e.calculateZoom()).coerceIn(1f, 4f), e.calculatePan())
                e.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (e.changes.any { it.pressed })
    }
}

@Composable
fun PreviewView(vm: AppViewModel) {
    val job by vm.job.collectAsState()
    val ctx = LocalContext.current
    val p = vm.preview
    var note by rememberSaveable { mutableStateOf("") }
    if (p == null) {
        LaunchedEffect(job.runId) { vm.refresh() }
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Busy("ينزّل المعاينة…")
            Gap(16)
            GhostButton("صيّر دون معاينة", fx = "film") { vm.approvePreview() }
        }
        return
    }
    val images by produceState(emptyList<ImageBitmap>(), p) { value = stills(p) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Pipeline(3)
            Gap(8)
            Text("المعاينة جاهزة", style = MaterialTheme.typography.headlineSmall)
            Text(job.title.ifBlank { job.slug }, style = MaterialTheme.typography.titleMedium, color = Gold)
            if (p.checksLine.isNotBlank()) { Gap(4); Pill("✓ ${p.checksLine}", Good) }
            Gap(6)
            Text(if (images.isNotEmpty()) "اسحب بين اللقطات، وكبّر بإصبعين. اكتب ملاحظة على أي لقطة بعينها، ثم وافق أو أعد التجهيز."
                 else "صورة من كل لقطة بترتيبها. اضغط الصورة لتكبيرها.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (images.isNotEmpty()) item { ShotPager(vm, images) }
        else p.sheet?.let { f -> item { ImageFile(f) { viewImage(ctx, f) } } }
        if (vm.shotNotes.isNotEmpty()) item {
            Card { Column {
                SectionTitle("ملاحظاتك على اللقطات (${vm.shotNotes.size})", fx = "pushpin")
                vm.shotNotes.toSortedMap().forEach { (k, v) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        Pill("اللقطة $k", Gold); androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                        Text(v, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        androidx.compose.material3.IconButton({ vm.shotNotes.remove(k) }) { androidx.compose.material3.Icon(Icons.Rounded.Close, "احذف الملاحظة") }
                    }
                }
            } }
        }
        if (p.checks.isNotEmpty()) item { Text("صور ChatGPT بعد القص (كل صورة برقمها)", style = MaterialTheme.typography.titleSmall) }
        p.checks.forEach { f -> item { ImageFile(f) { viewImage(ctx, f) } } }
        if (p.notes.isNotBlank()) item {
            Card { Row { FxIcon("robot", 28.dp); androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                Text("ملاحظة الذكاء الاصطناعي: ${p.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
        item {
            GoldButton("موافق — صيّر الفيديو", fx = "rocket", loading = vm.busy != null) { vm.approvePreview() }
            Gap(14)
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("ملاحظة عامة (اختياري)") },
                placeholder = { Text("مثال: الإيقاع بطيء في البداية") }, shape = RoundedCornerShape(16.dp), maxLines = 4)
            Gap(8)
            GhostButton("أعد التجهيز بملاحظاتي", icon = Icons.Rounded.Refresh, enabled = note.isNotBlank() || vm.shotNotes.isNotEmpty()) { vm.changePreview(note) }
            Gap(8)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                GhostButton("ابدأ من جديد", icon = Icons.Rounded.Close) { vm.reset() }
            }
            Gap(30)
        }
    }
}

@Composable
private fun ShotPager(vm: AppViewModel, images: List<ImageBitmap>) {
    val pager = rememberPagerState { images.size }
    val strip = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.currentPage) { strip.animateScrollToItem((pager.currentPage - 2).coerceAtLeast(0)) }
    Column {
        HorizontalPager(pager, Modifier.fillMaxWidth().height(460.dp), contentPadding = PaddingValues(horizontal = 60.dp), pageSpacing = 14.dp) { i ->
            var scale by remember { mutableFloatStateOf(1f) }
            var off by remember { mutableStateOf(Offset.Zero) }
            // pages beside the current one sit back a little: depth while swiping
            val dist = ((pager.currentPage - i) + pager.currentPageOffsetFraction).let { kotlin.math.abs(it) }.coerceIn(0f, 1f)
            Box(Modifier.fillMaxSize().graphicsLayer { val k = 1f - dist * 0.12f; scaleX = k; scaleY = k; alpha = 1f - dist * 0.4f }) {
                Image(images[i], "اللقطة ${i + 1}", Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))
                    .border(2.dp, if (vm.shotNotes.containsKey(i + 1)) Gold else Color.Transparent, RoundedCornerShape(20.dp))
                    .pinchZoom({ scale }) { s, pan -> scale = s; off = if (s <= 1.01f) Offset.Zero else off + pan }
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = off.x; translationY = off.y },
                    contentScale = ContentScale.Fit)
                Pill("${i + 1} / ${images.size}", Gold, Modifier.align(Alignment.TopStart).padding(10.dp))
            }
        }
        Gap(10)
        LazyRow(state = strip, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(images) { i, b ->
                Image(b, null, Modifier.width(44.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(8.dp))
                    .border(2.dp, when { i == pager.currentPage -> Gold; vm.shotNotes.containsKey(i + 1) -> Good; else -> Color.Transparent }, RoundedCornerShape(8.dp))
                    .pressable { scope.launch { pager.animateScrollToPage(i) } }, contentScale = ContentScale.Crop)
            }
        }
        Gap(10)
        val shot = pager.currentPage + 1
        var text by remember(shot) { mutableStateOf(vm.shotNotes[shot].orEmpty()) }
        OutlinedTextField(text, { text = it; if (it.isBlank()) vm.shotNotes.remove(shot) else vm.shotNotes[shot] = it }, Modifier.fillMaxWidth(),
            label = { Text("ملاحظة على اللقطة $shot") }, placeholder = { Text("مثال: الصورة خاطئة، أو النص مقطوع") },
            shape = RoundedCornerShape(16.dp), maxLines = 3)
    }
}
