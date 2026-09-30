package io.github.kuscher.summa.engine

import java.math.BigInteger
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.util.Locale

/** How the engine reads and writes numbers, dates and money. The app derives it from the locale. */
data class EngineSettings(
    val decimalSep: Char = '.',
    val groupSep: Char = ',',
    /** What a bare `$` means (USD, or CAD in Canada…). */
    val dollar: String = "USD",
    val yen: String = "JPY",
    val zone: ZoneId = ZoneId.of("UTC"),
    val locale: Locale = Locale.US,
    /** Numeric dates like 25/12/2026 put the day first. */
    val dayFirst: Boolean = false,
    /** Trig functions take degrees unless a unit says otherwise. */
    val degrees: Boolean = true,
    val use24h: Boolean = false,
    /** Fixed decimals for plain numbers, or -1 for automatic (up to 2, more for tiny numbers). */
    val decimals: Int = -1,
    /** Money as "12,00 €" instead of "€12.00". */
    val currencyAfter: Boolean = false,
    val thousands: Boolean = true,
)

enum class T {
    NUM, OP, LPAREN, RPAREN, COMMA, UNIT, CUR, KW, FUNC, CONST, VAR, LINEREF, AGG, DATE, TIME, DAYWORD,
    NOW, PLACE, FMT, SUPER, PCT, BANG, WORD, TAG, AT, EQ, STAT
}

/** A span of the line and what it means. Payload types depend on [type]. */
class Tok(val type: T, val start: Int, val end: Int, val text: String, val v: Any? = null) {
    /** A comment word, quote or bracketed note was skipped right before this token. */
    var gapBefore = false
    /** No whitespace between this token and the previous one ("5km", "1K"). */
    var tight = false
    /** A comment word was skipped right after this token ("in" in "$7k in expenses"). */
    var gapAfter = false
    override fun toString() = "$type($text)"
}

/** Styles for syntax colouring, with character ranges into the line. */
enum class Style { NUMBER, UNIT, VARIABLE, KEYWORD, FUNCTION, LABEL, COMMENT, HEADING, OPERATOR, REFERENCE, AGGREGATE, DATE, TAG }

data class Span(val start: Int, val end: Int, val style: Style)

/** Keyword ids used by the parser. */
object K {
    const val IN = "in"; const val TO = "to"; const val AS = "as"; const val OF = "of"; const val ON = "on"
    const val OFF = "off"; const val PER = "per"; const val IS = "is"; const val WHAT = "what"; const val AND = "and"
    const val OF_WHAT = "of what"; const val ON_WHAT = "on what"; const val OFF_WHAT = "off what"
    const val AS_PCT_OF = "as % of"; const val AS_PCT_ON = "as % on"; const val AS_PCT_OFF = "as % off"
    const val IS_WHAT_PCT_OF = "is what % of"; const val IS_WHAT_PCT_ON = "is what % on"; const val IS_WHAT_PCT_OFF = "is what % off"
    const val IS_WHAT_PCT = "is what %"
    const val PCT_CHANGE = "% change"; const val PCT_INCREASE = "% increase"; const val PCT_DECREASE = "% decrease"
    const val FROM = "from"; const val UNTIL = "until"; const val SINCE = "since"; const val AGO = "ago"
    const val FROM_NOW = "from now"; const val BEFORE = "before"; const val AFTER = "after"; const val BETWEEN = "between"
    const val NEXT = "next"; const val LAST = "last"; const val THIS = "this"
    const val SQUARE = "square"; const val CUBIC = "cubic"; const val SQUARED = "squared"; const val CUBED = "cubed"
    const val POW_OF = "to the power of"; const val SQRT_OF = "square root of"; const val CBRT_OF = "cube root of"
    const val HALF = "half of"; const val DOUBLE = "double"; const val TRIPLE = "triple"; const val TWICE = "twice"
    const val TIME = "time"; const val IF = "if"; const val THEN = "then"; const val ELSE = "else"
    const val MULTIPLIED_BY = "multiplied by"; const val DIVIDED_BY = "divided by"; const val REMAINDER = "remainder of"
    const val PPI = "ppi"
}

/** A trie over token texts for multi-word phrases ("fluid ounces", "as a % of", "New York"). */
class PhraseTrie<V> {
    private class Node<V> { val next = HashMap<String, Node<V>>(); var value: V? = null }
    private val root = Node<V>()
    var size = 0; private set

    fun put(keys: List<String>, value: V, overwrite: Boolean = false) {
        var n = root
        for (k in keys) n = n.next.getOrPut(k) { Node() }
        if (n.value == null) size++
        if (n.value == null || overwrite) n.value = value
    }

    /** Longest match starting at [from]; returns (token count, value). */
    fun longest(keys: List<String>, from: Int): Pair<Int, V>? {
        var n = root
        var best: Pair<Int, V>? = null
        var i = from
        while (i < keys.size) {
            n = n.next[keys[i]] ?: break
            i++
            n.value?.let { best = (i - from) to it }
        }
        return best
    }
}

/** Raw pieces before phrase matching. */
internal class Raw(val kind: Char, val text: String, val start: Int, val end: Int, val spaceBefore: Boolean, val num: Rational? = null) {
    // kind: 'n' number, 'w' word, 's' symbol, 'c' currency symbol
    val lower = text.lowercase(Locale.ROOT)
    override fun toString() = "$kind:$text"
}

object Lexicon {
    /** Case-insensitive phrases → token factory. */
    internal val ci = PhraseTrie<(String) -> Tok?>()
    /** Case-sensitive phrases (unit symbols like "m", "M", "B", "b"). */
    internal val cs = PhraseTrie<(String) -> Tok?>()

    /** Trie keys for a phrase: words lowercased (for [ci]) or exact (for [cs]). */
    internal fun keys(phrase: String, exact: Boolean = false): List<String> =
        Scanner.scan(phrase, EngineSettings()).map { if (it.kind == 'w' && !exact) it.lower else it.text }

    private fun kw(id: String, vararg phrases: String) = phrases.forEach { p -> ci.put(keys(p), { Tok(T.KW, 0, 0, it, id) }) }
    private fun op(id: String, vararg phrases: String) = phrases.forEach { p -> ci.put(keys(p), { Tok(T.OP, 0, 0, it, id) }) }
    private fun fn(id: String, vararg phrases: String) = phrases.forEach { p -> ci.put(keys(p), { Tok(T.FUNC, 0, 0, it, id) }) }
    private fun fmt(f: Fmt, vararg phrases: String) = phrases.forEach { p -> ci.put(keys(p), { Tok(T.FMT, 0, 0, it, f) }) }
    private fun agg(id: String, vararg phrases: String) = phrases.forEach { p -> ci.put(keys(p), { Tok(T.AGG, 0, 0, it, id) }) }

    val NUMBER_WORDS = mapOf(
        "zero" to 0L, "one" to 1L, "two" to 2L, "three" to 3L, "four" to 4L, "five" to 5L, "six" to 6L, "seven" to 7L,
        "eight" to 8L, "nine" to 9L, "ten" to 10L, "eleven" to 11L, "twelve" to 12L, "thirteen" to 13L, "fourteen" to 14L,
        "fifteen" to 15L, "sixteen" to 16L, "seventeen" to 17L, "eighteen" to 18L, "nineteen" to 19L, "twenty" to 20L,
        "thirty" to 30L, "forty" to 40L, "fifty" to 50L, "sixty" to 60L, "seventy" to 70L, "eighty" to 80L, "ninety" to 90L,
    )
    val SCALE_WORDS = mapOf(
        "hundred" to 100L, "thousand" to 1_000L, "thousands" to 1_000L, "million" to 1_000_000L, "millions" to 1_000_000L,
        "billion" to 1_000_000_000L, "billions" to 1_000_000_000L, "milliard" to 1_000_000_000L,
        "trillion" to 1_000_000_000_000L, "trillions" to 1_000_000_000_000L, "dozen" to 12L, "grand" to 1_000L,
        "bn" to 1_000_000_000L, "mn" to 1_000_000L, "tn" to 1_000_000_000_000L,
    )

    init {
        // Units: symbols case-sensitive, names case-insensitive.
        for ((s, u) in UnitCatalog.symbols) cs.put(keys(s, exact = true), { Tok(T.UNIT, 0, 0, it, UnitExpr.of(u)) })
        for ((n, u) in UnitCatalog.words) ci.put(keys(n), { Tok(T.UNIT, 0, 0, it, UnitExpr.of(u)) })
        // Currencies.
        for ((code, def) in Currencies.byCode) {
            cs.put(keys(code, exact = true), { Tok(T.CUR, 0, 0, it, def.code) })
            if (code.lowercase() in Currencies.lowercaseCodes) ci.put(keys(code.lowercase()), { Tok(T.CUR, 0, 0, it, def.code) })
        }
        for ((n, code) in Currencies.names) ci.put(keys(n), { Tok(T.CUR, 0, 0, it, code) })

        op("+", "plus", "add")
        op("-", "minus", "subtract", "less")
        op("*", "times", "multiplied by", "multiply", "mul", "mult")
        op("/", "divided by", "divide by", "divide", "over")
        op("^", "to the power of", "raised to", "power", "exponent", "raised to the power of")
        op("mod", "mod", "modulo", "remainder of")
        op("xor", "xor", "XOR")
        kw(K.IN, "in", "into")
        kw(K.TO, "to", "→", "->")
        kw(K.AS, "as")
        kw(K.OF, "of")
        kw(K.ON, "on")
        kw(K.OFF, "off")
        kw(K.PER, "per", "a", "an", "each", "every")
        kw(K.IS, "is", "are", "was")
        kw(K.WHAT, "what")
        kw(K.AND, "and")
        kw(K.OF_WHAT, "of what", "of what is")
        kw(K.ON_WHAT, "on what", "on what is")
        kw(K.OFF_WHAT, "off what", "off what is")
        for (pct in listOf("%", "percent", "percentage", "per cent", "pct")) {
            kw(K.AS_PCT_OF, "as a $pct of", "as $pct of", "as a $pct from")
            kw(K.AS_PCT_ON, "as a $pct on", "as $pct on", "as a $pct increase of", "as a $pct increase on")
            kw(K.AS_PCT_OFF, "as a $pct off", "as $pct off", "as a $pct decrease of", "as a $pct decrease from")
            kw(K.IS_WHAT_PCT_OF, "is what $pct of", "is what $pct from")
            kw(K.IS_WHAT_PCT_ON, "is what $pct on")
            kw(K.IS_WHAT_PCT_OFF, "is what $pct off")
            kw(K.IS_WHAT_PCT, "is what $pct")
            kw(K.PCT_CHANGE, "$pct change", "$pct change from", "what $pct change is")
            kw(K.PCT_INCREASE, "$pct increase", "$pct increase from")
            kw(K.PCT_DECREASE, "$pct decrease", "$pct decrease from")
        }
        for (p in listOf("percent", "percentage", "per cent", "pct", "pct.", "percents")) ci.put(keys(p), { Tok(T.PCT, 0, 0, it) })
        kw(K.FROM, "from")
        kw(K.UNTIL, "until", "till", "til", "to go until")
        kw(K.SINCE, "since")
        kw(K.AGO, "ago")
        kw(K.FROM_NOW, "from now", "later")
        kw(K.BEFORE, "before")
        kw(K.AFTER, "after")
        kw(K.BETWEEN, "between")
        kw(K.NEXT, "next", "coming")
        kw(K.LAST, "last", "previous")
        kw(K.THIS, "this")
        kw(K.SQUARE, "square", "sq", "sq.", "sqr")
        kw(K.CUBIC, "cubic", "cu", "cu.", "cb", "cube")
        kw(K.SQUARED, "squared")
        kw(K.CUBED, "cubed")
        kw(K.SQRT_OF, "square root of", "square root", "root of")
        kw(K.CBRT_OF, "cube root of", "cube root", "cubic root", "cubed root", "cubic root of")
        kw(K.HALF, "half of", "half")
        kw(K.DOUBLE, "double")
        kw(K.TWICE, "twice")
        kw(K.TRIPLE, "triple")
        kw(K.TIME, "time", "the time", "current time", "time now")
        kw(K.IF, "if")
        kw(K.THEN, "then")
        kw(K.ELSE, "else", "otherwise")
        kw(K.PPI, "ppi", "dpi")

        fn("sqrt", "sqrt", "√")
        fn("cbrt", "cbrt", "∛")
        fn("root", "root")
        fn("abs", "abs", "absolute value of")
        fn("ln", "ln")
        fn("log", "log", "log10", "lg")
        fn("log2", "log2")
        fn("exp", "exp")
        fn("sin", "sin", "sine")
        fn("cos", "cos", "cosine")
        fn("tan", "tan", "tangent")
        fn("asin", "asin", "arcsin")
        fn("acos", "acos", "arccos")
        fn("atan", "atan", "arctan")
        fn("sinh", "sinh"); fn("cosh", "cosh"); fn("tanh", "tanh")
        fn("sind", "sind"); fn("cosd", "cosd"); fn("tand", "tand")
        fn("asind", "asind"); fn("acosd", "acosd"); fn("atand", "atand")
        fn("hex", "hex"); fn("bin", "bin"); fn("oct", "oct"); fn("int", "int")
        fn("fact", "fact", "factorial", "factorial of")
        fn("round", "round"); fn("floor", "floor"); fn("ceil", "ceil", "ceiling"); fn("trunc", "trunc", "truncate")
        fn("gcd", "gcd", "gcf", "greatest common divisor", "greatest common factor", "greatest common divisor of")
        fn("lcm", "lcm", "lowest common multiple", "least common multiple", "lcm of")
        fn("random", "random", "rand", "random number")
        fn("fromunix", "fromunix", "unixtime", "from unix", "fromtimestamp")
        fn("sign", "sign", "sgn")
        fn("choose", "choose", "ncr")

        agg("sum", "sum", "total", "subtotal", "sum total", "grand total")
        agg("avg", "average", "avg", "mean")
        agg("count", "count")
        agg("median", "median")
        agg("min", "min", "minimum", "smallest", "lowest", "lesser of", "smaller of")
        agg("max", "max", "maximum", "largest", "highest", "larger of", "greater of")
        agg("stddev", "standard deviation", "std dev", "stddev", "sd")
        agg("prev", "prev", "previous line", "line above", "above", "ans", "answer", "last answer")

        fmt(Fmt(FmtKind.HEX), "hex", "hexadecimal", "base 16")
        fmt(Fmt(FmtKind.BIN), "binary", "bin", "base 2")
        fmt(Fmt(FmtKind.OCT), "octal", "base 8")
        fmt(Fmt(FmtKind.DEC), "decimal", "base 10", "dec")
        fmt(Fmt(FmtKind.SCI), "sci", "scientific", "scientific notation", "exponential", "exponent notation", "e notation")
        fmt(Fmt(FmtKind.FRACTION), "fraction", "a fraction", "fractions", "fractional")
        fmt(Fmt(FmtKind.MULTIPLIER), "multiplier", "a multiplier", "x")
        fmt(Fmt(FmtKind.NUMBER), "number", "num", "a number", "plain number")
        fmt(Fmt(FmtKind.TIMESTAMP), "timestamp", "unix", "unix time", "unix timestamp", "epoch")
        fmt(Fmt(FmtKind.ISO), "iso", "iso8601", "iso 8601")
        fmt(Fmt(FmtKind.DP), "dp", "decimal places", "decimal place", "decimals", "places", "digits")
        fmt(Fmt(FmtKind.SIGFIG), "sig figs", "significant figures", "significant digits", "sf", "sig fig")
        kw("nearest", "nearest", "the nearest")
        kw("rounded", "rounded", "rounded off")
        kw("rounded up", "rounded up", "round up")
        kw("rounded down", "rounded down", "round down")
        for (a in listOf("at", "@")) ci.put(keys(a), { Tok(T.AT, 0, 0, it) })
        // One-word areas and volumes.
        for ((w, pair) in mapOf("sqm" to ("m" to 2), "sqft" to ("ft" to 2), "sqkm" to ("km" to 2), "sqmi" to ("mi" to 2),
                "sqin" to ("in" to 2), "sqcm" to ("cm" to 2), "cbm" to ("m" to 3), "cuft" to ("ft" to 3), "cuin" to ("in" to 3),
                "m2" to ("m" to 2), "m3" to ("m" to 3), "km2" to ("km" to 2), "cm2" to ("cm" to 2), "cm3" to ("cm" to 3), "ft2" to ("ft" to 2),
                "ft3" to ("ft" to 3), "mm2" to ("mm" to 2))) {
            ci.put(keys(w), { Tok(T.UNIT, 0, 0, it, UnitExpr.of(UnitCatalog.byId(pair.first), pair.second)) })
        }
        fmt(Fmt(FmtKind.COMPOSITE), "feet and inches", "ft and in", "pounds and ounces", "lb oz", "hours and minutes", "h and min")

        for (c in listOf("pi", "π")) ci.put(keys(c), { Tok(T.CONST, 0, 0, it, Num.approx(Math.PI)) })
        for (c in listOf("tau", "τ")) ci.put(keys(c), { Tok(T.CONST, 0, 0, it, Num.approx(2 * Math.PI)) })
        for (c in listOf("phi", "φ", "golden ratio")) ci.put(keys(c), { Tok(T.CONST, 0, 0, it, Num.approx((1 + Math.sqrt(5.0)) / 2)) })
        cs.put(keys("e", exact = true), { Tok(T.CONST, 0, 0, it, Num.approx(Math.E)) })

        for ((w, n) in NUMBER_WORDS) ci.put(keys(w), { Tok(T.NUM, 0, 0, it, Rational.of(n)) })

        // Days and months.
        for (d in DayOfWeek.entries) {
            val full = d.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH).lowercase()
            val short = d.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH).lowercase()
            for (w in setOf(full, short, full + "s")) ci.put(keys(w), { Tok(T.DAYWORD, 0, 0, it, d) })
        }
        ci.put(keys("tues"), { Tok(T.DAYWORD, 0, 0, it, DayOfWeek.TUESDAY) })
        ci.put(keys("thurs"), { Tok(T.DAYWORD, 0, 0, it, DayOfWeek.THURSDAY) })
        for (w in listOf("today", "tomorrow", "yesterday", "now", "noon", "midday", "midnight", "tonight", "day after tomorrow", "day before yesterday"))
            ci.put(keys(w), { Tok(T.NOW, 0, 0, it, w) })
        for ((names, id) in listOf(
            listOf("christmas", "christmas day", "xmas") to "christmas", listOf("christmas eve") to "christmas eve",
            listOf("new year", "new year's day", "new years day", "new years") to "new year", listOf("new year's eve", "new years eve", "silvester") to "new year's eve",
            listOf("halloween") to "halloween", listOf("valentine's day", "valentines day", "valentines") to "valentine's day",
            listOf("easter", "easter sunday") to "easter", listOf("good friday") to "good friday", listOf("easter monday") to "easter monday",
            listOf("thanksgiving") to "thanksgiving", listOf("independence day", "fourth of july", "4th of july") to "independence day",
            listOf("pi day") to "pi day", listOf("st patrick's day", "st patricks day", "saint patrick's day") to "st patrick's day",
        )) for (n in names) ci.put(keys(n), { Tok(T.NOW, 0, 0, it, id) })
        for (w in listOf("week number", "week of year", "week of the year", "calendar week", "kw")) ci.put(keys(w), { Tok(T.NOW, 0, 0, it, "week number") })
        for (w in listOf("day of year", "day of the year")) ci.put(keys(w), { Tok(T.NOW, 0, 0, it, "day of year") })
    }

    val MONTHS: Map<String, Month> = buildMap {
        for (m in Month.entries) {
            val full = m.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH).lowercase()
            put(full, m)
            put(full.take(3), m)
        }
        put("sept", Month.SEPTEMBER)
    }
}

/** Stage one: split a line into numbers, words and symbols with their positions. */
internal object Scanner {
    private val FRACTIONS = mapOf('½' to (1L to 2L), '⅓' to (1L to 3L), '⅔' to (2L to 3L), '¼' to (1L to 4L), '¾' to (3L to 4L),
        '⅕' to (1L to 5L), '⅖' to (2L to 5L), '⅗' to (3L to 5L), '⅘' to (4L to 5L), '⅙' to (1L to 6L), '⅚' to (5L to 6L),
        '⅛' to (1L to 8L), '⅜' to (3L to 8L), '⅝' to (5L to 8L), '⅞' to (7L to 8L), '⅒' to (1L to 10L))
    const val SUPERS = "⁰¹²³⁴⁵⁶⁷⁸⁹⁻"

    fun isWordChar(c: Char) = c.isLetter() || c == '_' || c == 'µ' || c == '°' || c == 'º'

    fun scan(s: String, st: EngineSettings): List<Raw> {
        val out = ArrayList<Raw>()
        var i = 0
        var space = true
        while (i < s.length) {
            val c = s[i]
            if (c.isWhitespace()) { i++; space = true; continue }
            val start = i
            // Numbers
            if (c.isDigit() || (c == st.decimalSep && i + 1 < s.length && s[i + 1].isDigit())) {
                val (end, value) = number(s, i, st)
                out += Raw('n', s.substring(start, end), start, end, space, value)
                i = end; space = false; continue
            }
            FRACTIONS[c]?.let { (a, b) ->
                out += Raw('n', c.toString(), i, i + 1, space, Rational.of(a, b)); i++; space = false; return@let
            }?.let { continue }
            if (c in SUPERS) {
                var j = i
                while (j < s.length && s[j] in SUPERS) j++
                out += Raw('s', s.substring(i, j), i, j, space)
                i = j; space = false; continue
            }
            // Words: letters, with digits allowed after the first letter (line6, kWh, m2, CO2).
            if (isWordChar(c)) {
                var j = i + 1
                while (j < s.length && (isWordChar(s[j]) || s[j].isDigit() ||
                            (s[j] == '\'' && j + 1 < s.length && s[j + 1].isLetter() && s[j - 1].isLetter()))) j++
                // Currency prefixes like US$, C$, NZ$, CN¥, R$
                if (j < s.length && (s[j] == '$' || s[j] == '¥') && j - i <= 3 && s.substring(i, j).all { it.isUpperCase() }) {
                    out += Raw('c', s.substring(i, j + 1), i, j + 1, space)
                    i = j + 1; space = false; continue
                }
                // "x5" right after a number: multiplication.
                if ((c == 'x' || c == 'X') && j > i + 1 && s[i + 1].isDigit() && out.lastOrNull()?.kind == 'n' && !space) {
                    out += Raw('s', "×", i, i + 1, false); i++; space = false; continue
                }
                // Strip trailing digits from unit-like words glued to exponents: "m2" stays (handled by lexicon).
                out += Raw('w', s.substring(i, j), i, j, space)
                i = j; space = false; continue
            }
            if (Character.getType(c) == Character.CURRENCY_SYMBOL.toInt()) {
                out += Raw('c', c.toString(), i, i + 1, space); i++; space = false; continue
            }
            // Multi-char operators
            val two = if (i + 1 < s.length) s.substring(i, i + 2) else ""
            if (two in setOf("**", "<<", ">>", "->", "==", "!=", "<=", ">=", "&&", "||", ":=", "+=", "-=")) {
                out += Raw('s', two, i, i + 2, space); i += 2; space = false; continue
            }
            out += Raw('s', c.toString(), i, i + 1, space)
            i++; space = false
        }
        return out
    }

    /** Scans a number at [i]: 1,234.5 / 0x1F / 0b101 / 1e3 / 1_000. Returns (end, value). */
    private fun number(s: String, i0: Int, st: EngineSettings): Pair<Int, Rational> {
        var i = i0
        if (s[i] == '0' && i + 1 < s.length && s[i + 1] in "xXbBoO") {
            val radix = when (s[i + 1].lowercaseChar()) { 'x' -> 16; 'b' -> 2; else -> 8 }
            var j = i + 2
            val sb = StringBuilder()
            while (j < s.length && (Character.digit(s[j], radix) >= 0 || s[j] == '_')) { if (s[j] != '_') sb.append(s[j]); j++ }
            if (sb.isNotEmpty() && (j >= s.length || !s[j].isLetterOrDigit())) return j to Rational.of(BigInteger(sb.toString(), radix))
        }
        val digits = StringBuilder()
        var j = i
        fun readDigits(): Int { var n = 0; while (j < s.length && (s[j].isDigit() || (s[j] == '_' && j + 1 < s.length && s[j + 1].isDigit()))) { if (s[j] != '_') { digits.append(s[j]); n++ }; j++ }; return n }
        readDigits()
        // Grouping: groups of exactly three digits after the group separator.
        val g = st.groupSep
        while (j + 3 < s.length + 1 && j < s.length && (s[j] == g || s[j] == ' ' || s[j] == ' ' || (g == ' ' && s[j] == ' ')) &&
            j + 3 <= s.length && s.substring(j + 1, minOf(j + 4, s.length)).length == 3 &&
            s.substring(j + 1, j + 4).all { it.isDigit() } && (j + 4 >= s.length || !s[j + 4].isDigit())) {
            digits.append(s, j + 1, j + 4); j += 4
        }
        if (j < s.length && s[j] == st.decimalSep && j + 1 < s.length && s[j + 1].isDigit()) {
            digits.append('.'); j++
            readDigits()
        }
        // Exponent: 1e3, 2.5E-4 (not "2e" alone and not "3em")
        if (j + 1 < s.length && (s[j] == 'e' || s[j] == 'E')) {
            var k = j + 1
            if (k < s.length && (s[k] == '+' || s[k] == '-')) k++
            if (k < s.length && s[k].isDigit()) {
                val es = StringBuilder(s.substring(j + 1, k))
                while (k < s.length && s[k].isDigit()) { es.append(s[k]); k++ }
                if (k >= s.length || !s[k].isLetter()) {
                    val exp = es.toString().toInt()
                    if (kotlin.math.abs(exp) < 1000) {
                        return k to (Rational.parse(digits.toString()) * (Rational.of(BigInteger.TEN).pow(exp) ?: Rational.ONE))
                    }
                }
            }
        }
        return j to Rational.parse(digits.toString())
    }
}
