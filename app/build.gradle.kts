plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.amapredirect"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.amapredirect"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "1.0.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
}
