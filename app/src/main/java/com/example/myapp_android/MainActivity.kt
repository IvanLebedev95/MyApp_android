package com.example.myapp_android

import android.Manifest
import android.content.Context
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

// Модели
data class Message(val id: Int, val text: String, val createdAt: String)
data class SendMessageRequest(val text: String)
data class AuthRequest(val username: String, val password: String)
data class AuthResponse(
    val token: String? = null,
    val message: String? = null,
    val error: String? = null,
    val code: String? = null
)

// API
interface ApiService {
    @GET("api/messages")
    suspend fun getMessages(@Header("Authorization") auth: String): List<Message>

    @POST("api/messages")
    suspend fun sendMessage(@Header("Authorization") auth: String, @Body request: SendMessageRequest)

    @POST("api/auth/register")
    suspend fun register(@Body request: AuthRequest): AuthResponse

    @POST("api/auth/login")
    suspend fun login(@Body request: AuthRequest): AuthResponse

    @POST("api/register-token")
    suspend fun registerToken(@Header("Authorization") auth: String, @Body body: Map<String, String>)
}

// Retrofit
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

// Хранилище токена
object TokenStorage {
    private const val PREFS = "auth_prefs"
    private const val KEY_TOKEN = "jwt_token"
    private lateinit var masterKey: MasterKey
    private lateinit var sharedPreferences: android.content.SharedPreferences

    fun init(context: Context) {
        masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        sharedPreferences = EncryptedSharedPreferences.create(
            context,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(token: String) {
        sharedPreferences.edit().putString(KEY_TOKEN, token).apply()
    }

    fun get(): String? = sharedPreferences.getString(KEY_TOKEN, null)
    fun clear() { sharedPreferences.edit().clear().apply() }
}

// MainActivity
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TokenStorage.init(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RootScreen()
                }
            }
        }
    }
}

@Composable
fun RootScreen() {
    val context = LocalContext.current
    var token by remember { mutableStateOf(TokenStorage.get()) }

    if (token == null) {
        AuthScreen(onLoggedIn = { newToken ->
            TokenStorage.save(newToken)
            token = newToken
        })
    } else {
        MessageScreen(token = token!!, onLogout = {
            TokenStorage.clear()
            token = null
        })
    }
}

@Composable
fun AuthScreen(onLoggedIn: (String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var isRegisterMode by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(if (isRegisterMode) "Регистрация" else "Вход", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Логин") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Пароль") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            scope.launch {
                error = ""
                try {
                    val req = AuthRequest(username, password)
                    if (isRegisterMode) {
                        val resp = RetrofitClient.api.register(req)
                        error = resp.message ?: resp.error ?: "Готово"
                        isRegisterMode = false
                    } else {
                        val resp = RetrofitClient.api.login(req)
                        if (resp.token != null) onLoggedIn(resp.token)
                        else if (resp.code == "PENDING_APPROVAL") error = "⏳ " + (resp.error ?: "Ожидайте одобрения")
                        else error = resp.error ?: "Ошибка входа"
                    }
                } catch (e: Exception) { error = "Ошибка: ${e.message}" }
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text(if (isRegisterMode) "Зарегистрироваться" else "Войти")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { isRegisterMode = !isRegisterMode; error = "" }) {
            Text(if (isRegisterMode) "Уже есть аккаунт? Войти" else "Нет аккаунта? Зарегистрироваться")
        }
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(token: String, onLogout: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var messageText by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val authHeader = "Bearer $token"

    suspend fun refreshMessages() {
        try {
            messages = RetrofitClient.api.getMessages(authHeader)
            statusMessage = ""
        } catch (e: Exception) { statusMessage = "Ошибка загрузки: ${e.message}" }
    }

    fun sendMessage() {
        if (messageText.isBlank()) return
        scope.launch {
            try {
                RetrofitClient.api.sendMessage(authHeader, SendMessageRequest(messageText))
                statusMessage = "Сохранено успешно!"
                messageText = ""
            } catch (e: Exception) { statusMessage = "Ошибка: ${e.message}" }
        }
    }

    LaunchedEffect(Unit) { refreshMessages() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    scope.launch {
                        refreshMessages()
                        WebSocketClient.connect { newMessage ->
                            if (messages.none { it.id == newMessage.id }) messages = listOf(newMessage) + messages
                        }
                    }
                }
                Lifecycle.Event.ON_STOP -> WebSocketClient.disconnect()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        WebSocketClient.connect { newMessage ->
            scope.launch { if (messages.none { it.id == newMessage.id }) messages = listOf(newMessage) + messages }
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            WebSocketClient.disconnect()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мои сообщения") },
                actions = { TextButton(onClick = onLogout) { Text("Выйти") } }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = messageText, onValueChange = { messageText = it }, label = { Text("Введите текст") }, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { sendMessage() }) { Text("Отправить") }
            }
            if (statusMessage.isNotEmpty()) {
                Text(text = statusMessage, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { scope.launch { isRefreshing = true; refreshMessages(); isRefreshing = false } },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(messages) { message ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(message.text, style = MaterialTheme.typography.bodyLarge)
                                Text(message.createdAt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}