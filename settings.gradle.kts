pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "ragagent-java"
include("domains")
include("common")
include("boot")
include("engine")
