package agents

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import util.FileUtils
import util.GeminiClient

/**
 * Agent 2:
 * - Reads Agent 2 system instructions.
 * - Processes the input Markdown.
 * - Fallback: append a summary header with quick stats.
 */
class PostProcessorAgent(
    private val systemInstructionPath: String,
    private val inputMarkdown: String,
    private val gemini: GeminiClient
) : AgentRunner {
    override fun run(): AgentResult {
        return try {
            val system = FileUtils.readUtf8OrNull(systemInstructionPath)
            val prompt = """
                    --- BEGIN INPUT ---
                    $inputMarkdown
                    --- END INPUT ---
                """.trimIndent()

            val json = jacksonObjectMapper()

            val result = gemini.generate(systemInstruction = system, userText = prompt) ?: ""

            val tree = json.readTree(result)

            val reply = tree["candidates"]?.get(0)
                ?.get("content")?.get("parts")?.get(0)?.get("text")
                ?.asText()?.trim().orEmpty()

            println("Agent2 output:")
            println(reply)
            AgentResult(success = true, content = reply)
        } catch (e: Exception) {
            AgentResult(success = false, errorMessage = e.message)
        }
    }
}
