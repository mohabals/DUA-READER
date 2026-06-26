package com.example.viewmodel

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class KaraokeTtsManager(private val context: Context) {
    private var ttsRu: TextToSpeech? = null
    private var ttsAr: TextToSpeech? = null

    private val _isRuReady = MutableStateFlow(false)
    val isRuReady = _isRuReady.asStateFlow()

    private val _isArReady = MutableStateFlow(false)
    val isArReady = _isArReady.asStateFlow()

    private val _currentSentenceIndex = MutableStateFlow<Int>(-1)
    val currentSentenceIndex = _currentSentenceIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _showVoiceInstallDialog = MutableStateFlow<String?>(null) // "ru" or "ar" or null
    val showVoiceInstallDialog = _showVoiceInstallDialog.asStateFlow()

    init {
        initTts()
    }

    fun dismissVoiceDialog() {
        _showVoiceInstallDialog.value = null
    }

    private fun initTts() {
        ttsRu = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = ttsRu?.setLanguage(Locale("ru"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    _showVoiceInstallDialog.value = "ru"
                } else {
                    _isRuReady.value = true
                }
            } else {
                _showVoiceInstallDialog.value = "ru"
            }
        }

        ttsAr = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = ttsAr?.setLanguage(Locale("ar"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    _showVoiceInstallDialog.value = "ar"
                } else {
                    _isArReady.value = true
                }
            } else {
                _isArReady.value = true
            }
        }
    }

    fun play(sentences: List<com.example.model.SentenceCard>, speechRate: Float, speechPitch: Float) {
        if (sentences.isEmpty()) return
        val ru = ttsRu ?: return
        val ar = ttsAr ?: return

        stop()
        _isPlaying.value = true

        ru.setSpeechRate(speechRate)
        ru.setPitch(speechPitch)
        ar.setSpeechRate(speechRate)
        ar.setPitch(speechPitch)

        ru.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId != null && utteranceId.startsWith("ru_")) {
                    val idx = utteranceId.substringAfter("ru_").toIntOrNull() ?: -1
                    if (idx != -1) {
                        _currentSentenceIndex.value = idx
                    }
                }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId != null && utteranceId.startsWith("ar_")) {
                    val idx = utteranceId.substringAfter("ar_").toIntOrNull() ?: -1
                    if (idx == sentences.size - 1) {
                        _isPlaying.value = false
                        _currentSentenceIndex.value = -1
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isPlaying.value = false
                _currentSentenceIndex.value = -1
            }
        })

        ar.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId != null && utteranceId.startsWith("ar_")) {
                    val idx = utteranceId.substringAfter("ar_").toIntOrNull() ?: -1
                    if (idx == sentences.size - 1) {
                        _isPlaying.value = false
                        _currentSentenceIndex.value = -1
                    }
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isPlaying.value = false
                _currentSentenceIndex.value = -1
            }
        })

        for (i in sentences.indices) {
            val sentence = sentences[i]
            ru.speak(sentence.originalText, TextToSpeech.QUEUE_ADD, null, "ru_$i")
            ru.playSilentUtterance(300, TextToSpeech.QUEUE_ADD, "gap_$i")
            ar.speak(sentence.translatedText, TextToSpeech.QUEUE_ADD, null, "ar_$i")
            ar.playSilentUtterance(300, TextToSpeech.QUEUE_ADD, "gap_ar_$i")
        }
    }

    fun stop() {
        ttsRu?.stop()
        ttsAr?.stop()
        _isPlaying.value = false
        _currentSentenceIndex.value = -1
    }

    fun shutdown() {
        stop()
        ttsRu?.shutdown()
        ttsAr?.shutdown()
    }
}
