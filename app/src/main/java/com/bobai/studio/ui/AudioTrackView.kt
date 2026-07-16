package com.bobai.studio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.bobai.studio.audio.WaveformPoint
import com.bobai.studio.data.BeatMarker
import com.bobai.studio.ui.theme.BobCyan
import com.bobai.studio.ui.theme.BobRed
import kotlin.math.abs

/**
 * Renders the amplitude waveform (louder = taller bar, like CapCut) with
 * beat markers as dots underneath. When [beatPlacementMode] is active, tap
 * anywhere on the track to drop a beat; tap an existing dot to remove it —
 * mirroring the "tap to add rhythm point, hover/tap again to remove" flow
 * that was requested.
 */
@Composable
fun AudioTrackView(
    waveform: List<WaveformPoint>,
    beats: List<BeatMarker>,
    totalDurationMs: Long,
    beatPlacementMode: Boolean,
    onAddBeat: (timeMs: Long) -> Unit,
    onRemoveBeat: (beatId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(Color(0xFF141414))
                .pointerInput(beatPlacementMode, beats) {
                    detectTapGestures { offset ->
                        if (!beatPlacementMode || totalDurationMs <= 0) return@detectTapGestures
                        val tapTimeMs = ((offset.x / size.width) * totalDurationMs).toLong()

                        // If tapping near an existing beat dot, remove it instead of adding.
                        val nearBeat = beats.minByOrNull { abs(it.timeMs - tapTimeMs) }
                        val nearBeatPx = nearBeat?.let { (it.timeMs.toFloat() / totalDurationMs) * size.width }
                        if (nearBeat != null && nearBeatPx != null && abs(nearBeatPx - offset.x) < 24f) {
                            onRemoveBeat(nearBeat.id)
                        } else {
                            onAddBeat(tapTimeMs)
                        }
                    }
                }
        ) {
            if (waveform.isNotEmpty() && totalDurationMs > 0) {
                val barWidth = size.width / waveform.size
                waveform.forEachIndexed { index, point ->
                    val barHeight = (point.amplitude * size.height * 0.85f).coerceAtLeast(2f)
                    val x = index * barWidth
                    drawLine(
                        color = BobCyan.copy(alpha = 0.85f),
                        start = Offset(x, size.height / 2 - barHeight / 2),
                        end = Offset(x, size.height / 2 + barHeight / 2),
                        strokeWidth = (barWidth * 0.7f).coerceAtLeast(1f)
                    )
                }
            }

            // Beat markers as dots along the bottom edge, like the reference app.
            if (totalDurationMs > 0) {
                beats.forEach { beat ->
                    val x = (beat.timeMs.toFloat() / totalDurationMs) * size.width
                    drawCircle(
                        color = if (beat.isAutoDetected) BobCyan else BobRed,
                        radius = 6f,
                        center = Offset(x, size.height - 8f)
                    )
                }
            }
        }

        if (beatPlacementMode) {
            Text(
                "Режим ритма: тапните по дорожке — поставить бит, тапните по точке — убрать",
                color = BobRed,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }
    }
}
