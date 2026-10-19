# 基于业务反向评估的架构方案（2026-10-20）

> 评估基线：`v0.6.x` @ `c29414b83`（工作树干净）。方法：9 路并行深读（业务能力反向映射 / 入库链路 / 问答链路 / 能力抽象 / 管理配置面 / 平台设施 / 结构量化 / 域内分层一致性 / 前端与文档对齐），全部结论基于 HEAD 静态取证（file:line 或实测数字），未跑构建与测试；三处承重结论（SSE 半修复、ToolResultPersist 消费者、evaluation 前端残留）由主会话二次亲手复核。
> 与 2026-10-19 评估（`architecture-assessment-2026-10-19.md`，基线 v0.5.0）的关系：其后落地 56 提交（P1-1/P1-2 缺陷修复、L36~L45 门面化与端口化、L46~L50 域归属整备、OC-1~5 OCR 富化）。本文对其 A~F 方案逐批盘点执行情况，修正 4 处误判，并给出后续 G 系列批次。

## 1. 总裁决

**7 模块骨架与 20 域划分已被业务反向验证为合理，本轮无需结构性手术；真正欠的是三类活：①已规划未执行的结构批（还乡 2.0 约 2885 行原封未动），②整改收官的尾巴（本轮整改自己造出的残留），③两处缺陷级收尾（SSE 死轮询只修了一半）。**

| 维度 | 裁决 | 关键证据（HEAD 实测） |
|---|---|---|
| 模块划分（7 模块） | **维持** | 域间边 8→**6 边/33 文件**，全部登记、clearable=0；三 runtime 自 v0.5.0 零改动零漂移；boot→domains 10 个装配类为合法组合根边（10-19「import=0」系口径错误） |
| 业务域划分（20 域） | **维持 + 2 个拍板项** | 22 个产品能力中 19 个有唯一同名主落点（对齐度约 90%）；tenant 并入/evaluation 下线收官干净；余错配是收尾级：连通性双头、存储探测 HTTP 面仍在 system、approval 无独立落点 |
| 可读性 | **高于主流，链路优、域内 vocabulary 开始发散** | >800 行类 0；问答/入库核心类无 >100 行方法；package-info 239/239 全覆盖；但 `runtime` 包已现四义、repository 门面 98.9% 纯转发、session 三套服务词汇并存 |
| 可维护性 | **中上，守卫文化是护城河** | 40 守卫 + ArchUnit 28 + 5 生成器全绿；短板：两张手工路由表（593+705 行）、V1/TestSchema 双源、metrics 为零、配置真相源多头 |
| 缺陷面 | **1 项 P0 未修完** | P1-2 只修了 assistant 落库位；SSE 主循环仍不认 ERROR-done 终态且无 deadline（亲手复核） |

## 2. 业务能力反向映射（本轮核心新视角）

从产品功能面（前端 12 条实质路由 + Settings 14 个 section）反向枚举 22 个业务能力，逐一核对代码落点：

- **19/22 能力有唯一、同名的主落点域**（知识库→knowledge、问答→session、Wiki→wiki、模型→model、IM→im、数据源→datasource……），能力名与域名几乎一一对应。
- **跨域协作纪律极好且在改善**：IM/embedchannel/skills/审计/docreader 五条最像会乱接的线全部走 contract 端口；三 runtime 与 domains 无编译期依赖，全部经 32 个 `*Port` 反转、boot 装配。
- **能力落点分散的例外（=整改缺口）**：
  1. 知识加工能力的代码散在 4 处：knowledge 域 + platform/knowledge（1853 行）+ contract 算法居民 + retrieval-engine——还乡 2.0 未做所致；
  2. 模型连通性测试双头：initialization 5 个端点（`InitializationController.java:104-119`）与 model 的 `/models/{id}/debug`（`ModelDebugController.java:84`）覆盖同一能力，前端 `ModelEditorDialog` 与 `ModelDebugDrawer` 各调一头；
  3. 评测能力已删但外围仍在：`APIKeyCapability.java:57` 保留 `RUN_EVALUATIONS` 常量（可辩护——已持久化 Key 可能携带），但前端 `apiKeyCapabilities.ts:77` 仍把它列为可勾选项，勾出一个后端零路由使用的权限；
  4. 审批能力被拆两半：approval 域 7 文件是纯策略库（无 HTTP/无持久层），落点全在 mcp——与已消亡的 tenant 同款「名下无物」模式，需拍板去留。
- **根目录三个非构建目录均非孤儿**：docreader/（gRPC proto 源根，domains 构建输入）、otlp-proto/（OTLP proto，platform 构建输入）、mcp-server/（Python 独立 MCP 客户端，有意边车，但零 CI 联动，腐烂只能靠人工发现）。

## 3. 对 10-19 方案（A~F 批）的执行盘点

| 10-19 批次 | 执行情况 | 证据 |
|---|---|---|
| A 缺陷修复 | **半成**：register 投影 ✅（`AuthController.java:192-232` 注释自证）；NORMAL 死轮询仅修落库位半（见 §5.2）；title 忙转未修（`QaSseOrchestrator.java:291-314` 与 v0.5.0 逐字节相同） | 7a52aff12 / d4e1064a4 |
| B 还乡 2.0 | **零进展**：platform/knowledge 9 件/1853 行一件未动；且 **OC-1 又新增一条反向依赖**（`KnowledgeImageOcr.java:10` 用 ChunkSearchUtil）——冻结期还在往错的方向续费 | `wc -l` 实测 |
| B2 finalizer 收尾 | **未做**：knowledge→wiki 6 行 import 原样（KnowledgeProcessWorker.java:26-27、KnowledgeFinalizingStage.java:18-20、ChunkExtractService.java:29、QuestionGenerationService.java:16）；2 份 drainSubtask 逐字拷贝仍在 | 亲手 grep |
| C 域归属整备 | **大头已落地**：tenant→auth ✅、evaluation 下线 ✅、initialization DI 化 ✅、storage 探测实现归位 ✅、memory 走端口 ✅（域间 8→6 边）；**尾巴未收**：连通性双头、存储探测 HTTP 面仍在 system、Ops 46 件未动、Skill 双控制器未合一 | b4e9878ae / 4dfdf1d05 等 |
| D 横切升级 | **零进展**：无 PG 测试 profile、无 @RouteSecurity、metrics 仍为零（micrometer/actuator 全仓 0 命中） | 全仓 grep |
| E 可读性尾巴 | **微动**：>100 行方法 87→86、≥8 参构造器 51→49；但 KnowledgeProcessWorker 构造器 18→**19 参**（OC-1 所致，创新极值）；三大长方法 309/225/192 逐字节未动 | method-length-report |
| F 前端 | **零进展**：top5 巨石（WikiBrowser 6593 等，合计 27,110 行 ≈ 全部 .vue 的 21%）自基线 0 提交；api/system 1252 行未拆；api/tenant 未随 L50 并入 auth（`TenantInfo` 前端双定义） | git log 空 |

## 4. 对 10-19 结论本身的修正（4 处，均已复核）

1. **WikiLanguageSupport「还乡」判断应反转**：实测 189 行被 agent-engine + boot + 5 域共 16 文件消费（线程本地 locale 是全进程语义）——本就跨模块，应**留 contract 并文档化**。10-19「单模块消费」系误判。
2. **ModelFactsPort「砍」的建议撤回**：唯一消费者 retrieval-engine/HybridSearchService 跨模块，砍掉反而制造模块反向依赖。
3. **ToolResultPersist 改判为还乡对象**：10-19 判「真跨层留下」时消费者更广；现收缩为 session 单域 2 文件（AgentStreamBridge、AgentHistoryAssembler，亲手复核），427 行应还乡 session。
4. **口径勘误**：agent-engine 主码实为 67 文件/10,815 行（基线文档 74/12,321 系统计口径问题，该模块自 v0.5.0 零提交）；「14 阶段/24 插件」应为 13 个 PipelineEventType（单轮最多拼 11 段）/17 个 Plugin 类；「boot→domains import=0」实为 10 个装配类 50 条 import（`check-module-graph.py:25` 明文合法）；端口数 30→**32**（L46/L47 新增 2 个各修掉一处既有违规），「30→28」终态口径需刷新。

## 5. 业务面深读结论

### 5.1 入库链路 —— 维持，优等生，欠账全是「批已规划未执行」

- 全图：多触发源 → `KnowledgeIngest` 端口 → `KnowledgeProcessWorker`（208 行纯编排，19 参构造器）→ 4 stage（90/113/194/386 行）→ wiki/图谱/问题三路 fan-out；OCR 富化（OC-1~5）嵌在 TextExtractor 尾部，链路顺序未被打乱。
- 质量面：CAS 状态机 + 租约续期 + housekeeping 双道闸 sweep 全在；worker 类头 65 行 javadoc 讲清队列语义/事务边界/三条不变量，是教科书级编排。
- 欠账：B2（6 行 import + 2 份拷贝）；三大长方法 309/225/192 零进展；`knowledge/task/package-info.java` 事实性过时（声称 InProcess 是「当前唯一实现」，实际 Redis 四件是 @Primary 首选；21 个类只写了 7 个）——唯一会误导读者的 package-info。
- 治理回潮信号：注释里「M2 解环」×9、「走查批」×7 残留（批次代号清零后又攒出新的一族）。

### 5.2 问答/Agent 链路 —— 一处 P0 尾巴，其余优

- **P0 尾巴（亲手复核坐实）**：P1-2 只修了「assistant 消息永久 completed=false」半边；SSE 主循环（`QaSseOrchestrator.java:229-319`）终态仍只有 STOP/COMPLETE/IOException，`ERROR && isDone()` 帧被当普通事件写出后继续 `while(true)`，无 deadline ⇒ HTTP 线程 100ms 无限轮询直到客户端断开。`SessionStreamController` 断线续传循环（:277-322）同款形状。**前端也不自救**（`useChatStreamHandler.ts:874-886` 收到 error 帧不 abort 连接）——双向僵持。修法便宜：主循环加一个 ERROR-done 分支（对称照抄 stop watcher :213-215 已有写法）+ 前端 abort，半天含红态测试。
- 健康面：executeQA 七步/agentQA 26 行保持；L45 观测收口是净收益（SessionTrace/SessionModels 门面，SpanHandle 不漏框架类型）；agent-engine 自基线零改动，终态门/ContextBudget/compaction 三项最佳实践复验全部成立。
- 遗留：QaRequestContext 34 个 public 可变字段零进展（跨 7 类线程共享可变状态，字段上的时序注释本身就在补抽象缺失的洞）；EventType⇄ResponseType 双词汇仍分散映射（thought/thinking 同义不同值）；planTools 4 份工具清单（wiki 十件出现 3 次）。

### 5.3 管理后台与配置面 —— 归属整备大头落地，配置真相源是新的主要发现

- 控制器读模型：model/auth/mcp/system 四域抽查**零实体返回**，全 DTO/投影/record；register 返回 UserInfo 投影已钉住。
- RBAC/APIKey 模型质量维持（4+1 等级、默认拒绝闭环、Key 管理端点 default-deny 防自续命）；但两张手工镜像表涨到 593+705 行，顺序敏感，@RouteSecurity 注解化仍未启动。
- **配置真相源地图（本轮新绘制）**：application.yml 只承载基础设施与开关，业务凭据全在 DB 且 model/vectorstore/websearch 均走 AES-256-GCM 加密 + 凭据子资源端点——底子是对的。多头点：
  - **存储后端三头并存**：env 快照（建租户时落库 legacy）/ storage_backends 表 / tenants KV `storage-engine-config`，运行时按 resolver 命中顺序决定谁生效（`TenantFileServiceResolver.java:40-79`）——同一份对象存储凭据可能存在三处；
  - 向量库双头：DB 行 vs `RETRIEVE_DRIVER` env 派生虚拟库（`EnvVectorStores.java:30-136`）；
  - websearch 双头：provider 表 vs tenants KV 兼容路径（运行时查表回落 KV，`WebSearchService.java:88-115`）；
  - **IM 凭据明文落库**（`ImChannelController.java:209-211`），与 model/vectorstore/websearch 的加密策略不一致——配置面唯一的安全级不一致；
  - 解析引擎两套来源：docreader gRPC 地址走 env（无 UI），解析引擎端点走 tenants KV。
- **上手流程断链**：README 四步与 getting-started 均无「配置模型 API key」步骤；docker-compose 不含任何 LLM 服务；从零到跑通一次问答，必须手工去 Settings 建模型并写凭据，或提供 `builtin_models.yaml`（`.env.example:87-89` 仅注释示例）——两份文档都没把这一步写进路径。

### 5.4 能力抽象 —— 骨架恰粒度维持，算法居民还乡清单更新为 2885 行

- 32 端口逐一裁决：**31 个恰粒度**（含 L46/L47 两个新增，各修掉一处既有违规）；仅 EmbeddingModelPort 属可议（1 消费者），但「砍」与「域间只经 contract 端口」规则打架，需先统一口径再动。
- 三 runtime 零漂移全维持：26 家 LLM 仅 1074 行元数据、真实客户端收敛在 RemoteApiChat；EngineRegistry 四道闸；Connector/StreamingConnector 7 连接器形状平行且线格式有文件级冻结。
- **还乡清单（更新版）**：platform/knowledge 9 件 1853 行（消费者全在 domains）+ MemoryKeys 322（14 文件全在 memory 域）+ ToolResultPersist 427（消费者收缩为 session 单域，改判）+ Grep 族 283 ≈ **2885 行**；SearchAuth/SearchTextUtil/WikiLanguageSupport 留 contract 并文档化为「契约共享算法」。
- **B 批验收标准本身失效**：`check-platform-shapes.py` 只查命名红旗，域词汇（Chunk/Faq/Doc/Knowledge）不在检测面，测不出这 1853 行——需换域词汇检测守卫。
- model↔retrieval 包环仍在（各 1 行 import），被 SCC 基线棘轮固化而非消除。

### 5.5 平台基础设施 —— 冻结期，D 批三项全欠

- platform 主码自 v0.5.0 仅 2 文件 4 行改动：全部优点原样保留（StreamManager/EventBus/TenantContext/SsrfGuard 复验仍优），全部遗留债一条未还。
- 双源 schema：新增 V2 迁移（embed_visitor_id）但测试 flyway 仍关闭、主套件仍跑 H2；metrics 仍为零（半天工作量的欠账）。
- 悬挂引用 TenantFilterGuard.java:23 仍在；新发现：ci.yml:33 守卫计数写「32 条」实际 40（B267 教训复发）、TenantFilterGuard javadoc 计数漂移 ×2。
- 归属可议项：RbacInterceptor（426 行授权策略引擎）住 platform、规则表住 boot——授权语义横跨三模块；platform/prompt + 10 个业务提示词 YAML 住技术模块。

### 5.6 域内分层与 L36-L45 改造考古 —— 真收益与过度仪表化并存

- 方言普查：17/20 域走「标准六件套」；发散点是 session（service/conversation/qa 三套服务词汇）、knowledge（15 个顶层包 + access 概念散在 3 包）、`runtime` 包**四义并存**（观测门面/单文件观测包/技术实现外提/域共享内核）。
- L36-L45 逐类裁决：mapper 门面搬出、memory 破环端口、im Redis 外提、L45 观测收口 = **净收益**；L38 六小域门面化 = **中性偏税**（14 个新门面 86/87 方法 98.9% 单行转发，favorite 415 行小域养 4 层链）；DataSourceTrace ≡ WikiTrace **逐字双胞胎**（16 行、各 1 调用点）= **净成本**，纯为棘轮清零服务。
- L50 后遗症：TenantAdmin 端口失去存在理由（单实现单消费方同域，javadoc 宣称的跨域消费方全仓 grep 为 0）；auth 四处 package-info 陈旧（tenant 死引用 ×2 等）——合并只做了 tenant 侧删除，没做 auth 侧回填。
- Ops 伪解耦 46 件原样（10 件持有控制器字段，如 `EmbedChannelDelegateOps.java:28`），是 C 批最大欠账。

### 5.7 前端与文档 —— 前端冻结，文档漂移集中在「22 域」族

- 前端基线后仅 1 个样式提交；五个质量面（SSE 封装/信封单点/rawFetch 守卫/路由/状态）维持优或健康，拆分范式已在仓内成熟（AgentStreamDisplay 4 个 composable 是活样板），缺的只是对 top5 的执行。
- 文档漂移 9 处，根因级 1 处：`gen-module-map.py` 模板硬编码「22 个业务域」（:6,:94,:107），生成器 --check 恒绿但文字错——不修脚本，每次重生成都会带回错误数字；另 overview/README/docs-README/HANDOFF 头部（版本停在 v0.3.0、分支停在 v0.5.0）。
- 守卫盲区：gen-domain-index 不校验 frontend 登记路径存在性。

## 6. 问题清单（分级汇总）

**P0（1）**
1. SSE 早失败死轮询后半：主循环认 ERROR-done 退出 + deadline + SessionStreamController 同款 + 前端 error 帧 abort（§5.2）

**P1（结构，8）**
2. 还乡 2.0（更新版 2885 行）：platform/knowledge 9 件 + MemoryKeys + ToolResultPersist（改判）+ Grep 族；WikiLanguageSupport 反转留 contract；换域词汇检测守卫做验收（§5.4）
3. B2 finalizer：6 行 import 换 Adapter + 消 2 份 drainSubtask 拷贝
4. 配置真相源收口：存储三头、向量库双头、websearch KV 兼容路径、**IM 凭据明文→AES-GCM 对齐**（§5.3）
5. Ops 46 件降级/升格（10 件持控制器字段优先）+ SkillCatalog/SkillsCatalog 合一
6. 整改尾巴：连通性双头合一；存储探测 HTTP 面迁 storage + parser 厂商键 37 个归位；evaluation 前端勾选项移除（后端常量保留）
7. D 批三项：metrics（半天，最高性价比）→ PG+flyway 测试 profile → @RouteSecurity 注解化（593+705 行两张手工表）
8. 词汇治理：`runtime` 四义定词典、DataSourceTrace/WikiTrace 双胞胎合并、TenantAdmin 冗余端口折叠、auth 四处 package-info 回填
9. 上手流程：README/getting-started 补「配模型 key」步骤（或 builtin_models.yaml 示例转正）

**P2（卫生，按收益排序）**
10. 文档漂移 9 处（根因：gen-module-map.py 硬编码 22；HANDOFF 头部 v0.6.x 化）
11. knowledge/task/package-info 重写（唯一误导级 package-info）；「M2 解环/走查批」注释残留 ×16 清洗；APIKeyCapability.java:56 批次代号 L49 残留
12. 三大长方法棘轮先钉 309/225/192；KnowledgeProcessWorker 19 参参数对象化
13. QaRequestContext 34 字段三段拆分；EventType/ResponseType 单表；planTools 清单收敛；title 忙转 sleep
14. 悬空引用/javadoc 计数/ci.yml 计数三微修（半小时级）；gen-domain-index 补 frontend 路径存在性校验
15. RbacInterceptor 归属拍板；platform/prompt 与 10 个提示词 YAML 归属拍板；approval 域去留拍板（并入 mcp 或明示为策略域）；CORS 通配+credentials 补论证
16. 前端 F 批（独立立项）：top5 拆分 + api/system 拆分（storage 函数归位）+ api/tenant 并入 auth（TenantInfo 双定义合一）

## 7. 目标架构方案

### 7.1 终态结构

```
boot（组合根，只出不进；装配类集中 config/）
 ├─ domains（20 域，标准六件套分层；域间只经 contract 端口，6 条登记边只减不增）
 │    知识代码两处化：knowledge 域 + contract 形状（还乡 2.0 完成后 platform/knowledge 消亡）
 ├─ model-runtime / retrieval-engine / agent-engine（三 runtime 平级，零漂移，全部维持）
 ├─ platform（纯技术适配；域词汇清零，prompt 归属拍板后或剩资源目录）
 └─ contract（纯形状 + 明示的 3 件契约共享算法文档化；32 端口，域间规则单一口径）
```

不变式（延续已定决策 + 本轮数据）：不拆微服务、不拆 :knowledge/:session 模块、不上 JPA/TenantLine、SSE 轮询先测量再动、金片与 connector 线格式冻结。

### 7.2 行动批次（G 系列；每批独立全绿）

| 批 | 内容 | 估时 | 验收 |
|---|---|---|---|
| **G1 缺陷收口** | SSE ERROR-done 终态（主循环 + SessionStreamController + 前端 abort，红态测试先行）；title 忙转 sleep | 1 人日 | 新增红→绿测试；两端循环均有终态出口 |
| **G2 整改尾巴** | 连通性双头合一（initialization 5 端点并入 /models/{id}/debug，前端收敛一条 API）；存储探测 HTTP 面+parser 厂商键归 storage/knowledge；evaluation 前端勾选项移除；approval 去留拍板 | 2-3 人日 | 每能力唯一 HTTP 面；前端 api 同步 |
| **G3 还乡 2.0（修正版）** | platform/knowledge 9 件回 knowledge（含 Grep 族）；MemoryKeys 回 memory；ToolResultPersist 回 session；SearchAuth/SearchTextUtil/WikiLanguageSupport 留 contract 并写入契约算法文档；拆 model↔retrieval 环；EmbeddingModelPort/ModelFactsPort 按统一口径拍板（ModelFactsPort 留） | 3-5 人日 | platform/knowledge 目录删除；**新守卫：域词汇检测**（check-platform-shapes 测不出，验收标准换血）；contract 类数净减 |
| **G4 B2+门面尾巴** | finalizer 6 行 import 换 Adapter + drainSubtask 合一；TenantAdmin 折叠；Trace 双胞胎合并/内联；`runtime` 词汇词典写入架构文档 | 1-1.5 人日 | knowledge→wiki import=0；单调用点门面清零 |
| **G5 Ops 整编** | 46 件降级/升格（10 件持控制器字段优先）；Skill 双控制器合一 | 2-3 人日 | Ops 不再引用控制器；控制器包纯净 |
| **G6 横切升级（D 批）** | actuator+micrometer（先行，半天）→ PG+flyway 测试 profile（H2 分阶段退役）→ @RouteSecurity 注解化（RBAC 先、APIKey 后）；RbacInterceptor 归属随 @RouteSecurity 一并拍板 | 7-12 人日 | 两张手工表删除；CI 增 PG 套件；/actuator/metrics 可用 |
| **G7 配置收口** | 存储三头收敛（tenants KV storage-engine-config 转 storage_backends 表，旧 KV 只读兼容一个版本）；向量库 env 虚拟库文档化为部署形态；websearch KV 兼容路径下线计时；IM 凭据 AES-GCM 对齐；README/getting-started 补配模型 key 步骤 | 2-3 人日 | 每类凭据唯一真相源；凭据加密全域一致 |
| **G8 可读性棘轮（持续）** | 19 参构造器参数对象化；三大长方法先钉；QaRequestContext 三段拆分；EventType/ResponseType 单表；package-info 重写与 auth 回填；注释批次代号清洗；return null/trim 棘轮 | 持续 | 棘轮只减；误导级 package-info 清零 |
| **G9 前端（独立立项）** | top5 巨石按 tab/面板/composable 拆分（仓内范式已备）；api/system 拆分+storage 归位；api/tenant 并入 auth | 独立 | vue-tsc + 130 测试全绿 |
| **G10 文档对齐（随手批）** | gen-module-map.py「22→20」根因修+重新生成；overview/README/docs-README 散文 6 处；HANDOFF 头部 v0.6.x 化并收录两份评估文档；gen-domain-index 补路径存在性校验 | 0.5 人日 | 5 生成器 --check 绿且散文数字一致 |

顺序建议：G1 → G10（半天，先消除误导）→ G2 → G3（冻结期越长 diff 越大，OC 系列已在续费）→ G4 → G5 → G6 → G7/G8/G9 并行持续。

### 7.3 本轮新增的「拍板清单」（需用户/负责人决策）

1. approval 域：并入 mcp（收缩到 19 域）或保留并明示为「mcp 的策略实现域」。
2. EmbeddingModelPort：保留（域间端口合规）或并入 EmbeddingPort（embedding 三重抽象合一）——先统一「域间端口单一口径」规则。
3. platform/prompt 与 10 个提示词 YAML：随还乡 2.0 归 domains 资源目录，或保留并文档化为「提示词装载设施」。
4. RbacInterceptor 归属：随 @RouteSecurity 注解化一并定（建议规则与执行器同模块）。
5. NORMAL 失败轮是否照常排异步索引/建议/记忆（`QaTurnFinalizer.java:174-205` 失败轮与成功轮无差别）——语义问题，非缺陷，产品侧拍板。

## 8. 量化附录（HEAD 实测，对比 v0.5.0 基线）

- 模块主码：contract 351/20,523 · domains 1,564/210,851(70.2%) · platform 189/17,629 · boot 19/2,197 · model-runtime 152/17,925 · retrieval-engine 95/20,484 · agent-engine 67/10,815（口径修正）；合计 −2,747 行
- 域规模 top：session 187/29,901 · knowledge 259/28,026 · datasource 149/26,618 · wiki 166/23,632 · mcp 148/16,524 · im 102/17,770 · auth 120/13,009（tenant 并入 +741）
- 域间边 6 条/33 文件（8→6，memory→session 与 auth→tenant 已清）；boot→domains 10 装配类（合法）
- 卫生：>800 行类 0；>100 行方法 86（top：SqlSelectDeepChecker.deepCheck 275）；≥8 参构造器 49（极值 KnowledgeProcessWorker 19）；@Lazy 0；B 代号 0（新残留 L49 代号 1 处）；return null ~979（同法口径与基线持平）；trim 私拷贝 39
- 词汇：Service 91 / Adapter 51 / Ops 46 / Handler 33 / Support 23 / Manager 9 / Port 6 / Facade 1 / Gateway 0 / Delegate 0——未失控，但 runtime 包语义 4 种
- 测试：483 文件 / 4,632 @Test（evaluation 下线 −24）；守卫 40 条 + ArchUnit 28 + MISPLACED_TEST_ALLOW 10，三数零漂移
- 30 天热点：knowledge 1,931 > session 1,719 > wiki 866——两大核心链路即活动中心，与业务重要性一致
- 前端：129,334 行 .vue，top5 巨石 27,110 行（21.0%）基线后 0 提交
