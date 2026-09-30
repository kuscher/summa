package io.github.kuscher.summa.ui.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kuscher.summa.ui.Sym
import io.github.kuscher.summa.ui.SymIcon
import io.github.kuscher.summa.ui.copyToClipboard
import io.github.kuscher.summa.ui.theme.SummaFonts

/** What the display pill shows: a label ("Line 7", "4 lines · sum") and the value. */
data class Display(val label: String, val value: String?, val selection: Boolean)

private val UNITS = listOf(
    "Length" to listOf("km", "m", "cm", "mm", "mi", "ft", "in", "yd"),
    "Weight" to listOf("kg", "g", "lb", "oz", "st"),
    "Volume" to listOf("L", "mL", "cup", "tbsp", "tsp", "gal", "fl oz"),
    "Area" to listOf("m²", "km²", "sq ft", "ha", "acres"),
    "Time" to listOf("hours", "min", "days", "weeks", "months", "years"),
    "Temperature" to listOf("°C", "°F", "K"),
    "Data" to listOf("GB", "MB", "TB", "GiB", "Mbps"),
    "Speed" to listOf("km/h", "mph", "m/s", "knots"),
    "Screen" to listOf("px", "pt", "em", "rem"),
)
private val CURRENCIES = listOf("€", "$", "£", "¥", "CHF", "CA$", "A$", "₹", "BTC")
private val FUNCTIONS = listOf(
    "sqrt()" to "square root", "round()" to "round", "abs()" to "absolute value", "log()" to "log base 10",
    "ln()" to "natural log", "sin()" to "sine", "cos()" to "cosine", "tan()" to "tangent", "fact()" to "factorial",
    "sum" to "lines above", "average" to "lines above", "prev" to "previous answer",
    " in hex" to "as hexadecimal", " in binary" to "as binary", " as fraction" to "as a fraction", " to 2 dp" to "round to 2 decimals",
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SheetToolbar(
    display: Display,
    onInsert: (String) -> Unit,
    onCycle: () -> Unit,
    onMessage: (String) -> Unit,
    keypadOpen: Boolean,
    onKeypad: (() -> Unit)?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val context = LocalContext.current
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
        contentPadding = FloatingToolbarDefaults.ContentPadding,
        trailingContent = {
            DisplayPill(display, onCycle, onCopy = { v -> copyToClipboard(context, v); onMessage("Copied $v") }, big = !compact)
        },
    ) {
        if (onKeypad != null) ToolButton(Sym.DIALPAD, "Keypad", selected = keypadOpen, onClick = onKeypad)
        MenuButton(Sym.STRAIGHTEN, "Insert a unit") { close ->
            for ((group, units) in UNITS) {
                Text(group, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp))
                Row(Modifier.padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (u in units) Chip(u) { onInsert(" $u"); close() }
                }
            }
        }
        if (!compact) MenuButton(Sym.CURRENCY_EXCHANGE, "Insert a currency") { close ->
            for (cur in CURRENCIES) DropdownMenuItem(text = { Text(cur) }, onClick = { onInsert(cur); close() })
            DropdownMenuItem(text = { Text("in EUR") }, onClick = { onInsert(" in EUR"); close() })
            DropdownMenuItem(text = { Text("in USD") }, onClick = { onInsert(" in USD"); close() })
        }
        MenuButton(Sym.FUNCTION, "Functions and formats") { close ->
            for ((f, what) in FUNCTIONS) DropdownMenuItem(
                text = { Text(f.trim()) },
                trailingIcon = { Text(what, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                onClick = { onInsert(f); close() },
            )
        }
        if (!compact) ToolButton(Sym.PERCENT, "Percent") { onInsert("%") }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ToolButton(sym: String, label: String, selected: Boolean = false, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState(),
    ) {
        val scheme = MaterialTheme.colorScheme
        IconButton(
            onClick = onClick,
            modifier = Modifier.semantics { contentDescription = label }
                .clip(if (selected) RoundedCornerShape(14.dp) else CircleShape)
                .background(if (selected) scheme.secondaryContainer else scheme.surfaceContainerHigh.copy(alpha = 0f)),
        ) { SymIcon(sym, tint = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant) }
    }
}

@Composable
private fun MenuButton(sym: String, label: String, content: @Composable (close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ToolButton(sym, label, selected = open) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(18.dp)) {
            content { open = false }
        }
    }
}

@Composable
fun DisplayPill(d: Display, onCycle: () -> Unit, onCopy: (String) -> Unit, modifier: Modifier = Modifier, big: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (d.selection) scheme.tertiaryContainer else if (d.value == null) scheme.surfaceContainerHighest else scheme.primaryContainer
    val fg = if (d.selection) scheme.onTertiaryContainer else if (d.value == null) scheme.onSurfaceVariant else scheme.onPrimaryContainer
    Surface(
        shape = CircleShape, color = bg, contentColor = fg,
        modifier = modifier.height(if (big) 52.dp else 44.dp).padding(start = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 18.dp, end = 6.dp)) {
            Column(Modifier.widthIn(min = 64.dp, max = 260.dp).clickable(enabled = d.selection, onClickLabel = "Next statistic", onClick = onCycle)) {
                Text(d.label.uppercase(), style = TextStyle(fontFamily = SummaFonts.sans, fontSize = 10.5.sp, fontWeight = FontWeight(700), letterSpacing = 0.8.sp), maxLines = 1)
                AnimatedContent(
                    targetState = d.value ?: "—",
                    transitionSpec = {
                        (slideInVertically(spring(dampingRatio = 0.7f, stiffness = 500f)) { it / 2 } + fadeIn()) togetherWith
                            (slideOutVertically { -it / 2 } + fadeOut())
                    },
                    label = "display",
                ) { v ->
                    // Long answers shrink to fit instead of being cut off.
                    androidx.compose.foundation.text.BasicText(
                        v, maxLines = 1,
                        style = TextStyle(fontFamily = SummaFonts.display, fontWeight = FontWeight(820), fontFeatureSettings = "tnum", color = fg),
                        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 13.sp, maxFontSize = if (big) 25.sp else 21.sp, stepSize = 1.sp),
                    )
                }
            }
            val v = d.value
            Box(
                Modifier.padding(start = 8.dp).size(36.dp).clip(CircleShape).background(fg.copy(alpha = 0.12f))
                    .clickable(enabled = v != null) { if (v != null) onCopy(v) }
                    .semantics { contentDescription = "Copy answer" },
                contentAlignment = Alignment.Center,
            ) { SymIcon(if (d.selection) Sym.FUNCTIONS else Sym.CONTENT_COPY, size = 19.sp, tint = fg) }
        }
    }
}
