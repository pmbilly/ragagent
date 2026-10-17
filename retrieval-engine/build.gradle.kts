// 检索引擎模块（R3c 切出，2026-10-16）：`com.ragagent.retrieval`（91 件主源码 + 30 件测试）。
//
// 依赖方向：retrieval-engine → platform → contract（**对模型族出边 = 0**：R3a 已把
// VLM 客户端归位 model-runtime，索引面用的 `Embedder` 早在 R2 就上了契约面）。
//
// 技术栈按实测 import 面声明：Jackson（databind/core/annotations）· Neo4j driver ·
// Spring（context/jdbc/boot 配置绑定）· HikariCP · sqlite-jdbc；不含 MyBatis / Redis / servlet。
//
// 过渡说明：engine（chatpipeline/webfetch 还在）与 domains/boot 现阶段经 engine 的 `api` 传递可见
// 本模块类型——R3d 端口化后再收窄成逐消费点显式声明。
plugins {
    `java-library`
    id("io.spring.dependency-management")
    id("com.diffplug.spotless")
}

spotless {
    java {
        ratchetFrom("seed")
        targetExclude("build/**")
        // import 口径（2026-10 全仓重排同款）：\# 前缀=static 块置顶，空串=其余单块 ASCII 序
        importOrder("\\#", "")
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
    // 底座：检索面签名大量使用契约词汇（SearchResult/SearchParams/IndexInfo…）⇒ api 传递
    api(project(":platform"))
    api(project(":contract"))

    // 依实测 import 面声明（jackson 87 处 / neo4j 11 / spring-jdbc·context·stereotype /
    // Hikari 2 / sqlite 1）—— 不用 starter，避免把自动配置漏进库。
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    api("org.springframework:spring-context")
    api("org.springframework:spring-jdbc")
    api("org.springframework.boot:spring-boot")
    api("org.slf4j:slf4j-api")
    implementation(libs.neo4j.java.driver)
    implementation(libs.sqlite.jdbc)
    implementation("com.zaxxer:HikariCP")

    // 运行期驱动（JDBC ServiceLoader 加载，编译期不可见；Doris 走 mysql 协议、pgvector 走 pg）
    runtimeOnly("com.mysql:mysql-connector-j")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation(testFixtures(project(":platform")))   // EmbeddedRedis 等平台测试基座
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

// ── 集成测试任务（随 R3c 从 :engine 原样搬来）───────────────────────────────
// `*IT` 不匹配 Gradle 默认收集模式 ⇒ 必须给显式入口，且**不接 check**（要真实外部服务，env 门控）：
//   WEKNORA_MILVUS_IT=true ./gradlew :retrieval-engine:integrationTest
// 未设 env 时 JUnit 的 @EnabledIfEnvironmentVariable 把类标为 disabled ⇒ 任务绿、零联网。
val integrationTest = tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "集成测试（*IT）：需真实外部服务，env 门控，默认不接 check"
    useJUnitPlatform()
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    include("**/*IT.class")
    val byteBuddyAgent = configurations.testRuntimeClasspath.get().files
        .firstOrNull { it.name.startsWith("byte-buddy-agent-") }
    if (byteBuddyAgent != null) {
        jvmArgs("-javaagent:$byteBuddyAgent")
    }
    jvmArgs("-XX:+EnableDynamicAgentLoading")
    maxHeapSize = "1g"
}

// 反向显式化：默认 test 任务**不**收 `*IT`（今天靠默认模式排除，写出来免得将来被人改掉口径）。
tasks.named<Test>("test") {
    exclude("**/*IT.class")
}
