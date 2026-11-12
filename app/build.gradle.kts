
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.diegoluan.stackfactory"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.diegoluan.stackfactory"

        minSdk = 24
        targetSdk = 37

        // StackFactory - XP, Cartas e Skills
        versionCode = 5
        versionName = "1.3.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Mantemos a configuração já validada
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {

    // =========================================
    // JETPACK COMPOSE
    // =========================================

    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // =========================================
    // ANDROIDX
    // =========================================

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // =========================================
    // NOTIFICAÇÕES E TAREFAS AGENDADAS
    // =========================================

    implementation(
        "androidx.work:work-runtime-ktx:2.10.5"
    )

    // =========================================
    // GOOGLE ADMOB
    // =========================================

    implementation(
        "com.google.android.gms:play-services-ads:25.5.0"
    )

    // Consentimento e privacidade
    implementation(
        "com.google.android.ump:user-messaging-platform:4.0.0"
    )

    // =========================================
    // GOOGLE PLAY IN-APP UPDATES
    // =========================================

    implementation(
        "com.google.android.play:app-update:2.1.0"
    )

    implementation(
        "com.google.android.play:app-update-ktx:2.1.0"
    )

    // =========================================
    // CORREÇÃO ANDROIDX FRAGMENT
    // =========================================

    constraints {
        implementation("androidx.fragment:fragment:1.9.1") {
            because(
                "Atualizar Fragment e corrigir aviso de SDK desatualizado da Google Play"
            )
        }
    }

    // =========================================
    // TESTES
    // =========================================

    testImplementation(libs.junit)

    androidTestImplementation(
        platform(libs.androidx.compose.bom)
    )

    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(libs.androidx.junit)

    // =========================================
    // DEBUG E FERRAMENTAS
    // =========================================

    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )

    debugImplementation(
        libs.androidx.compose.ui.tooling
    )
}
