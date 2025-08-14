package util

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Minimal Google Generative Language API client for Gemini.
 * Uses HTTP directly (no external deps). If GEMINI_API_KEY isn't set, it becomes a no-op.
 *
 * Env:
 *  - GEMINI_API_KEY   : required to call the API
 *  - GEMINI_MODEL     : optional (default: gemini-1.5-flash)
 */
class GeminiClient private constructor(
    private val apiKey: String?,
    val model: String
) {
    companion object {
        fun fromEnv(): GeminiClient {
            val key: String = System.getenv("GEMINI_API_KEY") ?: error("GEMINI_API_KEY is not set")
            val model: String = System.getenv("GEMINI_MODEL") ?: "gemini-2.5-flash"
            return GeminiClient(apiKey = key, model = model)
        }
    }

    fun isConfigured(): Boolean = !apiKey.isNullOrBlank()

    /**
     * Sends a single-turn request. Returns model text or null on failure/misconfig.
     */
    fun generate(systemInstruction: String?, userText: String, prePayload: String? = null, temperature: Double = 0.7): String? {
        if (!isConfigured()) return null
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
        val payload = prePayload ?: buildPayload(systemInstruction, userText, temperature)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 300000
            readTimeout = 600000
        }
        try {
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(payload) }
            val code = conn.responseCode
            val body = if (code in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { it.readText() }
            } else {
                val err = conn.errorStream?.let {
                    BufferedReader(
                        InputStreamReader(
                            it,
                            StandardCharsets.UTF_8
                        )
                    ).use { r -> r.readText() }
                }
                throw IllegalStateException("Gemini API error $code: ${err ?: "no details"}")
            }
            return body
        } catch (e: Throwable) {
            e.printStackTrace()
            return null
        } finally {
            conn.disconnect()
        }
    }
}

private fun buildPayload(system: String?, user: String, temperature: Double): String {
    // Google v1beta supports "system_instruction".
    val sysBlock = if (!system.isNullOrBlank()) {
        """
            "system_instruction":{"role":"system","parts":[{"text":${jsonString(system)}}]},
            """.trimIndent()
    } else ""
    return """
            {
              $sysBlock
              "contents":[{"role":"user","parts":[{"text":${jsonString(user)}}]}],
              "generationConfig":{"temperature":$temperature}
            }
        """.trimIndent()
}

private fun jsonString(s: String): String {
    val escaped = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
    return "\"$escaped\""
}
