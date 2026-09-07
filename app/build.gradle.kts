plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.ideakaryanusa.smltrack"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ideakaryanusa.smltrack"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // Ganti ini kalau alamat backend berbeda per environment.
        buildConfigField("String", "API_BASE_URL", "\"https://ideakaryanusa.softindopp.com/\"")

        // Backend SENDIRI (Apps Script Web App). Isi dengan URL Web App hasil
        // deploy, DIAKHIRI dengan "/" (contoh: ".../macros/s/AKfy..../").
        // Saat pindah ke VPS nanti, cukup ganti URL ini.
        buildConfigField("String", "https://script.google.com/macros/s/AKfycbxDFvOiVDvFJNazyuDR1RUvZzhXHHA-aBY__3wbsAZAdYfiDwCB0JM8eXmJQQ3CulqmzA/exec", "\"https://script.google.com/macros/s/GANTI_DENGAN_ID_DEPLOY/\"")

        // Kunci rahasia yang HARUS sama dengan SECRET_APP di Apps Script.
        buildConfigField("String", "Marsya", "\"GANTI_DENGAN_KUNCI_RAHASIA\"")
    }

    // PENTING: debug keystore dipatok ke file tetap (bukan auto-generate).
    // Tanpa ini, tiap kali GitHub Actions build APK, kuncinya beda-beda
    // sehingga Android menolak install-timpa versi lama - harus uninstall
    // dulu tiap update. Dengan ini, update berikutnya bisa install-timpa
    // langsung tanpa uninstall.
    signingConfigs {
        getByName("debug") {
            storeFile = file("../keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-ktx:1.9.1")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // Local offline queue
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Background sync + location
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Simpan token & device id terenkripsi
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
