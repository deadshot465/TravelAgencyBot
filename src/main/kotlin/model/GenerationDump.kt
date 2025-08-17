package org.deadshot465.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GenerationDump(
    val model: LanguageModel,
    val content: String,
    @SerialName("is_final_result") val isFinalResult: Boolean?
)
