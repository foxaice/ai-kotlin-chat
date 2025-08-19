#!/bin/bash

echo "🚀 Быстрый тест цепочки MCP серверов..."

# Проверяем переменные окружения
if [ -z "$TELEGRAM_BOT_TOKEN" ] || [ -z "$TELEGRAM_CHAT_ID" ]; then
    echo "❌ Не хватает переменных окружения. Загружаем из .env..."
    source .env
fi

# Проверяем еще раз
if [ -z "$TELEGRAM_BOT_TOKEN" ] || [ -z "$TELEGRAM_CHAT_ID" ]; then
    echo "❌ TELEGRAM_BOT_TOKEN или TELEGRAM_CHAT_ID не установлены"
    exit 1
fi

echo "✅ Переменные окружения готовы"

# Запускаем основное приложение на 30 секунд
echo "🔄 Запуск системы на 30 секунд..."
timeout 30s java -cp build/install/ai-kotlin-chat/lib/* job.JobMainKt \
    --intervalSeconds=25 \
    --useMcpChain=true \
    --input=/mcp-chain

echo "🏁 Тест завершен! Проверьте Telegram чат."
