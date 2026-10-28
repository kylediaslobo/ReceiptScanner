import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Supabase URL + anon key: local.properties when building on a PC, environment variables in CI.
// Neither is committed to the repo.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun config(name: String): String? = localProps.getProperty(name) ?: System.getenv(name)

android {
    namespace = "com.kyled.receiptscanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kyled.receiptscanner"
        minSdk = 26
        targetSdk = 35
        versionCode = config("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = config("VERSION_NAME") ?: "0.1.0-dev"

        buildConfigField("String", "SUPABASE_URL", "\"${config("SUPABASE_URL").orEmpty()}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${config("SUPABASE_ANON_KEY").orEmpty()}\"")
    }

    // Release signing comes from a keystore file + passwords (GitHub Actions secrets in CI).
    // Every release must use the same keystore, or Android refuses to install it as an update.
    val keystorePath = config("RELEASE_KEYSTORE_PATH")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = config("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = config("RELEASE_KEY_ALIAS")
                keyPassword = config("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Shrinking is off: the APK is a bit bigger, but it avoids R8 keep-rule surprises
            // with Ktor/supabase-kt reflection in a personal app.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.windowsizeclass)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.functions)
    implementation(libs.ktor.client.okhttp)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
}
