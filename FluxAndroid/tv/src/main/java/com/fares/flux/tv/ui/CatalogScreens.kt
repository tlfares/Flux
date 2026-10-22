package com.fares.flux.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.fares.flux.core.DisplayName
import com.fares.flux.core.Episode
import com.fares.flux.core.Favorite
import com.fares.flux.core.Library
import com.fares.flux.core.MediaKind
import com.fares.flux.core.Movie
import com.fares.flux.core.MovieDetails
import com.fares.flux.core.SearchResults
import com.fares.flux.core.Series
import com.fares.flux.core.SeriesDetails
import kotlinx.coroutines.delay

/** Where a poster leads. */
sealed interface Route {
    data class MovieRoute(val movie: Movie) : Route
    data class SeriesRoute(val series: Series) : Route
}

/** Opens a film or series page over the tabs: in place of the poster,
 *  focus would fall back on the first tab. */
val LocalOpenRoute = androidx.compose.runtime.staticCompositionLocalOf<(Route) -> Unit> { {} }

/** A route's page, with Back to leave it. */
@Composable
fun RoutePage(route: Route, close: () -> Unit) {
    BackHandler { close() }
    when (route) {
        is Route.MovieRoute -> MovieDetail(route.movie)
        is Route.SeriesRoute -> SeriesDetail(route.series)
    }
}

/** Films or series: the categories down the left, the posters on the right. */
@Composable
private fun <T> PosterBrowser(
    empty: String,
    categories: List<com.fares.flux.core.Category>,
    isEmpty: Boolean,
    items: List<T>,
    selection: String,
    onSelect: (String) -> Unit,
    key: (T) -> Any,
    title: (T) -> String,
    image: (T) -> String?,
    progress: (T) -> Double? = { null },
    favorite: (T) -> Favorite,
    route: (T) -> Route,
) {
    val app = LocalApp.current
    val open = LocalOpenRoute.current
    if (isEmpty) { CatalogState(empty); return }
    Row(Modifier.fillMaxSize().padding(start = 20.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
        CategoryColumn(Categories.entries(categories, recent = false), selection, onSelect)
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (selection == Categories.FAVORITES) "Aucun favori. Maintiens le clic sur une affiche pour l’ajouter." else "Rien ici", color = Secondary)
            }
        } else {
            LazyVerticalGrid(GridCells.Adaptive(110.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 15.dp, bottom = 30.dp, end = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                items(items, key = key) { item ->
                    PosterCard(title(item), image(item), progress(item), 100.dp, onLongClick = { app.library.toggleFavorite(favorite(item)) }) { open(route(item)) }
                }
            }
        }
    }
}

@Composable
fun MoviesScreen() {
    val app = LocalApp.current
    var selection by rememberSaveable { mutableStateOf(Categories.ALL) }
    val movies = when (selection) {
        Categories.FAVORITES -> androidx.compose.runtime.produceState(emptyList<Movie>(), app.library.favorites, app.catalog.movies) {
            value = app.catalog.favoriteMovies(app.library.favorites(MediaKind.MOVIE).map { it.key })
        }.value
        Categories.ALL -> app.catalog.movies.items
        else -> app.catalog.movies.items(selection)
    }
    PosterBrowser("Aucun film", app.catalog.movies.categories, app.catalog.movies.isEmpty, movies, selection, { selection = it },
        key = { it.id }, title = { DisplayName.clean(it.name) }, image = { it.poster },
        progress = { app.library.progress[Library.key(MediaKind.MOVIE, it.id)]?.fraction },
        favorite = { Favorite.movie(it) }, route = { Route.MovieRoute(it) })
}

@Composable
fun SeriesScreen() {
    val app = LocalApp.current
    var selection by rememberSaveable { mutableStateOf(Categories.ALL) }
    val series = when (selection) {
        Categories.FAVORITES -> androidx.compose.runtime.produceState(emptyList<Series>(), app.library.favorites, app.catalog.series) {
            value = app.catalog.favoriteSeries(app.library.favorites(MediaKind.SERIES).map { it.key })
        }.value
        Categories.ALL -> app.catalog.series.items
        else -> app.catalog.series.items(selection)
    }
    PosterBrowser("Aucune série", app.catalog.series.categories, app.catalog.series.isEmpty, series, selection, { selection = it },
        key = { it.id }, title = { DisplayName.clean(it.name) }, image = { it.cover },
        favorite = { Favorite.series(it) }, route = { Route.SeriesRoute(it) })
}

/** A film or a series' page: its picture across the screen, the text and buttons over its left side. */
@Composable
private fun DetailPage(
    title: String,
    backdrop: String?,
    poster: String?,
    facts: List<String>,
    plot: String?,
    actions: @Composable () -> Unit,
    below: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        RemoteImage(backdrop ?: poster, Modifier.fillMaxSize().then(if (backdrop == null) Modifier.blur(20.dp) else Modifier)) {
            Box(Modifier.fillMaxSize().background(Color(0xFF141414)))
        }
        // Dark under the text whatever the picture.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.95f), 0.55f to Color.Black.copy(alpha = 0.9f),
            0.8f to Color.Black.copy(alpha = 0.35f), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.95f)))))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 50.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)) {
            item { Text(title, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            if (facts.isNotEmpty()) item { Text(facts.joinToString("  ·  "), color = Secondary, fontSize = 14.sp) }
            plot?.let { item { Text(it, Modifier.widthIn(max = 500.dp), color = Secondary, fontSize = 14.sp, maxLines = 5, overflow = TextOverflow.Ellipsis) } }
            item { Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { actions() } }
            item { below() }
        }
    }
}

@Composable
private fun FavoriteButton(favorite: Favorite) {
    val app = LocalApp.current
    val on = app.library.isFavorite(favorite)
    OutlinedButton(onClick = { app.library.toggleFavorite(favorite) }) { Text(if (on) "★  Retirer des favoris" else "☆  Ajouter aux favoris") }
}

@Composable
private fun MovieDetail(movie: Movie) {
    val app = LocalApp.current
    val player = LocalTVPlayer.current
    var details by remember { mutableStateOf<MovieDetails?>(null) }
    LaunchedEffect(movie.id) {
        details = app.accounts.client?.let { runCatching { it.movieDetails(movie.id) }.getOrNull() } ?: MovieDetails()
    }
    val key = Library.key(MediaKind.MOVIE, movie.id)
    val resumable = app.library.progress[key]?.isResumable == true
    val d = details
    val facts = listOfNotNull(d?.year, d?.duration, d?.genre, movie.rating?.let { "★ " + "%.1f".format(it) })
    DetailPage(DisplayName.clean(movie.name), d?.backdrop, d?.poster ?: movie.poster, facts, d?.plot, actions = {
        Button(onClick = { app.accounts.client?.let { player.play(movie, it) } }) { Text(if (resumable) "▶  Reprendre" else "▶  Lecture") }
        if (resumable) OutlinedButton(onClick = { app.accounts.client?.let { player.play(movie, it, fromStart = true) } }) { Text("↺  Depuis le début") }
        FavoriteButton(Favorite.movie(movie))
    }) {
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            d?.director?.let { Credit("Réalisation", it) }
            d?.cast?.let { Credit("Avec", it) }
        }
    }
}

@Composable
private fun Credit(label: String, value: String) {
    Row(Modifier.widthIn(max = 560.dp)) {
        Text(label, Modifier.width(90.dp), color = Tertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = Secondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SeriesDetail(series: Series) {
    val app = LocalApp.current
    val player = LocalTVPlayer.current
    var details by remember { mutableStateOf<SeriesDetails?>(null) }
    var failed by remember { mutableStateOf(false) }
    var season by remember { mutableStateOf<Int?>(null) }
    val seasons = details?.seasons?.keys?.sorted() ?: emptyList()

    fun nextEpisode(): Episode? {
        val d = details ?: return null
        val all = seasons.flatMap { d.seasons[it] ?: emptyList() }
        var latest: Pair<Int, Double>? = null
        all.forEachIndexed { i, e ->
            val p = app.library.progress[Library.key(MediaKind.SERIES, e.id)]
            if (p != null && (latest == null || p.date > latest!!.second)) latest = i to p.date
        }
        val i = latest?.first ?: return all.firstOrNull()
        if (app.library.progress[Library.key(MediaKind.SERIES, all[i].id)]?.isResumable == true) return all[i]
        return if (i + 1 < all.size) all[i + 1] else all[i]
    }

    LaunchedEffect(series.id) {
        val client = app.accounts.client ?: return@LaunchedEffect
        runCatching { client.seriesDetails(series.id) }
            .onSuccess { details = it; season = nextEpisode()?.season ?: it.seasons.keys.minOrNull() }
            .onFailure { failed = true }
    }
    val d = details
    val facts = listOfNotNull(d?.year ?: series.year, d?.genre, seasons.size.takeIf { it > 0 }?.let { if (it == 1) "1 saison" else "$it saisons" })
    DetailPage(DisplayName.clean(series.name), d?.backdrop, series.cover, facts, d?.plot, actions = {
        nextEpisode()?.let { next ->
            val started = app.library.progress[Library.key(MediaKind.SERIES, next.id)] != null
            Button(onClick = { app.accounts.client?.let { player.play(next, series, it) } }) {
                Text("▶  ${if (started) "Reprendre" else "Lecture"} S${next.season} E${next.number}")
            }
        }
        FavoriteButton(Favorite.series(series))
    }) {
        when {
            d == null && !failed -> Text("Chargement…", color = Secondary, modifier = Modifier.padding(top = 20.dp))
            seasons.isEmpty() -> Text("Aucun épisode.", color = Secondary, modifier = Modifier.padding(top = 20.dp))
            else -> Column(Modifier.padding(top = 15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val current = season ?: seasons[0]
                if (seasons.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                    items(seasons) { s ->
                        FocusRow(onClick = { season = s }, isCurrent = s == current, shape = RoundedCornerShape(50)) {
                            Text("Saison $s", Modifier.padding(horizontal = 13.dp, vertical = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                    items(d?.seasons?.get(current) ?: emptyList(), key = { it.id }) { episode -> EpisodeCard(episode, series, d?.backdrop) }
                }
            }
        }
    }
}

@Composable
private fun EpisodeCard(episode: Episode, series: Series, backdrop: String?) {
    val app = LocalApp.current
    val player = LocalTVPlayer.current
    val progress = app.library.progress[Library.key(MediaKind.SERIES, episode.id)]
    Column(Modifier.width(210.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        androidx.tv.material3.Card(
            onClick = { app.accounts.client?.let { player.play(episode, series, it) } },
            modifier = Modifier.width(210.dp).height(118.dp),
            shape = androidx.tv.material3.CardDefaults.shape(RoundedCornerShape(10.dp)),
            border = androidx.tv.material3.CardDefaults.border(focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(2.dp, Color.White))),
        ) {
            Box(Modifier.fillMaxSize()) {
                RemoteImage(episode.image ?: backdrop ?: series.cover, Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().background(Color(0xFF242424))) }
                val fraction = progress?.fraction ?: 0.0
                if (fraction > 0) Box(Modifier.align(Alignment.BottomCenter).padding(7.dp).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))) {
                    Box(Modifier.fillMaxWidth(fraction.toFloat()).height(3.dp).background(FluxBlue, RoundedCornerShape(50)))
                }
            }
        }
        Text("Épisode ${episode.number}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        ownTitle(episode)?.let { Text(it, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        episode.plot?.let { Text(it, fontSize = 10.sp, color = Secondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

/** What the title says beyond the series and the episode's number. */
private fun ownTitle(episode: Episode): String? {
    var title = episode.title
    Regex("S\\d+\\s*E\\d+", RegexOption.IGNORE_CASE).find(title)?.let { title = title.substring(it.range.last + 1) }
    val trimmed = title.trim { it in " -–:·." }
    return if (trimmed.isEmpty() || trimmed.lowercase() == "épisode ${episode.number}") null else trimmed
}

/** Channels, films and series by name. */
@Composable
fun SearchScreen() {
    val app = LocalApp.current
    val player = LocalTVPlayer.current
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(SearchResults()) }
    val open = LocalOpenRoute.current
    LaunchedEffect(query) {
        delay(250)
        results = app.catalog.search(query)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 15.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            TextInput(query, { query = it }, "Chaînes, films, séries", Modifier.padding(horizontal = 40.dp).width(520.dp))
        }
        if (results.live.isNotEmpty()) item {
            Shelf("Chaînes") {
                items(results.live, key = { it.id }) { group ->
                    ChannelCard(group, 200.dp) { app.accounts.client?.let { player.watch(group, results.live, it) } }
                }
            }
        }
        if (results.movies.isNotEmpty()) item {
            Shelf("Films") {
                items(results.movies, key = { it.id }) { m -> PosterCard(DisplayName.clean(m.name), m.poster) { open(Route.MovieRoute(m)) } }
            }
        }
        if (results.series.isNotEmpty()) item {
            Shelf("Séries") {
                items(results.series, key = { it.id }) { s -> PosterCard(DisplayName.clean(s.name), s.cover) { open(Route.SeriesRoute(s)) } }
            }
        }
        if (results.isEmpty && query.isNotBlank()) item {
            Text("Aucun résultat pour « $query »", Modifier.fillMaxWidth().padding(top = 40.dp), color = Secondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

/** Set by "Suivant" on the keyboard: the next field opens it again, so a
 *  form is filled in one go. */
private var keepTyping = false

/** A text field that reads as a TV row: white border when focused. The
 *  keyboard opens only on OK, as with the TV's own fields: opened as soon as
 *  focus passed by, it took over the remote, and nothing beside the fields
 *  could be reached. */
@Composable
fun TextInput(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, password: Boolean = false, next: Boolean = true) {
    var focused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(editing) { if (editing) keyboard?.show() }
    BasicTextField(
        value = value,
        onValueChange = onChange,
        readOnly = !editing,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
        cursorBrush = SolidColor(if (editing) FluxBlue else Color.Transparent),
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            imeAction = if (next) ImeAction.Next else ImeAction.Done,
            keyboardType = if (password) androidx.compose.ui.text.input.KeyboardType.Password else androidx.compose.ui.text.input.KeyboardType.Text,
            autoCorrectEnabled = false,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onNext = {
                editing = false
                keepTyping = true
                focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
                // Taken by the next field when it is one; a button leaves the keyboard closed.
                keepTyping = false
                keyboard?.hide()
            },
            onDone = { editing = false; keyboard?.hide() },
        ),
        // OK starts typing; until then, left and right go to what's beside
        // the field. While typing, they move the cursor through the text.
        modifier = modifier.onFocusChanged {
            focused = it.isFocused
            if (!it.isFocused) editing = false
            else if (keepTyping) { keepTyping = false; editing = true }
        }.onPreviewKeyEvent { event ->
            if (event.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
            when (event.nativeKeyEvent.keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_NUMPAD_ENTER ->
                    if (!editing) { editing = true; true } else false
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> !editing && focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Left)
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> !editing && focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Right)
                // A single line: up and down leave it, typing or not.
                android.view.KeyEvent.KEYCODE_DPAD_UP -> focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Up)
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
                // The keyboard closed already: back ends typing, and left and right go beside again.
                android.view.KeyEvent.KEYCODE_BACK -> if (editing) { editing = false; keyboard?.hide(); true } else false
                else -> false
            }
        },
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().background(Color.White.copy(alpha = if (focused) 0.16f else 0.08f), RoundedCornerShape(10.dp))
                .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (value.isEmpty()) Text(placeholder, color = Tertiary, fontSize = 16.sp)
                inner()
            }
        },
    )
}
