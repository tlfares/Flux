@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.fares.flux.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File
import java.util.UUID

private val storeJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

/** Settings, under the Apple apps' keys: pairing sends them by those names. */
class Settings(context: Context, tv: Boolean = false) {
    val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var quality by mutableStateOf(QualityPreference.of(prefs.getString(QualityPreference.STORAGE_KEY, null)))
        private set
    var numbering by mutableStateOf(ChannelNumbering.of(prefs.getString(ChannelNumbering.STORAGE_KEY, null)))
        private set
    var showsShifted by mutableStateOf(prefs.getBoolean(GuideOptions.SHIFTED_KEY, false))
        private set
    var sharesFavorites by mutableStateOf(prefs.getBoolean(SHARED_FAVORITES_KEY, true))
        private set
    var prefersFree by mutableStateOf(prefs.getBoolean(GuideOptions.PREFERS_FREE_KEY, true))
        private set
    /** Subtitles shown: off unless turned on, whatever streams flag as default. */
    var subtitles by mutableStateOf(prefs.getBoolean(SUBTITLES_KEY, false))
        private set

    /** The Guide tab: on by default on the TV, off on phones (shown only where there's a guide). */
    var guideTab by mutableStateOf(prefs.getBoolean(GUIDE_TAB_KEY, tv))
        private set

    fun changeGuideTab(value: Boolean) { guideTab = value; prefs.edit().putBoolean(GUIDE_TAB_KEY, value).apply() }

    /** The Films and Séries tabs, when the account has some. */
    var moviesTab by mutableStateOf(prefs.getBoolean(MOVIES_TAB_KEY, true))
        private set
    var seriesTab by mutableStateOf(prefs.getBoolean(SERIES_TAB_KEY, true))
        private set

    fun changeMoviesTab(value: Boolean) { moviesTab = value; prefs.edit().putBoolean(MOVIES_TAB_KEY, value).apply() }
    fun changeSeriesTab(value: Boolean) { seriesTab = value; prefs.edit().putBoolean(SERIES_TAB_KEY, value).apply() }

    fun changeSubtitles(value: Boolean) { if (value != subtitles) { subtitles = value; prefs.edit().putBoolean(SUBTITLES_KEY, value).apply() } }

    /** The free channels of one country ("FR", "US", "UK") or of all ("all").
     *  Nothing saved, or the first version's "mine": the device's country. */
    var freeCountry by mutableStateOf(when (val saved = prefs.getString(GuideOptions.FREE_COUNTRIES_KEY, null)) {
        "all" -> FreeChannels.ALL
        in FreeChannels.choices -> saved!!
        else -> FreeChannels.deviceCountry
    })
        private set

    /** Direct on phones: rows, or tiles as on the TV ("list" / "cards", the iOS key). */
    var liveCards by mutableStateOf(prefs.getString(LIVE_LAYOUT_KEY, "list") == "cards")
        private set

    /** "light", "dark" or "system", the iOS key and values; dark unless
     *  changed, as on iOS. */
    var appearance by mutableStateOf(prefs.getString(APPEARANCE_KEY, null) ?: "dark")
        private set

    /** Who resolves server names: the network's, unless one of the public resolvers is picked. */
    var dns by mutableStateOf(DnsProvider.of(prefs.getString(DnsProvider.STORAGE_KEY, null)))
        private set

    fun changeDns(value: DnsProvider) { dns = value; Net.use(value); prefs.edit().putString(DnsProvider.STORAGE_KEY, value.raw).apply() }

    fun changeAppearance(value: String) { appearance = value; prefs.edit().putString(APPEARANCE_KEY, value).apply() }

    fun changeSharesFavorites(value: Boolean) { sharesFavorites = value; prefs.edit().putBoolean(SHARED_FAVORITES_KEY, value).apply() }

    fun changeLiveCards(value: Boolean) { liveCards = value; prefs.edit().putString(LIVE_LAYOUT_KEY, if (value) "cards" else "list").apply() }

    val guideOptions: GuideOptions get() = GuideOptions(showsShifted, numbering, prefersFree, freeCountry)

    fun changeFreeCountry(value: String) {
        freeCountry = value
        prefs.edit().putString(GuideOptions.FREE_COUNTRIES_KEY, if (value == FreeChannels.ALL) "all" else value).apply()
    }

    fun changePrefersFree(value: Boolean) { prefersFree = value; prefs.edit().putBoolean(GuideOptions.PREFERS_FREE_KEY, value).apply() }

    fun changeQuality(value: QualityPreference) { quality = value; prefs.edit().putString(QualityPreference.STORAGE_KEY, value.raw).apply() }
    fun changeNumbering(value: ChannelNumbering) { numbering = value; prefs.edit().putString(ChannelNumbering.STORAGE_KEY, value.raw).apply() }
    fun changeShowsShifted(value: Boolean) { showsShifted = value; prefs.edit().putBoolean(GuideOptions.SHIFTED_KEY, value).apply() }

    /** What goes with the account to another device, under the Apple apps' keys. */
    fun exported(): Map<String, String> = mapOf(
        QualityPreference.STORAGE_KEY to quality.raw,
        ChannelNumbering.STORAGE_KEY to numbering.raw,
        GuideOptions.SHIFTED_KEY to if (showsShifted) "1" else "0",
        GuideOptions.PREFERS_FREE_KEY to if (prefersFree) "1" else "0",
        SHARED_FAVORITES_KEY to if (sharesFavorites) "1" else "0",
    )

    /** What came with an account from the iPhone. */
    fun apply(settings: Map<String, String>) {
        fun bool(v: String) = v == "1" || v == "true"
        settings[QualityPreference.STORAGE_KEY]?.let { changeQuality(QualityPreference.of(it)) }
        settings[ChannelNumbering.STORAGE_KEY]?.let { changeNumbering(ChannelNumbering.of(it)) }
        settings[GuideOptions.SHIFTED_KEY]?.let { changeShowsShifted(bool(it)) }
        settings[GuideOptions.PREFERS_FREE_KEY]?.let { changePrefersFree(bool(it)) }
        settings[SHARED_FAVORITES_KEY]?.let { sharesFavorites = bool(it); prefs.edit().putBoolean(SHARED_FAVORITES_KEY, bool(it)).apply() }
    }

    companion object {
        const val SHARED_FAVORITES_KEY = "favorites.keep"
        const val LIVE_LAYOUT_KEY = "live.layout"
        const val APPEARANCE_KEY = "appAppearance"
        const val SUBTITLES_KEY = "player.subtitles"
        const val GUIDE_TAB_KEY = "guide.tab"
        const val MOVIES_TAB_KEY = "tabs.movies"
        const val SERIES_TAB_KEY = "tabs.series"
    }
}

/** A provider subscription, under a name the user gives it. The id is a
 *  UUID as the Apple apps write it, so a paired account is recognised. */
@Serializable
data class Account(val id: String = UUID.randomUUID().toString().uppercase(), val name: String, val credentials: Credentials) {
    val host: String get() = runCatching { java.net.URL(credentials.baseURL).host }.getOrNull() ?: credentials.server
    /** The free channels' account is saved under its French name: shown in the device's language. */
    val title: String
        get() = if (credentials.source == SourceKind.FREE && (name.isBlank() || name == FREE_NAME)) tr(FREE_NAME, "Free channels")
            else name.nonEmpty ?: host

    /** What it is, under its name: the login, or the kind of list. */
    val subtitle: String get() = when (credentials.source) {
        SourceKind.XTREAM -> "${credentials.username} · $host"
        SourceKind.PLAYLIST -> tr("Liste M3U · $host", "M3U playlist · $host")
        SourceKind.FREE -> tr("TNT, info et Pluto TV, sans abonnement", "Free TV and news, no subscription")
    }

    companion object {
        const val FREE_NAME = "Chaînes gratuites"
    }
}

/** The accounts, and the one in use. Logins live in encrypted storage. */
class AccountStore(context: Context) {
    private val secure: SharedPreferences = runCatching {
        EncryptedSharedPreferences.create("accounts", MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC), context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }.getOrElse { context.getSharedPreferences("accounts-plain", Context.MODE_PRIVATE) }

    var accounts by mutableStateOf(
        runCatching { storeJson.decodeFromString(ListSerializer(Account.serializer()), secure.getString("accounts", "[]")!!) }.getOrDefault(emptyList())
    )
        private set
    var activeID by mutableStateOf(secure.getString("active", null)?.takeIf { id -> accounts.any { it.id == id } } ?: accounts.firstOrNull()?.id)
        private set
    var info by mutableStateOf<AccountInfo?>(null)
        private set

    val active: Account? get() = accounts.firstOrNull { it.id == activeID }
    val client: XtreamClient? get() = active?.let { XtreamClient(it.credentials) }

    /** The free channels, added once: nothing to check. */
    fun addFree() {
        accounts.firstOrNull { it.credentials.source == SourceKind.FREE }?.let { select(it.id); return }
        val account = Account(name = Account.FREE_NAME, credentials = Credentials.free)
        accounts = accounts + account
        save()
        select(account.id)
        info = AccountInfo.unlimited
    }

    /** Checked against the server before it's kept; becomes the one in use. */
    suspend fun add(account: Account) {
        if (account.credentials.source == SourceKind.FREE) { addFree(); return }
        val info = XtreamClient(account.credentials).account()
        accounts = accounts + account
        save()
        this.info = info
        select(account.id)
    }

    fun select(id: String) {
        if (id != activeID) info = null
        activeID = id
        secure.edit().putString("active", id).apply()
    }

    fun remove(id: String) {
        accounts = accounts.filter { it.id != id }
        save()
        if (activeID == id) {
            info = null
            activeID = null
            accounts.firstOrNull()?.let { select(it.id) } ?: secure.edit().remove("active").apply()
        }
    }

    suspend fun refreshInfo() {
        val client = client ?: return
        val id = activeID
        val info = runCatching { client.account() }.getOrNull()
        if (id == activeID) this.info = info
    }

    private fun save() {
        secure.edit().putString("accounts", storeJson.encodeToString(ListSerializer(Account.serializer()), accounts)).apply()
    }
}

/** Something kept in favorites, by what it is rather than by the provider's id. */
@Serializable
data class Favorite(val kind: String, val key: String, val name: String, val image: String? = null) {
    val id: String get() = "$kind:$key"

    companion object {
        fun live(group: ChannelGroup) = Favorite("live", group.id, group.name, group.icon)
        fun movie(movie: Movie) = Favorite("movie", nameKey(movie.name), DisplayName.clean(movie.name), movie.poster)
        fun series(series: Series) = Favorite("series", nameKey(series.name), DisplayName.clean(series.name), series.cover)

        /** Folded and without the country tag. */
        fun nameKey(name: String) = ChannelName.foldText(DisplayName.clean(name))
    }
}

/** Seconds watched and total; the date in seconds since 2001, as the Apple
 *  apps encode it, so it travels as is. */
@Serializable
data class Progress(val position: Double, val duration: Double, val date: Double) {
    val fraction: Double get() = if (duration > 0) minOf(1.0, position / duration) else 0.0
    /** Started and not finished: what "Reprendre" offers. */
    val isResumable: Boolean get() = position > 30 && fraction < 0.95

    companion object {
        fun now() = System.currentTimeMillis() / 1000.0 - 978_307_200
    }
}

/** What another device takes along when the account is sent to it. */
@Serializable
data class LibrarySnapshot(
    val favorites: List<Favorite> = emptyList(),
    val progress: Map<String, Progress> = emptyMap(),
    val recentChannels: List<String> = emptyList(),
    val qualityChoices: Map<String, String> = emptyMap(),
)

/** Favorites, what was last watched and where each film stopped. */
class Library(context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    private var account: String? = null
    private var sharesFavorites = true

    var favorites by mutableStateOf<List<Favorite>>(emptyList())
        private set
    var progress by mutableStateOf<Map<String, Progress>>(emptyMap())
        private set
    var recentChannels by mutableStateOf<List<String>>(emptyList())
        private set
    var qualityChoices by mutableStateOf<Map<String, String>>(
        runCatching { storeJson.decodeFromString(MapSerializer(String.serializer(), String.serializer()), prefs.getString("qualityChoices", "{}")!!) }.getOrDefault(emptyMap())
    )
        private set

    private val favoritesKey get() = if (sharesFavorites) "favorites.v2" else "favorites.v2.${account ?: ""}"
    private val progressKey get() = "progress.${account ?: ""}"
    private val recentKey get() = "recentLive.${account ?: ""}"

    fun use(account: String?, sharesFavorites: Boolean) {
        if (account == this.account && sharesFavorites == this.sharesFavorites) return
        this.account = account
        this.sharesFavorites = sharesFavorites
        if (account == null) {
            favorites = emptyList(); progress = emptyMap(); recentChannels = emptyList()
            return
        }
        if (!sharesFavorites && !prefs.contains(favoritesKey)) prefs.getString("favorites.v2", null)?.let { prefs.edit().putString(favoritesKey, it).apply() }
        favorites = read(favoritesKey, ListSerializer(Favorite.serializer())) ?: emptyList()
        progress = read(progressKey, MapSerializer(String.serializer(), Progress.serializer())) ?: emptyMap()
        recentChannels = read(recentKey, ListSerializer(String.serializer())) ?: emptyList()
    }

    fun forget(account: String) {
        prefs.edit().remove("favorites.v2.$account").remove("progress.$account").remove("recentLive.$account").apply()
    }

    private fun <T> read(key: String, serializer: kotlinx.serialization.KSerializer<T>): T? =
        prefs.getString(key, null)?.let { runCatching { storeJson.decodeFromString(serializer, it) }.getOrNull() }

    private fun <T> write(key: String, serializer: kotlinx.serialization.KSerializer<T>, value: T) {
        prefs.edit().putString(key, storeJson.encodeToString(serializer, value)).apply()
    }

    // Favorites

    fun isFavorite(favorite: Favorite) = favorites.any { it.id == favorite.id }
    fun favorites(kind: MediaKind) = favorites.filter { it.kind == kind.raw }

    fun toggleFavorite(favorite: Favorite) {
        favorites = if (isFavorite(favorite)) favorites.filter { it.id != favorite.id } else favorites + favorite
        write(favoritesKey, ListSerializer(Favorite.serializer()), favorites)
    }

    // Progress

    fun saveProgress(key: String, position: Double, duration: Double) {
        if (!(duration > 0) || duration.isInfinite() || position.isNaN()) return
        var updated = progress + (key to Progress(position, duration, Progress.now()))
        if (updated.size > 200) updated = updated.entries.sortedByDescending { it.value.date }.take(200).associate { it.toPair() }
        progress = updated
        write(progressKey, MapSerializer(String.serializer(), Progress.serializer()), progress)
    }

    fun resumePosition(key: String): Double? = progress[key]?.takeIf { it.isResumable }?.position

    /** Forgets what was watched: recent channels and where each film stopped. */
    fun clearHistory() {
        progress = emptyMap()
        recentChannels = emptyList()
        prefs.edit().remove(progressKey).remove(recentKey).apply()
    }

    // Live

    fun played(group: ChannelGroup) {
        recentChannels = (listOf(group.id) + recentChannels.filter { it != group.id }).take(30)
        write(recentKey, ListSerializer(String.serializer()), recentChannels)
    }

    fun rememberQuality(label: String?, group: ChannelGroup) {
        if (qualityChoices[group.id] == label) return
        qualityChoices = if (label == null) qualityChoices - group.id else qualityChoices + (group.id to label)
        write("qualityChoices", MapSerializer(String.serializer(), String.serializer()), qualityChoices)
    }

    // Pairing

    /** Adds what came from the iPhone: favorites missing here go at the end,
     *  each film keeps the position watched last, recents come first. */
    fun merge(other: LibrarySnapshot) {
        favorites = favorites + other.favorites.filter { f -> favorites.none { it.id == f.id } }
        write(favoritesKey, ListSerializer(Favorite.serializer()), favorites)

        val merged = progress.toMutableMap()
        for ((key, p) in other.progress) if ((merged[key]?.date ?: Double.MIN_VALUE) < p.date) merged[key] = p
        progress = merged
        write(progressKey, MapSerializer(String.serializer(), Progress.serializer()), progress)

        recentChannels = (other.recentChannels + recentChannels.filter { it !in other.recentChannels }).take(30)
        write(recentKey, ListSerializer(String.serializer()), recentChannels)

        qualityChoices = qualityChoices + other.qualityChoices
        write("qualityChoices", MapSerializer(String.serializer(), String.serializer()), qualityChoices)
    }

    companion object {
        fun key(kind: MediaKind, id: Any) = "${kind.raw}:$id"
    }
}

/** The categories as the user arranged them, per account and section
 *  (live, films, series): an order, and those hidden from the bar. Ids the
 *  provider adds later follow the saved ones, in the provider's order. */
class CategoryLayouts(context: Context) {
    @Serializable
    data class Layout(val order: List<String> = emptyList(), val hidden: Set<String> = emptySet()) {
        val isEmpty: Boolean get() = order.isEmpty() && hidden.isEmpty()
    }

    private val prefs = context.getSharedPreferences("categories", Context.MODE_PRIVATE)
    /** Read once each. Not state itself, as it's filled while screens
     *  compose: `version` is what they observe, bumped by each edit. */
    private val layouts = HashMap<String, Layout>()
    private var version by androidx.compose.runtime.mutableIntStateOf(0)

    private fun key(account: String?, kind: MediaKind) = "${account ?: ""}|${kind.raw}"

    fun layout(account: String?, kind: MediaKind): Layout {
        version
        return layouts.getOrPut(key(account, kind)) {
            prefs.getString(key(account, kind), null)?.let { runCatching { storeJson.decodeFromString(Layout.serializer(), it) }.getOrNull() } ?: Layout()
        }
    }

    /** Every category, hidden ones too, in the user's order. */
    fun ordered(categories: List<Category>, account: String?, kind: MediaKind): List<Category> {
        val layout = layout(account, kind)
        if (layout.order.isEmpty()) return categories
        val byID = categories.associateBy { it.id }
        val saved = layout.order.mapNotNull { byID[it] }
        val known = layout.order.toHashSet()
        return saved + categories.filter { it.id !in known }
    }

    /** The categories the bar shows. */
    fun visible(categories: List<Category>, account: String?, kind: MediaKind): List<Category> {
        val hidden = layout(account, kind).hidden
        return ordered(categories, account, kind).let { list -> if (hidden.isEmpty()) list else list.filter { it.id !in hidden } }
    }

    fun save(account: String?, kind: MediaKind, layout: Layout) {
        val key = key(account, kind)
        layouts[key] = layout
        version++
        if (layout.isEmpty) prefs.edit().remove(key).apply()
        else prefs.edit().putString(key, storeJson.encodeToString(Layout.serializer(), layout)).apply()
    }

    /** The provider's order, everything shown. */
    fun reset(account: String?, kind: MediaKind) = save(account, kind, Layout())

    fun forget(account: String) {
        MediaKind.entries.forEach { kind -> layouts.remove(key(account, kind)); prefs.edit().remove(key(account, kind)).apply() }
        version++
    }
}

@Serializable
data class CatalogSnapshot(
    val live: CatalogSection<LiveChannel> = CatalogSection(),
    val movies: CatalogSection<Movie> = CatalogSection(),
    val series: CatalogSection<Series> = CatalogSection(),
    val date: Long = 0,
    val account: String = "",
    val isPartial: Boolean? = null,
)

/** Everything the provider offers, from the disk cache at once, then from
 *  the server when it's older than a few hours. */
class Catalog(private val context: Context, private val scope: CoroutineScope) {
    var live by mutableStateOf(CatalogSection<LiveChannel>()); private set
    var movies by mutableStateOf(CatalogSection<Movie>()); private set
    var series by mutableStateOf(CatalogSection<Series>()); private set
    var updated by mutableStateOf<Long?>(null); private set
    var isLoading by mutableStateOf(false); private set
    var isStarting by mutableStateOf(false); private set
    var isIndexing by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var guide by mutableStateOf(LiveGuide.empty); private set

    private var loadedAccount: String? = null
    private var snapshot = CatalogSnapshot()
    private var options = GuideOptions()
    private var indexing: Job? = null
    private val refreshing = mutableSetOf<String>()
    var optionsProvider: () -> GuideOptions = { GuideOptions() }

    val isEmpty get() = live.isEmpty && movies.isEmpty && series.isEmpty

    /** One file per account and section: the channels read back in a
     *  second, without waiting for 170 000 films. */
    private fun cacheFile(account: String, part: String): File {
        var hash = 1469598103934665603L // FNV-1a offset basis
        for (b in account.toByteArray()) hash = (hash xor (b.toLong() and 0xff)) * 1099511628211L
        return File(context.cacheDir, "catalog-${java.lang.Long.toUnsignedString(hash, 36)}-$part.json")
    }

    @Serializable
    private data class CacheMeta(val date: Long, val account: String, val isPartial: Boolean? = null)

    private fun <T> readPart(account: String, part: String, serializer: kotlinx.serialization.KSerializer<T>): T? =
        runCatching { cacheFile(account, part).inputStream().buffered().use { storeJson.decodeFromStream(serializer, it) } }.getOrNull()

    private fun <T> writePart(account: String, part: String, serializer: kotlinx.serialization.KSerializer<T>, value: T) {
        runCatching {
            // Streamed, as it's read: as one string it wouldn't fit.
            val file = cacheFile(account, part)
            val tmp = File(file.path + ".tmp")
            tmp.outputStream().buffered().use { storeJson.encodeToStream(serializer, value, it) }
            tmp.renameTo(file)
        }
    }

    private fun guideKey(account: String, options: GuideOptions) =
        "$account|${options.showsShifted}|${options.numbering.raw}|${options.prefersFree}|${freeChannels.size}|${options.freeCountry}|${FreeChannels.deviceCountry}|4"

    /** The free channels' streams, for a subscription's channels that have
     *  one; none for the free channels' own account. */
    private var freeChannels: List<LiveChannel> = emptyList()
    private var usesFreeChannels = false
    /** The free channels' own account: its countries are filtered when shown. */
    private var isFreeAccount = false

    private val liveSerializer = CatalogSection.serializer(LiveChannel.serializer())
    private val movieSerializer = CatalogSection.serializer(Movie.serializer())
    private val seriesSerializer = CatalogSection.serializer(Series.serializer())

    suspend fun start(client: XtreamClient) {
        val account = accountKey(client.credentials)
        if (loadedAccount == account) return
        loadedAccount = account
        usesFreeChannels = client.credentials.source != SourceKind.FREE
        isFreeAccount = !usesFreeChannels
        if (usesFreeChannels && freeChannels.isEmpty()) scope.launch {
            freeChannels = FreeChannels.listed(context)
            if (usesFreeChannels && options.prefersFree && freeChannels.isNotEmpty()) apply(snapshot)
        }
        error = null
        updateLoading()
        isStarting = true
        try {
            apply(CatalogSnapshot())
            // The channels as last sorted, on screen before anything is read or fetched.
            val key = guideKey(account, optionsProvider())
            withContext(Dispatchers.IO) { readPart(account, "guide", LiveGuide.Saved.serializer()) }
                ?.takeIf { it.options == key && loadedAccount == account }
                ?.let { guide = LiveGuide.restore(it); isStarting = false }
            val meta = withContext(Dispatchers.IO) { readPart(account, "meta", CacheMeta.serializer()) }
            if (meta != null && meta.account == account) {
                // Channels first, on screen at once; films and series after.
                var cached = CatalogSnapshot(date = meta.date, account = account, isPartial = meta.isPartial)
                    withContext(Dispatchers.IO) { readPart(account, "live", liveSerializer) }?.let { cached = cached.copy(live = it) }
                if (loadedAccount != account) return
                apply(cached)
                isStarting = false
                withContext(Dispatchers.IO) { readPart(account, "movies", movieSerializer) }?.let { cached = cached.copy(movies = it) }
                if (loadedAccount != account) return
                apply(cached)
                withContext(Dispatchers.IO) { readPart(account, "series", seriesSerializer) }?.let { cached = cached.copy(series = it) }
                if (loadedAccount != account) return
                apply(cached)
                // A playlist may have no films or series at all.
                val complete = cached.isPartial != true && (!client.isPanel || (!cached.live.isEmpty && !cached.movies.isEmpty && !cached.series.isEmpty))
                if (System.currentTimeMillis() - cached.date < MAX_AGE && complete) return
            }
        } finally {
            if (loadedAccount == account) isStarting = false
        }
        refresh(client)
    }

    suspend fun refresh(client: XtreamClient) {
        val account = accountKey(client.credentials)
        if (account in refreshing) return
        refreshing += account
        updateLoading()
        error = null
        try {
            var snapshot = if (account == loadedAccount) this.snapshot else CatalogSnapshot()
            snapshot = snapshot.copy(account = account, isPartial = true)
            var gotAny = false
            var failed = false
            fun publish(save: Boolean = false) {
                snapshot = snapshot.copy(date = System.currentTimeMillis())
                if (account == loadedAccount) apply(snapshot)
                if (!save) return
                val saved = snapshot
                // One write at a time, each section in its file.
                scope.launch(Dispatchers.IO) { cacheLock.withLock {
                    writePart(account, "live", liveSerializer, saved.live)
                    writePart(account, "movies", movieSerializer, saved.movies)
                    writePart(account, "series", seriesSerializer, saved.series)
                    writePart(account, "meta", CacheMeta.serializer(), CacheMeta(saved.date, account, saved.isPartial))
                } }
            }
            val (lc, mc, sc) = coroutineScope {
                val l = async { runCatching { client.categories(MediaKind.LIVE) }.getOrNull() }
                val m = async { runCatching { client.categories(MediaKind.MOVIE) }.getOrNull() }
                val s = async { runCatching { client.categories(MediaKind.SERIES) }.getOrNull() }
                Triple(l.await(), m.await(), s.await())
            }
            // One after the other, which panels behind Cloudflare take better than a
            // burst. Empty is a failure from a panel, not from a playlist, which
            // often has live channels only.
            runCatching { client.channels() }.getOrNull()?.takeIf { it.isNotEmpty() || !client.isPanel }?.let {
                snapshot = snapshot.copy(live = withContext(Dispatchers.Default) { CatalogSection.build(lc ?: emptyList(), it) { c -> c.categoryID } })
                gotAny = true; publish()
            } ?: run { failed = true }
            runCatching { client.movies() }.getOrNull()?.takeIf { it.isNotEmpty() || !client.isPanel }?.let {
                snapshot = snapshot.copy(movies = withContext(Dispatchers.Default) { CatalogSection.build(mc ?: emptyList(), it) { c -> c.categoryID } })
                gotAny = true; publish()
            } ?: run { failed = true }
            runCatching { client.series() }.getOrNull()?.takeIf { it.isNotEmpty() || !client.isPanel }?.let {
                snapshot = snapshot.copy(series = withContext(Dispatchers.Default) { CatalogSection.build(sc ?: emptyList(), it) { c -> c.categoryID } })
                gotAny = true; publish()
            } ?: run { failed = true }
            if (gotAny) {
                if (!failed) snapshot = snapshot.copy(isPartial = null)
                publish(save = true)
            } else {
                val message = runCatching { client.account(); tr("Le serveur a répondu 520.", "The server responded with error 520.") }.getOrElse { it.readableMessage }
                if (isEmpty && account == loadedAccount) error = message
            }
        } finally {
            refreshing -= account
            updateLoading()
        }
    }

    private fun updateLoading() { isLoading = loadedAccount?.let { it in refreshing } ?: false }

    fun forget(credentials: Credentials) {
        val account = accountKey(credentials)
        if (loadedAccount == account) { loadedAccount = null; apply(CatalogSnapshot()) }
        listOf("meta", "live", "movies", "series", "guide").forEach { cacheFile(account, it).delete() }
    }

    /** The settings that shape the guide changed: built again from what's there. */
    fun optionsChanged() {
        if (optionsProvider() != options) apply(snapshot)
    }

    private fun apply(snapshot: CatalogSnapshot) {
        this.snapshot = snapshot
        options = optionsProvider()
        val options = options
        // The free channels' countries as the setting has them; everything stays fetched.
        live = if (isFreeAccount) arrangedFree(snapshot.live, options.freeCountry) else snapshot.live
        movies = snapshot.movies
        series = snapshot.series
        updated = snapshot.date.takeIf { it > 0 }
        // Only the channels need building, and only when they changed: on a
        // Fire TV, building them again for each list that came ran it out of memory.
        val section = live
        val free = if (usesFreeChannels && options.prefersFree) freeChannels else emptyList()
        if (section === indexedLive && options == indexedOptions && free === indexedFree) return
        indexedLive = section
        indexedOptions = options
        indexedFree = free
        isIndexing = !section.isEmpty
        indexing?.cancel()
        indexing = scope.launch {
            val built = indexLock.withLock { withContext(Dispatchers.Default) { LiveGuide.build(section, options, free) } }
            // An empty section is a start or a switch: the saved channels stay until real ones come.
            if (built.isEmpty && !guide.isEmpty && section.isEmpty) { isIndexing = false; return@launch }
            guide = built
            loadedAccount?.let { account ->
                val saved = built.saved(guideKey(account, options))
                launch(Dispatchers.IO) { cacheLock.withLock { writePart(account, "guide", LiveGuide.Saved.serializer(), saved) } }
            }
            isIndexing = false
        }
    }

    /** Kept while nothing changes, so the guide isn't built again for nothing. */
    private fun arrangedFree(section: CatalogSection<LiveChannel>, choice: String): CatalogSection<LiveChannel> {
        val key = "$choice|${FreeChannels.deviceCountry}"
        arranged?.let { (from, k, result) -> if (from === section && k == key) return result }
        return FreeChannels.arranged(section, choice).also { arranged = Triple(section, key, it) }
    }

    private var arranged: Triple<CatalogSection<LiveChannel>, String, CatalogSection<LiveChannel>>? = null
    private var indexedLive: CatalogSection<LiveChannel>? = null
    private var indexedOptions: GuideOptions? = null
    private var indexedFree: List<LiveChannel>? = null
    private val indexLock = kotlinx.coroutines.sync.Mutex()
    private val cacheLock = kotlinx.coroutines.sync.Mutex()

    /** Favorites found again by name, folded only when asked for. */
    suspend fun favoriteMovies(keys: List<String>): List<Movie> = findByKey(movies.items, keys) { it.name }
    suspend fun favoriteSeries(keys: List<String>): List<Series> = findByKey(series.items, keys) { it.name }

    private suspend fun <T> findByKey(items: List<T>, keys: List<String>, name: (T) -> String): List<T> = withContext(Dispatchers.Default) {
        if (keys.isEmpty()) return@withContext emptyList()
        val wanted = keys.toHashSet()
        val found = HashMap<String, T>()
        for (item in items) {
            if (found.size == wanted.size) break
            val key = Favorite.nameKey(name(item))
            if (key in wanted && key !in found) found[key] = item
        }
        keys.mapNotNull { found[it] }
    }

    /** Names folded for matching ("Télé" finds "tele"). */
    suspend fun search(query: String, limit: Int = 40): SearchResults = withContext(Dispatchers.Default) {
        val words = ChannelName.foldText(query).split(" ").filter { it.isNotEmpty() }
        if (words.isEmpty()) return@withContext SearchResults()
        fun matches(name: String) = ChannelName.foldText(name).let { n -> words.all { it in n } }
        SearchResults(
            guide.groups.asSequence().filter { matches(it.name) }.take(limit).toList(),
            movies.items.asSequence().filter { matches(it.name) }.take(limit).toList(),
            series.items.asSequence().filter { matches(it.name) }.take(limit).toList(),
        )
    }

    companion object {
        private const val MAX_AGE = 6 * 3600 * 1000L
        /** The free channels' catalog saved before categories had countries
         *  would show nothing outside France until refreshed: theirs has a key of its own. */
        fun accountKey(credentials: Credentials) = credentials.baseURL + "|" + credentials.username +
            if (credentials.source == SourceKind.FREE) "|countries" else ""
    }
}

data class SearchResults(
    val live: List<ChannelGroup> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val series: List<Series> = emptyList(),
) {
    val isEmpty get() = live.isEmpty() && movies.isEmpty() && series.isEmpty()
}

/** What's on, fetched for the rows on screen only and kept a few minutes.
 *  At most four requests at once: panels throttle bursts. */
class EPG {
    @Serializable
    private data class Entry(val programmes: List<Programme>, val fetched: Long, val limit: Int)

    /** What's on, kept between launches: cards show it before anything is asked again. */
    fun save(file: File) {
        val now = nowSeconds()
        val kept = cache.mapValues { (_, e) -> e.copy(programmes = e.programmes.filter { it.end > now }) }.filterValues { it.programmes.isNotEmpty() }
        runCatching { file.writeText(storeJson.encodeToString(MapSerializer(Int.serializer(), Entry.serializer()), kept)) }
    }

    suspend fun restore(file: File) {
        val saved = withContext(Dispatchers.IO) {
            runCatching { storeJson.decodeFromString(MapSerializer(Int.serializer(), Entry.serializer()), file.readText()) }.getOrNull()
        } ?: return
        for ((id, e) in saved) if (cache[id] == null) cache[id] = e
    }

    private val cache = mutableStateMapOf<Int, Entry>()
    private val inFlight = mutableSetOf<Int>()
    private val gate = Semaphore(4)
    /** Channel key → programmes from the open guide (XML TV Fr). */
    private var open by mutableStateOf<Map<String, List<Programme>>>(emptyMap())

    /** Whether the guide can show programmes for the channel: the open
     *  guide (the French TNT) or Pluto TV's own. Panels' come with the panel. */
    fun covers(group: ChannelGroup): Boolean =
        open[group.id]?.isNotEmpty() == true || group.streams.any { it.channel.url?.startsWith("pluto:") == true }

    /** Whether a guide has anything to show: always with a panel, else
     *  when one channel at least is covered. Follows the open guide's arrival. */
    fun available(groups: List<ChannelGroup>, panel: Boolean): Boolean = panel || groups.any { covers(it) }

    fun now(channelID: Int): Programme? = nowSeconds().let { t -> cache[channelID]?.programmes?.firstOrNull { it.start <= t && it.end > t } }
    fun next(channelID: Int): Programme? = nowSeconds().let { t -> cache[channelID]?.programmes?.firstOrNull { it.start > t } }
    fun upcoming(channelID: Int): List<Programme> = nowSeconds().let { t -> cache[channelID]?.programmes?.filter { it.end > t } ?: emptyList() }

    /** The stream whose guide stands for the channel. */
    fun source(group: ChannelGroup): Int {
        val candidates = listOf(group.epgChannel.id) + group.variants.take(3).map { it.id }
        return candidates.firstOrNull { cache[it]?.programmes?.isNotEmpty() == true } ?: group.epgChannel.id
    }

    fun now(group: ChannelGroup) = now(source(group))

    /** Whether the guide was asked and had nothing. */
    fun isEmpty(channelID: Int): Boolean = cache[channelID]?.programmes?.isEmpty() == true

    fun useOpenGuide(guide: Map<String, List<Programme>>, groups: List<ChannelGroup>) {
        if (guide.isNotEmpty()) open = guide
        if (open.isEmpty()) return
        for (group in groups) {
            val programmes = open[group.id] ?: continue
            if (cache[group.epgChannel.id]?.programmes != programmes) cache[group.epgChannel.id] = Entry(programmes, System.currentTimeMillis(), Int.MAX_VALUE)
        }
    }

    /** Tries the channel's streams until one has a guide; the open guide first. */
    suspend fun load(group: ChannelGroup, client: XtreamClient, limit: Int = 3) {
        open[group.id]?.takeIf { p -> p.any { it.end > nowSeconds() } }?.let { programmes ->
            if (cache[group.epgChannel.id]?.programmes != programmes) cache[group.epgChannel.id] = Entry(programmes, System.currentTimeMillis(), Int.MAX_VALUE)
            return
        }
        val candidates = (listOf(group.epgChannel.id) + group.variants.take(3).map { it.id }).distinct()
        for (id in candidates) {
            load(id, client, limit)
            if (cache[id]?.programmes?.isEmpty() != true) return
        }
    }

    private suspend fun load(channelID: Int, client: XtreamClient, limit: Int) {
        val entry = cache[channelID]
        if (entry != null && entry.limit >= limit && System.currentTimeMillis() - entry.fetched < 300_000 &&
            (now(channelID) != null || entry.programmes.isEmpty())) return
        if (!inFlight.add(channelID)) return
        try {
            val programmes = gate.withPermit { runCatching { client.shortEPG(channelID, limit) }.getOrDefault(emptyList()) }
            cache[channelID] = Entry(untangled(programmes), System.currentTimeMillis(), limit)
        } finally {
            inFlight.remove(channelID)
        }
    }

    /** One at a time: panels often merge two guides a few minutes apart. */
    private fun untangled(programmes: List<Programme>): List<Programme> {
        val kept = mutableListOf<Programme>()
        for (p in programmes.sortedBy { it.start }) {
            if (p.end <= p.start) continue
            val last = kept.lastOrNull()
            if (last != null && p.start < last.end - 120) continue
            kept.add(p)
        }
        return kept
    }
}

/** Everything the screens share. */
class AppState(context: Context, tv: Boolean = false) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val settings = Settings(context, tv)
    val accounts = AccountStore(context)
    val library = Library(context)
    val catalog = Catalog(context, scope)
    val categoryLayouts = CategoryLayouts(context)
    val epg = EPG()

    init {
        FrenchChannels.load(context)
        PlaylistStore.init(context)
        Net.use(settings.dns)
        catalog.optionsProvider = { settings.guideOptions }
        library.use(accounts.activeID, settings.sharesFavorites)
        scope.launch { epg.restore(epgFile) }
    }

    val epgFile = File(context.cacheDir, "epg.json")

    /** The account has a guide at all: a panel, or channels the open guide or Pluto TV cover. */
    val hasGuide: Boolean get() = epg.available(catalog.guide.groups, accounts.active?.credentials?.source == SourceKind.XTREAM)

    /** The Guide tab shows: turned on, and something to put in it. */
    val showsGuideTab: Boolean get() = settings.guideTab && hasGuide

    /** Films and series: a panel always has the sections; a playlist only when
     *  it lists some (the free channels have none). */
    private val isPanel: Boolean get() = accounts.active?.credentials?.source == SourceKind.XTREAM
    val hasMovies: Boolean get() = isPanel || !catalog.movies.isEmpty
    val hasSeries: Boolean get() = isPanel || !catalog.series.isEmpty
    val showsMoviesTab: Boolean get() = settings.moviesTab && hasMovies
    val showsSeriesTab: Boolean get() = settings.seriesTab && hasSeries
}
