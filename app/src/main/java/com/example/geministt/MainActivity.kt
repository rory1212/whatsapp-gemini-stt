package com.example.geministt

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.ScrollView
import android.widget.TextView
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

    private lateinit var textView: TextView
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // Replace with your Gemini API Key
    private val geminiApiKey = "YOUR_GEMINI_API_KEY"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this)
        textView = TextView(this).apply {
            textSize = 16f
            setPadding(32, 32, 32, 32)
            text = "Ready. Share an audio file from WhatsApp to transcribe."
        }
        scrollView.addView(textView)
        setContentView(scrollView)

        if (intent?.action == Intent.ACTION_SEND) {
            handleIncomingAudio(intent)
        }
    }

    private fun handleIncomingAudio(intent: Intent) {
        val audioUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return
        textView.text = "Reading audio file..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream: InputStream? = contentResolver.openInputStream(audioUri)
                val bytes = inputStream?.readBytes() ?: throw Exception("Failed to read audio bytes.")
                val base64Audio = Base64.encodeToString(bytes, Base64.NO_WRAP)

                withContext(Dispatchers.Main) {
                    textView.text = "Transcribing with Gemini..."
                }

                val transcript = requestGeminiTranscription(base64Audio)

                withContext(Dispatchers.Main) {
                    textView.text = transcript
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    textView.text = "Error: ${e.localizedMessage}"
                }
            }
        }
    }

    private fun requestGeminiTranscription(base64Audio: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$geminiApiKey"

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
