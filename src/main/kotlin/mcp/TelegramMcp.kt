package mcp

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class TelegramMcp {
    private var isConnected = false
    private var process: Process? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private val json = jacksonObjectMapper()

    fun connect(telegramServerPath: String = "telegram-mcp-server"): Boolean {
        return try {
            val jsFile = if (telegramServerPath.endsWith(".js")) {
                telegramServerPath
            } else {
                "$telegramServerPath/dist/index.js"
            }

            val processBuilder = ProcessBuilder("node", jsFile)

            // Передаем переменные окружения
            processBuilder.environment().apply {
                put("TELEGRAM_BOT_TOKEN", System.getenv("TELEGRAM_BOT_TOKEN") ?: "")
                put("TELEGRAM_CHAT_ID", System.getenv("TELEGRAM_CHAT_ID") ?: "")
            }

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
                println("✅ Подключен к Telegram MCP серверу")
                true
            } else {
                println("❌ Ошибка инициализации Telegram MCP сервера")
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Ошибка подключения к Telegram MCP серверу: ${e.message}")
            false
        }
    }

    fun disconnect() {
        try {
            isConnected = false
            process?.destroy()
            reader?.close()
            writer?.close()
            println("🔌 Отключен от Telegram MCP сервера")
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
            println("❌ Ошибка получения инструментов Telegram MCP: ${e.message}")
            emptyList()
        }
    }

    fun callTool(toolName: String, arguments: Map<String, Any>): Result {
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
                Result(isSuccessful = !isError, data = response)
            } else {
                Result(isSuccessful = false)
            }
        } catch (e: Exception) {
            println("❌ Ошибка вызова Telegram инструмента $toolName: ${e.message}")
            Result(isSuccessful = false)
        }
    }

    fun sendMessage(message: String, chatId: String? = null, parseMode: String = "HTML"): Boolean {
        val args = mutableMapOf<String, Any>()
        args["message"] = message
        args["parse_mode"] = parseMode

        if (chatId != null) {
            args["chat_id"] = chatId
        }

        return callTool("telegram_send_message", args).isSuccessful
    }

    fun formatTaskList(tasksData: String, formatStyle: String = "emoji"): String {
        val args = mapOf(
            "tasks_data" to tasksData,
            "format_style" to formatStyle
        )

        val result = callTool("telegram_format_task_list", args)
        return if (result.isSuccessful) {
            try {
                val response = result.data?.get("result") as? Map<*, *>
                val content = response?.get("content") as? List<*>
                val textContent = content?.get(0) as? Map<*, *>
                textContent?.get("text") as? String ?: "Ошибка форматирования"
            } catch (e: Exception) {
                "Ошибка извлечения форматированного текста: ${e.message}"
            }
        } else {
            "Ошибка форматирования задач"
        }
    }

    fun sendFormattedTasks(tasksData: String, chatId: String? = null): Boolean {
        val args = mutableMapOf<String, Any>()
        args["tasks_data"] = tasksData

        if (chatId != null) {
            args["chat_id"] = chatId
        }

        return callTool("telegram_send_formatted_tasks", args).isSuccessful
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
            println("❌ Ошибка чтения ответа от Telegram MCP: ${e.message}")
            null
        }
    }

    fun isConnected(): Boolean {
        return isConnected
    }
}
