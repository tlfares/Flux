package com.fares.flux.core

import kotlinx.serialization.Serializable
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/** One section of the provider (live, films or series), indexed once. */
@Serializable
data class CatalogSection<T>(
    val categories: List<Category> = emptyList(),
    val items: List<T> = emptyList(),
    /** Category id → indices into `items`. */
    val index: Map<String, List<Int>> = emptyMap(),
) {
    fun items(categoryID: String?): List<T> =
        if (categoryID == null) items else (index[categoryID] ?: emptyList()).map { items[it] }

    val isEmpty: Boolean get() = items.isEmpty()

    companion object {
        fun <T> build(categories: List<Category>, items: List<T>, categoryOf: (T) -> String?): CatalogSection<T> {
            val index = LinkedHashMap<String, MutableList<Int>>()
            items.forEachIndexed { i, item -> index.getOrPut(categoryOf(item) ?: "") { mutableListOf() }.add(i) }
            // Only categories with something in them, in the provider's order.
            return CatalogSection(categories.filter { index.containsKey(it.id) }, items, index)
        }
    }
}

/** How good a stream claims to be, read from its name. */
@Serializable
data class Quality(val rank: Int, val label: String) {
    /** The label as shown: `label` stays French, as remembered picks and saved guides keep it. */
    val title: String
        get() = if (isFrench) label else label.replace("Gratuit", "Free").replace("Secours", "Backup").replace(" i/s", " fps")

    companion object {
        /** 1 SD … 6 4K. Untagged streams sit with HD. */
        val untagged = Quality(3, "Standard")
    }
}

/** One way to watch a channel: a stream, its quality, and how many hours
 *  behind the live broadcast it runs (0 for live). */
@Serializable
data class ChannelVariant(
    val channel: LiveChannel,
    val quality: Quality,
    val delay: Int = 0,
    /** One of the free channels' streams, standing in for the provider's. */
    val isFree: Boolean = false,
) {
    val id: Int get() = channel.id

    companion object {
        val freeQuality = Quality(7, "Gratuit")
    }
}

/** A channel as people think of it: "TF1", whatever the number of streams
 *  the provider lists for it. */
@Serializable
data class ChannelGroup(
    /** Country and folded name, "fr|tf1". Favorites and recents go by it. */
    val id: String,
    val name: String,
    val icon: String?,
    val number: Int? = null,
    /** The streams of the earliest broadcast, best first. */
    val variants: List<ChannelVariant>,
    val shifted: List<ChannelVariant> = emptyList(),
) {
    val streams: List<ChannelVariant> get() = variants + shifted
    val epgChannel: LiveChannel get() = (variants.firstOrNull { it.channel.epgID != null } ?: variants[0]).channel
    val hasChoice: Boolean get() = variants.size > 1
    val baseDelay: Int get() = variants[0].delay

    fun variants(delay: Int): List<ChannelVariant> = if (delay == baseDelay) variants else shifted.filter { it.delay == delay }

    /** The stream to start: the one picked last time, else the closest to the preference. */
    fun preferred(preference: QualityPreference, remembered: String?): ChannelVariant {
        val candidates = variants
        if (remembered != null) candidates.firstOrNull { it.quality.label == remembered }?.let { return it }
        // The broadcaster's own stream, whatever the quality asked for: it
        // plays better and takes none of the subscription's screens.
        candidates.firstOrNull { it.isFree }?.let { return it }
        return when (preference) {
            QualityPreference.BEST -> candidates[0]
            QualityPreference.SAVER -> candidates.last()
            QualityPreference.HD -> candidates.minWith { a, b ->
                val da = abs(a.quality.rank - 3.5)
                val db = abs(b.quality.rank - 3.5)
                if (da == db) b.quality.rank.compareTo(a.quality.rank) else da.compareTo(db)
            }
        }
    }
}

enum class QualityPreference(val raw: String, private val french: String, private val english: String) {
    BEST("best", "Maximale", "Best"), HD("hd", "HD", "HD"), SAVER("saver", "Économie", "Data saver");

    val title: String get() = tr(french, english)

    companion object {
        const val STORAGE_KEY = "live.quality"
        fun of(raw: String?) = entries.firstOrNull { it.raw == raw } ?: BEST
    }
}

/** `prefersFree`: the free channels' streams first for the channels the
 *  provider has too (France 2, Arte, BFM TV…), the provider's kept as backups.
 *  `freeCountry`: the country the free channels' account shows ("FR", "US",
 *  "UK"), or "ALL". */
data class GuideOptions(
    val showsShifted: Boolean = false,
    val numbering: ChannelNumbering = ChannelNumbering.PROVIDER,
    val prefersFree: Boolean = true,
    val freeCountry: String = FreeChannels.ALL,
) {
    companion object {
        const val SHIFTED_KEY = "live.showsShifted"
        const val PREFERS_FREE_KEY = "live.prefersFree"
        const val FREE_COUNTRIES_KEY = "free.countries"
    }
}

/** The live section with each channel's streams put together. */
class LiveGuide private constructor(
    /** Numbered channels first, by number; the rest in the provider's order. */
    val groups: List<ChannelGroup>,
    private val byCategory: Map<String, List<Int>>,
    private val byKey: Map<String, Int>,
    private val byStream: Map<Int, Int>,
) {
    val isEmpty: Boolean get() = groups.isEmpty()

    fun groups(categoryID: String?): List<ChannelGroup> =
        if (categoryID == null) groups else (byCategory[categoryID] ?: emptyList()).map { groups[it] }

    fun group(key: String): ChannelGroup? = byKey[key]?.let { groups[it] }

    /** How many channels a category has, without listing them. */
    fun count(categoryID: String): Int = byCategory[categoryID]?.size ?: 0

    /** Kept on disk, so Accueil and Direct show at once at the next start. */
    @Serializable
    data class Saved(val options: String, val groups: List<ChannelGroup>, val byCategory: Map<String, List<Int>>)

    fun saved(options: String) = Saved(options, groups, byCategory)

    companion object {
        val empty = LiveGuide(emptyList(), emptyMap(), emptyMap(), emptyMap())

        fun restore(saved: Saved): LiveGuide {
            val byKey = HashMap<String, Int>()
            val byStream = HashMap<Int, Int>()
            saved.groups.forEachIndexed { i, g -> byKey[g.id] = i; g.streams.forEach { byStream[it.id] = i } }
            return LiveGuide(saved.groups, saved.byCategory, byKey, byStream)
        }

        /** `free`: the free channels' streams, added to the channels the provider has too. */
        fun build(section: CatalogSection<LiveChannel>, options: GuideOptions, free: List<LiveChannel> = emptyList()): LiveGuide {
            // Untagged names take their category's country.
            val countries = HashMap<String, String>()
            for (c in section.categories) countries.putIfAbsent(c.id, ChannelName.categoryCountry(c.name))
            val parsed = section.items.map { ChannelName.parse(it.name, it.categoryID?.let { id -> countries[id] } ?: "") }.toMutableList()
            // "TF1 1" next to "TF1", with no "TF1 2": a backup stream of TF1.
            val keys = parsed.map { it.key }.toHashSet()
            for (i in parsed.indices) {
                val base = parsed[i].backupOf ?: continue
                if (base in keys && (base + "2") !in keys) parsed[i] = parsed[i].copy(key = base)
            }

            // Radios kept apart from the TV channel of the same name: France Info the radio isn't franceinfo the channel.
            val radioCategories = section.categories.filter { c -> ChannelName.foldText(c.name).split(Regex("[^a-z0-9]+")).any { it == "radio" || it == "radios" } }.map { it.id }.toHashSet()

            class Building(val id: String, var name: String, var icon: String?, val variants: MutableList<ChannelVariant>)
            val building = mutableListOf<Building>()
            val byKey = HashMap<String, Int>()
            val names = HashMap<Int, MutableList<String>>()
            for ((channel, name) in section.items.zip(parsed)) {
                if (name.delay > 0 && !options.showsShifted) continue
                var key = name.key
                var display = name.display
                var icon = channel.icon
                val radio = channel.categoryID in radioCategories
                if (radio) key = "radio|$key"
                // Known French channels by one name, whatever the spelling.
                else if (name.country.uppercase() == "FR") FrenchChannels.known(name.key)?.let {
                    key = it.key; display = it.name; icon = it.logo ?: icon
                }
                val variant = ChannelVariant(channel, name.quality, name.delay)
                val i = byKey[key]
                if (i != null) {
                    building[i].variants.add(variant)
                    if (building[i].icon == null) building[i].icon = icon
                    names.getOrPut(i) { mutableListOf() }.add(display)
                } else {
                    byKey[key] = building.size
                    names[building.size] = mutableListOf(display)
                    building.add(Building(key, display, icon, mutableListOf(variant)))
                }
            }
            // Only to channels already here: the free list is the subscription's
            // channels played better, not more channels.
            for (channel in free) {
                val name = ChannelName.parse(channel.name, "FR")
                val i = byKey[FrenchChannels.known(name.key)?.key ?: name.key] ?: continue
                building[i].variants.add(ChannelVariant(channel, ChannelVariant.freeQuality, isFree = true))
            }

            val numbers = options.numbering.numbers
            val groups = building.mapIndexed { i, b ->
                // Earliest broadcast first, then highest quality; ties keep the provider's order.
                val sorted = b.variants.withIndex().sortedWith(compareBy<IndexedValue<ChannelVariant>> { it.value.delay }
                    .thenByDescending { it.value.quality.rank }.thenBy { it.index }).map { it.value }
                // Two "HD" read "HD" and "HD 2": the second is a backup.
                val seen = HashMap<String, Int>()
                val labelled = sorted.map { v ->
                    val tag = "${v.delay}|${v.quality.label}"
                    val n = (seen[tag] ?: 0) + 1
                    seen[tag] = n
                    if (n > 1) v.copy(quality = v.quality.copy(label = "${v.quality.label} $n")) else v
                }
                val base = labelled[0].delay
                // "France 2" reads better than "FRANCE 2"; known channels keep their own name.
                val name = if (FrenchChannels.known(b.id) == null) names[i]?.firstOrNull { it != it.uppercase() } ?: b.name else b.name
                val current = labelled.filter { it.delay == base }
                // Not in the line-up picked: the provider's own number, with its numbering.
                val number = numbers[b.id] ?: if (options.numbering == ChannelNumbering.PROVIDER) current.mapNotNull { it.channel.number }.filter { it > 0 }.minOrNull() else null
                ChannelGroup(b.id, name, b.icon, number, current, labelled.filter { it.delay != base })
            }

            // Numbered channels first, by number.
            val order = groups.indices.sortedWith { a, b ->
                val x = groups[a].number
                val y = groups[b].number
                when {
                    x != null && y != null -> if (x != y) x.compareTo(y) else a.compareTo(b)
                    x != null -> -1
                    y != null -> 1
                    else -> a.compareTo(b)
                }
            }
            // The rest follow on, as on a box: 27, 28…
            var next = (groups.mapNotNull { it.number }.maxOrNull() ?: 0) + 1
            val ordered = order.map { groups[it] }.map { g -> if (g.number != null) g else g.copy(number = next++) }
            val finalKey = HashMap<String, Int>()
            val byStream = HashMap<Int, Int>()
            ordered.forEachIndexed { i, g ->
                finalKey[g.id] = i
                g.streams.forEach { byStream[it.id] = i }
            }
            // A channel is in every category one of its streams is in, numbered ones first there too.
            val byCategory = HashMap<String, List<Int>>()
            for ((categoryID, indices) in section.index) {
                val seenGroups = HashSet<Int>()
                val list = mutableListOf<Int>()
                for (item in indices) {
                    val g = byStream[section.items[item].id] ?: continue
                    if (seenGroups.add(g)) list.add(g)
                }
                byCategory[categoryID] = list.filter { ordered[it].number != null }.sorted() + list.filter { ordered[it].number == null }
            }
            return LiveGuide(ordered, byCategory, finalKey, byStream)
        }
    }
}

/** A provider's channel name taken apart: "|FR| beIN SPORTS ᵁᴴᴰ 1" is
 *  beIN Sports 1, in France, in UHD. Keys match the Apple apps', so
 *  favorites and recents carry over when paired. */
data class ChannelName(
    val country: String,
    val display: String,
    val quality: Quality,
    val key: String,
    val delay: Int,
    val backupOf: String?,
) {
    companion object {
        private val superscripts: Map<Char, Char> = mapOf(
            'ᴬ' to 'A', 'ᴮ' to 'B', 'ᶜ' to 'C', 'ᴰ' to 'D', 'ᴱ' to 'E', 'ᶠ' to 'F', 'ᴳ' to 'G', 'ᴴ' to 'H', 'ᴵ' to 'I', 'ᴶ' to 'J',
            'ᴷ' to 'K', 'ᴸ' to 'L', 'ᴹ' to 'M', 'ᴺ' to 'N', 'ᴼ' to 'O', 'ᴾ' to 'P', 'ᴿ' to 'R', 'ˢ' to 'S', 'ᵀ' to 'T', 'ᵁ' to 'U',
            'ⱽ' to 'V', 'ᵂ' to 'W', 'ˣ' to 'X', 'ᵞ' to 'Y', 'ᶻ' to 'Z', '⁰' to '0', '¹' to '1', '²' to '2', '³' to '3', '⁴' to '4',
            '⁵' to '5', '⁶' to '6', '⁷' to '7', '⁸' to '8', '⁹' to '9', 'ᵏ' to 'K', 'ʰ' to 'H', 'ᵈ' to 'D', 'ˡ' to 'L',
            'ʜ' to 'H', 'ᴅ' to 'D', 'ꜱ' to 'S', 'ᴜ' to 'U', 'ᴋ' to 'K',
        )

        private val qualityTags: Map<String, Quality> = mapOf(
            "SD" to Quality(1, "SD"), "LQ" to Quality(1, "SD"), "LOW" to Quality(1, "SD"), "480P" to Quality(1, "SD"), "576P" to Quality(1, "SD"),
            "HD" to Quality(3, "HD"), "720P" to Quality(3, "HD"), "HQ" to Quality(3, "HD"),
            "FHD" to Quality(4, "FHD"), "FULLHD" to Quality(4, "FHD"), "1080P" to Quality(4, "FHD"), "1080" to Quality(4, "FHD"), "1080I" to Quality(4, "FHD"),
            "HEVC" to Quality(5, "HEVC"), "H265" to Quality(5, "HEVC"), "H.265" to Quality(5, "HEVC"), "X265" to Quality(5, "HEVC"),
            "UHD" to Quality(5, "UHD"), "4K" to Quality(6, "4K"), "2160P" to Quality(6, "4K"), "8K" to Quality(6, "8K"),
            "HDR" to Quality(6, "HDR"), "50FPS" to Quality(4, "50 i/s"), "60FPS" to Quality(4, "60 i/s"),
            "RAW" to Quality(4, "RAW"), "VIP" to Quality(3, "VIP"), "BACKUP" to Quality(2, "Secours"), "BK" to Quality(2, "Secours"),
        )

        /** The ISO country codes, and "UK": a bracketed "[FR] TF1" is read as a
         *  country only with one of these. */
        private val countryCodes: Set<String> = ("AD AE AF AG AL AM AO AR AT AU AZ BA BB BD BE BF BG BH BI BJ BN BO BR BS BT BW BY BZ CA CD CF CG CH CI CL CM CN CO CR " +
            "CU CV CY CZ DE DJ DK DM DO DZ EC EE EG ER ES ET FI FJ FR GA GB GD GE GH GM GN GQ GR GT GW GY HK HN HR HT HU ID IE IL " +
            "IN IQ IR IS IT JM JO JP KE KG KH KM KN KP KR KW KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MG MK ML MM MN MR MT " +
            "MU MV MW MX MY MZ NA NE NG NI NL NO NP NZ OM PA PE PG PH PK PL PS PT PY QA RE RO RS RU RW SA SC SD SE SG SI SK SL SN " +
            "SO SR SS SV SY SZ TD TG TH TJ TL TM TN TR TT TW TZ UA UG UK US UY UZ VE VN YE ZA ZM ZW").split(' ').toSet()

        /** Without brackets, "FR TF1" or "TF1 FR" could be words of the name
         *  ("LA CHAINE METEO", "Planète+ CI"): only the countries panels list. */
        private val bareCountries = setOf("FR", "BE", "CH", "CA", "UK", "US", "DE", "ES", "IT", "PT", "NL", "AR", "MA", "DZ", "TN", "TR", "PL", "RO", "GR")

        /** Three letters, or the country spelt out, as some panels write it. */
        private val countryNames = mapOf(
            "FRA" to "FR", "FRANCE" to "FR", "FRENCH" to "FR", "FRANCAIS" to "FR", "FRANÇAIS" to "FR", "BEL" to "BE", "BELGIQUE" to "BE", "BELGIUM" to "BE",
            "SUI" to "CH", "SUISSE" to "CH", "SWISS" to "CH", "CAN" to "CA", "CANADA" to "CA", "QUEBEC" to "CA", "QUÉBEC" to "CA", "USA" to "US",
            "ENG" to "UK", "GER" to "DE", "DEU" to "DE", "ITA" to "IT", "ESP" to "ES", "POR" to "PT", "TUR" to "TR", "ARB" to "AR", "ARAB" to "AR",
        )

        /** A country written before a name or a category: "FR", "fr", "FRA" or "France". */
        fun country(word: String): String? {
            val w = word.trim().uppercase(Locale.ROOT)
            if (w.length == 2 && w in countryCodes) return w
            return countryNames[w]
        }

        /** "|FR| TF1", "[FR] TF1", "(FR) TF1", "🇫🇷 TF1", "FR: TF1", "FR - TF1",
         *  "FRANCE | TF1", "FR TF1" → FR, TF1. Panels write it every way. */
        fun splitCountry(name: String): Pair<String, String> {
            val s = name.trimStart(' ')
            if (s.isEmpty()) return "" to s
            // A flag: two regional indicator letters.
            val a = s.codePointAt(0)
            if (a in 0x1F1E6..0x1F1FF && s.length >= 4) {
                val b = s.codePointAt(2)
                if (b in 0x1F1E6..0x1F1FF) {
                    val code = "${'A' + (a - 0x1F1E6)}${'A' + (b - 0x1F1E6)}"
                    return (if (code == "GB") "UK" else code) to s.substring(4)
                }
            }
            if (s.startsWith("|")) {
                val close = s.indexOf('|', 1)
                if (close in 1..8) return s.substring(1, close).trim() to s.substring(close + 1)
            }
            val closing = mapOf('[' to ']', '(' to ')', '{' to '}')[s[0]]
            if (closing != null) {
                val close = s.indexOf(closing, 1)
                if (close in 1..9) country(s.substring(1, close))?.let { return it to s.substring(close + 1) }
            }
            val sep = s.take(9).indexOfFirst { it in "|:-▎┃│»" }
            if (sep >= 0) {
                // A real country, so "LCP-Public Sénat" or "RMC-Story" stay whole.
                val head = s.substring(0, sep).trim()
                if (head.isNotEmpty() && head.none { it.isLowerCase() }) country(head)?.let { return it to s.substring(sep + 1) }
            }
            // "FR TF1": only a country panels list, so "TV Breizh" stays whole.
            if (s.length > 3 && s[2] == ' ' && s.substring(0, 2) in bareCountries) return s.substring(0, 2) to s.substring(2)
            return "" to s
        }

        /** A category's country: as a channel's, or its first word spelt out ("FRANCE FHD | TV"). */
        fun categoryCountry(name: String): String {
            splitCountry(name).first.takeIf { it.isNotEmpty() }?.let { return it }
            val first = name.split(' ', '|', ':', '-', '_').firstOrNull { it.isNotBlank() }?.uppercase(Locale.ROOT) ?: return ""
            return if (first in bareCountries) first else countryNames[first] ?: ""
        }

        private fun tag(s: String) = qualityTags[s.uppercase(Locale.ROOT).trim { it in ".,;:()[]-_*✪★|" }]

        /** Tags glued to each other ("UHD/4K", "HD|FHD") or to the name ("TF1HD"). */
        private fun glued(word: String): Pair<String?, List<Quality>>? {
            val parts = word.split('/', '|', '+').filter { it.isNotEmpty() }
            if (parts.size > 1) {
                val found = parts.mapNotNull { tag(it) }
                if (found.size == parts.size) return null to found
            }
            val upper = word.uppercase(Locale.ROOT)
            for (suffix in listOf("UHD", "FHD", "HEVC", "HD", "SD", "4K")) {
                if (!upper.endsWith(suffix) || upper.length <= suffix.length + 1) continue
                val head = word.dropLast(suffix.length)
                if (head.last().isDigit() && head.any { it.isLetter() }) tag(suffix)?.let { return head to listOf(it) }
            }
            return null
        }

        /** Split on whitespace, without a regex. */
        private fun words(s: String): List<String> {
            val list = ArrayList<String>()
            var start = -1
            for (i in s.indices) {
                if (s[i].isWhitespace()) { if (start >= 0) { list.add(s.substring(start, i)); start = -1 } }
                else if (start < 0) start = i
            }
            if (start >= 0) list.add(s.substring(start))
            return list
        }

        fun parse(raw: String, fallbackCountry: String = ""): ChannelName {
            // Superscript runs ("ᵁᴴᴰ", glued as in "2ˢᴰ") become separate words.
            val text = StringBuilder()
            var inSuper = false
            for (ch in raw) {
                val plain = superscripts[ch]
                if (plain != null) {
                    if (!inSuper) text.append(' ')
                    text.append(plain)
                    inSuper = true
                } else {
                    if (inSuper) text.append(' ')
                    text.append(ch)
                    inSuper = false
                }
            }

            val (splitCountry, rest) = splitCountry(text.toString())
            var country = splitCountry
            val words = mutableListOf<String>()
            val tags = mutableListOf<Quality>()
            var delay = 0
            val all = words(rest)
            var skipNext = false
            for ((n, word) in all.withIndex()) {
                if (skipNext) { skipNext = false; continue }
                // "+ 3": the sign and the hours apart.
                if (word == "+" && n + 1 < all.size) {
                    val h = hours(all[n + 1])
                    if (h != null) { delay = h; skipNext = true; continue }
                }
                val shifted = shift(word)
                if (shifted != null) { delay = shifted; continue }
                // "DUE+24": glued to the name.
                val plus = word.lastIndexOf('+')
                if (plus > 0) {
                    val h = hours(word.substring(plus + 1))
                    val head = word.substring(0, plus)
                    if (h != null && head.takeLast(2).all { it.isLetter() } && head.uppercase() != "CANAL") {
                        delay = h
                        words.add(head)
                        continue
                    }
                }
                val bare = word.uppercase().trim { it in ".,;:()[]-_*✪★|" }
                val tag = qualityTags[bare]
                val glued = if (tag == null) glued(word) else null
                if (tag != null) tags.add(tag)
                else if (glued != null) { tags += glued.second; glued.first?.let(words::add) }
                else if (bare.isNotEmpty() || word.any { it.isLetter() }) words.add(word)
            }
            // "TF1 (FR)", "TF1 FR": the country at the end.
            if (country.isEmpty() && words.size > 1) {
                val last = words.last()
                val inner = last.trim { it in "()[]{}|" }
                val code = country(inner)
                if (code != null && (inner != last || inner in bareCountries)) { country = code; words.removeAt(words.size - 1) }
            }
            if (country.isEmpty()) country = fallbackCountry
            // "beIN Sports 2 HD FR": the country again, at the end.
            if (words.size > 1 && country.isNotEmpty() && country(words.last().trim { it in "()[]{}|" }) == country.uppercase()) words.removeAt(words.size - 1)
            if (words.isEmpty()) words.addAll(words(rest))

            val quality = if (tags.isEmpty()) Quality.untagged else {
                val labels = mutableListOf<String>()
                for (t in tags.sortedByDescending { it.rank }) if (t.label !in labels) labels.add(t.label)
                Quality(tags.maxOf { it.rank }, labels.take(2).joinToString(" "))
            }

            var display = words.joinToString(" ").trim { it in " .-|" }
            if (display.isEmpty()) display = raw

            val prefix = country.lowercase(Locale.ROOT) + "|"
            val folded = fold(words)
            val key = prefix + folded.ifEmpty { raw.lowercase(Locale.ROOT) }
            val backupOf = if (words.size > 1 && words.last() == "1") prefix + fold(words.dropLast(1)) else null
            return ChannelName(country, display, quality, key, delay, backupOf)
        }

        /** Case, accents and width away, as Swift's folding does. */
        fun foldText(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFKD).let { n ->
                // No regex: built for each word of 20 000 names, they took most of a minute on a Fire TV.
                val out = StringBuilder(n.length)
                for (c in n) if (Character.getType(c).let { it != Character.NON_SPACING_MARK.toInt() && it != Character.COMBINING_SPACING_MARK.toInt() && it != Character.ENCLOSING_MARK.toInt() }) out.append(c)
                out.toString()
            }.lowercase(Locale.ROOT)

        /** The key ignores case, accents, spacing and punctuation, and a plural s. */
        fun fold(words: List<String>): String {
            val key = StringBuilder()
            for (word in words) {
                var w = foldText(word).filter { it.isLetterOrDigit() }
                if (w.length > 3 && w.endsWith("s")) w = w.dropLast(1)
                key.append(w)
            }
            return key.toString()
        }

        /** "+4", "-2H", "|-12H|", "+6h": hours behind. */
        private fun shift(word: String): Int? {
            val w = word.trim { it in "|()[]" }
            val sign = w.firstOrNull() ?: return null
            if (sign != '+' && sign != '-') return null
            return hours(w.drop(1))
        }

        /** "4", "4H", "24h", within two days. */
        private fun hours(s: String): Int? {
            var digits = s
            if (digits.endsWith("H") || digits.endsWith("h")) digits = digits.dropLast(1)
            if (digits.isEmpty() || digits.length > 2 || !digits.all { it.isDigit() }) return null
            return digits.toInt().takeIf { it in 1..48 }
        }
    }
}
