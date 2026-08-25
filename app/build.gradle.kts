plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.minidex.app"
    compileSdk = 35
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.minidex.app"
        minSdk = 29 // Android 10+ (Cover screens, DeX, Bluetooth HID)
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
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
            applicationIdSuffix = ".debug"
            isDebuggable = true
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
        aidl = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/LICENSE*.md"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
        }
    }
}

// Build the shell-side JNI library used by the app_process Binder service.
val generatedUhidAssets = layout.buildDirectory.dir("generated/uhidAssets")
val buildUhidJni = tasks.register<Exec>("buildUhidJni") {
    val source = layout.projectDirectory.file("src/main/cpp/minidex_uhid_jni.c")
    val output = generatedUhidAssets.map { it.file("libminidex_uhid.so") }
    inputs.file(source)
    outputs.file(output)

    doFirst {
        val ndk = androidComponents.sdkComponents.sdkDirectory.get().asFile
            .resolve("ndk/27.0.12077973")
        val hostTag = when {
            System.getProperty("os.name").startsWith("Mac", ignoreCase = true) -> "darwin-x86_64"
            System.getProperty("os.name").startsWith("Linux", ignoreCase = true) -> "linux-x86_64"
            else -> error("Unsupported NDK build host: ${System.getProperty("os.name")}")
        }
        val clang = ndk.resolve("toolchains/llvm/prebuilt/$hostTag/bin/aarch64-linux-android29-clang")
        output.get().asFile.parentFile.mkdirs()
        commandLine(
            clang.absolutePath,
            "-Oz", "-fPIC", "-shared", "-s",
            source.asFile.absolutePath,
            "-o", output.get().asFile.absolutePath
        )
    }
}

android.sourceSets.getByName("main").assets.srcDir(generatedUhidAssets)
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(buildUhidJni)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // DataStore & Serialization
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Hidden API bypass for system-level reflection
    implementation(libs.hiddenapibypass)

    // Wireless ADB & Shizuku
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    // PairingConnectionCtx needs the public Conscrypt key-export API. Android's
    // platform Conscrypt exposes a different hidden signature on some releases,
    // which otherwise surfaces as NoSuchMethodException during pairing.
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")
    implementation("pl.droidsonroids.gif:android-gif-drawable:1.2.32")
    implementation("com.squareup:gifencoder:0.10.1")

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
