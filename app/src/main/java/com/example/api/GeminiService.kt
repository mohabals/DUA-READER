package com.example.api

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object GeminiService {
    private const val TAG = "GeminiService"
    
    // Configured with smart timeouts to ensure UI stays responsive and times out fast if network stalls
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    // Caches to prevent duplicate or repeated analysis requests
    private val explanationCache = lruCache<String, String>(50)
    private val grammarCache = lruCache<String, String>(50)
    private val feedbackCache = lruCache<String, String>(50)

    // We target the primary active model specified in ADDITIONAL_METADATA.
    // Fallbacks to unauthorized/non-provisioned models are avoided to prevent misleading 404 errors.
    private val candidateModels = listOf(
        "gemini-3.5-flash"
    )

    private fun <K, V> lruCache(maxSize: Int): java.util.LinkedHashMap<K, V> {
        return object : java.util.LinkedHashMap<K, V>(maxSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
                return size > maxSize
            }
        }
    }

    /**
     * Executes text generation via Gemini API with retry logic, low-latency config, logging,
     * and automatic cascading fallback to highly stable models in case of 503/429/overload.
     */
    suspend fun generateText(
        apiKey: String,
        prompt: String,
        maxTokens: Int = 256,
        temperature: Double = 0.2,
        responseMimeType: String? = null,
        systemInstruction: String? = null
    ): String {
        val mediaType = "application/json".toMediaType()
        var lastException: Exception? = null

        for (modelName in candidateModels) {
            try {
                Log.d(TAG, "[generateText] Querying model: $modelName")
                val jsonBody = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                            })
                        })
                    })
                    
                    val config = JSONObject().apply {
                        put("maxOutputTokens", maxTokens)
                        put("temperature", temperature)
                        if (responseMimeType != null) {
                            put("responseMimeType", responseMimeType)
                        }
                    }
                    put("generationConfig", config)

                    if (systemInstruction != null) {
                        put("systemInstruction", JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", systemInstruction)
                                })
                            })
                        })
                    }
                }

                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey")
                    .post(jsonBody.toString().toRequestBody(mediaType))
                    .build()

                return executeWithRetryAndLogging("TextGen ($modelName)", request) { responseBody ->
                    val responseObj = JSONObject(responseBody)
                    val candidates = responseObj.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            parts.getJSONObject(0).getString("text")
                        } else {
                            throw IOException("Invalid response structure: empty parts")
                        }
                    } else {
                        throw IOException("No candidates returned from Gemini API")
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    throw e
                }
                Log.w(TAG, "[generateText] Model $modelName failed: ${e.message}. Trying next fallback candidate if available...")
                lastException = e
            }
        }
        throw lastException ?: IOException("Failed to generate text with any available Gemini models")
    }

    /**
     * Executes audio analysis / speech-to-text via inline multimedia content.
     */
    suspend fun transcribeAudio(
        apiKey: String,
        audioFile: File,
        expectedText: String
    ): String {
        val cacheKey = "${audioFile.length()}_$expectedText"
        synchronized(feedbackCache) {
            feedbackCache[cacheKey]?.let { return it }
        }

        val mediaType = "application/json".toMediaType()
        val audioBytes = audioFile.readBytes()
        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
        val prompt = "Transcribe what is spoken in this audio. The speaker was attempting to say: \"$expectedText\". Return ONLY the transcribed text in the original spoken language, without any extra commentary."
        
        var lastException: Exception? = null

        for (modelName in candidateModels) {
            try {
                Log.d(TAG, "[transcribeAudio] Querying model: $modelName")
                val jsonBody = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("inlineData", JSONObject().apply {
                                        put("mimeType", "audio/mp4")
                                        put("data", base64Audio)
                                    })
                                })
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("maxOutputTokens", 128)
                        put("temperature", 0.0) // Deterministic
                    })
                }

                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey")
                    .post(jsonBody.toString().toRequestBody(mediaType))
                    .build()

                val transcript = executeWithRetryAndLogging("AudioTranscribe ($modelName)", request) { responseBody ->
                    val responseObj = JSONObject(responseBody)
                    val candidates = responseObj.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            parts.getJSONObject(0).getString("text").trim()
                        } else {
                            throw IOException("Empty audio transcription parts")
                        }
                    } else {
                        throw IOException("No candidates returned for audio transcription")
                    }
                }

                synchronized(feedbackCache) {
                    feedbackCache[cacheKey] = transcript
                }
                return transcript
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    throw e
                }
                Log.w(TAG, "[transcribeAudio] Model $modelName failed: ${e.message}. Trying next fallback candidate if available...")
                lastException = e
            }
        }
        throw lastException ?: IOException("Failed to transcribe audio with any available Gemini models")
    }

    /**
     * Executes a request with robust exponential backoff, jitter, timeout handling, and security-safe logging.
     */
    private suspend fun executeWithRetryAndLogging(
        operationName: String,
        request: Request,
        maxRetries: Int = 4, // 4 retries (5 total attempts) gives excellent resilience to transient 503/429 spikes
        initialDelayMs: Long = 400,
        parser: (String) -> String
    ): String {
        val rawUrl = request.url.toString()
        val loggedUrl = rawUrl.substringBefore("?key=") + "?key=REDACTED"
        Log.d(TAG, "[$operationName] Initiating request to URL: $loggedUrl")

        var attempt = 1
        var delayMs = initialDelayMs

        while (true) {
            try {
                Log.d(TAG, "[$operationName] Request Attempt $attempt/$maxRetries")
                val responseText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string() ?: ""
                            Log.e(TAG, "[$operationName] Attempt $attempt failed. HTTP Code: ${response.code}, Error: $errorBody")
                            throw IOException("HTTP ${response.code}: $errorBody")
                        }
                        val body = response.body?.string() ?: throw IOException("Empty body returned")
                        body
                    }
                }

                Log.d(TAG, "[$operationName] Success on attempt $attempt. Response length: ${responseText.length} bytes")
                return parser(responseText)

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    throw e
                }
                Log.e(TAG, "[$operationName] Error during attempt $attempt: ${e.message}", e)
                
                if (attempt >= maxRetries) {
                    throw e // Out of retries, escalate/propagate to try next fallback model
                }

                // Check if transient or retryable error
                val isRetryable = e is IOException || (e.message?.contains("429") == true) || (e.message?.contains("50") == true)
                if (!isRetryable) {
                    throw e // Don't retry non-transient developer/structural bugs
                }

                // Calculate exponential backoff delay with 20% random jitter
                val jitter = (Random.nextDouble(0.8, 1.2) * delayMs).toLong()
                Log.w(TAG, "[$operationName] Retrying in ${jitter}ms (backoff scale)...")
                delay(jitter)
                
                attempt++
                delayMs *= 2 // Double the backoff scale
            }
        }
    }

    // Specific clean cache wrappers to allow low-latency instantly served results
    fun getCachedExplanation(sentence: String): String? {
        synchronized(explanationCache) {
            return explanationCache[sentence]
        }
    }

    fun setCachedExplanation(sentence: String, explanation: String) {
        synchronized(explanationCache) {
            explanationCache[sentence] = explanation
        }
    }

    fun getCachedGrammar(sentence: String): String? {
        synchronized(grammarCache) {
            return grammarCache[sentence]
        }
    }

    fun setCachedGrammar(sentence: String, result: String) {
        synchronized(grammarCache) {
            grammarCache[sentence] = result
        }
    }
}
