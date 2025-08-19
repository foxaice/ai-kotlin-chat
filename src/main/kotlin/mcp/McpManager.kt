package mcp

class McpManager {
    private val todoistMcp = TodoistMcp()
    private val telegramMcp = TelegramMcp()

    var isConnected = false
        private set

    fun connect(): Boolean {
        println("🔄 Подключение к MCP серверам...")

        val todoistConnected = todoistMcp.connect()
        val telegramConnected = telegramMcp.connect()

        isConnected = todoistConnected && telegramConnected

        if (isConnected) {
            println("✅ Оба MCP сервера подключены успешно")
            println("📋 Доступные Todoist инструменты: ${todoistMcp.getAvailableTools()}")
            println("📱 Доступные Telegram инструменты: ${telegramMcp.getAvailableTools()}")
        } else {
            println("❌ Не удалось подключить все MCP сервера")
            println("   - Todoist MCP: ${if (todoistConnected) "✅" else "❌"}")
            println("   - Telegram MCP: ${if (telegramConnected) "✅" else "❌"}")
        }

        return isConnected
    }

    fun disconnect() {
        println("🔌 Отключение от MCP серверов...")
        todoistMcp.disconnect()
        telegramMcp.disconnect()
        isConnected = false
    }

    /**
     * Основной метод для работы с цепочкой MCP:
     * 1. Получает задачи из Todoist через первый MCP
     * 2. Форматирует и отправляет их через Telegram MCP
     */
    fun getTodayTasksAndSendToTelegram(): String {
        if (!isConnected) {
            return "❌ MCP сервера не подключены"
        }

        try {
            println("📋 Получение задач на сегодня из Todoist...")

            // Шаг 1: Получаем задачи из Todoist
            val todayTasks = todoistMcp.listTasks(filter = "today")

            if (todayTasks.isEmpty()) {
                val noTasksMessage = "🎉 Отличная работа! Нет активных задач на сегодня ✅"
                telegramMcp.sendMessage(noTasksMessage)
                return noTasksMessage
            }

            println("📤 Найдено задач: ${todayTasks.size}")

            // Шаг 2: Конвертируем в JSON для передачи во второй MCP
            val tasksJson = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                .writeValueAsString(todayTasks[0]) // Используем первый элемент, который содержит все данные

            println("📱 Отправка форматированных задач в Telegram...")

            // Шаг 3: Отправляем через Telegram MCP
            val success = telegramMcp.sendFormattedTasks(tasksJson)

            return if (success) {
                val message = "✅ Задачи успешно отправлены в Telegram"
                println(message)
                message
            } else {
                val message = "❌ Ошибка отправки в Telegram"
                println(message)
                message
            }

        } catch (e: Exception) {
            val message = "❌ Ошибка в цепочке MCP: ${e.message}"
            println(message)
            e.printStackTrace()
            return message
        }
    }

    /**
     * Альтернативный метод с ручным форматированием
     */
    fun getTodayTasksWithCustomFormat(): String {
        if (!isConnected) {
            return "❌ MCP сервера не подключены"
        }

        try {
            // Получаем задачи
            val todayTasks = todoistMcp.listTasks(filter = "today")

            if (todayTasks.isEmpty()) {
                return "🎉 Нет задач на сегодня!"
            }

            // Форматируем вручную
            val tasksJson = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                .writeValueAsString(todayTasks[0])

            val formattedText = telegramMcp.formatTaskList(tasksJson, "emoji")

            // Отправляем
            val success = telegramMcp.sendMessage(formattedText)

            return if (success) {
                "✅ Задачи отправлены:\n$formattedText"
            } else {
                "❌ Ошибка отправки"
            }

        } catch (e: Exception) {
            return "❌ Ошибка: ${e.message}"
        }
    }

    // Методы для прямого доступа к MCP серверам
    fun getTodoistMcp(): TodoistMcp = todoistMcp
    fun getTelegramMcp(): TelegramMcp = telegramMcp
}
