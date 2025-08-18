# --- Builder ---
FROM gradle:8.8-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle --no-daemon clean installDist

# --- Runtime ---
FROM eclipse-temurin:21-jre
ENV TZ=UTC
WORKDIR /app

# copy the gradle application distribution
COPY --from=build /workspace/build/install/ai-kotlin-chat /app

# env vars expected at runtime
ENV GEMINI_API_KEY=""
ENV TELEGRAM_BOT_TOKEN=""
ENV TELEGRAM_CHAT_ID=""

# default: run the Day8 job (every 20s)
# You can override with: docker run ... java -cp /app/lib/* job.JobMainKt --intervalSeconds=60
CMD ["java","-cp","/app/lib/*","job.JobMainKt","--intervalSeconds=20","--input=/todoist todayTasks"]