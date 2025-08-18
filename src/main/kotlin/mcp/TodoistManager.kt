package mcp

class TodoistManager {
    private val mcpClient = TodoistMcp()
    private val apiClient = TodoistApiClient()
    private var useMcp = false

    fun initialize(todoistApiKey: String, todoistServerPath: String? = null): Boolean {
        // Устанавливаем API ключ для прямого API
        apiClient.setApiKey(todoistApiKey)

        // Пытаемся подключиться к MCP серверу
        if (todoistServerPath != null) {
            useMcp = mcpClient.connect(todoistServerPath)
        } else {
            // Пытаемся найти сервер в PATH
            useMcp = mcpClient.connect()
        }

        if (useMcp) {
            println("✅ Используется MCP сервер для Todoist")
        } else {
            println("⚠️ MCP сервер недоступен, используется прямой API")
        }

        return true
    }

    fun getProjects(): String {
        return if (useMcp && mcpClient.isConnected()) {
            mcpClient.listProjects()
        } else {
            // Конвертируем API проекты в Map
            apiClient.getProjects().map { project ->
                mapOf(
                    "id" to project.id,
                    "name" to project.name,
                    "color" to (project.color ?: ""),
                    "url" to project.url,
                    "is_inbox" to project.is_inbox_project,
                    "favorite" to project.is_favorite
                )
            }.toString()
        }
    }

    fun getTasks(projectId: String? = null, filter: String? = null): List<Map<String, Any>> {
        return if (useMcp && mcpClient.isConnected()) {
            mcpClient.listTasks(projectId = projectId, filter = filter)
        } else {
            // Конвертируем API задачи в Map
            apiClient.getTasks(projectId).map { task ->
                mapOf(
                    "id" to task.id,
                    "content" to task.content,
                    "description" to (task.description ?: ""),
                    "project_id" to (task.project_id ?: ""),
                    "priority" to task.priority,
                    "due" to (task.due?.string ?: ""),
                    "is_completed" to task.is_completed,
                    "url" to task.url,
                    "labels" to task.labels
                )
            }
        }
    }

    fun createTask(content: String, projectId: String? = null, dueDate: String? = null): Boolean {
        return if (useMcp && mcpClient.isConnected()) {
            mcpClient.createTask(content, projectId, dueDate)
        } else {
            val taskRequest = CreateTaskRequest(
                content = content,
                project_id = projectId,
                due_string = dueDate
            )
            apiClient.createTask(taskRequest) != null
        }
    }

    fun completeTask(taskId: String): Boolean {
        return if (useMcp && mcpClient.isConnected()) {
            // MCP использует название задачи, а не ID
            // Пока что используем прямой API
            apiClient.closeTask(taskId)
        } else {
            apiClient.closeTask(taskId)
        }
    }

    fun completeTaskByName(taskName: String): Boolean {
        return if (useMcp && mcpClient.isConnected()) {
            mcpClient.completeTaskByName(taskName)
        } else {
            // Для прямого API нужно найти задачу по названию
            val tasks = apiClient.getTasks()
            val task = tasks.find { it.content.contains(taskName, ignoreCase = true) }
            if (task != null) {
                apiClient.closeTask(task.id)
            } else {
                println("❌ Задача '$taskName' не найдена")
                false
            }
        }
    }

    fun updateTask(
        taskName: String,
        newContent: String? = null,
        newDueDate: String? = null,
        newPriority: Int? = null
    ): Boolean {
        return if (useMcp && mcpClient.isConnected()) {
            mcpClient.updateTask(taskName, newContent, newDueDate, newPriority)
        } else {
            // Для прямого API нужно найти задачу по названию и обновить
            val tasks = apiClient.getTasks()
            val task = tasks.find { it.content.contains(taskName, ignoreCase = true) }
            if (task != null) {
                // TODO: Реализовать обновление задачи через API
                println("⚠️ Обновление задачи через API пока не реализовано")
                false
            } else {
                println("❌ Задача '$taskName' не найдена")
                false
            }
        }
    }

    fun deleteTask(taskId: String): Boolean {
        return if (useMcp && mcpClient.isConnected()) {
            // MCP использует название задачи, а не ID
            // Пока что используем прямой API
            apiClient.deleteTask(taskId)
        } else {
            apiClient.deleteTask(taskId)
        }
    }

    fun deleteTaskByName(taskName: String): Boolean {
        return if (useMcp && mcpClient.isConnected()) {
            mcpClient.deleteTask(taskName)
        } else {
            // Для прямого API нужно найти задачу по названию
            val tasks = apiClient.getTasks()
            val task = tasks.find { it.content.contains(taskName, ignoreCase = true) }
            if (task != null) {
                apiClient.deleteTask(task.id)
            } else {
                println("❌ Задача '$taskName' не найдена")
                false
            }
        }
    }

    fun getAvailableTools(): List<String> {
        return if (useMcp && mcpClient.isConnected()) {
            mcpClient.getAvailableTools()
        } else {
            listOf("get_projects", "get_tasks", "create_task", "complete_task", "delete_task")
        }
    }

    fun isMcpConnected(): Boolean {
        return useMcp && mcpClient.isConnected()
    }

    fun disconnect() {
        if (useMcp) {
            mcpClient.disconnect()
        }
    }

    fun getStatus(): String {
        return if (useMcp && mcpClient.isConnected()) {
            "MCP сервер подключен"
        } else if (useMcp) {
            "MCP сервер недоступен, используется API"
        } else {
            "Прямой API"
        }
    }
}
