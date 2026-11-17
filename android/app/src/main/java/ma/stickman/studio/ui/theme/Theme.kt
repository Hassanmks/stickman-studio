@file:OptIn(ExperimentalTextApi::class)

package ma.stickman.studio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import ma.stickman.studio.R

// ============================================================================================
// 1.2.0: a calm, warm design language — cream paper by day, warm charcoal by night, one terracotta accent,
// a clean Arabic sans for the interface and an elegant Naskh for what the assistant writes.
// Our own palette and type (open-licence fonts); no logo or brand asset of anyone else.
// ============================================================================================

/** The one accent (buttons, the running step, selection). Kept under its old name so every screen follows. */
val Gold = Color(0xFFD97757)
val GoldDeep = Color(0xFFC2613F)
val GoldEdge = Color(0xFFA14F31)
val Clay = Gold
val Ink = Color(0xFF1F1E1D)
val Surface1 = Color(0xFF262624)
val Surface2 = Color(0xFF30302E)
val Good = Color(0xFF6FAF7E)
val Warn = Color(0xFFD9A441)
val Bad = Color(0xFFD9604F)
val Sky = Color(0xFF6E9FD8)
val Violet = Color(0xFF9B87D6)
/** The video's own keyword colour, only for previews of the video itself. */
val VideoGold = Color(0xFFFFD400)

private fun plex(w: Int) = Font(when { w >= 700 -> R.font.plex_bold; w >= 600 -> R.font.plex_semibold; w >= 500 -> R.font.plex_medium; else -> R.font.plex_regular }, FontWeight(w))
private fun naskh(w: Int) = Font(R.font.naskh, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

/** Interface font. (Old names kept: every screen that used them now gets the new type.) */
val Plex = FontFamily(plex(400), plex(500), plex(600), plex(700), plex(800), plex(900))
val Cairo = Plex
val Alexandria = Plex
/** The assistant's voice: greetings, chat replies, lessons. */
val Naskh = FontFamily(naskh(400), naskh(500), naskh(600), naskh(700))

private val type = Typography(
    displayMedium = TextStyle(fontFamily = Naskh, fontWeight = FontWeight(500), fontSize = 36.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = Naskh, fontWeight = FontWeight(500), fontSize = 28.sp, lineHeight = 42.sp),
    headlineSmall = TextStyle(fontFamily = Naskh, fontWeight = FontWeight(600), fontSize = 22.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = Plex, fontWeight = FontWeight(600), fontSize = 19.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Plex, fontWeight = FontWeight(600), fontSize = 16.sp, lineHeight = 25.sp),
    titleSmall = TextStyle(fontFamily = Plex, fontWeight = FontWeight(600), fontSize = 14.5.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Plex, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = Plex, fontWeight = FontWeight(400), fontSize = 14.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontFamily = Plex, fontWeight = FontWeight(400), fontSize = 12.5.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Plex, fontWeight = FontWeight(600), fontSize = 14.5.sp),
    labelMedium = TextStyle(fontFamily = Plex, fontWeight = FontWeight(500), fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = Plex, fontWeight = FontWeight(500), fontSize = 11.5.sp),
)

/** Text the assistant writes (chat replies, lessons): the Naskh face, a little larger and airy. */
val ReplyStyle = TextStyle(fontFamily = Naskh, fontWeight = FontWeight(400), fontSize = 17.sp, lineHeight = 30.sp)

/** Animation speed scale: 1 = normal, 0 = no motion (setting "reduce motion" or animations off in the phone). */
val LocalMotion = staticCompositionLocalOf { 1f }

/** True in the light theme. */
val LocalLight = staticCompositionLocalOf { false }

@Composable
fun StudioTheme(theme: String, reduceMotion: Boolean, highContrast: Boolean = false, fontScale: Float = 1f,
                content: @Composable () -> Unit) {
    val dark = when (theme) { "light" -> false; "system" -> isSystemInDarkTheme(); else -> true }
    val colors = if (dark) darkColorScheme(
        primary = Clay, onPrimary = Color.White, primaryContainer = Color(0xFF4A2E22), onPrimaryContainer = Color(0xFFF2BC97),
        secondary = Sky, tertiary = Violet,
        background = if (highContrast) Color.Black else Surface1, onBackground = Color(0xFFF4F3EE),
        surface = if (highContrast) Color(0xFF111110) else Ink, onSurface = if (highContrast) Color.White else Color(0xFFF4F3EE),
        surfaceVariant = Surface2, onSurfaceVariant = if (highContrast) Color(0xFFE6E4DC) else Color(0xFFA9A69C),
        surfaceContainer = Ink, surfaceContainerHigh = Color(0xFF30302E), surfaceContainerHighest = Color(0xFF3A3936),
        outline = if (highContrast) Color(0xFF8A877E) else Color(0xFF45433E),
        outlineVariant = if (highContrast) Color(0xFF5A5852) else Color(0xFF383733), error = Bad,
    ) else lightColorScheme(
        primary = GoldDeep, onPrimary = Color.White, primaryContainer = Color(0xFFF6E3D8), onPrimaryContainer = Color(0xFF6B3016),
        secondary = Color(0xFF3E6FA8), tertiary = Color(0xFF6B58B0),
        background = if (highContrast) Color.White else Color(0xFFF5F4EE), onBackground = Color(0xFF1F1E1D),
        surface = Color(0xFFFAF9F5), onSurface = if (highContrast) Color.Black else Color(0xFF1F1E1D),
        surfaceVariant = Color(0xFFEFEDE5), onSurfaceVariant = if (highContrast) Color(0xFF2B2A27) else Color(0xFF6B6860),
        surfaceContainer = Color(0xFFFAF9F5), surfaceContainerHigh = Color(0xFFF0EEE6), surfaceContainerHighest = Color(0xFFE8E5DB),
        outline = if (highContrast) Color(0xFF55524A) else Color(0xFFDDD9CD), outlineVariant = Color(0xFFE6E2D7),
        error = Color(0xFFC0473A),
    )
    val d = LocalDensity.current
    MaterialTheme(colorScheme = colors, typography = type) {
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalMotion provides if (reduceMotion) 0f else 1f,
            LocalLight provides !dark,
            LocalDensity provides Density(d.density, d.fontScale * fontScale),
        ) { content() }
    }
}
