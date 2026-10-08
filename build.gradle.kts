plugins {
    base
    alias(libs.plugins.android.application) apply false
    // Not applied: AGP 9 has built-in Kotlin, this only puts the pinned Kotlin Gradle plugin on the classpath.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.licensee) apply false
    alias(libs.plugins.ktlint)
}

ktlint {
    version.set(libs.versions.ktlint)
}
