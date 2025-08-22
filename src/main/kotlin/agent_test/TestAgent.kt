package agent_test

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

class TestAgent {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(20))
        .writeTimeout(Duration.ofSeconds(120))
        .readTimeout(Duration.ofSeconds(180))
        .callTimeout(Duration.ofSeconds(0))
        .retryOnConnectionFailure(true)
        .build()

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val geminiApiKey = System.getenv("GEMINI_API_KEY") 
        ?: throw IllegalStateException("GEMINI_API_KEY environment variable is not set")
    
    private val geminiApiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp:generateContent"

    suspend fun generateTests(sourceFilePath: String, testFilePath: String, maxIterations: Int = 5) {
        println("🚀 Запуск агента для генерации тестов...")
        println("📄 Исходный файл: $sourceFilePath")
        println("🧪 Файл тестов: $testFilePath")
        
        val sourceFile = File(sourceFilePath)
        if (!sourceFile.exists()) {
            println("❌ Файл не найден: $sourceFilePath")
            return
        }
        
        val sourceCode = sourceFile.readText()
        var iteration = 1
        
        while (iteration <= maxIterations) {
            println("\n🔄 Итерация $iteration из $maxIterations")
            
            try {
                val testCode = if (iteration == 1) {
                    generateInitialTests(sourceCode, sourceFilePath)
                } else {
                    val testFile = File(testFilePath)
                    val existingTests = if (testFile.exists()) testFile.readText() else ""
                    val errorOutput = runTests(testFilePath)
                    
                    if (errorOutput.isEmpty()) {
                        println("✅ Все тесты успешно прошли!")
                        return
                    }
                    
                    println("❌ Обнаружены ошибки в тестах:")
                    println(errorOutput)
                    
                    fixTests(sourceCode, existingTests, errorOutput, sourceFilePath)
                }
                
                // Записываем тесты в файл
                val testFile = File(testFilePath)
                testFile.parentFile?.mkdirs()
                testFile.writeText(testCode)
                
                println("📝 Тесты записаны в файл: $testFilePath")
                
                // Пытаемся скомпилировать и запустить тесты
                val errorOutput = runTests(testFilePath)
                
                if (errorOutput.isEmpty()) {
                    println("✅ Все тесты успешно созданы и прошли!")
                    return
                } else {
                    println("⚠️  Найдены ошибки, переходим к следующей итерации...")
                }
                
            } catch (e: Exception) {
                println("❌ Ошибка на итерации $iteration: ${e.message}")
                e.printStackTrace()
            }
            
            iteration++
        }
        
        println("❌ Не удалось создать рабочие тесты за $maxIterations итераций")
    }

    private suspend fun generateInitialTests(sourceCode: String, sourceFilePath: String): String {
        val prompt = """
Проанализируй следующий Kotlin код и создай для него полный набор JUnit тестов.

Исходный файл: $sourceFilePath
Код:
```kotlin
$sourceCode
```

Требования к тестам:
1. Используй JUnit 5 (org.junit.jupiter.api.*)
2. Покрой все публичные методы и функции
3. Протестируй граничные случаи и обработку ошибок
4. Создай осмысленные имена тестов
5. Используй подходящие assert методы
6. Если нужны моки или тестовые данные - создай их
7. Добавь необходимые imports
8. Убедись, что код компилируется

Верни только код тестов без дополнительных объяснений.
        """.trimIndent()

        return callGeminiApi(prompt)
    }

    private suspend fun fixTests(sourceCode: String, existingTests: String, errorOutput: String, sourceFilePath: String): String {
        val prompt = """
Исправь ошибки в тестах для Kotlin кода.

Исходный файл: $sourceFilePath
Исходный код:
```kotlin
$sourceCode
```

Текущие тесты:
```kotlin
$existingTests
```

Ошибки компиляции/выполнения:
```
$errorOutput
```

Исправь все ошибки и верни исправленный код тестов:
1. Исправь ошибки компиляции
2. Исправь ошибки импортов
3. Исправь логические ошибки в тестах
4. Убедись, что все тесты корректно написаны
5. Используй правильные типы данных и методы

Верни только исправленный код тестов без дополнительных объяснений.
        """.trimIndent()

        return callGeminiApi(prompt)
    }

    private suspend fun callGeminiApi(prompt: String): String {
        val requestBody = mapOf(
            "contents" to listOf(
                mapOf("parts" to listOf(mapOf("text" to prompt)))
            ),
            "generationConfig" to mapOf(
                "temperature" to 0.1,
                "maxOutputTokens" to 4000
            )
        )

        val json = objectMapper.writeValueAsString(requestBody)
        val body = json.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$geminiApiUrl?key=$geminiApiKey")
            .post(body)
            .build()

        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Ошибка API: ${response.code} - ${response.message}")
            }

            val responseBody = response.body?.string() 
                ?: throw IOException("Пустой ответ от API")
            
            val jsonResponse: JsonNode = objectMapper.readTree(responseBody)
            
            val textResponse = jsonResponse
                .path("candidates")
                .firstOrNull()
                ?.path("content")
                ?.path("parts")
                ?.firstOrNull()
                ?.path("text")
                ?.asText()
                ?: throw IOException("Не удалось извлечь текст из ответа API")

            println("model:\n $textResponse")
            textResponse
        }
    }

    private fun runTests(testFilePath: String): String {
        return try {
            val testFile = File(testFilePath)
            if (!testFile.exists()) {
                return "Файл тестов не существует: $testFilePath"
            }

            println("🔨 Компилируем и запускаем тесты...")
            
            // Пытаемся скомпилировать тесты через kotlinc
            val compileProcess = ProcessBuilder(
                "kotlinc", 
                "-cp", 
                getClasspath(),
                testFilePath
            ).redirectErrorStream(true).start()
            
            val compileOutput = compileProcess.inputStream.bufferedReader().readText()
            val compileExitCode = compileProcess.waitFor()
            
            if (compileExitCode != 0) {
                return "Ошибка компиляции:\n$compileOutput"
            }
            
            // Если компиляция прошла успешно, пытаемся запустить тесты
            val testClassName = extractClassNameFromPath(testFilePath)
            val runProcess = ProcessBuilder(
                "kotlin",
                "-cp",
                "${getClasspath()}:.",
                "org.junit.platform.console.ConsoleLauncher",
                "--select-class",
                testClassName
            ).redirectErrorStream(true).start()
            
            val runOutput = runProcess.inputStream.bufferedReader().readText()
            val runExitCode = runProcess.waitFor()
            
            if (runExitCode != 0) {
                return "Ошибка выполнения тестов:\n$runOutput"
            }
            
            println("✅ Тесты успешно скомпилированы и запущены")
            return "" // Пустая строка означает успех
            
        } catch (e: Exception) {
            "Ошибка при выполнении тестов: ${e.message}"
        }
    }

    private fun getClasspath(): String {
        // Возвращаем classpath с JUnit и другими зависимостями
        val gradleCache = System.getProperty("user.home") + "/.gradle/caches"
        val kotlinStdlib = "$gradleCache/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib"
        val junitApi = "$gradleCache/modules-2/files-2.1/org.junit.jupiter/junit-jupiter-api"
        val junitEngine = "$gradleCache/modules-2/files-2.1/org.junit.jupiter/junit-jupiter-engine"
        
        return "build/classes/kotlin/main:build/classes/kotlin/test:$kotlinStdlib/*:$junitApi/*:$junitEngine/*"
    }

    private fun extractClassNameFromPath(filePath: String): String {
        return File(filePath).nameWithoutExtension
    }
}

fun main(args: Array<String>) = runBlocking {
    println("🤖 Test Agent v1.0")
    println("=" * 50)
    
    if (args.isEmpty()) {
        println("""
            Использование: kotlin Main.kt <путь_к_исходному_файлу> [путь_к_файлу_тестов]
            
            Примеры:
            kotlin Main.kt src/main/kotlin/Calculator.kt
            kotlin Main.kt src/main/kotlin/Calculator.kt src/test/kotlin/CalculatorTest.kt
            
            Переменные окружения:
            GEMINI_API_KEY - API ключ для Gemini (обязательно)
        """.trimIndent())
        exitProcess(1)
    }
    
    val sourceFilePath = args[0]
    val testFilePath = args.getOrElse(1) { 
        val sourceFile = File(sourceFilePath)
        val testDir = "src/test/kotlin"
        val testFileName = "${sourceFile.nameWithoutExtension}Test.kt"
        "$testDir/$testFileName"
    }
    
    val agent = TestAgent()
    
    try {
        agent.generateTests(sourceFilePath, testFilePath)
        println("\n🎉 Агент завершил работу!")
    } catch (e: Exception) {
        println("❌ Критическая ошибка: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}

private operator fun String.times(n: Int) = this.repeat(n)