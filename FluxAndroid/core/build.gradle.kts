plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// What the TV and mobile apps share: the provider, the guide, favorites,
// pairing and the player.
android {
    namespace = "com.fares.flux.core"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    api(platform("androidx.compose:compose-bom:2026.02.01"))
    api("androidx.compose.runtime:runtime")
    api("androidx.compose.ui:ui")
    api("androidx.security:security-crypto:1.0.0")
    api("androidx.media3:media3-exoplayer:1.11.1")
    api("androidx.media3:media3-exoplayer-hls:1.11.1")
    api("androidx.media3:media3-ui:1.11.1")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    api("androidx.media3:media3-datasource-okhttp:1.11.1")
    api("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")
    implementation("org.tukaani:xz:1.10")
}
