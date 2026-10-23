package com.fares.flux.tv.ui

import com.fares.flux.core.Backdrop
import com.fares.flux.core.rememberVideoBackdrop
import com.fares.flux.core.videoGlass

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.fares.flux.core.ChannelGroup
import com.fares.flux.core.Favorite
import com.fares.flux.core.MediaKind
import com.fares.flux.core.Programme
import com.fares.flux.core.nowSeconds
import com.fares.flux.core.tr
import kotlinx.coroutines.delay
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.offset

/** The picture alone, for the live screen, which draws its own controls. */
@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(controls: Boolean, modifier: Modifier = Modifier, onReady: (PlayerView) -> Unit = {}) {
    val player = LocalTVPlayer.current
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                this.player = player.player
                useController = controls
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                setShowBuffering(if (controls) PlayerView.SHOW_BUFFERING_ALWAYS else PlayerView.SHOW_BUFFERING_NEVER)
                isFocusable = controls
                keepScreenOn = true
                onReady(this)
            }
        },
        modifier = modifier,
        onRelease = { it.player = null },
    )
}

/** Live TV on the whole screen, as Oqee does it: the banner comes up from
 *  the bottom, up and down zap, right opens the channel list. Back closes
 *  what's open, then the screen. */
@Composable
fun LiveScreen() {
    val player = LocalTVPlayer.current
    val app = LocalApp.current
    var showsBanner by remember { mutableStateOf(true) }
    var showsList by remember { mutableStateOf(false) }
    // What left opens with the banner up: "menu" (both to choose from), "streams" or "subtitles".
    var picking by remember { mutableStateOf<String?>(null) }
    var bannerShownAt by remember { mutableStateOf(System.currentTimeMillis()) }
    val focus = remember { FocusRequester() }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    // The picture blurred under the banner, only while it's up.
    // Black while zapping or loading: no frame of the new channel yet.
    val backdrop = rememberVideoBackdrop(playerView, showsBanner && !showsList && player.hasPicture && player.error == null)

    fun show(on: Boolean) { showsBanner = on; if (on) bannerShownAt = System.currentTimeMillis() }

    // Gone again after a few seconds unless paused.
    LaunchedEffect(bannerShownAt, showsBanner, player.isPaused) {
        if (showsBanner && !player.isPaused) { delay(6_000); showsBanner = false }
    }
    LaunchedEffect(player.channel?.id) {
        show(true)
        val group = player.channel ?: return@LaunchedEffect
        app.accounts.client?.let { app.epg.load(group, it, 4) }
    }
    LaunchedEffect(showsList, picking) { if (!showsList && picking == null) runCatching { focus.requestFocus() } }

    BackHandler {
        when {
            showsList -> showsList = false
            showsBanner -> show(false)
            else -> player.closeLive()
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focus)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || showsList) return@onKeyEvent false
                when (e.key) {
                    // The list's order: up is the channel above, down the one below.
                    Key.DirectionUp, Key.ChannelDown -> { player.zap(-1); true }
                    Key.DirectionDown, Key.ChannelUp -> { player.zap(1); true }
                    Key.DirectionRight -> { showsList = true; true }
                    // Left brings the banner up; with it up, the channel's other streams and the subtitles.
                    Key.DirectionLeft -> {
                        val streams = player.channel?.hasChoice == true
                        val subtitles = player.textTracks.isNotEmpty()
                        if (!showsBanner || !(streams || subtitles)) show(true)
                        else picking = if (streams && subtitles) "menu" else if (streams) "streams" else "subtitles"
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { show(!showsBanner); true }
                    else -> when (e.key.nativeKeyCode) {
                        AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AndroidKeyEvent.KEYCODE_MEDIA_PLAY, AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            player.togglePause(); show(true); true
                        }
                        else -> false
                    }
                }
            }
            .focusable()
    ) {
        VideoSurface(controls = false, modifier = Modifier.fillMaxSize()) { playerView = it }
        if (player.isAudioOnly && player.error == null) player.channel?.let { RadioScreen(it) }
        if (player.isBuffering && player.error == null) {
            Text(tr("Chargement…", "Loading…"), Modifier.align(Alignment.Center), color = Secondary)
        }
        player.error?.let { error ->
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Pas d’image", "No picture"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(error, color = Secondary)
            }
        }
        AnimatedVisibility(showsBanner && !showsList, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            LiveBanner(backdrop)
        }
        if (player.isPaused) {
            Box(Modifier.align(Alignment.Center).size(70.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Pause, null, Modifier.size(34.dp), tint = Color.White)
            }
        }
        fun done() { picking = null; show(true) }
        when (picking) {
            "menu" -> ChoiceDialog(tr("Options", "Options"), listOf(
                "streams" to tr("Flux · ${player.variant?.quality?.title ?: ""}", "Stream · ${player.variant?.quality?.title ?: ""}"),
                "subtitles" to tr("Sous-titres · ${player.textTrack?.label ?: "désactivés"}", "Subtitles · ${player.textTrack?.label ?: "off"}"),
            ), "", ::done) { picking = it }
            "streams" -> {
                val variants = player.channel?.variants.orEmpty()
                ChoiceDialog(tr("Regarder en", "Watch in"), variants.map { "${it.id}" to it.quality.label }, "${player.variant?.id}", ::done) { id ->
                    variants.firstOrNull { "${it.id}" == id }?.takeIf { it.id != player.variant?.id }?.let(player::pick)
                    done()
                }
            }
            "subtitles" -> {
                val tracks = player.textTracks
                ChoiceDialog(tr("Sous-titres", "Subtitles"), listOf("off" to tr("Désactivés", "Off")) + tracks.mapIndexed { i, t -> "$i" to t.label },
                    player.textTrack?.let { "${tracks.indexOf(it)}" } ?: "off", ::done) { id ->
                    player.selectText(id.toIntOrNull()?.let { tracks.getOrNull(it) })
                    done()
                }
            }
        }
        AnimatedVisibility(showsList, Modifier.align(Alignment.CenterStart),
            enter = slideInHorizontally { -it } + fadeIn(), exit = slideOutHorizontally { -it } + fadeOut()) {
            Box(Modifier.fillMaxHeight().width(700.dp).background(Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = 0.92f), 0.55f to Color.Black.copy(alpha = 0.85f), 1f to Color.Transparent)),
                contentAlignment = Alignment.CenterStart) {
                ChannelList { showsList = false; show(true) }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** The banner over the bottom of the picture: the channel, what's on, how far it's got and what's next. */
@Composable
private fun LiveBanner(backdrop: Backdrop) {
    val player = LocalTVPlayer.current
    val app = LocalApp.current
    val group = player.channel ?: return
    val upcoming = app.epg.upcoming(app.epg.source(group))
    val now = upcoming.firstOrNull { it.isOn }
    val next = upcoming.firstOrNull { it.start >= (now?.end ?: nowSeconds()) }
    Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))) {
        Row(
            Modifier.padding(horizontal = 40.dp).padding(bottom = 30.dp, top = 60.dp).fillMaxWidth()
                // Glass, as on the Apple TV: the picture blurred behind, its rim catching the light.
                .videoGlass(backdrop, RoundedCornerShape(22.dp))
                .padding(horizontal = 22.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (now?.image != null) {
                    Box(Modifier.size(128.dp, 72.dp).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(11.dp))) {
                        RemoteImage(now.image, Modifier.fillMaxSize().background(Color.Transparent, RoundedCornerShape(11.dp)))
                        ChannelLogo(group, 40.dp, 22.dp, Modifier.align(Alignment.BottomStart).padding(5.dp))
                    }
                } else {
                    Box(Modifier.background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)).padding(9.dp)) { ChannelLogo(group, 95.dp, 54.dp) }
                }
                group.number?.let { Text("$it", color = Secondary, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Dot()
                        Text(tr("EN DIRECT", "LIVE"), fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                    Text(group.name, color = Secondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    val variant = player.variant
                    if (variant != null && group.hasChoice) {
                        Text(variant.quality.title, Modifier.background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    // Only for streams that have some: left opens the choice. In
                    // the line, so it never sits over the summary.
                    if (player.textTracks.isNotEmpty()) {
                        Row(Modifier.background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(if (player.textTrack != null) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionDisabled, null, Modifier.size(14.dp), tint = Color.White)
                            Text(player.textTrack?.label ?: tr("Sous-titres désactivés", "Subtitles off"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
                Text(now?.title ?: group.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (now != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(now.timeRange, color = Secondary, fontSize = 13.sp)
                    LiveProgress(now, Modifier.weight(1f, fill = false).width(260.dp), height = 3.5.dp)
                    Text(remaining(now), color = Secondary, fontSize = 13.sp)
                }
                next?.let { Text(tr("À suivre · ${Programme.clock(it.start)}  ${it.title}", "Up next · ${Programme.clock(it.start)}  ${it.title}"), color = Secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            now?.summary?.let {
                Text(it, Modifier.width(260.dp), color = Secondary, fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
        val streams = group.hasChoice
        val subtitles = player.textTracks.isNotEmpty()
        Text(tr("▲▼ Chaîne  ·  ▶ Liste des chaînes", "▲▼ Channel  ·  ▶ Channel list") + when {
            streams && subtitles -> tr("  ·  ◀ Flux et sous-titres", "  ·  ◀ Stream and subtitles")
            streams -> tr("  ·  ◀ Changer de flux", "  ·  ◀ Change stream")
            subtitles -> tr("  ·  ◀ Sous-titres", "  ·  ◀ Subtitles")
            else -> ""
        }, Modifier.align(Alignment.BottomEnd).padding(end = 62.dp, bottom = 36.dp), color = Tertiary, fontSize = 10.sp)
    }
}

private fun remaining(p: Programme): String {
    val minutes = maxOf(0, ((p.end - nowSeconds()) / 60).toInt())
    return if (minutes >= 60) tr("${minutes / 60} h ${"%02d".format(minutes % 60)} restantes", "${minutes / 60} h ${"%02d".format(minutes % 60)} left")
        else tr("$minutes min restantes", "$minutes min left")
}

/** The channels down the left of the picture, the one playing in focus. */
@Composable
private fun ChannelList(close: () -> Unit) {
    val player = LocalTVPlayer.current
    val app = LocalApp.current
    var favoritesOnly by remember { mutableStateOf(false) }
    val channels = if (favoritesOnly) app.library.favorites(MediaKind.LIVE).mapNotNull { app.catalog.guide.group(it.key) } else player.lineup
    val start = channels.indexOfFirst { it.id == player.channel?.id }.coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, start - 3))
    val current = remember { FocusRequester() }

    Column(Modifier.width(390.dp).fillMaxHeight().padding(top = 30.dp, start = 35.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusRow(onClick = { favoritesOnly = false }, isCurrent = !favoritesOnly, shape = RoundedCornerShape(50)) {
                Text(tr("Toutes les chaînes", "All channels"), Modifier.padding(horizontal = 13.dp, vertical = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            FocusRow(onClick = { favoritesOnly = true }, isCurrent = favoritesOnly, shape = RoundedCornerShape(50)) {
                Text(tr("Favoris", "Favorites"), Modifier.padding(horizontal = 13.dp, vertical = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (channels.isEmpty()) {
            Text(if (favoritesOnly) tr("Aucune chaîne favorite.", "No favorite channels.") else tr("Aucune chaîne.", "No channels."), color = Secondary, modifier = Modifier.padding(top = 20.dp))
        }
        LazyColumn(state = state, contentPadding = PaddingValues(vertical = 8.dp, horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(channels, key = { it.id }) { group ->
                val playing = group.id == player.channel?.id
                ChannelListRow(group, playing, if (playing) Modifier.focusRequester(current) else Modifier) {
                    app.accounts.client?.let { player.watch(group, if (favoritesOnly) channels else player.lineup, it) }
                    close()
                }
            }
        }
    }
    LaunchedEffect(Unit) { delay(50); runCatching { current.requestFocus() } }
}

@Composable
private fun ChannelListRow(group: ChannelGroup, playing: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val app = LocalApp.current
    val now = app.epg.now(app.epg.source(group))
    LaunchedEffect(group.id) { delay(200); app.accounts.client?.let { app.epg.load(group, it) } }
    FocusRow(onClick = onClick, isCurrent = playing, modifier = modifier.fillMaxWidth(),
        onLongClick = { app.library.toggleFavorite(Favorite.live(group)) }) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(group.number?.toString() ?: "", Modifier.width(28.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.7f))
            ChannelLogo(group, 48.dp, 27.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(group.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (playing) Icon(Icons.Default.GraphicEq, null, Modifier.padding(start = 5.dp).size(11.dp), tint = FluxBlue)
                }
                if (now != null) {
                    Text(now.title, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.7f))
                    LiveProgress(now, Modifier.width(190.dp), height = 2.dp)
                }
            }
        }
    }
}

/** Films and episodes: Media3's player, its controls and seek bar. */
@OptIn(UnstableApi::class)
@Composable
fun VideoScreen() {
    val player = LocalTVPlayer.current
    BackHandler { player.closeVideo() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(controls = true, modifier = Modifier.fillMaxSize()) { view ->
            view.post { view.requestFocus(); view.showController() }
        }
        player.error?.let {
            Text(it, Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(10.dp)).padding(16.dp))
        }
        player.video?.let { video ->
            Column(Modifier.align(Alignment.TopStart).padding(30.dp)) {
                Text(video.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                video.subtitle?.let { Text(it, color = Secondary, fontSize = 13.sp) }
            }
        }
    }
}

/** A list to pick one from, over the screen. */
@Composable
fun ChoiceDialog(title: String, options: List<Pair<String, String>>, selection: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val first = remember { FocusRequester() }
        Column(Modifier.width(420.dp).background(Color(0xFF1C1C1E), RoundedCornerShape(20.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            val state = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, options.indexOfFirst { it.first == selection } - 2))
            LazyColumn(Modifier.height(minOf(380, 46 * options.size + 10).dp), state = state, verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(4.dp)) {
                items(options, key = { it.first }) { (id, name) ->
                    FocusRow(onClick = { onPick(id) }, isCurrent = id == selection,
                        modifier = Modifier.fillMaxWidth().then(if (id == selection) Modifier.focusRequester(first) else Modifier)) {
                        Text(name, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
    }
}

@Composable
fun Gap(height: Int) = Spacer(Modifier.height(height.dp))

@Composable
fun KeepAwake() {
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** A radio has no picture: its logo and what's on, drifting slowly
 *  round the screen so nothing stays burnt in. */
@Composable
private fun RadioScreen(group: ChannelGroup) {
    val app = LocalApp.current
    val now = app.epg.now(app.epg.source(group))
    val drift = androidx.compose.animation.core.rememberInfiniteTransition(label = "radio")
    val x by drift.animateFloat(-1f, 1f, androidx.compose.animation.core.infiniteRepeatable(
        androidx.compose.animation.core.tween(23_000, easing = androidx.compose.animation.core.LinearEasing),
        androidx.compose.animation.core.RepeatMode.Reverse), label = "x")
    val y by drift.animateFloat(-1f, 1f, androidx.compose.animation.core.infiniteRepeatable(
        androidx.compose.animation.core.tween(17_000, easing = androidx.compose.animation.core.LinearEasing),
        androidx.compose.animation.core.RepeatMode.Reverse), label = "y")
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val reachX = (maxWidth - 360.dp) / 2
        val reachY = (maxHeight - 220.dp) / 2
        Column(Modifier.align(Alignment.Center).offset(reachX * x, reachY * y).width(360.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)).padding(14.dp)) { ChannelLogo(group, 160.dp, 90.dp) }
            Text(group.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            now?.title?.let { Text(it, color = Secondary, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
        }
    }
}
