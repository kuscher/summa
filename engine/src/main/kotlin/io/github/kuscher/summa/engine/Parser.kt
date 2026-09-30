package io.github.kuscher.summa.engine

import java.time.DayOfWeek
import java.time.LocalTime

/** Expression tree for one line. */
sealed class Node
class NumLit(val v: Rational) : Node()
class ConstLit(val v: Num) : Node()
class Neg(val x: Node) : Node()
class Group(val x: Node) : Node()
class Bin(val op: String, val a: Node, val b: Node) : Node()
/** Two operands side by side: "1 m 20 cm" (a sum), "2(3)" or "2π" (a product), "1 1/2" (a mixed number). */
class Juxt(val a: Node, val b: Node) : Node()
class WithUnit(val x: Node, val unit: UnitExpr) : Node()
class BareUnit(val unit: UnitExpr) : Node()
class PctLit(val x: Node) : Node()
class PctApply(val kind: String, val p: Node, val x: Node) : Node()           // of / on / off
class PctOfWhat(val kind: String, val p: Node, val y: Node?) : Node()         // p% of what is y
class AsPct(val kind: String, val a: Node, val b: Node) : Node()             // a as % of b, a is what % of b
class PctChange(val kind: String, val range: Node) : Node()
class Convert(val x: Node, val target: Target) : Node()
class Call(val fn: String, val args: List<Node>) : Node()
class VarRef(val name: String) : Node()
class LineRef(val n: Int) : Node()
class Agg(val kind: String) : Node()
class ListStat(val kind: String, val items: List<Node>) : Node()
class DateNode(val d: DateLit) : Node()
class TimeNode(val t: LocalTime) : Node()
class NowNode(val word: String) : Node()
class DayNode(val rel: String?, val day: DayOfWeek) : Node()
class RelPeriod(val rel: String, val unit: UnitExpr) : Node()                // next week, last month
class AtPlace(val x: Node, val place: Place) : Node()
class PlaceNow(val place: Place) : Node()
class Until(val unit: Node?, val date: Node, val since: Boolean) : Node()
class Ago(val x: Node, val future: Boolean) : Node()
class Shift(val d: Node, val date: Node, val after: Boolean) : Node()        // 3 days before Dec 25
class RangeNode(val a: Node, val b: Node) : Node()
class Postfix(val op: String, val x: Node) : Node()                          // ! and powers
class AtNode(val a: Node, val b: Node) : Node()
class PpiLit(val x: Node) : Node()
class Scaled(val factor: Rational, val x: Node) : Node()                     // half of, double
class Cond(val cond: Node, val then: Node, val otherwise: Node?) : Node()
class Compare(val op: String, val a: Node, val b: Node) : Node()
class TagStat(val kind: String, val tag: String) : Node()
/**
 * Money over time: "fv" ($10k at 5% for 10 years), "interest" (just the interest), "simple"
 * (simple interest) and "loan" (the payment per period). [perYear] is the compounding or payment
 * frequency (0 = continuously), or null for the default.
 */
class Finance(val kind: String, val principal: Node, val rate: Node, val term: Node, val perYear: Int?) : Node()

sealed class Target
class UnitT(val unit: UnitExpr) : Target()
class FmtT(val fmt: Fmt) : Target()
class PlaceT(val place: Place) : Target()
class RoundT(val step: Node, val mode: String = "half") : Target()

class ParseError(msg: String) : Exception(msg)

/** A Pratt parser over [toks]; [parseAll] must consume everything. */
class Parser(private val toks: List<Tok>, private val start: Int = 0, private val end: Int = toks.size) {
    private var p = start

    private fun peek(k: Int = 0): Tok? = if (p + k < end) toks[p + k] else null
    private fun next(): Tok = if (p < end) toks[p++] else throw ParseError("unexpected end")
    private fun isKw(t: Tok?, vararg ids: String) = t != null && t.type == T.KW && t.v in ids
    private fun isOp(t: Tok?, vararg ids: String) = t != null && t.type == T.OP && t.v in ids

    fun parseAll(): Node {
        val n = expr(0)
        if (p < end) throw ParseError("unexpected ${toks[p]}")
        return n
    }

    /** Parses as much as possible from [start]; returns the node and where it stopped. */
    fun parsePrefix(): Pair<Node, Int>? = try {
        val n = expr(0); n to p
    } catch (_: ParseError) { null } catch (_: RuntimeException) { null }

    private fun expr(rbp: Int): Node {
        var left = nud(next())
        while (true) {
            val t = peek() ?: break
            val lbp = lbp(t, left)
            if (rbp >= lbp) break
            // If an operator can't complete ("$45 on clothes"), stop before it; the caller
            // decides whether a shorter expression is good enough.
            val save = p
            try {
                left = led(next(), left)
            } catch (e: ParseError) {
                p = save
                break
            }
        }
        return left
    }

    private fun startsOperand(t: Tok?): Boolean = t != null && (t.type in setOf(T.NUM, T.LPAREN, T.FUNC, T.CONST, T.VAR, T.LINEREF, T.CUR) ||
        (t.type == T.AGG && t.v == "prev"))

    private fun lbp(t: Tok, left: Node): Int = when (t.type) {
        T.OP -> when (t.v) {
            "|" -> 10; "xor" -> 11; "&" -> 12; "<<", ">>" -> 14
            "+", "-" -> 20
            "*", "/", "mod" -> 30
            "^" -> 50
            else -> 0
        }
        T.KW -> when (t.v) {
            K.IN, K.TO, K.AS -> 5
            K.UNTIL, K.SINCE -> 4
            K.IS, K.IS_WHAT_PCT_OF, K.IS_WHAT_PCT_ON, K.IS_WHAT_PCT_OFF, K.IS_WHAT_PCT -> 6
            K.AS_PCT_OF, K.AS_PCT_ON, K.AS_PCT_OFF -> 8
            K.AND -> 20
            K.OF, K.ON, K.OFF, K.OF_WHAT, K.ON_WHAT, K.OFF_WHAT -> 25
            K.BEFORE, K.AFTER -> 7
            K.PER -> 30
            K.AGO, K.FROM_NOW -> 35
            K.SQUARED, K.CUBED -> 55
            K.SQUARE, K.CUBIC -> 70
            K.PPI -> 70
            K.FROM -> if (left is PctLit) 25 else 0
            K.FOR -> 7
            K.INCL, K.EXCL -> 25
            "rounded", "rounded up", "rounded down" -> 40
            K.THEN, K.ELSE -> 0
            else -> 0
        }
        T.EQ -> if (peek(1) != null && t.text == "==") 9 else 0
        T.AT -> 7
        T.PCT -> 60
        T.BANG -> 60
        T.SUPER -> 55
        T.UNIT, T.CUR -> 70
        T.PLACE -> 70
        else -> if (startsOperand(t) && !t.gapBefore) 30 else 0
    }

    private fun nud(t: Tok): Node = when (t.type) {
        T.NUM -> numberNud(t)
        T.CONST -> ConstLit(t.v as Num)
        T.OP -> when (t.v) {
            "-" -> Neg(expr(45))
            "+" -> expr(45)
            else -> throw ParseError("operator")
        }
        T.LPAREN -> {
            val e = expr(0)
            if (peek()?.type == T.RPAREN) next()
            Group(e)
        }
        T.CUR -> {
            val code = t.v as String
            val nx = peek()
            if (nx != null && (nx.type == T.NUM || nx.type == T.LPAREN || nx.type == T.VAR || nx.type == T.CONST || nx.type == T.LINEREF ||
                        (nx.type == T.AGG && nx.v == "prev")) && !nx.gapBefore) {
                val x = expr(65)
                WithUnit(x, UnitExpr.of(Currencies.get(code)!!.unit))
            } else BareUnit(unitTail(UnitExpr.of(Currencies.get(code)!!.unit)))
        }
        T.UNIT -> {
            val u = unitTail(t.v as UnitExpr)
            // "workdays between Dec 1 and Dec 24", "days from today to Christmas"
            if (u.dim == Dim.TIME && (isKw(peek(), K.BETWEEN) || isKw(peek(), K.FROM))) {
                val range = kwNud(next())
                if (range is RangeNode) Convert(range, UnitT(u)) else throw ParseError("between")
            } else BareUnit(u)
        }
        T.KW -> kwNud(t)
        T.VAR -> VarRef(t.v as String)
        T.LINEREF -> LineRef(t.v as Int)
        T.AGG -> aggNud(t)
        T.FUNC -> funcNud(t)
        T.DATE -> DateNode(t.v as DateLit)
        T.TIME -> TimeNode(t.v as LocalTime)
        T.NOW -> NowNode(t.v as String)
        T.DAYWORD -> DayNode(null, t.v as DayOfWeek)
        T.PLACE -> {
            if (isKw(peek(), K.TIME)) next()
            PlaceNow(t.v as Place)
        }
        T.TAG -> TagStat("sum", t.v as String)
        else -> throw ParseError("unexpected ${t.type}")
    }

    private fun numberNud(t: Tok): Node {
        val n = NumLit(t.v as Rational)
        // "30 in" means inches when what follows can't be a conversion target
        // (but not "$7k in expenses": money, or a word right after "in").
        val afterCurrency = p >= 2 && toks[p - 2].type == T.CUR
        if (isKw(peek(), K.IN) && peek()?.text?.lowercase() == "in" && !isTargetStart(peek(1)) && !peek()!!.gapAfter && !afterCurrency) {
            next()
            return WithUnit(n, UnitExpr.of(UnitCatalog.byId("in")))
        }
        return n
    }

    private fun isTargetStart(t: Tok?): Boolean = t != null && (t.type in setOf(T.UNIT, T.CUR, T.FMT, T.PLACE, T.PCT) ||
        (t.type == T.FUNC && t.v in setOf("hex", "bin", "oct")) ||
        (t.type == T.KW && t.v in setOf(K.SQUARE, K.CUBIC)) || (t.type == T.OP && t.v == "/") || t.type == T.NUM && isFmtAfterNumber())

    private fun isFmtAfterNumber(): Boolean = false

    private fun kwNud(t: Tok): Node = when (t.v) {
        K.SQRT_OF -> Call("sqrt", listOf(expr(55)))
        K.CBRT_OF -> Call("cbrt", listOf(expr(55)))
        K.HALF -> Scaled(Rational.of(1, 2), expr(24))
        K.DOUBLE, K.TWICE -> { if (isKw(peek(), K.OF)) next(); Scaled(Rational.of(2), expr(24)) }
        K.TRIPLE -> { if (isKw(peek(), K.OF)) next(); Scaled(Rational.of(3), expr(24)) }
        K.SQUARE, K.CUBIC -> {
            val u = next()
            if (u.type != T.UNIT) throw ParseError("square what")
            BareUnit(unitTail((u.v as UnitExpr).pow(if (t.v == K.SQUARE) 2 else 3)))
        }
        K.NEXT, K.LAST, K.THIS -> {
            val nx = next()
            when (nx.type) {
                T.DAYWORD -> DayNode(t.v as String, nx.v as DayOfWeek)
                T.UNIT -> RelPeriod(t.v as String, nx.v as UnitExpr)
                else -> throw ParseError("next what")
            }
        }
        K.TIME -> {
            if (isKw(peek(), K.IN) && peek(1)?.type == T.PLACE) { next(); PlaceNow(next().v as Place) } else NowNode("now")
        }
        K.PCT_CHANGE, K.PCT_INCREASE, K.PCT_DECREASE -> {
            if (isKw(peek(), K.FROM)) next()
            val a = expr(6)
            if (isKw(peek(), K.TO) || isKw(peek(), K.AND)) next() else throw ParseError("from a to b")
            val b = expr(6)
            PctChange(t.v as String, RangeNode(a, b))
        }
        K.WHAT -> { if (isKw(peek(), K.IS)) next(); expr(0) }
        K.IS -> expr(0)
        K.IF -> {
            val c = expr(0)
            if (!isKw(peek(), K.THEN)) throw ParseError("if without then")
            next()
            val a = expr(0)
            val b = if (isKw(peek(), K.ELSE)) { next(); expr(0) } else null
            Cond(c, a, b)
        }
        K.FROM -> {
            val a = expr(6)
            if (isKw(peek(), K.TO) || isKw(peek(), K.UNTIL)) { next(); RangeNode(a, expr(6)) } else a
        }
        K.BETWEEN -> {
            val a = expr(21)
            if (!isKw(peek(), K.AND)) throw ParseError("between a and b")
            next()
            RangeNode(a, expr(21))
        }
        K.LOAN, K.INTEREST, K.SIMPLE_INTEREST -> {
            val inner = expr(0)
            if (inner !is Finance) throw ParseError("${t.v} needs a rate and a term")
            val kind = when (t.v) { K.LOAN -> "loan"; K.INTEREST -> "interest"; else -> "simple" }
            Finance(kind, inner.principal, inner.rate, inner.term, inner.perYear)
        }
        else -> throw ParseError("keyword ${t.v}")
    }

    private fun compounding(): Int? {
        val t = peek() ?: return null
        val id = (t.v as? String)?.takeIf { t.type == T.KW && it.startsWith(K.CMP) } ?: return null
        next()
        return id.removePrefix(K.CMP).toInt()
    }

    /** After "principal at rate": "for 10 years", optionally "compounded monthly" before or after. */
    private fun financeTail(principal: Node, rate: Node): Node? {
        var per = compounding()
        if (!isKw(peek(), K.FOR)) return null
        next()
        val term = expr(8)
        per = compounding() ?: per
        return Finance("fv", principal, rate, term, per)
    }

    private fun aggNud(t: Tok): Node {
        val kind = t.v as String
        if (kind == "prev") return Agg("prev")
        // "sum of 1, 2, 3", "average of 36, 42 and 81", "max(3, 5)", "total of #food"
        if (isKw(peek(), K.OF) || peek()?.type == T.LPAREN || (peek()?.type == T.NUM && !peek()!!.gapBefore)) {
            val paren = peek()?.type == T.LPAREN
            if (!paren && isKw(peek(), K.OF)) next()
            if (peek()?.type == T.TAG) return TagStat(kind, next().v as String)
            if (paren) next()
            val items = ArrayList<Node>()
            items += expr(21)
            while (peek()?.type == T.COMMA || isKw(peek(), K.AND) || (peek()?.type == T.NUM && !peek()!!.gapBefore)) {
                if (peek()?.type != T.NUM) next()
                items += expr(21)
            }
            if (paren && peek()?.type == T.RPAREN) next()
            if (items.size == 1 && !paren) {
                // "sum of line3"? a single item is just that value's statistic
                return ListStat(kind, items)
            }
            return ListStat(kind, items)
        }
        if (peek()?.type == T.TAG) return TagStat(kind, next().v as String)
        return Agg(kind)
    }

    private fun funcNud(t: Tok): Node {
        val fn = t.v as String
        // min/max/gcd/lcm take lists
        if (peek()?.type == T.LPAREN) {
            next()
            val args = ArrayList<Node>()
            if (peek()?.type != T.RPAREN) {
                args += expr(0)
                while (peek()?.type == T.COMMA) { next(); args += expr(0) }
            }
            if (peek()?.type == T.RPAREN) next()
            // Numi style: log 2 (10) handled below; here "root(2, 8)" or "sqrt(16)"
            return Call(fn, args)
        }
        if (fn in setOf("random") ) return Call(fn, emptyList())
        if (isKw(peek(), K.OF)) next()
        // Numi: "log 2 (10)", "root 2 (8)"
        if ((fn == "log" || fn == "root") && peek()?.type == T.NUM && peek(1)?.type == T.LPAREN) {
            val base = NumLit(next().v as Rational)
            next()
            val x = expr(0)
            if (peek()?.type == T.RPAREN) next()
            return Call(fn, listOf(x, base))
        }
        if (fn in setOf("gcd", "lcm")) {
            val items = ArrayList<Node>()
            items += expr(21)
            while (peek()?.type == T.COMMA || isKw(peek(), K.AND)) { next(); items += expr(21) }
            return Call(fn, items)
        }
        return Call(fn, listOf(expr(55)))
    }

    /** After a unit: powers (m², m^2), and "/ unit" or "per unit" to form rates. */
    private fun unitTail(u0: UnitExpr): UnitExpr {
        var u = u0
        while (true) {
            val t = peek() ?: break
            if (t.type == T.SUPER) { next(); u = powLast(u, t.v as Int); continue }
            if (isOp(t, "^") && peek(1)?.type == T.NUM && (peek(1)!!.v as Rational).isInteger && peek(2)?.type != T.UNIT) {
                next(); val n = next().v as Rational; u = powLast(u, n.num.toInt()); continue
            }
            if ((isOp(t, "/") || isKw(t, K.PER)) && (peek(1)?.type == T.UNIT || peek(1)?.type == T.CUR ||
                        (isKw(peek(1), K.SQUARE, K.CUBIC) && peek(2)?.type == T.UNIT))) {
                next()
                var d = next()
                var pw = 1
                if (d.type == T.KW) { pw = if (d.v == K.SQUARE) 2 else 3; d = next() }
                val du = if (d.type == T.CUR) UnitExpr.of(Currencies.get(d.v as String)!!.unit) else (d.v as UnitExpr)
                var den = du.pow(pw)
                if (peek()?.type == T.SUPER) { den = powLast(den, next().v as Int) }
                u = u / den
                continue
            }
            // Unit sequences in one unit phrase: "kW h"? (not supported) — stop.
            break
        }
        return u
    }

    private fun powLast(u: UnitExpr, n: Int): UnitExpr {
        if (u.terms.isEmpty()) return u
        val last = u.terms.last()
        return UnitExpr.of(u.terms.dropLast(1) + (last.first to last.second * n))
    }

    private fun led(t: Tok, left: Node): Node {
        when (t.type) {
            T.OP -> {
                val op = t.v as String
                if (op == "^") return Bin("^", left, expr(49))
                return Bin(op, left, expr(lbpOf(op)))
            }
            T.PCT -> {
                // "10 % 3" is a remainder; "10%" is a percentage.
                val nx = peek()
                if (nx != null && nx.type == T.NUM && !nx.gapBefore && left is NumLit && t.v == null) return Bin("mod", left, expr(30))
                return if (t.v == "permille") Bin("/", left, NumLit(Rational.of(10))).let { PctLit(it) } else PctLit(left)
            }
            T.BANG -> return Postfix("!", left)
            T.SUPER -> {
                val n = t.v as Int
                if (left is WithUnit) return WithUnit(left.x, powLast(left.unit, n))
                if (left is BareUnit) return BareUnit(powLast(left.unit, n))
                return Bin("^", left, NumLit(Rational.of(n.toLong())))
            }
            T.UNIT, T.CUR -> {
                val u0 = if (t.type == T.CUR) UnitExpr.of(Currencies.get(t.v as String)!!.unit) else t.v as UnitExpr
                val u = unitTail(u0)
                return if (left is WithUnit || left is BareUnit) Convert(left, UnitT(u)) else WithUnit(left, u)
            }
            T.PLACE -> return AtPlace(left, t.v as Place)
            T.AT -> {
                val b = expr(8)
                return financeTail(left, b) ?: AtNode(left, b)
            }
            T.EQ -> return Compare("==", left, expr(9))
            T.KW -> return kwLed(t, left)
            else -> {
                // Juxtaposition: "1 m 20 cm", "2(3)", "2π", "1 1/2"
                p--
                val right = expr(30)
                return Juxt(left, right)
            }
        }
    }

    private fun lbpOf(op: String) = when (op) {
        "|" -> 10; "xor" -> 11; "&" -> 12; "<<", ">>" -> 14; "+", "-" -> 20; "*", "/", "mod" -> 30; else -> 30
    }

    private fun kwLed(t: Tok, left: Node): Node = when (t.v) {
        K.IN, K.TO, K.AS -> conversion(t, left)
        K.PER -> Bin("/", left, expr(30))
        K.AND -> Bin("+", left, expr(20))
        K.OF -> {
            if (peek()?.type == T.TAG) TagStat("sum", next().v as String)
            else if (unwrapPct(left) != null) PctApply("of", left, expr(25)) else Bin("*", left, expr(25))
        }
        K.ON -> PctApply("on", left, expr(25))
        K.OFF -> PctApply("off", left, expr(25))
        K.OF_WHAT, K.ON_WHAT, K.OFF_WHAT -> {
            val kind = when (t.v) { K.OF_WHAT -> "of"; K.ON_WHAT -> "on"; else -> "off" }
            val y = if (isKw(peek(), K.IS)) { next(); expr(25) } else if (peek() != null && t.text.lowercase().endsWith("is")) expr(25) else null
            PctOfWhat(kind, left, y)
        }
        K.AS_PCT_OF -> AsPct("of", left, expr(9))
        K.AS_PCT_ON -> AsPct("on", left, expr(9))
        K.AS_PCT_OFF -> AsPct("off", left, expr(9))
        K.IS_WHAT_PCT_OF -> AsPct("of", left, expr(9))
        K.IS_WHAT_PCT_ON -> AsPct("on", left, expr(9))
        K.IS_WHAT_PCT_OFF -> AsPct("off", left, expr(9))
        K.IS_WHAT_PCT -> {
            // "50 to 75 is what %" → change; "3/20 is what %" → as percent
            if (left is RangeNode) PctChange(K.PCT_CHANGE, left) else Convert(left, FmtT(Fmt(FmtKind.PERCENT)))
        }
        K.IS -> {
            // "20 is 10% of what", "180 is 10% off what"
            val right = expr(6)
            if (right is PctOfWhat && right.y == null) PctOfWhat(right.kind, right.p, left)
            else Compare("==", left, right)
        }
        K.UNTIL, K.SINCE -> Until(left, expr(5), t.v == K.SINCE)
        K.AGO -> Ago(left, false)
        K.FROM_NOW -> Ago(left, true)
        K.BEFORE, K.AFTER -> Shift(left, expr(7), t.v == K.AFTER)
        K.SQUARED -> if (left is WithUnit) WithUnit(left.x, powLast(left.unit, 2)) else Bin("^", left, NumLit(Rational.of(2)))
        K.CUBED -> if (left is WithUnit) WithUnit(left.x, powLast(left.unit, 3)) else Bin("^", left, NumLit(Rational.of(3)))
        K.SQUARE, K.CUBIC -> {
            val u = next()
            if (u.type != T.UNIT) throw ParseError("square what")
            val unit = unitTail((u.v as UnitExpr).pow(if (t.v == K.SQUARE) 2 else 3))
            if (left is WithUnit) Convert(left, UnitT(unit)) else WithUnit(left, unit)
        }
        K.PPI -> PpiLit(left)
        K.FROM -> PctApply("of", left, expr(25))
        K.FOR -> {
            // "$10,000 for 10 years at 5%"
            var per = compounding()
            val term = expr(8)
            if (peek()?.type != T.AT) throw ParseError("for without at")
            next()
            val rate = expr(8)
            per = compounding() ?: per
            Finance("fv", left, rate, term, per)
        }
        // "€100 incl 20% VAT" adds the tax; "€120 without 20% VAT" takes an included tax out.
        K.INCL -> PctApply("on", expr(25), left)
        K.EXCL -> PctOfWhat("on", expr(25), left)
        "rounded", "rounded up", "rounded down" -> {
            val mode = when (t.v) { "rounded up" -> "up"; "rounded down" -> "down"; else -> "half" }
            // "rounded to nearest 10", "rounded up to nearest 5", or plain "rounded"
            if (isKw(peek(), K.TO) && isKw(peek(1), "nearest")) { next(); next(); Convert(left, RoundT(expr(21), mode)) }
            else if (isKw(peek(), K.TO) && peek(1)?.type == T.NUM && peek(2)?.type == T.FMT) { next(); val n = (next().v as Rational).num.toInt(); val f = next().v as Fmt; Convert(left, FmtT(Fmt(f.kind, n))) }
            else Convert(left, RoundT(NumLit(Rational.ONE), mode))
        }
        else -> throw ParseError("keyword ${t.v}")
    }

    private fun unwrapPct(n: Node): Node? = when (n) {
        is PctLit -> n
        is Group -> unwrapPct(n.x)
        else -> null
    }

    private fun conversion(t: Tok, left: Node): Node {
        val nx = peek() ?: run {
            // trailing "in" after a number = inches ("5 in"), unless words followed ("$7k in expenses")
            if (t.text.lowercase() == "in" && left is NumLit && !t.gapAfter) return WithUnit(left, UnitExpr.of(UnitCatalog.byId("in")))
            throw ParseError("convert to what")
        }
        when {
            nx.type == T.FMT -> { next(); return Convert(left, FmtT(nx.v as Fmt)) }
            nx.type == T.FUNC && nx.v in setOf("hex", "bin", "oct") -> {
                next(); return Convert(left, FmtT(Fmt(when (nx.v) { "hex" -> FmtKind.HEX; "bin" -> FmtKind.BIN; else -> FmtKind.OCT })))
            }
            nx.type == T.PCT -> { next(); return Convert(left, FmtT(Fmt(FmtKind.PERCENT))) }
            nx.type == T.PLACE -> { next(); return Convert(left, PlaceT(nx.v as Place)) }
            nx.type == T.UNIT || nx.type == T.CUR || isKw(nx, K.SQUARE, K.CUBIC) -> {
                next()
                val u = when {
                    nx.type == T.CUR -> unitTail(UnitExpr.of(Currencies.get(nx.v as String)!!.unit))
                    nx.type == T.UNIT -> unitTail(nx.v as UnitExpr)
                    else -> {
                        val base = next()
                        if (base.type != T.UNIT) throw ParseError("square what")
                        unitTail((base.v as UnitExpr).pow(if (nx.v == K.SQUARE) 2 else 3))
                    }
                }
                return Convert(left, UnitT(u))
            }
            isOp(nx, "/") && (peek(1)?.type == T.UNIT) -> {
                // "30/week as / month"
                next()
                val u = unitTail(next().v as UnitExpr)
                return Convert(left, UnitT(u.inverse()))
            }
            nx.type == T.NUM && peek(1)?.type == T.FMT && (peek(1)!!.v as Fmt).kind in setOf(FmtKind.DP, FmtKind.SIGFIG) -> {
                val n = (next().v as Rational).num.toInt()
                val f = next().v as Fmt
                return Convert(left, FmtT(Fmt(f.kind, n)))
            }
            nx.type == T.KW && nx.v == "nearest" -> {
                next()
                return Convert(left, RoundT(expr(21)))
            }
            nx.type == T.KW && nx.v in setOf("rounded", "rounded up", "rounded down") && isKw(peek(1), "nearest") -> {
                next(); next()
                return Convert(left, RoundT(expr(21), if (nx.v == "rounded up") "up" else if (nx.v == "rounded down") "down" else "half"))
            }
            nx.type == T.FUNC && nx.v == "round" -> { next(); return Convert(left, FmtT(Fmt(FmtKind.DP, 0))) }
        }
        // "a to b": a range between two values/dates.
        if (t.v == K.TO || t.text.lowercase() == "to") return RangeNode(left, expr(6))
        if (t.text.lowercase() == "in" && left is NumLit) return WithUnit(left, UnitExpr.of(UnitCatalog.byId("in")))
        throw ParseError("convert to what")
    }
}
