plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.qtekfun.ultimatephone.core.phonenumber"
}

dependencies {
    api(libs.libphonenumber)
    implementation(libs.javax.inject)

    testImplementation(libs.junit)
}
