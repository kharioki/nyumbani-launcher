package dev.kharioki.nyumbani

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A frosted "liquid glass" panel.
 *
 * [frosted] is a blurred copy of the wallpaper. The panel draws exactly the part of it that sits
 * behind the panel (mapped the same way the wallpaper is cropped to the screen), so the glass
 * picks up the colours of whatever wallpaper you set. On top: a light tint, a soft highlight
 * from the top edge, a thin gradient rim, and a drop shadow.
 */
@Composable
fun GlassPanel(
    frosted: ImageBitmap?,
    screen: IntSize,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 26.dp, vertical = 20.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier
            .shadow(elevation = 28.dp, shape = shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .onGloballyPositioned { origin = it.positionInRoot() }
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val clip = Path().apply { addOutline(outline) }
                val rimWidth = 1.dp.toPx()
                val rim = Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.55f),
                    0.45f to Color.White.copy(alpha = 0.14f),
                    1f to Color.White.copy(alpha = 0.30f),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                val highlight = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.16f),
                    0.45f to Color.White.copy(alpha = 0.04f),
                    1f to Color.White.copy(alpha = 0.02f),
                )

                onDrawBehind {
                    clipPath(clip) {
                        if (frosted != null && screen.width > 0 && screen.height > 0) {
                            // Same "centre crop" mapping as the wallpaper, shifted to this panel.
                            val s = max(screen.width.toFloat() / frosted.width, screen.height.toFloat() / frosted.height)
                            val drawnW = frosted.width * s
                            val drawnH = frosted.height * s
                            val left = (screen.width - drawnW) / 2f - origin.x
                            val top = (screen.height - drawnH) / 2f - origin.y
                            drawImage(
                                image = frosted,
                                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                                dstSize = IntSize(drawnW.roundToInt(), drawnH.roundToInt()),
                                filterQuality = FilterQuality.High,
                            )
                        }
                        // Darken slightly so white text reads on bright wallpapers, then add the sheen.
                        drawRect(Color.Black.copy(alpha = 0.20f))
                        drawRect(highlight)
                    }
                    drawOutline(outline, rim, style = Stroke(width = rimWidth))
                }
            }
            .padding(contentPadding),
        content = content,
    )
}
