plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.3.21"
    application
}

group = "com.tap8583.server"
version = "2.0.0"

application {
    mainClass.set("com.tap8583.server.ApplicationKt")
}

repositories {
    google()
    mavenCentral()
}

val ktorVersion = "2.3.12"
val logbackVersion = "1.5.6"

dependencies {
    implementation(project(":shared"))

    // Ktor Server Core & CIO engine
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")

    // Kotlinx Serialization JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
}

tasks.jar {
    archiveBaseName.set("tap8583-server")
    manifest {
        attributes("Main-Class" to "com.tap8583.server.ApplicationKt")
    }
}
