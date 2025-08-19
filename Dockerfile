# Используем готовый образ с Java и Node.js
FROM node:20

# Устанавливаем OpenJDK 21
RUN apt-get update && \
    apt-get install -y openjdk-21-jdk && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Копируем всё
COPY . .

# Устанавливаем gradle
RUN wget -q https://services.gradle.org/distributions/gradle-8.8-bin.zip && \
    unzip -q gradle-8.8-bin.zip && \
    mv gradle-8.8 /opt/gradle && \
    ln -s /opt/gradle/bin/gradle /usr/local/bin/gradle && \
    rm gradle-8.8-bin.zip

# Собираем всё
RUN gradle --no-daemon installDist
RUN cd telegram-mcp-server && npm install && npm run build
RUN cd todoist-mcp-server && npm install && npm run build

# Переменные окружения
ENV GEMINI_API_KEY=""
ENV TELEGRAM_BOT_TOKEN=""
ENV TELEGRAM_CHAT_ID=""
ENV TODOIST_API_KEY=""

# Запуск
CMD ["java", "-cp", "build/install/ai-kotlin-chat/lib/*", "job.JobMainKt", "--intervalSeconds=20", "--useMcpChain=true", "--input=/mcp-chain"]
