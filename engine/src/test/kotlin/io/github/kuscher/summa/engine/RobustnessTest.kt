package io.github.kuscher.summa.engine

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RobustnessTest {
    private val engine = CorpusTest.engine()

    @Test fun neverCrashesOnRandomText() {
        val rnd = Random(42)
        val pieces = listOf("$", "€", "12", "3.5", "%", "of", "in", "to", "km", "m", "(", ")", "+", "-", "*", "/", "^", "lunch",
            "is", "what", "sum", "prev", "line2", "Dec 25", "3pm", "Tokyo", "#", "//", "\"", ":", "=", "x", "half", "sqrt",
            "0x1F", "1e5", "k", "M", "until", "ago", "next friday", "at", "@", "per", "hour", "!", "²", "and", ",", "1/2", "ppi")
        repeat(3000) {
            val line = (1..rnd.nextInt(1, 9)).joinToString(" ") { pieces[rnd.nextInt(pieces.size)] }
            engine.evaluate(listOf("a = 5", line, line), CorpusTest.NOW)
        }
    }

    @Test fun thousandLinesAreFast() {
        val lines = (1..2000).map { i ->
            when (i % 5) {
                0 -> "sum"
                1 -> "Item $i: \$${i}.50 × 3"
                2 -> "distance$i = ${i} km"
                3 -> "distance${i - 1} in miles"
                else -> "20% of ${i * 3}"
            }
        }
        engine.evaluate(lines, CorpusTest.NOW) // warm-up
        val t0 = System.nanoTime()
        engine.evaluate(lines, CorpusTest.NOW)
        val warm = (System.nanoTime() - t0) / 1e6
        val edited = lines.toMutableList().also { it[500] = "Item 501: $999" }
        val t1 = System.nanoTime()
        engine.evaluate(edited, CorpusTest.NOW)
        val oneEdit = (System.nanoTime() - t1) / 1e6
        println("2000 lines: cached ${"%.1f".format(warm)} ms, after one edit ${"%.1f".format(oneEdit)} ms")
        assertTrue("too slow: $warm ms", warm < 400)
        assertTrue("too slow after an edit: $oneEdit ms", oneEdit < 400)
    }
}
