// 平台层（stage 6 提取，2026-10-16）：框架、可靠执行与通用技术适配；**不含业务状态机**。
//
// 依赖方向（方案 §5.2）：platform → contracts（+ 框架库）；**platform 不得依赖业务域**（§12.2 DAG 规则，
// 提取后由 Gradle 编译边强制 —— 此前 0 处违规，见 docs/internal/HANDOFF.md §12.5 准入 G2/G4）。
//
// 来源：common/src/main/java/com/ragagent/platform/**（135 件）+ 平台侧测试（22 件）+
// testFixtures（EmbeddedRedis：L1 与上层测试共用）；**包名不变** ⇒ 组件/属性/Mapper 扫描面零改动（G3）。
plugins {
    `java-library`
    `java-test-fixtures`   // EmbeddedRedis（平台侧测试基座，domains/engine/boot 共用）
    id("com.google.protobuf")   // OTLP proto（tracing 核心随 B163 迁入 platform/tracing）
    id("ragagent.java-conventions")
    id("ragagent.test-conventions")
}

sourceSets["main"].proto {
    srcDir("$rootDir/otlp-proto")
}


protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
}

dependencies {
    // 契约层（纯 JDK 公开 API/SPI）：common 的端口签名暴露契约类型 ⇒ api 传递
    api(project(":contract"))
    // 依 common/event 的实际 import 面声明（实测：jackson / spring / mybatis-plus / slf4j /
    // jakarta 六类，无云 SDK、无 jjwt、无 jdbc 驱动）—— 不用 starter 以免把自动配置漏进库。
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    // Boot 4 过渡期：GlobalExceptionHandler 需要识别 tools.jackson 的 MismatchedInputException
    compileOnly("tools.jackson.core:jackson-databind")
    api("org.springframework:spring-context")
    api("org.springframework:spring-web")
    api("org.springframework:spring-webmvc")
    api("org.springframework:spring-jdbc")
    api("org.springframework.boot:spring-boot")
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("org.springframework.data:spring-data-redis")
    api("org.slf4j:slf4j-api")
    implementation(libs.protobuf.java)
    // mybatis-plus：提供 org.apache.ibatis.* 与 com.baomidou.*
    api(libs.mybatis.plus.starter)
    // 3.5.9+ 把 JSQLParser 支持拆成独立 artifact；平台 SQL 守卫直接用 net.sf.jsqlparser
    api(libs.mybatis.plus.jsqlparser)
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("jakarta.validation:jakarta.validation-api")

    testFixturesImplementation("io.lettuce:lettuce-core")   // EmbeddedRedis 建连接用
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // 主源码用 compileOnly 的 API（servlet/validation），测试期也要在类路径上（B163 实测）
    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testImplementation("jakarta.validation:jakarta.validation-api")
}

// 编译期静态分析最小面（与 :contract 同款；见该模块注释）。-serial 排除同因：
// 异常类 serialVersionUID 告警在本模块同为噪音。
tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.addAll(listOf("-Xlint:all,-serial", "-Werror"))
}
