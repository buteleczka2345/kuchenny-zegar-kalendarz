plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "pl.kuchenny.timer"
    compileSdk = 35

    defaultConfig {
        applicationId = "pl.kuchenny.zegar.kalendarz"
        minSdk = 23
        targetSdk = 35
        versionCode = 13
        versionName = "2.6"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // Prawdziwy org.json na potrzeby testów: w android.jar to tylko atrapa.
    testImplementation("org.json:json:20240303")
}
