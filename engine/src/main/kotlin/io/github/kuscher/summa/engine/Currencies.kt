package io.github.kuscher.summa.engine

import java.math.BigDecimal

/** A currency: ISO code, how answers show it, and how many decimals it rounds to. */
class CurrencyDef(
    val code: String,
    /** Shown before the number ("$12.00") unless [suffix]. */
    val symbol: String,
    val suffix: Boolean,
    val digits: Int,
    val crypto: Boolean = false,
) {
    val unit: UnitDef = UnitDef(code, symbol, Dim.CURRENCY, Rational.ONE, UnitKind.CURRENCY)
}

/**
 * Every currency Summa knows, with the names and symbols people type. `$` means US dollars
 * unless the settings map it elsewhere. Lowercase ISO codes are only accepted where they can't
 * be an English word ("all", "top", "try" and "cup" are currency codes too).
 */
object Currencies {
    val byCode = LinkedHashMap<String, CurrencyDef>()
    /** Case-insensitive names: "euros", "swiss francs", "bitcoin". */
    val names = HashMap<String, String>()
    /** Case-sensitive symbols and prefixes: "$", "€", "US$", "C$". */
    val symbols = HashMap<String, String>()

    fun get(code: String): CurrencyDef? = byCode[code]

    private fun c(code: String, symbol: String = code, suffix: Boolean = symbol == code, digits: Int = 2,
                  names: String = "", crypto: Boolean = false, symbols: String = "") {
        byCode[code] = CurrencyDef(code, symbol, suffix, digits, crypto)
        this.names[code.lowercase()]?.let { } // codes are handled separately
        for (n in names.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }) this.names.putIfAbsent(n, code)
        for (s in symbols.split(' ').filter { it.isNotEmpty() }) this.symbols.putIfAbsent(s, code)
    }

    /** Lowercase codes that are safe to accept ("usd eur"). */
    val lowercaseCodes = setOf(
        "usd", "eur", "gbp", "jpy", "chf", "cad", "aud", "nzd", "cny", "rmb", "hkd", "sgd", "inr", "krw", "rub",
        "sek", "nok", "dkk", "pln", "czk", "huf", "ron", "bgn", "isk", "trl", "brl", "mxn", "zar", "ils", "aed",
        "sar", "thb", "idr", "myr", "php", "vnd", "twd", "uah", "egp", "ngn", "kes", "btc", "eth", "sol", "xrp",
        "ltc", "xau", "xag", "clp", "cop", "ars", "pen", "qar", "kwd", "bhd", "omr", "pkr", "bdt", "lkr",
    ) - "pen" // "pen" is a word too

    init {
        c("USD", "$", false, names = "dollar, dollars, us dollar, us dollars, usd, buck, bucks, american dollar, american dollars",
            symbols = "$ US$ U\$S")
        c("EUR", "€", false, names = "euro, euros", symbols = "€")
        c("GBP", "£", false, names = "pound sterling, pounds sterling, british pound, british pounds, quid, sterling, gbp", symbols = "£ ￡")
        c("JPY", "¥", false, 0, names = "yen, japanese yen", symbols = "¥ ￥ JP¥")
        c("CNY", "CN¥", false, names = "yuan, renminbi, rmb, chinese yuan", symbols = "CN¥ 元 RMB")
        c("CHF", "CHF", true, names = "swiss franc, swiss francs, franc, francs, sfr", symbols = "Fr. SFr.")
        c("CAD", "CA$", false, names = "canadian dollar, canadian dollars", symbols = "C$ CA$ CAD$")
        c("AUD", "A$", false, names = "australian dollar, australian dollars", symbols = "A$ AU$ AUD$")
        c("NZD", "NZ$", false, names = "new zealand dollar, new zealand dollars, kiwi dollar", symbols = "NZ$")
        c("HKD", "HK$", false, names = "hong kong dollar, hong kong dollars", symbols = "HK$")
        c("SGD", "S$", false, names = "singapore dollar, singapore dollars", symbols = "S$ SG$")
        c("TWD", "NT$", false, names = "taiwan dollar, taiwan dollars, new taiwan dollar", symbols = "NT$")
        c("MXN", "MX$", false, names = "mexican peso, mexican pesos, peso, pesos", symbols = "MX$ Mex$")
        c("BRL", "R$", false, names = "real, reais, brazilian real, brazilian reais", symbols = "R$")
        c("INR", "₹", false, names = "rupee, rupees, indian rupee, indian rupees", symbols = "₹ Rs Rs.")
        c("KRW", "₩", false, 0, names = "won, korean won, south korean won", symbols = "₩ ￦")
        c("RUB", "₽", false, names = "ruble, rubles, rouble, roubles, russian ruble, russian rubles", symbols = "₽")
        c("TRY", "₺", false, names = "lira, turkish lira, liras", symbols = "₺")
        c("UAH", "₴", false, names = "hryvnia, hryvnias, ukrainian hryvnia", symbols = "₴")
        c("ILS", "₪", false, names = "shekel, shekels, new shekel, new shekels, nis", symbols = "₪")
        c("THB", "฿", false, names = "baht, thai baht", symbols = "฿")
        c("VND", "₫", false, 0, names = "dong, vietnamese dong", symbols = "₫")
        c("NGN", "₦", false, names = "naira, nigerian naira", symbols = "₦")
        c("PHP", "₱", false, names = "philippine peso, philippine pesos", symbols = "₱")
        c("PLN", "zł", true, names = "zloty, zlotys, złoty, złote, polish zloty", symbols = "zł")
        c("CZK", "Kč", true, names = "koruna, korunas, czech koruna, czech crowns", symbols = "Kč")
        c("HUF", "Ft", true, names = "forint, forints, hungarian forint", symbols = "Ft")
        c("SEK", "SEK", names = "swedish krona, swedish kronor, krona, kronor")
        c("NOK", "NOK", names = "norwegian krone, norwegian kroner")
        c("DKK", "DKK", names = "danish krone, danish kroner")
        c("ISK", "ISK", digits = 0, names = "icelandic krona, icelandic kronur")
        c("RON", "RON", names = "romanian leu, lei")
        c("BGN", "BGN", names = "bulgarian lev, leva")
        c("ZAR", "R", false, names = "rand, south african rand", symbols = "")
        c("AED", "AED", names = "dirham, dirhams, uae dirham, emirati dirham")
        c("SAR", "SAR", names = "saudi riyal, saudi riyals, riyal, riyals")
        c("QAR", "QAR", names = "qatari riyal, qatari riyals")
        c("KWD", "KWD", digits = 3, names = "kuwaiti dinar, kuwaiti dinars")
        c("BHD", "BHD", digits = 3, names = "bahraini dinar, bahraini dinars")
        c("OMR", "OMR", digits = 3, names = "omani rial, omani rials")
        c("JOD", "JOD", digits = 3, names = "jordanian dinar, jordanian dinars")
        c("EGP", "EGP", names = "egyptian pound, egyptian pounds")
        c("MYR", "RM", false, names = "ringgit, malaysian ringgit", symbols = "RM")
        c("IDR", "Rp", false, 0, names = "rupiah, indonesian rupiah", symbols = "Rp")
        c("PKR", "PKR", names = "pakistani rupee, pakistani rupees")
        c("BDT", "৳", false, names = "taka, bangladeshi taka", symbols = "৳")
        c("LKR", "LKR", names = "sri lankan rupee, sri lankan rupees")
        c("KES", "KES", names = "kenyan shilling, kenyan shillings")
        c("MAD", "MAD", names = "moroccan dirham, moroccan dirhams")
        c("CLP", "CLP", digits = 0, names = "chilean peso, chilean pesos")
        c("COP", "COP", names = "colombian peso, colombian pesos")
        c("ARS", "ARS", names = "argentine peso, argentine pesos, argentinian peso")
        c("PEN", "S/", false, names = "sol, soles, peruvian sol", symbols = "S/")
        c("GEL", "₾", false, names = "lari, georgian lari", symbols = "₾")
        c("KZT", "₸", false, names = "tenge, kazakhstani tenge", symbols = "₸")
        c("RSD", "RSD", names = "serbian dinar, serbian dinars")
        c("TND", "TND", digits = 3, names = "tunisian dinar")
        c("XAU", "XAU", digits = 4, names = "gold, troy ounce of gold, ounce of gold")
        c("XAG", "XAG", digits = 4, names = "silver, ounce of silver")
        c("XPT", "XPT", digits = 4, names = "platinum")
        c("XPD", "XPD", digits = 4, names = "palladium")
        // Crypto (prices via CoinGecko when the user enables it).
        c("BTC", "₿", false, 8, names = "bitcoin, bitcoins", crypto = true, symbols = "₿")
        c("ETH", "ETH", digits = 6, names = "ether, ethereum", crypto = true)
        c("SOL", "SOL", digits = 4, names = "solana", crypto = true)
        c("XRP", "XRP", digits = 4, names = "ripple", crypto = true)
        c("LTC", "LTC", digits = 6, names = "litecoin, litecoins", crypto = true)
        c("BCH", "BCH", digits = 6, names = "bitcoin cash", crypto = true)
        c("BNB", "BNB", digits = 6, names = "binance coin", crypto = true)
        c("DOT", "DOT", digits = 4, names = "polkadot", crypto = true)
        c("LINK", "LINK", digits = 4, names = "chainlink", crypto = true)
        c("XLM", "XLM", digits = 4, names = "stellar, lumens", crypto = true)
        c("EOS", "EOS", digits = 4, crypto = true)
        c("YFI", "YFI", digits = 6, names = "yearn", crypto = true)
        c("SATS", "sats", true, 0, names = "sat, sats, satoshi, satoshis", crypto = true)
        // Every other ISO 4217 code the platform knows, shown by code.
        try {
            for (cur in java.util.Currency.getAvailableCurrencies()) {
                val code = cur.currencyCode
                if (code !in byCode && code.length == 3 && !code.startsWith("X")) {
                    val d = cur.defaultFractionDigits.let { if (it < 0) 2 else it }
                    byCode[code] = CurrencyDef(code, code, true, d)
                }
            }
        } catch (_: Throwable) {
        }
    }

    val cryptoCodes: Set<String> get() = byCode.values.filter { it.crypto }.map { it.code }.toSet()
}

/**
 * Exchange rates as "1 EUR = x CODE". Crypto arrives as prices per BTC and is folded in by
 * the app. The engine never waits on the network: it uses whatever table it's given.
 */
class Rates(val perEur: Map<String, Rational>, val asOf: String? = null, val source: String = "") {
    fun has(code: String) = code == "EUR" || perEur.containsKey(code)

    /** EUR per one [code]. */
    fun eurPer(code: String): Rational? = when (code) {
        "EUR" -> Rational.ONE
        "SATS" -> perEur["BTC"]?.let { Rational.ONE / it / Rational.of(100_000_000) }
        else -> perEur[code]?.takeIf { !it.isZero }?.let { Rational.ONE / it }
    }

    fun merged(other: Rates): Rates = Rates(perEur + other.perEur, other.asOf ?: asOf, other.source.ifEmpty { source })

    companion object {
        val EMPTY = Rates(emptyMap())

        /** Reads {"rates": {"USD": "1.1355", …}} as written by tools/rates_snapshot.py. */
        fun fromJson(json: String): Rates {
            val obj = Json.parse(json) as? Map<*, *> ?: return EMPTY
            val rates = (obj["rates"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                val code = k as? String ?: return@mapNotNull null
                val value = when (v) {
                    is String -> v.toBigDecimalOrNull()
                    is BigDecimal -> v
                    else -> null
                } ?: return@mapNotNull null
                code to Rational.of(value)
            }?.toMap() ?: emptyMap()
            return Rates(rates, obj["asOf"] as? String, obj["source"] as? String ?: "")
        }

        /** The ECB snapshot bundled with this build. */
        val bundled: Rates by lazy {
            val text = Rates::class.java.getResourceAsStream("/summa/rates.json")?.bufferedReader()?.readText()
            if (text == null) EMPTY else fromJson(text)
        }
    }
}

/** A small JSON reader (objects, arrays, strings, numbers as BigDecimal, booleans, null). */
object Json {
    fun parse(s: String): Any? = Parser(s).run { val v = value(); v }

    private class Parser(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            ws()
            if (i >= s.length) return null
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> { i += 4; true }
                'f' -> { i += 5; false }
                'n' -> { i += 4; null }
                else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("bad json at $i")
            }
        }
        fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++; ws()
            if (s[i] == '}') { i++; return m }
            while (true) {
                ws(); val k = str(); ws(); i++ // ':'
                m[k] = value(); ws()
                if (s[i] == ',') { i++; continue }
                i++; return m
            }
        }
        fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++; ws()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l += value(); ws()
                if (s[i] == ',') { i++; continue }
                i++; return l
            }
        }
        fun str(): String {
            val sb = StringBuilder()
            i++
            while (s[i] != '"') {
                if (s[i] == '\\') {
                    i++
                    when (val e = s[i]) {
                        'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                        'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                        'u' -> { sb.append(s.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }
                        else -> sb.append(e)
                    }
                } else sb.append(s[i])
                i++
            }
            i++
            return sb.toString()
        }
        fun num(): BigDecimal {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return BigDecimal(s.substring(start, i))
        }
    }

    fun quote(s: String): String = buildString {
        append('"')
        for (c in s) when (c) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
        append('"')
    }
}
