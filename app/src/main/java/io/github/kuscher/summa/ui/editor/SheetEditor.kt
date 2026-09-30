package io.github.kuscher.summa.ui.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.kuscher.summa.data.Settings
import io.github.kuscher.summa.engine.LineKind
import io.github.kuscher.summa.engine.Style
import io.github.kuscher.summa.ui.Sym
import io.github.kuscher.summa.ui.SymIcon
import io.github.kuscher.summa.ui.copyToClipboard
import io.github.kuscher.summa.ui.numberOnly
import io.github.kuscher.summa.ui.onSecondaryClick
import io.github.kuscher.summa.ui.theme.LocalSummaColors
import io.github.kuscher.summa.ui.theme.SummaColors
import io.github.kuscher.summa.ui.theme.SummaFonts

/** Start offsets of each line in [text]. */
fun lineStarts(text: CharSequence): IntArray {
    val out = ArrayList<Int>()
    out += 0
    for (i in text.indices) if (text[i] == '\n') out += i + 1
    return out.toIntArray()
}

fun lineOf(starts: IntArray, offset: Int): Int {
    var lo = 0
    var hi = starts.size - 1
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (starts[mid] <= offset) lo = mid else hi = mid - 1
    }
    return lo
}

/** Styles the text as it's drawn: numbers, units, variables, headings… without changing it. */
private class SyntaxColors : OutputTransformation {
    var styles by mutableStateOf<List<Triple<Int, Int, SpanStyle>>>(emptyList())
    override fun TextFieldBuffer.transformOutput() {
        val len = length
        for ((s, e, st) in styles) {
            if (s >= len) continue
            addStyle(st, s, minOf(e, len))
        }
    }
}

private fun spanStyle(style: Style, c: SummaColors): SpanStyle? = when (style) {
    Style.NUMBER -> SpanStyle(color = c.number, fontWeight = FontWeight(560))
    Style.UNIT -> SpanStyle(color = c.unit, fontWeight = FontWeight(560))
    Style.VARIABLE -> SpanStyle(color = c.variable, fontWeight = FontWeight(640))
    Style.KEYWORD, Style.OPERATOR -> SpanStyle(color = c.keyword)
    Style.FUNCTION -> SpanStyle(color = c.function, fontWeight = FontWeight(600))
    Style.LABEL -> SpanStyle(color = c.label)
    Style.COMMENT -> SpanStyle(color = c.comment)
    Style.HEADING -> SpanStyle(color = c.heading, fontSize = 1.3.em, fontWeight = FontWeight(760), fontFamily = SummaFonts.round, letterSpacing = (-0.01).em)
    Style.REFERENCE -> SpanStyle(color = c.reference, background = c.referenceBg, fontWeight = FontWeight(650))
    Style.AGGREGATE -> SpanStyle(color = c.reference, background = c.referenceBg, fontWeight = FontWeight(650))
    Style.DATE -> SpanStyle(color = c.date, fontWeight = FontWeight(560))
    Style.TAG -> SpanStyle(color = c.variable, fontWeight = FontWeight(600))
}

/**
 * The sheet: text on the left with live syntax colours, answers on the right in a tonal rail,
 * each answer aligned with the first visual line of its line.
 */
@Composable
fun SheetEditor(
    session: Session,
    settings: Settings,
    activeLine: Int,
    modifier: Modifier = Modifier,
    scroll: ScrollState,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    compact: Boolean = false,
    onInsert: (String) -> Unit = {},
    onMessage: (String) -> Unit = {},
) {
    val c = LocalSummaColors.current
    val ev = session.evaluated
    val text = session.state.text
    val starts = remember(text.toString()) { lineStarts(text) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val syntax = remember { SyntaxColors() }
    syntax.styles = remember(ev, c) {
        val out = ArrayList<Triple<Int, Int, SpanStyle>>()
        if (ev != null) {
            val evStarts = lineStarts(ev.text)
            for ((i, lr) in ev.result.lines.withIndex()) {
                val base = evStarts.getOrNull(i) ?: continue
                for (sp in lr.spans) spanStyle(sp.style, c)?.let { out += Triple(base + sp.start, base + sp.end, it) }
            }
        }
        out
    }
    val density = LocalDensity.current
    val primary = MaterialTheme.colorScheme.primary
    val fontSize = settings.textSize
    val textStyle = TextStyle(
        fontFamily = if (settings.mono) SummaFonts.mono else SummaFonts.sans,
        fontSize = fontSize.sp, lineHeight = (fontSize * 1.72f).sp,
        color = MaterialTheme.colorScheme.onSurface,
        fontFeatureSettings = if (settings.slashedZero) "zero" else null,
    )
    val railWidth = if (compact) (settings.answerWidth * 0.62f).dp.coerceIn(104.dp, 160.dp) else settings.answerWidth.dp
    val gutter = if (settings.lineNumbers && !compact) 40.dp else 12.dp

    Box(modifier.fillMaxSize().verticalScroll(scroll)) {
        Row(
            Modifier.fillMaxWidth().padding(contentPadding)
                .drawBehind {
                    // Current-line highlight across text and answers.
                    val l = layout ?: return@drawBehind
                    val s = starts.getOrNull(activeLine) ?: return@drawBehind
                    val e = starts.getOrNull(activeLine + 1)?.minus(1) ?: text.length
                    if (s > l.layoutInput.text.length) return@drawBehind
                    val top = l.getLineTop(l.getLineForOffset(s))
                    val bottom = l.getLineBottom(l.getLineForOffset(minOf(e, l.layoutInput.text.length)))
                    drawRoundRect(c.activeLine, Offset(4.dp.toPx(), top), Size(size.width - 8.dp.toPx(), bottom - top), CornerRadius(12.dp.toPx()))
                },
        ) {
            // Line numbers
            Box(Modifier.width(gutter)) {
                val l = layout
                if (settings.lineNumbers && !compact && l != null) {
                    val len = l.layoutInput.text.length
                    val rows = starts.withIndex().filter { it.value <= len }.map { (i, s) ->
                        val ln = l.getLineForOffset(s)
                        Triple(i, l.getLineTop(ln), l.getLineBottom(ln) - l.getLineTop(ln))
                    }
                    for ((i, top, h) in rows) {
                        androidx.compose.runtime.key(i) {
                            Box(Modifier.offset { IntOffset(0, top.toInt()) }.height(with(density) { h.toDp() }).width(gutter).padding(end = 10.dp), contentAlignment = Alignment.CenterEnd) {
                                Text("${i + 1}", style = TextStyle(fontFamily = SummaFonts.sans, fontSize = 12.sp, color = c.comment, fontFeatureSettings = "tnum"))
                            }
                        }
                    }
                }
            }
            BasicTextField(
                state = session.state,
                modifier = Modifier.weight(1f).padding(end = 12.dp).semantics { contentDescription = "Sheet" },
                textStyle = textStyle,
                cursorBrush = SolidColor(primary),
                outputTransformation = syntax,
                onTextLayout = { get -> layout = get() },
                lineLimits = TextFieldLineLimits.MultiLine(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Text),
            )
            AnswerRail(session, layout, starts, activeLine, railWidth, settings, compact, onMessage, onInsert)
        }
    }
}

@Composable
private fun AnswerRail(
    session: Session, layout: TextLayoutResult?, starts: IntArray, activeLine: Int, width: androidx.compose.ui.unit.Dp,
    settings: Settings, compact: Boolean, onMessage: (String) -> Unit, onInsert: (String) -> Unit,
) {
    val c = LocalSummaColors.current
    val density = LocalDensity.current
    val ev = session.evaluated
    val l = layout
    val height = with(density) { (l?.size?.height ?: 0).toDp() }
    Box(
        Modifier.width(width).height(height.coerceAtLeast(48.dp))
            .padding(end = if (compact) 6.dp else 10.dp)
            .background(c.rail, RoundedCornerShape(if (compact) 18.dp else 24.dp)),
    ) {
        if (l != null && ev != null) {
            val lines = ev.result.lines
            val textLen = l.layoutInput.text.length
            val placed = starts.withIndex().mapNotNull { (i, s) ->
                val lr = lines.getOrNull(i)
                val answer = lr?.answer
                if (lr == null || answer == null || lr.kind != LineKind.EXPR || s > textLen) null
                else {
                    val ln = l.getLineForOffset(s)
                    val top = l.getLineTop(ln)
                    Triple(i, answer, Pair(top, l.getLineBottom(ln) - top) to lr.isTotal)
                }
            }
            for ((i, answer, geo) in placed) {
                val (pos, isTotal) = geo
                val (top, h) = pos
                androidx.compose.runtime.key(i) {
                    Answer(
                        answer, i, i == activeLine, isTotal, settings,
                        Modifier.align(Alignment.TopEnd).offset { IntOffset(0, top.toInt()) }.height(with(density) { h.toDp() }),
                        onMessage, onInsert,
                    )
                }
            }
        }
    }
}

@Composable
private fun Answer(
    answer: String, index: Int, active: Boolean, isTotal: Boolean, settings: Settings, modifier: Modifier,
    onMessage: (String) -> Unit, onInsert: (String) -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    var menu by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        when { active -> scheme.primaryContainer; hovered -> scheme.surfaceContainerHighest; else -> scheme.primaryContainer.copy(alpha = 0f) },
        label = "answerBg",
    )
    val fg = if (active) scheme.onPrimaryContainer else scheme.onSurface
    val scale by animateFloatAsState(if (active) 1f else 1f, label = "s")
    Box(modifier.padding(end = 4.dp), contentAlignment = Alignment.CenterEnd) {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(bg)
                .hoverable(hover)
                .onSecondaryClick { menu = true }
                .clickable {
                    copyToClipboard(context, answer)
                    onMessage("Copied $answer")
                }
                .padding(horizontal = 10.dp, vertical = 1.dp)
                .semantics { contentDescription = "Line ${index + 1}, $answer. Click to copy." },
        ) {
            Text(
                answer, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
                style = TextStyle(
                    fontFamily = SummaFonts.round, fontSize = (settings.textSize * scale).sp, fontWeight = FontWeight(if (isTotal) 760 else 620),
                    fontFeatureSettings = "tnum" + if (settings.slashedZero) ", zero" else "", color = fg,
                ),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(18.dp)) {
            DropdownMenuItem(
                text = { Text("Copy") }, leadingIcon = { SymIcon(Sym.CONTENT_COPY, size = 20.sp) },
                trailingIcon = { Text(answer, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant) },
                onClick = { copyToClipboard(context, answer); onMessage("Copied $answer"); menu = false },
            )
            DropdownMenuItem(
                text = { Text("Copy number only") }, leadingIcon = { SymIcon(Sym.TAG, size = 20.sp) },
                onClick = {
                    val app = io.github.kuscher.summa.SummaApp.instance
                    val es = app.prefs.engineSettings()
                    val n = numberOnly(answer, es.decimalSep, es.groupSep)
                    copyToClipboard(context, n); onMessage("Copied $n"); menu = false
                },
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text("Insert reference") }, leadingIcon = { SymIcon(Sym.LINK, size = 20.sp) },
                trailingIcon = { Text("line ${index + 1}", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant) },
                onClick = { onInsert("line${index + 1}"); menu = false },
            )
        }
    }
}
