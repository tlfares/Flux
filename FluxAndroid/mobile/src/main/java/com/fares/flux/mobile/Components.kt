package com.fares.flux.mobile

import androidx.compose.animation.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.fares.flux.core.*
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
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
import com.fares.flux.core.tr
import kotlinx.coroutines.delay

/** FluxTint.blue, Display P3 (0.10, 0.62, 1.0), as iOS draws it: #00A1FF. */
val FluxBlue = Color(0xFF00A1FF)
val FluxViolet = Color(0xFF4F22DF)
val BrandTop = Color(0xFF0F9EFF)
val BrandBottom = Color(0xFF4F22DF)
/** Light or dark, from the setting or the system; set by FluxTheme before anything draws. */
object Palette { var dark by androidx.compose.runtime.mutableStateOf(true) }

/** Behind every screen, as iOS's screenBackground: black in dark mode, the
 *  grouped grey in light mode, so the glass cards stand out in both. */
val Background get() = if (Palette.dark) Color.Black else Color(0xFFF2F2F7)
/** iOS's label colors: white or black, then secondaryLabel and tertiaryLabel. */
val Ink get() = if (Palette.dark) Color.White else Color.Black
val Secondary get() = if (Palette.dark) Color(0x99EBEBF5) else Color(0x993C3C43)
val Tertiary get() = if (Palette.dark) Color(0x4DEBEBF5) else Color(0x4D3C3C43)
/** iOS's separator, between rows of a card. */
val Separator get() = if (Palette.dark) Color(0x99545458) else Color(0x4A3C3C43)
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
        typography = IOSTypography,
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
        // The launcher's art keeps the adaptive icon's margin: drawn larger, as iOS's icon.
        Image(painterResource(com.fares.flux.core.R.drawable.ic_launcher_glass), null, Modifier.fillMaxSize().scale(1.45f))
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
fun ChannelLogo(group: ChannelGroup, width: Dp = 46.dp, height: Dp = 30.dp, framed: Boolean = true, modifier: Modifier = Modifier) {
    Box(
        modifier.then(if (framed) Modifier.background(Ink.copy(alpha = 0.06f), RoundedCornerShape(11.dp)).padding(7.dp) else Modifier).size(width, height),
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
        val list = mutableListOf(FAVORITES to tr("Favoris", "Favorites"))
        if (recent) list.add(RECENT to tr("Récents", "Recent"))
        list.add(ALL to tr("Tout", "All"))
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

/** A channel: its logo, what's on, and the quality it comes in. A long
 *  press adds it to favorites or picks another stream. */
@Composable
fun ChannelRow(group: ChannelGroup, isPlaying: Boolean, play: (ChannelVariant?) -> Unit) {
    val app = LocalApp.current
    val now = app.epg.now(app.epg.source(group))
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(group.id) { delay(250); app.accounts.client?.let { app.epg.load(group, it) } }
    val shape = RoundedCornerShape(18.dp)
    Box {
        // As iOS's: .padding(8), .padding(.trailing, 6), minHeight 60, glass in an 18 pt rectangle.
        Row(
            Modifier.fillMaxWidth().pressable().cardGlass(shape)
                .then(if (isPlaying) Modifier.background(FluxBlue.copy(alpha = 0.25f)) else Modifier)
                .combinedClickable(remember { MutableInteractionSource() }, indication = null, onLongClick = { menu = true }) { play(null) }
                .padding(8.dp).padding(end = 6.dp).heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            group.number?.let { Text("$it", Modifier.padding(start = 2.dp).padding(end = 0.dp).widthIn(min = 28.dp).offset(x = 0.dp), color = Secondary,
                style = IOS.subheadline.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            ChannelLogo(group, modifier = if (group.number != null) Modifier.offset(x = (-4).dp) else Modifier)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(group.name, style = IOS.subheadline.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (app.library.isFavorite(Favorite.live(group))) Icon(Icons.Default.Star, null, Modifier.size(11.dp), tint = FluxBlue)
                }
                if (now != null) {
                    Text(now.title, style = IOS.caption, color = Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        Text(if (best.label == "Standard") "Auto" else best.title, fontSize = 11.sp, color = Secondary, fontWeight = FontWeight.SemiBold)
        if (group.variants.size > 1) Text(" +${group.variants.size - 1}", fontSize = 11.sp, color = Tertiary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ChannelMenu(group: ChannelGroup, expanded: Boolean, dismiss: () -> Unit, play: (ChannelVariant?) -> Unit) {
    val app = LocalApp.current
    DropdownMenu(expanded, dismiss) {
        val favorite = Favorite.live(group)
        DropdownMenuItem(text = { Text(if (app.library.isFavorite(favorite)) tr("Retirer des favoris", "Remove from favorites") else tr("Ajouter aux favoris", "Add to favorites")) },
            onClick = { app.library.toggleFavorite(favorite); dismiss() })
        if (group.hasChoice) group.variants.forEach { v ->
            DropdownMenuItem(text = { Text(tr("Regarder en ${v.quality.label}", "Play ${v.quality.title}")) }, onClick = { play(v); dismiss() })
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
            Text(now?.let { "${group.name} · ${it.timeRange}" } ?: tr("En direct", "Live"), fontSize = 12.sp, color = Secondary, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 0.dp))
        }
        ChannelMenu(group, menu, { menu = false }, play)
    }
}

/** As iOS's PosterCard: a 2:3 poster in 14 pt corners with a faint rim,
 *  a star in glass when it's a favorite, the progress at its foot, and
 *  the title under it on two lines. */
@Composable
fun Poster(title: String, image: String?, progress: Double? = null, isFavorite: Boolean = false, onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    Column(Modifier.pressable().combinedClickable(onLongClick = onLongClick, onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).background(Ink.copy(alpha = 0.06f))
                .border(1.dp, Ink.copy(alpha = 0.08f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Theaters, null, Modifier.size(26.dp), tint = Tertiary)
            RemoteImage(image, Modifier.fillMaxSize())
            if (isFavorite) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).cardGlass(CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Star, null, Modifier.size(12.dp), tint = FluxBlue)
            }
            if (progress != null && progress > 0) {
                Box(Modifier.align(Alignment.BottomCenter).padding(8.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress.toFloat()).background(FluxBlue))
                }
            }
        }
        Text(title, style = IOS.caption.copy(fontWeight = FontWeight.Medium), maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun LoadingOrEmpty(empty: String) {
    val app = LocalApp.current
    val c = app.catalog
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (c.isLoading || c.isStarting || c.isIndexing) androidx.compose.material3.CircularProgressIndicator()
        else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (c.error == null) empty else tr("Impossible de charger", "Couldn’t load"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(c.error ?: tr("Le fournisseur n’a rien envoyé cette fois.", "The provider didn’t send anything this time."), color = Secondary)
        }
    }
}
