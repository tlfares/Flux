package com.fares.flux.mobile

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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/** What the glass blurs: the screen's content, under the bars. */
val LocalHaze = staticCompositionLocalOf<HazeState?> { null }

/** Room the floating bar takes at the bottom: lists scroll under it and end above it. */
val LocalBarInset = staticCompositionLocalOf { 0.dp }

/** Liquid Glass, as close as Android gets: the content behind blurred and
 *  lightened, a bright rim catching light at the top fading at the bottom. */
private fun glassStyle() = HazeStyle(
    backgroundColor = Background,
    tints = listOf(HazeTint(if (Palette.dark) Color(0x8C16161C) else Color(0x99F4F4F8)), HazeTint(Ink.copy(alpha = 0.04f))),
    blurRadius = 32.dp,
    noiseFactor = 0.08f,
    // Before Android 12, no blur: a darker frost so text still reads.
    fallbackTint = HazeTint(if (Palette.dark) Color(0xF01A1A22) else Color(0xF2F4F4F8)),
)

fun Modifier.glass(state: HazeState?, shape: Shape): Modifier {
    val base = this.clip(shape)
    // Blur only from Android 13 (as Haze does); before, a thick frost so nothing shows through.
    val blurs = android.os.Build.VERSION.SDK_INT >= 33
    val frosted = if (state != null && blurs) base.hazeEffect(state, glassStyle()) else base.background(if (Palette.dark) Color(0xF01A1A22) else Color(0xF2F4F4F8))
    return frosted
        .background(Brush.verticalGradient(listOf(Ink.copy(alpha = 0.10f), Ink.copy(alpha = 0.02f))), shape)
        .border(1.dp, Brush.verticalGradient(listOf(Ink.copy(alpha = 0.35f), Ink.copy(alpha = 0.06f))), shape)
}

/** Glass with nothing to blur behind it (a still panel): the same rim and frost. */
fun Modifier.glassPanel(shape: Shape): Modifier = this.clip(shape)
    .background(Ink.copy(alpha = 0.07f), shape)
    .background(Brush.verticalGradient(listOf(Ink.copy(alpha = 0.08f), Color.Transparent)), shape)
    .border(1.dp, Brush.verticalGradient(listOf(Ink.copy(alpha = 0.28f), Ink.copy(alpha = 0.04f))), shape)

data class GlassTab(val title: String, val icon: ImageVector)

/** The tabs on glass, floating over the content as on iOS 26: a capsule at
 *  the bottom on phones, a column down the left on tablets and unfolded
 *  foldables. The tab in use sits in a lens that slides to the next one. */
@Composable
fun GlassScaffold(tabs: List<GlassTab>, selected: Int, onSelect: (Int) -> Unit, content: @Composable () -> Unit) {
    // Two layers: the bar blurs everything under it; cards blur the plain backdrop.
    val contentHaze = remember { HazeState() }
    val backdropHaze = remember { HazeState() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rail = maxWidth >= 600.dp
        Box(Modifier.fillMaxSize().hazeSource(contentHaze)) {
            Box(Modifier.fillMaxSize().background(Background).hazeSource(backdropHaze))
            Box(Modifier.fillMaxSize().padding(start = if (rail) 96.dp else 0.dp)) {
                CompositionLocalProvider(LocalHaze provides backdropHaze, LocalBarInset provides if (rail) 0.dp else 96.dp) { content() }
            }
        }
        if (rail) {
            Box(
                Modifier.statusBarsPadding().navigationBarsPadding().padding(12.dp).width(72.dp).height((72 * tabs.size + 16).dp)
                    .glass(contentHaze, RoundedCornerShape(36.dp)).padding(8.dp),
            ) {
                val lens by animateDpAsState(72.dp * selected, snappy(), label = "lens")
                Lens(Modifier.offset(y = lens).fillMaxWidth().height(72.dp))
                Column {
                    tabs.forEachIndexed { i, t -> TabButton(t, i == selected, Modifier.fillMaxWidth().height(72.dp)) { onSelect(i) } }
                }
            }
        } else {
            BoxWithConstraints(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)
                    .fillMaxWidth().height(64.dp).glass(contentHaze, RoundedCornerShape(32.dp)).padding(5.dp),
            ) {
                val width = maxWidth / tabs.size
                val lens by animateDpAsState(width * selected, snappy(), label = "lens")
                Lens(Modifier.offset(x = lens).width(width).fillMaxHeight())
                Row(Modifier.fillMaxSize()) {
                    tabs.forEachIndexed { i, t -> TabButton(t, i == selected, Modifier.weight(1f).fillMaxHeight()) { onSelect(i) } }
                }
            }
        }
    }
}

/** The selection's lens: lighter glass tinted with the logo's colors. */
@Composable
private fun Lens(modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(28.dp))
            .background(Brush.horizontalGradient(listOf(FluxBlue.copy(alpha = 0.38f), FluxViolet.copy(alpha = 0.38f))))
            .border(1.dp, Brush.verticalGradient(listOf(Ink.copy(alpha = 0.40f), Ink.copy(alpha = 0.06f))), RoundedCornerShape(28.dp)),
    )
}

@Composable
private fun TabButton(tab: GlassTab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    // A small bounce on the symbol as its tab is picked, and a press that sinks.
    val pop by animateFloatAsState(if (pressed) 0.86f else if (selected) 1.08f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "pop")
    val color by animateColorAsState(if (selected) Ink else Secondary, label = "tint")
    Column(
        modifier.clip(RoundedCornerShape(28.dp)).clickable(source, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, tab.title, Modifier.size(22.dp).scale(pop), tint = color)
        Text(tab.title, fontSize = 10.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = color, maxLines = 1)
    }
}

/** Bottom padding for a list under the floating bar. */
@Composable
fun listPadding(horizontal: Dp = 16.dp, top: Dp = 4.dp, bottom: Dp = 24.dp) =
    PaddingValues(start = horizontal, end = horizontal, top = top, bottom = bottom + LocalBarInset.current)
