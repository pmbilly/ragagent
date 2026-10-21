# 基于业务反向评估的架构方案（2026-10-21，第四轮）

> 评估基线：`v0.7.x` @ `de7c7c854`（工作树仅他人会话的 widget-test.html 改动）。方法：8 路并行深读（问答链路可读性 / 入库链路+platform-knowledge 归属 / 管理配置面 / 能力抽象 / 平台设施+Maven / 结构量化+守卫实跑 / **红队结构挑战** / 前端对齐），四条承重结论（G3 消费者图、方法长度 top、IM+KV 凭据明文、前端 error 帧不 abort）由主会话亲手复核。
> 与前三轮（`architecture-assessment-2026-10-19.md` / `-2026-10-20.md`）的关系：10-20 基线后又落 71 提交（G1/G2/G3/G4/G10、H 系列大半、K/L/M 全部、X 系列 Maven 迁移）。本轮对 10-20 修正 4 处，首次设红队专责挑战「骨架维持」结论，并给出 v0.7.x 的 N 系列行动批次。

## 1. 总裁决（第四轮）

**7 模块/20 域骨架第四次被业务反向验证为合理——且本轮是首次由专责红队用数据挑战后仍未翻案（§5）。真正欠账从三类变四类：①执行型欠账（G5~G9、H 未完项原样）；②两处安全级凭据明文（新升级为本轮 P0）；③治理回潮——71 个提交的整改冲刺自己造出的卫生债（方法 >100 行 86→109、trim 私拷贝 39→72、构造器极值 19→20 参）；④文档信任侵蚀（platform-shapes baseline 3 处文案与代码事实不符、守卫计数漂移三连）。**

| 维度 | 裁决 | 关键证据（HEAD 实测） |
|---|---|---|
| 模块划分（7 模块） | **维持**（红队 R1/R4/R5 挑战后不改） | 域间 6 边/27 类 clearable=0；拆 session 需切 3 边暴露 36 类成端口；三 runtime 无重复基建（SSRF/JSON/SSE/重试均单实现）；contract 单模块消费面仅 2.7% |
| 业务域划分（20 域） | **维持 + 2 个拍板项连挂三轮** | approval 7 文件全仓 0 import 消费者、0 控制器 0 持久层（红队实测）；initialization 的 ollama/* 8 端点是模型管理职责错域 |
| 可读性 | **链路优、冲刺期在回潮** | 入库链「能」（核心 1,081 行）、问答链「勉强」（骨干 17 文件 5.9k 行）；>100 行方法 86→**109**；Ops 后缀 46→**76**；session 三包词汇未收敛 |
| 可维护性 | **中上，守卫 45/45 绿是护城河** | 全部守卫实跑绿；但两套 schema（TestSchema 948 行 vs V1 3,574 行）无对账守卫；metrics 仍为零 |
| 缺陷/安全面 | **2 项 P0** | IM 渠道凭据明文 jsonb 直通（ImChannelEntity:55）+ tenants KV 三键明文（TenantConfigOps 0 加密引用）；上手路径断链（新用户必失败）四轮未修 |
| 前端 | **中，冻结区** | top5 巨石 27,110 行（20.95%）逐字节未动；前后端同名同义 12/22；四个易混点全踩坑 |

## 2. 四轮收敛轨迹与本轮对 10-20 的修正（4 处，均已亲手复核）

1. **方法长度口径失真**：10-20 记「>100 行方法 86、top=SqlSelectDeepChecker.deepCheck 275」→ 实测 **109 个、top=WikiIngestRunSupport.runIngestPhases 372 / reduceSlugUpdatesBody 309 / deepCheck 275**（M1 棘轮基线自己登记的就是 372/309）。前轮 top 漏扫 wiki 摄取族；且 86→109 是**恶化**不是持平——H4b-2 队列迁移等结构批在造长方法。
2. **还乡判断第三次修正（终版）**：10-18「9 件全还乡」→ 10-20「2,885 行还乡」→ G3 执行时「8 件全留 platform」→ 本轮逐件核验：**G3 的两个前提对 8 件并不全部成立**——「跨族共享」对 GrepChunksScoring（仅 agenttools 2 文件）与 ImageInfoMatchUtil（仅 session 3 文件主码）不成立；「依赖 Jackson/ImageIO」对 GrepChunksScoring、ImageInfoEnricher 无直接依赖。根因：package-info 判据是「任一成立即留」的 **OR** 语义，G3 提交信息当成了 AND。终版处置见 §6 N6。
3. **G1「已修」须限定为服务端半边**：QaSseOrchestrator:230-295 与 SessionStreamController:276-315 的 deadline+ERROR-done 均在（亲手复核）；但前端 ① 收 error 帧不 abort（useChatStreamHandler.ts:874-898 只置标志位）；② **NORMAL 路径无 error 分支**——error 帧 content 直接进 `fullContent` 当正文渲染、不设 message.error（:1196-1246 亲手复核，只有 agent 路径有 case 'error'）；③ SSE 无前端超时兜底。闭环成立靠服务端断流，回归无防线。
4. **「整改改善卫生」的隐含假设不成立**：71 提交里 Ops +30、Service +11、Port +40（端口化真产出）、trim 私拷贝 39→**72**、构造器极值 19→**20**（KnowledgeProcessWorker）、批次叙事注释新攒 15 处——冲刺在改善结构的同时造新卫生债，需要棘轮钉住而不是等下一轮体检。

## 3. 业务能力反向映射（更新）

- 22 个产品能力中 **19 个维持唯一同名落点**；变化两项：evaluation 已清干净（前端 RUN_EVALUATIONS 0 命中、后端仅剩 APIKeyCapability 枚举常量属正常单源）；知识加工能力从「分散 4 处」收敛为**实质 1.5 处**（knowledge 域 264 文件 + contract.knowledge 契约面 60 + platform/knowledge 工具函数 9），L19 当年「拆模块的前置理由」进一步消解。
- **approval 坐实「名下无物」**：7 文件纯 contract.tool SPI 策略实现，全仓 0 处 import、无 HTTP 无持久层，数据在 mcp 的 `mcp_tool_approvals`——与已消亡的 tenant 同款模式，**连挂三轮的拍板项，建议本轮拍掉**（§8）。
- **连通性测试双头仍在且初始化域职责错位**：InitializationController 17 端点（:57-153，含 ollama/* 8 端点、remote/check、embedding/test）vs ModelDebugController:85；前端 ModelEditorDialog 调 initialization、ModelDebugDrawer 调 model。「模型管理入口住 initialization 域」是当前最明显的归属错配。
- 配置类能力多头全部存活：存储三头、向量库双头（读模型已统一 VectorStoreConfigService:95-104）、websearch 双头（KV 侧已无 UI）、解析引擎三头（env / tenants KV / system 域 HTTP）。M6c-2 拆了协作者但**没有并真相源**。

## 4. 分维度深读

### 4.1 问答链路 —— 「勉强」：终态与状态分组是净收益，词汇与并发是欠账

- 骨干必读 17 文件 ≈5,900 行（NORMAL），AGENT 再加 ≈12 文件 5,000+ 行。迷路点实测：入口类名只反映三入口之一（KnowledgeQaController:30-33 自述）；qa 链核心类型 QaRequestContext/QaMode 住在 service/QaSupport:43 而非 qa/ 包；「写一帧 SSE」有 7 个类（SseSink/ServletSseSink/SseFrameSink/SseFrameWriter/SseContract/StreamEventEmitter/StreamResponseBuilder）；插件参数 `PipelineState chatManage` 参数名残留 68 处。
- H2 状态分组**净收益为正**：ChatManage 309 行/50 字段，六组状态「每组一个写者」可在字段注释核对（PluginRerank:67/243、PluginQueryUnderstand:71 实测）；TurnInputView:12-13 注释已过时（说签名未改，实际早改完）。
- **QaRequestContext 33 个 public 可变字段跨 3 类线程裸读写**（QaTurnExecutor:122-131 虚拟线程、:239-253 异步闭包、:338-349 follow-up 再起线程；除 assistantMessage synchronized 外无同步）——H2b-3 待做，这是问答链最大的并发正确性敞口。
- 事件三套词汇并存：ResponseType 22 值枚举 / EventType 38 个 String 常量 / PipelineEventType 14 值；同义不同值坐实（thought↔thinking、final_answer↔answer）。
- **工具名清单 ≥8 份后端字面量 + 3 份前端**（wiki 十件重复 ≥6 个后端文件）：ToolDefinitions/ToolCapabilities/ToolPolicy/AgentToolNames/AgentEngineAssembler 两处/AgentToolBackends/QUICK_ANSWER_TIMELINE_TOOLS/ToolResultPersist/im ToolDisplay——新加工具要改 5-6 个文件，守卫只盖 2 处同步，第四 switch（AgentEngineAssembler.buildTools:420，default 仅 warn :453）漏 case 会**静默丢工具**。

### 4.2 入库链路 —— 「能」：教科书级编排，欠账是装配与文档

- Worker 223 行讲清全链（CAS 状态机/租约/housekeeping javadoc 均在）；但构造器 **20 参**（16 依赖+4 ObjectProvider）且在构造器内手工 new 4 个阶段——阶段依赖关系不可见。
- IngestStage.MULTIMODAL 无人推进：枚举声称「顺序即依赖」列于 EMBEDDING 前，实际是 OCR 在 DOC_READER 内部的子步（KnowledgeTextExtractor:44→KnowledgeImageOcr:232）——按枚举读链路会错序，1 行 javadoc 可修。
- B2 半收口：drainSubtask 拷贝已消（G4 统一 SubtaskDrain 27 行），但 knowledge→wiki import 仍有 7 处/4 文件。knowledge/task/package-info 仍失真（声称 InProcess 唯一实现，实际 Redis 四件 @Primary）。

### 4.3 管理后台与配置面 —— 「良」+ 两处安全级明文（本轮 P0）

- **凭据加密一致性实测**：model/vectorstore/websearch/mcp/datasource/storage(表) 七路全走 AES-256-GCM（enc:v1:）；**例外两处**——IM 渠道 credentials 明文 jsonb 直通（`ImChannelEntity.java:55` JsonbRawStringTypeHandler，Controller:209 直写，亲手复核），且绑定时回显 botToken；**tenants KV 三键明文**（storage-engine-config 云凭据 / web-search-config apiKey / parser-engine 密钥，`TenantConfigOps.java` 0 处加密引用，亲手复核，仅响应打码）。同一张库里两种纪律。
- 路由安全：RBAC 表 593 行（boot）+ APIKey 表 705 行（auth 域），check-route-policy 384 路由/372 规则 0 幽灵全命中；新加受保护端点必碰 3 后端文件+1 再生成文档。L3 否决双表合并的理由（主语/语法/语义三不同）在两表头注释可见且成立。
- 控制器零实体泄漏（51 文件 0 import）；BizException 50% 在控制器层；19 个 List 端点无分页（H5a-2 待做，真需要的是 listAllTenants/platformKeys/loadMessages/favorites）。
- **上手路径断链四轮未修**：README:132-158 与 getting-started:17-70 均无「配模型 key」步骤，builtin_models.yaml 默认 no-op，docker-compose 无 LLM——新用户照文档走必在第一次提问失败。10 分钟工作量。

### 4.4 能力抽象 —— 端口恰粒度，contract 未膨胀，算法类与工具 SPI 是两个口子

- **40 个 \*Port 全有实现、域内 0、最大 14 方法**，port-map 机器生成且 --check 绿；「1 实现 1 消费者同模块」=0（port-budget 守卫强制）。H6-1 的 7 个窄知识端口**直接消费者为 0**——结构就位、窄面未启用。
- contract 361 文件构成：101 interface（40 Port+61 SPI）/69 record/57 final class；**算法类 37 个，其中 11 个仅单一域消费**（MemoryKeys 322 行仅 memory、ToolCapabilities 仅 im、WikiConstants 仅 wiki 等）≈−1,500 行可回域——「第二个 common」的苗头在算法类，但纯度守卫（0 框架依赖）拦住了最坏情况；增长曲线 10-17 后仅 +12 文件，无持续膨胀斜率。
- 三 runtime 无重复基建（红队 R4 逐项实测）；装配 22 点三处一致，port-map.md 是「Port→实现→消费者」的机器生成地图且抽查一致；QaWiring 留域内理由成立（17 个域内插件类型）。
- 工具 SPI「不足」：四段手工同步（工具类→常量→AgentToolBackends 三 switch→AgentEngineAssembler 第四 switch→前端 12 个 ts 硬编码），守卫只盖前两段。

### 4.5 平台基础设施 —— 纯度「良」，D 批三项全欠，schema 双源是最大敞口

- platform 19 包 17 包零域词；10-20 后 8 提交全是技术收敛。扣分项不变：RbacInterceptor(426 行授权语义)+规则表(boot)+APIKey(auth) 授权链横跨 5 处；prompt 包 11 份业务 YAML；knowledge 包登记≠消除。
- **D 批三项仍全欠**：metrics 0 命中、测试仍 H2+flyway 关闭、@RouteSecurity 0 命中。**新发现最高性价比项：TestSchema(948 行/55 表) vs V1__baseline.sql(3,574 行) 双真相无任何对账守卫**——CI 只验灌库，漂移只能靠测试失败倒逼。
- Maven 迁移落地质量高（X 系列四批全绿、5042 用例等值），登记新债 6 笔（FaqContractTest 上下文共享、无增量编译、jacoco 无聚合、test-jar 不传递等）+ 文本漂移三连（ci.yml「32 条」实为 45、overview「352 份」实为 356、TenantFilterGuard:23 悬空引用四轮未修）。

### 4.6 前端 —— 「中」：质量面优、对齐四坑、巨石冻结

- 信封单点（request.ts:162-179 唯一解包+rawFetch 守卫）、SSE 单点+1 特例、vue-tsc 在 CI——维持优。130 测试文件 745 用例（node:test 非 vitest）。
- **四个同名陷阱全踩**：views/platform（布局壳）≠后端 platform 模块；views/system 无自有路由且 SkillManagement 调的是 agentmanagement 域；api/system 1,252 行跨 system+storage+auth 三域 56 interface；api/tenant 四文件实指已合并的 auth 域且 **TenantInfo 仍双定义**（auth:162 与 tenant:7，消费侧分裂）。
- H5a-1 的 PageResult 前端 0 处镜像（10 个 api 文件各自声明分页形状）；EMB/G2 证明小步同步通道畅通。
- top5 巨石 27,110 行（WikiBrowser 6,593 且 0 composable / AgentEditorModal 6,320 / FAQEntryManager 5,613 / KnowledgeBase 4,487 / AgentStreamDisplay 4,097）四天 0 提交，G9 未启动。

## 5. 红队结构挑战（本轮方法论增量）

专责红队以数据为「应该改骨架」找最强证据，五个命题全部裁决「不改」：

| 命题 | 反方最强论据 | 裁决依据 |
|---|---|---|
| R1 domains 仓中仓 | 211k 行 70% 无编译期隔离 | 域间仅 6 边冻结零新增；拆 session 要暴露 36 类成端口；Maven 增量收益被 X2 实测削弱（reactor 循环）。**缺口在域间边只有 python 棘轮、无 ArchUnit 规则——升格约 30 行，建议做** |
| R2 伪域 | approval/favorite 等八小域 133 文件 | approval 确为 0 消费者（并入 mcp 零代价），但收益仅包数 20→19 属审美级；每域 package-info 四元组齐全，按名索骥靠元数据不靠包数 |
| R3 agent 五处 | 新人要被指向 5 处 | 依赖已单向化（agenttools/agentmanagement→agent-engine import=0），三处 package-info 职责互斥互写指针；现状是 §2.7 既定路径的目标态 |
| R4 三 runtime 重复 | — | 命题不成立：SSRF/JSON/SSE 解析/任务重试均单实现；retrieval 11 个 HTTP 文件是每向量库各说各的 REST 方言 |
| R5 contract 第二个 common | 37 个算法类 | 单模块消费面仅 2.7%（4 包 9 文件）；增长曲线无膨胀斜率；病根「无人能回答谁消费我」需守卫不需搬家 |

**红队结论：维持是读数支撑，非路径依赖。** 两条值得做的小改动：域间守卫升格 ArchUnit；favorite/approval 合并留新人反馈再定（approval 建议本轮拍板）。

## 6. 问题清单（分级汇总）

**P0（2）**
1. 凭据加密两处明文：IM credentials（TypeHandler 模式现成，仿 McpSecretTypeHandler）+ tenants KV 三键（需拍板：加密迁移 or 降只读兼容）（§4.3）——✅ 2026-10-21（N2/N3：jsonb 内嵌密文 + 逐字段加密；**写时加密、读兼容历史明文**，存量行在下次保存时转密文）
2. 上手路径断链：两文档补「配模型 key」步骤 + builtin_models.yaml 样例转正（10 分钟级，四轮未修）（§4.3）——✅ 2026-10-21（N1）

**P1（结构，10）**
3. SSE 前端收尾：NORMAL 路径 error 分支 + message.error + stopStream 主动收束 + 超时兜底 + 红态测试（§2.3）
4. 工具清单单源化：≥8 份字面量收敛到 ToolDefinitions，前端表由生成/契约锁定；**T4 守卫**（第四 switch case 集 ≡ AgentToolBackends case 集，堵静默丢工具）（§4.1）
5. QaRequestContext 收口（H2b-3）：33 可变字段→builder 构造+只读快照传异步段（§4.1）
6. 还乡 2.0 终版：GrepChunksScoring 回 agenttools（3 文件小改）；ImageInfoMatchUtil 留 platform 修文案（真理由=被 platform 姊妹件 ImageInfoEnricher 8 处调用，搬走断链）；其余 6 件维持；contract 11 个单域消费类随消费者回域（MemoryKeys 连 MemoryConfig 一起）；新增 gen-contract-consumers 消费者答案守卫（§2.2/§4.4）
7. 配置真相源收口（原 G7 未启动）：存储三头→storage_backends 表（KV 降只读兼容一版）；websearch KV 兼容路径下线计时；parser 三头归位（§3/§4.3）
8. 域归属尾巴：initialization 的 ollama/* 归 model；连通性双头合一或明示分工（表单预检 vs 调试）；Skill 双控制器合一（L3 只改名）；approval 拍板执行（§3）
9. 横切升级（D 批排序更新）：**schema 对账守卫**（TestSchema vs V1 表名/列名对账，性价比最高）——✅ 2026-10-21（P1.9a，第 46 条守卫；顺带补出 `user_kb_pins.tenant_id` 的实体缺口，见 HANDOFF）→ metrics（半天）→ PG+flyway 测试 profile → @RouteSecurity（L3 已否决合并，注解化收益需重估）（§4.5）
10. H 系列待做：H5a-2 分页四端点、H7 platform 判据换血（本轮实证：方法签名级域词与 prompt/RbacInterceptor 不可见）、H8 适配层逻辑化、H9 管理面模板化；H6-1 的 7 个零消费者窄端口要么启用要么撤（§4.4/§4.5）
11. 前端对齐四坑：api/system 拆 3-4 文件、api/tenant 并入 auth+TenantInfo 单定义、PageResult 前端镜像、views/system 与 agentmanagement 的错位（§4.6）

**P2（卫生回潮治理，按收益排序）**
12. 三大长方法收敛：372/309/275（wiki 摄取两件+deepCheck）；>100 行方法 109 只减棘轮
13. trim 私拷贝 39→72：单独立棘轮（本轮唯一接近翻倍的卫生项）
14. KnowledgeProcessWorker 20 参参数对象化；IM 域 ImTurnOrchestrator 15 参同类
15. 插件词汇机械收尾：`PipelineState chatManage` 68 处参数名改名 + TurnInputView 过时注释
16. 文档信任三连+两处失真：ci.yml 32→45、overview 352→356、TenantFilterGuard 悬空引用；knowledge/task/package-info 重写；platform-shapes baseline 3 处文案修正 + package-info OR 判据澄清；MULTIMODAL 错序 1 行
17. Maven 债：javadoc 配置上移根 pom、jacoco 聚合、FaqContractTest 上下文隔离 ——✅ 2026-10-21（N4 已清零，见 HANDOFF §12）
18. 批次代号新残留 7 处+叙事 15 处清洗；G9 前端巨石独立立项（首刀 WikiBrowser，仓内范式现成）

## 7. 目标架构方案

### 7.1 终态结构（不变，第四轮确认）

```
boot（组合根，只出不进；@Configuration 8 组合根档）
 ├─ domains（20 域，标准六件套；域间只经 contract 端口，6 条冻结边只减不增，
 │    目标升格为 ArchUnit 规则获得编译期同级的测试期强制）
 ├─ model-runtime / retrieval-engine / agent-engine（三 runtime 平级，零漂移维持）
 ├─ platform（纯技术适配；knowledge 包终态 7 件+修正文案；prompt 归属拍板）
 └─ contract（纯形状 + 40 端口 + 明示契约共享算法；新增「消费者答案」机械守卫防 common 化）
```

不变式延续：不拆微服务、不拆 :knowledge/:session 模块、不上 JPA/TenantLine、金片与 connector 线格式冻结、RBAC/APIKey 双表不合并（L3 裁决维持）。

### 7.2 行动批次（N 系列；每批独立全绿，红态探针随新守卫）

| 批 | 内容 | 估时 | 验收 |
|---|---|---|---|
| **N1 凭据对齐** | IM credentials TypeHandler（仿 McpSecretTypeHandler，enc:v1: 宽容解密）；KV 三键按拍板执行（加密迁移或降只读） | 1-2 人日 | 全域凭据单一定律成立：库内密文或拍板豁免 |
| **N2 上手路径** | README/getting-started 补配模型 key；builtin_models.yaml 示例转正 | 0.5 人日 | 照文档走通首次问答 |
| **N3 文档信任** | baseline 3 处失真+package-info OR 澄清+task/package-info 重写+计数三连+悬空引用+MULTIMODAL 注释；platform-shapes 增「消费族计数」机器校验 | 0.5-1 人日 | 守卫绿且文案与代码一致 |
| **N4 SSE 前端收尾** | NORMAL error 分支+stopStream+超时兜底+用例 | 0.5-1 人日 | error 帧不再当正文；断流有前端防线 |
| **N5 工具单源** | ≥8 份清单收敛；T4 第四 switch 守卫；前端表生成/锁定 | 1-2 人日 | 新加工具改 2 处（类+常量）；漏注册必红 |
| **N6 还乡终版+消费者守卫** | GrepChunksScoring 回 agenttools；11 单域类回域；gen-contract-consumers.py | 1-2 人日 | contract −1,500 行；答不出消费者的公共类即红 |
| **N7 QaRequestContext 收口** | H2b-3：builder+只读快照 | 1-2 人日 | 异步段不可变；编译器兜底 |
| **N8 配置收口** | 存储三头→storage_backends；websearch KV 下线计时；parser 归位 | 2-3 人日 | 每类配置唯一真相源 |
| **N9 域归属尾巴** | ollama/*→model；连通性分工明示；Skill 合一；approval 拍板执行 | 1-2 人日 | 每能力唯一 HTTP 面 |
| **N10 横切** | schema 对账守卫→metrics→PG profile→@RouteSecurity 重估 | 5-10 人日 | 双 schema 对账绿；/actuator/metrics 可用 |
| **N11 卫生棘轮** | trim 72 棘轮；三大长方法；20 参构造器；68 处参数名；代号清洗 | 持续 | 棘轮只减 |
| **N12 前端** | api/system 拆+tenant 并入+TenantInfo 单定义+PageResult 镜像；G9 巨石独立立项 | 2-3 人日+独立 | vue-tsc+130 文件测试绿 |

顺序建议：**N1 → N2 → N3（先把信任的地板修平）→ N4 → N5 → N7 → N6 → N9 → N8 → N10 → N11/N12 持续**。

### 7.3 拍板清单（连挂轮次标注）

1. **approval 域**（挂三轮）：并入 mcp（红队：零边代价）或保留并明示为「mcp 的策略实现域」。
2. **tenants KV 三键**（本轮新）：加密迁移（与 N1 同批）或降级只读兼容一版后下线。
3. **initialization 职责**（挂两轮）：明示为「上手向导域」并将 ollama/* 归 model，或整域并入 model。
4. **NORMAL 失败轮异步副作用**（挂一轮，10-20 §7.3-5）：失败轮是否照常排索引/建议/记忆。
5. **G9 前端启动时机**（挂两轮）：top5 巨石 27,110 行四天零提交；建议随 N12 后独立立项。
6. **platform/prompt 11 份 YAML**（挂两轮）：归 domains 资源目录或文档化为「提示词装载设施」。

## 8. 量化附录（HEAD 实测，对比 10-20）

| 指标 | 10-20 | 10-21 | 信号 |
|---|---|---|---|
| 模块主码 | contract 351/20,523 · domains 1,564/210,851 · platform 189/17,629 | 361/20,272 · 1,589/211,133 · 198/18,224 | 结构面稳 |
| 域间边 | 6 条/33 文件 | 6 条/**27 类**，clearable 0 | 收紧 |
| >100 行方法 | 86 | **109**（top 372/309/275） | **回潮** |
| >150 行方法（M1 棘轮） | 基线 32 | 31 | 只减 |
| ≥8 参构造器 | 49（极值 19） | 51（极值 **20**） | 回潮 |
| trim 私拷贝 | 39 | **72** | **回潮最陡** |
| 后缀词汇 | Service 91/Ops 46/Port 6 | Service 102/Ops **76**/Port **46** | 熵升（Port 是真产出） |
| 测试 | 483 文件/4,632 @Test | 486/4,677 | 增 |
| 守卫 | 40 | check-\*.py 40 + run-guards 45 run，**45/45 绿**（本轮实跑） | 护城河 |
| 域间参考类/SCC/环 | 33 / 5 / 0 | 27 / 5 / 0 | 稳 |
| package-info | — | 356/356=100% | 稳 |
| 30 天热点 | knowledge>session>wiki | knowledge 1,991>session 1,865>wiki 876 | 热区未转移 |
| 前端 | 129,334 行，top5=27,110 | 129,350 行，top5=27,110 逐字节未动 | 冻结 |
