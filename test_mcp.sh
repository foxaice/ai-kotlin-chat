#!/bin/bash

echo "🧪 Тест MCP команд Todoist"
echo "=========================="

# Создаем тестовые команды
cat > /tmp/mcp_test.txt << 'EOF'
/todoist status
/todoist add Тестовая задача MCP
/todoist tasks
exit
EOF

echo "📋 Тестовые команды:"
cat /tmp/mcp_test.txt

echo ""
echo "🚀 Запуск теста..."

# Запускаем приложение
./gradlew run < /tmp/mcp_test.txt

echo ""
echo "🧹 Очистка..."
rm -f /tmp/mcp_test.txt

echo "✅ Тест завершен!"
