// 契约层：纯 JDK 的公开 API / SPI / 不可变载荷。
//
// 模块名取**单数** `contract`（＝顶层包 com.ragagent.contract）：本仓约定「模块名 = 顶层包名」，
// 且 `contracts` 已被 domains 的契约夹具目录占用：1,349 份 JSON 金片 + 5 份非 JSON 样本，
// 另有 48 个 *ContractTest。
//
// 门槛：把「契约面纯 JDK」从 ArchUnit 的**静态规则**升格为**编译期硬约束** ——
// 本模块的生产依赖块为空，一沾框架/其他模块就编译不过，回流即时红。
// 依赖方向：其余模块 → contract；contract → 无（纯 JDK）。
plugins {
    `java-library`
    id("ragagent.java-conventions")
    id("ragagent.test-conventions")
}

// 编译期静态分析最小面：契约层最稳定，全量 lint + 警告即错误先行
//（其余模块按棘轮节奏跟进）。-serial 排除：异常类的 serialVersionUID 告警
// 在本仓契约面全是噪音（异常不做跨进程序列化）。-parameters 等公共 flags 在根 build 注入。
tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.addAll(listOf("-Xlint:all,-serial", "-Werror"))
}

// 生产依赖刻意留空：契约面零依赖（由 ArchUnit 守，这里由"没有依赖可声明"再兜一层）。
// 测试面允许 JUnit / AssertJ —— 契约自身的纯 JDK 行为需要自测。
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
