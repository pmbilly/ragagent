# ragagent 交接文档（新仓起步）

> 本文档写给在 `~/ragagent` 打开的新会话/新成员。**一切背景以本文为准**；最近的执行细节在 `git log`。
> 种子：自 `~/ragagent-java` @ `646aba7`（2026-09-28）分叉，git 历史完整保留。
> **最近更新 2026-10-02（B32 文档重组）**：§14 域级作战/执行记录（2269 行）与 §15.1.1 逐批记录（442 行）
> **已移出本文件**——`docs/handoff/plans/`（索引见 §14.7+）与 `docs/handoff/records/batch-records.md`（索引见 §15.1.1）。
> 本文件＝**常读面**：接手须知 / §2 决策 / §13 方法论 / §14.1~14.6 判据与坑 / §14.9 换锚总纲 / §15 批次总表与纪律。
> **当前状态总账**见 §15 末（结构各轴完成 + 5 项已知边界）。


## ⭐ 接手须知（5 分钟版）

0. **文档布局（2026-10-02 重组）**：本文件＝**常读面**（接手须知 / §2 决策 / §13 方法论 / §14.1~14.6 判据与坑 / §14.9 换锚总纲 / §15 批次表与纪律）；**查证面已移出**——域级作战与执行记录见 `docs/handoff/plans/`（索引见 §14.7+）、逐批执行记录见 `docs/handoff/records/batch-records.md`（索引见 §15.1.1）。
1. **先验证基线全绿**（三条命令见 §9；session 域单域验证：`./gradlew :server:test --tests "com.ragagent.session.*"`，当前 **388 条 / 失败 0**）。
2. **总目标**＝按 Java 标准提升可读性（§0）；**行为不变**是底线（测试是安全网）；已定决策见 §2（勿再讨论）。
3. **已完成**：P0/P1/P2/P3 包结构治理（§11.8~§11.14）；**session 域阶段 2 神类批次全收官**（§11 总览表）——
   `SessionAgentQaService` 1,430→**364**、`KnowledgeQaController` 1,614→**328**、`SessionQaResolution` 2,906→**706**，
   域内 ≥800 只剩 `SessionKnowledgeQaService` 1,036（§14.5 已登记例外）；
   **wiki 域阶段 2 已收官（2026-10-01，10 刀）**——`WikiPageController` 1,311→**272**、`WikiIngestService` 1,208→**509**（§14.7.2）；
   **im 域已收官（2026-10-01，5 刀）**——`ImService` 1,445→**664**（§14.7.4）；
   **retrieval 适配器批已收官（2026-10-01，9 仓 20 刀）**——sqlite/qdrant/milvus/tencentvectordb/weaviate/es8/es7/opensearch/doris 全部出榜（§14.7.5）；**HybridSearchService 已出榜（3 刀，1,260→775，§14.7.6）——检索域清零**。**AuthController 已出榜（3 刀，1,167→704，§14.7.7）——auth controller 清零**。**FaqImportService 已出榜（2 刀 F1/F2，1,235→583+419，knowledge 例外解除）**；
   **wiki page 面已收官（2026-10-01，w1~w5 + 两个卫生刀，§14.7.14）——`WikiPageServiceImpl` 1,008→732、`WikiPageRepository` 858→708、`WikiIngestDedupService` 851→468（例外解除）、`WikiPageFolderSupport` 822→383（切片产物空行折叠）——wiki 域 ≥800 清零**；
   **DataSourceService 已出榜（2026-10-01，d1~d3，§14.7.15）——1,828→666，四协作者**；
   **datasource 连接器批已收官（2026-10-01，f1~f3 + n1，§14.7.16）——`FeishuClient` 1,155→709、`NotionConnector` 1,093→625，datasource 域 ≥800 清零**；
   **memory 域四刀落定（2026-10-01，m1~m4，§14.7.17）——`MemoryExtractionService` 1,217→718、`MemoryRepository` 1,515→458、`MemoryService` 1,663→965（余 m5 评估）**；
   **memory 域契约换锚 M1 完成（2026-10-01，§14.9k）——20 端点去信封 + 6 响应实体 camelCase + 创建 201/删除 204 + 分页形态对齐；前端 8 文件同批（含 A2 漏改的 KV 解包）；真实服务冒烟 21 路通过**；
   **memory 域契约换锚 M2 完成（2026-10-01，§14.9k）——7 个落库/内部实体 + `MemoryConfig` 去注解 60 处（memory 域 @JsonProperty 137→22，余者为 LLM 载荷并登记保留）；`tenants.memory_config` 与 `memory_subjects.extraction_state` 两处 jsonb 已跑存量迁移 SQL；前端 2 文件同批；真实服务冒烟 11 路通过**；
   **memory 域 M3 收尾完成（2026-10-01，§14.9k）——请求侧手写 `rawBody+parse()` 全部退役（三个 DTO 进 `memory/dto` + `@Valid`），错误形态统一到全局处理器；LLM 载荷 22 处登记保留；真实服务冒烟 12 路通过**；
   **memory 域 m5 切片完成（2026-10-01，§14.7.17）——`MemoryService` 965→**768**（出榜），「召回」段外提 `MemoryRecallOps` 252 行；忠实性逐字核验通过；memory 域 ≥800 仅剩 `MemoryIndexStore` 929（已登记例外）**；
   **session 域 S1 完成（2026-10-01，§14.9l）——会话主资源全换锚**：实体面（`Session`/`SessionListItem`/
   `SessionLastRequestState`/`MentionedItem` 换 camelCase + 恒输出，`is_pinned`→**`pinned`**）+
   控制器面（请求体标准 DTO、列表 `{items,page,pageSize,total}`、置顶 `{pinned}`、产物裸数组、
   生成标题 `{title}`、删除类与停止 **204**、查询参数 `pageSize`/`agentId`），两处 jsonb 迁移 SQL 已备
   （dev 库 0 行需迁移）；**session 域 S1~S5 全部完成（S2/S3/S4 前后端同批，S5 后端面）——`@JsonProperty` 188 → 0**；
   **阶段 3 打样已跑通（2026-10-01，evaluation 域，§14.9b）——去信封 + camelCase + 标准 DTO 绑定，真实服务冒烟 8 路通过**；
   **阶段 3 第二域 model 全域收官（2026-10-01，§14.9c/§14.9e）——主资源 + debug + weknoracloud + 落库 jsonb 四块换锚，`@JsonProperty` 87→0，前端 15 文件同批（首次前后端同 PR）**；
   **阶段 3 其余域全部收官（2026-10-01~10-02）**：system（§14.9f/g）/ auth（§14.9h/i/j）/ memory（§14.9k）/
   mcp（§14.9n/o/p，余 22 处第三方协议面冻结）/ session（§14.9l，S1~S5，`@JsonProperty` 188→0）/
   embed（§14.9m）/ datasource（§14.9q，D1~D3，余 350 处 connector 线格式 + 5 处 `lf_*` 冻结）；
   **wiki ingest 载荷 W1 收官（2026-10-02，§14.9r）**；
   **契约尾巴全清 + 两个真单类出榜（2026-10-02，§14.9s，15 个提交）**——全局错误体去 `success:false` +
   622 个错误金片重录；散存量七域批（audit/favorite/im/storage/vectorstore/auth 补刀/agentm·init）+
   M6（mcp `@JsonInclude` 恒输出）；`SourceRegistry` 878→534、`UserService` 876→748 切片出榜；
   4 个登记例外复核结论入册（§14.3）。**至此 ⭐ 第 4 条所列全部剩余工作完成：
   复查批后全仓 `@JsonProperty` 余量 913 处全部是登记冻结面（§14.6）**。
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
- 后端测试：**4,681 用例全绿**（含 6 个 skip，2026-09-30 复核；2026-10-01 model 域收官批实测 **4,670 / 失败 0 / 跳过 4**，434 测试类）；前端 `vue-tsc` 0 错误 + **690 用例全绿**（§9 有命令）。
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

> 注意：同包引用不产生 import，以下只列**跨包**缝合点；包内调用方（如 SessionAgentQaService 调 SessionSandboxExecutionService）在删服务类时编译器会全部指出。

### 6.1 第一批移除：五功能（§2 第 5 条）

**① CLI / Chrome 插件 / Claw Skill —— 纯前端集成页，无任何后端代码**：
- `frontend/src/config/integrations.ts`：`IntegrationTab`/`INTEGRATION_TABS`/`INTEGRATION_PREVIEW_ITEMS` 去掉 `cli`/`chrome`/`claw` 三项，删 `CHROME_EXTENSION_URL`（Chrome 商店外链）与 `CLAWHUB_SKILL_URL`（clawhub.ai 外链）两个常量
- 删三个 landing 视图：`views/integrations/CliIntegrationLanding.vue`、`ChromeExtensionLanding.vue`、`ClawSkillLanding.vue`（+ `cliIntegration.ts` 及其测试）
- i18n 五语言对应文案、`settingsRoute` 相关测试同步更新

**② 浏览器连接（browserskill，3.3k 行）**：
- 后端删整个 `browserskill/` 包：端点族为 `/api/v1/me/browser`（BrowserSkillAccountController）、`/api/v1/sessions/{id}/local-browser`（BrowserSkillSessionController）、BrowserSkillGatewayController；跨包引用仅 2 处——`agent/AgentConsts.java`（常量）、`session/controller/SessionController.java`
- 前端删：`views/settings/BrowserConnectionSettings.vue`、`BrowserSearchPreferences.vue`（+测试）、`stores/browserConnection.ts`（+测试）、chat 的 `BrowserTaskPreview.vue`/`BrowserToolDetails.vue`、`AgentStreamDisplay.vue` 内 browser 工具展示分支、`Settings.vue` 导航项

**③ 沙箱（sandbox，26.6k 行）——量最大，8 个跨包引用文件；技能按选项 B 降级（§2 第 10 条）**：
- `config/SandboxWiringConfig.java`（装配类，随沙箱整体删）
- `agent/skills/TenantSkillSource.java`（租户**镜像**技能源，删；但 `Skill/Loader/Manager` 提示词注入路径**保留**）
- `session/service/`：`SessionSandboxExecutionService`、`SessionTerminalService`、`TerminalBridge`、`InstallEngineFactoryImpl`、`SessionBoundArtifactSource`、`SessionAttachmentStagingService`
- 技能侧连带删除：安装管线（`sandbox/service/SkillInstallPipelineImpl` 及快照/镜像指针切换/reaper 的镜像部分）、`Manager.prepareShellEnvironment`（shell 环境注入）与技能 staging 进 `/workspace` 的路径、安装器专用工具 `WriteSkillFileTool`/`EditSkillFileTool`、前端技能的**上传/安装/install-events SSE/transcript/reinstall/stop** 页面与 API
- 技能侧保留：SKILL.md 加载与系统提示词注入、agent config 的 `skills_selection_mode/selected_skills`、前端技能**选择器**
- 沙箱侧连带清理：agent config 的 `sandboxConfigId` 字段；`agentm/builtin_agents.yaml` 的 `builtin-skill-installer` 角色；system_settings 的 `sandbox.docker_enabled` 键；`SessionAgentQaService` 的 `holdSandboxTurn`/沙箱工具注册调用点；docker-java/远程沙箱（Cube/E2B）相关依赖与配置；前端 `SandboxSettings.vue`
- **PR3 唯一设计项**：指令型技能的来源——内置静态 SKILL.md（classpath）或简化版 DB 目录（上传 bundle 只存档+注入，去掉"装依赖+验证+快照"步骤）；建议先做内置静态源跑通、DB 目录随后

### 6.2 可选裁剪项状态

- `org`（空间分享）：**已完成裁撤（2026-09-29，d4d63e0）**——org 包、KB/Agent shares 端点族、跨租户开关（enableCrossTenantAccess）、租户目录发现面（/tenants/all|search）、共享七表（V1__baseline 第二版）；保留面：同空间成员管理、/auth/invitations、个人多空间切换。AgentResolver 已改本租户直查；AgentResponses 内联 agentConfigMap。
- `datasource` / `evaluation` / `favorite`：**零外部引用**，随时可纯删（datasource 前端在 KB 设置面板 `views/knowledge/settings/DataSource*.vue`）。
- `im`（1 个跨包引用）：`config/ImAdapterWiringConfig.java`（+ im 包内回调 controller 自删）+ 前端渠道设置页（integrations 的 `im` tab）。

## 7. 当前状态（2026-10-01 复核）

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
   memory 域**两条线都已走完**（切片 m1~m5 + 换锚 M1~M3，≥800 只剩登记例外 `MemoryIndexStore` 929）；
   单类：modelcontext(`SourceRegistry` 878)、auth service(`UserService` 876)、knowledge 例外 2 个；
   另登记：wiki 域 "原 ORM / 原实现" 措辞 19 文件（约 50 处，独立卫生批）、datasource 域 Go 锚点（`对照 Go` 多处，
   随连接器批清）、`SessionKnowledgeQaService` 1,036 例外复核。
2. **阶段 3 契约换锚**：**部分已执行** —— knowledge / retrieval / chunker-preview / evaluation / model / system /
   auth（A1+A2+B）/ **memory M1+M2+M3** / **session 全域收官（S1 会话主资源 → S2 消息面 → S3 附件·建议·steer → S4 QA 请求面 → S5 收尾，前四批前后端同批）** / **embed 域 E1（渠道管理 + 公开面，前后端同批）** / **mcp 域 M1（服务资源 + 凭据面）+ M4（工具审批 + OAuth 用户面，均前后端同批）** 已完成（同批带前端）；
   **mcp 域已收官（M1 + M4 + M5，仅剩第三方协议面 22 处永久冻结）**。
   **datasource 域已收官（D1 + D2 + D3，仅剩 connector 第三方线格式与 `lf_*` 共享载具，均冻结）**。
   **2026-10-08 复核**：域级换锚已完成——全仓剩余 `@JsonProperty` **426 处全部落在已登记冻结面**（datasource connector 126 / event 载荷 92 / auth tenantconfig 89 / llm provider 62 / SearchParams 19 / mcp oauth 18 / memory·stream·retrieval·tracing·wiki·rerank 25），无在办候选；工具面另已于 **B88（JSON 键）+ B89（XML 形态）** 全量换锚。
   硬约束：**序列化层删除必须一次性全仓完成**，半删状态最危险（§5 阶段 3）；时机由用户定，可与阶段 2 对调。
   **入场前先做**：§14.9 的"端点 × 前端"清单盘点。
3. **阶段 4 其余域标准化 + 架构调整**（Gradle 多模块 + ArchUnit 边界固化等）：未开始。
   **盘点与方案已出（2026-10-08）**：`docs/phase4-module-boundaries-plan.md`——实测 30 包 / 1,862 文件 / 284.5k 行；守卫 R1 只覆盖"两两双向"环（0 组），但包级 SCC 实测 **2 个间接环**（`{audit,auth,knowledge,model,retrieval,storage,wiki}`、`{config,im,session,stream}`）；切割清单 C1~C8（1 处一行搬家 + 7 处端口化）、目标 6 模块图、ArchUnit 规则 8 条、批次建议 B91~B97。ArchUnit 1.3.0 已是测试依赖。
4. **编号对照（防混淆）**：§5 用**阶段 0-4**；§14.2 用**步骤 0-4**（单域 SOP）。神类切片属「阶段 2 / 步骤 2」，
   契约换锚属「阶段 3 / 步骤 4」——两套编号并存，引用时写全称。
3. 可选尾巴：`QaSearchTargets`（706）内部 4 块细分；`SessionKnowledgeQaService` 1,036 的 §14.5 例外复核。

## 8. 环境与运行

- **后端端口改 8083**（避开旧仓 8082 本地走查环境）。
- **PG 独立库名**（建议 `ragagent`）：基线合并会改 schema，不能与旧仓共用 dev 库；docker-compose 里 ParadeDB/Redis 实例可共用，建新库即可。
- 前端开发代理：`VITE_DEV_PROXY_TARGET=http://localhost:8083`。
- `.env` 已从旧仓原样复制（未入库，gitignore 正常），**待改** `SERVER_PORT` 与库名；`SYSTEM_AES_KEY` 可沿用。
- **`LOCAL_STORAGE_BASE_DIR` 必须放持久目录、严禁 /tmp**（旧环境实测踩坑 2026-09-28：放在 `/tmp/weknora-java-files`，macOS 定期清理 /tmp 导致已入库文档原始文件丢失——文档列表正常、检索可能正常，但 preview 全 500、重处理报 "failed to read file"，原始文件不可恢复只能重传）。建议 `~/ragagent-data/files` 之类仓库外持久路径。
- CI 起步三样：build、test、Spotless；ArchUnit 规则留到阶段 4。
- **远程仓库（2026-09-30 起）**：`origin` = `https://github.com/pmbilly/ragagent.git`（**公开**）；首次推送只推了 `main`（`bbf7443`），**wip 分支与 tag 都留在本地**。此后本地提交若要同步，记得 `git push`（并行会话在同一仓库提交、同样落在 main，也需推送）。

## 9. 测试与安全网

- 434 个测试类 / 1,366 契约 fixture（**4,670 用例 / 4 skip**，2026-10-01 实测）是重构回归网，**每一步（哪怕纯移动）结束都必须全绿**——近两轮的工作方式就是"改一步 → 全量验证 → 提交"。这是"种子 fork + 渐进转型"优于重写的全部意义。
- **三条验证命令（接手先跑一遍确认基线）**：
  ```bash
  # 后端全量（约 3 分钟；期望 BUILD SUCCESSFUL，4,670 用例 0 失败）
  cd ~/ragagent && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :server:test
  # 前端类型检查（期望 0 错误）
  cd ~/ragagent/frontend && npx vue-tsc --build --force
  # 前端单测（期望 pass 690 / fail 0）
  cd ~/ragagent/frontend && npm test
  ```
- 已知偶发：`WebToolsRecordingTest.searchWithContentFetchesLeadingPagesViaSharedFetchTool` 在全量并发下**偶发失败**（单独重跑通过，与代码改动无关）；遇到它单独重跑确认即可，别误判为回归。
- 已知偶发（第二个）：`EvaluationContractTest.getTerminalRunsExecution` 在**全量并发**下偶发失败
  （期望 `model ID cannot be empty`、实际空串；单独 `--tests "*EvaluationContractTest"` 重跑通过）。
  与代码改动无关，遇到时先单独重跑确认（2026-09-30 首次观察到）。
- 裁剪功能的测试/fixture 随 PR 删除；阶段 1 末对比器改 **JSON 语义对比**后，fixture 锚定的是**本仓自己的行为**，与 Go 再无关系（键序/转义差异不算失败）。
- A/B 对拍脚本与 `artifacts/` 产物未带入本仓（留在旧仓）。

## 10. 考古指引（需要时去旧仓查）

- 某行为为什么是这样：旧仓 `docs/HANDOFF.md`（翻译约定正文）、`docs/known-issues/`（坑史，尤其 04 沙箱/技能卷、05 事件契约/工具/引擎卷）。
- 某行代码来历：直接在本仓 `git blame`（seed 前历史完整保留）。
- 架构总览（裁剪前状态）：`docs/site/` 门户、`architecture.html` / `agent-workflow.html` 交互图、`api/` 452 路由清单——阶段 1 后按新形态重生成。

### 7.1 存档：知识库域 Java 本位重构完成（2026-09-29）

**范围**：阶段 2 中 knowledge 域的完整改造（方案见 `docs/superpowers/plans/2026-09-29-knowledge-module-java-refactor.md`，分支 `refactor/knowledge-java-idioms`，17 任务 17 提交）。

**成果**：
- **神类拆分**：FaqService(3,086 行门面) → Guard/ChunkCodec/IndexWriter/ImportTaskStore/EntryCommand/EntryQuery/Import 七类删除门面；ChunkService(1,296) → ChunkEdit/ChunkQuestion + 写守卫并入 ChunkAccessGuard；KnowledgeSummaryPipelineService(1,114) → Summary/File/Parse 三服务；KnowledgeProcessWorker 阶段方法化。KnowledgeController(1,171) 拆为文档主面 + KnowledgeOpsController。
- **DTO 化**：知识库 7 个 controller 的 34 个 rawBody 端点 → `@Valid` DTO（30 个）+ 3 个固定文案兜底端点保留手绑 + 1 个 multipart；`FaqDtos` 分域为 Entry/Import/Search 三文件；请求 record 用 `@JsonNaming(SnakeCaseStrategy)`；响应统一 ApiResponse/MessageResponse/DataMessageResponse；`GlobalExceptionHandler` 增补四类校验异常 → 400 字段级中文 details（`NonNullBody`/`PageParams` 基建在 common/web）。
- **注释本位化**：知识库包 main 源码 Go/GORM/对照 锚点清零；ChunkRepository 类 javadoc 重写为本仓数据契约；goTrimSpace/goTimeString 等 go 前缀标识符改名；6 个 package-info 导览。
- **数据访问**：FAQ 仓储段独立为 FaqChunkRepository；ChunkRepository 死方法（saveChunks/deleteUnindexedChunks）删除。

**契约变化（唯一且已核实）**：参数校验错误的 `details` 从 Go validator/strconv 复刻文案换为字段级中文（message 类别文案统一"请求参数不合法/分页参数不合法"）；约 60 个错误 fixture 重录；200 路径 fixture 零改动（比较器语义化前置）。前端零改动（三绿验证）；`error.code` 字符串、半成功 200、信封形态均保持。

**量化**：knowledge 包 main ~26k 行；controller 层 3,780→2,940 行且全部 <500 行；>800 行仅剩 FaqImportService(1,243 导入状态机)/KnowledgeService(~830 门面聚合面) 两例外（javadoc 注明）。4,664 后端测试全绿 + 前端三绿。

**下一步候选**：agent 域五神类（阶段 2 另一半）、阶段 3 全局换锚（Go 序列化器在 DTO 上的注解仍保留）。

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
> （判据已并入 13.1~13.7 与 §14.5）；本仓当前编号到 **13.8** 为止，新条目从 13.9 继续。

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
CLI 起服务常写 `set -a && . ./.env && set +a && ./gradlew :server:bootRun`；Agent 工具链会
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
  ./gradlew :server:test --tests "com.ragagent.embed.EmbedContractTest" -Dcontract.refresh=true
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
   `clean :server:test :spotlessCheck`（~3m25s）。**判据：抓断链靠"强制重编"（便宜），防跨域行为回归才靠全量（贵）**
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

复测命令：`git ls-files 'server/src/main/java/**/*.java' | xargs wc -l | sort -rn | head -20`

### 14.4 体检命令（复制即用）

```bash
cd ~/ragagent
# 神类/大文件排行（全仓）
git ls-files 'server/src/main/java/**/*.java' | xargs wc -l | sort -rn | head -25
# Go 债务：锚点注释 / 逐字段 @JsonProperty / 手写 rawBody 绑定
git grep -cE '对照 Go|GORM|Go 的' -- 'server/src/main/java/**/*.java' | sort -t: -k2 -nr | head -15
git grep -c '@JsonProperty(' -- 'server/src/main/java/**/*.java' | sort -t: -k2 -nr | head -15
git grep -nE '@RequestBody\s+(String|Map<|JsonNode|Object)' -- 'server/src/main/java/**/*.java'
# 卫生闸门（ratchet：只覆盖 seed 后触碰过的文件，这是设计不是遗漏）
./gradlew :server:spotlessCheck
# 收尾闸门（按改动分档；实测：重编 ~31s / 单域 ~30s / spotless ~10s / 全量 ~2m50s / clean 全量 ~3m25s）
# ① 常规批（同包抽协作者、成员增删、卫生、文案）≈ 1m10s
./gradlew :server:compileJava :server:compileTestJava --rerun-tasks   # 专抓"引用被搬走"的断链
./gradlew :server:test --tests "com.ragagent.<受影响域>.*"
./gradlew :server:spotlessCheck
# ② 结构搬迁批（跨包 git mv / 改共享 API / 删类）≈ 3m25s
./gradlew :server:clean :server:test :server:spotlessCheck
# ③ 迭代中：单类/单域（秒级）
./gradlew :server:test --tests "com.ragagent.session.service.SomeTest"
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
      **结构搬迁批** = `clean :server:test :spotlessCheck`（+ 触及前端契约时 `vue-tsc` 0 错误 / `npm test` 全绿）
- [ ] §4 数据、§12 地图、§13 经验、本节候选表四处同步更新

### 14.6 不要做什么（踩过的坑，别再踩）

- **别把"Go 序列化层删除"拆到各域**：409 处引用 / 94 文件的那一刀按 §3 红线必须**一次性全仓完成**。
  按域先换锚（同 PR 带前端）是允许的，删序列化器本体不是。
- **别动冻结面，但先核对理由**：现行口径见 **§15.3**（2026-10-08 重排为「外部决定 / 协议行为面 / 落库·DDL」三类，并单列「已解除」）。
  以旧结论（"工具输出自有 schema""存量回放面"）为由拒绝改造前，先看 §15.3 的理由栏是否仍成立——
  「兼容历史数据」已整体作废；「协议/行为面」要连提示词与解析器同批且需拍板；「外部决定」才是真不能动。
- **别为数字写注释**：getter/POJO 访问器保持 0 javadoc（§13.8 第 3 条）。
- **别做全仓文本替换**：先用单文件验证再决定扩大（§13.2 的两次翻车）。
- **别跳过闸门**：只跑 `:server:test` 会漏掉 Spotless（§13.9）。


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
- **无未完成项**。残留 `@JsonInclude` ~288 处与 Go 零值时间哨兵为登记尾巴（§14.6 / ⭐ 第 4 条），非在办。

**存量表（2026-10-01 盘点实测，§14.9 第 1 步交付物；只读扫描，三分法甄别）**

`@JsonProperty` 全仓 1,978 处 / 221 文件，**不是都是债**：

| 类别 | 量级 | 处置 |
|---|---|---|
| ① 外部 API 映射面（第三方 snake_case 合法映射） | 346 处 / 24 文件（feishu/yuque/ima/gitlab/notion 等 connector+client） | **保留**（映射外部 API 不是 Go 债） |
| ② §11 已登记边界面（SSE/Redis 事件载荷、provider 请求体、手搓载荷、agent config jsonb） | event 155 + agent(`AgentConfig`) 14 + stream 9 + tracing 7 + llm 大部（provider 面） | **保留**（§14.6 边界清单；动它=改事件契约，须独立切片） |
| ③ 真·阶段 3 存量（HTTP 契约面 + 落库 jsonb 面） | **~897 处 / ~150 文件**，重域：auth 247 / datasource 127 / memory 123 / mcp 110 / system 86 / **wiki 39 → 0（W1 收官，§14.9r：17 键换锚 + 8 处死注解摘除；余 22 处 = `lf_*` 5 + LLM 解析面 17，登记冻结）**；evaluation 62 → **0**（打样，§14.9b）；model 87 → **0**（四块收官，§14.9c/§14.9e）；**session 188 → 0（S1+S2+S3+S4+S5 全部收官，§14.9l，含 5 处落库 jsonb 迁移 SQL）**；**embed 23 → 0（E1 收官，§14.9m）**；**mcp 119 → 58（M1，§14.9n）→ 35（M4，§14.9o）→ 22（M5 收官，§14.9p；余 22 处全是第三方协议面：RFC 8414/9728/6749 文档 + 授权服务器 token 响应，永久冻结）**；**datasource 493 → 417（D1）→ 366（D2）→ 355（D3 收官，§14.9q）：余 350 处为 connector 第三方线格式 + 5 处 `lf_*` 平铺载具，**均为永久冻结** ⇒ 该域可换锚面 0**。**2026-10-02 两段收官判定**：域级换锚（§14.9r）后散尾巴约 77 处由 §14.9s 七域批处理完——甄别结果：
真存量已换锚（audit 1+信封、favorite 5、im 17、storage 24、retrieval WebSearchResult 7、TempKbState 3、
APIPrincipalConfig 5、RuntimeStat 5 死注、WebSearchResult 死注）；**判冻结新增登记**：image_info（docreader
第三方）、SearchParams（chat span 载荷）、RankResult（第三方 rerank API）。复查批后全仓 `@JsonProperty` 余量 **913 处
/ 102 文件** 全部是登记冻结面（§14.6）⇒ **③ 类真存量 = 0，阶段 3 收官**。⚠️ **计数口径**：`QaRequests` 那批用的是全限定注解（`@com.fasterxml…JsonProperty`），只 grep `@JsonProperty` 会漏——盘点时两种写法都要扫 | 按域推进，一域一 PR 同批带前端 |

`@JsonInclude`（Go omitempty 直译）存量：**~487 处**（NON_EMPTY 256 / NON_NULL 123 / NON_DEFAULT 108；ALWAYS 19 处是正确形态的显式 null，保留）。
`@JsonNaming` **0**、Problem Details **0**、Go 序列化器线上引用 **0**（2026-09-30 已一次性删除）。
Controller 全仓 52 个；每域 PR 入场时再做该域的"端点 × 前端调用点"细清单（§14.9 执行顺序第 3 步的入场检查）。

**执行顺序（关键约束）**
1. **先做清单盘点**（低风险、只读）：按域扫出「未换锚端点 + 对应前端调用点 + 涉及的 Go 序列化残留（注解 / jsr310 / NON_NULL / Problem Details 引用）」，
   产出一张存量表（形如 §14.3）。
2. **再一次性全仓删除序列化层**（§2 第 7 条已删的 156 处注解 + `JacksonConfig` 是第一批；剩余引用按清单扫净）——
   **不允许半删状态**（§5 阶段 3 红字：一部分端点走 Go 格式、一部分走标准 Jackson 最危险）。
3. 端点/落库面**按清单逐域推进**：一个域一个 PR、同批带前端、重录 fixture。

**验收（Acceptance）**
- 全量测试绿（当前口径 **4,670 用例**）；每一步"改一步 → 全量验证 → 提交"。
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

| 批 | 内容 | 优先级 | 量级 | 状态 |
|---|---|---|---|---|
| **B0 端到端真实走查** | 起服（后端 8083 + 前端 dev）按域走查 14 条链路：注册/登录/**令牌刷新**/登出 → 空间创建/切换/成员邀请/**审计页** → KB 创建/摄取/**处理时间线**/预览 → 检索/对话（SSE）→ wiki 浏览/编辑 → datasource（RSS 桩）同步/凭据 → im 渠道 CRUD → vectorstore/storage 设置 → 收藏/技能目录/模型调试/系统运行时页。每条记录 API 形状 × 视图渲染 × 控制台报错 | **P0** | 1-2 天 | ✅ **完成（2026-10-02）**——14 条链路全走通，修 15 处断点（前端 12：裸体未适配 8 + 字段漂移 4 族；后端 2）+ 1 处 dev 环境配置；登记 5 项残留（含 B3b′ 升格）。详见 15.1.1 |
| **B1 契约文档同步** | `docs/knowledge-api-contract-v1.md` v1.0→v1.1：错误体、裸信封/裸数组、游标分页、恒输出、204 语义、七域差异表 | P0 | 小 | ✅ |
| **B2 金片对比器统一** | `support/GoldenContract` 共享基建（deep 归一 + strip + 单一 refresh 开关）；字节级（`goldenBytes`/裸 compare）与语义级双轨并存 → 语义单轨，存量字节级测试逐个迁移 | P0 | 中 | ✅ |
| **B3 `@JsonInclude` 恒输出化** | 真面 68 处（19 文件：wiki domain 全家 + websearch 三 DTO + VectorStoreTypes）；冻结面豁免（tenantconfig/LLM 载荷/event/tracing/common/agent/stream + connector + lf_*）；每域重录夹具 + 前端键集合核对 | P1 | 中 | ✅（**B3b 已登记并已执行**——见下行） |
| **B3b KB 配置 jsonb 键名统一（camelCase）** | 由 B0 走查升格为真实缺陷：`knowledge_bases` 的 `*_config` 列三方咬合面（前端 payload / 服务端读取器 / 落库 jsonb）键名分裂，导致界面上的 wiki 合成模型、问题生成参数、索引开关被静默忽略。服务端读取器 + 更新路径 dispatch 键 + 前端 payload/读取/类型 + V2 存量迁移 + 列默认值（连带修掉「编辑弹窗恒打不开」的裸资源读取） | **P1** | 中 | ✅ **完成（2026-10-02）**——详见 15.1.1 |
| **B4 Go 零值时间哨兵 → null** | `0001-01-01T00:00:00Z`（AgentStep / agentm GO_ZERO_TIME / init goTime 系） | P1 | 小-中 | ✅ **结论：不改**（调查后判已知例外，见 15.1.1） |
| **B5 lf_* 载具嵌套化** | 四域队列载荷的 `lf_*` 平铺键 → 嵌套 `tracing` 键 | P1 | 判定小 | ✅ **完成（2026-10-02）——判定：做**（载荷只在进程内队列流动、无外部消费面）；五载荷改嵌套 + 两域两种空值形状（三域省略 / 知识域按模块约定恒输出）；详见 15.1.1 |
| **B6 getenv 收敛 151 处** | 裸 `System.getenv()` → `@ConfigurationProperties`，按域分批 | P1 | 中 | ✅ **完成（2026-10-02，批 1~10）**：storage 装配 / langfuse / 检索驱动 / 系统部署面（含 `GIN_MODE`→`WEKNORA_DEPLOYMENT_MODE`）/ 知识域单值 / common 静态工具族（**启动期快照口径确立**）/ 存储静态单值族 + JWT / 存储 provider 环境族查找面（**storage 域归零**）/ 检索域引擎命名·开关·超时族（**retrieval 域归零**）/ 收尾批（全局查找面 + `EnvironmentPostProcessor`）——**全仓裸 getenv 代码内 149→0**（余 7 处为注释引用）；十批明细与两处新机制见 15.1.1 |
| **B7 死成员清扫** | 只注入不读取依赖（依赖级口径）+ 死 logger/`ObjectMapper`/`Pattern`/私有方法/冗余 import | P1 | 小-中 | ✅ |
| **B8 注解形态收尾** | 全限定名注解 → import 短名；`@JsonIgnoreProperties` 44 处接工厂评估 | P2 | 小 | ✅（FQ 177→0；`@JsonIgnoreProperties` 评估后保留） |
| **B9 Go 锚点注释清洗** | ~6,000 处；按 §4 既定"随触碰清洗"继续；若专项则按域分批 | P2 | 大（专项）/零（随批） | ✅ **机制已落地（2026-10-02）**——政策不变（随触碰、先摘不变量再删锚点、不立专项）；新增 `scripts/check-go-anchors.py` 棘轮守卫（按文件只许减不许增）+ CI guards job 一行；main 基线 3,999 处 / 1,029 文件，探针证伪过。**2026-10-03 用户立项专项，B48 一次清扫收官，基线已刷新 0**。详见 15.1.1 |
| **B10 ArchUnit 边界规则进 CI** | 环 0 组基线 + 包依赖白名单固化（§5 阶段 4） | P2 | 中 | ✅ **完成（2026-10-02）**——包级部分已于 2026-09-30 在 CI（脚本棘轮 guards job）；本批补**代码级**四条（禁裸 getenv / 属性类须被扫描覆盖 / 配置类不双装配 / install* 只许装配层）+ 探针自证会红。详见 15.1.1 |
| **B11 Gradle 多模块** | 按域拆模块（§5 阶段 4 尾） | P2 | 大 | ✅ **判定：不做（2026-10-02 搁置）**——立项目的（边界固化）已由 B10 + 包级棘轮达成；量化：冷编译 32s vs 全量测试 ≈2m50s（85% 在测试），拆模块收益≈0 而成本数天；重访触发条件已写死。详见 15.1.1 |
| **B12 B0 残留批 1** | wiki 任务级死信释放槽位（②）+ 孤儿 op 启动重放（③）+ 裸 NUL 审计盲区（R5） | P1 | 小 | ✅ **完成（2026-10-02）**——详见 15.1.1 |
| **B13 B0 残留批 2** | 孤儿存储组件判定与删除（④）+ `/auth/config` 版本信号消除登录 403 噪音（⑤）+ `process_overrides` 移植缺口判定 | P2 | 小 | ✅ **完成（2026-10-02）**——详见 15.1.1 |
| **B14 存储读侧投影合并** | 引擎面 env 回落行 vs 落库面类型化记录（两套词汇） | P2 | 中 | ✅ **完成（2026-10-02）**——合并为「一面一源」（落库面 camel、引擎面由唯一次名器派生）+ 修掉两个同源静默 bug（① 非 minio 行凭据被丢；② 环境供给行读回来为空），含红态证明、消费者层断言与真机验证；另登记供给器明文落库（未修）。详见 15.1.1 |
| **B15 供给行明文落库** | 供给器绕过加密直写 jsonb | P2 | 小 | ✅ **完成（2026-10-02）**——抽出唯一读写口 `StorageConfigCodec`（存储服务与供给器共用），真机 A/B 证明凭据由明文转为 `enc:v1:`；全量绿。详见 15.1.1 |
| **B16 静默失效定向扫描** | 枚举「静默丢数据」机制点并逐对核写读词汇 | P1 | 中 | ✅ **完成（2026-10-02）**——未发现新缺陷（负面结果如实记录）；产出两个此前不存在的守卫（EPP 运行时守卫 + 契约面键名防回流守卫，均含红态证明）；附带盘出 B17 换锚欠账清单。详见 15.1.1 |
| **B17 换锚收尾** | 残余逐字段 `@JsonProperty`（我方 ≈57）按「零风险 / 动形状 / 冻结」判定后分批清 | P2 | 小~中 | ✅ **完成（2026-10-02）**——判定批（三类 + 承重注解判据）+ 安全子集 6 处 + **websearch 请求键 camel 收口**（真机 A/B 实证）；其中 **agent 配置面**原判「不做」，后经用户决定由 **B18** 完成（见下一行）。详见 15.1.1 |
| **B18 agent 配置面换锚** | agent 配置键（~60 个）+ 前端同批 | P1 | 大 | ✅ **完成**——键面清单系统性低估纠缠度（前端命中 42 文件 / 850 处）；**副产品**：B20 发现的令牌误伤、B21 组键收口均源于此批遗漏。详见 15.1.1 |
| **B19 前端契约键收口 + 守卫** | 前端 snake 契约键排查 + 嵌套层 + 双侧守卫进 CI | P2 | 中 | ✅ **完成（2026-10-02）**——修掉两处静默缺陷（B18 的 ModelService 回归、前端 tagScopes 链路断）+ 嵌套 7 键 camel + V3 扩到 66 键（并修 WHERE 漏行）；新增前端契约键棘轮（进 CI）与后端键名守卫测试。详见 15.1.1 |
| **B20 占位符令牌回归** | 批量改名误伤数据值（模板令牌）+ 缺跨面对照守卫 | P2 | 小 | ✅ **完成（2026-10-02）**——修回 `knowledge_bases` 令牌（夹具曾被同步改掉=假绿），新增「HTTP 面 ↔ 渲染面令牌一致」守卫（含红态证明）；组键半改状态待拍板。详见 15.1.1 |
| **B21 占位符组键收口 + 同类排查** | 只改一半的「混搭面」与被误伤的数据值 | P2 | 小 | ✅ **完成（2026-10-02）**——3 组键收口 camel（模板面有意不动）；两道筛子给出同类清单：模板面 4 键、`api_key`、死模块 `api/web-search.ts`、4 个待判孤儿夹具；「令牌被误伤」**只有已修的一处**。详见 15.1.1 |
| **B22 模板面 4 键 + api_key 收口** | 混搭面续清（只读派生视图，无写回副作用） | P2 | 小 | ✅ **完成（2026-10-02）**——模板面 4 键与 `api_key` 改 camel（含 FE 映射/读者/夹具/测试侧归一化），删死模块 `api/web-search.ts`；另：文本资源名与 DB 列名等数据值一律未动。详见 15.1.1 |
| **B23 孤儿夹具审计** | 夹具「真被引用」判定（行为式，不按名猜） | P3 | 小 | ✅ **工具 + 首审完成（2026-10-02）**——1344 个夹具中 115 个为孤儿（≈8.6%，聚类见 15.1.1）；本批未删，等拍板 |
| **B24 换锚长尾回头扫** | 注解面清零；新增 payload 键面盘点与棘轮 | P3 | 中 | ✅ **完成（2026-10-02）**——盘点 + 逐族判定收口（B25~B29）：注解真债 0；payload 键面 309 键/119 文件全部判定（68 文件/324 键例外逐条带理由），**棘轮进 CI**。详见 15.1.1 |
| **B25 批甲（首面）** | 模板标志位收口 + 引用/进度载荷判冻结 | P3 | 小 | ✅ **完成（2026-10-02）**——`hasKnowledgeBase`/`hasWebSearch` 收口（YAML 输入面未动）；引用载荷因落库+回放+前端重建而冻结；并纠正 B24 筛子的「FE 可见」低估 |
| **B26 批甲续（websearch 凭据面）** | 逐面看消费者链 | P2 | 中 | ✅ 🐞 **修掉真 bug 并收官（2026-10-02）**——凭据面三处键名错位（保存静默失效 / 删除 400 / 徽标恒「未配置」），金鹰记录的缺陷态一并纠正；另四面判冻结并入 BASELINE |
| **B27 批甲续 2** | 推荐问题键收口 + 6 类面判冻结 | P3 | 小 | ✅ **完成（2026-10-02）**——`knowledgeBaseId` 收口；观测面/内部预设名/agent_steps 落库桶/存储引擎面/wiki 内部状态/模型契约均判冻结并入 BASELINE；待判 80→66 |
| **B28 批甲续 3** | websearch provider-types 字段面收口 | P3 | 小 | ✅ **完成（2026-10-02）**——`labelKey`/`descriptionKey` 收口（只改键、不动 i18n 值）；闸门全绿 |
| **B29 批甲/批乙收口** | 换锚待判清单清空 | P3 | 小 | ✅ **完成（2026-10-02）**——66→0：按族判定（MyBatis 列名/观测面/引擎 DSL/存量配置 jsonb/模板令牌），model 凭据面登记为例外待拍板；扫描器边界（文件级条目）已写入脚本 |
| **B30 凭据面统一** | model ↔ MCP/websearch 统一 camel | P3 | 小 | ✅ **完成（2026-10-02）**——只动 API 层（存量存储层与第三方载荷未动）；确认 PUT 体本就 camel（无第二个 B26）；棘轮减侧生效（清理 2 条基线） |
| **B31 B23 处置** | 孤儿夹具该不该删 | P3 | 小 | ✅ ⚠️ **结论修正并收官（2026-10-02）**——115 个里 102 个是录制脚本清单（证据链）；一次删除 13 个的尝试导致 2 条测试失败、已全部还原；工具加固为"仅线索、禁批量删"；**一个都不删** |
| **B33 包结构守卫修复** | B6 批 10 引入的 5 组环（工具放错层）归位到底层 | P2 | 小 | ✅ **完成（2026-10-02）**——`AppEnvLookup` → `common/deployment`、`StorageRuntimeEnv` → `common/storage`，`ImageResolver` 脱开 storage 直连；守卫回绿（环 5 → **0**、依赖 config 11 → **1**、L2→L3 回 6 条）；全量 4713 + spotlessCheck 绿。详见 15.1.1 |
| **B34 agent/tools 分包** | 94 文件单层 → 根（框架/共享/通用）+ 5 能力子包（wiki/knowledge/sql/data/web） | P2 | 中 | ✅ **完成（2026-10-02）**——MCP 族因与 `ToolRegistry` 同包紧耦合（含 protected 互访，实测约 40 处）暂留根并登记；可见性放宽 8 处逐条登记；测试镜像 12 个跟移；全量 4713 + spotlessCheck 绿、守卫绿。详见 15.1.1 |
| **B53 知识面卡片模型键收口** | 内部视图模型键 snake→camel + 失败原因接上 `errorMessage` + 防回流守卫 | P2 | 小 | ✅ **完成（2026-10-04）**——点检触发：三键定性后用户拍板「收口 + 接线」。① `original_file_name`/`display_name` = 内部视图模型键（写读同套、功能正常但属 Go 时代遗留）→ 写侧 `useKnowledgeBase` camel 化，读侧 3 处同步；② `error_message` = 死字段（接口给 camel `errorMessage`，全仓无人读写）→ 接线到卡片悬停浮层失败态（**紧凑时间线不含 `lastError`**，此前无任何原因出口）；③ 守卫：`crossFaceKeyContract.test.ts` 新增第 4 条（红态探针实测可红）。前端全量 704 + `vue-tsc` 0 错误 + 契约键守卫绿。另附走查微调 2 处（占位文案 / 树形间距）独立提交。详见 15.1.1 |
| **B54 api 面 snake 键收口** | 12 处真断链修复（改密/邀请/auth 时间/wiki/KB 复制）+ 裁撤死参数收尾 + 守卫扩面棘轮 | P1 | 中 | ✅ **完成（2026-10-04）**——由 B53「内部键收口」追问扩展到 api 线格式面。① **改密实测恒失败**（snake body → 400 `oldPassword/newPassword: 不能为空`）已修；② 邀请 6 键 + 5 消费点（邀请人列此前显示用户 ID）；③ auth 时间键（注册时间被兜底写成当前时刻）；④ wiki 修订 4 消费点（`vue-tsc` 抓出）；⑤ KB 复制 `sourceId/targetId`（探针证实）+ 标签 `sortOrder`；⑥ 裁撤死参数：`api/knowledge-base` 5 个接口的 `agent_id/agent_source_tenant_id` 全删 + 过期 javadoc（根因=空间分享裁撤漏清中间层）。守卫新增第 5、6 条（api 面 `*_at` 全禁 + snake 记号棘轮 32 键/只许减/红态探针两轮）。前端 706 + `vue-tsc` 0 错误 + 后端 compile/spotless 绿。遗留 24 键待核实 → B55。详见 15.1.1 |
| **B55 api 面 snake 记号逐条核实** | B54 挂起的 32 键全定性：13 改 camel / 6 删死字段 / 13 已核实合法 | P1 | 中 | ✅ **完成（2026-10-04）**——逐键查「后端 DTO 字段名 / 显式 `put` 的键 / `JsonNode.path` 读取 / 接口实测」。① 改 camel 13 键，其中 **6 处用户可见**：偏好 `lastActiveTenantId`（探针：snake PUT 200 但 prefs 仍 `{}` → 「回到上次空间」永不生效）、`oidcOnlyLogin`（改密门禁恒 false）、wiki 六键（页数恒 0 / 问题标签与举报人不显示 / 图 meta 熟悉度）、`requireApproval`（MCP 审核开关初始态丢）、KB 摘要四键（注意是 `knowledgeCount`）、`isDefault`；② 删死字段 6 键（agent 三键、建议问题死参数、浏览器连接偏好、UserInfo.knowledge_bases）；③ 13 键核实合法入基线（kb_filter 一族 / file_types / owner_id / chat-history / embed / KB 筛选查询参数）。棘轮 32→13（只许减）+ 红态探针复验。前端 706 + `vue-tsc` 0 错误 + 契约键守卫绿。详见 15.1.1 |
| **B56 技能区文案/死键/宿主技能目录** | 技能区文案改指令型口径 + 删 19 死键 ×5 语言 + dev 宿主技能目录 | P3 | 小 | ✅ **完成（2026-10-04）**——点检追问「技能管理为何没了」查实＝2026-09-28 定稿的功能裁剪第一批（PR3 `caef9d5`：移除沙箱 + 技能降级为指令型，删 30 前端文件）。本批：① 4 条在用文案（`skillsConfigDesc`/`skillsSelectionDesc`/`selectSkillsDesc`/`skillsAllListHint`）五语言改指令型口径；② 删 19 个零引用死键 ×5 语言（沙箱/安装期遗留，`agent.editor` 块漏网）；③ dev 宿主技能目录 + `.env`/`dev-env.sh` 注入；④ 更正控制器 javadoc 的 env 名（踩坑：必须复数 `WEKNORA_SKILLS_HOST_DIRS`，写单数不报错、只回 `skillsAvailable:false`）。i18n 审计 11/11 + 前端 706 + `vue-tsc` 0 错误 + 后端 compile/spotless 绿。⚠️ ③④ 将由 **B57（技能入库、弃用宿主目录）** 取代。详见 15.1.1 |
| **B57 技能管理回归（入库）** | 指令型技能入库 + 平台级 CRUD（复用原 catalog 路径）+ 宿主目录退役 | P1 | 大 | ✅ **完成（2026-10-04）**——点检追问「技能管理为何没了」→ 方案三稿收敛（v2「复用原接口」→ v3「入库」），用户拍板：平台级 SystemAdmin / camel / 只在线填写 / 硬拒+force / **入库**（多实例部署）+ 弃用宿主目录。① 数据层 `V4__skills.sql`（平台级、软删；主键按仓库约定 varchar，首版 uuid 被 PG 拒后改定义重跑）；② 运行期 `DbSkillSource`（复用 `Skill.parseSkillFile`；每轮装配新建→下一轮生效）+ `Manager` 数据源泛化为 `List<SkillSource>`；③ 接口复用 `/api/v1/skills/catalog` 且**不实现** install；frontmatter 服务端组装 + `requireRuntimeIdentity` 拦下会被静默改名的名称；RBAC 仅 SystemAdmin；审计 `skill.created/skill.deleted`；④ 退役 `Loader`、`host-dirs` 配置与 dev 注入；⑤ 前端恢复「技能管理」页（新建/查看/删除，被引用时给引用清单+force 二次确认）+ 五语言 38 键 + 7 条纯逻辑单测。实测：新建 201→选择器可见→400/409/引用 409/force 204 全通；后端 4,702 测试 0 失败 + spotlessCheck、前端 713/713 + `vue-tsc` 0 错误。详见 15.1.1 |
| **B58 旧信封读法清剿** | Go `{success,data}` 残留 → Java 裸载荷（8 处消费点 / 6 文件） | P1 | 小 | ✅ **完成（2026-10-04）**——触发：点检 `?section=integration-api` 报「加载 API 集成设置失败」。根因：`/auth/me` 裸信封（tenant 在顶层），页面读 `userResp.data.tenant` → 恒 undefined → 抛错。逐条 curl 实测端点形状后清剿 8 处：集成页 tenant/agents（②后者此前静默为空）、聊天页开场建议（永远为空）、FAQ 导入 taskId（进度条不出现）、KB 文件夹树（恒 null）、KB 重命名 `moved_count`（**成功也弹失败**）、KB 跨库移动 `task_id`、移动进度轮询（每次 tick 空转）、轨迹探测（入口被隐藏）。有意适配的 API 层（web-search-provider / invitations）与冻结面未动。守卫：`crossFaceKeyContract.test.ts` 表格钉 9 条（红态探针验过）。前端 717/717 + `vue-tsc` 0 错误。详见 15.1.1 |
| **B59 技能编辑** | 「查看内容」→「编辑」：PUT 更新 + 改名保护 + 编辑弹窗 | P1 | 中 | ✅ **完成（2026-10-04）**——用户要求编辑弹窗与新建一致。① 后端 `PUT /api/v1/skills/catalog/{id}`（收 name/description/content，**slug 不可改**）+ 重新组装自校验 + `version` 自增；② **改名保护**：name 是运行期身份（`selectedSkills`），被引用时改名 **409 + 引用清单**（只改描述/正文放行）；③ `GET /{id}` 作编辑草稿（content = 剥掉 frontmatter 的正文，与组装严格可逆）；④ 审计 `skill.updated`；⑤ 前端新建/编辑共用弹窗（回填/加载态/slug 只读/改名时行内报错 + 保存置灰），移除只读抽屉；五语言 −4/+9 键；纯逻辑单测 7→10。**浏览器端到端实测**：回填 ✓ / 保存（v+1 + 审计 + 列表刷新）✓ / 改名拦截（点名 Smart Reasoning、保存置灰）✓；后端 4,704/0 失败 + spotless、前端 720/720 + tsc 0 错误。详见 15.1.1 |
| **B60 技能租户化** | 平台级 → 空间级（含平台内置只读层）+ 菜单并入「数据与扩展」 | P1 | 中 | ✅ **完成（2026-10-04）**——触发：用户提「技能配租户配置 + 菜单移到数据与拓展」，核实为**修隔离缺陷**（选择器读全表 → 任何成员可见别家技能；slug 全局唯一）。① `V5` 加 `tenant_id`（NULL=平台内置）+ `(COALESCE(tenant_id,0), slug)` 部分唯一索引；② 服务层可见范围单点表达 `visibleScope`（平台层 or 本空间，null → 仅平台层 fail closed）、平台行只读 403、name/slug 冲突 409；③ 引用清单按空间过滤 **且计入 `is_builtin`**（内建 agent 是全局一行，tenant_id 取自首个物化它的空间，实测会漏报）；④ 运行期注入与选择器按当前空间读；⑤ RBAC `addSystemAdminRule` → `TenantRole.ADMIN`（空间 admin）；⑥ 菜单挪到「数据与扩展」+ 平台内置行「内置」标记只给「查看」（弹窗三模式）。实测：第二空间（注册新建）目录/选择器均空、同名 slug 201、跨空间直取 404、平台行 PUT/DELETE 403；UI 实测含「另一空间 owner 非系统管理员可见」。后端 4,708/0 失败 + spotless、前端 720/720 + tsc 0 错误。详见 15.1.1 |
| **B61 技能按需读取 + 点名注入** | `read_file` 的 `skill://` 支持（A）+ @点名注入正文（B） | P1 | 中 | ✅ **完成（2026-10-04）**——用户追问「先注入名称描述、按需加载正文？」→ 核查发现**只有 Level 1 通**：`skill://` 无解析器、`read_file` 是沙箱绑定工具（无实现类）、`Manager` 读面零调用者 ⇒ 「选项 B」的正文从没生效。① A：`SkillReadFileTool` 契约**以 Go 录像为准**（schema/描述逐字、三段式输出、data 键、8 类错误文案、行数语义与分页；非 `skill://` 按 `exec_no_source` 明确报错）；② B：系统提示词新增 `skill_instructions` 段（正文注入），`must_use` 措辞随注入结果切换，读不到则回退按需读取；③ 顺带修 pinned 描述空串。**真实回合端到端**：工具清单含 `read_file`、提示词含目录段+注入正文、桩触发的 read_file 真实执行并回 `size=559 bytes` 的技能正文。后端 4,720/0 失败 + spotless。详见 15.1.1 |
| **B62 WeKnora Cloud 整功能裁撤** | 设置页 + 提供商 + 解析引擎 + VLM/embedding/rerank 适配器 | P1 | 大 | ✅ **完成（2026-10-04）**——用户要求「去掉 WeKnora Cloud 设置」；核查发现它还是**模型提供商**与**解析引擎**，范围摆成三选项后用户选**整功能裁撤**。① 后端 35 文件（整删 8：Service/Controller/2 DTO/Provider/Embedder/Sign/Reranker）；② 去分支：provider 注册表与枚举、`AuthCreds` 缩成 `apiKey`（appId/appSecret 唯一消费者是云）、`RemoteApiChat` 校验与死字段、两个工厂、`ModelRuntimeFactory`（云校验 + 租户回落 → `modelCredentials`）、`ModelConnectivityTestService` ×3、`VlmClient`（含 `Transport.postWithHeaders`）/`VlmHttpTransport`、`ParserEngineRegistry`、`SystemController` ×3、`WebConfig` 2 条 RBAC + pathPatterns、`APIKeyRoutePolicies` 2 条路由；③ 前端 20 文件（整删 2：设置页 + 云模型工具），对话框/解析引擎页去云、五语言去块与条目；④ 测试去云用例 + golden 去条目 + 删 4 fixture。**dev 库零存量**（credentials/config/chunking 全无命中）→ 无需数据迁移。实测：两个云端点 **404**、providers **26 家不含云**、parser-engines **9 个不含云**、UI「模型」组只剩 模型管理/Ollama。后端 4,704/0 失败 + spotless、前端 720/720 + tsc 0 错误。详见 15.1.1 |
| **B63 embed 语言跟随失效** | 派生语言被写进 localStorage → 覆盖「跟随浏览器/宿主」 | P1 | 小 | ✅ **完成（2026-10-04）**——点检 `widget-test.html` 报「默认语言设跟随但没生效」。实验坐实：`applyEmbedLocale` 一律写持久值，而初始化顺序是 URL → localStorage → 浏览器 ⇒ 渠道默认语言 / 预览 `?locale=` 一旦应用就永久压过浏览器语言。修：拆 `applyDerivedEmbedLocale`（渠道默认/浏览器/URL，不落盘）与 `applyEmbedLocale`（宿主 set_locale，落盘），跟随模式先 `clearStoredEmbedLocale()` 再取浏览器语言，`<html lang>` 同步；widget 脚本补 `locale`/`data-locale` → iframe URL `?locale=`，并把握手前的 `setLocale()` 排队补发。真机 6/6 + 渠道默认语言链路（可被宿主覆盖、验后还原）；新增 2 单测 + 1 守卫（2 个红态探针）。前端 723/723 + tsc 0。详见 15.1.1 |
| **B64 「跟随宿主」补上页面语言** | widget 转发宿主页 `<html lang>`（弱信号 `?hostLocale=`） | P1 | 小 | ✅ **完成（2026-10-04）**——用户追问「页面浏览器语言是 zh-CN 吗、为什么跟随还是英文」。实测：浏览器 zh-CN→中文 ✓、浏览器 en-US→英文 ✓、**浏览器 en-US + 页面 `<html lang="zh-CN">`→英文 ✗** ⇒ ① 用户浏览器语言实为 en*；②「跟随宿主」只跟随了浏览器、不读页面声明。修：widget 无显式 locale 时读 `documentElement.lang` 走**独立参数 `?hostLocale=`**（不能占 `?locale=`，否则压过渠道默认语言）；`matchEmbedLocale()` 严格归一化（认不出→null，`lang="de"` 不被兜底成中文）；优先级＝宿主显式 > 渠道默认 > 页面 `<html lang>` > 浏览器，且全为派生、不落盘。五语言说明同步。真机 4/4（含 `de-DE` 回落、显式压过页面声明），B63 六项回归绿；3 单测 + 守卫 2 不变量（红态探针 ×2）。前端 724/724 + tsc 0。详见 15.1.1 |
| **B65 嵌入渠道保存后密钥丢失** | `publishToken` 被 `load()` 的列表行冲掉 → 嵌入代码退化成"加载密钥失败" | P1 | 小 | ✅ **完成（2026-10-04）**——用户报「保存后嵌入代码变成 `<!-- 加载渠道密钥失败 -->`」。根因（真机复现）：token 只在**详情/创建/轮换**响应返回（授权边界），列表行与 **PUT 响应都不带**；面板靠详情 `mergeChannelDetail` 合并，而保存分支 `await load()` 用列表行**整体重建**数组 ⇒ token 被冲掉。修：新增 `embedChannelTokenRegistry`（本会话记住见过的 token，`hydrate` 在每次 `load()` 后贴回、删除时 `forget`，仅内存不落盘），面板三处接线；**不改后端**（PUT 返 token 会扩大暴露面）。同族路径（开关/新建/轮换）同覆盖。真机：保存前/后 snippet 均含 `em_…`；3 单测 + 守卫 3 不变量（红态探针 ×2）；前端 728/728 + tsc 0。成因来自初始建仓、非近期回归（用户改语言后保存时撞上）。详见 15.1.1 |
| **B66 Agent 编辑器变量全空** | `placeholders`/`type-presets` 裸载荷漏 api 层适配 | P1 | 小 | ✅ **完成（2026-10-04）**——用户问「点击插入 / 输入 `{{` 唤起列表」为何不工作。根因：两个端点返回**裸载荷**（实测无 `data` 键），api 层声明 `get<{ data: … }>` 却**没做适配** → store 读 `?.data` 恒 undefined ⇒ 变量芯片空、`{{` 无弹出、类型预设空（同族第二处）。按同仓约定改为显式适配（`return { data: resp }`，同 KV 三兄弟/web-search）。真机：修复前芯片 0 个、`{{` 无弹出；修复后芯片出现、`{{` 弹出 5 项、点击成功插入 `{{query}}`，store 直读 presets 5 条 + placeholders 七组齐全；全仓 `get<{ data }>` 共 2 处即这两处，其余 6 处均已适配。守卫 1 条（红态探针验过）；前端 729/729 + tsc 0 + i18n 11/11。详见 15.1.1 |
| **B67 KB 解析设置整页崩** | 解析规则读 snake 而 KB 配置面是 camel（B3b 漏改读侧） | P1 | 中 | ✅ **完成（2026-10-04）**——用户贴 `getEngineForGroup` 的 `undefined.some` 报错。根因：KB 配置面按 B3b 已 camel（V2 迁移 + 后端 `ParserEngineRuleView(fileTypes,…)`），解析设置页仍按 snake 读 → 对 camel 数据崩页（真机复现：分区 select 数 0）。同族三处：写入侧也错（emit snake → 后端按 camel 收 → 落库成 `fileTypes: []`，KB 级规则从未生效）、`UploadConfirmDialog` 在 KB 配置面与上传覆盖面之间直接透传、相关类型声明也是 snake。修：新增 `utils/parserEngineRules.ts` 作两面互转唯一出口（宽容归一化 + 显式转换），解析设置全面 camel 化并过归一化；保留覆盖/智能体面的 snake（运行时契约）。真机验证：22 个 select + 0 报错 + 读值正确（Excel=simple 即库里值）+ UI 保存后 camel 完整落库。守卫 1 条（红态探针 ×2）+ 3 单测；前端 733/733 + tsc 0。详见 15.1.1 |
| **B68 智能体推荐问题恒空** | 读取侧查 snake `generated_questions`，写入侧/库是 camel | P1 | 小 | ✅ **完成（2026-10-04）**——用户问「GACI 库有没有自动生成问题」（答：**有**，enabled + 23/24 chunk 各 4 个），顺带查出：推荐问题的读取侧（`listRecentDocumentChunksWithQuestions` 的 `LIKE` + `firstGeneratedQuestion`）按 **snake** 取，而写入侧 `DocumentChunkMetadata` 与全库 31 chunk 全是 **camel**（从无改名迁移）⇒ SQL 恒 0 行、解析恒 null ⇒ 「推荐问题」永远为空（真机 `[]`）。修：SQL camel 主 + OR 容忍 snake；解析 camel 优先 snake 兜底。真机红→绿（播一条 camel 问题 → 修复前 `[]`，重启后返回该问题；验完已清）。新增 2 条后端测试（含扫描所有 @Select 的键名守卫）+ 两个红态探针；后端 4,706/0 失败 + spotless 绿。详见 15.1.1 |
| **B69 持久层治理（架构师四条落地）** | 全表改删防护拦截器 + R6-R9 棘轮 + 分页拼接归零 + 字符串 wrapper Lambda 化 + knowledge 域 @Lazy 解环 + 租户过滤评估 | P1 | 大 | ✅ **完成（2026-10-05）**——架构师四条意见逐条转为机器强制面。① **FullTableWriteGuard** 防全表改删（方言 SQL fail-open；全表写须具名 Mapper 方法 + `FULL_TABLE_ALLOWED` 登记，首个登记例 `applyDefaultStorageQuota`——顺带把控制器匿名 `update(null,wrapper)` 全表写改具名）；② **R6-R9 进 ArchitectureRulesTest**（@Lazy 禁令 / 裸 JDBC 白名单 28 类 / 字符串 wrapper / `.last` 拼接；基线=代码内 Set/Map + 双断言：基线外新增红、条目过期也红防空转；R6 红态探针实测可红）；③ **23 处 `.last` 拼接全灭**——新增 `PageRequests`（cap/range/atOffset，负数钳 0；`atOffset` 覆写 `IPage.offset()` 让任意偏移走分页插件，`SessionRepository` 手写 FETCH 方言分支一并退役）；④ **字符串 wrapper 25 类清零**（唯一保留 `MessageRepository`：jsonb 三参 set 是作者实测后的刻意选择，基线登记理由）；⑤ **knowledge 域 @Lazy 解环**：8 类 11 注入点 → **0**（门面 helper 下沉 `KnowledgeAccessHelper`；enqueue 端口外提 `KnowledgeProcessingQueue`（含 datasource 桥跨域消费者 1 处）；worker 摘要触发改同步领域事件 `KnowledgeProcessedEvent`（SummaryService `@EventListener`，语义=原直调）；嵌套接口 `KnowledgeService.KnowledgeProcessWorker` 删除）；⑥ 文档：`docs/persistence-architecture-rules.md`（规约修订版）+ `persistence-tenant-filtering-evaluation.md`（M3 评估：不上 TenantLine——与 B60 的 NULL=平台内置语义冲突，建议「缺失过滤探测 fail-loud」路线）。后端 **4,719**/0 失败 + spotlessCheck 绿 + 包结构守卫绿（新包 `common.mybatis` 无环）。详见 15.1.1 |
| **B70 M3 落地：方言探测归一 + 租户过滤缺失探测（alert）** | detectPostgres 八处归一 + TenantFilterGuard 拦截器 + 首次盘面 | P2 | 中 | ✅ **完成（2026-10-05）**——按 B69 评估报告的路线落地 Step1/2。① **方言探测归一**：`DatabaseDialects.isPostgres` 八处本地副本（session 三仓/memory 两类/mcp/storage/datasource 桥）清零——失败语义逐字一致（按非 PG 走），散落日志收敛为 DatabaseDialects 一处告警；`StorageBackendRepository` 构造器 catch-Exception 静默默认改为显式注释（连不上=启动问题，不再吞 RuntimeException）；**R7 白名单 28→24 条**（7 条方言探测摘除 + MemoryIndexStore 理由收窄为列存在性探测），`JdbcClient` 纳入 R7 目标类型（storage 两仓储登记）。② **TenantFilterGuard**（`common/mybatis`，挂插件链末位）：SELECT 主表未带 `tenant_id` 谓词按三档处置（`weknora.persistence.tenant-filter-guard` = off/alert/enforce，默认 **alert**）；v1 刻意从窄（主表/UNION 不查/解析失败放行/子查询出现即过），边界全部登记在类注释；表注册表 47 张由 migrations 推导；alert 档 Logback 捕获单测 + enforce 红态 + 装配顺序断言。③ **首次盘面**（H2 契约全量）：6,084 告警 / 73 条去重语句 / 21 张表——头部 users(2799)/tenant_members(1021)/knowledge_bases(831) 是按 id/成员关系的合法传递面，直连缺失候选在腰部（chunks 47 / task_pending_ops 37 / im_channels 25）；清单与收口三选一写回评估报告 §5，**enforce 切档待 B71+ 逐表定性后**。踩坑一则：表名字面量撞 B18 键名扫描器（`knowledge_bases` 既是表名也是 agent 配置键）→ 按其「表名=合法 snake 面」的既有口径加路径豁免。后端 **4,725**/0 失败 + spotlessCheck 绿。详见 15.1.1 |
| **B71 租户探测切 enforce（逐表定性收口）** | 72 条语句五族归入白名单 + 默认档 enforce | P1 | 小 | ✅ **完成（2026-10-05）**——B70 盘面的 72 条去探针语句全部定性：**认证面 7**（hash/bot 身份/邮箱定位，请求期无租户上下文）/ **调度面 4**（IM 投递、数据源同步轮询全表=设计行为）/ **跨空间身份关系面 4**（成员/邀请/收藏按 user 天生跨空间）/ **按 id/父键传递 54**（UUID 取行 + requireKb/AccessGuard 上层守卫，或 kb_id/knowledge_id/item_id 传递；含 wiki 全族 33）/ **内部任务队列 5**（scope 三元组即边界），归入 `TenantFilterGuard.ALLOWED_STATEMENTS`（增删须注明族别）。**真漏=0 条**——印证本仓边界模型是「UUID + 上层守卫」，B60 型洞的根因是缺守卫不是缺谓词。默认档切 **enforce**（env `WEKNORA_TENANT_FILTER_GUARD` 可降档排障）：未登记的新增无租户谓词 SELECT 执行期即红。残余风险与处置写进评估报告 §6。enforce 档下全量 **4,725**/0 + spotlessCheck 绿 = 收口验证。详见 15.1.1 |
| **B72 前端契约键失配收口（snake 全量排查·修复批）** | 9 条「前端读/写 snake、后端发 camel」DRIFT 修复 + created_at 死读删除 + 预览串对齐 + 守卫收口 | **P0** | 中 | ✅ **完成（2026-10-05）**——全量排查 2,979 处/699 词/182 文件定谳：95%+ 是冻结契约面不动（SSE 载荷显式 @JsonProperty、工具名、设置 KV、连接器凭据、embed 协议）；失配集中在 chunk 编辑面（**乐观锁+启用开关曾静默失效**）、FAQ 标签（恒「未分类」）、图谱提取（两按钮必 400）、Ollama 列表、平台 Key 有效期（潜伏）。**Phase 0 实证**：`POST /api/v1/agent-chat` 是集成页对外文档化 API（playground 按 snake `response_type` 解析并向集成者展示原始帧）⇒ SSE 载荷族=「外部权威面」，camel 化需协议版本化，立项稿见 `docs/handoff/plans/sse-payload-camel-立项设计稿.md`。守卫收口：B72 回归钉测（修复面禁回流）+ api/initialization 移出整面白名单（余量 11 键逐条登记）+ python 基线 42→41。前端 **734**/734 + vue-tsc + 守卫绿。详见 15.1.1 |
| **B73 前端死键清理（snake 全量排查·清理批）** | 前后端都不认的 snake 记号逐个验证后删除 | P2 | 小 | ✅ **完成（2026-10-05）**——creatChat 的 agent_config 整块（CreateSessionRequest 只收 title/description）、doc-content 的 getChunkMeta/char_count/token_count（ChunkResponse 无此字段，meta 恒空）、tool-results 死类型 relation_type/type_icon/tools_to_use、KnowledgeBaseList 死视图键 updated_at/processing_count、docreader_addr/docreader_transport 六处（Java 配置域无此字段，Go 残留）。**翻案一则**：page_size 不是死键——`ListKnowledgeChunksTool:245` 在发、`knowledgeChunksDisplay.ts:21` 在消费（分页文案），type-check 拦截删除后复核恢复——**「删前必验证读写两侧」的价值实证**（排查期代理判定「无读写点」是错的）。前端 734/734 + vue-tsc + 守卫绿。详见 15.1.1 |
| **B74 守卫基线精确化 + 前端 Go 锚点注释换锚（snake 排查·收尾批）** | python 基线 41 条全量去「B19 整包话术」+ 16 文件 Go 文件名注释换 Java 锚点 | P3 | 小 | ✅ **完成（2026-10-05）**——① python 基线 41 条理由逐条核实重写（六类：守卫/测试自引用、注释引用、BEM CSS、i18n 值、上传覆盖冻结面〔B13 缺口〕、死参数链），**通用话术归零**——此后基线每条自带端点级判定依据，棘轮「只许减」可执行；② 前端 16 文件 ~20 处 `internal/handler/*.go` 等过期 Go 文件引用换成真实 Java 锚点（AuditAction/AuditLog→`audit/domain`、TenantRole→`common/tenant`、validateAllowedOrigins→`EmbedChannelService`、thinking_control→`ThinkingStrategies`、系统设置键→`SystemSettingRegistry`、kb_filter→`AgentTypePresets`、收藏→`UserFavoriteController`、rbac→`WebConfig` 等；溯源类注释标「Go 时代」不删史实）。窗口期无新 DRIFT 浮出（基线挡住的记号全部核实为非契约）。前端 734/734 + vue-tsc + 守卫绿；后端全量=B72/B73/B74 会话级闸门（零后端代码变更）。详见 15.1.1 |
| **B75 @JsonPropertyOrder 遗产摘除（snake 排查·注解卫生批）** | 66 文件键序注解盘点：60 文件摘除 861 行，7 承重者保留+原地理由 | P2 | 小-中 | ✅ **完成（2026-10-05）**——字节稳定依赖全量盘点定谳：①主代码 8 处摘要/签名输入全是纯字符串/文件字节（MCP 指纹是手写 canonicalJson 键序自带，不过 Jackson）；②字节级金片 B2 已清零（6 处 `.getBytes()` 命中全是测试夹具）；③Redis CAS=eventId/槽位**原样读回**比对（LINDEX~=ARGV[2]），needle 是 contains 只依赖键名字节；④LLM 出站体在 `RemoteApiBodyCodec` 显式 goSorted/structSorted 重排（ObjectNode 手工构建），注解不在 wire 路径。**唯一真承重=`PromptCache.promptPrefixFingerprint`**（valueToTree 序列化 system ChatMessage+tools，javadoc 明示指纹对序列化字节敏感）→ 保留 ChatMessage/ChatTool/FunctionDef/MessageContentPart/ImageUrl + B38 键序决策面 StreamEvent/LiveRunPayload，共 7 个原地注释理由。**过程事故一则**：首版多行摘除脚本在收束行带尾注释时吞掉 class 行（compileJava 抓 "unnamed class"）——`git restore` 后用括号配平正则重做，回滚重做优于手修补洞。**探针实录**：首轮全量红于 spotlessJavaCheck（格式非语义），测试任务全绿=零键序依赖实证；spotlessApply 后终轮 BUILD SUCCESSFUL（test+spotlessCheck 双绿）。详见 15.1.1 |
| **B76 方法级 Go 名收口（去名 + 死代码 + 常量/局部名）** | 31 个 `go*` 方法名全去 + 死代码 2 处 + 常量/内部类/局部名去 Go | P2 | 中 | ✅ **完成（2026-10-07）**——档 3（2026-10-03）只裁类级（`Go*` 类 25 → 0），方法级 31 名 / ~150 引用从未裁决。本批零行为变更：① 删死代码 `McpCatalog.goEncoderJson`（0 引用）+ 测试侧 `Tools45cFakes.goJson`（0 引用）；② 主源码 33 项去名（`goFmt4→formatScore4`、`goFmtV→valueText`、`goSliceString→sliceText`、`toGoJsonIndent→indentedJson`、`goQuote→quoted`（wiki）、`goJsonString→jsonString`、`goJsonKind→jsonKindName`、`goTypeName→jsonTypeLabel`、`goQueryEscape→queryEscape` 等，全表见 batch-records）；③ 常量/内部类/局部名：`GO_ENCODER→STRUCT_JSON`、`GO_KEY_ORDER→KEY_BYTE_ORDER`、`GoMarshalException→ArgsRenderException`、`GO_MARSHAL→REQUEST_BODY_JSON`、`GoJsonBridge→JsonBridge`（文件同移）、`goStyleErrorReportValveCustomizer→plainTextErrorReportValveCustomizer`；④ **收敛重复常量**：`AgentResponses.GO_ZERO_TIME` 删 → 复用 `ZeroTimeSerializer.ZERO_TIME_LITERAL`；⑤ 顺清 6 处陈旧 Go 注释。范围外保留：`GoRecording*` 实录（禁手改）、`goMarshal`/`goFormatG` 族（行为面，另批）。闸门：全量 **4,823**/0 失败（6 跳过）+ spotlessCheck + 包结构守卫 + 注释棘轮全绿。详见 15.1.1 |
| **B77 方法级 Go 复刻换实现（保输出）** | `Registry.goQuote`→`ToolJson.quoted`；`ParamCaster` 手写指数展开→BigDecimal | P2 | 小 | ✅ **完成（2026-10-07）**——① `Registry.goQuote` 删除，改标准 `ToolJson.quoted`：实录 `routing_text`（`GoRecording46A`，测试**逐字比**）绿 ⇒ 真实 ID 输出逐字不变，Go `%q` 的 `\xNN` 控制字符转义语义退役；② `ParamCaster.goFormatFloat`（手写 mant/exp 解析）→ `BigDecimal.valueOf(v).stripTrailingZeros().toPlainString()`：非指数形态逐字不变，指数形态**去尾零**（`0.00000010→0.0000001`，测试数字 token 归一化不受影响）；③ 评估未动：`IssueView.indentedJson`——Jackson 默认字段分隔符 `" : "` + 数组布局与实录逐字不符，需自定义 printer 才等价，收益低（格式已是工具输出契约、与 Go 无关）⇒ 保 writer + 中性名；④ 登记重复项：`MessageSanitizer.escapeHtml` 与 `agent.modelcontext.HtmlEntities.escape` 同表两份（跨包可见性所限）。两批同树执行、共用一轮闸门。详见 15.1.1 |
| **B78 压缩/观测渲染换 Java 标准** | 手写 JSON writer 退役 → `ToolJson.write`；`ObservePhase.goMarshal`→`argsJson` | P2 | 小 | ✅ **完成（2026-10-07）**——① `ConversationSerializer` 删手写 `goMarshal`/`goMarshalInto`/`goEscapeString`（HTML 转义/U+2028-9/`Double.toString` 数值链）与内部异常 → `renderToolArgs` 值走 `ToolJson.write`（递归键排序 + 标准紧凑输出），外层键仍按 **UTF-8 字节序**（渲染恒定），非对象/非有限数回退截断原文；② `ObservePhase.goMarshal` → `argsJson`（体内本就是标准实现，去名 + 修陈旧 javadoc）。可见变化仅限压缩提示词文本：HTML 实体不再转义、U+2028/9 原样（结构与键序不变）。测试收编：`fold` 增 `normalizeEscapes`（B40 同口径）+ 两处测试名去 Go；实录未动。闸门：全量 **4,823**/0 + spotlessCheck + 包结构守卫 + 注释棘轮全绿。详见 15.1.1 |
| **B79 Notion 数字换 Java 标准** | `goFormatG`/`shortestRoundTrip` 删除 → `Double.toString` | P2 | 小 | ✅ **完成（2026-10-07）**——手写 `%g`（~80 行：指数至少两位/最短往返压缩）退役；整数分支保留，其余走 Java 标准形态。可见变化落在 Notion 条目正文文本（落库/检索/LLM）：`1e+20→1.0E20`、`1.2345675e+06→1234567.5`、`1e-05→1.0E-5`；已入库旧数据文本不变、含数字列的源下次同步一次性内容 diff（用户已拍板接受）。测试两条按 Java 形态重写（`numbersFollowGoFormatting→numbersFollowJavaFormatting`、`goFormatGMatchesStrconv→jsonNumberToStringUsesJavaStandardForm`）。闸门：全量 **4,823**/0 + spotlessCheck 绿；**主源码 `go*`/`GO_*` 标识符归零**（类级 2026-10-03 清零 + 方法级 B76~B79 清零）。详见 15.1.1 |
| **B80 契约文案换锚·第一部分（类型名）** | `jsonTypeLabel` 词表 → JSON 类型名（null/boolean/number/array/object）+ 补钉子 | P2 | 小 | ✅ **完成（2026-10-08，余项并入 B81/B82/B83）**——只做无金片面：`SystemSettingRegistry.jsonTypeLabel` 换 Java/JSON 标准词表（`<nil>→null`、`bool→boolean`、`float64→number`、`[]interface {}→array`、`map[string]interface {}→object`），`expected bool`→`expected boolean`；消息模板不变。**原盲区补钉**：新增 `SystemSettingRegistryTest`（12 条断言）+ **红态探针已验**；金片零变化（已核实无夹具钉类型名分支）。**收口说明**：四项余项分别由 ① ② → **B83**（auth 2 金片 + ASR/VLM 6 金片换锚）、③ → **B82**（gin 32 金片 / 8 域收敛到 `RequestFields`）、④ → **B81**（wiki/mcp/ollama 三处孪生 + 补钉）完成；本行不再有在办项。详见 15.1.1  |
| **B81 盲区孪生文案换 Java 标准 + 补钉** | wiki/mcp/ollama 三处 `non-object into Go value` → `expected JSON object, got <类型名>` + 三处新钉子 | P3 | 小 | ✅ **完成（2026-10-07）**——① 新增共享 `ToolJson.nodeTypeLabel`（Jackson 节点类型小写：null/boolean/number/string/array/object），`SystemSettingRegistry.jsonTypeLabel` 收敛为委托（去掉第二份映射）；② 三处孪生换文案：`WikiRequestSupport`（`Invalid request body: expected JSON object, got …`）、`McpServiceCrudOps`（同式）、`OllamaManageService`（同式，顺删本地 `jsonKindName`（旧词表含 `bool` 不符合标准））；③ **原盲区补钉**：新增 `WikiRequestSupportTest`/`McpServiceCrudOpsBindingTest`/`OllamaBindJsonObjectTest`（+ 设置域钉子仍绿），**三处红态探针逐一验过**（临时改坏 → 三红；还原后绿）；④ 金片零变化（已核实 0 夹具钉这三处文案）。闸门：全量 **4,829**/0（6 跳过）+ spotlessCheck 绿。详见 15.1.1 |
| **B82 gin 校验文案换锚** | 17 文件生成点收敛到 `RequestFields` + 32 金片重锚（8 域） | P2 | 中 | ✅ **完成（2026-10-07）**——旧文案 `Key: '<Struct>.<Field>' Error:Field validation for '…' failed on the '…' tag`（gin 味）散布 17 文件（7 份 helper 副本 + 10+ 内联）。新文案（单一实现 `common/web/RequestFields`）：`field '<camel>' is required` / `is not a valid email address` / `is below the minimum` / `is above the maximum`，其余 tag 兜底；字段名 PascalCase→camelCase（`TenantID→tenantId`、`LLMModelID→llmModelId`）；单引号免转义。32 金片 + 3 测试文件断言按同规则转换，**由全量测试自证与实现逐字一致**。⚠️ 事故一则：首版用双引号 → 夹具/Java 字面量双转义，compileTestJava 9 错；改单引号一次通过。⚠️ `structName` 形参保留签名（不再渲染），调用点清理登记后续。闸门：全量 **4,831**/0 + spotlessCheck；gin 文案残留 0。详见 15.1.1 |
| **B83 auth 类型错 + ASR/VLM 错误文本换锚** | `json: cannot unmarshal …` 族退役 → 字段级文案；ASR 错误文案 → `HTTP <状态行>: <详情>` | P2 | 中 | ✅ **完成（2026-10-07）**——① auth：`TenantBindSupport.stringFieldTypeError`（原 `stringFieldValue`）、`TenantCrudOps` 非对象、`AuthSessionOps` 六处（非对象/refreshToken/tenantId 类型/小数/越界）全部改字段级文案（`field 'name' must be a string, got number`、`field 'tenantId' must be an integer, got string`、`… must be an integer`、`… is out of range`）；顺删 `AuthSessionOps.jsonKindName`、`TenantBindSupport.jsonKindName`、`AuthController.SWITCH_ANON_STRUCT_TYPE` 三个 Go 面常量/helper；② ASR/VLM：`openAiErrorText` 重写为 `HTTP <状态行>: <详情>`（详情取 `error.message`，取不到退回 body 原文），**顺删 `jsonErrorText`（Go 逐字符 JSON 报错仿真器退场）**；③ 2 个 auth 金片 + 6 个 ASR/VLM 金片按同规则转换（测试自证一致）；④ 修两处陈旧注释（ASR `%!s(<nil>)` 描述、wiki 截断报错文案）。⚠️ 语法坑一则：`must be a integer` → wrongType 加冠词判断（an/a），红金片当场抓出。闸门：全量 **4,831**/0 + spotlessCheck。剩余 Go 味文案登记 B84。详见 15.1.1 |
| **B84 最后三处盲区文案换锚 + 补钉** | web 工具 5 处 / 技能 frontmatter 2 处 / Notion 4 处 → Java 标准文案 | P3 | 中 | ✅ **完成（2026-10-08）**——① web 工具面（LLM 可见）：`WebSearchTool` 五处类型错收敛到 `RequestFields.wrongType`（query/count/country/freshness/content，count=integer、content=boolean），删本地 `jsonTypeOf`/`fieldTypeMessage`；`WebFetchTool.itemsTypeMessage` → `invalid argument 'items': expected an array of {url, offset?, limit?}, got <类型名>`；② `SkillFrontmatter` → `invalid frontmatter: …`（两处 Go yaml 文案）；③ Notion 连接器 → `invalid Notion response: 'results' is missing` / `… must be an array, got <类型名>`（块前缀同步中性化），parse 两方法改 static 便于钉。**原全盲区补钉**：新增 3 个测试类 + `NotionClientTest` 新用例（并更新其旧文案断言），**四处红态探针逐一验过**。闸门：全量 **4,836**/0 + spotlessCheck；**主源码 Go 味文案残留归零**。详见 15.1.1 |
| **B85 escapeHtml 四副本收敛 + 测试侧 go* 局部名清理** | 五字符 HTML 转义单一实现 `common/web/HtmlText`；7 个测试文件局部名去 Go | P3 | 小 | ✅ **完成（2026-10-08）**——① 盘点出 **4 份同表实现**（`MessageSanitizer`/`common/prompt/MessageAttachmentsPrompt`/`memory/domain/MemoryRender`/`agent/modelcontext/HtmlEntities`，五字符表逐字一致），新增 `common/web/HtmlText.escape`（null→空串、单趟替换）并让四份全部委托（后两者保留签名供既有调用点）；② 测试侧局部名去 Go：`RssPureFunctionsTest.goZero`、`StreamJsonTest.goRow`、`McpStubABTest` 八个 `go*` 族、`OssMultipartUploadTest.goSpecConstants`、`JiebaTokenizerDiffTest.goSideDictionaryIsEmpty`、`WikiIngestLanguageTest` 注释、`MemoryTextTest.escapeHtmlMatchesGoHtmlPackage`（实录字段名字符串**不动**）。闸门：全量 **4,836**/0 + spotlessCheck + 包守卫绿。详见 15.1.1 |
| **B86 structName 死形参清理** | 8 个 helper 去 `structName`（~31 调用点）+ AgentController 判别改布尔 | P3 | 小 | ✅ **完成（2026-10-08）**——B82 换锚后 `structName` 只服务被删掉的旧前缀，全部成死参。① 去参 8 处：`TenantBindSupport`/`AuthBindingSupport`/`AuthController`/`QaRequestBinder` 的 `bindingError`、`TenantMemberController.requireFields`、`WikiRequestSupport.requiredFieldErrors`、`WebSearchProviderController.validatorError`+`bind`、`VectorStoreController.validator`+`parseOrValidator`（15 文件同步摘除调用点字面量首参）；② `AgentController.bindAgentRequest` 的 `structName` 是**语义判别**（Create 才要求 name）→ 改布尔 `requireName`，字符串比较退场；③ 全仓 `structName` 残留 **0**。闸门：全量 **4,836**/0 + spotlessCheck + 包守卫绿。详见 15.1.1 |
| **B88 工具面 schema 按 Java 标准 camel 化** | 142 键（30 输入 + 107 输出 + 5 字符串拼接键）跨后端/实录/前端全量换锚 | P1 | 大 | ✅ **完成（2026-10-08）**——用户拍板「只动键、整体一批」。① 键集口径补全（JSON 键位 + `put(` 写侧 + 转义/深层转义 + 复合键 `tool|arg`）；② 后端 41 文件 / 445 处（工具面白名单 + `ModelOutput`/`ActPhase`/`AgentTool*Backends`/`ToolDisplay` 消费侧）；③ 实录 3,037 处（仅 45A/45B/45C/46A 工具面；46B/46C/`GoRecording` 装 SSE/检索载荷，保持 snake）；④ 前端 32 文件改名 + 双读容错（`mcpToolDisplay` 历史载荷归一化、引用载荷 `?? snake` 兜底、守卫正则同步）；⑤ 守卫 `check-json-key-case.py` 摘除 `agent/tools/` 整目录豁免 + 登记工具名基线。⚠️ **过程发现 3 类真 bug**：`ToolPolicy.sourceArgumentAllowed` 与 `SourceToolCodec` 6 处 `key.toLowerCase()` 键比较在 snake 时代是恒等操作、改 camel 后全失配（句柄解析/检索目标/MCP 路由连锁挂）；`ReferencesSupport` 引用载荷不同批会让检索消息整个消失（`ModelOutput` 渲染器读不到）；误伤 4 类（常量值 `TYPE_KNOWLEDGE_BASE`、JDBC 列标签、外部载荷读侧、websearch metadata `published_at`）。保留项：工具名 36 个 + enum 值 + 外部/第三方面键（见清单文档）。闸门：后端 **4,836**/0 + 前端 **734**/734 + `vue-tsc` 0 + spotlessCheck + 键名守卫 strict 绿。详见 15.1.1 |
| **B89 模型输出契约 XML 面** | 自有序列化标记（工具输出 + runtime_context）的属性/元素名 → camel | P2 | 中 | ✅ **完成（2026-10-08）**——B88 的延续：JSON 键之外，「我们自己的 XML 形态字段名」也换 camel。① 产出侧 12 文件 / 56 处（`AgentPrompts`、`PromptAssembly`、`ModelOutput`、`KnowledgeSearchOutputFormatter`、`GrepChunksTool`、`ListKnowledgeChunksTool`、`WikiSearchTool`、`WikiReadPageTool`、`WikiReadSourceDocTool`、`PendingWikiPage`、`FaqSnippet`、`ToolDefinitions` 文案）：属性位（`knowledge_id=`→`knowledgeId=` 等）与元素位（`<knowledge_id>`→`<knowledgeId>`、`<storage_error>`→`<storageError>`）；② 解析侧 `SourceRegistry` 7 条正则改双拼容忍（camel 优先 + 历史 snake 仍可解析，含 `<k>` 元素位与 `kb_id/kbId` 族）；③ 实录同步（45B 260 处 + 46B 1 处，协议标记 `<kb>`/`<web>`/`<ref>` 先行屏蔽不动）；④ **截断快照重算**：`R_WIKI_READ_PAGE_READ_BUDGET` 因属性名长度变化位移（1065→1069 字符），用确定性探针取实测输出后重写；⑤ 前端 `wikiToolReferences` 元素读取双拼容忍 + 补 camel 用例。**边界（保留）**：引用协议标记 `<kb>`/`<web>`/`<ref>` 及其属性（`doc`/`chunk_id`/`kb_id`/`url`/`title`）= 模型输出方言（提示词定义 + 前端解析 + 协议语义），不随本批改；解析侧已双拼，将来要动只需改提示词一处。⚠️ 误伤一则：测试夹具批处理越界打到 15 个非 agent 域文件（`file_path=`/`knowledge_id=` 等非工具面属性）→ 已 `git checkout` 回退。闸门：后端 **4,836**/0 + 前端 **735**/735 + `vue-tsc` 0 + spotlessCheck + 键名守卫 strict 绿。详见 15.1.1 |
| **B90 自有标记的多词标签名 camel 化** | 工具输出/提示词标记里的 snake 标签名（33 个）→ camel | P2 | 中 | ✅ **完成（2026-10-08）**——B89 只覆盖「与 JSON 键同名的属性/元素」，多词 snake **标签名**（`<wiki_page>`/`<links_to>`/`<linked_from>`/`<search_results>`/`<knowledge_chunks>`/`<source_document>`/`<runtime_context>`/`<bound_knowledge_bases>` 等）仍留 snake。本批：① 生产侧 **18 文件 / 87 处**（agent 面 16 文件 81 处 + 域外 2 处：`session/service/AgentHistoryAssembler` 的 `<steer_message>`/`<continue_task>`、`memory/domain/MemoryRender` 的 `<user_memory>`）；② 测试/实录同步 9 文件 457 处（`GoRecording45B` 279 / `46B` 103 / `GoRecording` 27 / `45A` 15 / `46A` 11 + 测试断言）；③ **截断快照再重算**：`R_WIKI_READ_PAGE_READ_BUDGET` 再次位移（1061→1067，标签变短）——探针取真值；④ 前端 `wikiToolReferences` 块标签双读（`wikiPage` 优先 + `wiki_page` 兜底）+ 补 camel 用例；⑤ agent 面 snake 标签残留 **0**（含 `\u003c` 转义形）。⚠️ 教训复现：**消费面（测试/前端）改名范围必须与生产面一致**，否则像 `MemoryTextTest`/`AgentHistoryAssembler` 那样出现「测试已改、产出未改」的假红（本批已补齐域外两处）。闸门：后端 **4,836**/0 + 前端 **736**/736 + `vue-tsc` 0 + `spotlessCheck` + 键名守卫 strict 绿。详见 15.1.1 |
| **B92 双读清除 + 冻结清单按理由重排** | 用户确认「不再考虑历史数据」后的兼容分支清算 | P2 | 中 | ✅ **完成（2026-10-08）**——① **双读全清**：前端 `wikiToolReferences`（`firstTag` 兜底/`wiki_page` 分支）、`mcpToolDisplay`（`legacyDiscoveryKeys`+`withLegacyKeys` 整块）、`referenceSources`/`citationMarkdown`/`sessionMarkdown`/`rag-pipeline-history`（`camel ?? snake` 与 legacy 字段）、`grepResultsGroup`+`AgentStreamDisplay`（`chunk.chunk_id` 兜底 + 守卫正则同步）；归一化输出字段一并 camel（`chunk_ids→chunkIds`、`knowledge_filename→knowledgeFilename`）。后端 `SourceRegistry` 7 条正则复原为**自有输出单 camel**，引用协议属性拆出独立命名 `PUBLIC_CHUNK_ATTR`（`chunk_id` 保留——协议方言，非历史）；`ToolPolicy` 归一化**保留**并注明只服务第三方 MCP 载荷。② **前端 4 处旧键读取修正**（跨面守卫抓出）：`content_length`×7 → `contentLength`、`display_type`×2 → `displayType`、`parsed_count`/`skipped_count`×3 → camel；`chunkCount` 命中经复核是**注释**、`row__count` 是 **CSS 类名**（两处误报）→ 守卫基线收紧为 43 条。③ **冻结清单重排**：§15.3 改为按理由三类（外部决定 / 协议行为面 / 落库·DDL）+「已解除」表（SSE 事件载荷键、wiki 图片标记、落库 jsonb 存量键）；§14.6 陈旧清单换为指针 + 理由核对纪律。④ 历史形态用例改写：`McpToolResult.test`（去 "including old history"）、`referenceSources.test`（三条双拼用例改 camel-only）、`wikiToolReferences.test`/`chatMarkdownRenderer.test` 夹具 camel。闸门：后端 **4,836**/0 + 前端 **736**/736 + `vue-tsc` 0 + `spotlessCheck` + 四守卫（键名/包环/Go 锚点/跨面键 43 条）绿。详见 15.1.1 |
| **B91 阶段 4 起步（C1 破环 + 架构规则）** | `StreamProperties` 搬家解 SCC-B + 守卫补间接环棘轮 + ArchUnit R10/R11 | P2 | 小 | ✅ **完成（2026-10-08）**——① **C1**：`config/StreamProperties` → `com.ragagent.stream`（`@ConfigurationPropertiesScan` 名单同步加 `com.ragagent.stream`；`StreamManagerConfig` 去 import），`stream → config` 边消失 ⇒ 包级**间接环 2 → 1 组**（只剩 SCC-A），守卫「依赖 config 的包」1 → 0；② **守卫补 SCC 棘轮**（`check-package-cycles.py` R1b）：原先只查两两双向（0 组）查不出三包以上的环，现按强连通分量登记、成员只许减不许增；**红态探针**（临时重建 `stream → config`）报「新增成员 ['config','im','session','stream']」并非零退出 ✓；③ **ArchUnit R10/R11**（续既有 R1~R9）：R10 分层倒挂（`service→controller`、`domain→service|controller`，实测 0 违例）、R11 `*Mapper` **顶层接口**必须在 `..mapper..`（护 `@MapperScan`；收窄排除嵌套 helper `DorisSqlExecutor$RowMapper`）；两条均过**红态探针**；④ 方案文档同步（§1.3 状态、§2.1 C1 完成、§4 改为「已落地 R10/R11 + 其余候选」）。闸门：后端全量 **4,836**/0 + `spotlessCheck` + 四守卫（键名 / 包环含 SCC / Go 锚点 / 跨面键）绿。详见 15.1.1 |
| **B93a wiki 图片标记 camel 化** | `<image_caption>`/`<image_ocr>`/`<image_original>` → camel（标记名，值不动） | P3 | 小 | ✅ **完成（2026-10-08）**——B93 三面中的第一面（§15.3「已解除」表）。① **口径先分清**：`image_ocr`/`image_caption` **同时是 chunk_type 的值**（`ChunkTypes.IMAGE_OCR`、`WikiIngestService.CHUNK_TYPE_*`、`HybridResultOps` 过滤、`?chunkType=image_caption`、落库枚举）——值属语义、**保留**；本批只改**标记名**。② 产出/解析/渲染 **11 文件 / 97 处标签位**：`common/wiki/WikiImageMarkup`（三块删除/解包正则）、`knowledge/support/ImageInfoEnricher`（7 处包装 `<imageCaption>`/`<imageOcr>`）、`wiki/service/{WikiImageEnricher,DefaultWikiImageEnricher}`、`im/runtime/ImFormat`（剥离正则）、前端 `chatMarkdownRenderer`（3 条 legacy 正则）+ 4 个后端测试 + 前端用例。③ **过程事故（同类第二次）**：首轮替换漏两类转义形态——`<image_original\\b`（Java 正则里的 `\b` 边界）与 `<\\/image_caption>`（JS 正则的转义斜杠）；前者是**静默功能缺陷**（`IMAGE_ORIGINAL_BLOCK_RE` 只匹配旧开标签 ⇒ 新内容里的冗余块删不掉，且无测试覆盖），已补并复扫（任意转义深度）确认零漏。④ 前端 3 条负向断言（`doesNotMatch`）同步到新名，保证测的是新标签被剥离。闸门：后端全量 **4,836**/0 + 前端 **736**/736 + `vue-tsc` 0 + `spotlessCheck` + 四守卫绿。B93 余两面（SSE 事件载荷键 / 落库 jsonb 存量键）待续 |
| **B35 modelcontext 并入 agent** | 顶层包 31 → 30（用户 2026-10-02 拍板） | P2 | 小 | ✅ **完成（2026-10-02）**——13 文件 → `agent/modelcontext/`（test 3 同移），23 文件改包路径零残留；守卫绿、全量 4713 + spotlessCheck 绿。详见 15.1.1 |
| **B93b 事件面 camel 化（SSE/Redis 流）** | 载荷类删冗余注解 + SSE 事件体/信封键 + 事件名与响应类型值 + 前端事件消费 + 实录 | P2 | 大 | ✅ **完成（2026-10-08）**——§15.3「已解除」第二面，全仓一并（后端 + 前端 + 实录 + golden）。① **载荷 26 类**：删 149 条「值 == 字段 snake 形」的 `@JsonProperty`、改 2 条（`requested_at` → `requestedAtUnix`，字段名即语义：Unix 秒）；② **SSE 事件体/信封**：`llm/domain/StreamResponse`（11 键，含 `response_type`/`finish_reason`/`knowledge_references`）+ `session/sse/**` 与 `AgentStreamBridge` 键位 38 处（`eventId`/`completedAt`/`toolCallId`/`durationMs`…）；③ **值面统一**：`EventType` 38 个事件名 + `ResponseType` 23 个响应类型全部 camel 且去点号（`agent.step` → `agentStep`、`tool_approval_required` → `toolApprovalRequired`），前端本地词表（`agent_complete`/`agent_query` 等下划线形）一并并入；④ **事件产出侧**：18 文件 93 处键位（chatpipeline 插件 / ActPhase / QaSseOrchestrator / Gate…）；⑤ **前端**：43 文件（469 键位 + 87 值）；⑥ **测试/实录**：`EventPayloadJsonTest`、`GoRecording46B/46C`、`Rec46cSupport`、`Engine46bStubSupport`（含时长掩码键名）、golden `md-chat-*.json` 共 800+ 处同步。**四个真 bug（本批自查抓出）**：㈠ 删注解改变 Jackson **属性显式性** ⇒ `isFallback`/`durationMs`/`totalDurationMs` 恒输出（值/顺序双错）→ 恢复字段注解 110 处 + 带 include 字段的 getter 显式名 57 处；㈡ **helper 二参形态读侧漏改**（`SteerIntake.mapString(data, "tool_call_id")`）⇒ 工具调用 **pending 拍静默不再发射**（引擎事件流缺一拍）→ 修 3 处（扫描模式须覆盖 helper 第二参数形态）；㈢ 飞书事件解析 `message.path("message_id")` 被误改（**平台外部载荷**）→ 回退；㈣ 守卫 `check-json-key-case`/`crossFaceKeyContract` 的 `forbid(... modelId ...)` 正则笔误（列的是 camel 形 ⇒ 规则一直空转）→ 修正为 snake 形。**供应商词表保留**：`tool_calls`/`finish_reason`/`tool_call_id`（OpenAI/Anthropic 线格式，按邻近键 `role`/`name`/`content` 区分，不以本仓规则改名）。闸门：后端 **4,838**/0 + 前端 **736**/0 + `vue-tsc` 0 + `spotlessCheck` + 四守卫绿（含新增基线登记 43 条豁免）。详见 15.1.1 |
| **B93b-2 路径/查询参数名 camel 化** | URL 路径变量 + 自有查询参数名 + 防回流守卫 | P3 | 小 | ✅ **完成（2026-10-08）**——决策点 2。① **路径变量**：15 个复合名 73 处（`{channel_id}` 27 / `{session_id}` 21 / `{pending_id}` 6 …）+ 多路径注解形态（`TemporaryDocumentController` 的 `{attachment_id}` 6 处）+ `@PathVariable` 名 → camel；**路径变量名不进具体 URL**（Spring 按位置匹配）⇒ 对外零影响、前端零改动，且统一到已存在的 `{sessionId}` 惯例（`MessageController` 早已如此）。② **查询参数**：`message_id`/`authorization_attempt` → camel（后端注解 + 前端 URL 拼串 + 用例 + OAuth 卡片守卫共 7 处）；**OIDC 两键 `redirect_uri`/`error_description` 保留**（外部协议）。③ **新增守卫** `scripts/check-event-face-case.py` 并接入 CI：事件载荷注解 / 事件名与响应类型值 / 路径变量 / 查询参数四类一律 camel（OIDC 白名单登记理由）；**红态探针**（临时造 1 个 snake 注解 + 1 条 snake 路径变量 + 1 个 snake 查询参数）验证四类全抓、删除后转绿 ✓。闸门：后端 **4,838**/0 + 前端 **736**/0 + `vue-tsc` 0 + `spotlessCheck` + 五守卫绿。详见 15.1.1 |
| **B93b-3 事件/流收尾 + 面登记** | `EventMiddleware` 事件元数据键、`LiveRunPayload`（Redis live-run 标记）、换锚守卫冻结节流清理 | P3 | 小 | ✅ **完成（2026-10-08）**——① **摘掉换锚守卫的过期冻结节流**（`event/`、`stream/` 已 camel 化）后立即暴露 3 处真残留：`event/EventMiddleware` 的事件元数据 `duration_ms`（**前端早已按 `durationMs` 读 ⇒ 该字段此前静默丢失**）、`stream/LiveRunPayload` 的 `assistant_message_id`/`request_id`（含 `@JsonPropertyOrder`）→ 全部 camel；② **第 4 个真 bug**：`RedisStreamManager.clearLiveRun` 用**原始子串**匹配序列化后的键做 CAS（`"assistant_message_id":`）⇒ 键改名后 CAS 失效、标记不再被清 ⇒ 由 `RedisStreamManagerTest` 抓出，改为 `assistantMessageId`；③ **面登记（回答「为什么还有 snake」）**：`chatpipeline/plugin` 的 rerank/观测行、`RetrievalObs`、平台适配器（`im/**`/`websearch/provider`/`rerank`/`asr`/`vlm`）、connector（`datasource/connector`）、Langfuse、DB 列/mapper 六类**显式冻结**（`check-json-key-case.py` 的 `FROZEN_PREFIXES` + BASELINE 260 条均有理由）；SSE references 面**两侧已同为 camel**（自检：`ReferencesSupport` 写 ↔ `ModelOutput`/`ToolResultPersist`/`ToolDisplay` 读）。闸门：后端 **4,838**/0 + 前端 736/0 + `vue-tsc` 0 + `spotlessCheck` + 五守卫绿；另记 `EmbedRateLimiterTest`（Redis 限流跨实例）**偶发 flaky**（复跑即过，未做处理）。详见 15.1.1 |
| **B93c 落库 jsonb 键收尾** | jsonb 列内层键（表列名与 SQL 不动） | P3 | 小 | ✅ **完成（2026-10-08）**——侦察后发现**该面的主体早已完成**：`V2__kb_config_keys_camel`（KB 配置 45 条映射）与 `V3__agent_config_keys_camel`（agent 配置 ~70 条映射）已在**数据层**重写存量行，代码侧读写全部 camel（`KnowledgeBaseConfigJsonContractTest` 反向钉住旧键**被忽略**）。**实测残留只有 DDL 默认值**：全库 **87 个 jsonb 列**扫描后，仅 `knowledge_bases.chunking_config` 与 `image_processing_config` 两列的 `DEFAULT` 仍是 snake （`chunk_size`/`split_markers`/`model_id`/`enable_multimodal` 等）⇒ 未显式给配置插入的行会带回旧键（与读侧失配，静默）。**处置**：新增 `V6__kb_default_config_keys_camel.sql`（两列 `SET DEFAULT` 改 camel，幂等；**不** UPDATE 存量行——V2 已覆盖；不动列名/SQL）。顺带清理换锚守卫的 1 条过期基线条目（`MessageSuggestionService` 已无命中）。闸门：后端 **4,838**/0（唯一红条为 `EmbedRateLimiterTest` 已知 flaky，复跑即过）+ 前端 736/0 + 五守卫绿。详见 15.1.1 |
| **B94 阶段 4 解环收口（C8）** | `WikiActivityAudit` 端口搬入中性包 ⇒ SCC-A 瓦解、包图成 DAG | P1 | 小 | ✅ **完成（2026-10-08）**——**阶段 4 的前置条件达成**。① **搬端口而非搬实现**：`wiki/domain/WikiActivityAudit` → **`common/audit/WikiActivityAudit`**（审计模块的实现 `audit/service/WikiActivityAuditRecorder` 原地不动，`wiki` 与 `audit` 同时只依赖中性包）；② 效果超预期：`audit → wiki` 是 SCC-A 在环里**唯一的出边**，这条边一断，**7 包间接环整体消失**（守卫 `间接环 1 → 0 组`、基线已刷新为「两两环 0 / 间接环 0 / 依赖 config 0 / L2→L3 6」）⇒ **包图现在是 DAG**，模块化前置完成；③ 改 6 文件（1 搬移 + 5 import），jar 内引用的 javadoc 同步说明搬移理由。剩余结构性项：**C6**（`retrieval → auth` 3 处，端口 `TenantConfigLookup` 设计已定：`retrieverEngines`/`retrievalConfig`/`memoryConfig` 三个 jsonb 只读视图）、**C7**（`model → auth` 1 处，同端口）、C4（17）/C5（16）/C2（43）。
| **B36 agentm 并入 agent** | 顶层包 30 → 29（用户 2026-10-03 拍板） | P2 | 小 | ✅ **完成（2026-10-03）**——`agentm`（20 文件）→ `agent/management/`（先例 `auth/apikey/`），含资源目录改名 + 61 文件包路径 + 10 处 loader 路径 + 12 处文案 + 3 处脚本；顺手修正 AsrTestAudio 的过时错误文案；守卫绿、全量 4713 + spotlessCheck 绿。详见 15.1.1 |
| **B95 阶段 4 端口化（C6+C7）** | `retrieval → auth` / `model → auth` 清零（中性只读端口 `TenantConfigLookup`） | P2 | 小 | ✅ **完成（2026-10-08）**——① **端口**：新增 `common/tenant/TenantConfigLookup`（`retrieverEngines`/`retrievalConfig`/`memoryConfig` 三个 jsonb 只读视图），由 `auth.service.TenantService` **直接实现**（它已有 `getTenantById`，三个方法只是取 node + null 兜底）⇒ **零新 wiring**，Spring 照旧注入同一个 bean；② **改造**：`EffectiveEngines.of(Tenant,…)` → `of(JsonNode,…)`（含 2 个调用点 `HybridStoreGroupOps`/`ChunkQuestionService`）、`HybridSearchService` 的 `currentTenant()` → `currentRetrieverEngines()`（并让 `currentRetrievalConfig()` 直读端口）、`ModelService` 直读 `memoryConfig`；③ **效果**：`retrieval`/`model` 对 `auth` 的 import **清零**（守卫 L2→L3 直连 6 → **5**，基线已刷新），环与分层违例保持 0；④ **测试涟漪极小**：实现类即满足端口类型 ⇒ 13 个 mock `TenantService` 的测试**无需改动**，只改了 1 处参数类型（`EffectiveEnginesTest`）。闸门：后端 **4,838**/0 + `spotlessCheck` + 五守卫绿。剩余：C4（17）/C5（16）/C2（43）。详见 15.1.1 |
| **B96 共享词汇下沉（C4/C5 前置）** | 租户配置视图 + API Key 作用域词汇搬入 `common`（auth 不再被跨域引用） | P2 | 中 | ✅ **完成（2026-10-08）**——C4/C5 的**共同前置**：把"跨域共享词汇"按 B94 同法搬进中性包，auth 只留实现与端点。① **租户配置视图**：`auth/domain/tenantconfig/**` 6 类（`StorageEngineConfig`/`WebSearchConfig`/`RetrievalConfig`/`ParserEngineConfig`/`ChatHistoryConfig`/`TenantConfigRedaction`）→ **`common/tenant/`**（与 `TenantProperties` 同址）；② **API Key 作用域词汇**：`TenantAPIKeyScope`/`APIKeyScopeContext`/`APIKeyScopeType`/`APIKeyCapability` → **`common/security/`**（后者两个是 `TenantAPIKeyScope` 的同包隐式依赖，靠编译错误逐个发现）；③ 全仓引用更新 **55+13 文件**（含 3 处守卫/测试里的路径前缀：`AgentConfigKeyUsageTest` 豁免清单、`check-json-key-case` 冻结前缀、`JsonFaceVocabularyTest`）；④ **效果**：`knowledge`+`storage` 对 `auth` 的 import **33 → 19**（剩下全是租户查询面：`Tenant`×12 / `TenantService`×7 / `UserService`×1 / `TenantMapper`×1）——这批用 B95 的端口 + 每站点判定收尾（下一步 B97）。⑤ 过程教训：搬迁类若与旧包内其它类**同包隐式引用**（无 import），需靠**编译错误**逐个补/搬（首轮 8 个报错即此因）；守卫/测试里的**路径前缀**也是搬迁的必改项（三处）。闸门：后端 **4,838**/0 + `spotlessCheck` + 五守卫绿。详见 15.1.1 |
| **B97a C4/C5 收口（可转换子集）** | 端口补 `storageView` + 两个纯配置读取点换端口 | P3 | 小 | ✅ **完成（2026-10-08）**——① **端口扩展**：`TenantConfigLookup` 增 `TenantStorageView storageView(long)`（`tenantId`/`defaultStorageBackendId`/`storageEngineConfig` 三字段；**租户不存在返回 null**，与 `getTenantById` 语义一致，调用方据此回 401/400），`TenantService` 实现；② **转换（零签名涟漪）**：`ChunkQuestionService.tenantEngines()`（纯配置读 → 端口）、`KnowledgeBaseService` 两处（存储 provider 解析 + 默认后端校验 → 端口 `storageView`）；③ **效果**：`knowledge → auth` **10 → 6**（`storage` 仍 11，属解析器链，见下）；④ 过程：`KnowledgeCodeConventionsTest`（禁内联全限定名）抓到我一处 `var engines` 前的 FQN 写法 → 改 import；另一处 import 行被自己的字符串替换改坏（`import JsonNode;`）→ 按行修复（**教训：替换 FQN 时勿把 import 行本身当目标**）。**余下 17 处需先定策略**（见下条决策）：storage 解析器链（11 处，需把 `Tenant` 换成 `TenantStorageView`，涟漪 ~10 主 + ~28 测试）+ knowledge 4 处（`TenantFileStorage` 同链、`FaqIndexWriter`/`TenantStorageService` 依赖 `TenantMapper`、`KnowledgeBaseService` 依赖 `UserService` 各 1）。闸门：后端 **4,838**/0 + `spotlessCheck` + 五守卫绿。详见 15.1.1 |
| **B97b C4/C5 归零（实体下沉 + 端口补全）** | `Tenant` 实体/mapper/类型处理器搬入 `common.tenant` + 端口补 `tenantById` + `UserNameLookup` | P1 | 中 | ✅ **完成（2026-10-08）**——按**路 B**（实体下沉，用户拍板）。① **搬移**：`auth/domain/Tenant` → `common/tenant/Tenant`；`auth/mapper/TenantMapper` → **`common/tenant/mapper/`**（保留 `**.mapper` 以维持 `@MapperScan` ✓）；连带 `APIPrincipalConfig` + `APIPrincipalConfigTypeHandler`（同包隐式依赖，靠编译错误发现）；全仓引用替换 **86 + 4 文件**。② **端口补全**：`TenantConfigLookup.tenantById(long)`（**语义与 `getTenantById` 完全一致**：软删过滤 + 检索/上下文配置归一——因此不能用裸 mapper 替代）；新增 `common/security/UserNameLookup`（`UserService` 实现）取代 KB 创建者取名对 `UserService` 的依赖。③ **6 个站点全部换端口**（storage 4：`FileProxyService`×4 调用/`FileProxyController`/`FileserveStorageBackendResolver`/`ChatLocalImageResolverWiring`；knowledge 2：`TenantFileStorage`/`KnowledgeBaseService`）。④ **结果**：`knowledge → auth` **17 → 0**、`storage → auth` **16 → 0**（**C4/C5 完成**）；包图仍 DAG（环 0）、L2→L3 保持 5。⑤ 过程教训：**回退测试文件时注意连带回退**——`git checkout` 那 4 个测试把之前的包路径更新也撤了（编译期才发现）；测试桩的方法名要随端口方法改（`when/verify` 两种形态都要改，`verify(x).getTenantById(...)` 形态与桩形态不同，首轮漏改）。闸门：后端 **4,838**/0 + `spotlessCheck` + 五守卫绿。详见 15.1.1 |
| **B98 C2-a：wiki→knowledge 只读门面** | `common.knowledge.KnowledgeBaseLookup` 端口 + 3 处换端口（43 → 36） | P2 | 中 | ✅ **完成（2026-10-08）**——① **端口**：`common/knowledge/KnowledgeBaseLookup`，四个只读方法（`kbById` 软删过滤 / `kbByIdIncludingDeleted` 给健康检查 / `knowledgeGone` 解析状态判定 / `knowledgeExists` 纯存在性）+ `KnowledgeBaseView` 视图（`id/tenantId/creatorId/summaryModelId/embeddingModelId/wikiEnabled/wikiConfig`，**getter 风格**故调用点零改写）；② **实现**：`knowledge/service/KnowledgeBaseLookupAdapter`（过滤条件与迁移前的 wiki 直查**逐字一致**：软删过滤、`LIMIT 1`、仓储缺位保守返回、异常按原语义）；③ **换端口 3 处**：`WikiLintService`（4 引入 → 0）、`WikiKbAccessGuard`（2 → 0，`requireWikiKB` 返回视图）、`WikiPageController`（1 → 0）⇒ `wiki → knowledge` **43 → 36**；④ **侦察出的真相（修正方案预估）**：C2 **不是**"只读门面能收口"的 43 处——wiki 的 ingest 会**写** knowledge 域（`Chunk`/`Knowledge` 实体、三个 mapper、`ChunkRepository`、`SpanTracker`、`ImageInfoEnricher`、`EmbedderClient.configFrom`），且 `requireWikiKB` 有 **22 个调用点**；⑤ 因此 C2 拆三批：**C2-a ✅（本批）** / **C2-b ingest 门面（大头）** / **C2-c 调用点收尾**。闸门：后端 **4,838**/0（唯一红条为已知 flaky `EmbedRateLimiterTest`，复跑即过）+ `spotlessCheck` + 五守卫绿。详见 15.1.1 |
| **B99 C2-b 侦察 + KB 读再收口** | 17 文件方法级需求分组 + `WikiPageServiceImpl` 换端口（36 → 34） | P2 | 中 | ✅ **完成（2026-10-08）**——① **方法级侦察**（`/tmp` 脚本产出，结论已入方案文档）：把剩余 36 处按**能力**分成 7 组（KB 读 ✅ / Knowledge 读 / Knowledge 写 / **Chunk 读写**（`Chunk` 实体被 7 文件传递，是最大设计点）/ Span 生命周期（6 方法）/ 图片富化（3 方法）/ 嵌入（2 方法）），并定位两类"贵"点：`WikiIngestBatchHandler`（7 引入，端口化要改测试替身与 `wikiKb()` 夹具）、`Chunk` 实体传递；② **`WikiPageServiceImpl:444` 换端口**（`kbByIdIncludingDeleted`，无测试直构 ⇒ 零涟漪）⇒ `wiki → knowledge` **36 → 34**；③ `spotlessApply` 清 4 个失用 import。闸门：wiki 面测试全绿 + spotlessCheck + 四守卫绿。详见 15.1.1 |
| **B100 C2 归零：wiki→knowledge 完全端口化** | 7 个 common 类型 + 4 个适配器 + wiki 全量换端口（43 → 0） | P2 | 大 | ✅ **完成（2026-10-08）**——① **端口/视图（`common/knowledge`）**：`ChunkPort`（textChunks / chunksByIds / deleteChunk / enrichContentWithImageInfo）、`KnowledgeSpanPort`（五方法 + 不透明 `SpanHandle`，best-effort 语义随实现搬走）、`KnowledgeFinalizePort`（递减 + 带守卫晋升，返回 `Result`）、`EmbeddingModelPort`（`embedderFor(modelId)`）；视图 `ChunkView`（12 字段投影）、`KnowledgeBaseView`（提升为顶层并补 name/type/description/createdAt/updatedAt + setter，便于 22 个调用点与测试夹具）、`KnowledgeView`；`KnowledgeBaseLookup` 补 `knowledgeById`；② **适配器（`knowledge/service`）**：`ChunkPortAdapter`/`KnowledgeSpanAdapter`（搬 `WikiBatchSupport.WikiSpans`）/`KnowledgeFinalizeAdapter`（搬两条 UPDATE + try/catch 日志）/`EmbeddingModelAdapter`（搬类型闸门 + configFrom），过滤条件与降级姿态与迁移前**逐字一致**；③ **wiki 侧换端口**：`WikiIngestBatchHandler`（7 引入 → 0，构造器改为 `ChunkPort`/`KnowledgeBaseLookup`/`KnowledgeSpanPort`）、`WikiBatchSupport`（删 `WikiSpans`）、`WikiIngestMapPhase`/`ReducePhase`/`RunSupport`/`FinalizePhase`/`ContentSupport`/`Service`/`CitePipeline`/`ChunkMerge`/`Taxonomy`/`DocIngestResult`/`DefaultWikiChunkCleaner`/`DefaultWikiImageEnricher`/`WikiImageEnricher`/`DefaultWikiKnowledgeFinalizer`/`DefaultWikiModelResolver`（`wiki → knowledge` **43 → 0**）；④ **测试随实现搬家**：原 `WikiKnowledgeFinalizerTest` 的 SQL 形状断言搬到新的 `KnowledgeFinalizeAdapterTest`（覆盖不减），wiki 侧改为薄壳测试；7 个 wiki 测试改替身；⑤ **守卫新增 R4 解耦对棘轮**（`wiki → knowledge` 绝对禁止回流），红态探针验证会红。闸门：后端全量 **BUILD SUCCESSFUL**（4,843/0）+ `spotlessCheck` + 四守卫（含 R4）绿。详见 15.1.1 |
| **B101 common 纪律：R5/R6 守卫 + 载荷收窄** | 两条新守卫（含红态探针）+ `ChunkView` 12 → 6 字段 | P2 | 小 | ✅ **完成（2026-10-08）**——① **R5 底座不得依赖业务域**（绝对禁止）：`common`/`event`/`stream`/`tracing` 不得 import 任何 L3 域（实测 **0 条**）；探针（common 建类 import `wiki.domain.WikiConstants`）→ 报 `L1 底座 → 业务域：1 条；✗ common→wiki` + **退出码 1** ✓；② **R6 `common` 实现痕迹棘轮**（按包登记、只许减不许增）：① Spring 注册型注解 ② 对业务域 `*.mapper|repository.*` 的 import；基线 `bean = {crypto:1, security:1, storage:1}`、持久层引用 **0**（探针在 `common/knowledge` 加 `@Component` → 报「新增：bean [knowledge]」✓）；③ **`ChunkView` 收窄 12 → 6 字段**（`id`/`content`/`chunkType`/`chunkIndex`/`startAt`/`endAt`——按实测消费点收，删掉 `tenantId`/`knowledgeId`/`knowledgeBaseId`/`parentChunkId`/`imageInfo`/`metadata`；`ChunkPortAdapter.view()` 同步收窄；零编译错误即证明无消费点 ✓），落实包内既有的「载荷只带消费方真正读取的字段」纪律。闸门：后端全量 BUILD SUCCESSFUL + `spotlessCheck` + 四守卫（含 R5/R6）绿。详见 15.1.1 |
| **B102 common 减重：approval 域归位** | `common/approval` 1,758 行 / 24 文件 → 顶层 `com.ragagent.approval` | P2 | 中 | ✅ **完成（2026-10-08）**——① **判定依据**（先量后动）：`approval` 出向依赖 **只有 common**（零业务域 ⇒ 独立成域不引入环）；消费方 **mcp 5 / agent 3 / im 1**（多消费方 ⇒ 不能并入任一域，否则另两个域反向依赖它）；且它**有行为、有状态、有外部 I/O**（Gate 决策机 655 行、Redis pub/sub、HTTP 待审请求）⇒ 属"共享内核里长出的子系统"；② **执行**：`git mv` + 24 个包声明改写 + 全仓 26 文件引用改写（含 `ImService` 里 `new SpringRedisPubSub(...)`）；零编译错误；③ **文档同步**：`approval/package-info.java` 重写为"为什么是独立域而不是 common"（保留历史原因：当年为解 `mcp → agent` 反向依赖才搬进 common；独立成域同样解环且不让 common 长实现）；`event/package-info` 与 `ApprovalBridge` 的旧路径表述同步；④ **效果**：`common` **11,183 → 9,374 行**（-16%，126 文件），包图守卫全绿（环 0 / SCC 0 / L2→L3 5 不变 / R4 ✓ / R5 ✓ / R6 bean 3 ✓）。闸门：后端全量 BUILD SUCCESSFUL + `spotlessCheck` + 四守卫绿。详见 15.1.1 |
| **B103 common 减重②：settings 归位 + Memory 词汇判定** | `common/settings` → 顶层 `settings` 域；`Memory*` 经守卫抓环后回退 `common/memory` | P2 | 中 | ✅ **完成（2026-10-08）**——① **`settings` 归位**：`ConversationProperties`/`SystemSettingGateway`/`SystemSettingRegistry` → `com.ragagent.settings`（461 行；无 other-common 反向依赖、出向只有 `common.web.ToolJson` ⇒ 域→L1 ✓）；`@ConfigurationPropertiesScan` 条目随迁（`com.ragagent.common.settings` → `com.ragagent.settings`）；② **`Memory*` 的判定过程（守卫当场抓错）**：先把 `MemoryConfig`/`MemoryKeys`/`MemoryKinds` 搬进 `memory/domain` ⇒ **SCC 守卫报「间接环 1 组；新增成员 [auth, memory]」**（新边 `auth/controller/TenantConfigOps → memory.domain.MemoryConfig`、旧边 `memory/service/MemoryService → auth.service.TenantService`）⇒ 结论：它们是 **auth 租户配置 + memory 域 + datasource 共享的词汇**（不是 memory 私有）⇒ 回退并新建 `common/memory/`（含 package-info 写明"为什么不放 memory 域"）⇒ 守卫回绿（SCC 0）；③ **顺带发现**：`ConversationProperties` 在全仓**无任何业务引用**（疑似死代码，未删，已登记）；④ **体量**：`common` 9,374 → **8,175 行 / 119 文件**（两批共 -27%）。闸门：后端全量 BUILD SUCCESSFUL + `spotlessCheck` + 四守卫绿。详见 15.1.1 |
| **B104 common 减重③：tenant 拆分 + R6 第三条** | `common/tenant` 拆分（实体/mapper 归域）+ R6 增"common 不得自带 mapper 子包" | P2 | 中 | ✅ **完成（2026-10-08）**——① **先量后动**：`TenantRole` 被 **13 个包**消费（含 `common/web` 自身）⇒ 必须留 common；`TenantProperties`（auth/config/common-web 共用）留 common；`WebSearchConfig` 被 **L2 `chatpipeline`** 读 ⇒ 必须留 L1（否则新增 L2→L3 边，R3 会红）；② **搬出去**：`Tenant` 实体 + `mapper/TenantMapper` + `APIPrincipalConfig`/`ChatHistoryConfig`/`ParserEngineConfig`/`RetrievalConfig`/`StorageEngineConfig` + `TenantConfigRedaction` → `com.ragagent.tenant`（10 文件；出向仅 common ⇒ 无环）；③ **端口按分层拆开**：`TenantConfigLookup.tenantById`（返回实体 ✗ L1 安全）拆成域侧 `tenant.TenantLookup`（仅 L3：storage 7 处 / knowledge 1 处换注入），common 侧 `TenantConfigLookup` 只留 JsonNode 配置 + `TenantStorageView`（`retrieval` 是 L2，只能见这一半）；④ **R6 追加第三条**：`common/**/mapper|repository/**` 不得存在（探针验证会红）；⑤ **测试豁免随路径更新**：`AgentConfigKeyUsageTest` 的 `ALLOWED_PREFIXES` 加 `tenant/`（租户配置 jsonb 键由 Go 侧/迁移决定）+ 注释说明搬家。效果：`common` 8,175 → **7,812 行 / 114 文件**（四批共 -30%）。闸门：后端全量 BUILD SUCCESSFUL + `spotlessCheck` + 四守卫绿。详见 15.1.1 |
| **B105 搬家副作用修复 + R7 守卫** | 补搬 2 个测试目录（声明↔路径一致）+ 新增 R7 守卫 | P2 | 小 | ✅ **完成（2026-10-08）**——① **问题**：B102/B103 的全仓改名把<b>测试文件里的 `package` 声明</b>也改了（`com.ragagent.approval` / `com.ragagent.settings`），但测试<b>目录</b>没搬 ⇒ 构建全绿（javac 不看目录）而 **IDE 报「declared package does not match」**并连带一片 unresolved；② **修复**：`git mv` `src/test/java/com/ragagent/common/approval/**`（9 文件）→ `.../approval/`、`common/settings/SystemSettingRegistryTest.java` → `.../settings/`；复查 main+test 全域 0 处不一致；③ **新增守卫 R7**（绝对禁止）：`package X;` 必须等于路径推导包名（main/test 都查）——探针（声明 `com.ragagent.wrong`）验证会红并给出行号与应有包名；④ **纪律**：改名批要么同时 `git mv` 目录，要么把 `package` 声明行排除在替换外（本次把这条固化成了守卫）。闸门：后端全量 BUILD SUCCESSFUL + `spotlessCheck` + 四守卫绿。详见 15.1.1 |
| **B106 L2→L3 清零①：websearch + memory（5 → 3 条）** | 端口收 L1 配置 + 记忆词汇下沉 + 通用去重下沉 | P2 | 中 | ✅ **完成（2026-10-08）**——① **websearch（1 处）**：`PipelinePorts.WebSearch.search` 改收 `common.tenant.WebSearchConfig`（L1），执行面配置的转换搬进域侧 `WebSearchService.WebSearchConfig.from(...)`（缺省合并口径逐字保留）⇒ 管线不再 import `websearch.*`；② **memory（5 处）**：`MemoryRecall`/`MemoryRetrievalContext` 下沉 `common.memory`（条目改 **`MemoryItemView`**，实体不越层；`empty()` 口径照旧不含 items），`MemoryText.mergeUsedMemories` 的通用去重下沉 **`common.text.ListMerges`**（记忆侧留薄委托，原测试不动），`QaWiring` 两个适配器变为纯委托 / 单点转换；③ **顺带修一处隐患**：`PluginSearchOps` 的 agent 级 `max_results` 覆写原先写在执行面配置上，改造后若直接写 L1 租户配置对象会**污染租户配置缓存** ⇒ 改为 `copy()` 后再覆写（新增 `WebSearchConfig.copy()`）；④ **基线刷新**：`L2 → L3 直连 5 → 3 条`（剩 `retrieval→vectorstore`、`chatpipeline→knowledge`、`chatpipeline→agent`，均在 §2.4 登记了处置思路与规模）。闸门：后端全量 BUILD SUCCESSFUL + `spotlessCheck` + 五守卫（含 R7）绿。详见 15.1.1 |
| **B107 L2→L3 清零②：retrieval → vectorstore（3 → 2 条）** | 值对象下沉 L1 + 实体/mapper 端口化 | P2 | 中 | ✅ **完成（2026-10-08）**——① **性质先判**：`vectorstore` 含 `domain`(7)/`mapper`/`controller`/`service` ⇒ 是**业务域**（驱动在 `retrieval/engine/*`），所以不能"重分类成能力层"绕过 ⇒ 走端口化；② **值对象下沉**：`IndexConfig`/`ConnectionConfig`（公开字段 JSON DTO，被 6 个引擎仓库 + 域内共用）→ `common.vectorstore`；③ **端口 + 视图**：`VectorStoreView` + `VectorStoreLookup.byId(tenantId, storeId)`（两个消费点都只用这一个方法），实现留 `vectorstore/service/VectorStoreLookupAdapter`（直接委托仓储，不复制查询逻辑）；④ **纯谓词也下沉**：`EnvStoreIds.isEnvStoreId`（`__env_` 前缀判定，域内保留同名委托）；⑤ 装配层改注入端口；测试替身 `FakeStoreRepo` 从"实现整个 mapper"收窄为"实现 1 方法的端口"（删 4 个多余重写）、`EngineFactoryTest` 夹具改视图（**id 保持 null** 以维持 env-store 判定为 false）。⑥ **一处纪律事故**：按前缀替换 `com.ragagent.vectorstore.domain.ConnectionConfig` 时吃掉了 `…ConnectionConfigTypeHandler`（同类第 5 次）——**编译器立刻抓到**（与 B105 的 package 声明不同：那条只有 IDE 能抓），已回滚并复核。基线：`L2 → L3 直连 3 → 2 条`。闸门：后端全量 BUILD SUCCESSFUL + `spotlessCheck` + 五守卫绿。详见 15.1.1 |
| **B108 L2→L3 清零③：chatpipeline→agent 词汇归位（15 → 4 处）** | modelcontext 子系统搬 L2 + SearchTarget/PromptConstants 落 L1 | P2 | 中 | ✅ **完成（2026-10-08）**——① **`agent.modelcontext.**`（13 文件子系统：Registry/StreamDecoder/SourceRegistry/HandleStore/…）→ 顶层 `com.ragagent.modelcontext`**：这正是脚本 L2 名单里"预留未落地"的那个能力层名字（`modelcontext`/`webfetch`）⇒ 搬完 L2→L2 与 L3→L2 都是允许方向；② **`agent.tools.SearchTarget` → `common.retrieval.SearchTarget`**（agent/chatpipeline/evaluation/session 四个域共用 ⇒ L1，清 8 处）；③ **`agent.PromptInstructions` → `common.prompt.PromptConstants`**（agent/session/chatpipeline 三方共用 ⇒ L1；**必须改名**——`common/prompt` 已有同名不同职的 `PromptInstructions`（KB 业务指引追加），两个同名类并存会误导）；④ **测试随类型搬**：`src/test/java/com/ragagent/agent/modelcontext/**` → `.../modelcontext/`（包声明同步），两个录制测试补 import（R7 复核仍 0 处不一致）；⑤ **剩余 4 处已定方案**：`DataAnalysisTool`/`DataAnalysisSessionBridge`（3 处 ⇒ 端口化 + 适配器搬回 agent 侧）、`Fetcher`（1 处 ⇒ `Fetcher`+`FetchException`+`BrowserRenderer`+`AgentMarkdown` 一起搬进 L2 `webfetch`）。⑥ **一处操作事故**：`git mv dir dest` 在 `dest` 已被 `mkdir` 时会把源目录**搬进其中**（`modelcontext/modelcontext/`）⇒ 已纠正；纪律：`git mv` 目标目录**不要预先创建**。闸门：后端全量 BUILD SUCCESSFUL + `spotlessCheck` + 五守卫（含 R7）绿。详见 15.1.1 |
| **B109 L2→L3 清零④：chatpipeline→agent 归零（4 → 0）** | Fetcher 集群搬 L2 webfetch + DataAnalysis 端口化 | P2 | 中 | ✅ **完成（2026-10-08）**——① **`webfetch` 落地**：`Fetcher`/`FetchException`/`BrowserRenderer`/`AgentMarkdown`（4 文件，实测**零 `com.ragagent` 依赖**、完全自洽）从 `agent.support` 搬进顶层 `com.ragagent.webfetch`（L2 预留名）；测试 `WebFetchTest` 随类型搬到 `test/.../webfetch/`；② **DataAnalysis 端口化**：`PipelinePorts` 增加端口自有 record `KnowledgeData`/`ColumnInfo`/`TableSchema`（形状对齐工具侧），`DataAnalysisSession.loadFromKnowledge` 改收它们；适配器 `DataAnalysisSessionFactoryAdapter` 从 `chatpipeline` **搬回 `agent.tools.data`**（record 转换在 L3 侧完成）；`PluginDataAnalysis`（表结构描述 + 装载调用）改走端口类型；`QaWiring` 装配点同步；③ **一处自己的失误被新守卫抓到**：适配器搬运时我把新内容写回了**旧路径**（`p.write_text` 用错了对象）⇒ **R7 立刻报「声明 `com.ragagent.agent.tools.data` ≠ 路径 `chatpipeline/`」**，已 `git mv` 纠正（R7 首次实战拦截 ✓）；④ 另有一处 **Gradle 增量编译假报**（类型明明存在却报 cannot find symbol）⇒ `--rerun-tasks` 后 BUILD SUCCESSFUL（纪律：搬家+改写后若报可疑的找不到符号，先 rerun 再排查）。基线：`L2 → L3 直连 2 → 1 条`（只剩 `chatpipeline → knowledge`，等定方向）。闸门：后端全量 BUILD SUCCESSFUL + `spotlessCheck` + 五守卫绿。详见 15.1.1 |
| **B110 L2→L3 清零⑤：载荷+算法下沉（17 → 12 处）+ R3b 处数棘轮** | 3 载荷 → common.knowledge；SearchChunkMerge → common.retrieval | P2 | 中 | ✅ **完成（2026-10-08）**——① **元数据载荷下沉**：`FaqChunkMetadata`(291 行)/`DocumentChunkMetadata`(40)/`GeneratedQuestion`(34) 的 `com.ragagent.*` import **实测为 0**（纯 JSON 载荷）⇒ 搬入 `common.knowledge`（与包内既有"载荷"定位一致）；② **算法下沉**：`SearchChunkMerge`(187 行) → `common.retrieval` 并改收 **`ChunkView`**（关键发现：`mergeTextChunks` 只读 `getStartAt`/`getChunkIndex`/`getContent`/`getEndAt`，**正好落在 C2 的 6 字段视图内** ✓ 无需加宽）；`KnowledgeSummaryService` 改用 `ChunkPortAdapter.viewAll(...)` 投影（该方法转 public，作为域内"实体→L1 视图"公用投影）；测试 `SearchUtilTest` 夹具改 `ChunkView`；③ **新增守卫 R3b（处数棘轮）**：原先 R3 只登记"边是否存在"，清一条大边要分多批 ⇒ 现在对每条 L2→L3 边登记 **import 处数**，只许减不许增（`chatpipeline->knowledge=12` 已入基线）；红态探针（往 `chatpipeline` 加一个 `knowledge.domain.Chunk` import）→ 报 `处数 chatpipeline->knowledge=13；✗ 处数反弹` ✓（探针已删）；④ **剩余 12 处（B111 方案）**：9 处实体（⇒ 视图化 or 有界登记）+ 3 处 `ImageInfoEnricher`（用的是三个**不同**的静态方法，其中 collector 形参是 `BiFunction<…, List<Chunk>>` ⇒ 要么给 `ChunkView` 补回 `imageInfo`、要么扩端口；三方法本体是纯文本函数可单独下沉）。闸门：后端全量 BUILD SUCCESSFUL + `spotlessCheck` + 五守卫（含 R3b/R7）绿。详见 15.1.1 |
| **B111 内联全限定名清算 + R8 守卫（依赖图完整性）** | 1,331 行内联 FQ → import（保留 49 处必要消歧）；R8 守卫上线 | P2 | 大 | ✅ **完成（2026-10-08）**——① **发现**：`check-package-cycles.py` 的 R1/R1b/R3/R3b 只解析 `import` 行 ⇒ 代码里写成 `com.ragagent.x.y.Z` 的内联引用是**图盲区**（字节码依赖早就存在，图里却没有边；B107 的「1 处全限定遗漏」就是同一个坑的局部）；② **清算**：1,331 行 / 312 文件（主源码 842/186、测试 489/126）逐处转为 `import` + 简单名——只替换**类型前缀**、保留 `.MEMBER` 尾巴（`case ToolDefinitions.TOOL_KNOWLEDGE_SEARCH ->` 与 `ContractJson.semantic` 两类都抽样验证过），保留 49 处**必要消歧**（简单名已被已导入类型 / 本文件声明 / 同包顶层类型占用）；③ **守卫第一次说真话**：R1 0 → **2 组**（`agent⇄auth`、`agent⇄im`）、R1b 0 → **1 组**（8 域 `{agent,auth,chatpipeline,datasource,im,memory,session,webfetch}`）、R3 1 → **2 条**（新增 `webfetch→datasource` 2 处）且 `chatpipeline→knowledge` 处数 12 → **15**——**均为「暴露」而非新增**（基线按真读数重刷，脚本 docstring 写明来龙去脉）；④ **新增 R8 禁内联 FQ**（唯一例外＝必要消歧；当前 31 处允许 / 0 违规），红态探针验过 ⇒ R1/R1b/R3 从「代理测量」升级为「真测量」；⑤ **顺延**：原 B111（L2→L3 剩余清零）→ **B112**，目标数由 12 修正为 15 处。闸门：后端全量 BUILD SUCCESSFUL + `spotlessCheck` + 五守卫绿。详见 phase4 方案 §3 |
| **B112 解环收官：包图成为 DAG** | 最小反馈边集 3 处（`auth→agent` 2 + `agent→im` 1）⇒ R1 2→0 组、R1b 1→0 组 | P2 | 小 | ✅ **完成（2026-10-08）**——① **方法论（本批最大收获）**：环的体积要看**割**不看**成员数**——8 域间接环看着比 SCC-A（7 域）大，但按"删边最少"暴力枚举拓扑序求最小反馈边集，只有 **2 条边 / 3 处**（拓扑序 `im < session < agent < chatpipeline < memory < webfetch < datasource < auth`）⇒ 3 处改动同时消掉两组两两环与整个 8 域 SCC（SCC-A 当年也是靠 1 条 `audit→wiki` 瓦解）；② **C9b `auth → agent`（2 处）**：`PromptTemplateCatalog`(233 行) → `common.prompt`（`com.ragagent.*` import **实测为 0**，纯 classpath YAML 装载器，且**只被 auth 消费**——放在 agent 域纯属历史摆放）；`BuiltinAgentRegistry.localeFromRequest` → `common.wiki.WikiLanguageSupport`（"env + Accept-Language → locale" 纯解析，与既有 `envLanguage()` 同族；agent 侧留**薄委托** ⇒ `AgentController` 9 处调用点零改写）；③ **C10 `agent → im`（1 处）**：新增端口 `common.agent.AgentChannelCleaner`（单方法 `deleteChannelsByAgent`）——`im` 侧 `ImService implements` 之（签名逐字一致、零改写），`agent` 侧 `ObjectProvider<ImService>` → `ObjectProvider<AgentChannelCleaner>`，**保留原"破 Spring 级构造环 + 缺实现静默跳过"语义**；④ **不动合法边**：`agent → auth`（拓扑序 agent 在前）、`im → agent`（im 在底）；⑤ **结果**：R1 2→**0 组**、R1b 1→**0 组** ⇒ **全仓包图成为 DAG，阶段 4 前置达成**；红态探针（注入 `agent→im`）报 `新增环 1 agent⇄im` + SCC 新增成员 ✓；基线归零后任何回流立即变红；⑥ 顺延：原 B112（L2→L3 剩余清零）→ **B113**。闸门：后端全量 BUILD SUCCESSFUL + `spotlessCheck` + 五守卫绿。详见 phase4 方案 §4 |
| **B113 L2→L3 清零⑥：webfetch → datasource（2 → 0）** | 四个零域依赖的 HTML 工具下沉 `common/web` | P2 | 小 | ✅ **完成（2026-10-08）**——① **性质判定（先量后动）**：`HtmlToMarkdown`（接缝，32 行）/`JdkHtmlToMarkdown`（有界实现，544 行）/`HtmlConversionException`/`HtmlEntities` **四个类 `com.ragagent.*` import 实测全为 0**（纯 HTML→Markdown 工具，被 RSS 连接器与 webfetch 两侧共用）⇒ 不是"端口化"而是**下沉 L1**，与既有 `common/web/HtmlText` 同族；② **搬迁**：`git mv` 四个类（+ 一个测试）→ `common/web`，`RssConnector`/`JdkXmlFeedParser`/`webfetch/AgentMarkdown` 三处消费点补 import，javadoc-only 引用改全限定名（避免 unused-import 被 spotless 删除后链接失效）；`HtmlEntities` **包级可见 → public**（跨包访问需要）；③ **R7 联动**：测试 `JdkHtmlToMarkdownTest` 随包搬入 `test/…/common/web/`（守卫 R7 要求 package 声明↔路径一致），其空白归一等价原用包级私有的 `RssUtil.trimUnicodeWhitespace` ⇒ 改用 L1 `common.text.Whitespace.trimSpace`，**18 条语料测试全绿证明等价**；④ **观察项（本批刻意不做，避免行为变化）**：`webfetch/Fetcher` 因"H​tmlEntities 在 datasource 包内不可见"自带了**有界 12 实体**表，换用 L1 全表会让 `&alpha;` 这类从字面量变 `α` ⇒ 属行为变化，另立批次评估；⑤ **结果**：R3 直连 **2 → 1 条**（只剩 `chatpipeline → knowledge`）。闸门：后端全量 BUILD SUCCESSFUL + `spotlessCheck` + 五守卫绿。详见 phase4 方案 §2.4 |
| **B114 L2→L3 清零⑦：chatpipeline → knowledge 归零（15 → 0）⇒ R3 全清** | 3 类载荷换成 L1 facts/视图 + `ImageInfoEnricher` 归位 retrieval | P2 | 大 | ✅ **完成（2026-10-08）**——① **载荷换形状**（`PipelinePorts` 9 个签名）：`KnowledgeBase` → **`KnowledgeBaseView`**（+3 字段 vectorEnabled/keywordEnabled/extractConfig）、`Knowledge` → **`KnowledgeDocumentFacts`**（+3 字段 tenantId/fileType/filePath）、`Chunk` → **`ChunkFacts`**（+1 字段 imageInfo）；② **语义保持（关键）**：`QaWiring` 适配器仍走 `kbService.getAllTenantById(...)`（含 `ensureDefaults` 的"索引策略零值 → vector+keyword 默认"回填）**投影在回填之后取值**，各方法原租户/软删语义不变；③ **投影统一**：新增 `ChunkPortAdapter.factsOf/factsAll`、`KnowledgeService.factsOf`（含批量重载），`KnowledgeBaseLookupAdapter.view` 转 public —— 域内一份映射、装配层复用；④ **`ImageInfoEnricher`（575 行）`knowledge.support` → `retrieval.support`**：其 lister 回调改收 `ChunkFacts` 后本类**零 knowledge 依赖**、只依赖 `retrieval.*` ⇒ 与依赖同处（`retrieval → chatpipeline` 不存在 ⇒ 只新增合法的 L2→L2 单向边）；⑤ **测试侧**：四个替身内部保留实体、端口方法投影（镜像 `QaWiring`）⇒ 夹具零改写；⑥ **迁移教训**：facts 是 record（`content()`）而消费方是 getter 风格 ⇒ **124 处访问器改名**；`chunk` 等名字跨作用域分指两种类型 ⇒ 必须按"声明类型唯一性"过滤 + 编译器错误行精确改写（已记入 phase4 §2.4）。**结果：R3 直连 1 → 0 条，L2→L3 全清**——包图 DAG + 分层纪律全部达成。闸门：后端全量绿（唯一红条为已知 flaky `ModelConcurrencyGovernorWiringTest`，复跑即过）+ `spotlessCheck` + 五守卫绿 |
| **B115 M1 可行性侦察（Gradle 多模块）** | 带权包图 + 拓扑最优切分 + 装配/资源面清点（仅侦察，未动构建） | P2 | 中 | ✅ **完成（2026-10-08）**——**三条结论**：① **包图已是 DAG ⇒ 拆模块零业务代码改动**（只需搬文件 + 每模块一行 `project()`，`import` 全不动）；② **"按业务域拆"不可行——中部无便宜缝**：把最优切分形式化为"DAG 拓扑序的连续块切分"（该切法保证模块间无环）后实测，按引用处数每条边界 ≈1800 处**几乎恒定**（被 `→ common` 底噪吞掉），按跨界**不同类型数**中部为 **240~416**，最优切分**退化**成"只摘小域 + 剩 272k LOC 长在一起"⇒ 15 个业务域各自成模块要暴露 178~268 类型，**它们不是模块、就是"应用"**；③ **真缝在"架构层"不在"业务域"**（与守卫 L1/L2/L3 同构）：`:contracts`(common+event, 0 暴露) → `:engine`(11 域, 79 类型暴露) → `:app`(15 域, 102+129) + `:datasource`(入度 0) + `:misc` + `:boot`，**已核验模块图是 DAG**。**风险**：装配面绑单根包（`@SpringBootApplication` 无 scanBasePackages + `@MapperScan("com.ragagent.**.mapper")` + 15 包 `@ConfigurationPropertiesScan` + spring.factories EPP）、**13 处 classpath 资源"静默 null"**（读法已核实全是 classloader 绝对路径 ✓，缺资源不报错 ⇒ 需补断言）、proto/migrations/守卫脚本/`GoldenContract` 的路径硬编码、spotless `ratchetFrom("seed")` 搬家会放大 diff、测试归属（单测随域搬 + 集成测试集中 `:boot` + `TestSchema`/`GoldenContract` 改 test-fixtures）。**策略**：不做一次性 6 模块，第一步只做 **`:contracts`**（零成本切点，把"底座不得依赖任何域"从脚本规则升格为编译规则）。详见 phase4 方案 §5 |
| **B116 M1 第一步：`:contracts` 抽取（编译期硬边界）** | `common`+`event`(175 文件/LOC 13.8k) → 零 project 依赖模块；5 个守卫多模块化；2 个 ArchUnit 坑 | P2 | 大 | ✅ **完成（2026-10-08）**——① **只做零成本切点**（按 B115 §5.3 策略）：新增 `:contracts`（`java-library` + dependency-management + spotless），**零 `project(...)` 依赖**，`:server` 声明 `implementation(project(":contracts"))`；依赖按 `common`/`event` 的**实际 import 面**声明（jackson/spring-context·web·webmvc·jdbc/spring-boot·autoconfigure/spring-data-redis/slf4j/mybatis-plus 3.5.7/jakarta），**不用 starter** 以免把自动配置漏进库；② **搬迁**：`git mv` 主源码两目录 + `resources/common/text/*.txt`（被 `/common/text/…` 绝对路径读）+ **9 个纯底座测试**（2 个 `@SpringBootTest` 与 5 个引用其它域的留 `:server`；`JsonRoundTrip` 因被 server 多测试引用而挪回）；13 处 classpath 资源读法逐个核实**全是 classloader 绝对路径** ✓；③ **守卫多模块化（否则静默失覆盖）**：新增 `scripts/_source_roots.py` 作源码根单一事实来源，5 个守卫全改为按它遍历——改造前实测 `R6 bean 3→0`、`R8 31→28`、`R5` 变空检查（全绿但少覆盖 175 文件），改造后数字复原 + 红态探针（注入 `contracts/common → knowledge.domain.Chunk` ⇒ 报 `common→knowledge`、退出码 1）；④ **两个 ArchUnit 坑（实测）**：`MAIN` 的导入过滤器**不能用 `location.asURI()`**（ArchUnit 对 jar 内类求 asURI 抛异常，"抛异常的导入选项"被当作**排除** ⇒ contracts 以 jar 形态被整段排除、R7 报"已不再违例"；探针四变体定位，改用 `Location.contains`）；源码遍历类规则（R5 裸 NUL、R9 `.last`）改为跨模块 `backendSourceRoots(...)`；⑤ **结果**：**编译期硬约束生效**——往 `:contracts` 注入 `import com.ragagent.knowledge.domain.Chunk` ⇒ `:contracts:compileJava` **FAILED**（`package com.ragagent.knowledge does not exist`）⇒"底座不得反向依赖任何域"从脚本规则**升格为编译规则**。闸门：`./gradlew spotlessCheck build` = BUILD SUCCESSFUL（4,778 测试）+ 五守卫绿。详见 phase4 方案 §6；⚠️ **B117 当日改名为 `:common`**（本仓 `contracts` 已被 1,426 个 golden 契约夹具 + 49 个 `*ContractTest` 占用，语义撞车）|
| **B117 模块定名：`:contracts` → `:common`** | 改名 7 处；门槛移入 build 注释 | P2 | 小 | ✅ **完成（2026-10-08）**——① **定名理由（硬）**：本仓 `contracts` 已被占用——`server/src/test/resources/contracts/**` **1,426 个** golden 契约夹具 + 18 个测试类读它 + **49 个** `*ContractTest` ⇒ 模块再叫 `contracts` 语义撞车；② **另两条支持**：名实相符（27 个子包里既有端口/视图/facts，也有 `CryptoService`/`SsrfGuard`/`StorageAllowList`/`HealthController`/`GlobalExceptionHandler`/`RbacInterceptor` + 6 个 `*Properties` ⇒ 是 **shared kernel** 不是纯契约层）；与顶层包 1:1（135/175 文件在 `com.ragagent.common`，包名不动 ⇒ import 零改动）；③ **候选评估**：`:common` > `:kernel`（语义最准、名字自带约束，但与包名不一致且本仓此前无此词）≫ `:core`（DDD 里=核心业务领域，本模块零业务 ⇒ 误导）/ `:foundation`（Java 里几乎不用）；④ **门槛移到 build 注释**（名字不承担约束）：`common/build.gradle.kts` 头部写明"只应有跨域词汇/不可变载荷/端口契约，实现各归其域；现存 3 bean 是 R6 棘轮基线只许减"；⑤ **改动面 7 处**：`git mv contracts common`、`settings.gradle.kts`、`server` 的 `project(":common")`、`_source_roots.py` 的 `MODULE_DIRS`、`ArchitectureRulesTest` 2 处字面量、build 头注释、phase4 文档；**无影响**：包名/import（0 行）/Dockerfile/jar 名。复验：`./gradlew projects` → `:common`+`:server` ✓；编译期硬约束探针仍 FAILED ✓；`spotlessCheck build` = BUILD SUCCESSFUL + 五守卫绿。详见 phase4 方案 §6.5 |
| **B118 classpath 资源存在性断言（R12a/R12b）** | `ClasspathResourcesTest`：20 条资源清单 + 反漂移扫描 | P2 | 小 | ✅ **完成（2026-10-08）**——对应 B115 §5.2 风险 #2：主源码 **15 处资源读取里 13 处"缺资源不报错"**（`if (in == null) return/continue`）⇒ 症状是**功能悄悄降级**（模板为空/内置 agent 列表为空/jieba 分词退化/`spring.factories` 的 EPP 不注册），而 B116 搬家已真实搬过一批资源。① **R12a**：`RESOURCES` 清单 **20 条**（每条注明消费方）逐个断言"可读且非 0 字节"（11 个提示词模板 + builtin_agents/agent_type_presets + extract_config/asr_test.wav + dataset/samples.json + jieba/hmm_model.json + common/text/2 + spring.factories）；② **R12b 反漂移**：源码里每个 `getResourceAsStream("字面量")` 必须是清单精确路径或其**目录前缀**——只看单实参形式，拼接形式（`DIR + fileName`）天然不命中 ⇒ **零误报**、不必解析常量表；③ **多模块复用**：扫描根复用 `ArchitectureRulesTest.backendSourceRoots("main/java")`（B118 起放开为包级可见），同时覆盖 `server/` 与 `../common/`；④ **红态探针两条**：挪走 `dataset/samples.json` ⇒ R12a 报出"哪个资源+哪个消费方" ✓；往 common 加 `getResourceAsStream("probe/missing.yaml")` ⇒ R12b 报出文件与字面量 ✓（证明跨模块扫描生效）；⑤ **维护约定**：新增 classpath 资源时在 `RESOURCES` 加一行，两条断言自动覆盖。闸门：`spotlessCheck build` = BUILD SUCCESSFUL + 五守卫绿。详见 phase4 方案 §7 |
| **B119 javadoc 引用漂移守卫（`-Xdoclint:reference` 接进 check）** | 首轮清算 44 error + 19 warning 行；抓出 4 处真缺陷 | P2 | 中 | ✅ **完成（2026-10-08）**——对应本会话反复人工修的同一问题（改名/搬家/神类切片后注释里的 `{@link}` 还指着旧目标）。① **设计（零维护）**：只启用 doclint 的**引用组**（HTML 风格与 `@param` 完整性不纳入——实测 100+ 条噪声、价值低），并用 `tasks.named("check"){dependsOn(javadoc)}` 接进构建 ⇒ `./gradlew build`（CI backend job）连带执行 javadoc；**不写自定义脚本**（手写"简单名→类型"匹配远弱于 javadoc 自身解析器）。踩坑：root 的 `subprojects{}` 在子项目应用插件**之前**求值，须用 `plugins.withId("java")` 包住，否则 "Task with name 'check' not found"（实测）。② **首轮清算 44 error + 19 warning 行**：`#成员`已搬走（切片遗留）11 处 → 降级 `{@code}`；跨包类型补 FQN 12 处；指向 private/包级成员（已不存在方法）→ 降级；`@param` 写在类型上 6 条 → 改写为字段列表；`{@value}` 引用非编译期常量 1 处；未转义 `<`/`&` 19 行 → `{@code}` 或散文（`<pre>` 内用 `&lt;`）。③ **顺手抓出 4 处真缺陷（人眼没看到）**：`QuestionBatchPlanner:47` 注释已被改坏（`{@code for (int start` 被吞）；`SearchChunkMerge` 的方法注释（含 `@return`）被误挂到 `record ExactResult`（B110 搬迁错位）；`GuardForbiddenException` 包路径写错（`web.*` → `common.web.*`）；**`:common` 有 6 处注释反向引用上层类型**（`TenantAPIKey`/`APIKeyRouteAuthorizer`/`config.JacksonConfig`/`llm.domain.StreamResponse`/`ConnectionConfigTypeHandler`）⇒ 编译期看不到 ⇒ 去链接降级——等于**给"底座注释不得指向上层"补了一条免费的编译期约束**。④ **红态探针**：注入 `{@link com.ragagent.does.NotExist}` ⇒ `:server:javadoc` FAILED 且 `:server:check` 退出码 1（因 check 连带 javadoc）✓。闸门：`spotlessCheck build` = BUILD SUCCESSFUL（两模块 javadoc **0 error / 0 warning**）+ 五守卫绿。详见 phase4 方案 §8 |
| **B120 契约层命名规范 + 5 组改名** | 盘点 26 个契约接口；定 5 条规范；改名 20 文件 | P2 | 小 | ✅ **完成（2026-10-08）**——① **盘点**（26 个契约接口）暴露的真分歧：`KnowledgeBaseLookup` 同接口里 **`kbById`（知识库）与 `knowledgeById`（知识条目）混用**（而领域模型分别叫 `KnowledgeBase`/`Knowledge`）；`getKnowledgeBaseByIDs` 的 `ByIDs` 大小写；`getKnowledgeBaseByIdOnly` 的 `Only` 指代不明（实为"不做调用方作用域过滤"，领域侧同义方法叫 `getAllTenantById`）。② **定 5 条规范**（写进 `docs/backend-package-map.md` §3.5 + `common/package-info.java`）：用领域类型名前缀不用缩写；单条 `…ById`/批量 `…ByIds`；不用无信息量后缀（语义进 javadoc）；仓储 `list*`/服务 `get*`；**同名方法跨接口必须同义**。③ **改名 5 组 / 20 文件**（纯改名零语义）：`kbById`→`knowledgeBaseById`、`kbByIdIncludingDeleted`→`knowledgeBaseByIdIncludingDeleted`、`getKnowledgeBaseByIDs`→`getKnowledgeBaseByIds`、`getKnowledgeBaseByIdOnly`→`getKnowledgeBaseByIdUnscoped`、`getKnowledgeBasesByIdsOnly`→`getKnowledgeBasesByIdsUnscoped`；**`Only` 后缀只存在于消费侧接口**（领域侧叫 `getAllTenantById`）⇒ 改名自成闭环、不动领域。④ **踩坑**：改名正则若用 `(?<![\w.])` 会**跳过方法调用形态**（`kbLookup.kbById(…)` 前面是 `.`）⇒ 只改了声明；正确写法是 `(?<![\w])` + `(?=\s*\()`（前者排除更长标识符、后者精准只命方法调用，从而保住同名局部变量 `Map kbById`）。⑤ **刻意未动**：`VectorStoreLookup.byId`（通用词、命中多为同名局部变量）、`QaSearchTargets` 的局部 Map。闸门：`spotlessCheck build` = BUILD SUCCESSFUL（含 javadoc 守卫）+ 五守卫绿。规范本身由 **B119 的 javadoc 守卫**兜底（指向已改名方法的注释会在构建期红）。|
| **B121 大文件棘轮（`check-file-size.py`）** | 68 个 >600 行文件入基线；不得新增 | P2 | 小 | ✅ **完成（2026-10-08）**——① **盘点**：主源码 1,888 文件里 **>600 行 68 个**（400~600 行另有 120 个）；最大：`ImService` 1091 / `SessionKnowledgeQaService` 1041 / `MemoryIndexStore` 919 / `KnowledgeProcessWorker` 847 / `KnowledgeService` 827。② **棘轮口径（只对新文件设硬门）**：行数是粗指标——有内聚的 650 行类不该被阻止，且 68 个既有大文件都在持续改动，若对既有文件也"只许减"会反复卡住日常开发（与 R3b"处数只减"语义不同：那里一处是**违例**，这里一行只是**体量**）⇒ **不得新增 >600 行主源码文件**（硬门）+ 既有文件报 Δ（提示不阻塞）+ 拆小后 `--write` 收紧；**真正的膨胀源是新增大文件，这条把它刹住**。既有多模块扫描复用 `_source_roots.py`（B116）。③ **待办清单已落文档**：`docs/backend-package-map.md` 新增 **P2b 超大文件**（Top 12 表 + 拆分范式）；范式 = 项目既有的 `*Ops` 普通类（**不加** `@Component`，由门面构造持有）⇒ **零装配改动、零调用点改写**；四例可切性已侦察（`MemoryIndexStore` 1 字段四簇 / `ImService` Leader 簇 / `SessionKnowledgeQaService` 0 字段 / `StorageFileResolver` 3 字段）。④ 已接入 CI `guards` job；红态探针：新增 645 行文件 ⇒ 报"新增超大文件"且退出码 1 ✓。闸门：五守卫 + 新守卫绿（纯新增脚本与文档，未动业务代码）|
| **B122 大文件守卫执行 §14.5 + 首次例外复核** | 政策从散文变可执行规则；登记表入 JSON | P2 | 小 | ✅ **完成（2026-10-08）**——① **取证（本批核心）**：`git log` 逐版本量行数发现 **`ImService` 2026-10-01 切片到 664 行「出榜」后，7 天内被 im 域功能批次（Redis 面 → 跨实例 /stop → 附件面 → 选主广播 → 附件异步入库）加到 1,091 行（+427）**——而两套机制**都看不见它**：§14.5 只在「≥800」时登记（它出榜时 664）、B121 只管「新增文件」⇒ **「已出榜文件回涨」是双重盲区**。② **又一处漂移**：§14.3 记「≥800 只剩 4 个登记例外」，实测 **5 个且只有 3 个有自述理由**；`SessionKnowledgeQaService`（1041）类 javadoc **无**「规模例外」理由（违反 §14.5 自己的要求）；`FaqImportService`（566）注释仍自称「规模例外（>800 行）」——**政策在散文里，就会漂移**。③ **把 §14.5 做成可执行规则**（`check-file-size.py` 升级为 5 条）：R-a 新文件 >600 硬门（B121）；**R-b 回涨门槛：基线内文件增长 >30 行即红**（判据即 ① 的 +427）；**R-c ≥800 必须登记**；**R-d 例外必须自述**（执行 §14.5「例外必须在类 javadoc 写明理由」）；**R-e 自述数字必须相符**（B119 守卫只管 `{@link}`、抓不到散文里的数字，故 R-e 全量扫描不按阈值过滤）。④ **登记表落成数据**（`scripts/file-size.baseline.json` 的 `exempt`，68 limits + 5 登记）：3 条 `accepted`（带原文理由）+ 2 条 `pending`（`ImService` 回涨欠债 / `SessionKnowledgeQaService` 理由缺失待复核）——**`pending` 不是豁免，是后续批次的工作清单**，守卫输出里标【待还债】。⑤ 红态探针 ×4 全过：R-b（+40 行）/ R-c（移出登记）/ R-d（改成 accepted 但无自述）/ R-e（放回过期自称）。闸门：六守卫绿 + `spotlessCheck build`（`FaqImportService` 仅删注释）|
| **B123 `ImService` 第一刀（停止链路外提）** | 1091→995；新协作者 `ImStopOps` + 4 条钉子测试 | P2 | 中 | ✅ **完成（2026-10-08）**——① **刀口选在「跨实例 /stop 全链路」**（原 907~1012 行）：本地出队/在途取消 → Redis 在途映射补 IDs → 写 stop 事件到 StreamManager → 标记兜底，是一条**完整的请求取消链路**（用户在不同阶段按 /stop 会分别命中三条路径），与渠道生命周期、消息主管道同处一类只是历史堆积。② **范式照项目既有协作者**（`ImStreamPipeline`/`ImQaRunner` 同款）：新增 `final class ImStopOps`（**不加** `@Component`），由门面构造持有；依赖面刻意只收**显式四项**（Redis 面 / 队列 / 在途表共享引用 / StreamManager 的 **延迟供应** `Supplier`——后者由门面 `setStreamManager` 装配后注入，避免与 stream 包的装配环）。③ **零外部改写**：`bindInflight`/`unbindInflight`/`checkAndClearStopMarker` 被 `ImStreamPipeline`/`ImQaRunner` 以包级成员调用 ⇒ 门面保留**同名薄转发**（3 个方法 ~14 行）；`doLocalStop` 仅内部调用（`handleCommand` 的 ACTION_STOP 分支）⇒ 直接走 `stopOps`。④ **逐字保真核对**：脚本对照搬迁段原文（归一化依赖取用方式后），**90 行代码逐行一致**；唯一实质差异是 `bindInflight` 里 volatile 读**收敛为一次**（原读两次，同线程等价且更稳）。⑤ **补了原缺的测试**：这段代码此前**零直接覆盖**（`ImFoundationContractTest` 只测命令解析，`ImRedisStoreTest` 测的是 Redis 侧同名方法）⇒ 新增 `ImStopOpsTest` 4 条钉子测试（单实例标记兜底 + 一次性消费 / thread 键带 threadId / 在途命中触发取消并仍写兜底 / Redis 缺席时在途登记只更新 entry）。⑥ **本刀是系列第一刀**：995 仍 ≥800 ⇒ 该域还需 2~3 刀出榜（候选：限流去重（字段全私有，可随迁）/ 附件摄入 / 渠道生命周期与选主（选主簇与 startChannel/stopChannel 有回调耦合，需引入回调接口，风险最高）。基线已随刀收紧（`--write`），R-b 继续冻结回涨。闸门：六守卫绿（含 R-b 收紧后）+ `spotlessCheck build` = BUILD SUCCESSFUL + im 域 145 条 + 新 4 条全绿 |
| **B124 三项卫生：游离目录守卫 + `embed→embedchannel` + 编号解冲突** | 新增 S1/S2 守卫；包改名；ArchUnit 标签 R*→A* | P2 | 小 | ✅ **完成（2026-10-08）**——起因：发现仓库根有个**空的** `webfetch/` 目录，追查得实情：① **它为什么看不见**：git **对空目录完全无感**（不入 `status`、不入提交、`git ls-files` 查不到），`.gitignore`/文档/构建文件也没提它，于是躺了一周。② **来源**：B108 期间为一次搬迁**预建目标目录**时基准路径写错（`mkdir webfetch` 本该基于 `server/src/main/java/com/ragagent/`，却基于了仓库根）；真正的搬迁在 B109 走 `git mv` 到了正确位置，预建目录被落下——B108 自己的记录里已记过同类事故（`modelcontext/modelcontext/`）。③ **新守卫 `scripts/check-stray-dirs.py`**：S1 游离目录（仓库根/各模块根里既无跟踪文件、又未被 gitignore、也不在白名单的目录）+ S2 死包目录（源码树里整棵子树无 `.java`）。**上线即抓到 5 个同类残留**（全空、对 git 不可见）：`auth/domain/tenantconfig`、`test/agent/support`、`test/common/security`、`test/common/text`、`common/tenant/mapper` ⇒ 已清。红态探针 3 条全过（根级空目录 / 模块内空目录 / 嵌套死包目录）。已接 CI；白名单须写理由（同其它守卫口径）。④ **`com.ragagent.embed` → `com.ragagent.embedchannel`**：域内 14 类**有 11 类本就叫 `EmbedChannel*`**，而它与 L2 能力层的 `com.ragagent.embedding` **仅差三个字母**（两份 module-guide 也同名近似的并存）——是仓库里最容易引错的一对包名；文档的 L3 名单里本来写的就是 `embedchannel`（代码去对齐文档）。**与 `embedding` 无同名类**；`git mv` 两个包目录（main + test 镜像）+ 21 个文件引用更新 + `docs/embed-module-guide.md` → `embedchannel-module-guide.md`（里面 3 处含 `--tests "com.ragagent.embed.*"` 的**可执行命令**，不改会直接失效）+ `docs/README.md` 链接同步；残留 0。⑤ **编号解冲突**：B124 前脚本侧与 ArchUnit 侧**都用 `R*` 且互不对应**（`R7`：脚本是「`package`↔路径一致」、ArchUnit 是「裸 JDBC 白名单」）⇒ 说「R7 复核」读者无法判断。改法：**脚本侧保持 `R*`**（HANDOFF/phase4 里 ~165 处历史与计划引用不必改写），**ArchUnit 侧改 `A*`**（39 处标签：`A1~A12b`；只有 4 份活的手册引用它，已同批同步 28 处）。地图落在 `docs/backend-package-map.md`「守卫编号地图」（含读历史文档的判据）。⑥ **顺带发现的守卫摩擦**（已写进脚本说明）：`check-file-size.py` 的基线按**路径**记 ⇒ 包改名被读成「老路径消失 + 新路径新增」（本次 `EmbedChannelService` 773 行触发 R-a）——**刻意保留**：它无法区分改名与新增，多红一次只花一条 `--write`；若改成按文件名记，不同包的同名文件会互相掩盖。闸门：七守卫绿（新增 S1/S2）+ `spotlessCheck build` = BUILD SUCCESSFUL（含 javadoc 引用守卫）|
| **B125 `ImService` 第二刀（入口闸门外提）+ 端口判据修正** | 995→936；新协作者 `ImInboundGuardOps` + 4 条钉子测试 | P2 | 小 | ✅ **完成（2026-10-08）**——① **先纠正上一轮的判断**：B124 把「`PipelinePorts` 没进 `common`」列为"第 1 号不一致"，侦察后发现**判断过重**：它引用了 4 个 **L2 类型**（`LlmChatClient`/`Reranker`/`retrieval.domain.WebSearchResult`/`retrieval.graph.RetrieveGraphRepository`）⇒ **整体搬 `:common` 会造成 L1→L2 反向依赖**，是错的。真相是**两种场景的正解**：L3↔L3 端口必须进 `common.<domain>`（域间不许互相依赖）；**L2↔L3 端口由消费侧拥有**（依赖方向本就合法，消费侧拥有接口正是依赖倒置，载荷用 L2 类型也合法）⇒ 判据已写进 `docs/backend-package-map.md`「端口归属判据」，并记下这次修正，免得以后反过来搬。② **刀口：消息入口的两道闸门**（去重 + 限流）。它们同属一个关注点：都在 `runHandleMessage` 最前面拦住不该进管线的消息，都优先走 Redis 面、故障回落进程内形态——与停止链路、渠道生命周期无关。③ **比第一刀更彻底**：两个进程内回落表（`processedMsgs`/`rateWindows`）**随之搬家**，门面不再持有（第一刀的停止链路因 `inflight` 表被 `ImQaRunner` 共享，只能传引用）。依赖面只收三项 + 一个随迁常量。④ **零外部改写**：三方法类外调用为 0（`isDuplicate`/`rateLimitAllow`/`makeRateKey` 仅 `runHandleMessage` 调用）⇒ 门面**不需要**薄转发，两个内部调用点直连 `inboundGuard`（`makeRateKey` 由 private 改包级以便跨类调用）。⑤ **逐段保真核对**：簇**不连续**（三方法之间隔着 `handleMessage`/`runHandleMessage`）⇒ 脚本按大括号配对逐段抽出原文对照，43 行代码逐行一致（唯一差异：`makeRateKey` 可见性 + 一处 spotless 换行）。⑥ **补钉子测试 4 条**（单实例回落形态此前无直接覆盖）：同 id 第二次判重 / 窗口配额用尽即拒且与 key 绑定 / `rl:` 前缀且与 `ImFormat.makeUserKey` 同构（thread 形态带 threadId）/ `max=1` 时第二次即拒。⑦ 基线随刀收紧（936<995 ⇒ R-b 继续冻结回涨）。**仍未出榜**（≥800）⇒ 还需 1~2 刀，候选：附件摄入（~72 行）/ 渠道生命周期与选主（~170 行，与 `startChannel` 有回调耦合，风险最高）。闸门：七守卫绿 + `spotlessCheck build` = BUILD SUCCESSFUL + im 域 153 条全绿 |
| **B126 `ImService` 第三刀（知识库桥接外提）+ 删重复映射** | 936→856；新协作者 `ImKnowledgeBridgeOps`；删 `imPlatformToChannel` 副本 | P2 | 小 | ✅ **完成（2026-10-08）**——① **刀口**：IM 与知识库域的**全部接面**合成一刀——命令面的 KB 清单/检索读取（`kbLister`/`knowledgeSearcher`）+ 附件异步入库（`ingestAttachmentToKnowledgeBase`/`Inner`）；四件事共用同一个依赖（`ObjectProvider<KnowledgeService>`，组件缺席静默跳过），同属「IM 怎么够到知识域」一个关注点。② **核对时发现一处功能缺口（本批只搬不修，已登记）**：`kbLister()`/`knowledgeSearcher()` 是**返回空列表的桩**，而 `ImCommandSet.InfoCommand`/`SearchCommand` 会读它们（`ImCommandSet:280/415`）⇒ **生产上 `/info` 与 `/search` 的 KB/检索面恒为空**；`KnowledgeService` 明明已注入却没用上 ⇒ 疑似"未接线"。搬到一类里正是为让它显眼，缺口另立批次（属功能问题不是重构问题）。③ **顺带删掉一处重复实现**：`ImService.imPlatformToChannel` 与 `ImFormat.imPlatformToChannel` 功能等价（前者用字面量、后者用 `ImTypes.CHANNEL_*` 常量，取值逐字相同）⇒ 保留知识域常量化那份、删副本；并把 `ImFormat` 版的 `toLowerCase()` **加固为 `Locale.ROOT`**（原为默认 locale：在 tr/az 语言下会把 "I" 映射错，属既有隐患的小修；11 例映射测试全绿）。④ 测试迁移：`ImServicePlatformChannelTest`（11 例，比 golden 的 2 例更全）→ `im/runtime/ImFormatPlatformChannelTest` （包声明与路径同步，守 R7）。⑤ 保真核对：四段逐行一致（唯一差异是 `imPlatformToChannel` 的调用点改 `ImFormat.` + 两处可见性 private→包级）。⑥ 零外部改写：`ingestAttachmentToKnowledgeBase` 被 `ImQaRunner` 调用 ⇒ 门面留同名薄转发；`imPlatformToChannel` 迁移后 C 无外部残留（原测试已改指 `ImFormat`）。⑦ 基线随刀收紧（856<936）。**仍差 1 行出榜**（856 ≥ 800）⇒ 第四刀（渠道运行时 ~250 行）落地即出榜。闸门：七守卫绿 + `spotlessCheck build` = BUILD SUCCESSFUL + im 域 153 条全绿 |
| **B127 `ImService` 第四刀（渠道运行时外提）⇒ 出榜** | 856→554；新协作者 `ImChannelRuntimeOps`（17 方法 / 313 行整块）| P2 | 中 | ✅ **完成（2026-10-08）**——① **刀口**：adapter 工厂注册表 + 渠道生命周期（起停/重载/按库重建/配置广播）+ WS 长连接选主（续期/抢锁重试/释放），17 个方法；**在门面里本来就是连续一块（原 221~533 行）**，故搬迁几乎零文本改动。选主决定"哪台实例跑哪些渠道"、渠道起停又要读写 leader 锁——本就是同一关注点。② **唯一的出向依赖是消息回调**：`startChannel` 把 msgHandler 交给适配器工厂 ⇒ 构造期注入 `BiConsumer<IncomingMessage, String>`（门面传 `this::handleMessage`）；顺带把门面消息路径的**懒启动**（`startChannel(ch)`）改走 `channelRuntime`。③ **字段策略**：`channels`（mapper）/`channelStates`/`qaQueue` 三项与门面**共享引用**（同名注入 ⇒ 块内代码零改写）；`adapterFactories`/`instanceId`/两张 leader 线程表/`shuttingDown` 与三个 LEADER_* 常量**随迁**，`redisStore`/`redisPubSub` 也随之搬走 ⇒ 门面不再持有。④ **保真核对**：313 行原块 vs 311 行新块，归一化后**仅 3 处预期差异**（字段声明移位 / 去 `@Override` / 一处可见性 private→包级）。⑤ **踩坑（值得记）**：随迁字段若不在所搬的块里（`instanceId` 等声明在门面字段区）⇒ 脚本必须**显式补字段**，而 spotless 会先把"暂时未使用"的 import 剪掉 ⇒ 补字段后要按**当时存在的锚点**再补 import（我第一版按不存在的锚点插 import，`str.replace` 变空操作而脚本仍打印成功 ✗ —— 教训：**替换类脚本必须断言命中**）。⑥ **出榜**：`ImService` 1091→995→936→856→**554**（门面留消息入口/命令/附件入口/装配面，9 个协作者分区）；守卫 `--write` 后 **>600 行文件 68→67**、**≥800 4 个**、且**自动清理了 `ImService` 的豁免登记**（"清理失效登记"）。⑦ **⚠️ 本行原写的"两处未接线"已被 B128 更正**（见下方 B128 行）：`startChannelsOnReady` **本来就是接线的**（带 `@EventListener(ApplicationReadyEvent)`），是**本批搬迁把它弄断了**；`ImKnowledgeBridgeOps` 的 KB/检索桩仍然成立（那条判断无误）。原判断的两处错误留在下面这句里以资对照：~~⇒ 重启后渠道不会自动起~~（**该结论错误**：注解驱动入口没有静态调用点，我的文本检索判据不成立）；`ImKnowledgeBridgeOps` 的 KB/检索两桩恒返回空**仍成立**（B126 已记）。⑧ 文档：`docs/im-module-guide.md` **10 处**过时描述已更新（含"1,086 行全仓最大、迟早要切"⇒ 四刀结清 + 刀序复用指南）。**⚠️ 但本批同时引入一处运行时回归**（`@EventListener`/`@PreDestroy` 随块搬到非 bean 类 ⇒ 静默失效），编译与 4,700+ 测试全绿也抓不到 ⇒ 见 B128 的修复与 A13 守卫。闸门：七守卫绿 + `spotlessCheck build` = BUILD SUCCESSFUL + im 域 153 条全绿 |
| **B128 修回归 + A13 守卫（钩子不能在非 bean 上）** | 钩子挂回门面；新 ArchUnit A13；接线契约测试 3 条 | **P0** | 小 | ✅ **完成（2026-10-08）**——① **事故**：B127 把「渠道运行时」整块外提时，`@EventListener(ApplicationReadyEvent)` 与 `@jakarta.annotation.PreDestroy` **跟着方法搬进了 `ImChannelRuntimeOps`**，而那个类是由门面 `new` 出来的**普通类** ⇒ 两个钩子**静默失效**（渠道不再随应用启动、停机不再清理）；**编译 0 错误、4,700+ 测试全绿、七守卫全绿都抓不到**。② **我上一条的"未接线"判断也是错的**（两处错因）：「全仓无调用者」的检索判据**不适用于注解驱动入口**（`@EventListener` 没有静态调用点）；且原代码写的是**全限定注解** `@jakarta.annotation.PreDestroy`，我按 `@PreDestroy` 检索自然漏看——**这正好反证了 §14.5「无全限定名注解」这条纪律的额外价值**。③ **修复**：钩子挂回门面（`@EventListener` / `@PreDestroy`，改用 **import 形式**、顺带清掉全限定名注解）；`ImChannelRuntimeOps` 只留实现，并在 javadoc 写明"本类不是 bean，触发入口在门面"。④ **新增 ArchUnit A13**：`@EventListener`/`@PostConstruct`/`@PreDestroy` 方法必须声明在 Spring 扫描到的类上 （`@Component` 派生 **或** `@ConfigurationProperties`）。**上线即抓第二例** （`com.ragagent.settings.ConversationProperties#backfillFromTemplates`）——复核后判定为**误报**：`@ConfigurationProperties` 类经 `@ConfigurationPropertiesScan` 注册，同样是 bean（且 A2 已保证其落在扫描名单内，两条规则正好接上）⇒ 判据已补该口径（探针实测修正，非拍脑袋）。⑤ **A13 兜不住"注解被整个删掉"** ⇒ 另加接线契约测试 `ImLifecycleWiringTest` 3 条：门面 `startChannelsOnReady` 必带 `@EventListener(ApplicationReadyEvent)`、门面 `stop` 必带 `@PreDestroy`、`ImChannelRuntimeOps` **不得**自行声明钩子（唯一入口原则）。⑥ 红态探针：往非 bean 的 `ImStopOps` 注入 `@PreDestroy` ⇒ A13 红；还原 ⇒ 绿 ✓。⑦ **教训（已写进 A13 注释与本节）**：搬到非 bean 类上的一切**注解驱动成员**都会静默失效（`@EventListener`/`@PostConstruct`/`@PreDestroy`/`@Transactional`/`@Scheduled`/`@Async`…）——外提方法时**先扫注解**，别只扫字段与调用点。闸门：七守卫绿 + `spotlessCheck build` = BUILD SUCCESSFUL + im/arch 170 条全绿 |
| **B129 `SessionKnowledgeQaService` 例外复核 + 两刀出榜** | 1041→756；两刀（兜底流并入既有协作者 + WebSearch 解析新叶子）| P2 | 中 | ✅ **完成（2026-10-08）**——① **复核结论：例外不成立，且"无接缝"是误判**——真正的情况是**上一次拆分没搬完**：本域早已切出深协作者树（`QaSearchTargets`/`QaChatManageOverrides`/`QaMentionTagScope`/`QaKbScope`/`QaModelSelection` + `SessionQaFallback`/`SessionQaResolution`），而门面里还留着 900+ 行真逻辑（32 个成员里只有 4 个真薄委托）。② **刀 1（兜底流渲染，207 行 → 1089~1300 段）**：`trailTrim`/`renderFallbackPrompt`/`buildKbDocumentListing`/`consumeFallbackStream`/`emitKnowledgeReferencesEvent`/`emitFallbackAnswer` **并入既有** `SessionQaFallback` —— grep 证明这 4 个方法的**唯一调用方本来就是 `SessionQaFallback` 自己**（它原先回调门面 ✗）⇒ 搬迁是"补完未竟的拆分"，门面**无需任何薄转发**（改完 129→352 行）。`substringByCodePoints` **留在门面**（`modelcontext/ModelOutput` 也在用，跨域共享的小工具，只放宽为包级）。③ **刀 2（WebSearch 参数解析，69 行）**：`resolveWebSearchProviderId`/`resolveWebFetchEnabled`/`resolveWebFetchTopN`/`resolveWebSearchMaxResults` 新建叶子 `QaWebSearchParams`（持门面引用、自带 log/JSON，与既有叶子同形）；门面 4 处调用点改走叶子。④ **出榜**：门面 1041→836→**756**；守卫 `--write` 后 **≥800 从 4 → 3 个（全是已论证例外）**，**"待还债"归零**，`SessionKnowledgeQaService` 的豁免登记被自动清理。⑤ **踩坑（同型第二次）**：机械加前缀的替换**会误伤同名局部变量**——刀 2 里 `cfg.` → `service.cfg.` 把`WebSearchConfig cfg = JSON.treeToValue(...); cfg.getMaxResults()` 里的**局部 `cfg`** 也改了（同 B114 的 `chunk`）。**教训：优先"同名注入"而不是"加前缀"**——刀 1 与 B127 的渠道运行时都用同名注入（字段传给协作者后名字不变）⇒ 需要**零替换**、零风险；用 `service.` 前缀则必须先确认接收者不是同名局部变量，且替换脚本应**打印命中行供过目**（不能只打印计数——本次就是只打了计数）。⑥ 文档：`docs/session-module-guide.md` **2 处**过时描述已更新（"1,029 行 / 登记例外 / 全仓仅剩 4 个之一"⇒ 已出榜 + 刀序）。闸门：七守卫绿 + `spotlessCheck build` = BUILD SUCCESSFUL + session 域 394 条全绿 |
| **B130 `MemoryIndexStore` 例外复核 + 向量面外提（出榜）** | 919→636；新协作者 `MemoryVectorStore`(318)；A7 白名单随簇迁移 | P2 | 中 | ✅ **完成（2026-10-08）**——① **复核结论：例外不成立**，且属 **B129 同型误判**：原理由「六段同属『索引侧读写』一个关注点」是**层次**论点（"都在哪一层"），不是**内聚**论点；六簇实为**四个表家族**（topics / doc_affinity / item_embeddings / extraction_sessions）+ 工具，各 50~190 行 ⇒ 天然接缝。**可复用判据**：看例外理由说的是层次还是内聚 + 看每簇用哪个 mapper/表。② **搬迁比预期省**：`MemoryRepository`（454 行）是**纯转发门面**（`/** 实现随协作者 */` + 一行调用）⇒ 只改它的转发目标，**外部调用方零 churn**（`MemoryVectorService`/`MemoryCatalogOps`/测试都不用动）。③ **刀口：向量面** 11 个方法（`upsertItemEmbedding` 等 5 个 + pgvector 就绪探测与回退 5 个 + 列存在性探测 `columnExists`）⇒ `MemoryVectorStore`；依赖面**同名注入**（`MemoryRepository repo` ⇒ 段内 21 处 `repo.` **零替换** ✓ —— 正是 B129 记下的"同名注入优于加前缀"）。④ **出榜 + 棘轮联动**：919→**636**（<800）；`columnExists` 是**唯一**的裸 JDBC 元数据探测 ⇒ **A7 白名单条目同批从 `MemoryIndexStore` 迁到 `MemoryVectorStore`**（该棘轮是集合精确相等，迁移即自验）；类 javadoc 的「规模例外」自称按 §14.5 撤除 ⇒ 守卫先按 **R-d** 报"登记为 accepted 但源码无标记"、`--write` 后**自动清理登记** ⇒ ≥800 从 3 → **2 个（全是已论证例外）**、**memory 域 ≥800 归零**。⑤ **顺带修了守卫自身一处误报口径**：R-e 原先只要源码出现「规模例外」就判过期自称，而例外解除后类注释里往往留着「**原**『规模例外』**已解除**」这类**回顾性提及**（本批两处实测误报 ✗）⇒ 口径改为"同一行出现「解除」或「复核」即视为回顾、跳过"，并做红态探针复验（注入真自称仍会红 ✓）。⑥ 文档：`docs/memory-module-guide.md` **7 处**过时描述已更新（含"919 行登记例外/不硬切"⇒ 已出榜 + 复核方法）。闸门：七守卫绿 + `spotlessCheck build` = BUILD SUCCESSFUL + memory/arch 249 条全绿 |
| **B131 死成员清账 + 新守卫 `check-dead-members.py`** | 40 条 IDE 警告 → 单一根因；八守卫 | P2 | 小 | ✅ **完成（2026-10-08）**——① **根因（本会话自己造的）**：40 条警告里 **24 条"未使用 import"实为「重复 import」** ✗——**B111 的「内联全限定名 → import」机械转换重复添加过 import**（同 FQN 两次，散布 21 个文件）；用"简单名是否在别处出现"复核后，**真未使用 import = 0** ✓。② **其余 16 条的真身**：4 个**搬走代码后的死字段**（`MemoryIndexStore.log`/`SessionKnowledgeQaService.JSON`/`DefaultWikiModelResolver.log` + `DataSourceCredentialsController.REQUEST_TYPE_NAME`——后者查 git 史确认随 `7078b55a`（校验文案收敛到 `RequestFields`）**自然作废**，不是漏引用）；1 处**静态方法被实例调用**（`fallback.emitKnowledgeReferencesEvent(...)`，**B129 我自己写的** ✗ ⇒ 改 `SessionQaFallback.` 静态访问）；1 处 `if (true)` 兼容壳**死代码**（`EnvVectorStores`，B107 谓词下沉后留下的不可达 `return`）⇒ 简化为直接返回（行为不变）。③ **新守卫 `scripts/check-dead-members.py`**（已接 CI，共 **8 个守卫**）：D-a 重复 import（硬门）/ D-b 未使用 import（硬门，"javadoc `{@link}` 里出现算使用"，与 B119 的引用守卫不打架）/ D-c 未使用的 **Logger/ObjectMapper** 字段（§14.5 点名的两类）。**口径取舍（重要）**：先试过"扫所有私有字段"，实测全仓 **934 处**——绝大多数是**词汇表性质的常量族**（`AuditAction` 65 / `EventType` 38 / `WikiIngestConstants` 36 / `ProviderBaseURLs` 34 / `LangfuseAttributes` 25 …），与"搬走代码后的真死字段"不同类 ⇒ **做硬门只会变噪声**，故 D-c 刻意只查 §14.5 点名的 Logger/ObjectMapper。④ 红态探针：注入重复 import ⇒ D-a 红；注入未使用 Logger 字段 ⇒ D-c 红；还原 ⇒ 绿 ✓。⑤ **教训**：机械"补 import"必须**先查同 FQN 是否已导入**（B111 的转换器漏了这一步——当时只查了"简单名是否被占用"，没查"同 FQN 是否已 import"）⇒ 这类纯冗余无编译错误、spotless 也不去重，只能靠守卫拦。闸门：**八守卫**绿 + `spotlessCheck build` = BUILD SUCCESSFUL |
| **B132 租户 KV 配置面换锚（第一段：chat-history / retrieval）** | 12 键去逐字段 `@JsonProperty`；动态读路径 + 前端 + 金片 + 记录脚本同批 | P2 | 小 | ✅ **完成（2026-10-09）**——① **先核对冻结理由，再动手**：`check-json-key-case.py` 口径把「租户配置 jsonb」列为冻结面，§14.9b/system-module-guide 都以它为由推迟；核到 §15.3（2026-10-08 按**理由**重排）「已解除」表最后一行——**落库 jsonb 存量键（…租户配置内容）⬜ 可改造（开发库可清，不写迁移脚本）** ⇒ 冻结理由（"兼容历史数据"）已整体作废，本批正是"收已解除的账"。仍冻结的是 **jsonb 列名**（③ 落库/DDL 面）与 **span/PipelineLog 观测面**（§15.3 + `batch-records` "chat span 元数据曾误改已回退"）——两者都没碰。② **范围刻意窄**：`tenant/` 包还有 `ParserEngineConfig`（27 键）/`StorageEngineConfig`（63 键），合计 102 键；且 §14.9 表②把 **agent config jsonb** 列为冻结（`record-ag-golden.sh` 里同名 `embedding_top_k` 即属它）⇒ 全仓替换会打坏冻结面，本批只做**消费侧接口已收口的两段**（12 键）。③ **服务端 6 文件**：两 DTO 去 12 处注解（保留 `@JsonInclude(NON_DEFAULT)` 的省键语义）；**动态读同步**——`HybridSearchService` 3 处 `node.path("rrf_*")`、`MessageService` 4 处、`MessageSearch` 2 处（这三处是"注解改了但 JsonNode 字面量没改"的静默失效高危点）；`TenantConfigOps` 5 条校验文案（会原样进响应体，蛇形键名会指错）。④ **前端同批**：`api/chat-history.ts`/`api/retrieval.ts` + 两个设置页；跨面契约测试**摘掉 1 条白名单 + 2 条基线**（`api/retrieval.ts`「检索参数设置键」、`api/chat-history.ts` 两键）；⑤ **顺带修掉一处字段缺口缺陷**：`RetrievalConfig` 线上 9 键而前端类型只建模 6 键、装载逐字段映射 6 项、保存 `{...localConfig}` 只发 6 项，而 PUT 是**整体替换** ⇒ 经 API 设过 `rrfK/rrfVectorWeight/rrfKeywordWeight` 非缺省值的租户，从设置页存一次即被**静默重置**为服务端缺省（60/0.7/0.3）。改法：类型补三个**可选**键（服务端 0 值时省略键）+ 装载处**原样透传**（`undefined` 被 `JSON.stringify` 丢弃 ⇒ 缺省仍不落地）。⑥ **金片 11 个 + 录制脚本 5 处请求体**同批改；java 侧 4 个测试类重跑绿。**刻意未动**：`record-ct-golden.sh` 的 memory 段（其请求体是 snake，而 `TenantCatalogContractTest.kvMemoryMatchesGo` 早已用 camel ⇒ **录制脚本该段过期**，属另一批次）；`TenantService` 基线里 `compression_strategy`/`max_tokens` 等（另一配置面）。闸门:八守卫绿 + `spotlessCheck build` = BUILD SUCCESSFUL + 前端 736/736 + `vue-tsc` 干净 |
| **B37 档 3 第一刀（provider 请求面）** | Go 字节兼容层退役起步：三份 provider GoJson 去 HTML 转义复刻 | P2 | 小 | 🚧 **完成第一刀（2026-10-03）**——字节流向盘点（四类）+ 三份副本退役（探针先行：embedding 单跑绿后同批改 rerank/websearch）；`GoJsonEscapes` 类保留（stream/langfuse/MCP 仍用）。⚠️ **待决**：stream 面涉「与 Go 版共用 Redis 的 CAS」需确认 Go 版是否在跑；`GoTimeSerializer.isGoZero`（业务语义）与 `GoMapSerializer`（被继承）需单独方案。详见 15.1.1 |
| **B38 档 3 第二刀（stream/langfuse/LLM 请求体）** | Go 版确认下线 → Redis 事件等三面退役 Go 转义 | P2 | 小 | ✅ **完成（2026-10-03）**——`stream/StreamJson`（保留键序=内部 CAS 需稳定字节）、`RemoteApiBodyCodec`（保留键序归一）、`LangfuseAttributes` 三处退役；4 处「与 Go 对齐」测试改写为「字节稳定 + 标准形态」；全局面收窄至 4 处（event/MCP/待办工具/GoJsonUtil）。全量 4713 + spotlessCheck 绿。详见 15.1.1 |
| **B39 档 3 第三刀（工具面）+ 实录约束发现** | MCP/待办/检索三处退役；发现「Go 形态实录」为档 3 硬边界 | P2 | 小 | ✅ **完成（2026-10-03）**——三处退役；⚠️ **关键发现**：`GoRecording*` 实录由录制脚本生成、禁止手改、录制源（Go 服务）已下线 ⇒ 无法重录；被实录覆盖的面（`EventJson`/`RecordingSupport`）退役必须**先立「基线重建机制」**，本刀已回退这两处（复绿）。档 3 现状：①②③ 面已退役；④（event/实录覆盖面）待解锁。详见 15.1.1 |
| **B40 实录基线重建（GoJsonEscapes 全量退役）** | 实录比对「逐字节」→「ContractJson.deep 语义比较」；删 GoJsonEscapes 类 | P2 | 中 | ✅ **完成（2026-10-03）**——解法＝不改实录（禁止手改）而改比对方式（收编漏网的字节级对比）；`EventJson` 退役 + 62 处 `assertRecording` 改写 + 类删除（零残留）。全量 4709 绿（−4=删除的契约测试）+ spotlessCheck 绿。**escapes 面全清**（①②③④）。详见 15.1.1 |
| **B41 provider JSON 四副本收敛** | 4 份 GoJson/GoJsonUtil → `common/web/ProviderJson`；删零引用 GoFloatSerializer | P2 | 小 | ✅ **完成（2026-10-03）**——embedding 版为超集；42 文件引用改写 + 20 文件补 import；**Go\* 类 26 → 21**；全量 4709 + spotlessCheck 绿。详见 15.1.1 |
| **B42 档 3 第五刀（工具协议面）** | 转义退役 + 四类去 Go 名（HtmlEntities/JsonValues/ToolJson/JsonQuoting） | P2 | 小 | ✅ **完成（2026-10-03）**——先分类后落刀（真复刻 vs 只是名字带 Go）；`ToolJson.writeString` 去 HTML 转义（保留键序）；测试收编（`normalizeEscapes` 共享辅助 + `ToolJsonRecordingTest` 语义比较 + 键序单独钉住）；**Go\* 21 → 17**；全量 4710 + spotlessCheck 绿。详见 15.1.1 |
| **B43 档 3 第六刀（真退役）** | 工具协议面换 Java 原生：删 JsonQuoting/GoValueStr/GoJsonMarshal + ToolJson 手写 writer 退役 | P2 | 小 | ✅ **完成（2026-10-03）**——用户拍板「不要只是改名」；`ToolJson.quoted`（标准 Jackson）+ 收敛 WeaviateGql 第 5 份拷贝；`valueStr` 原生字符串化（`<nil>` 退役）；`prettyJson`（Jackson pretty printer）；ToolJson 删手写 writer；**Go\* 17 → 15**；全量 4710 + spotlessCheck 绿。⚠️ 口径修正：B42 改名不计退役。详见 15.1.1 |
| **B44 档 3 第七刀（connector 面）** | 删 GoBase64 / yuque GoDuration / common.time GoDuration（Java 原生替换） | P2 | 小 | ✅ **完成（2026-10-03）**——`java.util.Base64`（去换行）+ `Double.parseDouble`（与 Feishu 收敛）+ `Duration.toString()`（ISO-8601 文案）；**Go\* 15 → 12**；全量 4706 + spotlessCheck 绿。⚠️ 顺带发现「Go duration 解析」另有 2-3 份（Langfuse/TenantInvitation/env 面）待下批。详见 15.1.1 |
| **B45 档 3 第八刀（GoStrings 收敛）** | 两份 GoStrings → common/text/Whitespace + CodePointOrder（Unicode 空白统一） | P2 | 中 | ✅ **完成（2026-10-03）**——16 文件 / 45 处改写；`isSpaceChar`+6（Java 原生，精确等于 White_Space）+ 码点序比较器迁出；`trim(x,'/')` 退役为正则一行；**Go\* 12 → 10**；全量 4706 + spotlessCheck 绿。⚠️ 空白判断另剩 3 处私有实现（rss/memory 两处）待收敛。详见 15.1.1 |
| **B46 档 3 第九刀（验证驱动裁决）** | GoUrl / GoPath×2 / GoStyleErrorReportValve 逐类裁决 | P2 | 小 | ✅ **完成（2026-10-03）**——`GoUrl` 保留（95 位全表实测 7+2 处转义差异、无真实环境可验证）；`GoPath`×2 保留（POSIX 语义/平台相关性/安全面，21/24 一致但空结果差异含安全退步）；`GoStyleErrorReportValve` 裁决误标（非 Go 复刻）。**档 3 收口口径确立：逐类裁决（退役/误标/契约保留），不因名字强删**。详见 15.1.1 |
| **B47 保留类改名** | 契约保留类修标签（GitLabUrl/GitLabPath/PosixPath/PlainTextErrorReportValve） | P3 | 小 | ✅ **完成（2026-10-03）**——用户拍板：保留类的 `Go*` 名误导后人；行为零变更（~85 处引用 + 4 处 javadoc）；`PosixPath` 顺带归位 `common/text`；**误标修正不计退役进度**；全量 4706 + spotlessCheck 绿。剩 `Go*` 名字仅 C 类 5 个（+ GoogleProvider 误报）。详见 15.1.1 |
| **B48 注释大清洗专项** | 全仓注释去 Go 锚点/翻译腔/过期引用（用户 2026-10-03 立项，B9 专项化；main+test） | P2 | 大 | ✅ **完成（2026-10-03）**——11,932 匹配行/1,723 文件起步 → 棘轮基线 3,999→**0**（`--write` 已刷新）；口径=先摘不变量再删锚点、不变量中性化（「GORM 隐式行为清单」→「落库行为清单」逐条保留）、裸形态盲区补扫（`Go X:`/`照 Go`/Go 专名指针/omitempty/nil/len→Java 本位）；白名单=域词（wiki 批次·句柄翻译·SQL 可移植）/`@DisplayName`·断言消息/方法名/有效 § 引用/`GoRecording*` 实录（禁止手改）；与档 3（B37~B47）交叉期清单三次过期，收尾改「开工实时 grep」。compile + spotlessCheck 绿。详见 15.1.1 |
| **B52 B48 排查批** | 审批事件流断链修复 + 三项待复核疑点闭环 | P1 | 小 | ✅ **完成（2026-10-04）**——实锤修复：`ApprovalBridge` 原样转投使三类审批事件 instanceof 失配、聊天流审批卡静默消失（审批面板路径幸存故冒烟未抓到），补 `toPayloadData` 四对 DTO 映射 + `ApprovalBridgeTest` 4 用例；疑点①③ 复核**无需动作**（语义比较器整值浮点归一 / 四创建点全显式赋值）；疑点② 删空 if 死分支（对齐宽容置空 house 口径）。回归 + spotlessCheck 绿。详见 15.1.1 |

### 15.1.1 执行记录（索引：正文已移出，按批号 Ctrl-F）

逐批执行记录在 `docs/handoff/records/batch-records.md`（442 行）。

| 批 | 摘要 |
|---|---|
| ✅ B2 | **✅ B2**：`support/GoldenContract` 共享基建上线（`ContractJson.deep` 归一 + strip + 单一 `-Dcontract.refresh` 开关）；Faq/W5d/ |
| ✅ B1 | **✅ B1**：`docs/knowledge-api-contract-v1.md` v1.0→v1.1——升格全服务端标准；§2.2 错误体（顶层仅 `error`、键序声明序、`details` 显式 null、 |
| ✅ B3 | **✅ B3**：真面 68 处（19 文件）`@JsonInclude` 退役——wiki domain 全家（10 文件）+ websearch 三 DTO + VectorStoreTypes；键恒输出/空数组 ` |
| ✅ B4（结论：不改） | **✅ B4（结论：不改）**：哨兵不是债——① datasource 域零值=「从未同步」的**业务信号**（`lastSyncTime` 进调度比较，改 null 要动调度语义）；② AgentStep 是冻结 SS |
| ✅ B7 | **✅ B7**：依赖级口径（声明+构造赋值 ≤2 次）实锤断成员 19 处全清——只注入不读取字段 3（`PluginSearchParallel`）+ 死 logger 8 + 死 `ObjectMapper` 3  |
| ✅ B8 | **✅ B8**：全限定名注解 177+6 → 0（14 文件，注入前逐文件同名符号冲突扫描）；`@JsonIgnoreProperties` 48 处评估**保留**——14 个严格 mapper 读取点全是外部/不可 |
| ✅ B0（2026-10-02，走查批收官） | **✅ B0（2026-10-02，走查批收官）**：后端 8083（postgres 驱动）+ 前端 5173 + 桩 LLM（127.0.0.1:18090，`b0-stub-chat`）+ 桩 RSS（127.0. |
| ✅ B3b（2026-10-02，由 B0 走查升格） | **✅ B3b（2026-10-02，由 B0 走查升格）**：知识库配置 jsonb 键名统一到 Java 字段名（camelCase）——前端 payload / 服务端读取器 / 落库列三方咬合面同批对齐。 |
| 🚧 B6 批 1（2026-10-02，storage 装配） | **🚧 B6 批 1（2026-10-02，storage 装配）**：`storage/service/DefaultStorageBackendProvisioner` 单文件 **46 处 `System.gete |
| 🚧 B6 批 2（2026-10-02，langfuse） | **🚧 B6 批 2（2026-10-02，langfuse）**：`tracing/langfuse` 12 处清零（全仓 149→93，代码内 88）。 |
| B6 余量口径（下一批动手前先定，2026-10-02 侦察结论） | **B6 余量口径（下一批动手前先定，2026-10-02 侦察结论）**：剩 88 处代码内读取，按上下文分两类—— |
| 🚧 B6 批 3（2026-10-02，检索驱动 + 向量库 env 查找面） | **🚧 B6 批 3（2026-10-02，检索驱动 + 向量库 env 查找面）**：`RETRIEVE_DRIVER` 的 7 个读取点清零（全仓 93→86，代码内 81）。 |
| 🚧 B6 批 4（2026-10-02，系统/部署面 + `GIN_MODE` 改名） | **🚧 B6 批 4（2026-10-02，系统/部署面 + `GIN_MODE` 改名）**：15 处清零（全仓 86→75，代码内 68）——`system` 域代码内 getenv 归零。 |
| 🚧 B6 批 5（2026-10-02，知识域单值：docreader / 批大小 / 清理开关） | **🚧 B6 批 5（2026-10-02，知识域单值：docreader / 批大小 / 清理开关）**：6 处清零（全仓 75→69，代码内 62）。 |
| 🚧 B6 批 7（2026-10-02，存储静态单值族 + JWT 密钥） | **🚧 B6 批 7（2026-10-02，存储静态单值族 + JWT 密钥）**：8 处清零（全仓 64→57，代码内 50）。 |
| 🚧 B6 批 8（2026-10-02，存储 provider 环境族查找面） | **🚧 B6 批 8（2026-10-02，存储 provider 环境族查找面）**：3 处清零（全仓 57→53，代码内 46；**storage 域归零**）。 |
| 🚧 B6 批 9（2026-10-02，检索域：引擎命名/开关/超时族） | **🚧 B6 批 9（2026-10-02，检索域：引擎命名/开关/超时族）**：18 处清零（全仓 53→35，代码内 46→28；**retrieval 域归零**）。 |
| ✅ B6 批 10（2026-10-02，收尾批——裸 `getenv` 代码内清零，B6 结项） | **✅ B6 批 10（2026-10-02，收尾批——裸 `getenv` 代码内清零，B6 结项）**：28 处清零（代码内 46→**0**；余 7 处仅注释/文档提及）。 |
| B6 结项小结（149 → 0） | **B6 结项小结（149 → 0）**：十批分别是 storage 装配 46 / langfuse 12 / 检索驱动 7 / 系统部署面 15 / 知识域单值 6 / common 静态族 5 / 存储单值+JWT |
| ✅ B12（2026-10-02，B0 残留批 1：wiki 槽位与孤儿任务） | **✅ B12（2026-10-02，B0 残留批 1：wiki 槽位与孤儿任务）** |
| ✅ B13（2026-10-02，B0 残留批 2） | **✅ B13（2026-10-02，B0 残留批 2）** |
| ✅ B5（2026-10-02，lf_* 载具嵌套化：判定 + 执行） | **✅ B5（2026-10-02，lf_* 载具嵌套化：判定 + 执行）** |
| ✅ B9（2026-10-02，Go 锚点：政策 + 执行机制落地；清扫仍"随触碰"，不立专项） | **✅ B9（2026-10-02，Go 锚点：政策 + 执行机制落地；清扫仍"随触碰"，不立专项）** |
| ✅ B11 判定（2026-10-02，Gradle 多模块）——结论：不做，正式搁置 | **✅ B11 判定（2026-10-02，Gradle 多模块）——结论：不做，正式搁置** |
| 🐞 B14 修复（2026-10-02，真 bug：非 minio 的「行配置」云凭据被静默丢弃） | **🐞 B14 修复（2026-10-02，真 bug：非 minio 的「行配置」云凭据被静默丢弃）** |
| ✅ B14 合并完成（2026-10-02，两套词汇收成「一面一源」） | **✅ B14 合并完成（2026-10-02，两套词汇收成「一面一源」）** |
| ✅ B15（2026-10-02，供给行明文落库——登记项闭环） | **✅ B15（2026-10-02，供给行明文落库——登记项闭环）** |
| ✅ B16「静默失效」定向扫描（2026-10-02，用户拍板方案 A） | **✅ B16「静默失效」定向扫描（2026-10-02，用户拍板方案 A）** |
| 🚧 B17 换锚收尾·判定批（2026-10-02） | **🚧 B17 换锚收尾·判定批（2026-10-02）**——把「残余 @JsonProperty」逐条判定为三类，本批**无代码变更**（实验性删除已回退，全量复绿）。 |
| ✅ B17② websearch 请求键收口（2026-10-02） | **✅ B17② websearch 请求键收口（2026-10-02）**——`is_default` → camel `isDefault`（§2 第 4 条），**不留兼容别名**。 |
| 🔎 B17① agent 配置面：判定「暂不做」（建议，待用户拍板） | **🔎 B17① agent 配置面：判定「暂不做」（建议，待用户拍板）** |
| ✅ B18 agent 配置面 camel 换锚（专项，2026-10-02 完成） | **✅ B18 agent 配置面 camel 换锚（专项，2026-10-02 完成）**——§2 第 11 条"落库格式走 Java 字段名"的最后一块大口子。⚠️ 本批一度按用户决定中止（见 main 上的定案提交 |
| ✅ B19 前端契约键收口 + 双侧守卫（2026-10-02） | **✅ B19 前端契约键收口 + 双侧守卫（2026-10-02）** |
| ✅ B21（2026-10-02，占位符组键收口 + 「同类情况」定向排查） | **✅ B21（2026-10-02，占位符组键收口 + 「同类情况」定向排查）** |
| ✅ B22（2026-10-02，模板面 4 键 + `api_key` 收口 + 删死模块） | **✅ B22（2026-10-02，模板面 4 键 + `api_key` 收口 + 删死模块）** |
| 🧰 B23（2026-10-02，孤儿夹具审计：工具 + 首次结论） | **🧰 B23（2026-10-02，孤儿夹具审计：工具 + 首次结论）** |
| 🔎 B24（2026-10-02，换锚长尾回头扫：注解面清零 + 真实残留面盘点） | **🔎 B24（2026-10-02，换锚长尾回头扫：注解面清零 + 真实残留面盘点）** |
| ✅ B25（2026-10-02，批甲：模板标志位收口；引用/进度载荷判为冻结） | **✅ B25（2026-10-02，批甲：模板标志位收口；引用/进度载荷判为冻结）** |
| 🐞 B26（2026-10-02，批甲续：websearch 凭据面全链修复 + 其余四面判冻结） | **🐞 B26（2026-10-02，批甲续：websearch 凭据面全链修复 + 其余四面判冻结）** |
| ✅ B27（2026-10-02，批甲续 2：推荐问题键收口 + 6 类面判冻结） | **✅ B27（2026-10-02，批甲续 2：推荐问题键收口 + 6 类面判冻结）** |
| ✅ B28（2026-10-02，批甲续 3：websearch provider-types 配置字段面收口） | **✅ B28（2026-10-02，批甲续 3：websearch provider-types 配置字段面收口）** |
| ✅ B29（2026-10-02，批甲/批乙收口：换锚待判清单 66 → 0） | **✅ B29（2026-10-02，批甲/批乙收口：换锚待判清单 66 → 0）** |
| 🔧 B29 订正（2026-10-02） | **🔧 B29 订正（2026-10-02）**：B29 的 BASELINE 插入代码多写了一个提前闭合字典的 `}`，脚本出现 `IndentationError`；而当时的"待判 66 → 0"是**用坏脚本 gr |
| ✅ B30（2026-10-02，model 凭据面与 MCP/websearch 统一为 camel） | **✅ B30（2026-10-02，model 凭据面与 MCP/websearch 统一为 camel）** |
| ⚠️ B31（2026-10-02，B23 处置：结论修正 + 一次失败尝试的完整记录） | **⚠️ B31（2026-10-02，B23 处置：结论修正 + 一次失败尝试的完整记录）** |
| ✅ B33（2026-10-02，包结构守卫红灯修复：两个工具类归位到最低层） | **✅ B33（2026-10-02，包结构守卫红灯修复：两个工具类归位到最低层）**——B6 批 10 引入的环 5 组 / 依赖 config 1→11 全清：`AppEnvLookup` → `common/deployment`、`StorageRuntimeEnv` → `common/storage`，`ImageResolver` 脱开 storage；守卫回绿；全量 4713 + spotlessCheck 绿。 |
| ✅ B34（2026-10-02，agent/tools 分包：根 + 5 能力子包） | **✅ B34（2026-10-02，agent/tools 分包）**——94 文件 → wiki 30 / knowledge 11 / sql 5 / data 4 / web 2 + 根 42（框架 + 跨族共享 + 通用单件 + MCP 族）；MCP 因与 `ToolRegistry` 同包紧耦合暂留根（登记）；可见性放宽 8 处逐条登记；测试镜像 12 个跟移；全量 4713 + spotlessCheck + 守卫绿。 |
| ✅ B35（2026-10-02，modelcontext 并入 agent） | **✅ B35（2026-10-02，modelcontext 并入 agent）**——顶层包 31 → **30**：13 文件 → `agent/modelcontext/`（test 3 同移）+ 23 文件改包路径（含 FQN/javadoc，零残留）；守卫绿、全量 4713 + spotlessCheck 绿。 |
| ✅ B36（2026-10-03，agentm 并入 agent） | **✅ B36（2026-10-03，agentm 并入 agent）**——顶层包 30 → **29**：`agentm`（20 文件）→ `agent/management/`（子域形态，先例 `auth/apikey/`）；资源目录 `resources/agentm` → `resources/agent/management`；61 文件包路径 + 10 处 loader 资源路径 + 12 处文案 + 3 处脚本；顺手修 AsrTestAudio 过时文案；守卫绿、全量 4713 + spotlessCheck 绿。 |
| 🚧 B37（2026-10-03，档 3 第一刀：provider 请求面退役 Go 转义） | **🚧 B37（2026-10-03，档 3 起步：provider 请求面）**——字节流向盘点（四类：provider 请求 / Redis 事件 / 落库响应 / 工具协议）+ 三份 provider `GoJson` 去 `GoJsonEscapes`（探针先行）；`GoJsonEscapes` 类保留（stream/langfuse/MCP 仍用）；全量 4713 + spotlessCheck 绿。⚠️ stream 面「与 Go 版共用 Redis 的 CAS」待确认 Go 版是否在跑。 |
| ✅ B38（2026-10-03，档 3 第二刀：stream/langfuse/LLM 请求体） | **✅ B38（2026-10-03，档 3 第二刀）**——用户确认「Go 版已下线、不再双跑」（关键决策登记）；`StreamJson`/`RemoteApiBodyCodec`/`LangfuseAttributes` 退役 Go 转义（键序保留=内部 CAS 稳定性）；4 处对齐测试改写；残留 4 处（event/MCP/TodoWrite/GoJsonUtil）；全量 4713 + spotlessCheck 绿。 |
| ✅ B39（2026-10-03，档 3 第三刀：工具面 + 实录约束发现） | **✅ B39（2026-10-03，档 3 第三刀）**——`McpCatalog`/`TodoWriteTool`/`GoJsonUtil` 去 escapes + 两处测试侧清理；⚠️ 发现档 3 硬边界：**Go 形态实录（GoRecording*）无法重录**（录制源已下线 + 禁止手改）⇒ `EventJson`/`RecordingSupport` 的退役已回退（先立基线重建机制）；`GoJsonEscapes` 类暂不能删。全量 4713 + spotlessCheck 绿。 |
| ✅ B40（2026-10-03，实录基线重建：GoJsonEscapes 全量退役） | **✅ B40（2026-10-03，实录基线重建）**——不改实录、改比对方式（`ContractJson.deep` 语义比较，B2 方针的收编）；`EventJson` 退役 + `EngineRecordingTest` 62 处 `assertRecording` + `ToolRegistryRecordingTest` 辅助归一 + **删 `GoJsonEscapes` 类**（零残留）；全量 4709 绿 + spotlessCheck 绿。剩余：`GoTimeSerializer.isGoZero`/`GoMapSerializer`/`GoJsonCodec` 等非 escapes 面。 |
| ✅ B41（2026-10-03，provider JSON 四副本收敛 + 删零引用） | **✅ B41（2026-10-03，四副本收敛）**——`embedding/rerank/websearch/retrieval` 四份副本 → `common/web/ProviderJson`（超集）；删零引用 `GoFloatSerializer`；42 文件改写 + 20 补 import；**Go\* 26 → 21**；全量 4709 + spotlessCheck 绿。 |
| ✅ B42（2026-10-03，档 3 第五刀：工具协议面） | **✅ B42（2026-10-03，工具协议面）**——`GoHtml`/`GoJsonValues` 改名（非 Go 语义）+ `GoJsonCodec`→`ToolJson`（去 HTML 转义、保键序）+ `GoQuoting`→`JsonQuoting`；`RecordingSupport.normalizeEscapes` + `ToolJsonRecordingTest`（语义比较 + 键序钉子）；**Go\* 21 → 17**；全量 4710 + spotlessCheck 绿。 |
| ✅ B43（2026-10-03，档 3 第六刀：真退役） | **✅ B43（2026-10-03，工具协议面换 Java 原生）**——删 `JsonQuoting`（→ `ToolJson.quoted`，收敛 WeaviateGql 第 5 份拷贝）/`GoValueStr`（→ 原生 `valueStr`，`<nil>` 退役）/`GoJsonMarshal`（→ `ToolJson.prettyJson`）+ `ToolJson` 手写 writer 退役（Jackson + 只留键排序）；测试基线化；**Go\* 17 → 15**；全量 4710 + spotlessCheck 绿。 |
| ✅ B44（2026-10-03，档 3 第七刀：connector 面） | **✅ B44（2026-10-03，connector 三刀）**——删 `GoBase64`（→ JDK Base64，GitLab 换行处理保留）/yuque `GoDuration`（→ `Double.parseDouble`，与 Feishu 收敛）/`common.time.GoDuration`（→ ISO-8601 文案 `PT1H10M`）；`GoCompat`/`Yuque`/`Housekeeping` 测试收编；**Go\* 15 → 12**；全量 4706 + spotlessCheck 绿。 |
| ✅ B45（2026-10-03，档 3 第八刀：GoStrings 收敛） | **✅ B45（2026-10-03，空白实现统一）**——两份 `GoStrings`（wiki 96 行 + gitlab 82 行）删除 → `common/text/Whitespace`（`isSpaceChar`+6 = White_Space 精确等价）+ `common/text/CodePointOrder`；16 文件 45 处改写；`trim(x,'/')` → 正则；**Go\* 12 → 10**；全量 4706 + spotlessCheck 绿。 |
| ✅ B46（2026-10-03，档 3 第九刀：验证驱动裁决） | **✅ B46（2026-10-03）**——`GoUrl`/`GoPath`×2 **保留**（探针实测：转义 7+2 处差异 / 路径 21/24 一致但空结果差异含安全退步；无真实环境可验证）；`GoStyleErrorReportValve` = 误标（HTTP 契约非 Go 复刻）；**档 3 口径改为逐类裁决**。 |
| ✅ B47（2026-10-03，保留类改名） | **✅ B47（2026-10-03）**——保留类修标签：`GoUrl`→`GitLabUrl`、`GoPath`→`GitLabPath`/`PosixPath`（归位 common/text）、`GoStyleErrorReportValve`→`PlainTextErrorReportValve`、`GoCompatTest`→`GitLabCompatTest`；行为零变更；全量 4706 + spotlessCheck 绿。 |
| ✅ B48（2026-10-03，注释大清洗专项：Go 锚点/翻译腔/过期引用归零） | **✅ B48（2026-10-03，B9 专项化收官）**——main+test 11,932 匹配行/1,723 文件起步 → 棘轮基线 3,999→**0**（`--write` 已刷新）；不变量中性化（落库行为清单/SSE 帧契约/签名算法等事实逐条保留）+ 裸形态盲区补扫 + D 类错误陈述就地核实修正（wiki 列默认值、favorite 旧信封形态、mcp 键名方向等）；白名单五类留档（域词/@DisplayName·断言消息/方法名/有效 § 引用/GoRecording* 实录）；与档 3 交叉期悬挂 `{@link GoXxx}` 按过期引用清。compile + spotlessCheck + 棘轮绿。 |
| ✅ B52（2026-10-04，B48 排查批：审批事件流断链修复） | **✅ B52（2026-10-04）**——`ApprovalBridge` 原样转投致三类审批事件 instanceof 失配、聊天流审批卡静默消失；补 `toPayloadData` 四对 DTO 映射 + `ApprovalBridgeTest`；B48 三项待复核全部闭环（score=语义比较器归一无需动作 / 解密空 if 删除 / wiki 零值 page_type 不可达）。回归 + spotlessCheck 绿。 |
| ✅ B53（2026-10-04，知识面卡片模型键收口 + 失败原因接线） | **✅ B53（2026-10-04）**——三键定性：`original_file_name`/`display_name` = 内部视图模型键（写读同套、功能正常但属 Go 时代遗留）、`error_message` = 死字段（接口给 camel `errorMessage`，全仓无人读写）；用户拍板「收口 + 接线」。收口 5 文件（写侧 `useKnowledgeBase` camel 化 + 读侧 3 处同步）；接线 = 卡片悬停浮层失败态显示原因（紧凑时间线不含 `lastError`，此前无任何出口）；守卫 = `crossFaceKeyContract.test.ts` 第 4 条（红态探针验过）。前端全量 704 + `vue-tsc` 0 错误 + 契约键守卫绿。 |
| ✅ B54（2026-10-04，api 面 snake 键收口 + 裁撤死参数收尾 + 守卫扩面） | **✅ B54（2026-10-04）**——由 B53 追问扩展到 api 线格式面：修 12 处「接口 camel / 前端 snake」断链，其中**改密实测恒失败**（snake body → 400 `oldPassword/newPassword: 不能为空`）、邀请 6 键 + 5 消费点（邀请人列此前显示用户 ID）、auth 时间键（注册时间被兜底写成当前时刻）、wiki 修订 4 消费点（`vue-tsc` 抓出）、KB 复制 `sourceId/targetId`（探针证实）、标签 `sortOrder`；裁撤死参数：`api/knowledge-base` 5 个接口的 `agent_id/agent_source_tenant_id` 全删 + 过期 javadoc（根因=空间分享裁撤漏清中间层）。守卫第 5、6 条：api 面 `*_at` 全禁 + snake 记号棘轮（32 键/只许减/红态探针两轮）。前端 706 + `vue-tsc` 0 错误 + 后端 compile/spotless 绿。遗留 24 键待核实 → B55。 |
| ✅ B55（2026-10-04，api 面 snake 记号逐条核实） | **✅ B55（2026-10-04）**——B54 挂起的 32 键全定性：**13 改 camel**（偏好 `lastActiveTenantId` 探针证实「回到上次空间」此前永不生效、`oidcOnlyLogin` 门禁、wiki 六键→页数恒 0/问题标签/举报人、`requireApproval` MCP 审核开关、KB 摘要四键含 `knowledgeCount`、`isDefault`）、**6 删死字段**（agent 三键 + 建议问题死参数 + 浏览器连接偏好 + UserInfo.knowledge_bases）、**13 已核实合法**入基线（`kb_filter` 一族从预设 JSON 透出、`file_types`、`owner_id`、chat-history jsonb、embed 协议、KB 筛选查询参数）。棘轮 32→13（只许减）+ 红态探针复验。前端 706 + `vue-tsc` 0 错误 + 契约键守卫绿。 |
| ✅ B56（2026-10-04，技能区文案/死键/宿主技能目录） | **✅ B56（2026-10-04）**——点检追问「技能管理为何没了」查实＝功能裁剪第一批（PR3 `caef9d5`，技能降级为指令型）。本批：① 4 条在用文案五语言改指令型口径；② 删 19 个零引用死键 ×5 语言（沙箱/安装期遗留）；③ dev 宿主技能目录 + `.env`/`dev-env.sh` 注入（实测 `GET /api/v1/skills` 列出 2 技能）；④ 更正控制器 javadoc 的 env 名（踩坑：必须复数 `WEKNORA_SKILLS_HOST_DIRS`）。i18n 审计 11/11 + 前端 706 + `vue-tsc` 0 错误 + 后端 compile/spotless 绿。⚠️ ③④ 由 **B57（技能入库）** 取代。 |
| ✅ B57（2026-10-04，技能管理回归：入库 + 平台级 CRUD + 宿主目录退役） | **✅ B57（2026-10-04）**——方案三稿收敛（复用原 `/api/v1/skills/catalog` 路径 → 改指令型 → 入库）。① `V4__skills.sql`（平台级、软删；主键 varchar——首版 uuid 被 PG 拒，未发布直接改基线重跑）；② `DbSkillSource`（复用 `Skill.parseSkillFile`，每轮装配新建→下一轮生效）+ `Manager` 泛化为 `List<SkillSource>`；③ 管理面 5 端点（`install` 不实现）+ frontmatter 服务端组装 + `requireRuntimeIdentity` 拦静默改名 + 仅 SystemAdmin + `skill.created/deleted` 审计；④ 退役 `Loader`/`host-dirs`/dev 注入；⑤ 前端「技能管理」页（设置→系统管理组；新建/查看/删除 + 引用清单 + force 二次确认）+ 五语言 38 键 + 7 单测。实测：201→选择器可见→400/409→引用 409→force 204；后端 4,702/0 失败 + spotless、前端 716/716 + tsc 0 错误 + 契约键守卫绿。⚠️ 点检当场暴露**菜单漏登记分组**（设置 section 需三处登记：权限集合 / navGroups 显式清单 / 容器；本批漏第 2 处 → 菜单不渲染）→ 已修 + 新增 `settingsNavGroups.test.ts` 守卫（红态探针验过）。 |
| ✅ B58（2026-10-04，旧信封读法清剿：Go `{success,data}` 残留 → Java 裸载荷） | **✅ B58（2026-10-04）**——用户点检 `?section=integration-api` 报「加载 API 集成设置失败」：`/auth/me` 裸信封（tenant 在顶层）而页面读 `userResp.data.tenant` → 恒 undefined → 抛错。同族一次清剿 8 处（**先 curl 实测端点形状再改**）：集成页 tenant/agents、聊天页开场建议、FAQ 导入 taskId、KB 文件夹树、KB 重命名 `moved_count`（成功误报失败）、KB 跨库移动 `task_id`、移动进度轮询（tick 空转）、轨迹探测（入口被隐藏）。症状三类＝抛错 / 静默为空 / 误报失败；静态检查抓不到（键名与形状都"存在"）。有意适配的 API 层与冻结面未动。守卫 `crossFaceKeyContract.test.ts` 表格钉 9 条（红态探针验过）；前端 717/717 + tsc 0 错误。 |
| ✅ B59（2026-10-04，技能编辑：PUT 更新 + 改名保护 + 编辑弹窗） | **✅ B59（2026-10-04）**——用户要求「查看内容」改「编辑」、弹窗同新建。① `PUT /api/v1/skills/catalog/{id}`：收 name/description/content，**slug 不可改**，重新组装 + `parseSkillFile` 自校验 + version 自增；② **改名保护**：name = 运行期身份（`selectedSkills`）→ 被引用时改名 **409 + 引用清单**，同名不算改名（只改描述/正文放行）；③ `GET /{id}` 作编辑草稿（content = 正文，剥 frontmatter，可逆性由单测钉住）；④ 审计 `skill.updated`；⑤ 前端共用弹窗（回填/加载态/slug 只读/改名行内报错 + 保存置灰），五语言 −4/+9 键，纯逻辑单测 7→10。**浏览器端到端**：回填 ✓、保存（v+1 + 审计 + 列表刷新）✓、改名拦截（点名引用者 + 置灰）✓；后端 4,704/0 失败 + spotless、前端 720/720 + tsc 0 错误。 |

| ✅ B60（2026-10-04，技能租户化：平台级 → 空间级 + 菜单并入「数据与扩展」） | **✅ B60（2026-10-04）**——用户提「技能配租户配置 + 菜单挪到数据与扩展」，核实为修隔离缺陷（`GET /api/v1/skills` 读全表 → 跨租户可见；slug 全局唯一挡住同名）。① `V5__skills_tenant.sql`（tenant_id + 命名空间唯一索引，NULL=平台内置只读层）；② 服务层 `visibleScope` 单点表达可见范围（null → 仅平台层）、平台行 403、命名冲突 409；③ 引用清单按空间 + `is_builtin`；④ 运行期/选择器按当前空间读；⑤ RBAC 改空间 admin；⑥ 菜单挪组 + 内置行「查看」只读态 + 五语言 −4/+3 键口径更新。实测：第二空间目录/选择器空、同名 slug 201、跨空间 404、平台行 403；UI 实测菜单在「数据与扩展」、另一空间 owner（非系统管理员）可见。后端 4,708/0 失败 + spotless、前端 720/720 + tsc 0 错误。 |

| ✅ B61（2026-10-04，技能按需读取打通 + @点名注入正文） | **✅ B61（2026-10-04）**——用户追问「如何加载 skill」→ 实证**Level 1 通、Level 2/3 断**（`skill://` 无解析器 / `read_file` 无实现 / Manager 读面零调用者）。① A：新增 `SkillReadFileTool`，契约对齐 `GoRecording45C` 的 31 条 `read_file/*`（schema·描述逐字、三段式输出、data 键、8 类错误文案、行数语义与分页），非 `skill://` 走 `exec_no_source` 文案；技能启用时由装配器注册；② B：`skill_instructions` 段注入 @点名技能正文 + `must_use` 措辞随注入结果切换（失败回退 read_file）；修 pinned 描述空串。验证：单测 12 条 + **真实回合**（记录桩）——工具清单含 read_file、提示词含注入正文、工具真实执行回 `size=559 bytes`。后端 4,720/0 失败 + spotless。 |

| ✅ B62（2026-10-04，WeKnora Cloud 整功能裁撤） | **✅ B62（2026-10-04）**——用户：「去掉 WeKnora Cloud 设置」。核查发现它**不只是设置页**（还是模型提供商 + 解析引擎 + 三处适配器）→ 摆三选项请用户拍板 → 选**整功能裁撤**。① 后端 35 文件/整删 8（Service·Controller·2 DTO·Provider·Embedder·Sign·Reranker）；② 去分支：provider 注册表+枚举（删**最后**一项，序即对外顺序）、`AuthCreds`→`apiKey`、`RemoteApiChat` 校验与死字段、两工厂、`ModelRuntimeFactory`（→`modelCredentials`）、`ModelConnectivityTestService`×3、`VlmClient`+`VlmHttpTransport`、`ParserEngineRegistry`（常量+注册块+「去设置」文案）、`SystemController`×3、`WebConfig`(RBAC×2+pathPatterns)、`APIKeyRoutePolicies`×2；③ 前端 20 文件/整删 2，对话框与解析引擎页去云 + 五语言去块；④ 测试：删云测试类、8 类去用例、golden 去条目、删 4 fixture。**dev 零存量** → 无数据迁移。实测：云端点 **404**、providers **26**、engines **9**、UI「模型」组仅 模型管理/Ollama；后端 4,704/0 + spotless、前端 720/720 + tsc 0。**留痕**：脚本"大括号配对删块"在无花括号语句上会跑偏（`new ProviderEntry(...)`、`boolean cloud = …`）→ 已恢复并改精确文本删除。 |

| ✅ B63（2026-10-04，embed 语言「跟随浏览器/宿主」失效） | **✅ B63（2026-10-04）**——用户点检 `widget-test.html`。根因：**派生语言被写进持久值**。`applyEmbedLocale()` 一律 `localStorage.setItem`，而取值顺序 URL → localStorage → 浏览器，于是渠道默认语言 / 预览 `?locale=` 一旦应用就永久压过浏览器语言 ⇒ 改回「跟随」不生效（真机复现：陈旧 en-US/zh-CN → 显示 en/zh）。修：新增 `applyDerivedEmbedLocale`（派生值不落盘）+ `clearStoredEmbedLocale()`，跟随分支先清后取浏览器语言；`syncEmbedLocaleFromUrl` 转派生（预览不再污染访客）；`<html lang>` 随语言同步；widget 脚本支持 `locale`/`data-locale` → URL `?locale=`（首屏即对语言 + 宿主优先），握手前 `setLocale()` 排队补发。验证：真机 6/6 + 渠道默认语言生效且可被宿主覆盖（DB 临时值已还原）；2 单测 + 1 源码守卫（红态探针 ×2）。前端 723/723 + tsc 0。**口径**：能重新推导的派生值不要落盘。 |

| ✅ B64（2026-10-04，「跟随宿主」补上宿主页面语言） | **✅ B64（2026-10-04）**——用户追问「为什么配置跟随还是英文」。实测三场景：浏览器 zh→中文 ✓；浏览器 en→英文 ✓；**浏览器 en + 页面 `<html lang="zh-CN">`→英文 ✗**（既说明用户浏览器语言是 en*，也说明"跟随宿主"只做了"跟随浏览器"）。修：widget 在无显式 locale 时读宿主页 `<html lang>`，走独立参数 `?hostLocale=`（不占 `?locale=`，避免压过渠道默认语言）；`matchEmbedLocale()` 严格归一化（认不出→null 而非兜底 zh-CN）；优先级＝宿主显式 > 渠道默认 > 页面 `<html lang>` > 浏览器语言，全部派生不落盘；渠道说明改五语言。真机 4/4（含不支持语言回落、显式声明压过页面声明）+ B63 六项回归；3 单测 + 守卫补 2 不变量（红态探针 ×2）；前端 724/724 + tsc 0。 |

| ✅ B65（2026-10-04，嵌入渠道保存后发布密钥丢失） | **✅ B65（2026-10-04）**——用户报「嵌入代码原来正常，点保存后变成 `<!-- 加载渠道密钥失败… -->`」。根因：`publishToken` 只在详情/创建/轮换响应返回（授权边界），**列表行与 PUT 响应都不带**；面板靠详情 `mergeChannelDetail` 合并 token，而保存分支 `await load()` 用列表行整体重建数组 ⇒ token 被冲掉 ⇒ snippet 退化。修：新增 `embedChannelTokenRegistry`（本会话记 token，`load()` 后 `hydrate` 贴回、删除 `forget`；仅内存不落盘），面板三处接线，**不动后端**（避免扩大 token 暴露面）。真机复现→修复：保存前/后 snippet 均含 `em_…`（此前保存后即变提示，与用户描述一字不差）；同族（开关/新建/轮换）同覆盖；3 单测 + 守卫 3 不变量（红态探针 ×2）；前端 728/728 + tsc 0 + i18n 11/11。 |

| ✅ B66（2026-10-04，Agent 编辑器提示词变量全空） | **✅ B66（2026-10-04）**——用户问 `/platform/agents` 的变量「点击插入 / `{{` 唤起列表」。根因：`/api/v1/agents/placeholders` 与 `/type-presets` 是**裸载荷**，api 层却声明 `get<{ data: … }>` 又没适配 → store 读 `?.data` 恒 undefined ⇒ 芯片空 / `{{` 无弹出 / 类型预设空。修：两处改为显式适配 `return { data: resp }`（同 KV 三兄弟/web-search 约定），消费端契约不动。真机红→绿：芯片 0→有、`{{` 无→5 项、点击插入 ✓、store 直读 presets 5 + placeholders 七组齐全；全仓仅这 2 处漏适配。守卫 1 条 + 红态探针；前端 729/729 + tsc 0 + i18n 11/11。**口径**：裸载荷 ≠ 没包裹——不许"声明 {data} 而运行时是裸的"。 |

| ✅ B67（2026-10-04，KB 解析设置整页崩：规则键名错面） | **✅ B67（2026-10-04）**——用户贴 `getEngineForGroup` 的 `undefined.some`。KB 配置面是 **camel**（B3b：V2 迁移 + `ParserEngineRuleView(fileTypes,…)`；用户那条 KB 正是全仓唯一 camel 规则库），解析设置页却按 **snake** 读 → 崩页（真机：分区 select 0 + 同款堆栈）。同族：写入侧 emit snake → 后端按 camel 收 → 落库 `fileTypes: []`（规则从未生效）；`UploadConfirmDialog` 两面直接透传；相关类型也是 snake。修：`utils/parserEngineRules.ts` 作两面互转唯一出口 + 解析设置 camel 化并归一化读取，覆盖/智能体面保持 snake（运行时契约）。真机：22 select / 0 报错 / 读值正确（Excel=simple）/ 保存后 camel 完整落库。守卫 1（红态探针 ×2）+ 3 单测；前端 733/733 + tsc 0。**口径**：同一概念在不同面可约定不同键名，搬运必须显式转换、读侧容忍历史数据。 |

| ✅ B68（2026-10-04，智能体「推荐问题」恒空） | **✅ B68（2026-10-04）**——用户问某库有无自动生成问题（**有**：enabled + 每块 4 个、23/24 chunk 已生成）。顺带查出：推荐问题读取侧按 **snake** `generated_questions` 过滤/取值（`LIKE` + JSON path），写入侧 `DocumentChunkMetadata` 与全库都是 **camel** `generatedQuestions`、从无改名迁移 ⇒ SQL 恒 0 行、解析恒 null ⇒ 推荐问题永远为空（真机端点 `[]`）。修：SQL camel 主 + OR 容忍 snake、解析 camel 优先 snake 兜底。真机红→绿（探针数据已清）；新增 2 条测试（解析容错 + 扫所有 @Select 的键名守卫，首版钉错方法得假红已修正）+ 红态探针 ×2；后端 4,706/0 + spotless。**口径**：写入侧 camel / 读取侧 snake 是本仓反复出现的一类错（B58/B62/B66/B67/B68）——字面键名的过滤与取值必须与写入侧对齐。 |

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

**✅ B10（2026-10-02，代码级架构规则进 CI）**
- **前提核对**：B10 原描述「环 0 组基线 + 包依赖白名单固化」**已于 2026-09-30 在 CI**（`scripts/check-package-cycles.py` 挂 guards job，环/分层/域依赖 `config` 三项带基线棘轮）。本批的真实缺口是 **ArchUnit 级（代码级）规则**。
- 新增 `server/src/test/java/com/ragagent/arch/ArchitectureRulesTest.java`（测试依赖 `com.tngtech.archunit:archunit:1.3.0`），四条**当前零违例**的规则：
  - **R1 禁裸 `System.getenv`**（守 B6 成果 149→0；含无参重载）；
  - **R2 `@ConfigurationProperties` 类必须被 `@ConfigurationPropertiesScan` 名单覆盖**（漏扫描 = **静默**取默认值，B6 期间反复踩）；
  - **R3 配置类不得同时 `@Component/@Service`**（双装配）；
  - **R4 `install*`（启动期写入查找面/快照）只许 `*.config` 装配层调用**（把批次 6~10 各类注释里的约束变成红条）。
- 分工写进类注释与 CI 注释：**包级**归脚本棘轮、**代码级**归 ArchUnit，不重复建设。
- 两条实现要点（都属「静态闸门盲区」同类）：① 导入面必须按输出目录过滤到 **main**（`importPackages` 会连测试类一起扫，而测试里读真实 env 是**合法**的——各 connector 桩要读宿主 env 拼 SSRF 白名单），并加「导入面 >500 类」自证断言防**规则空转**；② **`noClasses().should(自定义条件)` 会把条件取反**（手写 violation 被反转成通过），R4 因此一度「永远绿」。
- **验证（核心）**：造一个探针类（未扫描包 + `@Component`&`@ConfigurationProperties` 双注解 + 内部调 `System.getenv` 与 `AppEnvLookup.install`）同时触发四规则 → **四条确实全红**（首次只红三条，正是借此发现 R4 空转）→ 删探针复绿。闸门：`spotlessCheck` 绿 + 全量 **4697** 测试绿（+4）。命令：`./gradlew :server:test --tests "com.ragagent.arch.ArchitectureRulesTest"`。
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
| 引用载荷（`knowledge_references` / `data.references`） | ✅ 已 camel（B88），双读分支已清（B92） |
| SSE/Redis 事件载荷键（`session_id`/`tool_name`/`total_steps`…） | ⬜ 可改造（前后端同批，无需双读） |
| wiki 内容图片标记（`<image_caption>`/`<image_ocr>`/`<image_original>`） | ✅ 已完成（B93a：标记名 → `<imageCaption>`/`<imageOcr>`/`<imageOriginal>`；**chunk_type 值 `image_ocr`/`image_caption` 是枚举值，保留**） |
| 落库 jsonb 存量键（agent_steps payload、memory 抽取状态、租户配置内容） | ⬜ 可改造（开发库可清，不写迁移脚本）——**租户配置内容已部分换锚（B132：`chat-history-config` / `retrieval-config` 两段 12 键 → camel）**；余段（web-search / parser-engine（27 键）/ storage-engine（63 键）等）待逐段判定 |

