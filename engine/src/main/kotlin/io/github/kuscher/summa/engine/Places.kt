package io.github.kuscher.summa.engine

import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.ZoneId
import java.util.zip.GZIPInputStream

/**
 * Places for time-zone maths, all offline:
 *  - every IANA zone's city ("Europe/Lisbon" → Lisbon),
 *  - GeoNames cities of 100k+ people (CC BY 4.0, bundled as places.tsv.gz: name, country, zone, population),
 *  - countries (by their capital's zone), US states, common abbreviations (PST, CET, IST…), and aliases (NYC, SF).
 * When a name is ambiguous the bigger city wins.
 */
object Places {
    /** Case-insensitive names (cities, countries, states). */
    val trie: PhraseTrie<Place> get() = tries.first
    /** Case-sensitive names: abbreviations like PST, CET, and short aliases (LA, SF, NYC). */
    val exact: PhraseTrie<Place> get() = tries.second
    private val tries: Pair<PhraseTrie<Place>, PhraseTrie<Place>> by lazy { build() }

    /** zone abbreviations → zone; ambiguous ones use the common reading (IST = India, CST = US Central). */
    val ABBREVIATIONS = mapOf(
        "UTC" to "UTC", "GMT" to "UTC", "Z" to "UTC", "Zulu" to "UTC",
        "PST" to "America/Los_Angeles", "PDT" to "America/Los_Angeles", "PT" to "America/Los_Angeles", "Pacific" to "America/Los_Angeles",
        "MST" to "America/Denver", "MDT" to "America/Denver", "MT" to "America/Denver", "Mountain" to "America/Denver",
        "CST" to "America/Chicago", "CDT" to "America/Chicago", "CT" to "America/Chicago", "Central" to "America/Chicago",
        "EST" to "America/New_York", "EDT" to "America/New_York", "ET" to "America/New_York", "Eastern" to "America/New_York",
        "AKST" to "America/Anchorage", "AKDT" to "America/Anchorage", "HST" to "Pacific/Honolulu",
        "AST" to "America/Halifax", "ADT" to "America/Halifax", "NST" to "America/St_Johns",
        "WET" to "Europe/Lisbon", "WEST" to "Europe/Lisbon", "BST" to "Europe/London",
        "CET" to "Europe/Paris", "CEST" to "Europe/Paris", "MEZ" to "Europe/Berlin", "MESZ" to "Europe/Berlin",
        "EET" to "Europe/Athens", "EEST" to "Europe/Athens", "MSK" to "Europe/Moscow",
        "IST" to "Asia/Kolkata", "PKT" to "Asia/Karachi", "ICT" to "Asia/Bangkok", "WIB" to "Asia/Jakarta",
        "SGT" to "Asia/Singapore", "HKT" to "Asia/Hong_Kong", "PHT" to "Asia/Manila", "CCT" to "Asia/Shanghai",
        "JST" to "Asia/Tokyo", "KST" to "Asia/Seoul", "GST" to "Asia/Dubai",
        "AWST" to "Australia/Perth", "ACST" to "Australia/Adelaide", "ACDT" to "Australia/Adelaide",
        "AEST" to "Australia/Sydney", "AEDT" to "Australia/Sydney", "NZST" to "Pacific/Auckland", "NZDT" to "Pacific/Auckland",
        "SAST" to "Africa/Johannesburg", "CAT" to "Africa/Maputo", "EAT" to "Africa/Nairobi", "WAT" to "Africa/Lagos",
        "BRT" to "America/Sao_Paulo", "ART" to "America/Argentina/Buenos_Aires",
    )

    val ALIASES = mapOf(
        "nyc" to "America/New_York", "new york city" to "America/New_York", "la" to "America/Los_Angeles",
        "sf" to "America/Los_Angeles", "san francisco" to "America/Los_Angeles", "silicon valley" to "America/Los_Angeles",
        "bay area" to "America/Los_Angeles", "dc" to "America/New_York", "washington dc" to "America/New_York",
        "bombay" to "Asia/Kolkata", "calcutta" to "Asia/Kolkata", "bangalore" to "Asia/Kolkata", "bengaluru" to "Asia/Kolkata",
        "peking" to "Asia/Shanghai", "beijing" to "Asia/Shanghai", "saigon" to "Asia/Ho_Chi_Minh", "kiev" to "Europe/Kyiv",
        "munich" to "Europe/Berlin", "münchen" to "Europe/Berlin", "cologne" to "Europe/Berlin", "köln" to "Europe/Berlin",
        "frankfurt" to "Europe/Berlin", "hamburg" to "Europe/Berlin", "zurich" to "Europe/Zurich", "zürich" to "Europe/Zurich",
        "geneva" to "Europe/Zurich", "milan" to "Europe/Rome", "florence" to "Europe/Rome", "venice" to "Europe/Rome",
        "barcelona" to "Europe/Madrid", "porto" to "Europe/Lisbon", "edinburgh" to "Europe/London", "manchester" to "Europe/London",
        "hawaii" to "Pacific/Honolulu", "seattle" to "America/Los_Angeles", "boston" to "America/New_York",
        "miami" to "America/New_York", "atlanta" to "America/New_York", "dallas" to "America/Chicago", "houston" to "America/Chicago",
        "austin" to "America/Chicago", "san diego" to "America/Los_Angeles", "las vegas" to "America/Los_Angeles",
        "washington" to "America/New_York", "philadelphia" to "America/New_York", "montreal" to "America/Toronto",
        "osaka" to "Asia/Tokyo", "kyoto" to "Asia/Tokyo", "mumbai" to "Asia/Kolkata", "delhi" to "Asia/Kolkata",
        "new delhi" to "Asia/Kolkata", "hyderabad" to "Asia/Kolkata", "chennai" to "Asia/Kolkata",
        "rio" to "America/Sao_Paulo", "rio de janeiro" to "America/Sao_Paulo", "tel aviv" to "Asia/Jerusalem",
        "abu dhabi" to "Asia/Dubai", "doha" to "Asia/Qatar", "st petersburg" to "Europe/Moscow", "saint petersburg" to "Europe/Moscow",
    )

    /** US states → majority zone. */
    val STATES = mapOf(
        "alabama" to "America/Chicago", "alaska" to "America/Anchorage", "arizona" to "America/Phoenix", "arkansas" to "America/Chicago",
        "california" to "America/Los_Angeles", "colorado" to "America/Denver", "connecticut" to "America/New_York",
        "delaware" to "America/New_York", "florida" to "America/New_York", "georgia" to "America/New_York",
        "hawaii" to "Pacific/Honolulu", "idaho" to "America/Boise", "illinois" to "America/Chicago", "indiana" to "America/Indiana/Indianapolis",
        "iowa" to "America/Chicago", "kansas" to "America/Chicago", "kentucky" to "America/New_York", "louisiana" to "America/Chicago",
        "maine" to "America/New_York", "maryland" to "America/New_York", "massachusetts" to "America/New_York",
        "michigan" to "America/Detroit", "minnesota" to "America/Chicago", "mississippi" to "America/Chicago",
        "missouri" to "America/Chicago", "montana" to "America/Denver", "nebraska" to "America/Chicago", "nevada" to "America/Los_Angeles",
        "new hampshire" to "America/New_York", "new jersey" to "America/New_York", "new mexico" to "America/Denver",
        "new york state" to "America/New_York", "north carolina" to "America/New_York", "north dakota" to "America/Chicago",
        "ohio" to "America/New_York", "oklahoma" to "America/Chicago", "oregon" to "America/Los_Angeles",
        "pennsylvania" to "America/New_York", "rhode island" to "America/New_York", "south carolina" to "America/New_York",
        "south dakota" to "America/Chicago", "tennessee" to "America/Chicago", "texas" to "America/Chicago", "utah" to "America/Denver",
        "vermont" to "America/New_York", "virginia" to "America/New_York", "washington state" to "America/Los_Angeles",
        "west virginia" to "America/New_York", "wisconsin" to "America/Chicago", "wyoming" to "America/Denver",
    )

    private fun build(): Pair<PhraseTrie<Place>, PhraseTrie<Place>> {
        val t = PhraseTrie<Place>()
        val x = PhraseTrie<Place>()
        fun put(name: String, zoneId: String, display: String = name, exact: Boolean = false) {
            val zone = try { ZoneId.of(zoneId) } catch (_: Exception) { return }
            val keys = Lexicon.keys(name, exact)
            if (keys.isEmpty()) return
            (if (exact) x else t).put(keys, Place(display, zone))
        }
        for ((a, z) in ABBREVIATIONS) put(a, z, a, exact = true)
        for ((a, z) in ALIASES) {
            if (a.length <= 3) put(a.uppercase(), z, a.uppercase(), exact = true)
            else put(a, z, a.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } })
        }
        for ((s, z) in STATES) put(s, z, s.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } })
        // Bundled GeoNames cities (largest first, so the bigger of two same-named cities wins).
        Places::class.java.getResourceAsStream("/summa/places.tsv.gz")?.use { ins ->
            BufferedReader(InputStreamReader(GZIPInputStream(ins), Charsets.UTF_8)).lineSequence().forEach { line ->
                val f = line.split('\t')
                if (f.size >= 3) {
                    put(f[0], f[2], f[0])
                    for (alt in f.getOrNull(4)?.split('|').orEmpty()) if (alt.isNotBlank()) put(alt, f[2], f[0])
                }
            }
        }
        // Every IANA zone's own city ("America/Argentina/Buenos_Aires" → Buenos Aires).
        for (id in ZoneId.getAvailableZoneIds().sorted()) {
            if (!id.contains('/') || id.startsWith("Etc/") || id.startsWith("SystemV/")) continue
            val city = id.substringAfterLast('/').replace('_', ' ')
            if (city.any { it.isDigit() }) continue
            put(city, id, city)
        }
        return t to x
    }

    fun lookup(name: String): Place? = trie.longest(Lexicon.keys(name), 0)?.takeIf { it.first == Lexicon.keys(name).size }?.second
}
