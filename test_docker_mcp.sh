#!/bin/bash

echo "🧪 Тестирование цепочки MCP в Docker..."

# Проверяем переменные окружения
if [ ! -f ".env" ]; then
    echo "❌ Создайте файл .env с токенами"
    exit 1
fi

set -a; source .env; set +a

echo "🔨 Сборка образа..."
docker compose build mcp-test

echo "🚀 Запуск теста (60 секунд)..."
timeout 60s docker compose --profile test run --rm mcp-test || \
gtimeout 60s docker compose --profile test run --rm mcp-test

echo "🏁 Тест завершен!"
echo "Проверьте Telegram чат на наличие сообщений."
