package com.bobai.studio.audio

import com.bobai.studio.data.BeatMarker
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/** A single point of the waveform preview drawn under the timeline. */
data class WaveformPoint(val timeMs: Long, val amplitude: Float) // amplitude normalized 0..1

/**
 * Handles two things for the audio track, mirroring CapCut's beat feature:
 *
 * 1. Waveform extraction for the visual "loud here / quiet there" display.
 * 2. Auto beat detection (simple energy-based onset detection) PLUS manual
 *    beat placement (long-press + drag along the track, tap again to
 *    remove), and the magnet/snap logic used while dragging timeline
 *    fragments so their edges lock to the nearest beat marker.
 */
class BeatEngine {

    /**
     * Very lightweight onset detector: splits PCM amplitude samples into
     * short windows, computes RMS energy per window, and flags a beat
     * wherever energy rises sharply above the local rolling average. This is
     * intentionally simple (no FFT/tempo tracking) so it runs fast on-device
     * for a several-minute clip, and its output is only ever a *starting
     * point* — the user can freely add/remove markers afterward.
     */
    fun detectBeats(pcmSamples: ShortArray, sampleRateHz: Int, windowMs: Int = 50): List<BeatMarker> {
        val windowSize = (sampleRateHz * windowMs / 1000).coerceAtLeast(1)
        val energies = mutableListOf<Float>()
        var i = 0
        while (i < pcmSamples.size) {
            val end = (i + windowSize).coerceAtMost(pcmSamples.size)
            var sumSquares = 0.0
            for (j in i until end) {
                val v = pcmSamples[j] / 32768.0
                sumSquares += v * v
            }
            val rms = sqrt(sumSquares / (end - i).coerceAtLeast(1)).toFloat()
            energies.add(rms)
            i += windowSize
        }

        val beats = mutableListOf<BeatMarker>()
        val rollingWindow = 8
        for (idx in energies.indices) {
            val windowStart = (idx - rollingWindow).coerceAtLeast(0)
            val localAvg = energies.subList(windowStart, idx.coerceAtLeast(1)).average().toFloat()
            val threshold = localAvg * 1.6f + 0.02f
            if (energies[idx] > threshold) {
                val timeMs = idx.toLong() * windowMs
                // Debounce: don't place beats closer than 200ms apart
                if (beats.isEmpty() || timeMs - beats.last().timeMs > 200) {
                    beats.add(BeatMarker(id = "beat_auto_${Random.nextInt(1_000_000)}", timeMs = timeMs, isAutoDetected = true))
                }
            }
        }
        return beats
    }

    /** Downsampled amplitude envelope for drawing the waveform UI. */
    fun buildWaveform(pcmSamples: ShortArray, sampleRateHz: Int, targetPoints: Int): List<WaveformPoint> {
        if (pcmSamples.isEmpty()) return emptyList()
        val chunkSize = (pcmSamples.size / targetPoints).coerceAtLeast(1)
        val totalDurationMs = (pcmSamples.size.toLong() * 1000L) / sampleRateHz
        val points = mutableListOf<WaveformPoint>()
        var i = 0
        var pointIndex = 0
        while (i < pcmSamples.size) {
            val end = (i + chunkSize).coerceAtMost(pcmSamples.size)
            var peak = 0
            for (j in i until end) peak = maxOf(peak, abs(pcmSamples[j].toInt()))
            val amplitude = (peak / 32768f).coerceIn(0f, 1f)
            val timeMs = (pointIndex.toLong() * totalDurationMs) / targetPoints
            points.add(WaveformPoint(timeMs, amplitude))
            i += chunkSize
            pointIndex++
        }
        return points
    }

    /**
     * Given a fragment edge time (while the user is dragging it on the
     * timeline) and the set of beat markers, returns the snapped time if a
     * beat lies within [snapWindowMs], otherwise returns the original time
     * unchanged (i.e. "un-magnetized" once you drag far enough away, exactly
     * like the requested CapCut-style behavior).
     */
    fun snapToNearestBeat(
        draggedTimeMs: Long,
        beats: List<BeatMarker>,
        snapWindowMs: Long = 150
    ): Long {
        val nearest = beats.minByOrNull { abs(it.timeMs - draggedTimeMs) } ?: return draggedTimeMs
        return if (abs(nearest.timeMs - draggedTimeMs) <= snapWindowMs) nearest.timeMs else draggedTimeMs
    }
}
