plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.qtekfun.ultimatephone.core.settings"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    // Argon2id (Argon2BytesGenerator): the same lightweight API :core:datapacks already uses for Ed25519.
    implementation(libs.bouncycastle)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
