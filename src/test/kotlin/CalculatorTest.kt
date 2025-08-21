import calculator.Calculator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertFailsWith
import kotlin.time.measureTime

@DisplayName("Тесты для улучшенного калькулятора")
class CalculatorTest {

    private lateinit var calculator: Calculator

    @BeforeEach
    fun setUp() {
        calculator = Calculator()
    }

    @Nested
    @DisplayName("Тесты сложения")
    inner class AdditionTests {

        @Test
        @DisplayName("Сложение положительных чисел")
        fun `should add positive numbers correctly`() {
            assertEquals(8.0, calculator.add(5.0, 3.0), 0.001)
        }

        @ParameterizedTest
        @ValueSource(doubles = [1.0, 5.0, 100.0, -10.0, 0.001, -1000.0])
        @DisplayName("Сложение с нулем для различных чисел")
        fun `should add zero correctly for various numbers`(number: Double) {
            assertEquals(number, calculator.add(number, 0.0), 0.001)
            assertEquals(number, calculator.add(0.0, number), 0.001)
        }

        @ParameterizedTest
        @CsvSource(
            "5.0, 3.0, 8.0",
            "-5.0, -3.0, -8.0",
            "10.5, 2.5, 13.0",
            "-7.2, 3.8, -3.4"
        )
        @DisplayName("Параметризованное сложение чисел")
        fun `should add numbers correctly`(a: Double, b: Double, expected: Double) {
            assertEquals(expected, calculator.add(a, b), 0.001)
        }

        @Test
        @DisplayName("Обработка NaN")
        fun `should handle NaN in addition`() {
            assertTrue(calculator.add(Double.NaN, 5.0).isNaN())
            assertTrue(calculator.add(5.0, Double.NaN).isNaN())
            assertTrue(calculator.add(Double.NaN, Double.NaN).isNaN())
        }

        @Test
        @DisplayName("Обработка бесконечности")
        fun `should handle infinity in addition`() {
            assertTrue(calculator.add(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).isNaN())
            assertEquals(Double.POSITIVE_INFINITY, calculator.add(Double.POSITIVE_INFINITY, 5.0))
            assertEquals(Double.NEGATIVE_INFINITY, calculator.add(Double.NEGATIVE_INFINITY, 5.0))
        }

        @Test
        @DisplayName("Граничные случаи с очень большими числами")
        fun `should handle extreme values in addition`() {
            // Очень большие числа
            assertEquals(Double.POSITIVE_INFINITY, calculator.add(Double.MAX_VALUE, Double.MAX_VALUE))

            // Очень маленькие числа
            val result = calculator.add(Double.MIN_VALUE, Double.MIN_VALUE)
            assertTrue(result > 0.0 && result.isFinite())

            // Переполнение
            assertTrue(calculator.add(Double.MAX_VALUE, 1e308).isInfinite())
        }
    }

    @Nested
    @DisplayName("Тесты вычитания")
    inner class SubtractionTests {

        @Test
        @DisplayName("Вычитание положительных чисел")
        fun `should subtract positive numbers correctly`() {
            assertEquals(2.0, calculator.subtract(5.0, 3.0), 0.001)
        }

        @Test
        @DisplayName("Вычитание с нулем")
        fun `should subtract zero correctly`() {
            assertEquals(5.0, calculator.subtract(5.0, 0.0), 0.001)
            assertEquals(-5.0, calculator.subtract(0.0, 5.0), 0.001)
        }

        @Test
        @DisplayName("Вычитание отрицательных чисел")
        fun `should subtract negative numbers correctly`() {
            assertEquals(-2.0, calculator.subtract(-5.0, -3.0), 0.001)
        }

        @Test
        @DisplayName("Обработка NaN в вычитании")
        fun `should handle NaN in subtraction`() {
            assertTrue(calculator.subtract(Double.NaN, 5.0).isNaN())
            assertTrue(calculator.subtract(5.0, Double.NaN).isNaN())
            assertTrue(calculator.subtract(Double.NaN, Double.NaN).isNaN())
        }

        @Test
        @DisplayName("Обработка бесконечности в вычитании")
        fun `should handle infinity in subtraction`() {
            assertTrue(calculator.subtract(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY).isNaN())
            assertEquals(Double.POSITIVE_INFINITY, calculator.subtract(Double.POSITIVE_INFINITY, 5.0))
        }
    }

    @Nested
    @DisplayName("Тесты умножения")
    inner class MultiplicationTests {

        @Test
        @DisplayName("Умножение положительных чисел")
        fun `should multiply positive numbers correctly`() {
            assertEquals(15.0, calculator.multiply(5.0, 3.0), 0.001)
        }

        @Test
        @DisplayName("Умножение с нулем")
        fun `should multiply with zero correctly`() {
            assertEquals(0.0, calculator.multiply(5.0, 0.0), 0.001)
            assertEquals(0.0, calculator.multiply(0.0, 5.0), 0.001)
        }

        @Test
        @DisplayName("Умножение отрицательных чисел")
        fun `should multiply negative numbers correctly`() {
            assertEquals(15.0, calculator.multiply(-5.0, -3.0), 0.001)
            assertEquals(-15.0, calculator.multiply(-5.0, 3.0), 0.001)
        }

        @Test
        @DisplayName("Обработка NaN в умножении")
        fun `should handle NaN in multiplication`() {
            assertTrue(calculator.multiply(Double.NaN, 5.0).isNaN())
            assertTrue(calculator.multiply(5.0, Double.NaN).isNaN())
            assertTrue(calculator.multiply(Double.NaN, Double.NaN).isNaN())
        }

        @Test
        @DisplayName("Обработка бесконечности в умножении")
        fun `should handle infinity in multiplication`() {
            assertEquals(Double.POSITIVE_INFINITY, calculator.multiply(Double.POSITIVE_INFINITY, 5.0))
            assertEquals(Double.NEGATIVE_INFINITY, calculator.multiply(Double.POSITIVE_INFINITY, -5.0))
        }
    }

    @Nested
    @DisplayName("Тесты деления с улучшенной точностью")
    inner class ImprovedDivisionTests {

        @Test
        @DisplayName("Деление положительных чисел")
        fun `should divide positive numbers correctly`() {
            assertEquals(2.0, calculator.divide(6.0, 3.0), 0.001)
        }

        @Test
        @DisplayName("Деление на ноль должно вызывать исключение с конкретным сообщением")
        fun `should throw exception when dividing by zero`() {
            val exception = assertFailsWith<IllegalArgumentException> {
                calculator.divide(5.0, 0.0)
            }
            assertEquals("Деление на ноль невозможно", exception.message)
        }

        @Test
        @DisplayName("Деление на очень маленькое число должно вызывать исключение с конкретным сообщением")
        fun `should throw exception when dividing by very small number`() {
            val exception = assertFailsWith<IllegalArgumentException> {
                calculator.divide(5.0, 1e-15)
            }
            assertEquals("Деление на ноль невозможно", exception.message)
        }

        @ParameterizedTest
        @CsvSource(
            "10.0, 2.0, 5.0",
            "15.0, 3.0, 5.0",
            "100.0, 4.0, 25.0",
            "-10.0, 2.0, -5.0"
        )
        @DisplayName("Параметризованное деление чисел")
        fun `should divide numbers correctly`(a: Double, b: Double, expected: Double) {
            assertEquals(expected, calculator.divide(a, b), 0.001)
        }

        @Test
        @DisplayName("Граничные случаи деления с экстремальными значениями")
        fun `should handle extreme values in division`() {
            // Очень большое число на маленькое допустимое число - проверяем, что результат очень большой
            val result1 = calculator.divide(Double.MAX_VALUE, 1e-8)
            assertTrue(result1 > 1e300, "Результат должен быть очень большим числом")

            // Маленькое допустимое число на очень большое - результат очень маленький
            val result2 = calculator.divide(1e-8, Double.MAX_VALUE)
            assertTrue(result2 > 0.0 && result2.isFinite(), "Результат должен быть маленьким положительным числом")

            // Деление максимального на единицу
            val result3 = calculator.divide(Double.MAX_VALUE, 1.0)
            assertEquals(Double.MAX_VALUE, result3, 0.001)

            // Переполнение при делении большого числа на очень маленькое допустимое число
            val result4 = calculator.divide(1e100, 1e-8)
            assertTrue(result4 > 1e100, "Результат должен быть очень большим")

            // Деление на границе EPSILON - проверяем, что операция выполнима
            val result5 = calculator.divide(10.0, 1e-8)  // 1e-8 > 1e-9 (EPSILON)
            assertEquals(1e9, result5, 1e6, "10 / 1e-8 должно быть около 1e9")

            // Проверка работы с маленькими, но допустимыми числами
            val smallValue = 1e-5  // Больше EPSILON
            val result6 = calculator.divide(smallValue, smallValue)
            assertEquals(1.0, result6, 0.001, "Деление числа на себя должно давать 1")

            // Проверка деления на число чуть больше EPSILON
            val epsilonPlus = 1e-8  // Больше 1e-9
            val result7 = calculator.divide(1.0, epsilonPlus)
            assertEquals(1e8, result7, 1e5, "1 / 1e-8 должно быть около 1e8")
        }

        @Test
        @DisplayName("Деление с NaN")
        fun `should handle NaN in division`() {
            assertTrue(calculator.divide(Double.NaN, 5.0).isNaN())
            assertTrue(calculator.divide(5.0, Double.NaN).isNaN())
            assertTrue(calculator.divide(Double.NaN, Double.NaN).isNaN())
        }

        @Test
        @DisplayName("Деление с бесконечностью")
        fun `should handle infinity in division`() {
            assertEquals(0.0, calculator.divide(5.0, Double.POSITIVE_INFINITY), 0.001)
            assertTrue(calculator.divide(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY).isNaN())
        }
    }

    @Nested
    @DisplayName("Тесты вычисления процентов")
    inner class PercentageTests {

        @Test
        @DisplayName("Вычисление процентов от положительного числа")
        fun `should calculate percentage correctly`() {
            assertEquals(5.0, calculator.percentage(100.0, 5.0), 0.001)
            assertEquals(25.0, calculator.percentage(50.0, 50.0), 0.001)
        }

        @Test
        @DisplayName("Вычисление процентов с нулем")
        fun `should calculate percentage with zero correctly`() {
            assertEquals(0.0, calculator.percentage(0.0, 50.0), 0.001)
            assertEquals(0.0, calculator.percentage(100.0, 0.0), 0.001)
        }

        @Test
        @DisplayName("Обработка NaN в процентах")
        fun `should handle NaN in percentage`() {
            assertTrue(calculator.percentage(Double.NaN, 5.0).isNaN())
            assertTrue(calculator.percentage(5.0, Double.NaN).isNaN())
            assertTrue(calculator.percentage(Double.NaN, Double.NaN).isNaN())
        }

        @Test
        @DisplayName("Вычисление процентов с отрицательными числами")
        fun `should calculate percentage with negative numbers`() {
            assertEquals(-5.0, calculator.percentage(-100.0, 5.0), 0.001)
            assertEquals(-5.0, calculator.percentage(100.0, -5.0), 0.001)
        }
    }

    @Nested
    @DisplayName("Тесты возведения в степень с улучшенной обработкой")
    inner class ImprovedPowerTests {

        @Test
        @DisplayName("Возведение в положительную степень")
        fun `should calculate power correctly`() {
            assertEquals(8.0, calculator.power(2.0, 3.0), 0.001)
            assertEquals(25.0, calculator.power(5.0, 2.0), 0.001)
        }

        @Test
        @DisplayName("Особые случаи степеней")
        fun `should handle special power cases`() {
            assertEquals(1.0, calculator.power(0.0, 0.0), 0.001) // Математическое соглашение
            assertEquals(1.0, calculator.power(5.0, 0.0), 0.001)
            assertEquals(1.0, calculator.power(-5.0, 0.0), 0.001)
        }

        @Test
        @DisplayName("Возведение в отрицательную степень")
        fun `should calculate negative power correctly`() {
            assertEquals(0.5, calculator.power(2.0, -1.0), 0.001)
            assertEquals(0.25, calculator.power(2.0, -2.0), 0.001)
        }

        @Test
        @DisplayName("Возведение отрицательного числа в целую степень")
        fun `should calculate negative base to integer power`() {
            assertEquals(-8.0, calculator.power(-2.0, 3.0), 0.001)
            assertEquals(16.0, calculator.power(-2.0, 4.0), 0.001)
        }

        @Test
        @DisplayName("Нельзя возвести 0 в отрицательную степень - проверка сообщения")
        fun `should throw exception for zero to negative power`() {
            val exception = assertFailsWith<IllegalArgumentException> {
                calculator.power(0.0, -2.0)
            }
            assertEquals("Нельзя возвести 0 в отрицательную степень", exception.message)
        }

        @Test
        @DisplayName("Нельзя возвести отрицательное число в дробную степень - проверка сообщения")
        fun `should throw exception for negative base to fractional power`() {
            val exception = assertFailsWith<IllegalArgumentException> {
                calculator.power(-4.0, 0.5)
            }
            assertEquals("Нельзя возвести отрицательное число в дробную степень в вещественной арифметике", exception.message)
        }

        @ParameterizedTest
        @CsvSource(
            "2.0, 3.0, 8.0",
            "5.0, 2.0, 25.0",
            "3.0, 4.0, 81.0",
            "10.0, 0.0, 1.0"
        )
        @DisplayName("Параметризованное возведение в степень")
        fun `should calculate power correctly for various inputs`(base: Double, exponent: Double, expected: Double) {
            assertEquals(expected, calculator.power(base, exponent), 0.001)
        }

        @Test
        @DisplayName("Граничные случаи возведения в степень с экстремальными значениями")
        fun `should handle extreme values in power`() {
            // Очень большое основание в небольшую степень
            assertTrue(calculator.power(Double.MAX_VALUE, 2.0).isInfinite())

            // Очень маленькое основание в отрицательную степень
            assertTrue(calculator.power(Double.MIN_VALUE, -1.0).isInfinite())
        }

        @Test
        @DisplayName("Обработка NaN в степенях")
        fun `should handle NaN in power`() {
            assertTrue(calculator.power(Double.NaN, 2.0).isNaN())
            assertTrue(calculator.power(2.0, Double.NaN).isNaN())
            assertTrue(calculator.power(1.0, Double.POSITIVE_INFINITY).isNaN())
        }
    }

    @Nested
    @DisplayName("Тесты извлечения корня с улучшенной обработкой")
    inner class ImprovedSqrtTests {

        @Test
        @DisplayName("Извлечение корня из положительного числа")
        fun `should calculate square root correctly`() {
            assertEquals(3.0, calculator.sqrt(9.0), 0.001)
            assertEquals(5.0, calculator.sqrt(25.0), 0.001)
        }

        @Test
        @DisplayName("Извлечение корня из нуля")
        fun `should calculate square root of zero correctly`() {
            assertEquals(0.0, calculator.sqrt(0.0), 0.001)
        }

        @Test
        @DisplayName("Обработка особых значений в корне")
        fun `should handle special values in sqrt`() {
            assertTrue(calculator.sqrt(Double.NaN).isNaN())
            assertEquals(Double.POSITIVE_INFINITY, calculator.sqrt(Double.POSITIVE_INFINITY))
        }

        @Test
        @DisplayName("Исключение для отрицательных чисел с проверкой точного сообщения")
        fun `should throw exception for negative numbers`() {
            val exception = assertFailsWith<IllegalArgumentException> {
                calculator.sqrt(-4.0)
            }
            assertEquals("Нельзя извлечь корень из отрицательного числа", exception.message)
        }

        @ParameterizedTest
        @CsvSource(
            "9.0, 3.0",
            "25.0, 5.0",
            "16.0, 4.0",
            "1.0, 1.0",
            "0.0, 0.0"
        )
        @DisplayName("Параметризованное извлечение корня")
        fun `should calculate square root correctly for various inputs`(number: Double, expected: Double) {
            assertEquals(expected, calculator.sqrt(number), 0.001)
        }

        @Test
        @DisplayName("Граничные случаи извлечения корня с экстремальными значениями")
        fun `should handle extreme values in sqrt`() {
            // Очень большое число
            assertTrue(calculator.sqrt(Double.MAX_VALUE).isFinite())

            // Очень маленькое положительное число
            val result = calculator.sqrt(Double.MIN_VALUE)
            assertTrue(result > 0.0 && result.isFinite())
        }
    }

    @Nested
    @DisplayName("Тесты безопасных методов с Result")
    inner class SafeMethodsTests {

        @Test
        @DisplayName("Безопасное деление - успех")
        fun `should return success for safe divide`() {
            val result = calculator.safeDivide(10.0, 2.0)
            assertTrue(result.isSuccess)
            assertEquals(5.0, result.getOrThrow(), 0.001)
        }

        @Test
        @DisplayName("Безопасное деление - ошибка")
        fun `should return failure for safe divide by zero`() {
            val result = calculator.safeDivide(10.0, 0.0)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        }

        @Test
        @DisplayName("Безопасное деление с NaN")
        fun `should return success for safe divide with NaN`() {
            val result = calculator.safeDivide(Double.NaN, 5.0)
            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().isNaN())
        }

        @Test
        @DisplayName("Безопасное возведение в степень - успех")
        fun `should return success for safe power`() {
            val result = calculator.safePower(2.0, 3.0)
            assertTrue(result.isSuccess)
            assertEquals(8.0, result.getOrThrow(), 0.001)
        }

        @Test
        @DisplayName("Безопасное возведение в степень - ошибка")
        fun `should return failure for safe power with invalid input`() {
            val result = calculator.safePower(0.0, -1.0)
            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("Безопасное возведение в степень с NaN")
        fun `should return success for safe power with NaN`() {
            val result = calculator.safePower(Double.NaN, 2.0)
            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().isNaN())
        }

        @Test
        @DisplayName("Безопасное извлечение корня - успех")
        fun `should return success for safe sqrt`() {
            val result = calculator.safeSqrt(25.0)
            assertTrue(result.isSuccess)
            assertEquals(5.0, result.getOrThrow(), 0.001)
        }

        @Test
        @DisplayName("Безопасное извлечение корня - ошибка")
        fun `should return failure for safe sqrt of negative`() {
            val result = calculator.safeSqrt(-4.0)
            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("Безопасное извлечение корня с NaN")
        fun `should return success for safe sqrt with NaN`() {
            val result = calculator.safeSqrt(Double.NaN)
            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().isNaN())
        }

        @Test
        @DisplayName("Безопасное извлечение корня с бесконечностью")
        fun `should return success for safe sqrt with infinity`() {
            val result = calculator.safeSqrt(Double.POSITIVE_INFINITY)
            assertTrue(result.isSuccess)
            assertEquals(Double.POSITIVE_INFINITY, result.getOrThrow())
        }
    }

    @Nested
    @DisplayName("Тесты утилитарных методов")
    inner class UtilityMethodsTests {

        @Test
        @DisplayName("Проверка равенства чисел с учетом погрешности")
        fun `should check equality with epsilon`() {
            assertTrue(calculator.isEqual(1.0, 1.0000000001, 1e-9)) // Используем больший эпсилон
            assertTrue(calculator.isEqual(1.0, 1.0000000009)) // Разность меньше 1e-10
            assertFalse(calculator.isEqual(1.0, 1.1))
            assertTrue(calculator.isEqual(Double.NaN, Double.NaN))
            assertTrue(calculator.isEqual(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY))
            assertFalse(calculator.isEqual(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY))
        }

        @Test
        @DisplayName("Проверка равенства с пользовательской погрешностью")
        fun `should check equality with custom epsilon`() {
            assertTrue(calculator.isEqual(1.0, 1.05, 0.1))
            assertFalse(calculator.isEqual(1.0, 1.05, 0.01))
            assertTrue(calculator.isEqual(0.0, 0.0001, 0.001))
        }

        @Test
        @DisplayName("Округление до десятичных знаков")
        fun `should round to decimal places correctly`() {
            assertEquals(3.14, calculator.roundToDecimalPlaces(3.14159, 2), 0.001)
            assertEquals(3.0, calculator.roundToDecimalPlaces(3.14159, 0), 0.001)
            assertTrue(calculator.roundToDecimalPlaces(Double.NaN, 2).isNaN())
            assertEquals(Double.POSITIVE_INFINITY, calculator.roundToDecimalPlaces(Double.POSITIVE_INFINITY, 2))
            assertEquals(Double.NEGATIVE_INFINITY, calculator.roundToDecimalPlaces(Double.NEGATIVE_INFINITY, 2))
        }

        @Test
        @DisplayName("Округление с нулем знаков")
        fun `should round to zero decimal places`() {
            assertEquals(4.0, calculator.roundToDecimalPlaces(3.7, 0), 0.001)
            assertEquals(4.0, calculator.roundToDecimalPlaces(3.5, 0), 0.001)
            assertEquals(3.0, calculator.roundToDecimalPlaces(3.2, 0), 0.001)
        }

        @Test
        @DisplayName("Округление отрицательных чисел")
        fun `should round negative numbers correctly`() {
            assertEquals(-3.14, calculator.roundToDecimalPlaces(-3.14159, 2), 0.001)
            assertEquals(-3.0, calculator.roundToDecimalPlaces(-3.14159, 0), 0.001)
        }

        @Test
        @DisplayName("Округление с отрицательным количеством знаков должно вызывать ошибку с конкретным сообщением")
        fun `should throw exception for negative decimal places`() {
            val exception = assertFailsWith<IllegalArgumentException> {
                calculator.roundToDecimalPlaces(3.14, -1)
            }
            assertEquals("Количество десятичных знаков не может быть отрицательным", exception.message)
        }

        @ParameterizedTest
        @CsvSource(
            "3.14159, 2, 3.14",
            "2.71828, 3, 2.718",
            "1.23456, 1, 1.2",
            "9.99999, 0, 10.0"
        )
        @DisplayName("Параметризованное округление")
        fun `should round numbers correctly`(number: Double, places: Int, expected: Double) {
            assertEquals(expected, calculator.roundToDecimalPlaces(number, places), 0.001)
        }
    }

    @Nested
    @DisplayName("Тесты производительности")
    inner class PerformanceTests {

        @Test
        @DisplayName("Производительность базовых арифметических операций")
        fun `should perform basic operations within reasonable time`() {
            val iterations = 1000000

            val addTime = measureTime {
                repeat(iterations) {
                    calculator.add(123.456, 789.012)
                }
            }

            val multiplyTime = measureTime {
                repeat(iterations) {
                    calculator.multiply(123.456, 789.012)
                }
            }

            val divideTime = measureTime {
                repeat(iterations) {
                    calculator.divide(789.012, 123.456)
                }
            }

            // Простая проверка, что операции выполняются достаточно быстро
            // В реальном проекте здесь были бы более точные бенчмарки
            println("Время выполнения $iterations операций:")
            println("Сложение: $addTime")
            println("Умножение: $multiplyTime")
            println("Деление: $divideTime")

            // Проверяем, что все операции выполнились менее чем за 5 секунд
            assertTrue(addTime.inWholeMilliseconds < 5000, "Сложение выполняется слишком медленно")
            assertTrue(multiplyTime.inWholeMilliseconds < 5000, "Умножение выполняется слишком медленно")
            assertTrue(divideTime.inWholeMilliseconds < 5000, "Деление выполняется слишком медленно")
        }

        @Test
        @DisplayName("Производительность сложных операций")
        fun `should perform complex operations within reasonable time`() {
            val iterations = 100000

            val powerTime = measureTime {
                repeat(iterations) {
                    calculator.power(2.5, 3.7)
                }
            }

            val sqrtTime = measureTime {
                repeat(iterations) {
                    calculator.sqrt(156.789)
                }
            }

            println("Время выполнения $iterations сложных операций:")
            println("Возведение в степень: $powerTime")
            println("Извлечение корня: $sqrtTime")

            // Более мягкие ограничения для сложных операций
            assertTrue(powerTime.inWholeMilliseconds < 10000, "Возведение в степень выполняется слишком медленно")
            assertTrue(sqrtTime.inWholeMilliseconds < 10000, "Извлечение корня выполняется слишком медленно")
        }
    }

    @Nested
    @DisplayName("Интеграционные тесты")
    inner class IntegrationTests {

        @Test
        @DisplayName("Сложные вычисления")
        fun `should perform complex calculations correctly`() {
            // (2 + 3) * 4 / 2 = 10
            val step1 = calculator.add(2.0, 3.0)
            val step2 = calculator.multiply(step1, 4.0)
            val result = calculator.divide(step2, 2.0)

            assertEquals(10.0, result, 0.001)
        }

        @Test
        @DisplayName("Безопасные сложные вычисления")
        fun `should perform safe complex calculations`() {
            val result = calculator.safeDivide(10.0, 2.0)
                .mapCatching { calculator.power(it, 2.0) }
                .mapCatching { calculator.sqrt(it) }

            assertTrue(result.isSuccess)
            assertEquals(5.0, result.getOrThrow(), 0.001)
        }

        @Test
        @DisplayName("Цепочка безопасных операций с ошибкой")
        fun `should handle error in safe operation chain`() {
            val result = calculator.safeDivide(10.0, 0.0) // Ошибка здесь
                .mapCatching { calculator.power(it, 2.0) }
                .mapCatching { calculator.sqrt(it) }

            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("Вычисления с округлением")
        fun `should calculate with rounding`() {
            val result = calculator.divide(10.0, 3.0)
            val rounded = calculator.roundToDecimalPlaces(result, 2)
            assertEquals(3.33, rounded, 0.001)
        }
    }
}
