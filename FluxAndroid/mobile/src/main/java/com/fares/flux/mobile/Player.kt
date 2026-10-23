package com.fares.flux.mobile

import androidx.compose.animation.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
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
import com.fares.flux.core.Backdrop
import com.fares.flux.core.rememberVideoBackdrop
import com.fares.flux.core.videoGlass
import com.fares.flux.core.Programme
import com.fares.flux.core.tr
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(controls: Boolean, modifier: Modifier = Modifier, onView: (PlayerView) -> Unit = {}) {
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
            }.also(onView)
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

/** Live TV, as iOS's LiveWatchView: upright, the picture on top edge to
 *  edge, what's on under it, then the chips and the channels; on its side
 *  or full screen, the picture alone. The channel's logo, blown up and
 *  blurred, colors the screen behind the glass. */
@Composable
fun LiveWatch() {
    val player = LocalPlayer.current
    val app = LocalApp.current
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var fullScreen by remember { mutableStateOf(false) }
    BackHandler { if (fullScreen) fullScreen = false else player.closeLive() }
    LaunchedEffect(player.channel?.id) { player.channel?.let { g -> app.accounts.client?.let { app.epg.load(g, it, 8) } } }

    BoxWithConstraints(Modifier.fillMaxSize().background(Background)) {
        LogoBackdrop(player.channel?.icon)
        val wide = maxWidth > 840.dp && maxWidth > maxHeight
        val height = maxHeight
        val alone = fullScreen || (landscape && !wide)
        Immersive(alone)
        when {
            alone -> Picture(Modifier.fillMaxSize(), fullScreen = true) { fullScreen = !fullScreen }
            wide -> Row(Modifier.fillMaxSize().statusBarsPadding()) {
                Column(Modifier.width(400.dp).fillMaxHeight()) {
                    LiveChips()
                    ChannelColumn(Modifier.weight(1f))
                }
                Column(Modifier.weight(1f).padding(end = 16.dp, top = 8.dp)) {
                    Picture(Modifier.fillMaxWidth().aspectRatio(16f / 9f), false) { fullScreen = true }
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 14.dp, bottom = 16.dp)) { LiveDetails(expanded = true) }
                }
            }
            else -> Column(Modifier.fillMaxSize()) {
                // Black up to the top edge, the status bar over it.
                Box(Modifier.fillMaxWidth().background(Color.Black).statusBarsPadding()) {
                    Picture(Modifier.fillMaxWidth().aspectRatio(16f / 9f), false) { fullScreen = true }
                }
                Column(Modifier.heightIn(max = height * 0.45f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 4.dp)) { LiveDetails(expanded = false) }
                Box(Modifier.bleed(4.dp)) { LiveChips() }
                ChannelColumn(Modifier.weight(1f))
            }
        }
    }
}

/** The channel's logo, blown up and blurred behind the glass (Android 12
 *  and later: before, the plain screen). */
@Composable
private fun LogoBackdrop(icon: String?) {
    if (android.os.Build.VERSION.SDK_INT < 31) return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Crossfade(icon, label = "backdrop") { url ->
            RemoteImage(url, Modifier.padding(top = 40.dp).size(260.dp).blur(90.dp, BlurredEdgeTreatment.Unbounded).alpha(0.55f), ContentScale.Fit)
        }
    }
}

@Composable
private fun LiveChips() {
    val app = LocalApp.current
    var selection by rememberSaveable { mutableStateOf(Categories.ALL) }
    val player = LocalPlayer.current
    CategoryBar(MediaKind.LIVE, app.catalog.live.categories, recent = true, selection, { app.catalog.guide.count(it) }) {
        selection = it
        player.changeLineup(app.channels(it))
    }
}

@Composable
private fun Picture(modifier: Modifier, fullScreen: Boolean, toggle: () -> Unit) {
    val player = LocalPlayer.current
    var controls by remember { mutableStateOf(true) }
    var shownAt by remember { mutableStateOf(System.currentTimeMillis()) }
    // Up for three seconds, unless paused, as iOS's.
    LaunchedEffect(shownAt, controls, player.isPaused) { if (controls && !player.isPaused) { delay(3_000); controls = false } }
    // The picture blurred under the buttons while they're up, as on the TV.
    var view by remember { mutableStateOf<PlayerView?>(null) }
    val backdrop = rememberVideoBackdrop(view, controls && player.hasPicture && player.error == null)
    Box(
        modifier.background(Color.Black)
            .clickable(remember { MutableInteractionSource() }, indication = null) { controls = !controls; shownAt = System.currentTimeMillis() }
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = {
                    // Up: the channel below in the list; down: the one above.
                    if (total < -80) player.zap(1) else if (total > 80) player.zap(-1)
                }) { _, dy -> total += dy }
            },
    ) {
        VideoSurface(false, Modifier.fillMaxSize()) { view = it }
        if (player.isBuffering && player.error == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        player.error?.let { Text(it, Modifier.align(Alignment.Center).padding(20.dp), color = Secondary, style = IOS.subheadline) }
        androidx.compose.animation.AnimatedVisibility(controls, Modifier.fillMaxSize(), enter = fadeIn(tween(250)), exit = fadeOut(tween(250))) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.TopStart).padding(10.dp)) {
                    VideoButton(backdrop, Icons.Default.KeyboardArrowDown, tr("Fermer", "Close")) { if (fullScreen) toggle() else player.closeLive() }
                }
                // Only for streams that have some.
                if (player.textTracks.isNotEmpty()) Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) {
                    var menu by remember { mutableStateOf(false) }
                    VideoButton(backdrop, if (player.textTrack != null) Icons.Filled.Chat else Icons.Outlined.Chat, tr("Sous-titres", "Subtitles")) {
                        menu = true; shownAt = System.currentTimeMillis()
                    }
                    DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(18.dp), containerColor = if (Palette.dark) Color(0xF22C2C2E) else Color(0xF7FFFFFF)) {
                        val choices = listOf<com.fares.flux.core.TextTrack?>(null) + player.textTracks
                        choices.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t?.label ?: tr("Désactivés", "Off"), style = IOS.body) },
                                trailingIcon = { if (t == player.textTrack) Icon(Icons.Default.Check, null, tint = FluxBlue) },
                                onClick = { player.selectText(t); menu = false },
                            )
                        }
                    }
                }
                if (player.error == null) Box(Modifier.align(Alignment.Center)) {
                    VideoButton(backdrop, if (player.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, if (player.isPaused) tr("Lecture", "Play") else "Pause", size = 56.dp, iconSize = 28.dp) {
                        player.togglePause(); shownAt = System.currentTimeMillis()
                    }
                }
                Box(Modifier.align(Alignment.BottomEnd).padding(10.dp)) {
                    VideoButton(backdrop, if (fullScreen) Icons.Default.FullscreenExit else Icons.Default.OpenInFull, tr("Plein écran", "Full screen"), onClick = toggle)
                }
            }
        }
    }
}

/** A round glass button on the picture, as iOS's VideoButton: 40 pt, the
 *  picture blurred through it. */
/** iOS's glass over a picture, measured: grey, a little of the picture
 *  showing through (#6F6F6F over white). */
private val VideoGlassTint = Color(0xFF575757).copy(alpha = 0.86f)

@Composable
private fun VideoButton(backdrop: Backdrop, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: Dp = 40.dp, iconSize: Dp = 22.dp, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 1.1f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "press")
    Box(Modifier.size(size).scale(scale).videoGlass(backdrop, CircleShape, VideoGlassTint).glassRim(CircleShape).clickable(source, indication = null, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(iconSize), tint = Color.White)
    }
}

/** What's on the channel playing, as iOS's LiveDetails: its logo, name and
 *  whether it's live, a star; the qualities it comes in; then the programme
 *  on now in a glass card, its summary under a tap, and what follows. */
@Composable
private fun LiveDetails(expanded: Boolean) {
    val player = LocalPlayer.current
    val app = LocalApp.current
    val group = player.channel ?: return
    val schedule = app.epg.upcoming(app.epg.source(group))
    var showsSummary by remember(group.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChannelLogo(group, 52.dp, 34.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(group.name, style = IOS.title3.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFFF453A)))
                    val quality = player.variant?.takeIf { group.variants.size > 1 }?.quality?.label
                    Text(tr("En direct", "Live") + (quality?.let { " · $it" } ?: ""), style = IOS.caption.copy(fontWeight = FontWeight.SemiBold), color = Secondary)
                }
            }
            val favorite = Favorite.live(group)
            val on = app.library.isFavorite(favorite)
            Box(Modifier.size(40.dp).pressable().cardGlass(CircleShape).clickable(remember { MutableInteractionSource() }, indication = null) { app.library.toggleFavorite(favorite) },
                contentAlignment = Alignment.Center) {
                Icon(if (on) Icons.Default.Star else Icons.Outlined.StarOutline, tr("Favori", "Favorite"), Modifier.size(22.dp), tint = if (on) FluxBlue else Ink)
            }
        }
        if (group.variants.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            group.variants.forEach { v ->
                ChoiceChip(v.quality.label, v.id == player.variant?.id) { app.accounts.client?.let { player.watch(group, player.lineup, it, v) } }
            }
        }
        val now = schedule.firstOrNull()?.takeIf { it.isOn }
        if (now != null) {
            Column(Modifier.fillMaxWidth().cardGlass(RoundedCornerShape(22.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, indication = null) { if (now.summary != null) showsSummary = !showsSummary }
                    .animateContentSize(spring(dampingRatio = 0.9f, stiffness = 230f)), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(now.title, Modifier.weight(1f), style = IOS.headline)
                        if (now.summary != null) {
                            val turn by animateFloatAsState(if (showsSummary) 180f else 0f, label = "chevron")
                            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.padding(top = 2.dp).size(18.dp).rotate(turn), tint = Secondary)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(now.timeRange, style = IOS.caption.copy(fontFeatureSettings = "tnum"), color = Secondary)
                        LiveProgress(now, Modifier.weight(1f), 4.dp)
                    }
                    if (showsSummary) now.summary?.let { Text(it, style = IOS.subheadline, color = Secondary) }
                }
                val later = schedule.drop(1).take(if (expanded) 6 else 1)
                if (later.isNotEmpty()) {
                    HairlineDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        later.forEach { p ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(Programme.clock(p.start), Modifier.width(48.dp), style = IOS.footnote.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), color = FluxBlue)
                                Text(p.title, style = IOS.subheadline, maxLines = if (expanded) 2 else 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        } else if (app.epg.isEmpty(app.epg.source(group))) {
            Text(tr("Pas de guide des programmes pour cette chaîne.", "No program guide for this channel."), style = IOS.footnote, color = Secondary)
        }
    }
}

/** A small pick among a few, as iOS's ChoiceChip: the gradient when chosen. */
@Composable
private fun ChoiceChip(title: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(30.dp).pressable().clip(RoundedCornerShape(50))
            .then(if (selected) Modifier.background(Brush.horizontalGradient(listOf(FluxBlue, FluxViolet))) else Modifier.cardGlass(RoundedCornerShape(50)))
            .clickable(remember { MutableInteractionSource() }, indication = null) { if (!selected) onClick() }.padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(title, style = IOS.footnote.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium), color = if (selected) Color.White else Ink) }
}

/** The list being zapped through, the channel playing in view; rows slide
 *  out under the chips rather than being cut. */
@Composable
private fun ChannelColumn(modifier: Modifier) {
    val player = LocalPlayer.current
    val app = LocalApp.current
    val lineup = player.lineup
    val state = rememberLazyListState(maxOf(0, lineup.indexOfFirst { it.id == player.channel?.id } - 1))
    LazyColumn(modifier.fadedTop(10.dp), state = state, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 44.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
