package io.github.kuscher.summa.engine

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Everything a line can evaluate to. */
sealed interface Value

/** A number with an optional unit (money, length, rates like €/L). Plain numbers have [UnitExpr.NONE]. */
data class Qty(val num: Num, val unit: UnitExpr = UnitExpr.NONE) : Value {
    val isPlain get() = unit.isNone
    override fun toString() = if (unit.isNone) num.toString() else "$num $unit"
    companion object {
        fun of(n: Long) = Qty(Num.of(n))
        fun of(r: Rational) = Qty(Num.of(r))
    }
}

/** A percentage: 20% has [num] = 20. */
data class Pct(val num: Num) : Value

/** A multiplier like 1.5x. */
data class Mult(val num: Num) : Value

data class Bool(val v: Boolean) : Value

/**
 * A point in time. [hasDate]/[hasTime] say what the user wrote: "8:30 am" has no date (it means
 * today), "Dec 25" has no time. [zoneGiven] is true when a place or zone was named.
 */
data class Moment(
    val dt: ZonedDateTime,
    val hasDate: Boolean,
    val hasTime: Boolean,
    val zoneGiven: Boolean = false,
    /** The place name to show ("Munich"), when converted to a named place. */
    val place: String? = null,
    /** The day the user's input referred to, for "tomorrow at" / "yesterday at" labels. */
    val refDate: LocalDate? = null,
) : Value {
    val date: LocalDate get() = dt.toLocalDate()
    val zone: ZoneId get() = dt.zone
}

/** A range "a to b", kept until something decides what it means (difference, % change, interval). */
data class Range(val from: Value, val to: Value) : Value

/** A list of values ("3, 4 and 5") for statistics. */
data class ListVal(val items: List<Value>) : Value

/** A value plus how to show it (hex, fraction, scientific…). The value itself is unchanged. */
data class Shown(val value: Value, val format: Fmt) : Value

enum class FmtKind { UNIT, HEX, BIN, OCT, DEC, SCI, FRACTION, PERCENT, MULTIPLIER, DP, SIGFIG, TIMESTAMP, ISO, NUMBER, WORDS, COMPOSITE }

data class Fmt(val kind: FmtKind, val n: Int = 0)

/** A place for time-zone maths ("Tokyo"). */
data class Place(val name: String, val zone: ZoneId) : Value
