// CI 环境（GitHub Actions runner 在海外）直连官方 Maven 源——
// 海外访问 maven.aliyun.com 不稳定（曾 502 导致构建失败）；本地（国内）继续用阿里云镜像加速。
// 注意：Kotlin DSL 各块有独立作用域，判断变量需在块内声明
pluginManagement {
    val onCi = System.getenv("CI") == "true" || System.getenv("GITHUB_ACTIONS") == "true"
    repositories {
        if (!onCi) {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        }
        google()
        if (onCi) {
            // Google 托管的 Maven Central 镜像（GCS + CDN）。
            // 为什么需要它：runner 直连 repo.maven.apache.org 偶发 "Connection reset"
            // （2026-10-22 的 v2.1.0 构建就是被 io.coil-kt:coil:2.7.0 的 aar 下载 reset 打断的），
            // 而 Gradle 遇到网络错误**不会**自动换下一个仓库，只会让整条流水线失败。
            // 把镜像放在 mavenCentral() 之前：命中就走 CDN，没命中会 404 → 自动回落到官方源。
            maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    val onCi = System.getenv("CI") == "true" || System.getenv("GITHUB_ACTIONS") == "true"
    repositories {
        if (!onCi) {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
        }
        google()
        if (onCi) {
            // 同 pluginManagement：CI 上先用 Google 的 Central 镜像，避免被单个 aar 的 reset 打断
            maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        }
        mavenCentral()
    }
}

rootProject.name = "AestheticMusic"
include(":app")
include(":baselineprofile")
include(":lyrics")
