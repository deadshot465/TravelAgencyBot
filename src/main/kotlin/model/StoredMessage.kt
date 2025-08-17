package org.deadshot465.model

import com.aallam.openai.api.chat.ChatRole
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class StoredMessage(
    val role: ChatRole,
    val content: JsonElement
)
