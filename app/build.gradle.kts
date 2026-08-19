import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use(::load)
    }
}

val mapboxAccessToken = localProperties.getProperty("MAPBOX_ACCESS_TOKEN")
    ?: providers.gradleProperty("MAPBOX_ACCESS_TOKEN").orNull
    ?: System.getenv("MAPBOX_ACCESS_TOKEN")
    ?: ""

// Set only by the protected cloud workflow. Keeping the path explicit prevents a runner-provided
// debug key from silently replacing TMLN's stable signing identity.
val stableSigningStoreFile = System.getenv("TMLN_SIGNING_STORE_FILE")

val tmlnVersionCode = providers.gradleProperty("TMLN_VERSION_CODE").orNull?.toIntOrNull()
    ?: error("TMLN_VERSION_CODE must be a positive integer in gradle.properties.")
val tmlnVersionName = providers.gradleProperty("TMLN_VERSION_NAME").orNull?.takeIf { it.isNotBlank() }
    ?: error("TMLN_VERSION_NAME must be set in gradle.properties.")

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.example.timelineviewer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.timelineviewer"
        minSdk = 26
        targetSdk = 35
        versionCode = tmlnVersionCode
        versionName = tmlnVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Mapbox reads this standard resource automatically at map initialization.
        resValue("string", "mapbox_access_token", mapboxAccessToken)
        buildConfigField(
            "boolean",
            "MAPBOX_ACCESS_TOKEN_CONFIGURED",
            (mapboxAccessToken.isNotBlank()).toString()
        )
    }

    signingConfigs {
        getByName("debug") {
            if (!stableSigningStoreFile.isNullOrBlank()) {
                storeFile = file(stableSigningStoreFile)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // The cloud workflow provides the stable private keystore path above before Gradle
            // configures this build. Local debug builds retain Android's standard default key.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.gson)

    // Mapbox 3D mapping and its Jetpack Compose extension.
    implementation(libs.mapbox.maps)
    implementation(libs.mapbox.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
