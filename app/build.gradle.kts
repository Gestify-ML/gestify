import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.chaquopy)
    alias(libs.plugins.kapt)
    alias(libs.plugins.safe.args)
}

android {
    namespace = "com.example.gestify"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.gestify"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val localProperties = Properties()
        val localFile = rootProject.file("local.properties")
        if (localFile.exists()) {
            localProperties.load(FileInputStream(localFile))
        }

        // Add Client ID to BuildConfig
        val spotifyClientId = localProperties["SPOTIFY_CLIENT_ID"] as String? ?: ""
        buildConfigField("String", "SPOTIFY_CLIENT_ID", spotifyClientId)

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
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
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true
        compose = true
        viewBinding = true
        dataBinding = true
    }
}



dependencies {
    implementation(libs.gson)
    implementation(libs.auth)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.constraintlayout)
    // To use constraintlayout in compose
    implementation(libs.androidx.constraintlayout.compose)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.extensions)
    //noinspection GradlePath
    implementation(files("spotify-app-remote-release-0.8.0.aar"))
    implementation(libs.androidx.appcompat)



    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui)




    implementation (libs.tensorflow.lite.task.vision)
    implementation(libs.material)

    implementation(libs.tensorflow.lite.task.vision.v040)
    // Import the GPU delegate plugin Library for GPU inference
    implementation(libs.tensorflow.lite.gpu.delegate.plugin)
    implementation(libs.tensorflow.lite.gpu)










    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}


