plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.baite.adopta"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.baite.adopta"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // URL de la API de Adopta+ (backend/). 10.0.2.2 = tu PC visto desde el emulador de Android.
        // En un celular físico usa la IP de tu PC en la red, p. ej. en gradle.properties:
        //   ADOPTA_API_URL=http://192.168.1.50:3000/
        val apiUrl = providers.gradleProperty("ADOPTA_API_URL").orElse("http://10.0.2.2:3000/").get()
        buildConfigField("String", "API_BASE_URL", "\"$apiUrl\"")
    }

    buildFeatures {
        buildConfig = true
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
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    //implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation("androidx.core:core:1.19.0")
    implementation("androidx.core:core-ktx:1.19.0")

    // Red y datos
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    // Imágenes y sesión segura
    implementation(libs.coil)
    implementation(libs.androidx.security.crypto)
}