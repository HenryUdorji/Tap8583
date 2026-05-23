plugins {
    kotlin("jvm")   // version inherited from root project (2.3.21)
}

group = "com.tap8583.visualizer"
version = "1.0.0"

java {
    // Java 8 bytecode — compatible with any JVM 8+ host and Android (with desugaring)
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        // Cap language/API level at 1.8 — compatible with Kotlin 1.8, 1.9, and 2.x projects
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        // Suppress warnings that come from the conservative language version cap
        freeCompilerArgs.add("-Xsuppress-version-warnings")
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://jpos.org/maven") }
}

dependencies {
    // jPOS is provided by the host application at runtime — never bundle it
    compileOnly("org.jpos:jpos:2.1.9")

    // Kotlin stdlib is ALWAYS provided by the host Kotlin project — compileOnly avoids
    // version conflicts when the host uses Kotlin 1.6, 1.7, 1.8, 1.9, or 2.x
    compileOnly(kotlin("stdlib"))
}

tasks.jar {
    archiveBaseName.set("visualizer-log-listener")
    manifest {
        attributes(
            "Implementation-Title"   to "Tap8583 VisualizerLogListener",
            "Implementation-Version" to version
        )
    }
}

