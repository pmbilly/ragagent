pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "ragagent-java"
include("contract")
include("domains")
include("common")
include("boot")
include("engine")
