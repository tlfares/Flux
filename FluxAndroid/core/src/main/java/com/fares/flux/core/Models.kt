package com.fares.flux.core

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Where an account's channels come from, as the Apple apps write it. */
enum class SourceKind(val raw: String) {
    /** An Xtream Codes panel: a server and a login. */
    XTREAM("xtream"),
    /** An M3U playlist, at the address in `server`. */
    PLAYLIST("playlist"),
    /** The channels anyone can watch for free, built into the app. */
    FREE("free");

    companion object {
        fun of(raw: String?) = entries.firstOrNull { it.raw == raw } ?: XTREAM
    }
}

/** What a provider gives: a server and a login. `kind` is missing on
 *  accounts from before playlists: those are Xtream. */
@Serializable
data class Credentials(val server: String, val username: String, val password: String, val kind: String? = null) {
    val source: SourceKind get() = SourceKind.of(kind)

    /** `http://host:port`, whatever was typed: a missing scheme, a trailing
     *  slash or a pasted `player_api.php` link. A playlist's whole address. */
    val baseURL: String
        get() {
            var s = server.trim()
            if (source != SourceKind.XTREAM) {
                if (source == SourceKind.PLAYLIST && "://" !in s) s = "http://$s"
                return s
            }
            val api = s.indexOf("/player_api.php")
            if (api >= 0) s = s.substring(0, api)
            while (s.endsWith("/")) s = s.dropLast(1)
            val lower = s.lowercase()
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) s = "http://$s"
            return s
        }

    companion object {
        /** The free channels: nothing to type. */
        val free = Credentials("flux://free", "", "", SourceKind.FREE.raw)

        /** An M3U link from an Xtream panel (`get.php?username=…&password=…`)
         *  is better used as the panel itself: films, series and the guide come with it. */
        fun playlist(link: String): Credentials {
            val trimmed = link.trim()
            val uri = runCatching { android.net.Uri.parse(trimmed) }.getOrNull()
            val path = uri?.path
            if (uri != null && path != null && path.endsWith("/get.php") && uri.scheme != null && uri.host != null) {
                val user = runCatching { uri.getQueryParameter("username") }.getOrNull()?.nonEmpty
                val pass = runCatching { uri.getQueryParameter("password") }.getOrNull()?.nonEmpty
                if (user != null && pass != null) {
                    val port = if (uri.port > 0) ":${uri.port}" else ""
                    return Credentials("${uri.scheme}://${uri.host}$port${path.removeSuffix("/get.php")}", user, pass)
                }
            }
            return Credentials(trimmed, "", "", SourceKind.PLAYLIST.raw)
        }
    }
}

// Providers send ids and numbers as strings or ints, sometimes null, from
// one row to the next: every field is read leniently.

fun JsonObject.str(key: String): String? = when (val e = this[key]) {
    null, is JsonNull -> null
    is JsonPrimitive -> e.content
    else -> null
}

fun JsonObject.int(key: String): Int? {
    val s = str(key) ?: return null
    return s.toIntOrNull() ?: s.toDoubleOrNull()?.toInt()
}

fun JsonObject.double(key: String): Double? = str(key)?.toDoubleOrNull()

fun JsonObject.strings(key: String): List<String> = when (val e = this[key]) {
    is JsonArray -> e.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content }
    else -> str(key)?.takeIf { it.isNotEmpty() }?.let { listOf(it) } ?: emptyList()
}

val JsonElement.obj: JsonObject? get() = this as? JsonObject

/** Empty and whitespace-only strings as null. */
val String.nonEmpty: String? get() = trim().ifEmpty { null }

enum class MediaKind(val raw: String) { LIVE("live"), MOVIE("movie"), SERIES("series") }

@Serializable
data class Category(val id: String, val name: String) {
    companion object {
        fun from(o: JsonObject) = Category(o.str("category_id") ?: "", o.str("category_name")?.nonEmpty ?: "—")
    }
}

@Serializable
data class LiveChannel(
    val id: Int,
    val name: String,
    val number: Int? = null,
    val icon: String? = null,
    val categoryID: String? = null,
    val epgID: String? = null,
    /** Where it plays from, for playlists and the free channels; Xtream
     *  streams play from the panel. Either a web address or what
     *  `StreamResolver` turns into one ("francetv:…"). */
    val url: String? = null,
) {
    companion object {
        fun from(o: JsonObject): LiveChannel? {
            val id = o.int("stream_id") ?: return null
            return LiveChannel(id, o.str("name")?.nonEmpty ?: tr("Chaîne $id", "Channel $id"), o.int("num"), o.str("stream_icon")?.nonEmpty,
                o.str("category_id"), o.str("epg_channel_id")?.nonEmpty)
        }
    }
}

@Serializable
data class Movie(
    val id: Int,
    val name: String,
    val poster: String? = null,
    val categoryID: String? = null,
    val rating: Double? = null,
    val container: String = "mp4",
    val added: Int? = null,
    /** Where it plays from, for playlists. */
    val url: String? = null,
) {
    companion object {
        fun from(o: JsonObject): Movie? {
            val id = o.int("stream_id") ?: return null
            return Movie(id, o.str("name")?.nonEmpty ?: tr("Film $id", "Movie $id"), o.str("stream_icon")?.nonEmpty, o.str("category_id"),
                o.double("rating")?.takeIf { it > 0 }, o.str("container_extension")?.nonEmpty ?: "mp4", o.int("added"))
        }
    }
}

@Serializable
data class Series(
    val id: Int,
    val name: String,
    val cover: String? = null,
    val categoryID: String? = null,
    val rating: Double? = null,
    val year: String? = null,
) {
    companion object {
        fun from(o: JsonObject): Series? {
            val id = o.int("series_id") ?: return null
            return Series(id, o.str("name")?.nonEmpty ?: tr("Série $id", "Series $id"), o.str("cover")?.nonEmpty, o.str("category_id"),
                o.double("rating")?.takeIf { it > 0 }, o.str("releaseDate")?.nonEmpty?.take(4))
        }
    }
}

data class AccountInfo(val authenticated: Boolean, val status: String?, val expiration: Long?) {
    companion object {
        /** A playlist or the free channels: nothing expires. */
        val unlimited = AccountInfo(true, null, null)

        fun from(root: JsonObject): AccountInfo? {
            val u = root["user_info"]?.obj ?: return null
            return AccountInfo(u.int("auth") == 1, u.str("status"), u.double("exp_date")?.toLong())
        }
    }
}

data class MovieDetails(
    val plot: String? = null,
    val genre: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val duration: String? = null,
    val year: String? = null,
    val backdrop: String? = null,
    val poster: String? = null,
) {
    companion object {
        fun from(root: JsonObject): MovieDetails {
            val c = root["info"]?.obj ?: return MovieDetails()
            return MovieDetails(
                plot = (c.str("plot") ?: c.str("description"))?.nonEmpty,
                genre = c.str("genre")?.nonEmpty,
                cast = (c.str("cast") ?: c.str("actors"))?.nonEmpty,
                director = c.str("director")?.nonEmpty,
                duration = c.str("duration")?.nonEmpty,
                year = (c.str("releasedate") ?: c.str("release_date") ?: c.str("year"))?.nonEmpty?.take(4),
                backdrop = c.strings("backdrop_path").firstOrNull()?.nonEmpty,
                poster = (c.str("movie_image") ?: c.str("cover_big"))?.nonEmpty,
            )
        }
    }
}

data class Episode(
    val id: String,
    val number: Int,
    val season: Int,
    val title: String,
    val container: String,
    val image: String?,
    val plot: String?,
    val duration: String?,
    /** Where it plays from, for playlists. */
    val url: String? = null,
) {
    companion object {
        fun from(o: JsonObject, seasonKey: Int = 0): Episode? {
            val id = o.str("id") ?: return null
            val number = o.int("episode_num") ?: 0
            val info = o["info"]?.obj
            return Episode(id, number, (o.int("season") ?: 0).takeIf { it != 0 } ?: seasonKey,
                o.str("title")?.nonEmpty ?: tr("Épisode $number", "Episode $number"), o.str("container_extension")?.nonEmpty ?: "mp4",
                info?.str("movie_image")?.nonEmpty, info?.str("plot")?.nonEmpty, info?.str("duration")?.nonEmpty)
        }
    }
}

data class SeriesDetails(
    val plot: String?,
    val genre: String?,
    val cast: String?,
    val year: String?,
    val backdrop: String?,
    /** Season number → its episodes, in order. */
    val seasons: Map<Int, List<Episode>>,
) {
    companion object {
        fun from(root: JsonObject): SeriesDetails {
            val c = root["info"]?.obj
            // A dictionary keyed by season on most panels, an array of arrays on some.
            val all = mutableListOf<Episode>()
            when (val episodes = root["episodes"]) {
                is JsonObject -> for ((key, list) in episodes) {
                    (list as? JsonArray)?.forEach { e -> e.obj?.let { Episode.from(it, key.toIntOrNull() ?: 0) }?.let(all::add) }
                }
                is JsonArray -> episodes.forEach { inner ->
                    (inner as? JsonArray)?.forEach { e -> e.obj?.let { Episode.from(it) }?.let(all::add) }
                }
                else -> {}
            }
            return SeriesDetails(
                c?.str("plot")?.nonEmpty, c?.str("genre")?.nonEmpty, c?.str("cast")?.nonEmpty,
                c?.str("releaseDate")?.nonEmpty?.take(4), c?.strings("backdrop_path")?.firstOrNull()?.nonEmpty,
                all.groupBy { it.season }.mapValues { (_, v) -> v.sortedBy { it.number } },
            )
        }
    }
}

/** A programme, its times in seconds since 1970. */
@Serializable
data class Programme(
    val title: String,
    val summary: String?,
    val start: Long,
    val end: Long,
    /** A still of the programme: only the open guide has them. */
    val image: String? = null,
) {
    val isOn: Boolean get() = (System.currentTimeMillis() / 1000).let { it in start..end }
    val timeRange: String get() = "${clock(start)} – ${clock(end)}"

    companion object {
        fun from(o: JsonObject): Programme {
            // Titles and descriptions come base64-encoded.
            fun text(key: String): String? {
                val raw = o.str(key) ?: ""
                val decoded = runCatching { String(Base64.decode(raw, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
                    ?.takeUnless { '\uFFFD' in it }
                return (decoded ?: raw).nonEmpty
            }
            return Programme(text("title") ?: tr("Programme", "Program"), text("description"),
                o.double("start_timestamp")?.toLong() ?: 0, o.double("stop_timestamp")?.toLong() ?: 0)
        }

        private val clockFormat = ThreadLocal.withInitial { SimpleDateFormat("HH:mm", Locale.FRANCE) }
        fun clock(seconds: Long): String = clockFormat.get()!!.format(Date(seconds * 1000))
    }
}

fun nowSeconds(): Long = System.currentTimeMillis() / 1000

/** Names as panels write them, "|FR| TF1 ᵁᴴᴰ" or "FR: TF1", shown without
 *  the country tag every row repeats. */
object DisplayName {
    fun clean(name: String): String {
        // Also the stars some panels lead every category with.
        val rest = ChannelName.splitCountry(name).second.trim { it == ' ' || it in "✪★☆●•◉▶►" }
        return rest.ifEmpty { name }
    }
}

/** What leads a category in the category panel: its flag when the list
 *  mixes countries, else the first letters of its name on a colour drawn
 *  from the name, the same each time. */
object CategoryBadge {
    /** Flags tell categories apart only when there's more than one country. */
    fun usesFlags(categories: List<Category>): Boolean {
        val seen = HashSet<String>()
        for (c in categories) {
            flagCountry(c.name)?.let { seen.add(it) }
            if (seen.size > 1) return true
        }
        return false
    }

    /** "AR" is Arabic to panels, not Argentina: no flag for it. */
    private fun flagCountry(name: String): String? =
        ChannelName.categoryCountry(name).takeIf { it.length == 2 && it != "AR" }

    fun flag(category: Category): String? = flagCountry(category.name)?.let { FreeChannels.flag(it) }?.ifEmpty { null }

    /** The name as the panel shows it: without its tag, nor the flag the badge already shows. */
    fun title(category: Category, flagged: Boolean): String {
        val clean = DisplayName.clean(category.name)
        return if (flagged) DisplayName.clean(clean) else clean
    }

    /** As iOS's: the first letter of the first two words, "Sport FHD" →
     *  "SF", "Documentaires" → "D". */
    fun initials(name: String): String {
        val words = name.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        return words.take(2).joinToString("") { it.take(1) }.uppercase().ifEmpty { "#" }
    }

    /** A hue, 0 to 360, from the name: iOS's (FNV-1a over its UTF-8), so a
     *  category has the same colour everywhere. */
    fun hue(name: String): Float {
        var hash = 1469598103934665603uL
        for (byte in name.encodeToByteArray()) hash = (hash xor byte.toUByte().toULong()) * 1099511628211uL
        return (hash % 360uL).toFloat()
    }
}
