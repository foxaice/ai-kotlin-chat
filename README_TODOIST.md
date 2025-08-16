# 🎯 Todoist MCP интеграция для Gemini Kotlin Chat

Полная интеграция Todoist с приложением Gemini Kotlin Chat через Model Context Protocol (MCP).

## ✨ Что мы достигли

### ✅ **MCP интеграция:**
- **Реальное подключение** к Todoist MCP серверу через stdio
- **Автоматическая инициализация** MCP протокола
- **Прямое взаимодействие** с MCP сервером
- **Автоматическое переключение** между MCP и прямым API

### ✅ **Все команды Todoist:**
- `/todoist projects` - список проектов (через API)
- `/todoist tasks` - список задач (через MCP)
- `/todoist add <текст>` - создание задач (через MCP)
- `/todoist complete <название>` - завершение задач (через MCP)
- `/todoist delete <название>` - удаление задач (через MCP)
- `/todoist update <название>` - обновление задач (через MCP)
- `/todoist status` - статус подключения

### ✅ **Умные возможности:**
- **Работа по названию** - можно указывать название задачи вместо ID
- **Естественный язык** для дат (tomorrow, next Monday, etc.)
- **Приоритеты задач** (1-4)
- **Автоматическое определение** ID vs название

## 🚀 Установка и настройка

### 1. **Клонирование Todoist MCP сервера:**
```bash
git clone https://github.com/abhiz123/todoist-mcp-server.git
cd todoist-mcp-server
npm install
npm run build
cd ..
```

### 2. **Установка переменных окружения:**
```bash
# Todoist API ключ (получите на https://todoist.com/app/settings/integrations/developer)
export TODOIST_API_KEY="your_api_key_here"

# Путь к MCP серверу
export TODOIST_MCP_SERVER_PATH="$(pwd)/todoist-mcp-server/dist/index.js"

# MCP сервер ожидает эту переменную
export TODOIST_API_TOKEN="$TODOIST_API_KEY"
```

### 3. **Запуск приложения:**
```bash
./gradlew run
```

## 🔧 Использование команд

### **Создание задач:**
```
/todoist add Создать презентацию
/todoist add Важная задача завтра priority 3
/todoist add Простая задача
```

### **Просмотр задач:**
```
/todoist tasks                    # Все задачи
/todoist tasks <project_id>       # Задачи конкретного проекта
```

### **Управление задачами:**
```
/todoist complete Простая задача  # Завершить по названию
/todoist complete 12345           # Завершить по ID
/todoist delete Важная задача     # Удалить по названию
/todoist update Создать презентацию  # Обновить задачу
```

### **Статус и информация:**
```
/todoist status                   # Статус подключения
/todoist projects                 # Список проектов
```

## 🔮 MCP возможности

### **Естественный язык для дат:**
- `tomorrow` - завтра
- `next Monday` - следующий понедельник
- `Jan 23` - 23 января
- `in 2 days` - через 2 дня

### **Приоритеты задач:**
- `1` - низкий приоритет
- `2` - нормальный приоритет
- `3` - высокий приоритет
- `4` - срочный приоритет

### **Фильтры для задач:**
- `today` - задачи на сегодня
- `overdue` - просроченные задачи
- `priority 1` - задачи с высоким приоритетом
- `next week` - задачи на следующую неделю

## 🏗️ Архитектура

### **Компоненты:**
1. **`TodoistMcp`** - MCP клиент для взаимодействия с сервером
2. **`TodoistApiClient`** - прямой API клиент для fallback
3. **`TodoistManager`** - менеджер, переключающийся между MCP и API
4. **`Chat.kt`** - основное приложение с обработкой команд

### **Поток данных:**
```
Пользователь → Chat.kt → TodoistManager → TodoistMcp → MCP Сервер → Todoist API
                                    ↓
                              TodoistApiClient → Todoist API (fallback)
```

### **Автоматическое переключение:**
- **MCP приоритет** - если сервер доступен
- **API fallback** - если MCP недоступен
- **Прозрачное переключение** для пользователя

## 🧪 Тестирование

### **Автоматический тест:**
```bash
chmod +x test_mcp.sh
./test_mcp.sh
```

### **Интерактивный тест:**
```bash
chmod +x interactive_test.sh
./interactive_test.sh
```

### **Ручное тестирование:**
```bash
./gradlew run
# Введите команды по одной
```

## 🔍 Отладка

### **Проверка MCP сервера:**
```bash
cd todoist-mcp-server
node dist/index.js
# Должно показать: "Todoist MCP Server running on stdio"
```

### **Проверка переменных:**
```bash
echo "TODOIST_API_KEY: ${TODOIST_API_KEY}"
echo "TODOIST_API_TOKEN: ${TODOIST_API_TOKEN}"
echo "TODOIST_MCP_SERVER_PATH: ${TODOIST_MCP_SERVER_PATH}"
```

### **Логи приложения:**
- ✅ "Подключен к Todoist MCP серверу" - MCP работает
- ⚠️ "MCP сервер недоступен, используется API" - fallback на API
- ❌ "Ошибка подключения" - проблемы с настройкой

## 🎉 Результат

**MCP интеграция полностью готова и работает!**

Приложение автоматически:
- ✅ Подключается к MCP серверу через `node`
- ✅ Обрабатывает все команды Todoist
- ✅ Переключается между MCP и API при необходимости
- ✅ Интегрируется с Gemini для умного управления задачами
- ✅ Поддерживает естественный язык и умные фильтры

## 📚 Дополнительные ресурсы

- [Todoist MCP Server](https://github.com/abhiz123/todoist-mcp-server)
- [Model Context Protocol](https://modelcontextprotocol.io/)
- [Todoist API Documentation](https://developer.todoist.com/)
- [Gemini API](https://ai.google.dev/docs/gemini_api_overview)
