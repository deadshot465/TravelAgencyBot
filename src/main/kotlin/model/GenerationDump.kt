package org.deadshot465.model

import kotlinx.serialization.Serializable

@Serializable
data class GenerationDump(
    val model: LanguageModel,
    val content: String
)
