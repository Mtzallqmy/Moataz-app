package com.moataz.edge.data

import android.content.Context
import com.moataz.edge.security.SecretStore

class ConfigStore(context: Context) {
    private val preferences = context.getSharedPreferences("edge_config", Context.MODE_PRIVATE)
    private val secretStore = SecretStore(context)

    fun saveBotToken(token: String) {
        require(token.isNotBlank()) { "Bot token must not be blank" }
        secretStore.put(KEY_BOT_TOKEN, token.trim())
    }

    fun botToken(): String? = secretStore.get(KEY_BOT_TOKEN)
    fun hasBotToken(): Boolean = secretStore.contains(KEY_BOT_TOKEN)

    fun saveGithubToken(token: String) {
        require(token.isNotBlank()) { "GitHub token must not be blank" }
        secretStore.put(KEY_GITHUB_TOKEN, token.trim())
    }

    fun githubToken(): String? = secretStore.get(KEY_GITHUB_TOKEN)
    fun hasGithubToken(): Boolean = secretStore.contains(KEY_GITHUB_TOKEN)

    fun saveRouting(sourceChat: String, destinationChat: String) {
        preferences.edit()
            .putString(KEY_SOURCE_CHAT, sourceChat.trim())
            .putString(KEY_DESTINATION_CHAT, destinationChat.trim())
            .apply()
    }

    fun sourceChat(): String = preferences.getString(KEY_SOURCE_CHAT, "").orEmpty()
    fun destinationChat(): String = preferences.getString(KEY_DESTINATION_CHAT, "").orEmpty()

    fun nodeName(): String = preferences.getString(KEY_NODE_NAME, "عقدة الهاتف").orEmpty()
    fun setNodeName(value: String) = preferences.edit().putString(KEY_NODE_NAME, value.trim().ifBlank { "عقدة الهاتف" }).apply()

    fun autoStart(): Boolean = preferences.getBoolean(KEY_AUTO_START, false)
    fun setAutoStart(value: Boolean) = preferences.edit().putBoolean(KEY_AUTO_START, value).apply()

    fun telegramOffset(): Long = preferences.getLong(KEY_TELEGRAM_OFFSET, 0L)
    fun saveTelegramOffset(value: Long) = preferences.edit().putLong(KEY_TELEGRAM_OFFSET, value).apply()

    private companion object {
        const val KEY_BOT_TOKEN = "telegram_bot_token"
        const val KEY_GITHUB_TOKEN = "github_access_token"
        const val KEY_SOURCE_CHAT = "source_chat"
        const val KEY_DESTINATION_CHAT = "destination_chat"
        const val KEY_NODE_NAME = "node_name"
        const val KEY_AUTO_START = "auto_start"
        const val KEY_TELEGRAM_OFFSET = "telegram_offset"
    }
}
