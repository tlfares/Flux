package com.fares.flux.mobile

import androidx.compose.animation.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.saveable.rememberSaveable
import com.fares.flux.core.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.*
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.exp

/** Inter: drawn after San Francisco, closer to iOS than Roboto. Inter
 *  is heavier than San Francisco's text cut at a weight, so each weight
 *  takes Inter's one lighter: semibold its 500, bold its 600. Only the
 *  large titles, where San Francisco's display cut is heavier, ask for
 *  `ExtraBold`, Inter's 700. */
val Inter = FontFamily(
    Font(R.font.inter_400, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_500, FontWeight.SemiBold),
    Font(R.font.inter_600, FontWeight.Bold),
    Font(R.font.inter_700, FontWeight.ExtraBold),
)

/** Tighter as text gets bigger, as San Francisco is set: Inter's own
 *  "dynamic metrics" (rsms.me/inter/dynmetrics). */
fun tracking(size: Float): TextUnit = (-0.0223f + 0.185f * exp(-0.1745f * size)).em

/** The font's own line height, as SwiftUI lays a line out: a fixed one
 *  (Material's) made every row taller than iOS's. `line` is the style's
 *  leading on iOS, kept for reference. */
@Suppress("UNUSED_PARAMETER")
private fun style(size: Float, line: Float, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Inter, fontSize = size.sp, lineHeight = TextUnit.Unspecified, fontWeight = weight, letterSpacing = tracking(size))

/** iOS's text styles, at their default size: `.largeTitle` to `.caption2`. */
object IOS {
    val largeTitle = style(34f, 41f, FontWeight.ExtraBold)
    val title = style(28f, 34f, FontWeight.Bold)
    val title2 = style(22f, 28f, FontWeight.Bold)
    val title3 = style(20f, 25f, FontWeight.SemiBold)
    val headline = style(17f, 22f, FontWeight.SemiBold)
    val body = style(17f, 22f)
    val callout = style(16f, 21f)
    val subheadline = style(15f, 20f)
    val footnote = style(13f, 18f)
    val caption = style(12f, 16f)
    val caption2 = style(11f, 13f)
}

/** Every Material style in Inter with iOS's tracking, so text without a
 *  style of its own reads as iOS's body does. */
val IOSTypography = Typography().let { t ->
    fun TextStyle.ios() = copy(fontFamily = Inter, letterSpacing = tracking(fontSize.value), lineHeight = TextUnit.Unspecified)
    Typography(
        displayLarge = t.displayLarge.ios(), displayMedium = t.displayMedium.ios(), displaySmall = t.displaySmall.ios(),
        headlineLarge = t.headlineLarge.ios(), headlineMedium = t.headlineMedium.ios(), headlineSmall = t.headlineSmall.ios(),
        titleLarge = IOS.title3, titleMedium = IOS.headline, titleSmall = IOS.subheadline.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = IOS.body, bodyMedium = IOS.subheadline, bodySmall = IOS.footnote,
        labelLarge = IOS.subheadline.copy(fontWeight = FontWeight.SemiBold), labelMedium = IOS.caption.copy(fontWeight = FontWeight.Medium),
        labelSmall = IOS.caption2.copy(fontWeight = FontWeight.Medium),
    )
}
