plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.mindcore"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.mindcore"
        // Glass needs RenderEffect/RuntimeShader (Android 13+); older phones get plain blur later.
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1-m0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.core)
    implementation(libs.backdrop)
    implementation(libs.kyant.shapes)
    implementation(libs.datastore.preferences)
}
