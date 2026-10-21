package com.fares.flux.core

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class XtreamException(message: String, val transient: Boolean = false) : Exception(message)

/** In French, and about the server rather than about Android. */
val Throwable.readableMessage: String
    get() = when (this) {
        is XtreamException -> message ?: ""
        is UnknownHostException -> "Serveur introuvable. Vérifie l’adresse."
        is ConnectException -> "Le serveur refuse la connexion. Vérifie le port."
        is SocketTimeoutException -> "Le serveur met trop de temps à répondre."
        is SSLException -> "Connexion sécurisée impossible. Essaie en http://."
        is kotlinx.serialization.SerializationException, is IllegalArgumentException -> "Le serveur n’a pas répondu comme un serveur Xtream."
        is IOException -> "Erreur réseau."
        else -> message ?: "Erreur."
    }

/** The Xtream Codes `player_api.php`, and where each stream plays from. */
class XtreamClient(val credentials: Credentials) {
    companion object {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        // Many panels reject requests without a player-like agent.
        const val USER_AGENT = "Flux/1.0 (Android TV)"
    }

    private fun apiURL(action: String?, extra: Map<String, String> = emptyMap()): URL {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        val query = buildList {
            add("username=${enc(credentials.username)}")
            add("password=${enc(credentials.password)}")
            if (action != null) add("action=${enc(action)}")
            extra.forEach { (k, v) -> add("$k=${enc(v)}") }
        }.joinToString("&")
        val url = runCatching { URL(credentials.baseURL + "/player_api.php?" + query) }.getOrNull()
        if (url == null || url.host.isNullOrEmpty()) throw XtreamException("L’adresse du serveur n’est pas valide.")
        return url
    }

    private suspend fun fetch(action: String?, extra: Map<String, String> = emptyMap()): JsonElement = withContext(Dispatchers.IO) {
        val url = apiURL(action, extra)
        var attempt = 0
        while (true) {
            try {
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000
                connection.setRequestProperty("User-Agent", USER_AGENT)
                try {
                    val code = connection.responseCode
                    if (code == 401 || code == 403) throw XtreamException("Identifiants refusés par le serveur.")
                    if (code !in 200..299) throw XtreamException("Le serveur a répondu $code.", transient = code >= 500 || code == 429)
                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                    return@withContext json.parseToJsonElement(text)
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                // Panels behind Cloudflare answer 5xx to bursts, and drop
                // connections: a moment later the same request goes through.
                attempt++
                val transient = (e as? XtreamException)?.transient ?: (e is SocketTimeoutException || e is ConnectException ||
                    (e is IOException && e !is UnknownHostException && e !is SSLException))
                if (attempt >= 3 || !transient) throw e
                delay(600L * attempt)
            }
        }
        @Suppress("UNREACHABLE_CODE") throw IllegalStateException()
    }

    /** A big list read one row at a time: as a whole, a VOD catalogue
     *  (100 MB+ of text) is more than a Fire TV gives an app. */
    private suspend fun <T> stream(action: String, read: (JsonObject) -> T?): List<T> = withContext(Dispatchers.IO) {
        val url = apiURL(action)
        var attempt = 0
        while (true) {
            try {
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 30_000
                connection.readTimeout = 120_000
                connection.setRequestProperty("User-Agent", USER_AGENT)
                try {
                    val code = connection.responseCode
                    if (code == 401 || code == 403) throw XtreamException("Identifiants refusés par le serveur.")
                    if (code !in 200..299) throw XtreamException("Le serveur a répondu $code.", transient = code >= 500 || code == 429)
                    val items = ArrayList<T>()
                    android.util.JsonReader(connection.inputStream.bufferedReader()).use { reader ->
                        reader.isLenient = true
                        if (reader.peek() != android.util.JsonToken.BEGIN_ARRAY) { reader.skipValue(); return@use }
                        reader.beginArray()
                        while (reader.hasNext()) {
                            val element = readElement(reader)
                            (element as? JsonObject)?.let { o -> runCatching { read(o) }.getOrNull()?.let(items::add) }
                        }
                    }
                    return@withContext items
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                attempt++
                val transient = (e as? XtreamException)?.transient ?: (e is SocketTimeoutException || e is ConnectException ||
                    (e is IOException && e !is UnknownHostException && e !is SSLException))
                if (attempt >= 3 || !transient) throw e
                delay(600L * attempt)
            }
        }
        @Suppress("UNREACHABLE_CODE") throw IllegalStateException()
    }

    private fun readElement(reader: android.util.JsonReader): JsonElement = when (reader.peek()) {
        android.util.JsonToken.BEGIN_OBJECT -> {
            val map = LinkedHashMap<String, JsonElement>()
            reader.beginObject()
            while (reader.hasNext()) map[reader.nextName()] = readElement(reader)
            reader.endObject()
            JsonObject(map)
        }
        android.util.JsonToken.BEGIN_ARRAY -> {
            val list = ArrayList<JsonElement>()
            reader.beginArray()
            while (reader.hasNext()) list.add(readElement(reader))
            reader.endArray()
            JsonArray(list)
        }
        android.util.JsonToken.NULL -> { reader.nextNull(); kotlinx.serialization.json.JsonNull }
        android.util.JsonToken.BOOLEAN -> kotlinx.serialization.json.JsonPrimitive(reader.nextBoolean())
        android.util.JsonToken.NUMBER -> kotlinx.serialization.json.JsonPrimitive(reader.nextString())
        else -> kotlinx.serialization.json.JsonPrimitive(reader.nextString())
    }

    private fun <T> list(element: JsonElement, read: (JsonObject) -> T?): List<T> =
        (element as? JsonArray)?.mapNotNull { e -> (e as? JsonObject)?.let { runCatching { read(it) }.getOrNull() } } ?: emptyList()

    /** A playlist or the free channels rather than a panel: the same calls,
     *  answered from the playlist. */
    val isPanel: Boolean get() = credentials.source == SourceKind.XTREAM

    private suspend fun playlist(maxAge: Long = 120_000) = PlaylistStore.playlist(credentials, maxAge)

    suspend fun account(): AccountInfo {
        if (!isPanel) { playlist(0); return AccountInfo.unlimited }
        val info = (fetch(null) as? JsonObject)?.let { AccountInfo.from(it) }
            ?: throw XtreamException("Le serveur n’a pas répondu comme un serveur Xtream.")
        if (!info.authenticated) throw XtreamException("Identifiants refusés par le serveur.")
        return info
    }

    suspend fun categories(kind: MediaKind): List<Category> {
        if (!isPanel) return playlist().categories(kind)
        val action = when (kind) {
            MediaKind.LIVE -> "get_live_categories"
            MediaKind.MOVIE -> "get_vod_categories"
            MediaKind.SERIES -> "get_series_categories"
        }
        return list(fetch(action)) { Category.from(it) }
    }

    suspend fun channels() = if (!isPanel) playlist().live else stream("get_live_streams") { LiveChannel.from(it) }
    suspend fun movies() = if (!isPanel) playlist().movies else stream("get_vod_streams") { Movie.from(it) }
    suspend fun series() = if (!isPanel) playlist().series else stream("get_series") { Series.from(it) }

    suspend fun movieDetails(id: Int): MovieDetails {
        if (!isPanel) return MovieDetails()
        return (fetch("get_vod_info", mapOf("vod_id" to id.toString())) as? JsonObject)?.let { MovieDetails.from(it) } ?: MovieDetails()
    }

    suspend fun seriesDetails(id: Int): SeriesDetails {
        if (!isPanel) return SeriesDetails(null, null, null, null, null, playlist(Long.MAX_VALUE).episodes[id] ?: emptyMap())
        val root = fetch("get_series_info", mapOf("series_id" to id.toString())) as? JsonObject
            ?: throw XtreamException("Le serveur n’a pas répondu comme un serveur Xtream.")
        return SeriesDetails.from(root)
    }

    suspend fun shortEPG(channelID: Int, limit: Int = 2): List<Programme> {
        if (!isPanel) {
            // Pluto TV has its own guide; the TNT's comes from the open guide.
            val link = PlaylistStore.channel(channelID, credentials)?.url ?: return emptyList()
            return if (link.startsWith("pluto:")) PlutoTV.programmes(link.removePrefix("pluto:")) else emptyList()
        }
        val root = fetch("get_short_epg", mapOf("stream_id" to channelID.toString(), "limit" to limit.toString())) as? JsonObject
        return root?.get("epg_listings")?.let { list(it) { o -> Programme.from(o) } } ?: emptyList()
    }

    // Streams

    private fun streamURL(path: String, file: String): String =
        "${credentials.baseURL}/$path/${Uri.encode(credentials.username)}/${Uri.encode(credentials.password)}/$file"

    /** HLS, as on the Apple TV: the same streams behave the same. A
     *  playlist's own address, looked up first for the free channels. */
    suspend fun liveURL(channel: LiveChannel): String? =
        channel.url?.let { StreamResolver.resolve(it) } ?: streamURL("live", "${channel.id}.m3u8").takeIf { channel.url == null }
    fun movieURL(movie: Movie) = movie.url ?: streamURL("movie", "${movie.id}.${movie.container}")
    fun episodeURL(episode: Episode) = episode.url ?: streamURL("series", "${episode.id}.${episode.container}")
}
