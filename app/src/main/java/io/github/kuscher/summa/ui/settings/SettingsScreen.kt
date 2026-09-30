package io.github.kuscher.summa.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kuscher.summa.BuildConfig
import io.github.kuscher.summa.SummaApp
import io.github.kuscher.summa.data.Settings
import io.github.kuscher.summa.ui.Sym
import io.github.kuscher.summa.ui.SymIcon
import io.github.kuscher.summa.ui.theme.schemeFor

@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, showHeader: Boolean = true) {
    val app = SummaApp.instance
    val s by app.prefs.state.collectAsState()
    val rates by app.rates.rates.collectAsState()
    val scheme = MaterialTheme.colorScheme
    fun set(f: (Settings) -> Settings) = app.prefs.update(f)
    Column(modifier.fillMaxSize().background(scheme.surface)) {
        if (showHeader) Header("Settings", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 40.dp).widthIn(max = 720.dp)) {
            Group("Look") {
                Text("Colors", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 10.dp))
                ThemePicker(s.theme) { t -> set { it.copy(theme = t) } }
                Spacer(Modifier.height(18.dp))
                Choice("Light or dark", s.dark, listOf("system" to "System", "light" to "Light", "dark" to "Dark")) { v -> set { it.copy(dark = v) } }
                SliderRow("Text size", s.textSize, 13f..24f, "${"%.1f".format(s.textSize)} sp") { v -> set { it.copy(textSize = (v * 2).toInt() / 2f) } }
                SliderRow("Answer column", s.answerWidth, 160f..360f, "${s.answerWidth.toInt()} dp") { v -> set { it.copy(answerWidth = v) } }
                SwitchRow("Line numbers", "Handy for line references like line6", s.lineNumbers) { v -> set { it.copy(lineNumbers = v) } }
                SwitchRow("Monospaced sheet", "Google Sans Code instead of Google Sans Flex", s.mono) { v -> set { it.copy(mono = v) } }
                SwitchRow("Slashed zero", "Tell 0 from O at a glance", s.slashedZero) { v -> set { it.copy(slashedZero = v) } }
            }
            Group("Numbers") {
                Choice("Decimal places", s.decimals.toString(), listOf("-1" to "Auto", "0" to "0", "2" to "2", "4" to "4", "6" to "6")) { v -> set { it.copy(decimals = v.toInt()) } }
                Choice("Number format", s.numberFormat, listOf("auto" to "Device", "us" to "1,234.5", "eu" to "1.234,5", "space" to "1 234,5", "ch" to "1'234.5")) { v -> set { it.copy(numberFormat = v) } }
                SwitchRow("Thousands separators", "1,000,000 instead of 1000000", s.thousands) { v -> set { it.copy(thousands = v) } }
                Choice("Trigonometry", if (s.degrees) "deg" else "rad", listOf("rad" to "Radians", "deg" to "Degrees")) { v -> set { it.copy(degrees = v == "deg") } }
                Choice("\$ means", s.dollar, listOf("auto" to "Region", "USD" to "US\$", "CAD" to "CA\$", "AUD" to "A\$", "NZD" to "NZ\$")) { v -> set { it.copy(dollar = v) } }
            }
            Group("Money") {
                SwitchRow("Live exchange rates", "Downloads rate tables from Frankfurter and the European Central Bank, at most twice a day. Nothing else is ever sent.", s.onlineRates) { v -> set { it.copy(onlineRates = v) } }
                SwitchRow("Crypto prices", "Bitcoin, Ether and friends from CoinGecko, only when a sheet uses them", s.crypto) { v -> set { it.copy(crypto = v) } }
                Text("Rates: ${rates.source.ifBlank { "built in" }}${rates.asOf?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                app.rates.status.collectAsState().value?.let { st ->
                    Text(st, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                Surface(onClick = { app.rates.refresh(force = true) }, shape = CircleShape, color = scheme.secondaryContainer, modifier = Modifier.padding(top = 10.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        SymIcon(Sym.REFRESH, size = 18.sp, tint = scheme.onSecondaryContainer)
                        Text("Update rates now", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge, color = scheme.onSecondaryContainer)
                    }
                }
            }
            Group("Mini calculator") {
                SwitchRow("Keep the mini calculator on top", "On Googlebooks the mini window stays above other windows, parked bottom-right. Open it from the Quick Settings tile, the launcher, or Ctrl+Shift+M.", s.miniOnTop) { v -> set { it.copy(miniOnTop = v) } }
                Surface(onClick = { io.github.kuscher.summa.ui.MainActivity.openMini(app) }, shape = CircleShape, color = scheme.secondaryContainer, modifier = Modifier.padding(top = 6.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        SymIcon(Sym.PICTURE_IN_PICTURE_ALT, size = 18.sp, tint = scheme.onSecondaryContainer)
                        Text("Open the mini calculator", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge, color = scheme.onSecondaryContainer)
                    }
                }
            }
            Group("About") { About() }
        }
    }
}

@Composable
fun Header(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack).semantics { contentDescription = "Back" }, contentAlignment = Alignment.Center) {
            SymIcon(Sym.ARROW_BACK)
        }
        Text(title, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight(760)))
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(title.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.9.sp, fontWeight = FontWeight(700)),
        color = scheme.primary, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(scheme.surfaceContainerLow).padding(18.dp)) { content() }
}

@Composable
private fun ThemePicker(current: String, onPick: (String) -> Unit) {
    val context = LocalContext.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((id, label) in listOf("tangerine" to "Tangerine", "wallpaper" to "Wallpaper", "cobalt" to "Cobalt", "lime" to "Lime", "berry" to "Berry")) {
            val sc = schemeFor(id, false, context)
            val on = current == id
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onPick(id) }.semantics { role = Role.RadioButton; contentDescription = label }) {
                Box(
                    Modifier.size(52.dp)
                        .border(if (on) 3.dp else 0.dp, if (on) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(if (on) 16.dp else 26.dp))
                        .padding(5.dp).clip(RoundedCornerShape(if (on) 12.dp else 22.dp)).background(sc.primaryContainer),
                    contentAlignment = Alignment.BottomEnd,
                ) { Box(Modifier.size(20.dp).clip(RoundedCornerShape(topStart = 12.dp)).background(sc.tertiary)) }
                Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Choice(label: String, value: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, (v, text) ->
                ToggleButton(
                    checked = value == v, onCheckedChange = { onPick(v) },
                    shapes = when (i) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                ) { Text(text) }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, shown: String, onChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row { Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge); Text(shown, style = MaterialTheme.typography.labelLarge) }
        Slider(value, onChange, valueRange = range)
    }
}

@Composable
fun About() {
    val scheme = MaterialTheme.colorScheme
    Text("Summa ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleLarge)
    Text(
        "A notepad calculator for Googlebooks and Android. A personal hobby project by Alexander Kuscher, " +
            "not affiliated with or endorsed by any employer. Developed entirely on a Googlebook.",
        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
    )
    Text("Inspired by Soulver and Numi, two lovely Mac apps.", style = MaterialTheme.typography.bodyMedium,
        color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    Text("Credits", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    for (line in listOf(
        "Summa Sans and Summa Mono are Google Sans Flex and Google Sans Code (SIL Open Font License 1.1), subset for size.",
        "Icons: Material Symbols Rounded (Apache License 2.0).",
        "Exchange rates: European Central Bank (source cited); Frankfurter (frankfurter.dev). Crypto prices by CoinGecko.",
        "Cities: GeoNames (CC BY 4.0). Unit factors follow Unicode CLDR. Time zones: IANA tz database.",
        "Colors: Google's Material Color Utilities (Apache License 2.0).",
        "Summa's own code is MIT-licensed.",
    )) Text("• $line", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp))
}
