import org.gradle.external.javadoc.StandardJavadocDocletOptions

// 根构建脚本：公共配置（坐标/仓库）与插件版本统一声明；插件此处不应用（apply false），
// 由各模块（domains/engine/common/boot）应用。
plugins {
    java
    // 插件版本统一在 gradle/libs.versions.toml（B214）
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.spotless) apply false
}

// 各模块 Javadoc 共用一个门槛：**只启用 doclint 的「引用」检查组**。
// 理由：本仓真实漂移过多次——改名/搬家后注释里的 {@link}/{@code}/{@see} 还指着旧类型
// （B111~B118 期间人工修过 ≥5 次）。交给 javadoc 自己的解析器判定，零维护成本；
// HTML 风格（</p>、heading 顺序）与 @param 完整性刻意**不**纳入——噪声大、价值低。

// ── 十守卫（B214 接入；B217 加第十条「异常语义」）──────────────────────────────
// 单一入口 scripts/run-guards.sh（B168 起）：包环 / Go 锚点 / 前端契约键 / JSON 键大小写
// （--strict）/ 事件面 / 文件规模棘轮 / 目录卫生 / 死成员 / 统一响应外壳。
// 注册在本块**之前**：下面的 subprojects{} 里 check 要 dependsOn 它，而 tasks.named()
// 是立即解析的（找不到即抛异常）。
val guards = tasks.register<Exec>("guards") {
    group = "verification"
    description = "十守卫（脚本入口与 CI guards job 同一份）"
    workingDir = rootDir
    commandLine("bash", "scripts/run-guards.sh")
    // 默认 false：守卫失败即构建失败 —— 这正是接入的目的，别改成 ignoreExitValue。
}

subprojects {
    // withId：root 的 subprojects{} 在子项目应用插件**之前**求值，
    // ⚠️ 注意 guards 任务必须注册在本块**之前**（见下方 register），因为
    // rootProject.tasks.named("guards") 是立即解析的，找不到就抛。
    plugins.withId("java") {
        // 十守卫（B214 接进 check）：`scripts/run-guards.sh` 是本地与 CI 的**同一份**入口，
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
