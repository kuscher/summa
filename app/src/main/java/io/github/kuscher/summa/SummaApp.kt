package io.github.kuscher.summa

import android.app.Application
import io.github.kuscher.summa.data.Library
import io.github.kuscher.summa.data.Prefs
import io.github.kuscher.summa.data.RatesRepo
import io.github.kuscher.summa.engine.Definitions
import io.github.kuscher.summa.engine.SheetEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SummaApp : Application() {
    lateinit var library: Library; private set
    lateinit var prefs: Prefs; private set
    lateinit var rates: RatesRepo; private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** Main-thread scope for the shared sheet sessions. */
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The engine's settings, following Settings and the device locale. */
    val engineSettings: kotlinx.coroutines.flow.StateFlow<io.github.kuscher.summa.engine.EngineSettings> by lazy {
        prefs.state.map { prefs.engineSettings(it) }.stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, prefs.engineSettings())
    }

    /** The open sheets, shared by all windows (see [io.github.kuscher.summa.ui.editor.Sessions]). */
    val sessions by lazy { io.github.kuscher.summa.ui.editor.Sessions(library, engineSettings, rates.rates, uiScope) }

    /** What the definitions sheet declares; every other sheet sees it. Its own session keeps it live while you type. */
    val definitions = MutableStateFlow(Definitions.EMPTY)

    /** Evaluates the saved definitions sheet (at start, and when settings or rates change). */
    fun loadDefinitions() {
        if (library.get(Library.DEFINITIONS) == null) { definitions.value = Definitions.EMPTY; return }
        val text = library.text(Library.DEFINITIONS)
        definitions.value = SheetEngine(prefs.engineSettings(), rates.rates.value).evaluate(text).definitions
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        library = Library(this)
        prefs = Prefs(this)
        rates = RatesRepo(this, prefs, scope)
        // Rate switches in Settings take effect right away; otherwise refresh at most twice a day.
        scope.launch {
            prefs.state.map { it.onlineRates to it.crypto }.distinctUntilChanged().collect { rates.onSettingsChanged() }
        }
        scope.launch { loadDefinitions() }
        scope.launch { library.purgeTrash(7L * 24 * 3600 * 1000) }
        if (!prefs.value.firstRunDone) {
            if (library.state.value.sheets.isEmpty()) {
                library.create(Samples.MUNICH)
                library.create(Samples.WELCOME).also { prefs.putString("lastSheet", it.id) }
            }
            prefs.update { it.copy(firstRunDone = true) }
        }
    }

    companion object {
        lateinit var instance: SummaApp; private set
    }
}

object Samples {
    val WELCOME = """
        # Welcome to Summa
        Type maths the way you'd say it. Answers appear on the right.

        Coffee: 2 × $4.50
        Lunch: $12 + 15% tip
        sum

        # Units and money
        5 km in miles
        72 °F in °C
        €49 in USD
        1,200 sq ft in m²

        # Dates and time
        days until Dec 25
        3 pm Munich in Tokyo
        today + 3 weeks

        # Money over time
        $10,000 at 5% for 10 years
        loan of $300k at 6% for 30 years

        # Your own names
        rate = $85/hour
        work = 6.5 hours
        rate × work
        + 20% tax
        // Names on the Definitions sheet (Settings › Definitions) work in every sheet.

        // Lines that start with // are notes. Click an answer to copy it,
        // right-click it for more. Grey text after the cursor is a
        // suggestion: Tab takes it.
    """.trimIndent()

    val MUNICH = """
        # Munich weekend
        Flights: 2 × €149
        Hotel: 3 nights × $165 in EUR
        MVV day tickets: 3 × €9.70
        Pretzels and beer: 4 × €12.40
        sum
        Split 3 ways: line6 / 3

        # Day trip to Salzburg
        distance = 145 km
        consumption = 6.4 L/100 km
        fuel = distance × consumption
        fuel × €1.74/L
        distance in miles
        Leave 8:30 am + 1 h 50 min
    """.trimIndent()
}
