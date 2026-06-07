import java.util.Properties
import com.posthog.android.PostHogCliExecTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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

android {
    namespace = "com.zhousl.aether"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.baimoqilin.aether"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.6.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "POSTHOG_API_KEY", "\"${localProperties.getProperty("posthog.apiKey", "")}\"")
        buildConfigField("String", "POSTHOG_HOST", "\"${localProperties.getProperty("posthog.host", "https://us.i.posthog.com")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.datastore.preferences)
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
    implementation(libs.android.app.process)
    implementation(libs.posthog.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit4)
    testImplementation(libs.squareup.okhttp.mockwebserver)
    testImplementation(libs.json)
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
