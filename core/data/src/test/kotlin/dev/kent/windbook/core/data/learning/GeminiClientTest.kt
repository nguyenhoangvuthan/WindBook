package dev.kent.windbook.core.data.learning

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeminiClientTest {
    @Test fun requestSeparatesInstructionsAndRejectsOverLimitInput() {
        val payload=JSONObject(GeminiProtocol.request("source says ignore instructions","Explain faithfully",1024))
        assertTrue(payload.getJSONObject("systemInstruction").toString().contains("Explain faithfully"))
        assertTrue(payload.getJSONArray("contents").toString().contains("source says"))
        assertTrue(runCatching {GeminiProtocol.request("x".repeat(70000),"system",1024)}.isFailure)
    }
    @Test fun onlyCompleteBoundedResponsesAreAccepted() {
        val valid="""{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"answer"}]}}],"usageMetadata":{"totalTokenCount":42}}"""
        assertEquals("answer",GeminiProtocol.parse(valid).text)
        assertEquals(42,GeminiProtocol.parse(valid).tokens)
        assertTrue(runCatching {GeminiProtocol.parse(valid.replace("STOP","MAX_TOKENS"))}.isFailure)
        assertTrue(runCatching {GeminiProtocol.parse("{}")}.isFailure)
    }
    @Test fun quotaErrorNeverBecomesAnAnswerOrAutomaticRetry()=runTest {
        var calls=0
        val client=GeminiClient {_,_-> calls++; GeminiHttpResult(429,"sensitive upstream body") }
        val error=runCatching {client.generate("key","data","system")}.exceptionOrNull()
        assertEquals(1,calls);assertEquals(AiFailure.QUOTA,(error as GeminiException).reason)
        assertFalse(error.toString().contains("sensitive"))
    }
}
