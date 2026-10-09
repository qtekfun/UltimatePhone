import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.licensee)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * The app version lives in one place, `appVersion` in gradle.properties (SemVer, optionally
 * `-rcN`). The version code is derived from it, so it never depends on dates or the machine:
 * (MAJOR*10000 + MINOR*100 + PATCH) * 100 + N for `-rcN`, and 99 for a final release, which
 * therefore sorts after its release candidates.
 */
val appVersion = providers.gradleProperty("appVersion").get()

fun versionCodeOf(version: String): Int {
    val match = Regex("""(\d+)\.(\d+)\.(\d+)(?:-rc\.?(\d+))?""").matchEntire(version)
        ?: error("appVersion must be MAJOR.MINOR.PATCH or MAJOR.MINOR.PATCH-rcN: $version")
    val (major, minor, patch, rc) = match.destructured
    require(minor.toInt() < 100 && patch.toInt() < 100 && (rc.isEmpty() || rc.toInt() in 1..98))
    val base = major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt()
    return base * 100 + (rc.toIntOrNull() ?: 99)
}

/** Release signing from the environment (CI secrets); without it the release build is debug-signed. */
val releaseKeystore: String? = System.getenv("UG_KEYSTORE_FILE")

android {
    namespace = "com.qtekfun.ultimatephone"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.qtekfun.ultimatephone"
        minSdk = 31
        targetSdk = 37
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("UG_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UG_KEY_ALIAS")
                keyPassword = System.getenv("UG_KEY_PASSWORD")
            }
        }
    }

    // Reproducible builds (F-Droid): no Google-encrypted dependency blob in the APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        debug {
            // Lets a debug build live next to a release build (different signing key).
            applicationIdSuffix = ".debug"
        }
        release {
            // Without a release key the APK is signed with the debug key so it stays installable.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            // The git commit is not part of the APK: a build from a source tarball must match.
            vcsInfo.include = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    testOptions {
        unitTests {
            all {
                // Use the standard service loader so the test-only main dispatcher in src/test/resources is found
                // (see SafeMainDispatcherFactory); the fast loader only knows Android's own factory.
                it.systemProperty("kotlinx.coroutines.fast.service.loader", "false")
            }
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        resources {
            // R8 already strips the unused Bouncy Castle classes (about 20 remain: Ed25519, Argon2 and their helpers);
            // what it cannot strip are the library's own message tables, which only certificate path code reads.
            excludes += "org/bouncycastle/**"
            // Debug probes of kotlinx.coroutines, only read by the debugger agent.
            excludes += "DebugProbesKt.bin"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Only free licenses may ship in the APK (F-Droid). Anything else fails the build.
licensee {
    allow("Apache-2.0")
    allow("BSD-3-Clause")
    allow("0BSD")
    // Bouncy Castle: MIT-style licence without an SPDX id in its POM; only the Ed25519 verifier is used and R8 strips the rest.
    allowUrl("https://www.bouncycastle.org/licence.html")
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:phonenumber"))
    implementation(project(":core:telecom"))
    implementation(project(":core:contacts"))
    implementation(project(":core:calllog"))
    implementation(project(":core:datapacks"))
    implementation(project(":core:spam"))
    implementation(project(":core:sync"))
    implementation(project(":core:settings"))
    implementation(project(":core:recording"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
