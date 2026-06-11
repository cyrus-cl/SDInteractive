plugins {
    id("com.android.application")
    kotlin("plugin.compose")
}

val configuredServerBaseUrl: String =
    (project.findProperty("serverBaseUrl") as String?)
        ?: System.getenv("SERVER_BASE_URL")
        ?: "http://10.0.2.2:8081/"

fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

gradle.taskGraph.whenReady {
    val buildsRelease = allTasks.any { it.path.contains(":android-app:assembleRelease") || it.path.contains(":android-app:bundleRelease") }
    val isLocalUrl = configuredServerBaseUrl.contains("127.0.0.1") ||
        configuredServerBaseUrl.contains("10.0.2.2") ||
        configuredServerBaseUrl.contains("localhost")

    if (buildsRelease && isLocalUrl) {
        throw GradleException("Release APK requires -PserverBaseUrl=https://your-public-backend/ or SERVER_BASE_URL.")
    }
}

android {
    namespace = "com.sdinteractive.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sdinteractive.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "DEFAULT_SERVER_URL", configuredServerBaseUrl.asBuildConfigString())
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-database:1.5.1")
    implementation("androidx.media3:media3-datasource:1.5.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
