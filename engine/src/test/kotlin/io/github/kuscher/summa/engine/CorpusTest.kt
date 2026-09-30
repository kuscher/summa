package io.github.kuscher.summa.engine

import org.junit.Assert.fail
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Runs engine/src/test/resources/corpus.tsv: every line's answer must match exactly. */
class CorpusTest {
    companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Berlin")
        val NOW: ZonedDateTime = ZonedDateTime.of(2026, 9, 30, 10, 0, 0, 0, ZONE)
        val RATES = Rates(mapOf("USD" to "1.2", "GBP" to "0.8", "JPY" to "160", "CAD" to "1.5", "CHF" to "0.9", "BTC" to "0.00001")
            .mapValues { Rational.parse(it.value) }, "test", "test")

        fun engine() = SheetEngine(EngineSettings(zone = ZONE, degrees = false), RATES)

        fun cases(): List<Triple<Int, String, String>> {
            val text = CorpusTest::class.java.getResourceAsStream("/corpus.tsv")!!.bufferedReader().readText()
            return text.lines().withIndex().filter { (_, l) -> l.isNotBlank() && !l.startsWith("#") }.map { (i, l) ->
                val (input, expected) = l.split('\t', limit = 2)
                Triple(i + 1, input, expected)
            }
        }

        fun run(engine: SheetEngine, input: String): String {
            val r = engine.evaluate(input.split('⏎'), NOW)
            return r.lines.last().answer ?: "∅"
        }
    }

    @Test fun corpus() {
        val engine = engine()
        val failures = ArrayList<String>()
        val all = cases()
        for ((line, input, expected) in all) {
            val got = try { run(engine, input) } catch (e: Throwable) { "💥 ${e.javaClass.simpleName}: ${e.message}" }
            if (got != expected) failures += "  L$line  ${input.replace('⏎', '|')}\n        expected: $expected\n        got:      $got"
        }
        if (failures.isNotEmpty()) {
            fail("${failures.size}/${all.size} corpus cases failed:\n" + failures.joinToString("\n"))
        }
    }
}
