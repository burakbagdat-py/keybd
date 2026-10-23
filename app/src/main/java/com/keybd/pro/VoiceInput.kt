package com.keybd.pro

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Klavyenin içinden sesle yazma (cihazın konuşma tanıma servisiyle). */
class VoiceInput(private val svc: KeybdService) : RecognitionListener {
    private var sr: SpeechRecognizer? = null
    var listening = false
        private set

    fun start(languageTag: String): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(svc)) return false
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(svc)
        r.setRecognitionListener(this)
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        sr = r
        listening = true
        r.startListening(i)
        return true
    }

    fun stop() {
        listening = false
        try {
            sr?.cancel()
            sr?.destroy()
        } catch (_: Exception) {
        }
        sr = null
    }

    override fun onReadyForSpeech(params: Bundle?) = svc.onVoiceStatus("🎤 Dinleniyor… konuşun")
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() = svc.onVoiceStatus("⏳ İşleniyor…")
    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onError(error: Int) {
        val msg = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Ses anlaşılamadı"
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Ağ hatası"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon izni gerekli"
            else -> "Sesli yazma hatası ($error)"
        }
        stop()
        svc.onVoiceDone(null, msg)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        if (!t.isNullOrBlank()) svc.onVoiceStatus("🎤 $t")
    }

    override fun onResults(results: Bundle?) {
        val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        stop()
        svc.onVoiceDone(t, null)
    }
}
