pluginManagement {
    includeBuild("node_modules/@react-native/gradle-plugin")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("com.facebook.react.settings")
}

extensions.configure<com.facebook.react.ReactSettingsExtension> {
    autolinkLibrariesFromCommand(
        workingDirectory = settingsDir,
        lockFiles = files("package.json", "yarn.lock", "react-native.config.js")
    )
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven(url = uri("$rootDir/node_modules/react-native/android"))
        maven(url = uri("$rootDir/node_modules/jsc-android/dist"))
        maven(url = uri("$rootDir/node_modules/@notifee/react-native/android/libs"))
        maven(url = uri("https://jitpack.io"))
    }
}

rootProject.name = "ShbeebOneAndroid"
include(":app")
includeBuild("node_modules/@react-native/gradle-plugin")
