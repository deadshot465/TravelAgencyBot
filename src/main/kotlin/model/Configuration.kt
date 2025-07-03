package org.deadshot465.model

import com.charleskorn.kaml.Yaml
import java.io.File

object Configuration {
    private val configFile = File("config/config.yml")
    private val config = Yaml.default.decodeFromString(Config.serializer(), configFile.readText())

    val token: String = config.token
}