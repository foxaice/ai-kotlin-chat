import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration

private const val INSTRUCTION_FILE = "system_instruction.txt"

data class Msg(val role: String, val content: String) // role: "user" | "model"

fun main() {
    val apiKey = System.getenv("GEMINI_API_KEY")
        ?: error("Set GEMINI_API_KEY environment variable")

    val model = "gemini-2.5-flash"
    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

    val systemInstruction = loadSystemInstruction()

    println("systemInstruction: $systemInstruction")
    val history = mutableListOf<Msg>()

    val httpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(20))
        .writeTimeout(Duration.ofSeconds(120))
        .readTimeout(Duration.ofSeconds(180))
        .callTimeout(Duration.ofSeconds(0))
        .retryOnConnectionFailure(true)
        .build()

    val json = jacksonObjectMapper()

    println("Gemini Kotlin Chat. Type 'exit' to quit.\n")

    while (true) {
        print("Вы: ")
        val userInput = readlnOrNull()?.trim().orEmpty()
        if (userInput.isEmpty() || userInput.equals("exit", true)) break

        history += Msg("user", userInput)

        // Gemini expects: contents[] with role + parts[text]
        val contents = history.map { m ->
            mapOf("role" to m.role, "parts" to listOf(mapOf("text" to m.content)))
        }
        val bodyMap = mapOf(
            "systemInstruction" to mapOf("parts" to listOf(mapOf("text" to systemInstruction))),
            "contents" to contents
        )
        val reqBody = json.writeValueAsString(bodyMap)
            .toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url(url)
            .post(reqBody)
            .header("Content-Type", "application/json")
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty()
                error("HTTP ${resp.code} - $err")
            }
            val txt = resp.body?.string().orEmpty()
            val tree = json.readTree(txt)

            // candidates[0].content.parts[0].text
            val reply = tree["candidates"]?.get(0)
                ?.get("content")?.get("parts")?.get(0)?.get("text")
                ?.asText()?.trim().orEmpty()

            println("Модель: $reply\n")
            history += Msg("model", reply)
        }
    }
}

private fun loadSystemInstruction(): String {
    return readTextFileOrNull(INSTRUCTION_FILE) ?: ""
}

private fun readTextFileOrNull(path: String): String? {
    return try {
        val p = Paths.get(path)
        if (Files.exists(p)) Files.readString(p, StandardCharsets.UTF_8) else null
    } catch (_: Exception) {
        null
    }
}
