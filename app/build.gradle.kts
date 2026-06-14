import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.junit5)
}

// The version name is the single source of truth; the integer versionCode is derived from it so the
// in-app updater can compare the installed build against the latest GitHub release tag (X.Y.Z).
val appVersionName = "1.3.0"

fun versionCodeOf(name: String): Int {
    val parts = name.split(".").map { it.toIntOrNull() ?: 0 }
    val major = parts.getOrElse(0) { 0 }
    val minor = parts.getOrElse(1) { 0 }
    val patch = parts.getOrElse(2) { 0 }
    return major * 10000 + minor * 100 + patch
}

// GitHub repository that hosts the release APKs the updater checks (owner/repo).
val updateOwner = "andreaferraboli"
val updateRepo = "PeopleHub"

// Release signing is configured from a (git-ignored) keystore.properties for local builds, or from
// environment variables in CI. When neither is present, the release build remains unsigned.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties =
    Properties().apply {
        if (keystorePropertiesFile.exists()) {
            load(FileInputStream(keystorePropertiesFile))
        }
    }
val ciKeystoreFile: String? = System.getenv("KEYSTORE_FILE")
val hasSigning = keystorePropertiesFile.exists() || ciKeystoreFile != null

android {
    namespace = "com.peoplehub"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.peoplehub"
        minSdk = 26
        targetSdk = 35
        versionCode = versionCodeOf(appVersionName)
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField("String", "UPDATE_OWNER", "\"$updateOwner\"")
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            } else if (ciKeystoreFile != null) {
                storeFile = rootProject.file(ciKeystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    // Feature & core modules
    implementation(projects.core.domain)
    implementation(projects.core.ui)
    implementation(projects.core.database)
    implementation(projects.core.notifications)
    implementation(projects.core.dataio)
    implementation(projects.feature.people)
    implementation(projects.feature.birthdays)
    implementation(projects.feature.events)
    implementation(projects.feature.widget)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // WorkManager + Hilt worker integration
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // DataStore (settings)
    implementation(libs.androidx.datastore.preferences)

    // Glance (manifest + receivers live in feature:widget, but app hosts them)
    implementation(libs.androidx.glance.appwidget)

    // Coil
    implementation(libs.coil.compose)

    // Permissions
    implementation(libs.accompanist.permissions)

    debugImplementation(libs.bundles.compose.debug)

    // Tests
    testImplementation(libs.bundles.unit.test)
    testRuntimeOnly(libs.junit5.engine)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
