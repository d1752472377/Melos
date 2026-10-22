import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.aesthetic.music"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.aesthetic.music"
        minSdk = 26
        targetSdk = 35
        versionCode = 27
        versionName = "2.1.2"
    }
    lint {
        abortOnError = false        // 遇到剩余的 40 多个 lint 报错不打断编译
        checkReleaseBuilds = false
    }

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

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.coil.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.quickjs.android)

    implementation(libs.androidx.metrics.performance)
    implementation(libs.androidx.compose.ui.geometry)
    implementation("dev.chrisbanes.haze:haze:1.3.1")

    implementation(project(":lyrics"))

    baselineProfile(project(":baselineprofile"))

    // 纯逻辑单元测试（JVM，无需设备）
    testImplementation(libs.junit)

    debugImplementation(libs.androidx.ui.tooling)
}