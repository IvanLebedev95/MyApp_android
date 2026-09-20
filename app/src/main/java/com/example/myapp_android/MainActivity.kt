package com.example.myapp_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

// 1. Модели
data class Message(val id: Int, val text: String, val createdAt: String)
data class SendMessageRequest(val text: String)

// 2. API-интерфейс
interface ApiService {
    @GET("api/messages")
    suspend fun getMessages(): List<Message>

    @POST("api/messages")
    suspend fun sendMessage(@Body request: SendMessageRequest)
}

// 3. Retrofit
object RetrofitClient {
    private const val BASE_URL = "https://myapp-ivan.ru/"
    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

// 4. MainActivity
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MessageScreen()
                }
            }
        }
    }
}

// 5. Основной экран
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen() {
    var messageText by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun loadMessages() {
        scope.launch {
            try {
                messages = RetrofitClient.api.getMessages()
            } catch (e: Exception) {
                statusMessage = "Ошибка загрузки: ${e.message}"
            }
        }
    }

    fun sendMessage() {
        if (messageText.isBlank()) return
        scope.launch {
            try {
                RetrofitClient.api.sendMessage(SendMessageRequest(messageText))
                statusMessage = "Сохранено успешно!"
                messageText = ""
            } catch (e: Exception) {
                statusMessage = "Ошибка: ${e.message}"
            }
        }
    }

    // Первая загрузка списка
    LaunchedEffect(Unit) {
        loadMessages()
    }

    // WebSocket на время жизни экрана
    DisposableEffect(Unit) {
        WebSocketClient.connect { newMessage ->
            scope.launch {
                if (messages.none { it.id == newMessage.id }) {
                    messages = listOf(newMessage) + messages
                }
            }
        }
        onDispose {
            WebSocketClient.disconnect()
        }
    }

    // Scaffold сам добавляет системные отступы (статус-бар, навигация)
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Мои сообщения") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Поле ввода + кнопка
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    label = { Text("Введите текст") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { sendMessage() }) {
                    Text("Отправить")
                }
            }

            // Статус операции
            if (statusMessage.isNotEmpty()) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Список сообщений
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(messages) { message ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = message.createdAt,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}