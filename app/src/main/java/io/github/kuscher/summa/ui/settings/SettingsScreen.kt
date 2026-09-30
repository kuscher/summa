package io.github.kuscher.summa.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kuscher.summa.BuildConfig
import io.github.kuscher.summa.SummaApp
import io.github.kuscher.summa.data.Settings
import io.github.kuscher.summa.engine.Holidays
import io.github.kuscher.summa.ui.Sym
import io.github.kuscher.summa.ui.SymIcon
import io.github.kuscher.summa.ui.theme.schemeFor

/** Settings: a short list of rows, nothing more. */
@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, showHeader: Boolean = true, onOpenDefinitions: (() -> Unit)? = null) {
    val app = SummaApp.instance
    val s by app.prefs.state.collectAsState()
    val rates by app.rates.rates.collectAsState()
    val scheme = MaterialTheme.colorScheme
    fun set(f: (Settings) -> Settings) = app.prefs.update(f)
    Column(modifier.fillMaxSize().background(scheme.surface)) {
        if (showHeader) Header("Settings", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 40.dp).widthIn(max = 680.dp)) {
            Group("Appearance") {
                Row("Theme") { ThemePicker(s.theme) { t -> set { it.copy(theme = t) } } }
                Row("Light or dark") { Segmented(s.dark, listOf("system" to "System", "light" to "Light", "dark" to "Dark"), "Light or dark") { v -> set { it.copy(dark = v) } } }
                Row("Text size") {
                    Select(s.textSize.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }, "Text size",
                        listOf(14f, 15f, 16f, 17f, 18f, 20f, 22f).map { it.toInt().toString() to it }) { v -> set { it.copy(textSize = v) } }
                }
                Row("Line numbers", "For line references like line6") { Switch(s.lineNumbers, { v -> set { it.copy(lineNumbers = v) } }) }
            }
            Group("Numbers") {
                val formats = listOf("auto" to "Device", "us" to "1,234.5", "eu" to "1.234,5", "space" to "1 234,5", "ch" to "1'234.5")
                Row("Number format") { Select(formats.first { it.first == s.numberFormat }.second, "Number format", formats.map { it.second to it.first }) { v -> set { it.copy(numberFormat = v) } } }
                val places = listOf(-1 to "Auto", 0 to "0", 2 to "2", 4 to "4", 6 to "6")
                Row("Decimal places") { Select(places.firstOrNull { it.first == s.decimals }?.second ?: "Auto", "Decimal places", places.map { it.second to it.first }) { v -> set { it.copy(decimals = v) } } }
            }
            Group("Money") {
                val source = "${rates.source.ifBlank { "Built-in rates" }}${rates.asOf?.let { " · $it" } ?: ""}"
                Row("Live exchange rates", "$source. Rate tables only, at most twice a day; nothing is sent.") {
                    Switch(s.onlineRates, { v -> set { it.copy(onlineRates = v) } })
                }
                Row("Crypto prices", "From CoinGecko, only when a sheet uses them") { Switch(s.crypto, { v -> set { it.copy(crypto = v) } }) }
            }
            Group("Everywhere") {
                val effective = app.prefs.holidayCountry(s)
                val shown = when (s.holidays) {
                    "auto" -> if (effective.isEmpty()) "Auto: none here" else "Auto: ${Holidays.country(effective)?.name ?: effective}"
                    "none" -> "None"
                    else -> Holidays.country(s.holidays)?.name ?: s.holidays
                }
                Row("Public holidays", "Workday maths skips them") {
                    Select(shown, "Public holidays", listOf("Auto (device region)" to "auto", "None" to "none") +
                        Holidays.countries.sortedBy { it.name }.map { it.name to it.code }, tall = true) { v -> set { it.copy(holidays = v) } }
                }
                if (onOpenDefinitions != null) Row("Definitions", "Variables, units and functions for every sheet") {
                    Surface(onClick = onOpenDefinitions, shape = RoundedCornerShape(10.dp), color = scheme.surfaceContainer) {
                        Text("Open", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Row("Mini calculator stays on top") { Switch(s.miniOnTop, { v -> set { it.copy(miniOnTop = v) } }) }
            }
            About()
        }
    }
}

@Composable
fun Header(title: String, onBack: () -> Unit) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = "Back", onClick = onBack).semantics { contentDescription = "Back" }, contentAlignment = Alignment.Center) {
            SymIcon(Sym.ARROW_BACK)
        }
        Text(title, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight(700)))
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp, fontWeight = FontWeight(700)),
        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 26.dp, bottom = 2.dp))
    content()
}

/** A row: label (and a line of detail) on the left, the control on the right, a hairline below. */
@Composable
private fun Row(label: String, sub: String? = null, control: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            control()
        }
        HorizontalDivider(color = scheme.surfaceContainerHighest)
    }
}

@Composable
private fun ThemePicker(current: String, onPick: (String) -> Unit) {
    val context = LocalContext.current
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((id, label) in listOf("tangerine" to "Tangerine", "wallpaper" to "Wallpaper", "cobalt" to "Cobalt", "lime" to "Lime", "berry" to "Berry")) {
            val sc = schemeFor(id, false, context)
            val on = current == id
            Box(
                Modifier.size(30.dp)
                    .border(2.dp, if (on) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                    .padding(3.dp).clip(CircleShape).background(sc.primaryContainer)
                    .clickable(onClickLabel = label) { onPick(id) }
                    .semantics { role = Role.RadioButton; selected = on; contentDescription = label },
            )
        }
    }
}

@Composable
private fun Segmented(value: String, options: List<Pair<String, String>>, label: String, onPick: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(scheme.surfaceContainer).padding(3.dp).semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for ((v, text) in options) {
            val on = v == value
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) scheme.surfaceContainerLowest else Color.Transparent)
                    .clickable(onClickLabel = text) { onPick(v) }.semantics { role = Role.RadioButton; selected = on }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) { Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight(if (on) 650 else 450))) }
        }
    }
}

@Composable
private fun <T> Select(shown: String, label: String, options: List<Pair<String, T>>, tall: Boolean = false, onPick: (T) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(onClick = { open = true }, shape = RoundedCornerShape(10.dp), color = scheme.surfaceContainer, modifier = Modifier.semantics { contentDescription = "$label: $shown" }) {
            androidx.compose.foundation.layout.Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(shown, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                SymIcon(Sym.EXPAND_MORE, size = 18.sp, tint = scheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            }
        }
        DropdownMenu(open, onDismissRequest = { open = false }, shape = RoundedCornerShape(14.dp),
            modifier = if (tall) Modifier.heightIn(max = 420.dp) else Modifier) {
            for ((text, v) in options) DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(v) })
        }
    }
}

@Composable
fun About() {
    val scheme = MaterialTheme.colorScheme
    var credits by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 28.dp)) {
        androidx.compose.foundation.layout.Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            io.github.kuscher.summa.ui.SummaMark(32.dp)
            Text("Summa ${BuildConfig.VERSION_NAME}", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight(700)))
        }
        Text(
            "A personal hobby project by Alexander Kuscher, not affiliated with or endorsed by any employer. " +
                "Developed entirely on a Googlebook. Inspired by Soulver and Numi.",
            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
        )
        var licences by remember { mutableStateOf(false) }
        androidx.compose.foundation.layout.Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(if (credits) "Hide credits" else "Credits", Modifier.clickable(onClickLabel = "Show credits") { credits = !credits }.padding(vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            Text("Open-source licences", Modifier.clickable(onClickLabel = "Show licences") { licences = true }.padding(vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge, color = scheme.primary)
        }
        if (licences) LicencesDialog { licences = false }
        if (credits) for (line in listOf(
            "Summa Sans and Summa Mono are Google Sans Flex and Google Sans Code (SIL Open Font License 1.1), subset for size.",
            "Icons: Material Symbols Rounded (Apache License 2.0).",
            "Exchange rates: European Central Bank (source cited); Frankfurter (frankfurter.dev). Crypto prices by CoinGecko.",
            "Cities: GeoNames (CC BY 4.0). Unit factors follow Unicode CLDR. Time zones: IANA tz database.",
            "Colors: Material Color Utilities (Apache License 2.0).",
            "Summa's own code is MIT-licensed.",
        )) Text("• $line", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp))
    }
}

/** Every notice and full licence text shipped in assets/licenses, in one scrollable page. */
@Composable
private fun LicencesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val parts = remember {
        listOf(
            null to "NOTICES.txt",
            "Apache License 2.0" to "Apache-2.0.txt",
            "SIL Open Font License 1.1 (Summa Sans, Summa Mono)" to "OFL-GoogleSans.txt",
            "MIT License (Summa)" to "MIT-Summa.txt",
        ).map { (title, file) ->
            title to (try { context.assets.open("licenses/$file").bufferedReader().use { it.readText() } } catch (_: Exception) { "" })
        }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(20.dp), color = scheme.surface, modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(0.94f).fillMaxHeight(0.9f)) {
            Column {
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Open-source licences", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight(700)))
                    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = "Close", onClick = onDismiss).semantics { contentDescription = "Close" }, contentAlignment = Alignment.Center) {
                        SymIcon(Sym.CLOSE, size = 20.sp, tint = scheme.onSurfaceVariant)
                    }
                }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                    for ((title, text) in parts) {
                        if (title != null) Text(title, Modifier.padding(top = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight(700)))
                        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = io.github.kuscher.summa.ui.theme.SummaFonts.mono, fontSize = 11.5.sp, lineHeight = 16.sp),
                            color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
