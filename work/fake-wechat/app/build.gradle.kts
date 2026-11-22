plugins {
    id("com.android.application")
}

android {
    namespace = "fake.wechat"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.tencent.mm"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "test"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
