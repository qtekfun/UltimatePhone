plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.qtekfun.ultimatephone.core.sync"
}

dependencies {
    // ListEntry and EntryJsonl are part of this module's API: the sync files are exactly what core:spam writes.
    api(project(":core:spam"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
