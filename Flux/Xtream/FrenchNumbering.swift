import Foundation

/// The numbers channels go by, from the provider's list or from a French
/// line-up people know by heart: the TNT, or their box's.
nonisolated enum ChannelNumbering: String, CaseIterable, Sendable {
    case provider, tnt, orange, bouygues, free, sfr

    var title: String {
        switch self {
        case .provider: "Fournisseur"
        case .tnt: "TNT"
        case .orange: "Orange"
        case .bouygues: "Bouygues"
        case .free: "Free"
        case .sfr: "SFR"
        }
    }

    var subtitle: String {
        switch self {
        case .provider: "Les numéros et l’ordre de ton abonnement."
        case .tnt: "Les numéros de la TNT : 1 TF1, 2 France 2…"
        case .orange: "Les numéros de la TV d’Orange."
        case .bouygues: "Les numéros de la Bbox."
        case .free: "Les numéros de la Freebox."
        case .sfr: "Les numéros de la box SFR."
        }
    }

    static let storageKey = "live.numbering"

    /// The TNT for French speakers, the provider's order for others.
    static var `default`: ChannelNumbering {
        let locale = Locale.current
        return locale.region == .france || locale.language.languageCode == .french ? .tnt : .provider
    }

    /// Channel key ("fr|tf1") → number. A channel listed twice (TF1 and
    /// TF1 4K) keeps its first number.
    var numbers: [String: Int] {
        var result: [String: Int] = [:]
        for (number, name) in plan {
            let key = ChannelName("|FR| " + name).key
            let channel = FrenchChannels.known(key)?.key ?? key
            if result[channel] == nil { result[channel] = number }
        }
        return result
    }

    private var plan: [(Int, String)] {
        self == .provider ? [] : FrenchChannels.plans[rawValue] ?? []
    }
}

/// French channels known under several names: providers write "TF1 SERIES
/// FILMS", "TF1 Séries-Films" or even "TF1 SIERES". All are one channel,
/// under its proper name.
nonisolated enum FrenchChannels {
    struct Known: Sendable {
        var key: String
        var name: String
        /// Today's logo: providers often still serve the one from ten years ago.
        var logo: String?
    }

    /// The proper name first, then what providers write instead.
    private static let names: [[String]] = [
        ["TF1"],
        ["France 2"],
        ["France 3"],
        ["France 4"],
        ["France 5"],
        ["M6"],
        ["Arte"],
        // Two channels sharing number 8 by the hour: the number goes to LCP.
        ["LCP", "LCP-Public Sénat", "LCP/PS", "LCP/Public Sénat", "LaChaîne Parlementaire", "LCP Public Sénat", "LCP Assemblée Nationale", "La Chaîne Parlementaire", "La Chains Parlementaire"],
        ["Public Sénat", "Public Senat"],
        ["W9"],
        ["TMC"],
        ["TFX"],
        ["Gulli"],
        ["BFM TV", "BFMTV"],
        ["CNews", "C News"],
        ["LCI"],
        ["franceinfo", "France Info", "France Info TV", "franceinfo:"],
        ["CStar", "C Star"],
        ["T18"],
        ["Novo19", "Novo 19"],
        ["TF1 Séries Films", "TF1 Séries", "TF1 Series Films", "TF1 Series / Films", "TF1 Séries-Films", "TF1 Sieres"],
        ["L’Équipe", "L'Equipe", "LEquipe", "L Equipe", "La Chaîne L'Équipe", "Lachaîne L'Equipe", "L'Equipe TV", "La chaine l’Équipe"],
        ["6ter"],
        ["RMC Story"],
        ["RMC Découverte", "RMC Decouverte"],
        ["RMC Life", "Chérie 25", "Cherie 25"],
        ["Paris Première", "Paris Premiere"],
        ["Canal+", "Canal Plus"],
        ["NRJ 12", "NRJ12"],
        ["C8"],
        ["RTL 9", "RTL9"],
        ["TV5 Monde", "TV5Monde"],
        ["France 24"],
        ["BFM Business"],
        ["13e Rue", "13eme Rue", "13ème Rue", "Treizième Rue"],
        ["Série Club", "Serie Club", "SerieClub"],
        ["Téva", "Teva"],
        ["TV Breizh"],
        ["E! Entertainment", "E Entertainment", "E!", "E HD France"],
        ["SyFy", "Sci Fi", "SciFi"],
        ["Canal+ Sport", "Canal Sport"],
        ["Canal+ Sport 360", "Canal Sport 360"],
        ["Canal+ Foot", "Canal Foot"],
        ["Canal+ Cinéma(s)", "Canal+ Cinema", "Canal+ Cinéma", "Canal+ Cinemas", "Canal Cinema"],
        ["Canal+ Box Office", "Canal+ Box-Office", "Canal Box Office"],
        ["Canal+ Grand Écran", "Canal+ Grand Ecran", "Canal Grand Ecran"],
        ["Canal+ Docs", "Canal+ Doc", "Canal Docs"],
        ["Canal+ Kids", "Canal Kids"],
        ["Ciné+ Frisson", "Cine+ Frisson", "Cine Frisson"],
        ["Ciné+ Émotion", "Cine+ Emotion", "Cine Emotion"],
        ["Ciné+ Family", "Cine+ Family", "Cine Family"],
        ["Ciné+ Festival", "Cine+ Festival", "Cine Festival"],
        ["Ciné+ Classic", "Cine+ Classic", "Cine Classic"],
        ["beIN Sports 1", "beIN Sport 1"],
        ["beIN Sports 2", "beIN Sport 2"],
        ["beIN Sports 3", "beIN Sport 3"],
        ["RMC Sport 1"],
        ["RMC Sport 2", "RMC Sport Live 2"],
        ["Eurosport 1"],
        ["Eurosport 2"],
        ["Ligue 1+", "Ligue 1 Plus"],
        ["Infosport+", "Infosport"],
        ["Equidia"],
        ["Automoto", "Auto Moto"],
        ["Planète+", "Planete+", "Planete"],
        ["Planète+ Crime", "Planete+ Crime", "Planete Crime"],
        ["Planète+ Aventure", "Planete+ Aventure", "Planete Aventure"],
        ["Ushuaïa TV", "Ushuaia TV", "Ushuaia"],
        ["Histoire TV", "Histoire"],
        ["Toute l’Histoire", "Toute l'Histoire"],
        ["Discovery Channel", "Discovery"],
        ["Discovery Investigation", "Investigation Discovery"],
        ["National Geographic", "Nat Geo", "National Geographic Channel"],
        ["Nat Geo Wild", "National Geographic Wild"],
        ["Science & Vie TV", "Science et Vie TV", "Science Vie TV"],
        ["Animaux"],
        ["Chasse & Pêche", "Chasse et Peche", "Chasse Peche"],
        ["Trek"],
        ["Museum TV", "Museum"],
        ["KTO"],
        ["Mezzo"],
        ["MCM"],
        ["MTV"],
        ["M6 Music"],
        ["NRJ Hits", "NRJ Hits TV"],
        ["Melody"],
        ["Comédie+", "Comedie+", "Comedie"],
        ["Comedy Central"],
        ["Warner TV"],
        ["Paramount Network"],
        ["TCM Cinéma", "TCM Cinema", "TCM"],
        ["Action"],
        ["Polar+", "Polar"],
        ["OCS", "OCS Max"],
        ["AB1"],
        ["AB3"],
        ["Mangas"],
        ["Game One"],
        ["Tiji"],
        ["Canal J"],
        ["Piwi+", "Piwi"],
        ["Télétoon+", "Teletoon+", "Teletoon"],
        ["Télétoon+1", "Teletoon+1", "Teletoon +1"],
        ["Boomerang"],
        ["Cartoon Network"],
        ["Cartoonito", "Boing"],
        ["Nickelodeon", "Nickelodeon France"],
        ["Nickelodeon Junior", "Nick Jr"],
        ["Nickelodeon+1", "Nickelodeon +1"],
        ["Nicktoons"],
        ["Disney Channel"],
        ["Disney Junior"],
        ["Dreamworks", "DreamWorks Channel"],
        ["Gulli"],
        ["Euronews"],
        ["i24 News", "i24NEWS", "I24"],
        ["La Chaîne Météo", "La Chaine Meteo"],
        ["Bfm Lyon", "BFM Lyon Métropole"],
        ["BFM Paris", "BFM Paris Île-de-France", "BFM Paris Ile-de-France"],
        ["BFM Marseille Provence", "BFM Marseille"],
        ["BFM Grand Lille"],
        ["BFM Grand Littoral"],
        ["BFM Alsace"],
        ["BFM Normandie"],
        ["BFM Grands Reportages"],
        ["RMC Talk Info"],
        ["Olympia TV"],
        ["Fashion TV"],
        ["Luxe TV"],
        ["Men’s Up TV", "Men's Up", "Mens Up TV"],
        ["Top Santé TV", "Top Sante TV"],
        ["Maison & Travaux TV", "Maison Travaux TV"],
        ["Le Figaro TV", "Figaro TV", "Figaro TV IDF"],
        ["Trace Urban"],
        ["Trace Caribbean"],
        ["Golf+", "Golf+ HD", "Golf Plus"],
    ]

    /// Today's logos, from the tv-logo/tv-logos collection, by proper name.
    private static let logos: [String: String] = [
        "TF1": "tf1",
        "France 2": "france-2",
        "France 3": "france-3",
        "France 4": "france-4",
        "France 5": "france-5",
        "M6": "m6",
        "Arte": "arte",
        "LCP": "lcp",
        "Public Sénat": "public-senat",
        "W9": "w9",
        "TMC": "tmc",
        "TFX": "tfx",
        "Gulli": "gulli",
        "BFM TV": "bfm-tv",
        "CNews": "c-news",
        "LCI": "lci",
        "franceinfo": "franceinfo",
        "CStar": "c-star",
        "T18": "t18",
        "Novo19": "novo19",
        "TF1 Séries Films": "tf1-series-films",
        "L’Équipe": "lequipe",
        "6ter": "6ter",
        "RMC Story": "rmc-story",
        "RMC Découverte": "rmc-decouverte",
        "RMC Life": "rmc-life",
        "Paris Première": "paris-premiere",
        "Canal+": "canal-plus",
        "NRJ 12": "nrj-12",
        "C8": "c8",
        "RTL 9": "rtl9",
        "TV5 Monde": "tv5-monde",
        "France 24": "france-24",
        "BFM Business": "bfm-business",
        "Série Club": "serie-club",
        "Téva": "teva",
        "TV Breizh": "tv-breizh",
        "Canal+ Sport": "canal-plus-sport",
        "Canal+ Sport 360": "canal-plus-sport-360",
        "Canal+ Foot": "canal-plus-foot",
        "Canal+ Cinéma(s)": "canal-plus-cinemas",
        "Canal+ Box Office": "canal-plus-box-office",
        "Canal+ Grand Écran": "canal-plus-grand-ecran",
        "Canal+ Docs": "canal-plus-docs",
        "Canal+ Kids": "canal-plus-kids",
        "Ciné+ Frisson": "cine-plus-frisson",
        "Ciné+ Émotion": "cine-plus-emotion",
        "Ciné+ Family": "cine-plus-family",
        "Ciné+ Festival": "cine-plus-festival",
        "Ciné+ Classic": "cine-plus-classic",
        "beIN Sports 1": "bein-sports-1-french",
        "beIN Sports 2": "bein-sports-2-french",
        "beIN Sports 3": "bein-sports-3-french",
        "RMC Sport 1": "rmc-sport-1",
        "RMC Sport 2": "rmc-sport-2",
        "Eurosport 1": "eurosport-1",
        "Eurosport 2": "eurosport-2",
        "Ligue 1+": "ligue-1plus",
        "Infosport+": "infosport-plus",
        "Equidia": "equidia",
        "Automoto": "automoto-la-chaine",
        "Planète+": "planete-plus",
        "Planète+ Crime": "planete-plus-crime",
        "Planète+ Aventure": "planete-plus-aventure",
        "Ushuaïa TV": "ushuaia-tv",
        "Histoire TV": "histoire-tv",
        "Toute l’Histoire": "toute-lhistoire",
        "Science & Vie TV": "science-and-vie-tv",
        "Animaux": "animaux",
        "Chasse & Pêche": "chasse-et-peche",
        "Trek": "trek",
        "KTO": "kto",
        "Mezzo": "mezzo",
        "MCM": "mcm",
        "M6 Music": "m6-music",
        "NRJ Hits": "nrj-hits",
        "Melody": "melody",
        "Comédie+": "comedie-plus",
        "Warner TV": "warner-tv",
        "TCM Cinéma": "tcm-cinema",
        "Action": "action",
        "Polar+": "polar-plus",
        "OCS": "ocs",
        "AB1": "ab1",
        "AB3": "ab3",
        "Mangas": "mangas",
        "Game One": "game-one",
        "Tiji": "tiji",
        "Canal J": "canal-j",
        "Piwi+": "piwi-plus",
        "Télétoon+": "teletoon-plus",
        "Télétoon+1": "teletoon-plus-1",
        "Boomerang": "boomerang",
        "Cartoon Network": "cartoon-network",
        "Nickelodeon": "nickelodeon",
        "Nickelodeon Junior": "nickelodeon-junior",
        "Disney Channel": "disney-channel",
        "Disney Junior": "disney-jr",
        "La Chaîne Météo": "la-chaine-meteo",
        "Bfm Lyon": "bfm-lyon",
        "BFM Paris": "bfm-paris",
        "BFM Grand Lille": "bfm-grand-lille",
        "BFM Grand Littoral": "bfm-grand-littoral",
        "Olympia TV": "olympia-tv",
        "Trace Urban": "trace-urban",
        "Trace Caribbean": "trace-caribbean",
        "Golf+": "golf-plus",
    ]

    private static let table: [String: Known] = {
        var table: [String: Known] = [:]
        for synonyms in names {
            let logo = logos[synonyms[0]].map { "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/france/\($0)-fr.png" }
            let known = Known(key: ChannelName("|FR| " + synonyms[0]).key, name: synonyms[0], logo: logo)
            for name in synonyms {
                let alias = ChannelName("|FR| " + name).key
                if table[alias] == nil { table[alias] = known }
            }
        }
        return table
    }()

    /// The channel a French key stands for, under its proper name.
    static func known(_ key: String) -> Known? { table[key] }

    // MARK: Line-ups

    /// TNT since the renumbering of 6 June 2025; SFR's guide of March 2026;
    /// Orange's of October 2026, Bouygues' of October 2026 and Free's of
    /// 25 August 2026, as each prints them.
    static let plans: [String: [(Int, String)]] = {
        guard let url = Bundle.main.url(forResource: "Numbering", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let raw = try? JSONSerialization.jsonObject(with: data) as? [String: [[Any]]] else { return [:] }
        return raw.mapValues { rows in rows.compactMap { row in
            guard row.count == 2, let n = row[0] as? Int, let name = row[1] as? String else { return nil }
            return (n, name)
        } }
    }()
}
