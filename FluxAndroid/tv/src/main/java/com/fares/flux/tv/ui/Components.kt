package com.fares.flux.tv.ui

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fares.flux.core.AppState
import com.fares.flux.core.ChannelGroup
import com.fares.flux.core.Programme
import com.fares.flux.core.TVPlayer
import com.fares.flux.core.nowSeconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val FluxBlue = Color(0xFF0F9EFF)
val FluxViolet = Color(0xFF4F22DF)
val Secondary = Color.White.copy(alpha = 0.62f)
val Tertiary = Color.White.copy(alpha = 0.38f)

val LocalApp = staticCompositionLocalOf<AppState> { error("AppState") }
val LocalTVPlayer = staticCompositionLocalOf<TVPlayer> { error("TVPlayer") }

@Composable
fun FluxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = FluxBlue, background = Color.Black, surface = Color(0xFF121212))) {
        androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides Color.White) { content() }
    }
}

/** The clock, moving every `periodMs`: for progress bars. */
@Composable
fun rememberNow(periodMs: Long = 20_000): Long {
    var now by remember { mutableLongStateOf(nowSeconds()) }
    LaunchedEffect(periodMs) {
        while (true) { delay(periodMs); now = nowSeconds() }
    }
    return now
}

/** A row that turns white under focus, its text dark, as tvOS draws its lists. */
@Composable
fun FocusRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    isCurrent: Boolean = false,
    shape: Shape = RoundedCornerShape(9.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isCurrent) FluxViolet.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.06f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
            pressedContainerColor = Color.White.copy(alpha = 0.85f),
            pressedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        content = content,
    )
}

/** A remote picture, or what stands in for it. */
@Composable
fun RemoteImage(url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, placeholder: @Composable BoxScope.() -> Unit = {}) {
    Box(modifier) {
        placeholder()
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** A channel's logo, or its first letters. */
@Composable
fun ChannelLogo(group: ChannelGroup, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    RemoteImage(group.icon, modifier.size(width, height), ContentScale.Fit) {
        if (group.icon == null) {
            Text(group.name.take(3).uppercase(), Modifier.align(Alignment.Center), color = Color.White.copy(alpha = 0.7f),
                fontSize = (height.value * 0.4f).sp, fontWeight = FontWeight.Black)
        }
    }
}

/** How far into a programme, moving with the clock. */
@Composable
fun LiveProgress(programme: Programme, modifier: Modifier = Modifier, height: Dp = 3.dp, tint: Color = FluxBlue) {
    val now = rememberNow()
    val total = (programme.end - programme.start).toFloat()
    val fraction = if (total > 0) ((now - programme.start) / total).coerceIn(0f, 1f) else 0f
    Box(modifier.height(height).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(tint))
    }
}

/** The colors of a channel's logo, blurred into its tile's background. */
@Composable
fun ChannelGlow(group: ChannelGroup, modifier: Modifier = Modifier) {
    Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF2B2B2B), Color(0xFF141414))))) {
        // Blur needs Android 12; before it, the plain gradient.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && group.icon != null) {
            RemoteImage(group.icon, Modifier.fillMaxSize().scale(1.6f).blur(30.dp).alpha(0.45f))
        }
    }
}

/** What's on as a still, darkened where text and logo sit; the channel's colors otherwise. */
@Composable
fun ProgrammeBackdrop(group: ChannelGroup, image: String?, modifier: Modifier = Modifier) {
    Box(modifier) {
        ChannelGlow(group, Modifier.fillMaxSize())
        if (image != null) {
            RemoteImage(image, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent, Color.Black.copy(alpha = 0.6f)))))
        }
    }
}

/** A channel as Oqee shows it: its logo, what's on and how far it's got. */
@Composable
fun ChannelCard(
    group: ChannelGroup,
    width: Dp = 200.dp,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val app = LocalApp.current
    val source = app.epg.source(group)
    val now = app.epg.now(source).also { rememberNow(60_000) }
    LaunchedEffect(group.id) { app.accounts.client?.let { app.epg.load(group, it) } }
    Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.width(width).height(width * 9 / 16),
            shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
            border = CardDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.dp, Color.White))),
            scale = CardDefaults.scale(focusedScale = 1.06f),
        ) {
            Box(Modifier.fillMaxSize()) {
                ProgrammeBackdrop(group, now?.image, Modifier.fillMaxSize())
                Column(Modifier.fillMaxSize().padding(11.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        ChannelLogo(group, width * 0.34f, width * 0.19f)
                        Spacer(Modifier.weight(1f))
                        group.number?.let { Text("$it", color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                    }
                    Spacer(Modifier.weight(1f))
                    if (isPlaying) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GraphicEq, null, tint = FluxBlue, modifier = Modifier.size(12.dp))
                            Text(" En cours", color = FluxBlue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (now != null) LiveProgress(now, Modifier.fillMaxWidth().padding(top = 4.dp), height = 2.5.dp)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(now?.title ?: group.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(now?.let { "${group.name} · ${it.timeRange}" } ?: "En direct", fontSize = 11.sp, color = Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A poster that lifts under focus, its title under it. */
@Composable
fun PosterCard(title: String, image: String?, progress: Double? = null, width: Dp = 110.dp, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.width(width).height(width * 1.5f),
            shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
            border = CardDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.dp, Color.White))),
            scale = CardDefaults.scale(focusedScale = 1.08f),
        ) {
            Box(Modifier.fillMaxSize()) {
                RemoteImage(image, Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().background(Color(0xFF242424))) }
                if (progress != null && progress > 0) {
                    Box(Modifier.align(Alignment.BottomCenter).padding(7.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.25f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.toFloat()).background(FluxBlue))
                    }
                }
            }
        }
        Text(title, fontSize = 11.sp, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** A titled row that scrolls sideways, as on the home screen. */
@Composable
fun Shelf(title: String, state: LazyListState = rememberLazyListState(), spacing: Dp = 20.dp, content: LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, Modifier.padding(horizontal = 40.dp), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(state = state, contentPadding = PaddingValues(horizontal = 40.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

/** The ids the category column starts with. */
object Categories {
    const val ALL = "_all"
    const val FAVORITES = "_fav"
    const val RECENT = "_recent"

    fun entries(categories: List<com.fares.flux.core.Category>, recent: Boolean): List<Pair<String, String>> {
        val list = mutableListOf(FAVORITES to "Favoris")
        if (recent) list.add(RECENT to "Récents")
        list.add(ALL to "Tout")
        return list + categories.map { it.id to it.name }
    }
}

/** The channels a category stands for. */
fun AppState.channels(selection: String): List<ChannelGroup> {
    val guide = catalog.guide
    return when (selection) {
        Categories.FAVORITES -> library.favorites(com.fares.flux.core.MediaKind.LIVE).mapNotNull { guide.group(it.key) }
        Categories.RECENT -> library.recentChannels.mapNotNull { guide.group(it) }
        Categories.ALL -> guide.groups
        else -> guide.groups(selection)
    }
}

/** The list of categories down the left of a catalogue screen. */
@Composable
fun CategoryColumn(entries: List<Pair<String, String>>, selection: String, onSelect: (String) -> Unit) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = entries.indexOfFirst { it.first == selection }.coerceAtLeast(0))
    LazyColumn(Modifier.width(220.dp).fillMaxHeight(), state = state, contentPadding = PaddingValues(vertical = 10.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(entries, key = { it.first }) { (id, name) ->
            FocusRow(onClick = { onSelect(id) }, isCurrent = id == selection, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(com.fares.flux.core.DisplayName.clean(name), Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (id == selection) FontWeight.SemiBold else FontWeight.Normal)
                    if (id == selection) Icon(Icons.Default.Check, null, Modifier.size(14.dp))
                }
            }
        }
    }
}

/** While the catalogue first loads, or when it couldn't. */
@Composable
fun CatalogState(empty: String) {
    val app = LocalApp.current
    val catalog = app.catalog
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (catalog.isLoading || catalog.isStarting || catalog.isIndexing) {
            Text("Chargement du catalogue…", color = Secondary)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (catalog.error == null) empty else "Impossible de charger", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(catalog.error ?: "Le fournisseur n’a rien envoyé cette fois.", color = Secondary)
                androidx.tv.material3.Button(onClick = {
                    app.accounts.client?.let { c -> app.scope.launchCatching { app.catalog.refresh(c) } }
                }) { Text("Réessayer") }
            }
        }
    }
}

fun kotlinx.coroutines.CoroutineScope.launchCatching(block: suspend () -> Unit) =
    launch { runCatching { block() } }

@Composable
fun Dot() = Image(androidx.compose.ui.graphics.painter.ColorPainter(Color.Red), null, Modifier.size(7.dp).clip(RoundedCornerShape(50)))
