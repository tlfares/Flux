package com.fares.flux.mobile

import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.animation.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.saveable.rememberSaveable
import com.fares.flux.core.*
import androidx.compose.ui.graphics.*
import androidx.compose.material.icons.Icons
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.lerp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.fares.flux.core.Backdrop
import com.fares.flux.core.backdropGlass
import com.fares.flux.core.rememberContentBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/** What the glass blurs: the screen's content, under the bars. */
val LocalHaze = staticCompositionLocalOf<HazeState?> { null }

/** Room the floating bar takes at the bottom: lists scroll under it and end above it. */
val LocalBarInset = staticCompositionLocalOf { 0.dp }

/** iOS 26's regular glass, as measured on the iPhone app: over the black
 *  screen, a fill of white at 7.5% (#131313) and a 1 pt rim lighter at the
 *  top left; over content, the content blurred under a dark veil. No sheen
 *  and no glow: iOS draws none on a still pane. */
private fun glassStyle() = HazeStyle(
    backgroundColor = Background,
    tints = listOf(HazeTint(glassTint())),
    blurRadius = 12.dp,
    noiseFactor = 0f,
    fallbackTint = HazeTint(frost()),
)

private fun glassTint() = if (Palette.dark) Color(0xB0101010) else Color(0xB8F7F7F9)
private fun frost() = if (Palette.dark) Color(0xFF151515) else Color(0xFFF7F7F9)

/** Android's own blur, which Haze uses from 12L; before it, the content is
 *  blurred by hand, as the TV app does the picture (`backdrop`). */
private val nativeBlur = android.os.Build.VERSION.SDK_INT >= 32

/** Glass over content (the bars): what's under it blurred, then the rim. */
fun Modifier.glass(state: HazeState?, shape: Shape, backdrop: Backdrop? = null): Modifier {
    val base = this.shadow(if (Palette.dark) 0.dp else 12.dp, shape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.12f)).clip(shape)
    val frosted = when {
        state != null && nativeBlur -> base.hazeEffect(state, glassStyle())
        backdrop != null -> base.backdropGlass(backdrop, glassTint(), frost())
        else -> base.background(frost())
    }
    return frosted.glassRim(shape)
}

/** The 1 pt rim of iOS's glass: lighter where the light comes from, the
 *  top left. On a light screen, a faint dark line instead. */
fun Modifier.glassRim(shape: Shape): Modifier =
    if (Palette.dark) {
        // Bright on the outside and fading inward, as measured: 47, 40, 32
        // across the pixels of iOS's rim on #131313 (white at 6%, then 7%
        // more on its outer half), softer towards the bottom right.
        border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.05f))), shape)
            .border(0.5.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.075f), Color.White.copy(alpha = 0.03f), Color.White.copy(alpha = 0.05f))), shape)
    } else {
        border(1.dp, Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.4f), Color.Black.copy(alpha = 0.04f))), shape)
    }

/** Glass straight on the screen: a card, a row, the chips' track. */
fun Modifier.cardGlass(shape: Shape): Modifier =
    (if (Palette.dark) this else this.shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.10f)))
        .clip(shape)
        // Light, as measured: #F7F7FB on #F2F2F7, a white rim over its top.
        .background(if (Palette.dark) Color.White.copy(alpha = 0.075f) else Color(0xFFF7F7FB))
        .glassRim(shape)

/** Glass on glass (a button or a picker in a card): a step lighter, no rim
 *  of its own, as iOS draws it (#1C1C1C on a card's #131313). */
fun Modifier.glassPanel(shape: Shape): Modifier = this.clip(shape)
    .background(if (Palette.dark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.037f), shape)

/** iOS's interactive glass under a finger: it swells a little and
 *  brightens, and settles back as the finger lifts or the list scrolls. */
fun Modifier.pressable(): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 1.025f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "press")
    val glow by animateFloatAsState(if (pressed) 1f else 0f, spring(stiffness = 500f), label = "glow")
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                waitForUpOrCancellation()
                pressed = false
            }
        }
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .drawWithContent { drawContent(); if (glow > 0f) drawRect(Color.White.copy(alpha = 0.06f * glow)) }
}

/** iOS's tint: the app icon's blue, for text, symbols and selections. */
val Tint = FluxBlue

data class GlassTab(val title: String, val icon: ImageVector, val isSearch: Boolean = false)

/** The search typed in the bar, as iOS 26's search tab has it: the bar
 *  turns into the field, the search screen shows what it finds. */
class SearchState {
    var query by mutableStateOf("")
    /** Searching: the field taken, until the close button ends it. */
    var active by mutableStateOf(false)
    val focus = FocusRequester()
}
/** Set while the bar holds the search field; null on a rail, where the
 *  search screen keeps its own. */
val LocalSearch = staticCompositionLocalOf<SearchState?> { null }

/** Whether the bar is down to its selected tab: set by the lists as they
 *  scroll, as iOS's `tabBarMinimizeBehavior(.onScrollDown)`. */
class BarState { var minimized by mutableStateOf(false) }
val LocalBar = staticCompositionLocalOf { BarState() }

/** The tabs on glass, floating over the content as on iOS 26: a capsule at
 *  the bottom with search in a circle of its own beside it, shrinking to
 *  the tab in use as the list scrolls down; a column down the left on
 *  tablets and unfolded foldables. The tab in use sits in a grey lens,
 *  its symbol and title in the tint. */
@Composable
fun GlassScaffold(tabs: List<GlassTab>, selected: Int, onSelect: (Int) -> Unit, content: @Composable () -> Unit) {
    // Two layers: the bar blurs everything under it; cards blur the plain backdrop.
    val contentHaze = remember { HazeState() }
    val backdropHaze = remember { HazeState() }
    // Without Android's blur, the content recorded as it's drawn, for the bar to blur.
    val contentLayer = rememberGraphicsLayer()
    val contentBounds = remember { mutableStateOf(Rect.Zero) }
    val contentBackdrop = if (nativeBlur) null else rememberContentBackdrop(contentLayer, true) { contentBounds.value }
    val bar = remember { BarState() }
    val searchState = remember { SearchState() }
    // Back to full on another tab, as iOS does.
    LaunchedEffect(selected) { bar.minimized = false }
    // The tab search was opened from, for the button that goes back to it.
    var before by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(selected) { if (tabs.getOrNull(selected)?.isSearch == false) before = selected }
    // Down the list, the bar shrinks; back up, it opens again.
    val scroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed.y < -6f) bar.minimized = true else if (consumed.y > 6f) bar.minimized = false
                return Offset.Zero
            }
        }
    }
    val tabsOnly = tabs.filter { !it.isSearch }
    val search = tabs.indexOfFirst { it.isSearch }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rail = maxWidth >= 600.dp
        Box(Modifier.fillMaxSize().nestedScroll(scroll).hazeSource(contentHaze).then(
            if (nativeBlur) Modifier else Modifier
                .onGloballyPositioned { contentBounds.value = it.boundsInWindow() }
                .drawWithContent {
                    contentLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(contentLayer)
                },
        )) {
            Box(Modifier.fillMaxSize().background(Background).hazeSource(backdropHaze))
            Box(Modifier.fillMaxSize().padding(start = if (rail) 96.dp else 0.dp)) {
                CompositionLocalProvider(
                    LocalHaze provides backdropHaze, LocalBarInset provides if (rail) 0.dp else 96.dp, LocalBar provides bar,
                    LocalSearch provides if (rail) null else searchState,
                ) { content() }
            }
        }
        if (rail) {
            Box(
                Modifier.statusBarsPadding().navigationBarsPadding().padding(12.dp).width(72.dp).height((72 * tabs.size + 8).dp)
                    .glass(contentHaze, RoundedCornerShape(36.dp), contentBackdrop).padding(4.dp),
            ) {
                val lens by animateDpAsState(72.dp * selected, snappy(), label = "lens")
                Lens(Modifier.offset(y = lens).fillMaxWidth().height(72.dp))
                Column {
                    tabs.forEachIndexed { i, t -> TabButton(t, i == selected, Modifier.fillMaxWidth().height(72.dp)) { onSelect(i) } }
                }
            }
        } else {
            val navigation = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            // iOS's bar sits 23 pt over the screen's bottom edge, over the home indicator.
            val bottom = if (navigation > 30.dp) navigation + 4.dp else 23.dp
            val shrink by animateFloatAsState(if (bar.minimized) 1f else 0f, snappy(), label = "shrink")
            val current = tabs.getOrNull(selected)
            val inSearch = current?.isSearch == true
            if (inSearch) {
                SearchBarRow(searchState, tabs.getOrNull(before), bottom, contentHaze, contentBackdrop) { searchState.query = ""; searchState.active = false; onSelect(before) }
                return@BoxWithConstraints
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = bottom)
                    .padding(horizontal = lerp(22.dp, 28.dp, shrink)),
                verticalAlignment = Alignment.Bottom,
            ) {
                val size = lerp(62.dp, 48.dp, shrink)
                if (shrink < 0.5f) {
                    // The tabs in their capsule, the one in use in the lens.
                    BoxWithConstraints(
                        Modifier.weight(1f).height(size).graphicsLayer { alpha = 1f - shrink * 2f }
                            .glass(contentHaze, RoundedCornerShape(50), contentBackdrop).padding(4.dp),
                    ) {
                        val width = maxWidth / tabsOnly.size
                        val index = tabsOnly.indexOf(current)
                        val lens by animateDpAsState(width * index.coerceAtLeast(0), snappy(), label = "lens")
                        if (index >= 0) Lens(Modifier.offset(x = lens).width(width).fillMaxHeight())
                        Row(Modifier.fillMaxSize()) {
                            tabsOnly.forEach { t ->
                                TabButton(t, t == current, Modifier.weight(1f).fillMaxHeight()) { onSelect(tabs.indexOf(t)) }
                            }
                        }
                    }
                } else {
                    // Scrolled down: the tab in use alone, in a circle.
                    val shown = if (inSearch) tabsOnly.firstOrNull() else current
                    Box(
                        Modifier.size(size).graphicsLayer { alpha = shrink * 2f - 1f }
                            .glass(contentHaze, CircleShape, contentBackdrop)
                            .clickable(remember { MutableInteractionSource() }, indication = null) { bar.minimized = false },
                        contentAlignment = Alignment.Center,
                    ) { shown?.let { Icon(it.icon, it.title, Modifier.size(24.dp), tint = if (inSearch) Ink else Tint) } }
                    Spacer(Modifier.weight(1f))
                }
                if (search >= 0) {
                    Spacer(Modifier.width(9.dp))
                    val t = tabs[search]
                    val source = remember { MutableInteractionSource() }
                    val pressed by source.collectIsPressedAsState()
                    val pop by animateFloatAsState(if (pressed) 1.08f else 1f, snappy(), label = "pop")
                    Box(
                        Modifier.size(size).scale(pop).glass(contentHaze, CircleShape, contentBackdrop)
                            .clickable(source, indication = null) { onSelect(search) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(t.icon, t.title, Modifier.size(26.dp), tint = if (inSearch) Tint else Ink) }
                }
            }
        }
    }
}

/** iOS 26's bar on the search tab: the tab search came from in a circle,
 *  then the field; while searching, a close button that ends it, and
 *  over the keyboard the field widening into the circle's place. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoxScope.SearchBarRow(state: SearchState, back: GlassTab?, bottom: Dp, haze: HazeState, backdrop: Backdrop?, close: () -> Unit) {
    val typing = WindowInsets.isImeVisible
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    Row(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()
            .padding(bottom = if (typing) 8.dp else bottom).padding(horizontal = if (typing) 8.dp else 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!typing && back != null) Box(
            Modifier.size(48.dp).glass(haze, CircleShape, backdrop).clickable(remember { MutableInteractionSource() }, indication = null, onClick = close),
            contentAlignment = Alignment.Center,
        ) { Icon(back.icon, back.title, Modifier.size(24.dp), tint = Ink) }
        Row(
            Modifier.weight(1f).height(48.dp).glass(haze, RoundedCornerShape(50), backdrop).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Search, null, Modifier.size(20.dp), tint = Secondary)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (state.query.isEmpty()) Text(tr("Chaînes, films, séries", "Channels, movies, series"), style = IOS.body, color = Secondary, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                BasicTextField(
                    state.query, { state.query = it },
                    Modifier.fillMaxWidth().focusRequester(state.focus).onFocusChanged { if (it.isFocused) state.active = true }, singleLine = true,
                    textStyle = IOS.body.copy(color = Ink), cursorBrush = SolidColor(Tint),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                )
            }
            if (state.query.isNotEmpty()) Icon(Icons.Default.Cancel, tr("Effacer", "Clear"), Modifier.size(20.dp).clip(CircleShape).clickable { state.query = "" }, tint = Tertiary)
        }
        if (state.active) Box(
            Modifier.size(48.dp).glass(haze, CircleShape, backdrop).clickable(remember { MutableInteractionSource() }, indication = null) {
                state.query = ""; state.active = false; focusManager.clearFocus(); keyboard?.hide()
            },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Close, tr("Annuler", "Cancel"), Modifier.size(24.dp), tint = Ink) }
    }
}

/** The selection's lens: lighter glass, grey as iOS's (#3E3E3E over the bar). */
@Composable
private fun Lens(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(50)).background(if (Palette.dark) Color.White.copy(alpha = 0.17f) else Color.Black.copy(alpha = 0.07f)))
}

@Composable
private fun TabButton(tab: GlassTab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val pop by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "pop")
    val color by animateColorAsState(if (selected) Tint else Ink, label = "tint")
    Column(
        modifier.clip(RoundedCornerShape(50)).clickable(source, indication = null, onClick = onClick).scale(pop),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, tab.title, Modifier.size(24.dp), tint = color)
        Spacer(Modifier.height(2.dp))
        Text(tab.title, style = IOS.caption2.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold), color = color, maxLines = 1)
    }
}

/** Bottom padding for a list under the floating bar. */
@Composable
fun listPadding(horizontal: Dp = 16.dp, top: Dp = 4.dp, bottom: Dp = 24.dp) =
    PaddingValues(start = horizontal, end = horizontal, top = top, bottom = bottom + LocalBarInset.current)
