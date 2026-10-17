
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val stableKeystoreFile = file("rasidi-release.jks")
val stableStorePassword = System.getenv("RASIDI_KEYSTORE_PASSWORD")
val stableKeyAlias = System.getenv("RASIDI_KEY_ALIAS")
val stableKeyPassword = System.getenv("RASIDI_KEY_PASSWORD")

android {
    namespace = "com.rasidi.smart"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rasidi.smart"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    signingConfigs {
        create("stable") {
            storeFile = stableKeystoreFile
            storePassword = stableStorePassword
            keyAlias = stableKeyAlias
            keyPassword = stableKeyPassword
        }
    }

    buildTypes {
        getByName("debug") {
            if (
                stableKeystoreFile.exists() &&
                !stableStorePassword.isNullOrBlank() &&
                !stableKeyAlias.isNullOrBlank() &&
                !stableKeyPassword.isNullOrBlank()
            ) {
                signingConfig = signingConfigs.getByName("stable")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
