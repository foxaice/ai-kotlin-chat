package job

import main as mainWithParam
import runChat
import mcp.McpManager
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.system.exitProcess

/**
 * Day 9 job с двумя MCP серверами:
 * - every N seconds:
 *   - использует McpManager для координации Todoist и Telegram MCP
 *   - Todoist MCP получает задачи
 *   - Telegram MCP форматирует и отправляет их
 *
 * Default interval: 20 seconds
 *
 * CLI args (all optional):
 *   --intervalSeconds=20
 *   --input=/todoist todayTasks | /mcp-chain
 *   --chatId=12345678
 *   --tgToken=1234:ABCD...
 *   --useMcpChain=true
 */
fun main(args: Array<String>) {
    val opts = args.associateNotNull()
    val intervalSec = opts["intervalSeconds"]?.toIntOrNull() ?: 20
    val input = opts["input"] ?: "/mcp-chain"
    val useMcpChain = opts["useMcpChain"]?.toBoolean() ?: true
    val tgChatId = opts["chatId"] ?: System.getenv("TELEGRAM_CHAT_ID")
    val tgToken = opts["tgToken"] ?: System.getenv("TELEGRAM_BOT_TOKEN")

    System.err.println("[Day9] 🚀 Запуск планировщика...")
    System.err.println("[Day9] ⚙️ Параметры: intervalSeconds=$intervalSec, useMcpChain=$useMcpChain")
    System.err.println("[Day9] 📱 TG Chat ID: ${tgChatId?.take(5)}***")
    System.err.println("[Day9] 🔑 TG Token: ${if (tgToken?.isNotBlank() == true) "установлен" else "НЕ УСТАНОВЛЕН"}")

    if (tgToken.isNullOrBlank() || tgChatId.isNullOrBlank()) {
        System.err.println("[Day9] ❌ TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID not set. Exiting.")
        exitProcess(2)
    }
    if (System.getenv("GEMINI_API_KEY").isNullOrBlank()) {
        System.err.println("[Day9] ❌ GEMINI_API_KEY is not set. Exiting.")
        exitProcess(3)
    }

    val mcpManager = if (useMcpChain) {
        System.err.println("[Day9] 🔗 Инициализация цепочки MCP серверов...")
        McpManager().apply {
            if (!connect()) {
                System.err.println("[Day9] ❌ Не удалось подключиться к MCP серверам")
                exitProcess(4)
            }
        }
    } else {
        null
    }

    System.err.println("[Day9] 🚀 Job started. intervalSeconds=$intervalSec, useMcpChain=$useMcpChain")

    try {
        while (true) {
            try {
                System.err.println("[Day9] 🔄 Начинаю новый цикл...")

                val reply = if (useMcpChain && mcpManager != null) {
                    // Используем цепочку MCP серверов
                    System.err.println("[Day9] 🔗 Запуск цепочки MCP серверов...")
                    val result = mcpManager.getTodayTasksAndSendToTelegram()
                    System.err.println("[Day9] 📤 Результат MCP цепочки: $result")
                    result
                } else {
                    // Используем старый способ через ChatKt
                    System.err.println("[Day9] 💬 Запуск через ChatKt...")
                    val chatReply = mainWithParam(input)
                    if (chatReply.isBlank()) {
                        System.err.println("[Day9] WARN: empty reply from ChatKt")
                        "Нет ответа от ChatKt"
                    } else {
                        System.err.println("[Day9] Got reply (${chatReply.length} chars). Sending to Telegram…")
                        sendTelegramMessage(tgToken, tgChatId, chatReply)
                        System.err.println("[Day9] Sent to Telegram via HTTP.")
                        "Отправлено через HTTP API"
                    }
                }

                System.err.println("[Day9] ✅ Цикл завершен: $reply")
                System.err.println("[Day9] ⏳ Ожидание ${intervalSec} секунд до следующего цикла...")

            } catch (e: Exception) {
                System.err.println("[Day9] ERROR: ${e.message}")
                e.printStackTrace()
            }

            try {
                Thread.sleep((intervalSec * 1000L).coerceAtLeast(5000L))
            } catch (_: InterruptedException) {
                System.err.println("[Day9] 🛑 Получен сигнал остановки")
                break
            }
        }
    } finally {
        mcpManager?.disconnect()
        System.err.println("[Day9] 👋 Job завершен")
    }
}

private const val MAX_TG_MESSAGE = 3900 // keep below Telegram 4096 limit

/**
 * Minimal Telegram sendMessage via HTTPS POST (application/x-www-form-urlencoded).
 * Splits long messages into chunks if needed.
 */
private fun sendTelegramMessage(token: String, chatId: String, text: String) {
    val chunks = text.chunked(MAX_TG_MESSAGE)
    for ((i, chunk) in chunks.withIndex()) {
        val ok = tgPostForm(
            url = "https://api.telegram.org/bot$token/sendMessage",
            form = "chat_id=${enc(chatId)}&text=${enc(chunk)}"
        )
        if (!ok) throw IllegalStateException("Telegram sendMessage failed on part ${i + 1}/${chunks.size}")
        // tiny delay to avoid flood limits
        Thread.sleep(300)
    }
}

private fun tgPostForm(url: String, form: String): Boolean {
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
    // For debugging:
    if (code >= 300) {
        val err = conn.errorStream?.readBytes()?.toString(Charsets.UTF_8)
        System.err.println("[Day8] Telegram HTTP $code: $err")
    }
    conn.inputStream?.close()
    conn.disconnect()
    return code in 200..299
}

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

private fun Array<String>.associateNotNull(): Map<String, String> =
    buildMap {
        for (a in this@associateNotNull) {
            if (!a.startsWith("--")) continue
            val eq = a.indexOf('=')
            if (eq > 2 && eq < a.length - 1) {
                val k = a.substring(2, eq)
                val v = a.substring(eq + 1)
                put(k, v)
            }
        }
    }