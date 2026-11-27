package com.example.core

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class TtsSpeaker(context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                    tts?.setSpeechRate(0.95f)
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                        }

                        @Deprecated("Deprecated in Android API")
                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            _isSpeaking.value = false
                        }
                    })
                    ready = true
                }
            }
        }
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        runCatching {
            val cleaned = text.replace("**", "").replace("#", "").take(3500)
            tts?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
            _isSpeaking.value = true
        }
    }

    fun stop() {
        runCatching {
            tts?.stop()
        }
        _isSpeaking.value = false
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        ready = false
        _isSpeaking.value = false
    }
}
