#!/bin/bash

echo "🧪 Тестирование цепочки MCP серверов..."

# Проверяем переменные окружения
if [ -z "$TELEGRAM_BOT_TOKEN" ] || [ -z "$TELEGRAM_CHAT_ID" ] || [ -z "$TODOIST_API_KEY" ]; then
    echo "❌ Не все переменные окружения установлены:"
    echo "   - TELEGRAM_BOT_TOKEN: ${TELEGRAM_BOT_TOKEN:+установлен}"
    echo "   - TELEGRAM_CHAT_ID: ${TELEGRAM_CHAT_ID:+установлен}"  
    echo "   - TODOIST_API_KEY: ${TODOIST_API_KEY:+установлен}"
    echo ""
    echo "Загрузите их из .env файла:"
    echo "  source .env"
    exit 1
fi

# Собираем MCP сервера
echo "📦 Сборка MCP серверов..."

# Todoist MCP
if [ -d "todoist-mcp-server" ]; then
    echo "🔨 Сборка Todoist MCP..."
    cd todoist-mcp-server
    npm install > /dev/null 2>&1
    npm run build > /dev/null 2>&1
    cd ..
    echo "✅ Todoist MCP собран"
else
    echo "❌ Директория todoist-mcp-server не найдена"
    exit 1
fi

# Telegram MCP
if [ -d "telegram-mcp-server" ]; then
    echo "🔨 Сборка Telegram MCP..."
    cd telegram-mcp-server
    npm install > /dev/null 2>&1
    npm run build > /dev/null 2>&1
    cd ..
    echo "✅ Telegram MCP собран"
else
    echo "❌ Директория telegram-mcp-server не найдена"
    exit 1
fi

# Собираем основное приложение
echo "🔨 Сборка основного приложения..."
./gradlew installDist > /dev/null 2>&1

if [ $? -eq 0 ]; then
    echo "✅ Основное приложение собрано"
else
    echo "❌ Ошибка сборки основного приложения"
    exit 1
fi

# Запускаем тест
echo ""
echo "🚀 Запуск тестирования цепочки MCP..."
echo "   Это займет около 30 секунд..."
echo ""

# Запускаем job с коротким интервалом для тестирования
timeout 30s java -cp build/install/ai-kotlin-chat/lib/* job.JobMainKt \
    --intervalSeconds=25 \
    --useMcpChain=true \
    --input=/mcp-chain

echo ""
echo "🏁 Тест завершен!"
echo ""
echo "Проверьте ваш Telegram чат на наличие сообщений с задачами."
echo ""
echo "Если сообщения пришли - цепочка MCP работает! 🎉"
echo "Если нет - проверьте логи выше на наличие ошибок."
