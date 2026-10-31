import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Helper function to resolve properties from .env, local.properties, or system environment
fun resolveEnvProperty(propName: String, defaultValue: String = ""): String {
    // 1. Check .env file in rootProject
    val envFile = rootProject.file(".env")
    if (envFile.exists()) {
        val envProps = Properties()
        FileInputStream(envFile).use { envProps.load(it) }
        val key = envProps.getProperty(propName)?.trim()
        if (!key.isNullOrEmpty()) return key
    }

    // 2. Check local.properties in rootProject
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        val localProps = Properties()
        FileInputStream(localFile).use { localProps.load(it) }
        val key = localProps.getProperty(propName)?.trim()
        if (!key.isNullOrEmpty()) return key
    }

    // 3. Check System environment
    val sysEnv = System.getenv(propName)?.trim()
    if (!sysEnv.isNullOrEmpty()) return sysEnv

    // 4. Check Gradle project property
    if (project.hasProperty(propName)) {
        val prop = project.property(propName)?.toString()?.trim()
        if (!prop.isNullOrEmpty()) return prop
    }

    return defaultValue
}

val mapApiKey = resolveEnvProperty("MAPS_API_KEY")
val mapStyleUrl = resolveEnvProperty("MAP_STYLE_URL", "https://tiles.openfreemap.org/styles/dark")

android {
    namespace = "com.vyora"
    compileSdk = 37
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.vyora"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        manifestPlaceholders["MAPS_API_KEY"] = mapApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapApiKey\"")
        buildConfigField("String", "MAP_STYLE_URL", "\"$mapStyleUrl\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                arguments += "-DBUILD_SHARED_LIBS=ON"
                arguments += "-DLLAMA_BUILD_APP=OFF"
                arguments += "-DLLAMA_BUILD_COMMON=ON"
                arguments += "-DLLAMA_OPENSSL=OFF"
                arguments += "-DGGML_NATIVE=OFF"
                arguments += "-DGGML_OPENMP=OFF"
                arguments += "-DGGML_LLAMAFILE=OFF"
            }
        }
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
        buildConfig = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.play.services.location)
    implementation(libs.maplibre.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.usb.serial)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}