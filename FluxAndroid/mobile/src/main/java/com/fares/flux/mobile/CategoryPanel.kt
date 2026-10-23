package com.fares.flux.mobile

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset

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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.flux.core.Category
import com.fares.flux.core.CategoryBadge
import com.fares.flux.core.CategoryLayouts
import com.fares.flux.core.ChannelName
import com.fares.flux.core.DisplayName
import com.fares.flux.core.MediaKind
import com.fares.flux.core.tr

private val TintGradient get() = Brush.horizontalGradient(listOf(FluxBlue, FluxViolet))

/** The chips above a list, as on iOS: a list button, then the categories
 *  sliding in one track, in the user's order and without the hidden ones.
 *  `count`: what a category holds, shown in the panel. */
@Composable
fun CategoryBar(kind: MediaKind, categories: List<Category>, recent: Boolean, selection: String, count: (String) -> Int, onSelect: (String) -> Unit) {
    val app = LocalApp.current
    val account = app.accounts.activeID
    val visible = app.categoryLayouts.visible(categories, account, kind)
    val entries = Categories.entries(visible, recent)
    var showsPanel by remember { mutableStateOf(false) }
    // A category hidden, or gone with the country setting: back to everything.
    LaunchedEffect(visible) {
        if (categories.isNotEmpty() && selection !in setOf(Categories.ALL, Categories.FAVORITES, Categories.RECENT) && visible.none { it.id == selection }) onSelect(Categories.ALL)
    }
    val state = rememberLazyListState()
    LaunchedEffect(selection, entries.size) {
        val i = entries.indexOfFirst { it.first == selection }
        // The chips in front show from the start; the others come into view.
        if (i in 0..2) state.animateScrollToItem(0) else if (i > 2) state.animateScrollToItem(maxOf(0, i - 1))
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(44.dp).pressable().cardGlass(CircleShape).clickable(remember { MutableInteractionSource() }, indication = null) { showsPanel = true }, contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.List, tr("Toutes les catégories", "All categories"), Modifier.size(22.dp))
        }
        // Labels fade at the ends of the track instead of being sliced, as on iOS.
        LazyRow(Modifier.height(44.dp).cardGlass(RoundedCornerShape(50)).fadedEdges(12.dp), state = state,
            contentPadding = PaddingValues(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            items(entries, key = { it.first }) { (id, name) ->
                val selected = id == selection
                Row(
                    Modifier.height(36.dp).clip(RoundedCornerShape(50))
                        .background(if (selected) TintGradient else SolidColor(Color.Transparent))
                        .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(id) }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    val ink = if (selected) Color.White else Ink
                    if (id == Categories.FAVORITES) Icon(Icons.Default.Star, null, Modifier.size(14.dp), tint = ink)
                    if (id == Categories.RECENT) Icon(Icons.Default.AccessTime, null, Modifier.size(14.dp), tint = ink)
                    Text(DisplayName.clean(name), style = IOS.subheadline.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium), color = ink, maxLines = 1)
                }
            }
        }
    }
    if (showsPanel) CategoryPanel(kind, categories, recent, selection, count, { showsPanel = false }) { onSelect(it); showsPanel = false }
}

/** Every category in a sheet: the shortcuts on top, then the categories
 *  with what they hold. In edit mode they're moved and hidden. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPanel(
    kind: MediaKind, categories: List<Category>, recent: Boolean, selection: String, count: (String) -> Int,
    dismiss: () -> Unit, onSelect: (String) -> Unit,
) {
    val app = LocalApp.current
    val layouts = app.categoryLayouts
    val account = app.accounts.activeID
    var editing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val layout = layouts.layout(account, kind)
    val ordered = layouts.ordered(categories, account, kind)
    val flagged = remember(categories) { CategoryBadge.usesFlags(categories) }
    val words = ChannelName.foldText(query).split(" ").filter { it.isNotEmpty() }
    // Edit mode lists everything, as it is: a filter would make moves unclear.
    val rows = when {
        editing -> ordered
        else -> ordered.filter { it.id !in layout.hidden }
            .filter { c -> words.isEmpty() || ChannelName.foldText(DisplayName.clean(c.name)).let { n -> words.all { it in n } } }
    }
    fun move(category: Category, by: Int) {
        val ids = ordered.map { it.id }.toMutableList()
        val from = ids.indexOf(category.id)
        val to = from + by
        if (from < 0 || to !in ids.indices) return
        ids.add(to, ids.removeAt(from))
        layouts.save(account, kind, CategoryLayouts.Layout(ids, layout.hidden))
    }
    fun toggle(category: Category) {
        val hidden = if (category.id in layout.hidden) layout.hidden - category.id else layout.hidden + category.id
        layouts.save(account, kind, CategoryLayouts.Layout(layout.order, hidden))
    }

    // iOS's sheet at its medium detent: floating 8 pt in from the edges,
    // #202020 with a fine rim; scrolling the list, or a tap on the
    // grabber, takes it to full height.
    var large by remember { mutableStateOf(false) }
    val screen = LocalConfiguration.current.screenHeightDp.dp
    val height by animateDpAsState(if (large) screen else screen * 0.52f, snappy(), label = "detent")
    val grow = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0 && !large) { large = true; return available }
                return Offset.Zero
            }
        }
    }
    // The sheet itself transparent: the card drawn inside it, to float.
    ModalBottomSheet(
        onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.statusBarsPadding(), shape = RectangleShape, containerColor = Color.Transparent,
        tonalElevation = 0.dp, scrimColor = Color.Black.copy(alpha = 0.3f), dragHandle = null, contentWindowInsets = { WindowInsets(0) },
    ) {
        val card = RoundedCornerShape(38.dp)
        Column(
            Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp).fillMaxWidth().heightIn(max = height)
                .clip(card).background(FloatingSheet).glassRim(card).nestedScroll(grow).padding(horizontal = 16.dp),
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).clickable(remember { MutableInteractionSource() }, indication = null) { large = !large }
                    .padding(horizontal = 40.dp).padding(top = 6.dp, bottom = 2.dp),
            ) { Box(Modifier.size(36.dp, 5.dp).clip(RoundedCornerShape(50)).background(Ink.copy(alpha = 0.22f))) }
            // The bar: Modifier on the left, the title, the close button in the tint.
            Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(44.dp)) {
                Text(if (editing) tr("OK", "Done") else tr("Modifier", "Edit"),
                    Modifier.align(Alignment.CenterStart).clip(RoundedCornerShape(50)).clickable { editing = !editing; query = "" }.padding(horizontal = 8.dp, vertical = 8.dp),
                    style = IOS.body.copy(fontWeight = if (editing) FontWeight.SemiBold else FontWeight.Normal), color = FluxBlue)
                Text(tr("Catégories", "Categories"), Modifier.align(Alignment.Center), style = IOS.headline)
                SheetCircleButton(Icons.Default.Close, tr("Fermer", "Close"), Modifier.align(Alignment.CenterEnd), prominent = true, onClick = dismiss)
            }
            if (!editing) SearchField(query) { query = it }
            if (!editing && words.isEmpty()) Row(Modifier.padding(top = 10.dp, bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Shortcut(Icons.Default.Star, tr("Favoris", "Favorites"), selection == Categories.FAVORITES, Modifier.weight(1f)) { onSelect(Categories.FAVORITES) }
                if (recent) Shortcut(Icons.Default.WatchLater, tr("Récents", "Recent"), selection == Categories.RECENT, Modifier.weight(1f)) { onSelect(Categories.RECENT) }
                Shortcut(GridFill, tr("Tout", "All"), selection == Categories.ALL, Modifier.weight(1f)) { onSelect(Categories.ALL) }
            }
            if (editing) Box(Modifier.padding(top = 8.dp, bottom = 12.dp)) { Footnote(tr("Range les catégories avec les flèches. Les catégories masquées restent dans « Tout ».",
                "Reorder categories with the arrows. Hidden categories stay in “All”.")) }
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(rows, key = { it.id }) { category ->
                    val hidden = category.id in layout.hidden
                    val index = ordered.indexOf(category)
                    CategoryRow(category, flagged, count(category.id), selected = !editing && category.id == selection, editing = editing, hidden = hidden,
                        canMoveUp = index > 0, canMoveDown = index < ordered.size - 1,
                        modifier = Modifier.animateItem(),
                        onUp = { move(category, -1) }, onDown = { move(category, 1) }, onToggle = { toggle(category) }) { onSelect(category.id) }
                }
                if (rows.isEmpty() && words.isNotEmpty()) item {
                    ContentUnavailable(Icons.Default.Search, tr("Aucun résultat pour « $query »", "No results for “$query”"),
                        tr("Vérifie l’orthographe ou lance une nouvelle recherche.", "Check the spelling or try a new search."), Modifier.padding(top = 40.dp))
                }
                if (editing && (layout.order.isNotEmpty() || layout.hidden.isNotEmpty())) item(key = "reset") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(16.dp)).background(Ink.copy(alpha = 0.05f))
                            .clickable { layouts.reset(account, kind) }.padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Replay, null, Modifier.size(20.dp), tint = Color(0xFFFF453A))
                        Text(tr("Réinitialiser l’ordre", "Reset order"), style = IOS.body, color = Color(0xFFFF453A))
                    }
                }
            }
        }
    }
}

/** SF Symbols' square.grid.2x2.fill: four rounded squares. */
private val GridFill: ImageVector by lazy {
    ImageVector.Builder("GridFill", 24.dp, 24.dp, 24f, 24f).apply {
        for ((x, y) in listOf(3f to 3f, 13f to 3f, 3f to 13f, 13f to 13f)) addPath(
            androidx.compose.ui.graphics.vector.PathBuilder().apply {
                moveTo(x + 2f, y); lineTo(x + 6f, y); arcTo(2f, 2f, 0f, false, true, x + 8f, y + 2f); lineTo(x + 8f, y + 6f)
                arcTo(2f, 2f, 0f, false, true, x + 6f, y + 8f); lineTo(x + 2f, y + 8f); arcTo(2f, 2f, 0f, false, true, x, y + 6f)
                lineTo(x, y + 2f); arcTo(2f, 2f, 0f, false, true, x + 2f, y); close()
            }.nodes,
            fill = SolidColor(Color.Black),
        )
    }.build()
}

/** iOS's sheet at a detent: #202020 in the dark, white in the light. */
private val FloatingSheet get() = if (Palette.dark) Color(0xFF202020) else Color.White

/** The search drawn under a sheet's bar, as `.navigationBarDrawer`. */
@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Search, null, Modifier.size(20.dp), tint = Secondary)
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(tr("Rechercher une catégorie", "Search categories"), style = IOS.body, color = Secondary, maxLines = 1)
            BasicTextField(query, onChange, Modifier.fillMaxWidth(), singleLine = true,
                textStyle = IOS.body.copy(color = Ink), cursorBrush = SolidColor(FluxBlue))
        }
        if (query.isNotEmpty()) Icon(Icons.Default.Cancel, tr("Effacer", "Clear"), Modifier.size(20.dp).clip(CircleShape).clickable { onChange("") }, tint = Tertiary)
    }
}

/** Favoris, Récents, Tout, side by side: glass that's barely there on the
 *  sheet, the tint's gradient on the one chosen. */
@Composable
private fun Shortcut(icon: ImageVector, title: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.clip(shape).then(if (selected) Modifier.background(TintGradient) else Modifier).pressable().clickable(onClick = onClick).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = if (selected) Color.White else Ink)
        Text(title, style = IOS.subheadline.copy(fontWeight = FontWeight.SemiBold), color = if (selected) Color.White else Ink, maxLines = 1)
    }
}

@Composable
private fun CategoryRow(
    category: Category, flagged: Boolean, count: Int, selected: Boolean, editing: Boolean, hidden: Boolean,
    canMoveUp: Boolean, canMoveDown: Boolean, modifier: Modifier,
    onUp: () -> Unit, onDown: () -> Unit, onToggle: () -> Unit, onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val flag = if (flagged) CategoryBadge.flag(category) else null
    val title = CategoryBadge.title(category, flag != null)
    Row(
        modifier.fillMaxWidth().clip(shape)
            .background(if (selected) TintGradient else SolidColor(Ink.copy(alpha = 0.05f)))
            .then(if (editing) Modifier else Modifier.clickable(onClick = onClick))
            .padding(start = 12.dp, end = if (editing) 4.dp else 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.weight(1f).alpha(if (hidden) 0.45f else 1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Badge(title, flag)
            Text(title, Modifier.weight(1f), style = IOS.body.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                color = if (selected) Color.White else Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!editing) Text("$count", style = IOS.subheadline.copy(fontFeatureSettings = "tnum"), color = if (selected) Color.White.copy(alpha = 0.85f) else Secondary)
        }
        if (editing) {
            EditButton(if (hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (hidden) tr("Afficher", "Show") else tr("Masquer", "Hide"), true, onToggle)
            EditButton(Icons.Default.KeyboardArrowUp, tr("Monter", "Move up"), canMoveUp, onUp)
            EditButton(Icons.Default.KeyboardArrowDown, tr("Descendre", "Move down"), canMoveDown, onDown)
        }
    }
}

@Composable
private fun EditButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(22.dp), tint = if (enabled) FluxBlue else Tertiary)
    }
}

/** The category's flag, or its first letters on a muted colour of its own. */
@Composable
private fun Badge(title: String, flag: String?) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).then(
            if (flag != null) Modifier else Color.hsv(CategoryBadge.hue(title), 0.45f, 0.62f).let {
                // As SwiftUI's `.gradient`: a little lighter at the top.
                Modifier.background(Brush.verticalGradient(listOf(lerp(it, Color.White, 0.12f), it)))
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (flag != null) Text(flag, style = IOS.title3)
        else Text(CategoryBadge.initials(title), style = IOS.caption.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1)
    }
}
