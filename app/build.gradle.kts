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
        // CI her derlemede numarayı artırır: -PbuildNumber=N → sürüm 1.N (kod N+1)
        val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 0
        versionCode = buildNumber + 1
        versionName = "1.$buildNumber"
        buildConfigField("String", "UPDATE_REPO", "\"burakbagdat-py/keybd\"")
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
    buildFeatures {
        buildConfig = true
    }
    androidResources {
        noCompress += listOf("txt", "wav")
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
