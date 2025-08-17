package org.deadshot465.shared.utilities

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.float
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

val defaultJsonSerializer = Json {
    prettyPrint = false
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun JsonElement.toAny(): Any? {
    return when (this) {
        is JsonPrimitive -> when {
            this.isString -> this.content
            this.booleanOrNull != null -> this.boolean
            this.intOrNull != null -> this.int
            this.longOrNull != null -> this.long
            this.doubleOrNull != null -> this.double
            this.floatOrNull != null -> this.float
            else -> this.content
        }
        is JsonArray -> this.map { it.toAny() }
        is JsonObject -> this.mapValues { it.value.toAny() }
        JsonNull -> null
    }
}

inline fun <reified T> encodeToMap(value: T): Map<String, Any?> {
    val element = defaultJsonSerializer.encodeToJsonElement(value)
    require(element is JsonObject) {
        "Top-level element must be an object."
    }
    return element.mapValues { it.value.toAny() }
}