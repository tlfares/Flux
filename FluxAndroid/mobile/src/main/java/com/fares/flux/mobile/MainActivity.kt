package com.fares.flux.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.fares.flux.core.AppState
import com.fares.flux.core.OpenGuide
import com.fares.flux.core.Programme
import com.fares.flux.core.TVPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var app: AppState
    private lateinit var player: TVPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        app = AppState(applicationContext)
        player = TVPlayer(this, app)
        setContent {
            FluxTheme(app.settings.appearance) {
                // Bar icons dark on white, light on black, whatever the system says.
                val dark = Palette.dark
                androidx.compose.runtime.LaunchedEffect(dark) {
                    val style = if (dark) androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                        else androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    enableEdgeToEdge(style, style)
                }
                CompositionLocalProvider(LocalApp provides app, LocalPlayer provides player) {
                    // The surface gives text its light color, as the dark theme wants.
                    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = Background) { Root() }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        app.epg.save(app.epgFile)
        // Nothing plays once Flux is out of sight.
        if (!isChangingConfigurations) {
            if (player.showsLive) player.closeLive()
            if (player.video != null) player.closeVideo()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) player.release()
    }
}

private enum class Tab(val title: String) { LIVE("Direct"), MOVIES("Films"), SERIES("Séries"), SEARCH("Recherche"), SETTINGS("Réglages") }

@Composable
private fun Root() {
    val app = LocalApp.current
    val player = LocalPlayer.current
    val client = app.accounts.client
    val context = LocalContext.current.applicationContext

    LaunchedEffect(app.accounts.activeID, app.settings.sharesFavorites) { app.library.use(app.accounts.activeID, app.settings.sharesFavorites) }
    LaunchedEffect(client?.credentials) {
        if (client == null) return@LaunchedEffect
        app.catalog.start(client)
        app.accounts.refreshInfo()
    }
    var openGuide by remember { mutableStateOf<Map<String, List<Programme>>>(emptyMap()) }
    LaunchedEffect(Unit) { openGuide = withContext(Dispatchers.IO) { OpenGuide.load(context) } }
    LaunchedEffect(app.catalog.guide, openGuide) { app.epg.useOpenGuide(openGuide, app.catalog.guide.groups) }
    LaunchedEffect(app.accounts.activeID) { if (player.showsLive) player.closeLive() }

    val saved = rememberSaveableStateHolder()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var route by remember { mutableStateOf<Route?>(null) }
    when {
        client == null -> SignInScreen()
        player.video != null -> VideoScreen()
        player.showsLive -> LiveWatch()
        route != null -> RoutePage(route!!) { route = null }
        else -> CompositionLocalProvider(LocalOpenRoute provides { route = it }) {
            // A playlist without films or series (the free channels) shows none.
            val isPanel = client.isPanel
            val visible = Tab.entries.filter { t ->
                when (t) {
                    Tab.MOVIES -> isPanel || !app.catalog.movies.isEmpty
                    Tab.SERIES -> isPanel || !app.catalog.series.isEmpty
                    else -> true
                }
            }
            GlassScaffold(visible.map { t ->
                GlassTab(t.title, when (t) {
                    Tab.LIVE -> Icons.Default.LiveTv
                    Tab.MOVIES -> Icons.Default.Movie
                    Tab.SERIES -> Icons.Default.Tv
                    Tab.SEARCH -> Icons.Default.Search
                    Tab.SETTINGS -> Icons.Default.Settings
                })
            }, tab.coerceAtMost(visible.lastIndex), { tab = it }) {
                // Tabs fade into each other, as iOS's do.
                androidx.compose.animation.Crossfade(tab, animationSpec = androidx.compose.animation.core.tween(220), label = "tab") { shown ->
                saved.SaveableStateProvider(shown) {
                    when (visible[shown.coerceAtMost(visible.lastIndex)]) {
                        Tab.LIVE -> LiveScreen()
                        Tab.MOVIES -> MoviesScreen()
                        Tab.SERIES -> SeriesScreen()
                        Tab.SEARCH -> SearchScreen()
                        Tab.SETTINGS -> SettingsScreen()
                }
                    }
                }
            }
        }
    }
}
