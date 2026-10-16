import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.testing.jacoco.plugins.JacocoCoverageReport
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

// 根构建脚本：公共配置（坐标/仓库）与插件版本统一声明；插件此处不应用（apply false），
// 由各模块（domains/engine/common/boot）应用。
plugins {
    java
    // 覆盖率聚合（B220）：跨模块合并 exec 数据 ⇒ 给出**整体**覆盖率。
    id("jacoco-report-aggregation")
    // 插件版本统一在 gradle/libs.versions.toml（B214）
    alias(libs.plugins.spring.boot) apply false
    // dependency-management **实际应用**（不再 apply false）：聚合配置要解析子项目的依赖，
    // 而它们的版本来自 Boot BOM —— BOM 原先只在各模块声明，根项目没有 ⇒
    // 报 "Could not find org.springframework.boot:spring-boot-starter-web:."（版本为空）。
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.spotless) apply false
}

dependencyManagement {
    imports {
        mavenBom(libs.spring.boot.dependencies.get().toString())
    }
}

// ── 覆盖率聚合报告（B220）────────────────────────────────────────────────────
// 为什么需要：单模块报告只统计**本模块的类**，而本仓大量类被其他模块的测试间接覆盖
// （`:common` 的端口/工具被 `:domains` 的测试跑到、`:engine` 的被 `:boot` 的集成测试跑到）
// ⇒ 模块口径会**系统性低估**（首测 common 行覆盖仅 22.7%，而它的类明明在别处被跑）。
// 聚合把各模块 exec 合并后与全部类匹配，才是可信的整体数字。
//
// 用法：./gradlew testCodeCoverageReport -Pcoverage
//   （-Pcoverage 会同时开启各模块插桩；忘了加会因缺 exec 数据而得到空报告 ⇒ 见下方防呆）
// 产物：build/reports/jacoco/testCodeCoverageReport/html/index.html
dependencies {
    jacocoAggregation(project(":contract"))
    jacocoAggregation(project(":domains"))
    jacocoAggregation(project(":model-runtime"))
    jacocoAggregation(project(":retrieval-engine"))
    jacocoAggregation(project(":agent-engine"))
    jacocoAggregation(project(":platform"))
    jacocoAggregation(project(":boot"))
}

// 报告本身由插件**自动创建**（Gradle 8：声明 jacocoAggregation 依赖即注册 testCodeCoverageReport）
// ⇒ 不要手写 reporting{} 块（会撞名：ReportSpec 'testCodeCoverageReport' already exists）。

// 各模块 Javadoc 共用一个门槛：**只启用 doclint 的「引用」检查组**。
// 理由：本仓真实漂移过多次——改名/搬家后注释里的 {@link}/{@code}/{@see} 还指着旧类型
// （B111~B118 期间人工修过 ≥5 次）。交给 javadoc 自己的解析器判定，零维护成本；
// HTML 风格（</p>、heading 顺序）与 @param 完整性刻意**不**纳入——噪声大、价值低。

// ── 十一守卫（B214 接入；B217 加异常语义；B221 加契约同步）───────────────────
// 单一入口 scripts/run-guards.sh（B168 起）：包环 / Go 锚点 / 前端契约键 / JSON 键大小写
// （--strict）/ 事件面 / 文件规模棘轮 / 目录卫生 / 死成员 / 统一响应外壳。
// 注册在本块**之前**：下面的 subprojects{} 里 check 要 dependsOn 它，而 tasks.named()
// 是立即解析的（找不到即抛异常）。
val guards = tasks.register<Exec>("guards") {
    group = "verification"
    description = "十一守卫（脚本入口与 CI guards job 同一份）"
    workingDir = rootDir
    commandLine("bash", "scripts/run-guards.sh")
    // 默认 false：守卫失败即构建失败 —— 这正是接入的目的，别改成 ignoreExitValue。
}

subprojects {
    // withId：root 的 subprojects{} 在子项目应用插件**之前**求值，
    // ⚠️ 注意 guards 任务必须注册在本块**之前**（见下方 register），因为
    // rootProject.tasks.named("guards") 是立即解析的，找不到就抛。
    plugins.withId("java") {
        // 十一守卫（B214 接进 check）：`scripts/run-guards.sh` 是本地与 CI 的**同一份**入口，
        // 此前它只挂在 CI 的 guards job ⇒ 本地 `./gradlew build` 绿 ≠ CI 绿
        // （B168 实测漏检过一次真失败：本地手敲守卫漏了 check-json-key-case 的 --strict）。
        // 代价：CI 会跑两遍守卫（guards job + 本 job 的 build）—— 刻意的，两边同口径
        // 比省一次扫描值。需要 python3 在 PATH 上（九条守卫都是 Python）。
        tasks.named("check") { dependsOn(rootProject.tasks.named("guards")) }
        // `-parameters`：MyBatis 无 @Param 的参数绑定、Spring MVC/Jackson 的**参数名反射**都依赖它。
        // 该标志原由 org.springframework.boot 插件自动附加；B165 把 boot 插件从 :server 移到 :boot 后
        // :domains 丢失了它（当时还叫 :server） ⇒ 运行期大批 500（实测：BindingException: Parameter 'ids' not found +
        // IllegalArgumentException: Name for argument … not specified）。放这里统一声明，别再依赖插件隐含行为。
        tasks.withType<JavaCompile>().configureEach {
            options.compilerArgs.add("-parameters")
        }

        tasks.withType<Javadoc>().configureEach {
            (options as StandardJavadocDocletOptions).apply {
                addStringOption("Xdoclint:reference", "-quiet")
                addStringOption("Xmaxerrs", "10000")   // 默认截断在 100，看不到全貌
            }
        }
        // 接进 check ⇒ `./gradlew build`（CI backend job 跑的就是它）会连带执行 javadoc：
        // 注释里出现指向已不存在类型/成员/常量的 {@link}/{@value} 时**构建期就红**。
        tasks.named("check") { dependsOn(tasks.named("javadoc")) }

        // ── 覆盖率报告（B218）────────────────────────────────────────────────
        // 只**报告**、不设门：先让盲区可见（本仓长期"没有覆盖率工具"，薄弱区域只能靠
        // 文件清单推断）。阈值等基线跑稳了再议 —— 过早设门会逼出一堆"为达标而写"的测试。
        //   各模块：./gradlew :domains:jacocoTestReport -Pcoverage
        //   报告位置：<模块>/build/reports/jacoco/test/html/index.html
        //
        // ⚠️ 默认**不插桩**：jacoco agent 实测让全量闸门从 1m24s 涨到 1m49s（+25%），
        // 而"全量 1.5 分钟"是本仓刻意优化过的成果（B194），不该被无声回退。
        // 需要覆盖率时显式 `-Pcoverage`；忘了加会由 jacocoTestReport 的 doFirst 拦下报错，
        // 而不是安静地给出一份空报告。
        apply(plugin = "jacoco")
        val coverageEnabled = providers.gradleProperty("coverage").isPresent
        tasks.withType<Test>().configureEach {
            extensions.configure<JacocoTaskExtension> {
                isEnabled = coverageEnabled
            }
        }
        tasks.named<JacocoReport>("jacocoTestReport") {
            dependsOn(tasks.named("test"))
            reports {
                xml.required.set(true)    // 供后续接 Codecov / 自建看板
                html.required.set(true)
                csv.required.set(false)
            }
            doFirst {
                if (!coverageEnabled) {
                    throw GradleException(
                        "覆盖率数据未采集：本仓默认不插桩（省 25% 构建时间），请显式加 -Pcoverage\n"
                                + "  例：./gradlew :domains:jacocoTestReport -Pcoverage"
                    )
                }
            }
        }

        // 测试并行（B194）：本仓 4800+ 用例单 JVM 串行约 3 分钟。这里只让 Gradle 按**测试类**分派到
        // 多个测试 JVM（同类内仍串行 ⇒ 不破坏类内夹具顺序）。分叉数取核数/4 并夹在 1..3：
        // 本机 10 核 ⇒ 2；CI（2-4 核）⇒ 1，与并行前行为一致 ✓。
        // ⚠️ 契约测试连的是**真 Postgres**（boot/domains 的 test application.yml）⇒ 开启后必须跑两遍
        // 全量确认不 flaky（B194 实测两遍全绿才保留）。
        tasks.withType<Test>().configureEach {
            maxParallelForks = (Runtime.getRuntime().availableProcessors() / 4).coerceIn(1, 3)
        }
    }
}

allprojects {
    group = "com.ragagent"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

// ── dev server「陈旧」提醒（B186）────────────────────────────────────────────
// 本仓 dev server（scripts/java-server-up.sh 起的 :boot:bootRun）类路径里是**模块 jar**，
// 而构建会**就地重写**它们 ⇒ 运行中的 JVM 陷入「惰性类加载必炸」状态。2026-10-10 实测两次：
// ①全端点点 500（B184）；②某条"少走"的功能路径卡住（wiki 搜索的卡片一直转圈，B186）。
// 这里让 build 收尾时跑一次 scripts/dev-stale-check.sh：只提醒、不阻断；没有 dev server 时静默。
tasks.register<Exec>("devStaleWarn") {
    group = "verification"
    description = "提醒：正在运行的 dev server 是否比刚构建的产物更旧（需重启，见 B184/B186）"
    workingDir = rootDir
    commandLine("bash", "scripts/dev-stale-check.sh")
    isIgnoreExitValue = true
}
tasks.matching { it.name == "build" }.configureEach { finalizedBy("devStaleWarn") }
subprojects {
    tasks.matching { it.name == "build" }.configureEach { finalizedBy(rootProject.tasks.named("devStaleWarn")) }
}
