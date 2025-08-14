package agents

data class AgentResult(
    val success: Boolean,
    val content: String? = null,
    val errorMessage: String? = null
)

interface AgentRunner {
    fun run(): AgentResult
}
