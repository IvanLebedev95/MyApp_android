package com.example.myapp_android

import android.util.Log
import com.google.gson.Gson
import okhttp3.*
import java.util.concurrent.TimeUnit

object WebSocketClient {
    private const val TAG = "WebSocketClient"

    // ⚠️ wss:// для HTTPS, ws:// для HTTP
    private const val WS_URL = "wss://myapp-ivan.ru/ws/messages"

    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS) // чтобы соединение не обрывалось
        .build()

    private var webSocket: WebSocket? = null
    private val gson = Gson()

    fun connect(onMessage: (Message) -> Unit) {
        val request = Request.Builder().url(WS_URL).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket подключён")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = gson.fromJson(text, Message::class.java)
                    onMessage(message)
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка парсинга: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket ошибка: ${t.message}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket закрыт: $reason")
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Activity destroyed")
        webSocket = null
    }
}