plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.google.protobuf")
    id("com.diffplug.spotless")
}

// 格式卫生：ratchet 从 seed 起只检查后续触碰的文件（避免全仓格式化大爆炸混入无关 diff）；
// 全量 formatter 待评估。
spotless {
    java {
        ratchetFrom("seed")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

/**
 * build-info.properties（/system/info 用）：version 显式对齐前端 package.json 的 0.8.0，
 * 否则前端按「后端版本 != 前端版本」显示「版本不匹配」告警。
 */
fun gitShortCommit(): String = try {
    providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        workingDir = rootDir
    }.standardOutput.asText.get().trim().ifEmpty { "unknown" }
} catch (_: Exception) {
    "unknown" // 非 git 检出（如打包好的源码树）→ 显示 unknown
}

springBoot {
    buildInfo {
        properties {
            version = "0.8.0"
            additional = mapOf("commitId" to gitShortCommit())
        }
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

dependencies {
    // 共享内核（common + event）——B116 抽成独立模块后在此声明依赖（B117 定名 :common）
    implementation(project(":common"))
    // Spring
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // 数据库
    implementation("com.baomidou:mybatis-plus-spring-boot3-starter:3.5.7")
    implementation("org.postgresql:postgresql")   // PGobject 等编译期可见（VectorStoreService）
    // Neo4j 图库驱动：NEO4J_ENABLE 未启用时驱动为 null，仓储全部操作为 no-op。
    implementation("org.neo4j.driver:neo4j-java-driver:5.28.5")
    // S3 协议族存储后端（s3/minio/obs/ks3 共用一套 S3 协议客户端）。
    // 版本要求 ≥2.30：才有 requestChecksumCalculation 可放宽尾校验和协商——
    // S3 兼容服务（MinIO/OBS/KS3）常拒绝默认协商。
    implementation("software.amazon.awssdk:s3:2.31.68")
    // 厂商原生存储 SDK：阿里云 OSS / 腾讯云 COS / 火山引擎 TOS。
    implementation("com.aliyun.oss:aliyun-sdk-oss:3.18.1")
    implementation("com.qcloud:cos_api:5.6.227")
    implementation("com.volcengine:ve-tos-java-sdk:2.9.19")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // gRPC（docreader 客户端；proto 在仓库根 docreader/ 目录）
    implementation("net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE")
    implementation("com.google.protobuf:protobuf-java:3.25.5")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")   // grpc 生成代码的 @Generated 注解

    // DuckDB（SQL 数据分析）
    implementation("org.duckdb:duckdb_jdbc:1.1.3")

    // SQLite 检索引擎：xerial 纯 JDBC 驱动，平台 native 由 Maven 构件自带（仓内零二进制）。
    // 3.46.1 起支持 FTS5 / contentless_delete / bm25()。
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")

    // Doris 检索引擎：MySQL 协议主链路；Stream Load（HTTP/8030）由 DorisStreamLoadClient
    // 自持，不用 SDK。版本由 Spring Boot BOM 管理。
    implementation("com.mysql:mysql-connector-j")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // bcrypt（DefaultCost=10）
    implementation("org.springframework.security:spring-security-crypto")

    // tiktoken BPE 编码器（cl100k_base；纯 BPE、纯 Java、零传递依赖）
    implementation("com.knuddels:jtokkit:1.1.0")

    // 工具
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // 测试
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // 架构规则测试（CI 的 ./gradlew build 即闸门）
    testImplementation("com.tngtech.archunit:archunit:1.3.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("com.h2database:h2")   // 契约/单元测试内存库，不依赖外部 postgres
}

// proto 源目录指向仓库根 docreader/（单一来源，不复制）
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.5"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.66.0"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins { create("grpc") }
        }
    }
}
sourceSets["main"].proto {
    srcDir("../docreader")
    // OTLP proto（langfuse OTLP/HTTP 导出用）：vendored 自 open-telemetry/opentelemetry-proto
    // v1.10.0（common/resource/trace 三文件，未改动）。
    srcDir("$rootDir/otlp-proto")
}

// Flyway 命名 V<version>__<desc>.sql；迁移已基线化（V1__baseline.sql，历史增量不再参与构建），
// 后续增量按 Flyway 命名放入 migrations/versioned/ 即可。
val syncMigrations = tasks.register<Copy>("syncMigrations") {
    from("$rootDir/migrations/versioned") {
        include("V*.sql")
    }
    into(layout.buildDirectory.dir("generated-migrations"))
}
tasks.named("processResources") { dependsOn(syncMigrations) }

tasks.withType<Test> {
    useJUnitPlatform {
        // CI 不提供开发库（PG）：带 needs-dev-pg 标签的实录测试在 CI 下排除。
        // 本地**刻意不排除** —— 那两个测试的 javadoc 写明「dev PG 不可达时显式失败（不允许跳过）」，
        // 免得本地环境坏掉时被静默掩盖（它们自种数据、对 JdbcExecutor 真跑 SQL）。
        if (System.getenv("CI") != null) {
            excludeTags("needs-dev-pg")
        }
    }
    // Mockito inline 在 JDK 21+ 自挂 attach 会被拒（MockitoInitializationException 批量假失败）：
    // 把 byte-buddy-agent 显式挂为 javaagent，Mockito 检测到已装入的 instrumentation 后不再 attach。
    val byteBuddyAgent = configurations.testRuntimeClasspath.get().files
        .firstOrNull { it.name.startsWith("byte-buddy-agent-") }
    if (byteBuddyAgent != null) {
        jvmArgs("-javaagent:$byteBuddyAgent")
    }
    jvmArgs("-XX:+EnableDynamicAgentLoading")
    // 并行分叉（2026-10-09 B142）：此前是**单 fork 串行**跑完 4,792 条 ⇒ 10 核机器上测试期间 9 核闲着，
    // 而套件是 I/O 型长尾（Top 20 类占 55%，全是 HTTP 契约往返与嵌入式 Redis）⇒ 分叉收益直接。
    // 实测：全量测试 214s → 见 §15.1.1 B142 记录（连跑三遍验稳定性）。
    // 注意：契约类会各起桩服务器/嵌入式 Redis，若将来出现端口或夹具冲突，先降 fork 数再查桩。
    maxParallelForks = 4
    // 测试 JVM 堆：单 fork 时代 5g（多个 @SpringBootTest 上下文各自驻留整份上下文）；
    // 分 4 叉后每个 JVM 驻留的上下文≈1/4 ⇒ 2g 起步（fork 数变了就重新看堆，别照搬旧值）。
    maxHeapSize = "2g"

    // 测试沙箱化：DOCREADER_ADDR 一律置空，避免测试 JVM 继承 shell 里 source 过的
    // dev-env.sh 而连上真 docreader 造成假红（空串与未设等价）。
    environment("DOCREADER_ADDR", "")

    // 契约夹具重录开关：-Dcontract.refresh=true 时把掩码后的实际响应写回
    // src/test/resources/contracts（平时是断言）；-D 只作用于 daemon JVM，需显式转发。
    systemProperty("contract.refresh", System.getProperty("contract.refresh") ?: "false")
}
