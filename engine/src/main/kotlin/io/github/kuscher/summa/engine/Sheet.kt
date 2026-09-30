package io.github.kuscher.summa.engine

import java.time.ZonedDateTime

enum class LineKind { EMPTY, HEADING, COMMENT, DIVIDER, EXPR }

/** The answer for one line, plus everything the editor needs to colour it. */
class LineResult(
    val kind: LineKind,
    /** The value other lines see (money rounded to cents, as in Soulver). */
    val value: Value?,
    /** The exact value (what a variable keeps). */
    val exact: Value?,
    val answer: String?,
    val spans: List<Span>,
    /** The variable this line declares, if any. */
    val declares: String? = null,
    /** A `sum`/`total`/`average` line over the lines above. */
    val isTotal: Boolean = false,
    val timeDependent: Boolean = false,
    val rateDependent: Boolean = false,
    val tags: Set<String> = emptySet(),
    val referenced: Set<Int> = emptySet(),
    val error: String? = null,
)

class SheetResult(val lines: List<LineResult>, private val settings: EngineSettings, private val rates: Rates, private val now: ZonedDateTime) {
    val anyTimeDependent get() = lines.any { it.timeDependent }
    val anyRateDependent get() = lines.any { it.rateDependent }

    /** Statistic over chosen lines (0-based), e.g. a selection: sum, avg, count, min, max, median. */
    fun stat(kind: String, indexes: Collection<Int>): Value? {
        val vals = indexes.sorted().mapNotNull { lines.getOrNull(it)?.value }.filter { it is Qty || it is Pct }
        if (vals.isEmpty()) return null
        return compatibleStat(kind, vals, settings, rates, now)
    }

    /**
     * The sheet's floating total: the bottom-most lines that add up, leaving out declarations
     * and lines other lines refer to (so a subtotal isn't counted twice).
     */
    fun total(kind: String = "sum"): Value? {
        val referenced = lines.flatMap { it.referenced }.toSet()
        val vals = lines.withIndex().filter { (i, l) ->
            l.value != null && l.declares == null && !l.isTotal && (i + 1) !in referenced && (l.value is Qty)
        }.map { it.value.value!! }
        if (vals.isEmpty()) return null
        return compatibleStat(kind, vals, settings, rates, now)
    }

    fun format(v: Value): String = Formatter(settings).format(v)
}

/** Adds up what can be added, starting from the bottom-most value; skips the rest. */
internal fun compatibleStat(kind: String, vals: List<Value>, settings: EngineSettings, rates: Rates, now: ZonedDateTime): Value? {
    val ev = Evaluator(EvalCtx(settings, rates, now))
    val used = ArrayList<Value>()
    var anchor: Value? = null
    for (v in vals.asReversed()) {
        if (v !is Qty) continue
        if (anchor == null) { anchor = v; used += v; continue }
        try { ev.add(anchor, v, 1); used += v } catch (_: EvalError) {}
    }
    if (used.isEmpty()) return null
    return try { stat(kind, used.asReversed(), ev) } catch (_: EvalError) { null }
}

/**
 * Evaluates a whole sheet, line by line. Holds a small parse cache so re-evaluating after a
 * keystroke only re-tokenizes the lines that changed.
 */
class SheetEngine(
    var settings: EngineSettings = EngineSettings(),
    var rates: Rates = Rates.bundled,
) {
    private val cache = object : LinkedHashMap<String, Parsed>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Parsed>?) = size > 4000
    }

    /** Global variables from the definitions sheet or settings. */
    var globals: Map<String, Value> = emptyMap()

    fun evaluate(text: String, now: ZonedDateTime = ZonedDateTime.now(settings.zone)): SheetResult =
        evaluate(text.split('\n'), now)

    fun evaluate(lines: List<String>, now: ZonedDateTime = ZonedDateTime.now(settings.zone)): SheetResult {
        val out = ArrayList<LineResult>(lines.size)
        val vars = LinkedHashMap<String, Value>(globals)
        val varTrie = PhraseTrie<String>()
        for (k in globals.keys) varTrie.put(Lexicon.keys(k), k)
        var varSig = varTrie.size
        val fmt = Formatter(settings)
        for ((idx, line) in lines.withIndex()) {
            val r = try {
                evalLine(idx, line, out, vars, varTrie, now, fmt)
            } catch (e: StackOverflowError) {
                LineResult(LineKind.EXPR, null, null, null, emptyList(), error = "too deep")
            }
            out += r
            if (r.kind == LineKind.DIVIDER) {
                // A divider starts a fresh scope for variables, like Soulver.
                vars.clear(); vars.putAll(globals)
            }
            r.declares?.let { name ->
                r.exact?.let { vars[name] = it }
                if (varTrie.longest(Lexicon.keys(name), 0)?.first != Lexicon.keys(name).size) varTrie.put(Lexicon.keys(name), name)
            }
            varSig = varTrie.size
        }
        return SheetResult(out, settings, rates, now)
    }

    private class Parsed(val kind: LineKind, val spans: List<Span>, val tokens: List<Tok>, val declares: String?, val op: String?, val tags: Set<String>)

    private fun evalLine(
        idx: Int, line: String, above: List<LineResult>, vars: Map<String, Value>, varTrie: PhraseTrie<String>,
        now: ZonedDateTime, fmt: Formatter,
    ): LineResult {
        val key = line + "\u0000" + vars.keys.joinToString("\u0001")
        val parsed = cache.getOrPut(key) { parse(line, varTrie) }
        if (parsed.kind != LineKind.EXPR) return LineResult(parsed.kind, null, null, null, parsed.spans)
        if (parsed.tokens.isEmpty()) {
            // "total cost =" with nothing after it: a subtotal into a variable.
            return LineResult(LineKind.EXPR, null, null, null, parsed.spans, declares = null, tags = parsed.tags)
        }
        var toks = parsed.tokens
        // A line that starts with an operator (or "in EUR") continues from the line above.
        val first = toks.first()
        val prevValue = above.lastOrNull { it.value != null }?.value
        val continues = prevValue != null && (
            (first.type == T.OP && first.v in setOf("+", "*", "/", "^") ) ||
            (first.type == T.OP && first.v == "-" && line.trimStart().let { it.startsWith("- ") || it.startsWith("− ") }) ||
            (first.type == T.KW && first.v in setOf(K.IN, K.TO, K.AS) && toks.size > 1) ||
            (first.type == T.PCT && false))
        if (continues) toks = listOf(Tok(T.AGG, first.start, first.start, "", "prev")) + toks

        val ctx = EvalCtx(
            settings, rates, now, vars,
            lines = above.map { it.value },
            prev = prevValue,
            aggregate = { kind -> blockStat(kind, above) },
            tagStat = { kind, tag -> tagStat(kind, tag, above) },
        )
        val isTotal = toks.size == 1 && toks[0].type == T.AGG && toks[0].v != "prev"
        var error: String? = null
        var value: Value? = null
        for (node in candidates(toks)) {
            try {
                val v = Evaluator(ctx).eval(node)
                value = finalize(v, ctx)
                break
            } catch (e: EvalError) {
                error = e.message
            } catch (e: ArithmeticException) {
                error = e.message
            }
        }
        if (value == null) {
            return LineResult(LineKind.EXPR, null, null, null, parsed.spans, declares = null, isTotal = isTotal,
                timeDependent = ctx.timeDependent, rateDependent = ctx.rateDependent, tags = parsed.tags, error = error)
        }
        val shown = value
        val rounded = roundMoney(value)
        val answer = try { fmt.format(shown) } catch (_: Exception) { null }
        return LineResult(
            LineKind.EXPR, rounded, value, answer, parsed.spans,
            declares = parsed.declares, isTotal = isTotal,
            timeDependent = ctx.timeDependent, rateDependent = ctx.rateDependent,
            tags = parsed.tags, referenced = ctx.referencedLines.toSet(),
        )
    }

    /** Ranges become their difference; formats stay for display. */
    private fun finalize(v: Value, ctx: EvalCtx): Value = when (v) {
        is Range -> {
            val ev = Evaluator(ctx)
            val a = v.from; val b = v.to
            if (a is Moment && b is Moment) ev.momentDiff(a, b) else ev.add(b, a, -1)
        }
        else -> v
    }

    /** Money rounds to the currency's decimals for totals and references. */
    private fun roundMoney(v: Value): Value = when (v) {
        is Qty -> {
            val cur = v.unit.currency
            if (cur != null && v.unit.isCurrencyOnly) {
                val digits = Currencies.get(cur)?.let { if (it.crypto) 8 else it.digits } ?: 2
                Qty(v.num.round(digits), v.unit)
            } else v
        }
        is Shown -> roundMoney(v.value)
        else -> v
    }

    /** Parse attempts in order: the whole line, then the last valid expression in it. */
    private fun candidates(toks: List<Tok>): Sequence<Node> = sequence {
        val whole = try { Parser(toks).parseAll() } catch (_: ParseError) { null } catch (_: RuntimeException) { null }
        if (whole != null) yield(whole)
        if (toks.size > 60) return@sequence
        val found = ArrayList<Triple<Int, Int, Node>>()
        for (s in toks.indices) {
            val t = toks[s]
            if (t.type in setOf(T.OP, T.RPAREN, T.COMMA, T.PCT, T.BANG, T.SUPER, T.EQ, T.AT) && !(t.type == T.OP && t.v == "-")) continue
            if (t.type == T.KW && t.v in setOf(K.IN, K.TO, K.AS, K.OF, K.ON, K.OFF, K.PER, K.AND, K.IS, K.UNTIL, K.SINCE, K.AGO, K.FROM_NOW)) continue
            val r = Parser(toks, s, toks.size).parsePrefix() ?: continue
            if (r.second > s) found += Triple(s, r.second, r.first)
        }
        // Prefer the expression that ends last; among those, the longest.
        found.sortWith(compareByDescending<Triple<Int, Int, Node>> { it.second }.thenBy { it.first })
        for ((_, _, n) in found) yield(n)
    }

    // ------------------------------------------------------------------ block statistics

    /** Lines a `sum` adds up: upwards until a heading, divider, another total, or a blank line. */
    private fun block(above: List<LineResult>): List<LineResult> {
        val out = ArrayList<LineResult>()
        var i = above.size - 1
        while (i >= 0 && above[i].kind == LineKind.EMPTY) i--
        while (i >= 0) {
            val l = above[i]
            if (l.kind == LineKind.HEADING || l.kind == LineKind.DIVIDER || l.kind == LineKind.EMPTY || l.isTotal) break
            if (l.kind == LineKind.EXPR) out += l
            i--
        }
        out.reverse()
        return out
    }

    private fun blockStat(kind: String, above: List<LineResult>): Value? {
        val lines = block(above).filter { it.value != null }
        if (lines.isEmpty()) return null
        val ev = Evaluator(EvalCtx(settings, rates, ZonedDateTime.now(settings.zone)))
        if (kind == "sum") {
            // Percentage lines inside a block mark the running total up: $500, 10% → $550.
            var total: Value? = null
            for (l in lines) {
                val v = l.value!!
                total = when {
                    total == null -> if (v is Qty) v else null
                    v is Pct -> try { ev.add(total, v, 1) } catch (_: EvalError) { total }
                    else -> try { ev.add(total, v, 1) } catch (_: EvalError) { total }
                }
            }
            return total
        }
        return compatibleStat(kind, lines.mapNotNull { it.value }, settings, rates, ZonedDateTime.now(settings.zone))
    }

    private fun tagStat(kind: String, tag: String, above: List<LineResult>): Value? {
        val vals = above.filter { tag in it.tags && it.value != null && !it.isTotal }.map { it.value!! }
        if (vals.isEmpty()) return null
        return compatibleStat(kind, vals, settings, rates, ZonedDateTime.now(settings.zone))
    }

    // ------------------------------------------------------------------ line structure

    private val LABEL = Regex("^(\\s*)([^:=\"]*?\\p{L}[^:=\"]*?):(?=\\s|$)")
    private val ASSIGN = Regex("^\\s*([\\p{L}_][\\p{L}\\p{N}_ ()']*?)\\s*(\\+=|-=|:=|=)(?!=)\\s*(.*)$")

    private fun parse(line: String, varTrie: PhraseTrie<String>): Parsed {
        val spans = ArrayList<Span>()
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return Parsed(LineKind.EMPTY, spans, emptyList(), null, null, emptySet())
        if (trimmed.startsWith("#") && (trimmed.length == 1 || trimmed[1] == ' ' || trimmed[1] == '#')) {
            spans += Span(0, line.length, Style.HEADING)
            return Parsed(LineKind.HEADING, spans, emptyList(), null, null, emptySet())
        }
        if (trimmed.startsWith("//")) {
            spans += Span(0, line.length, Style.COMMENT)
            return Parsed(LineKind.COMMENT, spans, emptyList(), null, null, emptySet())
        }
        if (trimmed.length >= 3 && trimmed.all { it == '-' || it == '—' || it == '–' }) {
            spans += Span(0, line.length, Style.COMMENT)
            return Parsed(LineKind.DIVIDER, spans, emptyList(), null, null, emptySet())
        }
        // Blank out comments and quotes, keeping offsets.
        val chars = line.toCharArray()
        fun blank(from: Int, to: Int) { for (k in from until to) chars[k] = ' '; spans += Span(from, to, Style.COMMENT) }
        run {
            var k = 0
            while (k < chars.size) {
                val c = chars[k]
                if (c == '/' && k + 1 < chars.size && chars[k + 1] == '/' && !(k > 0 && chars[k - 1] == ':')) { blank(k, chars.size); break }
                if (c == '#' && k > 0 && chars[k - 1] == ' ' && k + 1 < chars.size && chars[k + 1] == ' ') { blank(k, chars.size); break }
                if (c == '"' || c == '“' || c == '”') {
                    val close = (k + 1 until chars.size).firstOrNull { chars[it] == '"' || chars[it] == '”' || chars[it] == '“' }
                    if (close != null && close > k + 1) { blank(k, close + 1); k = close + 1; continue }
                }
                k++
            }
        }
        var text = String(chars)
        // URLs and e-mail addresses are notes.
        for (m in Regex("(https?://\\S+|www\\.\\S+|\\S+@\\S+\\.\\w+)").findAll(text)) {
            spans += Span(m.range.first, m.range.last + 1, Style.COMMENT)
            text = text.replaceRange(m.range, " ".repeat(m.value.length))
        }
        var start = 0
        // Label: "Rent: $1000"
        LABEL.find(text)?.let { m ->
            val labelText = m.groupValues[2]
            val before = labelText.trimEnd().lastOrNull()
            if (before != null && !labelText.trimStart().lowercase().let { it == "http" || it == "https" }) {
                spans += Span(m.groups[2]!!.range.first, m.range.last + 1, Style.LABEL)
                start = m.range.last + 1
            }
        }
        var declares: String? = null
        var op: String? = null
        var exprStart = start
        val rest = text.substring(start)
        ASSIGN.find(rest)?.let { m ->
            val name = m.groupValues[1].trim()
            val nameOk = name.isNotEmpty() && !name.first().isDigit() && name.split(' ').size <= 6 &&
                    Lexicon.keys(name).all { it.isNotEmpty() }
            if (nameOk) {
                declares = name
                op = m.groupValues[2]
                val nameRange = m.groups[1]!!.range
                spans += Span(start + nameRange.first, start + nameRange.last + 1, Style.VARIABLE)
                val opRange = m.groups[2]!!.range
                spans += Span(start + opRange.first, start + opRange.last + 1, Style.OPERATOR)
                exprStart = start + m.groups[3]!!.range.first.coerceAtLeast(opRange.last + 1)
            }
        }
        val exprText = if (exprStart <= text.length) text.substring(exprStart) else ""
        val lexed = Tokenizer.lex(exprText, exprStart, LexEnv(settings, varTrie))
        spans += lexed.spans
        var toks = lexed.tokens
        // Cut a trailing "= answer" someone typed after an expression ("2 + 2 = 4").
        val eq = toks.indexOfFirst { it.type == T.EQ && it.text == "=" }
        if (eq > 0) toks = toks.subList(0, eq)
        // "x += 5" → x + 5
        val name = declares
        if (name != null && (op == "+=" || op == "-=") && toks.isNotEmpty()) {
            toks = listOf(Tok(T.VAR, exprStart, exprStart, name, name), Tok(T.OP, exprStart, exprStart, op!!.take(1), op!!.take(1))) + toks
        }
        val tags = toks.filter { it.type == T.TAG }.map { it.v as String }.toSet() +
            lexed.tokens.filter { it.type == T.TAG }.map { it.v as String }
        // Tags at the end of a line label it; they aren't part of the maths.
        val mathToks = if (toks.lastOrNull()?.type == T.TAG && toks.size > 1 && toks.none { it.type == T.AGG }) toks.filter { it.type != T.TAG } else toks
        return Parsed(LineKind.EXPR, spans.sortedBy { it.start }, mathToks, declares, op, tags)
    }
}
