package com.fares.flux.mobile

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ListAlt

import androidx.compose.animation.*
import androidx.compose.ui.layout.ContentScale
import com.fares.flux.core.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.ui.draw.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.interaction.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.flux.core.Account
import com.fares.flux.core.ChannelNumbering
import com.fares.flux.core.Credentials
import com.fares.flux.core.DisplayName
import com.fares.flux.core.Episode
import com.fares.flux.core.Favorite
import com.fares.flux.core.Library
import com.fares.flux.core.MediaKind
import com.fares.flux.core.Movie
import com.fares.flux.core.MovieDetails
import com.fares.flux.core.PairingReceiver
import com.fares.flux.core.QualityPreference
import com.fares.flux.core.SearchResults
import com.fares.flux.core.Series
import com.fares.flux.core.SeriesDetails
import com.fares.flux.core.SourceKind
import com.fares.flux.core.readableMessage
import com.fares.flux.core.receive
import com.fares.flux.core.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where a poster leads. */
sealed interface Route {
    data class MovieRoute(val movie: Movie) : Route
    data class SeriesRoute(val series: Series) : Route
}

val LocalOpenRoute = staticCompositionLocalOf<(Route) -> Unit> { {} }

@Composable
fun ScreenTitle(title: String) = LargeTitle(title)

// Direct

@Composable
fun LiveScreen() {
    val app = LocalApp.current
    val player = LocalPlayer.current
    var selection by rememberSaveable { mutableStateOf(Categories.ALL) }
    val cards = app.settings.liveCards
    val channels = app.channels(selection)
    val state = rememberLazyGridState()
    val title = tr("Direct", "Live")
    Box(Modifier.fillMaxSize()) {
        // As iOS's: the title, then the chips, scroll away with the channels.
        LazyVerticalGrid(
            GridCells.Adaptive(if (cards) 160.dp else 300.dp), Modifier.fillMaxSize(), state,
            contentPadding = listPadding(top = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(if (cards) 16.dp else 8.dp),
        ) {
            item(key = "title", span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.bleed(16.dp)) { LargeTitle(title) } }
            if (app.catalog.guide.isEmpty) {
                item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth().height(480.dp)) { LoadingOrEmpty(tr("Aucune chaîne", "No channels")) } }
                return@LazyVerticalGrid
            }
            item(key = "chips", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.bleed(16.dp)) { CategoryBar(MediaKind.LIVE, app.catalog.live.categories, recent = true, selection, { app.catalog.guide.count(it) }) { selection = it } }
            }
            if (channels.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().height(420.dp), contentAlignment = Alignment.Center) {
                        Text(when (selection) {
                            Categories.FAVORITES -> tr("Aucun favori. Maintiens une chaîne pour l’ajouter.", "No favorites yet. Press and hold a channel to add it.")
                            Categories.RECENT -> tr("Les chaînes regardées apparaîtront ici.", "Channels you watch will appear here.")
                            else -> tr("Catégorie vide", "Nothing in this category")
                        }, color = Secondary, style = IOS.subheadline)
                    }
                }
            }
            items(channels, key = { it.id }) { group ->
                // Zaps within the category; through every channel from Récents.
                val play: (com.fares.flux.core.ChannelVariant?) -> Unit = { v ->
                    app.accounts.client?.let { player.watch(group, if (selection == Categories.RECENT) app.catalog.guide.groups else channels, it, v) }
                }
                if (cards) ChannelCard(group, player.channel?.id == group.id, play) else ChannelRow(group, player.channel?.id == group.id, play)
            }
        }
        TopEdge(title, state.firstVisibleItemIndex > 0)
    }
}

// Films and series

/** iOS's ContentUnavailableView: a symbol, a title, a line under it. */
@Composable
fun ContentUnavailable(icon: ImageVector, title: String, description: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(52.dp), tint = Secondary)
        Spacer(Modifier.height(14.dp))
        Text(title, style = IOS.title2, textAlign = TextAlign.Center)
        description?.let { Spacer(Modifier.height(6.dp)); Text(it, style = IOS.callout, color = Secondary, textAlign = TextAlign.Center) }
    }
}

/** A poster grid under category chips, as iOS's PosterBrowser: the title
 *  and chips scroll away with the posters. */
@Composable
private fun <T> PosterBrowser(
    title: String, empty: String, emptyIcon: ImageVector, kind: MediaKind, section: com.fares.flux.core.CatalogSection<*>, items: List<T>,
    selection: String, onSelect: (String) -> Unit, key: (T) -> Any, name: (T) -> String, image: (T) -> String?,
    progress: (T) -> Double? = { null }, favorite: (T) -> Favorite, route: (T) -> Route,
) {
    val app = LocalApp.current
    val open = LocalOpenRoute.current
    val state = rememberLazyGridState()
    @Composable fun Card(item: T) = Poster(name(item), image(item), progress(item), app.library.isFavorite(favorite(item)),
        onLongClick = { app.library.toggleFavorite(favorite(item)) }) { open(route(item)) }
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            GridCells.Adaptive(104.dp), Modifier.fillMaxSize(), state, contentPadding = listPadding(top = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "title", span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.bleed(16.dp)) { LargeTitle(title) } }
            if (section.isEmpty) {
                item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth().height(480.dp)) { LoadingOrEmpty(empty) } }
                return@LazyVerticalGrid
            }
            item(key = "chips", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.bleed(16.dp).padding(bottom = 4.dp)) { CategoryBar(kind, section.categories, recent = kind == MediaKind.MOVIE, selection, { section.index[it]?.size ?: 0 }, onSelect) }
            }
            if (items.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val favorites = selection == Categories.FAVORITES
                    ContentUnavailable(if (favorites) Icons.Outlined.StarOutline else emptyIcon, if (favorites) tr("Aucun favori", "No favorites") else tr("Rien ici", "Nothing here"),
                        if (favorites) tr("Maintiens une affiche pour l’ajouter.", "Press and hold a poster to add it.") else null, Modifier.padding(top = 140.dp))
                }
                return@LazyVerticalGrid
            }
            items(items, key = key) { Card(it) }
        }
        TopEdge(title, state.firstVisibleItemIndex > 0)
    }
}

@Composable
fun MoviesScreen() {
    val app = LocalApp.current
    var selection by rememberSaveable { mutableStateOf(Categories.ALL) }
    val movies = when (selection) {
        Categories.FAVORITES -> produceState(emptyList<Movie>(), app.library.favorites, app.catalog.movies) {
            value = app.catalog.favoriteMovies(app.library.favorites(MediaKind.MOVIE).map { it.key })
        }.value
        Categories.RECENT -> app.catalog.movies.items
            .mapNotNull { m -> app.library.progress[Library.key(MediaKind.MOVIE, m.id)]?.let { m to it.date } }
            .sortedByDescending { it.second }.map { it.first }
        Categories.ALL -> app.catalog.movies.items
        else -> app.catalog.movies.items(selection)
    }
    PosterBrowser(tr("Films", "Movies"), tr("Aucun film", "No movies"), Icons.Outlined.Theaters, MediaKind.MOVIE, app.catalog.movies, movies, selection, { selection = it },
        { it.id }, { DisplayName.clean(it.name) }, { it.poster }, { app.library.progress[Library.key(MediaKind.MOVIE, it.id)]?.fraction },
        { Favorite.movie(it) }, { Route.MovieRoute(it) })
}

@Composable
fun SeriesScreen() {
    val app = LocalApp.current
    var selection by rememberSaveable { mutableStateOf(Categories.ALL) }
    val series = when (selection) {
        Categories.FAVORITES -> produceState(emptyList<Series>(), app.library.favorites, app.catalog.series) {
            value = app.catalog.favoriteSeries(app.library.favorites(MediaKind.SERIES).map { it.key })
        }.value
        Categories.ALL, Categories.RECENT -> app.catalog.series.items
        else -> app.catalog.series.items(selection)
    }
    PosterBrowser(tr("Séries", "Series"), tr("Aucune série", "No series"), Icons.Outlined.Tv, MediaKind.SERIES, app.catalog.series, series, selection, { selection = it },
        { it.id }, { DisplayName.clean(it.name) }, { it.cover }, favorite = { Favorite.series(it) }, route = { Route.SeriesRoute(it) })
}

@Composable
fun RoutePage(route: Route, close: () -> Unit) {
    BackHandler { close() }
    when (route) {
        is Route.MovieRoute -> MovieDetail(route.movie, close)
        is Route.SeriesRoute -> SeriesDetail(route.series, close)
    }
}

/** A film or series page, as iOS's DetailHeader: the backdrop 300 pt high
 *  fading into the background, the poster and title over its foot, the
 *  actions under them; the back and star buttons in glass over the top. */
@Composable
private fun DetailPage(title: String, backdrop: String?, poster: String?, facts: List<String>, favorite: Favorite, close: () -> Unit,
                       actions: @Composable ColumnScope.() -> Unit, below: LazyListScope.() -> Unit) {
    val app = LocalApp.current
    val state = rememberLazyListState()
    Box(Modifier.fillMaxSize().background(Background)) {
        LazyColumn(Modifier.fillMaxSize(), state, contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(300.dp)) {
                    RemoteImage(backdrop ?: poster, Modifier.fillMaxSize().then(if (backdrop == null) Modifier.blur(30.dp) else Modifier))
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.5f to Background.copy(alpha = 0.7f), 1f to Background)))
                    Row(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Bottom) {
                        Box(
                            Modifier.size(112.dp, 168.dp).shadow(18.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black, spotColor = Color.Black)
                                .clip(RoundedCornerShape(16.dp)).background(Ink.copy(alpha = 0.06f))
                                .border(1.dp, Ink.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                        ) { RemoteImage(poster, Modifier.fillMaxSize()) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(title, style = IOS.title2, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            if (facts.isNotEmpty()) Text(facts.joinToString(" · "), style = IOS.subheadline, color = Secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            item { Column(Modifier.padding(horizontal = 20.dp).padding(top = 16.dp), content = actions) }
            below()
        }
        // The navigation bar: back on the left, the star on the right.
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBackIos, tr("Retour", "Back"), onClick = close)
            Spacer(Modifier.weight(1f))
            val on = app.library.isFavorite(favorite)
            GlassIconButton(if (on) Icons.Default.Star else Icons.Outlined.StarOutline, if (on) tr("Retirer des favoris", "Remove from favorites") else tr("Ajouter aux favoris", "Add to favorites")) {
                app.library.toggleFavorite(favorite)
            }
        }
    }
}

/** The tint filled, a white label with a symbol: `.glassProminent` at `.large`. */
@Composable
private fun PlayButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 1.04f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "press")
    Row(
        modifier.widthIn(max = 260.dp).scale(scale).clip(RoundedCornerShape(50)).background(FluxBlue)
            .clickable(source, indication = null, onClick = onClick).height(52.dp).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.PlayArrow, null, Modifier.size(22.dp), tint = Color.White)
        Spacer(Modifier.width(6.dp))
        Text(text, style = IOS.headline, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun ProgressLine(fraction: Double, modifier: Modifier = Modifier) {
    Box(modifier.height(4.dp).clip(RoundedCornerShape(50)).background(Ink.copy(alpha = 0.18f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.toFloat().coerceIn(0f, 1f)).background(FluxBlue))
    }
}

@Composable
private fun MovieDetail(movie: Movie, close: () -> Unit) {
    val app = LocalApp.current
    val player = LocalPlayer.current
    var details by remember { mutableStateOf<MovieDetails?>(null) }
    LaunchedEffect(movie.id) { details = app.accounts.client?.let { runCatching { it.movieDetails(movie.id) }.getOrNull() } ?: MovieDetails() }
    val d = details
    val progress = app.library.progress[Library.key(MediaKind.MOVIE, movie.id)]
    val resumable = progress?.isResumable == true
    DetailPage(DisplayName.clean(movie.name), d?.backdrop, d?.poster ?: movie.poster,
        listOfNotNull(d?.year, d?.duration, d?.genre, movie.rating?.let { "★ " + "%.1f".format(it) }), Favorite.movie(movie), close, actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayButton(if (resumable) tr("Reprendre", "Resume") else tr("Lecture", "Play"), Modifier.weight(1f, fill = false).fillMaxWidth()) {
                    app.accounts.client?.let { player.play(movie, it) }
                }
                if (resumable) Box(
                    Modifier.size(52.dp).cardGlass(CircleShape).clickable { app.accounts.client?.let { player.play(movie, it, fromStart = true) } },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Replay, tr("Depuis le début", "From the start"), Modifier.size(22.dp), tint = FluxBlue) }
            }
            if (progress != null && resumable) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ProgressLine(progress.fraction, Modifier.width(200.dp))
                val left = ((progress.duration - progress.position).coerceAtLeast(0.0) / 60).toInt()
                Text((if (left >= 60) "${left / 60} h ${left % 60} min" else "$left min") + tr(" restantes", " left"), style = IOS.caption, color = Secondary)
            }
        }) {
        item {
            Column(Modifier.padding(20.dp).widthIn(max = 820.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when {
                    d?.plot != null -> Text(d.plot!!, style = IOS.body, color = Ink.copy(alpha = 0.9f))
                    d == null -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                }
                d?.director?.let { Credit(tr("Réalisation", "Director"), it) }
                d?.cast?.let { Credit(tr("Avec", "Cast"), it) }
            }
        }
    }
}

@Composable
private fun Credit(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = IOS.caption.copy(fontWeight = FontWeight.SemiBold), color = Secondary)
        Text(value, style = IOS.subheadline)
    }
}

@Composable
private fun SeriesDetail(series: Series, close: () -> Unit) {
    val app = LocalApp.current
    val player = LocalPlayer.current
    var details by remember { mutableStateOf<SeriesDetails?>(null) }
    var failed by remember { mutableStateOf(false) }
    var season by remember { mutableStateOf<Int?>(null) }
    val seasons = details?.seasons?.keys?.sorted() ?: emptyList()
    fun next(): Episode? {
        val d = details ?: return null
        val all = seasons.flatMap { d.seasons[it] ?: emptyList() }
        var latest: Pair<Int, Double>? = null
        all.forEachIndexed { i, e -> app.library.progress[Library.key(MediaKind.SERIES, e.id)]?.let { p -> if (latest == null || p.date > latest!!.second) latest = i to p.date } }
        val i = latest?.first ?: return all.firstOrNull()
        if (app.library.progress[Library.key(MediaKind.SERIES, all[i].id)]?.isResumable == true) return all[i]
        return if (i + 1 < all.size) all[i + 1] else all[i]
    }
    LaunchedEffect(series.id) {
        val client = app.accounts.client ?: return@LaunchedEffect
        runCatching { client.seriesDetails(series.id) }.onSuccess { details = it; season = next()?.season ?: it.seasons.keys.minOrNull() }.onFailure { failed = true }
    }
    val d = details
    DetailPage(DisplayName.clean(series.name), d?.backdrop, series.cover,
        listOfNotNull(d?.year ?: series.year, seasons.size.takeIf { it > 0 }?.let { if (it == 1) tr("1 saison", "1 season") else tr("$it saisons", "$it seasons") }, d?.genre,
            series.rating?.let { "★ " + "%.1f".format(it) }), Favorite.series(series), close,
        actions = {
            next()?.let { e ->
                val started = app.library.progress[Library.key(MediaKind.SERIES, e.id)] != null
                PlayButton(if (started) tr("Reprendre S${e.season} E${e.number}", "Resume S${e.season} E${e.number}") else "S${e.season} E${e.number}", Modifier.fillMaxWidth()) {
                    app.accounts.client?.let { player.play(e, series, it) }
                }
            }
        }) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                d?.plot?.let { Text(it, style = IOS.body, color = Ink.copy(alpha = 0.9f), maxLines = 6, overflow = TextOverflow.Ellipsis) }
                when {
                    d == null && !failed -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp))
                    seasons.isEmpty() -> ContentUnavailable(Icons.Outlined.Tv, tr("Aucun épisode", "No episodes"), modifier = Modifier.padding(top = 40.dp))
                    seasons.size > 1 -> SeasonPicker(seasons, season ?: seasons[0]) { season = it }
                }
            }
        }
        if (seasons.isNotEmpty()) {
            val episodes = d?.seasons?.get(season ?: seasons[0]) ?: emptyList()
            items(episodes, key = { it.id }) { e ->
                Box(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)) {
                    EpisodeRow(e, app.library.progress[Library.key(MediaKind.SERIES, e.id)]?.fraction) { app.accounts.client?.let { player.play(e, series, it) } }
                }
            }
        }
    }
}

/** As iOS: the capsule picker up to four seasons, glass chips beyond. */
@Composable
private fun SeasonPicker(seasons: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    if (seasons.size <= 4) {
        SegmentedPicker(seasons, selected, { tr("Saison $it", "Season $it") }, onSelect = onSelect)
        return
    }
    LazyRow(Modifier.bleed(20.dp), contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(seasons) { s ->
            val on = s == selected
            Box(
                Modifier.height(36.dp).clip(RoundedCornerShape(50))
                    .then(if (on) Modifier.background(Brush.horizontalGradient(listOf(FluxBlue, FluxViolet))) else Modifier.cardGlass(RoundedCornerShape(50)))
                    .clickable { onSelect(s) }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(tr("Saison $s", "Season $s"), style = IOS.subheadline.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium), color = if (on) Color.White else Ink)
            }
        }
    }
}

/** As iOS's EpisodeRow: a 128×72 still, the number in the tint, the
 *  title and length, a play (or done) symbol, all in glass. */
@Composable
private fun EpisodeRow(episode: Episode, progress: Double?, play: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().cardGlass(RoundedCornerShape(20.dp)).pressable().clickable(onClick = play).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(128.dp, 72.dp).clip(RoundedCornerShape(12.dp)).background(Ink.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
            Text("${episode.number}", style = IOS.title3.copy(fontWeight = FontWeight.Bold), color = Tertiary)
            RemoteImage(episode.image, Modifier.fillMaxSize())
            if (progress != null && progress > 0) ProgressLine(progress, Modifier.align(Alignment.BottomCenter).padding(horizontal = 6.dp).padding(bottom = 4.dp).fillMaxWidth())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr("Épisode ${episode.number}", "Episode ${episode.number}"), style = IOS.caption.copy(fontWeight = FontWeight.SemiBold), color = FluxBlue)
            Text(episode.title, style = IOS.subheadline.copy(fontWeight = FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis)
            episode.duration?.let { Text(it, style = IOS.caption, color = Secondary) }
        }
        Icon(if ((progress ?: 0.0) >= 0.95) Icons.Default.CheckCircle else Icons.Default.PlayCircle, null, Modifier.size(28.dp), tint = FluxBlue)
    }
}

// Search

/** As iOS's SearchView: the field in the bar (`LocalSearch`), up with the
 *  keyboard at once; channels as rows, films and series in rows of
 *  posters, an empty state in the middle while there's nothing. On a
 *  rail, the field heads the screen instead. */
@Composable
fun SearchScreen() {
    val app = LocalApp.current
    val player = LocalPlayer.current
    val open = LocalOpenRoute.current
    val inBar = LocalSearch.current
    val own = remember { SearchState() }
    val search = inBar ?: own
    val query = search.query
    var results by remember { mutableStateOf(SearchResults()) }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(query) { if (query.isBlank()) { results = SearchResults(); return@LaunchedEffect }; delay(180); results = app.catalog.search(query) }
    // Opening the tab is asking to search, unless a search is already there.
    LaunchedEffect(Unit) { if (query.isEmpty()) { delay(80); runCatching { search.focus.requestFocus() } } }
    val title = tr("Rechercher", "Search")
    val state = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        if (query.isBlank() || results.isEmpty) {
            Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
                if (query.isBlank()) ContentUnavailable(Icons.Default.Search, title, tr("Chaînes, films et séries.", "Channels, movies and series."))
                else ContentUnavailable(Icons.Default.Search, tr("Aucun résultat pour « $query »", "No results for “$query”"),
                    tr("Vérifie l’orthographe ou lance une nouvelle recherche.", "Check the spelling or try a new search."))
            }
        }
        LazyColumn(Modifier.fillMaxSize(), state, contentPadding = listPadding(top = 0.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (inBar == null) {
                item(key = "title") { Box(Modifier.bleed(16.dp)) { LargeTitle(title) } }
                item(key = "field") { SearchBar(query, { own.query = it }, own.focus) { keyboard?.hide() } }
            } else if (!search.active) item(key = "title") { Box(Modifier.bleed(16.dp)) { LargeTitle(title) } }
            // While searching, the title gives way to what's found, as on iOS.
            else item(key = "top") { Spacer(Modifier.statusBarsPadding().height(20.dp)) }
            if (results.live.isNotEmpty()) {
                item { SearchSection(tr("Chaînes", "Channels")) }
                items(results.live, key = { "l" + it.id }) { g ->
                    ChannelRow(g, player.channel?.id == g.id) { v -> app.accounts.client?.let { player.watch(g, results.live, it, v) } }
                }
            }
            if (results.movies.isNotEmpty()) {
                item { SearchSection(tr("Films", "Movies")) }
                item { PosterRow(results.movies, { "m" + it.id }) { m -> Poster(DisplayName.clean(m.name), m.poster) { open(Route.MovieRoute(m)) } } }
            }
            if (results.series.isNotEmpty()) {
                item { SearchSection(tr("Séries", "Series")) }
                item { PosterRow(results.series, { "s" + it.id }) { s -> Poster(DisplayName.clean(s.name), s.cover) { open(Route.SeriesRoute(s)) } } }
            }
        }
        TopEdge(title, !search.active && state.firstVisibleItemIndex > 0)
    }
}

@Composable
private fun SearchSection(title: String) = Text(title, Modifier.padding(top = 14.dp, bottom = 2.dp), style = IOS.title3.copy(fontWeight = FontWeight.Bold))

@Composable
private fun <T> PosterRow(items: List<T>, key: (T) -> Any, card: @Composable (T) -> Unit) {
    LazyRow(Modifier.bleed(16.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items, key = key) { Box(Modifier.width(112.dp)) { card(it) } }
    }
}

/** iOS 26's search field: a glass capsule, the magnifier and the prompt in
 *  secondary, a clear button once there's text. */
@Composable
private fun SearchBar(query: String, onChange: (String) -> Unit, focus: androidx.compose.ui.focus.FocusRequester, done: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).cardGlass(RoundedCornerShape(50)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.Search, null, Modifier.size(20.dp), tint = Secondary)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(tr("Chaînes, films, séries", "Channels, movies, series"), style = IOS.body, color = Secondary, maxLines = 1)
            androidx.compose.foundation.text.BasicTextField(
                query, onChange, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                textStyle = IOS.body.copy(color = Ink), cursorBrush = androidx.compose.ui.graphics.SolidColor(FluxBlue),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { done() }),
            )
        }
        if (query.isNotEmpty()) Icon(Icons.Default.Cancel, tr("Effacer", "Clear"), Modifier.size(20.dp).clip(CircleShape).clickable { onChange("") }, tint = Tertiary)
    }
}

// Settings

/** As on iOS: cards of glass, a capsule picker that slides, switches. */
@Composable
fun SettingsScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Account?>(null) }
    var managesAccounts by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    if (adding) { SignInScreen { adding = false }; BackHandler { adding = false }; return }
    if (managesAccounts) AccountsSheet({ managesAccounts = false }, add = { managesAccounts = false; adding = true }, remove = { removing = it })
    val s = app.settings
    val c = app.catalog
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), listState, contentPadding = listPadding(top = 0.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Box(Modifier.bleed(16.dp)) { ScreenTitle(tr("Réglages", "Settings")) } }
        item { AccountCard { managesAccounts = true } }
        item {
            // The free channels have no qualities, delays nor subscription:
            // their country comes first instead.
            val free = app.accounts.active?.credentials?.source == SourceKind.FREE
            CollapsibleCard(tr("Lecture", "Playback"), Icons.Outlined.SmartDisplay, "playback") {
                // As iOS's: the label, the picker and its note 8 pt apart.
                if (free) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel(tr("Chaînes gratuites", "Free channels"))
                    SegmentedPicker(com.fares.flux.core.FreeChannels.choices, s.freeCountry, com.fares.flux.core.FreeChannels::choiceTitle,
                        mark = com.fares.flux.core.FreeChannels::choiceFlag) {
                        s.changeFreeCountry(it)
                        // After the lens has slid: the guide built again would hold up its frames.
                        scope.launch { delay(350); c.optionsChanged() }
                    }
                    Footnote(com.fares.flux.core.FreeChannels.countriesNote(s.freeCountry))
                } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel(tr("Qualité par défaut", "Default quality"))
                    SegmentedPicker(QualityPreference.entries, s.quality, { it.title }, {
                        when (it) { QualityPreference.BEST -> Icons.Default.AutoAwesome; QualityPreference.HD -> Icons.Default.Tv; else -> Icons.Default.Eco }
                    }) { s.changeQuality(it) }
                    Footnote(tr("Quand une chaîne existe en plusieurs qualités. Celle choisie à la main pour une chaîne reste la sienne.", "When a channel comes in several qualities. One you pick by hand for a channel sticks to it."))
                }
                HairlineDivider()
                MenuPicker(tr("Numérotation", "Numbering"), ChannelNumbering.entries, s.numbering, { it.title }) { s.changeNumbering(it); c.optionsChanged() }
                Footnote(if (s.numbering == ChannelNumbering.PROVIDER) tr("Les numéros et l’ordre de ton abonnement.", "Your subscription’s numbers and order.") else tr("Les chaînes du plan d’abord, les autres suivent.", "The line-up’s channels first, then the rest."))
                if (!free) {
                    HairlineDivider()
                    ToggleRow(tr("Chaînes en différé", "Time-shifted channels"), s.showsShifted) { s.changeShowsShifted(it); c.optionsChanged() }
                    Footnote(tr("Les versions décalées (« TF1 +4 », diffusée 4 h plus tard) se choisissent depuis leur chaîne au lieu d’être masquées.", "Time-shifted versions (“TF1 +4”, aired 4 hours later) are picked from their channel instead of being hidden."))
                    HairlineDivider()
                    ToggleRow(tr("Flux gratuits en priorité", "Prefer free streams"), s.prefersFree) { s.changePrefersFree(it); c.optionsChanged() }
                    Footnote(tr("France 2, Arte, BFM TV… se lisent depuis le flux officiel de la chaîne, plus stable, sans occuper un écran de ton abonnement. Celui de l’abonnement prend le relais s’il ne répond pas.", "France 2, Arte, BFM TV… play from the channel’s official stream: more stable, and it doesn’t use up one of your subscription’s screens. The subscription’s stream takes over if it doesn’t respond."))
                }
                HairlineDivider()
                MenuPicker("DNS", DnsProvider.entries, s.dns, { it.label }) { s.changeDns(it); app.accounts.client?.let { cl -> scope.launch { c.refresh(cl) } } }
                Footnote(tr("Si ta box bloque ton fournisseur, un DNS public (Cloudflare, Google, Quad9) le rend souvent accessible.", "If your router blocks your provider, a public DNS (Cloudflare, Google, Quad9) often gets through."))
            }
        }
        item {
            val modes = listOf("light", "dark", "system")
            fun modeTitle(m: String) = when (m) { "light" -> tr("Clair", "Light"); "dark" -> tr("Sombre", "Dark"); else -> tr("Système", "System") }
            CollapsibleCard(tr("Affichage", "Display"), Icons.Outlined.Palette, "display") {
                FieldLabel(tr("Mode", "Mode"))
                SegmentedPicker(modes, s.appearance, ::modeTitle, {
                    when (it) { "light" -> Icons.Default.LightMode; "dark" -> Icons.Default.DarkMode; else -> Icons.Default.Contrast }
                }) { s.changeAppearance(it) }
                HairlineDivider()
                FieldLabel(tr("Chaînes", "Channels"))
                SegmentedPicker(listOf(false, true), s.liveCards, { if (it) tr("Cartes", "Cards") else tr("Liste", "List") }, {
                    if (it) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList
                }) { s.changeLiveCards(it) }
                Footnote(if (s.liveCards) tr("Des cartes avec l’image du programme, comme sur la TV.", "Cards with a picture of the show, like on the TV.") else tr("Une ligne par chaîne, la plus compacte.", "One row per channel, the most compact."))
                // Only where the guide has something to show.
                if (app.hasGuide) {
                    HairlineDivider()
                    ToggleRow(tr("Onglet Guide", "Guide tab"), s.guideTab) { s.changeGuideTab(it) }
                    Footnote(tr("Les programmes de toutes les chaînes sur une grille.", "What’s on every channel, on a grid."))
                }
                // Only for an account that has some: the free channels have none.
                if (app.hasMovies) {
                    HairlineDivider()
                    ToggleRow(tr("Onglet Films", "Movies tab"), s.moviesTab) { s.changeMoviesTab(it) }
                }
                if (app.hasSeries) {
                    HairlineDivider()
                    ToggleRow(tr("Onglet Séries", "Series tab"), s.seriesTab) { s.changeSeriesTab(it) }
                }
            }
        }
        item {
            CollapsibleCard(tr("Favoris", "Favorites"), Icons.Outlined.StarOutline, "favorites") {
                ToggleRow(tr("Communs à tous les comptes", "Shared across accounts"), s.sharesFavorites) { s.changeSharesFavorites(it) }
                Footnote(if (s.sharesFavorites) tr("Une seule liste, retrouvée par nom chez chaque fournisseur.", "One list, matched by name with every provider.") else tr("Chaque compte a sa propre liste.", "Each account has its own list."))
            }
        }
        item {
            CollapsibleCard(tr("Catalogue", "Catalog"), Icons.Outlined.Layers, "data") {
                Footnote(tr("${c.guide.groups.size} chaînes · ${c.movies.items.size} films · ${c.series.items.size} séries. Gardé sur l’appareil et actualisé en arrière-plan à l’ouverture.",
                    "${c.guide.groups.size} ${if (c.guide.groups.size == 1) "channel" else "channels"} · ${c.movies.items.size} ${if (c.movies.items.size == 1) "movie" else "movies"} · " +
                        "${c.series.items.size} series. Kept on the device and refreshed in the background when the app opens."))
                GlassButton(if (c.isLoading) tr("Actualisation…", "Refreshing…") else tr("↻  Actualiser", "↻  Refresh"), modifier = Modifier.fillMaxWidth()) {
                    if (!c.isLoading) app.accounts.client?.let { cl -> scope.launch { c.refresh(cl) } }
                }
                if (app.library.progress.isNotEmpty() || app.library.recentChannels.isNotEmpty())
                    GlassButton(tr("Effacer l’historique", "Clear history"), modifier = Modifier.fillMaxWidth()) { clearing = true }
            }
        }
        item {
            Text("Flux ${LocalContext.current.packageManager.getPackageInfo(LocalContext.current.packageName, 0).versionName}",
                Modifier.fillMaxWidth().padding(top = 12.dp), color = Tertiary, style = IOS.footnote, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
    TopEdge(tr("Réglages", "Settings"), listState.firstVisibleItemIndex > 0)
    }
    if (clearing) AlertDialog(onDismissRequest = { clearing = false }, title = { Text(tr("Effacer l’historique ?", "Clear history?")) },
        text = { Text(tr("Les chaînes récentes et la reprise des films et épisodes seront oubliées.", "Recent channels and where films and episodes stopped will be forgotten.")) },
        confirmButton = { TextButton({ app.library.clearHistory(); clearing = false }) { Text(tr("Effacer", "Clear"), color = Color(0xFFFF453A)) } },
        dismissButton = { TextButton({ clearing = false }) { Text(tr("Annuler", "Cancel")) } }, containerColor = if (Palette.dark) Color(0xFF1E1E26) else Color(0xFFF4F4F8), shape = RoundedCornerShape(28.dp))
    removing?.let { a ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text(tr("Supprimer « ${a.title} » ?", "Remove “${a.title}”?")) },
            text = { Text(tr("Ses identifiants, son catalogue et ses reprises quittent cet appareil.", "Its login, catalog and watch progress will be removed from this device.")) },
            confirmButton = { TextButton({ app.catalog.forget(a.credentials); app.library.forget(a.id); app.categoryLayouts.forget(a.id); app.accounts.remove(a.id); removing = null }) { Text(tr("Supprimer", "Remove"), color = Color(0xFFFF453A)) } },
            dismissButton = { TextButton({ removing = null }) { Text(tr("Annuler", "Cancel")) } }, containerColor = if (Palette.dark) Color(0xFF1E1E26) else Color(0xFFF4F4F8), shape = RoundedCornerShape(28.dp))
    }
}

// Sign-in

/** A code another device nearby picks to send its account here: an iPhone,
 *  a Mac, another Android… Listens only while open. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(dismiss: () -> Unit, done: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val receiver = remember {
        PairingReceiver(context, kind = "Android") { message ->
            if (!checking) scope.launch {
                checking = true; error = null
                runCatching { app.receive(message) }.onSuccess { done() }.onFailure { error = it.readableMessage }
                checking = false
            }
        }
    }
    DisposableEffect(Unit) { receiver.start(); onDispose { receiver.stop() } }
    androidx.compose.ui.window.Dialog(onDismissRequest = dismiss) {
        // Pops in with a small spring, as an iOS alert does.
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val pop by androidx.compose.animation.core.animateFloatAsState(if (shown) 1f else 0.85f,
            androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 500f), label = "pop")
        Column(Modifier.widthIn(max = 340.dp).fillMaxWidth().graphicsLayer { scaleX = pop; scaleY = pop; alpha = ((pop - 0.85f) / 0.15f).coerceIn(0f, 1f) }
            .clip(RoundedCornerShape(32.dp)).background(if (Palette.dark) Color(0xFF1C1C22) else Color.White)
            .border(1.dp, Ink.copy(alpha = 0.12f), RoundedCornerShape(32.dp)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("⤓", fontSize = 36.sp, color = FluxBlue)
            Text(tr("Importer un compte", "Import an account"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(tr("Sur l’appareil qui a le compte : Réglages › Comptes › Envoyer à un appareil, puis touche « Android » avec ce code.", "On the device that has the account, go to Settings › Accounts › Send to a device, then tap “Android” with this code."),
                color = Secondary, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(receiver.code.toCharArray().joinToString("  "), fontSize = 40.sp, fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(brush = Brush.horizontalGradient(listOf(FluxBlue, FluxViolet))))
            if (checking) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = FluxBlue)
            error?.let { Text(it, color = Color(0xFFFF453A), fontSize = 13.sp) }
            Text(tr("Les deux appareils doivent être sur le même Wi-Fi.", "Both devices need to be on the same Wi-Fi."), color = Tertiary, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            GlassButton(tr("Annuler", "Cancel"), modifier = Modifier.fillMaxWidth()) { dismiss() }
        }
    }
}

/** The first screen, and adding an account later, as iOS's SignInView:
 *  the logo, the free channels in a glass capsule, the import, then the
 *  account typed in a glass card, and the button in the tint. */
@Composable
fun SignInScreen(onDone: (() -> Unit)? = null) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(SourceKind.XTREAM) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    val complete = server.isNotBlank() && (kind == SourceKind.PLAYLIST || (username.isNotBlank() && password.isNotEmpty()))
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    fun signIn() {
        if (checking || !complete) return
        scope.launch {
            checking = true; error = null
            val credentials = if (kind == SourceKind.PLAYLIST) Credentials.playlist(server) else Credentials(server.trim(), username.trim(), password)
            runCatching { app.accounts.add(Account(name = name.trim(), credentials = credentials)) }
                .onSuccess { onDone?.invoke() }.onFailure { error = it.readableMessage; haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Reject) }
            checking = false
        }
    }
    if (importing) ImportSheet({ importing = false }) { importing = false; onDone?.invoke() }

    val logo by animateFloatAsState(if (checking) 0.9f else 1f, spring(dampingRatio = 0.6f, stiffness = 400f), label = "logo")
    LazyColumn(Modifier.fillMaxSize().background(Background).imePadding(), horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        item {
            Column(Modifier.widthIn(max = 480.dp).statusBarsPadding().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // The logo's glow, as iOS's: its violet, spread around it.
                    Box(Modifier.scale(logo).drawBehind {
                        drawCircle(Brush.radialGradient(0.35f to FluxViolet.copy(alpha = 0.45f), 1f to Color.Transparent, center = center, radius = 75.dp.toPx()), 75.dp.toPx())
                    }) { FluxLogo(88.dp) }
                    Text("Flux", style = IOS.largeTitle.copy(fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = tracking(44f)))
                    Text(tr("Ajoute ton abonnement IPTV, ou regarde les chaînes gratuites.", "Add your IPTV subscription, or watch the free channels."),
                        style = IOS.subheadline, color = Secondary, textAlign = TextAlign.Center)
                }
                if (app.accounts.accounts.none { it.credentials.source == SourceKind.FREE }) FreeChannelsButton { app.accounts.addFree(); onDone?.invoke() }
                // `.buttonStyle(.glass)` at `.large`: the label in the tint.
                Row(
                    Modifier.fillMaxWidth().height(52.dp).cardGlass(RoundedCornerShape(50)).pressable().clickable { importing = true },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.ArrowCircleDown, null, Modifier.size(20.dp), tint = FluxBlue)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Importer depuis un autre appareil", "Import from another device"), style = IOS.subheadline.copy(fontWeight = FontWeight.SemiBold), color = FluxBlue)
                }
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    NativeSegmented(listOf(SourceKind.XTREAM, SourceKind.PLAYLIST), kind, { if (it == SourceKind.XTREAM) "Xtream Codes" else tr("Lien M3U", "M3U link") }) { kind = it }
                    GlassCard {
                        Column {
                            AccountField(tr("Nom", "Name"), Icons.Outlined.Sell, name, { name = it }, tr("Facultatif, ex. « Maison »", "Optional, e.g. “Home”"))
                            if (kind == SourceKind.PLAYLIST) {
                                FieldDivider()
                                AccountField(tr("Lien de la liste", "Playlist link"), Icons.AutoMirrored.Outlined.ListAlt, server, { server = it }, "https://exemple.com/liste.m3u",
                                    keyboard = KeyboardType.Uri, imeAction = androidx.compose.ui.text.input.ImeAction.Go, onDone = { signIn() })
                            } else {
                                FieldDivider()
                                AccountField(tr("Serveur", "Server"), Icons.Outlined.Dns, server, { server = it }, tr("exemple.com:8080", "example.com:8080"), keyboard = KeyboardType.Uri)
                                FieldDivider()
                                AccountField(tr("Utilisateur", "Username"), Icons.Outlined.Person, username, { username = it }, tr("Nom d’utilisateur", "Username"))
                                FieldDivider()
                                AccountField(tr("Mot de passe", "Password"), Icons.Outlined.Key, password, { password = it }, tr("Mot de passe", "Password"), secure = true, iconTurn = 90f,
                                    keyboard = KeyboardType.Password, imeAction = androidx.compose.ui.text.input.ImeAction.Go, onDone = { signIn() })
                            }
                        }
                    }
                }
                error?.let {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, Modifier.size(16.dp), tint = Color(0xFFFF453A))
                        Text(it, style = IOS.footnote, color = Color(0xFFFF453A))
                    }
                }
                // `.glassProminent` at `.large`: the tint, grey while it can't go.
                val ready = !checking && complete
                Box(
                    Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(50))
                        .background(if (ready || checking) FluxBlue else Ink.copy(alpha = if (Palette.dark) 0.16f else 0.08f))
                        .clickable(enabled = ready) { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm); signIn() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (checking) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                    else Text(tr("Se connecter", "Sign In"), style = IOS.headline, color = if (ready) Color.White else Tertiary)
                }
                Text(tr("Tes identifiants restent sur cet appareil, chiffrés.", "Your login stays on this device, encrypted."),
                    style = IOS.footnote, color = Tertiary, textAlign = TextAlign.Center)
            }
        }
    }
}

/** The free channels with nothing to type, as iOS's FreeChannelsButton:
 *  `.glass` at `.large`, so the labels take the tint. */
@Composable
fun FreeChannelsButton(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Ink.copy(alpha = if (Palette.dark) 0.1f else 0.05f)).glassRim(RoundedCornerShape(50))
            .pressable().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Default.SettingsInputAntenna, null, Modifier.width(32.dp).height(26.dp), tint = FluxBlue)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tr("Regarder les chaînes gratuites", "Watch free channels"), style = IOS.headline, color = FluxBlue)
            Text(tr("TNT, info, France 3 régions et Pluto TV. Sans compte.", "Free TV and news from the US, the UK and France. No account needed."),
                style = IOS.footnote, color = FluxBlue.copy(alpha = 0.55f))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = FluxBlue.copy(alpha = 0.55f))
    }
}

/** iOS's own segmented control, grey: a 32 pt track, the choice on a lighter pill. */
@Composable
fun <T> NativeSegmented(options: List<T>, selected: T, title: (T) -> String, onSelect: (T) -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(50))
            .background(if (Palette.dark) Color(0xFF1C1C1F) else Color(0x1F767680)).padding(2.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * options.indexOf(selected).coerceAtLeast(0), snappy(), label = "thumb")
        Box(Modifier.offset(x = offset).width(segment).fillMaxHeight().shadow(2.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50))
            .background(if (Palette.dark) Color(0xFF5A5A5F) else Color.White))
        Row(Modifier.fillMaxSize()) {
            options.forEach { o ->
                Box(Modifier.weight(1f).fillMaxHeight().clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(o) }, contentAlignment = Alignment.Center) {
                    Text(title(o), style = IOS.footnote.copy(fontWeight = if (o == selected) FontWeight.SemiBold else FontWeight.Medium), color = Ink, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun FieldDivider() = Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(0.5.dp).background(Separator))

/** A field of the account card, as iOS's: the symbol in the tint, the
 *  label in caption above what's typed. */
@Composable
private fun AccountField(
    label: String, icon: ImageVector, value: String, onChange: (String) -> Unit, prompt: String,
    secure: Boolean = false, iconTurn: Float = 0f, keyboard: KeyboardType = KeyboardType.Text,
    imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Next, onDone: () -> Unit = {},
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        // The key upright, as SF Symbols draws it.
        Icon(icon, null, Modifier.width(24.dp).height(20.dp).rotate(iconTurn), tint = FluxBlue)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = IOS.caption, color = Secondary)
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(prompt, style = IOS.body, color = Tertiary, maxLines = 1)
                androidx.compose.foundation.text.BasicTextField(
                    value, onChange, Modifier.fillMaxWidth(), singleLine = true, textStyle = IOS.body.copy(color = Ink),
                    cursorBrush = SolidColor(FluxBlue),
                    visualTransformation = if (secure) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false, imeAction = imeAction),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone() }, onGo = { onDone() }),
                )
            }
        }
    }
}

/** The account in use, as iOS's: its avatar, name and what it is, and a
 *  button to the accounts. A panel shows when it expires. */
@Composable
private fun AccountCard(manage: () -> Unit) {
    val app = LocalApp.current
    val account = app.accounts.active
    GlassCard(Modifier.widthIn(max = 720.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.AccountCircle, null, Modifier.size(46.dp), tint = FluxBlue)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(account?.title ?: "—", style = IOS.headline)
                Text(account?.subtitle ?: "", style = IOS.footnote, color = Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            app.accounts.info?.status?.let { status ->
                Text(if (status == "Active") tr("Actif", "Active") else status, Modifier.cardGlass(RoundedCornerShape(50)).padding(horizontal = 11.dp, vertical = 7.dp),
                    style = IOS.caption.copy(fontWeight = FontWeight.SemiBold), color = if (status == "Active") FluxBlue else Secondary)
            }
        }
        val many = app.accounts.accounts.size > 1
        GlassLabelButton(if (many) tr("Changer de compte", "Switch account") else tr("Comptes", "Accounts"), Icons.Outlined.People, Modifier.fillMaxWidth(), onClick = manage)
        val info = app.accounts.info
        if (info?.expiration != null && account?.credentials?.source == SourceKind.XTREAM) {
            HairlineDivider()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(info.expiration!! * 1000)),
                    style = IOS.title3.copy(fontFeatureSettings = "tnum"))
                Text(tr("Expire", "Expires"), style = IOS.caption, color = Secondary)
            }
        }
    }
}

/** The accounts, in a sheet as iOS's AccountsView: the list to switch, a
 *  pencil to remove one, then sending the account in use to a device. */
@Composable
private fun AccountsSheet(dismiss: () -> Unit, add: () -> Unit, remove: (Account) -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    IOSSheet(tr("Comptes", "Accounts"), dismiss, leading = { SheetCircleButton(Icons.Default.Add, tr("Ajouter", "Add"), onClick = add) }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 40.dp, bottom = 32.dp)) {
            InsetSection(footer = tr("Touche un compte pour l’utiliser. Chacun garde son catalogue : revenir à un compte est immédiat.",
                "Tap an account to use it. Each keeps its own catalog, so switching back is instant.")) {
                val accounts = app.accounts.accounts
                accounts.forEachIndexed { i, a ->
                    InsetRow(last = i == accounts.lastIndex, onClick = { app.accounts.select(a.id); dismiss() }) {
                        val on = a.id == app.accounts.activeID
                        Icon(if (on) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, Modifier.size(24.dp), tint = if (on) FluxBlue else Tertiary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(a.title, style = IOS.body.copy(fontWeight = FontWeight.SemiBold))
                            Text(a.subtitle, style = IOS.footnote, color = Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(Modifier.size(36.dp).pressable().cellGlass(CircleShape).clickable { remove(a) }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Edit, tr("Modifier ${a.title}", "Edit ${a.title}"), Modifier.size(18.dp))
                        }
                    }
                }
            }
            app.accounts.active?.let { active ->
                val sender = remember { com.fares.flux.core.PairingSender(context) }
                DisposableEffect(Unit) { sender.start(); onDispose { sender.stop() } }
                InsetSection(header = tr("Envoyer à un appareil", "Send to a device"),
                    footer = tr("Sur l’autre appareil (iPhone, iPad, Mac, Android, Apple TV, Android TV), touche « Importer depuis un autre appareil » à la connexion, puis touche ici celui dont le code s’affiche à l’écran pour y envoyer « ${active.title} », avec tes favoris, tes reprises, tes chaînes récentes et tes réglages.",
                        "On the other device (iPhone, iPad, Mac, Android, Apple TV, Android TV), tap “Import from another device” on the sign-in screen, then tap here the one whose code is on screen to send it “${active.title}”, with your favorites, resume points, recent channels and settings.")) {
                    if (sender.screens.isEmpty()) InsetRow {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = FluxBlue)
                        Text(tr("Recherche des appareils à proximité…", "Looking for nearby devices…"), style = IOS.body, color = Secondary)
                    }
                    sender.screens.forEachIndexed { i, screen ->
                        InsetRow(last = i == sender.screens.lastIndex, onClick = { sender.send(app, screen) }) {
                            Icon(when (screen.kind) {
                                "Mac" -> Icons.Default.Laptop
                                "Apple TV", "Android TV" -> Icons.Default.Tv
                                "iPad" -> Icons.Default.TabletAndroid
                                else -> Icons.Default.Smartphone
                            }, null, Modifier.size(24.dp), tint = FluxBlue)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(screen.kind, style = IOS.body.copy(fontWeight = FontWeight.SemiBold))
                                Text("Code ${screen.code}", style = IOS.footnote.copy(fontFeatureSettings = "tnum"), color = Secondary)
                            }
                            when (screen.code) {
                                sender.sent -> Text(tr("Envoyé", "Sent"), style = IOS.footnote.copy(fontWeight = FontWeight.SemiBold), color = FluxBlue)
                                sender.failed -> Text(tr("Injoignable", "Unreachable"), style = IOS.footnote.copy(fontWeight = FontWeight.SemiBold), color = Color(0xFFFF9F0A))
                            }
                        }
                    }
                }
            }
        }
    }
}
