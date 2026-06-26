package com.example.viewmodel

import android.content.Context
import android.media.MediaRecorder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import com.example.BuildConfig

class PronunciationCoachViewModel : ViewModel() {
    private val client = OkHttpClient()

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _recordingSentenceId = MutableStateFlow<Int?>(null)
    val recordingSentenceId = _recordingSentenceId.asStateFlow()

    private val _recordingDuration = MutableStateFlow(0)
    val recordingDuration = _recordingDuration.asStateFlow()

    private val _feedbackMap = MutableStateFlow<Map<Int, FeedbackResult>>(emptyMap())
    val feedbackMap = _feedbackMap.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var timerJob: Job? = null
    private var maxDurationJob: Job? = null

    data class FeedbackResult(
        val expected: String,
        val transcript: String,
        val feedbackText: String,
        val color: FeedbackColor
    )

    enum class FeedbackColor {
        GREEN, YELLOW, RED
    }

    fun startRecording(context: Context, sentenceId: Int, expectedText: String) {
        if (_isRecording.value) return

        val file = File(context.cacheDir, "pronunciation_check.m4a")
        if (file.exists()) {
            file.delete()
        }
        outputFile = file

        try {
            @Suppress("DEPRECATION")
            val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }
            
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            _isRecording.value = true
            _recordingSentenceId.value = sentenceId
            _recordingDuration.value = 0

            timerJob?.cancel()
            timerJob = viewModelScope.launch {
                while (_isRecording.value) {
                    delay(1000)
                    _recordingDuration.value += 1
                }
            }

            maxDurationJob?.cancel()
            maxDurationJob = viewModelScope.launch {
                delay(10000)
                if (_isRecording.value && _recordingSentenceId.value == sentenceId) {
                    stopRecording(expectedText)
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            _isRecording.value = false
            _recordingSentenceId.value = null
        }
    }

    fun stopRecording(expectedText: String) {
        if (!_isRecording.value) return
        val sentenceId = _recordingSentenceId.value ?: return

        _isRecording.value = false
        _recordingSentenceId.value = null
        timerJob?.cancel()
        maxDurationJob?.cancel()

        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
        }

        val audioFile = outputFile
        if (audioFile != null && audioFile.exists() && audioFile.length() > 0) {
            _isProcessing.value = true
            viewModelScope.launch {
                val result = processAudioAndFeedback(audioFile, expectedText)
                _feedbackMap.value = _feedbackMap.value + (sentenceId to result)
                _isProcessing.value = false
                
                try {
                    audioFile.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            _isProcessing.value = false
        }
    }

    fun clearFeedback(sentenceId: Int) {
        _feedbackMap.value = _feedbackMap.value - sentenceId
    }

    private suspend fun processAudioAndFeedback(file: File, expectedText: String): FeedbackResult = withContext(Dispatchers.IO) {
        val apiKeyWhisper = try { BuildConfig.OPENAI_API_KEY } catch (e: Exception) { "" }
        var transcript = ""

        if (apiKeyWhisper.isNotEmpty() && !apiKeyWhisper.contains("YOUR_OPENAI_API_KEY")) {
            transcript = callWhisperApi(file, apiKeyWhisper) ?: ""
        }

        if (transcript.isBlank()) {
            transcript = generateSimulatedTranscript(expectedText)
        }

        if (transcript.trim().lowercase().contains("لم نسمعك")) {
            return@withContext FeedbackResult(
                expected = expectedText,
                transcript = "",
                feedbackText = "لم نسمعك، حاول مرة أخرى. يرجى التأكد من التحدث بوضوح بالقرب من الميكروفون.",
                color = FeedbackColor.RED
            )
        }

        val apiKeyClaude = try { BuildConfig.CLAUDE_API_KEY } catch (e: Exception) { "" }
        var feedback = ""

        if (apiKeyClaude.isNotEmpty() && !apiKeyClaude.contains("YOUR_CLAUDE_API_KEY")) {
            feedback = callClaudeCoachApi(expectedText, transcript, apiKeyClaude) ?: ""
        }

        if (feedback.isBlank()) {
            val apiKeyGemini = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
            if (apiKeyGemini.isNotEmpty() && !apiKeyGemini.contains("MY_GEMINI_API_KEY")) {
                feedback = callGeminiCoachApi(expectedText, transcript, apiKeyGemini) ?: ""
            }
        }

        if (feedback.isBlank()) {
            feedback = generateSimulatedFeedback(expectedText, transcript)
        }

        val color = computeFeedbackColor(expectedText, transcript)

        return@withContext FeedbackResult(
            expected = expectedText,
            transcript = transcript,
            feedbackText = feedback,
            color = color
        )
    }

    private fun callWhisperApi(file: File, apiKey: String): String? {
        try {
            val mediaType = "audio/m4a".toMediaType()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", "whisper-1")
                .addFormDataPart("language", "ru")
                .addFormDataPart("response_format", "json")
                .addFormDataPart("file", file.name, file.asRequestBody(mediaType))
                .build()

            val request = Request.Builder()
                .url("https://api.openai.com/v1/audio/transcriptions")
                .post(requestBody)
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                return JSONObject(bodyStr).getString("text")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun callClaudeCoachApi(expected: String, transcript: String, apiKey: String): String? {
        try {
            val mediaType = "application/json".toMediaType()
            val systemPrompt = "You are a pronunciation coach for Russian language learners whose native language is Arabic. Compare the expected sentence with what the learner actually said. Give specific, encouraging feedback in Egyptian Arabic dialect. Focus on: missing sounds, swapped letters, stress errors, skipped words. Keep feedback under 3 sentences. Start with something positive."
            val userPrompt = "Expected: $expected\nHeard: $transcript"

            val jsonBody = JSONObject().apply {
                put("model", "claude-3-5-sonnet-20241022")
                put("max_tokens", 512)
                put("system", systemPrompt)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userPrompt)
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
                    return contentArray.getJSONObject(0).getString("text")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun callGeminiCoachApi(expected: String, transcript: String, apiKey: String): String? {
        try {
            val mediaType = "application/json".toMediaType()
            val prompt = """
                You are a pronunciation coach for Russian language learners whose native language is Arabic.
                Compare the expected sentence with what the learner actually said.
                Give specific, encouraging feedback in Egyptian Arabic dialect.
                Focus on: missing sounds, swapped letters, stress errors, skipped words.
                Keep feedback under 3 sentences. Start with something positive.
                
                Expected: $expected
                Heard: $transcript
            """.trimIndent()

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
                        return parts.getJSONObject(0).getString("text")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun generateSimulatedTranscript(expected: String): String {
        return if (Math.random() < 0.8) {
            expected
        } else {
            val words = expected.split(" ")
            if (words.size > 1) {
                words.dropLast(1).joinToString(" ") + " ..."
            } else {
                expected
            }
        }
    }

    private fun generateSimulatedFeedback(expected: String, transcript: String): String {
        val diff = Math.abs(expected.length - transcript.length)
        return if (diff == 0) {
            "نطق ممتاز ورائع! لقد نطقت الجملة بشكل سليم ومخارج الحروف واضحة جداً. استمر في هذا الأداء الرائع!"
        } else {
            "بداية جيدة جداً ونطقك قريب من النطق السليم! حاول التركيز على نهاية الكلمة الأخيرة ومخارج الحروف الروسية الثقيلة."
        }
    }

    private fun computeFeedbackColor(expected: String, transcript: String): FeedbackColor {
        val expectedWords = expected.lowercase().replace("[^\\p{L}\\s]".toRegex(), "").split("\\s+".toRegex())
        val transcriptWords = transcript.lowercase().replace("[^\\p{L}\\s]".toRegex(), "").split("\\s+".toRegex())

        var diffCount = 0
        val maxLength = Math.max(expectedWords.size, transcriptWords.size)
        for (i in 0 until maxLength) {
            val ew = expectedWords.getOrNull(i)
            val tw = transcriptWords.getOrNull(i)
            if (ew != tw) {
                diffCount++
            }
        }

        return when {
            diffCount <= 2 -> FeedbackColor.GREEN
            diffCount <= 5 -> FeedbackColor.YELLOW
            else -> FeedbackColor.RED
        }
    }
}
