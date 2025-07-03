package org.deadshot465

import dev.kord.core.Kord
import dev.kord.rest.builder.interaction.string

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
suspend fun main() {
    val kord = Kord("token")

    registerGlobalCommands(kord)

    kord.login()
}

private suspend fun registerGlobalCommands(kord: Kord) {
    kord.createGlobalChatInputCommand("plan", "Plan your next trip with your truly Aspirin Travel Agency") {
        string("request", "Describe the trip you are planning, including the destination, date of arrival, focuses, etc.") {
            required = true
        }
    }

    kord.createGlobalChatInputCommand("ping", "Check if the travel agency is online.")
}