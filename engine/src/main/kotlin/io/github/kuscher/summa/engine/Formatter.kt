package io.github.kuscher.summa.engine

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Turns values into the answers shown in the answer column (and copied to the clipboard). */
class Formatter(private val st: EngineSettings) {

    fun format(v: Value): String = when (v) {
        is Qty -> qty(v)
        is Pct -> num(v.num) + "%"
        is Mult -> num(v.num) + "x"
        is Bool -> if (v.v) "true" else "false"
        is Moment -> moment(v)
        is Shown -> shown(v)
        is Range -> "${format(v.from)} – ${format(v.to)}"
        is ListVal -> v.items.joinToString(", ") { format(it) }
        is Place -> v.name
    }

    // ------------------------------------------------------------------ numbers

    /** A plain number in the user's format: grouping, decimal separator, automatic precision. */
    fun num(n: Num, decimals: Int = st.decimals, grouping: Boolean = st.thousands): String {
        if (!n.isFinite) return if (n.double > 0) "∞" else if (n.double < 0) "-∞" else "NaN"
        val bd = n.toBigDecimal(MathContext(40))
        val abs = bd.abs()
        if (bd.signum() != 0 && (abs >= BigDecimal("1e15") && !(n.isExact && n.isInteger && abs < BigDecimal("1e21")) || abs < BigDecimal("1e-9"))) {
            return sci(bd, 4)
        }
        val scaled = when {
            decimals >= 0 -> bd.setScale(decimals, RoundingMode.HALF_UP)
            n.isInteger -> bd.setScale(0, RoundingMode.HALF_UP)
            abs >= BigDecimal.ONE -> bd.setScale(2, RoundingMode.HALF_UP).stripZeros()
            else -> bd.round(MathContext(3, RoundingMode.HALF_UP)).let { if (it.scale() > 10) it.setScale(10, RoundingMode.HALF_UP) else it }.stripZeros()
        }
        return plain(scaled, grouping)
    }

    private fun BigDecimal.stripZeros(): BigDecimal = stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }

    /** "1234567.5" → "1,234,567.5" with the configured separators. */
    fun plain(bd: BigDecimal, grouping: Boolean = st.thousands): String {
        val s = bd.abs().toPlainString()
        val intPart = s.substringBefore('.')
        val frac = s.substringAfter('.', "")
        val grouped = if (grouping && intPart.length > 3) {
            val sb = StringBuilder()
            for ((i, c) in intPart.withIndex()) {
                if (i > 0 && (intPart.length - i) % 3 == 0) sb.append(st.groupSep)
                sb.append(c)
            }
            sb.toString()
        } else intPart
        val sign = if (bd.signum() < 0) "-" else ""
        return sign + grouped + if (frac.isNotEmpty()) st.decimalSep + frac else ""
    }

    private fun sci(bd: BigDecimal, digits: Int): String {
        if (bd.signum() == 0) return "0"
        val exp = bd.precision() - bd.scale() - 1
        val mant = bd.movePointLeft(exp).round(MathContext(digits + 1, RoundingMode.HALF_UP)).stripZeros()
        return plain(mant, false) + "e" + exp
    }

    // ------------------------------------------------------------------ quantities

    fun qty(q: Qty): String {
        if (q.isPlain) return num(q.num)
        val u = q.unit
        if (u.isCurrencyOnly) return money(q.num, u.currency!!)
        // Money rates: "$120.00/hour", "€1.79/L"
        val cur = u.terms.firstOrNull { it.first.kind == UnitKind.CURRENCY && it.second == 1 }
        if (cur != null && u.terms.size >= 2) {
            val rest = UnitExpr.of(u.terms.filter { it != cur })
            val m = money(q.num, cur.first.id, trimCents = false)
            return m + unitSuffix(rest, perWords = true).let { if (it.startsWith("/")) it else " $it" }
        }
        val single = u.single
        if (single != null) {
            if (single.dim == Dim.TIME && !q.num.isInteger && single.id in setOf("h", "min", "day")) composite(q)?.let { return it }
            single.word?.let { (one, many) ->
                val n = num(q.num)
                return "$n " + if (q.num.abs().compareTo(Num.ONE) == 0) one else many
            }
            return num(q.num) + (if (single.tight) "" else " ") + single.symbol
        }
        return num(q.num) + unitSuffix(u, perWords = false).let { if (it.startsWith("/")) it else " $it" }
    }

    private val SUP = "⁰¹²³⁴⁵⁶⁷⁸⁹"
    private fun sup(n: Int): String = (if (n < 0) "⁻" else "") + kotlin.math.abs(n).toString().map { SUP[it - '0'] }.joinToString("")

    /** "km/h", "m²", "kg·m/s²". With [perWords], time denominators read "/hour", "/day". */
    fun unitSuffix(u: UnitExpr, perWords: Boolean): String {
        val num = u.terms.filter { it.second > 0 }
        val den = u.terms.filter { it.second < 0 }.map { it.first to -it.second }
        fun sym(t: Pair<UnitDef, Int>, per: Boolean): String {
            val (unit, p) = t
            val s = if (per && p == 1 && unit.dim == Dim.TIME) when (unit.id) {
                "h" -> if (perWords) "hour" else "h"; "min" -> "min"; "s" -> "s"; "day" -> "day"; "week" -> "week"; "month" -> "month"
                "year" -> "year"; "quarter" -> "quarter"; "workday" -> "workday"; "fortnight" -> "fortnight"; else -> unit.symbol
            } else if (unit.kind == UnitKind.CURRENCY) Currencies.get(unit.id)?.symbol ?: unit.id else unit.symbol
            return if (p == 1) s else s + sup(p)
        }
        val top = num.joinToString("·") { sym(it, false) }
        if (den.isEmpty()) return top
        val bottom = den.joinToString("·") { sym(it, true) }
        return (top.ifEmpty { "" }) + "/" + bottom
    }

    /** "3 h 20 min", "4 min 30 s", "2 days 6 h" */
    private fun composite(q: Qty): String? {
        val u = q.unit.single ?: return null
        val secs = q.num.toRational() * u.factor
        val total = secs.toBigDecimal(0, RoundingMode.HALF_UP).toLong()
        val neg = total < 0
        val t = kotlin.math.abs(total)
        val (bigN, bigU, smallN, smallU) = when (u.id) {
            "day" -> listOf(t / 86400, if (t / 86400 == 1L) "day" else "days", (t % 86400 + 1800) / 3600, "h")
            "h" -> listOf(t / 3600, "h", (t % 3600 + 30) / 60, "min")
            else -> listOf(t / 60, "min", t % 60, "s")
        }
        var big = bigN as Long
        var small = smallN as Long
        if (u.id == "h" && small == 60L) { big++; small = 0 }
        if (u.id == "day" && small == 24L) { big++; small = 0 }
        val sign = if (neg) "-" else ""
        return if (small == 0L) "$sign$big $bigU" else if (big == 0L) "$sign$small $smallU" else "$sign$big $bigU $small $smallU"
    }

    /** Money with the currency's own decimals: "$1,234.00", "¥1,234", "12.50 CHF", "0.00012345 BTC". */
    fun money(n: Num, code: String, trimCents: Boolean = false): String {
        val def = Currencies.get(code)
        val digits = def?.digits ?: 2
        val bd = n.toBigDecimal(MathContext(40))
        val body = if (def?.crypto == true) {
            // Crypto keeps significant digits instead of cents.
            val r = if (bd.abs() >= BigDecimal.ONE) bd.setScale(minOf(digits, 4), RoundingMode.HALF_UP) else bd.round(MathContext(4, RoundingMode.HALF_UP))
            plain(r.stripZeros())
        } else {
            val r = bd.setScale(digits, RoundingMode.HALF_UP)
            if (r.abs() >= BigDecimal("1e15")) sci(r, 4) else plain(if (trimCents && r.stripTrailingZeros().scale() <= 0) r.setScale(0) else r)
        }
        val neg = body.startsWith("-")
        val b = body.removePrefix("-")
        val sym = def?.symbol ?: code
        val suffix = def?.suffix ?: true
        val s = when {
            suffix -> "$b $sym"
            st.currencyAfter && sym.length <= 2 -> "$b $sym"
            else -> sym + b
        }
        return if (neg) "-$s" else s
    }

    // ------------------------------------------------------------------ dates

    private fun timeStr(t: java.time.LocalTime): String =
        if (st.use24h) "%02d:%02d".format(t.hour, t.minute) + (if (t.second != 0) ":%02d".format(t.second) else "")
        else {
            val h = if (t.hour % 12 == 0) 12 else t.hour % 12
            "$h:%02d".format(t.minute) + (if (t.second != 0) ":%02d".format(t.second) else "") + if (t.hour < 12) " am" else " pm"
        }

    fun date(d: LocalDate): String {
        val f = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(st.locale)
        val dow = d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, st.locale)
        return "$dow, ${d.format(f)}"
    }

    private fun moment(m: Moment): String {
        val dt = m.dt
        return when {
            m.hasDate && !m.hasTime -> date(dt.toLocalDate())
            !m.hasDate -> {
                val t = timeStr(dt.toLocalTime())
                val ref = m.refDate
                if (ref != null && dt.toLocalDate() != ref) {
                    val diff = java.time.temporal.ChronoUnit.DAYS.between(ref, dt.toLocalDate())
                    when (diff) {
                        1L -> "Tomorrow $t"
                        -1L -> "Yesterday $t"
                        else -> "${date(dt.toLocalDate())} $t"
                    }
                } else t
            }
            else -> "${date(dt.toLocalDate())} ${timeStr(dt.toLocalTime())}"
        }
    }

    // ------------------------------------------------------------------ explicit formats

    private fun shown(s: Shown): String {
        val v = s.value
        return when (s.format.kind) {
            FmtKind.HEX, FmtKind.BIN, FmtKind.OCT -> {
                val q = v as? Qty ?: return format(v)
                val r = q.num.toRational()
                if (!r.isInteger) return format(v)
                val (radix, prefix) = when (s.format.kind) { FmtKind.HEX -> 16 to "0x"; FmtKind.BIN -> 2 to "0b"; else -> 8 to "0o" }
                val body = r.num.abs().toString(radix).uppercase()
                (if (r.signum < 0) "-" else "") + prefix + body
            }
            FmtKind.DEC -> (v as? Qty)?.let { num(it.num) } ?: format(v)
            FmtKind.TIMESTAMP -> (v as? Qty)?.let { plain(it.num.toBigDecimal(MathContext(34)).stripZeros(), false) } ?: format(v)
            FmtKind.UNIT -> (v as? Qty)?.let { q ->
                val s = q.unit.single
                if (s != null && s.word == null && s.dim == Dim.TIME) num(q.num) + " " + s.symbol else qty(q)
            } ?: format(v)
            FmtKind.SCI -> (v as? Qty)?.let { q -> sci(q.num.toBigDecimal(MathContext(34)), 6) + if (q.isPlain) "" else " " + unitSuffix(q.unit, false) } ?: format(v)
            FmtKind.FRACTION -> when (v) {
                is Qty -> fraction(v.num) + if (v.isPlain) "" else " " + unitSuffix(v.unit, false)
                is Pct -> fraction(v.num / Num.of(100))
                else -> format(v)
            }
            FmtKind.DP -> when (v) {
                is Qty -> if (v.unit.isCurrencyOnly) money(v.num, v.unit.currency!!) else {
                    val n = num(v.num, s.format.n)
                    if (v.isPlain) n else qty(v).let { full -> n + full.removePrefix(num(v.num)) }
                }
                is Pct -> num(v.num, s.format.n) + "%"
                else -> format(v)
            }
            FmtKind.ISO -> (v as? Moment)?.let { m ->
                if (m.hasTime) m.dt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) else m.date.toString()
            } ?: format(v)
            FmtKind.COMPOSITE -> (v as? Qty)?.let { compositeAny(it) } ?: format(v)
            else -> format(v)
        }
    }

    /** Best rational approximation for approximate numbers, exact fractions otherwise. */
    fun fraction(n: Num): String {
        val r = n.exact ?: approxFraction(n.double)
        if (r.isInteger) return r.num.toString()
        val neg = r.signum < 0
        val a = r.abs()
        return (if (neg) "-" else "") + "${a.num}/${a.den}"
    }

    private fun approxFraction(x: Double): Rational {
        var h1 = BigInteger.ONE; var h0 = BigInteger.ZERO
        var k1 = BigInteger.ZERO; var k0 = BigInteger.ONE
        var v = x
        repeat(20) {
            val a = kotlin.math.floor(v).toLong()
            val ab = BigInteger.valueOf(a)
            val h2 = ab * h1 + h0; val k2 = ab * k1 + k0
            h0 = h1; h1 = h2; k0 = k1; k1 = k2
            if (k1 > BigInteger.valueOf(10000)) return Rational.of(h0, k0)
            val frac = v - a
            if (kotlin.math.abs(frac) < 1e-12 || kotlin.math.abs(h1.toDouble() / k1.toDouble() - x) < 1e-12) return Rational.of(h1, k1)
            v = 1 / frac
        }
        return Rational.of(h1, k1)
    }

    /** "5 ft 11 in", "13 lb 8 oz" */
    private fun compositeAny(q: Qty): String {
        val u = q.unit.single ?: return qty(q)
        val pairs = mapOf("ft" to "in", "mi" to "ft", "lb" to "oz", "st" to "lb", "h" to "min", "min" to "s", "day" to "h")
        val smallId = pairs[u.id] ?: return qty(q)
        val small = UnitCatalog.byId(smallId)
        val whole = q.num.floor()
        val rest = (q.num - whole) * Num.of(u.factor / small.factor)
        val restStr = num(rest.round(2))
        return "${num(whole)} ${u.symbol} $restStr ${small.symbol}"
    }
}
