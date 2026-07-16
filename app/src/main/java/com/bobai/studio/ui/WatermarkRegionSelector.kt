package com.bobai.studio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.bobai.studio.data.WatermarkRegion
import com.bobai.studio.ui.theme.BobRed

/**
 * Lets the user draw the watermark box directly on the video preview, the
 * same way the reference CapCut-style screenshot shows a red corner-handled
 * rectangle over the mask. Drag inside to move, drag the corner handle to
 * resize.
 */
@Composable
fun WatermarkRegionSelector(
    modifier: Modifier = Modifier,
    onConfirm: (WatermarkRegion) -> Unit,
    onCancel: () -> Unit
) {
    var boxOffset by remember { mutableStateOf(Offset(0.3f, 0.3f)) } // normalized top-left
    var boxSize by remember { mutableStateOf(Offset(0.4f, 0.35f)) }  // normalized w/h
    var canvasSizePx by remember { mutableStateOf(Offset(1f, 1f)) }
    val density = LocalDensity.current

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val dxNorm = dragAmount.x / canvasSizePx.x
                        val dyNorm = dragAmount.y / canvasSizePx.y
                        val newX = (boxOffset.x + dxNorm).coerceIn(0f, 1f - boxSize.x)
                        val newY = (boxOffset.y + dyNorm).coerceIn(0f, 1f - boxSize.y)
                        boxOffset = Offset(newX, newY)
                    }
                }
        ) {
            canvasSizePx = Offset(size.width, size.height)
            val topLeft = Offset(boxOffset.x * size.width, boxOffset.y * size.height)
            val rectSize = androidx.compose.ui.geometry.Size(boxSize.x * size.width, boxSize.y * size.height)

            drawRect(
                color = BobRed,
                topLeft = topLeft,
                size = rectSize,
                style = Stroke(width = 5f)
            )
            // Corner markers, echoing the dashed diamond guides in the reference screenshot
            val corners = listOf(
                topLeft,
                Offset(topLeft.x + rectSize.width, topLeft.y),
                Offset(topLeft.x, topLeft.y + rectSize.height),
                Offset(topLeft.x + rectSize.width, topLeft.y + rectSize.height)
            )
            corners.forEach { c ->
                drawCircle(color = Color.White, radius = 8f, center = c)
                drawCircle(color = BobRed, radius = 8f, center = c, style = Stroke(width = 3f))
            }
        }

        // Bottom-right corner resize handle
        val xPx = (boxOffset.x + boxSize.x) * canvasSizePx.x
        val yPx = (boxOffset.y + boxSize.y) * canvasSizePx.y
        Box(
            modifier = Modifier
                .offset(
                    x = with(density) { xPx.toDp() } - 14.dp,
                    y = with(density) { yPx.toDp() } - 14.dp
                )
                .size(28.dp)
                .background(BobRed, shape = androidx.compose.foundation.shape.CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val dxNorm = dragAmount.x / canvasSizePx.x
                        val dyNorm = dragAmount.y / canvasSizePx.y
                        boxSize = Offset(
                            (boxSize.x + dxNorm).coerceIn(0.08f, 1f - boxOffset.x),
                            (boxSize.y + dyNorm).coerceIn(0.08f, 1f - boxOffset.y)
                        )
                    }
                }
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Обведите водяной знак рамкой, затем нажмите «Убрать»",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel) { Text("Отмена") }
                Button(
                    onClick = {
                        onConfirm(
                            WatermarkRegion(
                                xNorm = boxOffset.x,
                                yNorm = boxOffset.y,
                                widthNorm = boxSize.x,
                                heightNorm = boxSize.y
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BobRed)
                ) { Text("Убрать водяной знак") }
            }
        }
    }
}
