package agents

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import util.GeminiClient
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Agent 1:
 * - Prompts the user for key inputs.
 * - Optionally asks Gemini to polish/expand the spec.
 * - Returns the final Markdown string.
 */
class MarkdownInterviewAgent(
    private val systemInstructionPath: String,
    private val gemini: GeminiClient
) : AgentRunner {

    override fun run(): AgentResult {
        try {
            val system = readTextFileOrNull(systemInstructionPath)
            println("[INFO] Agent 1 is using system instructions from '$systemInstructionPath'")

            val history = mutableListOf<Msg>()

            val startPrompt = """
                    помоги создать приложение
                """.trimIndent()

            history.putUser(startPrompt)

            val json = jacksonObjectMapper()

            while (true) {
                val payload = buildPayload(system, history.getContents(), 0.7)

                val result = gemini.generate(
                    systemInstruction = system,
                    userText = payload,
                    prePayload = payload
                ) ?: ""

                val tree = json.readTree(result)

                val reply = tree["candidates"]?.get(0)
                    ?.get("content")?.get("parts")?.get(0)?.get("text")
                    ?.asText()?.trim().orEmpty()

                history.putModel(reply)
                println("Модель: $reply")

                print("Вы: ")
                val userInput = readlnOrNull()?.trim().orEmpty()
                if (userInput.isEmpty() || userInput.equals("exit", true)) {
                    return AgentResult(success = true, content = reply)
                }
                history.putUser(userInput)
            }
        } catch (e: Exception) {
            return AgentResult(success = false, errorMessage = e.message)
        }
    }
}

private fun buildPayload(system: String?, contents: String, temperature: Double): String {
    // Google v1beta supports "system_instruction".
    val sysBlock = if (!system.isNullOrBlank()) {
        """
            "system_instruction":{"role":"system","parts":[{"text":${jsonString(system)}}]},
            """.trimIndent()
    } else ""
    return """
            {
              $sysBlock
              "contents": $contents,
              "generationConfig":{"temperature":$temperature}
            }
        """.trimIndent()
}

private fun jsonString(s: String): String {
    val json = jacksonObjectMapper()
    val reqBody = json.writeValueAsString(s)
    return reqBody
}

private fun MutableList<Msg>.putUser(input: String) {
    this += Msg("user", input)
}

private fun MutableList<Msg>.putModel(input: String) {
    this += Msg("model", input)
}

private fun MutableList<Msg>.getContents(): String {
    val json = jacksonObjectMapper()
    val map = this.map { m ->
        mapOf("role" to m.role, "parts" to listOf(mapOf("text" to m.content)))
    }
    val reqBody = json.writeValueAsString(map)
    return reqBody
}

private data class Msg(val role: String, val content: String) // role: "user" | "model"

private fun readTextFileOrNull(path: String): String? {
    return try {
        val p = Paths.get(path)
        if (Files.exists(p)) Files.readString(p, StandardCharsets.UTF_8) else null
    } catch (_: Exception) {
        null
    }
}
