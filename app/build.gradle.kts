import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    // Kotlin support is built into com.android.application since AGP 9.0 — no separate
    // org.jetbrains.kotlin.android plugin. compose/serialization compiler plugins are unrelated
    // to that and stay applied here.
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Supabase URL/anon key never live in source control (spec-1-1, "Never").
// They are read from local.properties (git-ignored) and exposed only via BuildConfig.
// See local.properties.sample for the template.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        FileInputStream(localPropertiesFile).use { load(it) }
    }
}

// 10.0.2.2 is not a secret (it's the Android emulator's fixed alias for the host
// machine's localhost), so it is a safe default for local Supabase development.
// The anon key IS environment-specific and has no default: the build fails loudly
// if it's missing instead of silently shipping an empty/broken key.
val supabaseUrl: String = localProperties.getProperty("SUPABASE_URL") ?: "http://10.0.2.2:54321"
val supabaseAnonKey: String = localProperties.getProperty("SUPABASE_ANON_KEY")
    ?: if (gradle.startParameter.taskNames.any { it.contains("test", ignoreCase = true) }) {
        "test-anon-key-placeholder"
    } else {
        throw GradleException(
            "SUPABASE_ANON_KEY is not set. Copy local.properties.sample to local.properties " +
                "and fill it in with the anon key printed by `supabase start`."
        )
    }

android {
    namespace = "com.agendamedica.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.agendamedica.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.core:core-ktx:1.15.0")

    // supabase-kt (community client) — BOM pins matching versions for every module.
    // Spec explicitly calls out checking for the latest 3.x at build time.
    implementation(platform("io.github.jan-tennert.supabase:bom:3.1.4"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:functions-kt")

    // supabase-kt needs a Ktor HTTP client engine.
    implementation("io.ktor:ktor-client-okhttp:3.0.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.14.7")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
