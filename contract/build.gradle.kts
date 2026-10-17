// 契约层（方案 §5.1 的 `contracts/`）：纯 JDK 的公开 API / SPI / 不可变载荷。
//
// 命名（登记一处与方案的差异）：模块名取**单数** `contract`（＝顶层包 com.ragagent.contract），
// 不取方案字面的 `contracts`，两条理由都来自本仓既有记录：
//   1. 本仓约定「模块名 = 顶层包名」—— B117 给 :common 定名时就以此为首要理由；
//   2. `contracts` 在本仓**已被占用** —— domains/src/testFixtures/resources/contracts 有 1,426 个
//      金片契约夹具、另有 49 个 *ContractTest（B117 原话："再叫 contracts 会语义撞车"）。
// ⇒ §8.1 的**面清单不变**，只差模块目录名。
//
// 门槛（本批的目的）：把「契约面纯 JDK」从 ArchUnit R1 的**静态规则**升格为**编译期硬约束** ——
// 本模块**没有任何依赖块**，一沾框架/其他模块就编译不过，回流即时红。
// 依赖方向：domains / engine / common / boot → contract；contract → 无（纯 JDK）。
plugins {
    `java-library`
    id("com.diffplug.spotless")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

spotless {
    java {
        ratchetFrom("seed")
        targetExclude("build/**")
        // import 口径（2026-10 全仓重排同款）：\# 前缀=static 块置顶，空串=其余单块 ASCII 序
        importOrder("\\#", "")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// 编译期静态分析最小面：契约层最稳定，全量 lint + 警告即错误先行
//（其余模块按棘轮节奏跟进）。-serial 排除：异常类的 serialVersionUID 告警
// 在本仓契约面全是噪音（异常不做跨进程序列化）。-parameters 等公共 flags 在根 build 注入。
tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.addAll(listOf("-Xlint:all,-serial", "-Werror"))
}

// 刻意留空：契约面零依赖（R1 由 ArchUnit 守，这里由"没有依赖可声明"再兜一层）。
