import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    kotlin("plugin.serialization") version "2.2.20"
    id("io.ktor.plugin") version "3.3.3"
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation("ch.qos.logback:logback-classic:1.5.21")
            implementation("io.github.oshai:kotlin-logging-jvm:7.0.7")
            implementation("org.jetbrains.exposed:exposed-core:1.0.0-rc-4")
            implementation("org.jetbrains.exposed:exposed-dao:1.0.0-rc-4")
            implementation("org.jetbrains.exposed:exposed-jdbc:1.0.0-rc-4")
            implementation("io.ktor:ktor-client-core")
            implementation("io.ktor:ktor-serialization-kotlinx-json")
            implementation("io.ktor:ktor-client-logging")
            implementation("io.ktor:ktor-client-apache5")
            implementation("io.ktor:ktor-client-content-negotiation")
            implementation("com.zaxxer:HikariCP:7.0.2")
            implementation("com.h2database:h2:2.4.240")
        }
    }
}


compose.desktop {
    application {
        mainClass = "com.dergruenkohl.newsillyimagedownloader.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.dergruenkohl.newsillyimagedownloader"
            packageVersion = "1.0.0"
        }
    }
}
