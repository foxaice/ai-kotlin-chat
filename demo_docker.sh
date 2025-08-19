#!/bin/bash

echo "🐳 ДЕМОНСТРАЦИЯ: Цепочка MCP в Docker"
echo "===================================="

# Загружаем переменные
if [ ! -f ".env" ]; then
    echo "❌ Создайте файл .env"
    exit 1
fi

set -a; source .env; set +a

echo "✅ Переменные загружены"
echo "🔨 Сборка Docker образа (может занять 2-3 минуты)..."

# Собираем
docker build -t mcp-chain-demo .

if [ $? -ne 0 ]; then
    echo "❌ Ошибка сборки"
    exit 1
fi

echo "✅ Образ собран успешно!"
echo "🚀 Запуск контейнера..."

# Запускаем
docker run --rm \
  -e GEMINI_API_KEY="$GEMINI_API_KEY" \
  -e TELEGRAM_BOT_TOKEN="$TELEGRAM_BOT_TOKEN" \
  -e TELEGRAM_CHAT_ID="$TELEGRAM_CHAT_ID" \
  -e TODOIST_API_KEY="$TODOIST_API_KEY" \
  --name mcp-demo \
  mcp-chain-demo

echo "🏁 Контейнер остановлен"
