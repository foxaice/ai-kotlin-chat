import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import mcp.TodoistManager

private const val INSTRUCTION_FILE = "system_instruction.txt"

data class Msg(val role: String, val content: String) // role: "user" | "model"

fun main() {
    val apiKey = System.getenv("GEMINI_API_KEY")
        ?: error("Set GEMINI_API_KEY environment variable")
    
    val todoistApiKey = System.getenv("TODOIST_API_KEY") ?: error("Set TODOIST_API_KEY environment variable")
    val todoistServerPath = System.getenv("TODOIST_MCP_SERVER_PATH")

    val model = "gemini-2.5-flash"
    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

    val systemInstruction = loadSystemInstruction()

    println("systemInstruction: $systemInstruction")
    
    // Инициализируем Todoist менеджер
    val todoistManager = TodoistManager()
    if (todoistApiKey != null) {
        todoistManager.initialize(todoistApiKey, todoistServerPath)
        println("📋 Todoist: ${todoistManager.getStatus()}")
    } else {
        println("⚠️ TODOIST_API_KEY не установлен, Todoist функции недоступны")
    }
    
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
        if (userInput.isEmpty() || userInput.equals("exit", true)) {
            if (todoistApiKey != null) {
                todoistManager.disconnect()
            }
            break
        }

        history += Msg("user", userInput)

        // Обрабатываем Todoist команды
        if (userInput.startsWith("/todoist")) {
            handleTodoistCommand(userInput, todoistManager)
            continue
        }

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

private fun handleTodoistCommand(command: String, todoistManager: TodoistManager) {
    val parts = command.split(" ")
    if (parts.size < 2) {
        println("📋 Использование: /todoist <команда> [параметры]")
        println("📋 Доступные команды:")
        println("  projects - список проектов")
        println("  tasks [project_id] - список задач")
        println("  add <текст> [project_id] [due_date] - создать задачу")
        println("  complete <task_id|название> - завершить задачу")
        println("  delete <task_id|название> - удалить задачу")
        println("  update <название> - обновить задачу")
        println("  status - статус подключения")
        return
    }
    
    when (parts[1]) {
        "projects" -> {
            println("📋 Получение проектов...")
            val projects = todoistManager.getProjects()
            if (projects.isEmpty()) {
                println("📋 Проекты не найдены")
            } else {
                println("📋 Проекты:")
                projects.forEach { project ->
                    val inbox = if (project["is_inbox"] as Boolean) " [Inbox]" else ""
                    val favorite = if (project["favorite"] as Boolean) " ⭐" else ""
                    println("  ${project["id"]}: ${project["name"]}$inbox$favorite")
                }
            }
        }
        "tasks" -> {
            val projectId = if (parts.size > 2) parts[2] else null
            println("📋 Получение задач${if (projectId != null) " для проекта $projectId" else ""}...")
            val tasks = todoistManager.getTasks(projectId)
            if (tasks.isEmpty()) {
                println("📋 Задачи не найдены")
            } else {
                println("📋 Задачи:")
                tasks.forEach { task ->
                    val completed = if (task["is_completed"] as Boolean) " ✅" else " ⏳"
                    val priority = when (task["priority"] as Int) {
                        4 -> " 🔴"
                        3 -> " 🟠"
                        2 -> " 🟡"
                        else -> " ⚪"
                    }
                    println("  ${task["id"]}: ${task["content"]}$completed$priority")
                    if ((task["description"] as String).isNotEmpty()) {
                        println("    ${task["description"]}")
                    }
                    if ((task["due"] as String).isNotEmpty()) {
                        println("    📅 ${task["due"]}")
                    }
                }
            }
        }
        "add" -> {
            if (parts.size < 3) {
                println("❌ Укажите текст задачи")
                return
            }
            val content = parts.drop(2).joinToString(" ")
            val projectId = if (parts.size > 3) parts[3] else null
            println("project "+parts.getOrNull(3))
            val dueDate = if (parts.size > 4) parts[4] else null
            println("due "+parts.getOrNull(4))

            println(parts)
            println("📋 Создание задачи: $content")
            if (todoistManager.createTask(content, projectId, dueDate)) {
                println("✅ Задача создана успешно")
            } else {
                println("❌ Ошибка создания задачи")
            }
        }
        "complete" -> {
            if (parts.size < 3) {
                println("❌ Укажите ID задачи или название")
                return
            }
            val taskIdentifier = parts[2]
            
            // Пытаемся определить, это ID или название
            if (taskIdentifier.matches(Regex("^\\d+$"))) {
                // Это ID
                println("📋 Завершение задачи по ID $taskIdentifier...")
                if (todoistManager.completeTask(taskIdentifier)) {
                    println("✅ Задача завершена")
                } else {
                    println("❌ Ошибка завершения задачи")
                }
            } else {
                // Это название
                val taskName = parts.drop(2).joinToString(" ")
                println("📋 Завершение задачи '$taskName'...")
                if (todoistManager.completeTaskByName(taskName)) {
                    println("✅ Задача завершена")
                } else {
                    println("❌ Ошибка завершения задачи")
                }
            }
        }
        "delete" -> {
            if (parts.size < 3) {
                println("❌ Укажите ID задачи или название")
                return
            }
            val taskIdentifier = parts[2]
            
            // Пытаемся определить, это ID или название
            if (taskIdentifier.matches(Regex("^\\d+$"))) {
                // Это ID
                println("📋 Удаление задачи по ID $taskIdentifier...")
                if (todoistManager.deleteTask(taskIdentifier)) {
                    println("✅ Задача удалена")
                } else {
                    println("❌ Ошибка удаления задачи")
                }
            } else {
                // Это название
                val taskName = parts.drop(2).joinToString(" ")
                println("📋 Удаление задачи '$taskName'...")
                if (todoistManager.deleteTaskByName(taskName)) {
                    println("✅ Задача удалена")
                } else {
                    println("❌ Ошибка удаления задачи")
                }
            }
        }
        "update" -> {
            if (parts.size < 3) {
                println("❌ Укажите название задачи для обновления")
                return
            }
            val taskName = parts.drop(2).joinToString(" ")
            println("📋 Обновление задачи '$taskName'...")
            
            // Запрашиваем новые данные
            print("Новое название (Enter для пропуска): ")
            val newContent = readlnOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            
            print("Новая дата (Enter для пропуска): ")
            val newDueDate = readlnOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            
            print("Новый приоритет 1-4 (Enter для пропуска): ")
            val newPriorityStr = readlnOrNull()?.trim()
            val newPriority = newPriorityStr?.toIntOrNull()?.takeIf { it in 1..4 }
            
            if (todoistManager.updateTask(taskName, newContent, newDueDate, newPriority)) {
                println("✅ Задача обновлена")
            } else {
                println("❌ Ошибка обновления задачи")
            }
        }
        "status" -> {
            println("📋 Статус Todoist: ${todoistManager.getStatus()}")
            if (todoistManager.isMcpConnected()) {
                println("📋 Доступные MCP инструменты: ${todoistManager.getAvailableTools().joinToString(", ")}")
            }
        }
        else -> {
            println("❌ Неизвестная команда: ${parts[1]}")
        }
    }
    println()
}
