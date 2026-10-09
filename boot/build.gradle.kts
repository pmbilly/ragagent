// 组合根模块（:boot）：入口 + 全局装配 + 扫描 + 打包（B165 / P3b-2）。
//
// 为什么独立：`config/` 与 `RagAgentApplication` 是**只出不进**的组合根（守卫 R-rule：依赖 config 的包 0 个），
// 集成测试（@SpringBootTest）也必须住在这里 —— 它们隐式依赖 `@SpringBootConfiguration`（包级向上搜索），
// 而 `:domains` 看不到 `:boot`（方向是 boot → domains）。
//
// 依赖方向：boot → domains(:server) → engine → common（一路向下，无环）。
plugins {
    java
    id("org.springframework.boot")
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
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.5")
    }
}

dependencies {
    implementation(project(":server"))
    // 组合根本身用到的 API（:server 的 compileOnly/implementation 不会传递到编译面）
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("com.baomidou:mybatis-plus-spring-boot3-starter:3.5.7")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("jakarta.validation:jakarta.validation-api")
    implementation(project(":engine"))
    implementation(project(":common"))
    testImplementation(testFixtures(project(":server")))
    testImplementation(testFixtures(project(":common")))   // EmbeddedRedis（共享测试基座）
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.tngtech.archunit:archunit:1.3.0")   // 架构规则（随 B165 迁入）
    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testImplementation("jakarta.validation:jakarta.validation-api")
}

// 构建信息（原 server/build.gradle.kts，随 bootBuildInfo 一起迁入）：
// system/service/SystemInfoService 通过 ObjectProvider<BuildProperties> 读它，缺失时降级。
fun gitShortCommit(): String =
    try {
        providers.exec { commandLine("git", "rev-parse", "--short", "HEAD") }.standardOutput.asText.get().trim()
    } catch (_: Exception) {
        "unknown"
    }

springBoot {
    buildInfo {
        properties {
            version = "0.8.0"
            additional = mapOf("commitId" to gitShortCommit())
        }
    }
}

// 迁移脚本同步（Flyway 读 application.yml 里的 filesystem:./build/generated-migrations —— **相对工作目录**，
// 所以这个 Copy 必须在**运行应用的那个模块**里做，否则起服读不到迁移）。
val syncMigrations = tasks.register<Copy>("syncMigrations") {
    from("$rootDir/migrations/versioned")
    into(layout.buildDirectory.dir("generated-migrations"))
}

tasks.named("processResources") { dependsOn(syncMigrations) }

tasks.withType<Test> {
    useJUnitPlatform()
    // ⚠️ 惰性取（doFirst）：配置期解析 testRuntimeClasspath 会与其他项目请求本模块 testFixtures
    // 元数据相撞（B165 实测：":server local metadata has not been calculated yet"）。
    doFirst {
        val byteBuddyAgent = configurations.testRuntimeClasspath.get().files
            .firstOrNull { it.name.startsWith("byte-buddy-agent-") }
        if (byteBuddyAgent != null) {
            jvmArgs("-javaagent:$byteBuddyAgent")
        }
    }
    jvmArgs("-XX:+EnableDynamicAgentLoading")
    maxHeapSize = "2g"
    // 测试沙箱化：DOCREADER_ADDR 一律置空（与 :server 同口径）
    environment("DOCREADER_ADDR", "")
    systemProperty("contract.refresh", System.getProperty("contract.refresh") ?: "false")
}
