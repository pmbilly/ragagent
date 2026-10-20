# 构建工具迁移方案（Gradle → Maven，X 系列）

> 2026-10-20 立项，分支 `v0.7.x`（自 `v0.6.x` @ `f30a6c5d0`）。
> 动因（已拍板）：**公司/团队标准是 Maven**，需要本仓构建口径与团队一致。
> 本文是执行方案，不是评估报告。执行锚点＝本文件 + `HANDOFF.md` §5 批次表 + §12 执行记录。
> 推送只在用户发话后执行。

## 1. 一页看懂

| 项 | 内容 |
|---|---|
| 做什么 | 把构建从 Gradle（10 个脚本 + 2 个约定插件 + 版本目录）迁到 Maven（1 根 pom + 10 子 pom），命令口径从 `./gradlew` 换成 `mvn` |
| 不做什么 | 不改业务代码（除 3 处构建耦合点）、不改模块划分、不改前端、不顺手升级依赖、不动 Go 仓 |
| 分几批 | X1 骨架 / X2 测试面 / X3 质量闸门 / X4 切换收尾 |
| 怎么算成功 | 同一提交下 `mvn -B verify` 与 `./gradlew build` **同时全绿**：测试条数一致、45 条守卫全绿、可执行 jar 与镜像能起服；之后才删 Gradle |
| 最大代价 | Maven 没有增量编译与构建缓存：日常「改一个类跑一次测试」会明显变慢（测试段可用 surefire 分叉缓解，编译段无法） |
| 最大风险 | `java-test-fixtures`（1504 个夹具文件、被 4 个模块交叉依赖）在 Maven 无对应机制；拆独立模块会撞 reactor 循环依赖，实际改用 test-jar（X2 已验证可行） |

## 2. 现状基线（实测，不是估计）

| 面 | 实测 |
|---|---|
| 构建定义 | 根 `build.gradle.kts`（守卫/覆盖率/Javadoc/测试并行）· `settings.gradle.kts` · `gradle.properties` · `gradle/libs.versions.toml`（29 个版本、32 个库别名）· `build-logic/`（4 件：2 个约定插件 + 其 build/settings）· 7 个模块脚本 · wrapper 4 件 |
| 代码规模 | 3008 个 Java 文件（main 2483 / test 497 / testFixtures 28） |
| 测试规模 | 约 4,900 条（最近记录 4,858），486 个测试文件用 JUnit Jupiter |
| 契约金片 | 1349 份 JSON + 5 份样本，5.9 MB，全部在 `domains/src/testFixtures/resources` |
| 一条命令的闸门 | `./gradlew build`＝编译 + 测试 + spotless + javadoc(doclint) + 45 条守卫（B194 实测 1m24s，现已更慢） |
| 构建系统耦合点 | 102 处 `gradlew`/`gradle` 字样，落在 28 个文件：脚本 15、README 5、CI 3、Dockerfile 2、IDE 配置 2、docs 约 78 |

## 3. 四个硬骨头

### 3.1 testFixtures（最大，决定 X2 成败）

Gradle 的 `java-test-fixtures` 提供三样东西：独立源集、`-test-fixtures.jar` 产物、跨模块 `testFixtures(project(...))` 依赖。Maven **没有**对应概念。

现状用量：

| 模块 | 夹具文件 | 被谁依赖 |
|---|---|---|
| `platform` | 16（EmbeddedRedis 等） | domains / model-runtime / retrieval-engine / agent-engine / boot |
| `agent-engine` | 7（录制金片加载器） | domains |
| `domains` | 1481（TestSchema + 桩服务器 + 1349 金片） | boot |

三处连带耦合：

1. `ContractPaths.repoRoot()` 靠**向上找 `settings.gradle.kts`** 定位仓库根，再按 `domains/src/testFixtures/resources/contracts` 等候选目录读金片 ⇒ 迁 Maven 后找不到根。
2. `ArchitectureRulesTest` 的导入排除规则里写着 `-test-fixtures.jar` 与 `/classes/java/testFixtures/` ⇒ 产物名变了会**整段漏排**，A1 规则会把测试基建当生产代码判红。
3. 契约测试按**文件路径**读金片（`workingDir = rootDir`），domains 与 boot 两套 `maxHeapSize`/时区/`DOCREADER_ADDR` 配置必须原样搬。

改造方式（**X2 实测修正**）：原计划的「拆 3 个独立夹具模块」**在 Maven 下不可行** —— domains 自己的测试要用自己的夹具（`TestSchema`/`GoldenContract`），
会让 reactor 出现 `domains → domains-fixtures → domains` 的循环依赖（Gradle 因 test 配置与 main 分离能容忍，Maven 直接报 cyclic reference）。

改用 Maven 的标准机制：

- 保留物理布局 `src/testFixtures/{java,resources}`（**不搬文件**，Gradle 的 `java-test-fixtures` 完全不动）；
- 三个模块用 `build-helper-maven-plugin` 把该目录挂进 **test 源集**（同模块测试直接可见，无循环）；
- 再用 `maven-jar-plugin:test-jar` 产出 `-tests.jar` 供跨模块消费（`<type>test-jar</type>`），等价于 Gradle 的 `testFixtures(project(...))`。

已知成本（实测）：test-jar 的依赖**不传递** ⇒ 消费者要显式再声明夹具用到的第三方（现在只有 `lettuce-core`，已在根 pom 的 test 面统一）；
ArchUnit 的导入排除要覆盖四种产物形态（`classes/java/test`、`target/test-classes`、`-test-fixtures.jar`、`-tests.jar`）。

### 3.2 proto / gRPC 代码生成

两处生成，机制不同：

| 模块 | proto 来源 | 生成内容 |
|---|---|---|
| `platform` | 仓库根 `otlp-proto/` | 仅 message（OTLP tracing） |
| `domains` | 仓库根 `docreader/` | message + **gRPC stub**（`protoc-gen-grpc-java`） |

Gradle 的 `com.google.protobuf` 插件换成 Maven 的 protobuf 插件（候选：`org.xolstice.maven.plugins`（事实标准、多年未更新）与 `io.github.ascopes`（活跃、支持 protoc 插件链））。X1 必须实测通过后再定版，判据：生成目录纳入编译、`protoc`/`protoc-gen-grpc-java` 二进制按平台自动解析（本机 arm64、CI 与 Docker 为 linux-x86_64）。

另有一个已知坑：domains 的「testFixtures 不生成 proto」排除逻辑是 Gradle 插件的行为，Maven 侧不存在对等物
（`protobuf-maven-plugin` 只处理 `src/main/proto`，testFixtures 挂在 test 源集也不会触发生成）⇒ Gradle 侧配置原样保留即可。

### 3.3 质量闸门七件套

当前闸门是「一条 `build` 全包」，Maven 侧要逐项找回等价物：

| 能力 | Gradle 现状 | Maven 等价物（X3 实测定稿） | 实测坑 |
|---|---|---|---|
| 格式检查 | Spotless `ratchetFrom("seed")`（只查 seed 之后触碰的文件）+ importOrder + removeUnusedImports | `spotless-maven-plugin` **3.10.4**；`ratchetFrom` 生效（假 tag 会报 "No such reference"） | `<includes>` 必须手写三条（main/test/**testFixtures**）—— Maven 默认只挂前两个；Docker 构建需 `-Dspotless.check.skip=true` |
| Javadoc 引用校验 | `-Xdoclint:reference -quiet` + `-Xmaxerrs 10000`，挂 `check` | `maven-javadoc-plugin` additionalOptions + `-Werror`，绑 `verify` | **收紧了一档**：Gradle 侧只告警不阻断，接入时全仓 0 条 reference warning ⇒ 加 `-Werror` 兑现「构建期就红」的承诺 |
| 编译参数 | 全局 `-parameters`；contract/platform 额外 `-Xlint:all,-serial -Werror` | `maven-compiler-plugin` `<parameters>true</parameters>` + `default-compile` execution 级 `compilerArgs` | plugin 级 `-Werror` 会误伤 **testCompile**（Gradle 是任务级）⇒ 必须限定 execution |
| 覆盖率 | jacoco 全模块 `-Pcoverage` 显式开关 + 聚合报告 | `jacoco-maven-plugin` 0.8.13 + profile `coverage`（prepare-agent + report） | **surefire 必须写 `@{argLine}`**（晚绑定）；`${argLine}` 在配置期就被解析，jacoco 后置设置的 `-javaagent` 挂不上（实测 exec 文件不生成）。聚合报告未做，登记待办 |
| 守卫 | 45 条挂 `check`（`rootProject.tasks.named("guards")`） | `exec-maven-plugin` 绑 **boot 的 verify** | 不能绑根 pom：Maven 按模块逐个跑完整生命周期，根的 verify 会在**最前面**执行 |
| 迁移脚本 | `syncMigrations` Copy 到 `<模块>/build/generated-migrations` | `maven-resources-plugin:copy-resources` → **仓库根 `build/generated-migrations`** | 保持原路径不动（`application.yml` 零改动），并把 `spring-boot:run` 工作目录钉在仓库根 |
| dev 起服 | `bootRun` 逐项注入根 `.env` | `mvn -pl boot -am spring-boot:run` + `scripts/dev-env.sh`（已是同口径） | Maven 下 `spring-boot:run` 用 `target/classes` ⇒ 反而消除了「jar 就地重写导致全端点 500」的老坑 |

### 3.4 守卫与工具链的构建系统耦合（必须同批改，否则静默失明）

| 文件 | 现在读的是 | 迁移后 |
|---|---|---|
| `check-version-consistency.py` | `libs.versions.toml` · wrapper 版本 · `FROM gradle:` · `*/build.gradle.kts` | 重写为读根 pom 的 `<properties>`；V5 改为「Dockerfile 的 JDK 21 ↔ pom `<release>21</release>`」 |
| `check-contract-purity.py` | `contract/build.gradle.kts` 的 `api/implementation(...)` | 改读 `contract/pom.xml` 的 dependencies（XML） |
| `check-doc-truth.py` | `settings.gradle.kts` 的 include 数（D1） | 改读根 pom `<modules>` 数 |
| `check-stray-dirs.py` | 白名单含 `build/`、`.gradle/` | 加 `target/`，保留旧项直到 X4 |
| `test-summary.py` | `{mod}/build/test-results/test/TEST-*.xml` | `{mod}/target/surefire-reports/TEST-*.xml` |
| `dev-stale-check.sh` | `*/build/libs/*.jar` 的 mtime | `target/classes` 的 mtime（bootRun 不再用 jar） |
| 约 12 处 `"/build/" not in path` | 排除构建产物 | 必须同时排 `/target/`，否则会把 `target/generated-sources` 的生成代码当源码扫 ⇒ 误报 |
| `ci-log-annotate.py` | 匹配 `What went wrong` / `BUILD FAILED` | 改匹配 `[ERROR]` / `BUILD FAILURE` |

这一组是**最容易被低估**的部分：守卫不是「脚本」，是这个仓的质量骨架，任何一条改错都会变成「全绿但失明」。

## 4. 必须接受的取舍

1. **没有增量编译与构建缓存。** Gradle 改一个类只重编受影响模块；Maven 每次编译该模块全量。测试段可用 surefire `forkCount` 并行缓解，编译段无解。对本仓「每批收尾跑一次全量闸门」的节奏影响可控，但**日常迭代会变慢**，这个体感下降要提前告知团队。
2. **没有 toolchain 自动下 JDK。** 机器必须自带 JDK 21 且 `JAVA_HOME` 有效（本机现状：brew 的 `maven 3.10.0` + `openjdk@21`，`JAVA_HOME` 仍需显式设置）。缓解：`docs/getting-started.md` 写清前置，CI/Docker 镜像固定 temurin 21；不引入 maven-toolchains（团队标准环境更易复制）。
3. **版本目录机制消失。** `libs.versions.toml` 换成根 pom `<properties>` + `<dependencyManagement>`；「单一来源」仍成立，但守卫要重写为 pom 口径。并存期内加一条「pom ↔ toml 版本互校」守卫，防止两份真相漂移。
4. **并行/分叉语义不同。** Gradle 的 `maxParallelForks`（按核数自适应）与 Maven surefire 的 `forkCount`/`reuseForks` 不是一对一，需要按实测调（domains 与 boot 原本各有一套堆与分叉参数）。
5. **并存期成本。** X1~X3 期间两套构建都在跑，CI 时长会翻倍。缓解：并存期 CI 仍以 Gradle 为闸门，Maven job 设 `continue-on-error: true`（观察态），X4 才换闸门。

## 5. 目标结构

### 5.1 Maven 模块（11 个 pom）

```
pom.xml                     根：properties（版本单一来源）+ dependencyManagement（Boot BOM）+ 插件管理
├── contract/pom.xml        纯 JDK，零生产依赖
├── platform/pom.xml        OTLP proto 生成
├── platform-fixtures/      ← 新（EmbeddedRedis）
├── model-runtime/
├── retrieval-engine/
├── agent-engine/
├── agent-engine-fixtures/  ← 新（录制金片加载器）
├── domains/                docreader proto + gRPC 生成
├── domains-fixtures/       ← 新（TestSchema + 桩服务器 + 1349 金片）
└── boot/                   spring-boot-maven-plugin repackage（唯一可执行产物）
```

`build-logic` 的两个约定插件（Java 21 / Spotless / Boot BOM / 测试基座）被根 pom 的 `<build>` 与 `<pluginManagement>` **吸收**，物理删除。

### 5.2 命令对照

| 场景 | 现在 | 迁移后 |
|---|---|---|
| 全量闸门 | `./gradlew build` | `mvn -B verify` |
| 格式检查 | `./gradlew spotlessCheck` | `mvn -B spotless:check` |
| 单模块测试 | `./gradlew :domains:test --tests "com.ragagent.x.*"` | `mvn -B -pl domains test -Dtest='com.ragagent.x.*'` |
| 集成测试 | `./gradlew :retrieval-engine:integrationTest` | `mvn -B -pl retrieval-engine -Pit verify`（failsafe） |
| 覆盖率 | `./gradlew testCodeCoverageReport -Pcoverage` | `mvn -B -Pcoverage verify` |
| 起服 | `scripts/java-server-up.sh`（内部 `bootRun`） | 同脚本，内部 `mvn -pl boot -am spring-boot:run` |
| 出镜像 | `docker build .` | 同（Dockerfile 换 maven 基础镜像与 `target/` 路径） |

## 6. 分批执行（X1~X4）

### X1 骨架与依赖（规模 L）——✅ 2026-10-21

- 建根 pom + 7 个模块 pom；`libs.versions.toml` 的版本落成根 pom `<properties>`；Boot BOM 走 `<dependencyManagement>`。
- proto/gRPC 插件接入（platform OTLP、domains docreader + grpc），选 **xolstice 0.6.1 + os-maven-plugin 1.7.1**（grpc-java 官方示例组合，JDK 21 / Maven 3.10 实测可用）。
- spring-boot-maven-plugin repackage，产出可执行 jar。
- **退出判据（全部达成）**：`mvn -B -Dmaven.test.skip=true package` 成功；`java -jar boot/target/*.jar` 真实起服（`Started … in 22.0s`、`/health` 200、认证端点 401）；fat jar 依赖清单与 Gradle **同为 189M**，差异 8 项且全部定性；`-Xlint:all,-serial -Werror` 在 contract 与 platform 均保留。
- **实测修正的 6 条认知**（后续批次的前提，勿再踩）：
  1. `libs.versions.toml` 里的版本是**声明值**，不是解析值。Boot BOM 会覆盖：protobuf-java 4.35.1、grpc 1.83.1、neo4j 6.1.0、sqlite 3.53.2.1、swagger 2.2.55（Gradle 侧实测）。Maven 侧这 5 项**必须交给 BOM**，自行钉版本会得到与生产不同的运行期。
  2. protoc 必须与 BOM 的 protobuf-java 对齐（4.35.1）：3.25 生成的 `GeneratedMessageV3` 在 4.x 运行时刷废弃警告，与 `-Werror` 相撞会让 platform 编译直接失败。
  3. Spring Boot 的 repackage **会打包 provided**（与 Gradle 的 bootJar 只打 runtimeClasspath 不同）⇒ servlet-api 必须显式 `<exclude>`。
  4. Guava 的 `-jre` / `-android` 是**版本后缀**两种变体；Maven 的「最近优先」会选 android ⇒ 必须钉 `33.6.0-jre`。
  5. `-DskipTests` 仍会编译测试；只跳执行用 `-Dmaven.test.skip=true`。
  6. 迁移脚本的 `filesystem:./build/generated-migrations` 是相对工作目录 ⇒ Maven 侧 copy 到仓库根 `build/`，并把 `spring-boot:run` 的 `workingDirectory` 钉在仓库根（与 `java -jar` 同口径，并存期不影响 Gradle）。
- 剩余差异（已解释，不补）：Gradle 多 6 个 dom4j 的 optional XPath 传递依赖（jaxen/xpp3/xsdlib…，主码 0 处使用，属死重）；Maven 多 `okio 1.12.0` + `kotlin-stdlib-common`（okhttp 2.7.5 链路，Maven 保留旧 okio 反而更完整）。
- 不做：不拆夹具模块（X2）、不接闸门、不动 CI。

### X2 测试面（规模 L，风险最高）——✅ 2026-10-21

- 夹具改用 **test-jar**（见 §3.1 的实测修正）：`build-helper` 挂 test 源集 + `maven-jar-plugin:test-jar` 产出 `-tests.jar`；物理布局不搬。
- `ContractPaths` 仓库根探测改**双候选**（`settings.gradle.kts` | 带 `<modules>` 的父 `pom.xml`）。
  坑：不能只判 `pom.xml` 存在 —— 每个子模块都有 pom，从 `domains/` 起就会误判成根（实测金片路径变成 `domains/domains/...`）。
- `ArchitectureRulesTest` 排除规则覆盖四种形态（`classes/java/test`、`target/test-classes`、`-test-fixtures.jar`、`-tests.jar`）。
- surefire：`forkCount=2`、`DOCREADER_ADDR=""`、`contract.refresh` 透传、domains 的 `user.timezone=Asia/Shanghai` + 2g、boot 的工作目录钉仓库根 + 2g。
- `-Werror` 收窄到 `default-compile` 执行（Gradle 里是任务级参数，Maven 的 plugin-level configuration 会误伤测试编译）。
- **退出判据（全部达成）**：`mvn -B test` 两遍全绿；**有效用例 5042 = Gradle 5042**（Gradle 5046−4 跳过，Maven 5044−2 跳过）；45 守卫绿；`./gradlew build` 仍绿。
- **等价性口径**：Maven 与 Gradle 对 `@BeforeAll + assumption` 的跳过报告不同（Gradle 计 2 个 skipped，surefire 计 0 个用例），因此对齐判据是「有效执行用例数」而非「XML tests 原始和」。
- **实测暴露的既有债（不夹在迁移里修）**：boot 的 `FaqContractTest` 与某个先跑测试共享 Spring 上下文，单 JVM 串行时会走「云存储」分支而失败；Gradle 的 4 分叉掩盖了它。Maven 侧按 Gradle 同口径取 `forkCount=2` 后一致全绿，该隔离债登记待办。

### X3 质量闸门（规模 M）——✅ 2026-10-21

- Spotless 3.10.4（ratchetFrom seed）· Javadoc doclint + `-Werror` 挂 verify · `-parameters` · contract/platform 的 `-Werror`（execution 级）· JaCoCo profile · `exec` 跑 45 条守卫（绑 **boot** 的 verify）· 迁移脚本同步（X1 已做）。
- **退出判据（全部达成）**：`mvn -B verify` 连跑两遍全绿（有效用例 5042、45 守卫、spotless、javadoc）；三类**红态探针**各红一次 —— ① 乱序 import（spotless 报格式违例）② `{@link NoSuchTypeXYZ}`（javadoc `-Werror` 报 reference not found）③ 游离子目录（守卫 S1 报游离目录）。
- **X3 实测发现（都已修）**：
  1. surefire 的 `argLine` 必须用 `@{argLine}`（晚绑定）。`${argLine}` 在**配置期**就解析成基值，jacoco 后置设置的 `-javaagent` 永远挂不上（实测 exec 文件不生成）。
  2. 守卫不能绑根 pom：Maven 按模块逐个跑完生命周期，根的 `verify` 会在**最前面**执行 ⇒ 绑到 reactor 最后一个模块（boot）。
  3. **X2 的 test-jar 副作用**：`ContractBoundaryRulesTest` 只用 `DO_NOT_INCLUDE_TESTS`，对 `-tests.jar` 里的测试类不生效（`mvn verify` 走 jar 形态才暴露，`mvn test` 走 target/test-classes 时不暴露）⇒ 补显式排除。
  4. **静态快照污染（既有债，本轮顺手修了一个源头）**：`StorageRuntimeEnv` 是进程级静态，`DefaultStorageBackendProvisionerTest` 把 `storage.type` 配成 s3 后不恢复，上下文复用的后续测试会读到 s3（实测 `FaqContractTest` 的 CSV 导出走云分支、返回 null）。已在 `@AfterAll` 恢复。这类"静态 + 上下文缓存"的债尚未全清，X4 登记为待办。

### X4 切换收尾（规模 M）

- 重写 6 个守卫 + 12 处路径过滤 + `ci-log-annotate`；`_source_roots.MODULE_DIRS` 登记 3 个夹具模块。
- 脚本（`java-server-up/down`、`ci-smoke`、`acceptance`、`refactor-harness`、`migrate-domain`、`audit-contract-fixtures`）改命令；`audit-contract-fixtures.sh` 里已过期的 `:server:test` 顺手修正为当前模块。
- CI：`mvn -B verify` 接为唯一闸门；Dockerfile 换 `maven:3.9-eclipse-temurin-21` + `/root/.m2` 缓存；`.gitignore` 加 `target/`；`.vscode` 去掉 `java.import.gradle.*`。
- 文档：README/getting-started/HANDOFF 的**操作口径**全改；**历史执行记录（§12 等）保留原文并加一行时间注记**（改写历史＝造假）。
- 删 Gradle：`build.gradle.kts` ×8、`settings.gradle.kts` ×2、`gradle/`、`gradlew*`、`gradle.properties`、`build-logic/`、`libs.versions.toml`。
- **退出判据**：删 Gradle 前后各跑一次 `mvn -B verify` 全绿；`scripts/java-server-up.sh` 起服 + `/health` 200；`docker build` 出镜像并起服；耗时登记（冷/热构建、全量测试）写入 §12。

## 7. 总验收判据

一条不能省的清单，任何一条不满足就不删 Gradle：

1. `mvn -B verify` 绿：编译 + 全部测试 + spotless + javadoc + 45 条守卫。
2. 测试条数逐模块与 Gradle 汇总一致（不允许「迁移时丢了测试」）。
3. 两遍真全量通过（契约测试连真 Postgres，防 flaky）。
4. 可执行 jar 起服正常：`/health` 200、任一业务端点可用。
5. `docker build` 出镜像、镜像起服正常。
6. 三类失败探针各红一次（格式/ javadoc 引用/ 守卫），证明闸门未空转。
7. 冷/热构建与全量测试耗时登记，与 Gradle 基线对照（不设硬门，但必须留痕）。

## 8. 风险与回滚

| 风险 | 影响 | 缓解 |
|---|---|---|
| 夹具拆模块后路径/包名漂移 | X2 大批测试红 | 只换物理位置不改包名；`_source_roots` 同步登记；先搬 platform（16 件）验证流程，再搬 domains（1481 件） |
| protobuf 插件选型不对 | 生成代码签名/包名与 Gradle 不同 ⇒ 编译错 | X1 用「生成文件清单 + 关键类 javap 签名」与 Gradle 产物比对，不接受「看起来一样」 |
| 守卫改写后失明 | 全绿但漏检（本仓最痛的历史） | 每条改写的守卫配红态探针；X4 逐条「造违例→必须红→恢复」 |
| Maven 无增量编译 | 团队日常变慢，可能引发抵触 | X1 起登记真实耗时；测试段靠分叉并行；必要时用 `-o`（离线）+ 只构建单模块 |
| 并存期两份依赖清单漂移 | 版本不一致，线上/CI 行为分叉 | 加「pom ↔ toml 互校」守卫，漂移即红 |
| 双构建期 CI 变慢 | 主干反馈变慢 | Maven job 观察态 `continue-on-error`，X4 一次性换闸门 |

**回滚**：X1~X3 全程不删 Gradle，任意时刻 `git checkout` 回到 `f30a6c5d0` 即完全恢复；X4 的删除单独成一个提交，回滚只需 revert 该提交。

## 9. 边界（刻意不做）

- **不改业务代码**。允许的例外只有 3 处构建耦合点：`ContractPaths`（仓库根探测）、`ArchitectureRulesTest`（排除规则）、`application.yml` 里的迁移脚本路径。
- **不改模块划分**。3 个夹具模块是构建产物，不是业务重构，不进 `check-module-graph` 的「主码矩阵」。
- **不升依赖版本**。版本冻结在当前 `libs.versions.toml` 的值，迁移与升级不是一件事。
- **不动前端**（`frontend/` 与 vite 无关）。
- **不引入 Maven 4**。本机 brew 装到 3.10.0（实测可用），Maven 4 等团队基线升级时再议。
- **镜像配置不入 pom**。阿里云镜像走 `~/.m2/settings.xml`（文档给片段），避免仓库自带「我的网络环境」假设。

## 10. 待拍板

| # | 问题 | 建议 |
|---|---|---|
| 1 | 并存期是否接受 CI 双跑（Maven 观察态） | 接受。否则 X1 一上来就换闸门，风险不可控 |
| 2 | 历史文档里的旧命令是否改写 | 不改写，只加时间注记。历史记录是事实，改了就不可复现 |
| 3 | 是否接受「日常构建变慢」 | 需要 X1 实测数字后再确认；若不可接受，只剩「加镜像不迁移」一条退路 |
| 4 | 本机/团队环境是否统一装 JDK 21 + Maven 3.10 | 必须。建议写进 `getting-started`，并在 CI/Docker 固定版本 |
