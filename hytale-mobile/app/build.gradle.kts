plugins {
    id("com.android.application")
}

android {
    namespace = "com.unofficial.hytalemobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.unofficial.hytalemobile"
        minSdk = 28
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0-alpha"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
        }
    }
}
