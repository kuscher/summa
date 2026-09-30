package io.github.kuscher.summa.engine

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/**
 * An exact fraction num/den, always normalized (den > 0, gcd 1). Summa computes with these so
 * `0.1 + 0.2` is exactly 0.3. When a denominator grows past [MAX_BITS] the value is rounded to
 * 40 significant digits (still stored as a fraction) so long chains stay fast.
 */
class Rational private constructor(val num: BigInteger, val den: BigInteger) : Comparable<Rational> {

    companion object {
        private const val MAX_BITS = 600
        val ZERO = Rational(BigInteger.ZERO, BigInteger.ONE)
        val ONE = Rational(BigInteger.ONE, BigInteger.ONE)
        val HUNDRED = of(100)
        private val MC = MathContext(40, RoundingMode.HALF_EVEN)

        fun of(n: Long): Rational = Rational(BigInteger.valueOf(n), BigInteger.ONE)
        fun of(n: BigInteger): Rational = Rational(n, BigInteger.ONE)

        fun of(n: BigInteger, d: BigInteger): Rational {
            require(d.signum() != 0) { "division by zero" }
            var a = n
            var b = d
            if (b.signum() < 0) { a = a.negate(); b = b.negate() }
            val g = a.gcd(b)
            if (g > BigInteger.ONE) { a /= g; b /= g }
            val r = Rational(a, b)
            return if (b.bitLength() > MAX_BITS) of(r.toBigDecimal(MC)) else r
        }

        fun of(n: Long, d: Long): Rational = of(BigInteger.valueOf(n), BigInteger.valueOf(d))

        fun of(d: BigDecimal): Rational {
            val s = d.stripTrailingZeros()
            return if (s.scale() <= 0) Rational(s.toBigIntegerExact(), BigInteger.ONE)
            else of(s.unscaledValue(), BigInteger.TEN.pow(s.scale()))
        }

        /** Parses "123", "1.25", "-0.5", "1e3", "2.5E-4" exactly. */
        fun parse(s: String): Rational = of(BigDecimal(s))

        fun ofDouble(d: Double): Rational = of(BigDecimal(d.toString()))
    }

    val signum: Int get() = num.signum()
    val isZero: Boolean get() = num.signum() == 0
    val isInteger: Boolean get() = den == BigInteger.ONE

    operator fun plus(o: Rational): Rational =
        if (den == o.den) of(num + o.num, den) else of(num * o.den + o.num * den, den * o.den)

    operator fun minus(o: Rational): Rational =
        if (den == o.den) of(num - o.num, den) else of(num * o.den - o.num * den, den * o.den)

    operator fun times(o: Rational): Rational = of(num * o.num, den * o.den)
    operator fun div(o: Rational): Rational = of(num * o.den, den * o.num)
    operator fun unaryMinus(): Rational = Rational(num.negate(), den)
    fun abs(): Rational = if (signum < 0) -this else this
    fun inverse(): Rational = of(den, num)

    /** Integer power; null when the result would be absurdly large. */
    fun pow(e: Int): Rational? {
        if (e == 0) return ONE
        val bits = maxOf(num.bitLength(), den.bitLength()).toLong() * kotlin.math.abs(e)
        if (bits > 20_000) return null
        val p = if (e > 0) of(num.pow(e), den.pow(e)) else of(den.pow(-e), num.pow(-e))
        return p
    }

    fun floor(): BigInteger {
        val (q, r) = num.divideAndRemainder(den)
        return if (r.signum() < 0) q - BigInteger.ONE else q
    }

    fun ceil(): BigInteger {
        val (q, r) = num.divideAndRemainder(den)
        return if (r.signum() > 0) q + BigInteger.ONE else q
    }

    fun toBigDecimal(mc: MathContext = MC): BigDecimal = BigDecimal(num).divide(BigDecimal(den), mc)

    /** Rounded to [scale] decimal places, half up (the way people round money). */
    fun toBigDecimal(scale: Int, mode: RoundingMode = RoundingMode.HALF_UP): BigDecimal =
        BigDecimal(num).divide(BigDecimal(den), scale, mode)

    fun toDouble(): Double = if (isInteger) num.toDouble() else toBigDecimal(MathContext.DECIMAL64).toDouble()

    override fun compareTo(other: Rational): Int = (num * other.den).compareTo(other.num * den)
    override fun equals(other: Any?): Boolean = other is Rational && num == other.num && den == other.den
    override fun hashCode(): Int = num.hashCode() * 31 + den.hashCode()
    override fun toString(): String = if (isInteger) num.toString() else "$num/$den"
}

/**
 * A number that is exact (a [Rational]) unless it came from a transcendental function, in which
 * case it's an approximate double and carries [approx] so the answer can say so.
 */
class Num private constructor(val exact: Rational?, private val d: Double) : Comparable<Num> {

    companion object {
        val ZERO = Num(Rational.ZERO, 0.0)
        val ONE = Num(Rational.ONE, 1.0)
        fun of(r: Rational) = Num(r, Double.NaN)
        fun of(n: Long) = of(Rational.of(n))
        fun of(n: Int) = of(Rational.of(n.toLong()))
        fun approx(d: Double): Num {
            // Doubles that are exactly representable small decimals become exact again, so
            // sqrt(16) or sin(90°) stay clean.
            if (d.isFinite()) {
                val rounded = Math.rint(d)
                if (rounded == d && kotlin.math.abs(d) < 1e15) return of(Rational.of(d.toLong()))
            }
            return Num(null, d)
        }
        fun parse(s: String) = of(Rational.parse(s))
    }

    val isExact: Boolean get() = exact != null
    val approx: Boolean get() = exact == null
    val double: Double get() = exact?.toDouble() ?: d
    val isFinite: Boolean get() = exact != null || d.isFinite()
    val signum: Int get() = exact?.signum ?: d.compareTo(0.0).coerceIn(-1, 1)
    val isZero: Boolean get() = exact?.isZero ?: (d == 0.0)
    val isInteger: Boolean get() = exact?.isInteger ?: (d.isFinite() && Math.rint(d) == d)

    private inline fun op(o: Num, e: (Rational, Rational) -> Rational, a: (Double, Double) -> Double): Num =
        if (exact != null && o.exact != null) of(e(exact, o.exact)) else Num(null, a(double, o.double))

    operator fun plus(o: Num) = op(o, { x, y -> x + y }, { x, y -> x + y })
    operator fun minus(o: Num) = op(o, { x, y -> x - y }, { x, y -> x - y })
    operator fun times(o: Num) = op(o, { x, y -> x * y }, { x, y -> x * y })
    operator fun div(o: Num): Num {
        if (o.isZero) throw EvalError("division by zero")
        return op(o, { x, y -> x / y }, { x, y -> x / y })
    }
    operator fun unaryMinus(): Num = if (exact != null) of(-exact) else Num(null, -d)
    fun abs(): Num = if (signum < 0) -this else this

    fun pow(e: Num): Num {
        val ex = e.exact
        if (exact != null && ex != null && ex.isInteger && ex.num.bitLength() < 31) {
            if (exact.isZero && ex.signum < 0) throw EvalError("division by zero")
            exact.pow(ex.num.toInt())?.let { return of(it) }
            throw EvalError("too large")
        }
        // Exact roots of exact numbers: 8^(1/3) = 2, 16^0.5 = 4.
        if (exact != null && ex != null && exact.signum >= 0 && ex.den.bitLength() < 8) {
            val root = exactRoot(exact, ex.den.toInt())
            if (root != null) return of(root).pow(of(Rational.of(ex.num)))
        }
        val r = Math.pow(double, e.double)
        if (r.isNaN()) throw EvalError("not a real number")
        return approx(r)
    }

    fun sqrt(): Num {
        if (signum < 0) throw EvalError("not a real number")
        exact?.let { r -> exactRoot(r, 2)?.let { return of(it) } }
        return approx(kotlin.math.sqrt(double))
    }

    fun root(n: Int): Num {
        if (n <= 0) throw EvalError("bad root")
        if (signum < 0 && n % 2 == 1) return -(-this).root(n)
        if (signum < 0) throw EvalError("not a real number")
        exact?.let { r -> exactRoot(r, n)?.let { return of(it) } }
        return approx(Math.pow(double, 1.0 / n))
    }

    fun floor(): Num = exact?.let { of(Rational.of(it.floor())) } ?: approx(kotlin.math.floor(d))
    fun ceil(): Num = exact?.let { of(Rational.of(it.ceil())) } ?: approx(kotlin.math.ceil(d))

    /** Round half away from zero to [places] decimals (negative places round to tens, hundreds…). */
    fun round(places: Int = 0): Num {
        val r = exact ?: Rational.ofDouble(d)
        val bd = r.toBigDecimal(MathContext(60)).setScale(places, RoundingMode.HALF_UP)
        return of(Rational.of(bd))
    }

    fun toBigDecimal(mc: MathContext = MathContext(34)): BigDecimal =
        exact?.toBigDecimal(mc) ?: BigDecimal(d, mc)

    fun toRational(): Rational = exact ?: Rational.ofDouble(d)

    override fun compareTo(other: Num): Int =
        if (exact != null && other.exact != null) exact.compareTo(other.exact) else double.compareTo(other.double)

    override fun equals(other: Any?): Boolean = other is Num && compareTo(other) == 0 && approx == other.approx
    override fun hashCode(): Int = exact?.hashCode() ?: d.hashCode()
    override fun toString(): String = exact?.toString() ?: "≈$d"
}

/** Exact n-th root of a non-negative rational, or null if it isn't a perfect power. */
private fun exactRoot(r: Rational, n: Int): Rational? {
    if (n == 1) return r
    val a = intRoot(r.num, n) ?: return null
    val b = intRoot(r.den, n) ?: return null
    return Rational.of(a, b)
}

private fun intRoot(x: BigInteger, n: Int): BigInteger? {
    if (x.signum() < 0) return null
    if (x.signum() == 0 || x == BigInteger.ONE) return x
    if (x.bitLength() > 4000) return null
    // Newton iteration on integers (BigInteger.sqrt() needs Android 13, so don't use it).
    var y = BigInteger.ONE.shiftLeft(x.bitLength() / n + 1)
    val nb = BigInteger.valueOf(n.toLong())
    val nm1 = BigInteger.valueOf((n - 1).toLong())
    while (true) {
        val next = (nm1 * y + x / y.pow(n - 1)) / nb
        if (next >= y) break
        y = next
    }
    return if (y.pow(n) == x) y else null
}

/** No sensible answer. A [final] error (wrong number of values for a function) also stops the "last valid expression" fallback. */
class EvalError(message: String, val final: Boolean = false) : Exception(message)
