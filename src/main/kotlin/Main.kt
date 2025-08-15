package org.deadshot465

import com.google.api.core.ApiFutures
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.DocumentReference
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import dev.kord.core.Kord
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.interaction.string
import org.deadshot465.eventHandlers.handleTravelThreadMessage
import org.deadshot465.model.Configuration
import org.deadshot465.model.PlanMapping
import java.io.File

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
suspend fun main() {
    val kord = Kord(Configuration.token)

    registerGlobalCommands(kord)

    val serviceAccount = File("config/${Configuration.serviceAccountFileName}")
    val options =
        FirebaseOptions.builder().setCredentials(GoogleCredentials.fromStream(serviceAccount.inputStream())).build()

    val firebaseApp = FirebaseApp.initializeApp(options)
    val firestore = FirestoreClient.getFirestore(firebaseApp)
    val collection = firestore.collection("travel_agency_plan_mappings")
    val futures = collection.listDocuments().map(DocumentReference::get)
    val planMappings = ApiFutures.allAsList(futures).get().mapNotNull {
        val threadId = it.get("thread_id") as String?
        if (threadId != null) {
            PlanMapping(
                it.get("plan_id") as String,
                threadId,
                it.get("channel_id") as String?,
                it.get("original_message_id") as String?
            )
        } else {
            null
        }
    }
    val threadIds = planMappings.map { it.threadId }

    kord.on<MessageCreateEvent> {
        if (message.channelId.value.toString() !in threadIds) return@on

        val planMapping = planMappings.find { it.threadId == message.channelId.value.toString() }
        if (planMapping == null) return@on

        handleTravelThreadMessage(message, guildId, member, planMapping, firestore)
    }

    kord.login {
        @OptIn(PrivilegedIntent::class)
        intents += Intent.MessageContent
    }
}

private suspend fun registerGlobalCommands(kord: Kord) {
    kord.createGlobalChatInputCommand("plan", "Plan your next trip with your truly Aspirin Travel Agency") {
        string(
            "request",
            "Describe the trip you are planning, including the destination, date of arrival, focuses, etc."
        ) {
            required = true
        }
    }

    kord.createGlobalChatInputCommand("ping", "Check if the travel agency is online.")
}