package com.fares.flux.mobile

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
import androidx.compose.foundation.interaction.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** iOS's grouped colors in a sheet: the sheet #1C1C1E, its cells #2C2C2E. */
val SheetBackground get() = if (Palette.dark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
val CellBackground get() = if (Palette.dark) Color(0xFF2C2C2E) else Color.White

/** A sheet as iOS 26 draws one: full height under the status bar, 38 pt
 *  corners, a bar with a glass button on the left, the title in the
 *  middle and the close button in the tint on the right. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IOSSheet(
    title: String, dismiss: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        dismiss, Modifier.statusBarsPadding().padding(top = 6.dp), sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        scrimColor = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp), containerColor = SheetBackground,
        dragHandle = null, contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp).height(44.dp)) {
                leading?.let { Box(Modifier.align(Alignment.CenterStart)) { it() } }
                Text(title, Modifier.align(Alignment.Center), style = IOS.headline)
                SheetCircleButton(Icons.Default.Close, "Close", Modifier.align(Alignment.CenterEnd), prominent = true, onClick = dismiss)
            }
            content()
        }
    }
}

/** A round 44 pt button of a sheet's bar: glass with the symbol in the
 *  tint, or the tint filled with a white symbol (`prominent`). */
@Composable
fun SheetCircleButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, prominent: Boolean = false, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 1.08f else 1f, snappy(), label = "press")
    Box(
        modifier.size(44.dp).scale(scale)
            .then(if (prominent) Modifier.clip(CircleShape).background(FluxBlue) else Modifier.cardGlass(CircleShape))
            .clickable(source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, Modifier.size(22.dp), tint = if (prominent) Color.White else FluxBlue) }
}

/** Glass on a cell: darker than the cell, as iOS draws it (#212122 on #2C2C2E). */
fun Modifier.cellGlass(shape: androidx.compose.ui.graphics.Shape): Modifier =
    clip(shape).background(if (Palette.dark) Color(0xFF212122) else Color(0xFFF2F2F7)).glassRim(shape)

/** A section of an inset grouped list: a header, its rows in one rounded
 *  cell, and a footnote under it, as iOS lays a Form out. */
@Composable
fun InsetSection(header: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        header?.let { Text(it, Modifier.padding(start = 20.dp, bottom = 8.dp, top = 12.dp), style = IOS.headline, color = Secondary) }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(CellBackground), content = content)
        footer?.let { Text(it, Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp), style = IOS.footnote, color = Secondary) }
    }
}

/** A row of an inset section, 16 pt in; hairline under it unless last. */
@Composable
fun InsetRow(last: Boolean = true, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), content = content,
        )
        if (!last) Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.5.dp).background(Separator))
    }
}
