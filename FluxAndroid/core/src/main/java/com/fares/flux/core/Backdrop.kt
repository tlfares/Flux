package com.fares.flux.core

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/** What's under a glass, blurred, on any Android: a video plays on a
 *  surface of its own that nothing drawn over it can blur, and Android has
 *  no blur of its own before 12. So, as TV launchers do, what's behind is
 *  copied small a few times a second, blurred there, and drawn back
 *  enlarged: the picture (`rememberVideoBackdrop`) or the screen's content
 *  (`rememberContentBackdrop`). */
class Backdrop {
    /** The blurred frame, and where the picture sits on screen. */
    var frame by mutableStateOf<ImageBitmap?>(null)
        private set
    var bounds by mutableStateOf(Rect.Zero)
        private set
    /** Bumped with each frame: the bitmaps are reused, two in turn. */
    var version by mutableIntStateOf(0)
        private set

    private val outputs = arrayOfNulls<Bitmap>(2)
    private var turn = 0

    internal fun publish(pixels: IntArray, width: Int, height: Int, bounds: Rect) {
        var out = outputs[turn]
        if (out == null || out.width != width || out.height != height) {
            out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            outputs[turn] = out
        }
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        turn = 1 - turn
        this.bounds = bounds
        frame = out.asImageBitmap()
        version++
    }

    fun clear() { frame = null }
}

/** Keeps `backdrop` filled while `active`, from the player's surface. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun rememberVideoBackdrop(view: PlayerView?, active: Boolean): Backdrop {
    val backdrop = remember { Backdrop() }
    LaunchedEffect(view, active) {
        // Nothing on screen (zapping, loading): the glass goes dark with it.
        if (!active) backdrop.clear()
        if (view == null || !active) return@LaunchedEffect
        val surface = view.videoSurfaceView as? SurfaceView ?: return@LaunchedEffect
        val handler = Handler(Looper.getMainLooper())
        var source: Bitmap? = null
        var vivid: Bitmap? = null
        var pixels = IntArray(0)
        val vibrancy = android.graphics.Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply {
                setSaturation(1.5f)
                postConcat(android.graphics.ColorMatrix().apply { setScale(0.85f, 0.85f, 0.85f, 1f) })
            })
        }
        while (isActive) {
            if (surface.width > 0 && surface.height > 0 && surface.holder.surface.isValid) {
                // Small: 160 pixels across, about 1/12 of the picture.
                val w = 160
                val h = maxOf(1, w * surface.height / surface.width)
                val src = source?.takeIf { it.width == w && it.height == h }
                    ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { source = it }
                val copied = suspendCancellableCoroutine { done ->
                    runCatching { PixelCopy.request(surface, src, { done.resume(it == PixelCopy.SUCCESS) }, handler) }
                        .onFailure { done.resume(false) }
                }
                if (copied) {
                    if (pixels.size != w * h) pixels = IntArray(w * h)
                    // Vibrancy, as the Glass launcher's: colours lifted, a little darker.
                    val tinted = vivid?.takeIf { it.width == w && it.height == h }
                        ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { vivid = it }
                    android.graphics.Canvas(tinted).drawBitmap(src, 0f, 0f, vibrancy)
                    tinted.getPixels(pixels, 0, w, 0, 0, w, h)
                    withContext(Dispatchers.Default) { blur(pixels, w, h, radius = 4) }
                    val at = IntArray(2)
                    surface.getLocationInWindow(at)
                    backdrop.publish(pixels, w, h, Rect(at[0].toFloat(), at[1].toFloat(), (at[0] + surface.width).toFloat(), (at[1] + surface.height).toFloat()))
                }
            }
            delay(110)
        }
    }
    return backdrop
}

/** Glass over the picture, as the Glass launcher draws it after tvOS: the
 *  blurred picture behind, a tint dark enough for white text over any
 *  picture, a highlight across the top, a glow inside and a bright rim.
 *  Without a frame (zapping, loading, a protected stream), dark like the
 *  black picture behind. */
fun Modifier.videoGlass(backdrop: Backdrop, shape: Shape, tint: Color? = null): Modifier {
    var area = Rect.Zero
    return this
        .onGloballyPositioned { area = it.boundsInWindow() }
        .drawWithCache {
            val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
            onDrawBehind {
                clipPath(path) {
                    if (drawFrame(backdrop, area)) {
                        drawRect(tint ?: Color(0xFF0B0B12).copy(alpha = 0.42f))
                    } else {
                        // Over a black picture, as the blurred black would look.
                        drawRect(tint?.copy(alpha = 1f) ?: Color(0xF00B0B12))
                    }
                }
            }
        }
        .then(if (tint == null) Modifier.glassSheen(shape) else Modifier)
}

/** What makes a pane read as glass, whatever's behind it, as on iOS: a
 *  highlight across the top, a glow inside, and a rim catching the light
 *  at the top left and bottom right. `strength` dims it all on light
 *  screens, where white light shows less. */
fun Modifier.glassSheen(shape: Shape, strength: Float = 1f): Modifier = drawWithCache {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    fun white(alpha: Float) = Color.White.copy(alpha = alpha * strength)
    val rim = Brush.linearGradient(
        0f to white(0.6f), 0.35f to white(0.10f), 0.7f to white(0.06f), 1f to white(0.3f),
        start = Offset.Zero, end = Offset(size.width, size.height),
    )
    // The highlight's depth fixed, not a share of the height: a tall card
    // gets the same sheen along its top as a button.
    val specularDepth = minOf(size.height, 56.dp.toPx())
    val specular = Brush.verticalGradient(0f to white(0.14f), 0.4f to white(0.03f), 1f to Color.Transparent, endY = specularDepth)
    val innerGlow = Brush.radialGradient(
        listOf(Color.Transparent, white(0.05f)),
        center = Offset(size.width / 2, size.height / 2), radius = maxOf(size.width, size.height) * 0.75f,
    )
    val rimWidth = 1.2.dp.toPx()
    onDrawBehind {
        clipPath(path) {
            drawRect(innerGlow)
            drawRect(specular)
        }
        drawPath(path, rim, style = Stroke(rimWidth))
    }
}

/** What's under the glass, blurred, with `tint` over it; `empty` alone
 *  while there's no frame yet. The glass's own rim and sheen are its own. */
fun Modifier.backdropGlass(backdrop: Backdrop, tint: Color, empty: Color): Modifier {
    var area = Rect.Zero
    return this
        .onGloballyPositioned { area = it.boundsInWindow() }
        .drawBehind {
            if (drawFrame(backdrop, area)) drawRect(tint) else drawRect(empty)
        }
}

/** The part of the frame under `area` (in the window), stretched over the
 *  glass; false when there's none to draw. */
private fun DrawScope.drawFrame(backdrop: Backdrop, area: Rect): Boolean {
    val frame = backdrop.frame
    backdrop.version
    val picture = backdrop.bounds
    if (frame == null || picture.width <= 0f || area.width <= 0f) return false
    // The part of the picture under the glass, in the small frame's pixels.
    val sx = frame.width / picture.width
    val sy = frame.height / picture.height
    val left = ((area.left - picture.left) * sx).roundToInt().coerceIn(0, frame.width - 1)
    val top = ((area.top - picture.top) * sy).roundToInt().coerceIn(0, frame.height - 1)
    val right = ((area.right - picture.left) * sx).roundToInt().coerceIn(left + 1, frame.width)
    val bottom = ((area.bottom - picture.top) * sy).roundToInt().coerceIn(top + 1, frame.height)
    drawImage(frame, IntOffset(left, top), IntSize(right - left, bottom - top),
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.Low)
    return true
}

/** The screen's content as `layer` records it (`bounds`, in the window),
 *  kept in `backdrop` while `active`: drawn small into a second layer, read back and blurred. For
 *  the Android versions whose own blur Haze doesn't use. */
@Composable
fun rememberContentBackdrop(layer: GraphicsLayer, active: Boolean, bounds: () -> Rect): Backdrop {
    val backdrop = remember { Backdrop() }
    val small = rememberGraphicsLayer()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    LaunchedEffect(layer, active) {
        if (!active) { backdrop.clear(); return@LaunchedEffect }
        var pixels = IntArray(0)
        while (isActive) {
            val full = layer.size
            if (full.width > 0 && full.height > 0) {
                // 120 pixels across: a blur hides the rest anyway.
                val w = 120
                val h = maxOf(1, w * full.height / full.width)
                val copied = runCatching {
                    small.record(density, direction, IntSize(w, h)) {
                        scale(w.toFloat() / full.width, h.toFloat() / full.height, pivot = Offset.Zero) { drawLayer(layer) }
                    }
                    val shot = small.toImageBitmap().asAndroidBitmap()
                    // Read back from the GPU's copy, small, when it gives one.
                    val readable = if (android.os.Build.VERSION.SDK_INT >= 26 && shot.config == Bitmap.Config.HARDWARE) shot.copy(Bitmap.Config.ARGB_8888, false) else shot
                    if (pixels.size != w * h) pixels = IntArray(w * h)
                    readable.getPixels(pixels, 0, w, 0, 0, minOf(w, readable.width), minOf(h, readable.height))
                }.isSuccess
                if (copied) {
                    withContext(Dispatchers.Default) { blur(pixels, w, h, radius = 3) }
                    backdrop.publish(pixels, w, h, bounds())
                }
            }
            delay(70)
        }
    }
    return backdrop
}

/** Three box blurs one after the other, close to a Gaussian, in place. */
internal fun blur(pixels: IntArray, w: Int, h: Int, radius: Int) {
    val tmp = IntArray(pixels.size)
    repeat(3) {
        boxPass(pixels, tmp, w, h, radius, horizontal = true)
        boxPass(tmp, pixels, w, h, radius, horizontal = false)
    }
}

private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val length = if (horizontal) w else h
    val span = 2 * r + 1
    for (line in 0 until lines) {
        fun at(i: Int): Int {
            val k = i.coerceIn(0, length - 1)
            return if (horizontal) line * w + k else k * w + line
        }
        var rs = 0; var gs = 0; var bs = 0
        for (i in -r..r) {
            val p = src[at(i)]
            rs += (p shr 16) and 0xFF; gs += (p shr 8) and 0xFF; bs += p and 0xFF
        }
        for (i in 0 until length) {
            dst[at(i)] = (0xFF shl 24) or ((rs / span) shl 16) or ((gs / span) shl 8) or (bs / span)
            val out = src[at(i - r)]
            val inn = src[at(i + r + 1)]
            rs += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
            gs += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
            bs += (inn and 0xFF) - (out and 0xFF)
        }
    }
}
