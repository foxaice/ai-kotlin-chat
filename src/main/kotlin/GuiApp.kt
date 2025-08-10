

import javafx.application.Application
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.web.WebView
import javafx.stage.Stage
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser

private const val MODEL = "gemini-2.5-flash"
private const val INSTRUCTION_FILE = "system_instruction.txt"
private const val HISTORY_FILE = "history_summary.txt"

class GuiApp : Application() {

    private val http = OkHttpClient()
    private val json = jacksonObjectMapper()
    private val parser = Parser.builder().build()
    private val renderer = HtmlRenderer.builder().build()

    private lateinit var historyBox: VBox
    private lateinit var inputField: TextArea
    private lateinit var sendBtn: Button
    private lateinit var newSessionCheck: CheckBox

    private val history = mutableListOf<Msg>()

    private var effectiveSystemInstruction: String = ""

    override fun start(stage: Stage) {
        val apiKey = "AIzaSyD99ReSQGXG5mCnTd3agAzaqnHeGLUlLOU"
        if (apiKey.isNullOrBlank()) {
            showAlert("Не найден GEMINI_API_KEY", "Установите переменную окружения GEMINI_API_KEY и перезапустите.")
            Platform.exit()
            return
        }

        val topBar = HBox(10.0).apply {
            padding = Insets(8.0)
            alignment = Pos.CENTER_LEFT
            newSessionCheck = CheckBox("Новая сессия (игнорировать прошлый пересказ)")
            children.add(newSessionCheck)
        }

        historyBox = VBox(12.0).apply { padding = Insets(12.0) }
        val scroll = ScrollPane(historyBox).apply {
            isFitToWidth = true
            vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
            prefHeight = 560.0
        }

        inputField = TextArea().apply {
            promptText = "Напишите сообщение..."
            isWrapText = true
            prefRowCount = 3
            prefHeight = 90.0
        }

        sendBtn = Button("Отправить").apply {
            setOnAction { onSendClicked() }
        }

        val bottom = HBox(10.0, inputField, sendBtn).apply {
            padding = Insets(8.0)
            HBox.setHgrow(inputField, Priority.ALWAYS)
        }

        val root = BorderPane().apply {
            top = topBar
            center = scroll
            setBottom(bottom)
            padding = Insets(8.0)
        }

        stage.title = "Gemini Kotlin Chat (GUI)"
        stage.scene = Scene(root, 900.0, 720.0)
        stage.show()

        // Build effective system instruction once at startup (considering checkbox state)
        buildEffectiveSystemInstruction(ignorePrev = newSessionCheck.isSelected)
        newSessionCheck.setOnAction {
            buildEffectiveSystemInstruction(ignorePrev = newSessionCheck.isSelected)
        }
    }

    private fun buildEffectiveSystemInstruction(ignorePrev: Boolean) {
        val base = readTextFileOrNull(INSTRUCTION_FILE)
            ?: "Ты — краткий и дружелюбный помощник. Отвечай по-русски."
        val prev = if (ignorePrev) "" else readTextFileOrNull(HISTORY_FILE)?.trim().orEmpty()
        effectiveSystemInstruction = if (prev.isNotEmpty()) {
            "$base\n\nПредыдущая сессия (пересказ):\n$prev"
        } else base
    }

    private fun onSendClicked() {
        val text = inputField.text.trim()
        if (text.isEmpty()) return
        inputField.clear()

        appendUserMessage(text)

        sendBtn.isDisable = true
        Thread {
            try {
                val reply = callGeminiChat(effectiveSystemInstruction, history)
                Platform.runLater {
                    appendAssistantMessage(reply)
                }
                // Update history summary after each full turn
                val summary = summarizeHistory(effectiveSystemInstruction, history)
                writeTextFile(HISTORY_FILE, summary)
            } catch (e: Exception) {
                Platform.runLater {
                    appendAssistantMessage("Ошибка: ${e.message}")
                }
            } finally {
                Platform.runLater { sendBtn.isDisable = false }
            }
        }.start()
    }

    private fun appendUserMessage(text: String) {
        history += Msg("user", text)
        val bubble = VBox(4.0).apply {
            style = "-fx-background-color: #e8f0fe; -fx-padding: 8; -fx-background-radius: 12;"
            children += Label("Вы:").apply { style = "-fx-font-weight: bold;" }
            children += Label(text).apply { isWrapText = true }
        }
        val row = HBox(bubble).apply {
            alignment = Pos.CENTER_RIGHT
            padding = Insets(2.0, 0.0, 2.0, 0.0)
        }
        historyBox.children += row
        Platform.runLater { historyBox.scene?.window?.sizeToScene() }
    }

    private fun appendAssistantMessage(markdown: String) {
        history += Msg("model", markdown)
        val web = WebView().apply {
            prefHeight = 100.0
            prefWidth = 800.0
        }
        val html = markdownToHtml(markdown)
        web.engine.loadContent(html, "text/html")
        val bubble = VBox(4.0).apply {
            style = "-fx-background-color: #f6f6f6; -fx-padding: 8; -fx-background-radius: 12;"
            children += Label("Модель:").apply { style = "-fx-font-weight: bold;" }
            children += web
        }
        val row = HBox(bubble).apply {
            alignment = Pos.CENTER_LEFT
            padding = Insets(2.0, 0.0, 2.0, 0.0)
        }
        historyBox.children += row
        Platform.runLater {
            (historyBox.parent as? ScrollPane)?.vvalue = 1.0
        }
    }

    private fun markdownToHtml(md: String): String {
        val document = parser.parse(md)
        val body = renderer.render(document)
        val css = (
            "<style>" +
            "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Noto Sans', 'Helvetica Neue', Arial, sans-serif; padding: 4px 8px; }" +
            "pre, code { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }" +
            "pre { background: #111; color: #f5f5f5; padding: 12px; border-radius: 10px; overflow-x: auto; }" +
            "code { background: #f0f0f0; padding: 2px 4px; border-radius: 6px; }" +
            "h1,h2,h3 { margin-top: 12px; }" +
            "table { border-collapse: collapse; }" +
            "td, th { border: 1px solid #ddd; padding: 6px 8px; }" +
            "blockquote { border-left: 3px solid #ddd; padding-left: 10px; color: #555; }" +
            "ul { margin-left: 18px; }" +
            "</style>"
        )
        return "<html><head>$css</head><body>$body</body></html>"
    }

    private fun callGeminiChat(systemInstruction: String, history: List<Msg>): String {
        val apiKey = System.getenv("GEMINI_API_KEY") ?: error("Missing GEMINI_API_KEY")
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"

        val contents = history.map { m ->
            mapOf("role" to m.role, "parts" to listOf(mapOf("text" to m.content)))
        }
        val bodyMap = mapOf(
            "systemInstruction" to mapOf("parts" to listOf(mapOf("text" to systemInstruction))),
            "contents" to contents
        )
        val reqBody = jacksonObjectMapper().writeValueAsString(bodyMap).toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(url).post(reqBody).header("Content-Type", "application/json").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty()
                error("HTTP ${resp.code} - $err")
            }
            val txt = resp.body?.string().orEmpty()
            val tree = jacksonObjectMapper().readTree(txt)
            return tree["candidates"]?.get(0)
                ?.get("content")?.get("parts")?.get(0)?.get("text")
                ?.asText()?.trim().orEmpty()
        }
    }

    private fun summarizeHistory(systemInstruction: String, history: List<Msg>): String {
        val apiKey = System.getenv("GEMINI_API_KEY") ?: error("Missing GEMINI_API_KEY")
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"

        val transcript = buildString {
            history.forEach { m ->
                append(if (m.role == "user") "Пользователь: " else "Модель: ")
                append(m.content.replace("\n", " ").trim())
                append("\n")
            }
        }.trim()

        val summarizerPrompt = (
            "Суммируй диалог кратко и точно, сохраняя ключевые факты, цели, решения и открытые вопросы.\n" +
            "Формат: 1-2 абзаца, максимум ~200-250 слов, без лишней воды.\n" +
            "Исходный диалог:\n" + transcript
        )

        val bodyMap = mapOf(
            "systemInstruction" to mapOf("parts" to listOf(mapOf("text" to systemInstruction))),
            "contents" to listOf(
                mapOf("role" to "user", "parts" to listOf(mapOf("text" to summarizerPrompt)))
            )
        )
        val reqBody = jacksonObjectMapper().writeValueAsString(bodyMap).toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(url).post(reqBody).header("Content-Type", "application/json").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty()
                return transcript.take(5000)
            }
            val txt = resp.body?.string().orEmpty()
            val tree = jacksonObjectMapper().readTree(txt)
            val summary = tree["candidates"]?.get(0)
                ?.get("content")?.get("parts")?.get(0)?.get("text")
                ?.asText()?.trim().orEmpty()
            return if (summary.isNotBlank()) summary else transcript.take(5000)
        }
    }

    private fun readTextFileOrNull(path: String): String? {
        return try {
            val p = Paths.get(path)
            if (Files.exists(p)) Files.readString(p, StandardCharsets.UTF_8) else null
        } catch (_: Exception) { null }
    }

    private fun writeTextFile(path: String, text: String) {
        try {
            val p = Paths.get(path)
            Files.writeString(p, text, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            System.err.println("Не удалось записать %s: %s".format(path, e.message))
        }
    }

    private fun showAlert(title: String, msg: String) {
        val alert = Alert(Alert.AlertType.ERROR)
        alert.title = title
        alert.headerText = null
        alert.contentText = msg
        alert.showAndWait()
    }
}

fun main() {
    Application.launch(GuiApp::class.java)
}
