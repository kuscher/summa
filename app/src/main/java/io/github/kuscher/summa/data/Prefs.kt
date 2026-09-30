package io.github.kuscher.summa.data

import android.content.Context
import android.text.format.DateFormat
import io.github.kuscher.summa.engine.EngineSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.chrono.IsoChronology
import java.util.Locale

/** Everything in Settings. Stored in SharedPreferences "summa" (backed up with the sheets). */
data class Settings(
    val theme: String = "tangerine",          // tangerine, wallpaper, cobalt, lime, berry
    val dark: String = "system",              // system, light, dark
    val textSize: Float = 16.5f,
    val lineNumbers: Boolean = true,
    val answerWidth: Float = 220f,
    val decimals: Int = -1,
    val thousands: Boolean = true,
    val degrees: Boolean = false,
    val mono: Boolean = false,
    val slashedZero: Boolean = false,
    val onlineRates: Boolean = true,
    val crypto: Boolean = false,
    /** "auto" follows the device; otherwise "us" (1,234.5), "eu" (1.234,5), "space" (1 234,5), "ch" (1'234.5). */
    val numberFormat: String = "auto",
    val dollar: String = "auto",
    val firstRunDone: Boolean = false,
    val sidebar: Boolean = true,
)

class Prefs(private val context: Context) {
    private val sp = context.getSharedPreferences("summa", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state.asStateFlow()
    val value: Settings get() = _state.value

    private fun read() = Settings(
        theme = sp.getString("theme", "tangerine")!!,
        dark = sp.getString("dark", "system")!!,
        textSize = sp.getFloat("textSize", 16.5f),
        lineNumbers = sp.getBoolean("lineNumbers", true),
        answerWidth = sp.getFloat("answerWidth", 220f),
        decimals = sp.getInt("decimals", -1),
        thousands = sp.getBoolean("thousands", true),
        degrees = sp.getBoolean("degrees", false),
        mono = sp.getBoolean("mono", false),
        slashedZero = sp.getBoolean("slashedZero", false),
        onlineRates = sp.getBoolean("onlineRates", true),
        crypto = sp.getBoolean("crypto", false),
        numberFormat = sp.getString("numberFormat", "auto")!!,
        dollar = sp.getString("dollar", "auto")!!,
        firstRunDone = sp.getBoolean("firstRunDone", false),
        sidebar = sp.getBoolean("sidebar", true),
    )

    fun update(f: (Settings) -> Settings) {
        val s = f(_state.value)
        sp.edit()
            .putString("theme", s.theme).putString("dark", s.dark).putFloat("textSize", s.textSize)
            .putBoolean("lineNumbers", s.lineNumbers).putFloat("answerWidth", s.answerWidth).putInt("decimals", s.decimals)
            .putBoolean("thousands", s.thousands).putBoolean("degrees", s.degrees).putBoolean("mono", s.mono)
            .putBoolean("slashedZero", s.slashedZero).putBoolean("onlineRates", s.onlineRates).putBoolean("crypto", s.crypto)
            .putString("numberFormat", s.numberFormat).putString("dollar", s.dollar).putBoolean("firstRunDone", s.firstRunDone)
            .putBoolean("sidebar", s.sidebar)
            .apply()
        _state.value = s
    }

    fun getString(key: String): String? = sp.getString(key, null)
    fun putString(key: String, v: String?) = sp.edit().putString(key, v).apply()

    /** The engine's view of these settings plus the device's locale, time zone and clock. */
    fun engineSettings(s: Settings = value): EngineSettings {
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val sym = DecimalFormatSymbols.getInstance(locale)
        val (dec, grp) = when (s.numberFormat) {
            "us" -> '.' to ','
            "eu" -> ',' to '.'
            "space" -> ',' to ' '
            "ch" -> '.' to '\''
            else -> sym.decimalSeparator to sym.groupingSeparator.let { if (it == ' ' || it == ' ') ' ' else it }
        }
        val dollar = if (s.dollar != "auto") s.dollar else when (locale.country) {
            "CA" -> "CAD"; "AU" -> "AUD"; "NZ" -> "NZD"; "SG" -> "SGD"; "HK" -> "HKD"; "MX" -> "MXN"; "TW" -> "TWD"
            else -> "USD"
        }
        val currencyAfter = (NumberFormat.getCurrencyInstance(locale) as? DecimalFormat)?.toPattern()?.let { p ->
            p.indexOf('¤') > p.indexOf('0').coerceAtLeast(0)
        } ?: false
        val datePattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.SHORT, null, IsoChronology.INSTANCE, locale)
        return EngineSettings(
            decimalSep = dec, groupSep = grp, dollar = dollar,
            yen = if (locale.country == "CN") "CNY" else "JPY",
            zone = ZoneId.systemDefault(), locale = locale,
            dayFirst = datePattern.indexOf('d') < datePattern.indexOf('M'),
            degrees = s.degrees, use24h = DateFormat.is24HourFormat(context),
            decimals = s.decimals, currencyAfter = currencyAfter, thousands = s.thousands,
        )
    }
}
