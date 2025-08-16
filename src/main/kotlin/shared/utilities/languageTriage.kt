package org.deadshot465.shared.utilities

import com.aallam.openai.api.chat.ToolCall
import com.aallam.openai.api.chat.ToolChoice
import com.aallam.openai.api.chat.chatCompletionRequest
import com.aallam.openai.api.core.Parameters
import com.aallam.openai.api.model.ModelId
import kotlinx.serialization.json.*
import org.deadshot465.model.Configuration
import org.deadshot465.model.Language
import org.deadshot465.model.LanguageTriageArguments
import org.deadshot465.shared.defaultModelName
import org.deadshot465.shared.openRouterClient
import org.deadshot465.shared.temperatureLow
import org.slf4j.LoggerFactory

suspend fun determineLanguage(message: String): Language {
    val request = chatCompletionRequest {
        model = ModelId(defaultModelName)
        temperature = temperatureLow
        messages {
            system {
                content = Configuration.languageTriagePrompt
            }
            user {
                content = message
            }
        }
        tools {
            function(
                name = "get_language",
                description = "Determine the language of the user's prompt.",
                parameters = Parameters.buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("language") {
                            put("type", "string")
                            put("description", "The language of the user's prompt, e.g. Simplified Chinese, English, Japanese, etc.")
                            putJsonArray("enum") {
                                add("English")
                                add("Chinese")
                                add("Japanese")
                                add("Other")
                            }
                        }
                    }
                    putJsonArray("required") {
                        add("language")
                    }
                    put("additionalProperties", false)
                }
            )
        }
        toolChoice = ToolChoice.function("get_language")
    }

    val logger = LoggerFactory.getLogger({}.javaClass.enclosingClass ?: {}.javaClass)

    val response = openRouterClient.chatCompletion(request)
    val responseMessage = response.choices.first().message
    val toolCall = responseMessage.toolCalls?.first()
    require(toolCall is ToolCall.Function) {
        logger.error("Tool call is not a function.")
    }

    return toolCall.execute()
}

private fun ToolCall.Function.execute(): Language {
    val args = Json.decodeFromString<LanguageTriageArguments>(function.arguments)
    return args.language
}