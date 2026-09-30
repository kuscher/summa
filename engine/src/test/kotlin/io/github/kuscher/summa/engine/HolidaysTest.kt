package io.github.kuscher.summa.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HolidaysTest {
    private fun answer(country: String, input: String): String? {
        val e = SheetEngine(EngineSettings(zone = CorpusTest.ZONE, holidays = country), CorpusTest.RATES)
        return e.evaluate(input.split('⏎'), CorpusTest.NOW).lines.last().answer
    }

    @Test fun usObservedDays() {
        val d2026 = Holidays.days("US", 2026)
        assertTrue(LocalDate.of(2026, 7, 3) in d2026)             // July 4 is a Saturday
        assertEquals("Thanksgiving", d2026[LocalDate.of(2026, 11, 26)])
        assertTrue(LocalDate.of(2027, 7, 5) in Holidays.days("US", 2027))   // July 4 is a Sunday
    }

    @Test fun ukSubstituteDays() {
        val d = Holidays.days("GB", 2027)   // Christmas on a Saturday, Boxing Day on a Sunday
        assertTrue(LocalDate.of(2027, 12, 27) in d)
        assertTrue(LocalDate.of(2027, 12, 28) in d)
        assertEquals(LocalDate.of(2026, 4, 6), Holidays.days("GB", 2026).entries.first { it.value == "Easter Monday" }.key)
    }

    @Test fun everyCountryHasHolidays() {
        for (c in Holidays.countries) for (y in 2024..2035) assertTrue("${c.code} $y", Holidays.days(c.code, y).size >= 7)
    }

    @Test fun workdaysSkipHolidays() {
        assertEquals("4 workdays", answer("US", "workdays between Nov 23 and Nov 27"))
        assertEquals("5 workdays", answer("", "workdays between Nov 23 and Nov 27"))
        assertEquals("8 workdays", answer("DE", "workdays between Dec 21 and Dec 31"))
        assertEquals("Wed, Dec 30, 2026", answer("US", "3 workdays after Dec 24"))
        assertEquals("Tue, Dec 29, 2026", answer("", "3 workdays after Dec 24"))
    }

    @Test fun nextHoliday() {
        assertEquals("Mon, Oct 12, 2026", answer("US", "next holiday"))
        assertEquals("Fri, Dec 25, 2026", answer("DE", "next holiday"))   // Oct 3 is a Saturday
        assertEquals(null, answer("", "next holiday"))
    }
}
