pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AioShell"

include(":app")

// 数据层：网络 / 数据库 / 密钥 / 配置仓库
include(":core:data")
// 共享 UI：主题 / 通用组件
include(":core:ui")

// 功能模块：接口配置 / 会话 / 对话
include(":feature:config")
include(":feature:chat")
include(":feature:session")