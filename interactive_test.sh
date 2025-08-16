#!/bin/bash

echo "🎯 Интерактивный тест Todoist MCP"
echo "=================================="
echo ""
echo "📋 Доступные команды:"
echo "  /todoist status          - статус подключения"
echo "  /todoist projects        - список проектов"
echo "  /todoist tasks           - список задач"
echo "  /todoist add <текст>     - создать задачу"
echo "  /todoist complete <название> - завершить задачу"
echo "  /todoist delete <название>   - удалить задачу"
echo "  /todoist update <название>   - обновить задачу"
echo ""
echo "🚀 Запуск приложения..."
echo "Введите команды по одной, например:"
echo "  /todoist status"
echo "  /todoist add 'Тестовая задача'"
echo "  /todoist tasks"
echo "  exit"
echo ""

# Запускаем приложение в интерактивном режиме
./gradlew run
