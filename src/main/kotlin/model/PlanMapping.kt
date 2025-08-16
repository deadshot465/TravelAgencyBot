package org.deadshot465.model

data class PlanMapping(
    val planId: String,
    val threadId: String,
    val channelId: String?,
    val originalMessageId: String?,
)
