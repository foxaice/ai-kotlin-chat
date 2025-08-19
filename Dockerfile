# --- Builder ---
FROM gradle:8.8-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle --no-daemon clean installDist

# --- Runtime with Node.js for MCP servers ---
FROM node:20-slim
ENV TZ=UTC
WORKDIR /app

# Install Java 21 for the main application
RUN apt-get update && \
    apt-get install -y openjdk-21-jre-headless && \
    rm -rf /var/lib/apt/lists/*

# Copy the gradle application distribution
COPY --from=build /workspace/build/install/ai-kotlin-chat /app

# Copy and build MCP servers
COPY telegram-mcp-server /app/telegram-mcp-server
COPY todoist-mcp-server /app/todoist-mcp-server

# Install dependencies and build MCP servers
RUN cd /app/telegram-mcp-server && \
    npm install && \
    npm run build

RUN cd /app/todoist-mcp-server && \
    npm install && \
    npm run build

# env vars expected at runtime
ENV GEMINI_API_KEY=""
ENV TELEGRAM_BOT_TOKEN=""
ENV TELEGRAM_CHAT_ID=""
ENV TODOIST_API_KEY=""
ENV JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"

# default: run the Day9 job with MCP chain (every 20s)
# You can override with: docker run ... java -cp /app/lib/* job.JobMainKt --intervalSeconds=60 --useMcpChain=false
CMD ["java","-cp","/app/lib/*","job.JobMainKt","--intervalSeconds=20","--input=/mcp-chain","--useMcpChain=true"]