plugins {
    java
    `java-test-fixtures`
    id("io.spring.dependency-management")
    id("com.diffplug.spotless")
}

// 格式卫生：与 common/engine/domains/boot 同规格（ratchet + 生成代码不格式化）。
spotless {
    java {
        ratchetFrom("seed")
        targetExclude("build/**")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

// 依赖版本由 Boot BOM 统一（与 domains/boot 同款显式声明）。
dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.5")
    }
}

dependencies {
    // 通道层在栈上位于 :domains 之上（im/embedchannel 依赖 session/knowledge/agent/auth…✓）
    // ⇒ 只向下依赖，绝不反向 ✗（见 docs/phase4-module-boundaries-plan.md §12：前置代码边已清零 ✓）
    implementation(project(":common"))
    implementation(project(":domains"))
    testImplementation(testFixtures(project(":domains")))   // TestSchema 等共享测试基建
    testImplementation(testFixtures(project(":common")))    // EmbeddedRedis（B162 起在 :common ✓）

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("com.baomidou:mybatis-plus-spring-boot3-starter:3.5.7")

    // 测试依赖逐项声明（本仓各模块自持，非 root 统一 ✓）
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("com.h2database:h2")   // 仓储类测试的内存库 ✓
}

tasks.withType<Test> {
    // ⚠️ §10 沉淀①：新模块**必须**显式配 —— B162 实测 common 的 8 个测试类自 B116 起静默未跑 ✗。
    useJUnitPlatform()
    // Mockito inline 在 JDK 21+ 自挂 attach 会被拒 ⇒ 显式挂 byte-buddy-agent（同 domains ✓）。
    doFirst {
        val byteBuddyAgent = configurations.testRuntimeClasspath.get().files
            .firstOrNull { it.name.startsWith("byte-buddy-agent-") }
        if (byteBuddyAgent != null) {
            jvmArgs("-javaagent:$byteBuddyAgent")
        }
    }
    jvmArgs("-XX:+EnableDynamicAgentLoading")
    maxParallelForks = maxOf(1, minOf(4, Runtime.getRuntime().availableProcessors() / 2))
    maxHeapSize = "2g"
    environment("DOCREADER_ADDR", "")
    systemProperty("contract.refresh", System.getProperty("contract.refresh") ?: "false")
    systemProperty("user.timezone", "Asia/Shanghai")
}
