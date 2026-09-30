package io.github.kuscher.summa.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.kuscher.summa.R
import io.github.kuscher.summa.data.Settings

/** Summa Sans (Google Sans Flex, OFL): the standard cut, a rounded cut for answers, and a mono. */
object SummaFonts {
    private val weights = listOf(300, 400, 500, 600, 700, 800, 900)

    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    private fun family(rond: Int, opsz: Float) = FontFamily(weights.map { w ->
        Font(
            R.font.summa_sans, FontWeight(w),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(w),
                FontVariation.Setting("ROND", rond.toFloat()),
                FontVariation.Setting("opsz", opsz),
            ),
        )
    })

    val sans = family(0, 18f)
    val round = family(100, 18f)
    val display = family(100, 48f)

    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    val mono = FontFamily(listOf(400, 500, 700).map { w ->
        Font(R.font.summa_mono, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    })
}

/** Colours for syntax and answers that aren't in the M3 scheme, derived from it. */
@Immutable
data class SummaColors(
    val number: Color, val unit: Color, val variable: Color, val keyword: Color, val function: Color,
    val label: Color, val comment: Color, val heading: Color, val reference: Color, val referenceBg: Color,
    val date: Color, val rail: Color, val activeLine: Color, val answer: Color,
)

val LocalSummaColors = staticCompositionLocalOf<SummaColors> { error("no theme") }

fun summaColors(c: ColorScheme) = SummaColors(
    number = c.onSurface, unit = c.tertiary, variable = c.primary, keyword = c.onSurfaceVariant,
    // Comments sit a little darker than the outline tone so they keep 4.5:1 contrast (WCAG AA).
    function = c.secondary, label = c.onSurfaceVariant, comment = androidx.compose.ui.graphics.lerp(c.outline, c.onSurfaceVariant, 0.4f), heading = c.onSurface,
    reference = c.onSecondaryContainer, referenceBg = c.secondaryContainer, date = c.tertiary,
    rail = c.surfaceContainerLow, activeLine = c.primaryContainer.copy(alpha = 0.16f), answer = c.onSurface,
)

private fun typography(): Typography {
    val base = Typography()
    fun TextStyle.sans() = copy(fontFamily = SummaFonts.sans)
    fun TextStyle.disp() = copy(fontFamily = SummaFonts.display)
    return base.copy(
        displayLarge = base.displayLarge.disp(), displayMedium = base.displayMedium.disp(), displaySmall = base.displaySmall.disp(),
        headlineLarge = base.headlineLarge.disp(), headlineMedium = base.headlineMedium.disp(), headlineSmall = base.headlineSmall.disp(),
        titleLarge = base.titleLarge.copy(fontFamily = SummaFonts.round, fontWeight = FontWeight(650)),
        titleMedium = base.titleMedium.copy(fontFamily = SummaFonts.round, fontWeight = FontWeight(620)),
        titleSmall = base.titleSmall.sans(),
        bodyLarge = base.bodyLarge.sans(), bodyMedium = base.bodyMedium.sans(), bodySmall = base.bodySmall.sans(),
        labelLarge = base.labelLarge.copy(fontFamily = SummaFonts.round, fontWeight = FontWeight(620)),
        labelMedium = base.labelMedium.sans(), labelSmall = base.labelSmall.sans(),
    )
}

fun schemeFor(theme: String, dark: Boolean, context: android.content.Context): ColorScheme = when (theme) {
    "wallpaper" -> if (Build.VERSION.SDK_INT >= 31) (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context))
                   else if (dark) Schemes.tangerineDark else Schemes.tangerineLight
    "cobalt" -> if (dark) Schemes.cobaltDark else Schemes.cobaltLight
    "lime" -> if (dark) Schemes.limeDark else Schemes.limeLight
    "berry" -> if (dark) Schemes.berryDark else Schemes.berryLight
    else -> if (dark) Schemes.tangerineDark else Schemes.tangerineLight
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SummaTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when (settings.dark) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val scheme = schemeFor(settings.theme, dark, LocalContext.current)
    androidx.compose.runtime.CompositionLocalProvider(LocalSummaColors provides summaColors(scheme)) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            typography = typography(),
            content = content,
        )
    }
}

val ANSWER_SIZE = 16.5.sp
