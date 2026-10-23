package com.fares.flux.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.fares.flux.core.Category
import com.fares.flux.core.CategoryBadge
import com.fares.flux.core.CategoryLayouts
import com.fares.flux.core.MediaKind
import com.fares.flux.core.tr
import kotlinx.coroutines.launch

private val Tint = Brush.horizontalGradient(listOf(FluxBlue, FluxViolet))

/** The categories down the left of a catalogue screen: Favoris, Récents
 *  and Tout as tiles, then the categories in the user's order with what
 *  they hold. "Modifier" turns it into the editor: OK picks a row up, up
 *  and down carry it, OK puts it down; the eye beside each hides it. */
@Composable
fun CategoryColumn(kind: MediaKind, categories: List<Category>, recent: Boolean, selection: String, count: (String) -> Int, onSelect: (String) -> Unit) {
    val app = LocalApp.current
    val layouts = app.categoryLayouts
    val account = app.accounts.activeID
    var editing by remember { mutableStateOf(false) }
    // The row being carried, in edit mode.
    var picked by remember { mutableStateOf<String?>(null) }
    val layout = layouts.layout(account, kind)
    val ordered = layouts.ordered(categories, account, kind)
    val visible = if (layout.hidden.isEmpty()) ordered else ordered.filter { it.id !in layout.hidden }
    val rows = if (editing) ordered else visible
    val flagged = remember(categories) { CategoryBadge.usesFlags(categories) }

    // A category hidden, or gone with the country setting: back to everything.
    LaunchedEffect(visible) {
        if (categories.isNotEmpty() && selection !in setOf(Categories.ALL, Categories.FAVORITES, Categories.RECENT) && visible.none { it.id == selection }) onSelect(Categories.ALL)
    }
    // Back puts a carried row down, then leaves edit mode, before going up to the tabs.
    val toggleButton = remember { FocusRequester() }
    BackHandler(enabled = editing) {
        if (picked != null) picked = null
        else {
            // The rows change under focus: it goes to the button that brings edit mode back.
            editing = false
            runCatching { toggleButton.requestFocus() }
        }
    }

    val state = rememberLazyListState(initialFirstVisibleItemIndex = rows.indexOfFirst { it.id == selection }.coerceAtLeast(0))
    val firstRow = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    fun move(id: String, by: Int) {
        val ids = ordered.map { it.id }.toMutableList()
        val from = ids.indexOf(id)
        val to = from + by
        if (from < 0 || to !in ids.indices) return
        ids.add(to, ids.removeAt(from))
        // The list holds still and the row moves in it: left alone, the list
        // would follow the row, as it keeps its first item in place.
        state.requestScrollToItem(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
        layouts.save(account, kind, CategoryLayouts.Layout(ids, layout.hidden))
    }
    fun toggle(id: String) {
        val hidden = if (id in layout.hidden) layout.hidden - id else layout.hidden + id
        layouts.save(account, kind, CategoryLayouts.Layout(layout.order, hidden))
    }

    Column(Modifier.width(272.dp).fillMaxHeight().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Catégories", "Categories"), Modifier.weight(1f).padding(start = 4.dp), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Pill(if (editing) tr("OK", "Done") else tr("Modifier", "Edit"), prominent = editing,
                modifier = Modifier.focusRequester(toggleButton).onPreviewKeyEvent { e ->
                    // Down from "OK" lands on the first row, not the eye under the button.
                    if (editing && e.key == Key.DirectionDown && rows.isNotEmpty()) {
                        if (e.type == KeyEventType.KeyDown) scope.launch { state.scrollToItem(0); runCatching { firstRow.requestFocus() } }
                        true
                    } else false
                }) { editing = !editing; picked = null }
        }
        if (!editing) Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(Icons.Default.Star, tr("Favoris", "Favorites"), selection == Categories.FAVORITES, Modifier.weight(1f)) { onSelect(Categories.FAVORITES) }
            if (recent) Tile(Icons.Default.AccessTime, tr("Récents", "Recent"), selection == Categories.RECENT, Modifier.weight(1f)) { onSelect(Categories.RECENT) }
            Tile(Icons.Default.GridView, tr("Tout", "All"), selection == Categories.ALL, Modifier.weight(1f)) { onSelect(Categories.ALL) }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = state, contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(rows, key = { it.id }) { category ->
                val hidden = category.id in layout.hidden
                if (editing) EditRow(category, flagged, hidden, picked == category.id, if (category == rows.first()) Modifier.focusRequester(firstRow) else Modifier,
                    onPick = { picked = if (picked == category.id) null else category.id },
                    onMove = { move(category.id, it) }, onToggle = { toggle(category.id) })
                else CategoryRow(category, flagged, count(category.id), category.id == selection) { onSelect(category.id) }
            }
            if (editing) item(key = "_reset") {
                Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                    Pill(tr("Réinitialiser l’ordre", "Reset order")) { picked = null; layouts.reset(account, kind) }
                }
            }
        }
    }
}

/** A focusable surface that turns white under focus, as the app's rows do;
 *  `selected` fills it with the tint when it isn't focused, `lifted` even
 *  under focus (a row being carried). */
@Composable
private fun GlassSurface(
    onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, lifted: Boolean = false, shape: Shape = RoundedCornerShape(12.dp),
    border: Border = Border.None, content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected || lifted) Color.Transparent else Color.White.copy(alpha = 0.06f),
            contentColor = Color.White,
            focusedContainerColor = if (lifted) Color.Transparent else Color.White,
            focusedContentColor = if (lifted) Color.White else Color.Black,
            pressedContainerColor = Color.White.copy(alpha = 0.85f),
            pressedContentColor = Color.Black,
        ),
        border = ClickableSurfaceDefaults.border(border = border, focusedBorder = border),
        scale = ClickableSurfaceDefaults.scale(focusedScale = if (lifted) 1.06f else 1.03f),
    ) {
        Box(if (lifted || (selected && !focused)) Modifier.background(Tint) else Modifier) { content(focused) }
    }
}

@Composable
private fun Pill(title: String, prominent: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassSurface(onClick, modifier, selected = prominent, shape = RoundedCornerShape(50)) {
        Text(title, Modifier.padding(horizontal = 14.dp, vertical = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun Tile(icon: ImageVector, title: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    GlassSurface(onClick, modifier.height(62.dp), selected = selected, shape = RoundedCornerShape(14.dp)) { focused ->
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)) {
            Icon(icon, null, Modifier.size(18.dp), tint = if (selected || focused) LocalContentColor.current else FluxBlue)
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CategoryRow(category: Category, flagged: Boolean, count: Int, selected: Boolean, onClick: () -> Unit) {
    val flag = if (flagged) CategoryBadge.flag(category) else null
    val title = CategoryBadge.title(category, flag != null)
    GlassSurface(onClick, Modifier.fillMaxWidth(), selected = selected) { focused ->
        Row(Modifier.padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Badge(title, flag)
            Text(title, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            Text("$count", fontSize = 12.sp, color = LocalContentColor.current.copy(alpha = if (focused || !selected) 0.55f else 0.8f))
        }
    }
}

/** A row in edit mode: the row itself, which OK picks up, and its eye. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditRow(category: Category, flagged: Boolean, hidden: Boolean, picked: Boolean, rowModifier: Modifier, onPick: () -> Unit, onMove: (Int) -> Unit, onToggle: () -> Unit) {
    val flag = if (flagged) CategoryBadge.flag(category) else null
    val title = CategoryBadge.title(category, flag != null)
    val view = remember { BringIntoViewRequester() }
    var moves by remember { mutableStateOf(0) }
    // Carried past the edge of the list: it scrolls along, so focus stays on it.
    LaunchedEffect(moves) { if (moves > 0) runCatching { view.bringIntoView() } }
    Row(Modifier.fillMaxWidth().bringIntoViewRequester(view), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        GlassSurface(onPick, Modifier.weight(1f).then(rowModifier).onPreviewKeyEvent { e ->
            if (!picked) return@onPreviewKeyEvent false
            when (e.key) {
                Key.DirectionUp, Key.DirectionDown -> {
                    if (e.type == KeyEventType.KeyDown) { onMove(if (e.key == Key.DirectionUp) -1 else 1); moves++ }
                    true
                }
                // Held in place while carried: neither the eye nor the tabs take focus.
                Key.DirectionLeft, Key.DirectionRight -> true
                else -> false
            }
        }, lifted = picked, border = if (picked) Border(BorderStroke(2.dp, Color.White)) else Border.None) { _ ->
            Row(Modifier.padding(horizontal = 8.dp, vertical = 7.dp).alpha(if (hidden && !picked) 0.4f else 1f),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Badge(title, flag)
                Text(title, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Default.SwapVert, if (picked) tr("Déplacer", "Move") else null, Modifier.size(16.dp),
                    tint = LocalContentColor.current.copy(alpha = if (picked) 1f else 0.4f))
            }
        }
        GlassSurface(onToggle, Modifier.size(38.dp), shape = CircleShape) { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(if (hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (hidden) tr("Afficher", "Show") else tr("Masquer", "Hide"), Modifier.size(18.dp))
            }
        }
    }
}

/** The category's flag, or its first letters on a muted colour of its own. */
@Composable
private fun Badge(title: String, flag: String?) {
    Box(
        Modifier.size(30.dp).background(if (flag != null) Color.White.copy(alpha = 0.08f) else Color.hsv(CategoryBadge.hue(title), 0.42f, 0.50f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (flag != null) Text(flag, fontSize = 17.sp)
        else Text(CategoryBadge.initials(title), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
    }
}

/** The categories a picker lists: the user's order, without the hidden ones. */
@Composable
fun visibleCategories(kind: MediaKind, categories: List<Category>): List<Category> {
    val app = LocalApp.current
    return app.categoryLayouts.visible(categories, app.accounts.activeID, kind)
}
