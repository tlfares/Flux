package com.fares.flux.tv

import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import com.fares.flux.core.AppState
import com.fares.flux.core.OpenGuide
import com.fares.flux.core.TVPlayer
import com.fares.flux.tv.ui.ChannelsScreen
import com.fares.flux.tv.ui.FluxTheme
import com.fares.flux.tv.ui.GuideScreen
import com.fares.flux.tv.ui.HomeScreen
import com.fares.flux.tv.ui.LiveScreen
import com.fares.flux.tv.ui.LocalApp
import com.fares.flux.tv.ui.LocalTVPlayer
import com.fares.flux.tv.ui.MoviesScreen
import com.fares.flux.tv.ui.SearchScreen
import com.fares.flux.tv.ui.SeriesScreen
import com.fares.flux.tv.ui.SettingsScreen
import com.fares.flux.tv.ui.SignInScreen
import com.fares.flux.tv.ui.VideoScreen
import com.fares.flux.tv.ui.Route
import com.fares.flux.tv.ui.RoutePage
import com.fares.flux.tv.ui.LocalOpenRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var app: AppState
    private lateinit var player: TVPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The whole screen, as on a TV (phones and some boxes show bars).
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        app = AppState(applicationContext)
        player = TVPlayer(this, app)
        setContent {
            FluxTheme {
                CompositionLocalProvider(LocalApp provides app, LocalTVPlayer provides player) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) { Root() }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        app.epg.save(app.epgFile)
        // Nothing plays behind the home screen.
        if (player.showsLive) player.closeLive()
        if (player.video != null) player.closeVideo()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}

private val allTabs = listOf("Accueil", "Direct", "Guide", "Films", "Séries", "Recherche", "Réglages")

@Composable
private fun Root() {
    val app = LocalApp.current
    val player = LocalTVPlayer.current
    val client = app.accounts.client
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext

    LaunchedEffect(app.accounts.activeID, app.settings.sharesFavorites) {
        app.library.use(app.accounts.activeID, app.settings.sharesFavorites)
    }
    LaunchedEffect(client?.credentials) {
        if (client == null) return@LaunchedEffect
        app.catalog.start(client)
        app.accounts.refreshInfo()
    }
    // The open guide, then whenever the channels change.
    var openGuideSize by remember { mutableIntStateOf(0) }
    val openGuide = remember { mutableListOf<Map<String, List<com.fares.flux.core.Programme>>>() }
    LaunchedEffect(Unit) {
        val guide = withContext(Dispatchers.IO) { OpenGuide.load(context) }
        openGuide.add(guide)
        openGuideSize = guide.size
    }
    LaunchedEffect(app.catalog.guide, openGuideSize) {
        openGuide.lastOrNull()?.let { app.epg.useOpenGuide(it, app.catalog.guide.groups) }
    }
    // Back after a long while: the catalogue may have moved.
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            val updated = app.catalog.updated
            if (event == Lifecycle.Event.ON_START && client != null && updated != null && System.currentTimeMillis() - updated > 6 * 3600 * 1000L) {
                app.scope.launch { app.catalog.refresh(client) }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(app.accounts.activeID) { if (player.showsLive) player.closeLive() }

    // Kept while a page or the player covers the tabs: the tab, the
    // category picked in each.
    val saved = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    var route by remember { androidx.compose.runtime.mutableStateOf<Route?>(null) }
    when {
        client == null -> SignInScreen()
        player.video != null -> VideoScreen()
        player.showsLive -> LiveScreen()
        route != null -> RoutePage(route!!) { route = null }
        else -> CompositionLocalProvider(LocalOpenRoute provides { route = it }) {
            saved.SaveableStateProvider("tabs") { Tabs(tab) { tab = it } }
        }
    }
}

@Composable
private fun Tabs(tab: Int, select: (Int) -> Unit) {
    val app = LocalApp.current
    // A playlist without films or series (the free channels) shows none.
    val isPanel = app.accounts.client?.isPanel ?: true
    val tabs = allTabs.filter { t ->
        when (t) {
            "Films" -> isPanel || !app.catalog.movies.isEmpty
            "Séries" -> isPanel || !app.catalog.series.isEmpty
            else -> true
        }
    }
    var tabsFocused by remember { androidx.compose.runtime.mutableStateOf(false) }
    val tabRow = remember { FocusRequester() }
    // Back from the content goes up to the tabs; from the tabs, to Accueil, then out.
    BackHandler(enabled = !tabsFocused || tab != 0) {
        if (!tabsFocused) runCatching { tabRow.requestFocus() } else select(0)
    }
    Column(Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = tab,
            // Up from anywhere lands on the tab in use, not the one nearest
            // above (from the wide hero, Recherche).
            modifier = Modifier.padding(start = 40.dp, top = 18.dp, bottom = 8.dp).onFocusChanged { tabsFocused = it.hasFocus }
                .focusRestorer(tabRow),
        ) {
            tabs.forEachIndexed { i, title ->
                // Focus comes back to the tab in use, not the first one,
                // which would switch to Accueil.
                Tab(selected = tab == i, onFocus = { select(i) }, modifier = if (tab == i) Modifier.focusRequester(tabRow) else Modifier) {
                    Text(title, Modifier.padding(horizontal = 14.dp, vertical = 6.dp), fontSize = 15.sp)
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
            when (tabs.getOrNull(tab)) {
                "Accueil" -> HomeScreen()
                "Direct" -> ChannelsScreen()
                "Guide" -> GuideScreen()
                "Films" -> MoviesScreen()
                "Séries" -> SeriesScreen()
                "Recherche" -> SearchScreen()
                else -> SettingsScreen()
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { tabRow.requestFocus() } }
}
