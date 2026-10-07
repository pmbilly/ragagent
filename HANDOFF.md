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
| P0 包间解环 | 全仓包间环 / 依赖 config 包 | **环 0 组**、依赖 config 仅 1 包、L2→L3 6 条（方向合法，属阶段 4）——守卫 `python3 scripts/check-package-cycles.py` |
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
   下一步候选：**wiki（54）/ auth 余面（138）/ retrieval（21）/ agent（15）/
   datasource connector 面（若将来要改对方 API 版本）**（§2 第 4 条落地范围）。
   硬约束：**序列化层删除必须一次性全仓完成**，半删状态最危险（§5 阶段 3）；时机由用户定，可与阶段 2 对调。
   **入场前先做**：§14.9 的"端点 × 前端"清单盘点。
3. **阶段 4 其余域标准化 + 架构调整**（Gradle 多模块 + ArchUnit 边界固化等）：未开始。
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
- [ ] **死成员清零**：未使用 logger / `ObjectMapper` / 私有方法 / 局部变量 / **只注入不读取的 final 依赖**（口径见 §13.15 ①④）；javac 不报未使用私有成员、Spotless 也只查 import，**必须主动扫**
- [ ] 触点变更后按 §14.4 分档收口：**常规批** = `--rerun-tasks` 重编 + 受影响域测试 + `spotlessCheck`；
      **结构搬迁批** = `clean :server:test :spotlessCheck`（+ 触及前端契约时 `vue-tsc` 0 错误 / `npm test` 全绿）
- [ ] §4 数据、§12 地图、§13 经验、本节候选表四处同步更新

### 14.6 不要做什么（踩过的坑，别再踩）

- **别把"Go 序列化层删除"拆到各域**：409 处引用 / 94 文件的那一刀按 §3 红线必须**一次性全仓完成**。
  按域先换锚（同 PR 带前端）是允许的，删序列化器本体不是。
- **别动 §11 的边界清单**：租户配置 jsonb（`chat_parser_engine_rules` 等）、auth 域、agent 域 fixture（`ag-*`）、**Go 工具面 5 类**（`GoDoubleSerializer`/`GoTimeSerializer`/`GoMapSerializer`/`GoJsonEscapes`/`GoJson`——线上注解已清零，但手搓载荷/provider 请求体仍依赖其字节）、chat/工具域手搓载荷与**工具输出自有 schema**、检索引擎索引文档、**`lf_*` 平铺追踪载具**（§14.9q D3：`TracingContext` 平铺进 4 个队列载荷，前缀是防撞名的命名空间；要清理应改为嵌套 `tracing` 键，不是去前缀）、**connector 第三方线格式**（`datasource/connector/**` 350 处，字段名由对方 API 决定）、**wiki LLM 输出解析面**（§14.9r：`CombinedExtraction`/`NewSlugFromCitation`/`CitationBatchResult`/`common/wiki/ExtractedItem` 约 17 处，键名由 `WikiPrompts` 三条 prompt 的正文钉住——要改 Java 侧键名必须连 prompt 一起改，属行为面，另批处理）、
**image_info 面**（§14.9s：`chunks.image_info` 列与 `retrieval.domain.ImageInfo`，入站是 **docreader Go 容器**
`PUT /knowledge/image/{id}/{chunkId}` 体的内层 JSON——键 `original_url/ocr_text/start_pos…` 由对方服务决定，
存储列透传同形，管线读侧 `.path("original_url")` 等不得"顺手 camel 化"）、
**chat span/log 载荷的 SearchParams**（§14.9s：`common.pipeline.SearchParams` 序列化进 PipelineLog params 载荷，
SearchRecordingTest 金片钉住 snake——死注判定必须扫"参数对象被泛型序列化"的路径）、
**Go 零值时间哨兵**（`0001-01-01T00:00:00Z`：AgentStep 时间戳、agentm/init 的 GO_ZERO_TIME 与 goTime 系——
涉冻结事件面与既有前端，登记保留；换 null 属另批形状变更）、
**rerank RankResult**（`index/document/relevance_score`，Jina/Aliyun/Lkeap 的第三方 rerank API 响应面）——
  这些"仍是 snake"是**对的**。**注意该清单会随各域推进而变动**：`wiki 域实体` 条目已作废
  （`b407769` C 波把 `wiki/domain` 换锚为 camelCase），`wiki/service` 残留的 `@JsonProperty` 载荷随其批次处理；
  **引用前先看 §14.3 该域的进度栏，别照抄旧结论**。
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
| **B26 批甲续（websearch 凭据面）** | 逐面看消费者链 | P2 | 中 | 🐞 **修掉真 bug（2026-10-02）**——凭据面三处键名错位（保存静默失效 / 删除 400 / 徽标恒「未配置」），金鹰记录的缺陷态一并纠正；另四面判冻结并入 BASELINE |
| **B27 批甲续 2** | 推荐问题键收口 + 6 类面判冻结 | P3 | 小 | ✅ **完成（2026-10-02）**——`knowledgeBaseId` 收口；观测面/内部预设名/agent_steps 落库桶/存储引擎面/wiki 内部状态/模型契约均判冻结并入 BASELINE；待判 80→66 |
| **B28 批甲续 3** | websearch provider-types 字段面收口 | P3 | 小 | ✅ **完成（2026-10-02）**——`labelKey`/`descriptionKey` 收口（只改键、不动 i18n 值）；闸门全绿 |
| **B29 批甲/批乙收口** | 换锚待判清单清空 | P3 | 小 | ✅ **完成（2026-10-02）**——66→0：按族判定（MyBatis 列名/观测面/引擎 DSL/存量配置 jsonb/模板令牌），model 凭据面登记为例外待拍板；扫描器边界（文件级条目）已写入脚本 |
| **B30 凭据面统一** | model ↔ MCP/websearch 统一 camel | P3 | 小 | ✅ **完成（2026-10-02）**——只动 API 层（存量存储层与第三方载荷未动）；确认 PUT 体本就 camel（无第二个 B26）；棘轮减侧生效（清理 2 条基线） |
| **B31 B23 处置** | 孤儿夹具该不该删 | P3 | 小 | ⚠️ **结论修正（2026-10-02）**——115 个里 102 个是录制脚本清单（证据链）；一次删除 13 个的尝试导致 2 条测试失败、已全部还原；工具加固为"仅线索、禁批量删"；**一个都不删** |
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
| **B80 契约文案换锚·第一部分（类型名）** | `jsonTypeLabel` 词表 → JSON 类型名（null/boolean/number/array/object）+ 补钉子 | P2 | 小 | 🚧 **部分完成（2026-10-07）**——只做无金片面：`SystemSettingRegistry.jsonTypeLabel` 换 Java/JSON 标准词表（`<nil>→null`、`bool→boolean`、`float64→number`、`[]interface {}→array`、`map[string]interface {}→object`），`expected bool`→`expected boolean`；消息模板不变。**原盲区补钉**：新增 `SystemSettingRegistryTest`（12 条断言）+ **红态探针已验**；金片零变化（已核实无夹具钉类型名分支）。⚠️ 未完成：① auth 类型错文案（2 金片，`record-w5a-golden.sh`，不支持 `-Dcontract.refresh` ⇒ 需起本地服务重录）② ASR/VLM 错误文本（~7 金片，w5b/modeldebug 脚本）③ 新发现 gin 校验文案面（`Key: '…' Error:Field validation…`）仍钉 **32 金片 / 8 域**，超出原批准清单待拍板 ④ 同类非 go 名孪生（wiki/mcp/ollama `non-object into Go value`）**0 金片**（盲区，改前补钉）。详见 15.1.1 |
| **B81 盲区孪生文案换 Java 标准 + 补钉** | wiki/mcp/ollama 三处 `non-object into Go value` → `expected JSON object, got <类型名>` + 三处新钉子 | P3 | 小 | ✅ **完成（2026-10-07）**——① 新增共享 `ToolJson.nodeTypeLabel`（Jackson 节点类型小写：null/boolean/number/string/array/object），`SystemSettingRegistry.jsonTypeLabel` 收敛为委托（去掉第二份映射）；② 三处孪生换文案：`WikiRequestSupport`（`Invalid request body: expected JSON object, got …`）、`McpServiceCrudOps`（同式）、`OllamaManageService`（同式，顺删本地 `jsonKindName`（旧词表含 `bool` 不符合标准））；③ **原盲区补钉**：新增 `WikiRequestSupportTest`/`McpServiceCrudOpsBindingTest`/`OllamaBindJsonObjectTest`（+ 设置域钉子仍绿），**三处红态探针逐一验过**（临时改坏 → 三红；还原后绿）；④ 金片零变化（已核实 0 夹具钉这三处文案）。闸门：全量 **4,829**/0（6 跳过）+ spotlessCheck 绿。详见 15.1.1 |
| **B82 gin 校验文案换锚** | 17 文件生成点收敛到 `RequestFields` + 32 金片重锚（8 域） | P2 | 中 | ✅ **完成（2026-10-07）**——旧文案 `Key: '<Struct>.<Field>' Error:Field validation for '…' failed on the '…' tag`（gin 味）散布 17 文件（7 份 helper 副本 + 10+ 内联）。新文案（单一实现 `common/web/RequestFields`）：`field '<camel>' is required` / `is not a valid email address` / `is below the minimum` / `is above the maximum`，其余 tag 兜底；字段名 PascalCase→camelCase（`TenantID→tenantId`、`LLMModelID→llmModelId`）；单引号免转义。32 金片 + 3 测试文件断言按同规则转换，**由全量测试自证与实现逐字一致**。⚠️ 事故一则：首版用双引号 → 夹具/Java 字面量双转义，compileTestJava 9 错；改单引号一次通过。⚠️ `structName` 形参保留签名（不再渲染），调用点清理登记后续。闸门：全量 **4,831**/0 + spotlessCheck；gin 文案残留 0。详见 15.1.1 |
| **B83 auth 类型错 + ASR/VLM 错误文本换锚** | `json: cannot unmarshal …` 族退役 → 字段级文案；ASR 错误文案 → `HTTP <状态行>: <详情>` | P2 | 中 | ✅ **完成（2026-10-07）**——① auth：`TenantBindSupport.stringFieldTypeError`（原 `stringFieldValue`）、`TenantCrudOps` 非对象、`AuthSessionOps` 六处（非对象/refreshToken/tenantId 类型/小数/越界）全部改字段级文案（`field 'name' must be a string, got number`、`field 'tenantId' must be an integer, got string`、`… must be an integer`、`… is out of range`）；顺删 `AuthSessionOps.jsonKindName`、`TenantBindSupport.jsonKindName`、`AuthController.SWITCH_ANON_STRUCT_TYPE` 三个 Go 面常量/helper；② ASR/VLM：`openAiErrorText` 重写为 `HTTP <状态行>: <详情>`（详情取 `error.message`，取不到退回 body 原文），**顺删 `jsonErrorText`（Go 逐字符 JSON 报错仿真器退场）**；③ 2 个 auth 金片 + 6 个 ASR/VLM 金片按同规则转换（测试自证一致）；④ 修两处陈旧注释（ASR `%!s(<nil>)` 描述、wiki 截断报错文案）。⚠️ 语法坑一则：`must be a integer` → wrongType 加冠词判断（an/a），红金片当场抓出。闸门：全量 **4,831**/0 + spotlessCheck。剩余 Go 味文案登记 B84。详见 15.1.1 |
| **B35 modelcontext 并入 agent** | 顶层包 31 → 30（用户 2026-10-02 拍板） | P2 | 小 | ✅ **完成（2026-10-02）**——13 文件 → `agent/modelcontext/`（test 3 同移），23 文件改包路径零残留；守卫绿、全量 4713 + spotlessCheck 绿。详见 15.1.1 |
| **B36 agentm 并入 agent** | 顶层包 30 → 29（用户 2026-10-03 拍板） | P2 | 小 | ✅ **完成（2026-10-03）**——`agentm`（20 文件）→ `agent/management/`（先例 `auth/apikey/`），含资源目录改名 + 61 文件包路径 + 10 处 loader 路径 + 12 处文案 + 3 处脚本；顺手修正 AsrTestAudio 的过时错误文案；守卫绿、全量 4713 + spotlessCheck 绿。详见 15.1.1 |
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

租户配置 jsonb（`auth/domain/tenantconfig`）、connector 第三方线格式（datasource 345 处等）、
SSE/Redis 事件载荷（event/stream）、provider 请求体（llm/ollama/anthropic）、LLM 输出解析面
（wiki/mcp/memory）、image_info（docreader 第三方载荷）、
Go 工具面 5 类、IM 平台 ACK、chat span 载荷（SearchParams）、rerank RankResult、
i18n 键与系统设置键（`tenant.default_storage_quota_gb` 等——DB/文案字符串）。
