package com.fares.flux.mobile

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.fares.flux.core.Programme
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(controls: Boolean, modifier: Modifier = Modifier) {
    val player = LocalPlayer.current
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                this.player = player.player
                useController = controls
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                setShowBuffering(if (controls) PlayerView.SHOW_BUFFERING_ALWAYS else PlayerView.SHOW_BUFFERING_NEVER)
                keepScreenOn = true
            }
        },
        modifier = modifier.background(Color.Black),
        onRelease = { it.player = null },
    )
}

/** Bars hidden while true, as a player on the whole screen. */
@Composable
private fun Immersive(on: Boolean) {
    val activity = LocalContext.current as Activity
    DisposableEffect(on) {
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (on) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/** Live TV, as on iOS: upright, the picture on top with what's on and the
 *  channels under it; on its side or full screen, the picture alone. A swipe
 *  up or down on the picture zaps, like the list. */
@Composable
fun LiveWatch() {
    val player = LocalPlayer.current
    val app = LocalApp.current
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var fullScreen by remember { mutableStateOf(false) }
    BackHandler { if (fullScreen) fullScreen = false else player.closeLive() }
    LaunchedEffect(player.channel?.id) { player.channel?.let { g -> app.accounts.client?.let { app.epg.load(g, it, 4) } } }

    BoxWithConstraints(Modifier.fillMaxSize().background(Background)) {
        val wide = maxWidth > 840.dp && maxWidth > maxHeight
        val alone = fullScreen || (landscape && !wide)
        Immersive(alone)
        when {
            alone -> Picture(Modifier.fillMaxSize(), fullScreen = true) { fullScreen = !fullScreen }
            wide -> Row(Modifier.fillMaxSize().statusBarsPadding()) {
                ChannelColumn(Modifier.width(360.dp).fillMaxHeight())
                Column(Modifier.weight(1f).padding(end = 16.dp, top = 8.dp)) {
                    Picture(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black, RoundedCornerShape(16.dp)), false) { fullScreen = true }
                    NowCard()
                }
            }
            else -> Column(Modifier.fillMaxSize()) {
                Picture(Modifier.statusBarsPadding().fillMaxWidth().aspectRatio(16f / 9f), false) { fullScreen = true }
                NowCard()
                ChannelColumn(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Picture(modifier: Modifier, fullScreen: Boolean, toggle: () -> Unit) {
    val player = LocalPlayer.current
    var controls by remember { mutableStateOf(true) }
    var shownAt by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(shownAt, controls, player.isPaused) { if (controls && !player.isPaused) { delay(4_000); controls = false } }
    Box(
        modifier.background(Color.Black)
            .clickable { controls = !controls; shownAt = System.currentTimeMillis() }
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = {
                    // Up: the channel below in the list; down: the one above.
                    if (total < -80) player.zap(1) else if (total > 80) player.zap(-1)
                }) { _, dy -> total += dy }
            },
    ) {
        VideoSurface(false, Modifier.fillMaxSize())
        if (player.isBuffering && player.error == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        player.error?.let { Text(it, Modifier.align(Alignment.Center).padding(20.dp), color = Secondary) }
        if (controls) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f))) {
                Row(Modifier.align(Alignment.TopStart).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundButton(Icons.Default.KeyboardArrowDown, "Fermer") { player.closeLive() }
                    player.channel?.let { Text(it.name, Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold) }
                }
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundButton(Icons.Default.KeyboardArrowUp, "Chaîne précédente") { player.zap(-1); shownAt = System.currentTimeMillis() }
                    RoundButton(if (player.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, "Pause", big = true) { player.togglePause() }
                    RoundButton(Icons.Default.KeyboardArrowDown, "Chaîne suivante") { player.zap(1); shownAt = System.currentTimeMillis() }
                }
                Box(Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
                    RoundButton(if (fullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "Plein écran", onClick = toggle)
                }
            }
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, big: Boolean = false, onClick: () -> Unit) {
    // Glass over the picture: a video surface can't be blurred, so a darker frost.
    IconButton(onClick, Modifier.size(if (big) 60.dp else 44.dp).background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50)).glassPanel(RoundedCornerShape(50))) {
        Icon(icon, label, Modifier.size(if (big) 34.dp else 26.dp), tint = Color.White)
    }
}

/** What's on: the programme's still when the guide has one, its time and what follows. */
@Composable
private fun NowCard() {
    val player = LocalPlayer.current
    val app = LocalApp.current
    val group = player.channel ?: return
    val upcoming = app.epg.upcoming(app.epg.source(group))
    val now = upcoming.firstOrNull { it.isOn }
    val next = upcoming.firstOrNull { it.start >= (now?.end ?: 0) }
    Row(Modifier.padding(16.dp).fillMaxWidth().glassPanel(RoundedCornerShape(22.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (now?.image != null) Box(Modifier.width(120.dp).aspectRatio(16f / 9f).background(Color.Black, RoundedCornerShape(10.dp))) {
            RemoteImage(now.image, Modifier.fillMaxSize())
        } else ChannelLogo(group, 64.dp, 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("EN DIRECT · ${group.name}", color = FluxBlue, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text(now?.title ?: group.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (now != null) {
                Text(now.timeRange, color = Secondary, fontSize = 12.sp)
                LiveProgress(now, Modifier.fillMaxWidth())
            }
            next?.let { Text("À suivre · ${Programme.clock(it.start)}  ${it.title}", color = Tertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** The list being zapped through, the channel playing in view. */
@Composable
private fun ChannelColumn(modifier: Modifier) {
    val player = LocalPlayer.current
    val app = LocalApp.current
    val lineup = player.lineup
    val state = rememberLazyListState(maxOf(0, lineup.indexOfFirst { it.id == player.channel?.id } - 1))
    LazyColumn(modifier.navigationBarsPadding(), state = state, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(lineup, key = { it.id }) { g ->
            ChannelRow(g, g.id == player.channel?.id) { v -> app.accounts.client?.let { player.watch(g, lineup, it, v) } }
        }
    }
}

/** Films and episodes: Media3's player on the whole screen, sideways. */
@Composable
fun VideoScreen() {
    val player = LocalPlayer.current
    val activity = LocalContext.current as Activity
    BackHandler { player.closeVideo() }
    Immersive(true)
    DisposableEffect(Unit) {
        val before = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity.requestedOrientation = before }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(true, Modifier.fillMaxSize())
        player.error?.let { Text(it, Modifier.align(Alignment.Center).padding(20.dp)) }
    }
}
