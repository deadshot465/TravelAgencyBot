package org.deadshot465.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FinalResult(
    @SerialName("final_result") val finalResult: String
)
