import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    // Material Icons Extended — pinned to 1.7.3; acceptable for desktop tooling use
    implementation("org.jetbrains.compose.material:material-icons-extended-desktop:1.7.3")
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
}



compose.desktop {
    application {
        mainClass = "com.tap8583.tap8583.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Pkg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "com.tap8583.tap8583"
            packageVersion = "1.0.0"
        }
    }
}

// Prints the app version to stdout so CI can capture it via:
//   ./gradlew -q :desktopApp:printVersion
tasks.register("printVersion") {
    val version = compose.desktop.application.nativeDistributions.packageVersion
    doLast {
        println(version)
    }
}