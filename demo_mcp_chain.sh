#!/bin/bash

echo "🎬 ДЕМОНСТРАЦИЯ: Цепочка из двух MCP серверов"
echo "=================================================="
echo ""

# Проверяем переменные окружения
if [ ! -f ".env" ]; then
    echo "❌ Создайте файл .env с токенами"
    exit 1
fi

set -a; source .env; set +a

echo "✅ Переменные окружения загружены"
echo "📋 Готовим систему..."

# Собираем все компоненты
echo "🔨 Сборка Kotlin приложения..."
./gradlew installDist > /dev/null 2>&1

echo "🔨 Сборка Telegram MCP сервера..."
cd telegram-mcp-server
npm install > /dev/null 2>&1
npm run build > /dev/null 2>&1
cd ..

echo "🔨 Сборка Todoist MCP сервера..."
cd todoist-mcp-server  
npm install > /dev/null 2>&1
npm run build > /dev/null 2>&1
cd ..

# Находим правильный путь
APP_DIR=$(find build/install -name "lib" -type d | head -1 | xargs dirname)
echo "📁 Найдена директория приложения: $APP_DIR"

echo ""
echo "🚀 ЗАПУСК ЦЕПОЧКИ MCP СЕРВЕРОВ"
echo "================================"
echo ""
echo "Система будет:"
echo "1. 📋 Подключаться к Todoist MCP"
echo "2. 📱 Подключаться к Telegram MCP" 
echo "3. 🔗 Получать задачи из Todoist"
echo "4. 🎨 Красиво форматировать их"
echo "5. 📤 Отправлять в ваш Telegram"
echo ""
echo "Интервал: каждые 20 секунд"
echo "Для остановки нажмите Ctrl+C"
echo ""
echo "▶️  Запуск через 3 секунды..."
sleep 3

# Запускаем систему с правильным путем
java -cp "$APP_DIR/lib/*" job.JobMainKt \
    --intervalSeconds=20 \
    --useMcpChain=true \
    --input=/mcp-chain
