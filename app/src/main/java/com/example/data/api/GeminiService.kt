package com.example.data.api

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Moshi Models for Gemini API ---

data class Part(
    @Json(name = "text") val text: String? = null,
    @Json(name = "inlineData") val inlineData: InlineData? = null
)

data class InlineData(
    @Json(name = "mimeType") val mimeType: String,
    @Json(name = "data") val data: String
)

data class Content(
    @Json(name = "parts") val parts: List<Part>,
    @Json(name = "role") val role: String? = null
)

data class GoogleSearch(
    val dummy: String? = null // Empty object is represented by empty or dummy fields in some languages, or Map
)

data class Tool(
    @Json(name = "googleSearch") val googleSearch: Map<String, Any>? = null,
    @Json(name = "googleSearchRetrieval") val googleSearchRetrieval: Map<String, Any>? = null
)

data class ThinkingConfig(
    @Json(name = "thinkingLevel") val thinkingLevel: String
)

data class PrebuiltVoiceConfig(
    @Json(name = "voiceName") val voiceName: String
)

data class VoiceConfig(
    @Json(name = "prebuiltVoiceConfig") val prebuiltVoiceConfig: PrebuiltVoiceConfig
)

data class SpeechConfig(
    @Json(name = "voiceConfig") val voiceConfig: VoiceConfig
)

data class GenerationConfig(
    @Json(name = "temperature") val temperature: Float? = null,
    @Json(name = "topP") val topP: Float? = null,
    @Json(name = "topK") val topK: Int? = null,
    @Json(name = "thinkingConfig") val thinkingConfig: ThinkingConfig? = null,
    @Json(name = "responseModalities") val responseModalities: List<String>? = null,
    @Json(name = "speechConfig") val speechConfig: SpeechConfig? = null
)

data class GeminiRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "generationConfig") val generationConfig: GenerationConfig? = null,
    @Json(name = "tools") val tools: List<Map<String, Any>>? = null,
    @Json(name = "systemInstruction") val systemInstruction: Content? = null
)

data class PartResponse(
    @Json(name = "text") val text: String? = null,
    @Json(name = "inlineData") val inlineData: InlineData? = null
)

data class ContentResponse(
    @Json(name = "parts") val parts: List<PartResponse>? = null
)

data class Candidate(
    @Json(name = "content") val content: ContentResponse?,
    @Json(name = "groundingMetadata") val groundingMetadata: GroundingMetadata? = null
)

data class GroundingMetadata(
    @Json(name = "groundingChunks") val groundingChunks: List<GroundingChunk>? = null,
    @Json(name = "webSearchQueries") val webSearchQueries: List<String>? = null
)

data class GroundingChunk(
    @Json(name = "web") val web: WebSource? = null
)

data class WebSource(
    @Json(name = "uri") val uri: String?,
    @Json(name = "title") val title: String?
)

data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<Candidate>?
)

// --- Retrofit API Service ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContentFlash(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse

    @POST("v1beta/models/gemini-3.1-pro-preview:generateContent")
    suspend fun generateContentPro(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse

    @POST("v1beta/models/gemini-3.1-flash-live-preview:generateContent")
    suspend fun generateContentLive(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val apiService: GeminiApiService = retrofit.create(GeminiApiService::class.java)

    fun getApiKey(): String {
        return BuildConfig.GEMINI_API_KEY
    }
}
