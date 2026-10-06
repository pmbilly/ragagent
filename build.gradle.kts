// 根构建脚本：公共配置（坐标/仓库）与插件版本统一声明；插件此处不应用（apply false），
// 由 server 模块应用。
plugins {
    java
    id("org.springframework.boot") version "3.3.5" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
    id("com.google.protobuf") version "0.9.4" apply false
    id("com.diffplug.spotless") version "8.10.3" apply false
}

allprojects {
    group = "com.ragagent"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}
