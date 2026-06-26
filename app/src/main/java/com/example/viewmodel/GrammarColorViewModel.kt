package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.WordTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import com.example.BuildConfig

class GrammarColorViewModel : ViewModel() {
    private val client = OkHttpClient()
    private val cache = ConcurrentHashMap<String, List<WordTag>>()

    private val _sentenceTags = MutableStateFlow<Map<String, List<WordTag>>>(emptyMap())
    val sentenceTags = _sentenceTags.asStateFlow()

    fun getTagsForSentence(text: String): List<WordTag>? {
        return cache[text]
    }

    fun requestGrammarAnalysis(text: String) {
        if (text.isBlank() || cache.containsKey(text)) return

        viewModelScope.launch {
            val tags = fetchGrammarTags(text)
            if (tags != null) {
                cache[text] = tags
                _sentenceTags.value = _sentenceTags.value + (text to tags)
            }
        }
    }

    private suspend fun fetchGrammarTags(text: String): List<WordTag>? = withContext(Dispatchers.IO) {
        // Try Claude API first if key is available
        val claudeKey = try { BuildConfig.CLAUDE_API_KEY } catch (e: Exception) { "" }
        if (claudeKey.isNotEmpty() && !claudeKey.contains("YOUR_CLAUDE_API_KEY")) {
            val result = callClaudeApi(text, claudeKey)
            if (result != null) return@withContext result
        }

        // Fallback to Gemini API
        val geminiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
        if (geminiKey.isNotEmpty() && !geminiKey.contains("MY_GEMINI_API_KEY")) {
            return@withContext callGeminiApi(text, geminiKey)
        }

        return@withContext null
    }

    private fun callClaudeApi(text: String, apiKey: String): List<WordTag>? {
        try {
            val mediaType = "application/json".toMediaType()
            val systemPrompt = "You are a Russian morphology analyzer. You receive a Russian sentence and return ONLY a JSON array with no explanation, no markdown, no code blocks. Each element: {\"word\": string, \"pos\": string, \"start\": number, \"end\": number}\nPOS values must be exactly one of: noun, verb, adjective, adverb, other\nstart and end are character indices in the original string."

            val jsonBody = JSONObject().apply {
                put("model", "claude-3-5-sonnet-20241022")
                put("max_tokens", 1024)
                put("system", systemPrompt)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", text)
                    })
                })
            }

            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .post(jsonBody.toString().toRequestBody(mediaType))
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                val responseObj = JSONObject(bodyStr)
                val contentArray = responseObj.getJSONArray("content")
                if (contentArray.length() > 0) {
                    val textContent = contentArray.getJSONObject(0).getString("text")
                    return parseWordTags(textContent)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun callGeminiApi(text: String, apiKey: String): List<WordTag>? {
        try {
            val mediaType = "application/json".toMediaType()
            val prompt = "Analyze the Russian sentence and return ONLY a JSON array with no explanation, no markdown, no code blocks. Each element: {\"word\": string, \"pos\": string, \"start\": number, \"end\": number}\nPOS values must be exactly one of: noun, verb, adjective, adverb, other\nstart and end are character indices in the original string.\n\nSentence: $text"

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
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                .post(jsonBody.toString().toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                val responseObj = JSONObject(bodyStr)
                val candidates = responseObj.getJSONArray("candidates")
                if (candidates.length() > 0) {
                    val parts = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                    if (parts.length() > 0) {
                        val responseText = parts.getJSONObject(0).getString("text")
                        return parseWordTags(responseText)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun parseWordTags(jsonText: String): List<WordTag> {
        val list = mutableListOf<WordTag>()
        try {
            var cleaned = jsonText.trim()
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.removePrefix("```json")
            }
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.removePrefix("```")
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.removeSuffix("```")
            }
            cleaned = cleaned.trim()

            val array = JSONArray(cleaned)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val word = obj.getString("word")
                val pos = obj.getString("pos")
                val start = obj.getInt("start")
                val end = obj.getInt("end")
                list.add(WordTag(word, pos, start, end))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
