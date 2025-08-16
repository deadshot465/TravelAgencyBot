package org.deadshot465.model

import com.aallam.openai.api.chat.ChatRole
import kotlinx.serialization.Serializable

@Serializable
data class StoredMessage(
    val role: ChatRole,
    val content: String
)
