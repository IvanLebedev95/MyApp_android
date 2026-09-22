package com.example.myapp_android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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

    @POST("api/register-token")
    suspend fun registerToken(@Body body: Map<String, String>)
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var messageText by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // ✅ Состояние: выдано ли разрешение на уведомления.
    // null = ещё не спрашивали, true = выдано, false = отказано.
    var notificationPermissionGranted by remember {
        mutableStateOf<Boolean?>(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // до Android 13 разрешение не требуется
            }
        )
    }

    // ✅ Лаунчер запроса разрешения
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        notificationPermissionGranted = isGranted
        if (!isGranted) {
            statusMessage = "Уведомления отключены. Включите в настройках приложения."
        }
    }

    // ✅ Запрашиваем разрешение один раз при появлении экрана (только Android 13+)
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            notificationPermissionGranted != true
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Общая функция загрузки
    suspend fun refreshMessages() {
        try {
            messages = RetrofitClient.api.getMessages()
            statusMessage = ""
        } catch (e: Exception) {
            statusMessage = "Ошибка загрузки: ${e.message}"
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
        refreshMessages()
    }

    // Отслеживание жизненного цикла: при возврате в приложение — реконнект + обновление
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    scope.launch {
                        refreshMessages()
                        WebSocketClient.connect { newMessage ->
                            if (messages.none { it.id == newMessage.id }) {
                                messages = listOf(newMessage) + messages
                            }
                        }
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    WebSocketClient.disconnect()
                }
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        // Первое подключение WebSocket при показе экрана
        WebSocketClient.connect { newMessage ->
            scope.launch {
                if (messages.none { it.id == newMessage.id }) {
                    messages = listOf(newMessage) + messages
                }
            }
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            WebSocketClient.disconnect()
        }
    }

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

            if (statusMessage.isNotEmpty()) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    scope.launch {
                        isRefreshing = true
                        refreshMessages()
                        isRefreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
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
}