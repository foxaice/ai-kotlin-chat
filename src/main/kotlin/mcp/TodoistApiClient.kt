package mcp

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.time.Duration

data class TodoistProject(
    val id: String,
    val name: String,
    val color: String?,
    val parent_id: String?,
    val order: Int,
    val comment_count: Int,
    val shared: Boolean,
    val favorite: Boolean,
    val sync_id: String?,
    val url: String,
    val is_inbox_project: Boolean,
    val is_team_inbox: Boolean,
    val view_style: String,
    val team_inbox: TodoistTeamInbox?,
    val created_at: String,
    val updated_at: String
)

data class TodoistTeamInbox(
    val team_id: String,
    val team_name: String,
    val team_inbox_id: String,
    val team_inbox_name: String
)

data class TodoistTask(
    val id: String,
    val content: String,
    val description: String?,
    val project_id: String?,
    val section_id: String?,
    val parent_id: String?,
    val order: Int,
    val labels: List<String>,
    val priority: Int,
    val due: TodoistDue?,
    val url: String,
    val comment_count: Int,
    val created_at: String,
    val updated_at: String,
    val assignee_id: String?,
    val assigner_id: String?,
    val creator_id: String?,
    val created_by_request_id: String?,
    val duration: TodoistDuration?,
    val is_completed: Boolean,
    val labels_ids: List<String>?,
    val parent_ids: List<String>?,
    val recurring: Boolean,
    val sync_id: String?,
    val user_id: String?,
    val view_style: String
)

data class TodoistDue(
    val date: String,
    val string: String,
    val lang: String,
    val is_recurring: Boolean,
    val timezone: String?
)

data class TodoistDuration(
    val amount: Int,
    val unit: String
)

data class CreateTaskRequest(
    val content: String,
    val description: String? = null,
    val project_id: String? = null,
    val section_id: String? = null,
    val parent_id: String? = null,
    val order: Int? = null,
    val labels: List<String>? = null,
    val priority: Int? = null,
    val due_string: String? = null,
    val due_date: String? = null,
    val due_datetime: String? = null,
    val due_lang: String? = null,
    val assignee_id: String? = null
)

class TodoistApiClient {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(20))
        .writeTimeout(Duration.ofSeconds(30))
        .readTimeout(Duration.ofSeconds(30))
        .build()
    
    private val json = jacksonObjectMapper()
    private val baseUrl = "https://api.todoist.com/rest/v2"
    private var apiKey: String = ""
    
    fun setApiKey(key: String) {
        apiKey = key
    }
    
    fun getProjects(): List<TodoistProject> {
        val request = Request.Builder()
            .url("$baseUrl/projects")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: "[]"
                    json.readValue(responseBody)
                } else {
                    println("❌ Ошибка получения проектов: ${response.code}")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            println("❌ Ошибка запроса проектов: ${e.message}")
            emptyList()
        }
    }
    
    fun getTasks(projectId: String? = null): List<TodoistTask> {
        val url = if (projectId != null) {
            "$baseUrl/tasks?project_id=$projectId"
        } else {
            "$baseUrl/tasks"
        }
        
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: "[]"
                    json.readValue(responseBody)
                } else {
                    println("❌ Ошибка получения задач: ${response.code}")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            println("❌ Ошибка запроса задач: ${e.message}")
            emptyList()
        }
    }
    
    fun createTask(taskRequest: CreateTaskRequest): TodoistTask? {
        val requestBody = json.writeValueAsString(taskRequest)
            .toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url("$baseUrl/tasks")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()
        
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: "{}"
                    json.readValue(responseBody)
                } else {
                    println("❌ Ошибка создания задачи: ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            println("❌ Ошибка создания задачи: ${e.message}")
            null
        }
    }
    
    fun closeTask(taskId: String): Boolean {
        val request = Request.Builder()
            .url("$baseUrl/tasks/$taskId/close")
            .header("Authorization", "Bearer $apiKey")
            .post("".toRequestBody("application/json".toMediaType()))
            .build()
        
        return try {
            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            println("❌ Ошибка закрытия задачи: ${e.message}")
            false
        }
    }
    
    fun deleteTask(taskId: String): Boolean {
        val request = Request.Builder()
            .url("$baseUrl/tasks/$taskId")
            .header("Authorization", "Bearer $apiKey")
            .delete()
            .build()
        
        return try {
            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            println("❌ Ошибка удаления задачи: ${e.message}")
            false
        }
    }
}
