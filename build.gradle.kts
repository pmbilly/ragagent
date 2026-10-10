import org.gradle.external.javadoc.StandardJavadocDocletOptions

// 根构建脚本：公共配置（坐标/仓库）与插件版本统一声明；插件此处不应用（apply false），
// 由各模块（domains/engine/common/boot）应用。
plugins {
    java
    id("org.springframework.boot") version "3.3.5" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
    id("com.google.protobuf") version "0.9.4" apply false
    id("com.diffplug.spotless") version "8.10.3" apply false
}

// 各模块 Javadoc 共用一个门槛：**只启用 doclint 的「引用」检查组**。
// 理由：本仓真实漂移过多次——改名/搬家后注释里的 {@link}/{@code}/{@see} 还指着旧类型
// （B111~B118 期间人工修过 ≥5 次）。交给 javadoc 自己的解析器判定，零维护成本；
// HTML 风格（</p>、heading 顺序）与 @param 完整性刻意**不**纳入——噪声大、价值低。
subprojects {
    // withId：root 的 subprojects{} 在子项目应用插件**之前**求值，
    // 直接 tasks.named("check") 会报 "Task with name 'check' not found"（实测）。
    plugins.withId("java") {
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
