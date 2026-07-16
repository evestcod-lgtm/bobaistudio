package com.bobai.studio.ai

import com.bobai.studio.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object GroqClient {

    // Groq's OpenAI-compatible endpoint. Vision model recommended by Groq as
    // the successor to meta-llama/llama-4-scout-17b-16e-instruct (which Groq
    // has scheduled for deprecation). We try Qwen3.6 first and fall back to
    // Scout automatically if the primary model call fails (e.g. capacity,
    // temporary removal, or account-specific availability differences).
    private const val BASE_URL = "https://api.groq.com/"

    const val MODEL_PRIMARY = "qwen/qwen3.6-27b"
    const val MODEL_FALLBACK = "meta-llama/llama-4-scout-17b-16e-instruct"

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
            .build()
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                else HttpLoggingInterceptor.Level.NONE
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: GroqApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GroqApi::class.java)
    }
}
