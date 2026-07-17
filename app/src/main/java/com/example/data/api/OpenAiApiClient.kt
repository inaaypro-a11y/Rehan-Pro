package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import com.example.data.database.ChatMessageEntity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object OpenAiApiClient {
    private const val TAG = "OpenAiApiClient"
    private const val BASE_URL = "https://api.openai.com/v1"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Calls OpenAI Chat Completions using gpt-4o-mini and returns the response.
     */
    suspend fun generateChatResponse(
        conversationHistory: List<ChatMessageEntity>,
        systemInstruction: String,
        customKey: String? = null
    ): String {
        val apiKey = getApiKey(customKey)
        if (apiKey.isEmpty() || apiKey == "MY_OPENAI_API_KEY") {
            Log.e(TAG, "OpenAI API Key is empty or placeholder!")
            return getFallbackResponse(conversationHistory.lastOrNull()?.text ?: "")
        }

        try {
            val url = "$BASE_URL/chat/completions"
            val requestBodyJson = buildChatRequestBody(conversationHistory, systemInstruction, false)

            val requestBody = requestBodyJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    Log.e(TAG, "Unsuccessful response from OpenAI API: ${response.code} $bodyStr")
                    try {
                        val errObj = JSONObject(bodyStr).optJSONObject("error")
                        val errMsg = errObj?.optString("message") ?: "Unknown OpenAI Error"
                        return "Error: ${response.code} - $errMsg"
                    } catch (e: Exception) {
                        return "Error: Received ${response.code} from OpenAI API."
                    }
                }

                val bodyStr = response.body?.string() ?: ""
                val responseJson = JSONObject(bodyStr)
                val choices = responseJson.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val choice = choices.getJSONObject(0)
                    val message = choice.optJSONObject("message")
                    if (message != null) {
                        return message.optString("content", "")
                    }
                }
                return "Error: Received empty or invalid response from OpenAI."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network call failed", e)
            return "Connection Error: ${e.localizedMessage ?: "Please verify your internet connection."}"
        }
    }

    /**
     * Calls OpenAI Chat Completions with streaming enabled (stream = true) and feeds chunks to onChunkReceived.
     */
    suspend fun generateChatResponseStream(
        conversationHistory: List<ChatMessageEntity>,
        systemInstruction: String,
        customKey: String? = null,
        onChunkReceived: suspend (String) -> Unit
    ) {
        val apiKey = getApiKey(customKey)
        if (apiKey.isEmpty() || apiKey == "MY_OPENAI_API_KEY") {
            Log.e(TAG, "OpenAI API Key is empty or placeholder!")
            val fallback = getFallbackResponse(conversationHistory.lastOrNull()?.text ?: "")
            val words = fallback.split(" ")
            for (word in words) {
                onChunkReceived("$word ")
                kotlinx.coroutines.delay(80) // Delay to simulate streaming
            }
            return
        }

        try {
            val url = "$BASE_URL/chat/completions"
            val requestBodyJson = buildChatRequestBody(conversationHistory, systemInstruction, true)

            val requestBody = requestBodyJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    Log.e(TAG, "Unsuccessful response from OpenAI API stream: ${response.code} $bodyStr")
                    try {
                        val errObj = JSONObject(bodyStr).optJSONObject("error")
                        val errMsg = errObj?.optString("message") ?: "Unknown OpenAI Error"
                        onChunkReceived("Error: ${response.code} - $errMsg")
                    } catch (e: Exception) {
                        onChunkReceived("Error: Received ${response.code} from OpenAI API. Please verify your API Key in Settings or AI Studio Secrets.")
                    }
                    return
                }

                response.body?.byteStream()?.bufferedReader()?.use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val trimmed = line!!.trim()
                        if (trimmed.startsWith("data: ")) {
                            val dataStr = trimmed.substring(6).trim()
                            if (dataStr == "[DONE]") break
                            try {
                                val json = JSONObject(dataStr)
                                val choices = json.optJSONArray("choices")
                                if (choices != null && choices.length() > 0) {
                                    val choice = choices.getJSONObject(0)
                                    val delta = choice.optJSONObject("delta")
                                    if (delta != null) {
                                        val content = delta.optString("content")
                                        if (content.isNotEmpty()) {
                                            onChunkReceived(content)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to parse OpenAI stream chunk: $dataStr", e)
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
     * Calls OpenAI Image Generation API (DALL-E) to generate an image from the prompt.
     * Returns the base64-encoded image string, or null on error.
     */
    suspend fun generateImage(
        prompt: String,
        customKey: String? = null
    ): String? {
        val apiKey = getApiKey(customKey)
        if (apiKey.isEmpty() || apiKey == "MY_OPENAI_API_KEY") {
            Log.e(TAG, "OpenAI API Key is empty or placeholder!")
            return null
        }

        try {
            val url = "$BASE_URL/images/generations"
            
            val requestBodyJson = JSONObject()
            requestBodyJson.put("model", "dall-e-3")
            requestBodyJson.put("prompt", prompt)
            requestBodyJson.put("n", 1)
            requestBodyJson.put("size", "1024x1024")
            requestBodyJson.put("response_format", "b64_json")

            val requestBody = requestBodyJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    Log.e(TAG, "Unsuccessful response from OpenAI Image Generation: ${response.code} $bodyStr")
                    return null
                }

                val bodyStr = response.body?.string() ?: ""
                val responseJson = JSONObject(bodyStr)
                val data = responseJson.optJSONArray("data")
                if (data != null && data.length() > 0) {
                    val item = data.getJSONObject(0)
                    return item.optString("b64_json")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenAI image generation failed", e)
        }
        return null
    }

    private fun getApiKey(customKey: String?): String {
        if (!customKey.isNullOrBlank()) {
            return customKey.trim()
        }
        return try {
            BuildConfig.OPENAI_API_KEY
        } catch (e: Exception) {
            ""
        }
    }

    private fun buildChatRequestBody(
        conversationHistory: List<ChatMessageEntity>,
        systemInstruction: String,
        stream: Boolean
    ): JSONObject {
        val requestBodyJson = JSONObject()
        requestBodyJson.put("model", "gpt-4o-mini")
        requestBodyJson.put("stream", stream)

        val messagesArray = JSONArray()

        // 1. Add system instruction if present
        if (systemInstruction.isNotEmpty()) {
            val sysMsg = JSONObject()
            sysMsg.put("role", "system")
            sysMsg.put("content", systemInstruction)
            messagesArray.put(sysMsg)
        }

        // 2. Add last 15 conversation messages
        val messagesToInclude = conversationHistory.takeLast(15)
        for (msg in messagesToInclude) {
            if (msg.role == "MODEL" && msg.text.isEmpty()) continue

            val msgObj = JSONObject()
            msgObj.put("role", if (msg.role == "USER") "user" else "assistant")

            if (msg.attachmentType != null && msg.attachmentType.startsWith("image/") && msg.attachmentPath != null) {
                // OpenAI supports multimodal input in content array format
                val contentArray = JSONArray()

                val textContentObj = JSONObject()
                textContentObj.put("type", "text")
                textContentObj.put("text", if (msg.text.isNotEmpty()) msg.text else "Describe this image")
                contentArray.put(textContentObj)

                val imageContentObj = JSONObject()
                imageContentObj.put("type", "image_url")
                
                val base64Data = if (msg.attachmentPath.contains(",")) {
                    msg.attachmentPath.substringAfter(",")
                } else {
                    msg.attachmentPath
                }
                
                val imageUrlObj = JSONObject()
                imageUrlObj.put("url", "data:${msg.attachmentType};base64,$base64Data")
                imageContentObj.put("image_url", imageUrlObj)
                contentArray.put(imageContentObj)

                msgObj.put("content", contentArray)
            } else {
                msgObj.put("content", msg.text)
            }

            messagesArray.put(msgObj)
        }

        requestBodyJson.put("messages", messagesArray)
        return requestBodyJson
    }

    private fun getFallbackResponse(prompt: String): String {
        val p = prompt.lowercase().trim()
        val isTelugu = p.contains(Regex("[\\u0C00-\\u0C7F]+")) || p.contains("ela") || p.contains("unnav") || p.contains("namaskaram") || p.contains("enti")

        if (isTelugu) {
            return when {
                p.contains("namaskaram") || p.contains("hello") || p.contains("hi") || p.contains("హలో") -> {
                    "నమస్కారం! 🙏 నేను **RehanProAI** సహాయకుడిని. మీకు సహాయం చేయడానికి నేను సిద్ధంగా ఉన్నాను.\n\n" +
                    "ఆన్‌లైన్ AI సమర్థతను యాక్టివేట్ చేయడానికి దయచేసి AI స్టూడియో సీక్రెట్స్ ప్యానెల్‌లో **OPENAI_API_KEY** ని కాన్ఫిగర్ చేయండి. ప్రస్తుతం నేను ఆఫ్-లైన్ డెమో మోడ్‌లో పని చేస్తున్నాను!"
                }
                p.contains("ela") || p.contains("unnav") || p.contains("ఎలా") -> {
                    "నేను చాలా బాగున్నాను! ధన్యవాదాలు. 😊 మీరెలా ఉన్నారు? ఆఫ్-లైన్ మోడ్‌లో ఉన్నప్పటికీ మీకు ఏదైనా సహాయం చేయగలను. పూర్తి స్థాయి తెలివి తేటలు కావాలంటే మీ OPENAI_API_KEY ని కనెక్ట్ చేయండి!"
                }
                p.contains("enti") || p.contains("సంగతి") || p.contains("ఏంటి") -> {
                    "ఏమీ లేదు, అంతా క్షేమమే! మీ కొత్త చాట్ ప్యాడ్‌ను సెటప్ చేస్తున్నాను. 🚀 మీకు సహాయపడటానికి సిద్ధంగా ఉన్నాను."
                }
                else -> {
                    "ధన్యవాదాలు! మీ సందేశం నాకు అందింది. 👍\n\n" +
                    "నేను ప్రస్తుతం **ఆఫ్-లైన్ డెమో మోడ్‌లో** ఉన్నాను, కాబట్టి పూర్తి ఆన్‌లైన్ చాట్ సమాధానాలను ఇవ్వలేకపోతున్నాను. " +
                    "నిజమైన స్మార్ట్ సమాధానాలను పొందడానికి, ఎడమ/క్రింద వైపు ఉన్న **Secrets ప్యానెల్‌లో** మీ `OPENAI_API_KEY`ని సెట్ చేయండి!"
                }
            }
        }

        return when {
            p.contains("hello") || p.contains("hi") || p.contains("hey") || p.contains("greetings") -> {
                "Hello! 👋 I'm **RehanProAI**, your advanced offline assistant. \n\n" +
                "To unlock my complete ChatGPT capabilities (online answers, image generation, etc.), " +
                "please enter your **OPENAI_API_KEY** into the **Secrets panel in AI Studio**! " +
                "\n\nHow can I assist you in offline mode today?"
            }
            p.contains("how are you") || p.contains("how're you") || p.contains("how do you do") -> {
                "I'm doing fantastic, thank you for asking! 😊 Ready to co-create with you in this modern space. " +
                "To let me fetch real-time data and solve complex reasoning, remember to configure your OPENAI_API_KEY in the Secrets panel!"
            }
            p.contains("image") || p.contains("draw") || p.contains("create") || p.contains("paint") -> {
                "🎨 **Image Generation Request Detected**\n\n" +
                "To generate real-time AI images, configure a valid `OPENAI_API_KEY` in the Secrets panel. " +
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
                "2. Add your custom `OPENAI_API_KEY`.\n" +
                "3. Start asking anything, and watch the streaming response in action!"
            }
        }
    }
}
