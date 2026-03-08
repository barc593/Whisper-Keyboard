package com.whisperkey.keyboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val MIC_PERMISSION_REQUEST = 100
        private val LANGUAGES = linkedMapOf(
            "auto" to "Auto-detect",
            "es" to "Español",
            "en" to "English",
            "fr" to "Français",
            "de" to "Deutsch",
            "pt" to "Português",
            "it" to "Italiano",
            "ja" to "日本語",
            "ko" to "한국어",
            "zh" to "中文",
            "ar" to "العربية",
            "hi" to "हिन्दी",
            "ru" to "Русский",
            "nl" to "Nederlands",
            "pl" to "Polski",
            "tr" to "Türkçe",
            "uk" to "Українська",
            "ca" to "Català",
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        requestMicPermission()
        setupApiKeyInput()
        setupLanguageSpinner()
        setupOpenSettings()
        updateStatus()
    }

    private fun requestMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                MIC_PERMISSION_REQUEST
            )
        }
    }

    private fun setupApiKeyInput() {
        val editApiKey = findViewById<TextInputEditText>(R.id.edit_api_key)
        val btnSave = findViewById<MaterialButton>(R.id.btn_save_key)

        // Load existing key
        val prefs = getSharedPreferences("whisper_keyboard", Context.MODE_PRIVATE)
        val savedKey = prefs.getString("groq_api_key", "") ?: ""
        if (savedKey.isNotBlank()) {
            editApiKey.setText(savedKey)
        }

        btnSave.setOnClickListener {
            val key = editApiKey.text.toString().trim()
            prefs.edit().putString("groq_api_key", key).apply()
            Toast.makeText(this, "API key saved!", Toast.LENGTH_SHORT).show()
            updateStatus()
        }
    }

    private fun setupLanguageSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinner_language)
        val languageNames = LANGUAGES.values.toList()
        val languageCodes = LANGUAGES.keys.toList()

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languageNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        // Load saved language
        val prefs = getSharedPreferences("whisper_keyboard", Context.MODE_PRIVATE)
        val savedLang = prefs.getString("whisper_language", "auto") ?: "auto"
        val idx = languageCodes.indexOf(savedLang)
        if (idx >= 0) spinner.setSelection(idx)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                prefs.edit().putString("whisper_language", languageCodes[pos]).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupOpenSettings() {
        findViewById<MaterialButton>(R.id.btn_open_ime_settings)?.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
    }

    private fun updateStatus() {
        val txtStatus = findViewById<TextView>(R.id.txt_status)
        val prefs = getSharedPreferences("whisper_keyboard", Context.MODE_PRIVATE)
        val key = prefs.getString("groq_api_key", "") ?: ""

        txtStatus.text = if (key.isNotBlank()) {
            getString(R.string.status_ready)
        } else {
            getString(R.string.status_no_key)
        }
    }
}
