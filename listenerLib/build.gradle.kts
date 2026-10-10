import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
    kotlin("jvm")   // version inherited from root project (2.3.21)
    id("com.vanniktech.maven.publish") version "0.34.0"
}

// Published as dev.sarari:tap8583-listener. The release workflow passes the
// version from the tag (listener-v1.2.3 → 1.2.3); local builds are snapshots.
group = "dev.sarari"
version = providers.gradleProperty("listenerVersion").getOrElse("1.0.0-SNAPSHOT")

java {
    // Java 11 bytecode — compatible with any JVM 11+ host and Android (with desugaring)
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        // Cap language/API level so the library works in Kotlin 2.0+ host projects
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

    // Kotlin stdlib is provided by host
    compileOnly(kotlin("stdlib"))

    // OkHttp for HTTPS connection pooling and streaming
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation(kotlin("test"))
    testImplementation("org.jpos:jpos:2.1.9")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("tap8583-listener")
    manifest {
        attributes(
            "Implementation-Title"   to "Tap8583 VisualizerLogListener",
            "Implementation-Version" to project.version
        )
    }
}

mavenPublishing {
    configure(KotlinJvm(javadocJar = JavadocJar.Empty(), sourcesJar = true))

    coordinates("dev.sarari", "tap8583-listener", project.version.toString())

    // Uploads to the Central Portal and releases once validation passes.
    publishToMavenCentral(automaticRelease = true)
    // Maven Central requires signed artifacts; CI provides the key. Skipping
    // signing without one lets anyone run publishToMavenLocal.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    pom {
        name.set("Tap8583 Listener")
        description.set(
            "jPOS LogListener that streams ISO 8583 send/receive events from POS apps " +
                "to a Tap8583 server for live decoding and inspection."
        )
        inceptionYear.set("2026")
        url.set("https://github.com/HenryUdorji/Tap8583")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("HenryUdorji")
                name.set("HenryUdorji")
                url.set("https://github.com/HenryUdorji")
            }
        }
        scm {
            url.set("https://github.com/HenryUdorji/Tap8583")
            connection.set("scm:git:https://github.com/HenryUdorji/Tap8583.git")
            developerConnection.set("scm:git:ssh://git@github.com/HenryUdorji/Tap8583.git")
        }
    }
}
