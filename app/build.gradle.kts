plugins {
    alias(libs.plugins.android.application)
    // Add the Google services Gradle plugin
    id("com.google.gms.google-services")
    // Firebase Crashlytics Gradle plugin
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.escobar.pasedegol"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    // configurar Gradle para que utilice el certificado incluido en el proyecto
    signingConfigs {
        create("debugConfig") {
            // archivo incluido en la carpeta app/
            storeFile = file("debug.keystore")
            // contraseñas por defecto que usa siempre Android Studio
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.escobar.pasedegol"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // para que cargue el signingConfigs previo para que funcione en otros dispositivos al compilar
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Navigation Component
    implementation("androidx.navigation:navigation-fragment-ktx:2.9.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.9.7")

    // Firebase BoM (gestiona versiones de todos los servicios Firebase)
    implementation(platform("com.google.firebase:firebase-bom:34.11.0"))
    // Firebase Analythics
    implementation("com.google.firebase:firebase-analytics")
    // Firebase Crashlytics
    implementation("com.google.firebase:firebase-crashlytics")
    // Firebase Auth
    implementation("com.google.firebase:firebase-auth")
    // Firebase Firestore
    implementation("com.google.firebase:firebase-firestore")
    // Firebase Cloud Messaging
    implementation("com.google.firebase:firebase-messaging")
    // Firebase Storage
    implementation("com.google.firebase:firebase-storage")
    // Firebase Functions
    implementation("com.google.firebase:firebase-functions-ktx:21.2.1")

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.5.1")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")

    // Glide (carga de imagenes)
    implementation("com.github.bumptech.glide:glide:5.0.5")

    // QRGen (generacion de QR)
    implementation("com.github.kenglxn.QRGen:android:3.0.1")

    // Lottie (animaciones)
    implementation("com.airbnb.android:lottie:6.7.1")

    // Stripe (pasarela de pago)
    implementation("com.stripe:stripe-android:23.1.0")
    // Google Pay Support
    implementation("com.google.android.gms:play-services-wallet:19.5.0")
    implementation(libs.firebase.crashlytics.buildtools)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}