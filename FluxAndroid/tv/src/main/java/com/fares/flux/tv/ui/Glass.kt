package com.fares.flux.tv.ui

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

/** The picture under the controls, blurred: a video plays on a surface of
 *  its own that nothing drawn over it can blur, and Fire OS has no blur of
 *  its own before Android 12. So, as TV launchers do, the frame is copied
 *  small a few times a second, blurred there, and drawn back enlarged. */
class VideoBackdrop {
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
fun rememberVideoBackdrop(view: PlayerView?, active: Boolean): VideoBackdrop {
    val backdrop = remember { VideoBackdrop() }
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
fun Modifier.videoGlass(backdrop: VideoBackdrop, shape: Shape): Modifier {
    var area = Rect.Zero
    return this
        .onGloballyPositioned { area = it.boundsInWindow() }
        .drawWithCache {
            val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
            val rim = Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.6f), 0.35f to Color.White.copy(alpha = 0.10f),
                0.7f to Color.White.copy(alpha = 0.06f), 1f to Color.White.copy(alpha = 0.3f),
                start = Offset.Zero, end = Offset(size.width, size.height),
            )
            val specular = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.14f), 0.4f to Color.White.copy(alpha = 0.03f), 1f to Color.Transparent)
            val innerGlow = Brush.radialGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.05f)),
                center = Offset(size.width / 2, size.height / 2), radius = maxOf(size.width, size.height) * 0.75f,
            )
            val rimWidth = 1.2.dp.toPx()
            onDrawBehind {
                clipPath(path) {
                    val frame = backdrop.frame
                    backdrop.version
                    val picture = backdrop.bounds
                    if (frame != null && picture.width > 0f && area.width > 0f) {
                        // The part of the picture under the glass, in the small frame's pixels.
                        val sx = frame.width / picture.width
                        val sy = frame.height / picture.height
                        val left = ((area.left - picture.left) * sx).roundToInt().coerceIn(0, frame.width - 1)
                        val top = ((area.top - picture.top) * sy).roundToInt().coerceIn(0, frame.height - 1)
                        val right = ((area.right - picture.left) * sx).roundToInt().coerceIn(left + 1, frame.width)
                        val bottom = ((area.bottom - picture.top) * sy).roundToInt().coerceIn(top + 1, frame.height)
                        drawImage(frame, IntOffset(left, top), IntSize(right - left, bottom - top),
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.Low)
                        drawRect(Color(0xFF0B0B12).copy(alpha = 0.42f))
                    } else {
                        // Over a black picture, as the blurred black would look.
                        drawRect(Color(0xF00B0B12))
                    }
                    drawRect(innerGlow)
                    drawRect(specular)
                }
                drawPath(path, rim, style = Stroke(rimWidth))
            }
        }
}

/** Three box blurs one after the other, close to a Gaussian, in place. */
private fun blur(pixels: IntArray, w: Int, h: Int, radius: Int) {
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
