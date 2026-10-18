import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// ---------- .env → Secrets.kt 注入 ----------
// 本地配置放项目根目录 .env（从 env.example 复制），构建时生成 Kotlin 常量。
val envValues: Map<String, String> = run {
    val f = rootProject.file(".env")
    if (!f.exists()) {
        emptyMap()
    } else {
        f.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
            .associate {
                val i = it.indexOf('=')
                it.substring(0, i).trim() to it.substring(i + 1).trim()
            }
    }
}

// local.properties（gitignore）里的本机配置，目前只有 MOCK_MEDIA_BASE。
val localValues: Map<String, String> = run {
    val f = rootProject.file("local.properties")
    if (!f.exists()) {
        emptyMap()
    } else {
        f.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
            .associate {
                val i = it.indexOf('=')
                it.substring(0, i).trim() to it.substring(i + 1).trim()
            }
    }
}

val generateSecrets by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/mojiSecrets")
    outputs.dir(outDir)
    inputs.properties(envValues)
    doLast {
        val dir = outDir.get().asFile
        dir.mkdirs()
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
        val body = buildString {
            appendLine("package com.moji.offline.config")
            appendLine()
            appendLine("// 由 gradle 从项目根目录 .env 生成；修改 .env 后重新构建即可。")
            appendLine("object Secrets {")
            appendLine("    const val QWEN_API_KEY: String = \"${esc(envValues["QWEN_API_KEY"] ?: "")}\"")
            appendLine("    const val QWEN_MODEL: String = \"${esc(envValues["QWEN_MODEL"] ?: "qwen-plus")}\"")
            appendLine("    const val QWEN_BASE_URL: String = \"${esc(envValues["QWEN_BASE_URL"] ?: "https://dashscope.aliyuncs.com/compatible-mode/v1")}\"")
            appendLine("    const val QWEN_TTS_MODEL: String = \"${esc(envValues["QWEN_TTS_MODEL"] ?: "qwen-tts")}\"")
            appendLine("    const val QWEN_TTS_VOICE: String = \"${esc(envValues["QWEN_TTS_VOICE"] ?: "Lenn")}\"")
            appendLine("    const val QWEN_TTS_BASE_URL: String = \"${esc(envValues["QWEN_TTS_BASE_URL"] ?: "https://dashscope.aliyuncs.com/api/v1")}\"")
            appendLine("    const val CONTENT_BASE_URL: String = \"${esc(envValues["CONTENT_BASE_URL"] ?: "")}\"")
            appendLine()
            appendLine("    /** 本机媒体 mock 根（手机侧经 adb reverse 访问），空 = 播放器禁用。 */")
            appendLine("    const val MOCK_MEDIA_BASE: String = \"${esc(envValues["MOCK_MEDIA_BASE"] ?: localValues["MOCK_MEDIA_BASE"] ?: "")}\"")
            appendLine("}")
            appendLine()
            appendLine("val hasQwenKey: Boolean get() = Secrets.QWEN_API_KEY.isNotBlank()")
        }
        dir.resolve("Secrets.kt").writeText(body)
    }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // iosX64（Intel 模拟器）已被 Compose Multiplatform 1.11 停止支持
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateSecrets)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)

            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)

            // Loopback book server (原版路径: NanoHTTPD equivalent for both platforms)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.ktor.client.cio)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
            implementation(libs.ktor.client.darwin)
        }
    }
}

sqldelight {
    databases {
        create("MojiDatabase") {
            packageName.set("com.moji.offline.db")
        }
        create("WordBookDatabase") {
            packageName.set("com.moji.offline.wordbook")
            srcDirs.setFrom("src/commonMain/wordbook-sqldelight")
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.moji.offline.resources"
}

android {
    namespace = "com.moji.offline"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.moji.offline"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}
