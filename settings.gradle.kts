pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        // Local .aar drop zone for the gomobile umbrella backend
        // (paykeyfearnative.aar). AGP 8.x's `fileTree(*.aar)` pattern
        // is unreliable: the .aar lands on the compile classpath but
        // its `jniLibs/*.so` and AndroidManifest entries are silently
        // dropped, so on-device every reflective `Class.forName` call
        // succeeds compile-time and fails runtime. flatDir + a named
        // dependency reuses the AGP transform pipeline that already
        // ships with the Android Gradle Plugin.
        flatDir {
            dirs("${rootDir}/protocols/awg/libs")
        }
    }
}

rootProject.name = "PaykeyfearVPN"

include(":app")
include(":core")
include(":core-config")
include(":vpn-service")
include(":protocols:awg")
include(":protocols:vless")
include(":protocols:hysteria2")
