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
        versionCode = 11
        versionName = "1.1.1"
    }

    signingConfigs {
        create("release") {
            val ks = rootProject.file("doxaiz-release.jks")
            if (ks.exists()) {
                storeFile = ks
                storePassword = "doxaiz2026"
                keyAlias = "doxaiz"
                keyPassword = "doxaiz2026"
                storeType = "jks"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            if (rootProject.file("doxaiz-release.jks").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
