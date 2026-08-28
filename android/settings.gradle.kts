// CI 环境（GitHub Actions runner 在海外）直连官方 Maven 源——
// 海外访问 maven.aliyun.com 不稳定（曾 502 导致构建失败）；本地（国内）继续用阿里云镜像加速
val onCi = System.getenv("CI") == "true" || System.getenv("GITHUB_ACTIONS") == "true"

pluginManagement {
    repositories {
        if (!onCi) {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (!onCi) {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "AestheticMusic"
include(":app")
include(":baselineprofile")
