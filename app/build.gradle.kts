import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing is opt-in via keystore.properties (not committed) OR
// environment variables populated by CI. If neither is present we fall
// back to the debug signing config so local `assembleRelease` still runs.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties =
    Properties().apply {
        if (keystorePropertiesFile.exists()) {
            keystorePropertiesFile.inputStream().use(::load)
        }
    }

fun signingValue(key: String, env: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(env)

android {
    namespace = "com.paykeyfear.vpn"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.paykeyfear.vpn"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 8
        versionName = "1.0.8"

        testInstrumentationRunner = "com.paykeyfear.vpn.HiltTestRunner"
        vectorDrawables.useSupportLibrary = true

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
        }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = signingValue("storeFile", "PAYKEYFEAR_KEYSTORE_PATH")
            val storePass = signingValue("storePassword", "PAYKEYFEAR_KEYSTORE_PASSWORD")
            val keyAliasValue = signingValue("keyAlias", "PAYKEYFEAR_KEY_ALIAS")
            val keyPass = signingValue("keyPassword", "PAYKEYFEAR_KEY_PASSWORD")
            if (storeFilePath != null && storePass != null && keyAliasValue != null && keyPass != null) {
                storeFile = file(storeFilePath)
                storePassword = storePass
                keyAlias = keyAliasValue
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = if (releaseSigning.storeFile?.exists() == true) {
                releaseSigning
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    // Rename APK outputs: PaykeyfearVPN-<baseVersion>-<buildType>.apk
    // versionName for debug includes the "-debug" suffix already, so we use
    // defaultConfig.versionName (the bare version) and append the build type.
    val baseVersion = defaultConfig.versionName ?: "0.0.0"

    // Rename AAB: base name drives the .aab filename (<base>-release.aab).
    setProperty("archivesBaseName", "PaykeyfearVPN-$baseVersion")
    applicationVariants.all {
        val variantBuildType = buildType.name
        outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                output.outputFileName = "PaykeyfearVPN-$baseVersion-$variantBuildType.apk"
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

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
            )
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }

    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":core-config"))
    implementation(project(":vpn-service"))
    implementation(project(":protocols:awg"))
    implementation(project(":protocols:vless"))
    implementation(project(":protocols:hysteria2"))

    // Umbrella native backend (awg + vless + hysteria2 combined,
    // see third_party/gomobile-bundle). scripts/build-native.sh
    // explodes the gomobile-bind .aar into:
    //   app/libs/paykeyfearnative.jar           <- pure Java classes
    //   app/src/main/jniLibs/<abi>/libgojni.so  <- one .so per ABI
    //
    // Why explode instead of consuming the .aar directly?
    //   AGP 8.x silently drops pieces of locally-resolved .aar files
    //   (both `fileTree(*.aar)` on :app and a flatDir + named-dep
    //   reference observed: classes.jar reaches compile but jniLibs
    //   never make it into the APK, OR vice-versa). Splitting the
    //   .aar by hand and feeding the parts through AGP's first-class
    //   `libs/*.jar` + `src/main/jniLibs/<abi>/` paths is what
    //   wireguard-android, AmneziaVPN-Android and v2rayNG all do
    //   for the same reason.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)
    implementation(libs.zxing.android.embedded)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)

    // Instrumented tests
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation("com.google.dagger:hilt-android-testing:${libs.versions.hilt.get()}")
    kspAndroidTest("com.google.dagger:hilt-android-compiler:${libs.versions.hilt.get()}")
}
