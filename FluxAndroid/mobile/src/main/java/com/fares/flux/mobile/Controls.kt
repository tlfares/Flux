package com.fares.flux.mobile

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
        icon?.let { Icon(it, null, Modifier.size(20.dp), tint = FluxBlue) }
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Footnote(text: String) = Text(text, fontSize = 13.sp, color = Secondary, lineHeight = 17.sp)

@Composable
fun HairlineDivider() = Box(Modifier.fillMaxWidth().height(0.5.dp).background(Ink.copy(alpha = 0.14f)))

/** A capsule picker whose selection slides between options and can be
 *  dragged, as iOS's GlassSegmentedPicker. */
@Composable
fun <T> SegmentedPicker(options: List<T>, selected: T, title: (T) -> String, icon: ((T) -> ImageVector)? = null, onSelect: (T) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var dragX by remember { mutableStateOf<Float?>(null) }
    val pressed = remember { mutableStateOf(false) }
    val index = options.indexOf(selected).coerceAtLeast(0)
    val height = if (icon == null) 40.dp else 56.dp
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
        val lift by animateFloatAsState(if (pressed.value) 1.08f else 1f, snappy(), label = "lift")
        // The selection: a lens tinted with the logo's colors.
        Box(
            Modifier.offset(x = offset).width(segment).fillMaxHeight().scale(lift).clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(FluxBlue.copy(alpha = 0.55f), FluxViolet.copy(alpha = 0.55f))))
                .border(1.dp, Brush.verticalGradient(listOf(Ink.copy(alpha = 0.45f), Ink.copy(alpha = 0.08f))), RoundedCornerShape(50)),
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
                    Text(title(option), fontSize = if (icon == null) 14.sp else 11.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
                }
            }
        }
    }
}

/** iOS's switch: a capsule that fills with the logo's colors, its white knob
 *  sliding over and stretching while held. */
@Composable
fun IOSSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val knob by animateDpAsState(if (checked) 20.dp else 0.dp, snappy(), label = "knob")
    val stretch by animateDpAsState(if (pressed) 8.dp else 0.dp, snappy(), label = "stretch")
    val fill by animateFloatAsState(if (checked) 1f else 0f, snappy(), label = "fill")
    Box(
        Modifier.size(51.dp, 31.dp).clip(RoundedCornerShape(50)).background(Ink.copy(alpha = 0.16f))
            .clickable(source, indication = null) { haptics.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn); onChange(!checked) },
    ) {
        Box(Modifier.fillMaxSize().alpha(fill).background(Brush.horizontalGradient(listOf(FluxBlue, FluxViolet))))
        Box(
            Modifier.padding(2.dp).offset(x = if (checked) knob - stretch else knob).width(27.dp + stretch).height(27.dp)
                .shadow(3.dp, RoundedCornerShape(50)).background(Color.White, RoundedCornerShape(50)),
        )
    }
}

/** A row with a label and a switch, as Toggle in an iOS form. */
@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 15.sp)
        IOSSwitch(checked, onChange)
    }
}

/** A glass capsule showing a value, opening the choices: a menu Picker. */
@Composable
fun <T> MenuPicker(label: String, options: List<T>, selected: T, title: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 15.sp)
        Box {
            Row(
                Modifier.glassPanel(RoundedCornerShape(50)).clickable { haptics.performHapticFeedback(HapticFeedbackType.ContextClick); open = true }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title(selected), fontSize = 15.sp, color = FluxBlue, fontWeight = FontWeight.Medium)
                Icon(Icons.Default.UnfoldMore, null, Modifier.size(16.dp), tint = FluxBlue)
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
    Box(Modifier.size(size).scale(scale).glassPanel(CircleShape).clickable(source, indication = null) { haptics.performHapticFeedback(HapticFeedbackType.ContextClick); onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(size * 0.48f))
    }
}

/** A capsule glass button with text. */
@Composable
fun GlassButton(text: String, prominent: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, snappy(), label = "press")
    val haptics = LocalHapticFeedback.current
    Box(
        modifier.scale(scale).clip(RoundedCornerShape(50))
            .then(if (prominent) Modifier.background(Brush.horizontalGradient(listOf(FluxBlue, FluxViolet))) else Modifier)
            .glassPanel(RoundedCornerShape(50)).clickable(source, indication = null) { haptics.performHapticFeedback(HapticFeedbackType.ContextClick); onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (prominent) Color.White else FluxBlue) }
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
            Icon(icon, null, Modifier.size(22.dp), tint = FluxBlue)
            Text(title, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(20.dp).rotate(180f * reveal), tint = Secondary)
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

/** Fields stacked in one rounded group with hairlines between, as an iOS form. */
@Composable
fun FieldGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Ink.copy(alpha = if (Palette.dark) 0.07f else 0.05f))
            .border(0.75.dp, Ink.copy(alpha = 0.08f), RoundedCornerShape(20.dp)),
        content = content,
    )
}

/** One line of a FieldGroup: the label inside as a placeholder until typed, a
 *  clear button while filled, the line lit with the logo's colors when in use. */
@Composable
fun FieldRow(
    value: String, onChange: (String) -> Unit, placeholder: String, icon: ImageVector,
    secure: Boolean = false, keyboard: KeyboardType = KeyboardType.Text, last: Boolean = false,
    imeAction: ImeAction = ImeAction.Next, onDone: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    var shows by remember { mutableStateOf(false) }
    val tint by animateColorAsState(if (focused) FluxBlue else Tertiary, label = "icon")
    Box {
        Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(20.dp), tint = tint)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, color = Tertiary, fontSize = 16.sp)
                BasicTextField(
                    value, onChange, Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                    singleLine = true, textStyle = TextStyle(color = Ink, fontSize = 16.sp),
                    cursorBrush = Brush.verticalGradient(listOf(FluxBlue, FluxViolet)),
                    visualTransformation = if (secure && !shows) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false, imeAction = imeAction),
                    keyboardActions = KeyboardActions(onDone = { onDone() }, onGo = { onDone() }),
                )
            }
            if (secure && value.isNotEmpty()) {
                Icon(if (shows) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (shows) "Masquer" else "Afficher",
                    Modifier.size(20.dp).clickable(remember { MutableInteractionSource() }, indication = null) { shows = !shows }, tint = Secondary)
            } else if (focused && value.isNotEmpty()) {
                Icon(Icons.Default.Cancel, "Effacer", Modifier.size(18.dp).clickable(remember { MutableInteractionSource() }, indication = null) { onChange("") }, tint = Tertiary)
            }
        }
        if (!last) Box(Modifier.align(Alignment.BottomStart).padding(start = 48.dp).fillMaxWidth().height(0.5.dp).background(Ink.copy(alpha = 0.12f)))
    }
}

/** iOS's regular glass on a plain backdrop: a grey veil lighter at the top,
 *  a rim catching light along the upper edge, a soft shadow on white. */
fun Modifier.cardGlass(shape: Shape): Modifier =
    (if (Palette.dark) this else this.shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.10f)))
        .clip(shape)
        .background(if (Palette.dark) Color(0xFF1C1C1E).copy(alpha = 0.92f) else Color(0xFFF7F7FA))
        .background(Brush.verticalGradient(listOf(Ink.copy(alpha = if (Palette.dark) 0.07f else 0.0f), Color.Transparent)))
        .border(0.75.dp, Brush.verticalGradient(listOf(Ink.copy(alpha = if (Palette.dark) 0.26f else 0.10f), Ink.copy(alpha = if (Palette.dark) 0.06f else 0.04f))), shape)
