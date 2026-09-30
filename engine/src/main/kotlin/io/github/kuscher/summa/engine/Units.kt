package io.github.kuscher.summa.engine

/** Base dimensions. Currency is one dimension; the code-to-code factor comes from the rate table. */
enum class Base { LENGTH, MASS, TIME, TEMP, CURRENT, AMOUNT, LUMINOUS, DATA, CURRENCY, ANGLE }

/** A dimension: integer exponents over [Base]. */
class Dim private constructor(private val e: IntArray) {
    companion object {
        val NONE = Dim(IntArray(Base.entries.size))
        fun of(vararg pairs: Pair<Base, Int>): Dim {
            val a = IntArray(Base.entries.size)
            for ((b, p) in pairs) a[b.ordinal] += p
            return Dim(a)
        }

        val LENGTH = of(Base.LENGTH to 1)
        val AREA = of(Base.LENGTH to 2)
        val VOLUME = of(Base.LENGTH to 3)
        val MASS = of(Base.MASS to 1)
        val TIME = of(Base.TIME to 1)
        val TEMP = of(Base.TEMP to 1)
        val DATA = of(Base.DATA to 1)
        val ANGLE = of(Base.ANGLE to 1)
        val CURRENCY = of(Base.CURRENCY to 1)
        val SPEED = of(Base.LENGTH to 1, Base.TIME to -1)
        val ENERGY = of(Base.MASS to 1, Base.LENGTH to 2, Base.TIME to -2)
        val POWER = of(Base.MASS to 1, Base.LENGTH to 2, Base.TIME to -3)
        val FORCE = of(Base.MASS to 1, Base.LENGTH to 1, Base.TIME to -2)
        val PRESSURE = of(Base.MASS to 1, Base.LENGTH to -1, Base.TIME to -2)
        val FREQUENCY = of(Base.TIME to -1)
        val CURRENT = of(Base.CURRENT to 1)
        val CHARGE = of(Base.CURRENT to 1, Base.TIME to 1)
        val VOLTAGE = of(Base.MASS to 1, Base.LENGTH to 2, Base.TIME to -3, Base.CURRENT to -1)
        val RESISTANCE = of(Base.MASS to 1, Base.LENGTH to 2, Base.TIME to -3, Base.CURRENT to -2)
        val AMOUNT = of(Base.AMOUNT to 1)
        val ACCEL = of(Base.LENGTH to 1, Base.TIME to -2)
        val DATARATE = of(Base.DATA to 1, Base.TIME to -1)
        val FUEL = of(Base.LENGTH to 2)          // volume per length, e.g. L/100 km
    }

    operator fun get(b: Base) = e[b.ordinal]
    operator fun times(o: Dim) = Dim(IntArray(e.size) { e[it] + o.e[it] })
    operator fun div(o: Dim) = Dim(IntArray(e.size) { e[it] - o.e[it] })
    fun pow(p: Int) = Dim(IntArray(e.size) { e[it] * p })
    fun inverse() = pow(-1)
    val isNone: Boolean get() = e.all { it == 0 }
    override fun equals(other: Any?) = other is Dim && e.contentEquals(other.e)
    override fun hashCode() = e.contentHashCode()
    override fun toString() = Base.entries.filter { e[it.ordinal] != 0 }.joinToString(" ") { "${it.name.lowercase()}^${e[it.ordinal]}" }
}

enum class UnitKind { NORMAL, CURRENCY, PIXEL, EM, TEMPERATURE }

/**
 * One named unit. [factor] converts one of it to SI base units (metre, kilogram, second, kelvin,
 * bit, radian…). Temperatures also carry [offset]: kelvin = (x + offset) × factor.
 */
class UnitDef(
    val id: String,
    /** How answers show it: "km", "°C", "$" (money uses [Currencies] formatting instead). */
    val symbol: String,
    val dim: Dim,
    val factor: Rational,
    val kind: UnitKind = UnitKind.NORMAL,
    val offset: Rational = Rational.ZERO,
    /** Answers with this unit read "86 days" rather than "86 d": singular and plural words. */
    val word: Pair<String, String>? = null,
    /** No space between number and symbol: 24px, 45°. */
    val tight: Boolean = false,
    /** Units of the same dimension compare by this when "the larger unit wins". */
    val category: String = "",
    /** A US-customary unit (answers from US inputs stay US where possible). */
    val imperial: Boolean = false,
) {
    override fun toString() = id
    override fun equals(other: Any?) = other is UnitDef && other.id == id
    override fun hashCode() = id.hashCode()
}

/** A product of units with integer powers: km/h is [(km,1),(h,-1)]. Empty means a plain number. */
class UnitExpr private constructor(val terms: List<Pair<UnitDef, Int>>) {
    companion object {
        val NONE = UnitExpr(emptyList())
        fun of(u: UnitDef, p: Int = 1) = UnitExpr(listOf(u to p))
        fun of(terms: List<Pair<UnitDef, Int>>): UnitExpr {
            val merged = LinkedHashMap<UnitDef, Int>()
            for ((u, p) in terms) merged[u] = (merged[u] ?: 0) + p
            return UnitExpr(merged.filter { it.value != 0 }.map { it.key to it.value })
        }
    }

    val isNone get() = terms.isEmpty()
    val dim: Dim get() = terms.fold(Dim.NONE) { d, (u, p) -> d * u.dim.pow(p) }
    val single: UnitDef? get() = terms.singleOrNull()?.takeIf { it.second == 1 }?.first
    val currency: String? get() = terms.firstOrNull { it.first.kind == UnitKind.CURRENCY && it.second == 1 }?.first?.id
    val isCurrencyOnly get() = terms.size == 1 && terms[0].first.kind == UnitKind.CURRENCY && terms[0].second == 1
    val hasCurrency get() = terms.any { it.first.kind == UnitKind.CURRENCY }
    val isTemperature get() = terms.size == 1 && terms[0].first.kind == UnitKind.TEMPERATURE && terms[0].second == 1

    operator fun times(o: UnitExpr) = of(terms + o.terms)
    operator fun div(o: UnitExpr) = of(terms + o.terms.map { it.first to -it.second })
    fun pow(p: Int) = of(terms.map { it.first to it.second * p })
    fun inverse() = pow(-1)

    override fun equals(other: Any?) = other is UnitExpr && terms.toSet() == other.terms.toSet()
    override fun hashCode() = terms.toSet().hashCode()
    override fun toString() = terms.joinToString("·") { (u, p) -> if (p == 1) u.id else "${u.id}^$p" }
}

/** Resolves factors that depend on the sheet: pixels (ppi), em (px per em) and currencies (rates). */
interface FactorResolver {
    /** Base units (EUR for money) per one of [u]; null when unknown (e.g. no rate for a currency). */
    fun factor(u: UnitDef): Rational?
}

fun UnitExpr.factor(r: FactorResolver): Rational? {
    var f = Rational.ONE
    for ((u, p) in terms) {
        val uf = r.factor(u) ?: return null
        f *= uf.pow(p) ?: return null
    }
    return f
}
