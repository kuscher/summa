package io.github.kuscher.summa.engine

/**
 * Summa's unit table. Factors are exact (international definitions, as in Unicode CLDR's
 * units.xml): inch = 0.0254 m, pound = 0.45359237 kg, US gallon = 231 in³, and so on.
 *
 * Names come in two sets: [symbols] match case-sensitively (m vs M, B vs b) and [words] match
 * ignoring case ("Kilometres"). Area and volume mostly come from powers (m², sq ft, cubic cm).
 */
object UnitCatalog {
    val all = ArrayList<UnitDef>()
    val symbols = HashMap<String, UnitDef>()
    val words = HashMap<String, UnitDef>()
    private val byId = HashMap<String, UnitDef>()

    fun byId(id: String): UnitDef = byId[id] ?: error("unknown unit $id")
    fun find(id: String): UnitDef? = byId[id]

    private fun r(s: String): Rational = if ('/' in s) {
        val (a, b) = s.split('/'); Rational.parse(a) / Rational.parse(b)
    } else Rational.parse(s)

    private fun add(
        id: String, symbol: String, dim: Dim, factor: Rational,
        syms: String = "", names: String = "", word: Pair<String, String>? = null,
        kind: UnitKind = UnitKind.NORMAL, offset: Rational = Rational.ZERO, tight: Boolean = false,
        category: String = "", imperial: Boolean = false,
    ): UnitDef {
        val u = UnitDef(id, symbol, dim, factor, kind, offset, word, tight, category, imperial)
        all += u
        byId[id] = u
        for (s in syms.split(' ').filter { it.isNotBlank() }) symbols.putIfAbsent(s, u)
        for (n in names.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }) words.putIfAbsent(n, u)
        return u
    }

    private data class Prefix(val sym: String, val word: String, val f: String)

    private val SI = listOf(
        Prefix("p", "pico", "1e-12"), Prefix("n", "nano", "1e-9"), Prefix("µ", "micro", "1e-6"),
        Prefix("m", "milli", "1e-3"), Prefix("c", "centi", "1e-2"), Prefix("d", "deci", "1e-1"),
        Prefix("h", "hecto", "1e2"), Prefix("k", "kilo", "1e3"), Prefix("M", "mega", "1e6"),
        Prefix("G", "giga", "1e9"), Prefix("T", "tera", "1e12"), Prefix("P", "peta", "1e15"),
    )

    /** A base unit plus its SI-prefixed family; [which] limits the prefixes. */
    private fun family(
        id: String, sym: String, dim: Dim, factor: String, singular: String, plural: String,
        which: String, extraSyms: String = "", extraNames: String = "", category: String = "",
        altSpelling: Pair<String, String>? = null,
    ) {
        add(id, sym, dim, r(factor), "$sym $extraSyms", "$singular, $plural, $extraNames", category = category)
        for (p in SI.filter { it.sym in which.split(' ') || (it.sym == "µ" && "u" in which.split(' ')) }) {
            val f = r(factor) * r(p.f)
            val s = p.sym + sym
            var names = "${p.word}$singular, ${p.word}$plural"
            if (altSpelling != null) names += ", ${p.word}${altSpelling.first}, ${p.word}${altSpelling.second}"
            val syms = if (p.sym == "µ") "$s u$sym mc$sym" else s
            add(p.sym + id, s, dim, f, syms, names, category = category)
        }
    }

    init {
        val L = Dim.LENGTH; val M = Dim.MASS; val T = Dim.TIME; val V = Dim.VOLUME
        // ---- length
        family("m", "m", L, "1", "metre", "metres", "p n u m c d k", extraNames = "meter, meters", category = "metric",
            altSpelling = "meter" to "meters")
        add("in", "in", L, r("0.0254"), "″ \" inch inches", "inch, inches", imperial = true, word = null)
        add("ft", "ft", L, r("0.3048"), "′ ' ft", "foot, feet", imperial = true)
        add("yd", "yd", L, r("0.9144"), "yd yds", "yard, yards", imperial = true)
        add("mi", "mi", L, r("1609.344"), "mi", "mile, miles", imperial = true)
        add("nmi", "nmi", L, r("1852"), "nmi NM", "nautical mile, nautical miles")
        add("league", "leagues", L, r("4828.032"), "", "league, leagues", imperial = true)
        add("furlong", "furlongs", L, r("201.168"), "fur", "furlong, furlongs", imperial = true)
        add("chain", "chains", L, r("20.1168"), "", "chain, chains", imperial = true)
        add("fathom", "ftm", L, r("1.8288"), "ftm", "fathom, fathoms", imperial = true)
        add("mil", "mil", L, r("0.0000254"), "", "mil, mils, thou", imperial = true)
        add("au", "au", L, r("149597870700"), "au AU", "astronomical unit, astronomical units")
        add("ly", "ly", L, r("9460730472580800"), "ly", "light year, light years, light-year, light-years, lightyear, lightyears")
        add("pc", "pc", L, r("30856775814913673"), "pc", "parsec, parsecs")
        add("pt", "pt", L, r("0.0254") / r("72"), "pt", "point, points", tight = true)
        add("pica", "pc", L, r("0.0254") / r("6"), "", "pica, picas", tight = true)
        add("px", "px", L, r("0.0254") / r("96"), "px", "pixel, pixels, pxs", kind = UnitKind.PIXEL, tight = true)
        add("em", "em", L, r("0.0254") / r("6"), "em", "em, ems", kind = UnitKind.EM, tight = true)
        add("rem", "rem", L, r("0.0254") / r("6"), "rem", "rem, rems", kind = UnitKind.EM, tight = true)
        add("hand", "hands", L, r("0.1016"), "", "hand, hands", imperial = true)
        // ---- area (named ones; the rest are powers of lengths)
        add("ha", "ha", Dim.AREA, r("10000"), "ha", "hectare, hectares")
        add("are", "a", Dim.AREA, r("100"), "", "are, ares")
        add("acre", "ac", Dim.AREA, r("4046.8564224"), "ac", "acre, acres", imperial = true)
        add("ping", "ping", Dim.AREA, r("400/121"), "", "ping, píng")
        // ---- volume
        family("L", "L", V, "0.001", "litre", "litres", "u m c d h k M", extraSyms = "l ltr", extraNames = "liter, liters, ltr, ltrs",
            category = "metric", altSpelling = "liter" to "liters")
        add("ml2", "mL", V, r("0.000001"), "ml cc ccm", "cc, ccm, ml, mls")
        val gal = r("0.003785411784")
        add("gal", "gal", V, gal, "gal", "gallon, gallons, us gallon, us gallons", imperial = true)
        add("impgal", "imp gal", V, r("0.00454609"), "", "imperial gallon, imperial gallons, uk gallon, uk gallons")
        add("qt", "qt", V, gal / r("4"), "qt", "quart, quarts", imperial = true)
        add("pint", "pt", V, gal / r("8"), "", "pint, pints", imperial = true)
        add("cup", "cup", V, gal / r("16"), "", "cup, cups, us cup, us cups", word = "cup" to "cups", imperial = true)
        add("mcup", "metric cup", V, r("0.00025"), "", "metric cup, metric cups")
        add("floz", "fl oz", V, gal / r("128"), "floz", "fl oz, fl. oz., fl.oz., fluid ounce, fluid ounces, fl ounce, fl ounces", imperial = true)
        add("tbsp", "tbsp", V, gal / r("256"), "tbsp Tbsp", "tablespoon, tablespoons, tbs, tbsps, table spoon, table spoons", imperial = true)
        add("tsp", "tsp", V, gal / r("768"), "tsp", "teaspoon, teaspoons, tsps, tea spoon, tea spoons", imperial = true)
        add("bbl", "bbl", V, gal * r("42"), "bbl", "barrel, barrels, oil barrel, oil barrels")
        add("bushel", "bu", V, r("0.03523907016688"), "bu", "bushel, bushels", imperial = true)
        // ---- mass
        family("g", "g", M, "0.001", "gram", "grams", "u m c k", extraSyms = "gr", extraNames = "gramme, grammes", category = "metric")
        add("mcg", "µg", M, r("1e-9"), "mcg", "")
        add("t", "t", M, r("1000"), "t", "tonne, tonnes, metric ton, metric tons")
        add("lb", "lb", M, r("0.45359237"), "lb lbs", "pound, pounds, lbm", imperial = true)
        add("oz", "oz", M, r("0.45359237") / r("16"), "oz", "ounce, ounces", imperial = true)
        add("st", "st", M, r("6.35029318"), "st", "stone, stones", imperial = true)
        add("ton", "ton", M, r("907.18474"), "", "ton, tons, short ton, short tons, us ton, us tons", word = "ton" to "tons", imperial = true)
        add("longton", "long ton", M, r("1016.0469088"), "", "long ton, long tons, imperial ton, imperial tons")
        add("ct", "ct", M, r("0.0002"), "ct", "carat, carats")
        add("ozt", "oz t", M, r("0.0311034768"), "ozt", "troy ounce, troy ounces, oz t")
        add("grain", "gr", M, r("0.00006479891"), "", "grain, grains")
        add("centner", "centner", M, r("100"), "", "centner, centners, quintal, quintals")
        add("slug", "slug", M, r("14.59390293720636"), "", "slug, slugs")
        // ---- time
        family("s", "s", T, "1", "second", "seconds", "p n u m", extraSyms = "sec secs", extraNames = "sec, secs", category = "time")
        add("min", "min", T, r("60"), "min mins", "minute, minutes, mins")
        add("h", "h", T, r("3600"), "h hr hrs", "hour, hours, hr, hrs")
        add("day", "days", T, r("86400"), "d", "day, days", word = "day" to "days")
        add("week", "weeks", T, r("604800"), "wk wks", "week, weeks", word = "week" to "weeks")
        add("fortnight", "fortnights", T, r("1209600"), "", "fortnight, fortnights", word = "fortnight" to "fortnights")
        // Months and years as durations use the mean Gregorian year (365.2425 days), like Soulver.
        add("month", "months", T, r("2629746"), "mo mos", "month, months", word = "month" to "months")
        add("year", "years", T, r("31556952"), "yr yrs", "year, years", word = "year" to "years")
        add("quarter", "quarters", T, r("7889238"), "", "quarter, quarters", word = "quarter" to "quarters")
        add("decade", "decades", T, r("315569520"), "", "decade, decades", word = "decade" to "decades")
        add("century", "centuries", T, r("3155695200"), "", "century, centuries", word = "century" to "centuries")
        add("workday", "workdays", T, r("86400"), "", "workday, workdays, weekday, weekdays, business day, business days",
            word = "workday" to "workdays")
        // ---- temperature
        add("K", "K", Dim.TEMP, Rational.ONE, "K", "kelvin, kelvins", kind = UnitKind.TEMPERATURE)
        add("C", "°C", Dim.TEMP, Rational.ONE, "°C ºC C degC", "celsius, degrees celsius, degree celsius, deg c, centigrade",
            kind = UnitKind.TEMPERATURE, offset = r("273.15"), tight = true)
        add("F", "°F", Dim.TEMP, r("5/9"), "°F ºF F degF", "fahrenheit, degrees fahrenheit, degree fahrenheit, deg f",
            kind = UnitKind.TEMPERATURE, offset = r("459.67"), tight = true)
        // ---- angle
        val pi = r("3.14159265358979323846264338327950288419716939937510")
        add("rad", "rad", Dim.ANGLE, Rational.ONE, "rad", "radian, radians, rads")
        add("deg", "°", Dim.ANGLE, pi / r("180"), "° º deg", "degree, degrees, degs", tight = true)
        add("arcmin", "′", Dim.ANGLE, pi / r("10800"), "arcmin", "arcminute, arcminutes, arc minute, arc minutes", tight = true)
        add("arcsec", "″", Dim.ANGLE, pi / r("648000"), "arcsec", "arcsecond, arcseconds, arc second, arc seconds", tight = true)
        add("turn", "turns", Dim.ANGLE, pi * r("2"), "rev", "turn, turns, revolution, revolutions, rev, revs", word = "turn" to "turns")
        add("grad", "gon", Dim.ANGLE, pi / r("200"), "gon", "gradian, gradians, grad, grads")
        // ---- data: B/b with SI (×1000) and IEC (×1024) prefixes
        add("b", "bit", Dim.DATA, Rational.ONE, "b bit bits", "bit, bits", word = "bit" to "bits")
        add("B", "B", Dim.DATA, r("8"), "B", "byte, bytes")
        add("nibble", "nibbles", Dim.DATA, r("4"), "", "nibble, nibbles", word = "nibble" to "nibbles")
        val dec = listOf("k" to "kilo", "M" to "mega", "G" to "giga", "T" to "tera", "P" to "peta", "E" to "exa")
        for ((i, pw) in dec.withIndex()) {
            val f = Rational.of(java.math.BigInteger.TEN.pow(3 * (i + 1)))
            val fi = Rational.of(java.math.BigInteger.valueOf(1024).pow(i + 1))
            val s = pw.first
            val si = if (s == "k") "K" else s
            add("${s}B", "${s}B", Dim.DATA, f * r("8"), "${s}B" + (if (s == "k") " KB" else ""), "${pw.second}byte, ${pw.second}bytes")
            add("${s}b", "${s}b", Dim.DATA, f, "${s}b ${s}bit" + (if (s == "k") " Kb" else ""), "${pw.second}bit, ${pw.second}bits")
            val bi = pw.second.substring(0, 2) + "bi"
            add("${si}iB", "${si}iB", Dim.DATA, fi * r("8"), "${si}iB" + (if (s == "k") " kiB" else ""), "${bi}byte, ${bi}bytes")
            add("${si}ib", "${si}ib", Dim.DATA, fi, "${si}ib ${si}ibit", "${bi}bit, ${bi}bits")
        }
        add("bps", "bps", Dim.DATARATE, Rational.ONE, "bps", "bits per second")
        add("kbps", "kbps", Dim.DATARATE, r("1e3"), "kbps Kbps", "kilobits per second")
        add("Mbps", "Mbps", Dim.DATARATE, r("1e6"), "Mbps mbps", "megabits per second")
        add("Gbps", "Gbps", Dim.DATARATE, r("1e9"), "Gbps gbps", "gigabits per second")
        add("MBps", "MB/s", Dim.DATARATE, r("8e6"), "MBps", "megabytes per second")
        // ---- speed
        add("kmh", "km/h", Dim.SPEED, r("1000/3600"), "kph kmh km/h kmph", "kilometres per hour, kilometers per hour, km per hour")
        add("mph", "mph", Dim.SPEED, r("1609.344/3600"), "mph", "miles per hour, mile per hour", imperial = true)
        add("mps", "m/s", Dim.SPEED, Rational.ONE, "m/s", "metres per second, meters per second")
        add("knot", "kn", Dim.SPEED, r("1852/3600"), "kn kt kts", "knot, knots")
        add("fps", "ft/s", Dim.SPEED, r("0.3048"), "ft/s", "feet per second", imperial = true)
        add("mach", "Mach", Dim.SPEED, r("340.29"), "", "mach")
        add("c0", "c", Dim.SPEED, r("299792458"), "", "speed of light")
        // ---- acceleration
        add("gee", "g₀", Dim.ACCEL, r("9.80665"), "", "gravity, g-force, gs, standard gravity")
        // ---- energy
        family("J", "J", Dim.ENERGY, "1", "joule", "joules", "k M G")
        add("cal", "cal", Dim.ENERGY, r("4.184"), "cal", "calorie, calories")
        add("kcal", "kcal", Dim.ENERGY, r("4184"), "kcal Cal", "kilocalorie, kilocalories, kcals, food calorie, food calories")
        add("Wh", "Wh", Dim.ENERGY, r("3600"), "Wh", "watt hour, watt hours, watt-hour, watt-hours")
        add("kWh", "kWh", Dim.ENERGY, r("3600000"), "kWh kwh KWh", "kilowatt hour, kilowatt hours, kilowatt-hour, kilowatt-hours")
        add("MWh", "MWh", Dim.ENERGY, r("3600000000"), "MWh", "megawatt hour, megawatt hours")
        add("GWh", "GWh", Dim.ENERGY, r("3600000000000"), "GWh", "gigawatt hour, gigawatt hours")
        add("eV", "eV", Dim.ENERGY, r("1.602176634e-19"), "eV", "electronvolt, electronvolts, electron volt, electron volts")
        add("BTU", "BTU", Dim.ENERGY, r("1055.05585262"), "BTU Btu btu", "british thermal unit, british thermal units")
        add("therm", "thm", Dim.ENERGY, r("105505585.262"), "thm", "therm, therms")
        // ---- power
        family("W", "W", Dim.POWER, "1", "watt", "watts", "m k M G T")
        add("hp", "hp", Dim.POWER, r("745.69987158227022"), "hp HP bhp", "horsepower, horse power")
        add("PS", "PS", Dim.POWER, r("735.49875"), "PS", "metric horsepower")
        // ---- force
        family("N", "N", Dim.FORCE, "1", "newton", "newtons", "m k M")
        add("lbf", "lbf", Dim.FORCE, r("4.4482216152605"), "lbf", "pound-force, pounds-force, pound force, pounds force")
        add("dyn", "dyn", Dim.FORCE, r("0.00001"), "dyn", "dyne, dynes")
        // ---- pressure
        family("Pa", "Pa", Dim.PRESSURE, "1", "pascal", "pascals", "h k M G")
        add("bar", "bar", Dim.PRESSURE, r("100000"), "bar", "bar, bars")
        add("mbar", "mbar", Dim.PRESSURE, r("100"), "mbar", "millibar, millibars")
        add("atm", "atm", Dim.PRESSURE, r("101325"), "atm", "atmosphere, atmospheres")
        add("psi", "psi", Dim.PRESSURE, r("6894.757293168361"), "psi PSI", "pounds per square inch")
        add("mmHg", "mmHg", Dim.PRESSURE, r("133.322387415"), "mmHg", "millimetre of mercury, millimeters of mercury")
        add("inHg", "inHg", Dim.PRESSURE, r("3386.388640341"), "inHg", "inches of mercury")
        add("torr", "Torr", Dim.PRESSURE, r("101325/760"), "Torr", "torr")
        // ---- frequency
        family("Hz", "Hz", Dim.FREQUENCY, "1", "hertz", "hertz", "k M G T", extraSyms = "hz")
        add("rpm", "rpm", Dim.FREQUENCY, r("1/60"), "rpm RPM", "revolutions per minute")
        // ---- electrical
        family("A", "A", Dim.CURRENT, "1", "ampere", "amperes", "u m k", extraNames = "amp, amps")
        family("V", "V", Dim.VOLTAGE, "1", "volt", "volts", "u m k M")
        family("ohm", "Ω", Dim.RESISTANCE, "1", "ohm", "ohms", "m k M", extraSyms = "ohm")
        add("Ah", "Ah", Dim.CHARGE, r("3600"), "Ah", "amp hour, amp hours, ampere hour, ampere hours")
        add("mAh", "mAh", Dim.CHARGE, r("3.6"), "mAh", "milliamp hour, milliamp hours")
        add("coulomb", "C", Dim.CHARGE, Rational.ONE, "", "coulomb, coulombs")
        // ---- amount
        family("mol", "mol", Dim.AMOUNT, "1", "mole", "moles", "u m k", extraSyms = "mols")
        // ---- fuel economy (volume per length and its inverse)
        add("l100km", "L/100 km", Dim.FUEL, r("1e-8"), "L/100km l/100km", "l/100 km, L/100 km, litres per 100 km, liters per 100 km, litres per 100km, liters per 100km")
        add("mpg", "mpg", Dim.FUEL.inverse(), r("1609.344") / gal, "mpg MPG", "miles per gallon", imperial = true)
        add("kml", "km/L", Dim.FUEL.inverse(), r("1000000"), "km/l km/L kmpl", "kilometres per litre, kilometers per liter")
    }

    /** Units that read naturally as a word after a number when used as a bare "per" target. */
    val TIME_UNITS: Set<String> = setOf("s", "min", "h", "day", "week", "month", "year", "quarter", "fortnight", "decade", "century", "workday")
}
