package com.whisperkey.keyboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.File

class WhisperKeyboardService : InputMethodService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val audioRecorder = AudioRecorder()
    private var isShifted = false
    private var showingSymbols = false
    private var currentView: View? = null

    // ─── Lifecycle ───────────────────────────────────────────────

    override fun onCreateInputView(): View {
        return createQwertyView()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    // ─── View Creation ───────────────────────────────────────────

    private fun createQwertyView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_view, null)
        currentView = view
        setupLetterKeys(view)
        setupSpecialKeys(view)
        setupMicButton(view.findViewById(R.id.key_mic))
        return view
    }

    private fun createSymbolsView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_symbols, null)
        currentView = view

        // Setup all symbol text keys
        setupSymbolClickListeners(view as LinearLayout)

        // Back to ABC
        view.findViewById<TextView>(R.id.key_abc)?.setOnClickListener {
            hapticFeedback()
            showingSymbols = false
            setInputView(createQwertyView())
        }

        // Special keys on symbols layout
        view.findViewById<ImageView>(R.id.sym_backspace)?.setOnClickListener {
            hapticFeedback()
            handleBackspace()
        }
        view.findViewById<TextView>(R.id.sym_space)?.setOnClickListener {
            hapticFeedback()
            commitChar(' ')
        }
        view.findViewById<ImageView>(R.id.sym_enter)?.setOnClickListener {
            hapticFeedback()
            handleEnter()
        }
        setupMicButton(view.findViewById(R.id.sym_mic))

        return view
    }

    // ─── Key Setup ───────────────────────────────────────────────

    private fun setupLetterKeys(view: View) {
        val letters = "qwertyuiopasdfghjklzxcvbnm"
        for (letter in letters) {
            val resId = resources.getIdentifier("key_$letter", "id", packageName)
            view.findViewById<TextView>(resId)?.setOnClickListener {
                hapticFeedback()
                val char = if (isShifted) letter.uppercaseChar() else letter
                commitChar(char)
                if (isShifted) {
                    isShifted = false
                    updateShiftState(view)
                }
            }
        }
    }

    private fun setupSpecialKeys(view: View) {
        // Shift
        view.findViewById<ImageView>(R.id.key_shift)?.setOnClickListener {
            hapticFeedback()
            isShifted = !isShifted
            updateShiftState(view)
        }

        // Backspace
        view.findViewById<ImageView>(R.id.key_backspace)?.setOnClickListener {
            hapticFeedback()
            handleBackspace()
        }

        // Space
        view.findViewById<TextView>(R.id.key_space)?.setOnClickListener {
            hapticFeedback()
            commitChar(' ')
        }

        // Comma & Period
        view.findViewById<TextView>(R.id.key_comma)?.setOnClickListener {
            hapticFeedback()
            commitChar(',')
        }
        view.findViewById<TextView>(R.id.key_period)?.setOnClickListener {
            hapticFeedback()
            commitChar('.')
        }

        // Enter
        view.findViewById<ImageView>(R.id.key_enter)?.setOnClickListener {
            hapticFeedback()
            handleEnter()
        }

        // Symbols toggle
        view.findViewById<TextView>(R.id.key_symbols)?.setOnClickListener {
            hapticFeedback()
            showingSymbols = true
            setInputView(createSymbolsView())
        }
    }

    private fun setupSymbolClickListeners(parent: LinearLayout) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is LinearLayout) {
                for (j in 0 until child.childCount) {
                    val key = child.getChildAt(j)
                    if (key is TextView && key.id != R.id.key_abc &&
                        key.id != R.id.sym_space && key.id != R.id.status_bar
                    ) {
                        key.setOnClickListener {
                            hapticFeedback()
                            val text = (it as TextView).text.toString()
                            currentInputConnection?.commitText(text, 1)
                        }
                    }
                }
            }
        }
    }

    // ─── Voice Input ─────────────────────────────────────────────

    private fun setupMicButton(micButton: ImageView?) {
        micButton ?: return

        micButton.setOnClickListener {
            hapticFeedback()
            if (audioRecorder.isCurrentlyRecording()) {
                stopRecordingAndTranscribe(micButton)
            } else {
                startRecording(micButton)
            }
        }
    }

    private fun startRecording(micButton: ImageView) {
        // Check permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Microphone permission needed — open app settings", Toast.LENGTH_LONG).show()
            return
        }

        // Check API key
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            Toast.makeText(this, "Set your Groq API key in the app settings", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val audioFile = File(cacheDir, "whisper_recording.wav")
            audioRecorder.startRecording(audioFile, serviceScope)
            micButton.setBackgroundResource(R.drawable.mic_button_recording)
            showStatus("Recording…")
        } catch (e: Exception) {
            Toast.makeText(this, "Mic error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecordingAndTranscribe(micButton: ImageView) {
        serviceScope.launch {
            audioRecorder.stopRecording()
            micButton.setBackgroundResource(R.drawable.mic_button_background)
            showStatus("Transcribing…")

            val audioFile = File(cacheDir, "whisper_recording.wav")
            if (!audioFile.exists() || audioFile.length() < 100) {
                showStatus("Recording too short")
                hideStatusDelayed()
                return@launch
            }

            val apiKey = getApiKey()
            val language = getLanguage()
            val groq = GroqApiService(apiKey)

            val result = groq.transcribe(audioFile, language)
            result.onSuccess { text ->
                currentInputConnection?.commitText(text, 1)
                hideStatus()
            }
            result.onFailure { error ->
                showStatus("Error: ${error.message}")
                hideStatusDelayed()
            }

            // Cleanup
            audioFile.delete()
        }
    }

    // ─── Input Helpers ───────────────────────────────────────────

    private fun commitChar(char: Char) {
        currentInputConnection?.commitText(char.toString(), 1)
    }

    private fun handleBackspace() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    private fun handleEnter() {
        val imeAction = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: 0
        if (imeAction != EditorInfo.IME_ACTION_UNSPECIFIED && imeAction != EditorInfo.IME_ACTION_NONE) {
            currentInputConnection?.performEditorAction(imeAction)
        } else {
            currentInputConnection?.commitText("\n", 1)
        }
    }

    private fun updateShiftState(view: View) {
        val letters = "qwertyuiopasdfghjklzxcvbnm"
        for (letter in letters) {
            val resId = resources.getIdentifier("key_$letter", "id", packageName)
            view.findViewById<TextView>(resId)?.text =
                if (isShifted) letter.uppercase() else letter.lowercase()
        }

        // Visual indicator on shift key
        view.findViewById<ImageView>(R.id.key_shift)?.alpha = if (isShifted) 1.0f else 0.6f
    }

    // ─── Status Bar ──────────────────────────────────────────────

    private fun showStatus(text: String) {
        currentView?.findViewById<TextView>(R.id.status_bar)?.apply {
            this.text = text
            visibility = View.VISIBLE
        }
    }

    private fun hideStatus() {
        currentView?.findViewById<TextView>(R.id.status_bar)?.visibility = View.GONE
    }

    private fun hideStatusDelayed(delayMs: Long = 3000) {
        serviceScope.launch {
            delay(delayMs)
            hideStatus()
        }
    }

    // ─── Preferences ─────────────────────────────────────────────

    private fun getApiKey(): String {
        val prefs = getSharedPreferences("whisper_keyboard", Context.MODE_PRIVATE)
        return prefs.getString("groq_api_key", "") ?: ""
    }

    private fun getLanguage(): String? {
        val prefs = getSharedPreferences("whisper_keyboard", Context.MODE_PRIVATE)
        val lang = prefs.getString("whisper_language", "auto") ?: "auto"
        return if (lang == "auto") null else lang
    }

    // ─── Haptic Feedback ─────────────────────────────────────────

    private fun hapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) { }
    }
}
