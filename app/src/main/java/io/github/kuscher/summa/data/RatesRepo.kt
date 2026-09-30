package io.github.kuscher.summa.data

import android.content.Context
import io.github.kuscher.summa.engine.Rates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Exchange rates for the engine. Starts from the ECB snapshot bundled in the APK; the online
 * refresh (Frankfurter, ECB fallback, optional crypto) arrives in v0.2.
 */
class RatesRepo(context: Context, private val prefs: Prefs, private val scope: CoroutineScope) {
    private val _rates = MutableStateFlow(Rates.bundled)
    val rates: StateFlow<Rates> = _rates.asStateFlow()
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    fun refresh(force: Boolean = false) {}

    @Suppress("unused") private val appContext = context.applicationContext
}
