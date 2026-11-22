# framework 业务语义回归实施计划（交执行 Agent）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 framework 中寄住的业务语义（vocabulary 26 · content 8 · prompt 11 · settings 2 · model 3 · tool 1 · event/payload 21 · tenant 载荷 5，共 77 类）按消费者矩阵归位到域 `api/` 与引擎 `api/` 或删除，framework 只留 6 个逐类登记理由的跨晶格形状。

**Architecture:** 钉子优先——台账已实证 vocabulary 搬不动的根因是 framework 自身的业务实现文件（`MessageView`/`ServletSseSink`/`MemoryConfig`/`SearchAuth`/`ConversationProperties`/`FaqChunkMetadataCodec`）反向钉住了形状。本计划先把钉子文件归位，类型随行；引擎落点服从依赖晶格（app→{ae,re,mr}→framework，ae/re 平级、不能互见，都只能见 mr 与 framework）。

**Tech Stack:** JDK 21 + Maven 多模块（根 `pom.xml`）；金片契约测试 + 49 条 Python 守卫为安全网；搬迁手法沿用 shared 解散计划验证过的「标准流程」。

**Spec:** `docs/blueprint/architecture-blueprint.md` §5.2（contract 解散归属表）、§5.4（域三级）；上游计划 `docs/internal/plans/shared-dissolution-execution-2026-11-21.md`（本文件是它 §7 终态第 6 条明示的「另立批次」，并执行其未竟的 WP2/WP3/WP5 落点）。

**生成**：2026-11-22，基线提交 `7f3f5280f`（v0.11.x，工作区 clean）。消费者矩阵为当日 grep 实测（主码 `src/main`，词边界匹配类简单名；framework 模块自身不计）。

---

## 0. 现状与依据

上轮收口（`e74fced8b`）后，业务语义在 framework 的七个寄住点（行数为主线码 `wc -l`）：

| 寄住点 | 文件/类数 | 行数 | 实质 |
|---|---|---|---|
| `framework/vocabulary` | 27（26 类+package-info） | 2280 | 会话/检索/知识/Agent/记忆/图谱业务形状 |
| `framework/content` | 9（8 类+package-info） | 1698 | 知识库授权/FAQ 元数据/wiki 工具接缝 |
| `framework/prompt` | 11（+`resources/prompts/` 12 个 YAML + `initialization/extract_config.yaml`） | 901 | RAG 提示词资产与组装 |
| `framework/settings` | 3 | 425 | system_settings schema + 问答管线配置 |
| `framework/model` | 4 | 62 | 嵌入端口（C8 登记在案） |
| `framework/tool` | 2 | 30 | BaseTool 的 JSON 适配 |
| `framework/event/payload` | 22（21 类+package-info） | 2073 | SSE 线格式业务载荷 |
| `framework/tenant` | 11 | 1440 | 其中 5 类是租户业务载荷，6 类已拍板永驻 |

台账实证的**钉子**（类型搬不动的原因，全部要先解）：`MessageView`（钉 AgentStep/AgentToolCall/SearchResult/MentionedItem 族）、`ServletSseSink`（钉 SseSink）、`framework/tenant/MemoryConfig`（钉 MemoryKinds，连带 MemoryKeys）、`content/SearchAuth`（钉 KnowledgeView）、`settings/ConversationProperties`（钉 prompt.PromptSource 消费链）、`content/FaqChunkMetadataCodec`（钉 FaqChunkMetadata）。

**已核实为零代码依赖的假钉子**（只需改 javadoc/package-info 文字）：`llm/domain/ProviderMetadataMaps` 之于 `AgentToolCall`（javadoc 提及）；`agentengine/tool/runtime/package-info` 之于 `SearchAuth`；`retrieval/support/package-info` 之于 `GrepChunkHit`。

## 1. 任务定义（终态）

1. vocabulary 26 类 → **6 个永驻**（§3.1 逐类登记晶格理由）+ 20 个归位/随迁；package-info 重写为永驻清单。
2. content 8 类 → 1 个中性化留 framework（WikiLanguageSupport）+ 7 个归位；`content` 包删除。
3. prompt 11 类 + 12 个 YAML + extract_config.yaml → 全部随 D-5 拍板迁出；`prompt` 包删除。
4. settings 3 类 → auth/session 归位；`settings` 包删除。
5. model 4 类 → `model-runtime/llm.api`（C8 基线清除）；`model` 包删除。
6. tool 2 类 → `agentengine/api`；`tool` 包删除。
7. event/payload 21 类 → 7 个删除 + 14 个归位（agentengine/api 9 · session/api 1 · mcp/api 4）；`Event` 包络与 eventbus 机制件留 framework（§4 D-6 登记）。
8. tenant 走 5 留 5（留者均为已拍板永驻：TenantRole/TenantConfigLookup/APIKeyScopeContext/TenantAPIKeyScope/StorageEngineConfig）。
9. framework 新增自卫测试：vocabulary 成员白名单棘轮（只许减不许增）。
10. 上游计划 §7 终态第 5 条「永驻词汇 ~10±」预算：本计划终态 vocabulary 6 + tenant 5 = 11，在带内。

**不在本计划**（各有归属批次，勿混入）：`task/` 自研队列→JobRunr（D19，引入时整体删除）；`web/RbacInterceptor`+`RbacRules`→Sa-Token（D20）；`persistence/` 三守卫→租户插件+RLS（蓝图骨架批，计划03）；`html`/`http`/`tracing/langfuse`/`jsonbind` 的去留（蓝图缺口，另立裁决）；otlp-proto 删除；Boot 3.5.16 升级；channel 域合并（im+embedchannel，D-4 关联项）。

## 2. 不可协商约束（继承上游 §2，验收每批全查）

| # | 约束 | 判据 |
|---|---|---|
| 1 | 不动前端 | 路由/字段/错误码/SSE 帧格式不变；金片测试逐字节不变 |
| 2 | 不动数据库 | 无 DDL、无迁移脚本 |
| 3 | 全量闸门 | `mvn -B clean verify`（根 pom）绿；**测试条数口径修订**：归位批守恒，删除批允许下降但台账登记差值与所删测试名 |
| 4 | 守卫 | `bash scripts/run-guards.sh` 全绿（条数以 run 行为准，上轮 49）；路径类基线只许 `--write` 重登记 |
| 5 | 包级环 | `check-package-cycles.py` 只减不增；搬入引擎前先跑（坑 12） |
| 6 | 域分级 | `scripts/capability-kinds.json` 为单一来源；落点必须 `level-ok`（上级→下级，或同级 `api/dto`） |

**验收命令**（每批一次，不用管道）：

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21   # keg-only，不 export 必炸（坑 14）
cd /Users/billy/ragagent
mvn -B clean verify; echo "exit=$?"
bash scripts/run-guards.sh; echo "exit=$?"
```

开工第一件事：跑一次上述命令，把**测试总条数 / 失败数 / 守卫条数**记入 §9 台账作基线（上轮参考值：5256 / 0 / 2，守卫 49/49）。

## 3. 归属总表（唯一归属，执行时不得另行发挥）

消费者列 = 2026-11-22 实测（app/ae/re/mr 主码文件数）。**落点晶格判据**：消费方 ⊆ {app} → 域 api；⊆ {app, ae} → agentengine/api；⊆ {app, re} → retrieval/api；⊆ {app, ae/re, mr} → llm.api 或 framework 永驻（语义决定）。

### 3.1 vocabulary 26 类

| 类 | 消费者 | 落点 | 前置/备注 |
|---|---|---|---|
| SearchResult | 53/1/8/4 | **永驻 framework/vocabulary** | ae(AgentState)+mr(Registry/SourceRegistry/StreamResponse) 双引擎钉死；两次拍板维持 |
| ChunkView | 19/0/1/1 | **永驻** | re(SearchChunkMerge)+mr(ModelView) 跨引擎 |
| GraphNode · GraphRelation | 7/0/2/2 | **永驻** | re(Neo4j)+mr(EntityExtraction) 跨引擎；上游 WP5 已定死 |
| ToolResult | 55/15/0/2 | **永驻**（D-3 改判上游 D-5） | mr modelcontext 打包消费，ae api 会造 mr→ae 反向边 |
| KnowledgeBaseGateway | 6/0/0/0 | **永驻** | 上游 WP4-B 唯一方向违规项（auth L1→knowledge L2）；待 Provider 反转批，本计划不动 |
| MessageView | 13/0/0/0 | `app/session/api`（**D-1 拍板**） | 钉子文件本体；消费域 config2/embed3/im4/session4，同级 api 放行 |
| SseSink | 10/0/0/0 | `app/session/api` | 前置：ServletSseSink 同批搬 app（上游 WP5/K16 既定） |
| MessageExecutionContext · SessionLastRequestState · SuggestionItem · SuggestionAttribution · UsedMemory · MessageImage · PipelineMessageAttachmentView | 各 3~9/0/0/0 | `app/session/api` | 上游 WP2 既定未竟；PipelineMessageAttachmentView 随 MessageAttachmentsPrompt 同批（WP4） |
| MessageAttachment | 15/0/0/0 | `app/im/api` | 上游 WP2 既定 |
| MemoryKinds | 17/0/0/0 | `app/auth/api`（**D-2 拍板**） | 与 MemoryConfig 同批（auth L1 不能 import memory L2） |
| SearchTarget · KnowledgeView | 31·10/0/0/0 | `app/knowledge/api` | SearchTarget 上游 WP4-B 已裁；KnowledgeView 前置=SearchAuth 先走 |
| KnowledgeBaseFacts | 3/0/0/0 | `app/audit/api` | 上游 WP4-B 转正项 |
| ChunkFacts · MessageArtifact | 20·11/0/0/4·1 | `retrieval-engine/retrieval/api` | 上游 WP3 既定；re 消费方为内部+api 包 |
| AgentStep · AgentToolCall · ToolCallTarget · MentionedItem | 18·12·6·9 / 10·7·5·1 /0/0 | `agent-engine/agentengine/api` | 前置=MessageView 先走（D-1）+ ProviderMetadataMaps javadoc 改写；MentionedItem 因 agentengine/api/SteerSink 消费不能去 session |

### 3.2 其余六包

| 现位置.类 | 消费者 | 落点 | 备注 |
|---|---|---|---|
| model.Embedder · EmbeddingPort · ModelFacts | 27·5·6 / 0·0·0 / 5·1·2 / 10·1·1 | `model-runtime/llm.api` | C8 基线同 commit 清除；上游 WP3 原意 |
| model.package-info · tool.ToolSchemas | 28/0/0/4(agent-engine) | `agentengine/api` + 删 model/、tool/ 包 | package-info 引用的 contract.tool 已不存在，随删 |
| content.SearchAuth | 21/1/0/0 | `app/knowledge/api` | ae 的 package-info 提及同批改文字；解 KnowledgeView 钉 |
| content.DocChunkSupport | 7/0/0/0 | `app/agenttools`（业务包，工具接缝） | javadoc 点名的三个工具全在 agenttools |
| content.FaqChunkMetadata + FaqChunkMetadataCodec | 15·8/0/0/0 | `app/knowledge/api/faq`（新子包） | 两类同批走（codec 钉）；新子包建 package-info |
| content.ChunkSearchUtil | 10/0/3/0 | `retrieval-engine/retrieval/api` | re 消费=ImageInfoMatchUtil/ImageInfoEnricher/SearchChunkMerge |
| content.GrepChunkHit | 4/0/1(package-info)/0 | `app/agenttools/api` | re 的 package-info 提及改文字 |
| content.ImageRefRewriter | 4/0/0/0 | `app/knowledge`（解析子包） | 落盘接缝 Saver 随调用方注入，无 framework 依赖 |
| content.WikiLanguageSupport | 16/1/0/0 | **留 framework**，迁 `framework/text`，中性化改名 `RequestLanguages`（**D-4 拍板**） | ae ConversationAssembler 消费 ⇒ 不能去 wiki 域；javadoc 去 wiki 叙事 |
| prompt.PromptComposer · PromptSource · AgentPromptPlaceholders · PromptConstants | 5·7·8·1 / 2·2·1·1 /0/0 | `agentengine/api`（+`PromptTemplates` 五路扫描判死或随迁） | `resources/prompts/` 12 个 YAML + `initialization/extract_config.yaml` 随 PromptSource 迁 `agent-engine/src/main/resources/`（**D-5 拍板**；classpath 合一，运行时无差） |
| prompt.PipelineConfig | 8/0/0/3 | `model-runtime/llm.api` | 上游 WP5「落点已定死」（mr EntityExtractionPort 消费） |
| prompt.ExtractPrompts · PromptInstructions | 3·3/0/0/0 | `app/knowledge` | 上游 WP5 定死 ExtractPrompts→app |
| prompt.MessageAttachmentsPrompt | 6/0/0/0 | `app/session` | 上游 WP5 定死；与 PipelineMessageAttachmentView 同批 |
| prompt.PromptTemplateCatalog | 2/0/0/0 | `app/auth` | 消费方=TenantCatalogController/TenantConfigOps |
| settings.SystemSettingRegistry | 2/0/0/0 | `app/auth`（system 面已并入 auth） | 含界面文案的 schema 属 system 域 |
| settings.ConversationProperties | 8/0/0/0 | `app/session/api` | **坑 6**：`RagAgentApplication` 的 `@ConfigurationPropertiesScan` 精确名单同步加 `com.ragagent.session.api` |
| event/payload 七死类：RetrievalData · RerankData · MergeData · QueryData · ChatData · AgentQueryData · AgentPlanData | 0/0/0/0 | **删除** | 已五路扫描复核主码零消费（WP0 再跑一遍含 mixin/测试/SPI）；`ContractJsonMixinsEvent` 内对应 mixin 条目同删 |
| event/payload.Agent 族 10 类：AgentCompleteData · AgentFinalAnswerData · AgentToolResultData · AgentToolCallData · AgentActionData · AgentThoughtData · ContextCompactedData · UserMessageInjectedData · ErrorData（+mixin 随迁） | 各 1~9 / 各 1~3 /0/0 | `agentengine/api` | mixin 进 agent-engine 既有 JsonModuleProvider（**只追加**，坑 1/2）；`check-event-face-case` 锚改 |
| event/payload.StopData | 1/0/0/0 | `app/session/api` | |
| event/payload.MCP 族 4 类：ToolApprovalRequiredData · ToolApprovalResolvedData · MCPOAuthRequiredData · MCPOAuthResolvedData | 各 2~3/0/0/0 | `app/mcp/api`（**新建**，含 package-info 四要素） | |
| eventbus.GlobalEventBus | 0/0/0/0 | **删除** | 同包引用五路扫描后删 |
| tenant.APIPrincipalConfig · APIKeyCapability · APIKeyScopeType | 8·10·6/0/0/0 | `app/auth/api/apikey` | 上游 WP2 既定 |
| tenant.MemoryConfig | 15/0/0/0 | `app/auth/api` | 上游 WP4-A 既定（tenants jsonb 载荷族）；带 MemoryKinds 同批 |
| tenant.MemoryKeys | 14/0/0/0 | `app/memory/api` | 前置=MemoryConfig 先走（闭包预检确认 framework 无残余引用，否则随去 auth/api 并登记） |
| framework/web/ServletSseSink | app 4 处消费 | `app/session/controller/sse/` | 与 SseFrameWriter 同址；蓝图后续 SseEmitter 替换不属本计划 |

## 4. 拍板项（开工前确认，均有推荐默认）

| # | 决策 | 推荐 | 后果 if 否决 |
|---|---|---|---|
| D-1 | `MessageView` 现在迁 session/api，还是维持上游 D-4 永驻等 channel 合并 | **现在迁**：消费方全在 app（同级 api 放行），且它是 agent 族四类的钉子 | AgentStep/AgentToolCall/ToolCallTarget/MentionedItem 四类继续滞留 vocabulary，WP5 半途废 |
| D-2 | MemoryConfig+MemoryKinds 落 auth/api 还是 memory/api | **auth/api**：auth(L1) 不能 import memory(L2)；memory/session 等上级引 auth 合法。语义注记「记忆域词汇，随域分级演进再迁」 | memory/api 需先把 MemoryConfig 对 MemoryKinds 的引用解掉（提级或复制常量），多 0.5 天且造重复 |
| D-3 | ToolResult 落点（改判上游 D-5 的 agentengine/api 推荐） | **永驻 framework**：mr modelcontext 两文件真实消费，ae api 会造 mr→ae 反向边；解耦 modelcontext 属改造非搬迁 | 若坚持 agentengine/api，须先重构 Registry/ModelOutput 接受中性形状，+1~2 天 |
| D-4 | WikiLanguageSupport | **中性化改名 RequestLanguages 留 framework/text**：ae 消费钉死域落点；framework 蓝图本有多语言能力位 | 迁 agentengine/api 亦可（晶格合法），但 16 个 app 消费方引引擎 api 取语言工具，语义倒挂 |
| D-5 | prompts/ YAML 资产位置 | **随 PromptSource 迁 agent-engine resources**：单一装载入口与资产同址 | 留 framework resources 则 PromptSource 拆两半或跨模块读资源，更乱 |
| D-6（登记，非本批） | eventbus/Event/EventType 整包去向 | 本计划不动（Event 包络蓝图字面归 framework；EventType 40 常量随 Modulith/JobRunr 批再裁） | — |

## 5. 工作包

执行手法 = 上游计划 WP2~WP4 验证过的**标准流程**，每类型五步：①闭包预检（`grep -rn "<简单名>" backend/ --include='*.java'` + 肉眼看同包留守者）→ ②移动文件改 `package`，目标包无 package-info 必新建（四要素，坑 4）→ ③改全部消费方 import（本项目 import 与全限定名两种都有）→ ④`mvn -pl backend/<模块> -am compile` 快速失败（坑 16）→ ⑤批末全量验收 + 分模块 spotless（坑 5）。

### WP0 快赢与解钉准备（0.5 天）

**Files:** 删 `framework/event/payload/{RetrievalData,RerankData,MergeData,QueryData,ChatData,AgentQueryData,AgentPlanData}.java`、`framework/eventbus/GlobalEventBus.java`；改 `framework/jsonbind/ContractJsonMixinsEvent.java`（删对应 mixin 条目）、`framework/vocabulary/package-info.java`（索引漂移：SearchAuth/FaqChunkMetadata/WikiLanguageSupport 已迁 content 未更新）、`model-runtime/.../llm/domain/ProviderMetadataMaps.java`（javadoc 去 AgentToolCall 提及）、`agent-engine/.../tool/runtime/package-info.java`（去 SearchAuth 提及）、`retrieval-engine/.../support/package-info.java`（去 GrepChunkHit 提及）。

- [ ] 对 7 个死类 + GlobalEventBus + `prompt/PromptTemplates` 跑五路扫描（简单名/同包/测试/反射字符串/SPI）；`PromptTemplates` 若仅同包引用则记录归 WP4 随迁，否则并入本批删除
- [ ] 删除 + mixin 条目清理；对应 `*Test` 随删，台账登记测试条数差值
- [ ] `check-event-face-case.py` 若因删类红：锚内条目属删除即改脚本去条目（不是 `--write` 掩盖）
- [ ] 全量验收 → commit `refactor(framework): WP0 清死载荷与文档钉（7+1 类）`

### WP1 引擎小包（0.5 天，无前置）

**Files:** `framework/model/{Embedder,EmbeddingPort,ModelFacts}.java` → `model-runtime/.../llm/api/`；`framework/tool/ToolSchemas.java` → `agent-engine/.../agentengine/api/`；删两个 package-info 与空目录；`scripts/` 下 C8 基线清除（先 `grep -rn "framework.model.Embedder\|framework.tool.ToolSchemas" scripts/`）。

- [ ] 标准流程五步 ×4 类；`check-platform-shapes.py` 的 C8 登记同 commit 删
- [ ] 全量验收 → commit `refactor(framework): WP1 model/tool 四类归引擎 api，C8 清除`

### WP2 session 面（1 天，D-1 前置）

**Files:** `framework/web/ServletSseSink.java` → `app/.../session/controller/sse/`；vocabulary 8 类（SseSink、MessageView、MessageExecutionContext、SessionLastRequestState、SuggestionItem、SuggestionAttribution、UsedMemory、MessageImage）→ `app/session/api/`（**新建包**，package-info 四要素：域定位/对外契约/消费方/负责人格式照 im/api 抄）；`MessageAttachment` → `app/im/api`。

- [ ] 先搬 ServletSseSink（解 SseSink 钉）再搬 SseSink；`web/package-info` 同步
- [ ] MessageView 走前跑 `check-package-cycles.py`（session/api 引 agentengine 前先确认 WP5 未开始时无环：本轮 MessageView 只引 framework 类型，安全）
- [ ] **MessageView 及其 mixin**：`ContractJsonMixins(Core)` 若持有 MessageView 条目，迁 `app/config/json`（P1a 先例，provider 只追加）
- [ ] `check-json-key-case.py` 冻结面按新路径 `--write`；`check-package-info-fields.py` 查新包
- [ ] 全量验收 → commit `refactor(framework): WP2 session 面 8+1 类归位，ServletSseSink 还乡（D-1）`

### WP3 auth/memory 面（0.5 天）

**Files:** `framework/tenant/{APIPrincipalConfig,APIKeyCapability,APIKeyScopeType}.java` → `app/auth/api/apikey/`；`MemoryConfig` + vocabulary 的 `MemoryKinds` → `app/auth/api/`；`MemoryKeys` → `app/memory/api/`（闭包预检不过则随去 auth/api 并在台账登记）；`framework/settings/SystemSettingRegistry.java` → `app/auth/`（system 子包）；`ConversationProperties` → `app/session/api/` + `RagAgentApplication` Scan 名单加包。

- [ ] 标准流程五步 ×7；`framework/tenant/package-info` 与 `framework/settings/package-info` 更新或随空包删除（settings 包本批后清空删除）
- [ ] **坑 6 专项**：搬完 ConversationProperties 后 `grep -n 'conversation'` 启动一次 `app` 本地起不来就查 Scan 名单；补一条单测断言 ConversationProperties 非 null 绑定（`@SpringBootTest` 既有配置测试里加一行）
- [ ] 全量验收 → commit `refactor(framework): WP3 租户载荷与 settings 归位（D-2）`

### WP4 prompt/content 面（1 天）

**Files:** 见 §3.2 prompt 六组与 content 七类。要点排序：先 `PipelineConfig`→llm.api（解 mr 钉）→ `PromptSource` 族+YAML 资产迁 agent-engine（**含 `git mv` resources**，`ClasspathResourcesTest` 若锚 framework 路径则同步改锚）→ `ExtractPrompts`/`PromptInstructions` → knowledge；`MessageAttachmentsPrompt`+`PipelineMessageAttachmentView` → session；`PromptTemplateCatalog` → auth；`SearchAuth` → knowledge/api（解 KnowledgeView 钉）→ `KnowledgeView`、`SearchTarget` → knowledge/api；`FaqChunkMetadata(+Codec)` → knowledge/api/faq；`ChunkSearchUtil` → retrieval/api；`GrepChunkHit` → agenttools/api；`DocChunkSupport` → agenttools；`ImageRefRewriter` → knowledge；`WikiLanguageSupport` 中性化 → framework/text。

- [ ] `app/agenttools`、`app/knowledge/api/faq` 等新落点无 package-info 则建；`PromptOwnershipTest`（app/arch）预期红——按新归属改判据（prompt 所有权从 framework 变引擎/域），这是**健康红**
- [ ] `check-wiring-points.py`：`RuntimeSnapshotWiring`（RAGAGENT_LANGUAGE 快照写入）分类与 overview.md 同步
- [ ] content/prompt 两包清空后删目录；`docs/architecture/packages.md` 的 vocabulary/content 条目重生成（`gen-arch-index.py --check` 会提示）
- [ ] 全量验收 → commit 拆两个：`refactor(framework): WP4a 提示词面归位（D-5）` / `refactor(framework): WP4b content 七类归位，包删除`

### WP5 agent 族与事件载荷（1 天，前置 D-1+WP0）

**Files:** vocabulary 4 类（AgentStep/AgentToolCall/ToolCallTarget/MentionedItem）→ `agentengine/api`；event/payload Agent 族 9 类 → `agentengine/api`（mixin 随迁，agent-engine 的 JsonModuleProvider **只追加**；无 provider 实现则新建 `agentengine/json/AgentEventJsonModule`）；StopData → session/api；MCP 族 4 类 → `app/mcp/api`（新建）；`KnowledgeBaseFacts` → audit/api；`ChunkFacts`/`MessageArtifact` → retrieval/api。

- [ ] 搬前确认 MessageView 已不在 framework（WP2）；`agentengine/api` 包加 package-info 段落说明事件载荷即前端 SSE 协议
- [ ] mixin 迁移后**金片专项**：`mvn -pl backend/app -am verify -Dtest='*ContractTest'` 全绿才算 mixin 搬对（坑 1：framework 自身 mapper 序列化路径上的类型 mixin 必须留——`EventJson` 若序列化这些 payload，其 mixin 留 framework 并在台账登记）
- [ ] `check-event-face-case.py` 锚从 `framework/event/payload` 改为 `{agentengine/api, app/session/api, app/mcp/api}` 三处；`event/payload` 删空目录
- [ ] 全量验收 → commit `refactor(framework): WP5 agent 族与 SSE 载荷归位（21 类）`

### WP6 收尾（0.5 天）

**Files:** `framework/vocabulary/package-info.java` 重写（只列 6 永驻+逐类晶格理由）；新建 `framework/src/test/java/com/ragagent/framework/vocabulary/VocabularyManifestTest.java`；守卫陈旧账清理。

- [ ] **VocabularyManifestTest**：断言 vocabulary 主码类的 FQN 集合恰为 6 类白名单——新增即红（棘轮只减不增）；同文件加第二条：`framework/tenant` 恰为 5 类白名单
- [ ] `check-platform-shapes.py` / `check-contract-shape.py`：搬进 api 的新路径字段预检（§3.1 已知撞墙项：`MessageView` 26 字段——进 session/api 同 commit 登记 `contract-shape.baseline.json` 新路径并删旧；`TenantAPIKeyScope` 留 framework 不撞）
- [ ] `grep -rn "framework.vocabulary\|framework.content\|framework.prompt\|framework.settings\|framework.model\|framework.tool" backend/*/src/main --include='*.java' | grep -v "framework/vocabulary" | wc -l` = 0（消费方清零；vocabulary 自身除外）
- [ ] 终态验收（§7）→ commit `refactor(framework): WP6 vocabulary 收缩至 6 永驻 + 白名单棘轮`

## 6. 执行顺序

```
WP0（死代码+文档钉）──► WP1（model/tool，无依赖）──► WP2（session 面，D-1）──► WP3（auth/memory）
                                                                    │
                                              WP4（prompt/content，依赖 WP3 的 Scan 名单先例）
                                                                    │
                                              WP5（agent 族+载荷，依赖 WP0 mixin 清理 + WP2 MessageView）
                                                                    │
                                                                    WP6（收尾+棘轮）
```

每批一个独立 commit（WP4 拆两个），出红可二分回退（`git revert` 单批）。message 惯例 `refactor(framework): WPx-N <内容>`，台账登记进 §9。

## 7. 全局验收（WP6 完成时）

1. `framework/{content,prompt,settings,model,tool,event/payload}` 目录不存在；`vocabulary` 恰 6 类 + package-info；`tenant` 恰 5 类 + package-info。
2. `mvn -B clean verify` 绿；测试条数 = 基线 − 台账登记的删除差值；守卫全绿。
3. `check-package-cycles.py` 环零新增；金片 `*ContractTest` 全绿（SSE 帧格式零漂移）。
4. `VocabularyManifestTest` 在 framework 模块内运行通过（framework 首个自卫测试）。
5. 蓝图 §5.2 对齐：`retrieval.*`→retrieval/api ✓、`model.*`→llm.api ✓、knowledge/session/wiki/memory 面→域 api ✓、`error/event.Event/pagination/json` 留 framework ✓。

## 8. 坑清单

上游 §8 全 17 条原样适用（编号沿用：坑 1 mixin 序列化路径 / 坑 2 provider 只追加 / 坑 3 同包闭包 / 坑 4 package-info / 坑 5 分模块 spotless / 坑 6 Scan 名单 / 坑 7 javadoc -Werror / 坑 8 守卫白名单全名 / 坑 9 json-key-case / 坑 10 capability-kinds / 坑 12 包环+命名空间 / 坑 13 管道 / 坑 14 JAVA_HOME / 坑 15 判死五路扫描 / 坑 16 -pl -am / 坑 17 形状守卫预检）。本计划新增：

18. **mixin 跨模块迁移后必须跑金片**：payload 的 mixin 从 `ContractJsonMixinsEvent` 迁出时，若 `EventJson`/`StreamJson`（framework 自身 mapper）的序列化对象图覆盖该类型，mixin 留 framework（坑 1 的本计划特化）。
19. **删类≠删 mixin**：payload 删除时 `ContractJsonMixinsEvent` 的条目与 `check-event-face-case` 的锚条目同 commit 清，否则守卫读数失真。

## 9. 执行台账（执行 Agent 填写）

> **已执行**（2026-11-22，8 commits fc9f58dd3..add0cd459，终态 5241/0/2 · 守卫 49/49）。
> 逐批台账与 16 条 Ruling 见 `.superpowers/sdd/framework-return-execution-2026-11-22/progress.md`（会话级暂存，要点已回填本节）。

| 批 | 日期 | 内容 | 验收数字（测试/守卫/金片） | 备注 |
|---|---|---|---|---|
| 基线 | 2026-11-22 | 开工全量 verify + 守卫 @7f3f5280f | 5257/0/2 · 49/49 | 必须带 ZZ_PG_JDBC=…/RagAgent（HANDOFF.md:109） |
| WP0 | 2026-11-22 | 删 7 死 payload+GlobalEventBus+PromptTemplates；mixin 清理；orEmpty 内联 14 类；修 3 文档钉+索引漂移 | 5236/0/2 · 49/49 | 测试差值 -21 精确对账 |
| WP1 | 2026-11-22 | model/tool 四类→llm.api/agentengine.api；C8 20→18；re→mr 依赖边补线（蓝图 §5.2） | 5236/0/2 · 49/49 | R6 矩阵登记新边 |
| WP2 | 2026-11-22 | 晶格改道：SessionLastRequestState→im/api、SuggestionItem→embedchannel/api；6 类被 MessageView 聚合簇字段级反钉留守 | 5236/0/2 · 49/49 | D-1 的"同级放行"判断对 L2 通道域不成立；上游 D-4 判断正确 |
| WP3 | 2026-11-22 | MemoryConfig/MemoryKeys/MemoryKinds→auth/api；SystemSettingRegistry→auth/system/dto；ConversationProperties→framework/config；settings 包删除 | 5236/0/2 · 49/49 | tenant 三类被 RbacInterceptor→TenantAPIKeyScope 钉链留守 |
| WP4 | 2026-11-22 | prompt 10 类+11 YAML+extract_config.yaml 迁出；content 8 类+KnowledgeView/SearchTarget/ChunkFacts 随行；WikiLanguageSupport→text/RequestLanguages；prompt/content 包删除 | 5236/0/2 · 49/49 | PromptSource 断钉=清单副本内联；ExtractPrompts 落 knowledge/api（R10 拒实现包） |
| WP5 | 2026-11-22 | 事件载荷 14 类迁出（agentengine/api 9+session/api+mcp/api）；mixin 拆三处；ContractJsonMixinsEvent 删除；守卫换锚三目录 | 5236/0/2 · 49/49 | 金片全绿=线格式零漂移 |
| WP6 | 2026-11-22 | vocabulary 终态 20 类 package-info 重写；VocabularyManifestTest 双白名单棘轮（framework 首个自卫测试） | 5239/0/2 · 49/49 | 六寄住点 import 全部清零 |
| 终审 | 2026-11-22 | fresh-context 审阅 verdict 可合并；3 Important 修复（PipelineMessageAttachmentView→session、C8 基线对齐、绑定钉测试） | 5241/0/2 · 49/49 | 终态 vocabulary 19+tenant 8；16 条 Ruling 在执行台账 |

**终态验收对照（计划 §7）**：1 ✓（六目录消失，vocabulary 19+tenant 8——20/6 的原始目标经 13 条字段级反钉实证修正为 19/8，逐类理由在 package-info 与 Ruling 5/8/14）；2 ✓；3 ✓（环零新增、金片全绿）；4 ✓（VocabularyManifestTest）；5 部分——`retrieval.*`/`model.*`/knowledge/session 面归位 ✓、`error/event.Event/pagination/json` 留 framework ✓。

---

## Review Focus（实现者必读的五个易错面）

1. **mixin 丢失导致线格式漂移**——金片不红不等于没丢（只测已覆盖路径）；每批迁移后跑 `-Dtest='*ContractTest'` 专项（WP5 步骤已钉）。
2. **同包简单名闭包**——vocabulary/content 内部互引不需要 import，矩阵看不见；每类型搬前肉眼扫同包留守者（标准流程①）。
3. **@ConfigurationProperties 静默失绑**——ConversationProperties 迁后属性全 null 但不报错；WP3 已钉单测断言。
4. **守卫锚未随迁**——`check-event-face-case`/`check-platform-shapes`(C8)/json-key-case 三处按路径锚定，搬包即失覆盖；每批对照 WP 内清单逐条改锚，禁用 `--write` 掩盖删除类红。
5. **javadoc `{@link}` 断链**——类迁包后简单名链接在 `-Werror` 下炸构建；改全限定（坑 7），WP0 的三处 package-info/javadoc 改写即是此类。
