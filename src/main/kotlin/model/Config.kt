package org.deadshot465.model

import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val token: String,
    val serviceAccountFileName: String,
    val openRouterApiKey: String
)
