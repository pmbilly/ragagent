# ragagent 交接文档（新仓起步）

> 本文档写给在 `~/ragagent` 打开的新会话/新成员。**一切背景以本文为准**；最近的执行细节在 `git log`。
> 种子：自 `~/ragagent-java` @ `646aba7`（2026-09-28）分叉，git 历史完整保留。
> **最近更新 2026-10-10（B204 文档瘦身：§15.1 批次表压成一行一批，432.9→171.9 KB）**；上一次 2026-10-02（B32 文档重组）：§14 域级作战/执行记录（2269 行）与 §15.1.1 逐批记录（442 行）
> **已移出本文件**——`docs/handoff/plans/`（索引见 §14.7+）与 `docs/handoff/records/batch-records.md`（索引见 §15.1.1）。
> 本文件＝**常读面**：接手须知 / §2 决策 / §13 方法论 / §14.1~14.6 判据与坑 / §14.9 换锚总纲 / §15 批次总表与纪律。
> **当前状态总账**见 §15 末（结构各轴完成 + 5 项已知边界）。


## ⭐ 接手须知（5 分钟版）

0. **文档布局（2026-10-02 重组；2026-10-10 复理）**：本文件＝**常读面**（接手须知 / §2 决策 / §13 方法论 / §14.1~14.6 判据与坑 / §14.9 换锚总纲 / §15 批次表与纪律）；**查证面已移出**——域级作战与执行记录见 `docs/handoff/plans/`（索引见 §14.7+）、逐批执行记录见 `docs/handoff/records/batch-records.md`（索引见 §15.1.1）。
1. **先验证基线全绿**（三条命令见 §9；session 域单域验证：`./gradlew :domains:test --tests "com.ragagent.session.*"`，当前 **4858 条 / 失败 0**（B202 实测 ✓））。
2. **总目标**＝按 Java 标准提升可读性（§0）；**行为不变**是底线（测试是安全网）；已定决策见 §2（勿再讨论）。
3. **项目主线的历史收束（一句话版）** ✓
   - 包结构治理 P0~P3 ✓、阶段 2 神类切片（session/wiki/im/retrieval/auth/llm/mcp/embed/chatpipeline/datasource/memory，含 m5）✓、
     阶段 3 契约换锚（evaluation 打样 → model/system/auth/memory/session/embed/mcp/datasource → wiki ingest ✓）——**均已收官** ✓；
   - **P2「统一响应外壳」线（B183–B203）已实质完成** ✓：**50/52 控制器**（pending 2 = `ImCallbackController` 外部平台协议 / `HealthController` 探针，均为**登记例外** ✓），
     约定见 `docs/api-response-convention.md` ✓、批次明细见 §15.1 索引 + `git log --grep="(B19"`~`"(B20"` ✓；
   - 端口/起服：**后端 :8083**（`bash scripts/java-server-up.sh` ✓ 读 `.env` 的 `SERVER_PORT`）+ **前端 :5173** ✓；⚠️ 跑完 `gradle build` 记得**重启后端**（构建就地重写 jar ⇒ 在线服务全站 500 ✓ README 有记 ✓）。
4. **下一步（2026-10-02 更新）**：§15 计划已完成 **B0 端到端真实走查（P0，2026-10-02 收官，修 15 处断点）/
   B1 契约文档 v1.1 / B2 金片对比器统一（GoldenContract 上线，字节级对比清零）/ B3 `@JsonInclude` 恒输出化
   （真面 68 处；yunzhijia 第三方回退）/ **B3b KB 配置 jsonb 键名统一（camelCase + V2 存量迁移，2026-10-02 收官）** /
   B4 零值哨兵（结论：不改，已知例外）/ B7 死成员 19 处 / B8 FQ 注解 177→0**（✅ 记录见 §15.1 各行与 §15.1.1）。
   **剩余待做**：~~B5 载具嵌套化~~（✅）/ ~~B9 Go 锚点~~（✅ 机制落地，清扫随触碰）/ **~~B6 getenv 收敛~~（✅ 已结项：十批清完，代码内裸 getenv 149→0；四种落点形态与踩坑见 §15.1.1）** /
   ~~B10 ArchUnit 进 CI~~（✅ 2026-10-02）/ ~~B11 多模块~~（✅ 判定：不做，理由与重访条件见 15.1.1）。**B0 登记残留见 §15.1.1**（B3b′ 已修；wiki 死信槽位（②）与孤儿 op 重放（③）已由 **B12** 修；其余两项已由 **B13** 处理：④ 判定为 Go 期 KV 页、已被后端行取代 → 删孤儿；⑤ 加 `edition` 信号前置跳过。**B0 残留全部清完**；**`process_overrides` 移植缺口**：用户 2026-10-02 定调——**保留现状（不删、界面控件不动），未来补后端**（见 15.1.1；「逐文档参数不生效且无提示」是已知并接受的现状）。
   **新会话接手**：直接读 §15.1 批次表（状态列）+ §15.1.1 执行记录 + §15.2 纪律五条（开工前必读）+
   §15.3 非目标冻结清单；做完一批把 ✅ 与记录写回 §15.1。
5. **落刀方法论**：§13 是**必读**（判据 + harness 流水线 + 守卫口径 + 忠实性核验手法），
   harness 模板已入库：`scripts/refactor-harness.sh`；切片产物排版闸门：`scripts/normalize-blank-lines.py`（§13.8）。
6. **全仓存量**：≥800 行的类只剩 **4 个登记例外**（§14.3，真单类已清零）；**检索 / auth controller / wiki / im / mcp / embed / chatpipeline / datasource / evaluation 域 ≥800 全为零**，memory 域已出榜 3/3（2026-10-01 m5 后仅剩 `MemoryIndexStore` 929，登记例外）。

## 0. 总目标（2026-09-29 用户定稿）

**根据 Java 的标准和思想，全面提升代码的可读性。**

- 这是从翻译期（"忠实复刻 Go"）到 Java 本位期的目标切换：以 Java 生态的主流标准与惯用法为尺度——标准 Jackson 序列化、DTO + `@Valid` 请求绑定、`@ConfigurationProperties` 配置、Spring 装配惯例、常规类规模与命名——让代码读起来像一个原生 Java 项目，而不是 Go 的 Java 转写。
- 可读性是唯一主线：§5 路线图的各阶段（神类拆分、序列化换锚、DTO 化、注释清洗等）都是达成它的手段，规划优先级以"对可读性的收益"衡量。
- 行为不变仍是底线（4,600+ 测试是安全网）；可读性改造不得改变对外契约与业务语义，契约形态的显式变更走 §2 第 4 条。
- **下一步规划由用户在另一个新会话进行**——那个会话应以本文档为唯一背景，围绕本目标展开（候选工作面见 §5 阶段 2/3/4 与 §4 的存量数据）。

## 1. 仓库身份与分界

- 本仓 = 原 WeKnora Go 后端的 Java 翻译版（ragagent-java）的**后续演进线**。
- 自 seed 起：**不再承担与 Go 仓的任何契约对齐义务**——字节级一致、双端 A/B 对拍、Go 错误文案复刻、GORM 行为复刻注释等全部退役。
- 旧仓 `~/ragagent-java` 已冻结零修改，仅作考古参照（翻译方法论、坑史在那边，见 §10）。不要往旧仓推任何代码。
- 本仓 git origin 未设置（本地 clone 后已摘除，避免误推旧仓）；远端建好后：`git remote add origin <url> && git push -u origin main --tags`。

## 2. 已定决策（勿再重新讨论）

0. **总目标 = 按 Java 标准全面提升可读性**（见 §0）：后续所有重构规划的出发点与优先级尺度。
1. **旧仓零修改**：没有冻结过渡期、没有 fix 回流，本仓即唯一工作仓。
2. **产品未上线，无数据连续性负担**：schema 可直接做基线合并（见 §5 阶段 1）。
3. **前端随后端逐步调整**：改契约的后端 PR 同 PR 带前端修改，不设集中适配期。
4. **契约标准（2026-09-29 用户改定，取代本文档早前"保留 snake_case + RFC 7807"版本；细则见 `docs/knowledge-api-contract-v1.md` v1.0）**：
   - **字段命名 = camelCase，且 JSON 字段名 = Java 字段名**（禁止逐字段 `@JsonProperty`、禁止 `@JsonNaming` 下划线转换）；布尔字段不带 `is` 前缀（`pinned`/`enabled`）。
   - **成功响应不再包 `{data, success}` 信封**：单资源直接返回对象、列表直接返回数组；分页统一 `{"items","page","pageSize","total"}`；删除类接口返回 **HTTP 204**。
   - **错误响应统一** `{"error":{"code","message","details"}}`（保留数值 code，前端分支不变）。
   - 时间 ISO-8601 带时区；**可空字段显式输出 `null`**（不用 NON_NULL 省略、不用空串/0 代替）；不使用 `Problem Details`。
   - 落地范围（**2026-09-30 更新**）：知识库域**已完成**；并已按同标准扩展到 **检索域**（`SearchResult` + `hybrid-search`）、**会话/消息/附件/建议/steer/knowledge-search**、**chunker/preview**（均同批带前端）。其余域（wiki/agent/auth/memory/mcp 等）尚未跟进，按"域接域、同 PR 带前端"继续。
5. **功能裁剪（2026-09-28 用户定稿，第一批）**：移除「**浏览器连接、沙箱、CLI、Chrome 插件、Claw Skill**」五项——对应后端 `browserskill` + `sandbox` 两包（沙箱执行面：installer agent、镜像快照、shell_exec、沙箱文件四件套、PTY 终端；**技能体系保留但降级为指令型**，见第 9 条）与前端 integrations 设置的 `cli`/`chrome`/`claw` 三个纯展示 tab（精确清单见 §6.1）。裁完后**聚焦知识库与 Agent 两个域的重构**（§5 阶段 2）。以下为**待排期可选项**（不在第一批，勿主动动手）：`org`（共享空间/跨租户授予）、`im`（九渠道）、`datasource`（连接器，28.3k 行零耦合）、`evaluation`、`favorite`；多引擎检索是否裁到 postgres 单引擎待议。保留：mcp、memory、embed、wiki、知识库/检索/会话主链路。
6. **自研基础设施保留**：EventBus、StreamManager（Redis Stream）、chatpipeline 插件管线、ToolRegistry、各 Bridge——是架构不是技术债；阶段 4 只做多模块边界固化，不替换。
7. **Go 兼容序列化层退役（可读性主线的一环）——线上对齐面已于 2026-09-30（`0ac456e`）执行完毕**：摘除逐字段 Go 注解 **156 处 + 全限定写法 5 处**（42 文件），删除 `JacksonConfig`（4 个 bean：全局时区归一 / HTML 转义 / writer 工厂 / `float[]`）与 `GoTimeDeserializer`、`GoNaiveTimeSerializer`、`GoWriterJsonFactory`、`GoFloatArraySerializer`；**保留工具面**（仍是字节契约的 §11 边界路径）：`GoDoubleSerializer`/`GoTimeSerializer`/`GoMapSerializer`/`GoJsonEscapes`/`GoJson`——chatpipeline 手搓载荷、agent 工具输出、事件总线、Redis 流事件、provider 客户端仍依赖其 Go 字节形态，**别再当遗产删**。原描述：Controller 里大量手搓 `ObjectNode` 一并收敛为 DTO 序列化——DTO 化是 Java 本位可读性的核心工作面。**注意**：这是唯一被红线要求"一次性全仓完成"的动作（半删状态最危险），见 §3 与 §5 阶段 3。
8. **神类拆分有现成地图**：41 个千行大类的分段注释就是原 Go 文件边界，沿注释拆即可，不需要重新设计边界。
9. **Agent 能力取舍已接受**：裁沙箱与浏览器连接后，Agent 剩余工具面 = 知识检索族 + wiki 十件 + web 两件 + MCP + DuckDB 数据分析（DataAnalysisTool 走独立 DuckDB 会话，初步判断不依赖沙箱，动手时验证）；browser skill 工具随 `browserskill` 一并消失。
10. **技能降级为指令型（2026-09-28 定稿，选项 B）**：技能 = playbook——保留 SKILL.md 提示词注入路径（`agent/skills` 的 Skill/Loader/Manager + `AgentEngine.setSkillsManager` + agent config 的 `skills_selection_mode/selected_skills` 字段 + 前端技能选择器），模型凭指令执行；**删除**镜像源（TenantSkillSource）、安装管线、shell/文件注入（范围见 §6.1③）；执行型扩展需求引导走 MCP。
11. **落库格式（jsonb / 会话引用的存储）同样走 Java 字段名**（2026-09-29 用户改定）：产品未上线、**无历史包袱**，所以落库 JSON 不再保留 snake——直接去 `@JsonProperty`，**不加兼容别名**（曾加过的 `@JsonAlias` 已删）。范围：knowledge 落库类型（`Knowledge`/`KnowledgeBase`/`Chunk`/`ChunkRevision`/`GeneratedQuestion`/7 个 `KnowledgeBase*Config`/`*Metadata`/`Payload`/`FaqImportResult`）+ `retrieval.domain.SearchResult`。
12. **宽松读统一策略**（不要再逐类挂注解）：读落库/外部 JSON 一律忽略未知属性——`common/web/JsonMappers.lenient()` 是唯一工厂（jsonb 读写 `PgJsonTypeHandler`、chatpipeline 的 mapper、配置类型工厂都接它），Spring MVC 绑定侧由 `application.yml` 的 `spring.jackson.deserialization.fail-on-unknown-properties: false` 承担。**新增 mapper 必须接工厂**；旧域尚存的 44 个逐类 `@JsonIgnoreProperties` 属未接工厂的域，勿直接批量删。
13. **包结构约定（knowledge 包已示范，其余域照此靠拢）**：`mapper/` 只放 MyBatis-Plus 接口；`repository/` 放仓储门面（软删/乐观锁/方言分支）；`service/` 只放 Spring 服务（用例）与 `@Component`；`support/` 放无状态零依赖的算法与规则；`task/`、`client/`、`storage/`、`security/` 按角色。**一类型一文件**——禁 `*Dtos`/`*Enums`/`*Jsons`/`*Util` 这类复数容器与收集器类（已清理，别再造）。
14. **命名政策**：类型名**全词**（禁 `Kb`/`Ops` 等缩写；例外：实体类名可跟随表名，如 `UserKbPin` ↔ `user_kb_pins`）；请求侧布尔字段不带 `is` 前缀；Go 术语清零（`Runes`→`CodePoints`、`runeSlicesEqual`→`codePointSlicesEqual`）；抽象接口后缀用 `Gateway`（非旧 `Bridge`）。

## 3. 两条红线

1. **一次只动一个轴**：裁剪期不改结构、换锚期不拆类、拆类期不动架构；每阶段结束必须全绿可运行。一旦开始"顺手把 X 也重构了"，就退化成大爆炸重写。
2. **裁剪手术期单工作流**：缝合点文件高度重叠，PR 串行合入；阶段 3 起可按包分线并行。

## 4. 关键测量数据（**2026-09-30 复核**）

- main：**1,535 文件 / 27.9 万行**；test：**401 文件 / 12.6 万行 / 1,366 契约 fixture**；frontend：**465 文件 / 20.0 万行**。
- 后端测试：**4,681 用例全绿**（含 6 个 skip，2026-09-30 复核；2026-10-01 model 域收官批实测 **4,670 / 失败 0 / 跳过 4**，434 测试类）；前端 `vue-tsc` 0 错误 + **738 用例全绿**（2026-10-10 B202 实测 ✓；早前 690 为 2026-10-01）。
- **≥800 行的类（main，全仓）**：AgentEngine 3,235、WikiIngestBatchHandler 2,268、WikiIngestService 2,182、InitializationController 1,981、DataSourceService 1,827、SessionKnowledgeQaService 1,764、MemoryService 1,660、OpenSearchRetrieveRepository 1,652、WikiPageServiceImpl 1,642、KnowledgeQaController 1,616……
  （**2026-09-30 历史快照，多数已过时**——活榜单以 §14.3 为准：2026-10-01 实测 36 个）
- **knowledge 包（已整治，可作样板）**：**197 文件 / 25,264 行**；最大三个 = `FaqImportService` 1,234、`KnowledgeService` 850、`KnowledgeProcessWorker` 814；13 个子包见 §12；容器类/`*Util` 反模式命名已清零。
- **agent 域（2026-09-30 A/B/E 波后）**：agent 145 文件 / 24,438 行 + agentm 30 文件 / 5,614 行；**≥800 行类 0 个**（A 波前 8 个）；**Go 锚点 0**（479 处/186 文件已清扫,§13.11/13.13 判据,真实不变量改中性陈述保留）；12 个子包全有 package-info；`@JsonProperty` 余 30 处已随落库换锚清零（§11.1）。
- **Go 遗留面（阶段 3 的存量，均为本仓 grep 口径）**：Go 兼容序列化器**线上引用 0 处**（2026-09-30 退役完成，`0ac456e`）；**工具面保留 5 个类**（`GoDoubleSerializer`/`GoTimeSerializer`/`GoMapSerializer`/`GoJsonEscapes`/`GoJson`，服务于 §11 边界内仍按 Go 字节的手搓载荷与 provider 请求体）；"对照 Go / GORM"类注释锚点 **6,157 处**（阶段 3 随触碰清洗，先摘不变量信息再删锚点，不搞专项大扫除）；裸 `System.getenv()` **151 处**（收敛进 `@ConfigurationProperties`）。
- **注释卫生（knowledge 包实测，2026-09-30，可作其余域标准）**：Go 锚点注释 **0 处**、注释掉的代码 **0 处**、TODO **1 处**、注释占比 12.1%、13 个包全有 `package-info`；坏 `{@link}` 0 处。Javadoc 覆盖：**public 类型 91%**（201/221，未写的 20 处是纯 CRUD 请求体——有意留白，名字即语义）、public 方法 33%（**分布是对的**：逻辑密集类 90%+，POJO 访问器 7%）。
- **import 卫生（实测 2026-09-30）**：主干 11,557 条 import，Spotless 闸门清掉 **295 处未使用**（其中 276 处在 `knowledge/dto`——**抽类时继承原文件 import 列表**留下的）+ **13 处重复**；剩 64 处未使用在 `seed` 后未触碰过的文件里，改到即被闸门清掉（这是 ratchet 的设计，不是遗漏）。**死 logger（声明却未使用）**：主干 15 处 / 测试 0 处；knowledge 已清零（`88c8054`，-24 行），**agent/agentm 已清零**（2026-09-30，含死 `ObjectMapper` 3 处），余 **5 处**散在 `model/controller`（3）、`auth/service`（1）、`wiki/service`（1），随各自批次清。**死成员**：knowledge 已清零（logger 8 + MAPPER 5 + 死局部变量 4 + 死方法 1 + 死依赖 16 + 遮蔽 import 2）；全仓候选 **66 处**（字段 51 / 私有方法 14 / 遮蔽 import 1，`7bf963e` 口径，**含误报类**，需逐条人工确认；口径**不含未使用局部变量**——那类目前只有 IDE 能发现）；`agentm/ModelConnectivityTestService` 3 处死依赖**已清**（2026-09-30）；**agent/agentm 死成员亦清零**（死成员 7 = 死 `ObjectMapper` 3 / 死 logger 2 / 死私有方法 2，另重复 import 6 条——Spotless 不去重）。**注意一个已知口径缺口**：字段级扫描"构造函数里赋值算引用"，所以**只注入不读取的依赖**要用依赖级口径单独扫（`agentm` 那 3 处就是这么漏到后来的）。
- **`@JsonInclude` 处置完毕（2026-09-30，批次 A/B = `dc62ef7` + `7f1b2a7`）**：knowledge 原 38 处（Go `omitempty` 直译）→ **全域清零**；响应面 3 处按「可空显式 null」改（7 个 cprev fixture 同步），落库/LLM 载荷 35 处删注解（键恒输出；`path(x).asDefault()` 容错，全仓无「依赖键缺席」判断）。**当时暴露的缺口已补**：KB 配置 jsonb（`config` 列）形状原本无任何契约测试（改了 4 个 config 类型的输出却零 fixture 变化）→ 2026-09-30 新增 `knowledge/domain/KnowledgeBaseConfigJsonContractTest`（5 用例：键集合钉死、空值/假值必须显式输出、7 个配置类型 round-trip、读取容错与「旧 snake 键不再映射」防回流，`cd153c3`）。
- **全限定名注解**：knowledge 已清零（22 处 → import + 短名，`4735348`）；全仓余 **177 处**（jackson annotation 138 / databind 15 / spring 9+4+2+1 / mybatis-plus 3 …），随各域批次清理。
- 历史对照（2026-09-28 裁剪前）：main 1,608 文件 / 32.4 万行、test 448 / 14 万 / 1,783 fixture、frontend 533 / 23.6 万；千行大类 41 个（含 KnowledgeService 3,392 行 / 153 方法、FaqService 3,089、KnowledgeController 1,312——**这些数字均已过时**，knowledge 域已完成拆分）。

## 5. 转型路线图

> 各阶段均为 §0 总目标的手段；具体下一刀的取舍与排序由用户在新会话规划——下表是存量工作面的盘点，不是既定排期。

| 阶段 | 内容 | 量级 |
|---|---|---|
| 0 起步 | 建仓/环境隔离/CI 骨架/裁剪清单签字（本文档即阶段 0 产物） | 已完成 |
| 1 五功能移除 | 按 §6.1 清单逐 PR 拆除浏览器连接/沙箱(含技能体系)/CLI/Chrome插件/Claw Skill；schema dump → `V1__baseline.sql`（减裁剪表，196 个增量迁移退役）；测试对比器从字节对比改 **JSON 语义对比**（键序/转义归一化后再比）+ fixture 重录 | **已完成（2026-09-29）**：ba04157（CI+环境）→ f073c88（PR1 三 tab）→ 0f72b0f（PR2 浏览器连接）→ caef9d5（PR3 沙箱+技能降级）→ fba0e7a（PR4 基线+语义比较器）；累计净删 ~8.3 万行，4,685 后端测试全绿 |
| 2 **知识库 + Agent 聚焦重构** | knowledge 四神类 + agent 五神类拆分（沿注释边界）；Controller rawBody → DTO + `@Valid`；GORM 复刻层改写为自有数据访问契约 | ✅ **knowledge 与 agent 域均已完成**（2026-09-29/30，见 §11）：knowledge 神类全拆（最大 1,234 行）、34 个 rawBody 端点 DTO 化、目录整治 13 子包；**agent 域 A/B 波**（AgentEngine 3,235→712、SqlGuard 1,590→442、KnowledgeSearchTool 1,178→585、WikiSupport 1,148 行容器→19 类型等八神类）——≥800 行清零（§14.5）、Go 锚点 479→0、12 子包 package-info 全覆盖。GORM 复刻层随阶段 3 换锚全仓收口 |
| 3 契约换锚（全仓一次性） | 删 Go 序列化层（408 处引用 / 94 文件）、Problem Details、jsr310、NON_NULL（§2 第 4 条）；每个端点改完同 PR 带前端 | **部分已执行**：knowledge / retrieval / 会话-消息-附件-建议 / chunker-preview 的前端可见契约已换锚（§11），**落库格式也已去 snake（§2 第 11 条）**；**Go 序列化器本体删除已执行（2026-09-30，`0ac456e`）**——按红线一次性全仓完成（线上对齐面清零，工具面按 §11 边界保留） |
| 4 其余域标准化 + 架构调整 | **按 §14 逐包重构范式推进**（knowledge 为范本）：session/wiki/retrieval/memory/llm 等其余神类；getenv 收敛（B6 ✅）；注释清洗（B9 机制 ✅）；可选裁剪（im/datasource，见 §6.2——org 已清账）；**边界固化 = ArchUnit 规则（B10 ✅）+ 包级棘轮脚本；Gradle 多模块经 B11 判定不做（见 15.1.1）** | ✅ **各轴已完成（2026-10-02）**：神类——全仓 ≥800 行只剩 **4 个登记例外**（§14.3）；getenv——十批清零；注释——B9 机制落地；边界——B10 + 包级棘轮。余量＝§15.1 批次表 + §14.3 例外复核（非"重构未完成"，见现状总账） |

总量约 5–8 人月；2 人并行日历约 2.5–4 个月。阶段 2/3 顺序可对调（语义对比落地后换锚对已拆分代码同样安全），但**序列化层删除必须一次性全仓完成**——半删状态（一部分端点走 Go 格式、一部分走标准 Jackson）最危险。

## 6. 缝合点（精确文件清单，2026-09-28 import grep 实测）

> **历史段（已执行完毕 ✓，2026-10-10 压缩）**：这里原列的是"五功能移除 + CLI/Chrome/Claw 陆地页 + browserskill 3.3k 行"等跨包缝合点清单 ✗。
> 该批已全部落地 ✓ ⇒ 清单**不再维护**；要复看当时的文件级脉络，用 `git log --grep="缝合" --grep="五功能"` 或在旧仓查考古（见 §10）✓。

### 6.1 第一批移除：五功能（§2 第 5 条）

> 已执行完毕 ✓（清单不再维护；复看用 `git log --grep="五功能"`）。

### 6.2 可选裁剪项状态

> 已执行完毕 ✓（原表所列可选裁剪项均已处置）。

## 7. 当前状态（初版 2026-10-01；节内含 10-08 / 10-09 复核注，以正文日期为准 ✓）

### 7.1 已完成

| 阶段 | 范围 | 结果 |
|---|---|---|
| P0 包间解环 | 全仓包间环 / 依赖 config 包 | **环 0 组**、依赖 config 仅 1 包、L2→L3 6 条（方向合法，属阶段 4）——守卫 `python3 scripts/check-package-cycles.py`；⚠️ **2026-10-08 B111 修正**：这是"只解析 import 行"的代理读数，真读数为 **R1 2 组 / R1b 1 组（8 域）/ L2→L3 2 条**——见 `docs/phase4-module-boundaries-plan.md` §3 |
| P1 扁平包 / P2 分包 / P3 归位 | chatpipeline·event·wiki/service·knowledge/dto·embedding·rerank 等 | 全部完成（§11.11~§11.14、§11.10） |
| 阶段 2 神类切片（session 域） | AgentQaService / QaController / Resolution / 其余神类 | **全部出榜**（§11 总览表；15 个同包协作者；4 条契约测试） |
| 阶段 2 神类切片（wiki 域，2026-10-01） | WikiPageController / WikiIngestService | **全部出榜**（10 刀 → 10 个包内协作者；1,311→272、1,208→509；§14.7.2） |
| 阶段 2 神类切片（im 域，2026-10-01） | ImService | **出榜**（5 刀 → 5 个包内协作者；1,445→664；§14.7.4） |
| 阶段 2 适配器批（retrieval，2026-10-01） | 9 个引擎仓 | **全部出榜**（sqlite 2 刀 + 6 仓 12 刀 + 尾仓 opensearch 3 刀/doris 3 刀，23 个协作者；≥800 引擎清零；§14.7.5） |
| 阶段 2 收官（HybridSearchService，2026-10-01） | 非引擎族检索编排 | **出榜**（3 刀 → FusionOps/ResultOps/StoreGroupOps；1,260→775；检索域清零；§14.7.6） |
| 阶段 2（AuthController，2026-10-01） | auth 域控制器 | **出榜**（3 刀 → OidcOps/SessionOps/BindingSupport；1,167→704；auth controller 清零；§14.7.7） |
| 阶段 2（llm RemoteApiChat，2026-10-01） | llm 域聊天客户端 | **出榜**（5 刀 → RequestOps/BodyCodec/HttpOps/StreamOps/ResponseOps；1,366→513；llm 清零；§14.7.8） |
| 阶段 2（TenantCatalogController，2026-10-01） | auth 域租户目录/KV 配置控制器 | **出榜**（4 刀 → BindSupport/CreateOps/CrudOps/ConfigOps；980→133；§14.7.9） |
| 阶段 2（mcp 域双类，2026-10-01） | McpServiceController + OAuthHandler | **出榜，mcp 清零**（4 刀 → UsageInstructionsOps/CrudOps/Discovery/TokenOps；937→372、825→220；§14.7.10） |
| 阶段 2（FeishuAdapter，2026-10-01） | im 域飞书适配器 | **出榜，im 清零**（4 刀 → CallbackOps/SendOps/CardStreamOps/MediaOps；926→331；§14.7.11） |
| 阶段 2（EmbedChannelController，2026-10-01） | embed 域渠道控制器 | **出榜，embed 清零**（3 刀 → MgmtOps/PublicOps/DelegateOps；925→561；§14.7.12） |
| 阶段 2（PluginSearch，2026-10-01） | chatpipeline 检索插件 | **出榜，chatpipeline 清零**（3 刀 → QueryTextOps/ExpansionOps/SearchOps；899→278；§14.7.13） |
| 阶段 2（wiki page 面，2026-10-01） | WikiPageServiceImpl / WikiPageRepository / WikiIngestDedupService / WikiPageFolderSupport | **出榜，wiki 域 ≥800 清零**（w1~w5 + 两个卫生刀 → RevisionOps/LinkOps/FolderRepository/IdentityDedup；1,008→732、858→708、851→468、822→383；§14.7.14） |
| 阶段 2（DataSourceService，2026-10-01） | datasource 域最长类 | **出榜**（d1~d3 → ResultOps/Support/ItemOps/SyncExecutor 四协作者；1,828→666，-63.6%；§14.7.15） |
| 阶段 2（datasource 连接器批，2026-10-01） | FeishuClient + NotionConnector | **双出榜，datasource 域 ≥800 清零**（f1~f3 + n1 → Transport/WikiTreeOps/DriveOps/FetchOps；1,155→709、1,093→625；§14.7.16） |
| 阶段 2（memory 域，2026-10-01 起） | MemoryExtractionService / MemoryRepository / MemoryService | **四刀落定**（m1 1,217→718 出榜；m2 1,515→1,200 出 `MemoryItemStore`；m3 1,200→**458** 出 `MemoryIndexStore`；m4 1,663→**965** 出 `MemoryCatalogOps`+`MemoryInsightOps`；⚠️ `MemoryIndexStore` 929 登记**已知例外**、`MemoryService` 965 待 m5 评估——用户 2026-10-01 定调「不硬切」，判据见 §14.7.17） |
| 阶段 3 打样（evaluation 域，2026-10-01） | 小域契约换锚打样（§14.9b） | **流水线跑通**：POST/GET 去信封 + camelCase + 标准 DTO 绑定；10 个 fixture（含新增空体用例）；前端零调用面；**真实服务冒烟 8 路通过** |
| 阶段 3（model 域，2026-10-01） | 模型域契约换锚四块（§14.9c + §14.9e） | **收官**：主资源 + debug + weknoracloud + 落库 jsonb 全部换锚；`@JsonProperty` **87→0**；**前端 15 文件同批**（首次前后端同 PR）；真实服务冒烟 11 路（§14.9c） |
| 阶段 3（system 域 S1，2026-10-01） | /system 读端与探测端 7 端点（§14.9f） | **完成**：去 `code/data/msg` 信封 + camelCase + 错误语义化（503/403/400）；`SystemDtos` 85 处清零（99→14）；前端 10 文件同批；契约 19/19 绿 |
| 阶段 3（system 域 S2/S3/S4，2026-10-01） | /system/admin 全部端点 + SystemSetting（§14.9g） | **收官**：账号面/平台密钥/设置/runtime+配额四组换锚（rawBody→DTO、PlainError→AppError、动作 204）；`@JsonProperty` 14→**0**；前端 5 文件同批；auth 域波及 fixture 4 个同批 |
| 阶段 3（auth 域 A1，2026-10-01） | 登录/会话/用户信息面 + User/Tenant/UserPreferences（§14.9h） | **完成**：成功裸 DTO / 失败 AppError / 动作 204；`@JsonProperty` 370→约 250（余 A2 租户成员邀请 + B apikey + 边界 tenantconfig 130）；前端 19 文件同批；**共享实体链条**牵动 4 测试类 ~50 fixture |
| 阶段 3（auth 域 A2，2026-10-01） | 租户/成员/邀请/配置面（§14.9i） | **完成**：成员/邀请列表去信封、租户 CRUD 裸 DTO + 删除 204、KV 配置裸对象、动作 204、三个手搓封装辅助删除；前端 20 文件同批；183 用例绿 |
| 阶段 3（auth 域 B，2026-10-01） | API 密钥面（§14.9j） | **收官**：4 文件去注解 + 四端点去信封/204 + 请求体 camelCase；波及平台密钥与 4 个外域测试；前端 5 文件同批；**auth 域 @JsonProperty 仅余边界** |
| 阶段 3（memory 域 M1，2026-10-01） | HTTP 响应面 16 路由（§14.9k） | **完成**（前三次尝试回滚后第四次成功）：6 响应实体去注解 + 去信封/camelCase + 创建 201 / 删除类 204 + 分页 `{items,page,pageSize,total}`；fixture JSON 解析改写 + Java 断言逐处手改；前端 8 文件同批（含修 A2 漏改的 KV 解包）；全域 339 用例绿 / 全量 4670 绿；**真实服务冒烟 21 路通过** |
| 阶段 3（memory 域 M2，2026-10-01） | 落库/内部 JSON 面（§14.9k） | **完成**：7 个落库实体 + `MemoryConfig` 去注解 60 处 → **memory 域 @JsonProperty 137→22**（余者 LLM 载荷，登记保留）；**真落库的两处 jsonb**（`memory_subjects.extraction_state`、`tenants.memory_config`）跑存量迁移 SQL；顺手修 `ModelService` 按旧键读 memory config 的运行时依赖 + KV 校验文案改 camelCase；前端 2 文件同批；全量 4670 绿；**真实服务冒烟 11 路通过（含迁移后存量行读回）** |
| 阶段 3（memory 域 M3，2026-10-01） | 请求侧绑定收尾（§14.9k） | **完成**：三个请求 DTO 进 `memory/dto` + `@Valid`，手写 `rawBody+parse()`/`MAPPER` 退役；错误形态统一到全局处理器（空体/null→请求体不能为空、畸形→请求体格式不正确、类型错→`<字段>: 类型不正确`、缺 enabled→`enabled: 不能为空`）；LLM 载荷 22 处**登记保留**；新增 5 条测试 + 2 个夹具、清 1 个孤儿夹具；**真实服务冒烟 12 路通过** |
| 阶段 2（memory 域 m5，2026-10-01） | 召回段外提（§14.7.17） | **完成**：`MemoryService` 965→**768**（出榜）、新协作者 `MemoryRecallOps` 252 行；忠实性逐字核验通过；memory 域 ≥800 只剩 `MemoryIndexStore` 929（登记例外）；跨域回归（memory/chatpipeline/session）+ 全量 4673 绿；**真实服务冒烟 7 路通过** |

### 7.2 当前存量（实测）

- session 域：**100 文件 / 22,734 行**（空行折叠后）；最大类 `SessionKnowledgeQaService` 1,036（例外）→ 其后 `SessionController` 791 / `AgentStreamBridge` 696 / `SessionService` 650。
- wiki 域（2026-10-01 page 面批次后）：**148 文件 / 22,964 行**；**≥800 = 0**（最大 `WikiIngestCitePipeline` 760，其后 `WikiPageServiceImpl` 732 / `WikiPageRepository` 708）。
- llm 域（2026-10-01 批次后）：chat 包 `RemoteApiChat` 家族 6 类全部 <800（最大 `RemoteApiStreamOps` 365）。
- auth 域 controller（2026-10-01 批次后）：`TenantCatalogController` 980→133，包内最大 `TenantInvitationController` 519。
- mcp 域（2026-10-01 批次后）：controller/oauth 两神类出榜，包内最大 `OAuthDiscovery` 439。
- im 域（2026-10-01 批次后）：`FeishuAdapter` 926→331，feishu 包最大协作者 `FeishuCardStreamOps` 256。
- embed 域（2026-10-01 批次后）：`EmbedChannelController` 925→561，controller 包最大协作者 `EmbedChannelDelegateOps` 279。
- chatpipeline 域（2026-10-01 批次后）：plugin 包 24 类全部 <800（最大 `PluginRerank` 686）。
- datasource 域（2026-10-01 全批后）：**115 文件 / 26,723 行**；`DataSourceService` 1,828→**666**、`FeishuClient` 1,155→**709**、`NotionConnector` 1,093→**625** —— **域内 ≥800 清零**（原三个：1,828 / 1,155 / 1,093）。
- memory 域（2026-10-01 m1~m5 + M1~M3 换锚后）：`MemoryExtractionService` 1,217→**718**（出榜）、`MemoryRepository` 1,515→**458**（出榜）、`MemoryService` 1,663→965→**768**（出榜，m5）；`MemoryItemStore` 462 / `MemoryCatalogOps` 517 / `MemoryInsightOps` 412 / `MemoryRecallOps` 252 / `MemoryIndexStore` 929；**≥800 只剩 `MemoryIndexStore` 929**（**登记例外**：同属索引侧一个关注点，§14.7.17）。
  **`@JsonProperty` 137→22**（HTTP 面 63 处 + 落库/内部面 52 处清零）：余 22 处**全是 LLM 载荷**
  （`MemoryExtractionLlm` 11 + `MemoryExtractPayload` 11，**保留**：模型输出 schema，§14.9k 三分法）；
  `common.settings.MemoryConfig` 12 处已清零（tenants.memory_config jsonb 换锚，M2）。
- evaluation 域（2026-10-01 打样后）：**`@JsonProperty` 66→0、`@JsonInclude` 12→0**；POST/GET 两端点契约已换锚（§14.9b）；`dto` 包 2 文件（`EvaluationDtos` 容器待拆分，另立批次）。
- model 域（2026-10-01 收官）：**`@JsonProperty` 87→0**、`@JsonInclude`/`Go*` 序列化引用清零；主资源（M1）+ debug（M2）+ weknoracloud（M3）+ 落库 jsonb 四块全部换锚（§14.9c/§14.9e）；前端 15 文件同批改；dev 库旧 jsonb 行已用迁移 SQL 改写。
- system 域（2026-10-01 收官）：**`@JsonProperty` 99→0**（S1 的 `SystemDtos` 85 + S3 的 `SystemSetting` 14）；`/system` 7 端点 + `/system/admin` 全部端点 + settings 实体均已换锚（§14.9f/§14.9g）；前端 16 文件同批；权威细节见 §14.9g（含"审计 details 有意保留"清单）。
- 全仓 ≥800 行的类：**7 个**（清单与分域建议见 §14.3）。
- 测试：session 域 388 条 / wiki 域 542 条，失败 0（本轮实测）；全量闸门命令见 §9。

### 7.3 未完成 / 待办

1. **阶段 2 其余域**：wiki（含 page 面）/ im / retrieval / auth controller / llm / mcp / embed / chatpipeline / knowledge(FaqImportService) **均已出榜**；
   `datasource` **全域出榜**（service §14.7.15；两个连接器 §14.7.16）——域内 ≥800 清零；
   memory 域**两条线都已走完**（切片 m1~m5 + 换锚 M1~M3）。
   **2026-10-09 复核：本节所列尾巴已全部清空**（实测行数）——`MemoryIndexStore` 929→**628**、`SourceRegistry` 878→**528**、
   `UserService` 876→**757**、`SessionKnowledgeQaService` 1,036→**753**（§14.5 例外复核见 B129）、`QaSearchTargets` 706→**190**；
   wiki 域的 Go 期措辞（原 ORM／原实现 一族）与 datasource 域 Go 锚点**均实测 0 处**
   （`scripts/check-go-anchors.py` 基线里 datasource 条目 0）⇒ **阶段 2 无在办项**。
2. **阶段 3 契约换锚**：**部分已执行** —— knowledge / retrieval / chunker-preview / evaluation / model / system /
   auth（A1+A2+B）/ **memory M1+M2+M3** / **session 全域收官（S1 会话主资源 → S2 消息面 → S3 附件·建议·steer → S4 QA 请求面 → S5 收尾，前四批前后端同批）** / **embed 域 E1（渠道管理 + 公开面，前后端同批）** / **mcp 域 M1（服务资源 + 凭据面）+ M4（工具审批 + OAuth 用户面，均前后端同批）** 已完成（同批带前端）；
   **mcp 域已收官（M1 + M4 + M5，仅剩第三方协议面 22 处永久冻结）**。
   **datasource 域已收官（D1 + D2 + D3，仅剩 connector 第三方线格式与 `lf_*` 共享载具，均冻结）**。
   **2026-10-08 复核**：域级换锚已完成——全仓剩余 `@JsonProperty` **426 处全部落在已登记冻结面**（datasource connector 126 / event 载荷 92 / auth tenantconfig 89 / llm provider 62 / SearchParams 19 / mcp oauth 18 / memory·stream·retrieval·tracing·wiki·rerank 25），无在办候选；工具面另已于 **B88（JSON 键）+ B89（XML 形态）** 全量换锚。
   **2026-10-09 订正**：上条"剩余 426 处全部冻结"**已过期**——**B151 清 235 处**（我们自己的面）+ **B152 清 43 处**（外部协议面，**面级放行表已撤**）⇒ 现余 **576 处**全部落在 A14 守卫认定的**四类必要形态**里：键≠隐式名 · 键形如 `isXxx`（getter 会丢 is）· Jackson 探测不到的成员（B151 实测：删了请求体 400）· **字段序锚**（同文件混用改名注解者，整文件跳过；删了会改线格式字段序，B151 实测）。硬门 `JsonPropertyHygieneTest`（A14）**基线 0**，新增即红 ✓。
   硬约束：**序列化层删除必须一次性全仓完成**，半删状态最危险（§5 阶段 3）；时机由用户定，可与阶段 2 对调。
   **入场前先做**：§14.9 的"端点 × 前端"清单盘点。
3. **阶段 4 架构调整（Gradle 多模块 + ArchUnit 边界固化）**：**已执行大半** ✓
   （执行记录即 `docs/phase4-module-boundaries-plan.md`，含 B110~B120 各批）。
   **2026-10-09 实测**：包图 **DAG**（环 0 组 ✓ · `L2 → L3` 0 条 ✓ · 内联全限定名 0 处 ✓）；`:common` 模块已落地
   （175 文件 / 13.7k 行，**编译期硬边界已用探针验证**：往 `:common` 注入业务域 import ⇒ `:common:compileJava` FAILED ✓）；
   守卫已多模块化（R1/R1b/R2/R3/R5/R6/R7/R8/R11/R12a/R12b + A14 + `ClasspathResourcesTest` ✓）。
   **✅ `:engine` 已落地（B161）+ 边界按分层校准（B162）**：现 = `llm` `retrieval` `embedding` `rerank` `chatpipeline`
   `modelcontext` `webfetch`（**7 域**）；`tracing` 已**按层次拆分**（B163）：core（20 文件，含 `OtlpHttpExporter`）进 `:common` ✓、
   装饰侧（`LangfuseChatClient`/`LangfuseEmbedder`/`LangfuseReranker`/`LangfusePayloads`/`LangfuseVlm`/`LangfuseWiring` = 6 文件）
   留 `:engine` 的新包 `tracing.decorators` ✓（那 11 条边全在这 6 个文件里 ✗ —— tracing **不是纯 L1** ✓）；
   `stream` 已按 L1 下沉 `:common` ✓、`model`/`vectorstore` 已回业务侧 ✓（引擎内无人依赖它们 ✓，顺带修掉 L2 里有 controller ✗）。
   **模块图（用户 2026-10-09 定案）**：`:common` / `:engine` / `:domains` / `:channels` / `:boot`（**5 模块**）——
   `datasource` 与 `:misc` 四域**并进 `:domains`**（不单列 ✓）；`:channels` = `im` + `embedchannel` + `channels.api`（API key 通道，
   **前置：解 `auth ⇄ apikey` 22+22 处互依** ✗）；`:boot` = `RagAgentApplication` + `config` + 装配/扫描 + `application.yml`
   + `spring.factories` + `syncMigrations`（Flyway 走**相对工作目录**的 `./build/generated-migrations` ✗ ⇒ Copy 目标必须在本模块）+ bootJar ✓；
   `channels.*` 包改名与模块目录改名（`domains/`→`domains/`）**合并成一次命名对齐批** ✓（`server/src` 波及 7 脚本 · 1 基线 · 30 文档 ✓）。
   **`:app` 不建议拆**（中部无缝：界上暴露 178~268 类型，文档结论"它们不是模块，是应用"）。
   **该步 5 条已知风险**（文档 §5.2，均有对治先例）：装配面绑定单根包（入口类 + R2/R3/R11 须同步）·
   13 处 classpath 资源静默 null（B118 已补存在性断言 ✓）· 路径硬编码（proto / migrations / 5 守卫 / `GoldenContract`）·
   spotless `ratchetFrom("seed")` 遇大搬家会放大 diff · 测试归属（单测随域、集成测试集中 `:boot`、共享基座走 `java-test-fixtures`）。
   **长尾（2026-10-09 实测，可选排期）**：① `controller/` 包内的**切片协作者 25 个**（auth 7 / wiki 7 / session 6 / embedchannel 3 / mcp 2；
   `*Ops`/`*Support`/`Qa*`——**是既定形态** ✓：`docs/auth-module-guide.md:405` 明文"端点方法留 controller、逻辑进协作者"；
   `pkg-audit.py` 只按文件名判、会误标"应归位" ✗，package-map §2 认的"真放错"只有 2 个）；② **>600 行文件 67 个**（B121 冻结面，含 2 个已核准例外；按"自然接缝"判据拆、不硬切）；③ **分层倒挂 `controller → domain` 57 文件**（审计抽样：多为响应装配，待拍板）；④ 超大单层包（`agent/tools` 98 · `wiki/service` 91 · `knowledge/dto` 80）；⑤ **真机联调**（IM 面 + 多实例 Redis 面，不可自动化）；⑥ 两个已知偶发测试（`WebToolsRecordingTest` 全量并发下 · `EvaluationContractTest.getTerminalRunsExecution`）；
   **2026-10-10 B178 实测判定（比上条更硬）**：这 25 个**逐个核过，全部不是缺陷** ✓ —— 21 个直接依赖 Web/MVC 类型 （`org.springframework.web`/`jakarta.servlet`/`com.ragagent.common.web`）⇒ 属表现层；余 4 个（`QaAttachmentResolver`/`QaRequestParser`/`QaTurnFinalizer`/`WikiActivityRecorder`）零 Web 依赖乍看可疑 ✗，但加做**反向依赖检查**后发现**全都引用控制器自身类型** （`KnowledgeQaController`/`QaRequestBinder`/`SessionStreamController`/`WikiRequestSupport`）⇒ 搬到 service 会造 `service → controller` **真倒挂** ✗，比原问题更糟 ✓。**已固化**：5 个 controller 包写入 `package-info`（列名协作类 + 判据 + "若将来要迁先复核" ✓）；`pkg-audit.py` ⑤ 改为**自证输出**（`协作类 25 = 依赖 Web 21 + 引用控制器 4 ⇒ 应归位 0` ✓）⇒ 此条待办**关闭** ✓。
   ⑦ **两处"包已并入"的登记与实况不符** ✗：`docs/backend-package-map.md` §3.5 表称 `webfetch`→`agent/support`、`modelcontext`→`agent/modelcontext` **均已并入** ✗，实测两者**仍是顶层包**（4 / 13 文件；`agent/support/` 只有 `package-info`）✓ ⇒ 疑似该并入计划已被 §5.1-③ 的模块方案取代（两者都归 `:engine`）⇒ **待拍板**：并入 agent 还是留给 `:engine`。
   **待拍板**：**P3**（`tracing` 11 条边专题下沉 `:common` + 拆 `:boot`/`:domains` 目录）· **P4**（`:channels` + 命名对齐批）·
   或长尾 ①②（清洁件）。
4. **编号对照（防混淆）**：§5 用**阶段 0-4**；§14.2 用**步骤 0-4**（单域 SOP）。神类切片属「阶段 2 / 步骤 2」，
   契约换锚属「阶段 3 / 步骤 4」——两套编号并存，引用时写全称。
5. ~~可选尾巴~~：`QaSearchTargets`（706→**190**）内部细分与 `SessionKnowledgeQaService`（1,036→**753**）的 §14.5 例外复核**均已完成**（2026-10-09 实测；后者见 B129）。

## 8. 环境与运行

- **后端端口改 8083**（避开旧仓 8082 本地走查环境）。
- **PG 独立库名**（建议 `ragagent`）：基线合并会改 schema，不能与旧仓共用 dev 库；docker-compose 里 ParadeDB/Redis 实例可共用，建新库即可。
- 前端开发代理：`VITE_DEV_PROXY_TARGET=http://localhost:8083`。
- `.env` 已从旧仓原样复制（未入库，gitignore 正常），**已改**（`SERVER_PORT=8083` 实测生效 ✓，B202 起后端就绪）；库名按本仓独立库 ✓，`SYSTEM_AES_KEY` 沿用 ✓。
- **`LOCAL_STORAGE_BASE_DIR` 必须放持久目录、严禁 /tmp**（旧环境实测踩坑 2026-09-28：放在 `/tmp/weknora-java-files`，macOS 定期清理 /tmp 导致已入库文档原始文件丢失——文档列表正常、检索可能正常，但 preview 全 500、重处理报 "failed to read file"，原始文件不可恢复只能重传）。建议 `~/ragagent-data/files` 之类仓库外持久路径。
- **CI（2026-10-09 现状，B157/B158）**：三 job —— **guards**（9 条 Python 守卫，`bash scripts/run-guards.sh` 为准 ✓）· **backend**（spotless（ratchet 自 `seed` tag）+ 全量测试 + **把 `V1__baseline.sql` 灌进全新 ParadeDB**；两个连真 PG 的录测试在 CI 真跑）· **frontend**（type-check + test + build）。ArchUnit 规则随测试套件跑（B10），包级规则仍归 guards；ArchUnit **边界固化**属阶段 4。
- **远程仓库（2026-09-30 起）**：`origin` = `https://github.com/pmbilly/ragagent.git`（**公开**）；首次推送只推了 `main`（`bbf7443`），**`seed` tag 已于 2026-10-09 推送**（B157——CI 的 spotless ratchet 需要它可达）；本地 wip 分支按需推；此后本地提交若要同步，记得 `git push`（并行会话在同一仓库提交、同样落在 main，也需推送）。

- **⚠️ 跑完 `./gradlew build` / `test` 必须重启后端** ✗：构建会**就地重写** bootRun 正在用的 `build/classes` ⇒ 在线服务**全站 500 + NoClassDefFoundError**（看起来像业务炸了 ✗ 其实是陈旧类 ✓）。**重启即解，别去查代码** ✓（README「坑」一节同款记录 ✓）。顺带：`scripts/*-down.sh --stop` 也会把 bootRun 一起杀掉 ⇒ 停完记得重启 ✓。

## 9. 测试与安全网

- 434 个测试类 / 1,366 契约 fixture（**4,858 用例 / 0 失败**，2026-10-10 B202 实测 ✓；早前记录 4,670 / 4 skip 为 2026-10-01 ✓）是重构回归网，**每一步（哪怕纯移动）结束都必须全绿**——近两轮的工作方式就是"改一步 → 全量验证 → 提交"。这是"种子 fork + 渐进转型"优于重写的全部意义。
- **三条验证命令（接手先跑一遍确认基线）**：
  ```bash
  # 后端全量（约 1.5 分钟，B194 提速后；期望 BUILD SUCCESSFUL，4,858 用例 0 失败）
  cd ~/ragagent && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :domains:test
  # 前端类型检查（期望 0 错误）
  cd ~/ragagent/frontend && npx vue-tsc --build --force
  # 前端单测（期望 pass 690 / fail 0）
  cd ~/ragagent/frontend && npm test
  ```
- 已知偶发：`WebToolsRecordingTest.searchWithContentFetchesLeadingPagesViaSharedFetchTool` 在全量并发下**偶发失败**（单独重跑通过，与代码改动无关）；遇到它单独重跑确认即可，别误判为回归。
- 已知偶发（第二个）：`EvaluationContractTest.getTerminalRunsExecution` 在**全量并发**下偶发失败
  （期望 `model ID cannot be empty`、实际空串；单独 `--tests "*EvaluationContractTest"` 重跑通过）。
  与代码改动无关，遇到时先单独重跑确认（2026-09-30 首次观察到）。
- 已知偶发（第三个，2026-10-09 B164 观察到）：`EmbedRateLimiterTest.redisPathSharesBudgetAcrossInstances`
  在**全量并发**下偶发失败（Redis 面跨实例共享预算，时序敏感）；单独 `--tests "*EmbedRateLimiterTest"` 重跑通过。
  与代码改动无关，遇到先单独重跑确认。
- 裁剪功能的测试/fixture 随 PR 删除；阶段 1 末对比器改 **JSON 语义对比**后，fixture 锚定的是**本仓自己的行为**，与 Go 再无关系（键序/转义差异不算失败）。
- A/B 对拍脚本与 `artifacts/` 产物未带入本仓（留在旧仓）。

## 10. 考古指引（需要时去旧仓查）

- 某行为为什么是这样：旧仓 `docs/HANDOFF.md`（翻译约定正文）、`docs/known-issues/`（坑史，尤其 04 沙箱/技能卷、05 事件契约/工具/引擎卷）。
- 某行代码来历：直接在本仓 `git blame`（seed 前历史完整保留）。
- 架构总览（裁剪前状态）：`docs/site/` 门户、`architecture.html` / `agent-workflow.html` 交互图、`api/` 452 路由清单——阶段 1 后按新形态重生成。

### 7.1 存档：知识库域 Java 本位重构完成（2026-09-29）

**一句话**：knowledge 域完成神类拆分（FaqService 3,086→七协作者）、34 个 rawBody 端点 DTO 化、Go 锚点清零、FAQ 仓储独立 ✓
**详情**：`git log --grep="(B10" --grep="knowledge"`；方案见 `docs/superpowers/plans/2026-09-29-knowledge-module-java-refactor.md` ✓

## 11. 已完成批次总览（细节见 `git log` 与历史版本；判据与手法见 §13）

### 11.1 契约换锚 / 落库 de-Go / knowledge 目录整治（2026-09-29~30）
（原 §11、§11.1、§11.5~§11.7 压缩）agent 域 A/B 波 + approval 换锚；知识库域 Java 本位重构完成（§7.1 存档）；
分支与残留清理；agent/agentm §14.5 复验与卫生清零；顶层包归并决策。

### 11.2 P0~P3 包结构治理（2026-09-30）
配置/工具类归位（批 1）、端口化（批 2）、放错包/倒挂清零（P3）、chatpipeline+event 分包（P1）、
wiki/service 分包（P2）、knowledge/dto 分包（P2）、embedding/rerank 拆 provider（P1 收尾）。
**结果**：环 0 组 / 依赖 config 1 包 / L2→L3 6 条（守卫长期绿）。

### 11.3 session 域阶段 2 神类批次（2026-09-30~10-01，本批主体）

| # | 类 | 前 → 后 | 切片（协作者） | 记录 |
|---|---|---|---|---|
| 1 | `TemporaryDocumentService` | 1,075 → **498** | prompt 切片（`TemporaryDocumentPromptResolver` 271）+ 解析/落盘管线（`TemporaryDocumentProcessor` 423） | §11.15 / §11.20 |
| 2 | `AgentStreamBridge` | 856 → **697** | 先补 9 例契约测试（`AgentStreamBridgeTest`），再抽发射器 `AgentStreamEmitter` 94 | §11.16 |
| 3 | `MessageService` | 1,028 → **510** | 聊天历史检索簇（`MessageSearch` 596） | §11.17 |
| 4 | `MessageSuggestionService` | 1,088 → **601** | 无状态管道（`MessageSuggestionPipeline` 513） | §11.18 |
| 5 | `AgentToolBackends` | 1,264 → **590** | 知识库检索簇（538）+ wiki 簇 | §11.21 / §11.22 |
| 6 | `SessionAgentQaService` | 1,430 → **364** | 历史装配（`AgentHistoryAssembler` 268）→ 配置装配（`AgentConfigAssembler` 324）→ 引擎/工具装配（`AgentEngineAssembler` 585） | §11.23~§11.25 |
| 7 | `KnowledgeQaController` | 1,614 → **328** | 静态解析助手（`QaRequestBinder` 105）→ 解析主体（`QaRequestParser` 331）→ 收尾簇（`QaTurnFinalizer` 161）→ SSE 编排（`QaSseOrchestrator` ~370）→ 附件解析（`QaAttachmentResolver` ~165）→ 执行/落库（`QaTurnExecutor` 418） | §11.26~§11.32 |
| 8 | `SessionQaResolution` | 2,906 → **706** | 模型选择（`QaModelSelection` 420）→ KB 范围（`QaKbScope` 341）→ mention/tag 收敛（`QaMentionTagScope` 422）→ 巨型方法项目三步（`QaChatManageOverrides` 540 + `QaSearchTargets` 706） | §11.33~§11.39 |

**批次口径（可复用）**：一次侦察定边界 → 按清单连续落刀，**每刀自带编译自检 + 独立提交**，共用收尾闸门；
不混改、不合并提交。会话域该批的实测结论：**"先落被依赖方"是解决受阻刀的关键**（见 §13）。

### 11.4 阶段 2 批次清单的历史版本
原 §14.9b / §14.9 / §14.9c（session 批次清单与刀序）已全部执行完毕，内容并入上表；不再保留独立章节。

## 12. knowledge 包结构地图（样板，其余域照此靠拢）
> **全后端分包地图与体检结论见 `docs/backend-package-map.md`**（2026-09-30：34 顶层包 / 1,599 文件 / 284k 行；P0 包间成环 32 组、P1 扁平包 10 个、P2 超大单层 4 个、P3 顶层 package-info 仅 5/34；复测 `python3 scripts/pkg-audit.py`）。

> **模块手册**：`docs/knowledge-module-guide.md`（架构师接手版，500 行 / 8 张 Mermaid 图：全景 · 分层 · ER · 入库时序 · 检索 · FAQ 状态机 · 任务 span · 守卫）——它讲「结构 + 接口 + 实体 + 链路 + 改哪里」，新人先读手册、再读本节地图。

```
knowledge/
  controller/ (9)   只放 @RestController：Chunk / ChunkerPreview / Faq / KnowledgeBase /
                    KnowledgeBaseFileProxy / Knowledge / KnowledgeOperations / KnowledgeTag
  service/ (29)     Spring 服务（用例）与 @Component —— 含 FaqImportService(1,234)、
                    KnowledgeService(850 门面)、SpanTracker(700，knowledge/wiki 共用)
  support/ (5)      无状态零依赖的算法与规则：GraphChunkSelector / QuestionBatchPlanner /
                    KnowledgeIndexContent / ParserEngineRules
  task/ (10)        异步任务：队列接口 + InProcess 实现 + KnowledgeProcessWorker +
                    KnowledgeTaskExecutor / KnowledgeTaskIdCodec / 进度 store
  client/ (3)       出站依赖薄封装：DocReaderClient / EmbedderClient
  storage/ (4)      LocalStorageService / TenantFileStorage / TenantStorageService
  security/ (5)     KnowledgeRouteGuards + Knowledge/Chunk AccessGuard + FaqGuard
  repository/ (6)   仓储门面（软删三面孔/乐观锁/方言分支）：ChunkRepository /
                    FaqChunkRepository / KnowledgeTagRepository / KnowledgeSpanRepository
                    + ChunkTxTemplate
  mapper/ (8)       只放 MyBatis-Plus 接口（7 个 *Mapper）
  domain/ (27)      实体（@TableName 跟随表名）+ jsonb 值类型 + WireValued/ParseStatus/
                    EnableStatus/SummaryStatus
  dto/ (75)         一类型一文件（请求/响应/view 分离命名）
  chunker/ (15)     分块子域：Chunker 接口 + Heading/Heuristic/Legacy(Tier3) Splitter +
                    CodePoints/TextNormalizer/ChunkPatterns 等
```
**注**：`datasource/mapper`、`memory/mapper` 里仍各有 `XxxTxTemplate`（历史约定），其批次跟随 `repository/` 分层；`chunker/LegacySplitter` 的 "legacy" 指 **Tier 3 算法分档**（与 `HeadingSplitter`/`HeuristicSplitter` 同族），**不是**待删的遗留代码。

## 13. 落刀方法论（新 Agent 必读；全部是踩坑换来的）

> **编号说明**：文中与提交信息里可见的 `§13.9`~`§13.28` 是 §13 改写前的历史条目编号
### 13.1 切片的先后动作（固定套路）

1. **侦察**（只读，1~2 条命令）：成员清单与行区间 → **三类依赖扫描**（字段／方法／常量；字段要含 `@Autowired`）→
   调用点（簇内 vs 簇外）→ 外部引用与测试床 → 打印出来再动刀。
2. **判定切片边界**：**按调用点定，不按名字猜**（同名/近名工具常是双用 → 留门面并放宽包内可见）。
3. **写 harness 脚本**（模板：`scripts/refactor-harness.sh`）：脚本补丁 → 落刀 → **干跑断言** → 编译 → 测试 → spotless → 忠实性 → 文档 → 提交，
   **任一环失败自动回退**。命令 >8KB 会超平台限制 → 一律写成 `/tmp/runN.sh` 再执行。
4. **落刀后核验**：忠实性（搬走的成员体**逐字**比对，允许且仅允许登记过的替换反向归一）。
5. **回填文档**：`HANDOFF §11` 总览表加一行 + `§14.3` 计数刷新（都是**实测值**，不估算）。

### 13.2 依赖判据（决定"能不能搬"）

| 依赖形态 | 处理 |
|---|---|
| **共享值**（可参数化） | 传参：`AgentResolver`、`readerTenant`、`maxFileBytes()` |
| **共享静态**（方法/常量） | 留门面 → 放宽为包内可见 + 簇内按类名限定（`SessionQaResolution.xxx(`） |
| **共享行为**（实例方法，带字段态） | ① **随簇搬走**（首选）② **先落被依赖方**（把该成员先搬进协作者，再让依赖它的刀持有协作者）③ 调整刀序 |
| **`@Autowired` 字段** | 构造期未注入，普通协作者拿不到 → 改 **`ObjectProvider` 构造注入**（仓内既有样式，语义等价）或作参数传值 |
| **嵌套/公共类型** | 类型不能委托 → 留门面，放宽包内可见，簇内按 `外层类.类型` 引用 |
| **`record`** | 跨类搬运要把**字段访问**改成**访问器**（`r.x` → `r.x()`） |

### 13.3 对外契约的保法：**全量薄委托**

每个搬走的成员，在门面留一行委托（`return collaborator.member(args);`）→ 宿主与同族调用点**零改动**。
注意：static 成员委托要写 `QaXxx.member(...)`；类型/常量不能委托。

### 13.4 守卫必须"正向证据"（血泪）

- 编译：断言日志含 **`BUILD SUCCESSFUL`**；**不要**用"没出现 `error:`"当判据（shell 报错/`command not found` 会被漏掉，
  曾因此误推坏提交）。
- 测试：读 `server/build/test-results/test/TEST-*.xml` 的 **`failures + errors == 0` 且用例数达标**；
  `compileTestJava` 绿 ≠ 测试绿（曾把一条失败测试推上去）。
- harness 里 Gradle 一律**显式 `./gradlew`**（别用变量跨进程传，曾因 `$GW` 未导出导致命令静默失败）。

### 13.5 忠实性核验（机械搬运的"逐字比对"）

从门面旧版（备份）与协作者新文件各抽同名成员体 → 剥掉注释与全部前导修饰符（**循环剥**，只剥一层会出假差异）→ 空白归一 → 逐字比较。
**只允许登记过的替换**反向归一（限定名前缀、参数化、字段→访问器、重命名）。

### 13.6 脚本与正则的坑（都踩过）

- **点号 lookbehind 会骗人**：`(?<![\w.])name\(` 匹配不到 `field.name(` → 报"0 处"（假 0）。判"是否还有裸调用"要剥限定词或用 `(?<![\w])`。
- **`this.X(` 形态**要单独识别。
- **空白行折叠**会改行数（曾一次折叠掉 935 行）：干跑断言必须写明 `剩余 == 行数 − 删除集 + 新增`，差值必须为 0。
- heredoc 里写正则补丁极易双重转义（曾有 `unterminated subpattern`）→ 补丁用**免转义字符串替换**。
- 机械改写自伤两例：拼实参漏逗号、`replace("...) {", …)` 把参数名前缀留在签名里 → **长签名改完必看一次现场**。

### 13.7 搜索/检查纪律

- 依赖扫描**只用于预估**，**编译才是权威**；每刀准备"显式补充清单"（扫描三次漏掉 `stringListOf`/`waitForAttachments`/`templateContentByIdAndFile`）。
- 找不到 import 时，先怀疑"它是嵌套类型"（`grep "class X\b"` 直接搜声明）。
- 差 1~2 行的异常先别慌：**先解释异常再动刀**（本次两次"异常"分别是空白折叠与扫描顺序假象）。

### 13.8 切片产物的排版与源码卫生（2026-10-01 wiki page 面批次，3 条）

1. **切片产物"每行后跟一个空行"的排版 artifact**（wiki/session 两批共 14 文件、空白行占比
   55%~78%，仓库中位数 13%）：文件被撑大一倍以上，`wc -l` 榜单随之失真——`WikiPageFolderSupport`
   表面 822 行、实际 383 行；`SessionQaResolution` 707→184。**落刀写盘一律单空行**；发现后
   用 `scripts/normalize-blank-lines.py` 折叠（只删空行 + "非空行逐一相同"断言；口径：>100 行且
   空白率 >30%）。**推广**：任何 `wc -l`/行数榜单先看空白率，别把排版当规模。
2. **源码里的裸 NUL 字节**：字符串字面量内嵌真实 NUL（`"...\x00..."`）会让 **git 把文件当二进制**
   （`Bin 5600 -> 5443 bytes`，`git grep` 只回 "Binary file matches"）→ diff/blame 全不可读。
   写成转义 `"\0"`（值不变）即可恢复文本。**判据**：`file <f>` 报 `data` / diff 报 `Bin` 就是它。
3. **死方法的判别不能信 javadoc 自述**：`assertNoFolderConflict` 的 javadoc 写着"供 service 在
   创建前显式判定冲突时抛错"，但全仓 `git grep` 0 调用方（service 侧真正用的是 `folderNameExists`）
   ——属未接线遗留，删。**删前两种写法都扫**：`<name>(` 与 `.<name>(`（含 `Type.name(`）。
   另一条连带经验：拆仓储时若某方法与目标聚合共用 mapper（`listDistinctCategoryPaths` 走
   `WikiFolderMapper`），**按数据归属判**（它是文件夹路径查询）→ 随该聚合走，别为了让新仓储
   少背一个 mapper 而留下它。

### 13.9 闸门命令的环境卫生（2026-10-01 evaluation 打样批，1 条）

**source .env 的 shell 会把 `SYSTEM_AES_KEY` 等变量泄漏给同 shell 里跑的 Gradle 测试**：
CLI 起服务常写 `set -a && . ./.env && set +a && ./gradlew :domains:bootRun`；Agent 工具链会
**复用同一 shell**，后续在同一会话里跑的全量测试就带上了这些变量——`DataSourceConfig.toJSON()`
走 AES 加密分支，`DataSourceJsonTest.dataSourceConfigToJsonMatchesGoMarshal` 的"无 KEY 时凭据
原样落库"断言失败（**1/4669，其余全绿，极易误判为回归**）。
**判据**：全量里出现"孤零零 1 个与环境相关的失败"时，先 `env | grep SYSTEM_AES`；
**修法**：跑闸门用 `env -u SYSTEM_AES_KEY ./gradlew ...`（或换独立 shell 复跑该单类确认）。

### 13.10 校验文案的 locale / Accept-Language 漂移（2026-10-01 model 域换锚，1 条）

**`@Valid` 默认 message 的 400 文案随请求头/进程 locale 变化**：同一条缺失字段的请求，
不带 `Accept-Language` 时 details = `name: 不得为空白`（Hibernate Validator 中文资源包原样），
带 `Accept-Language: en` / `zh-CN` 时 details = `name: 不能为空`（英文资源包经全局
`translateMessage` 归一）——**同一个二进制、两种文案**。
**根因**：契约测试（MockMvc 不带该头）只能钉住其中一种，真实匿名客户端与浏览器看到的可能不同。
**修法（随批次）**：`@Valid` 注解显式写 `message = "字段名: 不能为空"`——`^[a-z][A-Za-z0-9_]*: `
前缀格式被全局 `handleBind` 原样采用，不依赖任何资源包（model 域 2026-10-01 已示范）。
**全仓现状**：knowledge/session 等域的旧注解仍用默认 message，属"错误形态统一批"存量，逐域顺手改。

### 13.11 前端测试运行时不解析 `@/` 别名（2026-10-01 session S2 前端批，1 条）

**给被 `node:test`（`tsx --test`）直接加载的模块加 `@/...` 的"值导入"会让整个测试文件在加载期崩掉**
（`ERR_MODULE_NOT_FOUND: Cannot find package '@/types'`）——报错停在测试文件那一层，看起来像用例失败，
其实是模块解析失败。`paths` 只在 `tsconfig.app.json` 里，而根 `tsconfig.json` 没有，tsx 以根为准。
**判据**：某测试文件"整档失败"（`# Subtest: src/xxx.test.ts` 直接 not ok、无具体断言）时先看这条。
**修法**：这类模块用相对导入（`../types/mention`）；`import type { X } from '@/...'` 是安全的（类型导入被擦除）。
App 侧（Vite / vue-tsc）不受影响。

### 13.12 契约夹具批量重录（换锚批的省力工具，2026-10-01 embed E1 起可用）

换锚一次会改几十个 golden（去信封 / 键改名 / 键恒输出）。手改慢且容易漏，从 embed E1 起提供开关：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :domains:test --tests "com.ragagent.embed.EmbedContractTest" -Dcontract.refresh=true
```

命中时把**掩码后的实际响应**写回 `src/test/resources/contracts/`（`server/build.gradle.kts` 负责把该
属性转发给 fork 出的测试 JVM——Gradle 的 `-D` 只作用于 daemon，不转发测试读不到）。
目前 `EmbedContractTest` 已接入；其它契约测试按需照搬 `REFRESH_FIXTURES` 那三行。

**纪律（重要）**：重录之后**必须结构化复核差异**（解析新旧 JSON、比键集与取值），确认差异只是
本次换锚该有的那几类；否则就成了"测试适应实现"，夹具失去契约价值。复核脚本思路见 §14.9m。

### 13.13 契约夹具的掩码按键名匹配——键改名必须同步放宽正则（2026-10-01，三次实录）

**夹具掩码用的正则形如 `"([a-z_]+)":"<uuid>"`（按键名锚定）**：换锚（下划线 → camelCase）之后，
`knowledgeBaseId`/`dataSourceId`/`createdAt` 这类键不再匹配 → **掩码静默失效**，
夹具里混进真实 UUID 与**逐次变化的真实时间戳**（下次跑就红，看起来像业务回归）。
**判据**：一批换锚后先看"重录的夹具里还有没有未掩的 id/时间戳"，再跑第二遍确认确定性。
**修法**：把键名字符集放宽成 `[A-Za-z_]+`（含大写），并在掩码定义处留注释。
**三次实录**：S3 `att-get` 的 sessionId（掩码未覆盖，夹具混进真实会话 id）→ M4 `McpContractTest`
（加 `serviceId`）→ D1 `DataSourceHttpContractTest`（`[a-z_]+` → `[A-Za-z_]+`，一次影响 12 个夹具）。

## 14. 逐包重构范式（knowledge 为范本，其余域照此推进）

> **用户定稿（2026-09-30）：以 knowledge 包的重构为范本，逐步重构其他包。**
> §12 是"拆完长什么样"，本节是"**怎么拆**"——把 knowledge 那 40+ 个提交里可复制的部分固化成 SOP。
> knowledge 的量化基线（目标形态，实测 2026-09-30）：197 文件 / 25,138 行 / 最大类 1,234 行 /
> ≥800 行 3 个（皆有 javadoc 注明的例外理由）/**Go 锚点 0 / rawBody 0 / 逐字段 `@JsonProperty` 0 / 未使用 import 0**。

### 14.1 三条内核（范本之所以有效的地方）

1. **沿注释边界拆，不按行数硬切**：神类里的 `// ── X 段 ──` 分割线就是拆解点（`KnowledgeService`
   3,392 行 → 门面 + 7 切片服务就是照这个来的）。拆完"门面保留全部公共委托"→ 18+ 注入点与
   Mockito 测试**零改动**，这是能把大手术做小的关键。
2. **每步全绿再走下一步**（2026-09-30 起分档，命令见 §14.4）：闸门按改动性质分档——迭代中只跑单类/单域；
   **常规批**（同包抽协作者、成员增删、卫生、文案）= `--rerun-tasks` 重编（~31s）+ 受影响域测试（~30s）
   + `spotlessCheck`（~10s）≈ 1m10s；**结构搬迁批**（跨包 `git mv`、改共享 API、删类）=
   `clean :domains:test :spotlessCheck`（~3m25s）。**判据：抓断链靠"强制重编"（便宜），防跨域行为回归才靠全量（贵）**
   ——别把两件事混成一件（§13.21 的两次假绿根因都是**编译错误**被增量编译掩盖，重编即可暴露；
   用例数只认"干净一遍"后的 `build/test-results/test/*.xml` 汇总）。
   这是"种子 fork + 渐进转型"优于重写的全部意义（§9）。
3. **一次只动一个轴**（§3 红线 1）：拆类期不改契约、换锚期不拆类、卫生期不动逻辑。
   轴混了就退化成大爆炸重写。

### 14.2 单域 SOP（七步，每步独立提交、独立全绿）

| 步 | 动作 | 关键点 / 产出 |
|---|---|---|
| 0 侦察 | 按 §14.4 命令出该域体检表 | 规模 / 神类 / Go 锚点 / `@JsonProperty` / rawBody / 未用 import |
| 1 边界 | 判"该域哪些**不能动**" + 列跨包缝合点 | 对照 §11 边界清单；缝合点用 `git grep` 实测，别凭印象 |
| 2 拆分 | 神类沿注释边界 → 门面 + 切片；容器类 → 一类型一文件 | 测试随被拆类**同包 `git mv`**；容器级常量/私有 helper 先安置（§13.4/13.5） |
| 3 分层 | `controller/service/support/task/client/storage/security/repository/mapper/domain/dto` | 每包一份 `package-info.java`（职责地图） |
| 4 契约 Java 化 | controller 入参 → DTO + `@Valid`；去 `@JsonNaming` / 逐字段 `@JsonProperty` / 信封 | **顺手消灭手写绑定器**（那是真实缺陷温床，§7 第 1 条）；同批带前端 |
| 5 数据访问去 Go | 落库 jsonb 去 snake（若该域有此面）| 改完必须 `grep` 全仓"按旧键读取"的代码（§11 ② 的 4 处真实缺陷） |
| 6 卫生 | 注释判据与 import 卫生（§13.8）/ 坏 `{@link}` / 批次代号清除 | `spotlessApply` 是标准手段，别自己写替换脚本 |
| 7 收尾 | 更新 §4 数据、§12 地图、§14.3 候选表 | 顺带把该域新踩的坑写进 §13 |

### 14.3 候选域盘点（2026-10-02 收官复测：真单类清零，≥800 只剩 **4 个登记例外**）

> ⚠️ **2026-10-08 B122 修正（复测失真）**：上句的「4 个」是 2026-10-02 的读数，
> 现已失真——实测 **≥800 有 5 个**，且**只有 3 个是已论证例外**：
> `MemoryIndexStore` 919 / `KnowledgeProcessWorker` 847 / `KnowledgeService` 827（三者类头均有自述理由 ✓）；
> 另两个是**欠债**：`ImService` **1,091**（2026-10-01 切片至 664「出榜」后 7 天回涨 **+427**，
> 无人察觉——§14.5 只在 ≥800 时登记、当时的守卫只管新文件）与
> `SessionKnowledgeQaService` **1,041**（HANDOFF 记「已登记例外」但类 javadoc 无自述理由，
> 违反 §14.5 自己的要求；本文件 §⭐ 待办里早已列为「例外复核」）。
> 更根本的问题：**政策在散文里，就会漂移**。B122 已把 §14.5 的三条要求做成 `check-file-size.py`
> 的可执行规则（R-c ≥800 必须登记 / R-d 例外必须自述 / R-e 自述数字必须相符），
> 并新增 R-b 拦截「出榜后回涨」。登记表见 `scripts/file-size.baseline.json` 的 `exempt`
> （`accepted` = 已论证；`pending` = 欠债，即后续批次的工作清单）。
>
> **进展（2026-10-08 B123）**：`ImService` 第一刀已落，1091 → **995**（`ImStopOps` 外提停止链路）；出榜（<800）还需 2~3 刀，候选见 B123 行。

| 域 | ≥800 的类（行数） |
|---|---|
| datasource | **已清零**（`DataSourceService` 1,828→666 §14.7.15；`FeishuClient` 1,155→709、`NotionConnector` 1,093→625 §14.7.16） |
| memory | **已清零**（`MemoryService` 1,662→965→**768** 于 m5 出榜 §14.7.17；`MemoryIndexStore` 929 为**登记例外**：六段同属「索引侧读写」一个关注点，用户 2026-10-01 定调不硬切；仓储 1,515→458、`MemoryExtractionService` 1,216→718 亦已出榜） |
| retrieval | **已清零**（9 引擎仓 + HybridSearchService 1,260→775 均出榜；§14.7.5/§14.7.6） |
| wiki | **已清零**（`WikiPageController`/`WikiIngestService` §14.7.2；page 面 4 类 §14.7.14：1,008→732 / 858→708 / 851→468 / 822→383，最大类 `WikiIngestCitePipeline` 760） |
| mcp | **已清零**（McpServiceController 937→372 + OAuthHandler 825→220，§14.7.10） |
| 其余单类 | **已清零**（`SourceRegistry` 878→534，工具参数编解码外提 `SourceToolCodec`；`UserService` 876→748，会话令牌操作外提 `UserSessionOps`——§14.9s） |
| chatpipeline | **已清零**（PluginMerge 1,155→670 C1 + PluginSearch 899→278 P1-P3，§14.7.13） |
| embed | **已清零**（EmbedChannelController 925→561，§14.7.12；EmbedChannelService 792 本就 <800） |
| im / llm | **均已清零**（llm：RemoteApiChat 1,366→513，§14.7.8；im：FeishuAdapter 926→331，§14.7.11） |
| session | `SessionKnowledgeQaService` 1,036（§14.5 已登记例外）；**本域已清零**（§11.3） |
| auth | **controller 已清零**（§14.7.7 AuthController + §14.7.9 TenantCatalogController 980→133）；service 域剩 `UserService` 876 + apikey 未动 |

**例外复核结论（2026-10-02，§14.5 判据＝接缝优先）**：四个例外全部**维持登记**——
`SessionKnowledgeQaService` 1,036（三条入口流单一状态机，javadoc 已备案）、`MemoryIndexStore` 932
（六段同属索引侧读写，用户定调不硬切；javadoc 本批补例外说明）、`KnowledgeService` 848 /
`KnowledgeProcessWorker` 816（knowledge 门面与摄取状态机，类注释已备案；后者本批补 javadoc）。

复测命令：`git ls-files 'domains/src/main/java/**/*.java' | xargs wc -l | sort -rn | head -20`

### 14.4 体检命令（复制即用）

```bash
cd ~/ragagent
# 神类/大文件排行（全仓）
git ls-files 'domains/src/main/java/**/*.java' | xargs wc -l | sort -rn | head -25
# Go 债务：锚点注释 / 逐字段 @JsonProperty / 手写 rawBody 绑定
git grep -cE '对照 Go|GORM|Go 的' -- 'domains/src/main/java/**/*.java' | sort -t: -k2 -nr | head -15
git grep -c '@JsonProperty(' -- 'domains/src/main/java/**/*.java' | sort -t: -k2 -nr | head -15
git grep -nE '@RequestBody\s+(String|Map<|JsonNode|Object)' -- 'domains/src/main/java/**/*.java'
# 卫生闸门（ratchet：只覆盖 seed 后触碰过的文件，这是设计不是遗漏）
./gradlew :domains:spotlessCheck
# 收尾闸门（按改动分档；实测：重编 ~31s / 单域 ~30s / spotless ~10s / 全量 ~2m50s / clean 全量 ~3m25s）
# ① 常规批（同包抽协作者、成员增删、卫生、文案）≈ 1m10s
./gradlew :domains:compileJava :domains:compileTestJava --rerun-tasks   # 专抓"引用被搬走"的断链
./gradlew :domains:test --tests "com.ragagent.<受影响域>.*"
./gradlew :domains:spotlessCheck
# ② 结构搬迁批（跨包 git mv / 改共享 API / 删类）≈ 3m25s
./gradlew :domains:clean :domains:test :domains:spotlessCheck
# ③ 迭代中：单类/单域（秒级）
./gradlew :domains:test --tests "com.ragagent.session.service.SomeTest"
# 前端契约同步（触及前端契约时）
(cd frontend && npx vue-tsc --build --force && npm test)
```

### 14.5 完成判据（Acceptance，逐项核对）
> **800 行是启发式判据（2026-10-01 用户定调）**：切完只略超、或再切不再落在自然接缝上时，
> **不硬切**——按本节登记为已知例外并说明理由；判据是接缝，不是行数。

- [ ] 该域最大类 < 800 行；例外必须在类 javadoc 写明理由（对齐 knowledge 的 3 个例外）
- [ ] Go 锚点注释 0 / 注释掉的代码 0 / 坏 `{@link}` 0 / 批次与阶段代号 0
- [ ] 请求侧无 `@JsonNaming`、无逐字段 `@JsonProperty`；无 `{data,success}` 信封；删除返 204；可空显式 `null`
- [ ] controller 入参全部 `@Valid` DTO（multipart 与"固定文案兜底"端点可保留手绑，但须在 javadoc 注明）
- [ ] 每个子包有 `package-info.java`；`*Util`/容器类等反模式命名清零
- [ ] **无全限定名注解**（除真同名冲突并在注释说明）；校验 `message` 保持「字段名: 原因」前缀格式
- [ ] **死成员清零**：未使用 logger / `ObjectMapper` / 私有方法 / 局部变量 / **只注入不读取的 final 依赖**（口径见 §13.15 ①④）；javac 不报未使用私有成员、Spotless 也只查 import，**必须主动扫**——B131 起 `scripts/check-dead-members.py` 覆盖其中可自动化的三类（重复 import / 未使用 import / 遗留 Logger·ObjectMapper 字段）；私有方法、局部变量、只注入不读取的依赖仍需人工（常量族的"声明未用"是词汇表性质，全仓 934 处，刻意不做硬门）
- [ ] 触点变更后按 §14.4 分档收口：**常规批** = `--rerun-tasks` 重编 + 受影响域测试 + `spotlessCheck`；
      **结构搬迁批** = `clean :domains:test :spotlessCheck`（+ 触及前端契约时 `vue-tsc` 0 错误 / `npm test` 全绿）
- [ ] §4 数据、§12 地图、§13 经验、本节候选表四处同步更新

### 14.6 不要做什么（踩过的坑，别再踩）

- **别把"Go 序列化层删除"拆到各域**：409 处引用 / 94 文件的那一刀按 §3 红线必须**一次性全仓完成**。
  按域先换锚（同 PR 带前端）是允许的，删序列化器本体不是。
- **别动冻结面，但先核对理由**：现行口径见 **§15.3**（2026-10-08 重排为「外部决定 / 协议行为面 / 落库·DDL」三类，并单列「已解除」）。
  以旧结论（"工具输出自有 schema""存量回放面"）为由拒绝改造前，先看 §15.3 的理由栏是否仍成立——
  「兼容历史数据」已整体作废；「协议/行为面」要连提示词与解析器同批且需拍板；「外部决定」才是真不能动。
- **别为数字写注释**：getter/POJO 访问器保持 0 javadoc（§13.8 第 3 条）。
- **别做全仓文本替换**：先用单文件验证再决定扩大（§13.2 的两次翻车）。
- **别跳过闸门**：只跑 `:domains:test` 会漏掉 Spotless（§13.9）。


### 14.9 契约工作（阶段 3 换锚）——何时做 / 做什么 / 怎么验收（2026-10-01 补写）

**为什么单独立节**：标准在 §2 第 4 条、进度散在 §2/§5、细则在 `docs/knowledge-api-contract-v1.md`，
而"什么时候做、一次做多少、怎么算完成"此前没有集中交代 —— 新 Agent 容易误判（本轮曾误写成"均未开始"）。

**已定、勿再讨论**
- 标准 = §2 第 4 条四段：camelCase 且 **JSON 字段名＝Java 字段名**（禁逐字段 `@JsonProperty` / `@JsonNaming`）；
  成功响应不包 `{data,success}`（分页 `{"items","page","pageSize","total"}`、删除返 **204**）；
  错误统一 `{"error":{"code","message","details"}}`；时间 ISO-8601 带时区；可空字段**显式 null**；不用 Problem Details。
- 落库格式（jsonb）同走 Java 字段名、**不加兼容别名**（§2 第 11 条）。
- 每个改契约的 PR **同批带前端**（§2 第 3 条）；产品未上线，无兼容期（§2 第 2 条）。
- 细则与历史差异表：`docs/knowledge-api-contract-v1.md`（v1.0，32KB）。

**进度（2026-10-02 收官）**
- 已完成：knowledge（含 34 个端点 DTO 化）/ retrieval / 会话链 / chunker-preview / evaluation（§14.9b）/ model（§14.9c/e）/ system（§14.9f/g）/ auth（§14.9h/i/j + 补刀 §14.9s）/ memory（§14.9k）/ session（§14.9l）/ embed（§14.9m）/ mcp（§14.9n/o/p + M6 §14.9s）/ datasource（§14.9q）/ wiki（§14.9r）/ **散存量七域 + 错误体统一（§14.9s）**。
- **无未完成项**。残留 `@JsonInclude` **213 处**（**2026-10-09 机械重算订正**：原写 ~288 处 ✗；NON_EMPTY 104 / NON_DEFAULT 57 / NON_NULL 46 / CUSTOM 3（三个队列载荷）/ ALWAYS 3 ✓，全部落在 §14.6 登记面内）与 Go 零值时间哨兵为登记尾巴（§14.6 / ⭐ 第 4 条），非在办。

**存量表（2026-10-01 盘点实测，§14.9 第 1 步交付物；只读扫描，三分法甄别）**

`@JsonProperty` 全仓 1,978 处 / 221 文件，**不是都是债**：

| 类别 | 量级 | 处置 |
|---|---|---|
| ① 外部 API 映射面（第三方 snake_case 合法映射） | 346 处 / 24 文件（feishu/yuque/ima/gitlab/notion 等 connector+client） | **保留**（映射外部 API 不是 Go 债） |
| ② §11 已登记边界面（SSE/Redis 事件载荷、provider 请求体、手搓载荷、agent config jsonb） | **event 155 + stream 9 的冗余注解已清（B151）· 外部协议面的冗余注解也已清 43 处（B152，2026-10-09：只删注解、键与字段序未动 ⇒ 线格式不变；两批共 278 处）**；agent(`AgentConfig`) 14 + tracing 7 + llm 大部（provider 面）保留 | **保留**（§14.6 边界清单；动它=改事件契约，须独立切片）；**全域改由守卫 A14（`JsonPropertyHygieneTest`）统一硬门守**——B152 起**没有面级放行表**：只剩四类必要形态（键≠字段名 / 键形如 isXxx / Jackson 探测不到 / 混用改名注解的类型=字段序锚，整文件跳过）|
| ③ 真·阶段 3 存量（HTTP 契约面 + 落库 jsonb 面） | **~897 处 / ~150 文件**，重域：auth 247 / datasource 127 / memory 123 / mcp 110 / system 86 / **wiki 39 → 0（W1 收官，§14.9r：17 键换锚 + 8 处死注解摘除；余 22 处 = `lf_*` 5 + LLM 解析面 17，登记冻结）**；evaluation 62 → **0**（打样，§14.9b）；model 87 → **0**（四块收官，§14.9c/§14.9e）；**session 188 → 0（S1+S2+S3+S4+S5 全部收官，§14.9l，含 5 处落库 jsonb 迁移 SQL）**；**embed 23 → 0（E1 收官，§14.9m）**；**mcp 119 → 58（M1，§14.9n）→ 35（M4，§14.9o）→ 22（M5 收官，§14.9p；余 22 处全是第三方协议面：RFC 8414/9728/6749 文档 + 授权服务器 token 响应，永久冻结）**；**datasource 493 → 417（D1）→ 366（D2）→ 355（D3 收官，§14.9q）：余 350 处为 connector 第三方线格式 + 5 处 `lf_*` 平铺载具，**均为永久冻结** ⇒ 该域可换锚面 0**。**2026-10-02 两段收官判定**：域级换锚（§14.9r）后散尾巴约 77 处由 §14.9s 七域批处理完——甄别结果：
真存量已换锚（audit 1+信封、favorite 5、im 17、storage 24、retrieval WebSearchResult 7、TempKbState 3、
APIPrincipalConfig 5、RuntimeStat 5 死注、WebSearchResult 死注）；**判冻结新增登记**：image_info（docreader
第三方）、SearchParams（chat span 载荷）、RankResult（第三方 rerank API）。复查批后全仓 `@JsonProperty` 余量 **913 处
/ 102 文件** 全部是登记冻结面（§14.6）⇒ **③ 类真存量 = 0，阶段 3 收官**。⚠️ **计数口径**：`QaRequests` 那批用的是全限定注解（`@com.fasterxml…JsonProperty`），只 grep `@JsonProperty` 会漏——盘点时两种写法都要扫 | 按域推进，一域一 PR 同批带前端 |

`@JsonInclude`（Go omitempty 直译）存量：**213 处**（**2026-10-09 机械重算订正**：原写 ~487 处 ✗ = 更早快照的过期计数；NON_EMPTY 104 / NON_DEFAULT 57 / NON_NULL 46 / **CUSTOM 3**（`DataSourceSyncPayload`/`MemoryExtractPayload`/`WikiIngestPayload` 三个队列载荷）· ALWAYS 3 ✓ ——ALWAYS 是正确形态的显式 null ✓）。**按面**：llm 59 · event 50 · tenantconfig(jsonb) 43 · common 19 · agent 10 · connector 8 · tracing(lf_*) 7 · approval 6 · 其余 15 ✓ ⇒ **全在 §15 B3 明文豁免的冻结面内** ✓；**B3 真面（wiki domain 全家 / websearch 三 DTO / VectorStoreTypes）已清零** ✓（机械扫描确认 ✓），我们自己的面仅剩 14 处且逐条有明文理由（如 `RssCursor` javadoc 的「为空省略：`null` 或空 map 时整个键消失」✓ = 该注解即线格式契约 ✓）。
`@JsonNaming` **0**、Problem Details **0**、Go 序列化器线上引用 **0**（2026-09-30 已一次性删除）。
Controller 全仓 52 个；每域 PR 入场时再做该域的"端点 × 前端调用点"细清单（§14.9 执行顺序第 3 步的入场检查）。

**执行顺序（关键约束）**
1. **先做清单盘点**（低风险、只读）：按域扫出「未换锚端点 + 对应前端调用点 + 涉及的 Go 序列化残留（注解 / jsr310 / NON_NULL / Problem Details 引用）」，
   产出一张存量表（形如 §14.3）。
2. **再一次性全仓删除序列化层**（§2 第 7 条已删的 156 处注解 + `JacksonConfig` 是第一批；剩余引用按清单扫净）——
   **不允许半删状态**（§5 阶段 3 红字：一部分端点走 Go 格式、一部分走标准 Jackson 最危险）。
3. 端点/落库面**按清单逐域推进**：一个域一个 PR、同批带前端、重录 fixture。

**验收（Acceptance）**
- 全量测试绿（当前口径 **4,858 用例**，2026-10-10 ✓）；每一步"改一步 → 全量验证 → 提交"。
- 契约 fixture 重录后**语义对比**（键序/转义归一化）全过；200 路径 fixture 应零改动。
- 前端同 PR；`docs/knowledge-api-contract-v1.md` 同步更新版本与差异表。

**与阶段 2 的关系**：路线图上可对调（§5）；但换锚要动 HTTP 面与落库面，
**建议目标域先做完"神类切片"再换锚**，避免同一批文件反复改。

---

### 14.7+ 域级执行记录索引（正文已移出，节号保持原样）

| 节 | 状态 | 正文 |
|---|---|---|
| 14.7 wiki/im/retrieval/llm/auth/mcp/embed/chatpipeline/datasource/memory 神类拆分 | ✅ 执行完毕 | `docs/handoff/plans/14.7-域神类拆分执行记录.md` |
| 14.9b/14.9c/14.9e 小域打样与 model 收官 | ✅ | `docs/handoff/plans/14.9a-换锚总纲与小域打样.md` |
| 14.9f~14.9k system/auth/memory 域换锚 | ✅（14.9k 的 M3 亦已完成） | `docs/handoff/plans/14.9b-system-auth-memory.md` |
| 14.8 与 14.9l~14.9s session/embed/mcp/datasource/wiki 换锚 | ✅（14.9l/14.9q 标题原写"待执行"系过期，正文含 ✅ 交付记录） | `docs/handoff/plans/14.9c-session-embed-mcp-datasource-wiki.md` |

> 在这些计划文件里按**节号 Ctrl-F**（如 `14.7.2`、`14.9l`）即可定位原节。

## 15. 全面修复计划（2026-10-02 立项；批次由用户排期，本文档即排期输入）

> **输入与现状**：阶段 2（神类切片）与阶段 3（契约换锚）已收官（§14.3/§14.9s）——≥800 只剩
> 4 个登记例外，`@JsonProperty` 913 处全部是登记冻结面。本计划覆盖**全部已知残留**：
> 两轮复查暴露的"实况断点"类别（视图层读取漂移）、登记在案的尾巴（`@JsonInclude` 288 处、
> Go 零值时间哨兵、lf_* 载具）、以及 §5 阶段 4 既定面。**批次表就是排期输入**，做完一批
> 勾一批、把执行记录写回本节。

### 15.1 批次总表（状态：✅ 完成 / ⬜ 待做；执行记录见 15.1.1）

> **本表＝索引（2026-10-10 压缩：205 条批次说明 → 一行一批，省 263 KB）**：每行保留 批号 / 范围 / 优先级 / 量级 / 状态+日期 ✓。
> **详情取回**：每批提交信息都带 `(B###)` 号 ⇒ `git log --grep="(B201)"` 精确取回；`git log --oneline | grep B20` 看列表 ✓。

| 批 | 内容 | 优先级 | 量级 | 状态 |
|---|---|---|---|---|
| **B0 端到端真实走查** | 起服（后端 8083 + 前端 dev）按域走查 14 条链路：注册/登录/**令牌刷新**/登出 → 空间创建/切换/成员邀请/**审计页** → KB 创建/摄取/**处理时间线**/预览 → 检索/对话（SSE）→ wiki 浏览/编辑 → datasource（RSS 桩）同步/凭据 → im 渠道 CRUD → vectorstore/storage 设置 → 收藏/技能目录/模型调试/系统运行时页。每条记录 API 形状 × 视图渲染 × 控制台报错 | **P0** | 1-2 天 | ✅ **完成（2026-10-02）**——14 条链路全走通，修 15 处断点（前端 12：裸体未适配 8 + 字段漂移 4 族； |
| **B209 P4 第 3 刀：API-Key 认证通道口 + 侦察更正** | 最后一条 main 边清零 | 架构 | 小 | ✅ **完成（2026-10-10）**——`:common` 新增 `ApiKeyAuthPort`（签名与实现逐字一致，含 `throws IOException` ✓ —— 第一版漏了它 ⇒ 编译错 ✓），`APIKeyAuthChannel implements` 该口、`AuthFilter` 改依赖口 ✓ ⇒ **`domains → apikey` 代码边 1 → 0** ✓✓。**侦察更正** ✗：`TenantFilterGuard` 引的是 **MyBatis 语句名字符串白名单**（4 条 ✓）而非 mapper 类型 ⇒ **边 5 是误报** ✓（同 R-d 那类 ✓）；但 apikey 改名 `channels.api` 时这 4 条字符串**必须同批更新** ✓（已写进 §12 ✓）。验证：`spotlessCheck build` ✓ 1m40s · **4858 / 0** ✓ · 九守卫 ✓ · 验环 ✓。 |
| **B208 P4 第 2 刀：API-Key 管理口 port（:common）** | 窄口 + 脱敏搬移 + 清死依赖/迪米特 | 架构 | 小 | ✅ **完成（2026-10-10）**——`:common` 新增 `ApiKeyAdminPort`（平台列表/建/撤 + 租户默认键 ✓），apikey 侧 `ApiKeyAdminAdapter` 实现（脱敏 `masked()` **逐字搬** ✓）；`SystemAdminController` / `TenantCatalogController` / `TenantCreateOps` 改注入 port ✓（后者顺带修掉 `service.apiKeyService.…` 的迪米特违规 ✗ 与 Catalog 的死依赖 ✗）。**main 侧边数 5 → 1** ✓（只剩 `AuthFilter → APIKeyAuthChannel` ⇒ 第 3 刀 ✓）。验证：`spotlessCheck build` ✓ 1m37s · **4858 / 0** ✓ · 九守卫 ✓ · 验环 ✓。 |
| **B207 P4 第 1 刀：API-Key 两个响应记录沉 :common** | 记录下沉 + 映射留属主 | 架构 | 小 | ✅ **完成（2026-10-10）**——`TenantAPIKeyResponse` / `TenantAPIKeyCreateResponse` → `com.ragagent.common.apikey` ✓；**关键**：`TenantAPIKeyResponse.from(TenantAPIKey)` 依赖实体 ⇒ **不搬**（搬了会在 `:common` 造反向边 ✗），改建 `auth.apikey.domain.TenantAPIKeyProjections`（语义逐字保留 ✓）。doclint 抓到 `TenantAPIKey` 的 `{@link}` 还指旧包 ✗（§10 沉淀③ 原文生效 ✓）⇒ 全仓修 javadoc 引用 ✓。边数 6 → 5（DTO 两处消失；`SystemAdminController` 改指 projections ⇒ 该边留给第 2 刀的管理口 port 一并消掉 ✓）。验证：`spotlessCheck build` ✓ 1m40s · **4858 / 0** ✓ · 验环 ✓ · 九守卫 ✓。 |
| **B206 P4 前置侦察：auth ⇄ apikey 的真实边清单** | 只读侦察 + 四刀计划（未动代码） | 架构 | 小 | ✅ **完成（2026-10-10）**——实测推翻旧读数「22 + 22」✗：真实阻塞 = **main 侧 6 处 + common 侧 4 处** ✓（反向 4 处方向合法 ✓；boot 侧 7 类注册 + 4 个测试引用无害 ✓）。清单与四刀计划写进 `docs/phase4-module-boundaries-plan.md` §12 ✓（沉 2 个 DTO + 3 个 port：管理口 / 认证通道口 / 占位符查询口 ✓）。顺带更正：P3 早于 B163 完成 ✓（该文档「待 P3」行同批改 ✅ ✓）。 |
| **B205 收尾最后一个 ⬜：落库 jsonb 存量键** | 逐键判定 + dev 库一次性修数 | 数据 | 小 | ✅ **完成（2026-10-10）**——**批次表 ⬜ 清零** ✓。判定：落库面已全 camel，残留 snake 全在冻结面；dev 库实测 4 列 6 行旧键（chat_history 1 / retrieval 1 / storage_engine 1 / context 3）⇒ 备份后一次性修数（六列 + memory 两列旧键行数全 0 ✓）。三条踩坑：① `jsonb_set` 与嵌套 `(col->a - k)` 的类型解析会翻车（unknown→jsonb）；② psql `-c` 里 `:'var'` 不插值 ⇒ 改美元引号；③ `printf` 会转义 `$` ⇒ 改用 python 写 SQL 文件。 |
| **B204 交接文档梳理（HANDOFF 瘦身）** | §15.1 批次表压成一行一批 + 过期段清理 | 文档 | 小 | ✅ **完成（2026-10-10）**——**432.9 KB → 170.3 KB（-61%，省 263 KB）**：① `§15.1` 205 条批次说明逐行压成「批号 / 范围 / 优先级 / 量级 / 状态+日期」一行 ✓，**详情取回 recipe** 写进表头（每批 commit 带 `(B###)` ⇒ `git log --grep="(B201)"` ✓）；② `§15.1.1` 索引行去重复前缀 + 截断（正文早已在 `docs/handoff/records/batch-records.md` ✓）；③ 压掉三段明确过期的：`§6 缝合点`（2026-09-28 清单，已执行完毕 ✓，6.1/6.2 降为 stub 且**编号保留** ✓）、`7.1 存档`（knowledge 域重构，16 行 → 5 行 ✓）、`§11.4` 历史版本 ✓；④ 接手须知：过期的「session 388 条」→ **4858 条** ✓，「已完成」长段 → 一句话版（含 P2 统一外壳 50/52 + 起服端口/踩坑 ✓）；⑤ `§7` 标题日期误导改正（正文含 10-08/10-09 复核 ✓）。**纪律**：外部按 `§编号` 引用（§1.6/§2/§7/§9/§12/§13/§14.6/§14.9/§15.3…）⇒ **编号一律不动** ✓（改前/改后小节号集合逐一对账 ✓）。**过程翻车（记一条 ✓）**：三段替换**没自底向上**执行 ⇒ `§11.4` 那步打偏、吞掉 `### 13.1` ✗（有 `/tmp` 备份 + 标题集合对账才发现 ✓）。**验证**：小节号集合与备份**完全一致** ✓ · 表格 0 行未闭合 ✓ · 九守卫全绿 ✓（纯文档 ✓ 代码零改动 ⇒ 未跑全量闸门 ✓）。 |
| **B198 P2 第九批 C：knowledge 子批 C（5 控制器）→ knowledge 全完** | converge 六条规则成形 + 贪婪取值/换行两坑 | P2 | 大 | ✅ **完成（2026-10-10）**——**外壳进度 35/52 → 40/52**（pending 12 ✓）。子批 C = `KnowledgeBase` + `Knowledge` + `KnowledgeOperations` + `KnowledgeTag` + `KnowledgeBaseFileProxy`（44 端点 ✓）；**4 处 204** 退役 ✓ + 金片改写 194 ✓；`kb-*` 那 **17 文件**的跨域面实测只炸 **2 个测试** ✓（`W5aSundryRoutes` + `W5cFileProxy` ✓）⇒ 全部由 converge 吃掉 ✓。**新增 `retire204` 子命令** ✓（walk-up 改签名 + 换返回 + 补 import ✓，本批 4 处一次过 ✓ 无需就地展开 ✓）。**converge 这轮长到六条规则 ✓**：① 消息点名金片 ✓ ①b 栈行点名 ✓ ①c **另一种失败格式**（`golden mismatch: X | expected: … | actual: …` ✓）✓ ② 状态双向 ✓ ②b 空体 ✓ ③ JSON 字面量回填 ✓。 |
| **B197 P2 第九批 B：knowledge 子批 B（Faq）** | 修 advice 二进制缺口（真 500）| P2 | 中 | ✅ **完成（2026-10-10）**——**外壳进度 34/52 → 35/52**（pending 17 ✓）。 |
| **B196 P2 第九批 A：knowledge 子批 A（ChunkerPreview + Chunk）** | 新增 converge 失败驱动收敛器 | P2 | 中 | ✅ **完成（2026-10-10）**——**外壳进度 32/52 → 34/52**（pending 18 ✓）。 |
| **B195 P2 第八批：memory（1 控制器）** | 5×204 + 首次"零跨域连带" | P2 | 小 | ✅ **完成（2026-10-10）**——**外壳进度 31/52 → 32/52**（pending 20 ✓）。 |
| **B194 构建提速：parallel + caching + 测试分叉** | 半天全量的时间账 + 两遍验证 | 基建 | ✅ **完成（2026-10-10）**。 |
| **B193 P2 第七批：session（8 控制器，最大一批）** | 失败驱动收敛工具链 + 只改"确证已迁移"的断言 | P2 | 大 | ✅ **完成（2026-10-10）**——**外壳进度 23/52 → 31/52**（pending 21 ✓）。 |
| **B192 P2 第六批：system（2 控制器）+ 逐字面量断言工具链 + 后端起服事故** | 按栈行号回填 + ContractJson.payload + 例外清单第 4 类 | P2 | 中 | ✅ **完成（2026-10-10）**——**外壳进度 21/52 → 23/52**（pending 29 ✓）。 |
| **B191 P2 第五批：im + mcp（5 控制器）+ void 例外与跨测试金片** | 8×204 退役 + 内联形状断言 + 条件下钻 | P2 | 中 | ✅ **完成（2026-10-10）**——**外壳进度 16/52 → 21/52**（pending 31 ✓）。 |
| **B190 P2 第四批：storage + websearch（5 控制器）** | 204 退役 + Go 遗留壳拆壳 + 私有 handler 归一 | P2 | 中 | ✅ **完成（2026-10-10）**——**外壳进度 11/52 → 16/52**（pending 36 ✓）。 |
| **B189 P2 第三批：datasource + model + audit（7 控制器）+ 迁移工具脚本** | `scripts/migrate-domain.py` + 恒抛式错误助手 + 守卫去注释误报 | P2 | 中 | ✅ **完成（2026-10-10）**——**外壳进度 5/52 → 11/52**（pending 41 ✓）。 |
| **B188 多域批量迁移的边界与配方（尝试-回退，主分支保持绿）** | 18 控制器批量尝试 → 定位 5 类阻塞 → 回退 + 配方 | P2 | 中 | ⚠️ **尝试后回退（2026-10-10）**——**做法**：一脚本把 `common/audit/evaluation/model/mcp/memory/datasource/embedchannel/storag… |
| **B187 embed 挂件：用户消息气泡底色跟随后台「外观展示」主题色** | `EmbedUserMessage.vue` 两处 + 契约测试 + 验收清单 | P2 | 极小 | ✅ **完成（2026-10-10）**——**需求**：`/widget-test.html` 里用户消息（`.user_msg`）底色要用后台配置的主题色 ✓。 |
| **B186 修「wiki 搜索一直卡住」+ 给 dev server 陈旧加构建期提醒（B184 同族第三次）** | `dev-stale-check.sh` + 根 build 收尾钩子 | P1 | 小 | ✅ **完成（2026-10-10）**——**症状**：`/platform/chat/<sid>` 里「搜索 wiki」的卡片**一直转圈** ✗（前端等不到工具结果 ✓）。 |
| **B185 P2 按域迁移第二批：favorite + vectorstore（11 端点）** | 2 域外壳迁移 + 自写响应中间件读打标 + 3 处存量缺陷 | P2 | 中 | ✅ **完成（2026-10-10）**——机制与 B183 相同（`@ApiResult` 加在 3 个控制器上 ✓），本批补的是「按域迁移」的**模板与边界** ✓。 |
| **B184 记录并防住 dev server 的「jar 热重写」陷阱（全端点 500 的真因）** | `java-server-up.sh` 警示 + 复现证据 | P2 | 极小 | ✅ **完成（2026-10-10）**——**症状**：点检时浏览器控制台**全端点 500** ✗（`/models` `/agents` `/system/info` `/knowledge-bases` `/se… |
| **B181 把开发环境起起来（点检就绪）+ 修启动链 4 处坑** | `java-server-up.sh` / `dev-env.sh` / `token.sh` / README 凭据段 / Flyway 历史 | P2 | 中 | ✅ **完成（2026-10-10）**——**现状**：三件套容器在跑 ✓（`WeKnora-postgres-dev` :15432 / `redis-dev` :16379 / `docreader-dev` :5… |
| **B180 CI 注解清零：action 版本升到最新大版本** | ci.yml 6 处 `@v4` → 最新（checkout v7 / setup-java v6 / setup-node v7 / setup-gradle v6）| P3 | 极小 | ✅ **完成（2026-10-10）**——**起因**：用户贴来一串 CI 注解 ✗，**全是 GitHub 侧弃用提醒**（Node20→Node24、`setup-java@v4` 弃用、`ubuntu-lates… |
| **B179 钉紧 `EvaluationContractTest.postSuccess` 的竞态断言（CI 第 5 条长尾偶发）** | status 两侧归一 + 二值断言；注解截断 200→800 | P2 | 小 | ✅ **完成（2026-10-10）**——**病根就写在用例自己的注释里** ✓✓：`// status=0（创建快照； |
| **B178 判定并关掉长尾①「controller 包内 25 个非控制器文件」** | 5 个 package-info + 审计脚本自证 | P3 | 小 | ✅ **完成（2026-10-10）—— 结论：非缺陷，不搬** ✓（先核判据，避免一次会做错的搬家 ✗）。 |
| **B177 清 B165 的残渣：两个孤儿局部变量（IDE 先发现）** | 2 处 `golden()` 收敛为单行 return | P3 | 极小 | ✅ **完成（2026-10-10）**——**起因**：用户贴来 IDE 诊断 ✗「The value of the local variable file is not used」（`W5dTerminalEmbed… |
| **B176 修 CI 唯一阻塞：一处搬家后未跟上的 `{@link}`（javadoc 引用漂移）** | 1 行文档修正 | P1 | 小 | ✅ **完成（2026-10-10）**——**病根由 B175 的注解一次给出** ✓✓：`> Task :engine:javadoc FAILED` ✓ + `engine/…/chatpipeline/plugi… |
| **B175 修 CI「任务失败但零失败用例」：fork 自适应 + 构建失败也自证** | `min(4,核数/2)` fork + `tee` 日志 + 注解步 | P1 | 小 | ✅ **完成（2026-10-10）**——**症状**：CI 的 `Build & test` 失败 ✗ 而**测试汇总一条失败用例都没有** ✗（B174 之后的 run ✓）⇒ 失败**不在断言里** ✓；本地全量 `spotlessCheck build`（TZ=UTC）**4858 / 0** ✓ ⇒ 排除编译类原因 ✗ ⇒ 判为 **CI 环境性**：runner 只有 4 核 ✗，而 `4 fork × maxHeapSize 2g = 8g` 与 daemon / PG 容器 / 新装的 Redis 抢内存 ⇒ worker 崩 ✓（其表现正是"任务失败但零失败用例" ✓，且完整日志要鉴权才看得见 ✗）。**修**：① **fork 数按核数自适应** ✓ —— `maxParallelForks = maxOf(1, minOf(4, cores / 2))` ✓：本地 10 核 ⇒ 仍是 **4** ✓（与 B142 的调参完全一致 ✓），CI 4 核 ⇒ **2** ✓；② **构建失败也自证** ✓ —— 新增 `scripts/ci-log-annotate.py` ✓ + CI 用 `set -o pipefail; ./gradlew build 2>&1 | tee /tmp/build.log` ✓ + 失败时把关键行（`* What went wrong:` / `OutOfMemoryError` / `Gradle Test Executor` / `Executio… |
| **B174 修 domains 最后 1 条（节流竞态）+ 跳过原因可读性** | 等待循环内重试 + `assumeTrue` 带原因 | P1 | 小 | ✅ **完成（2026-10-10）**——**① 竞态（CI 最后 1 条 ✗）**：`TenantAPIKeyServiceTest.lastUsedWriteFailureClearsThrottle` ✓； |
| **B173 修 CI 的 domains 10 条：时区写死 + DuckDB 扩展自足** | Test 钉 `user.timezone` + 测试自装扩展 | P1 | 小 | ✅ **完成（2026-10-10）**——**两条均由 B171 的注解读出** ✓✓（第二次立刻回本 ✓）。 |
| **B1 契约文档同步** | `docs/knowledge-api-contract-v1.md` v1.0→v1.1：错误体、裸信封/裸数组、游标分页、恒输出、204 语义、七域差异表 | P0 | 小 | ✅ |
| **B2 金片对比器统一** | `support/GoldenContract` 共享基建（deep 归一 + strip + 单一 refresh 开关）；字节级（`goldenBytes`/裸 compare）与语义级双轨并存 → 语义单轨，存量字节级测试逐个迁移 | P0 | 中 | ✅ |
| **B3 `@JsonInclude` 恒输出化** | 真面 68 处（19 文件：wiki domain 全家 + websearch 三 DTO + VectorStoreTypes）；冻结面豁免（tenantconfig/LLM 载荷/event/tracing/common/agent/stream + connector + lf_*）；每域重录夹具 + 前端键集合核对 | P1 | 中 | ✅（**B3b 已登记并已执行**——见下行） |
| **B3b KB 配置 jsonb 键名统一（camelCase）** | 由 B0 走查升格为真实缺陷：`knowledge_bases` 的 `*_config` 列三方咬合面（前端 payload / 服务端读取器 / 落库 jsonb）键名分裂，导致界面上的 wiki 合成模型、问题生成参数、索引开关被静默忽略。服务端读取器 + 更新路径 dispatch 键 + 前端 payload/读取/类型 + V2 存量迁移 + 列默认值（连带修掉「编辑弹窗恒打不开」的裸资源读取） | **P1** | 中 | ✅ **完成（2026-10-02）**——详见 15.1.1 |
| **B4 Go 零值时间哨兵 → null** | `0001-01-01T00:00:00Z`（AgentStep / agentm GO_ZERO_TIME / init goTime 系） | P1 | 小-中 | ✅ **结论：不改**（调查后判已知例外，见 15.1.1） |
| **B5 lf_* 载具嵌套化** | 四域队列载荷的 `lf_*` 平铺键 → 嵌套 `tracing` 键 | P1 | 判定小 | ✅ **完成（2026-10-02）——判定：做**（载荷只在进程内队列流动、无外部消费面）； |
| **B6 getenv 收敛 151 处** | 裸 `System.getenv()` → `@ConfigurationProperties`，按域分批 | P1 | 中 | ✅ **完成（2026-10-02，批 1~10）**：storage 装配 / langfuse / 检索驱动 / 系统部署面（含 `GIN_MODE`→`WEKNORA_DEPLOYMENT_MODE`）/ 知识域单… |
| **B7 死成员清扫** | 只注入不读取依赖（依赖级口径）+ 死 logger/`ObjectMapper`/`Pattern`/私有方法/冗余 import | P1 | 小-中 | ✅ |
| **B8 注解形态收尾** | 全限定名注解 → import 短名；`@JsonIgnoreProperties` 44 处接工厂评估 | P2 | 小 | ✅（FQ 177→0； |
| **B9 Go 锚点注释清洗** | ~6,000 处；按 §4 既定"随触碰清洗"继续；若专项则按域分批 | P2 | 大（专项）/零（随批） | ✅ **机制已落地（2026-10-02）**——政策不变（随触碰、先摘不变量再删锚点、不立专项）； |
| **B10 ArchUnit 边界规则进 CI** | 环 0 组基线 + 包依赖白名单固化（§5 阶段 4） | P2 | 中 | ✅ **完成（2026-10-02）**——包级部分已于 2026-09-30 在 CI（脚本棘轮 guards job）； |
| **B11 Gradle 多模块** | 按域拆模块（§5 阶段 4 尾） | P2 | 大 | ✅ **判定：不做（2026-10-02 搁置）**——立项目的（边界固化）已由 B10 + 包级棘轮达成； |
| **B12 B0 残留批 1** | wiki 任务级死信释放槽位（②）+ 孤儿 op 启动重放（③）+ 裸 NUL 审计盲区（R5） | P1 | 小 | ✅ **完成（2026-10-02）**——详见 15.1.1 |
| **B13 B0 残留批 2** | 孤儿存储组件判定与删除（④）+ `/auth/config` 版本信号消除登录 403 噪音（⑤）+ `process_overrides` 移植缺口判定 | P2 | 小 | ✅ **完成（2026-10-02）**——详见 15.1.1 |
| **B14 存储读侧投影合并** | 引擎面 env 回落行 vs 落库面类型化记录（两套词汇） | P2 | 中 | ✅ **完成（2026-10-02）**——合并为「一面一源」（落库面 camel、引擎面由唯一次名器派生）+ 修掉两个同源静默 bug（① 非 minio 行凭据被丢； |
| **B15 供给行明文落库** | 供给器绕过加密直写 jsonb | P2 | 小 | ✅ **完成（2026-10-02）**——抽出唯一读写口 `StorageConfigCodec`（存储服务与供给器共用），真机 A/B 证明凭据由明文转为 `enc:v1:`； |
| **B170 CI `:boot: 2 failed / 4 skipped` 的排查（B169 后首次 CI 全量）** | CI 装 redis-server + 钉紧一条偶发轮询 | P1 | 小 | 🟡 **部分完成（2026-10-10）**——**症状**：CI backend job 的 `:boot:test` = 1312 / **2 failed** / **4 skipped** ✗（本地 1312/0… |
| **B171 让 CI 失败自证：测试汇总步（失败用例名 + 跳过原因 + 注解）** | `scripts/test-summary.py` + ci.yml `always()` 步 | P1 | 小 | ✅ **完成（2026-10-10）**——**起因**：排查 CI 红时**两次都缺关键信息** ✗（贴来的日志只有尾巴 ✓； |
| **B172 修 CI 的 2 个失败：纳秒时钟 vs 微秒列的精确回环（移植性缺陷）** | 2 处输入 `.truncatedTo(MICROS)` | P1 | 小 | ✅ **完成（2026-10-10）**——**病例名由 B171 的注解机制直接取得** ✓✓（投入立刻回本 ✓）：`MemoryRepositoryTest.createItemKeepsCallerSupplied… |
| **B183 统一响应外壳：`{code,message,data}` 一套外壳 + 一个解包点（P0+P1+agent 试点）** | 外壳/注解/advice/错误分派/前端解包点/第 9 条守卫 | P1 | 大 | ✅ **完成（2026-10-10）**——**决策**：脱离 Go 后不再以 Go 线格式为准 ✓，改按 Java/Spring 通行做法：**一套外壳 + 一个解包点**（约定文档 `docs/api-respons… |
| **B169 给守卫加"冻结覆盖快照"（换包打断冻结 ⇒ 同一提交内抓住）** | `check-json-key-case.py` +`freeze_coverage`/+`--write`；新快照 149 文件 | P2 | 小 | ✅ **完成（2026-10-10）**——B168 的坑（**换包使前缀冻结静默失效** ✗，只在 CI 才暴露 ✓）的**机制性预防** ✓。 |
| **B168 修 CI guards 红 + 堵住我自己的验证流程漏洞（本地守卫少带 `--strict`）** | 冻结前缀修复 + 八守卫单一入口 | P1 | 小 | ✅ **完成（2026-10-10，`cda99767`）**——**症状**：CI 的 guards job 红 ✗（6 文件 / 45 键：`common/llm/TokenUsage` 与 `tracing/dec… |
| **B167 P3b-3：`server/` → `domains/` 模块更名** | 目录 + settings + boot 引用 + 2 处中央模块表 + 5 脚本默认路径 + 194 处文档 | P2 | 中 | ✅ **完成（2026-10-10，`wip/p3b-2-boot` 分支）**——**理由**：拆分后 `:server` 名字失真（它装业务域 ✓，组合根已独立为 `:boot` ✓）⇒ 四模块名各安其位：**boo… |
| **B166 修 main 存量红：`KnowledgeFinalizeAdapterTest` 6 例** | 测试自预热 MP lambda 缓存 | P1 | 小 | ✅ **完成（2026-10-10）**——**症状**：6 例红 ✗（3 例 `Wanted but not invoked: knowledgeMapper.update(isNull(), <any>)` （"ze… |
| **B165 P3b-2：`:boot` 拆分跑通（组合根独立 + 集成测试归位）** | boot 模块 + config/入口/资源迁入 + 87+6 集成测试迁入 + 3 个卡点修复 | P1 | 大 | ✅ **完成（2026-10-10，`wip/p3b-2-boot` 分支）**——从 1192 失败一路收到 **0**（`boot` 1312 / `engine` 547 / `common` 127 全绿）。 |
| **B16 静默失效定向扫描** | 枚举「静默丢数据」机制点并逐对核写读词汇 | P1 | 中 | ✅ **完成（2026-10-02）**——未发现新缺陷（负面结果如实记录）； |
| **B17 换锚收尾** | 残余逐字段 `@JsonProperty`（我方 ≈57）按「零风险 / 动形状 / 冻结」判定后分批清 | P2 | 小~中 | ✅ **完成（2026-10-02）**——判定批（三类 + 承重注解判据）+ 安全子集 6 处 + **websearch 请求键 camel 收口**（真机 A/B 实证）； |
| **B18 agent 配置面换锚** | agent 配置键（~60 个）+ 前端同批 | P1 | 大 | ✅ **完成**——键面清单系统性低估纠缠度（前端命中 42 文件 / 850 处）； |
| **B19 前端契约键收口 + 守卫** | 前端 snake 契约键排查 + 嵌套层 + 双侧守卫进 CI | P2 | 中 | ✅ **完成（2026-10-02）**——修掉两处静默缺陷（B18 的 ModelService 回归、前端 tagScopes 链路断）+ 嵌套 7 键 camel + V3 扩到 66 键（并修 WHERE 漏行）… |
| **B203 约定加固：工厂命名定论 + `fail` 泛型对称** | `ApiResponse.fail(int,String)` → `ApiResponse<Void>` | P2 | 小 | ✅ **完成（2026-10-10）**——用户问"改成 `success` / `error` 会不会更好" ⇒ 评估后**定论保持 `ok` / `fail`** ✓（理由写进 `docs/api-response-… |
| **B202 P2 第十二批（收尾）：wiki（1 控制器 / 21 端点）** | 私有 handler 收口 + 字节级断言下钻 | P2 | 小 | ✅ **完成（2026-10-10）**——**外壳进度 49/52 → 50/52**（pending **2** = `ImCallbackController` + `HealthController`，两者都是*… |
| **B201 P2 第十一批：auth（6 控制器 / 47 端点 / 264 金片 / 7×204）** | 最后一域 + FE 观察名单落地 | P2 | 大 | ✅ **完成（2026-10-10）**——**外壳进度 43/52 → 49/52**（pending **3** = `wiki` + 两个已知例外 ✓）。 |
| **B200 P2 第十批：misc 4 域（health / embedchannel / evaluation / initialization）** | /health 判为例外 + converge 假绿修复 | P2 | 中 | ✅ **完成（2026-10-10）**——**外壳进度 40/52 → 43/52**（pending 9 ✓）。 |
| **B20 占位符令牌回归** | 批量改名误伤数据值（模板令牌）+ 缺跨面对照守卫 | P2 | 小 | ✅ **完成（2026-10-02）**——修回 `knowledge_bases` 令牌（夹具曾被同步改掉=假绿），新增「HTTP 面 ↔ 渲染面令牌一致」守卫（含红态证明）； |
| **B21 占位符组键收口 + 同类排查** | 只改一半的「混搭面」与被误伤的数据值 | P2 | 小 | ✅ **完成（2026-10-02）**——3 组键收口 camel（模板面有意不动）； |
| **B22 模板面 4 键 + api_key 收口** | 混搭面续清（只读派生视图，无写回副作用） | P2 | 小 | ✅ **完成（2026-10-02）**——模板面 4 键与 `api_key` 改 camel（含 FE 映射/读者/夹具/测试侧归一化），删死模块 `api/web-search.ts`； |
| **B23 孤儿夹具审计** | 夹具「真被引用」判定（行为式，不按名猜） | P3 | 小 | ✅ **工具 + 首审完成（2026-10-02）**——1344 个夹具中 115 个为孤儿（≈8.6%，聚类见 15.1.1）； |
| **B24 换锚长尾回头扫** | 注解面清零；新增 payload 键面盘点与棘轮 | P3 | 中 | ✅ **完成（2026-10-02）**——盘点 + 逐族判定收口（B25~B29）：注解真债 0； |
| **B25 批甲（首面）** | 模板标志位收口 + 引用/进度载荷判冻结 | P3 | 小 | ✅ **完成（2026-10-02）**——`hasKnowledgeBase`/`hasWebSearch` 收口（YAML 输入面未动）； |
| **B26 批甲续（websearch 凭据面）** | 逐面看消费者链 | P2 | 中 | ✅ 🐞 **修掉真 bug 并收官（2026-10-02）**——凭据面三处键名错位（保存静默失效 / 删除 400 / 徽标恒「未配置」），金鹰记录的缺陷态一并纠正； |
| **B27 批甲续 2** | 推荐问题键收口 + 6 类面判冻结 | P3 | 小 | ✅ **完成（2026-10-02）**——`knowledgeBaseId` 收口； |
| **B28 批甲续 3** | websearch provider-types 字段面收口 | P3 | 小 | ✅ **完成（2026-10-02）**——`labelKey`/`descriptionKey` 收口（只改键、不动 i18n 值）； |
| **B29 批甲/批乙收口** | 换锚待判清单清空 | P3 | 小 | ✅ **完成（2026-10-02）**——66→0：按族判定（MyBatis 列名/观测面/引擎 DSL/存量配置 jsonb/模板令牌），model 凭据面登记为例外待拍板； |
| **B30 凭据面统一** | model ↔ MCP/websearch 统一 camel | P3 | 小 | ✅ **完成（2026-10-02）**——只动 API 层（存量存储层与第三方载荷未动）； |
| **B31 B23 处置** | 孤儿夹具该不该删 | P3 | 小 | ✅ ⚠️ **结论修正并收官（2026-10-02）**——115 个里 102 个是录制脚本清单（证据链）； |
| **B33 包结构守卫修复** | B6 批 10 引入的 5 组环（工具放错层）归位到底层 | P2 | 小 | ✅ **完成（2026-10-02）**——`AppEnvLookup` → `common/deployment`、`StorageRuntimeEnv` → `common/storage`，`ImageResolv… |
| **B34 agent/tools 分包** | 94 文件单层 → 根（框架/共享/通用）+ 5 能力子包（wiki/knowledge/sql/data/web） | P2 | 中 | ✅ **完成（2026-10-02）**——MCP 族因与 `ToolRegistry` 同包紧耦合（含 protected 互访，实测约 40 处）暂留根并登记； |
| **B53 知识面卡片模型键收口** | 内部视图模型键 snake→camel + 失败原因接上 `errorMessage` + 防回流守卫 | P2 | 小 | ✅ **完成（2026-10-04）**——点检触发：三键定性后用户拍板「收口 + 接线」。 |
| **B54 api 面 snake 键收口** | 12 处真断链修复（改密/邀请/auth 时间/wiki/KB 复制）+ 裁撤死参数收尾 + 守卫扩面棘轮 | P1 | 中 | ✅ **完成（2026-10-04）**——由 B53「内部键收口」追问扩展到 api 线格式面。 |
| **B55 api 面 snake 记号逐条核实** | B54 挂起的 32 键全定性：13 改 camel / 6 删死字段 / 13 已核实合法 | P1 | 中 | ✅ **完成（2026-10-04）**——逐键查「后端 DTO 字段名 / 显式 `put` 的键 / `JsonNode.path` 读取 / 接口实测」。 |
| **B56 技能区文案/死键/宿主技能目录** | 技能区文案改指令型口径 + 删 19 死键 ×5 语言 + dev 宿主技能目录 | P3 | 小 | ✅ **完成（2026-10-04）**——点检追问「技能管理为何没了」查实＝2026-09-28 定稿的功能裁剪第一批（PR3 `caef9d5`：移除沙箱 + 技能降级为指令型，删 30 前端文件）。 |
| **B57 技能管理回归（入库）** | 指令型技能入库 + 平台级 CRUD（复用原 catalog 路径）+ 宿主目录退役 | P1 | 大 | ✅ **完成（2026-10-04）**——点检追问「技能管理为何没了」→ 方案三稿收敛（v2「复用原接口」→ v3「入库」），用户拍板：平台级 SystemAdmin / camel / 只在线填写 / 硬拒+forc… |
| **B58 旧信封读法清剿** | Go `{success,data}` 残留 → Java 裸载荷（8 处消费点 / 6 文件） | P1 | 小 | ✅ **完成（2026-10-04）**——触发：点检 `?section=integration-api` 报「加载 API 集成设置失败」。 |
| **B59 技能编辑** | 「查看内容」→「编辑」：PUT 更新 + 改名保护 + 编辑弹窗 | P1 | 中 | ✅ **完成（2026-10-04）**——用户要求编辑弹窗与新建一致。 |
| **B60 技能租户化** | 平台级 → 空间级（含平台内置只读层）+ 菜单并入「数据与扩展」 | P1 | 中 | ✅ **完成（2026-10-04）**——触发：用户提「技能配租户配置 + 菜单移到数据与拓展」，核实为**修隔离缺陷**（选择器读全表 → 任何成员可见别家技能； |
| **B61 技能按需读取 + 点名注入** | `read_file` 的 `skill://` 支持（A）+ @点名注入正文（B） | P1 | 中 | ✅ **完成（2026-10-04）**——用户追问「先注入名称描述、按需加载正文？」→ 核查发现**只有 Level 1 通**：`skill://` 无解析器、`read_file` 是沙箱绑定工具（无实现类）、`M… |
| **B62 WeKnora Cloud 整功能裁撤** | 设置页 + 提供商 + 解析引擎 + VLM/embedding/rerank 适配器 | P1 | 大 | ✅ **完成（2026-10-04）**——用户要求「去掉 WeKnora Cloud 设置」； |
| **B63 embed 语言跟随失效** | 派生语言被写进 localStorage → 覆盖「跟随浏览器/宿主」 | P1 | 小 | ✅ **完成（2026-10-04）**——点检 `widget-test.html` 报「默认语言设跟随但没生效」。 |
| **B64 「跟随宿主」补上页面语言** | widget 转发宿主页 `<html lang>`（弱信号 `?hostLocale=`） | P1 | 小 | ✅ **完成（2026-10-04）**——用户追问「页面浏览器语言是 zh-CN 吗、为什么跟随还是英文」。 |
| **B65 嵌入渠道保存后密钥丢失** | `publishToken` 被 `load()` 的列表行冲掉 → 嵌入代码退化成"加载密钥失败" | P1 | 小 | ✅ **完成（2026-10-04）**——用户报「保存后嵌入代码变成 `<!-- 加载渠道密钥失败 -->`」。 |
| **B66 Agent 编辑器变量全空** | `placeholders`/`type-presets` 裸载荷漏 api 层适配 | P1 | 小 | ✅ **完成（2026-10-04）**——用户问「点击插入 / 输入 `{{` 唤起列表」为何不工作。 |
| **B67 KB 解析设置整页崩** | 解析规则读 snake 而 KB 配置面是 camel（B3b 漏改读侧） | P1 | 中 | ✅ **完成（2026-10-04）**——用户贴 `getEngineForGroup` 的 `undefined.some` 报错。 |
| **B68 智能体推荐问题恒空** | 读取侧查 snake `generated_questions`，写入侧/库是 camel | P1 | 小 | ✅ **完成（2026-10-04）**——用户问「GACI 库有没有自动生成问题」（答：**有**，enabled + 23/24 chunk 各 4 个），顺带查出：推荐问题的读取侧（`listRecentDocum… |
| **B69 持久层治理（架构师四条落地）** | 全表改删防护拦截器 + R6-R9 棘轮 + 分页拼接归零 + 字符串 wrapper Lambda 化 + knowledge 域 @Lazy 解环 + 租户过滤评估 | P1 | 大 | ✅ **完成（2026-10-05）**——架构师四条意见逐条转为机器强制面。 |
| **B70 M3 落地：方言探测归一 + 租户过滤缺失探测（alert）** | detectPostgres 八处归一 + TenantFilterGuard 拦截器 + 首次盘面 | P2 | 中 | ✅ **完成（2026-10-05）**——按 B69 评估报告的路线落地 Step1/2。 |
| **B71 租户探测切 enforce（逐表定性收口）** | 72 条语句五族归入白名单 + 默认档 enforce | P1 | 小 | ✅ **完成（2026-10-05）**——B70 盘面的 72 条去探针语句全部定性：**认证面 7**（hash/bot 身份/邮箱定位，请求期无租户上下文）/ **调度面 4**（IM 投递、数据源同步轮询全表=设… |
| **B72 前端契约键失配收口（snake 全量排查·修复批）** | 9 条「前端读/写 snake、后端发 camel」DRIFT 修复 + created_at 死读删除 + 预览串对齐 + 守卫收口 | **P0** | 中 | ✅ **完成（2026-10-05）**——全量排查 2,979 处/699 词/182 文件定谳：95%+ 是冻结契约面不动（SSE 载荷显式 @JsonProperty、工具名、设置 KV、连接器凭据、embed 协… |
| **B73 前端死键清理（snake 全量排查·清理批）** | 前后端都不认的 snake 记号逐个验证后删除 | P2 | 小 | ✅ **完成（2026-10-05）**——creatChat 的 agent_config 整块（CreateSessionRequest 只收 title/description）、doc-content 的 get… |
| **B74 守卫基线精确化 + 前端 Go 锚点注释换锚（snake 排查·收尾批）** | python 基线 41 条全量去「B19 整包话术」+ 16 文件 Go 文件名注释换 Java 锚点 | P3 | 小 | ✅ **完成（2026-10-05）**——① python 基线 41 条理由逐条核实重写（六类：守卫/测试自引用、注释引用、BEM CSS、i18n 值、上传覆盖冻结面〔B13 缺口〕、死参数链），**通用话术归零*… |
| **B75 @JsonPropertyOrder 遗产摘除（snake 排查·注解卫生批）** | 66 文件键序注解盘点：60 文件摘除 861 行，7 承重者保留+原地理由 | P2 | 小-中 | ✅ **完成（2026-10-05）**——字节稳定依赖全量盘点定谳：①主代码 8 处摘要/签名输入全是纯字符串/文件字节（MCP 指纹是手写 canonicalJson 键序自带，不过 Jackson）； |
| **B76 方法级 Go 名收口（去名 + 死代码 + 常量/局部名）** | 31 个 `go*` 方法名全去 + 死代码 2 处 + 常量/内部类/局部名去 Go | P2 | 中 | ✅ **完成（2026-10-07）**——档 3（2026-10-03）只裁类级（`Go*` 类 25 → 0），方法级 31 名 / ~150 引用从未裁决。 |
| **B77 方法级 Go 复刻换实现（保输出）** | `Registry.goQuote`→`ToolJson.quoted`；`ParamCaster` 手写指数展开→BigDecimal | P2 | 小 | ✅ **完成（2026-10-07）**——① `Registry.goQuote` 删除，改标准 `ToolJson.quoted`：实录 `routing_text`（`GoRecording46A`，测试**逐字比… |
| **B78 压缩/观测渲染换 Java 标准** | 手写 JSON writer 退役 → `ToolJson.write`；`ObservePhase.goMarshal`→`argsJson` | P2 | 小 | ✅ **完成（2026-10-07）**——① `ConversationSerializer` 删手写 `goMarshal`/`goMarshalInto`/`goEscapeString`（HTML 转义/U+20… |
| **B79 Notion 数字换 Java 标准** | `goFormatG`/`shortestRoundTrip` 删除 → `Double.toString` | P2 | 小 | ✅ **完成（2026-10-07）**——手写 `%g`（~80 行：指数至少两位/最短往返压缩）退役； |
| **B80 契约文案换锚·第一部分（类型名）** | `jsonTypeLabel` 词表 → JSON 类型名（null/boolean/number/array/object）+ 补钉子 | P2 | 小 | ✅ **完成（2026-10-08，余项并入 B81/B82/B83）**——只做无金片面：`SystemSettingRegistry.jsonTypeLabel` 换 Java/JSON 标准词表（`<nil>→nu… |
| **B81 盲区孪生文案换 Java 标准 + 补钉** | wiki/mcp/ollama 三处 `non-object into Go value` → `expected JSON object, got <类型名>` + 三处新钉子 | P3 | 小 | ✅ **完成（2026-10-07）**——① 新增共享 `ToolJson.nodeTypeLabel`（Jackson 节点类型小写：null/boolean/number/string/array/object），… |
| **B82 gin 校验文案换锚** | 17 文件生成点收敛到 `RequestFields` + 32 金片重锚（8 域） | P2 | 中 | ✅ **完成（2026-10-07）**——旧文案 `Key: '<Struct>.<Field>' Error:Field validation for '…' failed on the '…' tag`（gin 味… |
| **B83 auth 类型错 + ASR/VLM 错误文本换锚** | `json: cannot unmarshal …` 族退役 → 字段级文案；ASR 错误文案 → `HTTP <状态行>: <详情>` | P2 | 中 | ✅ **完成（2026-10-07）**——① auth：`TenantBindSupport.stringFieldTypeError`（原 `stringFieldValue`）、`TenantCrudOps` 非对… |
| **B84 最后三处盲区文案换锚 + 补钉** | web 工具 5 处 / 技能 frontmatter 2 处 / Notion 4 处 → Java 标准文案 | P3 | 中 | ✅ **完成（2026-10-08）**——① web 工具面（LLM 可见）：`WebSearchTool` 五处类型错收敛到 `RequestFields.wrongType`（query/count/country… |
| **B85 escapeHtml 四副本收敛 + 测试侧 go* 局部名清理** | 五字符 HTML 转义单一实现 `common/web/HtmlText`；7 个测试文件局部名去 Go | P3 | 小 | ✅ **完成（2026-10-08）**——① 盘点出 **4 份同表实现**（`MessageSanitizer`/`common/prompt/MessageAttachmentsPrompt`/`memory/do… |
| **B86 structName 死形参清理** | 8 个 helper 去 `structName`（~31 调用点）+ AgentController 判别改布尔 | P3 | 小 | ✅ **完成（2026-10-08）**——B82 换锚后 `structName` 只服务被删掉的旧前缀，全部成死参。 |
| **B88 工具面 schema 按 Java 标准 camel 化** | 142 键（30 输入 + 107 输出 + 5 字符串拼接键）跨后端/实录/前端全量换锚 | P1 | 大 | ✅ **完成（2026-10-08）**——用户拍板「只动键、整体一批」。 |
| **B89 模型输出契约 XML 面** | 自有序列化标记（工具输出 + runtime_context）的属性/元素名 → camel | P2 | 中 | ✅ **完成（2026-10-08）**——B88 的延续：JSON 键之外，「我们自己的 XML 形态字段名」也换 camel。 |
| **B90 自有标记的多词标签名 camel 化** | 工具输出/提示词标记里的 snake 标签名（33 个）→ camel | P2 | 中 | ✅ **完成（2026-10-08）**——B89 只覆盖「与 JSON 键同名的属性/元素」，多词 snake **标签名**（`<wiki_page>`/`<links_to>`/`<linked_from>`/`<… |
| **B92 双读清除 + 冻结清单按理由重排** | 用户确认「不再考虑历史数据」后的兼容分支清算 | P2 | 中 | ✅ **完成（2026-10-08）**——① **双读全清**：前端 `wikiToolReferences`（`firstTag` 兜底/`wiki_page` 分支）、`mcpToolDisplay`（`legac… |
| **B91 阶段 4 起步（C1 破环 + 架构规则）** | `StreamProperties` 搬家解 SCC-B + 守卫补间接环棘轮 + ArchUnit R10/R11 | P2 | 小 | ✅ **完成（2026-10-08）**——① **C1**：`config/StreamProperties` → `com.ragagent.stream`（`@ConfigurationPropertiesScan… |
| **B93a wiki 图片标记 camel 化** | `<image_caption>`/`<image_ocr>`/`<image_original>` → camel（标记名，值不动） | P3 | 小 | ✅ **完成（2026-10-08）**——B93 三面中的第一面（§15.3「已解除」表）。 |
| **B35 modelcontext 并入 agent** | 顶层包 31 → 30（用户 2026-10-02 拍板） | P2 | 小 | ✅ **完成（2026-10-02）**——13 文件 → `agent/modelcontext/`（test 3 同移），23 文件改包路径零残留； |
| **B93b 事件面 camel 化（SSE/Redis 流）** | 载荷类删冗余注解 + SSE 事件体/信封键 + 事件名与响应类型值 + 前端事件消费 + 实录 | P2 | 大 | ✅ **完成（2026-10-08）**——§15.3「已解除」第二面，全仓一并（后端 + 前端 + 实录 + golden）。 |
| **B93b-2 路径/查询参数名 camel 化** | URL 路径变量 + 自有查询参数名 + 防回流守卫 | P3 | 小 | ✅ **完成（2026-10-08）**——决策点 2。 |
| **B93b-3 事件/流收尾 + 面登记** | `EventMiddleware` 事件元数据键、`LiveRunPayload`（Redis live-run 标记）、换锚守卫冻结节流清理 | P3 | 小 | ✅ **完成（2026-10-08）**——① **摘掉换锚守卫的过期冻结节流**（`event/`、`stream/` 已 camel 化）后立即暴露 3 处真残留：`event/EventMiddleware` 的事… |
| **B93c 落库 jsonb 键收尾** | jsonb 列内层键（表列名与 SQL 不动） | P3 | 小 | ✅ **完成（2026-10-08）**——侦察后发现**该面的主体早已完成**：`V2__kb_config_keys_camel`（KB 配置 45 条映射）与 `V3__agent_config_keys_came… |
| **B94 阶段 4 解环收口（C8）** | `WikiActivityAudit` 端口搬入中性包 ⇒ SCC-A 瓦解、包图成 DAG | P1 | 小 | ✅ **完成（2026-10-08）**——**阶段 4 的前置条件达成**。 |
| **B36 agentm 并入 agent** | 顶层包 30 → 29（用户 2026-10-03 拍板） | P2 | 小 | ✅ **完成（2026-10-03）**——`agentm`（20 文件）→ `agent/management/`（先例 `auth/apikey/`），含资源目录改名 + 61 文件包路径 + 10 处 loader… |
| **B95 阶段 4 端口化（C6+C7）** | `retrieval → auth` / `model → auth` 清零（中性只读端口 `TenantConfigLookup`） | P2 | 小 | ✅ **完成（2026-10-08）**——① **端口**：新增 `common/tenant/TenantConfigLookup`（`retrieverEngines`/`retrievalConfig`/`mem… |
| **B96 共享词汇下沉（C4/C5 前置）** | 租户配置视图 + API Key 作用域词汇搬入 `common`（auth 不再被跨域引用） | P2 | 中 | ✅ **完成（2026-10-08）**——C4/C5 的**共同前置**：把"跨域共享词汇"按 B94 同法搬进中性包，auth 只留实现与端点。 |
| **B97a C4/C5 收口（可转换子集）** | 端口补 `storageView` + 两个纯配置读取点换端口 | P3 | 小 | ✅ **完成（2026-10-08）**——① **端口扩展**：`TenantConfigLookup` 增 `TenantStorageView storageView(long)`（`tenantId`/`defa… |
| **B97b C4/C5 归零（实体下沉 + 端口补全）** | `Tenant` 实体/mapper/类型处理器搬入 `common.tenant` + 端口补 `tenantById` + `UserNameLookup` | P1 | 中 | ✅ **完成（2026-10-08）**——按**路 B**（实体下沉，用户拍板）。 |
| **B98 C2-a：wiki→knowledge 只读门面** | `common.knowledge.KnowledgeBaseLookup` 端口 + 3 处换端口（43 → 36） | P2 | 中 | ✅ **完成（2026-10-08）**——① **端口**：`common/knowledge/KnowledgeBaseLookup`，四个只读方法（`kbById` 软删过滤 / `kbByIdIncludingD… |
| **B99 C2-b 侦察 + KB 读再收口** | 17 文件方法级需求分组 + `WikiPageServiceImpl` 换端口（36 → 34） | P2 | 中 | ✅ **完成（2026-10-08）**——① **方法级侦察**（`/tmp` 脚本产出，结论已入方案文档）：把剩余 36 处按**能力**分成 7 组（KB 读 ✅ / Knowledge 读 / Knowledge… |
| **B100 C2 归零：wiki→knowledge 完全端口化** | 7 个 common 类型 + 4 个适配器 + wiki 全量换端口（43 → 0） | P2 | 大 | ✅ **完成（2026-10-08）**——① **端口/视图（`common/knowledge`）**：`ChunkPort`（textChunks / chunksByIds / deleteChunk / enr… |
| **B101 common 纪律：R5/R6 守卫 + 载荷收窄** | 两条新守卫（含红态探针）+ `ChunkView` 12 → 6 字段 | P2 | 小 | ✅ **完成（2026-10-08）**——① **R5 底座不得依赖业务域**（绝对禁止）：`common`/`event`/`stream`/`tracing` 不得 import 任何 L3 域（实测 **0 条*… |
| **B102 common 减重：approval 域归位** | `common/approval` 1,758 行 / 24 文件 → 顶层 `com.ragagent.approval` | P2 | 中 | ✅ **完成（2026-10-08）**——① **判定依据**（先量后动）：`approval` 出向依赖 **只有 common**（零业务域 ⇒ 独立成域不引入环）； |
| **B103 common 减重②：settings 归位 + Memory 词汇判定** | `common/settings` → 顶层 `settings` 域；`Memory*` 经守卫抓环后回退 `common/memory` | P2 | 中 | ✅ **完成（2026-10-08）**——① **`settings` 归位**：`ConversationProperties`/`SystemSettingGateway`/`SystemSettingRegist… |
| **B104 common 减重③：tenant 拆分 + R6 第三条** | `common/tenant` 拆分（实体/mapper 归域）+ R6 增"common 不得自带 mapper 子包" | P2 | 中 | ✅ **完成（2026-10-08）**——① **先量后动**：`TenantRole` 被 **13 个包**消费（含 `common/web` 自身）⇒ 必须留 common； |
| **B105 搬家副作用修复 + R7 守卫** | 补搬 2 个测试目录（声明↔路径一致）+ 新增 R7 守卫 | P2 | 小 | ✅ **完成（2026-10-08）**——① **问题**：B102/B103 的全仓改名把<b>测试文件里的 `package` 声明</b>也改了（`com.ragagent.approval` / `com.ra… |
| **B106 L2→L3 清零①：websearch + memory（5 → 3 条）** | 端口收 L1 配置 + 记忆词汇下沉 + 通用去重下沉 | P2 | 中 | ✅ **完成（2026-10-08）**——① **websearch（1 处）**：`PipelinePorts.WebSearch.search` 改收 `common.tenant.WebSearchConfig`… |
| **B107 L2→L3 清零②：retrieval → vectorstore（3 → 2 条）** | 值对象下沉 L1 + 实体/mapper 端口化 | P2 | 中 | ✅ **完成（2026-10-08）**——① **性质先判**：`vectorstore` 含 `domain`(7)/`mapper`/`controller`/`service` ⇒ 是**业务域**（驱动在 `r… |
| **B108 L2→L3 清零③：chatpipeline→agent 词汇归位（15 → 4 处）** | modelcontext 子系统搬 L2 + SearchTarget/PromptConstants 落 L1 | P2 | 中 | ✅ **完成（2026-10-08）**——① **`agent.modelcontext.**`（13 文件子系统：Registry/StreamDecoder/SourceRegistry/HandleStore/…… |
| **B109 L2→L3 清零④：chatpipeline→agent 归零（4 → 0）** | Fetcher 集群搬 L2 webfetch + DataAnalysis 端口化 | P2 | 中 | ✅ **完成（2026-10-08）**——① **`webfetch` 落地**：`Fetcher`/`FetchException`/`BrowserRenderer`/`AgentMarkdown`（4 文件，实测… |
| **B110 L2→L3 清零⑤：载荷+算法下沉（17 → 12 处）+ R3b 处数棘轮** | 3 载荷 → common.knowledge；SearchChunkMerge → common.retrieval | P2 | 中 | ✅ **完成（2026-10-08）**——① **元数据载荷下沉**：`FaqChunkMetadata`(291 行)/`DocumentChunkMetadata`(40)/`GeneratedQuestion`(… |
| **B111 内联全限定名清算 + R8 守卫（依赖图完整性）** | 1,331 行内联 FQ → import（保留 49 处必要消歧）；R8 守卫上线 | P2 | 大 | ✅ **完成（2026-10-08）**——① **发现**：`check-package-cycles.py` 的 R1/R1b/R3/R3b 只解析 `import` 行 ⇒ 代码里写成 `com.ragagent.… |
| **B112 解环收官：包图成为 DAG** | 最小反馈边集 3 处（`auth→agent` 2 + `agent→im` 1）⇒ R1 2→0 组、R1b 1→0 组 | P2 | 小 | ✅ **完成（2026-10-08）**——① **方法论（本批最大收获）**：环的体积要看**割**不看**成员数**——8 域间接环看着比 SCC-A（7 域）大，但按"删边最少"暴力枚举拓扑序求最小反馈边集，只有… |
| **B113 L2→L3 清零⑥：webfetch → datasource（2 → 0）** | 四个零域依赖的 HTML 工具下沉 `common/web` | P2 | 小 | ✅ **完成（2026-10-08）**——① **性质判定（先量后动）**：`HtmlToMarkdown`（接缝，32 行）/`JdkHtmlToMarkdown`（有界实现，544 行）/`HtmlConversi… |
| **B114 L2→L3 清零⑦：chatpipeline → knowledge 归零（15 → 0）⇒ R3 全清** | 3 类载荷换成 L1 facts/视图 + `ImageInfoEnricher` 归位 retrieval | P2 | 大 | ✅ **完成（2026-10-08）**——① **载荷换形状**（`PipelinePorts` 9 个签名）：`KnowledgeBase` → **`KnowledgeBaseView`**（+3 字段 vecto… |
| **B115 M1 可行性侦察（Gradle 多模块）** | 带权包图 + 拓扑最优切分 + 装配/资源面清点（仅侦察，未动构建） | P2 | 中 | ✅ **完成（2026-10-08）**——**三条结论**：① **包图已是 DAG ⇒ 拆模块零业务代码改动**（只需搬文件 + 每模块一行 `project()`，`import` 全不动）； |
| **B116 M1 第一步：`:contracts` 抽取（编译期硬边界）** | `common`+`event`(175 文件/LOC 13.8k) → 零 project 依赖模块；5 个守卫多模块化；2 个 ArchUnit 坑 | P2 | 大 | ✅ **完成（2026-10-08）**——① **只做零成本切点**（按 B115 §5.3 策略）：新增 `:contracts`（`java-library` + dependency-management + s… |
| **B117 模块定名：`:contracts` → `:common`** | 改名 7 处；门槛移入 build 注释 | P2 | 小 | ✅ **完成（2026-10-08）**——① **定名理由（硬）**：本仓 `contracts` 已被占用——`server/src/test/resources/contracts/**` **1,426 个**… |
| **B118 classpath 资源存在性断言（R12a/R12b）** | `ClasspathResourcesTest`：20 条资源清单 + 反漂移扫描 | P2 | 小 | ✅ **完成（2026-10-08）**——对应 B115 §5.2 风险 #2：主源码 **15 处资源读取里 13 处"缺资源不报错"**（`if (in == null) return/continue`）⇒ 症状… |
| **B119 javadoc 引用漂移守卫（`-Xdoclint:reference` 接进 check）** | 首轮清算 44 error + 19 warning 行；抓出 4 处真缺陷 | P2 | 中 | ✅ **完成（2026-10-08）**——对应本会话反复人工修的同一问题（改名/搬家/神类切片后注释里的 `{@link}` 还指着旧目标）。 |
| **B120 契约层命名规范 + 5 组改名** | 盘点 26 个契约接口；定 5 条规范；改名 20 文件 | P2 | 小 | ✅ **完成（2026-10-08）**——① **盘点**（26 个契约接口）暴露的真分歧：`KnowledgeBaseLookup` 同接口里 **`kbById`（知识库）与 `knowledgeById`（知识条… |
| **B121 大文件棘轮（`check-file-size.py`）** | 68 个 >600 行文件入基线；不得新增 | P2 | 小 | ✅ **完成（2026-10-08）**——① **盘点**：主源码 1,888 文件里 **>600 行 68 个**（400~600 行另有 120 个）； |
| **B122 大文件守卫执行 §14.5 + 首次例外复核** | 政策从散文变可执行规则；登记表入 JSON | P2 | 小 | ✅ **完成（2026-10-08）**——① **取证（本批核心）**：`git log` 逐版本量行数发现 **`ImService` 2026-10-01 切片到 664 行「出榜」后，7 天内被 im 域功能批次… |
| **B123 `ImService` 第一刀（停止链路外提）** | 1091→995；新协作者 `ImStopOps` + 4 条钉子测试 | P2 | 中 | ✅ **完成（2026-10-08）**——① **刀口选在「跨实例 /stop 全链路」**（原 907~1012 行）：本地出队/在途取消 → Redis 在途映射补 IDs → 写 stop 事件到 StreamM… |
| **B124 三项卫生：游离目录守卫 + `embed→embedchannel` + 编号解冲突** | 新增 S1/S2 守卫；包改名；ArchUnit 标签 R*→A* | P2 | 小 | ✅ **完成（2026-10-08）**——起因：发现仓库根有个**空的** `webfetch/` 目录，追查得实情：① **它为什么看不见**：git **对空目录完全无感**（不入 `status`、不入提交、`g… |
| **B125 `ImService` 第二刀（入口闸门外提）+ 端口判据修正** | 995→936；新协作者 `ImInboundGuardOps` + 4 条钉子测试 | P2 | 小 | ✅ **完成（2026-10-08）**——① **先纠正上一轮的判断**：B124 把「`PipelinePorts` 没进 `common`」列为"第 1 号不一致"，侦察后发现**判断过重**：它引用了 4 个 *… |
| **B126 `ImService` 第三刀（知识库桥接外提）+ 删重复映射** | 936→856；新协作者 `ImKnowledgeBridgeOps`；删 `imPlatformToChannel` 副本 | P2 | 小 | ✅ **完成（2026-10-08）**——① **刀口**：IM 与知识库域的**全部接面**合成一刀——命令面的 KB 清单/检索读取（`kbLister`/`knowledgeSearcher`）+ 附件异步入库（… |
| **B127 `ImService` 第四刀（渠道运行时外提）⇒ 出榜** | 856→554；新协作者 `ImChannelRuntimeOps`（17 方法 / 313 行整块）| P2 | 中 | ✅ **完成（2026-10-08）**——① **刀口**：adapter 工厂注册表 + 渠道生命周期（起停/重载/按库重建/配置广播）+ WS 长连接选主（续期/抢锁重试/释放），17 个方法； |
| **B128 修回归 + A13 守卫（钩子不能在非 bean 上）** | 钩子挂回门面；新 ArchUnit A13；接线契约测试 3 条 | **P0** | 小 | ✅ **完成（2026-10-08）**——① **事故**：B127 把「渠道运行时」整块外提时，`@EventListener(ApplicationReadyEvent)` 与 `@jakarta.annotati… |
| **B129 `SessionKnowledgeQaService` 例外复核 + 两刀出榜** | 1041→756；两刀（兜底流并入既有协作者 + WebSearch 解析新叶子）| P2 | 中 | ✅ **完成（2026-10-08）**——① **复核结论：例外不成立，且"无接缝"是误判**——真正的情况是**上一次拆分没搬完**：本域早已切出深协作者树（`QaSearchTargets`/`QaChatMana… |
| **B130 `MemoryIndexStore` 例外复核 + 向量面外提（出榜）** | 919→636；新协作者 `MemoryVectorStore`(318)；A7 白名单随簇迁移 | P2 | 中 | ✅ **完成（2026-10-08）**——① **复核结论：例外不成立**，且属 **B129 同型误判**：原理由「六段同属『索引侧读写』一个关注点」是**层次**论点（"都在哪一层"），不是**内聚**论点； |
| **B131 死成员清账 + 新守卫 `check-dead-members.py`** | 40 条 IDE 警告 → 单一根因；八守卫 | P2 | 小 | ✅ **完成（2026-10-08）**——① **根因（本会话自己造的）**：40 条警告里 **24 条"未使用 import"实为「重复 import」** ✗——**B111 的「内联全限定名 → import」… |
| **B132 租户 KV 配置面换锚（第一段：chat-history / retrieval）** | 12 键去逐字段 `@JsonProperty`；动态读路径 + 前端 + 金片 + 记录脚本同批 | P2 | 小 | ✅ **完成（2026-10-09）**——① **先核对冻结理由，再动手**：`check-json-key-case.py` 口径把「租户配置 jsonb」列为冻结面，§14.9b/system-module-gui… |
| **B133 租户 KV 配置换锚（第二段：storage-engine）+ parser 逐键判定** | 14 键去 `@JsonProperty`（63 处）；两面一源合并；parser 27 键登记冻结 | P2 | 中 | ✅ **完成（2026-10-09）**——① **先出判定表再动手**（承 B132 的「先核对理由」纪律）：41 个"零理由"键里，**parser 27 键经逐键复核实为「外部决定」**——`SystemContr… |
| **B134 事件/进度载荷收口（`PipelineProgress`）+ 摘两处空冻结** | 5 键 → camel（前后端 + 夹具）；摘 `PipelineProgress`/`ReferencesSupport` 冻结条目 | P2 | 小 | ✅ **完成（2026-10-09）**——① **承接 B93b 没做完的那一角**：`event/` 与 `stream/` 当时已 camel 化并从冻结名单摘除，但 `chatpipeline/PipelineP… |
| **B135 存量族清算·第一片（审计 details 8 键）+ 三处理由订正** | `rawPath`/`requiredRole`/`scopeType`/`quotaBytes`/`quotaGb`/`valueType`/`oldValue`/`newValue` | P2 | 小 | ✅ **完成（2026-10-09）**——① **先做逐文件判定表再动手**（承 B132/B133 纪律）：枚举审计 details 的**全部**写入键后发现该面**本就是 camel**（`tenantId`/`… |
| **B135c mention/steer 载荷换锚（8 键）** | `mentionedItems`/`steerId`/`kbId`/`kbName`/`kbType`/`serviceId`/`skillName` | P2 | 小 | ✅ **完成（2026-10-09）**——① **判定表发现"只改了一半"的真相**：FE 侧 `types/mention.ts` 的 javadoc 早写着「提及项**单一形状**…camelCase…请求面 /… |
| **B135d1 `context_config` 4 键换锚 + 一处数据源面矛盾入册** | `maxTokens`/`compressionStrategy`/`recentMessageCount`/`summarizeThreshold` | P2 | 小 | ✅ **完成（2026-10-09）**——① **先查消费者再动**（承纪律）：这 4 键在 main 里只有 `TenantService.normalizeContextConfig`（读取路径与响应输出共用的归一… |
| **B135b1 引用载荷回放路径的静默缺陷修复（判缺陷，非换锚）** | `searchResultFromMap` 11 键按生产者形状读；加结构化断言 + 红态证明 | P2 | 小 | ✅ **完成（2026-10-09）**——① **判定链**（承"先核对理由"纪律）：`AgentStreamBridge` 的 19 键基线**整条过期**（16 个键文件里根本不存在，其余是日志占位符 ✗），真站点… |
| **B136 wiki 摄取 36 键「结案」——判观测面，不改（负结果）+ 第 4 处误标订正** | 逐站复核 3 文件 36 键；基线理由订正；13 个模型输出标签仍为拍板项 | P3 | 小 | ✅ **完成（2026-10-09，结论为"不改"）**——① **逐站取证**：三个文件的键站点**全部**是 span/stage 的 input/output（`extractInput`/`extractOut`… |
| **B135b2 SSE 事件载荷收尾（3 键）+ 两道陈账销号 + 新守卫 ⑤** | `finalContent`/`userCreatedAt`/`assistantCreatedAt`；立项稿与 FE 白名单订正 | P2 | 小 | ✅ **完成（2026-10-09）**——① **前提复核（§14.6 纪律）推翻了三条"冻结"依据**：（a）§14.9l 前提判定 2 写"**已实测后端无这些键**"⇒ **事实错误**（`QaSseOrches… |
| **B137 datasource 面判定表·头两片（d-a 过期注释 + d-b RSS 配置换锚）** | 3 处 javadoc 订正；`feedUrls`/`authHeaders`（去命名策略 + 删"老位置回显"）| P2 | 小 | ✅ **完成（2026-10-09）**——**先出判定表**（该面在 §14.9q 的结论是"可换锚面 **0**"，复核后**大体成立但有三处漏判**）：① **真外部约 140 处**（`*ApiTypes`/`*… |
| **B138 datasource 面·d-c + d-d（游标族 + item metadata + resource_ids）** | 15 键换锚 + 6 处 Go 期残留 javadoc 订正 | P2 | 中 | ✅ **完成（2026-10-09）**——**d-c 游标族 8 键**：`lastSyncTime`（rss/ima/yuque/feishu 四处共用）· `feedItems`/`feedSignals` · `… |
| **B139 datasource 面·d-e（*Config 自有键 7 个）** | 7 键换锚 + 2 条未覆盖文案 + 2 个测试重写 | P2 | 中 | ✅ **完成（2026-10-09）**——**判定表的核心是一句话**：这些键**看着像外部词，实际是我们自己的**。 |
| **B140 datasource 面·d-g（connector 侧 metadata 29 键）** | 29 键换锚 + FE 连带 2 面 + 守卫基线 1 条 | P2 | 中 | ✅ **完成（2026-10-09）**——**起因是 d-f 的收窄预演**：把 `datasource/connector` 从整目录冻结前缀摘掉后，立刻露出 **26 文件 /134 键**（此前全被"整目录豁免"… |
| **B141 datasource 面·d-f（守卫收网）** | 整目录豁免 → 文件级 16 条 + 清 4 条失效基线 | P2 | 小 | ✅ **完成（2026-10-09）**——把 `datasource/connector` 从**整目录冻结前缀**改为**文件级 16 条**（`DocxBlocks`/`FeishuApiTypes`/`Feish… |
| **B142 测试并行化（全量闸门提速）** | gradle 测试 4 fork + 修 EmbeddedRedis 端口竞态 | P2 | 小 | ✅ **完成（2026-10-09）**——**起因**：全量闸门 ≈5 分钟，拆开量账发现 Java 测试 **213.9s（81%）**，而 `server/build.gradle.kts` 的 test 块**没… |
| **B143 键名守卫加"读侧形态" + 观察单** | 第 5 种形态 + A 桶 12 文件登记 + 29 键观察单 | P2 | 小 | ✅ **完成（2026-10-09）**——**动机**：B132/B138/B140 三次真实事故是**同一形态**：写侧改成 camel、**读侧仍 snake** ⇒ 静默失效； |
| **B144 C 桶追清 + 第 6 种形态 + B 桶换锚** | 3 处活缺陷 + 1 处 B138 遗留 + 11 键换锚 + 8 文件登记 | P1 | 中 | ✅ **完成（2026-10-09）**——**C 桶追清了，结论不是"待拍板"而是三处活缺陷** ✗：写侧 `FaqChunkMetadata`（common 模块、**零注解**）经 `JSON.valueToTre… |
| **B145 第 7 种形态（集合字面量）+ 审计两键换锚** | Map.of/Map.entry 形态 + 12 文件登记 + 2 键 | P2 | 小 | ✅ **完成（2026-10-09）**——**预演**：`Map.of("snake", …)` / `Map.entry("snake", …)` 全仓增量 **43 键 / 13 文件**，但结构极不均匀：**31… |
| **B146 审计 details 面换锚** | 3 键（+2 死标签）+ FE 字典 5 语言 | P2 | 小 | ✅ **完成（2026-10-09）**——**枚举源用的是 FE 的 `detailFields` 字典**（活动页渲染 `detailFields.${key}`，所以它天然是"审计 details 键"的权威清单）… |
| **B147 守卫注释账 + 自定义 helper 族换锚** | 2 条孤儿注释 + 1 条错理由 + 2 键 | P3 | 小 | ✅ **完成（2026-10-09）**——**a）注释账**：守卫里三条"存量面"理由经复核只剩三种命运——① `# 读取 SQL/检索行键（存量面）` 与 ② `# 工具结果/附件载荷（存量面，同 tool-resu… |
| **B147c 快照清账（脏键 + 失效统计）** | 1 个脏键 + 32 键失效台账 | P3 | 小 | ✅ **完成（2026-10-09）**——**收尾快照**（`--list` 汇总）当场抓出一处**我自己引入的脏数据** ✗：`SystemController` 条目的键表里混进了**文件路径本身**（`'syst… |
| **B148 基线瘦身（棘轮只许减）** | 32 失效键 + 3 脏键 ⇒ 294→262 | P3 | 小 | ✅ **完成（2026-10-09）**——**先出逐键三态表**（不只看"守卫有没有命中"，而是逐键回源码查**残留**，因为 B144 的 `tag_ids` 教训表明"不再命中"可能是**换了形态**而非消失 ✗）… |
| **B149 两处「像外部词、实为我们自己」的换锚** | modelUsage 的 2300 details + agent 预设 kbFilter 谓词 | P3 | 小 | ✅ **完成（2026-10-09）**——用户问"这些 snake 正常吗"，逐处定性后收掉**两处真债**（另两处判正常 ✓）：**① `knowledge_bases` → `knowledgeBases`**：`… |
| **B151 逐字段 @JsonProperty 清算 + A14 硬门（B150 的 Step 1）** | 清 235 处纯冗余；新增守卫 | P2 | 中 | ✅ **完成（2026-10-09）**——起因：用户问"全仓还有大量的 `@JsonProperty`，是否都有必要"。 |
| **B152 外部协议面冗余注解清算（B150 Step 2）** | 清 43 处；守卫撤掉面级放行表 | P3 | 小 | ✅ **完成（2026-10-09）**——用户选「也清掉」。 |
| **B154 IDE 告警收口（三类死物）** | 自 import 7 · 重复 import 4（test 侧）· 死方法 2 · 冗余 import 1 | P3 | 小 | ✅ **完成（2026-10-09）**——IDE 报 9 条，逐条核实后扩到全仓同类（8 文件 −41 行）：① **自 import 7 处**（`StorageEngineConfig` 导入**自己的嵌套类**… |
| **B155 死成员守卫扩面（D-d + main/test 全扫 + D-c 收紧）** | 守卫三改；零代码改动 | P3 | 小 | ✅ **完成（2026-10-09）**——B154 顺出的两个盲区 + 一处规则漏洞：① **新增 D-d 自 import** ✗（`import` 的 FQN = 本文件 package + 本文件顶层类 ⇒ 必然… |
| **B156 迁移折叠：V1~V6 → 单文件 `V1__baseline.sql`** **补记（开发库重建实测，2026-10-09）**：① **折叠块漏了 `SET search_path`** ✗ —— pg_dump 的语句是全限定的（`public.xxx`）且开头把 `search_path` 置空 ✗，而折叠块用了**未限定名** ⇒ 灌入在第一条 `CREATE TABLE skills` 上失败 ✓（**换任何一台新库都灌不进去** ✓ —— 幸好先在开发库上试 ✓；教训：**迁移折叠必须真灌一次** ✓，单测跑 Flyway=false ⇒ 覆盖不到 ✓）。② 重建后核对：**60 表** ✓ / `skills`+`tenant_id`+三索引 ✓ / 两列 jsonb 默认值 **camel** ✓ / 对 tenants 的 4 个外键与 V1 计数一致 ✓。③ **表集比对（旧库 71 vs 新基线 56）**：差集**全是应删物** ✓ —— `flyway_schema_history`+`schema_migrations`（簿记表 ✓ 重建后 Flyway 自建）· **15 张已移除/取代的功能表**（share/organization 系 + `browser_*` 系 + `tenant_sandbox_configs` + `tenant_user_env_vars` + Go 时代 `tenant_skill*`）✓ ⇒ **旧开发库本就停留在 Go 时代 schema、从没跟过 Java 侧的移除** ✓ ⇒ 重建顺带对齐了 ✓。④ 备份在 `/tmp/weknora-dev-20261009-1801.dump`（20M ✓ `pg_restore -Fc` 可还原 ✓）。 | 6 文件 → 1；活引用 9 处 | P3 | 中 | ✅ **完成（2026-10-09）**——用户确认"只有开发库"后，按 V1 那次（`fba0e7ae` PR4 基线合并）的先例做**终态折叠**（非拼接 ✓）：① **V6 → 折进建表**（`chunking_c… |
| **B157 CI 首次跑暴露的两处（seed tag 未推 + 录测试无 CI 排除）** | tag 推送 + 两类打标签排除 | P1 | 小 | ✅ **完成（2026-10-09）**——push 后首次跑 CI，在 spotless 步就红 ✗：`No such reference 'seed'`。 |
| **B158 CI 起真 PG（基线灌库守卫 + 录测试回归真跑）** | `.github/workflows/ci.yml` 服务容器 + 灌基线步；撤 B157 权宜排除 | P2 | 中 | ✅ **完成（2026-10-09）**——B157 记的那个缺口（CI 不灌基线 ⇒ B156 那类"只有真库才暴露"的缺陷无人守 ✗）在此收掉 ✓。 |
| **B159 订正 HANDOFF §7.3/§8 的过期内容** | 待办区清空 + CI/tag 行订正 | P2 | 小 | ✅ **完成（2026-10-09）**——**起因是一次真实的踩坑** ✗：本会话按"摘要 + 待办区"宣称「剩余待办 B150 Step 1」✗，遂重跑 B150 期的旧清单脚本 ⇒ 它按**旧口径**（不认识 A1… |
| **B163 P3a：`tracing` 按层次拆分（core 进 `:common` = L1 对齐）** | 25 文件归位（20 core → common / 6 装饰留 engine 换包）+ OTLP proto 移交 | P2 | 中 | ✅ **完成（2026-10-09）**——承用户"`tracing` 归 `:common`"的设计，先做**逐边定性** ✓：11 条边（`llm` 7 / `rerank` 3 / `embedding` 1）**… |
| **B162 P1+P2：L1 对齐（`stream` 下沉）+ `:engine` 边界校准（`model`/`vectorstore` 回业务侧）** | 2 文件下沉 + 18 文件回迁 + 共享测试基座归 `:common` | P2 | 中 | ✅ **完成（2026-10-09）**——用户定案 5 模块图（`:common` / `:engine` / `:domains` / `:channels` / `:boot`）后的前两步。 |
| **B161 M1 第二步：`:engine` 抽取（第二条编译期硬边界）** | 368 主源码 + 65 测试迁入；守卫多模块化；新模块 build | P2 | 大 | ✅ **完成（2026-10-09）**——把「能力层不得依赖业务域（L2→L3）」从脚本规则**升格为编译规则** ✓（B116 拆 `:common` 的同法）。 |
| **B160 项目现状重析 + §7.3 重写（反映 B110~B120 实际进度）** | §7.3 item 3 重写；零代码改动 | P2 | 小 | ✅ **完成（2026-10-09）**——用户要求「重新分析项目现状，梳理方案」⇒ **全部现场实测、不引用摘要** ✓。 |
| **B37 档 3 第一刀（provider 请求面）** | Go 字节兼容层退役起步：三份 provider GoJson 去 HTML 转义复刻 | P2 | 小 | 🚧 **完成第一刀（2026-10-03）**——字节流向盘点（四类）+ 三份副本退役（探针先行：embedding 单跑绿后同批改 rerank/websearch）； |
| **B38 档 3 第二刀（stream/langfuse/LLM 请求体）** | Go 版确认下线 → Redis 事件等三面退役 Go 转义 | P2 | 小 | ✅ **完成（2026-10-03）**——`stream/StreamJson`（保留键序=内部 CAS 需稳定字节）、`RemoteApiBodyCodec`（保留键序归一）、`LangfuseAttributes`… |
| **B39 档 3 第三刀（工具面）+ 实录约束发现** | MCP/待办/检索三处退役；发现「Go 形态实录」为档 3 硬边界 | P2 | 小 | ✅ **完成（2026-10-03）**——三处退役； |
| **B40 实录基线重建（GoJsonEscapes 全量退役）** | 实录比对「逐字节」→「ContractJson.deep 语义比较」；删 GoJsonEscapes 类 | P2 | 中 | ✅ **完成（2026-10-03）**——解法＝不改实录（禁止手改）而改比对方式（收编漏网的字节级对比）； |
| **B41 provider JSON 四副本收敛** | 4 份 GoJson/GoJsonUtil → `common/web/ProviderJson`；删零引用 GoFloatSerializer | P2 | 小 | ✅ **完成（2026-10-03）**——embedding 版为超集； |
| **B42 档 3 第五刀（工具协议面）** | 转义退役 + 四类去 Go 名（HtmlEntities/JsonValues/ToolJson/JsonQuoting） | P2 | 小 | ✅ **完成（2026-10-03）**——先分类后落刀（真复刻 vs 只是名字带 Go）； |
| **B43 档 3 第六刀（真退役）** | 工具协议面换 Java 原生：删 JsonQuoting/GoValueStr/GoJsonMarshal + ToolJson 手写 writer 退役 | P2 | 小 | ✅ **完成（2026-10-03）**——用户拍板「不要只是改名」； |
| **B44 档 3 第七刀（connector 面）** | 删 GoBase64 / yuque GoDuration / common.time GoDuration（Java 原生替换） | P2 | 小 | ✅ **完成（2026-10-03）**——`java.util.Base64`（去换行）+ `Double.parseDouble`（与 Feishu 收敛）+ `Duration.toString()`（ISO-86… |
| **B45 档 3 第八刀（GoStrings 收敛）** | 两份 GoStrings → common/text/Whitespace + CodePointOrder（Unicode 空白统一） | P2 | 中 | ✅ **完成（2026-10-03）**——16 文件 / 45 处改写； |
| **B46 档 3 第九刀（验证驱动裁决）** | GoUrl / GoPath×2 / GoStyleErrorReportValve 逐类裁决 | P2 | 小 | ✅ **完成（2026-10-03）**——`GoUrl` 保留（95 位全表实测 7+2 处转义差异、无真实环境可验证）； |
| **B47 保留类改名** | 契约保留类修标签（GitLabUrl/GitLabPath/PosixPath/PlainTextErrorReportValve） | P3 | 小 | ✅ **完成（2026-10-03）**——用户拍板：保留类的 `Go*` 名误导后人； |
| **B48 注释大清洗专项** | 全仓注释去 Go 锚点/翻译腔/过期引用（用户 2026-10-03 立项，B9 专项化；main+test） | P2 | 大 | ✅ **完成（2026-10-03）**——11,932 匹配行/1,723 文件起步 → 棘轮基线 3,999→**0**（`--write` 已刷新）； |
| **B52 B48 排查批** | 审批事件流断链修复 + 三项待复核疑点闭环 | P1 | 小 | ✅ **完成（2026-10-04）**——实锤修复：`ApprovalBridge` 原样转投使三类审批事件 instanceof 失配、聊天流审批卡静默消失（审批面板路径幸存故冒烟未抓到），补 `toPayloadD… |

### 15.1.1 执行记录（索引：正文已移出，按批号 Ctrl-F）

逐批执行记录在 `docs/handoff/records/batch-records.md`（442 行）。

| 批 | 摘要 |
|---|---|
| ✅ B2 | `support/GoldenContract` 共享基建上线（`ContractJson.deep` 归一 + strip + 单一 `-Dcontract.refresh` 开关）； |
| ✅ B1 | `docs/knowledge-api-contract-v1.md` v1.0→v1.1——升格全服务端标准； |
| ✅ B3 | 真面 68 处（19 文件）`@JsonInclude` 退役——wiki domain 全家（10 文件）+ websearch 三 DTO + VectorStoreTypes； |
| ✅ B4（结论：不改） | 哨兵不是债——① datasource 域零值=「从未同步」的**业务信号**（`lastSyncTime` 进调度比较，改 null 要动调度语义）； |
| ✅ B7 | 依赖级口径（声明+构造赋值 ≤2 次）实锤断成员 19 处全清——只注入不读取字段 3（`PluginSearchParallel`）+ 死 logger 8 + 死 `ObjectMapper` 3 |
| ✅ B8 | 全限定名注解 177+6 → 0（14 文件，注入前逐文件同名符号冲突扫描）； |
| ✅ B0（2026-10-02，走查批收官） | 后端 8083（postgres 驱动）+ 前端 5173 + 桩 LLM（127.0.0.1:18090，`b0-stub-chat`）+ 桩 RSS（127.0. |
| ✅ B3b（2026-10-02，由 B0 走查升格） | 知识库配置 jsonb 键名统一到 Java 字段名（camelCase）——前端 payload / 服务端读取器 / 落库列三方咬合面同批对齐。 |
| 🚧 B6 批 1（2026-10-02，storage 装配） | `storage/service/DefaultStorageBackendProvisioner` 单文件 **46 处 `System.gete |
| 🚧 B6 批 2（2026-10-02，langfuse） | `tracing/langfuse` 12 处清零（全仓 149→93，代码内 88）。 |
| B6 余量口径（下一批动手前先定，2026-10-02 侦察结论） | 剩 88 处代码内读取，按上下文分两类—— |
| 🚧 B6 批 3（2026-10-02，检索驱动 + 向量库 env 查找面） | `RETRIEVE_DRIVER` 的 7 个读取点清零（全仓 93→86，代码内 81）。 |
| 🚧 B6 批 4（2026-10-02，系统/部署面 + `GIN_MODE` 改名） | 15 处清零（全仓 86→75，代码内 68）——`system` 域代码内 getenv 归零。 |
| 🚧 B6 批 5（2026-10-02，知识域单值：docreader / 批大小 / 清理开关） | 6 处清零（全仓 75→69，代码内 62）。 |
| 🚧 B6 批 7（2026-10-02，存储静态单值族 + JWT 密钥） | 8 处清零（全仓 64→57，代码内 50）。 |
| 🚧 B6 批 8（2026-10-02，存储 provider 环境族查找面） | 3 处清零（全仓 57→53，代码内 46； |
| 🚧 B6 批 9（2026-10-02，检索域：引擎命名/开关/超时族） | 18 处清零（全仓 53→35，代码内 46→28； |
| ✅ B6 批 10（2026-10-02，收尾批——裸 `getenv` 代码内清零，B6 结项） | 28 处清零（代码内 46→**0**； |
| B6 结项小结（149 → 0） | 十批分别是 storage 装配 46 / langfuse 12 / 检索驱动 7 / 系统部署面 15 / 知识域单值 6 / common 静态族 5 / 存储单值+JWT |
| ✅ B12（2026-10-02，B0 残留批 1：wiki 槽位与孤儿任务） | **✅ B12（2026-10-02，B0 残留批 1：wiki 槽位与孤儿任务）** |
| ✅ B13（2026-10-02，B0 残留批 2） | **✅ B13（2026-10-02，B0 残留批 2）** |
| ✅ B5（2026-10-02，lf_* 载具嵌套化：判定 + 执行） | **✅ B5（2026-10-02，lf_* 载具嵌套化：判定 + 执行）** |
| ✅ B9（2026-10-02，Go 锚点：政策 + 执行机制落地；清扫仍"随触碰"，不立专项） | **✅ B9（2026-10-02，Go 锚点：政策 + 执行机制落地； |
| ✅ B11 判定（2026-10-02，Gradle 多模块）——结论：不做，正式搁置 | **✅ B11 判定（2026-10-02，Gradle 多模块）——结论：不做，正式搁置** |
| 🐞 B14 修复（2026-10-02，真 bug：非 minio 的「行配置」云凭据被静默丢弃） | **🐞 B14 修复（2026-10-02，真 bug：非 minio 的「行配置」云凭据被静默丢弃）** |
| ✅ B14 合并完成（2026-10-02，两套词汇收成「一面一源」） | **✅ B14 合并完成（2026-10-02，两套词汇收成「一面一源」）** |
| ✅ B15（2026-10-02，供给行明文落库——登记项闭环） | **✅ B15（2026-10-02，供给行明文落库——登记项闭环）** |
| ✅ B16「静默失效」定向扫描（2026-10-02，用户拍板方案 A） | **✅ B16「静默失效」定向扫描（2026-10-02，用户拍板方案 A）** |
| 🚧 B17 换锚收尾·判定批（2026-10-02） | **🚧 B17 换锚收尾·判定批（2026-10-02）**——把「残余 @JsonProperty」逐条判定为三类，本批**无代码变更**（实验性删除已回退，全量复绿）。 |
| ✅ B17② websearch 请求键收口（2026-10-02） | **✅ B17② websearch 请求键收口（2026-10-02）**——`is_default` → camel `isDefault`（§2 第 4 条），**不留兼容别名**。 |
| 🔎 B17① agent 配置面：判定「暂不做」（建议，待用户拍板） | **🔎 B17① agent 配置面：判定「暂不做」（建议，待用户拍板）** |
| ✅ B18 agent 配置面 camel 换锚（专项，2026-10-02 完成） | **✅ B18 agent 配置面 camel 换锚（专项，2026-10-02 完成）**——§2 第 11 条"落库格式走 Java 字段名"的最后一块大口子。 |
| ✅ B19 前端契约键收口 + 双侧守卫（2026-10-02） | **✅ B19 前端契约键收口 + 双侧守卫（2026-10-02）** |
| ✅ B21（2026-10-02，占位符组键收口 + 「同类情况」定向排查） | **✅ B21（2026-10-02，占位符组键收口 + 「同类情况」定向排查）** |
| ✅ B22（2026-10-02，模板面 4 键 + `api_key` 收口 + 删死模块） | **✅ B22（2026-10-02，模板面 4 键 + `api_key` 收口 + 删死模块）** |
| 🧰 B23（2026-10-02，孤儿夹具审计：工具 + 首次结论） | **🧰 B23（2026-10-02，孤儿夹具审计：工具 + 首次结论）** |
| 🔎 B24（2026-10-02，换锚长尾回头扫：注解面清零 + 真实残留面盘点） | **🔎 B24（2026-10-02，换锚长尾回头扫：注解面清零 + 真实残留面盘点）** |
| ✅ B25（2026-10-02，批甲：模板标志位收口；引用/进度载荷判为冻结） | **✅ B25（2026-10-02，批甲：模板标志位收口； |
| 🐞 B26（2026-10-02，批甲续：websearch 凭据面全链修复 + 其余四面判冻结） | **🐞 B26（2026-10-02，批甲续：websearch 凭据面全链修复 + 其余四面判冻结）** |
| ✅ B27（2026-10-02，批甲续 2：推荐问题键收口 + 6 类面判冻结） | **✅ B27（2026-10-02，批甲续 2：推荐问题键收口 + 6 类面判冻结）** |
| ✅ B28（2026-10-02，批甲续 3：websearch provider-types 配置字段面收口） | **✅ B28（2026-10-02，批甲续 3：websearch provider-types 配置字段面收口）** |
| ✅ B29（2026-10-02，批甲/批乙收口：换锚待判清单 66 → 0） | **✅ B29（2026-10-02，批甲/批乙收口：换锚待判清单 66 → 0）** |
| 🔧 B29 订正（2026-10-02） | B29 的 BASELINE 插入代码多写了一个提前闭合字典的 `}`，脚本出现 `IndentationError`； |
| ✅ B30（2026-10-02，model 凭据面与 MCP/websearch 统一为 camel） | **✅ B30（2026-10-02，model 凭据面与 MCP/websearch 统一为 camel）** |
| ⚠️ B31（2026-10-02，B23 处置：结论修正 + 一次失败尝试的完整记录） | **⚠️ B31（2026-10-02，B23 处置：结论修正 + 一次失败尝试的完整记录）** |
| ✅ B33（2026-10-02，包结构守卫红灯修复：两个工具类归位到最低层） | **✅ B33（2026-10-02，包结构守卫红灯修复：两个工具类归位到最低层）**——B6 批 10 引入的环 5 组 / 依赖 config 1→11 全清：`AppEnvLookup` → `common/deployment`、`StorageRun… |
| ✅ B34（2026-10-02，agent/tools 分包：根 + 5 能力子包） | **✅ B34（2026-10-02，agent/tools 分包）**——94 文件 → wiki 30 / knowledge 11 / sql 5 / data 4 / web 2 + 根 42（框架 + 跨族共享 + 通用单件 + MCP 族）； |
| ✅ B35（2026-10-02，modelcontext 并入 agent） | **✅ B35（2026-10-02，modelcontext 并入 agent）**——顶层包 31 → **30**：13 文件 → `agent/modelcontext/`（test 3 同移）+ 23 文件改包路径（含 FQN/javadoc，零残留… |
| ✅ B36（2026-10-03，agentm 并入 agent） | **✅ B36（2026-10-03，agentm 并入 agent）**——顶层包 30 → **29**：`agentm`（20 文件）→ `agent/management/`（子域形态，先例 `auth/apikey/`）； |
| 🚧 B37（2026-10-03，档 3 第一刀：provider 请求面退役 Go 转义） | **🚧 B37（2026-10-03，档 3 起步：provider 请求面）**——字节流向盘点（四类：provider 请求 / Redis 事件 / 落库响应 / 工具协议）+ 三份 provider `GoJson` 去 `GoJsonEscapes`… |
| ✅ B38（2026-10-03，档 3 第二刀：stream/langfuse/LLM 请求体） | **✅ B38（2026-10-03，档 3 第二刀）**——用户确认「Go 版已下线、不再双跑」（关键决策登记）； |
| ✅ B39（2026-10-03，档 3 第三刀：工具面 + 实录约束发现） | **✅ B39（2026-10-03，档 3 第三刀）**——`McpCatalog`/`TodoWriteTool`/`GoJsonUtil` 去 escapes + 两处测试侧清理； |
| ✅ B40（2026-10-03，实录基线重建：GoJsonEscapes 全量退役） | **✅ B40（2026-10-03，实录基线重建）**——不改实录、改比对方式（`ContractJson.deep` 语义比较，B2 方针的收编）； |
| ✅ B41（2026-10-03，provider JSON 四副本收敛 + 删零引用） | **✅ B41（2026-10-03，四副本收敛）**——`embedding/rerank/websearch/retrieval` 四份副本 → `common/web/ProviderJson`（超集）； |
| ✅ B42（2026-10-03，档 3 第五刀：工具协议面） | **✅ B42（2026-10-03，工具协议面）**——`GoHtml`/`GoJsonValues` 改名（非 Go 语义）+ `GoJsonCodec`→`ToolJson`（去 HTML 转义、保键序）+ `GoQuoting`→`JsonQuotin… |
| ✅ B43（2026-10-03，档 3 第六刀：真退役） | **✅ B43（2026-10-03，工具协议面换 Java 原生）**——删 `JsonQuoting`（→ `ToolJson.quoted`，收敛 WeaviateGql 第 5 份拷贝）/`GoValueStr`（→ 原生 `valueStr`，`<n… |
| ✅ B44（2026-10-03，档 3 第七刀：connector 面） | **✅ B44（2026-10-03，connector 三刀）**——删 `GoBase64`（→ JDK Base64，GitLab 换行处理保留）/yuque `GoDuration`（→ `Double.parseDouble`，与 Feishu 收敛… |
| ✅ B45（2026-10-03，档 3 第八刀：GoStrings 收敛） | **✅ B45（2026-10-03，空白实现统一）**——两份 `GoStrings`（wiki 96 行 + gitlab 82 行）删除 → `common/text/Whitespace`（`isSpaceChar`+6 = White_Space 精… |
| ✅ B46（2026-10-03，档 3 第九刀：验证驱动裁决） | **✅ B46（2026-10-03）**——`GoUrl`/`GoPath`×2 **保留**（探针实测：转义 7+2 处差异 / 路径 21/24 一致但空结果差异含安全退步； |
| ✅ B47（2026-10-03，保留类改名） | **✅ B47（2026-10-03）**——保留类修标签：`GoUrl`→`GitLabUrl`、`GoPath`→`GitLabPath`/`PosixPath`（归位 common/text）、`GoStyleErrorReportValve`→`Pla… |
| ✅ B48（2026-10-03，注释大清洗专项：Go 锚点/翻译腔/过期引用归零） | **✅ B48（2026-10-03，B9 专项化收官）**——main+test 11,932 匹配行/1,723 文件起步 → 棘轮基线 3,999→**0**（`--write` 已刷新）； |
| ✅ B52（2026-10-04，B48 排查批：审批事件流断链修复） | **✅ B52（2026-10-04）**——`ApprovalBridge` 原样转投致三类审批事件 instanceof 失配、聊天流审批卡静默消失； |
| ✅ B53（2026-10-04，知识面卡片模型键收口 + 失败原因接线） | **✅ B53（2026-10-04）**——三键定性：`original_file_name`/`display_name` = 内部视图模型键（写读同套、功能正常但属 Go 时代遗留）、`error_message` = 死字段（接口给 camel `er… |
| ✅ B54（2026-10-04，api 面 snake 键收口 + 裁撤死参数收尾 + 守卫扩面） | **✅ B54（2026-10-04）**——由 B53 追问扩展到 api 线格式面：修 12 处「接口 camel / 前端 snake」断链，其中**改密实测恒失败**（snake body → 400 `oldPassword/newPassword:… |
| ✅ B55（2026-10-04，api 面 snake 记号逐条核实） | **✅ B55（2026-10-04）**——B54 挂起的 32 键全定性：**13 改 camel**（偏好 `lastActiveTenantId` 探针证实「回到上次空间」此前永不生效、`oidcOnlyLogin` 门禁、wiki 六键→页数恒 0/… |
| ✅ B56（2026-10-04，技能区文案/死键/宿主技能目录） | **✅ B56（2026-10-04）**——点检追问「技能管理为何没了」查实＝功能裁剪第一批（PR3 `caef9d5`，技能降级为指令型）。 |
| ✅ B57（2026-10-04，技能管理回归：入库 + 平台级 CRUD + 宿主目录退役） | **✅ B57（2026-10-04）**——方案三稿收敛（复用原 `/api/v1/skills/catalog` 路径 → 改指令型 → 入库）。 |
| ✅ B58（2026-10-04，旧信封读法清剿：Go `{success,data}` 残留 → Java 裸载荷） | **✅ B58（2026-10-04）**——用户点检 `?section=integration-api` 报「加载 API 集成设置失败」：`/auth/me` 裸信封（tenant 在顶层）而页面读 `userResp.data.tenant` → 恒… |
| ✅ B59（2026-10-04，技能编辑：PUT 更新 + 改名保护 + 编辑弹窗） | **✅ B59（2026-10-04）**——用户要求「查看内容」改「编辑」、弹窗同新建。 |

| ✅ B60（2026-10-04，技能租户化：平台级 → 空间级 + 菜单并入「数据与扩展」） | **✅ B60（2026-10-04）**——用户提「技能配租户配置 + 菜单挪到数据与扩展」，核实为修隔离缺陷（`GET /api/v1/skills` 读全表 → 跨租户可见； |

| ✅ B61（2026-10-04，技能按需读取打通 + @点名注入正文） | **✅ B61（2026-10-04）**——用户追问「如何加载 skill」→ 实证**Level 1 通、Level 2/3 断**（`skill://` 无解析器 / `read_file` 无实现 / Manager 读面零调用者）。 |

| ✅ B62（2026-10-04，WeKnora Cloud 整功能裁撤） | **✅ B62（2026-10-04）**——用户：「去掉 WeKnora Cloud 设置」。 |

| ✅ B63（2026-10-04，embed 语言「跟随浏览器/宿主」失效） | **✅ B63（2026-10-04）**——用户点检 `widget-test.html`。 |

| ✅ B64（2026-10-04，「跟随宿主」补上宿主页面语言） | **✅ B64（2026-10-04）**——用户追问「为什么配置跟随还是英文」。 |

| ✅ B65（2026-10-04，嵌入渠道保存后发布密钥丢失） | **✅ B65（2026-10-04）**——用户报「嵌入代码原来正常，点保存后变成 `<!-- 加载渠道密钥失败… -->`」。 |

| ✅ B66（2026-10-04，Agent 编辑器提示词变量全空） | **✅ B66（2026-10-04）**——用户问 `/platform/agents` 的变量「点击插入 / `{{` 唤起列表」。 |

| ✅ B67（2026-10-04，KB 解析设置整页崩：规则键名错面） | **✅ B67（2026-10-04）**——用户贴 `getEngineForGroup` 的 `undefined.some`。 |

| ✅ B68（2026-10-04，智能体「推荐问题」恒空） | **✅ B68（2026-10-04）**——用户问某库有无自动生成问题（**有**：enabled + 每块 4 个、23/24 chunk 已生成）。 |

### 15.2 批次纪律（每批通用，违者必翻车——全是本轮实锤）

1. §14.2 七步 SOP：一次只动一个轴、独立提交独立全绿、双端闸门（后端全量+spotless；触前端契约则 vue-tsc+npm test）。
2. "同 PR 带前端"必须核到**视图层字段读取**——api 类型文件对齐 ≠ 消费端对齐（TS 断言会藏住一切）。
3. 键名替换前分清三类键：**线格式键**（换）/ **系统设置键与 i18n 键**（不换，DB/文案字符串）/ **客户端本地态字段**（保留并注释）。
4. 触 HTTP 面的批做真实服务冒烟；金片重录前先看该测试用字节级还是语义级对比器（B2 统一后此条自动消解）。
5. 每批执行记录写回本节（沿用 §14.9x 的 ✅ 格式）。
6. **规则型/守卫型测试必须发明可证伪的探针**：新增静态闸门（架构规则、源码扫描、契约断言）后，故意造一个违例看它是否**真的红**——B10 实测两条空转：`noClasses().should(自定义条件)` 会把条件取反（手写 violation 被反转成通过）、匹配键写成带参数的方法全名则永不命中。**没探针的规则等于没规则**。
7. **注册类扩展点必须实测行为**：Spring 扩展点（`EnvironmentPostProcessor` 等）写错注册文件是**静默**失效（不报错不执行）；B6 批 10 实测 Boot 3.3 的 `EnvironmentPostProcessor` 仍走 `META-INF/spring.factories`，写成 `.imports` 被忽略。别只信「文件已就位」。
8. **拿日志/行为当证据前先读被验对象的类注释**：B6 批 10 实测——我曾把「没有 distributed=true 日志」判成文案写死，实为该类注释明确写的设计（分布式模式刻意不复位）。
9. **动包/类落点的批次，包结构守卫是必跑闸门（与测试同级）**：`python3 scripts/check-package-cycles.py`——B6 批 10 只跑了测试、漏跑守卫，把 5 组环带进 main（B33 才修复）；B34 分包批每步都用它自检。**反向用法**：新类该放哪个包，先跑一次守卫看红灯，比事后解环便宜一个量级。
10. **跨进程 JSON 键名按「权威」分桶，不按风格统一**（B72 定谳）：REST DTO=Java 字段名 camel（默认、零注解）；**SSE 事件载荷=对外权威面**（agent-chat 是集成页文档化 API，playground 按 snake `response_type` 解析——别当纯内部面翻转）；MCP OAuth/OIDC 连接器键=RFC/厂商规范原文；工具名/枚举值/错误码=词表不是字段。判据口诀仍是 B19 的「这个键是不是我们定义的、跨进程 JSON 字段」，外加一句「这个面有没有已发布的外部消费者」。两道棘轮（`scripts/check-fe-contract-keys.py` 41 条 + `crossFaceKeyContract.test.ts`）是执行面；python 守卫四个结构性盲区（TS 只盖 api 面/后端 camel 只认带引号字面量/后端 snake 全仓点亮/api-system-initialization 曾整面豁免）见 B72 记录。
11. **闸门的退出码必须显式取用——任何 `| grep | head` 都不能替它作证**（B162 实锤 ✗）：把 `./gradlew … build | grep … | head` 接进 `&&` 链时，管道退出码来自最后一段（`head`）⇒ **红着的构建照样提交并推送了**。正解：`cmd > log 2>&1; rc=$?; grep … log; [ $rc -eq 0 ] || exit 1`。与第 6 条同族：**闸门/规则必须能自证**。

**✅ B10（2026-10-02，代码级架构规则进 CI）**
- **前提核对**：B10 原描述「环 0 组基线 + 包依赖白名单固化」**已于 2026-09-30 在 CI**（`scripts/check-package-cycles.py` 挂 guards job，环/分层/域依赖 `config` 三项带基线棘轮）。本批的真实缺口是 **ArchUnit 级（代码级）规则**。
- 新增 `domains/src/test/java/com/ragagent/arch/ArchitectureRulesTest.java`（测试依赖 `com.tngtech.archunit:archunit:1.3.0`），四条**当前零违例**的规则：
  - **R1 禁裸 `System.getenv`**（守 B6 成果 149→0；含无参重载）；
  - **R2 `@ConfigurationProperties` 类必须被 `@ConfigurationPropertiesScan` 名单覆盖**（漏扫描 = **静默**取默认值，B6 期间反复踩）；
  - **R3 配置类不得同时 `@Component/@Service`**（双装配）；
  - **R4 `install*`（启动期写入查找面/快照）只许 `*.config` 装配层调用**（把批次 6~10 各类注释里的约束变成红条）。
- 分工写进类注释与 CI 注释：**包级**归脚本棘轮、**代码级**归 ArchUnit，不重复建设。
- 两条实现要点（都属「静态闸门盲区」同类）：① 导入面必须按输出目录过滤到 **main**（`importPackages` 会连测试类一起扫，而测试里读真实 env 是**合法**的——各 connector 桩要读宿主 env 拼 SSRF 白名单），并加「导入面 >500 类」自证断言防**规则空转**；② **`noClasses().should(自定义条件)` 会把条件取反**（手写 violation 被反转成通过），R4 因此一度「永远绿」。
- **验证（核心）**：造一个探针类（未扫描包 + `@Component`&`@ConfigurationProperties` 双注解 + 内部调 `System.getenv` 与 `AppEnvLookup.install`）同时触发四规则 → **四条确实全红**（首次只红三条，正是借此发现 R4 空转）→ 删探针复绿。闸门：`spotlessCheck` 绿 + 全量 **4697** 测试绿（+4）。命令：`./gradlew :domains:test --tests "com.ragagent.arch.ArchitectureRulesTest"`。
- 新增纪律已写入 §15.2 第 6~8 条（探针 / 扩展点注册实测 / 先读被验对象注释）。

### 15.3 非目标（冻结面，见 §14.6，勿列入修复）

**2026-10-08 重排（B92）**：冻结项按**理由**分三类——改前先核对理由是哪种、是否仍成立；
「因为要兼容历史数据」这一理由已**整体作废**（产品未上线、不保留历史数据），原先按它冻结的项移入文末「已解除」。

**① 外部决定（字段名由对方 API / 协议定）**
connector 第三方线格式（`datasource/connector/**`）、provider 请求体（`llm/**`：anthropic/openai/ollama）、
websearch metadata（`published_at`，`WebResultConverter` 写、ProviderJson 出站）、
image_info（docreader 容器：`original_url`/`ocr_text`/`start_pos`…）、rerank `RankResult`（`index`/`document`/`relevance_score`）、
MCP OAuth 载荷、langfuse（`lf_*`）、IM 平台 ACK、i18n 键与系统设置键（`tenant.default_storage_quota_gb` 等，文案/DB 字符串）。

**② 协议 / 行为面（模型输出契约与提示词方言；改 = 提示词正文 + 解析器 + 实录金片同批，需拍板）**
引用协议标记 `<kb>`/`<web>`/`<ref>` + `doc`/`chunk_id`/`kb_id`/`url`/`title`；
wiki 摄取契约标签 13 个（`new_information`/`candidate_slugs`/`previous_slugs`/`shared_source_contexts`/`deleted_documents`/
`document_summaries`/`existing_folders`/`page_metadata`/`remaining_source_documents`/`valid_wiki_links`/`available_wiki_pages`/
`existing_page_content`/`current_introduction`）；
chatpipeline/evaluation 模板标签 4 个（`asker_background`/`images_uploaded`/`no_image_attached`/`no_document_attached`）；
工具名 36 个与 schema enum 值（`list_servers`/`list_tools`…——值是各自语义，不是字段名）。

**③ 落库 / DDL 形态（与"历史数据"无关：改列名是 schema 迁移）**
MyBatis 列名与 SQL 参数、租户配置 jsonb 列、`agent_steps`/`messages` 等列名、检索引擎索引文档字段（ES/OpenSearch/Milvus/Qdrant DSL）。

**Go 零值时间哨兵**（`0001-01-01T00:00:00Z`）：属**形状**变更（前端既有依赖），与命名无关，另批。

**已解除（2026-10-08）——原先按"存量回放/历史数据"冻结，现按普通改造处理**

| 面 | 状态 |
|---|---|
| 工具输出/提示词标记（JSON 键、XML 属性/元素名、多词标签名） | ✅ 已完成（B88/B89/B90） |
| 引用载荷（`knowledge_references` / `data.references`） | ✅ 已 camel（B88），双读分支已清（B92）；**但 B135b1 复核发现"Redis 回放"的两个 Map 重建读者（`StreamResponseBuilder` / `AgentStreamBridge` 的 `searchResultFromMap`）仍按 snake 读 11 键**（唯独 `knowledgeBaseId` 是 camel ——冒烟枪）⇒ 该路径字段**静默为空**：已修为 camel + 结构化断言 + 红态证明 |
| SSE/Redis 事件载荷键（`session_id`/`tool_name`/`total_steps`…） | ◐ 部分完成——**B93b 已 camel 化 `event/` 与 `stream/`**（并从冻结名单摘除，另有 `check-event-face-case.py` 守）；**B134 完成 `chatpipeline/PipelineProgress`**（`candidateCount`/`docCount`/`webCount`/`searchSource`/`hasImages`）。**B135c 完成 mention/steer 载荷**（`mentionedItems`/`steerId`/`kbId`/`kbName`/`kbType`/`serviceId`/`skillName`——两处生产侧 `QaSupport` 与 `SteerController`、解析侧 `MentionedItem`、读取侧 `SteerIntake`/`SteerSinkBridge`；`mentioned_items` **作为表列名**保持不动 §15.3 ③）。**B135b1 修掉一处静默缺陷**（引用载荷的 Redis 回放路径，见「已解除」表引用行）；**B135b2 完成收尾**：SSE `agentQuery`/complete 事件的最后 3 个漏网键（`finalContent`/`userCreatedAt`/`assistantCreatedAt`）→ camel ⇒ **事件面自此无 snake 键**。前提复核结论：§14.9l 前提判定 2 的"已实测后端无这些键"**是错的**（后端就在写），而 B72 立项稿的"维持 snake（路线 C）"其实**已被 B93b 执行翻转**（含 `AgentStreamBridge` 38 键位 + 前端 43 文件）⇒ 3 键只是漏扫尾巴；立项稿与 FE 白名单的两处过期依据已同批销账。另：`check-event-face-case.py` 新增 ⑤——**手搓 map 形态的事件载荷键守卫**（B93b 自列的余项，覆盖 `event/payload` 之外的 8 个生产/消费文件，含 27 个工具名的值面豁免），红态证明：把 `userCreatedAt` 退回 snake ⇒ 该行即刻被抓 ✓。**B136 结案（负结果）**：wiki 摄取 36 键经逐站复核**属 span 观测面**（去向 `spans.endSpan(...)`、FE 零命中）⇒ **不改**，基线理由已订正（第 4 处误标：原写"内部 jsonb 状态（存量）"）；其中 `new_slugs`/`source_chunks` 两条是 **LLM 输出契约**（§15.3 ② 拍板项，仍在用）。余「agent_steps 同族」与落库面待续 |
| wiki 内容图片标记（`<image_caption>`/`<image_ocr>`/`<image_original>`） | ✅ 已完成（B93a：标记名 → `<imageCaption>`/`<imageOcr>`/`<imageOriginal>`；**chunk_type 值 `image_ocr`/`image_caption` 是枚举值，保留**） |
| 落库 jsonb 存量键（agent_steps payload、memory 抽取状态、租户配置内容） | ✅ **完成（2026-10-10 B205）**——**判定**：落库面键已全 camel ✓（B132/B133/B135a/B135d1 ✓）；残留 snake 字符串**全在登记冻结面**（provider 请求体 / DB 列名 / MCP 枚举值 / LLM 载荷 ✓）。**实测**：dev 库 `tenants` 六列 + `memory_subjects` 两列，仅 4 列共 6 行还有旧键 ✗ ⇒ 按口径「开发库可清，不写迁移脚本」**一次性就地修数** ✓（先备份 → 事务 → 幂等 ✓；修后旧键行数**全 0** ✓）。修数 SQL 是 dev 一次性操作，**不入库** ✗（要点在提交 `(B205)` ✓）。 |

