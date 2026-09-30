package io.github.kuscher.summa.data

import android.content.Context
import android.text.format.DateFormat
import io.github.kuscher.summa.engine.EngineSettings
import io.github.kuscher.summa.engine.Holidays
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
    val textSize: Float = 17f,
    val lineNumbers: Boolean = false,
    val decimals: Int = -1,
    val onlineRates: Boolean = true,
    val crypto: Boolean = false,
    /** "auto" follows the device; otherwise "us" (1,234.5), "eu" (1.234,5), "space" (1 234,5), "ch" (1'234.5). */
    val numberFormat: String = "auto",
    val firstRunDone: Boolean = false,
    /** The sheet history on the left of desktop windows (Ctrl+B). */
    val sidebar: Boolean = true,
    /** The mini calculator asks to stay above other windows when it opens (Android 17 desktops). */
    val miniOnTop: Boolean = true,
    /** Public holidays for workday maths: "auto" (the device's region), "none", or a country code. */
    val holidays: String = "auto",
)

class Prefs(private val context: Context) {
    private val sp = context.getSharedPreferences("summa", Context.MODE_PRIVATE)

    init {
        // 1.1 (the simpler design): line numbers start off, and the settings that were removed
        // (answer column width, mono, slashed zero, thousands, trig mode, what $ means) go back
        // to their defaults.
        if (sp.getInt("uiVersion", 0) < 2) {
            sp.edit().putInt("uiVersion", 2).putBoolean("lineNumbers", false).putFloat("textSize", 17f)
                .remove("answerWidth").remove("mono").remove("slashedZero").remove("thousands").remove("degrees").remove("dollar")
                .apply()
        }
    }

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state.asStateFlow()
    val value: Settings get() = _state.value

    private fun read() = Settings(
        theme = sp.getString("theme", "tangerine")!!,
        dark = sp.getString("dark", "system")!!,
        textSize = sp.getFloat("textSize", 17f),
        lineNumbers = sp.getBoolean("lineNumbers", false),
        decimals = sp.getInt("decimals", -1),
        onlineRates = sp.getBoolean("onlineRates", true),
        crypto = sp.getBoolean("crypto", false),
        numberFormat = sp.getString("numberFormat", "auto")!!,
        firstRunDone = sp.getBoolean("firstRunDone", false),
        sidebar = sp.getBoolean("sidebar", true),
        miniOnTop = sp.getBoolean("miniOnTop", true),
        holidays = sp.getString("holidays", "auto")!!,
    )

    fun update(f: (Settings) -> Settings) {
        val s = f(_state.value)
        sp.edit()
            .putString("theme", s.theme).putString("dark", s.dark).putFloat("textSize", s.textSize)
            .putBoolean("lineNumbers", s.lineNumbers).putInt("decimals", s.decimals)
            .putBoolean("onlineRates", s.onlineRates).putBoolean("crypto", s.crypto)
            .putString("numberFormat", s.numberFormat).putBoolean("firstRunDone", s.firstRunDone)
            .putBoolean("sidebar", s.sidebar).putBoolean("miniOnTop", s.miniOnTop).putString("holidays", s.holidays)
            .apply()
        _state.value = s
    }

    fun getString(key: String): String? = sp.getString(key, null)
    fun putString(key: String, v: String?) = sp.edit().putString(key, v).apply()

    /** The country whose public holidays workday maths skips ("" for none). */
    fun holidayCountry(s: Settings = value): String = when (s.holidays) {
        "none" -> ""
        "auto" -> (context.resources.configuration.locales[0] ?: Locale.getDefault()).country.takeIf { Holidays.country(it) != null } ?: ""
        else -> s.holidays
    }

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
        val dollar = when (locale.country) {
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
            degrees = false, use24h = DateFormat.is24HourFormat(context),
            decimals = s.decimals, currencyAfter = currencyAfter, thousands = true,
            holidays = holidayCountry(s),
        )
    }
}
