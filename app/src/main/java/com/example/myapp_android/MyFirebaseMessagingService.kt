package com.example.myapp_android

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "MyFirebaseMessagingService"

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Новый FCM-токен: $token")
        sendTokenToServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM-сообщение: ${remoteMessage.data}")
        // Если приложение на переднем плане, система не покажет уведомление автоматически.
        // Можно добавить показ через NotificationManager, если понадобится.
    }

    private fun sendTokenToServer(token: String) {
        // Получаем JWT-токен пользователя. Если его нет — сервер не примет токен.
        val jwt = TokenStorage.get() ?: run {
            Log.w(TAG, "JWT-токен не найден, FCM-токен не отправлен")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                RetrofitClient.api.registerToken(
                    auth = "Bearer $jwt",
                    body = mapOf("token" to token)
                )
                Log.d(TAG, "FCM-токен отправлен на сервер")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка отправки FCM-токена: ${e.message}")
            }
        }
    }
}