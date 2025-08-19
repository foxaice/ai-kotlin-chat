#!/usr/bin/env node
"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const index_js_1 = require("@modelcontextprotocol/sdk/server/index.js");
const stdio_js_1 = require("@modelcontextprotocol/sdk/server/stdio.js");
const types_js_1 = require("@modelcontextprotocol/sdk/types.js");
const https_1 = __importDefault(require("https"));
const url_1 = require("url");
class TelegramMcpServer {
    constructor() {
        this.server = new index_js_1.Server({
            name: 'telegram-mcp-server',
            version: '1.0.0',
        }, {
            capabilities: {
                tools: {},
            },
        });
        this.setupToolHandlers();
        this.setupErrorHandling();
    }
    setupErrorHandling() {
        this.server.onerror = (error) => console.error('[Telegram MCP Error]', error);
        process.on('SIGINT', async () => {
            await this.server.close();
            process.exit(0);
        });
    }
    setupToolHandlers() {
        this.server.setRequestHandler(types_js_1.ListToolsRequestSchema, async () => ({
            tools: [
                {
                    name: 'telegram_send_message',
                    description: 'Отправить сообщение в Telegram чат',
                    inputSchema: {
                        type: 'object',
                        properties: {
                            message: {
                                type: 'string',
                                description: 'Текст сообщения для отправки',
                            },
                            chat_id: {
                                type: 'string',
                                description: 'ID чата (необязательно, если указан в переменных окружения)',
                            },
                            parse_mode: {
                                type: 'string',
                                enum: ['HTML', 'Markdown', 'MarkdownV2'],
                                description: 'Режим парсинга текста',
                            },
                        },
                        required: ['message'],
                    },
                },
                {
                    name: 'telegram_send_formatted_tasks',
                    description: 'Отформатировать и отправить задачи в Telegram',
                    inputSchema: {
                        type: 'object',
                        properties: {
                            tasks_data: {
                                type: 'string',
                                description: 'JSON данные с задачами от Todoist MCP',
                            },
                            chat_id: {
                                type: 'string',
                                description: 'ID чата (необязательно)',
                            },
                        },
                        required: ['tasks_data'],
                    },
                },
            ],
        }));
        this.server.setRequestHandler(types_js_1.CallToolRequestSchema, async (request) => {
            try {
                const { name, arguments: args } = request.params;
                switch (name) {
                    case 'telegram_send_message':
                        return await this.handleSendMessage(args);
                    case 'telegram_send_formatted_tasks':
                        return await this.handleSendFormattedTasks(args);
                    default:
                        throw new Error(`Unknown tool: ${name}`);
                }
            }
            catch (error) {
                console.error('[Telegram MCP] Tool error:', error);
                return {
                    content: [
                        {
                            type: 'text',
                            text: `Error: ${error instanceof Error ? error.message : String(error)}`,
                        },
                    ],
                    isError: true,
                };
            }
        });
    }
    async sendTelegramMessage(token, chatId, text, parseMode = 'HTML') {
        const params = new url_1.URLSearchParams({
            chat_id: chatId,
            text: text,
            parse_mode: parseMode
        });
        return new Promise((resolve, reject) => {
            const options = {
                hostname: 'api.telegram.org',
                port: 443,
                path: `/bot${token}/sendMessage`,
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'Content-Length': Buffer.byteLength(params.toString())
                }
            };
            const req = https_1.default.request(options, (res) => {
                let data = '';
                res.on('data', (chunk) => {
                    data += chunk;
                });
                res.on('end', () => {
                    try {
                        const result = JSON.parse(data);
                        if (result.ok) {
                            resolve(result.result);
                        }
                        else {
                            reject(new Error(`Telegram API error: ${result.description}`));
                        }
                    }
                    catch (e) {
                        reject(new Error(`Failed to parse response: ${data}`));
                    }
                });
            });
            req.on('error', (e) => {
                reject(e);
            });
            req.write(params.toString());
            req.end();
        });
    }
    async handleSendMessage(args) {
        const token = process.env.TELEGRAM_BOT_TOKEN;
        if (!token) {
            throw new Error('TELEGRAM_BOT_TOKEN environment variable is required');
        }
        const message = args.message;
        const chatId = args.chat_id || process.env.TELEGRAM_CHAT_ID;
        const parseMode = args.parse_mode || 'HTML';
        if (!chatId) {
            throw new Error('chat_id argument or TELEGRAM_CHAT_ID environment variable is required');
        }
        console.error(`[Telegram MCP] Sending message to ${chatId}`);
        try {
            const result = await this.sendTelegramMessage(token, chatId, message, parseMode);
            return {
                content: [
                    {
                        type: 'text',
                        text: JSON.stringify({
                            success: true,
                            message_id: result.message_id,
                            sent_at: new Date().toISOString(),
                        }),
                    },
                ],
            };
        }
        catch (error) {
            console.error('[Telegram MCP] Send error:', error);
            throw error;
        }
    }
    async handleSendFormattedTasks(args) {
        const tasksData = args.tasks_data;
        const chatId = args.chat_id || process.env.TELEGRAM_CHAT_ID;
        console.error(`[Telegram MCP] Processing tasks data...`);
        // Форматируем задачи
        const formattedMessage = this.formatTasksWithEmoji(tasksData);
        // Отправляем
        return await this.handleSendMessage({
            message: formattedMessage,
            chat_id: chatId,
            parse_mode: 'HTML',
        });
    }
    parseTasksFromTodoistMcp(tasksData) {
        try {
            // Парсим JSON ответ от MCP
            const mcpResponse = JSON.parse(tasksData);
            // Извлекаем текст из MCP ответа
            let taskText = '';
            if (mcpResponse.result && mcpResponse.result.content && mcpResponse.result.content[0]) {
                taskText = mcpResponse.result.content[0].text || '';
            }
            console.error(`[Telegram MCP] Task text: ${taskText.substring(0, 100)}...`);
            // Парсим текстовый формат задач
            const tasks = [];
            const taskLines = taskText.split('\n').filter(line => line.trim().startsWith('- task:'));
            for (const line of taskLines) {
                try {
                    // Извлекаем данные из формата: - task:[id=123|content="Task"|priority=1|due=date]
                    const taskMatch = line.match(/- task:\[(.*)\]/);
                    if (!taskMatch)
                        continue;
                    const taskData = taskMatch[1];
                    const task = {};
                    // Парсим каждое поле
                    const fields = taskData.split('|');
                    for (const field of fields) {
                        const [key, value] = field.split('=', 2);
                        if (!key || !value)
                            continue;
                        const cleanKey = key.trim();
                        let cleanValue = value.trim();
                        // Убираем кавычки
                        if (cleanValue.startsWith('"') && cleanValue.endsWith('"')) {
                            cleanValue = cleanValue.slice(1, -1);
                        }
                        // Конвертируем типы
                        switch (cleanKey) {
                            case 'id':
                            case 'projectId':
                            case 'priority':
                            case 'order':
                            case 'commentCount':
                                task[cleanKey] = parseInt(cleanValue) || 0;
                                break;
                            case 'isCompleted':
                                task[cleanKey] = cleanValue === 'true';
                                break;
                            case 'due':
                                if (cleanValue && cleanValue !== 'null') {
                                    task[cleanKey] = { date: cleanValue };
                                }
                                break;
                            default:
                                if (cleanValue !== 'null' && cleanValue !== '') {
                                    task[cleanKey] = cleanValue;
                                }
                        }
                    }
                    if (task.content) {
                        tasks.push(task);
                    }
                }
                catch (e) {
                    console.error(`[Telegram MCP] Failed to parse task line: ${line}`, e);
                }
            }
            console.error(`[Telegram MCP] Parsed ${tasks.length} tasks`);
            return tasks;
        }
        catch (error) {
            console.error('[Telegram MCP] Parse error:', error);
            return [];
        }
    }
    formatTasksWithEmoji(tasksData) {
        try {
            const tasks = this.parseTasksFromTodoistMcp(tasksData);
            if (!tasks || tasks.length === 0) {
                return '🎉 <b>Отличная работа!</b>\n\nНет активных задач на сегодня ✅';
            }
            const now = new Date();
            const timeOfDay = now.getHours() < 12 ? 'Доброе утро' : now.getHours() < 18 ? 'Добрый день' : 'Добрый вечер';
            let message = `${this.getTimeEmoji()} <b>${timeOfDay}!</b>\n\n`;
            message += `📋 <b>Ваши задачи на сегодня:</b> ${tasks.length} шт.\n\n`;
            tasks.forEach((task, index) => {
                const priority = this.getPriorityIcon(task.priority || 1);
                const content = task.content || 'Без названия';
                const dueInfo = task.due ? ' ⏰ сегодня' : '';
                message += `${priority} ${index + 1}. ${content}${dueInfo}\n`;
                if (task.description && task.description.trim()) {
                    message += `   💬 ${task.description}\n`;
                }
            });
            message += '\n💪 <i>Удачного дня и продуктивной работы!</i>';
            return message;
        }
        catch (error) {
            console.error('[Telegram MCP] Format error:', error);
            return `❌ Ошибка форматирования задач: ${error instanceof Error ? error.message : String(error)}`;
        }
    }
    getPriorityIcon(priority) {
        switch (priority) {
            case 4: return '🚨';
            case 3: return '🔴';
            case 2: return '🟡';
            case 1:
            default: return '⚪';
        }
    }
    getTimeEmoji() {
        const hour = new Date().getHours();
        if (hour < 6)
            return '🌙';
        if (hour < 12)
            return '🌅';
        if (hour < 18)
            return '☀️';
        return '🌆';
    }
    async run() {
        const transport = new stdio_js_1.StdioServerTransport();
        await this.server.connect(transport);
        console.error('Telegram MCP server running on stdio');
    }
}
const server = new TelegramMcpServer();
server.run().catch(console.error);
