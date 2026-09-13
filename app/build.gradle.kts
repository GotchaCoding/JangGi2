plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.janggi2"
    compileSdk = 36
    // 네이티브 빌드 결과(특히 16KB 페이지 정렬)가 기기·CI 마다 달라지지 않도록 고정합니다.
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.janggischool"
        minSdk = 34
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // NDK configuration for Fairy-Stockfish
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-O3", "-DNDEBUG")
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DCMAKE_BUILD_TYPE=Release"
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        // BuildConfig.DEBUG 로 디버그 전용 UI(디버그 버튼)를 가리기 위해 켭니다.
        // AGP 8부터는 기본으로 생성되지 않습니다.
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Navigation
    implementation(libs.navigation.compose)

    // ML Kit
    implementation(libs.mlkit.text.recognition.korean)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.kotlinx.coroutines.play.services)

    // OpenCV for circle detection.
    // 공식 org.opencv 배포판입니다. 예전엔 com.quickbirdstudios 래퍼를 썼지만 4.5.3.0
    // 에서 멈춰 16KB 페이지 정렬을 지원하지 않습니다. 자바 패키지(org.opencv)는 같습니다.
    implementation(libs.opencv)

    // On-device piece-type classification.
    // LiteRT 는 TFLite 의 후속 배포로, 1.x 는 org.tensorflow.lite 패키지를 그대로
    // 유지합니다. org.tensorflow:tensorflow-lite 는 2.16.1 까지도 16KB 정렬이 아닙니다.
    implementation(libs.litert)

    // Coil for image loading
    implementation(libs.coil.compose)

    // Material Icons Extended
    implementation(libs.androidx.compose.material.icons.extended)

    // Firebase (Auth + Firestore for cloud save sync)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)

    // Credential Manager + Google Identity Services (Google Sign-In)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}