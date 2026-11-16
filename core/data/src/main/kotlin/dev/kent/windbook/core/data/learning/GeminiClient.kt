package dev.kent.windbook.core.data.learning

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

enum class AiFailure { KEY, QUOTA, NETWORK, RESPONSE, LIMIT }
class GeminiException(val reason: AiFailure): Exception(reason.name)
data class GeminiReply(val text: String,val tokens: Int)
data class GeminiHttpResult(val status: Int,val body: String)

object GeminiProtocol {
    const val MODEL="gemini-3.5-flash-lite"
    const val PROMPT_VERSION=1
    fun request(prompt: String,system: String,maxTokens: Int): String {
        require(prompt.length<=65_536 && system.length<=8192 && maxTokens in 1..4096)
        return JSONObject().apply {
            put("systemInstruction",JSONObject().put("parts",JSONArray().put(JSONObject().put("text",system))))
            put("contents",JSONArray().put(JSONObject().put("role","user").put("parts",JSONArray().put(JSONObject().put("text",prompt)))))
            put("generationConfig",JSONObject().put("maxOutputTokens",maxTokens))
        }.toString().also {require(it.toByteArray().size<=262_144)}
    }
    fun parse(body: String): GeminiReply {
        if(body.length>262_144) throw GeminiException(AiFailure.RESPONSE)
        return try {
            val root=JSONObject(body); val candidate=root.getJSONArray("candidates").getJSONObject(0)
            if(candidate.optString("finishReason")!="STOP") throw GeminiException(AiFailure.RESPONSE)
            val parts=candidate.getJSONObject("content").getJSONArray("parts")
            val text=(0 until parts.length()).map {parts.getJSONObject(it)}.filterNot {it.optBoolean("thought")}.joinToString("\n") {it.optString("text")}
            if(text.isBlank() || text.length>32_768) throw GeminiException(AiFailure.RESPONSE)
            GeminiReply(text,root.optJSONObject("usageMetadata")?.optInt("totalTokenCount") ?: 0)
        } catch(_: Exception) {throw GeminiException(AiFailure.RESPONSE)}
    }
}

/** Only this transport may contact Google. Never logs credentials, book text or provider bodies. */
class GeminiClient(private val exchange: suspend (String,String)->GeminiHttpResult = ::geminiHttps) {
    suspend fun generate(key: String,prompt: String,system: String,maxTokens: Int=4096): GeminiReply {
        if(key.isBlank() || key.length>512 || key.any {it.isWhitespace() || it.code<32}) throw GeminiException(AiFailure.KEY)
        val request=try {GeminiProtocol.request(prompt,system,maxTokens)} catch(_: IllegalArgumentException){throw GeminiException(AiFailure.LIMIT)}
        currentCoroutineContext().ensureActive()
        val result=exchange(key,request)
        currentCoroutineContext().ensureActive()
        when(result.status) {
            200 -> return GeminiProtocol.parse(result.body)
            401,403 -> throw GeminiException(AiFailure.KEY)
            429 -> throw GeminiException(AiFailure.QUOTA)
            else -> throw GeminiException(AiFailure.NETWORK)
        }
    }
}
private suspend fun geminiHttps(key: String,body: String): GeminiHttpResult = withContext(Dispatchers.IO) {
    val connection=URL("https://generativelanguage.googleapis.com/v1beta/models/${GeminiProtocol.MODEL}:generateContent").openConnection() as HttpsURLConnection
    try {
        connection.instanceFollowRedirects=false
        connection.connectTimeout=15_000;connection.readTimeout=45_000
        connection.requestMethod="POST";connection.doOutput=true
        connection.setRequestProperty("x-goog-api-key",key)
        connection.setRequestProperty("Content-Type","application/json; charset=utf-8")
        val bytes=body.toByteArray();connection.setFixedLengthStreamingMode(bytes.size)
        connection.outputStream.use {it.write(bytes)}
        val status=connection.responseCode
        if(status!=200) return@withContext GeminiHttpResult(status,"")
        val data=connection.inputStream.use {input ->
            val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true) {
                currentCoroutineContext().ensureActive()
                val n=input.read(buffer);if(n<0) break
                if(output.size()+n>262_144) throw GeminiException(AiFailure.RESPONSE)
                output.write(buffer,0,n)
            };output.toByteArray()
        }
        GeminiHttpResult(status,data.toString(Charsets.UTF_8))
    } catch(e: kotlinx.coroutines.CancellationException){throw e}
    catch(e: GeminiException){throw e}
    catch(_: Exception){throw GeminiException(AiFailure.NETWORK)}
    finally {connection.disconnect()}
}
