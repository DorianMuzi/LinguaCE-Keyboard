import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Clé de signature de publication, lue dans keystore.properties (à la racine,
// jamais versionné : voir .gitignore). Sans ce fichier, la version « release »
// est signée avec la clé de débogage : bonne pour la tester sur l'émulateur,
// refusée par Google Play.
// Recherché d'abord à la racine du projet, puis dans ~/.linguace/ (hors du
// dossier du projet, donc hors d'un éventuel dossier synchronisé dans le cloud).
val keystoreFile: File? = listOf(
    rootProject.file("keystore.properties"),
    File(System.getProperty("user.home"), ".linguace/keystore.properties"),
).firstOrNull { it.exists() }
val keystoreProps = Properties().apply {
    keystoreFile?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.linguace.keyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.linguace.keyboard"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            create("release") {
                // Chemin relatif au dossier du fichier de réglages, ou absolu.
                storeFile = keystoreFile!!.parentFile.resolve(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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

// SeedListTest lit la liste de base : toute modification de la liste doit
// relancer les tests (sinon Gradle les croit à jour et ne vérifie rien).
tasks.withType<Test>().configureEach {
    inputs.file("src/main/assets/seed_words.tsv")
}

// Aucune dépendance d'exécution : le clavier ne contient que son propre code,
// lisible ligne à ligne. Les bibliothèques ci-dessous ne servent qu'aux tests
// (sur le poste et sur l'émulateur) et n'entrent pas dans l'APK.
dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
