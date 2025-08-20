package agent

import mcp.TodoistManager
import mcp.McpManager
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class SystemAgent {
    private val todoistManager = TodoistManager()
    private var schedulerProcess: Process? = null
    private var schedulerThread: Thread? = null
    
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
            command.contains("остановить планировщик") ||
            command.contains("стоп планировщик") -> {
                stopScheduler()
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
            
            // Проверяем необходимые переменные окружения
            val tgToken = System.getenv("TELEGRAM_BOT_TOKEN")
            val tgChatId = System.getenv("TELEGRAM_CHAT_ID")
            val geminiKey = System.getenv("GEMINI_API_KEY")
            val todoistKey = System.getenv("TODOIST_API_KEY")
            
            if (tgToken.isNullOrBlank() || tgChatId.isNullOrBlank()) {
                return "❌ Не установлены TELEGRAM_BOT_TOKEN или TELEGRAM_CHAT_ID"
            }
            
            if (geminiKey.isNullOrBlank()) {
                return "❌ Не установлен GEMINI_API_KEY"
            }
            
            if (todoistKey.isNullOrBlank()) {
                return "❌ Не установлен TODOIST_API_KEY"
            }
            
            // Останавливаем предыдущий планировщик, если он запущен
            stopScheduler()
            
            // Запускаем планировщик через gradle в отдельном потоке
            schedulerThread = thread {
                try {
                    val processBuilder = ProcessBuilder(
                        "./gradlew", "runJob",
                        "--args=--intervalSeconds=300 --useMcpChain=true --input=/mcp-chain"
                    )
                    processBuilder.environment().putAll(System.getenv())
                    processBuilder.redirectErrorStream(true)
                    
                    schedulerProcess = processBuilder.start()
                    
                    // Читаем вывод процесса
                    schedulerProcess?.inputStream?.bufferedReader()?.use { reader ->
                        reader.lines().forEach { line ->
                            println("[Планировщик] $line")
                        }
                    }
                    
                    val exitCode = schedulerProcess?.waitFor() ?: -1
                    println("🤖 Планировщик завершился с кодом: $exitCode")
                    
                } catch (e: Exception) {
                    println("❌ Ошибка в планировщике: ${e.message}")
                }
            }
            
            // Даем время на запуск
            Thread.sleep(2000)
            
            if (schedulerProcess?.isAlive == true) {
                "✅ Планировщик Todoist запущен в фоновом режиме!\n" +
                "📱 Задачи будут отправляться в Telegram каждые 5 минут\n" +
                "🛑 Для остановки используйте команду 'остановить планировщик'"
            } else {
                // Если процесс не запустился, показываем задачи напрямую
                val tasks = todoistManager.getTasks(filter = "today")
                if (tasks.isNotEmpty()) {
                    val tasksText = formatTasksForDisplay(tasks)
                    "⚠️ Планировщик не запустился, но вот ваши задачи на сегодня:\n$tasksText"
                } else {
                    "⚠️ Планировщик не запустился и задач на сегодня не найдено"
                }
            }
            
        } catch (e: Exception) {
            "❌ Ошибка выполнения планировщика: ${e.message}"
        }
    }
    
    private fun stopScheduler(): String {
        return try {
            var stopped = false
            
            schedulerProcess?.let { process ->
                if (process.isAlive) {
                    process.destroyForcibly()
                    process.waitFor(10, TimeUnit.SECONDS)
                    stopped = true
                }
            }
            
            schedulerThread?.let { thread ->
                if (thread.isAlive) {
                    thread.interrupt()
                    stopped = true
                }
            }
            
            schedulerProcess = null
            schedulerThread = null
            
            if (stopped) {
                "✅ Планировщик остановлен"
            } else {
                "ℹ️ Планировщик не был запущен"
            }
        } catch (e: Exception) {
            "❌ Ошибка остановки планировщика: ${e.message}"
        }
    }
    
    private fun formatTasksForDisplay(tasks: List<Map<String, Any>>): String {
        return buildString {
            for (task in tasks) {
                // Правильно извлекаем данные из MCP ответа
                val result = task["result"] as? Map<*, *>
                val content = result?.get("content") as? List<*>
                
                if (content != null) {
                    for (item in content) {
                        if (item is Map<*, *>) {
                            val taskContent = item["content"] as? String ?: "Без названия"
                            val taskDue = item["due"] as? Map<*, *>
                            val dueString = taskDue?.get("string") as? String ?: ""
                            val dueDate = if (dueString.isNotEmpty()) " ($dueString)" else ""
                            appendLine("- $taskContent$dueDate")
                        }
                    }
                } else {
                    // Fallback для прямого API
                    val taskContent = task["content"] as? String ?: "Без названия"
                    val taskDue = task["due"] as? String ?: ""
                    val dueDate = if (taskDue.isNotEmpty()) " ($taskDue)" else ""
                    appendLine("- $taskContent$dueDate")
                }
            }
            
            if (isEmpty()) {
                append("📝 Задач на сегодня не найдено")
            }
        }
    }
    
    private fun getSystemStatus(): String {
        return buildString {
            appendLine("📊 Статус системы:")
            appendLine("• Todoist: ${todoistManager.getStatus()}")
            appendLine("• MCP подключение: ${if (todoistManager.isMcpConnected()) "✅" else "❌"}")
            appendLine("• Доступные инструменты: ${todoistManager.getAvailableTools().joinToString(", ")}")
            appendLine("• Планировщик: ${if (schedulerProcess?.isAlive == true) "🟢 Запущен" else "🔴 Остановлен"}")
            
            // Проверяем переменные окружения
            val geminiKey = System.getenv("GEMINI_API_KEY")?.let { "✅ установлен" } ?: "❌ не установлен"
            val todoistKey = System.getenv("TODOIST_API_KEY")?.let { "✅ установлен" } ?: "❌ не установлен"
            val tgToken = System.getenv("TELEGRAM_BOT_TOKEN")?.let { "✅ установлен" } ?: "❌ не установлен"
            val tgChatId = System.getenv("TELEGRAM_CHAT_ID")?.let { "✅ установлен" } ?: "❌ не установлен"
            
            appendLine("• GEMINI_API_KEY: $geminiKey")
            appendLine("• TODOIST_API_KEY: $todoistKey")
            appendLine("• TELEGRAM_BOT_TOKEN: $tgToken")
            appendLine("• TELEGRAM_CHAT_ID: $tgChatId")
            
            // Проверяем файлы
            val envFile = File(".env")
            val gradlewFile = File("./gradlew")
            appendLine("• .env файл: ${if (envFile.exists()) "✅ найден" else "❌ не найден"}")
            appendLine("• gradlew: ${if (gradlewFile.exists() && gradlewFile.canExecute()) "✅ готов" else "❌ недоступен"}")
        }
    }
    
    private fun configureSystem(): String {
        return "⚙️ Конфигурация системы:\n" +
               "Для настройки системы установите переменные окружения:\n" +
               "- GEMINI_API_KEY\n" +
               "- TODOIST_API_KEY\n" +
               "- TELEGRAM_BOT_TOKEN\n" +
               "- TELEGRAM_CHAT_ID\n\n" +
               "Или создайте файл .env с этими переменными"
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
        stopScheduler()
        todoistManager.disconnect()
    }
}
