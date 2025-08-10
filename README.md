# Gemini Kotlin Chat (CLI)

Простой чат на Kotlin, который обращается к Google Gemini API (`gemini-2.5-flash`) и хранит контекст диалога в памяти процесса.

## Быстрый старт

1) **Создай API‑ключ** в Google AI Studio и сохрани его в переменной окружения `GEMINI_API_KEY`  
   (можно ориентироваться на `.env.example`).

2) **Запуск через IntelliJ IDEA**:
   - Открой проект как Gradle‑проект.
   - Создай Run/Debug конфигурацию *Application* с `Main class: ChatKt`.
   - В конфигурации добавь переменную окружения `GEMINI_API_KEY=<твой ключ>`.
   - Нажми **Run**.

3) **Запуск из терминала** (если установлен Gradle локально; либо используй Gradle из IDEA):
   ```bash
   export GEMINI_API_KEY=AIza...   # macOS/Linux
   gradle run
   ```

> Примечание: в репозиторий wrapper не добавлен; можно генерировать его локально (Gradle task `wrapper`) или запускать через встроенный Gradle из IntelliJ.

## Как пользоваться
В консоли вводите сообщения. Команда `exit` — выход. История диалога сохраняется в процессе и отправляется целиком на каждый запрос.

## Где менять модель
По умолчанию используется `gemini-2.5-flash`. Можно заменить на любую доступную модель, изменив переменную `model` в `Chat.kt`.

## Файлы
- `src/main/kotlin/Chat.kt` — основной код чата
- `build.gradle.kts` / `settings.gradle.kts` — Gradle‑настройки
- `.env.example` — пример файла окружения
- `.gitignore` — исключения для Git