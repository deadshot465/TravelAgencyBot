package org.deadshot465.shared

import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import org.deadshot465.model.Configuration

private val config = OpenAIConfig(
    token = Configuration.openRouterApiKey,
    host = OpenAIHost(baseUrl = "https://openrouter.ai/api/v1/")
)

val openRouterClient = OpenAI(config)
