package mcp

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.File
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

class TodoistMcp {
    private var isConnected = false
    private var process: Process? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private val json = jacksonObjectMapper()
    
    fun connect(todoistServerPath: String = "todoist-mcp-server"): Boolean {
        return try {
            // Запускаем MCP сервер через node
            val serverPath = if (todoistServerPath.endsWith(".js")) {
                // Если указан .js файл, запускаем через node
                "node"
            } else if (todoistServerPath.startsWith("/")) {
                // Если указан полный путь к .js файлу
                "node"
            } else {
                // По умолчанию используем node
                "node"
            }
            
            val processBuilder = if (serverPath == "node") {
                // Запускаем через node
                val jsFile = if (todoistServerPath.endsWith(".js")) {
                    todoistServerPath
                } else {
                    "$todoistServerPath/dist/index.js"
                }
                ProcessBuilder("node", jsFile)
            } else {
                // Прямой запуск (если это исполняемый файл)
                ProcessBuilder(serverPath)
            }
            
            processBuilder.environment()["TODOIST_API_TOKEN"] = System.getenv("TODOIST_API_KEY") ?: ""
            
            process = processBuilder.start()
            reader = BufferedReader(InputStreamReader(process!!.inputStream))
            writer = BufferedWriter(OutputStreamWriter(process!!.outputStream))
            
            // Инициализируем MCP соединение
            val initRequest = mapOf(
                "jsonrpc" to "2.0",
                "id" to 1,
                "method" to "initialize",
                "params" to mapOf(
                    "protocolVersion" to "2024-11-05",
                    "capabilities" to emptyMap<String, Any>(),
                    "clientInfo" to mapOf(
                        "name" to "gemini-kotlin-chat",
                        "version" to "1.0"
                    )
                )
            )
            
            sendRequest(initRequest)
            val initResponse = readResponse()
            
            if (initResponse != null && !initResponse.containsKey("error")) {
                isConnected = true
                println("✅ Подключен к Todoist MCP серверу")
                true
            } else {
                println("❌ Ошибка инициализации MCP сервера")
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Ошибка подключения к Todoist MCP серверу: ${e.message}")
            false
        }
    }
    
    fun disconnect() {
        try {
            isConnected = false
            process?.destroy()
            reader?.close()
            writer?.close()
            println("🔌 Отключен от Todoist MCP сервера")
        } catch (e: Exception) {
            println("⚠️ Ошибка при отключении: ${e.message}")
        }
    }
    
    fun getAvailableTools(): List<String> {
        return try {
            val request = mapOf(
                "jsonrpc" to "2.0",
                "id" to 2,
                "method" to "tools/list",
                "params" to emptyMap<String, Any>()
            )
            
            sendRequest(request)
            val response = readResponse()
            
            if (response != null && response.containsKey("result")) {
                val result = response["result"] as Map<*, *>
                val tools = result["tools"] as List<Map<*, *>>
                tools.mapNotNull { tool ->
                    tool["name"] as? String
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            println("❌ Ошибка получения инструментов: ${e.message}")
            emptyList()
        }
    }
    
    fun callTool(toolName: String, arguments: Map<String, Any>): Boolean {
        return try {
            val request = mapOf(
                "jsonrpc" to "2.0",
                "id" to System.currentTimeMillis().toInt(),
                "method" to "tools/call",
                "params" to mapOf(
                    "name" to toolName,
                    "arguments" to arguments
                )
            )
            
            sendRequest(request)
            val response = readResponse()
            
            if (response != null && response.containsKey("result")) {
                val result = response["result"] as Map<*, *>
                val isError = result["isError"] as? Boolean ?: false
                !isError
            } else {
                false
            }
        } catch (e: Exception) {
            println("❌ Ошибка вызова инструмента $toolName: ${e.message}")
            false
        }
    }
    
    private fun sendRequest(request: Map<String, Any>) {
        val jsonRequest = json.writeValueAsString(request)
        writer?.write(jsonRequest)
        writer?.newLine()
        writer?.flush()
    }
    
    private fun readResponse(): Map<String, Any>? {
        return try {
            val line = reader?.readLine()
            if (line != null) {
                json.readValue(line)
            } else {
                null
            }
        } catch (e: Exception) {
            println("❌ Ошибка чтения ответа: ${e.message}")
            null
        }
    }
    
    fun listProjects(): List<Map<String, Any>> {
        // MCP сервер не предоставляет инструмент для получения проектов
        // Используем прямой API через TodoistApiClient
        println("⚠️ Получение проектов через MCP не поддерживается, используется прямой API")
        return emptyList()
    }
    
    fun listTasks(projectId: String? = null): List<Map<String, Any>> {
        val args = mutableMapOf<String, Any>()
        if (projectId != null) {
            args["project_id"] = projectId
        }
        
        val success = callTool("todoist_get_tasks", args)
        return if (success) {
            try {
                // Парсим результат от MCP сервера
                // Пока что возвращаем пустой список, так как парсинг требует дополнительной логики
                emptyList()
            } catch (e: Exception) {
                println("❌ Ошибка парсинга задач: ${e.message}")
                emptyList()
            }
        } else {
            emptyList()
        }
    }
    
    fun createTask(content: String, projectId: String? = null, dueDate: String? = null): Boolean {
        val args = mutableMapOf<String, Any>()
        args["content"] = content
        
        if (dueDate != null) {
            args["due_string"] = dueDate
        }
        
        return callTool("todoist_create_task", args)
    }
    
    fun completeTask(taskId: String): Boolean {
        // MCP сервер использует название задачи, а не ID
        // Пока что заглушка
        println("⚠️ Завершение задачи через MCP пока не реализовано")
        return false
    }
    
    fun completeTaskByName(taskName: String): Boolean {
        return callTool("todoist_complete_task", mapOf("task_name" to taskName))
    }
    
    fun updateTask(taskName: String, newContent: String? = null, newDueDate: String? = null, newPriority: Int? = null): Boolean {
        val args = mutableMapOf<String, Any>()
        args["task_name"] = taskName
        
        if (newContent != null) {
            args["content"] = newContent
        }
        if (newDueDate != null) {
            args["due_string"] = newDueDate
        }
        if (newPriority != null) {
            args["priority"] = newPriority
        }
        
        return callTool("todoist_update_task", args)
    }
    
    fun deleteTask(taskName: String): Boolean {
        return callTool("todoist_delete_task", mapOf("task_name" to taskName))
    }
    
    fun isConnected(): Boolean {
        return isConnected
    }
}
