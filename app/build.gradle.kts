import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Everything an owner changes lives in release-kit.properties, so making the app yours is one
// file edited in the browser, not a hunt through Gradle.
val kit = Properties().apply { rootProject.file("release-kit.properties").reader().use { load(it) } }
fun kit(key: String): String = kit.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
    ?: error("release-kit.properties: $key is missing")

val appId = kit("applicationId")
require(Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$").matches(appId)) {
    "release-kit.properties: applicationId '$appId' must look like io.github.yourname.myapp"
}
val versionPrefix = kit("versionPrefix")
val versionCodeOffset = kit("versionCodeOffset").toInt()

// GitHub Actions sets these. The repository decides where the update check looks and what the
// APK is called, so nobody has to type their own repository name into the code.
val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val repository = System.getenv("GITHUB_REPOSITORY").orEmpty()
val apkName = (repository.substringAfter('/').ifEmpty { "app" }) + ".apk"

android {
    // The Kotlin package, which never needs to change. What the phone sees is applicationId.
    namespace = "releasekit.app"
    compileSdk = 36

    defaultConfig {
        applicationId = appId
        minSdk = 26
        targetSdk = 36
        // Every CI build is a higher number than the last, so each one installs as an update.
        // A build on your own computer is versionCode 1 (+ offset): it never outranks a release.
        versionCode = (runNumber ?: 1) + versionCodeOffset
        versionName = if (runNumber != null) "$versionPrefix.$runNumber" else "$versionPrefix-dev"

        resValue("string", "app_name", kit("appName"))
        buildConfigField(
            "String",
            "RELEASES_API",
            if (repository.isEmpty()) "\"\"" else "\"https://api.github.com/repos/$repository/releases?per_page=20\"",
        )
        buildConfigField("String", "APK_NAME", "\"$apkName\"")
    }

    signingConfigs {
        // The release key is never in the repository. The workflow decodes it from the repository's
        // secrets into a temporary file and passes its location and passwords here. A debug key would
        // not do: it differs on every computer, and Android refuses an update signed with a different
        // key ("App not installed").
        val keystore = System.getenv("SIGNING_KEYSTORE_PATH")
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without the signing variables a release build is left unsigned, and CI refuses to
            // publish it.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // One APK per processor type keeps downloads small once the app has native libraries;
    // the universal APK works on every phone and is the one the update check offers.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
