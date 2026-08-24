plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.aesthetic.music"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aesthetic.music"
        minSdk = 24
        targetSdk = 35
        versionCode = 12
        versionName = "1.6.0"
    }

    // 统一签名（私有签发）：keystore 不入库，每台打包机器需手动放置到
    // android/keystore/aesthetic.jks，保证不同机器打出的 APK 签名一致、OTA
    // 覆盖安装不互斥。文件缺失时回退本机默认 debug 签名（仅限日常开发调试，
    // 正式发布必须使用统一签名）。v1+v2 同开：修复部分国产 ROM 安装器报
    // "解析软件包时出现问题(33)"。
    val sharedKeystoreFile = rootProject.file("keystore/aesthetic.jks")

    signingConfigs {
        if (sharedKeystoreFile.exists()) {
            create("shared") {
                storeFile = sharedKeystoreFile
                storePassword = "***REMOVED***"
                keyAlias = "aesthetic"
                keyPassword = "***REMOVED***"
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.findByName("shared")
                ?: signingConfigs.getByName("debug")
        }
        release {
            // 开启 R8 + 资源收缩：Compose 运行时体量大，不混淆不裁剪会显著拉长
            // 冷启动 JIT 预热与滑动首帧耗时（首次安装卡、用一会儿变顺的主因之一）。
            // Gson 反序列化的数据模型已在 proguard-rules.pro 中保留字段名。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("shared")
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
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.coil.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.androidx.compose.foundation)

    // JankStats：采集真实帧耗时，量化滑动掉帧（logcat tag=JankStats）
    implementation(libs.androidx.metrics.performance)

    // Baseline Profiles：由 :baselineprofile 模块在真机上生成并打包进 APK，
    // 消除冷启动/首滑的 JIT 预热抖动。生成命令：
    //   .\gradlew.bat :app:generateReleaseBaselineProfile
    baselineProfile(project(":baselineprofile"))

    debugImplementation(libs.androidx.ui.tooling)
}
