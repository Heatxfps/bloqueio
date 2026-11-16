plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Chave fixa: um Device Owner não pode ser desinstalado, então toda atualização
// precisa da MESMA assinatura. No CI a chave vem dos secrets (ver docs/PADROES.md).
val keystorePath: String? = System.getenv("BLOQUEIO_KEYSTORE")
    ?: rootProject.file("keystore/bloqueio-release.p12").takeIf { it.exists() }?.path

android {
    namespace = "dev.foco.bloqueio"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.foco.bloqueio"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("fixed") {
                storeFile = file(keystorePath)
                storeType = "pkcs12"
                storePassword = System.getenv("BLOQUEIO_KEYSTORE_PASSWORD")
                keyAlias = "bloqueio"
                keyPassword = System.getenv("BLOQUEIO_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        val signing = signingConfigs.findByName("fixed") ?: signingConfigs.getByName("debug")
        debug { signingConfig = signing }
        release {
            isMinifyEnabled = false
            signingConfig = signing
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.11.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    // Liquid Glass para Compose: refração (lens), vibrancy, brilho especular. Requer Compose 1.9.4.
    implementation("io.github.kyant0:backdrop:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
}
