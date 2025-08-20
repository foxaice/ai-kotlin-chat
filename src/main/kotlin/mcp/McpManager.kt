package mcp

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
                val tasks = todoistMcp!!.listTasks(filter = "today")
                if (tasks.isNotEmpty()) {
                    val tasksText = formatTasksForTelegram(tasks)
                    "📅 Задачи на сегодня получены через MCP цепочку:\n$tasksText"
                } else {
                    "📅 Задач на сегодня не найдено"
                }
            } catch (e: Exception) {
                "❌ Ошибка получения задач через MCP: ${e.message}"
            }
        } else {
            "❌ MCP сервер не подключен"
        }
    }

    private fun formatTasksForTelegram(tasks: List<Map<String, Any>>): String {
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

    fun disconnect() {
        todoistMcp?.disconnect()
        isConnected = false
    }

    fun isConnected(): Boolean = isConnected
}