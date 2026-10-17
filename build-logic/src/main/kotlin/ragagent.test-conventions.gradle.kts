plugins {
    java
}

// Boot 3.5 管理 JUnit 5.12；Gradle 8.14 自带 launcher 较旧，不显式对齐会让
// 整个 Test Executor 在 discovery 阶段失败（OutputDirectoryProvider not available）。
dependencies {
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    // Mockito inline 在 JDK 21+ 自挂 attach 会被拒；把 byte-buddy-agent 显式挂为
    // javaagent。惰性取（doFirst）：配置期解析 testRuntimeClasspath 会与其他项目
    // 请求本模块 testFixtures 元数据相撞。
    doFirst {
        val byteBuddyAgent = configurations.testRuntimeClasspath.get().files
            .firstOrNull { it.name.startsWith("byte-buddy-agent-") }
        if (byteBuddyAgent != null) {
            jvmArgs("-javaagent:$byteBuddyAgent")
        }
    }

    jvmArgs("-XX:+EnableDynamicAgentLoading")
    maxHeapSize = "1g"
}
