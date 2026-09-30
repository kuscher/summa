package io.github.kuscher.summa.ui.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import io.github.kuscher.summa.data.Library
import io.github.kuscher.summa.engine.EngineSettings
import io.github.kuscher.summa.engine.Rates
import io.github.kuscher.summa.engine.SheetEngine
import io.github.kuscher.summa.engine.SheetResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

/** A sheet's text, re-evaluated in the background while you type, and the answers to show. */
class Evaluated(val text: String, val result: SheetResult)

/**
 * One open sheet: its [state] (the editor's text), the latest [evaluated] answers, and autosave.
 * Evaluation runs off the main thread with its own engine (the engine caches parsed lines).
 */
@OptIn(FlowPreview::class)
class Session(
    val id: String,
    initial: String,
    private val library: Library,
    private val settings: StateFlow<EngineSettings>,
    private val rates: StateFlow<Rates>,
    private val scope: CoroutineScope,
) {
    val state = TextFieldState(initial)
    var evaluated by mutableStateOf<Evaluated?>(null)
        private set
    private val engine = SheetEngine(settings.value, rates.value)
    private val jobs = ArrayList<Job>()
    private var savedText = initial
    private var tick by mutableStateOf(0)

    init {
        jobs += scope.launch {
            combine(snapshotFlow { state.text.toString() }, settings, rates, snapshotFlow { tick }) { t, s, r, _ -> Triple(t, s, r) }
                .debounce(24)
                .collect { (text, s, r) ->
                    val result = withContext(Dispatchers.Default) {
                        engine.settings = s
                        engine.rates = r
                        engine.evaluate(text, ZonedDateTime.now(s.zone))
                    }
                    evaluated = Evaluated(text, result)
                    if (result.anyRateDependent) io.github.kuscher.summa.SummaApp.instance.rates.refresh()
                }
        }
        // Autosave shortly after typing stops.
        jobs += scope.launch {
            snapshotFlow { state.text.toString() }.distinctUntilChanged().debounce(600).collect { saveNow() }
        }
        // Lines that use the clock ("now", "time in Tokyo") refresh every 20 seconds.
        jobs += scope.launch {
            while (true) {
                delay(20_000)
                if (evaluated?.result?.anyTimeDependent == true) tick++
            }
        }
    }

    fun saveNow() {
        val text = state.text.toString()
        val total = evaluated?.takeIf { it.text == text }?.result?.lines?.lastOrNull { it.answer != null }?.answer
        if (text != savedText) {
            savedText = text
            scope.launch(Dispatchers.IO) { library.save(id, text, total) }
        } else if (total != null) {
            scope.launch(Dispatchers.IO) { library.setTotal(id, total) }
        }
    }

    fun close() {
        saveNow()
        jobs.forEach { it.cancel() }
    }
}
