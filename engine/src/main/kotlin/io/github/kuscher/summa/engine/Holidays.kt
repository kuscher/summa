package io.github.kuscher.summa.engine

import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Nationwide public holidays, computed from rules (no data files, no network), so workday maths
 * can skip them. Regional holidays (states, cantons, provinces) are left out; so are one-off
 * days. Where a country moves a holiday that falls on a weekend, [Observe] says how.
 */
object Holidays {
    enum class Observe {
        NONE,
        /** United States: Saturday → Friday, Sunday → Monday. */
        US,
        /** UK, Ireland, Canada, Australia, New Zealand: the next weekday that isn't already a holiday. */
        NEXT_WEEKDAY,
    }

    class Holiday(val name: String, val observe: Observe = Observe.NONE, val date: (Int) -> LocalDate?)

    class Country(val code: String, val name: String, val holidays: List<Holiday>)

    private fun fixed(name: String, m: Int, d: Int, observe: Observe = Observe.NONE) = Holiday(name, observe) { y -> LocalDate.of(y, m, d) }
    private fun easter(name: String, offset: Long) = Holiday(name) { y -> easterSunday(y).plusDays(offset) }
    /** The [n]th [day] of month [m]; n = -1 is the last one. */
    private fun nth(name: String, m: Int, n: Int, day: DayOfWeek) = Holiday(name) { y ->
        val first = LocalDate.of(y, m, 1)
        if (n > 0) first.with(TemporalAdjusters.dayOfWeekInMonth(n, day)) else first.with(TemporalAdjusters.lastInMonth(day))
    }
    /** The [day] on or after [m]/[d] (e.g. Midsummer Eve: the Friday from June 19). */
    private fun onOrAfter(name: String, m: Int, d: Int, day: DayOfWeek) = Holiday(name) { y -> LocalDate.of(y, m, d).with(TemporalAdjusters.nextOrSame(day)) }

    private val US = Observe.US
    private val NEXT = Observe.NEXT_WEEKDAY

    val countries: List<Country> = listOf(
        Country("US", "United States", listOf(
            fixed("New Year's Day", 1, 1, US), nth("Martin Luther King Jr. Day", 1, 3, MONDAY), nth("Presidents' Day", 2, 3, MONDAY),
            nth("Memorial Day", 5, -1, MONDAY), fixed("Juneteenth", 6, 19, US), fixed("Independence Day", 7, 4, US),
            nth("Labor Day", 9, 1, MONDAY), nth("Columbus Day", 10, 2, MONDAY), fixed("Veterans Day", 11, 11, US),
            nth("Thanksgiving", 11, 4, THURSDAY), fixed("Christmas Day", 12, 25, US),
        )),
        Country("CA", "Canada", listOf(
            fixed("New Year's Day", 1, 1, NEXT), easter("Good Friday", -2),
            Holiday("Victoria Day") { y -> LocalDate.of(y, 5, 24).with(TemporalAdjusters.previousOrSame(MONDAY)) },
            fixed("Canada Day", 7, 1, NEXT), nth("Labour Day", 9, 1, MONDAY), nth("Thanksgiving", 10, 2, MONDAY),
            fixed("Christmas Day", 12, 25, NEXT), fixed("Boxing Day", 12, 26, NEXT),
        )),
        Country("MX", "Mexico", listOf(
            fixed("Año Nuevo", 1, 1), nth("Día de la Constitución", 2, 1, MONDAY), nth("Natalicio de Benito Juárez", 3, 3, MONDAY),
            fixed("Día del Trabajo", 5, 1), fixed("Día de la Independencia", 9, 16), nth("Día de la Revolución", 11, 3, MONDAY),
            fixed("Navidad", 12, 25),
        )),
        Country("BR", "Brazil", listOf(
            fixed("Confraternização Universal", 1, 1), easter("Carnaval", -47), easter("Sexta-feira Santa", -2), fixed("Tiradentes", 4, 21),
            fixed("Dia do Trabalho", 5, 1), easter("Corpus Christi", 60), fixed("Independência", 9, 7), fixed("Nossa Senhora Aparecida", 10, 12),
            fixed("Finados", 11, 2), fixed("Proclamação da República", 11, 15), fixed("Consciência Negra", 11, 20), fixed("Natal", 12, 25),
        )),
        Country("GB", "United Kingdom (England and Wales)", listOf(
            fixed("New Year's Day", 1, 1, NEXT), easter("Good Friday", -2), easter("Easter Monday", 1),
            nth("Early May bank holiday", 5, 1, MONDAY), nth("Spring bank holiday", 5, -1, MONDAY), nth("Summer bank holiday", 8, -1, MONDAY),
            fixed("Christmas Day", 12, 25, NEXT), fixed("Boxing Day", 12, 26, NEXT),
        )),
        Country("IE", "Ireland", listOf(
            fixed("New Year's Day", 1, 1, NEXT),
            Holiday("St Brigid's Day") { y -> LocalDate.of(y, 2, 1).let { if (it.dayOfWeek == FRIDAY) it else it.with(TemporalAdjusters.nextOrSame(MONDAY)) } },
            fixed("St Patrick's Day", 3, 17, NEXT), easter("Easter Monday", 1), nth("May bank holiday", 5, 1, MONDAY),
            nth("June bank holiday", 6, 1, MONDAY), nth("August bank holiday", 8, 1, MONDAY), nth("October bank holiday", 10, -1, MONDAY),
            fixed("Christmas Day", 12, 25, NEXT), fixed("St Stephen's Day", 12, 26, NEXT),
        )),
        Country("DE", "Germany", listOf(
            fixed("Neujahr", 1, 1), easter("Karfreitag", -2), easter("Ostermontag", 1), fixed("Tag der Arbeit", 5, 1),
            easter("Christi Himmelfahrt", 39), easter("Pfingstmontag", 50), fixed("Tag der Deutschen Einheit", 10, 3),
            fixed("1. Weihnachtstag", 12, 25), fixed("2. Weihnachtstag", 12, 26),
        )),
        Country("AT", "Austria", listOf(
            fixed("Neujahr", 1, 1), fixed("Heilige Drei Könige", 1, 6), easter("Ostermontag", 1), fixed("Staatsfeiertag", 5, 1),
            easter("Christi Himmelfahrt", 39), easter("Pfingstmontag", 50), easter("Fronleichnam", 60), fixed("Mariä Himmelfahrt", 8, 15),
            fixed("Nationalfeiertag", 10, 26), fixed("Allerheiligen", 11, 1), fixed("Mariä Empfängnis", 12, 8),
            fixed("Christtag", 12, 25), fixed("Stefanitag", 12, 26),
        )),
        Country("CH", "Switzerland", listOf(
            fixed("Neujahr", 1, 1), easter("Karfreitag", -2), easter("Ostermontag", 1), easter("Auffahrt", 39), easter("Pfingstmontag", 50),
            fixed("Bundesfeier", 8, 1), fixed("Weihnachten", 12, 25), fixed("Stephanstag", 12, 26),
        )),
        Country("FR", "France", listOf(
            fixed("Jour de l'an", 1, 1), easter("Lundi de Pâques", 1), fixed("Fête du Travail", 5, 1), fixed("Victoire 1945", 5, 8),
            easter("Ascension", 39), easter("Lundi de Pentecôte", 50), fixed("Fête nationale", 7, 14), fixed("Assomption", 8, 15),
            fixed("Toussaint", 11, 1), fixed("Armistice", 11, 11), fixed("Noël", 12, 25),
        )),
        Country("NL", "Netherlands", listOf(
            fixed("Nieuwjaarsdag", 1, 1), easter("Tweede Paasdag", 1),
            Holiday("Koningsdag") { y -> LocalDate.of(y, 4, 27).let { if (it.dayOfWeek == DayOfWeek.SUNDAY) it.minusDays(1) else it } },
            Holiday("Bevrijdingsdag") { y -> if (y % 5 == 0) LocalDate.of(y, 5, 5) else null },
            easter("Hemelvaartsdag", 39), easter("Tweede Pinksterdag", 50), fixed("Eerste Kerstdag", 12, 25), fixed("Tweede Kerstdag", 12, 26),
        )),
        Country("BE", "Belgium", listOf(
            fixed("Nieuwjaar", 1, 1), easter("Paasmaandag", 1), fixed("Dag van de Arbeid", 5, 1), easter("O.L.H. Hemelvaart", 39),
            easter("Pinkstermaandag", 50), fixed("Nationale feestdag", 7, 21), fixed("O.L.V. Hemelvaart", 8, 15), fixed("Allerheiligen", 11, 1),
            fixed("Wapenstilstand", 11, 11), fixed("Kerstmis", 12, 25),
        )),
        Country("IT", "Italy", listOf(
            fixed("Capodanno", 1, 1), fixed("Epifania", 1, 6), easter("Lunedì dell'Angelo", 1), fixed("Festa della Liberazione", 4, 25),
            fixed("Festa del Lavoro", 5, 1), fixed("Festa della Repubblica", 6, 2), fixed("Ferragosto", 8, 15), fixed("Ognissanti", 11, 1),
            fixed("Immacolata Concezione", 12, 8), fixed("Natale", 12, 25), fixed("Santo Stefano", 12, 26),
        )),
        Country("ES", "Spain", listOf(
            fixed("Año Nuevo", 1, 1), fixed("Epifanía del Señor", 1, 6), easter("Viernes Santo", -2), fixed("Fiesta del Trabajo", 5, 1),
            fixed("Asunción de la Virgen", 8, 15), fixed("Fiesta Nacional de España", 10, 12), fixed("Todos los Santos", 11, 1),
            fixed("Día de la Constitución", 12, 6), fixed("Inmaculada Concepción", 12, 8), fixed("Navidad", 12, 25),
        )),
        Country("PT", "Portugal", listOf(
            fixed("Ano Novo", 1, 1), easter("Sexta-feira Santa", -2), fixed("Dia da Liberdade", 4, 25), fixed("Dia do Trabalhador", 5, 1),
            easter("Corpo de Deus", 60), fixed("Dia de Portugal", 6, 10), fixed("Assunção de Nossa Senhora", 8, 15),
            fixed("Implantação da República", 10, 5), fixed("Todos os Santos", 11, 1), fixed("Restauração da Independência", 12, 1),
            fixed("Imaculada Conceição", 12, 8), fixed("Natal", 12, 25),
        )),
        Country("SE", "Sweden", listOf(
            fixed("Nyårsdagen", 1, 1), fixed("Trettondedag jul", 1, 6), easter("Långfredagen", -2), easter("Annandag påsk", 1),
            fixed("Första maj", 5, 1), easter("Kristi himmelsfärdsdag", 39), fixed("Sveriges nationaldag", 6, 6),
            onOrAfter("Midsommarafton", 6, 19, FRIDAY), fixed("Julafton", 12, 24), fixed("Juldagen", 12, 25), fixed("Annandag jul", 12, 26),
            fixed("Nyårsafton", 12, 31),
        )),
        Country("DK", "Denmark", listOf(
            fixed("Nytårsdag", 1, 1), easter("Skærtorsdag", -3), easter("Langfredag", -2), easter("2. påskedag", 1),
            easter("Kristi himmelfartsdag", 39), easter("2. pinsedag", 50), fixed("Grundlovsdag", 6, 5), fixed("Juleaftensdag", 12, 24),
            fixed("Juledag", 12, 25), fixed("2. juledag", 12, 26), fixed("Nytårsaftensdag", 12, 31),
        )),
        Country("NO", "Norway", listOf(
            fixed("Første nyttårsdag", 1, 1), easter("Skjærtorsdag", -3), easter("Langfredag", -2), easter("Andre påskedag", 1),
            fixed("Arbeidernes dag", 5, 1), fixed("Grunnlovsdag", 5, 17), easter("Kristi himmelfartsdag", 39), easter("Andre pinsedag", 50),
            fixed("Første juledag", 12, 25), fixed("Andre juledag", 12, 26),
        )),
        Country("FI", "Finland", listOf(
            fixed("Uudenvuodenpäivä", 1, 1), fixed("Loppiainen", 1, 6), easter("Pitkäperjantai", -2), easter("Toinen pääsiäispäivä", 1),
            fixed("Vappu", 5, 1), easter("Helatorstai", 39), onOrAfter("Juhannusaatto", 6, 19, FRIDAY), fixed("Itsenäisyyspäivä", 12, 6),
            fixed("Jouluaatto", 12, 24), fixed("Joulupäivä", 12, 25), fixed("Tapaninpäivä", 12, 26),
        )),
        Country("PL", "Poland", listOf(
            fixed("Nowy Rok", 1, 1), fixed("Trzech Króli", 1, 6), easter("Poniedziałek Wielkanocny", 1), fixed("Święto Pracy", 5, 1),
            fixed("Święto Konstytucji 3 Maja", 5, 3), easter("Boże Ciało", 60), fixed("Wniebowzięcie NMP", 8, 15),
            fixed("Wszystkich Świętych", 11, 1), fixed("Święto Niepodległości", 11, 11),
            Holiday("Wigilia") { y -> if (y >= 2025) LocalDate.of(y, 12, 24) else null },
            fixed("Boże Narodzenie", 12, 25), fixed("Drugi dzień świąt", 12, 26),
        )),
        Country("AU", "Australia", listOf(
            fixed("New Year's Day", 1, 1, NEXT), fixed("Australia Day", 1, 26, NEXT), easter("Good Friday", -2), easter("Easter Monday", 1),
            fixed("Anzac Day", 4, 25), nth("King's Birthday", 6, 2, MONDAY), fixed("Christmas Day", 12, 25, NEXT), fixed("Boxing Day", 12, 26, NEXT),
        )),
        Country("NZ", "New Zealand", listOf(
            fixed("New Year's Day", 1, 1, NEXT), fixed("Day after New Year's Day", 1, 2, NEXT), fixed("Waitangi Day", 2, 6, NEXT),
            easter("Good Friday", -2), easter("Easter Monday", 1), fixed("Anzac Day", 4, 25, NEXT), nth("King's Birthday", 6, 1, MONDAY),
            Holiday("Matariki") { y -> MATARIKI[y] },
            nth("Labour Day", 10, 4, MONDAY), fixed("Christmas Day", 12, 25, NEXT), fixed("Boxing Day", 12, 26, NEXT),
        )),
    )

    /** Matariki follows the lunar calendar; dates set in law (Te Kāhui o Matariki Public Holiday Act 2022). */
    private val MATARIKI = mapOf(
        2022 to LocalDate.of(2022, 6, 24), 2023 to LocalDate.of(2023, 7, 14), 2024 to LocalDate.of(2024, 6, 28),
        2025 to LocalDate.of(2025, 6, 20), 2026 to LocalDate.of(2026, 7, 10), 2027 to LocalDate.of(2027, 6, 25),
        2028 to LocalDate.of(2028, 7, 14), 2029 to LocalDate.of(2029, 7, 6), 2030 to LocalDate.of(2030, 6, 21),
        2031 to LocalDate.of(2031, 7, 11), 2032 to LocalDate.of(2032, 7, 2), 2033 to LocalDate.of(2033, 6, 24),
        2034 to LocalDate.of(2034, 7, 7), 2035 to LocalDate.of(2035, 6, 29),
    )

    private val byCode = countries.associateBy { it.code }
    fun country(code: String): Country? = byCode[code.uppercase()]

    private val cache = object : LinkedHashMap<String, Map<LocalDate, String>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Map<LocalDate, String>>?) = size > 64
    }

    /** The days off in [year] (after moving weekend holidays where the country does), with names. */
    fun days(code: String, year: Int): Map<LocalDate, String> {
        val c = country(code) ?: return emptyMap()
        val key = "${c.code}$year"
        synchronized(cache) { cache[key]?.let { return it } }
        val out = sortedMapOf<LocalDate, String>()
        val moved = ArrayList<Pair<LocalDate, String>>()
        for (h in c.holidays) {
            val d = h.date(year) ?: continue
            val weekend = d.dayOfWeek.value >= 6
            when {
                !weekend || h.observe == Observe.NONE -> out.putIfAbsent(d, h.name)
                h.observe == Observe.US -> {
                    val o = if (d.dayOfWeek == DayOfWeek.SATURDAY) d.minusDays(1) else d.plusDays(1)
                    out.putIfAbsent(d, h.name)
                    out.putIfAbsent(o, "${h.name} (observed)")
                }
                else -> { out.putIfAbsent(d, h.name); moved += d to h.name }
            }
        }
        // Substitute days go to the next weekday that isn't already a day off (Christmas on a
        // Saturday → Monday, Boxing Day → Tuesday).
        for ((d, name) in moved.sortedBy { it.first }) {
            var s = d.plusDays(1)
            while (s.dayOfWeek.value >= 6 || out.containsKey(s)) s = s.plusDays(1)
            out[s] = "$name (substitute day)"
        }
        synchronized(cache) { cache[key] = out }
        return out
    }

    fun isHoliday(code: String, d: LocalDate): Boolean = code.isNotEmpty() && days(code, d.year).containsKey(d)

    fun isWorkday(code: String, d: LocalDate): Boolean = d.dayOfWeek.value <= 5 && !isHoliday(code, d)

    /** The next day off on or after [from] (weekdays only: a holiday on a Sunday doesn't count). */
    fun next(code: String, from: LocalDate): Pair<LocalDate, String>? {
        if (country(code) == null) return null
        for (y in from.year..from.year + 1) {
            days(code, y).entries.firstOrNull { !it.key.isBefore(from) && it.key.dayOfWeek.value <= 5 }?.let { return it.key to it.value }
        }
        return null
    }

    /** Easter Sunday (Gregorian computus, "Anonymous" algorithm). */
    fun easterSunday(y: Int): LocalDate {
        val a = y % 19; val b = y / 100; val c = y % 100; val d = b / 4; val e = b % 4
        val f = (b + 8) / 25; val g = (b - f + 1) / 3; val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4; val k = c % 4; val l = (32 + 2 * e + 2 * i - h - k) % 7; val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31; val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(y, month, day)
    }
}
