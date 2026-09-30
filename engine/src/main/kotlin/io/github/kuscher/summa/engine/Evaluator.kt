package io.github.kuscher.summa.engine

import java.math.BigInteger
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** What a line's evaluation can see: variables, earlier lines, totals, rates, the clock. */
class EvalCtx(
    val settings: EngineSettings,
    val rates: Rates,
    val now: ZonedDateTime,
    val vars: Map<String, Value> = emptyMap(),
    /** Values of lines above, index 0 = line 1 (null where a line has no value). */
    val lines: List<Value?> = emptyList(),
    val prev: Value? = null,
    val aggregate: (String) -> Value? = { null },
    val tagStat: (String, String) -> Value? = { _, _ -> null },
    private val ppiOverride: Rational? = null,
    private val pairRates: Map<Pair<String, String>, Rational> = emptyMap(),
) : FactorResolver {
    var timeDependent = false
    var rateDependent = false
    val referencedLines = HashSet<Int>()

    fun with(ppi: Rational? = ppiOverride, pair: Map<Pair<String, String>, Rational> = pairRates) =
        EvalCtx(settings, rates, now, vars, lines, prev, aggregate, tagStat, ppi, pair).also { it.parent = this }

    private var parent: EvalCtx? = null
    private fun flagRate() { rateDependent = true; parent?.flagRate() }
    private fun flagTime() { timeDependent = true; parent?.flagTime() }
    fun markTime() = flagTime()
    fun markRef(n: Int) { referencedLines += n; parent?.markRef(n) }

    private fun plainVar(name: String): Rational? = (vars[name] as? Qty)?.takeIf { it.isPlain }?.num?.exact

    val ppi: Rational get() = ppiOverride ?: plainVar("ppi") ?: plainVar("dpi") ?: Rational.of(96)

    /** Pixels per em: `em = 20px` or a plain number. */
    private val emPx: Rational get() {
        val v = vars["em"] as? Qty ?: return Rational.of(16)
        if (v.isPlain) return v.num.exact ?: Rational.of(16)
        val px = UnitCatalog.byId("px")
        if (v.unit.single == px) return v.num.exact ?: Rational.of(16)
        return Rational.of(16)
    }

    override fun factor(u: UnitDef): Rational? = when (u.kind) {
        UnitKind.CURRENCY -> { flagRate(); rates.eurPer(u.id) }
        UnitKind.PIXEL -> Rational.parse("0.0254") / ppi
        UnitKind.EM -> Rational.parse("0.0254") / ppi * emPx
        else -> u.factor
    }

    /** Custom rate "at 1.05 USD/EUR": EUR per one [code], if the line set one. */
    fun pairFactor(from: String, to: String): Rational? = pairRates[from to to]
}

/** Evaluates expression trees. Throws [EvalError] when a line has no sensible answer. */
class Evaluator(private val ctx: EvalCtx) {
    private val st = ctx.settings
    private val today: LocalDate get() { ctx.markTime(); return ctx.now.toLocalDate() }

    fun eval(n: Node): Value = when (n) {
        is NumLit -> Qty(Num.of(n.v))
        is ConstLit -> Qty(n.v)
        is Group -> eval(n.x)
        is Neg -> neg(eval(n.x))
        is Bin -> bin(n.op, eval(n.a), eval(n.b))
        is Juxt -> juxt(n)
        is WithUnit -> withUnit(eval(n.x), n.unit)
        is BareUnit -> Qty(Num.ONE, n.unit)
        is PctLit -> when (val v = eval(n.x)) {
            is Qty -> if (v.isPlain) Pct(v.num) else throw EvalError("% of a unit")
            is Pct -> v
            else -> throw EvalError("%")
        }
        is PctApply -> pctApply(n.kind, eval(n.p), eval(n.x))
        is PctOfWhat -> pctOfWhat(n)
        is AsPct -> asPct(n.kind, eval(n.a), eval(n.b))
        is PctChange -> pctChange(n)
        is Convert -> convert(eval(n.x), n.target, n.x)
        is Call -> call(n.fn, n.args)
        is VarRef -> ctx.vars[n.name] ?: throw EvalError("unknown ${n.name}")
        is LineRef -> {
            ctx.markRef(n.n)
            ctx.lines.getOrNull(n.n - 1) ?: throw EvalError("line ${n.n} has no value")
        }
        is Agg -> if (n.kind == "prev") (ctx.prev ?: throw EvalError("no previous line")) else
            ctx.aggregate(n.kind) ?: throw EvalError("nothing to ${n.kind}")
        is ListStat -> listStat(n.kind, n.items.map { eval(it) })
        is TagStat -> ctx.tagStat(n.kind, n.tag) ?: throw EvalError("no #${n.tag}")
        is DateNode -> dateOf(n.d)
        is TimeNode -> Moment(atToday(n.t), hasDate = false, hasTime = true, refDate = ctx.now.withZoneSameInstant(st.zone).toLocalDate())
        is NowNode -> try { nowWord(n.word) } catch (e: Early) { e.v }
        is DayNode -> dayOf(n)
        is RelPeriod -> relPeriod(n)
        is AtPlace -> atPlace(eval(n.x), n.place)
        is PlaceNow -> { ctx.markTime(); Moment(ctx.now.withZoneSameInstant(n.place.zone), hasDate = false, hasTime = true, zoneGiven = true, place = n.place.name, refDate = ctx.now.toLocalDate()) }
        is Until -> until(n)
        is Ago -> ago(n)
        is Shift -> shift(n)
        is RangeNode -> Range(eval(n.a), eval(n.b))
        is Postfix -> factorial(eval(n.x))
        is AtNode -> at(n)
        is PpiLit -> eval(n.x)
        is Scaled -> mul(Qty(Num.of(n.factor)), eval(n.x))
        is Cond -> {
            val c = eval(n.cond)
            if (c !is Bool) throw EvalError("if needs a condition")
            if (c.v) eval(n.then) else n.otherwise?.let { eval(it) } ?: throw EvalError("no else")
        }
        is Compare -> Bool(compareValues(eval(n.a), eval(n.b)) == 0)
    }

    // ------------------------------------------------------------------ arithmetic

    private fun neg(v: Value): Value = when (v) {
        is Qty -> Qty(-v.num, v.unit)
        is Pct -> Pct(-v.num)
        is Shown -> neg(v.value)
        else -> throw EvalError("negate")
    }

    fun bin(op: String, a0: Value, b0: Value): Value {
        val a = unwrap(a0)
        val b = unwrap(b0)
        return when (op) {
            "+" -> add(a, b, 1)
            "-" -> add(a, b, -1)
            "*" -> mul(a, b)
            "/" -> div(a, b)
            "^" -> pow(a, b)
            "mod" -> intOp(a, b) { x, y -> x.mod(y.abs()).let { m -> if (x.signum() < 0 && m.signum() != 0) m - y.abs() else m } }.let {
                // non-integer remainders too
                if (it == null) remainder(a, b) else it
            }
            "&" -> intOp(a, b) { x, y -> x.and(y) } ?: throw EvalError("& needs integers")
            "|" -> intOp(a, b) { x, y -> x.or(y) } ?: throw EvalError("| needs integers")
            "xor" -> intOp(a, b) { x, y -> x.xor(y) } ?: throw EvalError("xor needs integers")
            "<<" -> intOp(a, b) { x, y -> x.shiftLeft(y.toInt()) } ?: throw EvalError("<< needs integers")
            ">>" -> intOp(a, b) { x, y -> x.shiftRight(y.toInt()) } ?: throw EvalError(">> needs integers")
            else -> throw EvalError("operator $op")
        }
    }

    private fun unwrap(v: Value): Value = when (v) {
        is Shown -> unwrap(v.value)
        is Range -> rangeDiff(v)
        else -> v
    }

    private fun intOp(a: Value, b: Value, f: (BigInteger, BigInteger) -> BigInteger): Value? {
        val x = (a as? Qty)?.takeIf { it.num.isInteger && it.num.isExact }?.num?.exact?.num ?: return null
        val y = (b as? Qty)?.takeIf { it.isPlain && it.num.isInteger && it.num.isExact }?.num?.exact?.num ?: return null
        if (y.signum() == 0 && f !== BigInteger::and) {
            // allow & | xor with 0; mod by 0 is an error
        }
        return try { Qty(Num.of(Rational.of(f(x, y))), (a as Qty).unit) } catch (e: ArithmeticException) { throw EvalError("math") }
    }

    private fun remainder(a: Value, b: Value): Value {
        val x = a as? Qty ?: throw EvalError("mod")
        val y = b as? Qty ?: throw EvalError("mod")
        val yy = if (y.isPlain) y else convertQty(y, x.unit)
        val q = (x.num / yy.num).floor()
        return Qty(x.num - q * yy.num, x.unit)
    }

    fun add(a: Value, b: Value, sign: Int): Value {
        val bs = if (sign < 0 && (b is Qty || b is Pct || b is Mult)) (if (b is Mult) Mult(-b.num) else neg(b)) else b
        return when {
            a is Qty && b is Qty -> addQty(a, bs as Qty)
            a is Qty && b is Pct -> Qty(a.num * (Num.ONE + (bs as Pct).num / Num.of(100)), a.unit)
            a is Pct && b is Pct -> Pct(a.num + (bs as Pct).num)
            a is Pct && b is Qty && b.isPlain -> Pct(a.num + (bs as Qty).num * Num.of(100))
            a is Moment && b is Qty -> shiftMoment(a, bs as Qty)
            a is Qty && b is Moment && sign > 0 -> shiftMoment(b, a)
            a is Moment && b is Moment && sign < 0 -> momentDiff(b, a)
            a is Mult && b is Mult -> Mult(a.num + (bs as Mult).num)
            else -> throw EvalError("can't add these")
        }
    }

    private fun addQty(a: Qty, b: Qty): Qty {
        if (a.isPlain && b.isPlain) return Qty(a.num + b.num)
        if (b.isPlain) return Qty(a.num + b.num, a.unit)   // $20 + 30 = $50
        if (a.isPlain) return Qty(a.num + b.num, b.unit)
        if (a.unit == b.unit) return Qty(a.num + b.num, a.unit)
        if (a.unit.dim != b.unit.dim) throw EvalError("incompatible units")
        // Money: the last currency wins. Other units: the larger unit wins.
        val target = if (a.unit.hasCurrency) b.unit else {
            val fa = a.unit.factor(ctx) ?: throw EvalError("unit")
            val fb = b.unit.factor(ctx) ?: throw EvalError("unit")
            if (fa >= fb) a.unit else b.unit
        }
        if (a.unit.isTemperature && b.unit.isTemperature) {
            // Temperature plus a temperature difference: add in the first unit.
            val ratio = (b.unit.single!!.factor / a.unit.single!!.factor)
            return Qty(a.num + b.num * Num.of(ratio), a.unit)
        }
        val ca = convertQty(a, target)
        val cb = convertQty(b, target)
        return Qty(ca.num + cb.num, target)
    }

    fun mul(a0: Value, b0: Value): Value {
        val a = unwrap(a0)
        val b = unwrap(b0)
        return when {
            a is Qty && b is Qty -> mulQty(a, b)
            a is Qty && b is Pct -> Qty(a.num * b.num / Num.of(100), a.unit)
            a is Pct && b is Qty -> Qty(b.num * a.num / Num.of(100), b.unit)
            a is Pct && b is Pct -> Pct(a.num * b.num / Num.of(100))
            a is Mult && b is Qty -> Qty(b.num * a.num, b.unit)
            a is Qty && b is Mult -> Qty(a.num * b.num, a.unit)
            else -> throw EvalError("can't multiply these")
        }
    }

    private fun mulQty(a: Qty, b: Qty): Qty {
        if (a.isPlain) return Qty(a.num * b.num, b.unit)
        if (b.isPlain) return Qty(a.num * b.num, a.unit)
        // Money × time is an implicit rate: $30 × 4 days = $120.
        if (a.unit.isCurrencyOnly && b.unit.dim == Dim.TIME && b.unit.terms.size == 1) return Qty(a.num * b.num, a.unit)
        if (b.unit.isCurrencyOnly && a.unit.dim == Dim.TIME && a.unit.terms.size == 1) return Qty(a.num * b.num, b.unit)
        val bb = align(b, a.unit)
        return simplify(Qty(a.num * bb.num, a.unit * bb.unit), a.unit, b.unit)
    }

    fun div(a0: Value, b0: Value): Value {
        val a = unwrap(a0)
        val b = unwrap(b0)
        return when {
            a is Qty && b is Qty -> {
                if (b.num.isZero) throw EvalError("division by zero")
                if (b.isPlain) Qty(a.num / b.num, a.unit)
                else {
                    val bb = align(b, a.unit)
                    simplify(Qty(a.num / bb.num, a.unit / bb.unit), a.unit, b.unit)
                }
            }
            a is Qty && b is Pct -> Qty(a.num / (b.num / Num.of(100)), a.unit)
            a is Pct && b is Qty && b.isPlain -> Pct(a.num / b.num)
            a is Pct && b is Pct -> Qty(a.num / b.num)
            a is Qty && b is Mult -> Qty(a.num / b.num, a.unit)
            else -> throw EvalError("can't divide these")
        }
    }

    private fun pow(a: Value, b: Value): Value {
        val x = a as? Qty ?: (a as? Pct)?.let { return Pct(Num.of(100) * (it.num / Num.of(100)).pow((b as Qty).num)) } ?: throw EvalError("^")
        val e = (b as? Qty)?.takeIf { it.isPlain } ?: throw EvalError("^ needs a number")
        if (x.isPlain) return Qty(x.num.pow(e.num))
        val ei = e.num.exact?.takeIf { it.isInteger }?.num?.toInt() ?: throw EvalError("unit ^ fraction")
        return Qty(x.num.pow(e.num), x.unit.pow(ei))
    }

    /** Converts b's simple units to a's units of the same dimension (m × cm → m × m). */
    private fun align(b: Qty, ref: UnitExpr): Qty {
        var num = b.num
        val terms = b.unit.terms.map { (u, p) ->
            val match = ref.terms.firstOrNull { (r, _) -> r != u && r.dim == u.dim && r.kind != UnitKind.CURRENCY && u.kind != UnitKind.CURRENCY &&
                    r.kind != UnitKind.TEMPERATURE && u.kind != UnitKind.TEMPERATURE && r.dim.let { d -> d == Dim.LENGTH || d == Dim.TIME || d == Dim.MASS || d == Dim.DATA || d == Dim.VOLUME || d == Dim.ANGLE } }
            if (match != null) {
                val fu = ctx.factor(u) ?: return b
                val fr = ctx.factor(match.first) ?: return b
                num *= Num.of((fu / fr).pow(p) ?: return b)
                match.first to p
            } else u to p
        }
        return Qty(num, UnitExpr.of(terms))
    }

    /** Parts that compound units are made of, for naming results: mph → miles and hours. */
    private val PARTS: Map<String, Map<Dim, String>> = mapOf(
        "kmh" to mapOf(Dim.LENGTH to "km", Dim.TIME to "h"),
        "mph" to mapOf(Dim.LENGTH to "mi", Dim.TIME to "h"),
        "mps" to mapOf(Dim.LENGTH to "m", Dim.TIME to "s"),
        "knot" to mapOf(Dim.LENGTH to "nmi", Dim.TIME to "h"),
        "fps" to mapOf(Dim.LENGTH to "ft", Dim.TIME to "s"),
        "l100km" to mapOf(Dim.VOLUME to "L", Dim.LENGTH to "km"),
        "mpg" to mapOf(Dim.LENGTH to "mi", Dim.VOLUME to "gal"),
        "kml" to mapOf(Dim.LENGTH to "km", Dim.VOLUME to "L"),
        "kW" to mapOf(Dim.ENERGY to "kWh"), "W" to mapOf(Dim.ENERGY to "Wh"), "MW" to mapOf(Dim.ENERGY to "MWh"),
        "Mbps" to mapOf(Dim.DATA to "Mb"), "Gbps" to mapOf(Dim.DATA to "Gb"), "kbps" to mapOf(Dim.DATA to "kb"),
        "bps" to mapOf(Dim.DATA to "b"), "MBps" to mapOf(Dim.DATA to "MB"),
        "A" to mapOf(Dim.CHARGE to "Ah"), "mA" to mapOf(Dim.CHARGE to "mAh"),
    )
    private val DEFAULT_UNIT: Map<Dim, String> = mapOf(
        Dim.ENERGY to "J", Dim.POWER to "W", Dim.FORCE to "N", Dim.PRESSURE to "Pa", Dim.CHARGE to "coulomb",
        Dim.FREQUENCY to "Hz", Dim.VOLTAGE to "V",
    )

    /** Tidies units after × and ÷: cancels to a plain number or names a single unit where it reads better. */
    private fun simplify(q: Qty, vararg hints: UnitExpr): Qty {
        val u = q.unit
        if (u.isNone) return q
        val dim = u.dim
        if (dim.isNone) {
            val f = u.factor(ctx) ?: throw EvalError("no rate")
            return Qty(q.num * Num.of(f))
        }
        if (dim == Dim.TIME && !u.hasCurrency) {
            // Durations read best in a fitting unit: 2,000 s → 33 min 20 s.
            val secs = convertQty(q, UnitExpr.of(UnitCatalog.byId("s")))
            val a = secs.num.abs().double
            val id = when { a >= 86400 * 2 -> "day"; a >= 3600 -> "h"; a >= 60 -> "min"; else -> "s" }
            if (u.terms.size >= 2 || u.single?.id == "s") return convertQty(secs, UnitExpr.of(UnitCatalog.byId(id)))
        }
        if (u.terms.size < 2 || u.hasCurrency) return q
        // A product that is a plain physical quantity (not a rate) reads best as one unit.
        val isRate = Base.entries.any { dim[it] < 0 } && dim != Dim.FUEL.inverse()
        val preferred: UnitDef? = run {
            for (h in hints) for ((hu, _) in h.terms) {
                PARTS[hu.id]?.get(dim)?.let { return@run UnitCatalog.find(it) }
                if (hu.dim == dim && hu.kind != UnitKind.CURRENCY) return@run hu
            }
            if (isRate) null else DEFAULT_UNIT[dim]?.let { UnitCatalog.find(it) }
        }
        if (preferred == null) {
            if (isRate) return q
            // Same-dimension products like m·m are already merged; leave others as they are.
            return q
        }
        return convertQty(q, UnitExpr.of(preferred))
    }

    private fun withUnit(v: Value, unit: UnitExpr): Value = when (v) {
        is Qty -> if (v.isPlain) Qty(v.num, unit) else convertQty(v, unit)
        is Pct -> throw EvalError("unit on %")
        else -> throw EvalError("unit")
    }

    // ------------------------------------------------------------------ conversion

    fun convertQty(q: Qty, target: UnitExpr): Qty {
        if (q.isPlain) return Qty(q.num, target)
        if (target.isNone) return Qty(q.num * Num.of(q.unit.factor(ctx) ?: throw EvalError("unit")))
        if (q.unit == target) return q
        val sd = q.unit.dim
        val td = target.dim
        if (q.unit.isTemperature && target.isTemperature) {
            val s = q.unit.single!!
            val t = target.single!!
            val k = (q.num + Num.of(s.offset)) * Num.of(s.factor)
            return Qty(k / Num.of(t.factor) - Num.of(t.offset), target)
        }
        // Money with a custom rate on this line: "50 EUR in USD at 1.05 USD/EUR".
        val qc = q.unit.currency
        val tc = target.currency
        if (qc != null && tc != null && q.unit.isCurrencyOnly && target.isCurrencyOnly) {
            ctx.pairFactor(qc, tc)?.let { return Qty(q.num * Num.of(it), target) }
        }
        if (sd == td) {
            val fs = q.unit.factor(ctx) ?: throw EvalError("no rate for ${q.unit}")
            val ft = target.factor(ctx) ?: throw EvalError("no rate for $target")
            return Qty(q.num * Num.of(fs) / Num.of(ft), target)
        }
        if (sd == td.inverse()) {
            // Fuel economy and the like: L/100 km ↔ mpg.
            val fs = q.unit.factor(ctx) ?: throw EvalError("unit")
            val ft = target.factor(ctx) ?: throw EvalError("unit")
            if (q.num.isZero) throw EvalError("division by zero")
            return Qty(Num.ONE / (q.num * Num.of(fs)) / Num.of(ft), target)
        }
        // "3 mph to minutes": keep the part of the unit that matches.
        throw EvalError("can't convert ${q.unit} to $target")
    }

    private fun convert(v0: Value, t: Target, src: Node): Value {
        val v = when (v0) { is Shown -> v0.value; else -> v0 }
        return when (t) {
            is UnitT -> when (v) {
                // Asked-for units show as written: "90 min in hours" is 1.5 h, not 1 h 30 min.
                is Qty -> convertQty(v, t.unit).let { if (t.unit.dim == Dim.TIME) Shown(it, Fmt(FmtKind.UNIT)) else it }
                is Range -> convertQty(rangeDiff(v) as? Qty ?: throw EvalError("range"), t.unit)
                is Moment -> throw EvalError("a time isn't a unit")
                is Mult -> throw EvalError("multiplier")
                else -> throw EvalError("convert")
            }
            is PlaceT -> when (v) {
                is Moment -> {
                    val base = if (v.zoneGiven || v.hasDate) v.dt else v.dt
                    Moment(base.withZoneSameInstant(t.place.zone), v.hasDate, v.hasTime || !v.hasDate, true, t.place.name, v.date)
                }
                else -> throw EvalError("in a place")
            }
            is RoundT -> {
                val step = unwrap(eval(t.step)) as? Qty ?: throw EvalError("nearest")
                val q = v as? Qty ?: throw EvalError("round")
                val s = if (step.isPlain) Qty(step.num, q.unit) else convertQty(step, q.unit)
                if (s.num.isZero) throw EvalError("nearest 0")
                val k = q.num / s.num
                val r = when (t.mode) { "up" -> k.ceil(); "down" -> k.floor(); else -> k.round(0) }
                Qty(r * s.num, q.unit)
            }
            is FmtT -> format(v, t.fmt)
        }
    }

    private fun format(v: Value, f: Fmt): Value = when (f.kind) {
        FmtKind.PERCENT -> when (v) {
            is Qty -> if (v.isPlain) Pct(v.num * Num.of(100)) else throw EvalError("% of a unit")
            is Pct -> v
            is Range -> pctChangeOf(v)
            else -> throw EvalError("as %")
        }
        FmtKind.NUMBER -> when (v) {
            is Pct -> Qty(v.num / Num.of(100))
            is Qty -> Qty(v.num)
            is Mult -> Qty(v.num)
            else -> throw EvalError("as number")
        }
        FmtKind.DEC -> when (v) {
            is Pct -> Qty(v.num / Num.of(100))
            is Qty -> Shown(v, f)
            else -> throw EvalError("decimal")
        }
        FmtKind.MULTIPLIER -> when (v) {
            is Qty -> if (v.isPlain) Mult(v.num) else throw EvalError("x")
            is Pct -> Mult(v.num / Num.of(100))
            is Range -> rangeDiff(v).let { throw EvalError("x") }
            else -> throw EvalError("x")
        }
        FmtKind.DP -> when (v) {
            is Qty -> Shown(Qty(v.num.round(f.n), v.unit), f)
            is Pct -> Shown(Pct(v.num.round(f.n)), f)
            else -> throw EvalError("dp")
        }
        FmtKind.SIGFIG -> when (v) {
            is Qty -> Shown(Qty(sigfig(v.num, f.n), v.unit), f)
            else -> throw EvalError("sig figs")
        }
        FmtKind.TIMESTAMP -> when (v) {
            is Moment -> Shown(Qty(Num.of(v.dt.toEpochSecond())), f)
            else -> throw EvalError("timestamp")
        }
        FmtKind.ISO -> if (v is Moment) Shown(v, f) else throw EvalError("iso")
        FmtKind.HEX, FmtKind.BIN, FmtKind.OCT -> when (v) {
            is Qty -> if (v.isPlain || true) Shown(Qty(v.num, v.unit), f) else throw EvalError("base")
            else -> throw EvalError("base")
        }
        else -> Shown(v, f)
    }

    private fun sigfig(n: Num, digits: Int): Num {
        if (n.isZero || digits <= 0) return n
        val bd = n.toBigDecimal().round(java.math.MathContext(digits, java.math.RoundingMode.HALF_UP))
        return Num.of(Rational.of(bd))
    }

    // ------------------------------------------------------------------ percentages

    private fun pctOf(v: Value): Num? = when (v) {
        is Pct -> v.num / Num.of(100)
        is Qty -> if (v.isPlain) v.num else null
        is Mult -> v.num
        else -> null
    }

    private fun pctApply(kind: String, p: Value, x0: Value): Value {
        val x = unwrap(x0)
        val frac = pctOf(p) ?: throw EvalError("percent")
        val isPct = p is Pct
        if (x is Pct) return when (kind) {
            "of" -> Pct(x.num * frac)
            "on" -> Pct(x.num * (Num.ONE + frac))
            else -> Pct(x.num * (Num.ONE - frac))
        }
        return when (kind) {
            "of" -> if (isPct || p is Mult) mul(Qty(frac), x) else mul(Qty(frac), x)
            "on" -> if (isPct) mul(Qty(Num.ONE + frac), x) else throw EvalError("on")
            "off" -> if (isPct) mul(Qty(Num.ONE - frac), x) else throw EvalError("off")
            else -> throw EvalError(kind)
        }
    }

    private fun pctOfWhat(n: PctOfWhat): Value {
        val p = eval(n.p)
        val frac = pctOf(p) ?: throw EvalError("percent")
        val y = unwrap(eval(n.y ?: throw EvalError("of what")))
        val divisor = when (n.kind) {
            "of" -> frac
            "on" -> Num.ONE + frac
            else -> Num.ONE - frac
        }
        return div(y, Qty(divisor))
    }

    private fun ratio(a: Value, b: Value): Num {
        val qa = unwrap(a)
        val qb = unwrap(b)
        val r = div(qa, qb)
        return when (r) {
            is Qty -> if (r.isPlain) r.num else throw EvalError("different units")
            else -> throw EvalError("ratio")
        }
    }

    private fun asPct(kind: String, a: Value, b: Value): Value {
        val r = ratio(a, b)
        val hundred = Num.of(100)
        return when (kind) {
            "of" -> Pct(r * hundred)
            "on" -> Pct((r - Num.ONE) * hundred)
            else -> Pct((Num.ONE - r) * hundred)
        }
    }

    private fun pctChangeOf(r: Range): Pct {
        val a = unwrap(r.from)
        val b = unwrap(r.to)
        val ratio = ratio(b, a)
        return Pct((ratio - Num.ONE) * Num.of(100))
    }

    private fun pctChange(n: PctChange): Value {
        val r = eval(n.range) as? Range ?: throw EvalError("change")
        val p = pctChangeOf(r)
        return when (n.kind) {
            K.PCT_DECREASE -> Pct(-p.num)
            else -> p
        }
    }

    private fun rangeDiff(r: Range): Value {
        val a = unwrap(r.from)
        val b = unwrap(r.to)
        return when {
            a is Moment && b is Moment -> momentDiff(a, b)
            else -> add(b, a, -1)
        }
    }

    // ------------------------------------------------------------------ juxtaposition, at, statistics

    private fun juxt(n: Juxt): Value {
        val a = unwrap(eval(n.a))
        val b = unwrap(eval(n.b))
        if (a is Qty && b is Qty) {
            // "1 m 20 cm", "3 h 20 min", "5 ft 11 in": a sum of like quantities.
            if (!a.isPlain && !b.isPlain && a.unit.dim == b.unit.dim) return addQty(a, b)
            // "1 1/2": a whole number and a fraction.
            if (a.isPlain && b.isPlain && n.b is NumLit && (n.b.v < Rational.ONE) && !n.b.v.isInteger && a.num.isInteger) return Qty(a.num + b.num)
            if (a.isPlain && b.isPlain && n.b is NumLit && n.a is NumLit) throw EvalError("two numbers")
            return mulQty(a, b)
        }
        if (a is Moment && b is Qty && !b.isPlain && b.unit.dim == Dim.TIME) return shiftMoment(a, b)
        return mul(a, b)
    }

    private fun at(n: AtNode): Value {
        if (n.b is PpiLit) {
            val ppi = (unwrap(eval(n.b.x)) as? Qty)?.num?.exact ?: throw EvalError("ppi")
            return Evaluator(ctx.with(ppi = ppi)).eval(n.a)
        }
        val b = unwrap(eval(n.b))
        // A custom exchange rate: "at 1.05 USD/EUR" means 1 EUR = 1.05 USD.
        if (b is Qty && b.unit.terms.size == 2 && b.unit.terms.all { it.first.kind == UnitKind.CURRENCY }) {
            val num = b.unit.terms.first { it.second == 1 }.first.id
            val den = b.unit.terms.first { it.second == -1 }.first.id
            val r = b.num.exact ?: throw EvalError("rate")
            return Evaluator(ctx.with(pair = mapOf((den to num) to r, (num to den) to Rational.ONE / r))).eval(n.a)
        }
        if (b is Qty && b.isPlain && n.a is Convert) {
            // "50 EUR in USD @ 1.05": the rate is target per source.
            val conv = n.a
            val srcV = unwrap(eval(conv.x)) as? Qty
            val tgt = (conv.target as? UnitT)?.unit
            if (srcV != null && tgt != null && srcV.unit.isCurrencyOnly && tgt.isCurrencyOnly) return Qty(srcV.num * b.num, tgt)
        }
        val a = unwrap(eval(n.a))
        if (a is Qty && b is Mult) return Qty(a.num / b.num, a.unit)
        if (a is Qty && b is Qty && !b.isPlain) {
            // "30 hours at $30/hour" → $900; "$500 at $20/hour" → 25 hours.
            val den = UnitExpr.of(b.unit.terms.filter { it.second < 0 }.map { it.first to -it.second })
            val numU = UnitExpr.of(b.unit.terms.filter { it.second > 0 })
            if (!den.isNone && a.unit.dim == den.dim) return mul(a, b)
            if (!numU.isNone && a.unit.dim == numU.dim) return div(a, b)
        }
        return mul(a, b)
    }

    private fun listStat(kind: String, items: List<Value>): Value {
        if (items.isEmpty()) throw EvalError("empty list")
        val vals = items.map { unwrap(it) }
        return stat(kind, vals, this)
    }

    private fun factorial(v: Value): Value {
        val q = unwrap(v) as? Qty ?: throw EvalError("!")
        val n = q.num.exact?.takeIf { it.isInteger && it.signum >= 0 }?.num ?: throw EvalError("! needs a whole number")
        if (n > BigInteger.valueOf(3000)) throw EvalError("too large")
        var r = BigInteger.ONE
        var i = BigInteger.TWO
        while (i <= n) { r *= i; i += BigInteger.ONE }
        return Qty(Num.of(Rational.of(r)))
    }

    // ------------------------------------------------------------------ functions

    private fun angleArg(v: Value): Double {
        val q = unwrap(v) as? Qty ?: throw EvalError("angle")
        if (q.isPlain) return if (st.degrees) Math.toRadians(q.num.double) else q.num.double
        if (q.unit.dim != Dim.ANGLE) throw EvalError("angle")
        return convertQty(q, UnitExpr.of(UnitCatalog.byId("rad"))).num.double
    }

    private fun angleResult(rad: Double): Value =
        if (st.degrees) Qty(Num.approx(Math.toDegrees(rad)), UnitExpr.of(UnitCatalog.byId("deg"))) else Qty(Num.approx(rad))

    private fun plainArg(v: Value): Num {
        val q = unwrap(v)
        return when (q) {
            is Qty -> if (q.isPlain) q.num else throw EvalError("needs a number")
            is Pct -> q.num / Num.of(100)
            else -> throw EvalError("needs a number")
        }
    }

    private fun call(fn: String, argNodes: List<Node>): Value {
        val args = argNodes.map { eval(it) }
        fun one(): Value = args.firstOrNull()?.let { unwrap(it) } ?: throw EvalError("$fn needs a value")
        fun unitOf(v: Value) = (v as? Qty)?.unit ?: UnitExpr.NONE
        return when (fn) {
            "sqrt" -> rootOf(one(), 2)
            "cbrt" -> rootOf(one(), 3)
            "root" -> {
                if (args.size == 2) {
                    val n = plainArg(args[1]).exact?.takeIf { it.isInteger }?.num?.toInt() ?: throw EvalError("root")
                    rootOf(unwrap(args[0]), n)
                } else rootOf(one(), 2)
            }
            "abs" -> (one() as? Qty)?.let { Qty(it.num.abs(), it.unit) } ?: (one() as? Pct)?.let { Pct(it.num.abs()) } ?: throw EvalError("abs")
            "ln" -> Qty(Num.approx(ln(plainArg(one()))))
            "log" -> if (args.size == 2) Qty(Num.approx(ln(plainArg(args[0])) / ln(plainArg(args[1]))))
                     else Qty(Num.approx(log10Exact(plainArg(one()))))
            "log2" -> Qty(Num.approx(ln(plainArg(one())) / Math.log(2.0)))
            "exp" -> Qty(Num.approx(Math.exp(plainArg(one()).double)))
            "sin" -> Qty(Num.approx(cleanTrig(Math.sin(angleArg(one())))))
            "cos" -> Qty(Num.approx(cleanTrig(Math.cos(angleArg(one())))))
            "tan" -> Qty(Num.approx(cleanTrig(Math.tan(angleArg(one())))))
            "sind" -> Qty(Num.approx(cleanTrig(Math.sin(Math.toRadians(plainArg(one()).double)))))
            "cosd" -> Qty(Num.approx(cleanTrig(Math.cos(Math.toRadians(plainArg(one()).double)))))
            "tand" -> Qty(Num.approx(cleanTrig(Math.tan(Math.toRadians(plainArg(one()).double)))))
            "asind" -> Qty(Num.approx(Math.toDegrees(Math.asin(plainArg(one()).double))), UnitExpr.of(UnitCatalog.byId("deg")))
            "acosd" -> Qty(Num.approx(Math.toDegrees(Math.acos(plainArg(one()).double))), UnitExpr.of(UnitCatalog.byId("deg")))
            "atand" -> Qty(Num.approx(Math.toDegrees(Math.atan(plainArg(one()).double))), UnitExpr.of(UnitCatalog.byId("deg")))
            "hex" -> Shown(one(), Fmt(FmtKind.HEX))
            "bin" -> Shown(one(), Fmt(FmtKind.BIN))
            "oct" -> Shown(one(), Fmt(FmtKind.OCT))
            "int" -> (one() as? Qty)?.let { Qty(it.num) } ?: throw EvalError("int")
            "asin" -> angleResult(Math.asin(plainArg(one()).double))
            "acos" -> angleResult(Math.acos(plainArg(one()).double))
            "atan" -> angleResult(Math.atan(plainArg(one()).double))
            "sinh" -> Qty(Num.approx(Math.sinh(plainArg(one()).double)))
            "cosh" -> Qty(Num.approx(Math.cosh(plainArg(one()).double)))
            "tanh" -> Qty(Num.approx(Math.tanh(plainArg(one()).double)))
            "fact" -> factorial(one())
            "round" -> (one() as? Qty)?.let { Qty(it.num.round(0), it.unit) } ?: throw EvalError("round")
            "floor" -> (one() as? Qty)?.let { Qty(it.num.floor(), it.unit) } ?: throw EvalError("floor")
            "ceil" -> (one() as? Qty)?.let { Qty(it.num.ceil(), it.unit) } ?: throw EvalError("ceil")
            "trunc" -> (one() as? Qty)?.let { Qty(if (it.num.signum < 0) it.num.ceil() else it.num.floor(), it.unit) } ?: throw EvalError("trunc")
            "sign" -> Qty(Num.of(plainArg(one()).signum))
            "gcd", "lcm" -> {
                val ints = args.flatMap { a -> (unwrap(a) as? ListVal)?.items ?: listOf(unwrap(a)) }.map {
                    plainArg(it).exact?.takeIf { r -> r.isInteger }?.num ?: throw EvalError("$fn needs whole numbers")
                }
                if (ints.isEmpty()) throw EvalError(fn)
                val r = if (fn == "gcd") ints.reduce { x, y -> x.gcd(y) } else ints.reduce { x, y -> if (x.signum() == 0 || y.signum() == 0) BigInteger.ZERO else (x * y).abs() / x.gcd(y) }
                Qty(Num.of(Rational.of(r)))
            }
            "random" -> {
                if (args.size >= 2) {
                    val lo = plainArg(args[0]).double; val hi = plainArg(args[1]).double
                    Qty(Num.of(Rational.of(kotlin.math.floor(lo + Math.random() * (hi - lo + 1)).toLong())))
                } else Qty(Num.approx(Math.random()))
            }
            "choose" -> {
                val n = plainArg(args.getOrNull(0) ?: throw EvalError("choose")).exact?.num ?: throw EvalError("choose")
                val k = plainArg(args.getOrNull(1) ?: throw EvalError("choose")).exact?.num ?: throw EvalError("choose")
                var r = BigInteger.ONE
                var i = BigInteger.ZERO
                while (i < k) { r = r * (n - i) / (i + BigInteger.ONE); i += BigInteger.ONE }
                Qty(Num.of(Rational.of(r)))
            }
            "fromunix" -> {
                val s = plainArg(one()).double
                val secs = if (s > 1e11) s / 1000 else s
                Moment(ZonedDateTime.ofInstant(java.time.Instant.ofEpochSecond(secs.toLong()), st.zone), hasDate = true, hasTime = true)
            }
            "min", "max", "avg", "sum", "median", "count", "stddev" -> listStat(fn, args)
            else -> throw EvalError("unknown function $fn").also { unitOf(one()) }
        }
    }

    private fun cleanTrig(d: Double): Double = if (kotlin.math.abs(d) < 1e-12) 0.0 else if (kotlin.math.abs(d - Math.rint(d)) < 1e-12) Math.rint(d) else d

    private fun ln(n: Num): Double {
        if (n.signum <= 0) throw EvalError("log of a non-positive number")
        return Math.log(n.double)
    }

    private fun log10Exact(n: Num): Double {
        if (n.signum <= 0) throw EvalError("log of a non-positive number")
        val r = n.exact
        if (r != null && r.isInteger) {
            val s = r.num.toString()
            if (s.startsWith("1") && s.drop(1).all { it == '0' }) return (s.length - 1).toDouble()
        }
        return Math.log10(n.double)
    }

    private fun rootOf(v: Value, n: Int): Value {
        val q = v as? Qty ?: throw EvalError("root")
        if (q.isPlain) return Qty(q.num.root(n))
        if (q.unit.terms.any { it.second % n != 0 }) throw EvalError("root of a unit")
        return Qty(q.num.root(n), UnitExpr.of(q.unit.terms.map { it.first to it.second / n }))
    }

    // ------------------------------------------------------------------ dates and times

    private fun atToday(t: LocalTime): ZonedDateTime { ctx.markTime(); return ZonedDateTime.of(ctx.now.toLocalDate(), t, st.zone) }

    private fun midday(d: LocalDate): ZonedDateTime = ZonedDateTime.of(d, LocalTime.NOON, st.zone)

    private fun dateOf(d: DateLit): Moment {
        val year = d.year ?: today.year
        val date = try { LocalDate.of(year, d.month, d.day) } catch (_: Exception) { throw EvalError("no such date") }
        return Moment(midday(date), hasDate = true, hasTime = false)
    }

    private fun nowWord(w: String): Moment {
        ctx.markTime()
        val now = ctx.now.withZoneSameInstant(st.zone)
        val d = now.toLocalDate()
        return when (w) {
            "now" -> Moment(now, hasDate = false, hasTime = true, refDate = d)
            "today", "tonight" -> Moment(midday(d), hasDate = true, hasTime = false)
            "tomorrow" -> Moment(midday(d.plusDays(1)), hasDate = true, hasTime = false)
            "yesterday" -> Moment(midday(d.minusDays(1)), hasDate = true, hasTime = false)
            "day after tomorrow" -> Moment(midday(d.plusDays(2)), hasDate = true, hasTime = false)
            "day before yesterday" -> Moment(midday(d.minusDays(2)), hasDate = true, hasTime = false)
            "week number" -> return0(Qty(Num.of(d.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR).toLong())))
            "day of year" -> return0(Qty(Num.of(d.dayOfYear.toLong())))
            in HOLIDAYS -> {
                // The next one, counting today.
                var y = d.year
                var date = holiday(w, y)
                if (date.isBefore(d)) { y++; date = holiday(w, y) }
                Moment(midday(date), hasDate = true, hasTime = false)
            }
            "noon", "midday" -> Moment(ZonedDateTime.of(d, LocalTime.NOON, st.zone), hasDate = false, hasTime = true)
            "midnight" -> Moment(ZonedDateTime.of(d, LocalTime.MIDNIGHT, st.zone), hasDate = false, hasTime = true)
            else -> Moment(now, hasDate = false, hasTime = true)
        }
    }

    private class Early(val v: Value) : RuntimeException()
    private fun return0(v: Value): Nothing = throw Early(v)

    private val HOLIDAYS = setOf("christmas", "christmas eve", "new year", "new year's eve", "halloween", "valentine's day", "easter",
        "good friday", "easter monday", "thanksgiving", "independence day", "pi day", "st patrick's day")

    private fun holiday(w: String, y: Int): LocalDate = when (w) {
        "christmas" -> LocalDate.of(y, 12, 25)
        "christmas eve" -> LocalDate.of(y, 12, 24)
        "new year" -> LocalDate.of(y, 1, 1)
        "new year's eve" -> LocalDate.of(y, 12, 31)
        "halloween" -> LocalDate.of(y, 10, 31)
        "valentine's day" -> LocalDate.of(y, 2, 14)
        "independence day" -> LocalDate.of(y, 7, 4)
        "pi day" -> LocalDate.of(y, 3, 14)
        "st patrick's day" -> LocalDate.of(y, 3, 17)
        "thanksgiving" -> LocalDate.of(y, 11, 1).with(TemporalAdjusters.dayOfWeekInMonth(4, java.time.DayOfWeek.THURSDAY))
        "easter" -> easter(y)
        "good friday" -> easter(y).minusDays(2)
        "easter monday" -> easter(y).plusDays(1)
        else -> throw EvalError(w)
    }

    /** Easter Sunday (Gregorian computus, "Anonymous" algorithm). */
    private fun easter(y: Int): LocalDate {
        val a = y % 19; val b = y / 100; val c = y % 100; val d = b / 4; val e = b % 4
        val f = (b + 8) / 25; val g = (b - f + 1) / 3; val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4; val k = c % 4; val l = (32 + 2 * e + 2 * i - h - k) % 7; val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31; val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(y, month, day)
    }

    private fun dayOf(n: DayNode): Moment {
        val d = today
        val date = when (n.rel) {
            K.NEXT -> d.with(TemporalAdjusters.next(n.day))
            K.LAST -> d.with(TemporalAdjusters.previous(n.day))
            else -> d.with(TemporalAdjusters.nextOrSame(n.day))
        }
        return Moment(midday(date), hasDate = true, hasTime = false)
    }

    private fun relPeriod(n: RelPeriod): Moment {
        val step = if (n.rel == K.LAST) -1L else if (n.rel == K.NEXT) 1L else 0L
        val u = n.unit.single?.id ?: throw EvalError("next what")
        val d = today
        val date = when (u) {
            "day" -> d.plusDays(step)
            "week" -> d.plusWeeks(step)
            "month" -> d.plusMonths(step)
            "year" -> d.plusYears(step)
            "quarter" -> d.plusMonths(3 * step)
            else -> throw EvalError("next $u")
        }
        return Moment(midday(date), hasDate = true, hasTime = false)
    }

    private fun atPlace(v: Value, place: Place): Value = when (v) {
        is Moment -> {
            // "3 pm Lisbon": that wall-clock time in Lisbon (today there).
            val local = v.dt.withZoneSameInstant(st.zone).toLocalDateTime()
            val dt = if (v.hasDate) ZonedDateTime.of(local, place.zone) else ZonedDateTime.of(ctx.now.withZoneSameInstant(place.zone).toLocalDate(), local.toLocalTime(), place.zone)
            Moment(dt, v.hasDate, true, true, place.name, dt.toLocalDate())
        }
        else -> throw EvalError("place")
    }

    /** Adds a duration to a moment: calendar-aware for months and years, exact otherwise. */
    fun shiftMoment(m: Moment, q: Qty): Moment {
        if (q.unit.dim != Dim.TIME) throw EvalError("add a duration")
        val u = q.unit.single
        val n = q.num
        val whole = n.exact?.takeIf { it.isInteger }?.num?.toLong()
        val dt = m.dt
        val out: ZonedDateTime
        var hasTime = m.hasTime
        when {
            u != null && whole != null && u.id in setOf("month", "year", "quarter", "decade", "century") -> {
                val months = whole * when (u.id) { "month" -> 1; "quarter" -> 3; "year" -> 12; "decade" -> 120; else -> 1200 }
                out = dt.plusMonths(months)
            }
            u != null && whole != null && u.id in setOf("day", "week", "fortnight") -> {
                out = dt.plusDays(whole * when (u.id) { "day" -> 1; "week" -> 7; else -> 14 })
            }
            u != null && whole != null && u.id == "workday" -> out = addWorkdays(dt, whole)
            else -> {
                val secs = convertQty(q, UnitExpr.of(UnitCatalog.byId("s"))).num
                val nanos = (secs.toBigDecimal() * java.math.BigDecimal(1_000_000_000)).toBigInteger()
                out = dt.plusNanos(nanos.toLong())
                if (!m.hasTime && secs.toRational().let { !(it / Rational.of(86400)).isInteger }) hasTime = true
            }
        }
        return m.copy(dt = out, hasTime = hasTime)
    }

    private fun addWorkdays(dt: ZonedDateTime, n: Long): ZonedDateTime {
        var d = dt
        var left = kotlin.math.abs(n)
        val step = if (n >= 0) 1L else -1L
        while (left > 0) {
            d = d.plusDays(step)
            if (d.dayOfWeek.value <= 5) left--
        }
        return d
    }

    fun momentDiff(from: Moment, to: Moment): Qty {
        if (!from.hasTime && !to.hasTime) {
            val days = ChronoUnit.DAYS.between(from.date, to.date)
            return Qty(Num.of(days), UnitExpr.of(UnitCatalog.byId("day")))
        }
        val secs = Duration.between(from.dt, to.dt).seconds
        val unit = when {
            kotlin.math.abs(secs) >= 86400 * 2 && secs % 86400 == 0L -> "day"
            kotlin.math.abs(secs) >= 3600 -> "h"
            kotlin.math.abs(secs) >= 60 -> "min"
            else -> "s"
        }
        val q = Qty(Num.of(secs), UnitExpr.of(UnitCatalog.byId("s")))
        return convertQty(q, UnitExpr.of(UnitCatalog.byId(unit)))
    }

    private fun until(n: Until): Value {
        val target = unwrap(eval(n.date)) as? Moment ?: throw EvalError("until a date")
        var t = target
        val d0 = today
        // "days until Dec 25": a date without a year means the next one (or the last one, for "since").
        val dateNode = n.date as? DateNode
        if (dateNode != null && dateNode.d.year == null) {
            if (!n.since && t.date.isBefore(d0)) t = t.copy(dt = t.dt.plusYears(1))
            if (n.since && t.date.isAfter(d0)) t = t.copy(dt = t.dt.minusYears(1))
        }
        val from = if (t.hasTime) Moment(ctx.now.withZoneSameInstant(st.zone), false, true) else Moment(midday(d0), true, false)
        val diff = if (n.since) momentDiff(t, from) else momentDiff(from, t)
        val unitNode = n.unit
        val unit = (unitNode as? BareUnit)?.unit ?: return diff
        if (unit.dim != Dim.TIME) throw EvalError("until")
        // Workdays count Monday to Friday.
        if (unit.single?.id == "workday") {
            var count = 0L
            var d = if (n.since) t.date else d0
            val end = if (n.since) d0 else t.date
            while (d.isBefore(end)) { d = d.plusDays(1); if (d.dayOfWeek.value <= 5) count++ }
            return Qty(Num.of(count), unit)
        }
        return convertQty(diff, unit)
    }

    private fun ago(n: Ago): Value {
        val d = unwrap(eval(n.x)) as? Qty ?: throw EvalError("ago")
        val base = if (d.unit.single?.id in setOf("day", "week", "month", "year", "fortnight", "quarter", "decade", "century"))
            Moment(midday(today), true, false) else Moment(ctx.now.withZoneSameInstant(st.zone), true, true).also { ctx.markTime() }
        return shiftMoment(base, if (n.future) d else Qty(-d.num, d.unit))
    }

    private fun shift(n: Shift): Value {
        val d = unwrap(eval(n.d)) as? Qty ?: throw EvalError("before/after")
        val m = unwrap(eval(n.date)) as? Moment ?: throw EvalError("before/after a date")
        return shiftMoment(m, if (n.after) d else Qty(-d.num, d.unit))
    }

    private fun compareValues(a: Value, b: Value): Int {
        val x = unwrap(a); val y = unwrap(b)
        return when {
            x is Qty && y is Qty -> { val yy = if (y.isPlain || x.isPlain) y else convertQty(y, x.unit); x.num.compareTo(yy.num) }
            x is Pct && y is Pct -> x.num.compareTo(y.num)
            x is Moment && y is Moment -> x.dt.compareTo(y.dt)
            else -> throw EvalError("compare")
        }
    }
}

/** Statistics over values that can be added: sum, avg, count, median, min, max, stddev. */
fun stat(kind: String, vals: List<Value>, ev: Evaluator): Value {
    if (kind == "count") return Qty(Num.of(vals.size.toLong()))
    if (vals.isEmpty()) throw EvalError("nothing")
    fun total(): Value = vals.drop(1).fold(vals.first()) { acc, v -> ev.add(acc, v, 1) }
    return when (kind) {
        "sum" -> total()
        "avg" -> ev.div(total(), Qty(Num.of(vals.size.toLong())))
        "min", "max" -> vals.reduce { a, b ->
            val c = ev.div(ev.add(a, b, -1), Qty(Num.ONE))
            val neg = (c as? Qty)?.num?.signum ?: (c as? Pct)?.num?.signum ?: 0
            if (kind == "min") (if (neg <= 0) a else b) else (if (neg >= 0) a else b)
        }
        "median" -> {
            val sorted = vals.sortedWith { a, b -> ((ev.add(a, b, -1) as? Qty)?.num?.signum ?: 0) }
            if (sorted.size % 2 == 1) sorted[sorted.size / 2]
            else ev.div(ev.add(sorted[sorted.size / 2 - 1], sorted[sorted.size / 2], 1), Qty(Num.of(2)))
        }
        "stddev" -> {
            if (vals.size < 2) throw EvalError("stddev needs two values")
            val qs = vals.map { it as? Qty ?: throw EvalError("stddev") }
            val unit = qs.first().unit
            val nums = qs.map { if (it.unit == unit || it.isPlain) it.num.double else ev.convertQty(it, unit).num.double }
            val mean = nums.average()
            val v = nums.sumOf { (it - mean) * (it - mean) } / (nums.size - 1)
            Qty(Num.approx(Math.sqrt(v)), unit)
        }
        else -> throw EvalError(kind)
    }
}
