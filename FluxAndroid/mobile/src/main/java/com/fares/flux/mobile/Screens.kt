package com.fares.flux.mobile

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where a poster leads. */
sealed interface Route {
    data class MovieRoute(val movie: Movie) : Route
    data class SeriesRoute(val series: Series) : Route
}

val LocalOpenRoute = staticCompositionLocalOf<(Route) -> Unit> { {} }

@Composable
fun ScreenTitle(title: String) {
    Text(title, Modifier.statusBarsPadding().padding(start = 20.dp, top = 12.dp, bottom = 4.dp), fontSize = 32.sp, fontWeight = FontWeight.Bold)
}

// Direct

@Composable
fun LiveScreen() {
    val app = LocalApp.current
    val player = LocalPlayer.current
    var selection by rememberSaveable { mutableStateOf(Categories.ALL) }
    val cards = app.settings.liveCards
    val channels = app.channels(selection)
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Direct")
        if (app.catalog.guide.isEmpty) { LoadingOrEmpty("Aucune chaîne"); return@Column }
        CategoryBar(Categories.entries(app.catalog.live.categories, recent = true), selection) { selection = it }
        if (channels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(when (selection) {
                    Categories.FAVORITES -> "Aucun favori. Maintiens une chaîne pour l’ajouter."
                    Categories.RECENT -> "Les chaînes regardées apparaîtront ici."
                    else -> "Catégorie vide"
                }, color = Secondary)
            }
            return@Column
        }
        LazyVerticalGrid(
            GridCells.Adaptive(if (cards) 160.dp else 300.dp), Modifier.fillMaxSize(),
            contentPadding = listPadding(),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(if (cards) 16.dp else 8.dp),
        ) {
            items(channels, key = { it.id }) { group ->
                // Zaps within the category; through every channel from Récents.
                val play: (com.fares.flux.core.ChannelVariant?) -> Unit = { v ->
                    app.accounts.client?.let { player.watch(group, if (selection == Categories.RECENT) app.catalog.guide.groups else channels, it, v) }
                }
                if (cards) ChannelCard(group, player.channel?.id == group.id, play) else ChannelRow(group, player.channel?.id == group.id, play)
            }
        }
    }
}

// Films and series

@Composable
private fun <T> PosterBrowser(
    title: String, empty: String, categories: List<com.fares.flux.core.Category>, isEmpty: Boolean, items: List<T>,
    selection: String, onSelect: (String) -> Unit, key: (T) -> Any, name: (T) -> String, image: (T) -> String?,
    progress: (T) -> Double? = { null }, favorite: (T) -> Favorite, route: (T) -> Route,
) {
    val app = LocalApp.current
    val open = LocalOpenRoute.current
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(title)
        if (isEmpty) { LoadingOrEmpty(empty); return@Column }
        CategoryBar(Categories.entries(categories, recent = false), selection, onSelect)
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (selection == Categories.FAVORITES) "Aucun favori. Maintiens une affiche pour l’ajouter." else "Rien ici", color = Secondary)
            }
            return@Column
        }
        LazyVerticalGrid(GridCells.Adaptive(108.dp), Modifier.fillMaxSize(), contentPadding = listPadding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items, key = key) { item ->
                Poster(name(item), image(item), progress(item), onLongClick = { app.library.toggleFavorite(favorite(item)) }) { open(route(item)) }
            }
        }
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
        Categories.ALL -> app.catalog.movies.items
        else -> app.catalog.movies.items(selection)
    }
    PosterBrowser("Films", "Aucun film", app.catalog.movies.categories, app.catalog.movies.isEmpty, movies, selection, { selection = it },
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
        Categories.ALL -> app.catalog.series.items
        else -> app.catalog.series.items(selection)
    }
    PosterBrowser("Séries", "Aucune série", app.catalog.series.categories, app.catalog.series.isEmpty, series, selection, { selection = it },
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

/** A film or series page: its picture across the top, the text and buttons under it. */
@Composable
private fun DetailPage(title: String, backdrop: String?, poster: String?, facts: List<String>, plot: String?, close: () -> Unit,
                       actions: @Composable () -> Unit, below: @Composable () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
                RemoteImage(backdrop ?: poster, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent, Background))))
                IconButton(close, Modifier.statusBarsPadding().padding(8.dp).background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50))) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp).widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), color = Secondary, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { actions() }
                plot?.let { Text(it, color = Secondary, fontSize = 14.sp) }
                below()
            }
        }
    }
}

@Composable
private fun FavoriteButton(favorite: Favorite) {
    val app = LocalApp.current
    FilledTonalButton({ app.library.toggleFavorite(favorite) }) { Text(if (app.library.isFavorite(favorite)) "★ Favori" else "☆ Favori") }
}

@Composable
private fun MovieDetail(movie: Movie, close: () -> Unit) {
    val app = LocalApp.current
    val player = LocalPlayer.current
    var details by remember { mutableStateOf<MovieDetails?>(null) }
    LaunchedEffect(movie.id) { details = app.accounts.client?.let { runCatching { it.movieDetails(movie.id) }.getOrNull() } ?: MovieDetails() }
    val d = details
    val resumable = app.library.progress[Library.key(MediaKind.MOVIE, movie.id)]?.isResumable == true
    DetailPage(DisplayName.clean(movie.name), d?.backdrop, d?.poster ?: movie.poster,
        listOfNotNull(d?.year, d?.duration, d?.genre, movie.rating?.let { "★ " + "%.1f".format(it) }), d?.plot, close, actions = {
            Button({ app.accounts.client?.let { player.play(movie, it) } }) { Text(if (resumable) "▶  Reprendre" else "▶  Lecture") }
            if (resumable) FilledTonalButton({ app.accounts.client?.let { player.play(movie, it, fromStart = true) } }) { Text("↺ Début") }
            FavoriteButton(Favorite.movie(movie))
        }) {
        d?.director?.let { Text("Réalisation · $it", color = Tertiary, fontSize = 13.sp) }
        d?.cast?.let { Text("Avec · $it", color = Tertiary, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
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
        listOfNotNull(d?.year ?: series.year, d?.genre, seasons.size.takeIf { it > 0 }?.let { if (it == 1) "1 saison" else "$it saisons" }), d?.plot, close,
        actions = {
            next()?.let { e -> Button({ app.accounts.client?.let { player.play(e, series, it) } }) { Text("▶  S${e.season} E${e.number}") } }
            FavoriteButton(Favorite.series(series))
        }) {
        when {
            d == null && !failed -> CircularProgressIndicator()
            seasons.isEmpty() -> Text("Aucun épisode.", color = Secondary)
            else -> {
                val current = season ?: seasons[0]
                if (seasons.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(seasons) { s ->
                        Text("Saison $s", Modifier.background(if (s == current) Brush.horizontalGradient(listOf(FluxBlue, FluxViolet)) else Brush.horizontalGradient(listOf(Card, Card)), RoundedCornerShape(50)).clickable { season = s }
                            .padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                (d?.seasons?.get(current) ?: emptyList()).forEach { e ->
                    val p = app.library.progress[Library.key(MediaKind.SERIES, e.id)]
                    Row(Modifier.fillMaxWidth().clickable { app.accounts.client?.let { player.play(e, series, it) } }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.width(140.dp).aspectRatio(16f / 9f).background(Card, RoundedCornerShape(10.dp))) {
                            RemoteImage(e.image ?: d?.backdrop ?: series.cover, Modifier.fillMaxSize())
                            p?.fraction?.takeIf { it > 0 }?.let { f ->
                                Box(Modifier.align(Alignment.BottomCenter).padding(5.dp).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.25f))) {
                                    Box(Modifier.fillMaxWidth(f.toFloat()).height(3.dp).background(FluxBlue))
                                }
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Épisode ${e.number}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            e.plot?.let { Text(it, color = Secondary, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
    }
}

// Search

@Composable
fun SearchScreen() {
    val app = LocalApp.current
    val player = LocalPlayer.current
    val open = LocalOpenRoute.current
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(SearchResults()) }
    LaunchedEffect(query) { delay(250); results = app.catalog.search(query) }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Recherche")
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), placeholder = { Text("Chaînes, films, séries") },
            leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(50))
        LazyVerticalGrid(GridCells.Adaptive(108.dp), Modifier.fillMaxSize(), contentPadding = listPadding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (results.live.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { Text("Chaînes", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
                items(results.live, key = { "l" + it.id }, span = { GridItemSpan(maxLineSpan) }) { g ->
                    ChannelRow(g, player.channel?.id == g.id) { v -> app.accounts.client?.let { player.watch(g, results.live, it, v) } }
                }
            }
            if (results.movies.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { Text("Films", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
                items(results.movies, key = { "m" + it.id }) { m -> Poster(DisplayName.clean(m.name), m.poster) { open(Route.MovieRoute(m)) } }
            }
            if (results.series.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { Text("Séries", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
                items(results.series, key = { "s" + it.id }) { s -> Poster(DisplayName.clean(s.name), s.cover) { open(Route.SeriesRoute(s)) } }
            }
            if (results.isEmpty && query.isNotBlank()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Aucun résultat pour « $query »", color = Secondary, modifier = Modifier.padding(top = 40.dp))
            }
        }
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
    if (adding) { SignInScreen { adding = false }; BackHandler { adding = false }; return }
    val s = app.settings
    val c = app.catalog
    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(top = 0.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ScreenTitle("Réglages") }
        item {
            GlassCard(Modifier.widthIn(max = 720.dp)) {
                CardTitle("Comptes", Icons.Default.Person)
                app.accounts.accounts.forEachIndexed { i, a ->
                    if (i > 0) HairlineDivider()
                    Row(Modifier.fillMaxWidth().combinedClickable(onLongClick = { removing = a }) { app.accounts.select(a.id) },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val on = a.id == app.accounts.activeID
                        Icon(if (on) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (on) FluxBlue else Tertiary)
                        Column(Modifier.weight(1f)) {
                            Text(a.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(a.subtitle, color = Secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Ajouter") { adding = true }
                }
                Footnote("Touche un compte pour l’utiliser, maintiens-le pour le supprimer.")
            }
        }
        if (app.accounts.active != null) item { SendCard() }
        item {
            CollapsibleCard("Lecture", Icons.Default.PlayCircle, "playback") {
                Text("Qualité par défaut", fontSize = 14.sp, color = Secondary)
                SegmentedPicker(QualityPreference.entries, s.quality, { it.title }, {
                    when (it) { QualityPreference.BEST -> Icons.Default.AutoAwesome; QualityPreference.HD -> Icons.Default.Tv; else -> Icons.Default.Eco }
                }) { s.changeQuality(it) }
                Footnote("Quand une chaîne existe en plusieurs qualités. Celle choisie à la main pour une chaîne reste la sienne.")
                HairlineDivider()
                MenuPicker("Numérotation", ChannelNumbering.entries, s.numbering, { it.title }) { s.changeNumbering(it); c.optionsChanged() }
                Footnote(if (s.numbering == ChannelNumbering.PROVIDER) "Les numéros et l’ordre de ton abonnement." else "Les chaînes du plan d’abord, les autres suivent.")
                HairlineDivider()
                ToggleRow("Chaînes en différé", s.showsShifted) { s.changeShowsShifted(it); c.optionsChanged() }
                Footnote("Les versions décalées (« TF1 +4 », diffusée 4 h plus tard) se choisissent depuis leur chaîne au lieu d’être masquées.")
                if (app.accounts.active?.credentials?.source != SourceKind.FREE) {
                    HairlineDivider()
                    ToggleRow("Flux gratuits en priorité", s.prefersFree) { s.changePrefersFree(it); c.optionsChanged() }
                    Footnote("France 2, Arte, BFM TV… se lisent depuis le flux officiel de la chaîne, plus stable, sans occuper un écran de ton abonnement. Celui de l’abonnement prend le relais s’il ne répond pas.")
                }
            }
        }
        item {
            val modes = listOf("light", "dark", "system")
            fun modeTitle(m: String) = when (m) { "light" -> "Clair"; "dark" -> "Sombre"; else -> "Système" }
            CollapsibleCard("Affichage", Icons.Default.Palette, "display") {
                Text("Mode", fontSize = 14.sp, color = Secondary)
                SegmentedPicker(modes, s.appearance, ::modeTitle, {
                    when (it) { "light" -> Icons.Default.LightMode; "dark" -> Icons.Default.DarkMode; else -> Icons.Default.BrightnessMedium }
                }) { s.changeAppearance(it) }
                HairlineDivider()
                Text("Chaînes", fontSize = 14.sp, color = Secondary)
                SegmentedPicker(listOf(false, true), s.liveCards, { if (it) "Cartes" else "Liste" }, {
                    if (it) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList
                }) { s.changeLiveCards(it) }
                Footnote(if (s.liveCards) "Des cartes avec l’image du programme, comme sur la TV." else "Une ligne par chaîne, la plus compacte.")
            }
        }
        item {
            CollapsibleCard("Favoris", Icons.Default.Star, "favorites") {
                ToggleRow("Communs à tous les comptes", s.sharesFavorites) { s.changeSharesFavorites(it) }
                Footnote(if (s.sharesFavorites) "Une seule liste, retrouvée par nom chez chaque fournisseur." else "Chaque compte a sa propre liste.")
            }
        }
        item {
            CollapsibleCard("Catalogue", Icons.Default.Layers, "data") {
                Footnote("${c.guide.groups.size} chaînes · ${c.movies.items.size} films · ${c.series.items.size} séries. Gardé sur l’appareil et actualisé en arrière-plan à l’ouverture.")
                GlassButton(if (c.isLoading) "Actualisation…" else "↻  Actualiser", modifier = Modifier.fillMaxWidth()) {
                    if (!c.isLoading) app.accounts.client?.let { cl -> scope.launch { c.refresh(cl) } }
                }
            }
        }
        item {
            Text("Flux ${LocalContext.current.packageManager.getPackageInfo(LocalContext.current.packageName, 0).versionName}",
                Modifier.fillMaxWidth().padding(top = 8.dp), color = Tertiary, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
    removing?.let { a ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text("Supprimer « ${a.title} » ?") },
            text = { Text("Ses identifiants, son catalogue et ses reprises quittent cet appareil.") },
            confirmButton = { TextButton({ app.catalog.forget(a.credentials); app.library.forget(a.id); app.accounts.remove(a.id); removing = null }) { Text("Supprimer", color = Color(0xFFFF453A)) } },
            dismissButton = { TextButton({ removing = null }) { Text("Annuler") } }, containerColor = if (Palette.dark) Color(0xFF1E1E26) else Color(0xFFF4F4F8), shape = RoundedCornerShape(28.dp))
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
            Text("Importer un compte", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Sur l’appareil qui a le compte : Réglages › Comptes › Envoyer à un appareil, puis touche « Android » avec ce code.",
                color = Secondary, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(receiver.code.toCharArray().joinToString("  "), fontSize = 40.sp, fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(brush = Brush.horizontalGradient(listOf(FluxBlue, FluxViolet))))
            if (checking) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = FluxBlue)
            error?.let { Text(it, color = Color(0xFFFF453A), fontSize = 13.sp) }
            Text("Les deux appareils doivent être sur le même Wi-Fi.", color = Tertiary, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            GlassButton("Annuler", modifier = Modifier.fillMaxWidth()) { dismiss() }
        }
    }
}

/** The first screen, and adding an account later: typed in, or sent from the iPhone. */
@Composable
fun SignInScreen(onDone: (() -> Unit)? = null) {
    val app = LocalApp.current
    val context = LocalContext.current
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
    fun signIn() {
        if (checking || !complete) return
        scope.launch {
            checking = true; error = null
            val credentials = if (kind == SourceKind.PLAYLIST) Credentials.playlist(server) else Credentials(server.trim(), username.trim(), password)
            runCatching { app.accounts.add(Account(name = name.trim(), credentials = credentials)) }
                .onSuccess { onDone?.invoke() }.onFailure { error = it.readableMessage }
            checking = false
        }
    }
    if (importing) ImportSheet({ importing = false }) { importing = false; onDone?.invoke() }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally, contentPadding = PaddingValues(20.dp)) {
        item {
            Column(Modifier.widthIn(max = 480.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Spacer(Modifier.height(24.dp))
                FluxLogo(88.dp)
                Text("Flux", fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Text("Ajoute ton abonnement IPTV, ou regarde les chaînes gratuites.", color = Secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                if (app.accounts.accounts.none { it.credentials.source == SourceKind.FREE }) {
                    // The free channels, with nothing to type: for trying the app, or without a subscription.
                    GlassCard(Modifier.fillMaxWidth().clickable { app.accounts.addFree(); onDone?.invoke() }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(Icons.Default.LiveTv, null, tint = FluxBlue)
                            Column(Modifier.weight(1f)) {
                                Text("Regarder les chaînes gratuites", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text("TNT, info, France 3 régions et Pluto TV. Sans compte.", color = Secondary, fontSize = 13.sp)
                            }
                            Text("›", color = Tertiary, fontSize = 22.sp)
                        }
                    }
                }
                GlassButton("⤓  Importer depuis un autre appareil", modifier = Modifier.fillMaxWidth()) { importing = true }
                SegmentedPicker(listOf(SourceKind.XTREAM, SourceKind.PLAYLIST), kind, { if (it == SourceKind.XTREAM) "Xtream Codes" else "Lien M3U" }) { kind = it }
                FieldGroup {
                    FieldRow(name, { name = it }, "Nom (facultatif)", Icons.Default.Badge)
                    if (kind == SourceKind.PLAYLIST) {
                        FieldRow(server, { server = it }, "Lien de la liste (https://…/liste.m3u)", Icons.Default.Dns, keyboard = KeyboardType.Uri, last = true,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Go, onDone = { signIn() })
                    } else {
                        FieldRow(server, { server = it }, "Serveur (exemple.com:8080)", Icons.Default.Dns, keyboard = KeyboardType.Uri)
                        FieldRow(username, { username = it }, "Nom d’utilisateur", Icons.Default.Person)
                        FieldRow(password, { password = it }, "Mot de passe", Icons.Default.Lock, secure = true, keyboard = KeyboardType.Password, last = true,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Go, onDone = { signIn() })
                    }
                }
                error?.let { Text(it, color = Color(0xFFFF453A), fontSize = 13.sp) }
                val ready = !checking && complete
                val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
                Box(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(FluxBlue, FluxViolet)))
                        .graphicsLayer { alpha = if (ready || checking) 1f else 0.4f }
                        .clickable(enabled = ready) {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                            signIn()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    else Text("Se connecter", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                Text("Tes identifiants restent sur cet appareil, chiffrés.", color = Tertiary, fontSize = 12.sp)
            }
        }
    }
}

/** The account in use, sent to a device nearby waiting for one. */
@Composable
private fun SendCard() {
    val app = LocalApp.current
    val context = LocalContext.current
    CollapsibleCard("Envoyer à un appareil", Icons.AutoMirrored.Filled.Send, "send") {
        // Searches only while open.
        val sender = remember { com.fares.flux.core.PairingSender(context) }
        val open = LocalCardOpen.current
        DisposableEffect(open) { if (open) sender.start(); onDispose { sender.stop() } }
        if (sender.screens.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Secondary)
            Text("Recherche des appareils à proximité…", color = Secondary, fontSize = 14.sp)
        }
        sender.screens.forEachIndexed { i, screen ->
            if (i > 0) HairlineDivider()
            val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
            Row(Modifier.fillMaxWidth().clickable { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm); sender.send(app, screen) }, verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(when (screen.kind) {
                    "Mac" -> Icons.Default.Laptop
                    "Apple TV", "Android TV" -> Icons.Default.Tv
                    "iPad" -> Icons.Default.TabletAndroid
                    else -> Icons.Default.Smartphone
                }, null, tint = FluxBlue)
                Column(Modifier.weight(1f)) {
                    Text(screen.kind, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Code ${screen.code}", color = Secondary, fontSize = 12.sp)
                }
                when (screen.code) {
                    sender.sent -> Text("Envoyé ✓", color = FluxBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    sender.failed -> Text("Échec", color = Color(0xFFFF453A), fontSize = 13.sp)
                }
            }
        }
        Footnote("Sur l’autre appareil, touche « Importer depuis un autre appareil » à la connexion, puis touche ici celui dont le code s’affiche. Le compte part avec tes favoris, tes reprises et tes réglages.")
    }
}
