package com.example.data.api

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.database.ChatMessageEntity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object GeminiApiClient {
    private const val TAG = "GeminiApiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Calls Gemini Text Generation using the recommended model for basic/smart chat.
     */
    suspend fun generateChatResponse(
        conversationHistory: List<ChatMessageEntity>,
        systemInstruction: String
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "API Key is empty or placeholder!")
            return getFallbackResponse(conversationHistory.lastOrNull()?.text ?: "")
        }

        try {
            val url = "$BASE_URL/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
            
            // Build the contents array
            val contentsArray = JSONArray()
            
            // We pass the last 15 messages for context window management
            val messagesToInclude = conversationHistory.takeLast(15)
            for (msg in messagesToInclude) {
                val contentObj = JSONObject()
                contentObj.put("role", if (msg.role == "USER") "user" else "model")
                
                val partsArray = JSONArray()
                
                // If the message has an attached image, send it too
                if (msg.attachmentType != null && msg.attachmentType.startsWith("image/") && msg.attachmentPath != null) {
                    val imagePartObj = JSONObject()
                    val inlineDataObj = JSONObject()
                    inlineDataObj.put("mimeType", msg.attachmentType)
                    
                    // The attachmentPath has the base64 string (after prefix)
                    val base64Data = if (msg.attachmentPath.contains(",")) {
                        msg.attachmentPath.substringAfter(",")
                    } else {
                        msg.attachmentPath
                    }
                    inlineDataObj.put("data", base64Data)
                    imagePartObj.put("inlineData", inlineDataObj)
                    partsArray.put(imagePartObj)
                }
                
                if (msg.text.isNotEmpty()) {
                    val partObj = JSONObject()
                    partObj.put("text", msg.text)
                    partsArray.put(partObj)
                } else if (partsArray.length() == 0) {
                    val partObj = JSONObject()
                    partObj.put("text", "...")
                    partsArray.put(partObj)
                }
                
                contentObj.put("parts", partsArray)
                contentsArray.put(contentObj)
            }

            val requestBodyJson = JSONObject()
            requestBodyJson.put("contents", contentsArray)

            // Add system instructions
            if (systemInstruction.isNotEmpty()) {
                val sysInstrObj = JSONObject()
                val partsArray = JSONArray()
                val partObj = JSONObject()
                partObj.put("text", systemInstruction)
                partsArray.put(partObj)
                sysInstrObj.put("parts", partsArray)
                requestBodyJson.put("systemInstruction", sysInstrObj)
            }

            val requestBody = requestBodyJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Unsuccessful response from Gemini API: ${response.code} $bodyStr")
                    return "Error: Received ${response.code} from API. Please make sure your GEMINI_API_KEY in the AI Studio Secrets panel is valid."
                }

                val jsonResponse = JSONObject(bodyStr)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val contentObj = firstCandidate.optJSONObject("content")
                    if (contentObj != null) {
                        val parts = contentObj.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            return parts.getJSONObject(0).optString("text", "No response text")
                        }
                    }
                }
                return "Received empty or unexpected response structure from Gemini API."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network call failed", e)
            return "Connection Error: ${e.localizedMessage ?: "Please verify your internet connection."}"
        }
    }

    /**
     * Calls Gemini Text Generation using streamGenerateContent and returns a Flow of text chunks.
     */
    suspend fun generateChatResponseStream(
        conversationHistory: List<ChatMessageEntity>,
        systemInstruction: String,
        onChunkReceived: suspend (String) -> Unit
    ) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "API Key is empty or placeholder!")
            // Stream the fallback response word by word/chunk by chunk to make it feel like streaming!
            val fallback = getFallbackResponse(conversationHistory.lastOrNull()?.text ?: "")
            val words = fallback.split(" ")
            for (word in words) {
                onChunkReceived("$word ")
                kotlinx.coroutines.delay(80) // delay to simulate streaming
            }
            return
        }

        try {
            val url = "$BASE_URL/v1beta/models/gemini-1.5-flash:streamGenerateContent?key=$apiKey"
            
            val contentsArray = JSONArray()
            val messagesToInclude = conversationHistory.takeLast(15)
            for (msg in messagesToInclude) {
                // Skip placeholder model messages
                if (msg.role == "MODEL" && msg.text.isEmpty()) continue

                val contentObj = JSONObject()
                contentObj.put("role", if (msg.role == "USER") "user" else "model")
                
                val partsArray = JSONArray()
                
                if (msg.attachmentType != null && msg.attachmentType.startsWith("image/") && msg.attachmentPath != null) {
                    val imagePartObj = JSONObject()
                    val inlineDataObj = JSONObject()
                    inlineDataObj.put("mimeType", msg.attachmentType)
                    
                    val base64Data = if (msg.attachmentPath.contains(",")) {
                        msg.attachmentPath.substringAfter(",")
                    } else {
                        msg.attachmentPath
                    }
                    inlineDataObj.put("data", base64Data)
                    imagePartObj.put("inlineData", inlineDataObj)
                    partsArray.put(imagePartObj)
                }
                
                if (msg.text.isNotEmpty()) {
                    val partObj = JSONObject()
                    partObj.put("text", msg.text)
                    partsArray.put(partObj)
                } else if (partsArray.length() == 0) {
                    val partObj = JSONObject()
                    partObj.put("text", "...")
                    partsArray.put(partObj)
                }
                
                contentObj.put("parts", partsArray)
                contentsArray.put(contentObj)
            }

            val requestBodyJson = JSONObject()
            requestBodyJson.put("contents", contentsArray)

            if (systemInstruction.isNotEmpty()) {
                val sysInstrObj = JSONObject()
                val partsArray = JSONArray()
                val partObj = JSONObject()
                partObj.put("text", systemInstruction)
                partsArray.put(partObj)
                sysInstrObj.put("parts", partsArray)
                requestBodyJson.put("systemInstruction", sysInstrObj)
            }

            val requestBody = requestBodyJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    Log.e(TAG, "Unsuccessful response from Gemini API stream: ${response.code} $bodyStr")
                    onChunkReceived("Error: Received ${response.code} from API. Please verify your API Key in Google AI Studio Secrets.")
                    return
                }

                response.body?.byteStream()?.bufferedReader()?.use { reader ->
                    val buffer = java.lang.StringBuilder()
                    var braceCount = 0
                    var inString = false
                    
                    val charArray = CharArray(2048)
                    var numChars: Int
                    while (reader.read(charArray).also { numChars = it } != -1) {
                        for (i in 0 until numChars) {
                            val c = charArray[i]
                            
                            // Check backslash escaping to correctly handle double quotes inside text values
                            val isEscaped = if (buffer.isNotEmpty()) {
                                var backslashCount = 0
                                var idx = buffer.length - 1
                                while (idx >= 0 && buffer[idx] == '\\') {
                                    backslashCount++
                                    idx--
                                }
                                backslashCount % 2 != 0
                            } else {
                                false
                            }
                            
                            buffer.append(c)
                            
                            if (c == '"' && !isEscaped) {
                                inString = !inString
                            }
                            
                            if (!inString) {
                                if (c == '{') {
                                    braceCount++
                                } else if (c == '}' && braceCount > 0) {
                                    braceCount--
                                    if (braceCount == 0) {
                                        val jsonStr = buffer.toString().trim()
                                        val startIndex = jsonStr.indexOf('{')
                                        if (startIndex != -1) {
                                            val objStr = jsonStr.substring(startIndex)
                                            try {
                                                val chunk = JSONObject(objStr)
                                                val candidates = chunk.optJSONArray("candidates")
                                                if (candidates != null && candidates.length() > 0) {
                                                    val firstCandidate = candidates.getJSONObject(0)
                                                    val contentObj = firstCandidate.optJSONObject("content")
                                                    if (contentObj != null) {
                                                        val parts = contentObj.optJSONArray("parts")
                                                        if (parts != null && parts.length() > 0) {
                                                            val text = parts.getJSONObject(0).optString("text")
                                                            if (text.isNotEmpty()) {
                                                                onChunkReceived(text)
                                                            }
                                                        }
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Chunk parsing failed: $objStr", e)
                                            }
                                        }
                                        buffer.setLength(0) // Clear buffer for the next JSON object
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network call failed in stream", e)
            onChunkReceived("\nConnection Error: ${e.localizedMessage ?: "Please verify your internet connection."}")
        }
    }

    /**
     * Calls the Gemini Image Generation model to create an image based on the prompt.
     * Returns the base64 representation of the generated image.
     */
    suspend fun generateImage(prompt: String): String? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "API Key is empty/placeholder. Image generation will use fallback.")
            return null
        }

        try {
            // Using recommended modern preview model for high quality image generation
            val url = "$BASE_URL/v1beta/models/imagen-3.0-generate-002:generateContent?key=$apiKey"

            val requestBodyJson = JSONObject()
            
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            contentObj.put("role", "user")
            val partsArray = JSONArray()
            val partObj = JSONObject()
            partObj.put("text", prompt)
            partsArray.put(partObj)
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            
            requestBodyJson.put("contents", contentsArray)

            // Configure Generation Parameters for Image output
            val generationConfig = JSONObject()
            val imageConfig = JSONObject()
            imageConfig.put("aspectRatio", "1:1")
            imageConfig.put("imageSize", "1K")
            generationConfig.put("imageConfig", imageConfig)
            
            val responseModalities = JSONArray()
            responseModalities.put("TEXT")
            responseModalities.put("IMAGE")
            generationConfig.put("responseModalities", responseModalities)
            
            requestBodyJson.put("generationConfig", generationConfig)

            val requestBody = requestBodyJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Unsuccessful image generation response: ${response.code} $bodyStr")
                    return null
                }

                val jsonResponse = JSONObject(bodyStr)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    if (content != null) {
                        val parts = content.optJSONArray("parts")
                        if (parts != null) {
                            for (i in 0 until parts.length()) {
                                val part = parts.getJSONObject(i)
                                val inlineData = part.optJSONObject("inlineData")
                                if (inlineData != null) {
                                    return inlineData.optString("data")
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Image generation call failed", e)
        }
        return null
    }

    /**
     * Fallback responses for local development before the user sets their API key.
     */
    private fun getFallbackResponse(prompt: String): String {
        val p = prompt.lowercase().trim()
        val isTelugu = p.contains(Regex("[\\u0C00-\\u0C7F]+")) || p.contains("ela") || p.contains("unnav") || p.contains("namaskaram") || p.contains("enti")

        if (isTelugu) {
            return when {
                p.contains("namaskaram") || p.contains("hello") || p.contains("hi") || p.contains("హలో") -> {
                    "నమస్కారం! 🙏 నేను **RehanProAI** సహాయకుడిని. మీకు సహాయం చేయడానికి నేను సిద్ధంగా ఉన్నాను.\n\n" +
                    "ఆన్‌లైన్ AI సమర్థతను యాక్టివేట్ చేయడానికి దయచేసి AI స్టూడియో సీక్రెట్స్ ప్యానెల్‌లో **GEMINI_API_KEY** ని కాన్ఫిగర్ చేయండి. ప్రస్తుతం నేను ఆఫ్-లైన్ డెమో మోడ్‌లో పని చేస్తున్నాను!"
                }
                p.contains("ela") || p.contains("unnav") || p.contains("ఎలా") -> {
                    "నేను చాలా బాగున్నాను! ధన్యవాదాలు. 😊 మీరెలా ఉన్నారు? ఆఫ్-లైన్ మోడ్‌లో ఉన్నప్పటికీ మీకు ఏదైనా సహాయం చేయగలను. పూర్తి స్థాయి తెలివి తేటలు కావాలంటే మీ GEMINI_API_KEY ని కనెక్ట్ చేయండి!"
                }
                p.contains("enti") || p.contains("సంగతి") || p.contains("ఏంటి") -> {
                    "ఏమీ లేదు, అంతా క్షేమమే! మీ కొత్త చాట్ ప్యాడ్‌ను సెటప్ చేస్తున్నాను. 🚀 మీకు సహాయపడటానికి సిద్ధంగా ఉన్నాను."
                }
                else -> {
                    "ధన్యవాదాలు! మీ సందేశం నాకు అందింది. 👍\n\n" +
                    "నేను ప్రస్తుతం **ఆఫ్-లైన్ డెమో మోడ్‌లో** ఉన్నాను, కాబట్టి పూర్తి ఆన్‌లైన్ చాట్ సమాధానాలను ఇవ్వలేకపోతున్నాను. " +
                    "నిజమైన స్మార్ట్ సమాధానాలను పొందడానికి, ఎడమ/క్రింద వైపు ఉన్న **Secrets ప్యానెల్‌లో** మీ `GEMINI_API_KEY`ని సెట్ చేయండి!"
                }
            }
        }

        return when {
            p.contains("hello") || p.contains("hi") || p.contains("hey") || p.contains("greetings") -> {
                "Hello! 👋 I'm **RehanProAI**, your advanced offline assistant. \n\n" +
                "To unlock my complete ChatGPT capabilities (online answers, image generation, etc.), " +
                "please enter your **GEMINI_API_KEY** into the **Secrets panel in AI Studio**! " +
                "\n\nHow can I assist you in offline mode today?"
            }
            p.contains("how are you") || p.contains("how're you") || p.contains("how do you do") -> {
                "I'm doing fantastic, thank you for asking! 😊 Ready to co-create with you in this modern space. " +
                "To let me fetch real-time data and solve complex reasoning, remember to configure your GEMINI_API_KEY in the Secrets panel!"
            }
            p.contains("image") || p.contains("draw") || p.contains("create") || p.contains("paint") -> {
                "🎨 **Image Generation Request Detected**\n\n" +
                "To generate real-time AI images, configure a valid `GEMINI_API_KEY` in the Secrets panel. " +
                "I will render a premium high-quality visual card in your chat thread to showcase our dynamic UI design!"
            }
            p.contains("code") || p.contains("program") || p.contains("kotlin") || p.contains("java") -> {
                "💻 Here is a simple Kotlin greeting function:\n\n" +
                "```kotlin\n" +
                "fun greetUser(name: String) {\n" +
                "    println(\"Welcome back to RehanProAI, \$name! 🚀\")\n" +
                "}\n" +
                "```\n" +
                "Enter your API key to let me write complex production code for you!"
            }
            p.contains("telugu") -> {
                "నమస్కారం! 🙏 నేను **RehanProAI** సహాయకుడిని. " +
                "మీకు సహాయం చేయడానికి నేను సిద్ధంగా ఉన్నాను. దయచేసి AI స్టూడియో సీక్రెట్స్ ప్యానెల్‌లో మీ API కీని నమోదు చేయండి."
            }
            else -> {
                "I am **RehanProAI**, standing by in offline simulation mode. 🤖✨\n\n" +
                "Because I don't have an active connection, I am ready to guide you on how to start:\n" +
                "1. Open the **Secrets panel** on the side of Google AI Studio.\n" +
                "2. Add your custom `GEMINI_API_KEY`.\n" +
                "3. Start asking anything, and watch the streaming response in action!"
            }
        }
    }
}
