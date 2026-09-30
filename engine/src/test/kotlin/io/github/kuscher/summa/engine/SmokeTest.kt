package io.github.kuscher.summa.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SmokeTest {
    private val now = ZonedDateTime.of(2026, 9, 30, 10, 0, 0, 0, ZoneId.of("Europe/Berlin"))
    private val engine = SheetEngine(EngineSettings(zone = ZoneId.of("Europe/Berlin")))

    private fun answers(text: String) = engine.evaluate(text, now).lines.map { it.answer }

    @Test fun basics() {
        assertEquals(listOf("4"), answers("2 + 2"))
        assertEquals(listOf("0.3"), answers("0.1 + 0.2"))
    }
}
