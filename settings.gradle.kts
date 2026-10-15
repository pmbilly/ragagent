pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "ragagent-java"
include("contract")
include("domains")
include("platform")
include("boot")
include("engine")
