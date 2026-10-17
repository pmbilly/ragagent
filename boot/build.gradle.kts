// 组合根模块（:boot）：入口 + 全局装配 + 扫描 + 打包（B165 / P3b-2）。
//
// 为什么独立：`config/` 与 `RagAgentApplication` 是**只出不进**的组合根（守卫 R-rule：依赖 config 的包 0 个），
// 集成测试（@SpringBootTest）也必须住在这里 —— 它们隐式依赖 `@SpringBootConfiguration`（包级向上搜索），
// 而 `:domains` 看不到 `:boot`（方向是 boot → domains）。
//
// 依赖方向：boot → domains（原 :server，B167 更名）→ engine → common（一路向下，无环）。
plugins {
    java
    id("org.springframework.boot")
    id("ragagent.java-conventions")
    id("ragagent.test-conventions")
}

dependencies {
    implementation(project(":agent-engine"))
    // 模型运行时：组合根自建模型装配/并发闸门（R4b 后不再经 :engine 传递）
    implementation(project(":model-runtime"))

    // 检索引擎：组合根自建引擎注册表/工厂与审计 sink（R3c 后不再经 engine 传递）
    implementation(project(":retrieval-engine"))

    implementation(project(":domains"))
    // 组合根本身用到的 API（:domains 的 compileOnly/implementation 不会传递到编译面）
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Boot 4 移除了 starter-aop；AOP 代理由 spring-aop（context 传递）提供，补 AspectJ weaver
    implementation("org.aspectj:aspectjweaver")
    // 过渡期 Jackson 2 ObjectMapper 由 WebConfig 显式提供（不再引 Boot 的
    // spring-boot-jackson2 自动配置）；Jackson 3 迁移完成后删除该 bean。
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation(libs.mybatis.plus.starter)
    // OpenAPI JSON（dev-only，默认关闭；UI 由 boot 的 /scalar 页面承载）
    implementation(libs.springdoc.openapi.starter.webmvc.api)
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("jakarta.validation:jakarta.validation-api")
    implementation(project(":platform"))
    implementation(project(":contract"))
    testImplementation(testFixtures(project(":domains")))
    testImplementation(testFixtures(project(":platform")))   // EmbeddedRedis（共享测试基座）
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Boot 4：@AutoConfigureMockMvc 迁到 spring-boot-webmvc-test
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    // 集成测试走内存库（与 :domains 同口径）。⚠️ testRuntimeOnly **不可传递** ⇒ 必须显式声明，
    // 否则测试会落到 application.yml 里的真 PG URL 上，TestSchema 的 H2 专有 DDL 直接 bad SQL grammar。
    testRuntimeOnly("com.h2database:h2")
    testImplementation(libs.archunit)   // 架构规则（随 B165 迁入）
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
    // 契约夹具按「模块相对 + server/ 兜底」两候选读 **文件路径**（18 处调用点 + GoldenContract 内置）；
    // 集成测试从 :server 迁到 :boot 后，工作目录变成 boot/ ⇒ 两个候选都落空（B165 实测 97 处失败）。
    // 统一把工作目录设为**仓库根**，两候选中的第二个（domains/src/testFixtures/resources/contracts/…）即可命中。
    workingDir = rootDir
    maxHeapSize = "2g"
    // 测试沙箱化：DOCREADER_ADDR 一律置空（与 :domains 同口径）
    environment("DOCREADER_ADDR", "")
    systemProperty("contract.refresh", System.getProperty("contract.refresh") ?: "false")
}

// dev 起服把仓库根的 .env 逐项注入进程环境（脚本路径用 scripts/dev-env.sh，同一口径）。
// 只挂在 bootRun：测试、容器与 jar 启动都不读，避免误用开发库。
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    val envFile = rootProject.file(".env")
    if (envFile.isFile) {
        envFile.readLines()
            .map { it.trim().removePrefix("export ").trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
            .forEach { line ->
                val key = line.substringBefore("=").trim()
                val value = line.substringAfter("=").trim()
                    .removeSurrounding("\"").removeSurrounding("'")
                // 与 dev-env.sh 一致：显式环境变量优先于 .env
                if (key.isNotEmpty() && System.getenv(key) == null) {
                    environment(key, value)
                }
            }
    }
}
