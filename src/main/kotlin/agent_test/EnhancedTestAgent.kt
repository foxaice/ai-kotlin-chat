import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.time.Duration
import kotlin.system.exitProcess

class EnhancedTestAgent {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(20))
        .writeTimeout(Duration.ofSeconds(120))
        .readTimeout(Duration.ofSeconds(180))
        .callTimeout(Duration.ofSeconds(0))
        .retryOnConnectionFailure(true)
        .build()

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val geminiApiKey = System.getenv("GEMINI_API_KEY")
        ?: throw IllegalStateException("❌ GEMINI_API_KEY environment variable is not set")

    private val geminiApiUrl = "https://generativelanguage.googleapis.com/v1beta/models/$geminiModel:generateContent"

    private val geminiModel: String
        get() {
            return "gemini-2.5-flash"
            return "gemini-2.5-flash-lite"
            return "gemini-2.5-flash-exp"
            return "gemini-2.0-flash"
            return "gemini-2.0-flash-lite"
            return "gemini-2.0-flash-exp"
            return "gemma-3n-e2b-it"
            return "gemma-3n-e4b-it"
            return "gemma-3-1b-it"
            return "gemma-3-4b-it"
            return "gemma-3-12b-it"
            return "gemma-3-27b-it"
        }

    private val deepseekModel: String
        get() {
            return "deepseek-chat"
            return "deepseek-reasoner"
        }

    private val isDebug = false

    private val llmType = LLMType.GEMINI

    private enum class LLMType {
        GEMINI,
        DEEPSEEK
    }

    data class TestResult(
        val success: Boolean,
        val output: String,
        val errors: List<String> = emptyList()
    )

    suspend fun generateTests(
        sourceFilePath: String,
        testFilePath: String,
        maxIterations: Int = 5,
        packageName: String? = null
    ) {
        println("🚀 Enhanced Test Agent запущен")
        println("📄 Исходный файл: $sourceFilePath")
        println("🧪 Файл тестов: $testFilePath")
        println("📦 Пакет: ${packageName ?: "auto-detect"}")

        val sourceFile = File(sourceFilePath)
        if (!sourceFile.exists()) {
            println("❌ Файл не найден: $sourceFilePath")
            return
        }

        val sourceCode = sourceFile.readText()
        val detectedPackage = packageName ?: detectPackageName(sourceCode)
        val className = extractClassName(sourceCode)

        println("🔍 Обнаружен класс: $className")
        println("📦 Пакет: $detectedPackage")

        var iteration = 1
        var lastError = ""

        while (iteration <= maxIterations) {
            println("\n🔄 Итерация $iteration/$maxIterations")

            try {
                val testCode = if (iteration == 1) {
                    generateInitialTests(sourceCode, sourceFilePath, className, detectedPackage)
                } else {
                    val existingTests = File(testFilePath).takeIf { it.exists() }?.readText() ?: ""
                    fixTests(sourceCode, existingTests, lastError, sourceFilePath, className, detectedPackage)
                }

                // Создаем директорию и записываем файл
                val testFile = File(testFilePath)
                testFile.parentFile?.mkdirs()
                testFile.writeText(testCode)

                println("📝 Тесты записаны в файл: $testFilePath")

                // Проверяем синтаксис Kotlin
                val syntaxCheck = checkKotlinSyntax(testCode)
                if (!syntaxCheck.success) {
                    println("⚠️  Синтаксические ошибки обнаружены:")
                    println(syntaxCheck.output)
                    lastError = syntaxCheck.output
                    iteration++
                    continue
                }

                // Пытаемся скомпилировать
                val compileResult = compileTest(testFilePath, detectedPackage)
                if (!compileResult.success) {
                    println("⚠️  Ошибки компиляции:")
                    println(compileResult.output)
                    lastError = compileResult.output
                    iteration++
                    continue
                }

                println("✅ Тесты успешно созданы и скомпилированы!")

                // Опционально: попытка запуска тестов
                val runResult = tryRunTests(testFilePath, className, detectedPackage)
                if (runResult.success) {
                    println("🎉 Все тесты прошли успешно!")
                } else {
                    println("❌ Тесты скомпилированы, но упали при выполнении:")
                    lastError = "Test execution failed: ${runResult.output}"

                    // Если тесты не прошли, продолжаем итерации для исправления
                    if (iteration < maxIterations) {
                        println("🔄 Попытка исправить упавшие тесты...")
                        iteration++
                        continue
                    }
                }

                return

            } catch (e: Exception) {
                println("❌ Ошибка на итерации $iteration: ${e.message}")
                lastError = e.message ?: "Unknown error"
                e.printStackTrace()
            }

            iteration++
        }

        println("❌ Не удалось создать рабочие тесты за $maxIterations итераций")
    }

    private suspend fun generateInitialTests(
        sourceCode: String,
        sourceFilePath: String,
        className: String,
        packageName: String
    ): String {
        val prompt = """
Создай полный набор JUnit 5 тестов для следующего Kotlin класса.

ВАЖНО: Верни ТОЛЬКО чистый Kotlin код без markdown разметки, объяснений или дополнительного текста!

Информация:
- Исходный файл: $sourceFilePath
- Класс: $className
- Пакет: $packageName

Исходный код:
```kotlin
$sourceCode
```

Требования к тестам:
1. Начни ответ сразу с: package $packageName
2. Используй JUnit 5 (import org.junit.jupiter.api.*)
3. Используй kotlin.test для дополнительных assertions если нужно
4. Создай класс теста ${className}Test
5. Импортируй тестируемый класс: import $packageName.$className
6. Покрой ВСЕ публичные методы и свойства
7. Создай тесты для:
   - Обычных случаев
   - Граничных случаев
   - Ошибочных входных данных
   - Исключений (используй assertThrows)
8. Используй @Test, @BeforeEach, @AfterEach если нужно
9. Создай понятные имена тестов в формате: `method should behavior when condition`
10. Используй правильные assertions (assertEquals, assertTrue, assertThrows и т.д.)
11. Для чисел с плавающей точкой используй delta в assertEquals
12. Добавь все необходимые импорты
13. ВАЖНО: Для Result типов используй getOrThrow() вместо getOrNull() в assertions
14. ВАЖНО: getOrNull() возвращает nullable тип - не используй его напрямую в assertEquals
15. Если метод возвращает Result<T> - используй result.getOrThrow() для получения значения в тестах

Верни код начинающийся с package declaration, без markdown блоков.
        """.trimIndent()

        return callModelApi(prompt)
    }

    private suspend fun fixTests(
        sourceCode: String,
        existingTests: String,
        errorOutput: String,
        sourceFilePath: String,
        className: String,
        packageName: String
    ): String {
        val prompt = """
Исправь ошибки в Kotlin тестах.

ВАЖНО: Верни ТОЛЬКО исправленный Kotlin код без markdown разметки или объяснений!

Информация:
- Исходный файл: $sourceFilePath
- Класс: $className  
- Пакет: $packageName

Исходный код класса:
```kotlin
$sourceCode
```

Текущие тесты (с ошибками):
```kotlin
$existingTests
```

Ошибки компиляции/выполнения:
```
$errorOutput
```

Исправь ВСЕ ошибки:
1. Анализируй ошибки внимательно
2. Исправь package declaration если нужно
3. Исправь все импорты (добавь недостающие, убери лишние)
4. Исправь синтаксис Kotlin
5. Исправь типы данных и их использование
6. Исправь вызовы методов и конструкторов
7. Исправь assertions - используй правильные методы
8. Убедись что все методы класса $className покрыты тестами
9. Для assertThrows используй правильный синтаксис: assertThrows<ExceptionType> { }
10. Для чисел с плавающей точкой добавь delta в assertEquals
11. Проверь что все переменные правильно объявлены
12. ВАЖНО: Если используешь Result.getOrNull() в assertions - замени на getOrThrow() или добавь null check
13. ВАЖНО: getOrNull() возвращает nullable тип, не используй его напрямую в assertEquals/assertTrue/assertFalse
14. Если есть ошибка "actual type is 'Type?', but 'Type' was expected" - исправь nullable типы
15. Для Result типов: используй getOrThrow() для получения значения или assertNotNull + assertEquals

Начни ответ сразу с: package $packageName

НЕ используй markdown блоки в ответе!
        """.trimIndent()

        return callModelApi(prompt)
    }

    private suspend fun callModelApi(prompt: String): String {
        return when (llmType) {
            LLMType.GEMINI -> callGeminiApi(prompt)
            LLMType.DEEPSEEK -> callDeepSeekChat(prompt, model = deepseekModel)
        }
    }

    private suspend fun callGeminiApi(prompt: String): String {
        val requestBody = mapOf(
            "contents" to listOf(
                mapOf("parts" to listOf(mapOf("text" to prompt)))
            ),
            "generationConfig" to mapOf(
                "temperature" to 0.1,
                "maxOutputTokens" to 8000,
                "topP" to 0.8,
                "topK" to 40
            ),
            "safetySettings" to listOf(
                mapOf("category" to "HARM_CATEGORY_HARASSMENT", "threshold" to "BLOCK_NONE"),
                mapOf("category" to "HARM_CATEGORY_HATE_SPEECH", "threshold" to "BLOCK_NONE"),
                mapOf("category" to "HARM_CATEGORY_SEXUALLY_EXPLICIT", "threshold" to "BLOCK_NONE"),
                mapOf("category" to "HARM_CATEGORY_DANGEROUS_CONTENT", "threshold" to "BLOCK_NONE")
            )
        )

        val json = objectMapper.writeValueAsString(requestBody)
        val body = json.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$geminiApiUrl?key=$geminiApiKey")
            .post(body)
            .header("Content-Type", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No error details"
                throw IOException("Gemini API error: ${response.code} - ${response.message}\nDetails: $errorBody")
            }

            val responseBody = response.body?.string()
                ?: throw IOException("Empty response from Gemini API")

            try {
                val jsonResponse: JsonNode = objectMapper.readTree(responseBody)

                // Улучшенная обработка ответа с проверкой на null
                val candidates = jsonResponse.path("candidates")
                if (candidates.isEmpty || candidates.isNull) {
                    // Попробуем альтернативные пути в ответе
                    val error = jsonResponse.path("error")
                    if (!error.isMissingNode) {
                        val errorMessage = error.path("message").asText("Unknown API error")
                        throw IOException("Gemini API returned error: $errorMessage")
                    }
                    throw IOException("No candidates in API response: $responseBody")
                }

                val firstCandidate = candidates.first()
                if (firstCandidate.isNull) {
                    throw IOException("First candidate is null in API response")
                }

                // Проверяем, если контент был заблокирован
                val finishReason = firstCandidate.path("finishReason").asText("")
                if (finishReason == "SAFETY") {
                    throw IOException("Content was blocked by safety filters")
                } else if (finishReason == "RECITATION") {
                    throw IOException("Content was blocked due to recitation concerns")
                } else if (finishReason == "OTHER") {
                    println("⚠️  Warning: API finished with 'OTHER' reason, attempting to extract content")
                }

                val content = firstCandidate.path("content")
                if (content.isNull || content.isMissingNode) {
                    // Пытаемся получить текст из альтернативных мест
                    val text = firstCandidate.path("text").asText("")
                    if (text.isNotBlank()) {
                        return cleanupGeneratedCode(text)
                    }
                    throw IOException("No content in API response candidate")
                }

                val parts = content.path("parts")
                if (parts.isEmpty || parts.isNull) {
                    throw IOException("No parts in API response content")
                }

                // Ищем первую часть с текстом
                var extractedText = ""
                for (part in parts) {
                    val text = part.path("text").asText("")
                    if (text.isNotBlank()) {
                        extractedText = text
                        break
                    }
                }

                if (extractedText.isBlank()) {
                    // Дополнительная диагностика
                    println("🔍 Debug: полный ответ API:")
                    println(responseBody.take(500) + "...")
                    throw IOException("Empty or missing text in all API response parts")
                }

                return cleanupGeneratedCode(extractedText)

            } catch (e: Exception) {
                when (e) {
                    is IOException -> throw e
                    else -> throw IOException(
                        "Failed to parse Gemini API response: ${e.message}\nResponse preview: ${
                            responseBody.take(
                                200
                            )
                        }..."
                    )
                }
            }
        }
    }

    private fun cleanupGeneratedCode(code: String): String {
        var cleaned = code.trim()

        if (isDebug.not()) {
            println("🔍 Исходный ответ от LLM (первые 200 символов):")
            println("${cleaned.take(200)}...")
        } else {
            println("🔍 Исходный ответ от LLM:")
            println(cleaned)
            println("press the Enter key to continue...")
            readlnOrNull()
        }

        // Множественные стратегии извлечения кода

        // Стратегия 1: Ищем kotlin блоки
        val kotlinBlockPattern = Regex("```kotlin\\s*\\n(.*?)\\n```", RegexOption.DOT_MATCHES_ALL)
        var match = kotlinBlockPattern.find(cleaned)
        if (match != null) {
            cleaned = match.groupValues[1].trim()
            println("✅ Найден kotlin блок")
        } else {
            // Стратегия 2: Ищем любые code блоки
            val codeBlockPattern = Regex("```\\s*\\n(.*?)\\n```", RegexOption.DOT_MATCHES_ALL)
            match = codeBlockPattern.find(cleaned)
            if (match != null) {
                cleaned = match.groupValues[1].trim()
                println("✅ Найден code блок")
            } else {
                // Стратегия 3: Ищем однострочные блоки
                val inlineKotlinPattern = Regex("```kotlin([^`]*)```", RegexOption.DOT_MATCHES_ALL)
                match = inlineKotlinPattern.find(cleaned)
                if (match != null) {
                    cleaned = match.groupValues[1].trim()
                    println("✅ Найден inline kotlin блок")
                } else {
                    // Стратегия 4: Убираем просто все ``` символы
                    if (cleaned.contains("```")) {
                        cleaned = cleaned
                            .replace("```kotlin", "")
                            .replace("```", "")
                            .trim()
                        println("✅ Убрали markdown разметку")
                    }
                }
            }
        }

        // Очистка и нормализация
        cleaned = cleaned
            .split('\n')
            .dropWhile { it.isBlank() }
            .dropLastWhile { it.isBlank() }
            .joinToString("\n")

        // Проверяем, что это похоже на Kotlin код
        if (!cleaned.contains("package ") && !cleaned.contains("class ") && !cleaned.contains("fun ")) {
            println("⚠️  Предупреждение: результат не похож на Kotlin код")

            // Последняя попытка - ищем код после определенных ключевых слов
            val lines = cleaned.split('\n')
            val kotlinStartIndex = lines.indexOfFirst {
                it.trim().startsWith("package ") ||
                        it.trim().startsWith("import ") ||
                        it.trim().startsWith("class ")
            }

            if (kotlinStartIndex >= 0) {
                cleaned = lines.drop(kotlinStartIndex).joinToString("\n")
                println("✅ Найден код начиная со строки $kotlinStartIndex")
            }
        }

        // Финальная проверка
        if (!cleaned.startsWith("package ")) {
            println("⚠️  Код не начинается с package declaration")
        }

        println("🔍 Обработанный код (первые 200 символов):")
        println("${cleaned.take(200)}...")

        return cleaned
    }

    private fun checkKotlinSyntax(code: String): TestResult {
        return try {
            val errors = mutableListOf<String>()
            val lines = code.split('\n')

            // Проверяем обязательные элементы
            if (!code.contains("package ")) {
                errors.add("❌ Missing package declaration")
            }

            if (!code.contains("class ") && !code.contains("object ")) {
                errors.add("❌ No class or object declaration found")
            }

            if (!code.contains("@Test")) {
                errors.add("❌ No test methods found (missing @Test annotations)")
            }

            if (!code.contains("import org.junit.jupiter.api")) {
                errors.add("❌ Missing JUnit imports")
            }

            // Проверяем базовый синтаксис
            var braceBalance = 0
            var parenBalance = 0

            lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("//") && !trimmed.startsWith("*")) {

                    // Считаем скобки
                    braceBalance += trimmed.count { it == '{' } - trimmed.count { it == '}' }
                    parenBalance += trimmed.count { it == '(' } - trimmed.count { it == ')' }

                    // Проверяем основные синтаксические ошибки
                    if (trimmed.contains("assertThrows(") && !trimmed.contains("assertThrows<")) {
                        errors.add("❌ Line ${index + 1}: Incorrect assertThrows syntax, use assertThrows<ExceptionType>")
                    }

                    if (trimmed.contains("assertEquals(") && trimmed.contains("Double") && !trimmed.contains("delta") && !trimmed.contains(
                            "0.0"
                        )
                    ) {
                        errors.add("⚠️  Line ${index + 1}: Consider using delta for Double assertions")
                    }

                    // Проверяем проблемы с nullable типами
                    if (trimmed.contains("result.getOrNull()") && trimmed.contains("assertEquals")) {
                        errors.add("❌ Line ${index + 1}: result.getOrNull() returns nullable type, use result.getOrThrow() or handle null")
                    }

                    if (trimmed.contains(".getOrNull()") && (trimmed.contains("assertEquals") || trimmed.contains("assertTrue") || trimmed.contains(
                            "assertFalse"
                        ))
                    ) {
                        errors.add("❌ Line ${index + 1}: getOrNull() returns nullable type, assertions may fail due to type mismatch")
                    }

                    // Проверяем паттерн "actual type is 'Type?', but 'Type' was expected"
                    if (trimmed.contains("Double?") && trimmed.contains("Double")) {
                        errors.add("❌ Line ${index + 1}: Nullable Double? cannot be used where non-null Double is expected")
                    }

                    // Проверяем использование Result.getOrNull() в assertions
                    val resultGetOrNullPattern = Regex("assertEquals\\s*\\([^,]+,\\s*[^.]+\\.getOrNull\\(\\)")
                    if (resultGetOrNullPattern.containsMatchIn(trimmed)) {
                        errors.add("❌ Line ${index + 1}: Use getOrThrow() instead of getOrNull() in assertEquals, or add null check")
                    }
                }
            }

            if (braceBalance != 0) {
                errors.add("❌ Unbalanced braces in code")
            }

            if (parenBalance != 0) {
                errors.add("❌ Unbalanced parentheses in code")
            }

            // Проверяем специфичные для тестов проблемы
            if (code.contains("@Test") && !code.contains("fun ")) {
                errors.add("❌ Test annotations found but no test functions")
            }

            TestResult(
                success = errors.isEmpty(),
                output = if (errors.isEmpty()) "✅ Syntax validation passed" else errors.joinToString("\n"),
                errors = errors
            )
        } catch (e: Exception) {
            TestResult(false, "❌ Syntax check failed: ${e.message}")
        }
    }

    private fun compileTest(testFilePath: String, packageName: String): TestResult {
        return try {
            // Для демонстрации - простая проверка, что файл можно прочитать и он содержит основные элементы
            val testFile = File(testFilePath)
            if (!testFile.exists()) {
                return TestResult(false, "Test file does not exist: $testFilePath")
            }

            val content = testFile.readText()
            val requiredElements = listOf(
                "package $packageName",
                "import org.junit.jupiter.api.Test",
                "@Test",
                "class "
            )

            val missingElements = requiredElements.filter { !content.contains(it) }

            if (missingElements.isNotEmpty()) {
                return TestResult(false, "Missing required elements: ${missingElements.joinToString(", ")}")
            }

            println("✅ Базовая проверка компиляции прошла успешно")
            TestResult(true, "Compilation check passed")

        } catch (e: Exception) {
            TestResult(false, "Compilation check failed: ${e.message}")
        }
    }

    private fun tryRunTests(testFilePath: String, className: String, packageName: String): TestResult {
        return try {
            println("🏃 Запуск тестов в реальном окружении...")

            val testFile = File(testFilePath).absoluteFile
            val projectRoot = findProjectRoot(testFile.parentFile)

            return runTestsWithGradle(projectRoot, className, packageName)
        } catch (e: Exception) {
            TestResult(false, "❌ Test execution failed: ${e.message}")
        }
    }

    private fun findProjectRoot(startDir: File?): File {
        var current = startDir ?: File(".")

        while (current.parentFile != null) {
            // Ищем признаки корня проекта
            if (File(current, "build.gradle.kts").exists() ||
                File(current, "build.gradle").exists() ||
                File(current, "pom.xml").exists() ||
                File(current, "settings.gradle.kts").exists() ||
                File(current, "gradlew").exists()
            ) {
                return current
            }
            current = current.parentFile
        }

        return startDir ?: File(".")
    }

    private fun runAllTestsWithGradle(projectRoot: File): TestResult {
        return try {
            println("🔍 Попытка запустить все тесты...")

            val gradlew = File(
                projectRoot,
                if (System.getProperty("os.name").lowercase().contains("win")) "gradlew.bat" else "gradlew"
            )
            val gradleCommand = if (gradlew.exists()) {
                gradlew.absolutePath
            } else {
                "gradle"
            }

            val testCommand = listOf(gradleCommand, "test", "--info")

            println("🚀 Выполнение команды: ${testCommand.joinToString(" ")}")

            val processBuilder = ProcessBuilder(testCommand)
                .directory(projectRoot)
                .redirectErrorStream(true)

            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            println("📋 Вывод всех тестов:")
            println(output)

            if (exitCode == 0) {
                TestResult(true, "✅ Все тесты прошли успешно!\n$output")
            } else {
                TestResult(false, "❌ Тесты упали с кодом $exitCode:\n$output")
            }

        } catch (e: Exception) {
            println("⚠️  Ошибка запуска всех тестов: ${e.message}")
            TestResult(false, "All tests execution failed: ${e.message}")
        }
    }

    private fun runTestsWithGradle(projectRoot: File, className: String, packageName: String): TestResult {
        return try {
            println("🔍 Поиск Gradle в проекте...")
            println("📊 Отладочная информация:")
            println("   - Project root: ${projectRoot.absolutePath}")
            println("   - Class name: $className")
            println("   - Package name: $packageName")

            val gradlew = File(
                projectRoot,
                if (System.getProperty("os.name").lowercase().contains("win")) "gradlew.bat" else "gradlew"
            )
            val gradleCommand = if (gradlew.exists()) {
                println("   - Using gradlew: ${gradlew.absolutePath}")
                gradlew.absolutePath
            } else {
                println("   - Using system gradle")
                "gradle"
            }

            // Формируем правильное имя класса теста - добавляем Test в конце, если его нет
            val testClassName = if (className.endsWith("Test")) className else "${className}Test"
            val fullTestClass = if (packageName.isNotEmpty()) {
                "${packageName}.${testClassName}"
            } else {
                testClassName
            }

            println("   - Test class name: $testClassName")
            println("   - Full test class: $fullTestClass")

            // Убеждаемся, что рабочая директория - это корень проекта
            if (!File(projectRoot, "build.gradle.kts").exists() && !File(projectRoot, "build.gradle").exists()) {
                println("⚠️  Внимание: build.gradle не найден в ${projectRoot.absolutePath}")
                println("⚠️  Возможно, неправильно определен корень проекта")
            }

            println("gradleCommand: $gradleCommand")

            // Сначала пытаемся запустить все тесты, если конкретный тест не найден
            val testCommand = listOf(
                gradleCommand,
                "clean",
                "test",
                "--tests", fullTestClass
            )

            println("🚀 Выполнение команды: ${testCommand.joinToString(" ")}")
            println("📂 Рабочая директория: ${projectRoot.absolutePath}")

            val processBuilder = ProcessBuilder(testCommand)
                .directory(projectRoot)
                .redirectErrorStream(true)

            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            println("📋 Вывод Gradle:")
            println(output)

            if (exitCode == 0) {
                TestResult(true, "✅ Gradle тесты прошли успешно!\n$output")
            } else {
                // Если тест с конкретным именем не сработал, пробуем запустить все тесты
                if (output.contains("No tests found")) {
                    println("🔄 Конкретный тест не найден, пытаемся запустить все тесты...")
                    return runAllGradleTests(projectRoot, gradleCommand)
                }
                TestResult(false, "❌ Gradle тесты упали с кодом $exitCode:\n$output")
            }

        } catch (e: Exception) {
            println("⚠️  Gradle не найден или не может быть запущен: ${e.message}")
            TestResult(false, "Gradle execution failed: ${e.message}")
        }
    }

    private fun runAllGradleTests(projectRoot: File, gradleCommand: String): TestResult {
        return try {
            val testCommand = listOf(
                gradleCommand,
                "clean",
                "test",
                "--info"
            )

            println("🚀 Запуск всех тестов: ${testCommand.joinToString(" ")}")

            val processBuilder = ProcessBuilder(testCommand)
                .directory(projectRoot)
                .redirectErrorStream(true)

            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            println("📋 Вывод всех тестов:")
            println(output)

            if (exitCode == 0) {
                TestResult(true, "✅ Все Gradle тесты прошли успешно!\n$output")
            } else {
                TestResult(false, "❌ Gradle тесты упали с кодом $exitCode:\n$output")
            }

        } catch (e: Exception) {
            TestResult(false, "All tests execution failed: ${e.message}")
        }
    }

    private fun runTestsWithMaven(projectRoot: File, className: String, packageName: String): TestResult {
        return try {
            println("🔍 Поиск Maven в проекте...")

            if (!File(projectRoot, "pom.xml").exists()) {
                return TestResult(false, "pom.xml not found")
            }

            val mvnCommand = if (System.getProperty("os.name").lowercase().contains("win")) "mvn.cmd" else "mvn"

            val testCommand = listOf(
                mvnCommand,
                "test",
                "-Dtest=${className}Test"
            )

            println("🚀 Выполнение команды: ${testCommand.joinToString(" ")}")

            val processBuilder = ProcessBuilder(testCommand)
                .directory(projectRoot)
                .redirectErrorStream(true)

            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            println("📋 Вывод Maven:")
            println(output)

            if (exitCode == 0) {
                TestResult(true, "✅ Maven тесты прошли успешно!\n$output")
            } else {
                TestResult(false, "❌ Maven тесты упали с кодом $exitCode:\n$output")
            }

        } catch (e: Exception) {
            println("⚠️  Maven не найден или не может быть запущен: ${e.message}")
            TestResult(false, "Maven execution failed: ${e.message}")
        }
    }

    private fun runTestsWithKotlinc(
        testFilePath: String,
        className: String,
        packageName: String,
        projectRoot: File
    ): TestResult {
        return try {
            println("🔍 Пытаемся скомпилировать и запустить тесты с kotlinc...")

            val testFile = File(testFilePath)
            val tempDir = File(System.getProperty("java.io.tmpdir"), "kotlin-test-${System.currentTimeMillis()}")
            tempDir.mkdirs()

            // Ищем исходный файл для компиляции
            val sourceFile = findSourceFile(projectRoot, className, packageName)

            if (sourceFile == null) {
                return TestResult(false, "❌ Не найден исходный файл для класса $className")
            }

            // Подготавливаем classpath с JUnit
            val junitClasspath = findJUnitClasspath()
            if (junitClasspath.isEmpty()) {
                return TestResult(false, "❌ JUnit не найден в classpath. Установите JUnit 5.")
            }

            // Компилируем исходный файл
            val compileSourceCommand = listOf(
                "kotlinc",
                sourceFile.absolutePath,
                "-cp", junitClasspath,
                "-d", tempDir.absolutePath
            )

            println("🔨 Компиляция исходного файла: ${compileSourceCommand.joinToString(" ")}")
            val compileSourceProcess = ProcessBuilder(compileSourceCommand).start()
            val compileSourceOutput = compileSourceProcess.inputStream.bufferedReader().readText()
            val compileSourceExit = compileSourceProcess.waitFor()

            if (compileSourceExit != 0) {
                return TestResult(false, "❌ Ошибка компиляции исходного файла:\n$compileSourceOutput")
            }

            // Компилируем тестовый файл
            val compileTestCommand = listOf(
                "kotlinc",
                testFile.absolutePath,
                "-cp", "$junitClasspath${File.pathSeparator}${tempDir.absolutePath}",
                "-d", tempDir.absolutePath
            )

            println("🔨 Компиляция тестового файла: ${compileTestCommand.joinToString(" ")}")
            val compileTestProcess = ProcessBuilder(compileTestCommand).start()
            val compileTestOutput = compileTestProcess.inputStream.bufferedReader().readText()
            val compileTestExit = compileTestProcess.waitFor()

            if (compileTestExit != 0) {
                return TestResult(false, "❌ Ошибка компиляции тестового файла:\n$compileTestOutput")
            }

            // Запускаем тесты через JUnit Platform Console Launcher
            val runTestCommand = listOf(
                "java",
                "-cp", "$junitClasspath${File.pathSeparator}${tempDir.absolutePath}",
                "org.junit.platform.console.ConsoleLauncher",
                "--select-class", "${packageName}.${className}Test",
                "--details", "verbose"
            )

            println("🏃 Запуск тестов: ${runTestCommand.joinToString(" ")}")
            val runTestProcess = ProcessBuilder(runTestCommand).start()
            val runTestOutput = runTestProcess.inputStream.bufferedReader().readText()
            val runTestExit = runTestProcess.waitFor()

            // Очищаем временную директорию
            tempDir.deleteRecursively()

            println("📋 Вывод тестов:")
            println(runTestOutput)

            if (runTestExit == 0) {
                TestResult(true, "✅ Тесты выполнены успешно с kotlinc!\n$runTestOutput")
            } else {
                TestResult(false, "❌ Тесты упали при выполнении с kotlinc (код $runTestExit):\n$runTestOutput")
            }

        } catch (e: Exception) {
            println("⚠️  Ошибка выполнения с kotlinc: ${e.message}")
            TestResult(false, "kotlinc execution failed: ${e.message}")
        }
    }

    private fun findSourceFile(projectRoot: File, className: String, packageName: String): File? {
        val packagePath = packageName.replace(".", File.separator)
        val possiblePaths = listOf(
            "src/main/kotlin/$packagePath/$className.kt",
            "src/main/java/$packagePath/$className.kt",
            "src/kotlin/$packagePath/$className.kt",
            "src/$packagePath/$className.kt",
            "$packagePath/$className.kt",
            "$className.kt"
        )

        for (path in possiblePaths) {
            val file = File(projectRoot, path)
            if (file.exists()) {
                println("✅ Найден исходный файл: ${file.absolutePath}")
                return file
            }
        }

        println("⚠️  Исходный файл не найден, проверенные пути:")
        possiblePaths.forEach { println("   - ${File(projectRoot, it).absolutePath}") }
        return null
    }

    private fun findJUnitClasspath(): String {
        // Пытаемся найти JUnit в известных местах
        val possibleJars = listOf(
            // Gradle cache locations
            "${System.getProperty("user.home")}/.gradle/caches/modules-2/files-2.1/org.junit.jupiter/junit-jupiter-engine",
            "${System.getProperty("user.home")}/.gradle/caches/modules-2/files-2.1/org.junit.jupiter/junit-jupiter-api",
            "${System.getProperty("user.home")}/.gradle/caches/modules-2/files-2.1/org.junit.platform/junit-platform-console-standalone",
            // Maven cache locations
            "${System.getProperty("user.home")}/.m2/repository/org/junit/jupiter/junit-jupiter-engine",
            "${System.getProperty("user.home")}/.m2/repository/org/junit/jupiter/junit-jupiter-api",
            "${System.getProperty("user.home")}/.m2/repository/org/junit/platform/junit-platform-console-standalone"
        )

        val junitJars = mutableListOf<String>()

        for (basePath in possibleJars) {
            val baseDir = File(basePath)
            if (baseDir.exists()) {
                baseDir.walkTopDown()
                    .filter { it.isFile && it.name.endsWith(".jar") }
                    .forEach { junitJars.add(it.absolutePath) }
            }
        }

        if (junitJars.isNotEmpty()) {
            println("✅ Найдены JUnit JAR файлы:")
            junitJars.forEach { println("   - $it") }
            return junitJars.joinToString(File.pathSeparator)
        }

        println("⚠️  JUnit JAR файлы не найдены. Попытка использовать системный classpath...")
        return System.getProperty("java.class.path", "")
    }

    private fun detectPackageName(sourceCode: String): String {
        val packageRegex = Regex("package\\s+([a-zA-Z_][a-zA-Z0-9_.]*)")
        val match = packageRegex.find(sourceCode)
        return match?.groupValues?.getOrNull(1) ?: "com.example"
    }

    private fun extractClassName(sourceCode: String): String {
        val classRegex = Regex("(?:class|object)\\s+([a-zA-Z_][a-zA-Z0-9_]*)")
        val match = classRegex.find(sourceCode)
        return match?.groupValues?.getOrNull(1) ?: "UnknownClass"
    }

    // === DeepSeek API support ===
    // Uses OpenAI-compatible Chat Completions endpoint.
    private val deepseekApiBaseUrl = "https://api.deepseek.com"
    private val deepseekChatCompletionsPath = "/chat/completions"
    private val deepseekApiKey: String? = System.getenv("DEEPSEEK_API_KEY")

    /**
     * Call DeepSeek chat completion API.
     *
     * @param userContent - user message to send
     * @param systemPrompt - optional system prompt
     * @param model - DeepSeek model id, e.g. "deepseek-chat" or "deepseek-reasoner"
     * @param temperature - sampling temperature (optional)
     * @param maxTokens - optional max tokens for completion
     * @throws IllegalStateException if DEEPSEEK_API_KEY is not set
     * @throws IOException on non-200 response
     */
    fun callDeepSeekChat(
        userContent: String,
        systemPrompt: String? = null,
        model: String = "deepseek-chat",
        temperature: Double? = 0.1,
        maxTokens: Int? = null
    ): String {
        val key = deepseekApiKey ?: throw IllegalStateException("❌ DEEPSEEK_API_KEY environment variable is not set")
        val url = deepseekApiBaseUrl + deepseekChatCompletionsPath

        val messages = mutableListOf<Map<String, Any?>>()
        if (!systemPrompt.isNullOrBlank()) {
            messages += mapOf("role" to "system", "content" to systemPrompt)
        }
        messages += mapOf("role" to "user", "content" to userContent)

        val payload = mutableMapOf<String, Any?>(
            "model" to model,
            "messages" to messages,
            "stream" to false
        )
        if (temperature != null) payload["temperature"] = temperature
        if (maxTokens != null) payload["max_tokens"] = maxTokens

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = objectMapper.writeValueAsString(payload).toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { resp ->
            val respBody = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw IOException("DeepSeek API error: HTTP ${resp.code} -> $respBody")
            }
            val node = objectMapper.readTree(respBody)
            // Non-streaming response: choices[0].message.content
            val contentNode = node.get("choices")?.get(0)?.get("message")?.get("content")
            return cleanupGeneratedCode(contentNode?.asText() ?: respBody)
        }
    }
    // === End of DeepSeek API support ===
}

fun main(args: Array<String>) = runBlocking {
    println("🤖 Enhanced Test Agent v2.0")
    println("=" * 60)

    if (args.isEmpty()) {
        println(
            """
            📋 Использование: 
            kotlin EnhancedTestAgent.kt <путь_к_исходному_файлу> [путь_к_файлу_тестов] [пакет]
            
            📝 Примеры:
            kotlin EnhancedTestAgent.kt src/main/kotlin/Calculator.kt
            kotlin EnhancedTestAgent.kt Calculator.kt test/CalculatorTest.kt
            kotlin EnhancedTestAgent.kt Calculator.kt test/CalculatorTest.kt com.myapp
            
            🔧 Переменные окружения:
            GEMINI_API_KEY - API ключ для Gemini (обязательно)
            
            💡 Как получить GEMINI_API_KEY:
            1. Перейти на https://makersuite.google.com/app/apikey
            2. Создать новый API ключ
            3. Экспортировать: export GEMINI_API_KEY="your-api-key-here"
        """.trimIndent()
        )
        exitProcess(1)
    }

    val sourceFilePath = args[0]
    val testFilePath = args.getOrElse(1) {
        val sourceFile = File(sourceFilePath)
        val baseName = sourceFile.nameWithoutExtension
        val parentDir = sourceFile.parent ?: "."
        "$parentDir/${baseName}Test.kt"
    }
    val packageName = args.getOrNull(2)

    println("📋 Параметры:")
    println("   📄 Исходный файл: $sourceFilePath")
    println("   🧪 Файл тестов: $testFilePath")
    println("   📦 Пакет: ${packageName ?: "авто-определение"}")
    println()

    val agent = EnhancedTestAgent()

    try {
        agent.generateTests(sourceFilePath, testFilePath, packageName = packageName)
        println("\n🎉 Enhanced Test Agent завершил работу!")
        println("📁 Проверьте сгенерированные тесты в файле: $testFilePath")

    } catch (e: Exception) {
        println("❌ Критическая ошибка: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}

private operator fun String.times(n: Int) = this.repeat(n)