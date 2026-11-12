import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// One version for the whole project: desktop/release.env (Firefox's version and our release).
val release = Properties().apply {
    rootProject.file("../desktop/release.env").readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
        .forEach { setProperty(it.substringBefore("="), it.substringAfter("=")) }
}
val firefoxVersion: String = release.getProperty("FIREFOX_VERSION")
val releaseNumber: Int = release.getProperty("RELEASE").toInt()
val geckoviewVersion: String = release.getProperty("GECKOVIEW_VERSION")
check(geckoviewVersion.startsWith("$firefoxVersion.")) {
    "GECKOVIEW_VERSION ($geckoviewVersion) isn't Firefox $firefoxVersion"
}

/** 160.0-1 → 16000001, 160.0.2-3 → 16000203: always higher for a later Firefox or release. */
fun versionCodeOf(firefox: String, release: Int): Int {
    val (major, minor, patch) = (firefox.split(".").map { it.toInt() } + listOf(0, 0, 0)).take(3)
    require(release in 1..99 && minor < 100 && patch < 10)
    return major * 100_000 + minor * 1_000 + patch * 100 + release
}

android {
    namespace = "app.superawesomemagical.browser"
    // GeckoView 160 asks for Android 17's API level 37.2.
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "app.superawesomemagical.browser"
        minSdk = 29
        targetSdk = 36
        versionCode = versionCodeOf(firefoxVersion, releaseNumber)
        versionName = "$firefoxVersion-$releaseNumber"
    }

    // Release builds are signed with the permanent key when it's there (see CLAUDE.md): from
    // keystore/release.properties locally, or the RELEASE_KEYSTORE_* secrets in CI.
    val localKey = rootProject.file("keystore/release.properties").takeIf { it.exists() }
        ?.let { file -> Properties().apply { file.inputStream().use(::load) } }
    fun signingValue(property: String, env: String): String? = localKey?.getProperty(property) ?: System.getenv(env)
    val keystorePath = signingValue("storeFile", "RELEASE_KEYSTORE_FILE")
    if (keystorePath != null) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystorePath)
                storePassword = signingValue("storePassword", "RELEASE_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "RELEASE_KEY_ALIAS") ?: "browser"
                keyPassword = signingValue("keyPassword", "RELEASE_KEY_PASSWORD") ?: storePassword
            }
        }
    }

    buildTypes {
        release {
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    // GeckoView carries Firefox for every processor; phones need only arm64. (x86_64 is for the
    // emulator and Chromebooks.)
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
        }
        // GeckoView's libraries stay compressed in the APK: a much smaller download.
        jniLibs.useLegacyPackaging = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        // Robolectric reads files through a JDK internal that Java 17+ only opens on request.
        unitTests.all { it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED") }
    }
}

dependencies {
    implementation("org.mozilla.geckoview:geckoview:$geckoviewVersion")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    // The screenshot tests (ScreenshotTest) draw the screens with Robolectric.
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
