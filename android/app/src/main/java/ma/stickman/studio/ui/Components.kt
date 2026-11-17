package ma.stickman.studio.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ma.stickman.studio.R
import ma.stickman.studio.ui.theme.Bad
import ma.stickman.studio.ui.theme.Gold
import ma.stickman.studio.ui.theme.GoldDeep
import ma.stickman.studio.ui.theme.GoldEdge
import ma.stickman.studio.ui.theme.Good
import ma.stickman.studio.ui.theme.Ink
import ma.stickman.studio.ui.theme.LocalLight
import ma.stickman.studio.ui.theme.LocalMotion
import ma.stickman.studio.ui.theme.Warn
import kotlin.math.roundToInt

/** Fast spring press feedback used on every tappable card and button, with the tap vibration/sound. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, label: String? = null, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val motion = LocalMotion.current
    val scale by animateFloatAsState(if (pressed && motion > 0f) 0.965f else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessHigh), label = "press")
    val fb = LocalFeedback.current
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(src, indication = androidx.compose.material3.ripple(), enabled = enabled, onClickLabel = label, role = Role.Button) {
            fb.tap(); onClick()
        }
}

/** The main action: a flat terracotta button with white text (1.2.0, calm design). */
@Composable
fun GoldButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true,
               loading: Boolean = false, fx: String? = null, onClick: () -> Unit) {
    FlatButton(text, modifier, icon, enabled, loading, fx, filled = true, onClick = onClick)
}

/** A quiet secondary button: thin outline, no fill. */
@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true,
                fx: String? = null, onClick: () -> Unit) {
    FlatButton(text, modifier, icon, enabled, false, fx, filled = false, onClick = onClick)
}

@Composable
private fun FlatButton(text: String, modifier: Modifier, icon: ImageVector?, enabled: Boolean, loading: Boolean, fx: String?,
                       filled: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val motion = LocalMotion.current
    val fb = LocalFeedback.current
    val scale by animateFloatAsState(if (pressed && enabled && motion > 0f) 0.98f else 1f, spring(stiffness = Spring.StiffnessHigh), label = "p")
    val alpha by animateFloatAsState(if (enabled) 1f else 0.45f, tween(150), label = "a")
    val shape = RoundedCornerShape(12.dp)
    val cs = MaterialTheme.colorScheme
    val bg = if (filled) cs.primary else Color.Transparent
    val fg = if (filled) cs.onPrimary else cs.onSurface
    val w = if (filled) Modifier.fillMaxWidth() else Modifier
    Box(modifier.then(w).height(if (filled) 50.dp else 42.dp).graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale }
        .semantics { contentDescription = text }
        .clip(shape).background(bg)
        .then(if (!filled) Modifier.border(1.dp, cs.outline, shape) else Modifier)
        .clickable(src, indication = androidx.compose.material3.ripple(), enabled = enabled && !loading, role = Role.Button) { fb.tap(); onClick() },
        contentAlignment = Alignment.Center) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = fg, strokeWidth = 2.5.dp)
        else RowContent(text, icon, fx, fg, if (filled) fg else cs.primary)
    }
}

@Composable
private fun RowContent(text: String, icon: ImageVector?, fx: String?, color: Color, iconTint: Color) {
    Row(Modifier.padding(horizontal = 16.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        when {
            fx != null -> { FxIcon(fx, 20.dp, tint = iconTint); Spacer(Modifier.width(8.dp)) }
            icon != null -> { Icon(icon, null, tint = iconTint, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)) }
        }
        Text(text, color = color, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** A flat card: warm surface, hairline border, no shadow (1.2.0). [tilt] is ignored, kept for old call sites. */
@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, tilt: Boolean = false, accent: Color? = null,
         content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val base = modifier.fillMaxWidth().clip(shape)
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, accent?.copy(alpha = 0.55f) ?: MaterialTheme.colorScheme.outlineVariant, shape)
    Box((if (onClick != null) base.pressable(onClick = onClick) else base).padding(16.dp)) { content() }
}

@Composable
fun SectionTitle(text: String, icon: ImageVector? = null, fx: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            fx != null -> { FxIcon(fx, 20.dp, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)) }
            icon != null -> {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
        }
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun Pill(text: String, color: Color = Gold, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = color,
        modifier = modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 3.dp))
}

/** A tappable choice chip (filters, quick wishes). */
@Composable
fun Chip(text: String, selected: Boolean = false, color: Color = Gold, fx: String? = null, onClick: () -> Unit) {
    val bg by androidx.compose.animation.animateColorAsState(if (selected) color.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh, label = "c")
    Row(Modifier.clip(CircleShape).background(bg)
        .border(1.dp, if (selected) color else MaterialTheme.colorScheme.outlineVariant, CircleShape)
        .pressable(onClick = onClick).defaultMinSize(minHeight = 40.dp).padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        fx?.let { FxIcon(it, 20.dp); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (selected) color else MaterialTheme.colorScheme.onSurface)
    }
}

/** Green / amber / red dot with a soft halo: is this key or service working? */
@Composable
fun StatusDot(state: Int, modifier: Modifier = Modifier) {   // 1 ok · 0 unknown/warn · -1 broken
    val c = when (state) { 1 -> Good; -1 -> Bad; else -> Warn }
    Box(modifier.size(14.dp).drawBehind {
        drawCircle(c.copy(alpha = 0.25f), radius = size.minDimension * 0.75f)
        drawCircle(c, radius = size.minDimension * 0.38f)
    })
}

/** "?" next to a setting: a short explanation in a dialog. */
@Composable
fun Help(title: String, text: String) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Rounded.HelpOutline, "شرح: $title", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton({ open = false }) { Text("فهمت") } })
}

/** A settings section that opens and closes, with a 3D icon. */
@Composable
fun Section(title: String, fx: String, subtitle: String = "", startOpen: Boolean = false, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(startOpen) }
    val rot by animateFloatAsState(if (open) 180f else 0f, label = "r")
    Card {
        Column(Modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).pressable(label = if (open) "أغلق" else "افتح") { open = !open },
                verticalAlignment = Alignment.CenterVertically) {
                FxIcon(fx, 22.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.ExpandMore, null, Modifier.graphicsLayer { rotationZ = rot })
            }
            AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = 12.dp)) { content() }
            }
        }
    }
}

/** Balance of one provider as a coloured bar. */
@Composable
fun ProviderBar(name: String, fx: String, left: Double?, total: Double?, note: String, state: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
        FxIcon(fx, 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1)
                StatusDot(state)
            }
            if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (left != null && total != null && total > 0) {
                val frac by animateFloatAsState((left / total).toFloat().coerceIn(0f, 1f), spring(stiffness = Spring.StiffnessLow), label = "b")
                val c = when { frac < 0.15f -> Bad; frac < 0.35f -> Warn; else -> Good }
                Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(frac).height(8.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(c.copy(alpha = 0.7f), c))))
                }
            }
        }
    }
}

/** A round gauge: [frac] of the ring filled, the value inside, the label under it (studio screen, 1.1.3). */
@Composable
fun Ring(frac: Float, value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(frac.coerceIn(0f, 1f), spring(stiffness = Spring.StiffnessLow), label = "ring")
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.size(78.dp).padding(4.dp)) {
                val st = androidx.compose.ui.graphics.drawscope.Stroke(width = 9.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawArc(track, -90f, 360f, false, style = st)
                drawArc(color, -90f, 360f * f, false, style = st)
            }
            Text(value, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** A number tile (weekly stats, result card). */
@Composable
fun StatTile(value: String, label: String, fx: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        FxIcon(fx, 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        RollingNumber(value, MaterialTheme.typography.titleLarge, color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** 1.2.0: thin line icons in place of the 3D emoji (calm design). The name list is unchanged, so every screen follows.
 *  "sparkles" is our own spark mark (SparkMark). Unknown names draw nothing, as before. */
@Composable
fun FxIcon(name: String, size: Dp, modifier: Modifier = Modifier, active: Boolean = false, tint: Color = Color.Unspecified) {
    val cs = MaterialTheme.colorScheme
    if (name == "sparkles") { Box(modifier.size(size), contentAlignment = Alignment.Center) { SparkMark(size * 0.8f, active = active) }; return }
    val v = FX[name] ?: return
    val c = if (tint != Color.Unspecified) tint else if (active) cs.primary else cs.onSurfaceVariant
    Box(modifier.size(size), contentAlignment = Alignment.Center) { Icon(v, null, Modifier.size(size * 0.8f), tint = c) }
}

val FX: Map<String, ImageVector> = mapOf(
    "clapper" to Icons.Outlined.Movie, "gear" to Icons.Outlined.Settings, "bell" to Icons.Outlined.Notifications,
    "robot" to Icons.Outlined.SmartToy, "palette" to Icons.Outlined.Palette, "phone" to Icons.Outlined.PhoneAndroid, "film" to Icons.Outlined.Movie,
    "bookmark" to Icons.Outlined.BookmarkBorder, "wastebasket" to Icons.Outlined.DeleteOutline, "speech" to Icons.Outlined.ChatBubbleOutline,
    "chart" to Icons.Outlined.Insights, "mic" to Icons.Outlined.Mic, "picture" to Icons.Outlined.Image, "rocket" to Icons.Outlined.RocketLaunch,
    "trophy" to Icons.Outlined.EmojiEvents, "party" to Icons.Outlined.Celebration, "hourglass" to Icons.Outlined.HourglassEmpty, "bulb" to Icons.Outlined.Lightbulb,
    "money" to Icons.Outlined.Payments, "key" to Icons.Outlined.Key, "lock" to Icons.Outlined.Lock, "shield" to Icons.Outlined.Shield,
    "calendar" to Icons.Outlined.CalendarMonth, "fire" to Icons.Outlined.LocalFireDepartment, "check" to Icons.Outlined.CheckCircle, "warning" to Icons.Outlined.WarningAmber,
    "scroll" to Icons.Outlined.Description, "books" to Icons.AutoMirrored.Outlined.MenuBook, "coin" to Icons.Outlined.Toll, "eyes" to Icons.Outlined.Visibility,
    "pushpin" to Icons.Outlined.PushPin, "globe" to Icons.Outlined.Public, "stopwatch" to Icons.Outlined.Timer,
    "magnifier" to Icons.Outlined.Search, "package" to Icons.Outlined.Inventory2, "crown" to Icons.Outlined.WorkspacePremium,
    "star" to Icons.Outlined.StarBorder, "coach" to Icons.AutoMirrored.Outlined.TrendingUp, "folder" to Icons.Outlined.FolderOpen,
)

/** Our own mark: a soft four-pointed spark with a small companion; it turns and breathes while the assistant thinks. */
@Composable
fun SparkMark(size: Dp, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, active: Boolean = false) {
    val motion = LocalMotion.current
    val on = active && motion > 0f
    // 1.3.5 (speed): a resting spark is drawn once; its clocks only exist while it turns
    if (!on) { SparkStill(size, modifier, color); return }
    val inf = rememberInfiniteTransition(label = "spark")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "r")
    val breathe by inf.animateFloat(0.86f, 1.06f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    SparkCanvas(size, modifier, color, true, rot, breathe)
}

@Composable
private fun SparkStill(size: Dp, modifier: Modifier, color: Color) = SparkCanvas(size, modifier, color, false, 0f, 1f)

@Composable
private fun SparkCanvas(size: Dp, modifier: Modifier, color: Color, on: Boolean, rot: Float, breathe: Float) {
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        fun star(cx: Float, cy: Float, r: Float, turn: Float) {
            val p = androidx.compose.ui.graphics.Path()
            for (i in 0 until 8) {
                val ang = Math.toRadians((i * 45.0) + turn - 90.0)
                val rr = if (i % 2 == 0) r else r * 0.30f
                val x = cx + (rr * kotlin.math.cos(ang)).toFloat(); val y = cy + (rr * kotlin.math.sin(ang)).toFloat()
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close(); drawPath(p, color)
        }
        val k = if (on) breathe else 1f
        val w = this.size.minDimension
        star(w * 0.44f, w * 0.56f, w * 0.42f * k, if (on) rot else 0f)
        star(w * 0.82f, w * 0.18f, w * 0.15f * (2f - k), if (on) -rot * 1.5f else 0f)
    }
}

fun copy(ctx: Context, label: String, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
}

fun openUrl(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Opens an installed app (e.g. ChatGPT, where the user is signed in), or the web page when it is missing. */
/** Shares [text] straight into an app's input box (ChatGPT puts it in its composer, tested 2026-11-13). */
fun sendText(ctx: Context, pkg: String, text: String): Boolean = runCatching {
    ctx.packageManager.getPackageInfo(pkg, 0)
    ctx.startActivity(Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(pkg).putExtra(Intent.EXTRA_TEXT, text)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}.isSuccess

fun openApp(ctx: Context, pkg: String, fallbackUrl: String) {
    val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
    if (i != null) runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { openUrl(ctx, fallbackUrl) }
    else openUrl(ctx, fallbackUrl)
}

/** Shows an image of the app (cache or files) full screen in the phone's own viewer, with pinch to zoom. */
fun viewImage(ctx: Context, f: java.io.File) {
    runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, if (f.extension == "png") "image/png" else "image/jpeg")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Text the user copies into another app: shown left-to-right if it is English, with a big copy button. */
@Composable
fun CopyBlock(title: String, text: String, ltr: Boolean = false, collapsed: Boolean = false, onCopied: (String) -> Unit) {
    val ctx = LocalContext.current
    val fb = LocalFeedback.current
    var open by rememberSaveable(title) { mutableStateOf(!collapsed) }
    var copied by remember { mutableStateOf(false) }
    val copiedScale by animateFloatAsState(if (copied) 1.15f else 1f, spring(dampingRatio = 0.35f), label = "cs",
        finishedListener = { copied = false })
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("${text.length} حرفًا", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { open = !open }) { Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (open) "أخفِ" else "اعرض") }
            IconButton(onClick = {
                copy(ctx, title, text); fb.success(); copied = true; onCopied("نُسخ: $title")
            }, modifier = Modifier.graphicsLayer { scaleX = copiedScale; scaleY = copiedScale }) {
                Icon(Icons.Rounded.ContentCopy, "انسخ $title", tint = MaterialTheme.colorScheme.primary)
            }
        }
        if (open) {
            CompositionLocalProvider(LocalLayoutDirection provides if (ltr) LayoutDirection.Ltr else LayoutDirection.Rtl) {
                SelectionContainer {
                    Text(text, style = MaterialTheme.typography.bodyMedium.copy(
                        textDirection = if (ltr) TextDirection.Ltr else TextDirection.ContentOrRtl,
                        fontFamily = if (ltr) FontFamily.Monospace else MaterialTheme.typography.bodyMedium.fontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp))
                }
            }
        }
    }
}

/** 1.2.0: no glow in the calm design (kept so old call sites compile). */
@Suppress("UNUSED_PARAMETER")
fun Modifier.glow(color: Color = Gold): Modifier = this

@Composable
fun PulseDot(color: Color = Gold) {
    val motion = LocalMotion.current
    if (motion == 0f) { Box(Modifier.size(10.dp).clip(CircleShape).background(color)); return }
    val t = rememberInfiniteTransition(label = "pulse")
    val s by t.animateFloat(0.6f, 1.15f, infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse), label = "s")
    Box(Modifier.size(10.dp).graphicsLayer { val k = if (motion > 0f) s else 1f; scaleX = k; scaleY = k }.clip(CircleShape).background(color))
}

/** Skeleton placeholder with a light sweeping over it. */
@Composable
fun Shimmer(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "sh")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "x")
    val base = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.clip(RoundedCornerShape(16.dp)).drawBehind {
        drawRect(base)
        drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
            start = Offset(size.width * x, 0f), end = Offset(size.width * (x + 0.6f), size.height)))
    })
}

/** Skeleton of a card: title line, two text lines. */
@Composable
fun SkeletonCard() {
    Card { Column {
        Shimmer(Modifier.fillMaxWidth(0.4f).height(18.dp)); Gap(10)
        Shimmer(Modifier.fillMaxWidth().height(14.dp)); Gap(6)
        Shimmer(Modifier.fillMaxWidth(0.8f).height(14.dp))
    } }
}

@Composable
fun Busy(text: String, value: Float? = null) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, tonalElevation = 6.dp, shadowElevation = 8.dp) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (value != null) CircularProgressIndicator(progress = { value }, modifier = Modifier.size(20.dp), strokeWidth = 3.dp)
            else CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(12.dp))
            Text(text + (value?.let { "  ${(it * 100).toInt()}%" } ?: ""), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Empty list: big 3D icon, a sentence and (optionally) the button that fixes it. */
@Composable
fun EmptyState(fx: String, text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FxIcon(fx, 44.dp)
        Gap(8)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) { Gap(12); GhostButton(action, onClick = onAction) }
    }
}

/** A line of a check list (green tick, amber warning, red cross). */
@Composable
fun CheckLine(ok: Boolean, text: String, warn: Boolean = false) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        val c = if (ok && !warn) Good else if (ok || warn) Warn else Bad
        Box(Modifier.size(22.dp).clip(CircleShape).background(c.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text(if (ok && !warn) "✓" else if (ok || warn) "!" else "✕", color = c, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun Gap(h: Int = 12) = Spacer(Modifier.height(h.dp))

/** Lays content in the middle of wide screens (tablets, unfolded phones) so lines never get too long. */
@Composable
fun Centered(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 640.dp).fillMaxWidth(), content = content)
    }
}
