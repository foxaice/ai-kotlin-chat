package job

import main
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.system.exitProcess

/**
 * Day 8 job:
 * - every N seconds:
 *   - run job.ChatKt.mainmain and feed "/todoist todayTasks" then "exit"
 *   - parse model reply from stdout
 *   - send the reply to Telegram (BOT token + chatId via env or args)
 *
 * Default interval: 20 seconds
 *
 * CLI args (all optional):
 *   --intervalSeconds=20
 *   --input=/todoist todayTasks
 *   --chatId=12345678
 *   --tgToken=1234:ABCD...
 */
fun main(args: Array<String>) {
    val opts = args.associateNotNull()
    val intervalSec = opts["intervalSeconds"]?.toIntOrNull() ?: 20
    val input = opts["input"] ?: "/todoist todayTasks"
    val tgChatId = opts["chatId"] ?: System.getenv("TELEGRAM_CHAT_ID")
    val tgToken = opts["tgToken"] ?: System.getenv("TELEGRAM_BOT_TOKEN")

    if (tgToken.isNullOrBlank() || tgChatId.isNullOrBlank()) {
        System.err.println("[Day8] TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID not set. Exiting.")
        exitProcess(2)
    }
    if (System.getenv("GEMINI_API_KEY").isNullOrBlank()) {
        System.err.println("[Day8] GEMINI_API_KEY is not set. Exiting.")
        exitProcess(3)
    }

    System.err.println("[Day8] Job started. intervalSeconds=$intervalSec input=\"$input\"")
    while (true) {
        try {
            val reply = main(input)
            if (reply.isBlank()) {
                System.err.println("[Day8] WARN: empty reply from ChatKt")
            } else {
                System.err.println("[Day8] Got reply (${reply.length} chars). Sending to Telegram…")
                sendTelegramMessage(tgToken, tgChatId, reply)
                System.err.println("[Day8] Sent to Telegram.")
            }
        } catch (e: Exception) {
            System.err.println("[Day8] ERROR: ${e.message}")
            e.printStackTrace()
        }
        try {
            Thread.sleep((intervalSec * 1000L).coerceAtLeast(5000L))
        } catch (_: InterruptedException) {
            break
        }
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