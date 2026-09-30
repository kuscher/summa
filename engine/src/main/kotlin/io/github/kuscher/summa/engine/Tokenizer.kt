package io.github.kuscher.summa.engine

import java.time.LocalDate
import java.time.LocalTime

/** A date the user typed; [year] is null when they left it out ("Dec 25"). */
data class DateLit(val year: Int?, val month: Int, val day: Int)

/** Things the tokenizer needs from the sheet: variable names and places. */
class LexEnv(
    val settings: EngineSettings,
    /** Known variable names (lowercased word keys) → canonical name. */
    val vars: PhraseTrie<String> = PhraseTrie(),
    val places: PhraseTrie<Place> = Places.trie,
    val exactPlaces: PhraseTrie<Place> = Places.exact,
    /** Units the user defined ("1 watermelon = 20 lb"), singular and plural. */
    val units: PhraseTrie<UnitDef> = PhraseTrie(),
    /** Functions the user defined ("tip(x) = x × 18%"). */
    val funcs: PhraseTrie<String> = PhraseTrie(),
)

class Lexed(val tokens: List<Tok>, val spans: List<Span>)

/** Stage two: raw pieces → meaningful tokens, with unknown words kept only as comment spans. */
object Tokenizer {
    private val ORDINAL = setOf("st", "nd", "rd", "th")
    private val TIME_UNIT_IDS = setOf("s", "min", "h", "day", "week", "month", "year", "quarter", "fortnight", "workday", "night")

    fun lex(text: String, offset: Int, env: LexEnv): Lexed {
        val st = env.settings
        val raw = Scanner.scan(text, st)
        val toks = ArrayList<Tok>()
        var i = 0
        fun add(t: Tok, first: Raw) { t.tight = !first.spaceBefore; toks += t }
        fun tok(type: T, from: Raw, to: Raw, v: Any? = null) =
            Tok(type, from.start + offset, to.end + offset, text.substring(from.start, to.end), v)

        while (i < raw.size) {
            val r = raw[i]
            val nx = raw.getOrNull(i + 1)
            val nx2 = raw.getOrNull(i + 2)

            // ---- clock times: 8:30, 8:30 am, 3pm, 12:15:30
            if (r.kind == 'n' && r.num != null && r.num.isInteger && r.num.signum >= 0) {
                val h = r.num.num.toInt().takeIf { r.num.num.bitLength() < 16 } ?: -1
                if (h in 0..24 && nx?.text == ":" && !nx.spaceBefore && nx2?.kind == 'n' && !nx2.spaceBefore && nx2.text.length == 2) {
                    var end = i + 2
                    val m = nx2.text.toInt()
                    var sec = 0
                    if (raw.getOrNull(end + 1)?.text == ":" && raw.getOrNull(end + 2)?.let { it.kind == 'n' && it.text.length == 2 && !it.spaceBefore } == true) {
                        sec = raw[end + 2].text.toInt(); end += 2
                    }
                    var hour = h
                    val ap = raw.getOrNull(end + 1)?.takeIf { it.kind == 'w' && it.lower in setOf("am", "pm", "a.m", "p.m") }
                    if (ap != null && h in 1..12) {
                        hour = (h % 12) + if (ap.lower.startsWith("p")) 12 else 0
                        end += 1
                        if (raw.getOrNull(end + 1)?.text == "." && !raw[end + 1].spaceBefore) end += 1
                    }
                    if (m in 0..59 && sec in 0..59 && hour in 0..23) {
                        add(tok(T.TIME, r, raw[end], LocalTime.of(hour, m, sec)), r); i = end + 1; continue
                    }
                }
                if (h in 1..12 && nx?.kind == 'w' && nx.lower in setOf("am", "pm")) {
                    val hour = (h % 12) + if (nx.lower == "pm") 12 else 0
                    add(tok(T.TIME, r, nx, LocalTime.of(hour, 0)), r); i += 2; continue
                }
            }

            // ---- dates: 2026-12-25, 25/12/2026, Dec 25 [2026], 25 Dec [2026], December 25th, 2026
            dateAt(raw, i, st)?.let { (n, d) ->
                add(tok(T.DATE, r, raw[i + n - 1], d), r); i += n; return@let
            }?.let { continue }

            // ---- line references: line6, line 6, line #6
            if (r.kind == 'w') {
                val m = Regex("^line(\\d+)$", RegexOption.IGNORE_CASE).matchEntire(r.text)
                if (m != null) { add(tok(T.LINEREF, r, r, m.groupValues[1].toInt()), r); i++; continue }
                if (r.lower == "line") {
                    var j = i + 1
                    if (raw.getOrNull(j)?.text == "#") j++
                    val n = raw.getOrNull(j)
                    if (n?.kind == 'n' && n.num?.isInteger == true) { add(tok(T.LINEREF, r, n, n.num.num.toInt()), r); i = j + 1; continue }
                }
            }

            // ---- tags: #food
            if (r.text == "#" && nx?.kind == 'w' && !nx.spaceBefore) {
                add(tok(T.TAG, r, nx, nx.lower), r); i += 2; continue
            }

            // ---- numbers, fractions, mixed numbers, scales, ordinals
            if (r.kind == 'n' && r.num != null) {
                var value = r.num
                var end = i
                // 1/3 written tight is a fraction literal (so "1/2 km" is half a kilometre).
                if (nx?.text == "/" && !nx.spaceBefore && nx2?.kind == 'n' && !nx2.spaceBefore && nx2.num != null &&
                    r.num.isInteger && nx2.num.isInteger && !nx2.num.isZero && raw.getOrNull(i + 3)?.text != "/") {
                    value = r.num / nx2.num; end = i + 2
                }
                val prevIsCurrency = toks.lastOrNull()?.let { it.type == T.CUR && it.end == r.start + offset }
                // Scale suffix glued on: 5k, 2.5M, $5m, 3bn, 10T
                val sfx = raw.getOrNull(end + 1)?.takeIf { it.kind == 'w' && !it.spaceBefore }
                if (sfx != null) {
                    val mult = when (sfx.text) {
                        "k", "K" -> 1_000L
                        "M", "MM" -> 1_000_000L
                        "G" -> 1_000_000_000L
                        "T" -> 1_000_000_000_000L
                        "bn", "Bn", "bln" -> 1_000_000_000L
                        "mn", "mln" -> 1_000_000L
                        "tn" -> 1_000_000_000_000L
                        "m" -> if (prevIsCurrency == true) 1_000_000L else null
                        "b", "B" -> if (prevIsCurrency == true) 1_000_000_000L else null
                        "t" -> if (prevIsCurrency == true) 1_000_000_000_000L else null
                        else -> null
                    }
                    if (mult != null) { value *= Rational.of(mult); end += 1 }
                    else if (sfx.lower in ORDINAL && r.num.isInteger) {
                        // 1st, 2nd: ordinals are comments ("the 3rd payment").
                        i = end + 2; continue
                    }
                }
                val t = tok(T.NUM, r, raw[end], value)
                add(t, r)
                i = end + 1
                // Scale words: 1.4 million, 3 thousand, 2 dozen
                while (i < raw.size && raw[i].kind == 'w' && Lexicon.SCALE_WORDS.containsKey(raw[i].lower)) {
                    val sw = raw[i]
                    val m = Lexicon.SCALE_WORDS[sw.lower] ?: break
                    val prev = toks.removeAt(toks.size - 1)
                    val nt = Tok(T.NUM, prev.start, sw.end + offset, text.substring(prev.start - offset, sw.end), (prev.v as Rational) * Rational.of(m))
                    nt.tight = prev.tight; nt.gapBefore = prev.gapBefore
                    toks += nt
                    i++
                }
                continue
            }

            // ---- number words: "five hundred thirty three"
            if (r.kind == 'w' && Lexicon.NUMBER_WORDS.containsKey(r.lower) && env.vars.longest(listOf(r.lower), 0) == null) {
                var j = i
                var total = 0L
                var cur = 0L
                var any = false
                while (j < raw.size && raw[j].kind == 'w') {
                    val w = raw[j].lower
                    val n = Lexicon.NUMBER_WORDS[w]
                    val sc = if (w in setOf("hundred", "thousand", "million", "billion", "trillion", "dozen")) Lexicon.SCALE_WORDS[w] else null
                    when {
                        n != null -> { cur += n; any = true }
                        sc == 100L -> cur = maxOf(cur, 1) * 100
                        sc != null -> { total += maxOf(cur, 1) * sc; cur = 0 }
                        w == "and" && any && raw.getOrNull(j + 1)?.let { Lexicon.NUMBER_WORDS.containsKey(it.lower) } == true -> {}
                        else -> break
                    }
                    j++
                }
                if (any) {
                    add(tok(T.NUM, r, raw[j - 1], Rational.of(total + cur)), r); i = j; continue
                }
            }

            // ---- a scale word on its own: "to nearest hundred"
            if (r.kind == 'w' && r.lower in setOf("hundred", "thousand", "million", "billion", "trillion", "dozen") && env.vars.longest(listOf(r.lower), 0) == null) {
                add(tok(T.NUM, r, r, Rational.of(Lexicon.SCALE_WORDS[r.lower]!!)), r); i++; continue
            }

            // ---- currency symbols: $, €, US$, CN¥
            if (r.kind == 'c') {
                val code = when (r.text) {
                    "$" -> st.dollar
                    "¥", "￥" -> st.yen
                    else -> Currencies.symbols[r.text]
                }
                if (code != null) { add(tok(T.CUR, r, r, code), r); i++; continue }
            }

            // ---- operators and punctuation ("%" may start a phrase: "% increase from")
            val pctPhrase = r.text == "%" && (Lexicon.ci.longest(raw.map { if (it.kind == 'w') it.lower else it.text }, i)?.first ?: 0) > 1
            if (r.kind == 's' && !pctPhrase) {
                val t: Tok? = when (r.text) {
                    "+" -> tok(T.OP, r, r, "+")
                    "-", "−", "–", "—" -> tok(T.OP, r, r, "-")
                    "*", "×", "·", "⋅", "∙", "✕" -> tok(T.OP, r, r, "*")
                    "/", "÷", "∕" -> tok(T.OP, r, r, "/")
                    "^", "**", "ˆ" -> tok(T.OP, r, r, "^")
                    "&", "&&" -> tok(T.OP, r, r, "&")
                    "|", "||" -> tok(T.OP, r, r, "|")
                    "<<" -> tok(T.OP, r, r, "<<")
                    ">>" -> tok(T.OP, r, r, ">>")
                    "%" -> tok(T.PCT, r, r)
                    "‰" -> tok(T.PCT, r, r, "permille")
                    "(", "[", "（" -> tok(T.LPAREN, r, r)
                    ")", "]", "）" -> tok(T.RPAREN, r, r)
                    ",", ";" -> tok(T.COMMA, r, r)
                    "=", "==" -> tok(T.EQ, r, r)
                    "!" -> tok(T.BANG, r, r)
                    "@" -> tok(T.AT, r, r)
                    "→", "->" -> tok(T.KW, r, r, K.TO)
                    else -> if (r.text.all { it in Scanner.SUPERS }) tok(T.SUPER, r, r, superValue(r.text)) else null
                }
                if (t != null && r.text !in setOf("'", "\"", "′", "″")) { add(t, r); i++; continue }
            }

            // ---- phrases: variables, units, currencies, keywords, functions, places
            val lowKeys = raw.map { if (it.kind == 'w') it.lower else it.text }
            val exactKeys = raw.map { it.text }
            val cands = ArrayList<Triple<Int, Int, () -> Tok?>>() // (length, priority, make)
            env.vars.longest(lowKeys, i)?.let { (n, name) -> cands += Triple(n, 5) { tok(T.VAR, r, raw[i + n - 1], name) } }
            env.funcs.longest(lowKeys, i)?.let { (n, name) -> cands += Triple(n, 6) { tok(T.FUNC, r, raw[i + n - 1], name) } }
            env.units.longest(lowKeys, i)?.let { (n, u) -> cands += Triple(n, 6) { tok(T.UNIT, r, raw[i + n - 1], UnitExpr.of(u)) } }
            Lexicon.cs.longest(exactKeys, i)?.let { (n, f) -> cands += Triple(n, 4) { f(text.substring(r.start, raw[i + n - 1].end))?.let { reTok(it, r, raw[i + n - 1], offset, text) } } }
            Lexicon.ci.longest(lowKeys, i)?.let { (n, f) -> cands += Triple(n, 3) { f(text.substring(r.start, raw[i + n - 1].end))?.let { reTok(it, r, raw[i + n - 1], offset, text) } } }
            env.places.longest(lowKeys, i)?.let { (n, p) -> cands += Triple(n, 2) { tok(T.PLACE, r, raw[i + n - 1], p) } }
            env.exactPlaces.longest(exactKeys, i)?.let { (n, p) -> cands += Triple(n, 2) { tok(T.PLACE, r, raw[i + n - 1], p) } }
            val best = cands.sortedWith(compareByDescending<Triple<Int, Int, () -> Tok?>> { it.first }.thenByDescending { it.second })
            var placed = false
            for ((n, _, make) in best) {
                val t = make() ?: continue
                if (!accept(t, toks, raw, i, n, env.vars)) continue
                add(fixUp(t, toks, raw, i + n), r)
                i += n
                placed = true
                break
            }
            if (placed) continue

            // ---- anything else is a comment word
            add(tok(T.WORD, r, r), r)
            i++
        }
        return finish(toks, text, offset)
    }

    private fun reTok(t: Tok, from: Raw, to: Raw, offset: Int, text: String) =
        Tok(t.type, from.start + offset, to.end + offset, text.substring(from.start, to.end), t.v)

    /** A line with "at" and a percentage reads "for", "over" and "loan" as finance words. */
    private fun financeContext(raw: List<Raw>, vars: PhraseTrie<String>? = null): Boolean {
        val at = raw.indexOfFirst { it.lower == "at" || it.text == "@" }
        if (at < 0) return false
        if (raw.any { it.text == "%" || it.lower in setOf("percent", "pct") }) return true
        // "at rate for 30 years", where rate = 6%
        return vars != null && vars.longest(raw.map { it.lower }, at + 1) != null
    }

    /** "incl 20%", "without VAT": the tax words need a percentage (or a variable) after them. */
    private fun pctFollows(raw: List<Raw>, at: Int, vars: PhraseTrie<String>?): Boolean {
        val a = raw.getOrNull(at) ?: return false
        if (a.kind == 'n') return raw.getOrNull(at + 1)?.let { it.text == "%" || it.lower in setOf("percent", "pct") } == true
        return vars != null && a.kind == 'w' && vars.longest(raw.map { it.lower }, at) != null
    }

    /** Context rules that stop short symbols and common words from being read as units. */
    private fun accept(t: Tok, toks: List<Tok>, raw: List<Raw>, i: Int, n: Int, vars: PhraseTrie<String>? = null): Boolean {
        val prev = toks.lastOrNull { it.type != T.WORD }
        val prevAny = toks.lastOrNull()
        val next = raw.getOrNull(i + n)
        if (t.type == T.KW) when {
            t.v == K.FOR || t.v == K.LOAN || t.v == K.INTEREST || t.v == K.SIMPLE_INTEREST -> return financeContext(raw, vars)
            (t.v as? String)?.startsWith(K.CMP) == true -> return financeContext(raw, vars)
            t.v == K.INCL || t.v == K.EXCL -> return prevAny != null && pctFollows(raw, i + n, vars)
        }
        when (t.type) {
            T.UNIT -> {
                val u = t.v as UnitExpr
                val isSymbol = t.text.length <= 3 && Lexicon.cs.longest(listOf(t.text), 0) != null && t.text !in setOf("mph", "kph", "kmh", "rpm", "psi", "atm")
                if (isSymbol || t.text in setOf("'", "\"", "′", "″")) {
                    // Symbols need a number (or a unit/conversion word) right before them.
                    val ok = prevAny != null && (prevAny.type in setOf(T.NUM, T.UNIT, T.RPAREN, T.SUPER, T.VAR, T.CONST, T.LINEREF, T.AGG) ||
                        (prevAny.type == T.KW && prevAny.v in setOf(K.IN, K.TO, K.AS, K.PER, K.SQUARE, K.CUBIC)) ||
                        (prevAny.type == T.OP && prevAny.v == "/") || prevAny.type == T.CUR)
                    if (!ok) return false
                    if (t.text in setOf("'", "\"", "′", "″") && prevAny.type != T.NUM) return false
                }
                // "min"/"max" as statistics when followed by "(" or "of".
                if (t.text.lowercase() in setOf("min") && next != null && (next.text == "(" || next.lower == "of")) return false
                return u.terms.isNotEmpty()
            }
            T.KW -> {
                // "a"/"an"/"each"/"every" only mean "per" before a unit: "$24 a day".
                if (t.v == K.PER && t.text.lowercase() in setOf("a", "an", "each", "every")) {
                    val nx = next ?: return false
                    val isUnit = (Lexicon.ci.longest(listOf(nx.lower), 0)?.second?.invoke(nx.text)?.type == T.UNIT) ||
                        (Lexicon.cs.longest(listOf(nx.text), 0)?.second?.invoke(nx.text)?.type == T.UNIT)
                    if (!isUnit || prev == null) return false
                }
                return true
            }
            T.CONST -> {
                if (t.text == "e") {
                    // "e" only as a stand-alone constant, not a stray letter.
                    if (prev?.type == T.NUM && prevAny?.end == t.start) return false
                }
                return true
            }
            T.FMT -> {
                // Formats only right after in/to/as (so "dec" alone stays December/prose).
                val f = t.v as Fmt
                if ((f.kind == FmtKind.DP || f.kind == FmtKind.SIGFIG) && prevAny?.type == T.NUM) return true
                return prevAny?.type == T.KW && prevAny.v in setOf(K.IN, K.TO, K.AS) || (prevAny?.type == T.NUM && t.text.lowercase() in setOf("x"))
            }
            T.CUR -> {
                // Currency names that are also common words ("real", "rand", "won", "sol", "gold") need a number near them.
                val w = t.text.lowercase()
                if (w in setOf("real", "rand", "won", "sol", "gold", "silver", "dong", "lira", "franc", "mark", "yen")) {
                    val nearNum = prevAny?.type == T.NUM || (prevAny?.type == T.KW && prevAny.v in setOf(K.IN, K.TO, K.AS)) ||
                        next?.kind == 'n'
                    if (!nearNum) return false
                }
                return true
            }
            T.DAYWORD -> return true
            T.PLACE -> {
                // Places only count next to time words: "3 pm Lisbon", "in Tokyo", "Berlin time".
                if (prevAny != null && (prevAny.type in setOf(T.TIME, T.NOW, T.DATE, T.RPAREN) ||
                            (prevAny.type == T.KW && prevAny.v in setOf(K.IN, K.TO, K.TIME, K.AS)))) return true
                val nx = next ?: return false
                return nx.lower in setOf("time", "in", "to") || (nx.kind == 'w' && Lexicon.ci.longest(listOf(nx.lower), 0)?.second?.invoke(nx.text)?.v == K.TIME)
            }
            T.AGG -> {
                if (t.v == "min" || t.v == "max") return true
                return true
            }
            else -> return true
        }
    }

    /** Turns a lone "x" between two operands into multiplication, and "over 30 years" into a loan term. */
    private fun fixUp(t: Tok, toks: List<Tok>, raw: List<Raw>, nextIdx: Int): Tok {
        if (t.type == T.OP && t.text.lowercase() == "over" && financeContext(raw)) return Tok(T.KW, t.start, t.end, t.text, K.FOR)
        if (t.type == T.FMT && t.text.lowercase() == "x" && toks.lastOrNull()?.type == T.NUM) {
            val nx = raw.getOrNull(nextIdx)
            if (nx != null && (nx.kind == 'n' || nx.kind == 'c' || nx.text == "(")) return Tok(T.OP, t.start, t.end, t.text, "*")
        }
        return t
    }

    private fun superValue(s: String): Int {
        var neg = false
        var v = 0
        for (c in s) {
            if (c == '⁻') { neg = true; continue }
            v = v * 10 + Scanner.SUPERS.indexOf(c)
        }
        return if (neg) -v else v
    }

    /** Drops comment words and bracketed notes, marking gaps, and builds the colour spans. */
    private fun finish(all: List<Tok>, text: String, offset: Int): Lexed {
        // "x" as a word between two operands means times.
        val list = all.toMutableList()
        // "$300k loan at 6% for 30 years": the finance word leads, whatever the word order.
        val lead = list.indexOfFirst { it.type == T.KW && it.v in setOf(K.LOAN, K.INTEREST, K.SIMPLE_INTEREST) }
        if (lead > 0) list.add(0, list.removeAt(lead))
        for (k in list.indices) {
            val t = list[k]
            if (t.type == T.WORD && t.text.lowercase() == "x" && k > 0 && k + 1 < list.size &&
                list[k - 1].type in setOf(T.NUM, T.UNIT, T.RPAREN, T.CUR, T.VAR) &&
                list[k + 1].type in setOf(T.NUM, T.CUR, T.LPAREN, T.VAR, T.CONST)) {
                list[k] = Tok(T.OP, t.start, t.end, t.text, "*").also { it.tight = t.tight }
            }
        }
        // Brackets that contain a comment word are notes: "(for iPhone 16)", "(3 partners)".
        val comment = BooleanArray(list.size)
        val stack = ArrayList<Int>()
        for ((k, t) in list.withIndex()) {
            if (t.type == T.WORD) comment[k] = true
            if (t.type == T.LPAREN) stack += k
            if (t.type == T.RPAREN && stack.isNotEmpty()) {
                val open = stack.removeAt(stack.size - 1)
                val inner = (open + 1 until k)
                val hasWord = inner.any { list[it].type == T.WORD }
                val yearOnly = inner.count() == 1 && list[open + 1].type == T.NUM && (list[open + 1].v as Rational).let { it.isInteger && it >= Rational.of(1900) && it <= Rational.of(2100) } && open > 0
                if (hasWord || yearOnly) for (m in open..k) comment[m] = true
            }
        }
        val kept = ArrayList<Tok>()
        val spans = ArrayList<Span>()
        var gap = false
        for ((k, t) in list.withIndex()) {
            if (comment[k] && k == list.size - 1) kept.lastOrNull()?.gapAfter = true
            if (comment[k]) {
                gap = true
                spans += Span(t.start, t.end, Style.COMMENT)
                continue
            }
            if (gap) { t.gapBefore = true; kept.lastOrNull()?.gapAfter = true; gap = false }
            kept += t
            spans += Span(t.start, t.end, styleOf(t))
        }
        return Lexed(kept, spans)
    }

    fun styleOf(t: Tok): Style = when (t.type) {
        T.NUM, T.CONST -> Style.NUMBER
        T.UNIT, T.CUR, T.PCT -> Style.UNIT
        T.VAR -> Style.VARIABLE
        T.FUNC -> Style.FUNCTION
        T.AGG, T.LINEREF -> Style.REFERENCE
        T.DATE, T.TIME, T.NOW, T.DAYWORD, T.PLACE -> Style.DATE
        T.WORD -> Style.COMMENT
        T.TAG -> Style.TAG
        T.OP, T.EQ, T.BANG, T.SUPER, T.AT, T.COMMA, T.LPAREN, T.RPAREN -> Style.OPERATOR
        else -> Style.KEYWORD
    }

    /** Dates at [i]; returns (raw pieces used, date). */
    private fun dateAt(raw: List<Raw>, i: Int, st: EngineSettings): Pair<Int, DateLit>? {
        val r = raw[i]
        fun intOf(x: Raw?) = x?.takeIf { it.kind == 'n' && it.num?.isInteger == true && it.num.signum >= 0 && !it.text.contains(st.decimalSep) }?.num?.num?.toInt()
        // ISO: 2026-12-25
        if (r.kind == 'n' && r.text.length == 4) {
            val y = intOf(r)
            if (y != null && raw.getOrNull(i + 1)?.text == "-" && raw.getOrNull(i + 3)?.text == "-" &&
                raw.subList(i + 1, minOf(i + 5, raw.size)).none { it.spaceBefore }) {
                val m = intOf(raw.getOrNull(i + 2)); val d = intOf(raw.getOrNull(i + 4))
                if (m != null && d != null && valid(y, m, d)) return 5 to DateLit(y, m, d)
            }
        }
        // Numeric with a 4-digit year: 25/12/2026, 12/25/2026, 25.12.2026
        if (r.kind == 'n' && r.text.length <= 2) {
            val sep = raw.getOrNull(i + 1)?.text
            if ((sep == "/" || sep == "." || sep == "-") && raw.getOrNull(i + 3)?.text == sep &&
                raw.getOrNull(i + 4)?.text?.length == 4 && raw.subList(i + 1, minOf(i + 5, raw.size)).none { it.spaceBefore }) {
                val a = intOf(r); val b = intOf(raw.getOrNull(i + 2)); val y = intOf(raw.getOrNull(i + 4))
                if (a != null && b != null && y != null) {
                    val (d, m) = if (st.dayFirst || sep == ".") a to b else b to a
                    if (valid(y, m, d)) return 5 to DateLit(y, m, d)
                    if (valid(y, d, m)) return 5 to DateLit(y, d, m)
                }
            }
        }
        // Month name first: Dec 25, December 25th, Dec 25 2026, Dec 25, 2026, March 2027
        if (r.kind == 'w') {
            val month = Lexicon.MONTHS[r.lower.removeSuffix(".")]
            if (month != null) {
                var j = i + 1
                if (raw.getOrNull(j)?.text == ".") j++
                val d = intOf(raw.getOrNull(j))
                if (d != null && d in 1..31 && raw[j].text.length <= 2) {
                    j++
                    if (raw.getOrNull(j)?.let { it.kind == 'w' && it.lower in ORDINAL && !it.spaceBefore } == true) j++
                    var y: Int? = null
                    val k0 = if (raw.getOrNull(j)?.text == ",") j + 1 else j
                    val yr = raw.getOrNull(k0)
                    if (yr != null && yr.kind == 'n' && yr.text.length == 4) { y = intOf(yr); j = k0 + 1 }
                    if (valid(y ?: 2024, month.value, d)) return (j - i) to DateLit(y, month.value, d)
                }
                // "March 2027"
                val yr = raw.getOrNull(j)
                if (yr != null && yr.kind == 'n' && yr.text.length == 4 && month.value in 1..12) {
                    val y = intOf(yr)
                    if (y != null) return (j + 1 - i) to DateLit(y, month.value, 1)
                }
            }
        }
        // Day first: 25 Dec, 25th December 2026, 1 Jan
        if (r.kind == 'n' && r.text.length <= 2) {
            val d = intOf(r)
            var j = i + 1
            if (raw.getOrNull(j)?.let { it.kind == 'w' && it.lower in ORDINAL && !it.spaceBefore } == true) j++
            if (raw.getOrNull(j)?.lower == "of") j++
            val mw = raw.getOrNull(j)
            val month = if (mw?.kind == 'w') Lexicon.MONTHS[mw.lower.removeSuffix(".")] else null
            if (d != null && month != null && d in 1..31) {
                j++
                var y: Int? = null
                val k0 = if (raw.getOrNull(j)?.text == ",") j + 1 else j
                val yr = raw.getOrNull(k0)
                if (yr != null && yr.kind == 'n' && yr.text.length == 4) { y = intOf(yr); j = k0 + 1 }
                // "5 mar" could be "5 marathons"; require the full word or a 3-letter month.
                if (valid(y ?: 2024, month.value, d)) return (j - i) to DateLit(y, month.value, d)
            }
        }
        return null
    }

    private fun valid(y: Int, m: Int, d: Int): Boolean =
        m in 1..12 && d in 1..31 && try { LocalDate.of(y, m, d); true } catch (_: Exception) { false }

}
