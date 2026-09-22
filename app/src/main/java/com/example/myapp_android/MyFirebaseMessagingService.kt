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
        Log.d(TAG, "Получено FCM-сообщение: ${remoteMessage.data}")

        // Если приложение на переднем плане, система не покажет уведомление автоматически.
        // Здесь можно показать его вручную через NotificationHelper (если он у вас есть).
        // Сейчас достаточно просто залогировать.
    }

    private fun sendTokenToServer(token: String) {
        // Запускаем корутину в фоновом потоке, потому что registerToken — suspend-функция
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RetrofitClient.api.registerToken(mapOf("token" to token))
                Log.d(TAG, "Токен успешно отправлен на сервер")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка отправки токена: ${e.message}")
            }
        }
    }
}