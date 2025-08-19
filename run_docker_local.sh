#!/bin/bash

echo "🐳 Запуск цепочки MCP через Docker локально..."

# Проверяем .env файл
if [ ! -f ".env" ]; then
    echo "❌ Файл .env не найден"
    echo "Создайте файл .env с переменными:"
    echo "GEMINI_API_KEY=ваш_ключ"
    echo "TELEGRAM_BOT_TOKEN=ваш_токен" 
    echo "TELEGRAM_CHAT_ID=ваш_chat_id"
    echo "TODOIST_API_KEY=ваш_ключ"
    exit 1
fi

# Загружаем переменные окружения
set -a; source .env; set +a

echo "✅ Переменные окружения загружены"
echo "🔨 Сборка Docker образа..."

# Собираем образ
docker compose build mcp-chain

echo "🚀 Запуск контейнера..."

# Запускаем в фоне
docker compose up -d mcp-chain

echo "📊 Статус контейнера:"
docker compose ps

echo ""
echo "📋 Для просмотра логов:"
echo "  docker compose logs -f mcp-chain"
echo ""
echo "🛑 Для остановки:"
echo "  docker compose down"
echo ""
echo "🧪 Для разового теста:"
echo "  docker compose --profile test run --rm mcp-test"
