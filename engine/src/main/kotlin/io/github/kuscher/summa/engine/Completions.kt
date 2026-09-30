package io.github.kuscher.summa.engine

/** One autocomplete suggestion: what to insert and a short description. */
data class Completion(val insert: String, val detail: String, val kind: Kind) {
    enum class Kind { VARIABLE, UNIT, CURRENCY, FUNCTION, KEYWORD, PLACE }
}

/**
 * Autocomplete for the word being typed: your variables first, then common units, currencies,
 * functions and phrases. Everything is offline and instant.
 */
object Completions {
    /** [group] folds spellings of one thing together: km, kilometre and kilometers are one unit. */
    private class Entry(val key: String, val c: Completion, val rank: Int, val group: String)

    /** Units people use most, in the order they should appear. */
    private val COMMON_UNITS = listOf(
        "km", "m", "cm", "mm", "mi", "ft", "in", "yd", "kg", "g", "mg", "lb", "oz", "st", "L", "mL", "cup", "tbsp", "tsp", "gal", "fl oz",
        "h", "min", "s", "day", "week", "month", "year", "°C", "°F", "K", "km/h", "mph", "m/s", "knot", "kWh", "W", "kW", "hp", "J", "cal", "kcal",
        "GB", "MB", "TB", "kB", "GiB", "MiB", "Mbps", "Gbps", "px", "pt", "em", "rem", "ha", "acre", "bar", "psi", "Pa", "atm", "Hz", "mpg",
    )

    private val entries: List<Entry> by lazy { build() }

    private fun build(): List<Entry> {
        val out = ArrayList<Entry>()
        val common = COMMON_UNITS.withIndex().associate { (i, s) -> s to i }
        // Unit words ("kilometres") and symbols ("km"); each suggestion shows the other.
        val bySymbolRank = HashMap<String, Int>()
        for ((sym, u) in UnitCatalog.symbols) {
            val r = common[sym] ?: common[u.symbol] ?: 500
            bySymbolRank[u.id] = minOf(bySymbolRank[u.id] ?: 999, r)
            // Aliases (kmh, kwh) insert the canonical symbol (km/h, kWh).
            val insert = if (u.symbol.first().lowercaseChar() == sym.first().lowercaseChar() && u.kind != UnitKind.CURRENCY && u.word == null) u.symbol else sym
            if (sym.length >= 1 && sym.first().isLetter()) out += Entry(sym, Completion(insert, unitName(u), Completion.Kind.UNIT), r, "u:" + u.id)
        }
        for ((word, u) in UnitCatalog.words) {
            if (!word.first().isLetter()) continue
            val r = (bySymbolRank[u.id] ?: 500) + 1
            out += Entry(word, Completion(word, u.symbol, Completion.Kind.UNIT), r, "u:" + u.id)
        }
        // Currencies: codes and names.
        val popular = listOf("USD", "EUR", "GBP", "JPY", "CHF", "CAD", "AUD", "CNY", "INR", "SEK", "NOK", "DKK", "PLN", "BTC", "ETH")
        for ((code, def) in Currencies.byCode) {
            val r = popular.indexOf(code).let { if (it < 0) 300 else 20 + it }
            out += Entry(code, Completion(code, currencyName(code), Completion.Kind.CURRENCY), r, "c:$code")
        }
        for ((name, code) in Currencies.names) out += Entry(name, Completion(name, code, Completion.Kind.CURRENCY), 200, "c:$code")
        // Functions and phrases.
        val fns = listOf(
            "sqrt" to "square root", "round" to "round to a whole number", "floor" to "round down", "ceil" to "round up",
            "abs" to "absolute value", "log" to "log base 10", "ln" to "natural log", "sin" to "sine", "cos" to "cosine",
            "tan" to "tangent", "fact" to "factorial", "gcd" to "greatest common divisor", "lcm" to "least common multiple",
            "random" to "random number", "sum" to "total of the lines above", "average" to "average of the lines above",
            "prev" to "the answer above", "today" to "today's date", "tomorrow" to "tomorrow's date", "now" to "the time now",
            "until" to "days until a date", "since" to "time since a date", "hex" to "as hexadecimal", "binary" to "as binary",
            "fraction" to "as a fraction", "percent" to "%", "rounded" to "round the answer", "nearest" to "round to nearest",
            "next" to "next friday, next week…", "median" to "middle value", "count" to "how many lines",
            "loan" to "loan of $300k at 6% for 30 years", "mortgage" to "monthly payment", "interest" to "interest on $5k at 4% for 3 years",
            "compounded monthly" to "compounding", "workdays" to "workdays until…, workdays between…", "including" to "incl 20% VAT",
            "excluding" to "without 20% VAT", "next holiday" to "next public holiday",
        )
        for ((i, p) in fns.withIndex()) out += Entry(p.first, Completion(p.first, p.second, Completion.Kind.FUNCTION), 100 + i, "f:" + p.first)
        return out.sortedWith(compareBy<Entry> { it.rank }.thenBy { it.key.length })
    }

    private fun unitName(u: UnitDef): String =
        UnitCatalog.words.entries.firstOrNull { it.value == u && it.key.length > 3 && !it.key.contains('/') }?.key ?: u.symbol

    private fun currencyName(code: String): String = try {
        java.util.Currency.getInstance(code).getDisplayName(java.util.Locale.ENGLISH)
    } catch (_: Exception) { Currencies.names.entries.firstOrNull { it.value == code }?.key ?: code }

    /**
     * Suggestions for [prefix] (the word before the cursor). [afterNumber] favours units and
     * currencies ("5 k" → km, kg). Returns at most [limit], never the prefix itself.
     */
    fun suggest(
        prefix: String, vars: Collection<String> = emptyList(), afterNumber: Boolean = false, limit: Int = 6,
        defs: Definitions = Definitions.EMPTY,
    ): List<Completion> {
        if (prefix.isEmpty()) return emptyList()
        val lower = prefix.lowercase()
        val out = ArrayList<Completion>()
        val seen = HashSet<String>()
        fun add(c: Completion) { if (c.insert != prefix && seen.add(c.insert.lowercase() + c.kind)) out += c }
        for (v in vars.sortedBy { it.length }) if (v.lowercase().startsWith(lower) && v.length > prefix.length) add(Completion(v, "variable", Completion.Kind.VARIABLE))
        // Your own units and functions (this sheet's and the definitions sheet's).
        for (u in defs.units) u.names.firstOrNull { it.lowercase().startsWith(lower) && it.length > prefix.length }?.let {
            add(Completion(if (afterNumber) u.names.last() else it, "your unit", Completion.Kind.UNIT))
        }
        for (f in defs.functions.values) if (f.name.lowercase().startsWith(lower) && f.name.length > prefix.length)
            add(Completion(f.name + "(", "your function (${f.params.joinToString(", ")})", Completion.Kind.FUNCTION))
        val pool = entries.filter { e ->
            val exactCase = e.c.kind == Completion.Kind.UNIT && e.key.length <= 3
            (if (exactCase) e.key.startsWith(prefix) || (prefix.length >= 2 && e.key.lowercase().startsWith(lower)) else e.key.lowercase().startsWith(lower)) &&
                e.key.length > prefix.length
        }
        val ordered = if (afterNumber) pool.sortedBy { if (it.c.kind == Completion.Kind.UNIT || it.c.kind == Completion.Kind.CURRENCY) 0 else 1 }
                      else pool.sortedBy { if (it.c.kind == Completion.Kind.FUNCTION) 0 else 1 }.takeIf { prefix.length >= 2 } ?: pool
        val groups = HashSet<String>()
        // Plurals and spellings of one unit show once: prefer a symbol after a number, a word otherwise.
        val preferred = ordered.sortedWith(compareBy<Entry> { e ->
            val isSymbol = e.c.kind == Completion.Kind.UNIT && e.key.length <= 4 && e.key != e.key.lowercase() || e.key.length <= 3
            if (afterNumber) (if (isSymbol) 0 else 1) else 0
        })
        for (e in preferred) {
            if (!groups.add(e.group)) continue
            add(e.c); if (out.size >= limit) break
        }
        return out.take(limit)
    }

    private val RELATED: Map<Dim, List<String>> = mapOf(
        Dim.LENGTH to listOf("km", "m", "cm", "mm", "mi", "ft", "in", "yd", "nmi"),
        Dim.MASS to listOf("kg", "g", "lb", "oz", "st", "t"),
        Dim.VOLUME to listOf("L", "mL", "cup", "fl oz", "gal", "tbsp", "tsp", "m³"),
        Dim.AREA to listOf("m²", "km²", "ft²", "ha", "acres", "cm²"),
        Dim.TIME to listOf("s", "min", "hours", "days", "weeks", "months", "years"),
        Dim.TEMP to listOf("°C", "°F", "K"),
        Dim.SPEED to listOf("km/h", "mph", "m/s", "knots"),
        Dim.DATA to listOf("B", "kB", "MB", "GB", "TB", "MiB", "GiB"),
        Dim.ENERGY to listOf("J", "kJ", "kWh", "cal", "kcal", "BTU"),
        Dim.POWER to listOf("W", "kW", "hp"),
        Dim.PRESSURE to listOf("bar", "psi", "atm", "kPa", "hPa"),
        Dim.ANGLE to listOf("°", "rad", "turns"),
        Dim.FUEL to listOf("L/100 km", "mpg", "km/L"),
        Dim.FUEL.inverse() to listOf("mpg", "km/L", "L/100 km"),
    )

    /** Targets for "Convert to" on an answer: same-kind units, or common currencies for money. */
    fun conversionsFor(v: Value?, home: List<String> = listOf("EUR", "USD", "GBP", "JPY", "CHF")): List<String> {
        val q = (v as? Shown)?.value as? Qty ?: v as? Qty ?: return if (v is Qty || v is Pct) listOf("hex", "fraction") else emptyList()
        if (q.isPlain) return listOf("%", "hex", "binary", "fraction", "sci")
        if (q.unit.isCurrencyOnly) return home.filter { it != q.unit.currency }
        val current = q.unit.single?.symbol
        return RELATED[q.unit.dim].orEmpty().filter { it != current && it != q.unit.single?.word?.second }
    }
}
