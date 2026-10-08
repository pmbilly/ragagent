import org.gradle.external.javadoc.StandardJavadocDocletOptions

// 根构建脚本：公共配置（坐标/仓库）与插件版本统一声明；插件此处不应用（apply false），
// 由 server 模块应用。
plugins {
    java
    id("org.springframework.boot") version "3.3.5" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
    id("com.google.protobuf") version "0.9.4" apply false
    id("com.diffplug.spotless") version "8.10.3" apply false
}

// 各模块 Javadoc 共用一个门槛：**只启用 doclint 的「引用」检查组**。
// 理由：本仓真实漂移过多次——改名/搬家后注释里的 {@link}/{@code}/{@see} 还指着旧类型
// （B111~B118 期间人工修过 ≥5 次）。交给 javadoc 自己的解析器判定，零维护成本；
// HTML 风格（</p>、heading 顺序）与 @param 完整性刻意**不**纳入——噪声大、价值低。
subprojects {
    // withId：root 的 subprojects{} 在子项目应用插件**之前**求值，
    // 直接 tasks.named("check") 会报 "Task with name 'check' not found"（实测）。
    plugins.withId("java") {
        tasks.withType<Javadoc>().configureEach {
            (options as StandardJavadocDocletOptions).apply {
                addStringOption("Xdoclint:reference", "-quiet")
                addStringOption("Xmaxerrs", "10000")   // 默认截断在 100，看不到全貌
            }
        }
        // 接进 check ⇒ `./gradlew build`（CI backend job 跑的就是它）会连带执行 javadoc：
        // 注释里出现指向已不存在类型/成员/常量的 {@link}/{@value} 时**构建期就红**。
        tasks.named("check") { dependsOn(tasks.named("javadoc")) }
    }
}

allprojects {
    group = "com.ragagent"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}
