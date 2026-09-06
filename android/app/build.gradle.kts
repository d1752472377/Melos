import java.util.Properties

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
        versionCode = 20
        versionName = "1.10.4"
    }

    // 统一签名（私有签发）：keystore 不入库，每台打包机器需手动放置到
    // android/keystore/aesthetic.jks，保证不同机器打出的 APK 签名一致、OTA
    // 覆盖安装不互斥。
    //
    // 口令来源优先级（均为机密，绝不写入受版本控制的文件）：
    //   1. 环境变量 AESTHETIC_STORE_PASSWORD / AESTHETIC_KEY_PASSWORD
    //      —— GitHub Actions Secrets 中的命名（CI 用），本地可用 setx 设置持久化
    //   2. 环境变量 KEYSTORE_PASSWORD（兼容旧配置）或 local.properties 的
    //      `keystore.password`（local.properties 已 gitignore）
    // 以上均缺失时回退本机默认 debug 签名（仅限日常开发调试，正式发布必须配置统一签名）。
    // v1+v2 同开：修复部分国产 ROM 安装器报 "解析软件包时出现问题(33)"。
    val sharedKeystoreFile = rootProject.file("keystore/aesthetic.jks")
    val keystoreProps = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    val storePw = System.getenv("AESTHETIC_STORE_PASSWORD")
        ?: System.getenv("KEYSTORE_PASSWORD")
        ?: keystoreProps.getProperty("keystore.password")
    val keyPw = System.getenv("AESTHETIC_KEY_PASSWORD")
        ?: storePw

    signingConfigs {
        if (sharedKeystoreFile.exists() && storePw != null && keyPw != null) {
            create("shared") {
                storeFile = sharedKeystoreFile
                storePassword = storePw
                keyAlias = "aesthetic"
                keyPassword = keyPw
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    // 缺少统一签名时给出构建期可见警告：此前 release 静默产出未签名/异签名 APK，
    // 用户侧表现为"更新时解析失败"，排查半天才发现是签名配置缺失
    if (!sharedKeystoreFile.exists() || storePw == null || keyPw == null) {
        println(
            "WARNING: 统一签名未配置（android/keystore/aesthetic.jks 或口令缺失），" +
                "release 构建将回退 debug 签名或产出未签名 APK，不能用于 OTA 覆盖更新"
        )
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
    // androidx-navigation-compose 已移除：全 App 用 AppShell 手写导航（StateFlow 覆盖层栈），
    // 无 NavHost/NavController 引用，留着的传递依赖只增构建体积

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.coil.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.quickjs.android)

    // JankStats：采集真实帧耗时，量化滑动掉帧（logcat tag=JankStats）
    implementation(libs.androidx.metrics.performance)

    // Baseline Profiles：由 :baselineprofile 模块在真机上生成并打包进 APK，
    // 消除冷启动/首滑的 JIT 预热抖动。生成命令：
    //   .\gradlew.bat :app:generateReleaseBaselineProfile
    baselineProfile(project(":baselineprofile"))

    debugImplementation(libs.androidx.ui.tooling)
}
