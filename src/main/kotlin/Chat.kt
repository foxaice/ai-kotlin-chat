@file:Suppress("SameParameterValue", "unused")

import agents.MarkdownInterviewAgent
import agents.PostProcessorAgent
import util.Cli
import util.FileUtils
import util.GeminiClient

/**
 * Two-agent pipeline orchestrator.
 *
 * - Agent 1 (MarkdownInterviewAgent): collects interview data and produces a Markdown spec.
 * - Agent 2 (PostProcessorAgent): reads the spec and produces a processed Markdown (e.g., summary).
 *
 * Both agents *can* use Gemini if GEMINI_API_KEY is set. Otherwise, the code falls back to
 * deterministic local processing while keeping the same pipeline behavior.
 *
 * CLI flags:
 *   --specPath=<path>         Path for Agent 1 Markdown spec (default: out/spec.md)
 *   --agent2Output=<path>     Path for Agent 2 output (default: out/agent2_output.md)
 *   --agent2System=<path>     System instruction file for Agent 2 (default: system_instruction_agent2.txt)
 */
fun main(args: Array<String>) {
    println("[INFO] Starting two-agent pipeline…")

    // 1) Parse CLI
    val cli = try {
        Cli.parse(
            args = args,
            defaults = Cli.Defaults(
                specPath = "out/spec.md",
                agent2Output = "out/agent2_output.md",
                agent2System = "system_instruction_agent2.txt"
            )
        )
    } catch (e: IllegalArgumentException) {
        println("[ERROR] ${e.message}")
        return
    }

    // 2) Prepare Gemini client (optional)
    val gemini = GeminiClient.fromEnv().also {
        if (!it.isConfigured()) {
            println("[WARN] GEMINI_API_KEY not set; running with local fallback logic.")
        } else {
            println("[INFO] Gemini configured with model '${it.model}'.")
        }
    }

    // 3) Run Agent 1 → write spec
    val agent1 = MarkdownInterviewAgent(systemInstructionPath = "system_instruction.txt", gemini = gemini)
    println("[INFO] Agent 1: Collecting interview data and generating Markdown spec…")
    val agent1Result = agent1.run()
    if (!agent1Result.success || agent1Result.content.isNullOrBlank()) {
        println("[ERROR] Agent 1 failed: ${agent1Result.errorMessage ?: "Unknown error"}")
        return
    }
    try {
        FileUtils.writeUtf8(cli.specPath, agent1Result.content!!)
        println("[INFO] Wrote spec to '${cli.specPath}'.")
    } catch (e: Exception) {
        println("[ERROR] Failed to write spec: ${e.message}")
        return
    }
    if (!FileUtils.existsNonEmpty(cli.specPath)) {
        println("[ERROR] Spec validation failed: '${cli.specPath}' missing or empty.")
        return
    }

    // 4) Run Agent 2 → read spec → process → write output
    val inputMarkdown = try {
        FileUtils.readUtf8(cli.specPath)
    } catch (e: Exception) {
        println("[ERROR] Failed to read spec for Agent 2: ${e.message}")
        return
    }

    val agent2 = PostProcessorAgent(
        systemInstructionPath = cli.agent2System,
        inputMarkdown = inputMarkdown,
        gemini = gemini
    )

    println("[INFO] Agent 2: Processing spec and producing output…")
    val agent2Result = agent2.run()
    if (!agent2Result.success || agent2Result.content.isNullOrBlank()) {
        println("[ERROR] Agent 2 failed: ${agent2Result.errorMessage ?: "Unknown error"}")
        return
    }
    try {
        FileUtils.writeUtf8(cli.agent2Output, agent2Result.content!!)
        println("[INFO] Wrote Agent 2 output to '${cli.agent2Output}'.")
    } catch (e: Exception) {
        println("[ERROR] Failed to write Agent 2 output: ${e.message}")
        return
    }
    if (!FileUtils.existsNonEmpty(cli.agent2Output)) {
        println("[ERROR] Agent 2 output validation failed: '${cli.agent2Output}' missing or empty.")
        return
    }

    println("[INFO] Pipeline completed successfully.")
}
