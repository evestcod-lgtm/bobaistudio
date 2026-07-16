package com.bobai.studio.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.ByteArrayOutputStream

/** Result of asking the vision model to describe one fragment's key frame. */
data class SceneAnalysis(
    val label: String,
    val tags: List<String>,
    val hasOnScreenText: Boolean,
    val detectedTextRegionsNorm: List<FloatArray> // [x, y, w, h] each 0..1, for future auto watermark hints
)

private data class SceneJson(
    val label: String? = null,
    val tags: List<String>? = null,
    val has_text: Boolean? = null,
    val text_boxes: List<List<Float>>? = null
)

/**
 * Wraps Groq vision calls used by the "Change Edit" feature: for every
 * timeline fragment we send one representative frame and ask for a short
 * scene description + tags, which are shown as labels in the fragment grid
 * so the user can quickly tell fragments apart before swapping them out.
 */
class SceneAnalyzer {

    private val gson = Gson()

    suspend fun analyzeFrame(bitmap: Bitmap): SceneAnalysis {
        val base64Image = bitmapToBase64(bitmap)
        val prompt = """
            Look at this single video frame from a short-form edit.
            Return ONLY a JSON object, no extra text, with this exact shape:
            {"label": "3-5 word scene description", "tags": ["tag1","tag2","tag3"],
             "has_text": true/false, "text_boxes": [[x,y,w,h], ...]}
            Coordinates in text_boxes are normalized 0..1 relative to frame
            width/height, representing any overlaid text or watermark-like text.
            If there is no visible on-screen text, return an empty text_boxes array.
        """.trimIndent()

        val messages = listOf(
            GroqMessage(
                role = "user",
                content = listOf(
                    GroqContentPart.text(prompt),
                    GroqContentPart.imageUrl("data:image/jpeg;base64,$base64Image")
                )
            )
        )

        val raw = try {
            callModel(GroqClient.MODEL_PRIMARY, messages)
        } catch (primaryError: Exception) {
            Log.w("SceneAnalyzer", "Primary model failed, falling back", primaryError)
            callModel(GroqClient.MODEL_FALLBACK, messages)
        }

        return parseSceneJson(raw)
    }

    private suspend fun callModel(model: String, messages: List<GroqMessage>): String {
        val request = GroqChatRequest(
            model = model,
            messages = messages,
            response_format = GroqResponseFormat("json_object")
        )
        val response = GroqClient.api.chatCompletion(request)
        return response.choices.firstOrNull()?.message?.content
            ?: throw IllegalStateException("Empty Groq response from $model")
    }

    private fun parseSceneJson(raw: String): SceneAnalysis {
        return try {
            val parsed = gson.fromJson(raw, SceneJson::class.java)
            SceneAnalysis(
                label = parsed.label ?: "Фрагмент",
                tags = parsed.tags ?: emptyList(),
                hasOnScreenText = parsed.has_text ?: false,
                detectedTextRegionsNorm = parsed.text_boxes?.map { it.toFloatArray() } ?: emptyList()
            )
        } catch (e: JsonSyntaxException) {
            Log.e("SceneAnalyzer", "Could not parse Groq JSON: $raw", e)
            SceneAnalysis("Фрагмент", emptyList(), false, emptyList())
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        // Downscale before sending — keeps request small/fast and well within
        // Groq's free-tier token budget while remaining plenty sharp for
        // scene classification purposes.
        val scaled = if (bitmap.width > 768) {
            val ratio = 768f / bitmap.width
            Bitmap.createScaledBitmap(bitmap, 768, (bitmap.height * ratio).toInt(), true)
        } else bitmap
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
