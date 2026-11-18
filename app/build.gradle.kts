plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.doxaiz.netoptimizer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.doxaiz.netoptimizer"
        minSdk = 31
        targetSdk = 34
        // Android forbids upgrading from the old public build (versionCode 11) to a lower code.
        // The user-facing release numbering can restart independently of this monotonic value.
        versionCode = 13
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            // Release keys and passwords must be supplied outside the repository.
            System.getenv("ANDROID_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }?.let { storeFile = file(it) }
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS")
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    testImplementation("junit:junit:4.13.2")
    debugImplementation(libs.compose.ui.tooling)
}
