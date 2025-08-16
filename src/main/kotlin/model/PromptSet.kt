package org.deadshot465.model

import kotlinx.serialization.Serializable

@Serializable
data class PromptSet(
    val system: String
)
