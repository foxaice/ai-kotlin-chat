#!/bin/bash

echo "🧪 Прямой тест через Docker..."

# Загружаем переменные
set -a; source .env; set +a

echo "✅ Переменные загружены"
echo "🔨 Сборка Docker образа..."

# Собираем образ
docker build -t mcp-chain .

if [ $? -ne 0 ]; then
    echo "❌ Ошибка сборки образа"
    exit 1
fi

echo "✅ Образ собран"
echo "🚀 Запуск контейнера на 60 секунд..."

# Запускаем контейнер
docker run --rm \
  -e GEMINI_API_KEY="$GEMINI_API_KEY" \
  -e TELEGRAM_BOT_TOKEN="$TELEGRAM_BOT_TOKEN" \
  -e TELEGRAM_CHAT_ID="$TELEGRAM_CHAT_ID" \
  -e TODOIST_API_KEY="$TODOIST_API_KEY" \
  --name mcp-test \
  mcp-chain &

DOCKER_PID=$!

# Ждем 60 секунд  
echo "⏰ Ждем 60 секунд..."
sleep 60

# Останавливаем контейнер
echo "🛑 Остановка контейнера..."
docker stop mcp-test 2>/dev/null
wait $DOCKER_PID 2>/dev/null

echo "🏁 Тест завершен!"
echo "Проверьте Telegram чат на наличие сообщений."
