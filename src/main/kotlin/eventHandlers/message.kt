package org.deadshot465.eventHandlers

import com.aallam.openai.api.chat.*
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.core.Parameters
import com.aallam.openai.api.model.ModelId
import com.google.cloud.firestore.Firestore
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Member
import dev.kord.core.entity.Message
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.deadshot465.model.Configuration
import org.deadshot465.model.Language
import org.deadshot465.model.Plan
import org.deadshot465.model.PlanMapping
import org.deadshot465.shared.defaultModelName
import org.deadshot465.shared.openRouterClient
import org.deadshot465.shared.temperatureMedium
import org.deadshot465.shared.utilities.determineLanguage
import org.slf4j.LoggerFactory

suspend fun handleTravelThreadMessage(
    message: Message,
    guildId: Snowflake?,
    member: Member?,
    planMapping: PlanMapping,
    firestore: Firestore
) {
    val (rawLanguage, messages) = getPreviousMessages(message, planMapping, firestore)
    val language = rawLanguage ?: determineLanguage(messages.first().content!!)


    println("Language: $language")
}

private suspend fun getPreviousMessages(
    message: Message,
    planMapping: PlanMapping,
    firestore: Firestore
): Pair<Language?, List<ChatMessage>> {
    val collection = firestore.collection("travel_agency_plans")
    val document = collection.document(planMapping.planId).get().get()
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
        .map {
            ChatMessage(
                role = if (message.author == botUser) {
                    ChatRole.Assistant } else {
                    ChatRole.User },
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

    return Pair(language, chain)
}

private suspend fun createResponse(previousMessages: List<ChatMessage>, language: Language) {
    val systemPrompt = when (language) {
        Language.Chinese -> Configuration.chinese.system
        Language.Japanese -> Configuration.japanese.system
        else -> Configuration.english.system
    }

    val (exportParameters, reviseParameters) = makeToolParameters()

    val request = chatCompletionRequest {
        model = ModelId(defaultModelName)
        temperature = temperatureMedium
        messages = listOf(chatMessage {
            role = ChatRole.System
            content = systemPrompt
        }) + previousMessages
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
    val requestWithReasoningEffort = request.copy(reasoningEffort = Effort("medium"))
    val logger = LoggerFactory.getLogger({}.javaClass.enclosingClass ?: {}.javaClass)
    val response = openRouterClient.chatCompletion(requestWithReasoningEffort)
}

private fun makeToolParameters(): Pair<Parameters, Parameters> {
    return Pair(Parameters.buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("format") {
                put("type", "string")
                put("description", "The format to export the itinerary to.")
                putJsonArray("enum") {
                    add("markdown")
                    add("plain_text")
                }
            }
        }
        putJsonArray("required") {
            add("format")
        }
        put("additionalProperties", false)
    }, Parameters.buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("request") {
                put("type", "string")
                put("description", "Detailed revision request in running text form, describing the customer's feedbacks, opinions, requests, additional notes, etc. This will be sent and read by the top planner to revise the itinerary accordingly.")
            }
        }
        putJsonArray("required") {
            add("request")
        }
        put("additionalProperties", false)
    })
}