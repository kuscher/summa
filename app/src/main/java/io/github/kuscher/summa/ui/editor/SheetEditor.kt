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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.zIndex
import io.github.kuscher.summa.engine.Completion
import io.github.kuscher.summa.engine.Completions
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
            // Autocomplete for the word before the cursor.
            val sel = session.state.selection
            val word = remember(text.toString(), sel) { wordBefore(text, sel) }
            val vars = remember(ev) { ev?.result?.lines?.mapNotNull { it.declares }?.distinct().orEmpty() }
            var dismissedAt by remember { mutableIntStateOf(-1) }
            var pick by remember { mutableIntStateOf(0) }
            var navigated by remember { mutableStateOf(false) }
            var focused by remember { mutableStateOf(false) }
            val suggestions = remember(word, vars) {
                if (word == null || (word.prefix.length < 2 && !word.afterNumber)) emptyList()
                else Completions.suggest(word.prefix, vars, word.afterNumber, limit = 6)
            }
            val showAc = focused && suggestions.isNotEmpty() && word != null && dismissedAt != sel.start
            LaunchedEffect(word?.start, word?.prefix) { pick = 0; navigated = false }
            fun accept(c: Completion) {
                val w = word ?: return
                session.state.edit {
                    replace(w.start, sel.start, c.insert)
                    selection = TextRange(w.start + c.insert.length)
                }
            }
            val focusReq = remember { androidx.compose.ui.focus.FocusRequester() }
            io.github.kuscher.summa.ui.DebugHooks.focus = { runCatching { focusReq.requestFocus() } }
            Box(Modifier.weight(1f).padding(end = 12.dp).zIndex(5f)) {
            BasicTextField(
                state = session.state,
                modifier = Modifier.fillMaxWidth().focusRequester(focusReq).semantics { contentDescription = "Sheet" }
                    .onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { e ->
                        if (!showAc || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.Tab -> { accept(suggestions[pick.coerceIn(0, suggestions.lastIndex)]); true }
                            Key.Enter, Key.NumPadEnter -> if (navigated) { accept(suggestions[pick.coerceIn(0, suggestions.lastIndex)]); true } else false
                            Key.DirectionDown -> { pick = (pick + 1) % suggestions.size; navigated = true; true }
                            Key.DirectionUp -> { pick = (pick - 1 + suggestions.size) % suggestions.size; navigated = true; true }
                            Key.Escape -> { dismissedAt = sel.start; true }
                            else -> false
                        }
                    },
                textStyle = textStyle,
                cursorBrush = SolidColor(primary),
                outputTransformation = syntax,
                onTextLayout = { get -> layout = get() },
                lineLimits = TextFieldLineLimits.MultiLine(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Text),
            )
            val l = layout
            if (showAc && l != null && sel.start <= l.layoutInput.text.length) {
                val r = l.getCursorRect(sel.start)
                Box(Modifier.offset { IntOffset(r.left.toInt() - 12.dp.roundToPx(), r.bottom.toInt() + 4.dp.roundToPx()) }.zIndex(10f)) {
                    AutocompleteList(suggestions, pick) { accept(it) }
                }
            }
            }
            AnswerRail(session, layout, starts, activeLine, railWidth, settings, compact, onMessage, onInsert)
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
private fun AutocompleteList(items: List<Completion>, pick: Int, onPick: (Completion) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    androidx.compose.material3.Surface(shape = RoundedCornerShape(18.dp), color = scheme.surfaceContainer, shadowElevation = 6.dp, tonalElevation = 2.dp) {
        androidx.compose.foundation.layout.Column(Modifier.padding(6.dp).width(260.dp)) {
            items.forEachIndexed { i, c ->
                val on = i == pick
                Row(
                    Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(if (on) 50 else 10))
                        .background(if (on) scheme.tertiaryContainer else scheme.surfaceContainer)
                        .clickable { onPick(c) }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val sym = when (c.kind) {
                        Completion.Kind.VARIABLE -> Sym.DATA_OBJECT; Completion.Kind.UNIT -> Sym.STRAIGHTEN
                        Completion.Kind.CURRENCY -> Sym.PAYMENTS; Completion.Kind.FUNCTION -> Sym.FUNCTION
                        else -> Sym.PUBLIC
                    }
                    SymIcon(sym, size = 17.sp, tint = if (on) scheme.onTertiaryContainer else scheme.onSurfaceVariant)
                    Text(c.insert, Modifier.padding(start = 10.dp).weight(1f), maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight(620)), color = if (on) scheme.onTertiaryContainer else scheme.onSurface)
                    Text(c.detail, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
                        color = if (on) scheme.onTertiaryContainer else scheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp).widthIn(max = 130.dp))
                }
            }
            Text("Tab to insert · Esc to hide", Modifier.padding(start = 12.dp, top = 4.dp, bottom = 2.dp), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
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
                    Triple(i, answer, Pair(top, l.getLineBottom(ln) - top) to lr)
                }
            }
            for ((i, answer, geo) in placed) {
                val (pos, lr) = geo
                val (top, h) = pos
                androidx.compose.runtime.key(i) {
                    Answer(
                        answer, i, i == activeLine, lr.isTotal, settings, lr.value,
                        onConvert = { target -> convertLine(session, i, target) },
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
    answer: String, index: Int, active: Boolean, isTotal: Boolean, settings: Settings, value: io.github.kuscher.summa.engine.Value?,
    onConvert: (String) -> Unit, modifier: Modifier,
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
            val targets = remember(value) { Completions.conversionsFor(value) }
            if (targets.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    SymIcon(Sym.SWAP_HORIZ, size = 20.sp, tint = scheme.onSurfaceVariant)
                    Text("Convert to", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                }
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.padding(start = 48.dp, end = 12.dp, top = 6.dp, bottom = 6.dp).widthIn(max = 280.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                ) {
                    for (target in targets) {
                        androidx.compose.material3.Surface(
                            onClick = { onConvert(target); menu = false }, shape = RoundedCornerShape(10.dp),
                            color = scheme.surfaceContainerHighest,
                        ) { Text(target, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text("Insert reference") }, leadingIcon = { SymIcon(Sym.LINK, size = 20.sp) },
                trailingIcon = { Text("line ${index + 1}", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant) },
                onClick = { onInsert("line${index + 1}"); menu = false },
            )
        }
    }
}
