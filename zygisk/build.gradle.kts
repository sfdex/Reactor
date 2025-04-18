plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.sfdex.zygisk"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    externalNativeBuild {
        ndkBuild {
            path("jni/Android.mk")
        }
    }
}
