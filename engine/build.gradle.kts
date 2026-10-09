// 能力层模块（L2）：llm / retrieval / embedding / rerank / chatpipeline / modelcontext /
// webfetch / stream / tracing / model / vectorstore —— 11 域（368 文件 / 58.3k 行）。
//
// 存在意义（B161，M1 第二步，承 B116 的 :common）：把"能力层不得依赖业务域（L2 → L3 = 0）"
// 从守卫脚本规则**升格为编译规则** —— 此前由 check-package-cycles.py 的 R3 守（可被改白名单绕过），
// 现在往本模块 import 任何业务域类型都会 compileJava 失败（探针见 HANDOFF B161 行）。
//
// 依赖方向：server → engine → common；engine 的出边只有 common / event
// （2026-10-09 实测：common 320 处 / event 21 处，业务域 0 处）。
plugins {
    `java-library`
    // 共享测试基座（EmbeddedRedis：engine 2 个 + server 15 个测试共用）⇒ 按方案文档 §5.2 风险 5 走 testFixtures，
    // 而不是把 helper 复制两份或让它留在某一侧的测试树里。
    `java-test-fixtures`
    id("io.spring.dependency-management")
    id("com.diffplug.spotless")
    // OTLP proto（tracing 的 langfuse OTLP/HTTP 导出用）；docreader 的 proto 留在 server
    // —— 其 gRPC 客户端不在本模块（实测）。
    id("com.google.protobuf")
}

// 格式卫生：与 server / common 同口径（ratchet 从 seed 起只检查触碰过的文件）。
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
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.5")
    }
}

// OTLP proto 源目录：与 server 同一份 vendored 目录（只有消息定义、无 service ⇒ 不接 gRPC 插件）。
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.5"
    }
}

sourceSets["main"].proto {
    srcDir("$rootDir/otlp-proto")
}

dependencies {
    // 底座：engine 的公开面大量使用 common 词汇（端口/载荷/视图）⇒ api 暴露给 server。
    api(project(":common"))

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
    implementation("com.baomidou:mybatis-plus-spring-boot3-starter:3.5.7")
    implementation("org.neo4j.driver:neo4j-java-driver:5.28.5")
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    implementation("com.google.protobuf:protobuf-java:3.25.5")
    implementation("org.yaml:snakeyaml")
    implementation("com.zaxxer:HikariCP")
    // 运行期驱动（JDBC ServiceLoader 加载，编译期不可见；Doris 走 mysql 协议、pgvector 走 pg）
    runtimeOnly("com.mysql:mysql-connector-j")
    runtimeOnly("org.postgresql:postgresql")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("jakarta.validation:jakarta.validation-api")

    testImplementation(testFixtures(project(":common")))   // EmbeddedRedis（B162 起在 :common）
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
