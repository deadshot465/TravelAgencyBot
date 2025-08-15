package org.deadshot465.eventHandlers

import com.google.cloud.firestore.Firestore
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Member
import dev.kord.core.entity.Message
import kotlinx.coroutines.flow.toList
import org.deadshot465.model.Plan
import org.deadshot465.model.PlanMapping

suspend fun handleTravelThreadMessage(
    message: Message,
    guildId: Snowflake?,
    member: Member?,
    planMapping: PlanMapping,
    firestore: Firestore
) {
    val collection = firestore.collection("travel_agency_plans")
    val document = collection.document(planMapping.planId).get().get()
    val messages = document.get("messages") as List<*>
    val plan = Plan(
        request = (messages[1] as Map<*, *>)["content"] as String,
        answer = ((messages[4] as Map<*, *>)["content"] as Map<*, *>)["final_result"] as String
    )

    val lastFewWords = plan.answer.drop(plan.answer.length - 50)

    val allPreviousMessages = message
        .channel
        .getMessagesBefore(message.id)
        .toList()
        .reversed()
        .dropWhile { !it.content.endsWith(lastFewWords) }
        .map {
            it.content
        }

    val messagesSoFar = mutableListOf(
        plan.request,
        plan.answer
    )
    messagesSoFar.addAll(allPreviousMessages)
    messagesSoFar += message.content
}