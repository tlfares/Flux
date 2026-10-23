package com.fares.flux.mobile

import androidx.compose.animation.*
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.flux.core.tr

/** iOS's snappy spring, for everything that slides. */
fun <T> snappy() = spring<T>(dampingRatio = 0.82f, stiffness = 520f)

/** A card of settings on glass, as iOS's GlassCard. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().cardGlass(RoundedCornerShape(28.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), content = content,
    )
}

@Composable
fun CardTitle(title: String, icon: ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        icon?.let { Icon(it, null, Modifier.size(22.dp), tint = FluxBlue) }
        Text(title, style = IOS.headline)
    }
}

@Composable
fun Footnote(text: String) = Text(text, style = IOS.footnote, color = Secondary)

/** A setting's label above its control: `.subheadline`, secondary. */
@Composable
fun FieldLabel(text: String) = Text(text, style = IOS.subheadline, color = Secondary)

@Composable
fun HairlineDivider() = Box(Modifier.fillMaxWidth().height(0.5.dp).background(Separator))

/** A capsule picker whose selection slides between options and can be
 *  dragged, as iOS's GlassSegmentedPicker. Over each label, a symbol
 *  (`icon`) or an emoji (`mark`, a flag). */
@Composable
fun <T> SegmentedPicker(
    options: List<T>, selected: T, title: (T) -> String, icon: ((T) -> ImageVector)? = null, mark: ((T) -> String)? = null, onSelect: (T) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var dragX by remember { mutableStateOf<Float?>(null) }
    val pressed = remember { mutableStateOf(false) }
    val index = options.indexOf(selected).coerceAtLeast(0)
    val stacked = icon != null || mark != null
    // As iOS's: 40 pt, 58 with a symbol, 66 with a flag, inset by 4.
    val height = when { mark != null -> 66.dp; stacked -> 58.dp; else -> 40.dp }
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(height + 8.dp).glassPanel(RoundedCornerShape(50)).padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val segmentPx = with(density) { segment.toPx() }
        fun nearest(x: Float) = (x / segmentPx).toInt().coerceIn(0, options.size - 1)
        val highlighted = dragX?.let { nearest(it + segmentPx / 2) } ?: index
        val offset by animateDpAsState(
            dragX?.let { with(density) { it.toDp() } } ?: (segment * index), if (dragX != null) spring(stiffness = Spring.StiffnessHigh) else snappy(), label = "lens",
        )
        val lift by animateFloatAsState(if (pressed.value) 1.1f else 1f, spring(dampingRatio = 0.7f, stiffness = 630f), label = "lift")
        // The selection: the tint at 30% (22% while held), as iOS's.
        Box(
            Modifier.offset(x = offset).width(segment).fillMaxHeight().scale(lift).clip(RoundedCornerShape(50))
                .background(FluxBlue.copy(alpha = if (pressed.value) 0.22f else 0.30f)),
        )
        Row(
            Modifier.fillMaxSize()
                .pointerInput(options, index) {
                    detectTapGestures(onPress = {
                        pressed.value = true
                        tryAwaitRelease()
                        pressed.value = false
                    }) { pos ->
                        val i = nearest(pos.x)
                        if (i != index) { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onSelect(options[i]) }
                    }
                }
                .pointerInput(options, index) {
                    detectHorizontalDragGestures(
                        onDragStart = { pressed.value = true; dragX = index * segmentPx },
                        onDragEnd = {
                            val i = nearest((dragX ?: 0f) + segmentPx / 2)
                            pressed.value = false; dragX = null
                            if (i != index) onSelect(options[i])
                        },
                        onDragCancel = { pressed.value = false; dragX = null },
                    ) { _, dx ->
                        val before = nearest((dragX ?: 0f) + segmentPx / 2)
                        dragX = ((dragX ?: 0f) + dx).coerceIn(0f, segmentPx * (options.size - 1))
                        if (nearest(dragX!! + segmentPx / 2) != before) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                },
        ) {
            options.forEachIndexed { i, option ->
                val on = i == highlighted
                val color by animateColorAsState(if (on) Ink else Secondary, label = "label")
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    icon?.let { Icon(it(option), null, Modifier.size(20.dp), tint = color) }
                    mark?.let { Text(it(option), style = IOS.headline) }
                    if (stacked) Spacer(Modifier.height(5.dp))
                    Text(title(option), style = (if (stacked) IOS.caption2 else IOS.subheadline).copy(fontWeight = FontWeight.SemiBold), color = color, maxLines = 1)
                }
            }
        }
    }
}

/** iOS 26's switch: a 64×28 capsule in the tint, its white pill of a knob
 *  (37×24) sliding over, swelling into a lens while held. */
@Composable
fun IOSSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val knob by animateDpAsState(if (checked) 25.dp else 0.dp, snappy(), label = "knob")
    val swell by animateFloatAsState(if (pressed) 1.25f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "swell")
    val fill by animateFloatAsState(if (checked) 1f else 0f, snappy(), label = "fill")
    Box(
        Modifier.size(64.dp, 28.dp)
            .clickable(source, indication = null) { haptics.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn); onChange(!checked) },
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(50)).background(if (Palette.dark) Color(0x5C787880) else Color(0x29787880)))
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(50)).alpha(fill).background(FluxBlue))
        Box(
            Modifier.padding(2.dp).offset(x = knob).size(37.dp, 24.dp).scale(swell)
                .shadow(if (pressed) 6.dp else 2.dp, RoundedCornerShape(50))
                .background(if (pressed) Color.White.copy(alpha = 0.92f) else Color.White, RoundedCornerShape(50)),
        )
    }
}

/** A row with a label and a switch, as Toggle in an iOS form. */
@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = IOS.body)
        IOSSwitch(checked, onChange)
    }
}

/** A glass capsule showing a value, opening the choices: a menu Picker. */
@Composable
fun <T> MenuPicker(label: String, options: List<T>, selected: T, title: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = IOS.body)
        Box {
            // A menu Picker in `.buttonStyle(.glass)`: the value in the label's color.
            Row(
                Modifier.pressable().glassPanel(RoundedCornerShape(50)).clickable(remember { MutableInteractionSource() }, indication = null) { haptics.performHapticFeedback(HapticFeedbackType.ContextClick); open = true }
                    .padding(start = 16.dp, end = 12.dp).height(44.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(title(selected), style = IOS.body)
                Icon(Icons.Default.UnfoldMore, null, Modifier.size(17.dp), tint = Ink)
            }
            DropdownMenu(open, { open = false }, shape = RoundedCornerShape(18.dp), containerColor = if (Palette.dark) Color(0xF21E1E26) else Color(0xF7F4F4F8)) {
                options.forEach { o ->
                    DropdownMenuItem(text = { Text(title(o), fontWeight = if (o == selected) FontWeight.SemiBold else FontWeight.Normal) },
                        trailingIcon = { if (o == selected) Text("✓", color = FluxBlue) }, onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onSelect(o); open = false })
                }
            }
        }
    }
}

/** A round glass button with a symbol, as `.buttonStyle(.glass)`. */
@Composable
fun GlassIconButton(icon: ImageVector, label: String, size: Dp = 44.dp, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, snappy(), label = "press")
    val haptics = LocalHapticFeedback.current
    Box(Modifier.size(size).scale(if (scale < 1f) 2f - scale else 1f).cardGlass(CircleShape).clickable(source, indication = null) { haptics.performHapticFeedback(HapticFeedbackType.ContextClick); onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(size * 0.48f))
    }
}

/** A capsule glass button with text. */
@Composable
fun GlassButton(text: String, prominent: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 1.04f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "press")
    val haptics = LocalHapticFeedback.current
    // `.buttonStyle(.glass)` (the label in the tint) or `.glassProminent` (the tint filled, white label).
    Box(
        modifier.scale(scale).clip(RoundedCornerShape(50))
            .then(if (prominent) Modifier.background(FluxBlue) else Modifier.glassPanel(RoundedCornerShape(50)))
            .clickable(source, indication = null) { haptics.performHapticFeedback(HapticFeedbackType.ContextClick); onClick() }
            .heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = IOS.subheadline.copy(fontWeight = FontWeight.SemiBold), color = if (prominent) Color.White else FluxBlue) }
}

/** `.buttonStyle(.glass)` with a symbol and a title, both in the tint. */
@Composable
fun GlassLabelButton(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 1.03f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "press")
    Row(
        modifier.scale(scale).glassPanel(RoundedCornerShape(50)).clickable(source, indication = null, onClick = onClick).height(40.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = FluxBlue)
        Spacer(Modifier.width(8.dp))
        Text(text, style = IOS.subheadline.copy(fontWeight = FontWeight.SemiBold), color = FluxBlue)
    }
}

/** iOS's Motion.snappy: interactiveSpring(response: 0.34, dampingFraction: 0.84). */
fun <T> foldSpring() = spring<T>(dampingRatio = 0.84f, stiffness = 341f)

/** Whether the CollapsibleCard around is open: work inside can wait for it. */
val LocalCardOpen = staticCompositionLocalOf { true }

/** A card that folds to its title, the same as Screenloss's CollapsibleGlassCard:
 *  the content stays laid out at its own height and is revealed by a growing
 *  clip, fading in, so nothing rewraps or jumps while it opens. */
@Composable
fun CollapsibleCard(title: String, icon: ImageVector, key: String, content: @Composable ColumnScope.() -> Unit) {
    val prefs = LocalContext.current.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
    var open by remember { mutableStateOf(prefs.getBoolean("settings.open.$key", false)) }
    val haptics = LocalHapticFeedback.current
    val reveal by animateFloatAsState(if (open) 1f else 0f, foldSpring(), label = "reveal")
    Column(
        Modifier.widthIn(max = 720.dp).fillMaxWidth().cardGlass(RoundedCornerShape(28.dp))
            .padding(20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                open = !open
                prefs.edit().putBoolean("settings.open.$key", open).apply()
            },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, null, Modifier.width(24.dp).size(22.dp), tint = FluxBlue)
            Text(title, Modifier.weight(1f), style = IOS.headline)
            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp).rotate(180f * reveal), tint = Secondary)
        }
        CompositionLocalProvider(LocalCardOpen provides open) {
            Column(
                Modifier.fillMaxWidth()
                    // Measured whole, placed at the top, shown as much as the spring has opened.
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                        val shown = (placeable.height * reveal.coerceAtLeast(0f)).toInt()
                        layout(placeable.width, shown) { placeable.place(0, 0) }
                    }
                    .clipToBounds()
                    .graphicsLayer { alpha = reveal.coerceIn(0f, 1f) },
            ) {
                Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
            }
        }
    }
}
