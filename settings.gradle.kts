pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

// 本机没有 JDK 21 时由 Gradle 自动下载（toolchain 解析）。
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "ragagent-java"

// 构建约定（toolchain / Spotless / Boot BOM / 测试基建）：消除 7 份复制样板。
includeBuild("build-logic")
include("contract")
include("domains")
include("platform")
include("boot")
include("model-runtime")
include("retrieval-engine")
include("agent-engine")
