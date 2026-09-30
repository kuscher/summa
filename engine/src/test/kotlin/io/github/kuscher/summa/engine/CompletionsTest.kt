package io.github.kuscher.summa.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletionsTest {
    @Test fun unitsAfterNumbers() {
        val s = Completions.suggest("k", afterNumber = true).map { it.insert }
        assertEquals("km", s.first())
        assertTrue(s.toString(), "kg" in s)
    }

    @Test fun variablesFirst() {
        val s = Completions.suggest("dis", vars = listOf("distance", "discount"))
        assertEquals(listOf("discount", "distance"), s.take(2).map { it.insert }.sorted())
    }

    @Test fun wordsAndFunctions() {
        assertTrue(Completions.suggest("kilom").any { it.insert.startsWith("kilomet") })
        assertEquals("sqrt", Completions.suggest("sq").first().insert)
        assertTrue(Completions.suggest("eu").any { it.insert == "euros" || it.insert == "EUR" })
    }

    @Test fun placesOffline() {
        val e = SheetEngine(EngineSettings(zone = java.time.ZoneId.of("Europe/Berlin")), CorpusTest.RATES)
        val r = e.evaluate(listOf("3 pm Munich in San Francisco", "time in Japan", "9am Bangalore in NYC"), CorpusTest.NOW)
        assertEquals("6:00 am", r.lines[0].answer)
        assertEquals("5:00 pm", r.lines[1].answer)
        assertEquals("Yesterday 11:30 pm", r.lines[2].answer)
    }
}
