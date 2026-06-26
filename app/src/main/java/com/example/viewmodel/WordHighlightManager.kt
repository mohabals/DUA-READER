package com.example.viewmodel

import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.regex.Pattern

class WordHighlightManager {
    private val _highlightRange = MutableStateFlow<IntRange?>(null)
    val highlightRange = _highlightRange.asStateFlow()

    private var simulationJob: Job? = null
    private var onRangeStartCalled = false
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun reset() {
        cancelSimulation()
        _highlightRange.value = null
    }

    private fun cancelSimulation() {
        simulationJob?.cancel()
        simulationJob = null
    }

    /**
     * Set up the progress listener on the given TextToSpeech instance
     */
    fun setupListener(tts: TextToSpeech, text: String, speechRate: Float) {
        reset()
        onRangeStartCalled = false

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // If onRangeStart isn't called within 300ms, start manual simulation
                cancelSimulation()
                simulationJob = mainScope.launch {
                    delay(300)
                    if (!onRangeStartCalled) {
                        startManualSimulation(text, speechRate)
                    }
                }
            }

            override fun onDone(utteranceId: String?) {
                mainScope.launch {
                    _highlightRange.value = null
                }
                cancelSimulation()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainScope.launch {
                    _highlightRange.value = null
                }
                cancelSimulation()
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                onRangeStartCalled = true
                cancelSimulation()
                mainScope.launch {
                    _highlightRange.value = start..end
                }
            }
        })
    }

    private fun startManualSimulation(text: String, speechRate: Float) {
        cancelSimulation()
        simulationJob = mainScope.launch {
            // Find all word bounds
            val words = mutableListOf<IntRange>()
            val pattern = Pattern.compile("[\\p{L}\\p{N}]+", Pattern.UNICODE_CHARACTER_CLASS)
            val matcher = pattern.matcher(text)
            while (matcher.find()) {
                words.add(matcher.start() until matcher.end())
            }

            if (words.isEmpty()) return@launch

            // Loop through each word and highlight it
            val rate = if (speechRate <= 0f) 1f else speechRate
            val averageWordDurationMs = (350 / rate).toLong().coerceAtLeast(100L)

            for (range in words) {
                _highlightRange.value = range
                delay(averageWordDurationMs)
            }
            _highlightRange.value = null
        }
    }
}
