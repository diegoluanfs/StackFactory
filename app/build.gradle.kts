
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

        versionCode = 3
        versionName = "1.1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
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
    // Jetpack Compose - BOM único
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Notificações e tarefas agendadas
    implementation("androidx.work:work-runtime-ktx:2.10.5")

    // Google AdMob
    implementation(
        "com.google.android.gms:play-services-ads:25.5.0"
    )

    // Consentimento de anúncios - Google UMP
    implementation(
        "com.google.android.ump:user-messaging-platform:4.0.0"
    )

    // Testes unitários
    testImplementation(libs.junit)

    // Testes instrumentados
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // Ferramentas de desenvolvimento
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
