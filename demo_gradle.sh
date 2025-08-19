#!/bin/bash

echo "🎬 ДЕМОНСТРАЦИЯ: Цепочка MCP серверов через Gradle"
echo "==============================================="

# Проверяем переменные окружения
if [ ! -f ".env" ]; then
    echo "❌ Создайте файл .env с токенами"
    exit 1
fi

set -a; source .env; set +a

echo "✅ Переменные окружения загружены"

echo "🔨 Сборка MCP серверов..."
cd telegram-mcp-server && npm install > /dev/null 2>&1 && npm run build > /dev/null 2>&1 && cd ..
cd todoist-mcp-server && npm install > /dev/null 2>&1 && npm run build > /dev/null 2>&1 && cd ..

echo ""
echo "🚀 ЗАПУСК ЧЕРЕЗ GRADLE"
echo "====================="
echo "Нажмите Ctrl+C для остановки"
echo ""

# Запускаем через gradle с экспортированными переменными
export TELEGRAM_BOT_TOKEN TELEGRAM_CHAT_ID TODOIST_API_KEY GEMINI_API_KEY

./gradlew runJob --args="--intervalSeconds=20 --useMcpChain=true --input=/mcp-chain"
