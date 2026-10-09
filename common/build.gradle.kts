// 共享内核（底座）：跨域词汇 / 不可变载荷 / 端口契约 + 少量通用基础设施实现。
//
// 存在意义（B116 抽取，B117 定名 :common）：把"底座不得反向依赖任何业务域"从守卫脚本规则
// **升格为编译规则** —— 此前由 check-package-cycles.py 的 R5 守（可被改白名单绕过），现在改不动了。
// 依赖方向：server → common；**common → 无**（本 build 文件里没有任何 project(...)）。
//
// 命名取 common 而非 contracts 的两个理由：
//   1. 与顶层包一致 —— 135/175 文件就在 com.ragagent.common 下（模块名 = 顶层包名，最常规）；
//   2. 本仓 "contracts" 已被占用 —— server/src/test/resources/contracts 有 1,426 个 golden
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

// 格式卫生：与 server 同口径（ratchet 从 seed 起只检查触碰过的文件）。
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
        artifact = "com.google.protobuf:protoc:3.25.5"
    }
}

sourceSets["main"].proto {
    srcDir("$rootDir/otlp-proto")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.5")
    }
}

dependencies {
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
    implementation("com.google.protobuf:protobuf-java:3.25.5")
    // mybatis-plus：提供 org.apache.ibatis.* 与 com.baomidou.*（含 net.sf.jsqlparser）
    api("com.baomidou:mybatis-plus-spring-boot3-starter:3.5.7")
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
