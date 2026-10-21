package com.fares.flux.core

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.BufferedReader
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** An M3U playlist read into what the screens show: live channels, films
 *  and series, each under its `group-title`. Panels' own M3U links put films
 *  under /movie/ and episodes under /series/; elsewhere a file extension
 *  marks a film, and "S01 E02" in its name an episode. */
class Playlist(
    val live: List<LiveChannel> = emptyList(),
    val liveCategories: List<Category> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val movieCategories: List<Category> = emptyList(),
    val series: List<Series> = emptyList(),
    val seriesCategories: List<Category> = emptyList(),
    /** Series id → season → its episodes, in order. */
    val episodes: Map<Int, Map<Int, List<Episode>>> = emptyMap(),
) {
    val isEmpty get() = live.isEmpty() && movies.isEmpty() && series.isEmpty()

    fun categories(kind: MediaKind) = when (kind) {
        MediaKind.LIVE -> liveCategories
        MediaKind.MOVIE -> movieCategories
        MediaKind.SERIES -> seriesCategories
    }

    private class Entry(val name: String, val url: String, val attributes: Map<String, String>, val group: String?)

    companion object {
        /** An id that stays the same from one download to the next: from the
         *  address, which is what identifies a stream in a playlist. */
        fun stableID(text: String): Int {
            var hash = 1469598103934665603L
            for (b in text.toByteArray()) hash = (hash xor (b.toLong() and 0xff)) * 1099511628211L
            return (hash and 0x7FFFFFFF).toInt()
        }

        private val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "m4v", "wmv", "flv", "webm", "mpg", "mpeg")
        private val episodePattern = Regex("""(?i)^(.*?)[\s._\-]*S(\d{1,2})[\s._\-]*E(\d{1,4})(.*)$""")

        /** Read a line at a time: a provider's playlist can be 100 MB. */
        fun parse(reader: BufferedReader): Playlist {
            val entries = ArrayList<Entry>()
            var pending: Pair<String, Map<String, String>>? = null
            var pendingGroup: String? = null
            reader.lineSequence().forEach { raw ->
                val line = raw.trim()
                when {
                    line.isEmpty() || line.startsWith("#EXTM3U") -> {}
                    line.startsWith("#EXTINF:") -> pending = info(line.substring(8)).let { (a, n) -> n to a }
                    line.startsWith("#EXTGRP:") -> pendingGroup = line.substring(8).nonEmpty
                    line.startsWith("#") -> {}
                    else -> {
                        val attributes = pending?.second ?: emptyMap()
                        val name = pending?.first?.nonEmpty ?: attributes["tvg-name"]?.nonEmpty
                            ?: line.substringAfterLast('/').substringBefore('?').substringBeforeLast('.').nonEmpty ?: line
                        entries.add(Entry(name, line, attributes, attributes["group-title"]?.nonEmpty ?: pendingGroup))
                        pending = null
                        pendingGroup = null
                    }
                }
            }
            return build(entries)
        }

        /** `-1 tvg-id="TF1.fr" group-title="TNT, HD",TF1`: the attributes, and
         *  the name after the first comma outside quotes. */
        private fun info(s: String): Pair<Map<String, String>, String> {
            var inQuotes = false
            for (i in s.indices) {
                val c = s[i]
                if (c == '"') inQuotes = !inQuotes else if (c == ',' && !inQuotes) return attributes(s.substring(0, i)) to s.substring(i + 1).trim()
            }
            return attributes(s) to ""
        }

        private fun attributes(s: String): Map<String, String> {
            val result = HashMap<String, String>()
            var i = 0
            while (i < s.length) {
                val eq = s.indexOf('=', i).takeIf { it >= 0 } ?: break
                val key = s.substring(i, eq).trim().substringAfterLast(' ').lowercase()
                var j = eq + 1
                if (j >= s.length) break
                val value: String
                if (s[j] == '"') {
                    j++
                    val close = s.indexOf('"', j).takeIf { it >= 0 } ?: s.length
                    value = s.substring(j, close)
                    i = close + 1
                } else {
                    val end = s.indexOf(' ', j).takeIf { it >= 0 } ?: s.length
                    value = s.substring(j, end)
                    i = end
                }
                if (key.isNotEmpty()) result[key] = value
            }
            return result
        }

        private enum class Kind { LIVE, MOVIE, EPISODE }

        private fun extension(url: String) = runCatching { URL(url).path }.getOrDefault(url).substringAfterLast('/').substringAfterLast('.', "").lowercase()

        private fun kind(entry: Entry): Kind {
            val path = runCatching { URL(entry.url).path.lowercase() }.getOrDefault(entry.url.lowercase())
            if ("/movie/" in path) return Kind.MOVIE
            if ("/series/" in path) return Kind.EPISODE
            if (extension(entry.url) in videoExtensions) return if (episodePattern.find(entry.name) == null) Kind.MOVIE else Kind.EPISODE
            return Kind.LIVE
        }

        /** "6ter" with `tvg-id="6ter.fr"` or `tvg-country="FR"` is the French
         *  one: tagged so, it gets the French numbers, logo and guide. */
        private fun countryTagged(entry: Entry): String {
            if (ChannelName.splitCountry(entry.name).first.isNotEmpty()) return entry.name
            val fromID = entry.attributes["tvg-id"]?.substringBefore('@')?.substringAfterLast('.', "")
            val country = entry.attributes["tvg-country"]?.substringBefore(';') ?: fromID
            if (country == null || country.length != 2 || !country.all { it.isLetter() }) return entry.name
            return "|${country.uppercase()}| ${entry.name}"
        }

        private fun build(entries: List<Entry>): Playlist {
            val used = HashSet<Int>()
            fun uniqueID(seed: String): Int {
                var id = stableID(seed)
                while (!used.add(id)) id++
                return id
            }
            val names = mapOf("live" to ArrayList<String>(), "movie" to ArrayList(), "series" to ArrayList())
            val known = HashSet<String>()
            fun category(section: String, group: String?): String {
                val name = group ?: "Sans catégorie"
                val id = "$section|$name"
                if (known.add(id)) names.getValue(section).add(name)
                return id
            }
            val live = ArrayList<LiveChannel>()
            val movies = ArrayList<Movie>()
            val series = ArrayList<Series>()
            val episodes = HashMap<Int, HashMap<Int, ArrayList<Episode>>>()
            val seriesByKey = HashMap<String, Int>()
            for (entry in entries) {
                val logo = entry.attributes["tvg-logo"]?.nonEmpty
                when (kind(entry)) {
                    Kind.LIVE -> live.add(LiveChannel(uniqueID(entry.url), countryTagged(entry), entry.attributes["tvg-chno"]?.toIntOrNull(), logo,
                        category("live", entry.group), entry.attributes["tvg-id"]?.nonEmpty, entry.url))
                    Kind.MOVIE -> movies.add(Movie(uniqueID(entry.url), entry.name, logo, category("movie", entry.group),
                        container = extension(entry.url).ifEmpty { "mp4" }, url = entry.url))
                    Kind.EPISODE -> {
                        val match = episodePattern.find(entry.name)
                        val show = match?.groupValues?.get(1)?.trim(' ', '.', '-', '_', '|')?.nonEmpty ?: entry.name
                        val key = ChannelName.foldText(show) + "|" + (entry.group ?: "")
                        val seriesID = seriesByKey.getOrPut(key) {
                            uniqueID("series|$key").also { series.add(Series(it, show, logo, category("series", entry.group))) }
                        }
                        val season = match?.groupValues?.get(2)?.toIntOrNull() ?: 1
                        val seasons = episodes.getOrPut(seriesID) { HashMap() }
                        val list = seasons.getOrPut(season) { ArrayList() }
                        val number = match?.groupValues?.get(3)?.toIntOrNull() ?: (list.size + 1)
                        val title = match?.groupValues?.get(4)?.trim(' ', '.', '-', '_', '|')?.nonEmpty ?: "Épisode $number"
                        list.add(Episode(uniqueID(entry.url).toString(), number, season, title, extension(entry.url).ifEmpty { "mp4" },
                            logo, null, null, entry.url))
                    }
                }
            }
            fun list(section: String) = names.getValue(section).map { Category("$section|$it", it) }
            return Playlist(live, list("live"), movies, list("movie"), series, list("series"),
                episodes.mapValues { (_, s) -> s.mapValues { (_, e) -> e.sortedBy { it.number } } })
        }
    }
}

/** Playlists as they were last read, so the catalogue's six requests (three
 *  categories, three lists) download the file once; the file itself is kept
 *  on disk for opening a series after a restart. */
object PlaylistStore {
    private lateinit var context: Context
    private val lock = Mutex()
    private val cache = HashMap<String, Pair<Playlist, Long>>()
    private val loading = HashMap<String, CompletableDeferred<Playlist>>()

    fun init(context: Context) { this.context = context.applicationContext }

    private fun file(key: String) = File(context.cacheDir, "playlist-${Integer.toUnsignedString(Playlist.stableID(key), 36)}.m3u")

    /** The playlist, downloaded again when what's here is older than
     *  `maxAge` (ms); `Long.MAX_VALUE` takes the copy on disk before the network. */
    suspend fun playlist(credentials: Credentials, maxAge: Long = 120_000): Playlist {
        val key = credentials.baseURL
        val waiting: CompletableDeferred<Playlist>
        val owner: Boolean
        lock.withLock {
            cache[key]?.let { (p, date) -> if (System.currentTimeMillis() - date < maxAge) return p }
            if (maxAge == Long.MAX_VALUE && credentials.source == SourceKind.PLAYLIST) {
                val saved = file(key)
                if (saved.exists()) {
                    val p = withContext(Dispatchers.IO) { saved.bufferedReader().use { Playlist.parse(it) } }
                    if (!p.isEmpty) { cache[key] = p to 0L; return p }
                }
            }
            val existing = loading[key]
            owner = existing == null
            waiting = existing ?: CompletableDeferred<Playlist>().also { loading[key] = it }
        }
        if (!owner) return waiting.await()
        try {
            val p = download(credentials)
            lock.withLock { cache[key] = p to System.currentTimeMillis(); loading.remove(key) }
            waiting.complete(p)
            return p
        } catch (e: Throwable) {
            lock.withLock { loading.remove(key) }
            waiting.completeExceptionally(e)
            throw e
        }
    }

    /** The channel with this id, in the playlist as last read. */
    suspend fun channel(id: Int, credentials: Credentials): LiveChannel? =
        runCatching { playlist(credentials, Long.MAX_VALUE) }.getOrNull()?.live?.firstOrNull { it.id == id }

    private suspend fun download(credentials: Credentials): Playlist = withContext(Dispatchers.IO) {
        if (credentials.source == SourceKind.FREE) return@withContext FreeChannels.playlist(context)
        val url = runCatching { URL(credentials.baseURL) }.getOrNull()?.takeIf { !it.host.isNullOrEmpty() }
            ?: throw XtreamException("L’adresse de la liste n’est pas valide.")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", XtreamClient.USER_AGENT)
        val target = file(credentials.baseURL)
        val tmp = File(target.path + ".tmp")
        try {
            val code = connection.responseCode
            if (code == 401 || code == 403) throw XtreamException("Accès refusé à cette liste.")
            if (code !in 200..299) throw XtreamException("Le serveur a répondu $code.")
            connection.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        } finally {
            connection.disconnect()
        }
        val p = tmp.bufferedReader().use { Playlist.parse(it) }
        if (p.isEmpty) {
            val head = tmp.bufferedReader().use { r -> CharArray(64).let { String(it, 0, maxOf(0, r.read(it))) } }
            tmp.delete()
            throw XtreamException(if ("#EXT" in head) "Cette liste ne contient aucune chaîne." else "Ce lien ne donne pas une liste M3U.")
        }
        tmp.renameTo(target)
        p
    }
}

/** The channels anyone can watch without a subscription, from the
 *  broadcasters' own players: the free TNT channels France TV, Arte, BFM,
 *  RMC and L'Équipe stream, news, the France 3 regions, then Pluto TV's
 *  French line-up. The list comes with the app (FreeChannels.json, the same
 *  file as on the Apple apps); streams whose address changes are looked up
 *  when they start (`StreamResolver`). */
object FreeChannels {
    /** The same file, hosted: streams that move are fixed there, without a
     *  new version of the app. Asked for at most every few hours; the copy
     *  that came with the app stands in until one arrives, and whenever it's
     *  older or unreadable. */
    const val REMOTE = "https://gist.githubusercontent.com/tlfares/b08fcc5378a0b04d14d0f15dfea7cf77/raw/FreeChannels.json"
    private const val REMOTE_MAX_AGE = 6 * 3600 * 1000L

    private class ListFile(val version: Int, val categories: List<Category>, val channels: List<LiveChannel>)

    /** A list worth using: read whole, and not emptied by mistake. */
    private fun read(text: String): ListFile? = runCatching {
        val root = XtreamClient.json.parseToJsonElement(text) as JsonObject
        val categories = (root["categories"] as JsonArray).mapNotNull { c ->
            (c as? JsonObject)?.let { Category(it.str("id") ?: "", it.str("name") ?: "") }
        }
        val channels = (root["channels"] as JsonArray).mapNotNull { c ->
            val o = c as? JsonObject ?: return@mapNotNull null
            val url = o.str("url") ?: return@mapNotNull null
            LiveChannel(Playlist.stableID(url), o.str("name") ?: return@mapNotNull null, icon = o.str("logo"), categoryID = o.str("category"), url = url)
        }
        ListFile(root.int("version") ?: 0, categories, channels)
    }.getOrNull()?.takeIf { it.channels.size >= 10 }

    /** The hosted list when it's at least as recent as the bundled one. */
    private fun current(context: Context): ListFile? {
        val cached = File(context.cacheDir, "free-channels.json")
        if (!cached.exists() || System.currentTimeMillis() - cached.lastModified() > REMOTE_MAX_AGE) {
            StreamResolver.get(REMOTE)?.takeIf { read(it) != null }?.let { text ->
                val tmp = File(cached.path + ".tmp")
                tmp.writeText(text)
                tmp.renameTo(cached)
            }
        }
        val bundled = runCatching { context.assets.open("FreeChannels.json").bufferedReader().use { it.readText() } }.getOrNull()?.let(::read)
        val hosted = runCatching { cached.readText() }.getOrNull()?.let(::read)
        return if (hosted != null && hosted.version >= (bundled?.version ?: 0)) hosted else bundled
    }

    /** The listed channels alone, to stand in for a provider's. */
    suspend fun listed(context: Context): List<LiveChannel> = withContext(Dispatchers.IO) { current(context)?.channels ?: emptyList() }

    /** The listed channels, then Pluto TV's when it answers. */
    suspend fun playlist(context: Context): Playlist = coroutineScope {
        val listed = async(Dispatchers.IO) { current(context) }
        val pluto = async { runCatching { PlutoTV.lineup() }.getOrNull() }
        val file = listed.await()
        val categories = ArrayList(file?.categories?.map(::french) ?: emptyList())
        val live = ArrayList(file?.channels ?: emptyList())
        pluto.await()?.let { (c, l) ->
            categories += c.map(::french)
            live += l
        }
        Playlist(live, categories)
    }

    /** Categories read as French: their channels are matched against the
     *  French line-up (numbers, logos, the open guide). */
    private fun french(category: Category) = Category(category.id, "|FR| " + category.name)
}

/** Turns the free channels' lookups into an address the player plays:
 *  `francetv:<id>` (a signed address, valid for a few hours),
 *  `dailymotion:<id>` and `pluto:<id>`. Anything else is an address already. */
object StreamResolver {
    private const val BROWSER = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15"

    internal fun get(url: String, headers: Map<String, String> = emptyMap()): String? {
        val connection = runCatching { URL(url).openConnection() as HttpURLConnection }.getOrNull() ?: return null
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("User-Agent", BROWSER)
        headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
        return try {
            if (connection.responseCode != 200) null else connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    internal fun json(url: String, headers: Map<String, String> = emptyMap()): JsonElement? =
        get(url, headers)?.let { runCatching { XtreamClient.json.parseToJsonElement(it) }.getOrNull() }

    suspend fun resolve(link: String): String? = withContext(Dispatchers.IO) {
        val colon = link.indexOf(':')
        val id = link.substring(colon + 1)
        when (if (colon > 0) link.substring(0, colon) else "") {
            "francetv" -> franceTV(id)
            "dailymotion" -> dailymotion(id)
            "pluto" -> PlutoTV.streamURL(id)
            else -> link
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** The player's own two steps: the stream's address, then a signature for it. */
    private fun franceTV(id: String): String? {
        val video = (json("https://k7.ftven.fr/videos/$id?country_code=FR&domain=www.france.tv&device_type=desktop&browser=safari&os=macos&diffusion_mode=tunnel_first&offline=false")
            as? JsonObject)?.get("video") as? JsonObject ?: return null
        val stream = video.str("url") ?: return null
        val token = (video["token"] as? JsonObject)?.str("akamai") ?: (video["token"] as? JsonPrimitive)?.content ?: return stream
        val separator = if ('?' in token) "&" else "?"
        return (json("$token${separator}url=${enc(stream)}") as? JsonObject)?.str("url") ?: stream
    }

    /** Dailymotion's metadata has raw control characters a strict parser
     *  refuses: the address is read straight out of it. */
    private fun dailymotion(id: String): String? {
        val text = get("https://www.dailymotion.com/player/metadata/video/$id?embedder=${enc("https://www.dailymotion.com/video/$id")}&locale=fr&app=com.dailymotion.neon&client_type=webapp") ?: return null
        val auto = text.indexOf("\"auto\"").takeIf { it >= 0 } ?: return null
        return Regex(""""url"\s*:\s*"([^"]+)"""").find(text, auto)?.groupValues?.get(1)?.replace("\\/", "/")
    }
}

/** Pluto TV, free and supported by ads: its French channels, their guide,
 *  and a session to play them with, renewed when Pluto says so. */
object PlutoTV {
    private class Session(val token: String, val stitcher: String, val params: String, val expires: Long)

    private var session: Session? = null
    private val lock = Mutex()
    private val clientID = UUID.randomUUID().toString()

    private suspend fun current(): Session? = lock.withLock {
        session?.takeIf { it.expires > System.currentTimeMillis() }?.let { return it }
        val query = mapOf("appName" to "web", "appVersion" to "8.0.0", "deviceVersion" to "122.0.0", "deviceModel" to "web",
            "deviceMake" to "chrome", "deviceType" to "web", "clientID" to clientID, "clientModelNumber" to "1.0.0", "serverSideAds" to "false")
            .entries.joinToString("&") { "${it.key}=${it.value}" }
        val boot = withContext(Dispatchers.IO) { StreamResolver.json("https://boot.pluto.tv/v4/start?$query") } as? JsonObject ?: return null
        val token = boot.str("sessionToken") ?: return null
        val stitcher = (boot["servers"] as? JsonObject)?.str("stitcher") ?: return null
        val params = boot.str("stitcherParams") ?: return null
        val refresh = boot.double("refreshInSec") ?: 3600.0
        Session(token, stitcher, params, System.currentTimeMillis() + (minOf(refresh, 6 * 3600.0) - 60).toLong() * 1000).also { session = it }
    }

    private suspend fun get(path: String): JsonElement? {
        val session = current() ?: return null
        return withContext(Dispatchers.IO) {
            StreamResolver.json("https://service-channels.clusters.pluto.tv$path", mapOf("Authorization" to "Bearer ${session.token}"))
        }
    }

    /** The channels, each in the first category Pluto lists it under. */
    suspend fun lineup(): Pair<List<Category>, List<LiveChannel>> {
        val channels = (get("/v2/guide/channels?channelIds=&offset=0&limit=1000&sort=number%3Aasc") as? JsonObject)?.get("data") as? JsonArray
            ?: return emptyList<Category>() to emptyList()
        val groups = (get("/v2/guide/categories") as? JsonObject)?.get("data") as? JsonArray ?: JsonArray(emptyList())
        val categoryOf = HashMap<String, String>()
        val categories = ArrayList<Category>()
        for (g in groups) {
            val o = g as? JsonObject ?: continue
            val id = o.str("id") ?: continue
            val name = o.str("name") ?: continue
            // "Nouveau sur Pluto TV", "Halloween sur Pluto TV": channels found again in their own category.
            if (name.contains("pluto", ignoreCase = true)) continue
            var used = false
            for (c in o.strings("channelIDs")) if (c !in categoryOf) { categoryOf[c] = "pluto|$id"; used = true }
            if (used) categories.add(Category("pluto|$id", "Pluto TV · $name"))
        }
        var usesFallback = false
        val list = channels.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val id = o.str("id") ?: return@mapNotNull null
            val name = o.str("name") ?: return@mapNotNull null
            val images = (o["images"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
            val logo = (images.firstOrNull { it.str("type") == "colorLogoPNG" } ?: images.firstOrNull())?.str("url")
            val category = categoryOf[id] ?: "pluto".also { usesFallback = true }
            LiveChannel(Playlist.stableID("pluto:$id"), name, icon = logo, categoryID = category, url = "pluto:$id")
        }
        return (if (usesFallback) categories + Category("pluto", "Pluto TV") else categories) to list
    }

    suspend fun streamURL(channel: String): String? {
        val s = current() ?: return null
        return "${s.stitcher}/stitch/hls/channel/$channel/master.m3u8?${s.params}&jwt=${s.token}&masterJWTPassthrough=true"
    }

    private fun isoFormat() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    /** What's on now and next, from Pluto's guide. */
    suspend fun programmes(channel: String): List<Programme> {
        val format = isoFormat()
        val start = format.format(java.util.Date(System.currentTimeMillis() - 3_600_000))
        val root = get("/v2/guide/timelines?start=$start&channelIds=$channel&duration=480") as? JsonObject ?: return emptyList()
        val timelines = ((root["data"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("timelines") as? JsonArray ?: return emptyList()
        return timelines.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val title = o.str("title") ?: return@mapNotNull null
            val from = o.str("start")?.let { runCatching { format.parse(it) }.getOrNull() } ?: return@mapNotNull null
            val to = o.str("stop")?.let { runCatching { format.parse(it) }.getOrNull() } ?: return@mapNotNull null
            val episode = o["episode"] as? JsonObject
            Programme(title, episode?.str("description")?.nonEmpty, from.time / 1000, to.time / 1000,
                (episode?.get("thumbnail") as? JsonObject)?.str("path"))
        }
    }
}
