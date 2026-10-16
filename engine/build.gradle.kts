// 能力层模块（L2）：chatpipeline / webfetch —— 2 域（R3c 后：retrieval 已切 :retrieval-engine、
// llm/modelruntime/embedding/rerank/modelcontext 已切 :model-runtime、tracing 已归 platform）。
// （`stream` 已下沉 `:common`；`tracing` 只留 `decorators` 侧；`model`/`vectorstore` 已回业务侧。）
//
// 存在意义（B161，M1 第二步，承 B116 的 :common）：把"能力层不得依赖业务域（L2 → L3 = 0）"
// 从守卫脚本规则**升格为编译规则** —— 此前由 check-package-cycles.py 的 R3 守（可被改白名单绕过），
// 现在往本模块 import 任何业务域类型都会 compileJava 失败（探针见 HANDOFF B161 行）。
//
// 依赖方向：domains → engine → common；engine 的出边只有 common / event
// （2026-10-09 实测：common 320 处 / event 21 处，业务域 0 处）。
plugins {
    `java-library`
    // 共享测试基座（wire/ 录制夹具：domains 的线格式测试消费）⇒ 按方案文档 §5.2 风险 5 走 testFixtures，
    // 而不是把 helper 复制两份或让它留在某一侧的测试树里。
    `java-test-fixtures`
    id("io.spring.dependency-management")
    id("com.diffplug.spotless")
}

// 格式卫生：四个模块同口径（ratchet 从 seed 起只检查触碰过的文件）。
spotless {
    java {
        ratchetFrom("seed")
        // proto 插件把生成目录加进 source set ⇒ 生成的 OTLP 代码会被扫到（实测报 28 个文件）
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
    // 底座：engine 的公开面大量使用 common 词汇（端口/载荷/视图）⇒ api 暴露给 server。
    api(project(":platform"))
    api(project(":contract"))
    // 模型运行时（R2 提取）：chatpipeline / webfetch 仍引用其类型（LlmTransport 归 platform 后
    // 出边已收窄）⇒ 过渡期 api 传递；R4b/R6 收口后改为逐消费点显式声明。
    api(project(":model-runtime"))
    // 检索引擎（R3c 提取）：chatpipeline 的检索插件引用其类型，domains/boot 也经此传递可见
    // ⇒ 过渡期 api 传递；R3d 端口化后收窄。
    api(project(":retrieval-engine"))

    // 依 11 域的实际 import 面声明（实测 top：jackson 288 / slf4j 122 / spring-web 32 /
    // mybatis 30 / neo4j 11 / spring-http·stereotype 10 / OTLP proto 9 / spring-data-redis 7 /
    // sqlite 1 / snakeyaml 2 / Hikari 2 / jakarta 4）—— 不用 starter，避免把自动配置漏进库。
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    api("org.springframework:spring-context")
    api("org.springframework:spring-web")
    api("org.springframework:spring-jdbc")
    api("org.springframework.data:spring-data-redis")
    api("org.springframework.boot:spring-boot")
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("org.slf4j:slf4j-api")
    implementation(libs.mybatis.plus.starter)
    implementation(libs.neo4j.java.driver)
    implementation(libs.sqlite.jdbc)
    implementation("org.yaml:snakeyaml")
    implementation("com.zaxxer:HikariCP")
    // 运行期驱动（JDBC ServiceLoader 加载，编译期不可见；Doris 走 mysql 协议、pgvector 走 pg）
    runtimeOnly("com.mysql:mysql-connector-j")
    runtimeOnly("org.postgresql:postgresql")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("jakarta.validation:jakarta.validation-api")

    testImplementation(testFixtures(project(":platform")))   // EmbeddedRedis（B162 起在 :common）
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // EmbeddedRedis（testFixtures）用 Lettuce 建连接 ⇒ fixtures 侧显式声明
    // 主源码用 compileOnly 的 API（servlet/validation），测试期也要在类路径上
    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testImplementation("jakarta.validation:jakarta.validation-api")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Mockito inline 在 JDK 21+ 自挂 attach 会被拒（与 server 同法挂 byte-buddy-agent）。
    val byteBuddyAgent = configurations.testRuntimeClasspath.get().files
        .firstOrNull { it.name.startsWith("byte-buddy-agent-") }
    if (byteBuddyAgent != null) {
        jvmArgs("-javaagent:$byteBuddyAgent")
    }
    jvmArgs("-XX:+EnableDynamicAgentLoading")
    maxHeapSize = "1g"
}

// ── 集成测试任务（B218）─────────────────────────────────────────────────────
// 起因：`*IT` 不匹配 Gradle 的默认收集模式（`*Test` / `Test*` / `*Tests` / `*TestCase`）
// ⇒ 本职的两个类（MilvusDriverLocalIT / WeaviateDriverLocalIT）**从未被执行过**，
// 连它们 javadoc 里写的 `--tests "*...IT*"` 也筛不出（候选集是空的 ⇒ 报 No tests found）。
// 覆盖上的死代码比"低覆盖"更糟：它看起来存在。
//
// 这里给它们一个**显式的执行入口**，且**不接 check** —— 它们要真实外部服务（env 门控）：
//   WEKNORA_MILVUS_IT=true ./gradlew :engine:integrationTest
// 未设 env 时 JUnit 的 @EnabledIfEnvironmentVariable 会把类标为 disabled ⇒ 任务绿、零联网。
val integrationTest = tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "集成测试（*IT）：需真实外部服务，env 门控，默认不接 check"
    useJUnitPlatform()
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    include("**/*IT.class")
    // 与 test 同口径的 JVM 设置（IT 同样用 Mockito / 需要 agent）
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
