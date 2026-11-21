plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.linguace.keyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.linguace.keyboard"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Aucune dépendance d'exécution : le clavier ne contient que son propre code,
// lisible ligne à ligne. JUnit ne sert qu'aux tests sur le poste.
dependencies {
    testImplementation("junit:junit:4.13.2")
}
