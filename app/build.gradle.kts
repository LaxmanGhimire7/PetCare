import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("com.google.devtools.ksp")
}

val localSecrets = Properties().apply {
    rootProject.file("local.defaults.properties").inputStream().use { load(it) }
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val googleWebClientId = localSecrets.getProperty("GOOGLE_WEB_CLIENT_ID").orEmpty()

android {
    namespace = "com.example.petcare"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.petcare"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${googleWebClientId.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    // A distributable APK is signed only when the owner's private key is supplied locally.
    val releaseStorePath = providers.environmentVariable("PETCARE_RELEASE_STORE_FILE").orNull
    val releaseStorePassword = providers.environmentVariable("PETCARE_RELEASE_STORE_PASSWORD").orNull
    val releaseKeyAlias = providers.environmentVariable("PETCARE_RELEASE_KEY_ALIAS").orNull
    val releaseKeyPassword = providers.environmentVariable("PETCARE_RELEASE_KEY_PASSWORD").orNull
    val releaseSigning = if (listOf(releaseStorePath, releaseStorePassword, releaseKeyAlias,
            releaseKeyPassword).all { !it.isNullOrBlank() }) {
        signingConfigs.create("petcareRelease") {
            storeFile = file(requireNotNull(releaseStorePath))
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    } else null

    buildTypes {
        release {
            signingConfig = releaseSigning
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    viewBinding {
        enable = true
    }
    buildFeatures {
        buildConfig = true
    }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.material)
    implementation(libs.google.location)
    implementation("org.maplibre.gl:android-sdk-opengl:13.5.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Database (Room)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Navigation
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)

    // Lifecycle Components
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.runtime)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // WorkManager
    implementation(libs.androidx.work.runtime)

    // Image loading
    implementation(libs.coil)
    implementation(platform(libs.kotlinx.serialization.bom))

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    // Room 2.8.5 migration schema readers require matching serialization modules.
    androidTestImplementation(platform(libs.kotlinx.serialization.bom))
}
