package com.bobai.studio.editor

import android.util.Log
import com.bobai.studio.data.FragmentSourceType
import com.bobai.studio.data.TimelineFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bytedeco.javacpp.Loader

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
 *
 * FFmpeg is invoked as a real command-line process extracted at runtime via
 * JavaCPP (`org.bytedeco.ffmpeg.ffmpeg`), the actively maintained successor
 * to the now-retired FFmpegKit.
 */
class FragmentReplacer {

    suspend fun replaceWithVideo(
        fragment: TimelineFragment,
        newSourcePath: String,
        workingDir: String
    ): ReplaceResult = withContext(Dispatchers.IO) {
        val targetDurationSec = fragment.durationMs / 1000.0
        val outputPath = "$workingDir/frag_${fragment.id}_replaced.mp4"

        // Trim (or, if shorter than needed, loop) the picked video so its
        // final length matches the slot it's filling on the timeline exactly.
        val args = listOf(
            "-y",
            "-stream_loop", "-1",
            "-i", newSourcePath,
            "-t", targetDurationSec.toString(),
            "-c:v", "libx264", "-preset", "veryfast",
            "-an",
            outputPath
        )

        runFFmpeg(args, fragment, outputPath, FragmentSourceType.VIDEO)
    }

    suspend fun replaceWithImage(
        fragment: TimelineFragment,
        newImagePath: String,
        workingDir: String,
        kenBurnsZoom: Boolean = true
    ): ReplaceResult = withContext(Dispatchers.IO) {
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

        val args = listOf(
            "-y",
            "-loop", "1",
            "-i", newImagePath,
            "-vf", filter,
            "-t", targetDurationSec.toString(),
            "-c:v", "libx264", "-preset", "veryfast", "-pix_fmt", "yuv420p",
            outputPath
        )

        runFFmpeg(args, fragment, outputPath, FragmentSourceType.IMAGE)
    }

    private fun runFFmpeg(
        args: List<String>,
        fragment: TimelineFragment,
        outputPath: String,
        newType: FragmentSourceType
    ): ReplaceResult {
        return try {
            val ffmpegBinary = Loader.load(org.bytedeco.ffmpeg.ffmpeg::class.java)
            val process = ProcessBuilder(listOf(ffmpegBinary) + args)
                .redirectErrorStream(true)
                .start()
            val log = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                val updated = fragment.copy(
                    sourceType = newType,
                    sourcePath = outputPath,
                    trimStartMs = 0,
                    trimEndMs = fragment.durationMs
                    // effects, orderIndex, id are untouched on purpose
                )
                ReplaceResult.Success(updated)
            } else {
                Log.e("FragmentReplacer", "FFmpeg exited with code $exitCode: $log")
                ReplaceResult.Failure(log.take(500))
            }
        } catch (e: Exception) {
            Log.e("FragmentReplacer", "Failed to run FFmpeg", e)
            ReplaceResult.Failure(e.message ?: "Unknown FFmpeg error")
        }
    }
}
