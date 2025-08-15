plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "1.4.20"
}

group = "org.deadshot465"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    implementation("dev.kord:kord-core:0.15.0")
    implementation("com.charleskorn.kaml:kaml:0.80.1")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("com.google.firebase:firebase-admin:9.5.0")
    implementation("com.aallam.openai:openai-client:4.0.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(23)
}