package com.fares.flux.mobile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fares.flux.core.AppState
import com.fares.flux.core.Category
import com.fares.flux.core.ChannelGroup
import com.fares.flux.core.ChannelVariant
import com.fares.flux.core.DisplayName
import com.fares.flux.core.Favorite
import com.fares.flux.core.MediaKind
import com.fares.flux.core.Programme
import com.fares.flux.core.TVPlayer
import com.fares.flux.core.nowSeconds
import kotlinx.coroutines.delay

val FluxBlue = Color(0xFF0F9EFF)
val FluxViolet = Color(0xFF4F22DF)
val BrandTop = Color(0xFF0F9EFF)
val BrandBottom = Color(0xFF4F22DF)
/** Light or dark, from the setting or the system; set by FluxTheme before anything draws. */
object Palette { var dark by androidx.compose.runtime.mutableStateOf(true) }

/** The background: black or white, nothing else (the colors are the content's). */
val Background get() = if (Palette.dark) Color.Black else Color.White
/** The ink of text and rims: white on black, black on white. */
val Ink get() = if (Palette.dark) Color.White else Color.Black
val Secondary get() = Ink.copy(alpha = if (Palette.dark) 0.6f else 0.55f)
val Tertiary get() = Ink.copy(alpha = if (Palette.dark) 0.38f else 0.35f)
val Card get() = Ink.copy(alpha = if (Palette.dark) 0.045f else 0.035f)

val LocalApp = staticCompositionLocalOf<AppState> { error("AppState") }
val LocalPlayer = staticCompositionLocalOf<TVPlayer> { error("TVPlayer") }

@Composable
fun FluxTheme(appearance: String = "system", content: @Composable () -> Unit) {
    val system = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = when (appearance) { "light" -> false; "dark" -> true; else -> system }
    Palette.dark = dark
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(
            primary = FluxBlue, onPrimary = Color.White, secondary = FluxViolet,
            background = Color.Black, surface = Color.Black, surfaceContainer = Color(0xFF16161C),
            surfaceContainerHigh = Color(0xFF1E1E26), secondaryContainer = FluxBlue.copy(alpha = 0.28f),
        ) else androidx.compose.material3.lightColorScheme(
            primary = FluxBlue, onPrimary = Color.White, secondary = FluxViolet,
            background = Color.White, surface = Color.White, onBackground = Color.Black, onSurface = Color.Black,
            surfaceContainer = Color(0xFFF2F2F7), surfaceContainerHigh = Color(0xFFE9E9EF), secondaryContainer = FluxBlue.copy(alpha = 0.18f),
        ),
        content = content,
    )
}

/** The app's icon, as on iOS: three streams into a play button, on the brand gradient. */
@Composable
fun FluxLogo(size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.225f))
            .background(Brush.verticalGradient(listOf(BrandTop, BrandBottom))),
    ) {
        Image(painterResource(com.fares.flux.core.R.drawable.ic_launcher_glass), null, Modifier.fillMaxSize())
    }
}

/** The clock, moving every `periodMs`: for progress bars. */
@Composable
fun rememberNow(periodMs: Long = 20_000): Long {
    var now by remember { mutableLongStateOf(nowSeconds()) }
    LaunchedEffect(periodMs) { while (true) { delay(periodMs); now = nowSeconds() } }
    return now
}

@Composable
fun RemoteImage(url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    if (url == null) { Box(modifier); return }
    AsyncImage(ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(), null, modifier, contentScale = contentScale)
}

@Composable
fun ChannelLogo(group: ChannelGroup, width: Dp = 46.dp, height: Dp = 30.dp, framed: Boolean = true) {
    Box(
        (if (framed) Modifier.background(Ink.copy(alpha = 0.06f), RoundedCornerShape(11.dp)).padding(7.dp) else Modifier).size(width, height),
        contentAlignment = Alignment.Center,
    ) {
        if (group.icon == null) Text(group.name.take(2).uppercase(), color = Secondary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        RemoteImage(group.icon, Modifier.fillMaxSize(), ContentScale.Fit)
    }
}

@Composable
fun LiveProgress(programme: Programme, modifier: Modifier = Modifier, height: Dp = 3.dp) {
    val now = rememberNow()
    val total = (programme.end - programme.start).toFloat()
    val fraction = if (total > 0) ((now - programme.start) / total).coerceIn(0f, 1f) else 0f
    Box(modifier.height(height).clip(RoundedCornerShape(50)).background(Ink.copy(alpha = 0.16f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(FluxBlue))
    }
}

/** The ids the chips start with. */
object Categories {
    const val ALL = "_all"
    const val FAVORITES = "_fav"
    const val RECENT = "_recent"

    fun entries(categories: List<Category>, recent: Boolean): List<Pair<String, String>> {
        val list = mutableListOf(FAVORITES to "Favoris")
        if (recent) list.add(RECENT to "Récents")
        list.add(ALL to "Tout")
        return list + categories.map { it.id to it.name }
    }
}

fun AppState.channels(selection: String): List<ChannelGroup> {
    val guide = catalog.guide
    return when (selection) {
        Categories.FAVORITES -> library.favorites(MediaKind.LIVE).mapNotNull { guide.group(it.key) }
        Categories.RECENT -> library.recentChannels.mapNotNull { guide.group(it) }
        Categories.ALL -> guide.groups
        else -> guide.groups(selection)
    }
}

/** The chips above a list, as on iOS: a list button, then the categories
 *  sliding in one track. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CategoryBar(entries: List<Pair<String, String>>, selection: String, onSelect: (String) -> Unit) {
    var showsList by remember { mutableStateOf(false) }
    val state = rememberLazyListState()
    LaunchedEffect(selection, entries.size) {
        val i = entries.indexOfFirst { it.first == selection }
        // The chips in front show from the start; the others come into view.
        if (i in 0..2) state.animateScrollToItem(0) else if (i > 2) state.animateScrollToItem(maxOf(0, i - 1))
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(44.dp).glassPanel(RoundedCornerShape(50)).clickable { showsList = true }, contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.List, "Toutes les catégories")
        }
        LazyRow(Modifier.height(44.dp).glassPanel(RoundedCornerShape(50)), state = state,
            contentPadding = PaddingValues(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            items(entries, key = { it.first }) { (id, name) ->
                val selected = id == selection
                Row(
                    Modifier.height(36.dp).clip(RoundedCornerShape(50))
                        .background(if (selected) Brush.horizontalGradient(listOf(FluxBlue, FluxViolet)) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                        .clickable { onSelect(id) }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (id == Categories.FAVORITES) Icon(Icons.Default.Star, null, Modifier.size(13.dp))
                    if (id == Categories.RECENT) Icon(Icons.Default.AccessTime, null, Modifier.size(13.dp))
                    Text(DisplayName.clean(name), fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
    if (showsList) {
        ModalBottomSheet(onDismissRequest = { showsList = false }) {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().height(480.dp)) {
                items(entries, key = { it.first }) { (id, name) ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(id); showsList = false }.padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Text(DisplayName.clean(name), Modifier.weight(1f))
                        if (id == selection) Text("✓", color = FluxBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** A channel: its logo, what's on, and the quality it comes in. A long
 *  press adds it to favorites or picks another stream. */
@Composable
fun ChannelRow(group: ChannelGroup, isPlaying: Boolean, play: (ChannelVariant?) -> Unit) {
    val app = LocalApp.current
    val now = app.epg.now(app.epg.source(group))
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(group.id) { delay(250); app.accounts.client?.let { app.epg.load(group, it) } }
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(if (isPlaying) Brush.horizontalGradient(listOf(FluxBlue.copy(alpha = 0.28f), FluxViolet.copy(alpha = 0.28f))) else Brush.horizontalGradient(listOf(Card, Card)))
                .combinedClickable(onLongClick = { menu = true }) { play(null) }
                .padding(8.dp).padding(end = 6.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            group.number?.let { Text("$it", Modifier.width(30.dp), color = Secondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            ChannelLogo(group)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(group.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (app.library.isFavorite(Favorite.live(group))) Icon(Icons.Default.Star, null, Modifier.padding(start = 5.dp).size(11.dp), tint = FluxBlue)
                }
                if (now != null) {
                    Text(now.title, fontSize = 12.sp, color = Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    LiveProgress(now, Modifier.fillMaxWidth().padding(end = 8.dp), 2.dp)
                }
            }
            if (isPlaying) Icon(Icons.Default.GraphicEq, null, tint = FluxBlue, modifier = Modifier.size(18.dp))
            else QualityBadge(group)
        }
        ChannelMenu(group, menu, { menu = false }, play)
    }
}

@Composable
fun QualityBadge(group: ChannelGroup) {
    val best = group.variants[0].quality
    if (best.label == "Standard" && !group.hasChoice) return
    Row(Modifier.background(Ink.copy(alpha = 0.07f), RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 4.dp)) {
        Text(if (best.label == "Standard") "Auto" else best.label, fontSize = 11.sp, color = Secondary, fontWeight = FontWeight.SemiBold)
        if (group.variants.size > 1) Text(" +${group.variants.size - 1}", fontSize = 11.sp, color = Tertiary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ChannelMenu(group: ChannelGroup, expanded: Boolean, dismiss: () -> Unit, play: (ChannelVariant?) -> Unit) {
    val app = LocalApp.current
    DropdownMenu(expanded, dismiss) {
        val favorite = Favorite.live(group)
        DropdownMenuItem(text = { Text(if (app.library.isFavorite(favorite)) "Retirer des favoris" else "Ajouter aux favoris") },
            onClick = { app.library.toggleFavorite(favorite); dismiss() })
        if (group.hasChoice) group.variants.forEach { v ->
            DropdownMenuItem(text = { Text("Regarder en ${v.quality.label}") }, onClick = { play(v); dismiss() })
        }
    }
}

/** A channel as the TV app shows it: what's on as a still, the logo and number over it. */
@Composable
fun ChannelCard(group: ChannelGroup, isPlaying: Boolean, play: (ChannelVariant?) -> Unit) {
    val app = LocalApp.current
    val now = app.epg.now(app.epg.source(group))
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(group.id) { delay(250); app.accounts.client?.let { app.epg.load(group, it) } }
    Box {
        Column(Modifier.combinedClickable(onLongClick = { menu = true }) { play(null) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF2B2B2B), Color(0xFF141414))))
                    .border(if (isPlaying) 2.dp else 1.dp, if (isPlaying) FluxBlue else Ink.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
            ) {
                if (now?.image != null) {
                    RemoteImage(now.image, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent, Color.Black.copy(alpha = 0.6f)))))
                } else {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { ChannelLogo(group, 90.dp, 50.dp, framed = false) }
                }
                Column(Modifier.fillMaxSize().padding(10.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        if (now?.image != null) ChannelLogo(group, 52.dp, 30.dp, framed = false)
                        Spacer(Modifier.weight(1f))
                        group.number?.let { Text("$it", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    }
                    Spacer(Modifier.weight(1f))
                    if (now != null) LiveProgress(now, Modifier.fillMaxWidth())
                }
            }
            Text(now?.title ?: group.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(now?.let { "${group.name} · ${it.timeRange}" } ?: "En direct", fontSize = 12.sp, color = Secondary, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 0.dp))
        }
        ChannelMenu(group, menu, { menu = false }, play)
    }
}

@Composable
fun Poster(title: String, image: String?, progress: Double? = null, onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    Column(Modifier.combinedClickable(onLongClick = onLongClick, onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1E1E24))) {
            RemoteImage(image, Modifier.fillMaxSize())
            if (progress != null && progress > 0) {
                Box(Modifier.align(Alignment.BottomCenter).padding(7.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress.toFloat()).background(FluxBlue))
                }
            }
        }
        Text(title, fontSize = 12.sp, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun LoadingOrEmpty(empty: String) {
    val app = LocalApp.current
    val c = app.catalog
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (c.isLoading || c.isStarting || c.isIndexing) androidx.compose.material3.CircularProgressIndicator()
        else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (c.error == null) empty else "Impossible de charger", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(c.error ?: "Le fournisseur n’a rien envoyé cette fois.", color = Secondary)
        }
    }
}
