package calculator

import kotlin.math.*

/**
 * Улучшенный калькулятор для выполнения базовых арифметических операций
 * с обработкой особых случаев и безопасными методами
 */
class Calculator {

    companion object {
        private const val EPSILON = 1e-9
        private const val PERCENT_DIVISOR = 100.0
    }

    /**
     * Сложение двух чисел с обработкой NaN
     * @param a первое число
     * @param b второе число
     * @return результат сложения
     */
    fun add(a: Double, b: Double): Double {
        return when {
            a.isNaN() || b.isNaN() -> Double.NaN
            else -> a + b
        }
    }

    /**
     * Вычитание двух чисел с обработкой NaN
     * @param a уменьшаемое
     * @param b вычитаемое
     * @return результат вычитания
     */
    fun subtract(a: Double, b: Double): Double {
        return when {
            a.isNaN() || b.isNaN() -> Double.NaN
            else -> a - b
        }
    }

    /**
     * Умножение двух чисел с обработкой NaN
     * @param a первый множитель
     * @param b второй множитель
     * @return результат умножения
     */
    fun multiply(a: Double, b: Double): Double {
        return when {
            a.isNaN() || b.isNaN() -> Double.NaN
            else -> a * b
        }
    }

    /**
     * Деление двух чисел с улучшенной обработкой малых чисел и NaN
     * @param a делимое
     * @param b делитель
     * @return результат деления
     * @throws IllegalArgumentException если делитель близок к нулю
     */
    fun divide(a: Double, b: Double): Double {
        return when {
            a.isNaN() || b.isNaN() -> Double.NaN
            abs(b) < EPSILON -> throw IllegalArgumentException("Деление на ноль невозможно")
            else -> a / b
        }
    }

    /**
     * Возведение числа в степень с обработкой особых случаев
     * @param base основание
     * @param exponent показатель степени
     * @return результат возведения в степень
     * @throws IllegalArgumentException для недопустимых операций
     */
    fun power(base: Double, exponent: Double): Double {
        return when {
            base.isNaN() || exponent.isNaN() -> Double.NaN
            base == 0.0 && exponent < 0.0 -> throw IllegalArgumentException("Нельзя возвести 0 в отрицательную степень")
            base < 0.0 && exponent != floor(exponent) -> throw IllegalArgumentException("Нельзя возвести отрицательное число в дробную степень в вещественной арифметике")
            base == 1.0 && exponent.isInfinite() -> Double.NaN
            else -> base.pow(exponent)
        }
    }

    /**
     * Извлечение квадратного корня с обработкой особых значений
     * @param number число, из которого извлекается корень
     * @return квадратный корень числа
     * @throws IllegalArgumentException если число отрицательное
     */
    fun sqrt(number: Double): Double {
        return when {
            number.isNaN() -> Double.NaN
            number < 0 -> throw IllegalArgumentException("Нельзя извлечь корень из отрицательного числа")
            number.isInfinite() && number > 0 -> Double.POSITIVE_INFINITY
            else -> kotlin.math.sqrt(number)
        }
    }

    /**
     * Вычисление процента от числа с проверкой на NaN
     * @param number число
     * @param percentage процент
     * @return результат вычисления процента
     */
    fun percentage(number: Double, percentage: Double): Double {
        return when {
            percentage.isNaN() || number.isNaN() -> Double.NaN
            else -> (number * percentage) / PERCENT_DIVISOR
        }
    }

    /**
     * Безопасное деление с возвращением Result
     * @param a делимое
     * @param b делитель
     * @return Result с результатом деления или ошибкой
     */
    fun safeDivide(a: Double, b: Double): Result<Double> {
        return runCatching { divide(a, b) }
    }

    /**
     * Безопасное возведение в степень с возвращением Result
     * @param base основание
     * @param exponent показатель степени
     * @return Result с результатом или ошибкой
     */
    fun safePower(base: Double, exponent: Double): Result<Double> {
        return runCatching { power(base, exponent) }
    }

    /**
     * Безопасное извлечение корня с возвращением Result
     * @param number число
     * @return Result с результатом или ошибкой
     */
    fun safeSqrt(number: Double): Result<Double> {
        return runCatching { sqrt(number) }
    }

    /**
     * Проверка равенства чисел с учетом погрешности
     * @param a первое число
     * @param b второе число
     * @param epsilon погрешность (по умолчанию используется EPSILON)
     * @return true, если числа равны с учетом погрешности
     */
    fun isEqual(a: Double, b: Double, epsilon: Double = EPSILON): Boolean {
        return when {
            a.isNaN() && b.isNaN() -> true
            a.isInfinite() && b.isInfinite() -> a == b
            else -> abs(a - b) <= epsilon
        }
    }

    /**
     * Округление числа до указанного количества десятичных знаков
     * @param number число для округления
     * @param decimalPlaces количество десятичных знаков
     * @return округленное число
     * @throws IllegalArgumentException если количество знаков отрицательное
     */
    fun roundToDecimalPlaces(number: Double, decimalPlaces: Int): Double {
        require(decimalPlaces >= 0) { "Количество десятичных знаков не может быть отрицательным" }

        return when {
            number.isNaN() -> Double.NaN
            number.isInfinite() -> number
            else -> {
                val factor = 10.0.pow(decimalPlaces)
                round(number * factor) / factor
            }
        }
    }

    /**
     * Приватная функция для валидации входных параметров
     * @param numbers числа для проверки
     * @throws IllegalArgumentException если параметр не является конечным числом
     */
    private fun validateInput(vararg numbers: Double) {
        numbers.forEach { number ->
            require(number.isFinite()) { "Параметр должен быть конечным числом: $number" }
        }
    }
}