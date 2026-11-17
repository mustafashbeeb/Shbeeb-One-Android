buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("io.objectbox:objectbox-gradle-plugin:4.3.0")
    }
}

plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0" apply false
    id("com.facebook.react.rootproject")
}

// Native React Native libraries (including llama.rn) read the Android NDK version
// from the root project's extra properties rather than the app module's android block.
rootProject.extra["ndkVersion"] = "27.1.12297006"
rootProject.extra["buildToolsVersion"] = "36.0.0"
rootProject.extra["compileSdkVersion"] = 36
rootProject.extra["targetSdkVersion"] = 35
rootProject.extra["minSdkVersion"] = 26
rootProject.extra["reactNativeAndroidRoot"] = file("node_modules/react-native/ReactAndroid")
