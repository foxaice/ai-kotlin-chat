#!/bin/bash

# Скрипт для установки и настройки Todoist MCP сервера
# для Gemini Kotlin Chat

set -e

echo "🚀 Установка Todoist MCP сервера для Gemini Kotlin Chat"
echo "=================================================="

# Проверяем наличие необходимых инструментов
check_requirements() {
    echo "📋 Проверка требований..."
    
    if ! command -v git &> /dev/null; then
        echo "❌ Git не установлен. Установите Git и повторите попытку."
        exit 1
    fi
    
    if ! command -v node &> /dev/null; then
        echo "❌ Node.js не установлен. Установите Node.js и повторите попытку."
        exit 1
    fi
    
    if ! command -v npm &> /dev/null; then
        echo "❌ npm не установлен. Установите npm и повторите попытку."
        exit 1
    fi
    
    echo "✅ Все требования выполнены"
}

# Клонирование репозитория
clone_repository() {
    echo "📥 Клонирование Todoist MCP сервера..."
    
    if [ -d "todoist-mcp-server" ]; then
        echo "⚠️ Директория todoist-mcp-server уже существует"
        read -p "Удалить существующую директорию? (y/N): " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            rm -rf todoist-mcp-server
        else
            echo "❌ Установка прервана"
            exit 1
        fi
    fi
    
    git clone https://github.com/abhiz123/todoist-mcp-server.git
    echo "✅ Репозиторий склонирован"
}

# Установка зависимостей
install_dependencies() {
    echo "📦 Установка зависимостей..."
    cd todoist-mcp-server
    
    if [ -f "package.json" ]; then
        npm install
        echo "✅ Зависимости установлены"
    else
        echo "⚠️ package.json не найден, пропускаем установку зависимостей"
    fi
    
    cd ..
}

# Настройка исполняемости
make_executable() {
    echo "🔧 Настройка исполняемости..."
    
    if [ -f "todoist-mcp-server/todoist-mcp-server" ]; then
        chmod +x todoist-mcp-server/todoist-mcp-server
        echo "✅ Файл сделан исполняемым"
    else
        echo "⚠️ Исполняемый файл не найден"
        echo "Проверьте содержимое директории todoist-mcp-server"
        ls -la todoist-mcp-server/
    fi
}

# Проверка установки
test_installation() {
    echo "🧪 Тестирование установки..."
    
    if [ -x "todoist-mcp-server/todoist-mcp-server" ]; then
        echo "✅ Todoist MCP сервер установлен и готов к использованию"
        echo "📁 Путь: $(pwd)/todoist-mcp-server/todoist-mcp-server"
    else
        echo "❌ Установка не завершена успешно"
        exit 1
    fi
}

# Настройка переменных окружения
setup_environment() {
    echo "🔑 Настройка переменных окружения..."
    
    # Проверяем наличие .bashrc или .zshrc
    local shell_rc=""
    if [ -f "$HOME/.bashrc" ]; then
        shell_rc="$HOME/.bashrc"
    elif [ -f "$HOME/.zshrc" ]; then
        shell_rc="$HOME/.zshrc"
    else
        echo "⚠️ Не найден .bashrc или .zshrc, создайте переменные окружения вручную"
        return
    fi
    
    echo "" >> "$shell_rc"
    echo "# Todoist MCP Server для Gemini Kotlin Chat" >> "$shell_rc"
    echo "export TODOIST_MCP_SERVER_PATH=\"$(pwd)/todoist-mcp-server/todoist-mcp-server\"" >> "$shell_rc"
    
    echo "✅ Переменная окружения добавлена в $shell_rc"
    echo "🔄 Перезапустите терминал или выполните: source $shell_rc"
}

# Основная функция
main() {
    check_requirements
    clone_repository
    install_dependencies
    make_executable
    test_installation
    setup_environment
    
    echo ""
    echo "🎉 Установка завершена!"
    echo ""
    echo "📋 Следующие шаги:"
    echo "1. Получите API ключ Todoist: https://todoist.com/app/settings/integrations/developer"
    echo "2. Установите переменную окружения:"
    echo "   export TODOIST_API_KEY=\"your_api_key_here\""
    echo "3. Перезапустите терминал или выполните:"
    echo "   source $HOME/.bashrc  # или .zshrc"
    echo "4. Запустите ваше приложение: ./gradlew run"
    echo ""
    echo "📚 Дополнительная информация в README_TODOIST.md"
}

# Запуск скрипта
main "$@"
