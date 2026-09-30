package io.github.kuscher.summa.ui.editor

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kuscher.summa.ui.Sym
import io.github.kuscher.summa.ui.SymIcon
import io.github.kuscher.summa.ui.theme.SummaFonts

private val ACCESSORY = listOf("€", "$", "£", "%", "of", "in", "km", "kg", "h", "sum", "prev", "(", ")", ":", "=", "^")

/**
 * The phone keypad: numbers and operators in the Expressive style. Keys are circles that squash
 * into rounded squares while pressed (a spring), and the return key is Material's cookie shape.
 */
@Composable
fun Keypad(onText: (String) -> Unit, onBackspace: () -> Unit, onKeyboard: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().background(scheme.surfaceContainer).windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (a in ACCESSORY) {
                Surface(onClick = { onText(if (a.length > 1 && a.first().isLetter()) " $a " else a) }, shape = RoundedCornerShape(10.dp), color = scheme.surfaceContainerLowest) {
                    Text(a, Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                        color = if (a.first().isLetter()) scheme.primary else scheme.tertiary)
                }
            }
        }
        val rows = listOf(
            listOf("7", "8", "9", "÷"), listOf("4", "5", "6", "×"),
            listOf("1", "2", "3", "−"), listOf(".", "0", "⌫", "+"),
        )
        Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in rows) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in r) when (k) {
                    "⌫" -> Key(Modifier.weight(1f), op = true, label = "Delete", onClick = onBackspace) { SymIcon(Sym.BACKSPACE, size = 22.sp) }
                    "÷", "×", "−", "+" -> Key(Modifier.weight(1f), op = true, label = k, onClick = { onText(" ${k.replace("−", "-")} ") }) { KeyText(k) }
                    else -> Key(Modifier.weight(1f), label = k, onClick = { onText(k) }) { KeyText(k) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Key(Modifier.weight(2f), op = true, label = "Keyboard", onClick = onKeyboard) {
                    Row(verticalAlignment = Alignment.CenterVertically) { SymIcon(Sym.KEYBOARD, size = 20.sp); Text("  ABC", style = MaterialTheme.typography.labelLarge) }
                }
                Key(Modifier.weight(1f), label = "Space", onClick = { onText(" ") }) { SymIcon(Sym.SPACE_BAR, size = 22.sp) }
                ReturnKey(Modifier.weight(1f)) { onText("\n") }
            }
        }
    }
}

@Composable
private fun KeyText(k: String) {
    Text(k, style = TextStyle(fontFamily = SummaFonts.round, fontSize = 24.sp, fontWeight = FontWeight(600)))
}

@Composable
private fun RowScope.Key(modifier: Modifier, op: Boolean = false, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateIntAsState(if (pressed) 28 else 50, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), label = "corner")
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "scale")
    val view = LocalView.current
    val bg = if (op) scheme.secondaryContainer else scheme.surfaceContainerHighest
    val fg = if (op) scheme.onSecondaryContainer else scheme.onSurface
    Box(
        modifier.height(54.dp).scale(scale).clip(RoundedCornerShape(corner)).background(bg)
            .clickable(interaction, indication = null, role = androidx.compose.ui.semantics.Role.Button) { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onClick() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides fg) { content() } }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RowScope.ReturnKey(modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val rotate by animateFloatAsState(if (pressed) 40f else 0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow), label = "rot")
    val view = LocalView.current
    val cookie: Shape = MaterialShapes.Cookie9Sided.toShape()
    Box(modifier.height(54.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.height(54.dp).padding(horizontal = 6.dp).fillMaxWidth()
                .clip(RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.height(54.dp).fillMaxWidth(0.72f)
                    .graphicsLayer { rotationZ = rotate }
                    .clip(cookie).background(if (pressed) scheme.primary else scheme.primaryContainer)
                    .clickable(interaction, indication = null, role = androidx.compose.ui.semantics.Role.Button) { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onClick() }
                    .semantics { contentDescription = "New line" },
            )
            SymIcon(Sym.KEYBOARD_RETURN, size = 24.sp, tint = if (pressed) scheme.onPrimary else scheme.onPrimaryContainer)
        }
    }
}

