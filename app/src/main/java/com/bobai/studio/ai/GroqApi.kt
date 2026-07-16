package com.bobai.studio.ai

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

// ---- Request/response DTOs matching Groq's OpenAI-compatible chat completions API ----

data class GroqChatRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val temperature: Double = 0.2,
    val max_tokens: Int = 512,
    val response_format: GroqResponseFormat? = null
)

data class GroqResponseFormat(val type: String = "json_object")

data class GroqMessage(
    val role: String,
    val content: List<GroqContentPart>
)

// Vision content parts: either plain text or an inline base64 image.
// NOTE: Gson serializes concrete classes field-by-field with no built-in
// polymorphism/discriminator support, so a sealed class here would silently
// drop the "type" tag needed by Groq's API. Using one flat class with
// nullable fields keeps serialization predictable and correct.
data class GroqContentPart(
    val type: String,
    val text: String? = null,
    val image_url: GroqImageUrl? = null
) {
    companion object {
        fun text(value: String) = GroqContentPart(type = "text", text = value)
        fun imageUrl(url: String) = GroqContentPart(type = "image_url", image_url = GroqImageUrl(url))
    }
}

data class GroqImageUrl(val url: String) // "data:image/jpeg;base64,<...>"

data class GroqChatResponse(
    val choices: List<GroqChoice>
)

data class GroqChoice(
    val message: GroqResponseMessage
)

data class GroqResponseMessage(
    val role: String,
    val content: String
)

interface GroqApi {
    @Headers("Content-Type: application/json")
    @POST("openai/v1/chat/completions")
    suspend fun chatCompletion(@Body request: GroqChatRequest): GroqChatResponse
}
