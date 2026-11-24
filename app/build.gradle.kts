plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "asia.so1.vietnote"
    compileSdk = 35

    defaultConfig {
        applicationId = "asia.so1.vietnote"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // Chỉ build cho arm64 (máy đích: OnePlus 15) để APK nhẹ hơn.
        // AAR sherpa-onnx chứa cả 4 ABI; lọc ở đây để khỏi đóng gói thừa.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        getByName("debug") {
            // Keystore debug dùng chung với VDM Android, commit kèm repo để
            // mọi lần build (GitHub Actions) ký cùng 1 chữ ký, cài đè được.
            // Đây là khóa debug, KHÔNG phải khóa phát hành.
            storeFile = rootProject.file("debug.keystore")
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
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    androidResources {
        // Model .onnx để nguyên không nén để máy đọc thẳng từ assets cho nhanh.
        noCompress += "onnx"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // AAR sherpa-onnx 2.0.3 do workflow tải về app/libs/ lúc build
    // (không commit file 50 MB này lên repo).
    implementation(files("libs/sherpa-onnx-2.0.3.aar"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
}
