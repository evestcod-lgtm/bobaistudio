package com.bobai.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bobai.studio.data.TimelineFragment
import com.bobai.studio.ui.theme.BobRed
import com.bobai.studio.ui.theme.BobSurface

/**
 * Horizontal filmstrip of fragments. Dragging a fragment's right edge
 * previews a new duration; the caller (ViewModel) runs that raw value
 * through `snapDragTime` so it magnetizes to nearby beat markers and lets go
 * cleanly once dragged past the snap window — matching the requested
 * CapCut-style "snap when close, free again when dragged further" feel.
 */
@Composable
fun TimelineView(
    fragments: List<TimelineFragment>,
    pixelsPerMs: Float,
    onDragFragmentEdge: (fragment: TimelineFragment, rawNewEndMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        fragments.forEach { fragment ->
            FragmentBlock(fragment, pixelsPerMs, onDragFragmentEdge)
            Spacer(Modifier.width(2.dp))
        }
    }
}

@Composable
private fun FragmentBlock(
    fragment: TimelineFragment,
    pixelsPerMs: Float,
    onDragEdge: (TimelineFragment, Long) -> Unit
) {
    val widthDp = (fragment.durationMs * pixelsPerMs).dp.coerceAtLeast(24.dp)

    Box(
        modifier = Modifier
            .width(widthDp)
            .height(72.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(BobSurface)
    ) {
        if (fragment.thumbnailPath != null) {
            AsyncImage(
                model = fragment.thumbnailPath,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (fragment.effects.isNotEmpty()) {
            Text(
                fragment.effects.first().type,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(2.dp)
            )
        }

        // Right-edge drag handle for trimming/stretching this fragment,
        // magnet-snapped to nearby beats by the caller.
        Box(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.CenterEnd)
                .width(10.dp)
                .fillMaxHeight()
                .background(BobRed.copy(alpha = 0.6f))
                .pointerInput(fragment.id) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaMs = (dragAmount.x / pixelsPerMs).toLong()
                        onDragEdge(fragment, fragment.trimEndMs + deltaMs)
                    }
                }
        )
    }
}
