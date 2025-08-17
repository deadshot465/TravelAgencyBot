package org.deadshot465.shared.utilities

import com.aallam.openai.api.chat.ChatRole
import com.google.cloud.firestore.Firestore
import dev.kord.core.entity.Message
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import org.deadshot465.model.*
import org.slf4j.LoggerFactory

fun saveUserMessage(
    firestore: Firestore,
    planMapping: PlanMapping,
    message: Message
) {
    val collection = firestore.collection("travel_agency_plans")
    val documentReference = collection.document(planMapping.planId)
    val document = documentReference.get().get()
    val messages = document.get("messages") as List<*>
    val newMessages = messages.toMutableList()
    newMessages += encodeToMap(
        StoredMessage(
            role = ChatRole.User,
            content = JsonPrimitive(message.content)
        )
    )
    val dumps = document.get("dumps") as List<*>
    val id = document.get("id") as String
    val language = document.get("language") as String?

    val updateArgs = mutableMapOf(
        "id" to id,
        "dumps" to dumps,
        "messages" to newMessages
    )

    if (language != null) {
        updateArgs["language"] = language
    }

    val result = documentReference.set(updateArgs).get()
    val logger = LoggerFactory.getLogger({}.javaClass.enclosingClass ?: {}.javaClass)
    logger.info("Firebase write result: ${result.updateTime}")
}

fun saveAssistantMessage(
    firestore: Firestore,
    planMapping: PlanMapping,
    message: String
) {
    val finalResult = try {
        defaultJsonSerializer.decodeFromString<FinalResult>(message)
    } catch (_: Exception) {
        null
    }

    val collection = firestore.collection("travel_agency_plans")
    val documentReference = collection.document(planMapping.planId)
    val document = documentReference.get().get()

    val messages = document.get("messages") as List<*>
    val newMessages = messages.toMutableList()
    newMessages += encodeToMap(
        StoredMessage(
            role = ChatRole.Assistant,
            content = if (finalResult != null) {
                defaultJsonSerializer.encodeToJsonElement(finalResult)
            } else {
                JsonPrimitive(message)
            }
        )
    )

    val dumps = document.get("dumps") as List<*>
    val newDumps = dumps.toMutableList()
    newDumps += encodeToMap(
        GenerationDump(
            model = LanguageModel.Gemini25Flash,
            content = message,
            isFinalResult = finalResult != null
        )
    )

    val id = document.get("id") as String
    val language = document.get("language") as String?

    val updateArgs = mutableMapOf(
        "id" to id,
        "dumps" to newDumps,
        "messages" to newMessages
    )

    if (language != null) {
        updateArgs["language"] = language
    }

    val result = documentReference.set(updateArgs).get()
    val logger = LoggerFactory.getLogger({}.javaClass.enclosingClass ?: {}.javaClass)
    logger.info("Firebase write result: ${result.updateTime}")
}

fun updateLanguageSetting(
    firestore: Firestore,
    planMapping: PlanMapping,
    language: Language
) {
    val collection = firestore.collection("travel_agency_plans")
    val documentReference = collection.document(planMapping.planId)
    val document = documentReference.get().get()
    val messages = document.get("messages") as List<*>
    val dumps = document.get("dumps") as List<*>
    val id = document.get("id") as String

    val result = documentReference.set(
        mapOf(
            "id" to id,
            "dumps" to dumps,
            "messages" to messages,
            "language" to language.name
        )
    ).get()
    val logger = LoggerFactory.getLogger({}.javaClass.enclosingClass ?: {}.javaClass)
    logger.info("Firebase write result: ${result.updateTime}")
}