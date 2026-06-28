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
        // Dual-layer cache check (global GeminiService cache)
        val cached = com.example.api.GeminiService.getCachedGrammar(text)
        if (cached != null) {
            return@withContext parseWordTags(cached)
        }

        val geminiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
        if (geminiKey.isNotEmpty() && !geminiKey.contains("MY_GEMINI_API_KEY")) {
            try {
                val prompt = "Analyze the Russian sentence and return ONLY a JSON array with no explanation, no markdown, no code blocks. Each element: {\"word\": string, \"pos\": string, \"start\": number, \"end\": number}\nPOS values must be exactly one of: noun, verb, adjective, adverb, other\nstart and end are character indices in the original string.\n\nSentence: $text"
                
                val result = com.example.api.GeminiService.generateText(
                    apiKey = geminiKey,
                    prompt = prompt,
                    maxTokens = 512,
                    temperature = 0.1,
                    responseMimeType = "application/json"
                )
                com.example.api.GeminiService.setCachedGrammar(text, result)
                return@withContext parseWordTags(result)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return@withContext null
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
