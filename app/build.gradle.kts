plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.doxaiz.netoptimizer"
    compileSdk = 34
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.doxaiz.netoptimizer"
        minSdk = 31
        targetSdk = 34
        // Android forbids upgrading from the old public build (versionCode 11) to a lower code.
        // The user-facing release numbering can restart independently of this monotonic value.
        versionCode = 25
        versionName = "1.0.12"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        // Compressed libraries are extracted at install, avoiding 4 KiB APK alignment on 16 KiB devices.
        jniLibs.useLegacyPackaging = true
    }
}

// Android still owns UI/network APIs; the bounded, pure calculations are built for all four ABIs.
// Explicit opt-out is for development/fallback verification, not the normal release build.
val nativeRust = providers.gradleProperty("nativeRust").map { it.toBooleanStrict() }.orElse(true)
if (nativeRust.get()) {
    val output = layout.buildDirectory.dir("generated/rust/jniLibs")
    val buildRustCore = tasks.register<Exec>("buildRustCore") {
        workingDir(rootProject.projectDir)
        commandLine("bash", rootProject.file("scripts/build-rust-android.sh"), output.get().asFile.absolutePath)
        environment("ANDROID_HOME", android.sdkDirectory.absolutePath)
        inputs.files(fileTree(rootProject.file("native/netoptimizer-core")) { exclude("target/**") })
        inputs.file(rootProject.file("rust-toolchain.toml"))
        inputs.file(rootProject.file("scripts/build-rust-android.sh"))
        outputs.dir(output)
    }
    android.sourceSets.getByName("main").jniLibs.srcDir(output)
    tasks.named("preBuild").configure { dependsOn(buildRustCore) }
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
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation(libs.compose.ui.tooling)
}
