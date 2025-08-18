package org.deadshot465.eventHandlers

import com.aallam.openai.api.chat.*
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.core.FinishReason
import com.aallam.openai.api.core.Parameters
import com.aallam.openai.api.model.ModelId
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.reply
import dev.kord.core.entity.Member
import dev.kord.core.entity.Message
import io.ktor.client.request.forms.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.deadshot465.model.*
import org.deadshot465.shared.defaultModelName
import org.deadshot465.shared.openRouterClient
import org.deadshot465.shared.temperatureMedium
import org.deadshot465.shared.utilities.*
import org.slf4j.LoggerFactory

suspend fun handleTravelThreadMessage(
    message: Message,
    guildId: Snowflake?,
    member: Member?,
    planMapping: PlanMapping,
    firestore: Firestore
) {
    if (message.author?.isBot ?: true) {
        return
    }

    val collection = firestore.collection("travel_agency_plans")
    val documentReference = collection.document(planMapping.planId)
    val document = documentReference.get().get()
    val (rawLanguage, messages) = getPreviousMessages(message, document)
    val language = rawLanguage ?: determineLanguage(messages.first().content!!)
    if (rawLanguage == null) {
        updateLanguageSetting(firestore, planMapping, language)
    }

    saveUserMessage(firestore, planMapping, message)

    val response = createResponse(
        messages,
        language,
        document,
        message.channel
    )

    saveAssistantMessage(firestore, planMapping, response)

    message.reply {
        content = response
    }
}

private suspend fun getPreviousMessages(
    message: Message,
    document: DocumentSnapshot
): Pair<Language?, List<ChatMessage>> {
    val messages = document.get("messages") as List<*>
    val plan = Plan(
        request = (messages[1] as Map<*, *>)["content"] as String,
        answer = ((messages[4] as Map<*, *>)["content"] as Map<*, *>)["final_result"] as String
    )

    val rawLanguage = document.get("language") as String?
    val language = if (rawLanguage != null) {
        Language.valueOf(rawLanguage)
    } else {
        null
    }

    val lastFewWords = plan.answer.drop(plan.answer.length - 50)

    val botUser = message.kord.getSelf()

    val allPreviousMessages = message
        .channel
        .getMessagesBefore(message.id)
        .toList()
        .reversed()
        .dropWhile { !it.content.endsWith(lastFewWords) }
        .filter {
            message.author == botUser || !(message.author?.isBot ?: true)
        }
        .map {
            ChatMessage(
                role = if (message.author == botUser) {
                    ChatRole.Assistant
                } else {
                    ChatRole.User
                },
                content = it.content
            )
        }

    val messagesSoFar = mutableListOf(
        ChatMessage(ChatRole.User, plan.request),
        ChatMessage(ChatRole.Assistant, plan.answer)
    )
    messagesSoFar.addAll(allPreviousMessages)
    messagesSoFar += ChatMessage(ChatRole.User, message.content)

    val chain = messagesSoFar.fold(mutableListOf<ChatMessage>()) { acc, message ->
        if (acc.isEmpty()) {
            acc += message
            acc
        } else if (acc.last().role == message.role) {
            val last = acc.last()
            acc.removeLast()
            acc += ChatMessage(role = last.role, content = "${last.content}\n${message.content}")
            acc
        } else {
            acc += message
            acc
        }
    }

    println("Message chain: $chain")

    return Pair(language, chain)
}

private suspend fun createResponse(
    previousMessages: List<ChatMessage>,
    language: Language,
    document: DocumentSnapshot,
    channel: MessageChannelBehavior
): String {
    val systemPrompt = when (language) {
        Language.Chinese -> Configuration.chinese.system
        Language.Japanese -> Configuration.japanese.system
        else -> Configuration.english.system
    }

    val (exportParameters, reviseParameters) = makeToolParameters()

    val messagesChain = mutableListOf(chatMessage {
        role = ChatRole.System
        content = systemPrompt
    })
    messagesChain.addAll(previousMessages)

    var request = chatCompletionRequest {
        model = ModelId(defaultModelName)
        temperature = temperatureMedium
        messages = messagesChain
        toolChoice = ToolChoice.Auto
        tools {
            function(
                name = "export_itinerary",
                description = "Export itinerary to Markdown or plain text format. Use this tool when convert the full itinerary into Markdown or plain text format.",
                parameters = exportParameters
            )
            function(
                name = "revise_itinerary",
                description = "Contact and ask the top planner to revise the entire itinerary when the customer's request is complicated and requires a full revision. Use this tool only when the customer's request is too complex and you cannot answer on your own.",
                parameters = reviseParameters
            )
        }
    }

    // Reasoning effort is a val and cannot be set in the builder. Don't know why the author did that.
    request = request.copy(reasoningEffort = Effort("medium"))
    val logger = LoggerFactory.getLogger({}.javaClass.enclosingClass ?: {}.javaClass)
    var response = openRouterClient.chatCompletion(request)
    var responseMessage = response.choices.first().message

    messagesChain += responseMessage

    val finishReason = response.choices.first().finishReason

    if (finishReason == FinishReason.ToolCalls) {
        val toolCallResults = mutableMapOf<ToolId, String>()
        for (toolCall in responseMessage.toolCalls.orEmpty()) {
            require(toolCall is ToolCall.Function) {
                logger.error("Tool call is not a function.")
            }

            val result = coroutineScope {
                async {
                    toolCall.execute(document, channel)
                }
            }
            toolCallResults[toolCall.id] = result.await()
        }

        toolCallResults.forEach { (id, result) ->
            messagesChain += chatMessage {
                role = ChatRole.Tool
                content = result
                toolCallId = id
            }
        }

        request = request.copy(messages = messagesChain)
        response = openRouterClient.chatCompletion(request)
        responseMessage = response.choices.first().message
        return responseMessage.content ?: "Empty"
    }

    return responseMessage.content ?: "Empty"
}

private fun makeToolParameters(): Pair<Parameters, Parameters> {
    return Pair(Parameters.Empty, Parameters.buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("request") {
                put("type", "string")
                put(
                    "description",
                    "Detailed revision request in running text form, describing the customer's feedbacks, opinions, requests, additional notes, etc. This will be sent and read by the top planner to revise the itinerary accordingly."
                )
            }
        }
        putJsonArray("required") {
            add("request")
        }
        put("additionalProperties", false)
    })
}

private suspend fun ToolCall.Function.execute(
    document: DocumentSnapshot,
    channel: MessageChannelBehavior
): String {
    val result = when (function.name) {
        "export_itinerary" -> exportItinerary(document, channel)
        "revise_itinerary" -> reviseItinerary(defaultJsonSerializer.decodeFromString<ReviseItineraryArguments>(function.arguments))
        else -> ""
    }

    return result
}

private suspend fun exportItinerary(
    document: DocumentSnapshot,
    channel: MessageChannelBehavior
): String {
    val dumps = document.get("dumps") as List<*>
    val generationDump = dumps.mapNotNull {
        try {
            val rawModelString = (it as Map<*, *>)["model"] as String
            val model = LanguageModel.valueOf(rawModelString)
            val isFinalResult = it.getOrDefault("is_final_result", false) as Boolean

            GenerationDump(
                model = model,
                content = it["content"] as String,
                isFinalResult = isFinalResult
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }.lastOrNull { it.isFinalResult!! }

    if (generationDump != null) {
        val finalResult = defaultJsonSerializer.decodeFromString<FinalResult>(generationDump.content).finalResult
        val message = channel.createMessage {
            addFile("travel_plan.txt", ChannelProvider(null) {
                ByteReadChannel(finalResult)
            })
        }
        val payload = mapOf(
            "file_link" to message.attachments.first().url
        )
        return defaultJsonSerializer.encodeToString(payload)
    } else {
        val payload = mapOf(
            "error" to "No travel plan found. Is the top planner still planning?"
        )
        return defaultJsonSerializer.encodeToString(payload)
    }
}

private suspend fun reviseItinerary(arguments: ReviseItineraryArguments): String {
    val finalResult = FinalResult(
        finalResult = "The top planner is currently too busy. Try again later."
    )
    return defaultJsonSerializer.encodeToString(finalResult)
}