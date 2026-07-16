package com.bobai.studio.editor

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.bobai.studio.data.FragmentSourceType
import com.bobai.studio.data.TimelineFragment
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed class ReplaceResult {
    data class Success(val fragment: TimelineFragment) : ReplaceResult()
    data class Failure(val message: String) : ReplaceResult()
}

/**
 * Implements "Change Edit": swap a single timeline fragment's underlying
 * media (photo or video picked from the gallery) while keeping its slot,
 * duration, and effect stack (glitch/shake/etc — those are re-applied at
 * final render time by the timeline compositor, not baked in here) intact.
 *
 * - If the new source is a VIDEO: it is trimmed/looped to exactly match the
 *   fragment's existing duration so the rest of the timeline and beat sync
 *   don't shift.
 * - If the new source is an IMAGE: it is turned into a static clip stretched
 *   to the fragment's duration (with a very subtle Ken-Burns zoom so a still
 *   photo doesn't look frozen/dead in a fast-cut edit — this can be disabled
 *   per-fragment).
 */
class FragmentReplacer {

    suspend fun replaceWithVideo(
        fragment: TimelineFragment,
        newSourcePath: String,
        workingDir: String
    ): ReplaceResult {
        val targetDurationSec = fragment.durationMs / 1000.0
        val outputPath = "$workingDir/frag_${fragment.id}_replaced.mp4"

        // Trim (or, if shorter than needed, loop) the picked video so its
        // final length matches the slot it's filling on the timeline exactly.
        val command = arrayOf(
            "-y",
            "-stream_loop", "-1",
            "-i", newSourcePath,
            "-t", targetDurationSec.toString(),
            "-c:v", "libx264", "-preset", "veryfast",
            "-an",
            outputPath
        )

        return runFFmpeg(command, fragment, outputPath, FragmentSourceType.VIDEO)
    }

    suspend fun replaceWithImage(
        fragment: TimelineFragment,
        newImagePath: String,
        workingDir: String,
        kenBurnsZoom: Boolean = true
    ): ReplaceResult {
        val targetDurationSec = fragment.durationMs / 1000.0
        val outputPath = "$workingDir/frag_${fragment.id}_replaced.mp4"
        val fps = 30
        val totalFrames = (targetDurationSec * fps).toInt().coerceAtLeast(1)

        // zoompan stretches a single still image across the fragment's full
        // duration. A gentle zoom (1.0 -> 1.08) keeps it feeling alive inside
        // a fast-paced edit; set kenBurnsZoom=false for a fully static hold.
        val zoomExpr = if (kenBurnsZoom) "zoom+0.0008" else "1"
        val filter = "scale=1080:1920:force_original_aspect_ratio=increase," +
            "crop=1080:1920," +
            "zoompan=z='min($zoomExpr,1.15)':d=$totalFrames:s=1080x1920:fps=$fps"

        val command = arrayOf(
            "-y",
            "-loop", "1",
            "-i", newImagePath,
            "-vf", filter,
            "-t", targetDurationSec.toString(),
            "-c:v", "libx264", "-preset", "veryfast", "-pix_fmt", "yuv420p",
            outputPath
        )

        return runFFmpeg(command, fragment, outputPath, FragmentSourceType.IMAGE)
    }

    private suspend fun runFFmpeg(
        command: Array<String>,
        fragment: TimelineFragment,
        outputPath: String,
        newType: FragmentSourceType
    ): ReplaceResult = suspendCancellableCoroutine { cont ->
        val joined = command.joinToString(" ") { if (it.contains(" ")) "\"$it\"" else it }
        FFmpegKit.executeAsync(joined) { session ->
            if (ReturnCode.isSuccess(session.returnCode)) {
                val updated = fragment.copy(
                    sourceType = newType,
                    sourcePath = outputPath,
                    trimStartMs = 0,
                    trimEndMs = fragment.durationMs
                    // effects, orderIndex, id are untouched on purpose
                )
                cont.resume(ReplaceResult.Success(updated))
            } else {
                val logs = session.allLogsAsString
                Log.e("FragmentReplacer", "Replace failed: $logs")
                cont.resume(ReplaceResult.Failure(logs ?: "Unknown FFmpeg error"))
            }
        }
    }
}
