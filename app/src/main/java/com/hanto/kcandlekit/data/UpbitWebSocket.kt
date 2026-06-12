package com.hanto.kcandlekit.data

import com.google.gson.Gson
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

private const val WS_URL = "wss://api.upbit.com/websocket/v1"
private val gson = Gson()

// 연결을 collect 하는 동안 유지하고, 취소 시 자동으로 닫히는 cold Flow.
// Upbit은 UTF-8 인코딩된 Binary 프레임으로 데이터를 전송.
fun upbitTickerFlow(client: OkHttpClient, market: String): Flow<UpbitTickerMessage> =
    callbackFlow {
        val ws = client.newWebSocket(
            Request.Builder().url(WS_URL).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(
                        """[{"ticket":"kcandlekit"},{"type":"ticker","codes":["$market"],"isOnlyRealtime":true}]"""
                    )
                }

                // Upbit 실제 데이터는 ByteString (binary frame)으로 옴
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    runCatching { gson.fromJson(bytes.utf8(), UpbitTickerMessage::class.java) }
                        .onSuccess { if (it.type == "ticker") trySend(it) }
                }

                // "CONNECTED" 등 텍스트 프레임은 무시
                override fun onMessage(webSocket: WebSocket, text: String) = Unit

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    close(t)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    close()
                }
            }
        )
        awaitClose { ws.close(1000, "cancelled") }
    }
