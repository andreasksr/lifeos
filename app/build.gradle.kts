plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.lifeos"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.lifeos.continuum"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Fester Entwicklungsschlüssel: jede CI-APK trägt dieselbe Signatur, damit neue Versionen
        // als Update über die alte installiert werden können (Daten bleiben erhalten).
        // Für eine Veröffentlichung durch einen privaten Schlüssel (GitHub-Secret) ersetzen.
        create("lifeosDev") {
            storeFile = file("signing/lifeos-dev.p12")
            storeType = "PKCS12"
            storePassword = "lifeos-dev-signing"
            keyAlias = "lifeos"
            keyPassword = "lifeos-dev-signing"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("lifeosDev")
        }
        release {
            signingConfig = signingConfigs.getByName("lifeosDev")
            // R8 wird aktiviert, sobald Regeln für SQLCipher/Room mit einem Release-Build verifiziert sind.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:database"))
    implementation(project(":core:security"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:ledger"))
    implementation(project(":feature:home"))
    implementation(project(":feature:planning"))
    implementation(project(":feature:projects"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime.ktx)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
