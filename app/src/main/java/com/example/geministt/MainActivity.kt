package com.example.geministt

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var rootLayout: FrameLayout
    private lateinit var settingsLayout: LinearLayout
    private lateinit var mainLayout: LinearLayout

    private lateinit var statusTextView: TextView
    private lateinit var apiKeyInput: EditText
    private lateinit var modelSpinner: Spinner
    private lateinit var closeSettingsButton: Button

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val PREFS_NAME = "GeminiPrefs"
    private val KEY_API = "apiKey"
    private val KEY_MODEL = "selectedModel"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Root container
        rootLayout = FrameLayout(this)
        
        buildSettingsView()
        buildMainSTTView()
        
        rootLayout.addView(mainLayout)
        rootLayout.addView(settingsLayout)
        setContentView(rootLayout)

        val savedKey = getSavedApiKey()
        if (savedKey.isEmpty()) {
            showSettings()
        } else {
            showMain()
            fetchAvailableModels(savedKey) // Refresh models silently
        }

        if (intent?.action == Intent.ACTION_SEND) {
            if (savedKey.isEmpty()) {
                showSettings()
                Toast.makeText(this, "Please set up your API Key first", Toast.LENGTH_LONG).show()
            } else {
                showMain()
                handleIncomingAudio(intent)
            }
        }
    }

    private fun buildSettingsView() {
        settingsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 80, 64, 64)
            setBackgroundColor(Color.parseColor("#121212")) // Dark theme background
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val title = TextView(this).apply {
            text = "Welcome to Gemini STT"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 16)
        }

        val description = TextView(this).apply {
            text = "Transcribe WhatsApp voice notes in any language. To get started, you need a free Gemini API key."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, 48)
        }

        val getLinkButton = Button(this).apply {
            text = "Get Free API Key"
            setBackgroundColor(Color.parseColor("#1A73E8"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val url = "https://aistudio.google.com/app/apikey"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }

        apiKeyInput = EditText(this).apply {
            hint = "Paste API Key Here"
            setHintTextColor(Color.DKGRAY)
            setTextColor(Color.WHITE)
            setText(getSavedApiKey())
            setPadding(0, 48, 0, 48)
        }

        modelSpinner = Spinner(this).apply {
            setPadding(0, 24, 0, 48)
        }

        val fetchModelsButton = Button(this).apply {
            text = "Verify Key & Load Models"
            setOnClickListener {
                val key = apiKeyInput.text.toString().trim()
                if (key.isNotEmpty()) {
                    saveApiKey(key)
                    Toast.makeText(this@MainActivity, "Fetching models...", Toast.LENGTH_SHORT).show()
                    fetchAvailableModels(key)
                }
            }
        }

        closeSettingsButton = Button(this).apply {
            text = "Save & Continue"
            visibility = View.GONE
            setOnClickListener { showMain() }
        }

        settingsLayout.addView(title)
        settingsLayout.addView(description)
        settingsLayout.addView(getLinkButton)
        settingsLayout.addView(apiKeyInput)
        settingsLayout.addView(fetchModelsButton)
        
        val modelLabel = TextView(this).apply { 
            text = "Select Audio Model:"
            setTextColor(Color.LTGRAY)
            setPadding(0, 48, 0, 8)
        }
        settingsLayout.addView(modelLabel)
        settingsLayout.addView(modelSpinner)
        settingsLayout.addView(closeSettingsButton)
    }

    private fun buildMainSTTView() {
        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // Top Navigation Bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.parseColor("#1F1F1F"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val appTitle = TextView(this).apply {
            text = "Gemini STT"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val settingsIcon = Button(this).apply {
            text = "⚙️ Settings"
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.LTGRAY)
            setPadding(0,0,0,0)
            setOnClickListener { showSettings() }
        }

        topBar.addView(appTitle)
        topBar.addView(settingsIcon)

        // Transcription Area
        val scrollArea = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            setPadding(48, 48, 48, 48)
        }

        statusTextView = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#E0E0E0"))
            text = "Ready.\n\nShare an audio file from WhatsApp to transcribe it."
            setLineSpacing(0f, 1.3f)
        }

        scrollArea.addView(statusTextView)

        mainLayout.addView(topBar)
        mainLayout.addView(scrollArea)
    }

    private fun showSettings() {
        settingsLayout.visibility = View.VISIBLE
        mainLayout.visibility = View.GONE
        if (getSavedModel().isNotEmpty()) {
            closeSettingsButton.visibility = View.VISIBLE
        }
    }

    private fun showMain() {
        settingsLayout.visibility = View.GONE
        mainLayout.visibility = View.VISIBLE
    }

    private fun getSavedApiKey(): String = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_API, "") ?: ""
    private fun saveApiKey(key: String) = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_API, key).apply()
    
    private fun getSavedModel(): String = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_MODEL, "") ?: ""
    private fun saveSelectedModel(model: String) = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_MODEL, model).apply()

    private fun fetchAvailableModels(apiKey: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
            val request = Request.Builder().url(url).get().build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    
                    val resObj = JSONObject(response.body?.string() ?: "")
                    val modelsArray = resObj.optJSONArray("models") ?: return@use
                    val validModels = mutableListOf<String>()

                    for (i in 0 until modelsArray.length()) {
                        val model = modelsArray.getJSONObject(i)
                        val methods = model.optJSONArray("supportedGenerationMethods")
                        if (methods != null) {
                            for (j in 0 until methods.length()) {
                                if (methods.getString(j) == "generateContent") {
                                    validModels.add(model.getString("name"))
                                    break
                                }
                            }
                        }
                    }
                    withContext(Dispatchers.Main) { updateModelSpinner(validModels) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateModelSpinner(models: List<String>) {
        if (models.isEmpty()) return
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, models)
        modelSpinner.adapter = adapter
        
        // Keep text white in the spinner
        modelSpinner.post {
            (modelSpinner.selectedView as? TextView)?.setTextColor(Color.WHITE)
        }

        val savedModel = getSavedModel()
        val position = models.indexOf(savedModel)
        if (position >= 0) modelSpinner.setSelection(position)

        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                (view as? TextView)?.setTextColor(Color.WHITE)
                saveSelectedModel(models[pos])
                closeSettingsButton.visibility = View.VISIBLE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun handleIncomingAudio(intent: Intent) {
        val audioUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return
        val apiKey = getSavedApiKey()
        val selectedModel = getSavedModel()
        
        if (apiKey.isEmpty() || selectedModel.isEmpty()) {
            statusTextView.text = "Error: Settings incomplete."
            return
        }

        statusTextView.text = "Reading audio file..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream: InputStream? = contentResolver.openInputStream(audioUri)
                val bytes = inputStream?.readBytes() ?: throw Exception("Failed to read audio bytes.")
                val base64Audio = Base64.encodeToString(bytes, Base64.NO_WRAP)

                withContext(Dispatchers.Main) { statusTextView.text = "Transcribing with $selectedModel...\nThis may take a few seconds." }

                val transcript = requestGeminiTranscription(base64Audio, apiKey, selectedModel)

                withContext(Dispatchers.Main) { statusTextView.text = transcript }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { statusTextView.text = "Error: ${e.localizedMessage}" }
            }
        }
    }

    private fun requestGeminiTranscription(base64Audio: String, apiKey: String, modelName: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/$modelName:generateContent?key=$apiKey"

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "Detect the spoken language, provide a full verbatim transcript in the original language, and then provide an English translation.")
                        })
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", "audio/ogg")
                                put("data", base64Audio)
                            })
                        })
                    })
                })
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return "API Error (${response.code}): ${response.body?.string()}"
            val resObj = JSONObject(response.body?.string() ?: "")
            val candidates = resObj.optJSONArray("candidates") ?: return "No response generated. (Ensure the selected model supports audio input)."
            val content = candidates.getJSONObject(0).getJSONObject("content")
            val parts = content.getJSONArray("parts")
            return parts.getJSONObject(0).getString("text")
        }
    }
}
