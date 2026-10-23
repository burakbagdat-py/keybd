plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.keybd.pro"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.keybd.pro"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        // Sabit imza: her derlemede aynı anahtar kullanılır, böylece yeni sürümler
        // eskisinin üzerine (verileri silmeden) güncelleme olarak kurulabilir.
        create("keybd") {
            storeFile = file("keybd.jks")
            storePassword = "keybd123"
            keyAlias = "keybd"
            keyPassword = "keybd123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("keybd")
        }
        debug {
            signingConfig = signingConfigs.getByName("keybd")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    androidResources {
        noCompress += "txt"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
