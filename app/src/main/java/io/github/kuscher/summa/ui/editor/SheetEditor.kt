package io.github.kuscher.summa.ui.editor

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import io.github.kuscher.summa.engine.Completions
import io.github.kuscher.summa.data.Settings
import io.github.kuscher.summa.engine.LineKind
import io.github.kuscher.summa.engine.Style
import io.github.kuscher.summa.ui.Sym
import io.github.kuscher.summa.ui.SymIcon
import io.github.kuscher.summa.ui.copyToClipboard
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

/** Above this many lines, syntax colours drop their weight changes (see SheetEditor). */
private const val LIGHT_SYNTAX_LINES = 400

private fun spanStyle(style: Style, c: SummaColors, light: Boolean = false): SpanStyle? = if (light) when (style) {
    Style.HEADING -> SpanStyle(color = c.heading, fontWeight = FontWeight(760))
    Style.REFERENCE, Style.AGGREGATE -> SpanStyle(color = c.reference)
    Style.NUMBER -> null
    else -> spanStyle(style, c)?.let { SpanStyle(color = it.color) }
} else when (style) {
    Style.NUMBER -> SpanStyle(color = c.number, fontWeight = FontWeight(560))
    Style.UNIT -> SpanStyle(color = c.unit, fontWeight = FontWeight(560))
    Style.VARIABLE -> SpanStyle(color = c.variable, fontWeight = FontWeight(620))
    Style.KEYWORD, Style.OPERATOR -> SpanStyle(color = c.keyword)
    Style.FUNCTION -> SpanStyle(color = c.function, fontWeight = FontWeight(600))
    Style.LABEL -> SpanStyle(color = c.label)
    Style.COMMENT -> SpanStyle(color = c.comment)
    Style.HEADING -> SpanStyle(color = c.heading, fontSize = 1.3.em, fontWeight = FontWeight(760), fontFamily = SummaFonts.round, letterSpacing = (-0.01).em)
    Style.REFERENCE, Style.AGGREGATE -> SpanStyle(color = c.reference, fontWeight = FontWeight(620))
    Style.DATE -> SpanStyle(color = c.date, fontWeight = FontWeight(560))
    Style.TAG -> SpanStyle(color = c.variable, fontWeight = FontWeight(600))
}

/**
 * The sheet, Numi-style: text on the left with quiet syntax colours, answers on the right as plain
 * text aligned with the first visual line of their line. Nothing floats over it; autocomplete is
 * grey text after the cursor (Tab or → accepts it).
 */
@Composable
fun SheetEditor(
    session: Session,
    settings: Settings,
    modifier: Modifier = Modifier,
    scroll: ScrollState,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    compact: Boolean = false,
    onMessage: (String) -> Unit = {},
) {
    val c = LocalSummaColors.current
    val ev = session.evaluated
    val text = session.state.text
    val starts = remember(text.toString()) { lineStarts(text) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val syntax = remember { SyntaxColors() }
    // Long sheets get lighter colouring: colour-only spans (weight changes split text shaping)
    // and only for lines near the viewport, so re-layouts of thousands of lines stay quick.
    val light = (ev?.result?.lines?.size ?: 0) > LIGHT_SYNTAX_LINES
    val styledLines by remember(scroll, light) {
        derivedStateOf {
            val l = layout
            if (!light || l == null) 0 to Int.MAX_VALUE
            else {
                val view = scroll.viewportSize.takeIf { it > 0 } ?: 2400
                val bucket = scroll.value / 1024 * 1024
                val top = (bucket - 2 * view).coerceAtLeast(0).toFloat()
                val bottom = (bucket + 3 * view + 1024).toFloat().coerceAtMost(l.size.height.toFloat())
                l.getLineForVerticalPosition(top).let { l.getLineStart(it) } to l.getLineForVerticalPosition(bottom).let { l.getLineEnd(it) }
            }
        }
    }
    syntax.styles = remember(ev, c, styledLines) {
        val out = ArrayList<Triple<Int, Int, SpanStyle>>()
        if (ev != null) {
            val evStarts = lineStarts(ev.text)
            val (from, to) = styledLines
            for ((i, lr) in ev.result.lines.withIndex()) {
                val base = evStarts.getOrNull(i) ?: continue
                if (base > to || (evStarts.getOrNull(i + 1) ?: Int.MAX_VALUE) < from) continue
                for (sp in lr.spans) spanStyle(sp.style, c, light)?.let { out += Triple(base + sp.start, base + sp.end, it) }
            }
        }
        out
    }
    val density = LocalDensity.current
    val primary = MaterialTheme.colorScheme.primary
    val fontSize = settings.textSize
    val textStyle = TextStyle(
        fontFamily = SummaFonts.sans, fontSize = fontSize.sp, lineHeight = (fontSize * 1.76f).sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val answerWidth = if (compact) 132.dp else 220.dp
    val gutter = if (settings.lineNumbers && !compact) 40.dp else 0.dp

    // Only rows near the viewport get answers and line numbers (a 2,000-line sheet stays smooth).
    // Bucketed so scrolling recomposes every few hundred pixels, not every frame.
    val visible by remember(scroll) {
        derivedStateOf {
            val view = scroll.viewportSize.takeIf { it > 0 } ?: 2400
            val bucket = scroll.value / 512 * 512
            (bucket - view).toFloat() to (bucket + 2 * view + 512).toFloat()
        }
    }
    Box(modifier.fillMaxSize().verticalScroll(scroll)) {
        Row(Modifier.fillMaxWidth().padding(contentPadding)) {
            if (gutter > 0.dp) Box(Modifier.width(gutter)) {
                val l = layout
                if (l != null) {
                    val len = l.layoutInput.text.length
                    val (from, to) = visible
                    val rows = starts.withIndex().filter { it.value <= len && l.getLineTop(l.getLineForOffset(it.value)).let { t -> t >= from && t <= to } }.map { (i, s) ->
                        val ln = l.getLineForOffset(s)
                        Triple(i, l.getLineTop(ln), l.getLineBottom(ln) - l.getLineTop(ln))
                    }
                    for ((i, top, h) in rows) {
                        androidx.compose.runtime.key(i) {
                            Box(Modifier.offset { IntOffset(0, top.toInt()) }.height(with(density) { h.toDp() }).width(gutter).padding(end = 12.dp), contentAlignment = Alignment.CenterEnd) {
                                Text("${i + 1}", style = TextStyle(fontFamily = SummaFonts.sans, fontSize = 12.sp, color = c.comment, fontFeatureSettings = "tnum"))
                            }
                        }
                    }
                }
            }
            // Autocomplete: the best suggestion for the word before the cursor, drawn as grey
            // text after it when the cursor ends its line.
            val sel = session.state.selection
            val word = remember(text.toString(), sel) { wordBefore(text, sel) }
            val defs = ev?.result?.definitions ?: io.github.kuscher.summa.engine.Definitions.EMPTY
            val vars = remember(ev) { defs.vars.keys.toList() }
            var dismissedAt by remember { mutableIntStateOf(-1) }
            var focused by remember { mutableStateOf(false) }
            val atLineEnd = sel.collapsed && (sel.start >= text.length || text[sel.start] == '\n')
            val suggestion = remember(word, vars, defs, atLineEnd) {
                if (word == null || !atLineEnd || (word.prefix.length < 2 && !word.afterNumber)) null
                else Completions.suggest(word.prefix, vars, word.afterNumber, limit = 4, defs = defs)
                    .firstOrNull { it.insert.length > word.prefix.length && it.insert.startsWith(word.prefix, ignoreCase = true) }
            }
            val ghost = if (focused && suggestion != null && word != null && dismissedAt != sel.start) suggestion.insert.substring(word.prefix.length) else null
            fun accept() {
                val w = word ?: return
                val c0 = suggestion ?: return
                session.state.edit {
                    replace(w.start, sel.start, c0.insert)
                    selection = TextRange(w.start + c0.insert.length)
                }
            }
            val measurer = rememberTextMeasurer()
            val ghostColor = c.comment
            val focusReq = remember { androidx.compose.ui.focus.FocusRequester() }
            io.github.kuscher.summa.ui.DebugHooks.focus = { runCatching { focusReq.requestFocus() } }
            BasicTextField(
                state = session.state,
                modifier = Modifier.weight(1f).padding(end = 16.dp).focusRequester(focusReq).semantics { contentDescription = "Sheet" }
                    .onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { e ->
                        if (ghost == null || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.Tab, Key.DirectionRight -> { accept(); true }
                            Key.Escape -> { dismissedAt = sel.start; true }
                            else -> false
                        }
                    }
                    .drawWithContent {
                        drawContent()
                        val l = layout
                        if (ghost != null && l != null && sel.start <= l.layoutInput.text.length) {
                            val r = l.getCursorRect(sel.start)
                            // Sit on the same baseline as the text being typed.
                            val g = measurer.measure(ghost, textStyle.copy(color = ghostColor))
                            val baseline = l.getLineBaseline(l.getLineForOffset(sel.start))
                            drawText(g, topLeft = Offset(r.left + 1.dp.toPx(), baseline - g.firstBaseline))
                        }
                    },
                textStyle = textStyle,
                cursorBrush = SolidColor(primary),
                outputTransformation = syntax,
                onTextLayout = { get -> layout = get() },
                lineLimits = TextFieldLineLimits.MultiLine(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Text),
            )
            Answers(session, layout, starts, answerWidth, settings, onMessage, visible)
        }
    }
}

/** Rewrites line [index] to end in "in <target>", replacing a conversion that's already there. */
fun convertLine(session: Session, index: Int, target: String) {
    val text = session.state.text.toString()
    val starts = lineStarts(text)
    val s = starts.getOrNull(index) ?: return
    val e = starts.getOrNull(index + 1)?.minus(1) ?: text.length
    val line = text.substring(s, e)
    val comment = line.indexOf("//").let { if (it < 0) line.length else it }
    val body = line.substring(0, comment).trimEnd()
    val tail = line.substring(comment)
    val m = Regex("\\s(in|to|as|into)\\s+[^\\s].*$").find(body)
    val base = if (m != null) body.substring(0, m.range.first) else body
    val phrase = when (target) { "%" -> " as %"; "hex", "binary", "fraction", "sci" -> " in $target"; else -> " in $target" }
    val newLine = base + phrase + (if (tail.isNotEmpty()) "  $tail" else "")
    session.state.edit { replace(s, e, newLine) }
}

class WordAt(val start: Int, val prefix: String, val afterNumber: Boolean)

/** The word being typed before the cursor, if the cursor sits at the end of a word. */
fun wordBefore(text: CharSequence, sel: TextRange): WordAt? {
    if (!sel.collapsed) return null
    val end = sel.start
    if (end > text.length) return null
    if (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_')) return null
    var s = end
    while (s > 0 && (text[s - 1].isLetterOrDigit() || text[s - 1] == '_' || text[s - 1] == '°' || text[s - 1] == 'µ')) s--
    while (s < end && text[s].isDigit()) s++
    if (s >= end || !(text[s].isLetter() || text[s] == '°' || text[s] == 'µ')) return null
    val lineStart = text.lastIndexOf('\n', s - 1) + 1
    val before = text.substring(lineStart, s)
    if (before.trimStart().startsWith("//") || before.trimStart().startsWith("#")) return null
    val prev = before.trimEnd().lastOrNull()
    val afterNumber = prev != null && (prev.isDigit() || Character.getType(prev) == Character.CURRENCY_SYMBOL.toInt())
    return WordAt(s, text.substring(s, end), afterNumber)
}

@Composable
private fun Answers(
    session: Session, layout: TextLayoutResult?, starts: IntArray, width: androidx.compose.ui.unit.Dp,
    settings: Settings, onMessage: (String) -> Unit, visible: Pair<Float, Float>,
) {
    val density = LocalDensity.current
    val ev = session.evaluated
    val l = layout
    val height = with(density) { (l?.size?.height ?: 0).toDp() }
    Box(Modifier.width(width).height(height.coerceAtLeast(24.dp))) {
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
                    if (top < visible.first || top > visible.second) null
                    else Triple(i, answer, Pair(top, l.getLineBottom(ln) - top) to lr)
                }
            }
            for ((i, answer, geo) in placed) {
                val (pos, lr) = geo
                val (top, h) = pos
                androidx.compose.runtime.key(i) {
                    Answer(
                        session, answer, i, lr.isTotal, settings, lr.value,
                        Modifier.align(Alignment.TopEnd).offset { IntOffset(0, top.toInt()) }.height(with(density) { h.toDp() }),
                        onMessage,
                    )
                }
            }
        }
    }
}

/** One answer: plain text. Click copies it (it reads "Copied" for a moment); right-click for more. */
@Composable
private fun Answer(
    session: Session, answer: String, index: Int, isTotal: Boolean, settings: Settings, value: io.github.kuscher.summa.engine.Value?,
    modifier: Modifier, onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    var convert by remember { mutableStateOf(false) }
    val copied = session.copiedLine == index
    val fg = if (copied) scheme.onSurfaceVariant else scheme.primary
    Box(modifier, contentAlignment = Alignment.CenterEnd) {
        androidx.compose.runtime.key(fg) {
        Box(
            Modifier
                .onSecondaryClick { menu = true }
                // Drag an answer into another app or window. (Keyed on the colour so the drag
                // node's cached drawing follows theme changes.)
                .dragAndDropSource { _ ->
                    androidx.compose.ui.draganddrop.DragAndDropTransferData(
                        android.content.ClipData.newPlainText("Summa answer", answer),
                        flags = android.view.View.DRAG_FLAG_GLOBAL,
                    )
                }
                .clickable(onClickLabel = "Copy", role = androidx.compose.ui.semantics.Role.Button) {
                    copyToClipboard(context, answer)
                    session.flashCopied(index)
                }
                .semantics {
                    contentDescription = if (copied) "Copied" else "Line ${index + 1}: $answer"
                    // TalkBack can't right-click: the answer menu as an action.
                    customActions = listOf(androidx.compose.ui.semantics.CustomAccessibilityAction("Answer options") { menu = true; true })
                },
        ) {
            // Long answers shrink a little to fit the column (phones) instead of being cut off.
            androidx.compose.foundation.text.BasicText(
                if (copied) "Copied" else answer, maxLines = 1,
                style = TextStyle(
                    fontFamily = SummaFonts.round, fontSize = settings.textSize.sp, fontWeight = FontWeight(if (isTotal) 760 else 600),
                    fontFeatureSettings = "tnum", color = fg, textAlign = TextAlign.End,
                ),
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                    minFontSize = (settings.textSize * 0.7f).sp, maxFontSize = settings.textSize.sp, stepSize = 0.5.sp),
            )
        }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(14.dp)) {
            DropdownMenuItem(text = { Text("Copy") }, onClick = { copyToClipboard(context, answer); session.flashCopied(index); menu = false })
            DropdownMenuItem(text = { Text("Copy line with answer") }, onClick = {
                val t = session.state.text.toString()
                val st = lineStarts(t)
                val line = t.substring(st[index], st.getOrNull(index + 1)?.minus(1) ?: t.length).trim()
                copyToClipboard(context, "$line = $answer"); session.flashCopied(index); menu = false
            })
            val targets = remember(value) { Completions.conversionsFor(value) }
            if (targets.isNotEmpty()) DropdownMenuItem(
                text = { Text("Convert to") }, trailingIcon = { SymIcon(Sym.CHEVRON_RIGHT, size = 18.sp) },
                onClick = { menu = false; convert = true },
            )
        }
        DropdownMenu(expanded = convert, onDismissRequest = { convert = false }, shape = RoundedCornerShape(14.dp)) {
            for (target in remember(value) { Completions.conversionsFor(value) }) {
                DropdownMenuItem(text = { Text("in $target") }, onClick = { convertLine(session, index, target); convert = false })
            }
        }
    }
}
