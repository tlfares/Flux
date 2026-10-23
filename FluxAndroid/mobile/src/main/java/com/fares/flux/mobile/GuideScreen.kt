package com.fares.flux.mobile

import androidx.compose.animation.*
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.flux.core.ChannelGroup
import com.fares.flux.core.MediaKind
import com.fares.flux.core.Programme
import com.fares.flux.core.tr
import kotlinx.coroutines.delay

// As iOS's GuideView: 8 pt in, a 72 pt column of logos, rows 64 pt tall
// 6 pt apart, half an hour 120 pt wide, four hours across.
private val Side = 8.dp
private val LogoColumn = 72.dp
private val Gap = 8.dp
private val RowHeight = 64.dp
/** Half an hour on the grid. */
private val HalfHour = 120.dp
private const val HOURS = 4

/** The guide on a phone: the logos down the left, three hours of programmes
 *  sliding together to the right, a line where it's now. */
@Composable
fun GuideScreen() {
    val app = LocalApp.current
    var selection by rememberSaveable { mutableStateOf(Categories.ALL) }
    val channels = app.channels(selection)
    val lineup = if (selection == Categories.RECENT) app.catalog.guide.groups else channels
    val now = rememberNow(60_000)
    val start = now - now % 1800
    val perSecond = HalfHour / 1800f
    val timeline = HalfHour * (HOURS * 2).toFloat()
    // One scroll for every row and the times above them.
    val scroll = rememberScrollState()
    val title = tr("Guide", "Guide")
    // The title inline, as iOS's `.navigationBarTitleDisplayMode(.inline)`.
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 44.dp)) {
        if (app.catalog.guide.isEmpty) { LoadingOrEmpty(tr("Aucune chaîne", "No channels")); return@Column }
        CategoryBar(MediaKind.LIVE, app.catalog.live.categories, recent = true, selection, { app.catalog.guide.count(it) }) { selection = it }
        if (channels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(when (selection) {
                    Categories.FAVORITES -> tr("Aucun favori. Maintiens une chaîne pour l’ajouter.", "No favorites yet. Press and hold a channel to add it.")
                    Categories.RECENT -> tr("Les chaînes regardées apparaîtront ici.", "Channels you watch will appear here.")
                    else -> tr("Catégorie vide", "Nothing in this category")
                }, color = Secondary, style = IOS.subheadline)
            }
            return@Column
        }
        // The times, over the grid.
        val density = LocalDensity.current
        val nowX = with(density) { (perSecond * (now - start).toFloat()).roundToPx() }
        Row(Modifier.fillMaxWidth().padding(horizontal = Side).height(32.dp), horizontalArrangement = Arrangement.spacedBy(Gap)) {
            Box(Modifier.width(LogoColumn))
            Box(Modifier.weight(1f).fillMaxHeight().clipToBounds().horizontalScroll(scroll)) {
                Box(Modifier.width(timeline).fillMaxHeight()) {
                    for (i in 0 until HOURS * 2) {
                        Text(Programme.clock(start + i * 1800L), Modifier.align(Alignment.CenterStart).offset(x = HalfHour * i.toFloat() + 4.dp),
                            color = Secondary, style = IOS.caption.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
                    }
                    // Now, a dot over the line.
                    Box(Modifier.align(Alignment.BottomStart).offset { IntOffset(nowX - 3.5.dp.roundToPx(), (-2).dp.roundToPx()) }.size(7.dp).clip(CircleShape).background(FluxBlue))
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(horizontal = Side, top = 0.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(channels, key = { it.id }) { group -> GuideRow(group, lineup, start, perSecond, timeline, scroll, nowX) }
        }
    }
    TopEdge(title, true)
    }
}

@Composable
private fun GuideRow(group: ChannelGroup, lineup: List<ChannelGroup>, start: Long, perSecond: Dp, timeline: Dp, scroll: ScrollState, nowX: Int) {
    val app = LocalApp.current
    val player = LocalPlayer.current
    val end = start + HOURS * 3600
    val programmes = app.epg.upcoming(app.epg.source(group)).filter { it.start < end && it.end > start }
    LaunchedEffect(group.id) {
        delay(150)
        app.accounts.client?.let { app.epg.load(group, it, 10) }
    }
    fun watch() { app.accounts.client?.let { player.watch(group, lineup, it) } }
    val playing = player.channel?.id == group.id
    // As iOS's blocks: white at 6%, the tint at 22% for what's on, 12 pt corners.
    val block = Ink.copy(alpha = 0.06f)
    val current = FluxBlue.copy(alpha = 0.22f)
    Row(Modifier.fillMaxWidth().height(RowHeight), horizontalArrangement = Arrangement.spacedBy(Gap)) {
        Box(
            Modifier.width(LogoColumn).fillMaxHeight().clickable(remember { MutableInteractionSource() }, indication = null) { watch() }
                .then(if (playing) Modifier.border(2.dp, FluxBlue, RoundedCornerShape(11.dp)) else Modifier),
            contentAlignment = Alignment.Center,
        ) { ChannelLogo(group, LogoColumn - 14.dp, RowHeight - 14.dp) }
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds().horizontalScroll(scroll)) {
            Box(Modifier.width(timeline).fillMaxHeight()) {
                // Now, down the grid, behind the titles.
                Box(Modifier.offset { IntOffset(nowX - 1.dp.roundToPx(), 0) }.width(2.dp).fillMaxHeight().background(FluxBlue))
                if (programmes.isEmpty()) {
                    Box(Modifier.width(280.dp).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(block).clickable { watch() }.padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart) {
                        Text(group.name, style = IOS.subheadline.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                for (p in programmes) {
                    val from = maxOf(p.start, start)
                    val to = minOf(p.end, end)
                    val on = p.isOn
                    Column(
                        Modifier.offset(x = perSecond * (from - start).toFloat())
                            .width(maxOf(4.dp, perSecond * (to - from).toFloat() - 4.dp)).fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) current else block)
                            .clickable { watch() }.padding(horizontal = 10.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(p.title, style = IOS.subheadline.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(p.timeRange, style = IOS.caption2.copy(fontFeatureSettings = "tnum"), color = Secondary, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        }
    }
}
