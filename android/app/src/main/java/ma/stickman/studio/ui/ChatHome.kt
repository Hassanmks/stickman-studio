@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class)

package ma.stickman.studio.ui

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ma.stickman.studio.data.ChatAction
import ma.stickman.studio.data.ChatMsg
import ma.stickman.studio.data.ChatText
import ma.stickman.studio.data.Claude
import ma.stickman.studio.data.Conversation
import ma.stickman.studio.data.Stage
import ma.stickman.studio.ui.theme.Plex
import ma.stickman.studio.ui.theme.ReplyStyle
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// ============================================================================================
// 1.2.0: the home is a conversation. A greeting in the Naskh face, a single composer at the bottom (attach a photo,
// speak, send), suggestions to start, the assistant's replies streamed word by word with buttons for the actions it
// proposes, and every past conversation in the side drawer (starred, today, yesterday, this week, older).
// ============================================================================================

private val SUGGESTIONS = listOf(
    "sparkles" to "اقترح عليّ 10 مواضيع لفيديو جديد",
    "coach" to "ما الذي تعلّمه المدرّب من فيديوهاتي؟",
    "chart" to "حلّل آخر فيديو: ما الذي أحسّنه في القادم؟",
    "bulb" to "أعطني 3 خطّافات قوية لموضوع تاريخي",
)

@Composable
fun ChatHome(vm: AppViewModel, toast: (String) -> Unit) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ChatDrawer(vm) { scope.launch { drawer.close() } }
    }) {
        Column(Modifier.fillMaxSize().imePadding()) {
            // top bar: drawer, the conversation's title, new conversation
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ scope.launch { drawer.open() } }) { Icon(Icons.Rounded.Menu, "المحادثات", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(vm.chat?.title?.ifBlank { null } ?: "TaleSpark", style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton({ vm.newChat() }) { Icon(Icons.Outlined.Edit, "محادثة جديدة", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            StageBanner(vm)
            Box(Modifier.weight(1f)) {
                val c = vm.chat
                if (c == null || c.messages.isEmpty()) Welcome(vm) else Messages(vm, c, toast)
            }
            Composer(vm)
        }
    }
}

/** While a video is being made (or waits for you), one line at the top brings its screen back. */
@Composable
private fun StageBanner(vm: AppViewModel) {
    val job by vm.job.collectAsState()
    if (job.stage == Stage.IDLE) return
    val (text, running) = when (job.stage) {
        Stage.TOPICS_RUNNING -> "يبحث عن المواضيع…" to true
        Stage.TOPICS_READY -> "المواضيع جاهزة: اختر واحدًا" to false
        Stage.SCRIPT_RUNNING -> "يكتب السيناريو…" to true
        Stage.SCRIPT_READY, Stage.UPLOADING -> "السيناريو جاهز: الصوت والصور" to false
        Stage.PREVIEWING -> "يجهّز المعاينة…" to true
        Stage.PREVIEW_READY -> "المعاينة جاهزة للموافقة" to false
        Stage.PRODUCING -> "يصيّر الفيديو…" to true
        Stage.DONE -> "الفيديو جاهز" to false
        Stage.FAILED -> "لم يكتمل التشغيل" to false
        else -> "" to false
    }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)).pressable { vm.chatFocus = false }
        .padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (running) LogSpinner(MaterialTheme.colorScheme.primary, 16.dp) else FxIcon("film", 18.dp, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (job.title.isNotBlank()) Text(job.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text("افتح", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
    // 1.3.5: the whole video's clock and money, under the banner, while a video is being made
    JourneyLine(vm, Modifier.padding(horizontal = 16.dp, vertical = 2.dp), compact = true)
}

@Composable
private fun Welcome(vm: AppViewModel) {
    val zone = ZoneId.of("Africa/Casablanca")
    val h = LocalTime.now(zone).hour
    val hello = when (h) { in 5..11 -> "صباح الخير"; in 12..17 -> "طاب نهارك"; else -> "مساء الخير" }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SparkMark(34.dp, active = true)
                Gap(10)
                Text(hello, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            }
        }
        // 1.3.6: the assistant speaks first — what changed since you were here, from the phone's own data (no AI call)
        item {
            val lines = vm.brief()
            if (lines.isNotEmpty()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                SparkMark(20.dp); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    lines.forEach { Text(it, style = ReplyStyle.copy(color = MaterialTheme.colorScheme.onSurface)) }
                }
            } else Text("بماذا نبدأ اليوم؟ اكتب عنوان فيديو، أو اسألني أي سؤال.", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        item {
            // the first suggestion stays «10 topics»; then the channel's own numbers (1.3.4); then ideas that change
            // every day and are never the ones you saw in the last three days (1.3.6)
            val videos by vm.local.videos.collectAsState()
            val s by vm.settings.collectAsState()
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val askShots = rememberShotsPermission(vm)
            val smart = buildList {
                if (s.autoStats && !shotsGranted(ctx) && videos.any { it.publishedAt > 0 })
                    add(ma.stickman.studio.data.Insights.Suggestion("chart", "فعّل قراءة أرقام تيك توك من لقطة واحدة", "perm"))
                addAll(ma.stickman.studio.data.Insights.suggestions(videos))
            }.take(2)
            val fresh = remember { vm.dailyIdeas(4 - 1 - smart.size) }
            val all = listOf(ma.stickman.studio.data.Insights.Suggestion(SUGGESTIONS[0].first, SUGGESTIONS[0].second)) + smart + fresh
            LaunchedEffect(fresh) { vm.memory.markShown(fresh.map { it.text }) }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                all.take(4).forEach { g ->
                    val live = g in smart
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .border(1.dp, if (live) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        .pressable {
                            when (g.open) {
                                "tiktok" -> ma.stickman.studio.work.ShotWatch.TIKTOK.firstNotNullOfOrNull { ctx.packageManager.getLaunchIntentForPackage(it) }
                                    ?.let { runCatching { ctx.startActivity(it) } } ?: vm.send(g.text)
                                "perm" -> askShots()
                                "type" -> vm.draft = g.text
                                else -> vm.send(g.text)
                            }
                        }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        FxIcon(g.fx, 18.dp, tint = if (live) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Unspecified)
                        Spacer(Modifier.width(10.dp))
                        Text(g.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        // 1.3.6: the money and the coach in one small strip (a tap opens each)
        item { HomeStrip(vm) }
        item { StudioPanel(vm) }
        item { Gap(20) }
    }
}

@Composable
private fun Messages(vm: AppViewModel, c: Conversation, toast: (String) -> Unit) {
    val list = rememberLazyListState()
    val n = c.messages.size + (if (vm.streaming != null) 1 else 0) + (if (vm.chatError != null) 1 else 0)
    LaunchedEffect(n, vm.streaming?.length?.div(80)) { if (n > 0) list.animateScrollToItem(n - 1) }
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        c.messages.forEachIndexed { i, m ->
            item(key = "m$i") {
                if (m.role == "user") UserBubble(m) { vm.editFrom(i) } else Reply(vm, m.text, last = i == c.messages.lastIndex && vm.streaming == null, streaming = false, toast = toast)
            }
        }
        vm.streaming?.let { t -> item(key = "stream") { Reply(vm, t, last = true, streaming = true, toast = toast) } }
        if (vm.streaming == null && vm.chatError == null && c.messages.lastOrNull()?.role == "user") item(key = "noreply") {
            GhostButton("لم يصل الرد: أعد المحاولة", icon = Icons.Outlined.Refresh) { vm.retryChat() }
        }
        vm.chatError?.let { e -> item(key = "err") {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)).padding(14.dp)) {
                Text(e, style = MaterialTheme.typography.bodyMedium)
                Gap(8)
                GhostButton("أعد المحاولة", icon = Icons.Outlined.Refresh) { vm.retryChat() }
            }
        } }
        item { Gap(8) }
    }
}

@Composable
private fun UserBubble(m: ChatMsg, edit: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        if (m.images.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                m.images.take(3).forEach { b -> B64Image(b, Modifier.size(84.dp)) }
            }
        }
        // 1.3.6: long press = edit this message and send it again (the conversation goes back to before it)
        if (m.text.isNotBlank()) Box {
            Text(m.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .pointerInput(Unit) { detectTapGestures(onLongPress = { menu = true }) }
                .padding(horizontal = 14.dp, vertical = 10.dp))
            androidx.compose.material3.DropdownMenu(menu, { menu = false }) {
                val ctx = LocalContext.current
                androidx.compose.material3.DropdownMenuItem({ Text("عدّل وأعد الإرسال") }, { menu = false; edit() })
                androidx.compose.material3.DropdownMenuItem({ Text("انسخ") }, { menu = false; copy(ctx, "رسالة", m.text) })
            }
        }
    }
}

@Composable
private fun Reply(vm: AppViewModel, raw: String, last: Boolean, streaming: Boolean, toast: (String) -> Unit) {
    val ctx = LocalContext.current
    val (text, actions) = remember(raw) { ChatText.split(raw) }
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        if (streaming && text.isBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SparkMark(22.dp, active = true); Spacer(Modifier.width(10.dp))
                Text(vm.chatStatus ?: "يفكّر…", style = MaterialTheme.typography.bodyMedium.copy(brush = thinkingBrush()))
            }
            return@Column
        }
        Markdown(text)
        // 1.3.6: the buttons it proposes, a note of what it remembered, then the follow-up questions (last reply only)
        val buttons = actions.filter { it.kind !in setOf("follow", "remember") }
        if (buttons.isNotEmpty() && !streaming) {
            Gap(10)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                buttons.forEach { a -> ActionButton(a) { vm.runAction(a) } }
            }
        }
        actions.filter { it.kind == "remember" }.forEach { a ->
            Text("✓ " + a.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        val follows = actions.filter { it.kind == "follow" && it.args.firstOrNull()?.isNotBlank() == true }
        if (last && follows.isNotEmpty() && !streaming) {
            Gap(10)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                follows.take(3).forEach { a ->
                    Row(Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .pressable { vm.runAction(a) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("↩", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge); Spacer(Modifier.width(8.dp))
                        Text(a.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (streaming) { Gap(6); SparkMark(18.dp, active = true) }
        else Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ copy(ctx, "رد", text); toast("نُسخ الرد") }, Modifier.size(34.dp)) {
                Icon(Icons.Outlined.ContentCopy, "انسخ", Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // 1.3.6: listen to the reply (the phone's own Arabic voice)
            IconButton({ Speaker.toggle(ctx, text) }, Modifier.size(34.dp)) {
                Icon(Icons.AutoMirrored.Outlined.VolumeUp, "استمع", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (last) IconButton({ vm.retryChat() }, Modifier.size(34.dp)) {
                Icon(Icons.Outlined.Refresh, "أعد الرد", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (last) { Spacer(Modifier.width(6.dp)); SparkMark(16.dp) }
        }
    }
}

@Composable
private fun ActionButton(a: ChatAction, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary).pressable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        FxIcon(when (a.kind) { "topics" -> "sparkles"; "resume" -> "scroll"; "open" -> "film"; "perf", "saveperf" -> "chart"; "coach" -> "coach"; else -> "folder" },
            18.dp, tint = MaterialTheme.colorScheme.onPrimary)
        Spacer(Modifier.width(8.dp))
        Text(a.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun thinkingBrush(): androidx.compose.ui.graphics.Brush {
    val inf = rememberInfiniteTransition(label = "think")
    val x by inf.animateFloat(-200f, 900f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "x")
    val base = MaterialTheme.colorScheme.onSurfaceVariant
    return androidx.compose.ui.graphics.Brush.linearGradient(listOf(base, MaterialTheme.colorScheme.primary, base),
        start = androidx.compose.ui.geometry.Offset(x - 150f, 0f), end = androidx.compose.ui.geometry.Offset(x + 150f, 0f))
}

/** The little Markdown the assistant uses: ### headings, - bullets, 1. numbers, **bold**, `code`. */
@Composable
fun Markdown(text: String) {
    val cs = MaterialTheme.colorScheme
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            text.trim().split("\n").forEach { line0 ->
                val line = line0.trimEnd()
                when {
                    line.isBlank() -> Spacer(Modifier.size(2.dp))
                    line.startsWith("#") -> Text(inline(line.trimStart('#').trim(), cs.primary), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp))
                    line.trimStart().startsWith("- ") || line.trimStart().startsWith("• ") || line.trimStart().startsWith("* ") -> Row {
                        Text("•", style = ReplyStyle, color = cs.primary, modifier = Modifier.padding(end = 8.dp))
                        Text(inline(line.trimStart().drop(2), cs.primary), style = ReplyStyle.copy(color = cs.onSurface))
                    }
                    Regex("^\\s*\\d+[.)] ").containsMatchIn(line) -> Row {
                        val num = Regex("^\\s*(\\d+)[.)] ").find(line)!!.groupValues[1]
                        Text("$num.", style = ReplyStyle.copy(fontFamily = Plex, fontSize = 15.sp), color = cs.primary, modifier = Modifier.padding(end = 8.dp, top = 3.dp))
                        Text(inline(line.replace(Regex("^\\s*\\d+[.)] "), ""), cs.primary), style = ReplyStyle.copy(color = cs.onSurface))
                    }
                    else -> Text(inline(line, cs.primary), style = ReplyStyle.copy(color = cs.onSurface))
                }
            }
        }
    }
}

private fun inline(s: String, accent: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    val r = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`")
    r.findAll(s).forEach { m ->
        append(s.substring(i, m.range.first))
        if (m.groupValues[1].isNotEmpty()) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
        else withStyle(SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = accent, fontSize = 14.sp)) { append(m.groupValues[2]) }
        i = m.range.last + 1
    }
    append(s.substring(i))
}

/** 1.3.5 (speed): small pictures kept as text (chat photos, step thumbnails) are decoded off the screen's thread,
 *  once: the next time the same picture is drawn it comes from memory. */
private val b64Cache = android.util.LruCache<Int, androidx.compose.ui.graphics.ImageBitmap>(48)

@Composable
fun rememberB64Bitmap(b64: String): androidx.compose.ui.graphics.ImageBitmap? {
    val key = b64.hashCode() * 31 + b64.length
    return androidx.compose.runtime.produceState(b64Cache.get(key), key) {
        if (value != null) return@produceState
        value = withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching { android.util.Base64.decode(b64, android.util.Base64.DEFAULT).let {
                android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }.getOrNull()
        }?.also { b64Cache.put(key, it) }
    }.value
}

/** 1.3.5 (speed): a picture file decoded off the screen's thread ([maxW] = widest useful size in pixels), kept in memory. */
private val fileCache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(24)

@Composable
fun rememberFileBitmap(path: String?, maxW: Int = 540): androidx.compose.ui.graphics.ImageBitmap? {
    val f = path?.let { java.io.File(it) }
    val key = f?.let { "${it.path}|${it.lastModified()}|$maxW" }
    return androidx.compose.runtime.produceState(key?.let { fileCache.get(it) }, key) {
        if (value != null || f == null || key == null) return@produceState
        value = withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(f.path, o)
                var sample = 1
                while (o.outWidth / (sample * 2) >= maxW) sample *= 2
                android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }?.also { fileCache.put(key, it) }
    }.value
}

@Composable
fun B64Image(b64: String, modifier: Modifier) {
    val bmp = rememberB64Bitmap(b64)
    bmp?.let { Image(it, null, modifier.clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop) }
}

/** The single input of the app: text, photos (+), voice (mic), send / stop. */
@Composable
private fun Composer(vm: AppViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    val images = remember { mutableStateListOf<String>() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(3)) { uris ->
        scope.launch { uris.forEach { u -> withContext(Dispatchers.IO) { Claude.imageB64(ctx, u) }?.let { if (images.size < 3) images.add(it) } } }
    }
    val speak = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { said -> text = (text + " " + said).trim() }
    }
    val cs = MaterialTheme.colorScheme
    val busy = vm.streaming != null
    // 1.3.6: a message given back for editing, or an idea that starts a sentence («أريد فيديو عن: »)
    LaunchedEffect(vm.draft) { vm.draft?.let { text = it; vm.draft = null } }
    // 1.3.6: one smart chip above the box, from what you are typing (before you send)
    val intent = remember(text, images.size) { intentOf(text, images.isNotEmpty()) }
    if (intent != null && !busy) Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp).clip(RoundedCornerShape(16.dp))
        .background(cs.primaryContainer.copy(alpha = 0.6f)).pressable {
            vm.send(intent.second, images.toList()); text = ""; images.clear()
        }.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        FxIcon(intent.third, 16.dp, tint = cs.primary); Spacer(Modifier.width(6.dp))
        Text(intent.first, style = MaterialTheme.typography.labelLarge, color = cs.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp)
        .clip(RoundedCornerShape(24.dp)).background(cs.surface).border(1.dp, cs.outline, RoundedCornerShape(24.dp))
        .padding(horizontal = 10.dp, vertical = 8.dp)) {
        if (images.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            items(images.toList()) { b ->
                Box {
                    B64Image(b, Modifier.size(64.dp))
                    Box(Modifier.align(Alignment.TopStart).padding(2.dp).size(20.dp).clip(CircleShape).background(cs.inverseSurface)
                        .pressable { images.remove(b) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, "أزل الصورة", Modifier.size(13.dp), tint = cs.inverseOnSurface)
                    }
                }
            }
        }
        BasicTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 26.dp, max = 160.dp).padding(horizontal = 6.dp, vertical = 4.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface), cursorBrush = SolidColor(cs.primary),
            decorationBox = { inner ->
                Box { if (text.isEmpty()) Text("اكتب رسالة…", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant); inner() }
            })
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.size(38.dp)) {
                Icon(Icons.Rounded.Add, "أرفق صورة", tint = cs.onSurfaceVariant)
            }
            IconButton({
                runCatching { speak.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-MA").putExtra(RecognizerIntent.EXTRA_PROMPT, "تكلّم…")) }
            }, Modifier.size(38.dp)) { Icon(Icons.Rounded.Mic, "تكلّم", tint = cs.onSurfaceVariant) }
            Spacer(Modifier.weight(1f))
            val canSend = text.isNotBlank() || images.isNotEmpty()
            Box(Modifier.size(36.dp).clip(CircleShape).background(if (busy || canSend) cs.primary else cs.surfaceContainerHighest)
                .pressable(enabled = busy || canSend, label = if (busy) "أوقف" else "أرسل") {
                    if (busy) vm.stopChat() else { vm.send(text, images.toList()); text = ""; images.clear() }
                }, contentAlignment = Alignment.Center) {
                Icon(if (busy) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward, null, Modifier.size(20.dp),
                    tint = if (busy || canSend) cs.onPrimary else cs.onSurfaceVariant)
            }
        }
    }
}

private val ASKING = Regex("""^(ما|ماذا|من|متى|أين|اين|كيف|كم|هل|لماذا|لم|علاش|واش|شنو|كيفاش|شكون|فين|اكتب|أعطني|اعطني|اقترح|حلل|حلّل|قيّم|قيم|لخص|لخّص|ترجم|اشرح|صحح|صحّح|ساعدني|أريد|اريد|بغيت|عطيني|افتح)(\s|$)""")

/** What the text in the box looks like: (chip label, message to send, icon), or null for an ordinary message. */
private fun intentOf(t0: String, image: Boolean): Triple<String, String, String>? {
    val t = t0.trim()
    if (image && t.isEmpty()) return Triple("احفظ أرقام هذه اللقطة لفيديو منشور", "هذه لقطة تحليلات من تيك توك: اقرأ أرقامها واقترح حفظها للفيديو المطابق.", "chart")
    if (t.isEmpty()) return null
    Regex("""https?://\S+""").find(t)?.let { return Triple("لخّص هذا الرابط", "لخّص لي هذه الصفحة، وقل هل تصلح فكرة فيديو: ${it.value}", "scroll") }
    val words = t.split(Regex("""\s+""")).filter { it.isNotBlank() }
    val looksTitle = words.size in 2..10 && !t.endsWith("?") && !t.endsWith("؟") && !ASKING.containsMatchIn(t) && '\n' !in t
    if (looksTitle) return Triple("اصنع فيديو عن «${t.take(40)}»", "أريد فيديو عن: $t", "film")
    if (Regex("^(أريد|اريد|بغيت) فيديو").containsMatchIn(t)) return Triple("اصنع هذا الفيديو", t, "film")
    return null
}

/** Every conversation, grouped like a chat app: starred, today, yesterday, last 7 days, older; long press = star/delete. */
@Composable
private fun ChatDrawer(vm: AppViewModel, close: () -> Unit) {
    val all by vm.chats.all.collectAsState()
    var q by rememberSaveable { mutableStateOf("") }
    val zone = ZoneId.of("Africa/Casablanca")
    val today = LocalDate.now(zone)
    fun day(c: Conversation) = Instant.ofEpochMilli(c.updated).atZone(zone).toLocalDate()
    val shown = all.filter { q.isBlank() || it.title.contains(q.trim(), true) || it.messages.any { m -> m.text.contains(q.trim(), true) } }
    val groups = listOf(
        "المميّزة" to shown.filter { it.starred },
        "اليوم" to shown.filter { !it.starred && day(it) == today },
        "أمس" to shown.filter { !it.starred && day(it) == today.minusDays(1) },
        "آخر 7 أيام" to shown.filter { !it.starred && day(it) < today.minusDays(1) && day(it) >= today.minusDays(7) },
        "أقدم" to shown.filter { !it.starred && day(it) < today.minusDays(7) },
    ).filter { it.second.isNotEmpty() }
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.background, modifier = Modifier.width(300.dp)) {
        Column(Modifier.fillMaxHeight().padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
                SparkMark(22.dp); Spacer(Modifier.width(10.dp))
                Text("TaleSpark", style = MaterialTheme.typography.headlineSmall)
            }
            Gap(8)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).pressable { vm.newChat(); close() }.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
                Spacer(Modifier.width(10.dp)); Text("محادثة جديدة", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            DrawerLink("folder", "المشاريع") { vm.tabRequest = 1; close() }
            DrawerLink("coach", "المدرّب") { vm.showCoach = true; close() }
            Gap(6)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                BasicTextField(q, { q = it }, Modifier.weight(1f), singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner -> Box { if (q.isEmpty()) Text("ابحث في المحادثات", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); inner() } })
            }
            Gap(8)
            LazyColumn(Modifier.weight(1f)) {
                if (groups.isEmpty()) item {
                    Text(if (all.isEmpty()) "لا محادثات بعد. اكتب أول رسالة." else "لا نتيجة.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(10.dp))
                }
                groups.forEach { (label, list) ->
                    item(key = "h$label") {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 10.dp, top = 12.dp, bottom = 4.dp))
                    }
                    items(list, key = { it.id }) { c -> ChatRow(vm, c, selected = vm.chat?.id == c.id) { vm.openChat(c.id); close() } }
                }
            }
        }
    }
}

@Composable
private fun DrawerLink(fx: String, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).pressable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        FxIcon(fx, 22.dp); Spacer(Modifier.width(12.dp)); Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ChatRow(vm: AppViewModel, c: Conversation, selected: Boolean, open: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(onClick = open, onLongClick = { menu = true }).padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (c.starred) { Icon(Icons.Rounded.Star, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(6.dp)) }
            Text(c.title.ifBlank { "محادثة" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem({ Text(if (c.starred) "أزل التمييز" else "ميّزها") }, { menu = false; vm.chats.star(c.id) },
                leadingIcon = { Icon(Icons.Outlined.StarBorder, null) })
            DropdownMenuItem({ Text("احذفها") }, { menu = false; vm.deleteChat(c.id) }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) })
        }
    }
}


/** 1.3.1: today's money in one card: Coarena's own figures (used, left) and what each kind of step cost. */
@Composable
private fun HomeStrip(vm: AppViewModel) {
    var money by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (vm.coach == null) vm.loadCoach() }
    val d = vm.spendToday()
    fun usd(x: Double) = "%.2f$".format(java.util.Locale.US, x)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .pressable { money = !money }.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                FxIcon("money", 16.dp); Spacer(Modifier.width(8.dp))
                Text("اليوم " + (d.used?.let { usd(it) } ?: "—") + (d.left?.let { " · بقي " + usd(it) } ?: ""),
                    style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .pressable { vm.showCoach = true }.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                FxIcon("coach", 16.dp); Spacer(Modifier.width(8.dp))
                Text("المدرّب " + (vm.coach?.avgLast5?.let { "${it.toInt()}/100" } ?: "—"), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
        AnimatedVisibility(money) { SpendToday(vm) }
    }
}

@Composable
private fun SpendToday(vm: AppViewModel) {
    val d = vm.spendToday()
    if (d.used == null && d.steps.isEmpty()) return
    fun usd(x: Double) = "%.2f$".format(java.util.Locale.US, x)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
        .padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FxIcon("money", 18.dp); Spacer(Modifier.width(10.dp))
            Text("إنفاق اليوم", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            d.used?.let { Text(usd(it), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
        }
        d.left?.let { Text("المتبقي في Coarena اليوم: ${usd(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (d.steps.isNotEmpty()) Text(d.steps.joinToString(" · ") { (k, v) -> "$k: ${usd(v)}" },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
