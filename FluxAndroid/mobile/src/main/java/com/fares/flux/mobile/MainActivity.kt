package com.fares.flux.mobile

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
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.outlined.ViewTimeline
import androidx.compose.material.icons.outlined.Tv
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
import com.fares.flux.core.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var app: AppState
    private lateinit var player: TVPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        app = AppState(applicationContext)
        // Images through the same client as the rest, so the DNS picked applies to them too.
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(applicationContext).callFactory(com.fares.flux.core.Net.callFactory).build())
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

private enum class Tab(private val french: String, private val english: String) {
    LIVE("Direct", "Live"), GUIDE("Guide", "Guide"), MOVIES("Films", "Movies"), SERIES("Séries", "Series"), SEARCH("Recherche", "Search"), SETTINGS("Réglages", "Settings");

    val title: String get() = tr(french, english)
}

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
    // By name: tabs come and go (Guide, Films…), the one in use stays.
    var current by rememberSaveable { mutableStateOf(Tab.LIVE) }
    var route by remember { mutableStateOf<Route?>(null) }
    when {
        client == null -> SignInScreen()
        player.video != null -> VideoScreen()
        player.showsLive -> LiveWatch()
        route != null -> RoutePage(route!!) { route = null }
        else -> CompositionLocalProvider(LocalOpenRoute provides { route = it }) {
            val visible = Tab.entries.filter { t ->
                when (t) {
                    // With something in them, and unless turned off.
                    Tab.MOVIES -> app.showsMoviesTab
                    Tab.SERIES -> app.showsSeriesTab
                    // Only turned on, and with something to show.
                    Tab.GUIDE -> app.showsGuideTab
                    else -> true
                }
            }
            // The tab in use gone (the guide turned off): back to Direct.
            val tab = current.takeIf { it in visible } ?: Tab.LIVE
            LaunchedEffect(tab) { if (current != tab) current = tab }
            GlassScaffold(visible.map { t ->
                // The nearest to iOS's SF Symbols: dot.radiowaves, calendar.day.timeline, film, tv, gearshape.
                GlassTab(t.title, when (t) {
                    Tab.LIVE -> Icons.Default.Sensors
                    Tab.GUIDE -> Icons.Outlined.ViewTimeline
                    Tab.MOVIES -> Icons.Default.Theaters
                    Tab.SERIES -> Icons.Outlined.Tv
                    Tab.SEARCH -> Icons.Default.Search
                    Tab.SETTINGS -> Icons.Default.Settings
                }, isSearch = t == Tab.SEARCH)
            }, visible.indexOf(tab), { current = visible[it] }) {
                // Tabs fade into each other, as iOS's do.
                androidx.compose.animation.Crossfade(tab, animationSpec = androidx.compose.animation.core.tween(220), label = "tab") { shown ->
                saved.SaveableStateProvider(shown.name) {
                    when (shown) {
                        Tab.LIVE -> LiveScreen()
                        Tab.GUIDE -> GuideScreen()
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
