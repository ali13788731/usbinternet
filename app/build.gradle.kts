plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Values can come from gradle.properties, ~/.gradle/gradle.properties or environment variables (CI secrets).
fun prop(name: String, def: String = ""): String =
    (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: def

val keystorePath = prop("KEYSTORE_FILE")
val hasKeystore = keystorePath.isNotEmpty() && file(keystorePath).exists()
val bazaarRsaKey = prop("BAZAAR_RSA_KEY")
if (bazaarRsaKey.isBlank()) {
    logger.warn("WARNING: BAZAAR_RSA_KEY is empty - purchases will NOT be signature-checked. Set it before publishing.")
}

android {
    namespace = "app.usbnet"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.usbnet"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.0"
        buildConfigField("String", "SKU_MONTHLY", "\"${prop("SKU_MONTHLY", "pro_monthly")}\"")
        buildConfigField("String", "SKU_YEARLY", "\"${prop("SKU_YEARLY", "pro_yearly")}\"")
        buildConfigField("String", "PC_DOWNLOAD_URL", "\"${prop("PC_DOWNLOAD_URL")}\"")
        buildConfigField("String", "CONTROL_SECRET", "\"${prop("CONTROL_SECRET", "pv-ctl-7Gq2xK9mWd4RtLz8")}\"")
        buildConfigField("String", "BAZAAR_RSA_KEY", "\"$bazaarRsaKey\"")
    }

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = prop("KEYSTORE_PASSWORD")
                keyAlias = prop("KEY_ALIAS")
                keyPassword = prop("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.work:work-runtime:2.9.0")
    // Cafe Bazaar in-app billing (subscriptions)
    implementation("com.github.cafebazaar.Poolakey:poolakey:2.2.0")
    // Bazaar in-app login + per-user cloud storage (keeps the one-time trial usage across reinstall)
    implementation("com.farsitel.bazaar:auth:1.0.1")
    implementation("androidx.lifecycle:lifecycle-process:2.8.3")
}
