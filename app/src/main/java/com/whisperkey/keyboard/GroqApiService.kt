package com.whisperkey.keyboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client for Groq's Whisper API endpoint.
 * Sends audio files and receives transcribed text.
 */
class GroqApiService(private val apiKey: String) {

    companion object {
        private const val BASE_URL = "https://api.groq.com/openai/v1/audio/transcriptions"
        private const val MODEL = "whisper-large-v3"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Transcribe audio file using Groq's Whisper API.
     *
     * @param audioFile WAV file to transcribe
     * @param language Language code (e.g. "es", "en") or null for auto-detect
     * @return Transcribed text, or error message
     */
    suspend fun transcribe(audioFile: File, language: String? = null): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("model", MODEL)
                    .addFormDataPart(
                        "file",
                        audioFile.name,
                        audioFile.asRequestBody("audio/wav".toMediaType())
                    )
                    .addFormDataPart("response_format", "json")
                    .apply {
                        if (!language.isNullOrBlank()) {
                            addFormDataPart("language", language)
                        }
                    }
                    .build()

                val request = Request.Builder()
                    .url(BASE_URL)
                    .header("Authorization", "Bearer $apiKey")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val text = json.optString("text", "").trim()
                    if (text.isNotEmpty()) {
                        Result.success(text)
                    } else {
                        Result.failure(Exception("Empty transcription — try speaking louder"))
                    }
                } else {
                    val errorMsg = try {
                        val errJson = JSONObject(body)
                        errJson.optJSONObject("error")?.optString("message")
                            ?: "API error ${response.code}"
                    } catch (e: Exception) {
                        "API error ${response.code}: $body"
                    }
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: IOException) {
                Result.failure(Exception("Network error: ${e.message}"))
            } catch (e: Exception) {
                Result.failure(Exception("Error: ${e.message}"))
            }
        }
    }
}
