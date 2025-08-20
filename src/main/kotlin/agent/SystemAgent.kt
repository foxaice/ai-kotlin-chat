package agent

import job.main as runJobMain
import mcp.TodoistManager
import mcp.McpManager
import kotlin.system.exitProcess
import java.io.File

class SystemAgent {
    private val todoistManager = TodoistManager()
    
    fun initialize() {
        val todoistApiKey = System.getenv("TODOIST_API_KEY")
        val todoistServerPath = System.getenv("TODOIST_MCP_SERVER_PATH")
        
        if (todoistApiKey != null) {
            todoistManager.initialize(todoistApiKey, todoistServerPath)
            println("🤖 Агент: Todoist менеджер инициализирован")
        }
    }
    
    fun executeCommand(command: String): String {
        return when {
            command.contains("запусти планировщик тудуиста") || 
            command.contains("планировщик") ||
            command.contains("запусти задачи") -> {
                executeScheduler()
            }
            command.contains("покажи статус системы") ||
            command.contains("статус агента") -> {
                getSystemStatus()
            }
            command.contains("настрой систему") ||
            command.contains("настрой разрешения") -> {
                configureSystem()
            }
            command.startsWith("выполни команду:") -> {
                val cmd = command.removePrefix("выполни команду:").trim()
                executeSystemCommand(cmd)
            }
            command.contains("запусти демо gradle") -> {
                executeDemoGradle()
            }
            command.contains("запусти mcp цепочку") -> {
                executeMcpChain()
            }
            else -> "❌ Команда не распознана агентом: $command"
        }
    }
    
    private fun executeScheduler(): String {
        return try {
            println("🤖 Агент: Запускаю планировщик Todoist...")
            
            // Запуск планировщика через прямой вызов
            val tasks = todoistManager.getTasks(filter = "today")
            if (tasks.isNotEmpty()) {
                val tasksText = formatTasksForDisplay(tasks)
                "✅ Планировщик Todoist запущен. Задачи на сегодня:\n$tasksText"
            } else {
                "✅ Планировщик Todoist запущен. Задач на сегодня не найдено."
            }
        } catch (e: Exception) {
            "❌ Ошибка выполнения планировщика: ${e.message}"
        }
    }
    
    private fun formatTasksForDisplay(tasks: List<Map<String, Any>>): String {
        return buildString {
            for (task in tasks) {
                val result = (task["result"] as? Map<*, *>)?.get("content") as? List<*>
                result?.forEach { taskData ->
                    if (taskData is Map<*, *>) {
                        val content = taskData["content"] as? String ?: "Без названия"
                        val due = taskData["due"] as? String ?: ""
                        appendLine("- $content${if (due.isNotEmpty()) " ($due)" else ""}")
                    }
                }
            }
        }
    }
    
    private fun getSystemStatus(): String {
        return buildString {
            appendLine("📊 Статус системы:")
            appendLine("• Todoist: ${todoistManager.getStatus()}")
            appendLine("• MCP подключение: ${if (todoistManager.isMcpConnected()) "✅" else "❌"}")
            appendLine("• Доступные инструменты: ${todoistManager.getAvailableTools().joinToString(", ")}")
            
            // Проверяем переменные окружения
            val geminiKey = System.getenv("GEMINI_API_KEY")?.let { "✅ установлен" } ?: "❌ не установлен"
            val todoistKey = System.getenv("TODOIST_API_KEY")?.let { "✅ установлен" } ?: "❌ не установлен"
            val tgToken = System.getenv("TELEGRAM_BOT_TOKEN")?.let { "✅ установлен" } ?: "❌ не установлен"
            val tgChatId = System.getenv("TELEGRAM_CHAT_ID")?.let { "✅ установлен" } ?: "❌ не установлен"
            
            appendLine("• GEMINI_API_KEY: $geminiKey")
            appendLine("• TODOIST_API_KEY: $todoistKey")
            appendLine("• TELEGRAM_BOT_TOKEN: $tgToken")
            appendLine("• TELEGRAM_CHAT_ID: $tgChatId")
        }
    }
    
    private fun configureSystem(): String {
        return "⚙️ Конфигурация системы:\n" +
               "Для настройки системы установите переменные окружения:\n" +
               "- GEMINI_API_KEY\n" +
               "- TODOIST_API_KEY\n" +
               "- TELEGRAM_BOT_TOKEN\n" +
               "- TELEGRAM_CHAT_ID"
    }
    
    private fun executeSystemCommand(command: String): String {
        return try {
            println("🤖 Агент: Выполняю системную команду: $command")
            val process = ProcessBuilder(command.split(" "))
                .redirectErrorStream(true)
                .start()
            
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                "✅ Команда выполнена успешно:\n$output"
            } else {
                "❌ Команда завершилась с ошибкой (код $exitCode):\n$output"
            }
        } catch (e: Exception) {
            "❌ Ошибка выполнения команды: ${e.message}"
        }
    }
    
    private fun executeDemoGradle(): String {
        return executeShellScript("./demo_gradle.sh")
    }
    
    private fun executeMcpChain(): String {
        return executeShellScript("./demo_mcp_chain.sh")
    }
    
    private fun executeShellScript(scriptPath: String): String {
        return try {
            val file = File(scriptPath)
            if (!file.exists()) {
                return "❌ Скрипт $scriptPath не найден"
            }
            
            val process = ProcessBuilder("bash", scriptPath)
                .redirectErrorStream(true)
                .start()
            
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                "✅ Скрипт выполнен успешно:\n${output.take(500)}${if (output.length > 500) "..." else ""}"
            } else {
                "❌ Скрипт завершился с ошибкой (код $exitCode):\n${output.take(500)}"
            }
        } catch (e: Exception) {
            "❌ Ошибка выполнения скрипта $scriptPath: ${e.message}"
        }
    }
    
    fun disconnect() {
        todoistManager.disconnect()
    }
}
