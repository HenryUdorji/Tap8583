plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutinesSwing)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlin.testJunit)
                implementation(libs.junit)
            }
        }
    }
}