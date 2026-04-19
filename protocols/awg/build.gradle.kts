plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.paykeyfear.vpn.protocols.awg"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

dependencies {
    api(project(":core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.timber)

    // The umbrella .aar produced by scripts/build-native.sh lands in
    // protocols/awg/libs/. It bundles ALL three protocols (awg + vless
    // + hysteria2) into a single Java class
    // `paykeyfearnative.Paykeyfearnative` so AGP doesn't see two
    // libgojni.so or two go.Seq across modules. Using `api` so the
    // umbrella is on the classpath of the other protocol modules
    // (which depend on :protocols:awg only transitively via :core's
    // shared-Protector dependency? — no: each protocol module
    // currently does NOT depend on this one). Instead the bundle is
    // exposed application-wide via the `app` module's direct dep on
    // :protocols:awg, which already exists.
    api(fileTree("libs") { include("*.aar") })

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
