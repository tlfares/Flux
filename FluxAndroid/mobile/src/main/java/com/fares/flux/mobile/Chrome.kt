package com.fares.flux.mobile

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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent

/** iOS's large title, as the first thing in a list: 34 pt bold, 20 pt in,
 *  under the navigation bar's 44 pt (the gap above it). It scrolls away
 *  with the list, the bar's small title taking over (`TopEdge`). */
@Composable
fun LargeTitle(title: String) {
    Text(title, Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 52.dp, bottom = 6.dp), style = IOS.largeTitle)
}

/** The top of an iOS 26 screen over its scrolled content: the content
 *  fading out under the status bar and navigation bar (the scroll edge
 *  effect), and the small centred title once the large one has gone, or
 *  always for a screen with an inline title. */
@Composable
fun BoxScope.TopEdge(title: String, showsTitle: Boolean) {
    val shown by animateFloatAsState(if (showsTitle) 1f else 0f, snappy(), label = "title")
    Box(
        Modifier.align(Alignment.TopCenter).fillMaxWidth()
            .background(Brush.verticalGradient(0f to Background, 0.55f to Background.copy(alpha = 0.82f), 1f to Color.Transparent))
            .statusBarsPadding().height(58.dp),
    ) {
        Text(title, Modifier.align(Alignment.TopCenter).padding(top = 11.dp).alpha(shown), style = IOS.headline)
    }
}

/** Out of a padded list's margins, to the screen's edges: for what iOS
 *  lays out apart from the list (the title, the chips). */
fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = horizontal.roundToPx() * 2
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** Content fading out over `width` at both ends, as a mask. */
fun Modifier.fadedEdges(width: Dp): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val w = width.toPx()
        drawRect(Brush.horizontalGradient(0f to Color.Transparent, w / size.width to Color.Black, 1f - w / size.width to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
    }

/** Content fading in over `height` at the top, as a mask. */
fun Modifier.fadedTop(height: Dp): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = height.toPx()
        drawRect(Brush.verticalGradient(0f to Color.Transparent, h / size.height to Color.Black, 1f to Color.Black), blendMode = BlendMode.DstIn)
    }
