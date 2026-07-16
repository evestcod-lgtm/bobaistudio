package com.bobai.studio.editor

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.bobai.studio.data.WatermarkRegion
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class WatermarkResult {
    data class Success(val outputPath: String) : WatermarkResult()
    data class Failure(val message: String) : WatermarkResult()
}

/**
 * Removes a semi-transparent creator watermark from a video.
 *
 * Approach: the user draws one rectangle over the watermark (as in the
 * reference screenshot's red selection box). We convert that normalized
 * rectangle into pixel coordinates for the source video and apply FFmpeg's
 * `delogo` filter, which samples the surrounding pixels to paint over the
 * marked region across every frame. This is a real, working technique for
 * static/semi-static watermarks (the common case for creator handles baked
 * into TikTok exports) — it is not a magic "erase anything" AI, but it is
 * reliable and fast, and does not touch any other part of the frame, so
 * existing effects (glitch, RGB split, etc.) baked into the footage outside
 * the marked box are left completely untouched.
 */
class WatermarkRemover {

    suspend fun remove(
        inputPath: String,
        outputPath: String,
        region: WatermarkRegion,
        videoWidth: Int,
        videoHeight: Int
    ): WatermarkResult {
        val x = (region.xNorm * videoWidth).toInt().coerceIn(0, videoWidth)
        val y = (region.yNorm * videoHeight).toInt().coerceIn(0, videoHeight)
        val w = (region.widthNorm * videoWidth).toInt().coerceAtLeast(2)
        val h = (region.heightNorm * videoHeight).toInt().coerceAtLeast(2)

        // delogo band=4 gives a soft feathered edge so the patch doesn't look
        // like a hard rectangle cut-out. show=0 renders the final clean output
        // (show=1 would draw a debug outline, useful only while testing).
        val filter = "delogo=x=$x:y=$y:w=$w:h=$h:band=4:show=0"

        val command = arrayOf(
            "-y",
            "-i", inputPath,
            "-vf", filter,
            "-c:a", "copy",
            "-preset", "veryfast",
            outputPath
        )

        return runFFmpeg(command, outputPath)
    }

    private suspend fun runFFmpeg(command: Array<String>, outputPath: String): WatermarkResult =
        suspendCancellableCoroutine { cont ->
            val joined = command.joinToString(" ") { if (it.contains(" ")) "\"$it\"" else it }
            FFmpegKit.executeAsync(joined) { session ->
                val code = session.returnCode
                if (ReturnCode.isSuccess(code)) {
                    cont.resume(WatermarkResult.Success(outputPath))
                } else {
                    val logs = session.allLogsAsString
                    Log.e("WatermarkRemover", "FFmpeg failed: $logs")
                    cont.resume(WatermarkResult.Failure(logs ?: "Unknown FFmpeg error"))
                }
            }
            cont.invokeOnCancellation {
                // best-effort cancellation of in-flight session
            }
        }
}
