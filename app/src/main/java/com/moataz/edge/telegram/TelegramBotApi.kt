package com.moataz.edge.telegram

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

data class TelegramUpdate(
    val updateId: Long,
    val chatId: String,
    val chatUsername: String?,
    val messageId: Int,
    val text: String?,
    val raw: String
)

class TelegramBotApi(private val token: String) {
    private val baseUrl = "https://api.telegram.org/bot$token"

    fun getMe(): String? {
        val response = request("GET", "$baseUrl/getMe")
        val root = checked(response)
        return root.optJSONObject("result")?.optString("username")?.takeIf { it.isNotBlank() }
    }

    fun getUpdates(offset: Long, timeoutSeconds: Int = 25): List<TelegramUpdate> {
        val url = "$baseUrl/getUpdates?offset=$offset&timeout=$timeoutSeconds"
        val root = checked(request("GET", url, readTimeoutMs = (timeoutSeconds + 10) * 1000))
        val result = root.getJSONArray("result")
        return buildList {
            for (index in 0 until result.length()) {
                val rawObject = result.getJSONObject(index)
                parseUpdate(rawObject.toString())?.let(::add)
            }
        }
    }

    fun copyMessage(destinationChat: String, sourceChat: String, messageId: Int) {
        val body = formEncode(
            mapOf(
                "chat_id" to destinationChat,
                "from_chat_id" to sourceChat,
                "message_id" to messageId.toString()
            )
        )
        checked(request("POST", "$baseUrl/copyMessage", body))
    }

    private fun checked(response: String): JSONObject {
        val root = JSONObject(response)
        if (!root.optBoolean("ok", false)) {
            throw IOException(root.optString("description", "Telegram API request failed"))
        }
        return root
    }

    private fun request(
        method: String,
        url: String,
        body: String? = null,
        readTimeoutMs: Int = 35_000
    ): String {
        val connection = (URL(url).openConnection() as HttpsURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            }
        }

        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw IOException("HTTP $status: ${response.take(500)}")
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun formEncode(values: Map<String, String>): String = values.entries.joinToString("&") { (key, value) ->
        "${encode(key)}=${encode(value)}"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    companion object {
        fun parseUpdate(raw: String): TelegramUpdate? = runCatching {
            val update = JSONObject(raw)
            val message = when {
                update.has("message") -> update.getJSONObject("message")
                update.has("channel_post") -> update.getJSONObject("channel_post")
                else -> return null
            }
            val chat = message.getJSONObject("chat")
            TelegramUpdate(
                updateId = update.getLong("update_id"),
                chatId = chat.getLong("id").toString(),
                chatUsername = chat.optString("username").takeIf { it.isNotBlank() },
                messageId = message.getInt("message_id"),
                text = message.optString("text").ifBlank {
                    message.optString("caption").takeIf { it.isNotBlank() }.orEmpty()
                }.takeIf { it.isNotBlank() },
                raw = update.toString()
            )
        }.getOrNull()
    }
}
