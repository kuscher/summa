package io.github.kuscher.summa.engine

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Times a real sheet: SUMMA_PERF=path ./gradlew :engine:test --tests '*PerfProbe*' -i */
class PerfProbe {
    @Test fun probe() {
        val path = System.getenv("SUMMA_PERF")
        assumeTrue(path != null)
        val lines = File(path!!).readLines()
        val engine = SheetEngine(EngineSettings(zone = CorpusTest.ZONE), CorpusTest.RATES)
        repeat(3) { engine.evaluate(lines) }
        for (k in 1..5) {
            val edited = lines.toMutableList().also { it[1000 + k] = "Edited $k: 7 × $k" }
            val t = System.nanoTime(); engine.evaluate(edited); println("edit $k: ${(System.nanoTime() - t) / 1e6} ms")
        }
        val t = System.nanoTime(); repeat(5) { engine.evaluate(lines) }; println("cached: ${(System.nanoTime() - t) / 5e6} ms")
    }
}
