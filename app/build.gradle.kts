import java.util.Properties
import groovy.json.JsonOutput
import com.posthog.android.PostHogCliExecTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.Sync
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.posthog.android)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

fun localOrEnv(
    localName: String,
    envName: String,
    defaultValue: String = "",
): String = (System.getenv(envName) ?: localProperties.getProperty(localName, defaultValue)).trim()

val posthogCliHost = localOrEnv(
    localName = "posthog.cliHost",
    envName = "POSTHOG_CLI_HOST",
    defaultValue = localProperties.getProperty("posthog.host", "https://us.posthog.com")
        .replace(".i.posthog.com", ".posthog.com"),
)
val posthogProjectId = localOrEnv("posthog.projectId", "POSTHOG_PROJECT_ID")
val posthogCliApiKey = localOrEnv("posthog.cliApiKey", "POSTHOG_CLI_API_KEY")
val posthogExecutable = localOrEnv("posthog.executable", "POSTHOG_EXECUTABLE")
val nightlyKeystoreFile = localOrEnv("nightly.storeFile", "NIGHTLY_KEYSTORE_FILE")
val nightlyKeystorePassword = localOrEnv("nightly.storePassword", "NIGHTLY_KEYSTORE_PASSWORD")
val nightlyKeyAlias = localOrEnv("nightly.keyAlias", "NIGHTLY_KEY_ALIAS")
val nightlyKeyPassword = localOrEnv("nightly.keyPassword", "NIGHTLY_KEY_PASSWORD")
val appVersionName = providers.gradleProperty("aether.versionName")
    .orNull
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: "2.1.6"
val piBridgeProjectDir = rootProject.layout.projectDirectory.dir("pi-bridge")
val piBridgeGeneratedAssetsDir = layout.buildDirectory.dir("generated/assets/piBridge")
val preinstalledExtensionsDir = rootProject.layout.projectDirectory.dir("extensions")
val preinstalledExtensionsGeneratedAssetsDir = layout.buildDirectory.dir("generated/assets/preinstalledExtensions")
val piProviderIconsGeneratedResDir = layout.buildDirectory.dir("generated/res/piProviderIcons")
// Make shared Compose resources available to Android resource APIs.
val sharedComposeResourcesDir = rootProject.project(":shared").projectDir.resolve(
    "src/commonMain/composeResources",
)
val sharedComposeResourcesGeneratedResDir = layout.buildDirectory.dir("generated/res/sharedComposeResources")
val piProviderIconFiles = mapOf(
    "provider_amazon_bedrock.png" to "bedrock-color.png",
    "provider_ant_ling.png" to "antgroup-color.png",
    "provider_anthropic.png" to "anthropic.png",
    "provider_azure_openai_responses.png" to "azure-color.png",
    "provider_cerebras.png" to "cerebras-color.png",
    "provider_cloudflare_ai_gateway.png" to "cloudflare-color.png",
    "provider_cloudflare_workers_ai.png" to "workersai-color.png",
    "provider_deepseek.png" to "deepseek-color.png",
    "provider_fireworks.png" to "fireworks-color.png",
    "provider_github_copilot.png" to "githubcopilot.png",
    "provider_google.png" to "google-color.png",
    "provider_google_vertex.png" to "vertexai-color.png",
    "provider_groq.png" to "groq.png",
    "provider_huggingface.png" to "huggingface-color.png",
    "provider_kimi_coding.png" to "kimi-color.png",
    "provider_minimax.png" to "minimax-color.png",
    "provider_minimax_cn.png" to "minimax-color.png",
    "provider_mistral.png" to "mistral-color.png",
    "provider_moonshotai.png" to "moonshot.png",
    "provider_moonshotai_cn.png" to "moonshot.png",
    "provider_nvidia.png" to "nvidia-color.png",
    "provider_openai.png" to "openai.png",
    "provider_openai_codex.png" to "codex-color.png",
    "provider_openai_compatible.png" to "openai.png",
    "provider_opencode.png" to "opencode.png",
    "provider_opencode_go.png" to "opencode.png",
    "provider_openrouter.png" to "openrouter.png",
    "provider_together.png" to "together-color.png",
    "provider_vercel_ai_gateway.png" to "vercel.png",
    "provider_xai.png" to "xai.png",
    "provider_xiaomi.png" to "xiaomimimo.png",
    "provider_xiaomi_token_plan_ams.png" to "xiaomimimo.png",
    "provider_xiaomi_token_plan_cn.png" to "xiaomimimo.png",
    "provider_xiaomi_token_plan_sgp.png" to "xiaomimimo.png",
    "provider_zai.png" to "zai.png",
    "provider_zai_coding_cn.png" to "zai.png",
)

fun npmExecutable(): String =
    if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"

abstract class SyncGeneratedSourceDirectory : Sync() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        into(outputDirectory)
    }
}

android {
    namespace = "com.zhousl.aether"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.baimoqilin.aether"
        minSdk = 26
        // Alpine/Termux-style local runtimes install executable ELF files into app-private
        // storage. Android blocks execve() from that location for targetSdk >= 29.
        targetSdk = 28
        versionCode = 11
        versionName = appVersionName

        ndk {
            abiFilters += "arm64-v8a"
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "POSTHOG_API_KEY", JsonOutput.toJson(localOrEnv("posthog.apiKey", "POSTHOG_API_KEY")))
        buildConfigField("String", "POSTHOG_HOST", JsonOutput.toJson(
            localOrEnv("posthog.host", "POSTHOG_HOST").ifBlank { "https://us.i.posthog.com" },
        ))
        buildConfigField("String", "UPDATE_CHANNEL", "\"stable\"")
        buildConfigField("Boolean", "SHOWCASE_MODE", "false")
    }

    signingConfigs {
        create("nightly") {
            if (
                nightlyKeystoreFile.isNotBlank() &&
                nightlyKeystorePassword.isNotBlank() &&
                nightlyKeyAlias.isNotBlank() &&
                nightlyKeyPassword.isNotBlank()
            ) {
                storeFile = file(nightlyKeystoreFile)
                storePassword = nightlyKeystorePassword
                keyAlias = nightlyKeyAlias
                keyPassword = nightlyKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_launcher"
            manifestPlaceholders["appRoundIcon"] = "@mipmap/ic_launcher_round"
            manifestPlaceholders["appLabel"] = "@string/app_name"
        }

        create("demo") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".showcase"
            matchingFallbacks += listOf("debug")
            resValue("string", "showcase_app_name", "Aether Showcase")
            manifestPlaceholders["appIcon"] = "@mipmap/ic_launcher"
            manifestPlaceholders["appRoundIcon"] = "@mipmap/ic_launcher_round"
            manifestPlaceholders["appLabel"] = "@string/showcase_app_name"
            buildConfigField("Boolean", "SHOWCASE_MODE", "true")
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            manifestPlaceholders["appIcon"] = "@mipmap/ic_launcher"
            manifestPlaceholders["appRoundIcon"] = "@mipmap/ic_launcher_round"
            manifestPlaceholders["appLabel"] = "@string/app_name"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        create("nightly") {
            initWith(getByName("release"))
            applicationIdSuffix = ".nightly"
            matchingFallbacks += listOf("release")
            resValue("string", "nightly_app_name", "Aether Nightly")
            buildConfigField("String", "UPDATE_CHANNEL", "\"nightly\"")
            manifestPlaceholders["appIcon"] = "@mipmap/ic_launcher_nightly"
            manifestPlaceholders["appRoundIcon"] = "@mipmap/ic_launcher_nightly_round"
            manifestPlaceholders["appLabel"] = "@string/nightly_app_name"
            signingConfig = if (nightlyKeystoreFile.isNotBlank()) {
                signingConfigs.getByName("nightly")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
        aidl = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // targetSdk is intentionally capped at API 28 for local runtime execution.
        disable += "ExpiredTargetSdkVersion"
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":terminal-view"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.google.material)
    implementation(libs.squareup.okhttp)
    implementation(libs.jsoup)
    implementation(libs.flexmark.html2md.converter)
    implementation(libs.snakeyaml)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.posthog.android)
    implementation(libs.sora.editor)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.squareup.okhttp.mockwebserver)
    testImplementation(libs.json)
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.sqlite.bundled)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

tasks.withType<PostHogCliExecTask>().configureEach {
    onlyIf {
        posthogProjectId.isNotBlank() && posthogCliApiKey.isNotBlank()
    }
    postHogHost.set(posthogCliHost)
    if (posthogProjectId.isNotBlank()) {
        postHogProjectId.set(posthogProjectId)
    }
    if (posthogCliApiKey.isNotBlank()) {
        postHogApiKey.set(posthogCliApiKey)
    }
    if (posthogExecutable.isNotBlank()) {
        postHogExecutable.set(posthogExecutable)
    }
}

val installPiBridgeDependencies = tasks.register<Exec>("installPiBridgeDependencies") {
    workingDir = piBridgeProjectDir.asFile
    commandLine(npmExecutable(), "install", "--ignore-scripts", "--legacy-peer-deps")
    inputs.file(piBridgeProjectDir.file("package.json"))
    inputs.file(piBridgeProjectDir.file("package-lock.json"))
    outputs.dir(piBridgeProjectDir.dir("node_modules"))
}

val buildPiBridge = tasks.register<Exec>("buildPiBridge") {
    dependsOn(installPiBridgeDependencies)
    workingDir = piBridgeProjectDir.asFile
    commandLine(npmExecutable(), "run", "build")
    inputs.file(piBridgeProjectDir.file("package.json"))
    inputs.file(piBridgeProjectDir.file("tsconfig.json"))
    inputs.dir(piBridgeProjectDir.dir("scripts"))
    inputs.dir(piBridgeProjectDir.dir("src"))
    outputs.file(piBridgeProjectDir.file("dist/bridge.mjs"))
}

val copySharedComposeResources = tasks.register<SyncGeneratedSourceDirectory>("copySharedComposeResources") {
    outputDirectory.set(sharedComposeResourcesGeneratedResDir)
    from(sharedComposeResourcesDir) {
        include("values*/**")
    }
    // Escape apostrophes in shared i18n strings for Android's resource parser.
    filter { line ->
        if (line.trimStart().startsWith("<string ")) {
            line.replace("'", "\\'").replace("&apos;", "\\'")
        } else {
            line
        }
    }
    includeEmptyDirs = false
}

val copyPiProviderIcons = tasks.register<SyncGeneratedSourceDirectory>("copyPiProviderIcons") {
    dependsOn(installPiBridgeDependencies)
    val iconSourceDir = piBridgeProjectDir.dir(
        "node_modules/@lobehub/icons-static-png/light",
    )
    outputDirectory.set(piProviderIconsGeneratedResDir)
    piProviderIconFiles.forEach { (outputName, sourceName) ->
        from(iconSourceDir.file(sourceName)) {
            into("drawable-nodpi")
            rename { outputName }
        }
    }
    inputs.file(piBridgeProjectDir.file("package-lock.json"))
    piProviderIconFiles.values.distinct().forEach { sourceName ->
        inputs.file(iconSourceDir.file(sourceName))
    }
}

val copyPiBridgeAsset = tasks.register<SyncGeneratedSourceDirectory>("copyPiBridgeAsset") {
    dependsOn(buildPiBridge)
    outputDirectory.set(piBridgeGeneratedAssetsDir)
    from(piBridgeProjectDir.file("dist/bridge.mjs")) {
        into("pi-bridge")
    }
    from(piBridgeProjectDir.file("dist/image-resize-worker.js")) {
        into("pi-bridge")
    }
    from(piBridgeProjectDir.file("dist/photon_rs_bg.wasm")) {
        into("pi-bridge")
    }
    includeEmptyDirs = false
}

val copyPreinstalledExtensions = tasks.register<SyncGeneratedSourceDirectory>("copyPreinstalledExtensions") {
    outputDirectory.set(preinstalledExtensionsGeneratedAssetsDir)
    from(preinstalledExtensionsDir) {
        into("extensions")
    }
    includeEmptyDirs = false
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            copyPiBridgeAsset,
            SyncGeneratedSourceDirectory::outputDirectory,
        )
        variant.sources.assets?.addGeneratedSourceDirectory(
            copyPreinstalledExtensions,
            SyncGeneratedSourceDirectory::outputDirectory,
        )
        variant.sources.res?.addGeneratedSourceDirectory(
            copySharedComposeResources,
            SyncGeneratedSourceDirectory::outputDirectory,
        )
        variant.sources.res?.addGeneratedSourceDirectory(
            copyPiProviderIcons,
            SyncGeneratedSourceDirectory::outputDirectory,
        )
    }
}
