#!/bin/bash

echo "🚀 Настройка Telegram MCP сервера..."

# Проверяем наличие Node.js
if ! command -v node &> /dev/null; then
    echo "❌ Node.js не найден. Установите Node.js версии 18 или выше."
    exit 1
fi

# Проверяем версию Node.js
NODE_VERSION=$(node --version | cut -d'v' -f2 | cut -d'.' -f1)
if [ "$NODE_VERSION" -lt 18 ]; then
    echo "❌ Требуется Node.js версии 18 или выше. Текущая версия: $(node --version)"
    exit 1
fi

echo "✅ Node.js версия: $(node --version)"

# Переходим в директорию Telegram MCP сервера
cd telegram-mcp-server

# Устанавливаем зависимости
echo "📦 Установка зависимостей..."
npm install

# Компилируем TypeScript
echo "🔨 Компиляция TypeScript..."
npm run build

# Проверяем что файл создался
if [ -f "dist/index.js" ]; then
    echo "✅ Telegram MCP сервер успешно собран!"
else
    echo "❌ Ошибка сборки Telegram MCP сервера"
    exit 1
fi

# Проверяем переменные окружения
echo ""
echo "🔧 Проверка переменных окружения:"

if [ -z "$TELEGRAM_BOT_TOKEN" ]; then
    echo "⚠️  TELEGRAM_BOT_TOKEN не установлен"
else
    echo "✅ TELEGRAM_BOT_TOKEN: ${TELEGRAM_BOT_TOKEN:0:10}..."
fi

if [ -z "$TELEGRAM_CHAT_ID" ]; then
    echo "⚠️  TELEGRAM_CHAT_ID не установлен"
else
    echo "✅ TELEGRAM_CHAT_ID: $TELEGRAM_CHAT_ID"
fi

echo ""
echo "🎉 Настройка завершена!"
echo ""
echo "Для тестирования запустите:"
echo "  node dist/index.js"
echo ""
echo "Или используйте интеграцию с основным приложением."
