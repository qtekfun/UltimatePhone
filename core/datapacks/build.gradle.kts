plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.qtekfun.ultimatephone.core.datapacks"
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    // Ed25519 is not in the platform before Android 13. Only the lightweight signer API is used.
    implementation(libs.bouncycastle)
    // Packs are .xz: pure Java, no native code (F-Droid friendly).
    implementation(libs.xz)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
