plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.facebook.react")
    id("kotlin-kapt")
    id("io.objectbox")
}

react {
    root.set(file(".."))
    entryFile.set(file("../index.js"))
    jsRootDir.set(file(".."))
    autolinkLibrariesWithApp()
}

android {
    namespace = "com.shbeeb.ai"
    compileSdk = 36
    ndkVersion = "27.1.12297006"
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.shbeeb.ai"
        minSdk = 26
        targetSdk = 35
        versionCode = 17
        versionName = "0.10.7"
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            excludes += listOf(
                "**/librnllama_v8.so",
                "**/librnllama_jni_v8.so",
                "**/librnllama_v8_2.so",
                "**/librnllama_jni_v8_2.so",
                "**/librnllama_v8_2_i8mm.so",
                "**/librnllama_jni_v8_2_i8mm.so",
                "**/librnllama_v8_2_dotprod_i8mm_hexagon_opencl.so",
                "**/librnllama_jni_v8_2_dotprod_i8mm_hexagon_opencl.so"
            )
        }
    }

    buildTypes {
        getByName("release") {
            // Release signing is intentionally not configured; never reuse upstream signing material.
            isMinifyEnabled = false
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")

    implementation("com.facebook.react:react-android")
    implementation("com.facebook.react:hermes-android")
    implementation("androidx.appcompat:appcompat:1.7.0")

    implementation("androidx.work:work-runtime-ktx:2.9.0")
    val roomVersion = "2.8.5"
    kapt("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0") {
        exclude(group = "org.bouncycastle")
    }
    implementation("com.google.guava:guava:32.1.3-android")

    // Local on-device inference used by the original Shbeeb Compose screens.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}

// Room 2.8.5's processor brings kotlin-metadata-jvm 2.2.0, which cannot read
// Kotlin 2.4 metadata present in the dependency classpath. Keep this override KAPT-only.
configurations.configureEach {
    if (name.startsWith("kapt")) {
        resolutionStrategy.force("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.21")
    }
}

kotlin {
    // LiteRT-LM 0.18.0 ships Java 21 bytecode; compile with JDK 21 while
    // retaining Java 17 bytecode for Android runtime compatibility.
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

project.extensions.extraProperties.set(
    "vectoricons",
    mapOf(
        "iconFontsDir" to file("../node_modules/react-native-vector-icons/Fonts").absolutePath,
        "iconFontNames" to listOf("MaterialIcons.ttf", "MaterialCommunityIcons.ttf")
    )
)
apply(from = file("../node_modules/react-native-vector-icons/fonts.gradle"))

// The fonts Gradle script writes resources consumed by lint model generation and analysis.
tasks.matching {
    it.name.endsWith("LintReportModel") || it.name.startsWith("lintAnalyze")
}.configureEach {
    dependsOn("copyReactNativeVectorIconFonts")
}
