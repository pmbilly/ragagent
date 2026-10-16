// 模型运行时模块（R2 提取，2026-10-16）：llm / modelruntime / embedding / rerank / modelcontext。
//
// 依赖方向：model-runtime → platform → contract。对 engine 其余包（retrieval / chatpipeline /
// webfetch / tracing）的出边实测 0（llm 与 modelcontext 全零）⇒ 可独立成模块。
//
// 跨模块消费走契约面：R2a 已把 LlmChatClient / ChatStream / StreamResponse（SSE 帧）/
// Embedder / Reranker / RankResult 上提 `contract.model`。
//
// 过渡说明：engine（retrieval / chatpipeline / webfetch / tracing）与 domains 现阶段经
// `api` 传递可见本模块类型——待 R2c 组装权下沉（`ModelAccessResolver` + 组装入组合根）后收窄。
plugins {
    `java-library`
    id("io.spring.dependency-management")
    id("com.diffplug.spotless")
}

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

dependencyManagement {
    imports {
        mavenBom(libs.spring.boot.dependencies.get().toString())
    }
}

dependencies {
    // 底座：模型面签名大量使用契约词汇（ChatMessage/ChatOptions/Embedder/...）⇒ api 传递
    api(project(":platform"))
    api(project(":contract"))

    // 依实际 import 面声明（实测：jackson / slf4j / spring-context（装配注解）/
    // spring-web（HttpHeaders 常量）/ spring-data-redis（限流器）—— 不用 starter，
    // 避免把自动配置漏进库。
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    api("org.springframework:spring-context")
    api("org.springframework:spring-web")
    api("org.springframework.data:spring-data-redis")
    api("org.slf4j:slf4j-api")

    testImplementation(testFixtures(project(":platform")))   // EmbeddedRedis
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Mockito inline 在 JDK 21+ 自挂 attach 会被拒（与 engine/domains 同法挂 byte-buddy-agent）。
    val byteBuddyAgent = configurations.testRuntimeClasspath.get().files
        .firstOrNull { it.name.startsWith("byte-buddy-agent-") }
    if (byteBuddyAgent != null) {
        jvmArgs("-javaagent:$byteBuddyAgent")
    }
    jvmArgs("-XX:+EnableDynamicAgentLoading")
    maxHeapSize = "1g"
}
