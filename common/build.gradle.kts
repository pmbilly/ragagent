// 共享内核（底座）：跨域词汇 / 不可变载荷 / 端口契约 + 少量通用基础设施实现。
//
// 存在意义（B116 抽取，B117 定名 :common）：把"底座不得反向依赖任何业务域"从守卫脚本规则
// **升格为编译规则** —— 此前由 check-package-cycles.py 的 R5 守（可被改白名单绕过），现在改不动了。
// 依赖方向：domains/engine/boot → common → **contract**（`api(project(":contract"))`）；
// 反向无依赖（本文件除 :contract 外没有任何 project(...)）。
//
// 2026-10-14（A③ 收口后）：契约面（com.ragagent.contract，18 面 / 116 文件）**已提取为独立模块 `:contract`**
// —— 模块名取单数 contract（＝顶层包名），不取方案字面的 contracts（该词在本仓已被
// domains/src/testFixtures/resources/contracts 的 1,426 金片 + 49 个 *ContractTest 占用，见下）。
//
// 命名取 common 而非 contracts 的两个理由（B117 记录，仍成立）：
//   1. 与顶层包一致 —— 146/219 文件就在 com.ragagent.common 下（模块名 = 顶层包名，最常规）；
//   2. 本仓 "contracts" 已被占用 —— domains/src/testFixtures/resources/contracts 有 1,426 个 golden
//      契约夹具、另有 49 个 *ContractTest，再叫 contracts 会语义撞车。
//
// ⚠️ 门槛（名字不承担约束，故写在这里）：这里只应有**跨域词汇 / 不可变载荷 / 端口契约**；
//    带 @Component/@Service 的实现应各归其域 —— 现存 3 个 bean（crypto/security/storage）
//    是守卫 R6 的棘轮基线，只许减不许增。
plugins {
    `java-library`
    `java-test-fixtures`   // 共享测试基座（EmbeddedRedis：L1 与上层测试共用）
    id("io.spring.dependency-management")
    id("com.diffplug.spotless")
    id("com.google.protobuf")   // OTLP proto（tracing 核心随 B163 迁入）
}

// 格式卫生：四个模块同口径（ratchet 从 seed 起只检查触碰过的文件）。
spotless {
    java {
        ratchetFrom("seed")
        // proto 插件把生成目录加进 source set ⇒ 生成的 OTLP 代码会被扫到（B161 engine 同款）
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

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
}

sourceSets["main"].proto {
    srcDir("$rootDir/otlp-proto")
}

dependencyManagement {
    imports {
        mavenBom(libs.spring.boot.dependencies.get().toString())
    }
}

dependencies {
    // 契约层（纯 JDK 公开 API/SPI）：common 的端口签名暴露契约类型 ⇒ api 传递
    api(project(":contract"))
    // 依 common/event 的实际 import 面声明（实测：jackson / spring / mybatis-plus / slf4j /
    // jakarta 六类，无云 SDK、无 jjwt、无 jdbc 驱动）—— 不用 starter 以免把自动配置漏进库。
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    api("org.springframework:spring-context")
    api("org.springframework:spring-web")
    api("org.springframework:spring-webmvc")
    api("org.springframework:spring-jdbc")
    api("org.springframework.boot:spring-boot")
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("org.springframework.data:spring-data-redis")
    api("org.slf4j:slf4j-api")
    implementation(libs.protobuf.java)
    // mybatis-plus：提供 org.apache.ibatis.* 与 com.baomidou.*（含 net.sf.jsqlparser）
    api(libs.mybatis.plus.starter)
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("jakarta.validation:jakarta.validation-api")

    testFixturesImplementation("io.lettuce:lettuce-core")   // EmbeddedRedis 建连接用
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // 主源码用 compileOnly 的 API（servlet/validation），测试期也要在类路径上（B163 实测）
    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testImplementation("jakarta.validation:jakarta.validation-api")
}

tasks.withType<Test> {
    // B162 实测踩到：原先 common 没有测试 ⇒ 缺 useJUnitPlatform() 也看不出来；
    // 搬入 stream 的 4 个测试类后它们**静默不跑**（build 里 common tests=0）⇒ 补上（与 engine 同口径）。
    useJUnitPlatform()
    val byteBuddyAgent = configurations.testRuntimeClasspath.get().files
        .firstOrNull { it.name.startsWith("byte-buddy-agent-") }
    if (byteBuddyAgent != null) {
        jvmArgs("-javaagent:$byteBuddyAgent")
    }
    jvmArgs("-XX:+EnableDynamicAgentLoading")
    maxHeapSize = "1g"
}
