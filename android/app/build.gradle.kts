import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.jesperhaafkes.caster"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jesperhaafkes.caster"
        minSdk = 26
        targetSdk = 37
        // Play rejects a duplicate versionCode, so CI hands one in rather than
        // this needing a commit per upload. A local build keeps 1, which is all
        // a debug install cares about.
        versionCode = (System.getenv("CASTER_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("CASTER_VERSION_NAME") ?: "1.0"
    }

    signingConfigs {
        // Wired from the environment so the keystore and its passwords live in
        // CI secrets and never in the tree. With the variables unset — which is
        // every local build — this stays empty and `assembleRelease` produces an
        // unsigned APK exactly as before.
        create("release") {
            val keystore = System.getenv("CASTER_KEYSTORE")
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = System.getenv("CASTER_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CASTER_KEY_ALIAS")
                keyPassword = System.getenv("CASTER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // On deliberately, and on early. R8 rewrites reflection sites that
            // Compose relies on, and the failures show up at runtime in release
            // only — finding that out on the day of a Play upload is the whole
            // problem. Anything it strips wrongly belongs in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
                .takeIf { System.getenv("CASTER_KEYSTORE") != null }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
