#!/bin/bash

# Test Agent Runner Script
# Скрипт для удобного запуска агента генерации тестов

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Функция для вывода логов
log_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

log_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

log_error() {
    echo -e "${RED}❌ $1${NC}"
}

# Заголовок
echo -e "${BLUE}"
echo "🤖 Test Agent Runner"
echo "===================="
echo -e "${NC}"

# Проверка переменной окружения GEMINI_API_KEY
if [ -z "$GEMINI_API_KEY" ]; then
    log_error "GEMINI_API_KEY не установлен!"
    echo ""
    echo "Как получить API ключ:"
    echo "1. Перейдите на https://makersuite.google.com/app/apikey"
    echo "2. Создайте новый API ключ"
    echo "3. Экспортируйте его: export GEMINI_API_KEY=\"your-api-key-here\""
    echo ""
    exit 1
fi

log_success "GEMINI_API_KEY найден"

# Проверка аргументов
if [ $# -eq 0 ]; then
    echo "Использование: $0 <source_file> [test_file] [package_name]"
    echo ""
    echo "Примеры:"
    echo "  $0 Calculator.kt"
    echo "  $0 src/main/kotlin/Calculator.kt"
    echo "  $0 Calculator.kt test/CalculatorTest.kt"
    echo "  $0 Calculator.kt test/CalculatorTest.kt com.example"
    echo ""
    echo "Доступные примеры для тестирования:"
    echo "  $0 example  # Запуск с примером Calculator"
    exit 1
fi

# Специальная команда для примера
if [ "$1" = "example" ]; then
    log_info "Запуск с примером Calculator..."

    # Создаем директории если их нет
    mkdir -p src/main/kotlin
    mkdir -p src/test/kotlin

    # Создаем пример Calculator.kt если его нет
    if [ ! -f "src/main/kotlin/Calculator.kt" ]; then
        log_info "Создаем пример Calculator.kt..."
        cat > src/main/kotlin/Calculator.kt << 'EOF'
package com.example

import kotlin.math.*

class Calculator {

    fun add(a: Double, b: Double): Double = a + b

    fun subtract(a: Double, b: Double): Double = a - b

    fun multiply(a: Double, b: Double): Double = a * b

    fun divide(a: Double, b: Double): Double {
        if (b == 0.0) {
            throw IllegalArgumentException("Division by zero is not allowed")
        }
        return a / b
    }

    fun power(base: Double, exponent: Double): Double = base.pow(exponent)

    fun sqrt(number: Double): Double {
        if (number < 0) {
            throw IllegalArgumentException("Cannot calculate square root of negative number")
        }
        return sqrt(number)
    }

    fun factorial(n: Int): Long {
        if (n < 0) throw IllegalArgumentException("Factorial is not defined for negative numbers")
        if (n <= 1) return 1L
        return (2..n).fold(1L) { acc, i -> acc * i }
    }

    fun isPrime(number: Int): Boolean {
        if (number < 2) return false
        if (number == 2) return true
        if (number % 2 == 0) return false

        val limit = sqrt(number.toDouble()).toInt()
        for (i in 3..limit step 2) {
            if (number % i == 0) return false
        }
        return true
    }
}
EOF
        log_success "Calculator.kt создан"
    fi

    SOURCE_FILE="src/main/kotlin/Calculator.kt"
    TEST_FILE="src/test/kotlin/CalculatorTest.kt"
    PACKAGE="com.example"
else
    SOURCE_FILE="$1"
    TEST_FILE="${2:-}"
    PACKAGE="${3:-}"
fi

# Проверяем существование исходного файла
if [ ! -f "$SOURCE_FILE" ]; then
    log_error "Исходный файл не найден: $SOURCE_FILE"
    exit 1
fi

log_info "Исходный файл: $SOURCE_FILE"
log_info "Файл тестов: ${TEST_FILE:-авто-генерация}"
log_info "Пакет: ${PACKAGE:-авто-определение}"

echo ""

# Компилируем проект
log_info "Компиляция проекта..."
if ./gradlew compileKotlin > /dev/null 2>&1; then
    log_success "Проект скомпилирован"
else
    log_warning "Проблемы с компиляцией, но продолжаем..."
fi

# Запускаем агента
log_info "Запуск Test Agent..."
echo ""

if [ -n "$TEST_FILE" ] && [ -n "$PACKAGE" ]; then
    ./gradlew runAgent -PappArgs="$SOURCE_FILE $TEST_FILE $PACKAGE" --quiet
elif [ -n "$TEST_FILE" ]; then
    ./gradlew runAgent -PappArgs="$SOURCE_FILE $TEST_FILE" --quiet
else
    ./gradlew runAgent -PappArgs="$SOURCE_FILE" --quiet
fi

echo ""

# Проверяем результат
if [ -n "$TEST_FILE" ] && [ -f "$TEST_FILE" ]; then
    log_success "Тесты созданы в файле: $TEST_FILE"
    echo ""
    log_info "Попытка запуска созданных тестов..."

    if ./gradlew test --tests "*$(basename ${TEST_FILE%.*})" > /dev/null 2>&1; then
        log_success "Все тесты прошли успешно! 🎉"
    else
        log_warning "Тесты созданы, но некоторые могут не проходить"
        echo "Запустите './gradlew test' для подробной информации"
    fi

    echo ""
    echo "📁 Содержимое сгенерированного теста:"
    echo "-----------------------------------"
    head -20 "$TEST_FILE"
    if [ $(wc -l < "$TEST_FILE") -gt 20 ]; then
        echo "... (показано первых 20 строк)"
    fi

else
    log_warning "Файл тестов не найден или не создан"
fi

echo ""
log_info "Готово!"