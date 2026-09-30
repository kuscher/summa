package io.github.kuscher.summa.data

import android.content.Context
import android.util.Log
import io.github.kuscher.summa.engine.Json
import io.github.kuscher.summa.engine.Rates
import io.github.kuscher.summa.engine.Rational
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.util.Date

/**
 * Exchange rates for the engine, never blocking a calculation:
 *  1. the ECB snapshot bundled in the APK,
 *  2. the last download, cached in files/rates/,
 *  3. a refresh at most every 12 hours when "Live exchange rates" is on: Frankfurter (central-bank
 *     rates for ~166 currencies and metals), falling back to the ECB's own XML,
 *  4. crypto from CoinGecko's keyless API, only when "Crypto prices" is on, and dropped after 24 hours
 *     as CoinGecko's terms ask.
 * Nothing but these rate tables is ever downloaded, and nothing is sent.
 */
class RatesRepo(private val context: Context, private val prefs: Prefs, private val scope: CoroutineScope) {
    private val dir = File(context.filesDir, "rates").apply { mkdirs() }
    private val fiatFile = File(dir, "fiat.json")
    private val cryptoFile = File(dir, "crypto.json")
    private val _rates = MutableStateFlow(compose())
    val rates: StateFlow<Rates> = _rates.asStateFlow()
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()
    private val lock = Mutex()

    private fun read(f: File): Rates? = try { if (f.exists()) Rates.fromJson(f.readText()) else null } catch (_: Exception) { null }

    private fun compose(): Rates {
        var r = Rates.bundled
        read(fiatFile)?.let { cached -> if ((cached.asOf ?: "") >= (r.asOf ?: "")) r = r.merged(cached) }
        val s = prefs.value
        if (s.crypto && cryptoFile.exists() && System.currentTimeMillis() - cryptoFile.lastModified() < DAY) {
            read(cryptoFile)?.let { c -> r = Rates(r.perEur + c.perEur, r.asOf, r.source) }
        }
        return r
    }

    /** Cheap to call often: refreshes only when due (or [force]). */
    fun refresh(force: Boolean = false) {
        val s = prefs.value
        if (!s.onlineRates) { _rates.value = compose(); return }
        scope.launch(Dispatchers.IO) {
            lock.withLock {
                val fiatDue = force || !fiatFile.exists() || System.currentTimeMillis() - fiatFile.lastModified() > 12 * HOUR
                val cryptoDue = s.crypto && (force || !cryptoFile.exists() || System.currentTimeMillis() - cryptoFile.lastModified() > 6 * HOUR)
                if (!fiatDue && !cryptoDue) { _rates.value = compose(); return@withLock }
                if (fiatDue) {
                    val ok = fetchFrankfurter() || fetchEcb()
                    _status.value = if (ok) "Updated ${time()} from ${read(fiatFile)?.source ?: "the web"}"
                                    else "Couldn't reach the rate services; using rates from ${_rates.value.asOf ?: "the app"}."
                }
                if (cryptoDue) fetchCoinGecko()
                _rates.value = compose()
            }
        }
    }

    /** Called when prefs change (the switches in Settings). */
    fun onSettingsChanged() { _rates.value = compose(); refresh() }

    private fun get(url: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "Summa (Android notepad calculator)")
        c.setRequestProperty("Accept", "application/json, application/xml")
        if (c.responseCode in 200..299) c.inputStream.bufferedReader().readText() else null.also { Log.i(TAG, "HTTP ${c.responseCode} $url") }
    } catch (e: Exception) { Log.i(TAG, "rates: ${e.javaClass.simpleName} ${e.message}"); null }

    private fun save(f: File, rates: Map<String, BigDecimal>, asOf: String?, source: String) {
        val body = rates.entries.sortedBy { it.key }.joinToString(",\n") { (k, v) -> "  ${Json.quote(k)}: ${Json.quote(v.toPlainString())}" }
        val json = "{\n \"base\": \"EUR\",\n \"asOf\": ${Json.quote(asOf ?: "")},\n \"source\": ${Json.quote(source)},\n \"rates\": {\n$body\n }\n}\n"
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(json)
        tmp.renameTo(f)
    }

    private fun fetchFrankfurter(): Boolean {
        val text = get("https://api.frankfurter.dev/v2/rates") ?: return false
        val arr = try { Json.parse(text) as? List<*> } catch (_: Exception) { null } ?: return false
        val rates = HashMap<String, BigDecimal>()
        var asOf = ""
        for (o in arr) {
            val m = o as? Map<*, *> ?: continue
            if (m["base"] != "EUR") continue
            val q = m["quote"] as? String ?: continue
            val r = m["rate"] as? BigDecimal ?: continue
            rates[q] = r
            (m["date"] as? String)?.let { if (it > asOf) asOf = it }
        }
        if (rates.size < 20) return false
        save(fiatFile, rates, asOf, "Frankfurter")
        return true
    }

    private fun fetchEcb(): Boolean {
        val xml = get("https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml") ?: return false
        val rates = HashMap<String, BigDecimal>()
        Regex("currency='([A-Z]{3})' rate='([0-9.]+)'").findAll(xml).forEach { rates[it.groupValues[1]] = BigDecimal(it.groupValues[2]) }
        val date = Regex("time='([0-9-]+)'").find(xml)?.groupValues?.get(1)
        if (rates.size < 10) return false
        save(fiatFile, rates, date, "European Central Bank")
        return true
    }

    private fun fetchCoinGecko(): Boolean {
        val text = get("https://api.coingecko.com/api/v3/exchange_rates") ?: return false
        val obj = (try { Json.parse(text) } catch (_: Exception) { null } as? Map<*, *>)?.get("rates") as? Map<*, *> ?: return false
        fun v(k: String) = ((obj[k] as? Map<*, *>)?.get("value") as? BigDecimal)
        val eurPerBtc = v("eur") ?: return false
        val out = HashMap<String, BigDecimal>()
        out["BTC"] = BigDecimal.ONE.divide(eurPerBtc, java.math.MathContext.DECIMAL64)
        for (code in listOf("eth", "ltc", "bch", "bnb", "eos", "xrp", "xlm", "link", "dot", "yfi", "sol")) {
            val perBtc = v(code) ?: continue
            out[code.uppercase()] = perBtc.divide(eurPerBtc, java.math.MathContext.DECIMAL64)
        }
        save(cryptoFile, out, java.time.LocalDate.now().toString(), "CoinGecko")
        return true
    }

    private fun time() = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())

    companion object {
        private const val TAG = "Summa"
        private const val HOUR = 3_600_000L
        private const val DAY = 24 * HOUR
        @Suppress("unused") private val keep = Rational.ONE
    }
}
