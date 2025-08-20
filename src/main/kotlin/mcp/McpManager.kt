package mcp

import runChat
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class McpManager {
    private var todoistMcp: TodoistMcp? = null
    private var isConnected = false

    fun connect(): Boolean {
        return try {
            todoistMcp = TodoistMcp()
            isConnected = todoistMcp?.connect() == true

            if (isConnected) {
                println("✅ MCP серверы подключены")
            } else {
                println("❌ Не удалось подключиться к MCP серверам")
            }

            isConnected
        } catch (e: Exception) {
            println("❌ Ошибка подключения к MCP: ${e.message}")
            false
        }
    }

    fun getTodayTasksAndSendToTelegram(): String {
        return if (isConnected && todoistMcp != null) {
            try {
                // Получаем задачи из Todoist
                val tasks = todoistMcp!!.listTasks(filter = "today")

                if (tasks.isNotEmpty()) {
                    val rawTasksText = formatTasksForDisplay(tasks)
                    println("[McpManager] 📋 Сырые данные задач: $rawTasksText")

                    // Обрабатываем через Gemini для красивого форматирования
                    val geminiInput = """
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
                        
                        Вот сами задачи на сегодня: $tasks
                    """.trimIndent()

                    println("[McpManager] 🤖 Обрабатываю задачи через Gemini...")
                    val formattedMessage = runChat(geminiInput)

                    println("[McpManager] 📝 Gemini сформировал сообщение: ${formattedMessage.take(200)}...")

                    // Отправляем отформатированное сообщение в Telegram
                    val telegramResult = sendToTelegram(formattedMessage)

                    if (telegramResult.startsWith("✅")) {
                        "✅ Отчёт по задачам отправлен в Telegram через Gemini:\n${formattedMessage.take(300)}..."
                    } else {
                        "⚠️ Отчёт сформирован Gemini, но ошибка отправки в Telegram:\n$telegramResult"
                    }
                } else {
                    // Обрабатываем отсутствие задач через Gemini
                    val noTasksInput = """
                        Создай доброе утреннее сообщение для пользователя. 
                        У него нет задач на сегодня в Todoist.
                        Добавь мотивирующую цитату и пожелания хорошего дня.
                        Используй эмоджи и тёплый тон.
                        Сообщение для Telegram, учитывай его форматирование.
                        Ты личный помощник Тудуистик!
                    """.trimIndent()

                    val noTasksMessage = runChat(noTasksInput)
                    sendToTelegram(noTasksMessage)
                    "📅 Задач на сегодня не найдено, отправлено мотивирующее сообщение"
                }
            } catch (e: Exception) {
                val errorMessage = "❌ Ошибка получения задач через MCP: ${e.message}"
                println("[McpManager] $errorMessage")
                try {
                    sendToTelegram("❌ Произошла ошибка при получении задач из Todoist. Проверьте настройки.")
                } catch (telegramError: Exception) {
                    println("[McpManager] Также не удалось отправить ошибку в Telegram: ${telegramError.message}")
                }
                errorMessage
            }
        } else {
            val errorMessage = "❌ MCP сервер не подключен"
            try {
                sendToTelegram("🔌 Планировщик задач временно недоступен. Проверьте подключение к Todoist.")
            } catch (e: Exception) {
                println("[McpManager] Не удалось отправить статус отключения в Telegram: ${e.message}")
            }
            errorMessage
        }
    }

    private fun sendToTelegram(message: String): String {
        return try {
            val tgToken = System.getenv("TELEGRAM_BOT_TOKEN")
            val tgChatId = System.getenv("TELEGRAM_CHAT_ID")

            if (tgToken.isNullOrBlank() || tgChatId.isNullOrBlank()) {
                return "❌ Не установлены токены Telegram"
            }

            println("[McpManager] 📤 Отправляю сообщение в Telegram...")

            val chunks = message.chunked(3900) // Telegram limit
            for ((i, chunk) in chunks.withIndex()) {
                val success = sendTelegramMessage(tgToken, tgChatId, chunk)
                if (!success) {
                    return "❌ Ошибка отправки части ${i + 1}/${chunks.size} в Telegram"
                }
                if (chunks.size > 1) {
                    Thread.sleep(300) // Avoid flood limits
                }
            }

            println("[McpManager] ✅ Сообщение отправлено в Telegram")
            "✅ Сообщение отправлено в Telegram"

        } catch (e: Exception) {
            val error = "❌ Ошибка отправки в Telegram: ${e.message}"
            println("[McpManager] $error")
            error
        }
    }

    private fun sendTelegramMessage(token: String, chatId: String, text: String): Boolean {
        return try {
            val url = "https://api.telegram.org/bot$token/sendMessage"
            val form = "chat_id=${URLEncoder.encode(chatId, "UTF-8")}&text=${URLEncoder.encode(text, "UTF-8")}&parse_mode=Markdown"

            val u = URL(url)
            val conn = (u.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                connectTimeout = 15000
                readTimeout = 15000
            }

            conn.outputStream.use { os ->
                os.write(form.toByteArray(StandardCharsets.UTF_8))
            }

            val code = conn.responseCode

            if (code >= 300) {
                val err = conn.errorStream?.readBytes()?.toString(Charsets.UTF_8)
                println("[McpManager] Telegram HTTP $code: $err")
                false
            } else {
                true
            }
        } catch (e: Exception) {
            println("[McpManager] Ошибка HTTP запроса к Telegram: ${e.message}")
            false
        }
    }

    private fun formatTasksForDisplay(tasks: List<Map<String, Any>>): String {
        return buildString {
            for (task in tasks) {
                val result = (task["result"] as? Map<*, *>)?.get("content") as? List<*>
                result?.forEach { taskData ->
                    if (taskData is Map<*, *>) {
                        val content = taskData["content"] as? String ?: "Без названия"
                        val due = (taskData["due"] as? Map<*, *>)?.get("string") as? String ?: ""
                        val dueText = if (due.isNotEmpty()) " (срок: $due)" else ""
                        appendLine("• $content$dueText")
                    }
                }
            }

            if (isEmpty()) {
                append("Задач на сегодня не запланировано")
            }
        }
    }

    fun disconnect() {
        todoistMcp?.disconnect()
        isConnected = false
    }

    fun isConnected(): Boolean = isConnected
}