package com.fares.flux.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import com.fares.flux.core.ChannelGroup
import com.fares.flux.core.Favorite
import com.fares.flux.core.MediaKind
import com.fares.flux.core.Programme
import kotlinx.coroutines.launch

/** The home screen, live TV alone: the channel last watched up top, then
 *  rows of channels with what's on, the way Oqee opens. */
@Composable
fun HomeScreen() {
    val app = LocalApp.current
    val player = LocalTVPlayer.current
    val guide = app.catalog.guide
    if (guide.isEmpty) { CatalogState("Aucune chaîne"); return }
    val recent = app.library.recentChannels.mapNotNull { guide.group(it) }
    val favorites = app.library.favorites(MediaKind.LIVE).mapNotNull { guide.group(it.key) }
    val numbered = guide.groups.filter { it.number != null }
    val tnt = (numbered.ifEmpty { guide.groups }).take(30)
    // Wherever it's picked from here, a channel zaps through them all.
    fun watch(group: ChannelGroup) { app.accounts.client?.let { player.watch(group, guide.groups, it) } }

    val shelves = buildList {
        if (recent.size > 1) add("Reprendre" to recent.drop(1))
        if (favorites.isNotEmpty()) add("Mes chaînes" to favorites)
        if (tnt.isNotEmpty()) add("En ce moment à la TV" to tnt)
    }
    val firstState = rememberLazyListState()
    val firstCard = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        (recent.firstOrNull() ?: tnt.firstOrNull())?.let { hero ->
            item(key = "hero") {
                // Down from the wide hero lands on the first card of the row
                // under it, not the one under its middle.
                Hero(hero, Modifier.onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown && shelves.isNotEmpty()) {
                        scope.launch {
                            firstState.scrollToItem(0)
                            runCatching { firstCard.requestFocus() }
                        }
                        true
                    } else false
                }) { watch(hero) }
            }
        }
        shelves.forEachIndexed { index, (title, groups) ->
            item(key = title) {
                Shelf(title, state = if (index == 0) firstState else rememberLazyListState()) {
                    items(groups, key = { it.id }) { group ->
                        val first = index == 0 && group == groups.first()
                        ChannelCard(group, 200.dp, player.channel?.id == group.id,
                            modifier = if (first) Modifier.focusRequester(firstCard) else Modifier,
                            onLongClick = { app.library.toggleFavorite(Favorite.live(group)) }) { watch(group) }
                    }
                }
            }
        }
    }
}

/** The big tile at the top of the home screen: back to the last channel. */
@Composable
private fun Hero(group: ChannelGroup, modifier: Modifier, onClick: () -> Unit) {
    val app = LocalApp.current
    val source = app.epg.source(group)
    val now = app.epg.now(source)
    val next = app.epg.next(source)
    LaunchedEffect(group.id) { app.accounts.client?.let { app.epg.load(group, it, 4) } }
    Card(
        onClick = onClick,
        modifier = modifier.padding(horizontal = 40.dp).padding(top = 10.dp).fillMaxWidth().height(220.dp),
        shape = CardDefaults.shape(RoundedCornerShape(16.dp)),
        border = CardDefaults.border(focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(2.dp, Color.White))),
        scale = CardDefaults.scale(focusedScale = 1.02f),
    ) {
        Box(Modifier.fillMaxSize()) {
            ProgrammeBackdrop(group, now?.image, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Black.copy(alpha = 0.4f), Color.Transparent))))
            Row(Modifier.fillMaxSize().padding(30.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (now?.image != null) ChannelLogo(group, 80.dp, 45.dp)
                    Text(if (app.library.recentChannels.firstOrNull() == group.id) "REPRENDRE EN DIRECT" else "EN DIRECT",
                        color = FluxBlue, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text(now?.title ?: group.name, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (now != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("${group.name} · ${now.timeRange}", color = Secondary, fontSize = 14.sp)
                        LiveProgress(now, Modifier.width(180.dp))
                    }
                    now?.summary?.let { Text(it, color = Secondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    next?.let { Text("À suivre · ${Programme.clock(it.start)}  ${it.title}", color = Tertiary, fontSize = 13.sp, maxLines = 1) }
                }
                if (now?.image == null) {
                    Spacer(Modifier.width(30.dp))
                    ChannelLogo(group, 190.dp, 107.dp)
                }
            }
        }
    }
}

/** Every channel, by category: the list down the left, the channels as tiles on the right. */
@Composable
fun ChannelsScreen() {
    val app = LocalApp.current
    val player = LocalTVPlayer.current
    var selection by rememberSaveable { mutableStateOf(Categories.ALL) }
    if (app.catalog.guide.isEmpty) { CatalogState("Aucune chaîne"); return }
    val channels = app.channels(selection)
    Row(Modifier.fillMaxSize().padding(start = 20.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
        CategoryColumn(Categories.entries(app.catalog.live.categories, recent = true), selection) { selection = it }
        if (channels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (selection == Categories.FAVORITES) "Aucun favori. Maintiens le clic sur une chaîne pour l’ajouter." else "Rien ici", color = Secondary)
            }
        } else {
            LazyVerticalGrid(GridCells.Adaptive(190.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 15.dp, bottom = 30.dp, end = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                items(channels, key = { it.id }) { group ->
                    ChannelCard(group, 190.dp, player.channel?.id == group.id, onLongClick = { app.library.toggleFavorite(Favorite.live(group)) }) {
                        // Zaps within the category; through every channel from Récents.
                        app.accounts.client?.let { player.watch(group, if (selection == Categories.RECENT) app.catalog.guide.groups else channels, it) }
                    }
                }
            }
        }
    }
}

/** The guide: what's on every channel of a category over the next three hours. */
@Composable
fun GuideScreen() {
    val app = LocalApp.current
    // Every channel each time, whatever Direct was left on.
    var selection by remember { mutableStateOf(Categories.ALL) }
    var picking by remember { mutableStateOf(false) }
    if (app.catalog.guide.isEmpty) { CatalogState("Aucune chaîne"); return }
    val now = rememberNow(60_000)
    val start = now - now % 1800
    val channels = app.channels(selection)
    val lineup = if (selection == Categories.RECENT) app.catalog.guide.groups else channels
    val entries = Categories.entries(app.catalog.live.categories, recent = true)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val channelWidth = 150.dp
        val gridWidth = maxWidth - channelWidth - 80.dp - 6.dp
        val perSecond = gridWidth / (3 * 3600f)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(horizontal = 40.dp).padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FocusRow(onClick = { picking = true }, modifier = Modifier.width(channelWidth)) {
                    Text(com.fares.flux.core.DisplayName.clean(entries.firstOrNull { it.first == selection }?.second ?: "Tout") + "  ▾",
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.fillMaxWidth()) {
                    for (i in 0 until 6) {
                        Text(Programme.clock(start + i * 1800L), Modifier.offset(x = perSecond * (i * 1800f)), color = Secondary, fontSize = 12.sp)
                    }
                }
            }
            Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 40.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(channels, key = { it.id }) { group -> GuideRow(group, lineup, start, perSecond, channelWidth) }
                }
                // Now, as a line down the grid.
                Box(Modifier.offset(x = 40.dp + channelWidth + 6.dp + perSecond * (now - start).toFloat()).width(1.5.dp).fillMaxHeight().background(FluxBlue))
            }
        }
    }
    if (picking) ChoiceDialog("Catégorie", entries.map { it.first to com.fares.flux.core.DisplayName.clean(it.second) }, selection,
        onDismiss = { picking = false }) { selection = it; picking = false }
}

@Composable
private fun GuideRow(group: ChannelGroup, lineup: List<ChannelGroup>, start: Long, perSecond: androidx.compose.ui.unit.Dp, channelWidth: androidx.compose.ui.unit.Dp) {
    val app = LocalApp.current
    val player = LocalTVPlayer.current
    val end = start + 3 * 3600
    val programmes = app.epg.upcoming(app.epg.source(group)).filter { it.start < end && it.end > start }
    LaunchedEffect(group.id) {
        kotlinx.coroutines.delay(150)
        app.accounts.client?.let { app.epg.load(group, it, 10) }
    }
    fun watch() { app.accounts.client?.let { player.watch(group, lineup, it) } }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.width(channelWidth).height(48.dp).background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
            verticalAlignment = Alignment.CenterVertically) {
            Text(group.number?.toString() ?: "", Modifier.width(25.dp), color = Secondary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            ChannelLogo(group, 55.dp, 31.dp)
        }
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            if (programmes.isEmpty()) {
                FocusRow(onClick = { watch() }, modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(7.dp)) {
                    Text(group.name, Modifier.align(Alignment.CenterStart).padding(horizontal = 10.dp), fontSize = 13.sp)
                }
            }
            // Laid out where it starts: the focus engine moves between frames.
            LazyRow(userScrollEnabled = false) {
                items(programmes, key = { it.start }) { p ->
                    val from = maxOf(p.start, start)
                    val to = minOf(p.end, end)
                    val gap = if (p == programmes.first()) perSecond * (from - start).toFloat() else 0.dp
                    Row {
                    Spacer(Modifier.width(gap))
                    FocusRow(onClick = { watch() }, isCurrent = p.isOn, shape = RoundedCornerShape(7.dp),
                        modifier = Modifier.padding(end = 3.dp).width(maxOf(4.dp, perSecond * (to - from).toFloat() - 3.dp)).height(48.dp)) {
                        Column(Modifier.padding(horizontal = 8.dp).align(Alignment.CenterStart)) {
                            Text(p.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(p.timeRange, fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(top = 1.dp), color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.65f))
                        }
                    }
                    }
                }
            }
        }
    }
}
