package com.example.geministt

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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

    private lateinit var statusTextView: TextView
    private lateinit var apiKeyInput: EditText
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val PREFS_NAME = "GeminiPrefs"
    private val KEY_API = "apiKey"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        apiKeyInput = EditText(this).apply {
            hint = "Paste Gemini API Key here"
            setText(getSavedApiKey())
        }

        val saveButton = Button(this).apply {
            text = "Save Key"
            setOnClickListener {
                saveApiKey(apiKeyInput.text.toString().trim())
                Toast.makeText(this@MainActivity, "API Key Saved", Toast.LENGTH_SHORT).show()
            }
        }

        statusTextView = TextView(this).apply {
            textSize = 16f
            setPadding(0, 48, 0, 0)
            text = "Ready. Set your key above, then share an audio file from WhatsApp."
        }

        layout.addView(apiKeyInput)
        layout.addView(saveButton)
        layout.addView(statusTextView)

        val scrollView = ScrollView(this).apply { addView(layout) }
        setContentView(scrollView)

        if (intent?.action == Intent.ACTION_SEND) {
            handleIncomingAudio(intent)
        }
    }

    private fun getSavedApiKey(): String {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_API, "") ?: ""
    }

    private fun saveApiKey(key: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API, key).apply()
    }

    private fun handleIncomingAudio(intent: Intent) {
        val audioUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return
        val apiKey = getSavedApiKey()
        
        if (apiKey.isEmpty()) {
            statusTextView.text = "Error: Please open the app directly and save your API key first."
            return
        }

        statusTextView.text = "Reading audio file..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream: InputStream? = contentResolver.openInputStream(audioUri)
                val bytes = inputStream?.readBytes() ?: throw Exception("Failed to read audio bytes.")
                val base64Audio = Base64.encodeToString(bytes, Base64.NO_WRAP)

                withContext(Dispatchers.Main) {
                    statusTextView.text = "Transcribing with Gemini..."
                }

                val transcript = requestGeminiTranscription(base64Audio, apiKey)

                withContext(Dispatchers.Main) {
                    statusTextView.text = transcript
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusTextView.text = "Error: ${e.localizedMessage}"
                }
            }
        }
    }

    private fun requestGeminiTranscription(base64Audio: String, apiKey: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"

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
            val candidates = resObj.optJSONArray("candidates") ?: return "No response generated."
            val content = candidates.getJSONObject(0).getJSONObject("content")
            val parts = content.getJSONArray("parts")
            return parts.getJSONObject(0).getString("text")
        }
    }
}
