package com.bobai.studio.editor

import android.util.Log
import com.bobai.studio.data.WatermarkRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bytedeco.javacpp.Loader

sealed class WatermarkResult {
    data class Success(val outputPath: String) : WatermarkResult()
    data class Failure(val message: String) : WatermarkResult()
}

/**
 * Removes a semi-transparent creator watermark from a video.
 *
 * Approach: the user draws one rectangle over the watermark (as in the
 * reference screenshot's red selection box). We convert that normalized
 * rectangle into pixel coordinates for the source video and run FFmpeg's
 * `delogo` filter, which samples the surrounding pixels to paint over the
 * marked region across every frame. This is a real, working technique for
 * static/semi-static watermarks (the common case for creator handles baked
 * into TikTok exports) — it is not a magic "erase anything" AI, but it is
 * reliable and fast, and does not touch any other part of the frame, so
 * existing effects (glitch, RGB split, etc.) baked into the footage outside
 * the marked box are left completely untouched.
 *
 * FFmpeg itself is invoked as a real command-line process, extracted at
 * runtime via JavaCPP's `Loader.load(org.bytedeco.ffmpeg.ffmpeg::class)`,
 * which resolves to a native binary bundled for the device's ABI. This is
 * the officially documented way to run FFmpeg from JavaCPP Presets.
 */
class WatermarkRemover {

    suspend fun remove(
        inputPath: String,
        outputPath: String,
        region: WatermarkRegion,
        videoWidth: Int,
        videoHeight: Int
    ): WatermarkResult = withContext(Dispatchers.IO) {
        val x = (region.xNorm * videoWidth).toInt().coerceIn(0, videoWidth)
        val y = (region.yNorm * videoHeight).toInt().coerceIn(0, videoHeight)
        val w = (region.widthNorm * videoWidth).toInt().coerceAtLeast(2)
        val h = (region.heightNorm * videoHeight).toInt().coerceAtLeast(2)

        // delogo band=4 gives a soft feathered edge so the patch doesn't look
        // like a hard rectangle cut-out. show=0 renders the final clean output.
        val filter = "delogo=x=$x:y=$y:w=$w:h=$h:band=4:show=0"

        val command = listOf(
            "-y",
            "-i", inputPath,
            "-vf", filter,
            "-c:a", "copy",
            "-preset", "veryfast",
            outputPath
        )

        runFFmpeg(command, outputPath)
    }

    private fun runFFmpeg(args: List<String>, outputPath: String): WatermarkResult {
        return try {
            val ffmpegBinary = Loader.load(org.bytedeco.ffmpeg.ffmpeg::class.java)
            val process = ProcessBuilder(listOf(ffmpegBinary) + args)
                .redirectErrorStream(true)
                .start()
            val log = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                WatermarkResult.Success(outputPath)
            } else {
                Log.e("WatermarkRemover", "FFmpeg exited with code $exitCode: $log")
                WatermarkResult.Failure(log.take(500))
            }
        } catch (e: Exception) {
            Log.e("WatermarkRemover", "Failed to run FFmpeg", e)
            WatermarkResult.Failure(e.message ?: "Unknown FFmpeg error")
        }
    }
}
