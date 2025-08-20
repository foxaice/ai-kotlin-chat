import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import mcp.TodoistManager
import agent.SystemAgent
import kotlin.collections.get

private const val INSTRUCTION_FILE = "system_instruction.txt"

data class Msg(val role: String, val content: String) // role: "user" | "model"

// Стандартная функция main для запуска через Gradle
fun main() {
    runChat()
}

// Функция main с параметром для использования в других местах
fun main(input: String? = null): String {
    return runChat(input)
}

fun runChat(input: String? = null): String {
    val apiKey = System.getenv("GEMINI_API_KEY")
        ?: error("Set GEMINI_API_KEY environment variable")

    val todoistApiKey = System.getenv("TODOIST_API_KEY")
    val todoistServerPath = System.getenv("TODOIST_MCP_SERVER_PATH")

    val model = "gemini-2.5-flash-lite"
    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

    val systemInstruction = loadSystemInstruction()

    // Инициализируем системный агент
    val systemAgent = SystemAgent()
    systemAgent.initialize()

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

    println("Gemini Kotlin Chat с поддержкой системного агента. Type 'exit' to quit.\n")

    while (true) {
        print("Вы: ")
        val userInput = input ?: readlnOrNull()?.trim().orEmpty()
        if (userInput.isEmpty() || userInput.equals("exit", true)) {
            if (todoistApiKey != null) {
                todoistManager.disconnect()
            }
            systemAgent.disconnect()
            break
        }

        history += Msg("user", userInput)

        // Обрабатываем Todoist команды
        if (userInput.startsWith("/todoist")) {
            val commandResult = handleTodoistCommand(userInput, todoistManager)
            history += Msg(
                "user", """
                Создай отчёт по задачам на сегодня для меня. Передавай мне доброе утро! и какую-нибудь рандомную цитату,
                чтобы легче было вставать и выполнять задачи - не говори, что это рандомная цитата, 
                сделай вид что ты сам её предложить и напиши всё сообщение мне гармонично, чтобы оно смотрелось
                Добавь эмоджи и теплоты)
                Расскажи, что у меня запланировано на сегодня и если есть конкретное время выполнение задачи, то обязательно укажи это в сообщении - при написании задач эмоджи не пишутся
                Укажи время к задачам!!!!
                Ты личный помощник Тудуистик!
                Помни, что ты пишешь сообщения для телеграмма. Учитывай формат сообщения, чтобы телеграм его поддерживал!!!
                
                формат задачи:
                - {название задачи}{время, когда необходимо её исполнить}
                
                пункт про задачи выдели жирным
                
                Вот сами задачи на сегодня: $commandResult
            """.trimIndent()
            )
            println("идёт сбор отчёта по задачам на сегодня...")
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

            // Проверяем, содержит ли ответ команду агента
            val finalReply = if (reply.contains("КОМАНДА_АГЕНТА:")) {
                val commandLine = reply.lines().find { it.contains("КОМАНДА_АГЕНТА:") }
                if (commandLine != null) {
                    val command = commandLine.substringAfter("КОМАНДА_АГЕНТА:").trim()
                    println("🤖 Обнаружена команда агента: $command")
                    
                    // Выполняем команду через агента
                    val agentResult = systemAgent.executeCommand(command)
                    println("🤖 Результат выполнения: $agentResult")
                    
                    // Заменяем команду на результат выполнения
                    reply.replace(commandLine, "Результат выполнения команды:\n$agentResult")
                } else {
                    reply
                }
            } else {
                reply
            }

            println("Модель: $finalReply\n")
            history += Msg("model", finalReply)

            // Если это однократный запуск (input задан), возвращаем результат
            if (input != null) {
                return finalReply
            }
        }
    }
    return ""
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

private fun handleTodoistCommand(command: String, todoistManager: TodoistManager): String {
    val parts = command.split(" ")

    val response = StringBuilder()

    operator fun StringBuilder.invoke() = this.toString()

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
        return response()
    }

    when (parts[1]) {
        "projects" -> {
            println("📋 Получение проектов...")
            val projects = todoistManager.getProjects()

            if (projects.isEmpty()) {
                println("📋 Проекты не найдены")
            } else {
                println("📋 Проекты получены")

                response.append(projects)
            }
        }

        "tasks" -> {
            val projectId = if (parts.size > 2) parts[2] else null
            println("📋 Получение задач${if (projectId != null) " для проекта $projectId" else ""}...")
            val tasks = todoistManager.getTasks(projectId)
            if (tasks.isEmpty()) {
                println("📋 Задачи не найдены")
            } else {
                println("📋 Задачи получены")
                val tasksText = (((tasks.first()["result"] as? Map<*, *>).orEmpty()["content"] as? List<*>)
                    ?.firstOrNull() as? Map<*, *>).orEmpty()

                response.append(tasksText)
            }
        }

        "todayTasks" -> {
            println("📋 Получение задач на сегодня...")
            val tasks = todoistManager.getTasks(filter = "today")
            if (tasks.isEmpty()) {
                println("📋 Задачи не найдены задачи на сегодня")
            } else {
                println("📋 Задачи получены")
                val tasksText = (((tasks.first()["result"] as? Map<*, *>).orEmpty()["content"] as? List<*>)
                    ?.firstOrNull() as? Map<*, *>).orEmpty()
                println(tasksText)
                response.append(tasksText)
            }
        }

        "add" -> {
            if (parts.size < 3) {
                println("❌ Укажите текст задачи")
                return response()
            }
            val content = parts.drop(2).joinToString(" ")
            val projectId = if (parts.size > 3) parts[3] else null
            println("project " + parts.getOrNull(3))
            val dueDate = if (parts.size > 4) parts[4] else null
            println("due " + parts.getOrNull(4))

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
                return response()
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
                return response()
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
                return response()
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
    return response()
}
