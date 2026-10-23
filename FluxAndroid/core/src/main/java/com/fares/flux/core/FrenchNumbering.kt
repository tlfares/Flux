package com.fares.flux.core

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/** The numbers channels go by, from the provider's list or from a French
 *  line-up people know by heart: the TNT, or their box's. */
enum class ChannelNumbering(val raw: String, private val french: String, private val english: String = french) {
    PROVIDER("provider", "Fournisseur", "Provider"),
    // The French line-ups carry a flag in English: French is known without it.
    TNT("tnt", "TNT", "🇫🇷 TNT"),
    ORANGE("orange", "Orange", "🇫🇷 Orange"),
    BOUYGUES("bouygues", "Bouygues", "🇫🇷 Bouygues"),
    FREE("free", "Free", "🇫🇷 Free"),
    SFR("sfr", "SFR", "🇫🇷 SFR");

    val title: String get() = tr(french, english)

    /** Channel key ("fr|tf1") → number. A channel listed twice keeps its first number. */
    val numbers: Map<String, Int>
        get() {
            val result = HashMap<String, Int>()
            for ((number, name) in FrenchChannels.plans[raw] ?: emptyList()) {
                val key = ChannelName.parse("|FR| " + name).key
                val channel = FrenchChannels.known(key)?.key ?: key
                result.putIfAbsent(channel, number)
            }
            return result
        }

    companion object {
        const val STORAGE_KEY = "live.numbering"

        /** The TNT for French speakers, the provider's order for others. */
        val default: ChannelNumbering
            get() = if (Locale.getDefault().language == "fr" || Locale.getDefault().country == "FR") TNT else PROVIDER

        fun of(raw: String?) = entries.firstOrNull { it.raw == raw } ?: default
    }
}

/** French channels known under several names: all one channel, under its proper name. */
object FrenchChannels {
    data class Known(val key: String, val name: String, val logo: String?)

    /** The proper name first, then what providers write instead. */
    private val names: List<List<String>> = listOf(
        listOf("TF1"),
        listOf("France 2"),
        listOf("France 3"),
        listOf("France 4"),
        listOf("France 5"),
        listOf("M6"),
        listOf("Arte"),
        listOf("LCP", "LCP-Public Sénat", "LCP/PS", "LCP/Public Sénat", "LaChaîne Parlementaire", "LCP Public Sénat", "LCP Assemblée Nationale", "La Chaîne Parlementaire", "La Chains Parlementaire"),
        listOf("Public Sénat", "Public Senat"),
        listOf("W9"),
        listOf("TMC"),
        listOf("TFX"),
        listOf("Gulli"),
        listOf("BFM TV", "BFMTV"),
        listOf("CNews", "C News"),
        listOf("LCI"),
        listOf("franceinfo", "France Info", "France Info TV", "franceinfo:"),
        listOf("CStar", "C Star"),
        listOf("T18"),
        listOf("Novo19", "Novo 19"),
        listOf("TF1 Séries Films", "TF1 Séries", "TF1 Series Films", "TF1 Series / Films", "TF1 Séries-Films", "TF1 Sieres"),
        listOf("L’Équipe", "L'Equipe", "LEquipe", "L Equipe", "La Chaîne L'Équipe", "Lachaîne L'Equipe", "L'Equipe TV", "La chaine l’Équipe"),
        listOf("6ter"),
        listOf("RMC Story"),
        listOf("RMC Découverte", "RMC Decouverte"),
        listOf("RMC Life", "Chérie 25", "Cherie 25"),
        listOf("Paris Première", "Paris Premiere"),
        listOf("Canal+", "Canal Plus"),
        listOf("NRJ 12", "NRJ12"),
        listOf("C8"),
        listOf("RTL 9", "RTL9"),
        listOf("TV5 Monde", "TV5Monde"),
        listOf("France 24"),
        listOf("BFM Business"),
        listOf("13e Rue", "13eme Rue", "13ème Rue", "Treizième Rue"),
        listOf("Série Club", "Serie Club", "SerieClub"),
        listOf("Téva", "Teva"),
        listOf("TV Breizh"),
        listOf("E! Entertainment", "E Entertainment", "E!", "E HD France"),
        listOf("SyFy", "Sci Fi", "SciFi"),
        listOf("Canal+ Sport", "Canal Sport"),
        listOf("Canal+ Sport 360", "Canal Sport 360"),
        listOf("Canal+ Foot", "Canal Foot"),
        listOf("Canal+ Cinéma(s)", "Canal+ Cinema", "Canal+ Cinéma", "Canal+ Cinemas", "Canal Cinema"),
        listOf("Canal+ Box Office", "Canal+ Box-Office", "Canal Box Office"),
        listOf("Canal+ Grand Écran", "Canal+ Grand Ecran", "Canal Grand Ecran"),
        listOf("Canal+ Docs", "Canal+ Doc", "Canal Docs"),
        listOf("Canal+ Kids", "Canal Kids"),
        listOf("Ciné+ Frisson", "Cine+ Frisson", "Cine Frisson"),
        listOf("Ciné+ Émotion", "Cine+ Emotion", "Cine Emotion"),
        listOf("Ciné+ Family", "Cine+ Family", "Cine Family"),
        listOf("Ciné+ Festival", "Cine+ Festival", "Cine Festival"),
        listOf("Ciné+ Classic", "Cine+ Classic", "Cine Classic"),
        listOf("beIN Sports 1", "beIN Sport 1"),
        listOf("beIN Sports 2", "beIN Sport 2"),
        listOf("beIN Sports 3", "beIN Sport 3"),
        listOf("RMC Sport 1"),
        listOf("RMC Sport 2", "RMC Sport Live 2"),
        listOf("Eurosport 1"),
        listOf("Eurosport 2"),
        listOf("Ligue 1+", "Ligue 1 Plus"),
        listOf("Infosport+", "Infosport"),
        listOf("Equidia"),
        listOf("Automoto", "Auto Moto"),
        listOf("Planète+", "Planete+", "Planete"),
        listOf("Planète+ Crime", "Planete+ Crime", "Planete Crime"),
        listOf("Planète+ Aventure", "Planete+ Aventure", "Planete Aventure"),
        listOf("Ushuaïa TV", "Ushuaia TV", "Ushuaia"),
        listOf("Histoire TV", "Histoire"),
        listOf("Toute l’Histoire", "Toute l'Histoire"),
        listOf("Discovery Channel", "Discovery"),
        listOf("Discovery Investigation", "Investigation Discovery"),
        listOf("National Geographic", "Nat Geo", "National Geographic Channel"),
        listOf("Nat Geo Wild", "National Geographic Wild"),
        listOf("Science & Vie TV", "Science et Vie TV", "Science Vie TV"),
        listOf("Animaux"),
        listOf("Chasse & Pêche", "Chasse et Peche", "Chasse Peche"),
        listOf("Trek"),
        listOf("Museum TV", "Museum"),
        listOf("KTO"),
        listOf("Mezzo"),
        listOf("MCM"),
        listOf("MTV"),
        listOf("M6 Music"),
        listOf("NRJ Hits", "NRJ Hits TV"),
        listOf("Melody"),
        listOf("Comédie+", "Comedie+", "Comedie"),
        listOf("Comedy Central"),
        listOf("Warner TV"),
        listOf("Paramount Network"),
        listOf("TCM Cinéma", "TCM Cinema", "TCM"),
        listOf("Action"),
        listOf("Polar+", "Polar"),
        listOf("OCS", "OCS Max"),
        listOf("AB1"),
        listOf("AB3"),
        listOf("Mangas"),
        listOf("Game One"),
        listOf("Tiji"),
        listOf("Canal J"),
        listOf("Piwi+", "Piwi"),
        listOf("Télétoon+", "Teletoon+", "Teletoon"),
        listOf("Télétoon+1", "Teletoon+1", "Teletoon +1"),
        listOf("Boomerang"),
        listOf("Cartoon Network"),
        listOf("Cartoonito", "Boing"),
        listOf("Nickelodeon", "Nickelodeon France"),
        listOf("Nickelodeon Junior", "Nick Jr"),
        listOf("Nickelodeon+1", "Nickelodeon +1"),
        listOf("Nicktoons"),
        listOf("Disney Channel"),
        listOf("Disney Junior"),
        listOf("Dreamworks", "DreamWorks Channel"),
        listOf("Gulli"),
        listOf("Euronews"),
        listOf("i24 News", "i24NEWS", "I24"),
        listOf("La Chaîne Météo", "La Chaine Meteo"),
        listOf("Bfm Lyon", "BFM Lyon Métropole"),
        listOf("BFM Paris", "BFM Paris Île-de-France", "BFM Paris Ile-de-France"),
        listOf("BFM Marseille Provence", "BFM Marseille"),
        listOf("BFM Grand Lille"),
        listOf("BFM Grand Littoral"),
        listOf("BFM Alsace"),
        listOf("BFM Normandie"),
        listOf("BFM Grands Reportages"),
        listOf("RMC Talk Info"),
        listOf("Olympia TV"),
        listOf("Fashion TV"),
        listOf("Luxe TV"),
        listOf("Men’s Up TV", "Men's Up", "Mens Up TV"),
        listOf("Top Santé TV", "Top Sante TV"),
        listOf("Maison & Travaux TV", "Maison Travaux TV"),
        listOf("Le Figaro TV", "Figaro TV", "Figaro TV IDF"),
        listOf("Trace Urban"),
        listOf("Trace Caribbean"),
        listOf("Golf+", "Golf+ HD", "Golf Plus"),
    )

    /** Today's logos, from the tv-logo/tv-logos collection, by proper name. */
    private val logos: Map<String, String> = mapOf(
        "TF1" to "tf1",
        "France 2" to "france-2",
        "France 3" to "france-3",
        "France 4" to "france-4",
        "France 5" to "france-5",
        "M6" to "m6",
        "Arte" to "arte",
        "LCP" to "lcp",
        "Public Sénat" to "public-senat",
        "W9" to "w9",
        "TMC" to "tmc",
        "TFX" to "tfx",
        "Gulli" to "gulli",
        "BFM TV" to "bfm-tv",
        "CNews" to "c-news",
        "LCI" to "lci",
        "franceinfo" to "franceinfo",
        "CStar" to "c-star",
        "T18" to "t18",
        "Novo19" to "novo19",
        "TF1 Séries Films" to "tf1-series-films",
        "L’Équipe" to "lequipe",
        "6ter" to "6ter",
        "RMC Story" to "rmc-story",
        "RMC Découverte" to "rmc-decouverte",
        "RMC Life" to "rmc-life",
        "Paris Première" to "paris-premiere",
        "Canal+" to "canal-plus",
        "NRJ 12" to "nrj-12",
        "C8" to "c8",
        "RTL 9" to "rtl9",
        "TV5 Monde" to "tv5-monde",
        "France 24" to "france-24",
        "BFM Business" to "bfm-business",
        "Série Club" to "serie-club",
        "Téva" to "teva",
        "TV Breizh" to "tv-breizh",
        "Canal+ Sport" to "canal-plus-sport",
        "Canal+ Sport 360" to "canal-plus-sport-360",
        "Canal+ Foot" to "canal-plus-foot",
        "Canal+ Cinéma(s)" to "canal-plus-cinemas",
        "Canal+ Box Office" to "canal-plus-box-office",
        "Canal+ Grand Écran" to "canal-plus-grand-ecran",
        "Canal+ Docs" to "canal-plus-docs",
        "Canal+ Kids" to "canal-plus-kids",
        "Ciné+ Frisson" to "cine-plus-frisson",
        "Ciné+ Émotion" to "cine-plus-emotion",
        "Ciné+ Family" to "cine-plus-family",
        "Ciné+ Festival" to "cine-plus-festival",
        "Ciné+ Classic" to "cine-plus-classic",
        "beIN Sports 1" to "bein-sports-1-french",
        "beIN Sports 2" to "bein-sports-2-french",
        "beIN Sports 3" to "bein-sports-3-french",
        "RMC Sport 1" to "rmc-sport-1",
        "RMC Sport 2" to "rmc-sport-2",
        "Eurosport 1" to "eurosport-1",
        "Eurosport 2" to "eurosport-2",
        "Ligue 1+" to "ligue-1plus",
        "Infosport+" to "infosport-plus",
        "Equidia" to "equidia",
        "Automoto" to "automoto-la-chaine",
        "Planète+" to "planete-plus",
        "Planète+ Crime" to "planete-plus-crime",
        "Planète+ Aventure" to "planete-plus-aventure",
        "Ushuaïa TV" to "ushuaia-tv",
        "Histoire TV" to "histoire-tv",
        "Toute l’Histoire" to "toute-lhistoire",
        "Science & Vie TV" to "science-and-vie-tv",
        "Animaux" to "animaux",
        "Chasse & Pêche" to "chasse-et-peche",
        "Trek" to "trek",
        "KTO" to "kto",
        "Mezzo" to "mezzo",
        "MCM" to "mcm",
        "M6 Music" to "m6-music",
        "NRJ Hits" to "nrj-hits",
        "Melody" to "melody",
        "Comédie+" to "comedie-plus",
        "Warner TV" to "warner-tv",
        "TCM Cinéma" to "tcm-cinema",
        "Action" to "action",
        "Polar+" to "polar-plus",
        "OCS" to "ocs",
        "AB1" to "ab1",
        "AB3" to "ab3",
        "Mangas" to "mangas",
        "Game One" to "game-one",
        "Tiji" to "tiji",
        "Canal J" to "canal-j",
        "Piwi+" to "piwi-plus",
        "Télétoon+" to "teletoon-plus",
        "Télétoon+1" to "teletoon-plus-1",
        "Boomerang" to "boomerang",
        "Cartoon Network" to "cartoon-network",
        "Nickelodeon" to "nickelodeon",
        "Nickelodeon Junior" to "nickelodeon-junior",
        "Disney Channel" to "disney-channel",
        "Disney Junior" to "disney-jr",
        "La Chaîne Météo" to "la-chaine-meteo",
        "Bfm Lyon" to "bfm-lyon",
        "BFM Paris" to "bfm-paris",
        "BFM Grand Lille" to "bfm-grand-lille",
        "BFM Grand Littoral" to "bfm-grand-littoral",
        "Olympia TV" to "olympia-tv",
        "Trace Urban" to "trace-urban",
        "Trace Caribbean" to "trace-caribbean",
        "Golf+" to "golf-plus",
    )

    private val table: Map<String, Known> by lazy {
        val table = HashMap<String, Known>()
        for (synonyms in names) {
            val logo = logos[synonyms[0]]?.let { "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/france/$it-fr.png" }
            val known = Known(ChannelName.parse("|FR| " + synonyms[0]).key, synonyms[0], logo)
            for (name in synonyms) table.putIfAbsent(ChannelName.parse("|FR| " + name).key, known)
        }
        table
    }

    /** The channel a French key stands for, under its proper name. */
    fun known(key: String): Known? = table[key]

    /** Line-up → (number, name), from the same Numbering.json as the Apple apps. */
    var plans: Map<String, List<Pair<Int, String>>> = emptyMap()
        private set

    fun load(context: Context) {
        if (plans.isNotEmpty()) return
        plans = runCatching {
            val json = JSONObject(context.assets.open("Numbering.json").bufferedReader().readText())
            json.keys().asSequence().associateWith { plan ->
                val rows = json.getJSONArray(plan)
                (0 until rows.length()).mapNotNull { i ->
                    val row = rows.optJSONArray(i) ?: return@mapNotNull null
                    if (row.length() != 2) null else row.getInt(0) to row.getString(1)
                }
            }
        }.getOrDefault(emptyMap())
    }
}
