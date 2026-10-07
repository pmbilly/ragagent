<!-- 由 HANDOFF.md 拆分而来（B32，2026-10-02）。
     节号保持原样；正文逐字节未改（除 3 处过期标题更正）。 -->
#### 15.1.1 执行记录（按批次，✅ 批必读）

**✅ B2**：`support/GoldenContract` 共享基建上线（`ContractJson.deep` 归一 + strip + 单一 `-Dcontract.refresh` 开关）；Faq/W5d/W5c 三文件迁移；Knowledge/Mcp/Model/Wiki 四个字节级文件 23 处 `content().bytes` 断言迁移（`goldenBytes` 助手退役——字节级对比类别清零）。教训：跨行 Java 语句的正则迁移必须以**语句锚**扫描（perform 起点 + `;` 终点），逐行扫描会在嵌套 perform 上重复插入（第一版已回退重写）。

**✅ B1**：`docs/knowledge-api-contract-v1.md` v1.0→v1.1——升格全服务端标准；§2.2 错误体（顶层仅 `error`、键序声明序、`details` 显式 null、纯字符串错误体两类场景）+ §2.2.1 收敛口径表（游标分页/附加字段分页/条件键恒输出/连通性测试/凭据状态/客户端本地态）+ §8 收官批记录。

**✅ B3**：真面 68 处（19 文件）`@JsonInclude` 退役——wiki domain 全家（10 文件）+ websearch 三 DTO + VectorStoreTypes；键恒输出/空数组 `[]`/空串照写；金片 vs-types/wsp-*（11 文件）/wiki 四件 refresh 重录；WikiDomainTest omitempty 负断言翻转、WikiHttpContractTest `depth:0` 断言翻转。
- **回退一则**：`im/yunzhijia/YunzhijiaTypes` 误列真面——它是云之家第三方出站线格式（NON_EMPTY 省略键是对端 API 契约），YunzhijiaAdapterTest 抓回，并入 §14.6 IM 第三方口径。
- **B3b 登记（独立可选批）**：KB 更新请求的 `faq_config/wiki_config/chunking_config/...` 外层 dispatch 键 + 内层业务键（`question_count`/`synthesis_model_id`/`index_mode`…，服务端 `path()` 读取器保留 snake 是收官批口径）+ `knowledge_bases.*_config` 落库列三方咬合——改键=落库格式变更+迁移 SQL，超出恒输出轴；仅当有真实需求（如前端统一读 camel）时立项。
- **新坑**：掩码正则 `TS_PATTERN` 只匹配数字、替换串带引号，产出 `""<ts>""` 非法 JSON——掩码应**连成对引号一起匹配**；refresh 写出的夹具才可再解析。

**✅ B4（结论：不改）**：哨兵不是债——① datasource 域零值=「从未同步」的**业务信号**（`lastSyncTime` 进调度比较，改 null 要动调度语义）；② AgentStep 是冻结 SSE 事件面，前端消费已安全（负时间戳 falsy→undefined，`useChatStreamHandler` 实测）；③ agentm/init 两处前端无读点；④ 11 个金片 126 处字面量钉住。收益 < 风险，按 §14.5 登记已知例外。

**✅ B7**：依赖级口径（声明+构造赋值 ≤2 次）实锤断成员 19 处全清——只注入不读取字段 3（`PluginSearchParallel`）+ 死 logger 8 + 死 `ObjectMapper` 3 + 死 `Pattern` 1 + 死私有方法 4（含孤立 javadoc 清理）+ 冗余 import 4（javadoc 行规避后复核）。66 处历史候选复核为零。

**✅ B8**：全限定名注解 177+6 → 0（14 文件，注入前逐文件同名符号冲突扫描）；`@JsonIgnoreProperties` 48 处评估**保留**——14 个严格 mapper 读取点全是外部/不可信 JSON（第三方 API 响应/OAuth 文档/租户落库/Redis blob），注解是正确纵深防御；WEB 侧 8 个读路径已宽松，冗余但无害（§2 第 12 条勿批量删）。

**✅ B0（2026-10-02，走查批收官）**：后端 8083（postgres 驱动）+ 前端 5173 + 桩 LLM（127.0.0.1:18090，`b0-stub-chat`）+ 桩 RSS（127.0.0.1:18091）起服，按域走查 14 条链路，每条「API 形状 × 视图渲染 × 控制台报错」三录。
- **走通（实测状态码 + 截图）**：注册/登录/刷新/登出；空间创建·切换·成员邀请·成员列表·审计页；KB 创建·上传·摄取（docreader 解析→分块→嵌入→索引，处理时间线四段）·预览·删除；检索（关键词/向量/混合，返回命中）；对话 SSE（`agent_query → query_understand → knowledge_search → references → answer 流 → complete`）；wiki 浏览页与 tab 呈现；datasource（RSS 桩）创建·同步（2 条入库）·资源树·日志·删除；im 渠道 CRUD（创建/总览不含凭据/toggle/更新/删除 204）；vectorstore·storage·parser 设置页；收藏（星标 + `/user/favorites`）；技能目录/智能体页；模型管理；系统运行时·审计·全局设置（临时提权 smokeuser 验证后未回退，dev 数据）。
- **修 15 处断点（前端 12 + 后端 2 + 环境 1）**：
  - **前端 A 类·裸体未适配（8 处）**：服务端 §2.1 早改成裸对象/裸数组，前端 API 层仍 `as ListXxxResponse` 强转 → 消费端 `data` 恒 `undefined`。命中：system KV 四件（`prompt-templates`/`parser-engine-config`/`storage-engine-config`/`retrieval-config` 的读与写）、tenant `invitations` 五个入口、`chat-history`、`web-search-provider`、`api/knowledge-base` 的 `vector_store_*` → `id/name/engineType` 注释口径。**实测症状**：成员管理页整块弹「U1 参数不可解析」红条（`data.invitations` 为 undefined，邀请列表也不渲染）；修后成员表 + 待接受邀请正常。
  - **前端 B 类·字段读取漂移（视图层读 snake，服务端已 camelCase，共 4 处族）**：① wiki stats 四键（`pending_issues/pending_tasks/is_active/pages_by_type`）→ wiki 头部徽标与类型分桶恒空；② folderTree 计数键（`root_document_count` 等）+ 两个文档视图 + 测试夹具 → 文件夹计数恒 0（`vue-tsc` 报 17 错）；③ `KnowledgeBase.vue` 三处：`item_count`→`documentCount`（卡片计数恒 0）、`storageConfig.provider`→`defaultProvider` 与 KV 的 `default_provider/provider/storage_type`（双触发「尚未选择存储引擎」误报）、`resource_type/resource_id`→camel（收藏星标失效）；④ 其余同族：`KBInfoPopover`、`knowledge-processing-timeline`+`utils/knowledgeTrace`（含测试）、`UploadConfirmDialog`、`TagEditDialog`/`BatchTagDialog`、`AgentEditorModal`、`KnowledgeBaseEditorModal`、`api/auth` 的 `is_active`。**最重一例**：检索引擎设置（⌘K 面板）`embedding_top_k`→`embeddingTopK`——表单恒显默认 5，用户保存即被覆盖（静默改写用户配置，走查抓出）。
  - **后端 2**：⑩ `KnowledgeProcessWorker.planFinalizing` wiki 子任务入队前先校验合成模型可解析（原先无模型也占槽入队 → ingest 重试 11 次后死信、**文档永远 finalizing**，实测复现）；⑪ `StartupTaskRecovery` Lite 模式不再排除「wiki 独槽」finalizing 行（Go 原文「启动后能重建触发器」在单机形态不成立——实测重启后无人触发，卡片永远「优化中」；重启后该行已实际复位为 failed 可重试）。
  - **环境 1**：`.env` 补 `DOCREADER_ADDR=localhost:50051`（连接态判据是「该 env 是否为空」而非探活，缺失时 `/system/parser-engines` 报 `connected:false`、内置引擎显示「不可用」、KB 页提示「暂无可解析引擎」，而解析其实正常）。
- **登记 5 项残留**：① **B3b 升格为真实缺陷（B3b′）**——KB 更新请求 `wiki_config` 内层键是 snake（前端写）而 Java 值类型 `wiki.domain.WikiConfig` 读 camelCase（无线名注解）→ **界面选的 wiki 合成模型被静默忽略**（实测：写入 `synthesis_model_id` 后 ingest 仍报 `missing_synthesis_model`）；修需一次落库键迁移 + 前端 payload + 读回三处同批。**（2026-10-02 同会话已执行：见本页 ✅ B3b 记录）**② wiki op 永久失败时无人释放槽位（死信路径缺 `WikiFinalizePort.finalizeWikiSubtask` 调用）——⑩ 只挡「无模型」这一新发生，运行期其它永久失败仍会搁浅。③ 孤儿 wiki op 不重放（Lite 重启后持久化 op 不再触发；⑪ 让文档不卡，但该文 wiki 内容要等下一次 KB 触发补生成）。④ `views/settings/StorageEngineSettings.vue` 是孤儿组件（`Settings.vue` 用该别名 import 的实为 `StorageBackendSettings.vue`）→ 存储引擎 KV 表单 UI 不可达。⑤ 每次登录都会打一发 `POST /auth/auto-setup` → 403「auto-setup is only available in lite edition」（控制台噪音，会掩住真 403；前端若能从 `/auth/config` 拿到版本信号即可前置跳过）。
- **未走**：第三方 connector 真凭据面（飞书/Notion/GitLab 等）、embed 渠道公开面、mcp OAuth（无桩、属 §14.6 冻结面）。
- **闸门**：前端 `vue-tsc` 0 错 + `npm test` 690 绿；后端 `spotlessCheck` 绿 + config/knowledge/wiki/system/session/datasource 六域测试全绿；上述每处修复都有真实服务复验（成员表与邀请列表出现、横幅消失、wiki/图谱 tab 出现、`/system/parser-engines` 转 `connected:true`、新文档 `completed + pending 0`、旧卡死文档复位 failed）。
- **教训**：「api 类型文件对齐 ≠ 消费端对齐」在本轮被证伪到第 9 例，且**类型断言把漂移全藏住**——凡是 `get<T>()` 泛型断言过的响应，消费端读错键 TS 一声不吭；这类断点只能靠「真数据 × 真渲染」走查兜底（VII 复查两批的静态闸门盲区判断成立）。

**✅ B3b（2026-10-02，由 B0 走查升格）**：知识库配置 jsonb 键名统一到 Java 字段名（camelCase）——前端 payload / 服务端读取器 / 落库列三方咬合面同批对齐。
- **修前真相**（走查实测 + 代码核查）：同一批配置列的键名分裂成三种——① `wiki_config` 读端是 camel（`wiki.domain.WikiConfig` 无线名注解：`synthesisModelId/maxPagesPerIngest/...`）而前端写 snake → **界面选的 wiki 合成模型与全部 wiki 调参被静默忽略**（实测写入 `synthesis_model_id` 后 ingest 仍报 `missing_synthesis_model`）；② `faq_config`/`question_generation_config` 的**同一列有两个读端**：`ChunkQuestionService`/`FaqIndexRows` 读 snake，而 `InitializationConfigService` 读 camel（`questionCount`/`customInstructions`）→ 编辑器加载配置时把已存的问题数显示成 0、**保存即写回 0（静默数据丢失）**；③ 更新路径的 `config.*` 外层 dispatch 键是 snake（`faq_config/wiki_config/auto_tag_config/indexing_strategy`）而创建面是 camel → 编辑态保存的索引开关（`vector_enabled` 等）落库后服务端读不到（`KnowledgeBaseIndexingStrategy` 读 camel）。
- **服务端（7 文件）**：读取器统一 camel——`KnowledgeProcessWorker`/`ChunkQuestionService`/`QuestionGenerationService`（`question_count`→`questionCount`、`custom_instructions`→`customInstructions`）、`FaqIndexRows`/`FaqChunkCodec`（`index_mode`/`question_index_mode`→camel）、`ModelService` 的「模型被谁引用」扫描（`image_processing_config`/`vlm_config`/`asr_config` 的 `model_id` 与 `wiki_config` 的 `synthesis_model_id`→camel）；`KnowledgeBaseService.applyUpdateConfig` 的 dispatch 键改 camel（与 `CreateKnowledgeBaseRequest` 同名同形）。**未动**：`ChunkQuestionService` 里的 `{{question_count}}` 是提示词占位符不是配置键。
- **前端（7 文件）**：创建/更新 payload 内层键（`wikiConfig` 五键、`faqConfig` 两键、`questionGenerationConfig` 两键、`autoTagConfig` 三键、`extractConfig.customInstructions`）+ 更新外层 dispatch 键改 camel；读取点（编辑器 `loadKBData`、上传确认框 `initFromKbInfo`、`Input-field` 能力回退、`KnowledgeBaseList` 内联类型）同步；`api/knowledge-base` 创建/更新 DTO 改 camel。**顺带修死读**：`Input-field.vue` 的 `s.vector_enabled`/`s.keyword_enabled` 在 camel 对象上永远取不到（能力回退恒 false）。
- **迁移**：新增 `migrations/versioned/V2__kb_config_keys_camel.sql`——递归键改名（含数组内对象，如 `parser_engine_rules[].file_types`）覆盖 12 个配置列，幂等（已 camel 的键不在映射表）；并把 `chunking_config`/`image_processing_config` 的**列默认值**改 camel（默认值也是新行的落库内容）。dev 库 8 行已迁，Flyway 启动 `Successfully applied 1 migration ... now at version v2`。
- **测试**：`FaqContractTest`（6 例）+ `WikiPageServiceTest` + `KnowledgeBaseEnsureDefaultsTest` 的种子数据改 camel——FAQ 那 6 例红是本次唯一既有断言冲突，方向正确（种子就是旧 snake 写法）。`WikiDomainTest` 的历史行容忍用例保留 snake（它断言"未知键被忽略"，仍是有效形状）。
- **连带修掉 2 处存量断点（B3b 验证时打开编辑器才暴露）**：① `KnowledgeBaseEditorModal.loadKBData` 读了 `kbInfo.data`，而 `GET /knowledge-bases/{id}` 是裸资源（§2.1）——**知识库设置/编辑弹窗对每个知识库都恒抛「知识库不存在」**（其余 5 个消费点都按裸体读，只有这一处漏改）；② 同处 `(kb as any).tenant_id` → `tenantId`（漂移使 `kbTenantId` 恒 0 → `canViewActivity` 恒 false，**编辑弹窗里的活动面板对所有人不可见**）。两条都实测复现/实测修复：修前点齿轮弹「加载知识库数据失败」，修后弹窗正常打开且回显存量参数（问题数 5、指令「用中文提问」、提取粒度「详细」、标签上限 6）。
- **验证**：前端 `vue-tsc` 0 错 + `npm test` 690 绿；后端 `spotlessCheck` 绿 + **全量测试绿**；真机冒烟——camel 建库回读一致、`/initialization/config/{id}` 的问题数由 0 变 5、PUT 用 camel dispatch 后库内三列全 camel、上传文档后 wiki ingest 由 `missing_synthesis_model` 变 `status=success` 且 `tunables(batch=5,map_par=10,reduce_par=10,max_inflight=4)` 生效、问题生成任务用上了 KB 选的模型与参数（失败仅因桩 LLM 返回固定文案、非 JSON）、编辑器 UI 回显一致（截图）。
- **登记（本批未动）**：① `knowledges.metadata.process_overrides`（每文件上传覆盖配置）键名仍 snake，且全服务端**无任何读取点**——该功能写了不用（前端上传框照写、时间线照读，服务端忽略）；② agent 域 `custom_agents.config` jsonb 内层键仍 snake（读写两端一致，属 §11.2 边界）；③ `ModelService` 的绑定标签值（`vlm_model` 等）是线格式字符串值，未动。

**🚧 B6 批 1（2026-10-02，storage 装配）**：`storage/service/DefaultStorageBackendProvisioner` 单文件 **46 处 `System.getenv` 清零**（占全仓三分之一）——「env 快照 → 存储后端实体/config JSON → 落库」的手写 switch 收敛为 `@ConfigurationProperties` 绑定的 provider 环境变量族。
- **新增** `storage/config/StorageProviderEnv.java`：8 个记录（`StorageType`/`Local`/`Minio`/`Cos`/`Tos`/`S3`/`Oss`/`Obs`）+ 公共接口 `ProviderEnvFamily{provider(), writeConfig(ObjectNode)}`；装配器改为 `Map<String, ProviderEnvFamily>` 查表（未知 provider 仍返回 null）。`RagAgentApplication` 的 `@ConfigurationPropertiesScan` 名单加 `com.ragagent.storage.config`（**域内配置类不进 `config/` 装配层**——域反向依赖装配层是本仓的既定禁线，见该类注释）。
- **契约保持**：env 变量名一个没改（`MINIO_ACCESS_KEY_ID`→`minio.access-key-id` 走 Spring 松散绑定，部署侧 .env 原样）；字段一律 `String` 而非 `Boolean`/数字——保留 Go 的宽容语义（`S3_USE_SSL` 只在恰为 "false" 时为假、`MINIO_USE_SSL` 只在恰为 "true" 时为真，写错的字面量不该让启动失败）；落库键名与省略规则（空串/假值整键省略）、键序都与原 switch 逐调用一致。
- **验证**：新增 `DefaultStorageBackendProvisionerTest`（测试属性代替 env，钉子断言 s3 族全键 + `use_ssl` 缺省真 + `force_path_style`）；真机三分支冒烟——`STORAGE_TYPE=s3`（含缺省 use_ssl=true）、`STORAGE_TYPE=oss`（临时桶 → `use_temp_bucket`+`temp_*`）、缺省（`System LOCAL` + `{}`）三条落库结果与改前逐键一致；`spotlessCheck` 绿 + **后端全量测试绿**。
- **踩坑（记给下一批）**：多次 `export A=B && nohup gradlew bootRun &` 后，**shell 的 export 会粘到后续命令**，导致「改了 env 重启却没变化」的假象；换变量族冒烟前先 `unset`，并核 `env | grep` 而不是只看日志。（另：bootRun 会 fork JVM，`pkill -f server:bootRun` 可能只杀 wrapper——要 `pkill -f RagAgentApplication` + 核 8083 端口。）
- **余量（105 处，代码内 100）**：retrieval 11（`RETRIEVE_DRIVER`×7 + `RetrievalEngineWiringConfig`/引擎仓的 `ELASTICSEARCH_*`/`QDRANT_*` 等现场建驱动）、storage 余 9（`LOCAL_STORAGE_BASE_DIR`/`STORAGE_TYPE`/`SYSTEM_AES_KEY` 的**静态工具方法**读点，转 bean 会牵动调用方，需先定静态→bean 的过渡口径）、common 7（`SSRF_WHITELIST*`/`JWT_SECRET`/`WEKNORA_LANGUAGE`）、knowledge 5（`BATCH_EMBED_SIZE`/`DOCREADER_ADDR`）、auth 5（`WEKNORA_INVITATION_TTL` 等）、system 4（`GIN_MODE`——**Go 框架名残留**，值得先判定是否还有意义）、tracing/langfuse 12（`LangfuseConfig` 单文件）。

**🚧 B6 批 2（2026-10-02，langfuse）**：`tracing/langfuse` 12 处清零（全仓 149→93，代码内 88）。
- 新增 `tracing/langfuse/LangfuseEnvProperties`（前缀 `langfuse`，12 个 **String** 字段）；`LangfuseConfig.loadFromEnv()` → `fromEnv(LangfuseEnvProperties)`（默认值与解析规则逐条照抄：Go 时长串、十种真值写法、采样率越界忽略、0 采样率视为整体关闭）；`LangfuseWiring` 改构造注入；扫描名单加 `com.ragagent.tracing.langfuse`。
- **为什么字段不用数字/布尔**：Go 的语义是「非法字面量静默回落默认值」，绑类型会把「写错一个字母」升级成启动失败——宽容语义优先。
- 验证：真机冒烟 `LANGFUSE_*` 起服 → `[Langfuse] enabled host=https://langfuse.example.com flush_at=20 flush_interval=90000ms sample_rate=0.5`（`1m30s`→90000ms ✓、采样率 ✓、启用判据=有 keys ✓）；`spotlessCheck` 绿 + 全量测试绿。（`LangfuseRegistry.init` 的这行启用日志是整条链路的可观测量，后续 langfuse 相关批次继续用它做冒烟断言。）
- **踩坑**：`export LANGFUSE_* && nohup gradlew bootRun &` 后必须 `unset`——shell 变量会粘到后续命令，否则下一批冒烟会被上一批的 env 污染（本批已按此收尾）。

**B6 余量口径（下一批动手前先定，2026-10-02 侦察结论）**：剩 88 处代码内读取，按上下文分两类——
- **A 类·bean 上下文（直接注入属性即可）**：`retrieval` 11（`RETRIEVE_DRIVER`×7 + `RetrievalEngineWiringConfig` 与各引擎仓的 `ELASTICSEARCH_*`/`QDRANT_*`/`MILVUS_*`/`WEAVIATE_*`/`TENCENT_VECTORDB_*` 现场建驱动）、`system` 13（`SystemSettingService` 6 / `SystemController` 4 / `SystemInfoService` 2 / `DeploymentCapabilitiesHolder` 1）、`datasource` 4（`DocxFetcher`）、`knowledge` 7（`KnowledgeBaseService` 3 / `HousekeepingService` 2 / `DocReaderClient` 2）、`model` 2、`vectorstore` 3、`initialization` 2、`session` 2、`mcp` 1、`embed` 1。
- **B 类·静态/构造上下文（先定过渡口径）**：`common` 7（`CryptoService`/`SsrfGuard`/`WikiLanguageSupport`/`UploadLimits`/`StorageAllowList`/`Gate`/`GateOptions` 全是静态方法，SSRF 那处在静态初始化块）、`storage` 余 9（`StoragePaths`/`support/Mode`/`support/FileServiceResolver` 静态工具 + 动态 key 助手）、跨域单值 9（`JWT_SECRET`×2 在**无参构造器**里、`BATCH_EMBED_SIZE`×3 静态、`WEKNORA_LANGUAGE`×3 静态、`GIN_MODE`×1）。
  **建议口径**：① 静态工具族（批大小/语言/SSRF/白名单）用「`@ConfigurationProperties` + 一个 `@Component` 启动时写入的静态快照持有者」——这几个都是启动期语义（进程内不变），成立，但持有者要写清「只允许启动期写入」；② `JwtService`/`OidcStateCodec` 这类无参构造器改构造注入（先核 `new Xxx()` 调用点）；③ `GIN_MODE` 先判定语义：Go 框架名残留，Java 侧真正问的是「是否生产部署」——建议改 `WEKNORA_DEPLOYMENT_MODE` 或复用既有部署信号，属对外配置变更，**需用户拍板**。




**🚧 B6 批 3（2026-10-02，检索驱动 + 向量库 env 查找面）**：`RETRIEVE_DRIVER` 的 7 个读取点清零（全仓 93→86，代码内 81）。
- 新增 `common/retrieval/RetrievalDriverProperties`（前缀 `retrieve`；只承载**原始串**——各读点缺省语义不同：知识库 `postgres`、系统信息页「未配置」、有效引擎空集＝检索全关、env 店空列表）；扫描名单加 `com.ragagent.common.retrieval`。
- 读取点改造：`RetrievalEngineWiringConfig`（@Bean 方法参数注入，保留 Go 的「不 trim、精确匹配」）、`SystemInfoService`（字段注入）、`KnowledgeBaseService`（构造器字段）、`VectorStoreConfigService`（构造器 + 新增 `findEnvStore(id)`）、`VectorStoreController`（2 处改调服务）、`EffectiveEngines.defaults()/of()` 改纯函数（驱动由调用方注入）、`HybridSearchService` → `HybridStoreGroupOps` 传递驱动。
- 新增 `config/EnvLookupWiring`：`EnvVectorStores.EnvLookup` bean 由 `Environment::getProperty` 支撑——键名不变（`OPENSEARCH_ADDR` 等照样解析，系统环境变量本就是 Environment 的一个 property source），但可被测试属性/命令行覆盖；`EnvVectorStores` 原本就是「纯函数 + 注入查找面」形状，本批只换查找面的来源。
- 测试：`EffectiveEnginesTest` 改传固定驱动串（不再依赖进程 env）；`HybridSearchServiceStoreGroupTest` 传 `null` 对齐改前「env 未配置 → 有效引擎为空」语义——**第一版写成 `"postgres"` 立刻红了一条**，这条正好提醒「驱动串就是引擎总开关」：测的语义变了，不是实现错了。
- 验证：真机冒烟（`RETRIEVE_DRIVER=postgres` 起服）——`/system/info` 报 `vectorStoreEngine=postgres`、向量库列表含 `__env_postgres__`、`GET /vector-stores/__env_postgres__` 200（走 Environment 查找面）、`POST .../test` 200、hybrid-search 200；`spotlessCheck` 绿 + 全量测试绿。

**🚧 B6 批 4（2026-10-02，系统/部署面 + `GIN_MODE` 改名）**：15 处清零（全仓 86→75，代码内 68）——`system` 域代码内 getenv 归零。
- 新增 `common/deployment/DeploymentProperties`（前缀 `weknora`）：`WEKNORA_EDITION` → `weknora.edition`（缺省 `standard`，capabilities 端点原样下发）+ `WEKNORA_DEPLOYMENT_MODE` → `weknora.deployment.mode`。**`WEKNORA_DEPLOYMENT_MODE` 取代 Go 时代的 `GIN_MODE`**（Gin 是 Go 的 Web 框架名，该变量在本仓只剩「是否生产部署」一个用途——用户已拍板可改名）：`production` 判生产、**兼容历史字面量 `release`**、其余（含未设）按非生产。⚠️ **对外配置变更**：部署侧若有 `GIN_MODE=release` 需改为 `WEKNORA_DEPLOYMENT_MODE=production`（本仓 .env 未设该变量，dev 无影响）；扫描名单加 `com.ragagent.common.deployment`。
- 读取点改造：`SystemSettingService`（6 处：三层解析的 ENV 层）、`SystemController`（4 处：`envAll`/`orEnv`/docreader addr+transport）、`SystemInfoService`（1 处：存储 env 可用性探测）改注入 Spring `Environment`——键名仍由调用方给，`Environment.getProperty` 与 `System.getenv` 等价（系统环境变量本就是 Environment 的一个 property source），但测试属性可覆盖；`envAll`/`orEnv`/`env` 由 static 改实例（调用方本就是实例方法）。`DeploymentCapabilitiesHolder`（版次）注入属性；`EmbedChannelService.validateAllowedOrigins` 由 static 改实例（生产判定取属性；两处调用点走门面回引 `ctrl.service.`）；`WebConfig` 组装 holder 时补传属性。
- 验证：真机冒烟两轮——① `WEKNORA_EDITION=lite WEKNORA_DEPLOYMENT_MODE=production` 起服：capabilities 报 `edition=lite`、建 embed 渠道带 `allowedOrigins:["*"]` 被拒（`wildcard origin '*' is not allowed in production`，400）；② 清环境后：`edition=standard`、同一请求 201 放行、`/system/parser-engines` 报 `connected:true addr=localhost:50051 transport=grpc`（走 Environment）。冒烟渠道已删；`spotlessCheck` 绿 + 全量测试绿。
- **教训**：这批判的 15 处里有 4 处读点藏在 **static 方法**里（`envAll`/`orEnv`/`env`/`validateAllowedOrigins`）——改注入时得连带把方法改实例并核调用方（`validateAllowedOrigins` 的两个调用点在门面协作类里，走 `ctrl.service.` 即可）；`WebConfig` 手工 `new` 的 holder 也要补传参数（Spring 装配点漏了就编译期报错，属好事）。

**🚧 B6 批 5（2026-10-02，知识域单值：docreader / 批大小 / 清理开关）**：6 处清零（全仓 75→69，代码内 62）。
- 新增 `knowledge/config/` 三个属性类：`DocReaderProperties`（前缀 `docreader`：`addr`+`transport`，含 `addrOrDefault()`＝`localhost:50051` / `addrConfigured()`＝**连接判据仍是「有没有显式配置」而非真探活** / `transportOrDefault()`＝小写归一缺省 `grpc`）、`BatchEmbedProperties`（前缀 `batch`：`embedSize` 只承载原始串）、`HousekeepingProperties`（前缀 `weknora`：`housekeeping.enabled` + `documentProcessTimeout`）；扫描名单加 `com.ragagent.knowledge.config`。
- 读取点：`DocReaderClient` 构造注入（2 处 env 读清零）、`SystemController` 的 `docReaderAddr()/docreaderTransport()` 改走属性（批 4 的 Environment 读法针对 DOCREADER_* 被属性取代）、`HousekeepingService` 的生产构造器注入属性（**两个无参 env 包装直接删除**——测试一直用的是「传原始串」的纯函数版，正好是纯函数化的收益）、`KnowledgeProcessWorker`（方法由 static 改实例读字段）、`ChunkVectorIndexer.embedBatchSize()` 改 `embedBatchSize(String raw)` + `FaqIndexWriter` 传值。
- 验证：真机冒烟三处可观测点——① `WEKNORA_HOUSEKEEPING_ENABLED=false` 起服 → 日志 `[Housekeeping] disabled via ...`；清环境后 → `[Housekeeping] started with 5-minute sweep`（开关双向都对）。② `/system/parser-engines` 报 `connected:true addr=localhost:50051 transport=grpc`。③ `BATCH_EMBED_SIZE=abc`（非法值）起服 → 上传新文档后 `parseStatus=failed` 且 `errorMessage=strconv.Atoi: parsing "abc": invalid syntax`——**证明值确实流到了向量化读点**。冒烟文档/渠道已删；`spotlessCheck` 绿 + 全量测试绿。
- **顺带发现（登记，未动）**：`embedding/BatchEmbedder` **生产无装配**（全仓只有它自己的测试 `EmbeddingWireTest` 构造它）→ 死类；其 `BATCH_EMBED_SIZE` 读点同属死代码（本批只改了两个在用的读点，没碰它）。删除与否留给 B7 类比批判定（删类要连带处理 `EmbedderPooler` 接口与测试）。

**🚧 B6 批 6（2026-10-02，common 静态工具族：语言 / 上传限额 / AES 密钥 / SSRF 白名单）**：5 处清零（全仓 69→64，代码内 57）。
- **确立「启动期快照」口径**（本批的正面产出）：能在 bean 里构造注入的就注入（本批 `StorageAllowList` 走注入）；只有**纯静态 / 手工 new 的工具类**才走快照——值是部署期确定的（语言默认值、上传限额、AES 密钥、SSRF 白名单），没有运行期变更需求。新 `config/RuntimeSnapshotWiring` 在启动期把值写入各工具类的 `installXxx`（文档写明「只允许装配层调用」）；`SsrfGuard` 保留既有 `reloadWhitelist` 运行期调谐通道与「白名单是进程级静态」的设计（多上下文互踩是 known-issues W5a 的既有教训）。新增属性类：`common/wiki/LanguageProperties`（`WEKNORA_LANGUAGE`）、`common/storage/UploadLimitProperties`（`MAX_FILE_SIZE_MB`）、`common/crypto/CryptoEnvProperties`（`SYSTEM_AES_KEY`）、`common/security/SsrfWhitelistProperties`（`SSRF_WHITELIST`/`_EXTRA`）、`common/storage/StorageAllowListProperties`（`STORAGE_ALLOW_LIST`）；扫描名单加 common 的四个包。
- **为什么不做构造注入**：`CryptoService`/`SsrfGuard` 在 MyBatis 类型处理器与出站工具类里是**手工 new** 的（不经 Spring，注入不进去）；`WikiLanguageSupport` 有 17 处静态调用点、`UploadLimits` 有 4 处跨域调用点——参数化会大面积牵动调用方。快照是这类值的正确承接（写清了边界：运行期要变的那是状态不是配置）。
- 验证：新增 `RuntimeSnapshotTest`（纯 POJO，四值的安装/回落钉子 + 静态还原防互踩）；真机三处冒烟——① `MAX_FILE_SIZE_MB=1` 起服后上传 2.9MB 文件被拒（*文件大小不能超过1MB*，正是快照算出的文案）；② RSS 数据源同步抓到 `items=2`（白名单快照放行 127.0.0.1）；③ 写数据源凭据落库为 `enc:v1:…` 且回读 200（AES 快照可用）。`spotlessCheck` 绿 + 全量测试绿。
- **登记（本批未动）**：① `Gate.pubsubChannel()` / `GateOptions.failCloseFromEnv()`（`WEKNORA_REDIS_NAMESPACE` / `WEKNORA_AGENT_TOOL_APPROVAL_FAIL_OPEN`）——`Gate` 是手工 new 的、`pubsubChannel()` 还被测试静态调用，改造要连带改测试；按本批口径做快照或走构造参数，独立小批即可。② **数据源同步的自动标签创建在后台线程恒失败**（日志 `failed to find/create auto-tag "x": knowledge base not found`，而同一个 KB 在请求上下文建标签是 200）——上下文丢失类缺陷，属 datasource 域，建议随该域批次查。③（小）`DELETE /datasource/{id}/credentials/credentials` 对「连接器不认定为密钥的残留字段」无效：走的是「无可清内容」分支，但仍把整份 config 重存一遍（副作用=重新加密）——garbage-in 边缘场景，登记不修。

**🚧 B6 批 7（2026-10-02，存储静态单值族 + JWT 密钥）**：8 处清零（全仓 64→57，代码内 50）。
- 新增属性类：`storage/config/LocalStorageEnvProperties`（`LOCAL_STORAGE_BASE_DIR`）、`storage/config/ResourceUrlModeProperties`（`RESOURCE_URL_MODE`）、`auth/config/JwtProperties`（`JWT_SECRET`）；扫描名单加 `com.ragagent.auth.config`。
- 新增 `storage/config/StorageRuntimeEnv`（存储域启动期快照：本地存储根 + 全局存储类型 + 资源 URL 模式），由 `RuntimeSnapshotWiring` 安装；`StoragePaths`（3 处）/`TenantFileServiceResolver`/`FileServiceResolver`/`Mode` 改读快照。**同一 env 不存两份**：存储签名键（要求 ≥16 字节）与 AES 密钥（要求恰 32 字节）共用批 6 的同一份快照（新增 `CryptoService.rawAesKey()` 只读口）。
- JWT：`JwtService`/`OidcStateCodec` 由无参构造器改**构造注入**（两者都是 `@Component` 且全仓无 `new Xxx()` 调用点）；「未配置时各自随机 32B 兜底」的行为逐字保留（因此未配 `JWT_SECRET` 的部署重启后旧令牌失效，属既有语义）。
- 验证：真机冒烟——① 登录拿令牌 + 刷新 200（`refreshToken` camelCase 契约）；② 存量文档 `preview`/`download` 200（本地存储根快照生效）；③ **受控跨重启验证**：`JWT_SECRET` 置 ≥32 字节 → 取令牌 → 同密钥重启 → 旧令牌仍 200（`jwt.secret` 绑定生效）。`Mode` 的语义由 `ModeTest` 覆盖（快照未装 → HANDLE）。`spotlessCheck` 绿 + 全量测试绿。
- **踩坑（值得记）**：把 `FileServiceResolver.localStorageBaseDir()` 判为死方法删掉后**编译立刻报错**——类内**非限定调用**（`localStorageBaseDir()`）不在 `grep "FileServiceResolver.localStorageBaseDir"` 的结果里。**死成员判定必须按方法名全仓扫（含类内调用）**，别按限定名扫。已恢复为快照改造版。
- **登记（本批未动）**：① 动态 key 助手三处（`StorageFileResolver.env` 约 15 个调用点、`FileServiceFactory.envOr`、`StorageBackendService.env`）——getenv 点只有 3 个，但服务约 30 个 provider 家族读取（`MINIO_*`/`S3_*`/`OSS_*`/`OBS_*`/`APP_EXTERNAL_URL`/`STORAGE_TYPE`/`LOCAL_STORAGE_PATH_PREFIX`…），宜与批 1 的 `StorageProviderEnv` 类型化记录**合流**成专门一批。② `JWT_SECRET` 短于 32 字节时 jjwt 到**登录时**才抛 `UnsupportedKeyException`（文案含糊、运维难定位）——建议启动期校验 + 明确文案，独立小批。③ `AuthController` 的 refresh javadoc 仍引用 Go 的 `json:"refresh_token"`，而绑定实现只认 camelCase `refreshToken`（§2.1 契约 ✓、前端已对齐）——文档陈旧，随触碰修正。

**🚧 B6 批 8（2026-10-02，存储 provider 环境族查找面）**：3 处清零（全仓 57→53，代码内 46；**storage 域归零**）。
- 新增 `storage/config/StorageEnvLookup`（存储域唯一的 provider 键查找面，启动期由 `RuntimeSnapshotWiring` 安装）：`StorageFileResolver.env`（约 15 个调用点）/`FileServiceFactory.envOr`（12 个）/`StorageBackendService.env`（4 个）三个助手改走它——裸 getenv 只有 3 处，但服务约 30 个键读取（`MINIO_*`/`S3_*`/`OSS_*`/`OBS_*`/`APP_EXTERNAL_URL`…）。
- 查找面语义：**键名原样优先**（`MINIO_ENDPOINT`——系统环境变量本就是 Environment 的一个 property source），**再回落属性风格**（`minio.endpoint`，可被属性源/命令行/`SPRING_APPLICATION_JSON` 覆盖）；未装配 → 视为未配置。
- **同一 env 只有一条读取路径**：这三个助手原先重复读的 `STORAGE_TYPE`（批 7 快照）与 `LOCAL_STORAGE_BASE_DIR`（`StoragePaths.localStorageBaseDir()`）一并归位；顺带删掉 `FileServiceFactory` 两个已无引用的公开常量（`DEFAULT_LOCAL_BASE_DIR`/`ENV_LOCAL_BASE_DIR`，全仓 0 引用）。
- 验证：以 minio **docker 模式**的连通性测试当探针（该分支的 endpoint 强制取自 env），本地起 TCP 桩，在「桩在听/不在听」两侧让结果分叉——

  | 场景 | `MINIO_*` 环境变量 | 属性源 `minio.endpoint` | 实测 |
  |---|---|---|---|
  | A | 127.0.0.1:19997（桩在听） | — | `connected:true` ✓ |
  | A₂ | 127.0.0.1:19997（无人听） | — | `连接被拒绝` ✓ |
  | B′ | 无 | 无 | `连接被拒绝` ✓ |
  | C‴ | 无 | 127.0.0.1:19997（桩在听） | `connected:true` ✓ |

  每次实验都 `ps eww <pid>` 自证目标进程环境（MINIO 计数 0）。`spotlessCheck` 绿 + 全量 4693 测试绿（0 失败）。
- **踩坑（两条都在「验证方法」层，值得固化）**：① **`execute_command` 的 shell 会复用**——某条命令里 `export MINIO_ENDPOINT=…` 泄漏进了后续命令，我先得到「无 env 也 connected:true」的**错误结论**（其实是上一轮的 export 还在），清理后才得出正确矩阵。**对照实验必须 `env -u` 显式清键，并在实验前打印目标进程环境自证**。② 同源的**假失败**：泄漏的 4 个 `MINIO_*` 让 `SystemContractTest` 两条 golden 断言（`sys-storage-status`/`sys-info`）失败，看着像回归；`env -u` 后全绿——**宿主 env 有值会改变这些 golden 的输出，失败先查 shell 泄漏**。③ `-Dspring-boot.run.arguments="--minio.endpoint=…"` 写在**任务名之后没生效**（据此先得了一次假失败，改用 `SPRING_APPLICATION_JSON` 才验成）——Gradle 传参姿势问题，与代码无关，记一笔免得再踩。
- **登记（本批未动）**：读侧投影与批 1 的类型化记录**仍是两套**——`StorageFileResolver.storageBackendFromEnvironment`（约 60 行 switch 逐键 `env(...)` 拼 JSON）与 `StorageProviderEnv.*.toStorageEngineConfig()` 形状重叠，宜合成一条投影；后者要动「静态读取点 → 注入 bean / 装记录快照」，是存储域最后一块结构性债务。另：**不经 Spring 装配的入口**（单元测试/未来 CLI）读这些键会得到「未配置」（装配面未装）——与本仓 CI 里这些 env 本来就未设置**等价**，全量测试已绿；但若将来出现脱离 Spring 的调用方，需显式安装查找面。

**🚧 B6 批 9（2026-10-02，检索域：引擎命名/开关/超时族）**：18 处清零（全仓 53→35，代码内 46→28；**retrieval 域归零**）。
- 新增 `retrieval/config/RetrievalEnvLookup`（检索域查找面）+ 共享查找函数 `config/EnvPropertyLookup`（存储查找面与检索查找面共用同一语义：键名原样优先 → 回落属性风格，避免两处 lambda 各自漂移）。
- **装配时机刻意与批 6/7 不同**：检索域引擎在**启动期构造**（`@Bean` 方法里造），故查找面装在 `RetrievalEngineWiringConfig` 的**构造器**里——构造器先于本类任何 `@Bean` 方法执行，必然早于任何引擎构造。若照批 6/7 放进通用快照装配类，bean 实例化顺序**不保证**，会**静默丢掉 env 里配的集合名**。这是本批新识别的一类风险，已写进两个查找面的类注释。
- 覆盖 14 处键读取：`EngineTypes.resolveIndexName`（ES/OpenSearch 共用）+ 各引擎 `resolveCollectionName`（Doris/Milvus/Qdrant/Weaviate/Sqlite/Tencent 三键）+ Doris 兼容模式（顺带把**同一键读两遍**合成一次）+ 多店检索超时 + VLM HTTP 超时。
- Neo4j 门（4 处）走**类型化属性**：新增 `retrieval/graph/Neo4jProperties`（`neo4j.enable/uri/username/password`），注入 `Neo4jGraphConfig` 的 `@Bean` 方法——启动期读点的正确形态（Spring 解依赖，无顺序问题）；`enable` 保留原始串，「不等于 true 即未启用」的大小写比较逐字不动。
- 验证（真机判别式）：

  | 实验 | 配置 | 实测 |
  |---|---|---|
  | sqlite 路径·env 风格 | `RETRIEVE_DRIVER=postgres,sqlite` + `SQLITE_PATH=/tmp/b0/b9-env.sqlite` | 该路径生成 53248 字节库文件 + 日志 `Register sqlite retrieve engine success` ✓ |
  | sqlite 路径·属性风格 | 无该 env，`SPRING_APPLICATION_JSON={"sqlite":{"path":"/tmp/b0/b9-prop.sqlite"}}` | 属性路径建库 ✓（点号回落在本域同样生效） |
  | Neo4j 启用 | `NEO4J_ENABLE=true` + 坏 URI `bolt://127.0.0.1:17687` | 重试日志带**该 URI**（attempt 1/30…）✓ |
  | Neo4j 未启用 | 不给 `NEO4J_*` | 进程 6652 重试行 0、健康 200 ✓ |

  `spotlessCheck` 绿 + 全量测试绿。
- **踩坑（第三条验证方法教训）**：B 组日志一度出现 18 条 Neo4j 重试，形似「没配也重试」的真回归——**实为 A 进程未杀干净**（`pkill` 后它仍在 30 次重试循环里），其输出继续写进了被 B 截断的**同一份日志文件**。日志行自带 pid，`grep -c '6652'/'6617'` 一验即明：B=0、A=21，控制组有效。教训：**多轮实验各用独立日志文件，且下一轮启动前确认端口已释放**（别只 sleep）；读日志下结论前先按 pid 归属。

**✅ B6 批 10（2026-10-02，收尾批——裸 `getenv` 代码内清零，B6 结项）**：28 处清零（代码内 46→**0**；余 7 处仅注释/文档提及）。
- 新机制：`config/AppEnvLookup`（应用级兜底查找面）+ `config/AppEnvLookupEnvironmentPostProcessor`——在**任何 bean 实例化之前**安装。理由：本批读点里有启动期就触发的（`StartupTaskRecovery.distributed()` 决定 Lite/分布式分支、`BuiltinModelsReconciler` 是启动 runner、API-Key 引导、Gate 的 pubsub 频道），`@Configuration` 顺序不保证（批 9 已踩过同类风险）。装的是**查找函数**而非值 → 不存在「装早了看不到后到的 property source」。
- **注册机制的坑（实测，重要）**：Boot **3.3 的 `EnvironmentPostProcessor` 仍由 `META-INF/spring.factories` 装载**；写成 `META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports`（那是 Initializer/Listener/AutoConfiguration 的机制）会**被静默忽略**——不报错、不执行、读点全部回落到「未配置」。已改回 `spring.factories` 并在文件里写明原因。
- 归属既有正典的读点（不新开路径）：`WEKNORA_LANGUAGE` ×2 → 批 6 `WikiLanguageSupport`（其一改用 `defaultLanguage()`，顺带与 `resolveLanguage` 的 trim 口径一致）；`LOCAL_STORAGE_BASE_DIR` → `StoragePaths.localStorageBaseDir()`（批 7）；`STORAGE_ALLOW_LIST` ×2 → `StorageEnvLookup`（批 8）；检索装配内的动态 key → `RetrievalEnvLookup`（批 9）。
- 其余 22 处（auth 的 OIDC/邀请/API-Key、Gate/GateOptions、StartupTaskRecovery、DocxFetcher、BatchEmbedder、initialization 三件、LlmTransport、OllamaService、OAuthStateStore、BuiltinModelsReconciler、TemporaryDocumentService、VectorStoreTypes …）统一走 `AppEnvLookup`。
- 验证（4 次起服，判别式）：

  | 起服 | 配置 | 实测 |
  |---|---|---|
  | 1 | 无（Lite） | 预置卡住文档复位 `failed` + 日志 `distributed=false` ✓ |
  | 2 | `SPRING_APPLICATION_JSON` 内置模型路径 + `STORAGE_ALLOW_LIST=local,minio` | `/storage-backends/types` = `["local","minio"]` ✓；内置模型日志仍是**默认路径** ✗ → **暴露注册缺失** |
  | 4 | 同上（改用 `spring.factories` 后） | 启动 runner 读到属性路径 `/tmp/b0/b10-models.yaml` ✓（属性风格 + 启动期 + 排序三者同证） |
  | 5 | `REDIS_ADDR=127.0.0.1:6379` | 卡住文档**不被复位**（保持 `processing`）= 分布式分支 ✓（与起服 1 反向对照） |

  `spotlessCheck` 绿 + 全量 4693 测试绿。
- **踩坑（两条，如实记录）**：① 上述 `.imports` **静默失效**——**注册类扩展点必须实测行为，别只信「文件已就位」**。② 我一度把「起服 5 没有 `distributed=true` 日志」判成「日志文案写死误导」并改了文案——**实为该类注释明确写的设计**（分布式模式刻意不复位知识/摘要行：`resetStuckKnowledge` 首行 `if (distributed) return`，该日志仅 Lite 可达），**已回退**。教训：**拿日志/行为当证据前先读被验对象的类注释**（与 §15.2「验收前先读锚点」同源）。
- 另：本批内联全限定名被 `KnowledgeCodeConventionsTest` 拦下 1 例（knowledge 模块禁代码体内联 FQN）→ 全部改导入（仓库口径：FQN 归零）。

**B6 结项小结（149 → 0）**：十批分别是 storage 装配 46 / langfuse 12 / 检索驱动 7 / 系统部署面 15 / 知识域单值 6 / common 静态族 5 / 存储单值+JWT 8 / 存储 provider 查找面 3 / 检索引擎族 18 / 收尾 28（分批口径含同批内追加项，故与各行数字之和不必相等）。四种落点形态已定型：**① `@ConfigurationProperties`（bean 可注入时）；② 域查找面 holder（键名运行期决定、静态读点）；③ 启动期值快照（纯静态工具 + 单一值）；④ `EnvironmentPostProcessor` 装的全局查找面（启动期就触发的零散静态读点）**。取用判据写在各自类的注释里。

**✅ B12（2026-10-02，B0 残留批 1：wiki 槽位与孤儿任务）**
- **② 任务级死信无人释放槽位**（原登记）→ `InProcessWikiIngestTaskQueue.archive(...)` 是**任务级**终态（处理器未接线 / 载荷损坏 / 批次入口反复抛错，根本没跑到结算），原先只写死信不释放槽位 → 对应文档**永久停在「优化中」**。现于归档前调用新增的 `WikiIngestService.releaseSlotsForAbandonedTask(kbId)`（内部走消费面解码拿 ingest op，逐个 `finalizeWikiSubtask`）。**op 刻意不删**：留待下次触发正常处理，wiki 内容不丢。批内失败预算耗尽那条（`requeueFailedOps`）早有释放，本次补齐队列侧。
- **③ 孤儿 wiki op 不重放**（原登记）→ 新增 `WikiPendingOpReplayer`（`ApplicationReadyEvent`，**仅 Lite** 判据 `isLiteMode()`）：扫出仍挂在 `task_pending_ops` 的 KB（新增仓储方法 `distinctIngestScopeIds`，**单列查询**避开 H2/PG 列名大小写差异），逐个走**与请求侧完全相同**的 `enqueueWikiIngestTrigger`（自带 30s 防抖 + TaskID 合并）。重启后该文 wiki 内容不再要等下一次 KB 触发。分布式形态跳过（队列自身会重投）。
- **验证**：真机判别式，各自独立日志文件——**A**（库里有 1 条遗留孤儿 op）重启 → `wiki orphan replay: re-triggered 1 KB(s) holding pending ingest ops` ✓；**B**（删掉该 op 后重启）→ 重放行数 **0** ✓；健康 200 ✓。② 走单测：新增两条（任务级死信释放槽位 / finalize 类任务不释放），`InProcessWikiIngestTaskQueueTest` 连跑三次稳定 ✓。
- **顺手修掉一个审计盲区**：`TaskPendingOpsRepository.java` 里藏着 **2 个裸 NUL 字节**（把 Java 的 `\0` 哨兵直接写成了字节）→ **grep/ripgrep 会把整个文件判为二进制并跳过**（本轮我自己的审计就被绕过去一次）。已改为 `\0` 八进制转义（语义逐字不变）；架构规则新增 **R5：源文件不得含裸 NUL**（另修 `MemoryTextTest` 同类 1 处）。闸门：spotlessCheck 绿 + 全量 **4700** 测试绿。
- **测试自身的教训**（同 §15.2 第 6 条同源）：② 的新单测首版用「等 `activeTaskIdCount()==0`」当屏障，但任务**无 TaskID 时该计数恒 0** → 循环立即退出、在归档前就断言（单跑靠时序侥幸绿、全量跑才暴露）。**时序型断言别手写等待循环**——用框架的超时断言；屏障要选「任务真正结束时才变」的量。

**✅ B13（2026-10-02，B0 残留批 2）**
- **④ 孤儿存储组件**：`Settings.vue` 用 `StorageEngineSettings` 这个**名字** import 了 `StorageBackendSettings.vue`，真正的 `StorageEngineSettings.vue`（1687 行）从未被渲染。**判定**：该页是 Go 期 **KV 引擎配置**（默认引擎 + 各家凭据），已被「存储后端行」（凭据 + 连通性测试 + 设默认 + 多实例）完整取代 → **删除孤儿**并给 import 正名；KV 的**读**面保留（编辑器资源 store 仍在用），**写**面（`updateStorageEngineConfig`，唯一消费者是孤儿页）随之退役——读写命运不同，原因写在 `api/system/index.ts` 注释里。
- **⑤ 登录 403 噪音**：`/auth/config` 增 `edition` 版本信号（`weknora.system.edition`，缺省 `standard`）；登录页先取该信号，非 lite 不再盲打 `/auth/auto-setup`（该端点非 lite 恒 403，噪音会掩盖真 403）。三条契约金片 `reg-config*.json` 同批重录（语义比较器 ✓，用例侧逐字节断言的那三条按新键集更新）。
- **`process_overrides` 判定**：字段自 Go 期前端原样带过来，Java 侧**从未移植**——服务端既不写 `metadata.process_overrides` 也不读，前端也没把它发进上传/重解析请求 ⇒ 当前有效行为恒为「KB 默认」。结论：**移植缺口，不是死代码**；本批只判定 + 在两处读取点写清缺口与指向，**不实现**。
  - **补充事实（复核后修正）**：前端**确实**会把逐文档参数发出去——上传/重解析走 `processConfig`/二进制表单；是**服务端收下后静默丢弃**（`KnowledgeController` 类注释原文「参数被接收但静默丢弃（管线只读 KB 级配置）」）。前端 `KnowledgeProcessOverrides` 共 **8 类**参数（解析规则/分块/多模态/VLM/ASR/问题生成/图谱/抽取）。
  - **决策（2026-10-02 用户定调）：保留现状——不删、界面控件不动、暂不实现，未来补后端。** 已知并接受的后果：界面上的逐文档参数**不生效且无提示**（比"没有该功能"更易损信任，故未来补时应优先消解这一点）。
  - **未来补的实现草图（先读再动，别一次上 8 类）**：① 先只做**分块 + 解析引擎**两家，其余 6 类留白并标注"暂未启用"；② 链路＝请求体 `processConfig` → 落 `knowledges.metadata.process_overrides`（**camelCase**，与 B3b 的 KB 配置口径一致）→ 处理管线按「文档级优先、KB 级兜底」读取；③ 配三段契约测试（请求 → 落库 → 读回）并让时间线展示对齐（前端已会读）。
- **验证**：前端 `vue-tsc` 0 错 + `npm test` **690** 绿；后端 `spotlessCheck` 绿 + 全量 **4700** 绿（含三条重录后的 auth 契约用例）。**真机 UI 冒烟**（Playwright，全新浏览器上下文）：登录页 **0 次** `/auth/auto-setup` 请求、0 API 错误、0 控制台错误；存储设置页正常渲染后端管理页（截图：System LOCAL 默认 + 添加存储实例）。`/auth/config` 实测 `{"complexPasswordEnabled":false,"registrationMode":"self_serve","edition":"standard"}` ✓。
- **顺手清了两处陈旧注释**（引用已删组件的样式注释）。

**✅ B5（2026-10-02，lf_* 载具嵌套化：判定 + 执行）**
- **判定（结论：做）**：`TracingContext`（`common/context`，共享记录）早已存在，但四个域的队列载荷各自把它的 5 个组件**平铺**了一份（4×5＝20 个字段声明 + 25 行空值归一）。根因是 Go 的匿名字段嵌入在 JSON 里自动摊平，而 Java record 无法自动摊平（各载荷 javadoc 记着这条限制）。判定依据：这些载荷**只在进程内队列流动、不落库、不出响应** → 无外部消费面 → 可以改形；收益是去掉 20 处重复，以后加追踪字段只改一处。
- **执行**：五个载荷（wiki / memory / datasource / knowledge 的 extractChunk + questionBatch）改为**嵌套 `tracing` 键**；`withTracing(...)` 工厂签名不变（**调用点零改动**）；`tracing()` 访问器保持"永不返回 null"。
- **两域两种形状（照各自模块约定，不再一刀切）**：wiki/memory/datasource 用 `Include.CUSTOM + TracingContext.EmptyOmitFilter`（空载体**整键省略** → 未启用追踪时负载字节与平铺期**完全一致**）；**知识域两个载荷不加任何注解**（该模块约定「字段一律显式输出、键名即字段名」，禁 `@JsonInclude`/`@JsonProperty`）→ 空载体输出 `"tracing":{}`。
- **踩坑（Jackson + record，值得记）**：record 的属性按**访问器**序列化——把访问器归一成 `EMPTY` 之后，字段上的 `@JsonInclude(NON_NULL)` 永远看不到 null，空载体被写成 `"tracing":{}`（本意是省略）。改用 `Include.CUSTOM + valueFilter`（按**值**判定）才拿到"空即省略"。**先写测试后实现**的探针当场抓到，否则会以为省略已成。
- **验证**：新增 `common/context/TracingCarrierNestingTest`（五载荷 × 非空回环 + 空载体形状两分支）；同步更新两处钉知识域形状的存量测试（`GraphChunkSelectorTest`/`QuestionBatchPlannerTest` 原先钉的是**平铺 camelCase** 键——与 wiki/memory/datasource 的 snake 键本就不一致，本次一并归位）。`spotlessCheck` 绿 + 全量 **4702** 绿。真机：新构建起服 + 上传文档 → `completed` / `pending 0`（链路无回归）；再经 B12 的孤儿重放入口**活体触发 wiki 载荷** → 队列解析 **0 错误**、批次跑到业务校验（`no synthesis model configured`，预期失败），证明嵌套载荷在真实队列里序列化/反序列化正确。
- **§14.6 冻结面更新**：`lf_*` 平铺键一项**已消解**（B5 执行完毕，改为嵌套 `tracing`）。

**✅ B9（2026-10-02，Go 锚点：政策 + 执行机制落地；清扫仍"随触碰"，不立专项）**
- **政策（§4 原定，本批不动）**：Go 锚点注释**随触碰清洗，先摘不变量信息再删锚点，不搞专项大扫除**；判据样板是 agent 域（479 处 / 186 文件一次随批清扫，§13.11/13.13：「真实不变量改中性陈述保留」）。故 B9 **没有"清完 6,157 处"这一说**——它是长尾卫生项。
- **本批补的是缺失的执行机制**：`scripts/check-go-anchors.py`（按**文件**计基线的棘轮：只许减不许增）+ `scripts/go-anchors.baseline.json` + CI `guards` job 一行。以后顺手写新锚点、或把旧锚点抄进新文件，**CI 直接红**；反之净减不设门槛（不制造一次性大扫除压力）。
- **口径刻意窄，避免误伤**（写进脚本头部）：
  - 统计：`对照 Go`（含 `对照Go`）与 `波 N`（移植波次编号，脱开当时分批语境即无意义）；
  - **不统计** `golden`（契约金片基建 `GoldenContract`/`golden(...)` 是本仓正式机制；knowledge 模块另有自己的扫描器禁它，那是该模块既定口径）与 `GORM`（`GORM 隐式行为清单` 是约定 §3 要求的**结构化段落**，属要保留的信息而非锚点）。
- **测量刷新（今日 main 域实测）**：`对照 Go` **3,886** 处 / 1,026 文件；`波 N` **113** 处 / 65 文件；合计 **3,999 处 / 1,029 文件**（即基线）。含 test 时 `对照 Go` 为 4,894 / 1,299（test 不进守卫：契约对比测试引用 Go 行为是正当的）。供后续对比 §4 的 6,157（2026-09-30 口径，含 GORM 段与 test）。
- **验证（按 §15.2 第 6 条：守卫必须可证伪）**：造两个探针文件各写一行 `对照 Go` → 脚本**退出码 1** 并逐文件列出 `0 → 1` ✓；删除探针后复绿（3,999 / 1,029 与基线一致，退出码 0）✓。CI 片段已核对（`guards` job 内两步并列，缩进一致）。
- **遗留**：main 的 3,999 处仍靠随触碰下降（本仓各批已在不自觉执行：B0/B12/B13 期间我写的注释都用"Go 期是…"这类**中性陈述**，不新增锚点）。

**✅ B11 判定（2026-10-02，Gradle 多模块）——结论：不做，正式搁置**
- **立项目的核对**：§5 阶段 4 把「Gradle 多模块 + ArchUnit 边界规则进 CI」**并列**为"边界固化"手段（L127）。其中 ArchUnit 部分已由 **B10** 交付（四条代码级规则 + 包级棘轮脚本），**立项目的已达成**；多模块只剩"用构建系统再表达一遍同一边界"。
- **量化反驳构建收益**（本机实测）：冷编译 main+test（`--rerun-tasks`）**32 秒**；全量测试套件 **≈2 分 50 秒** ⇒ 构建时间的**约 85% 在测试**，编译只占小头。多模块能带来的只是编译期的**有限并行**（对冷构建最多省十几秒级），而日常增量构建本来就是秒级 ⇒ 收益接近零。
- **顺带发现（比拆模块更对症的加速抓手）**：仓库**没有 `gradle.properties`**（`org.gradle.parallel` / `caching` / `configuration-cache` 都没开）。真要提速应从这里入手 + 拆测试（成本中心），而不是拆模块。
- **成本**：按域拆 ~12 个模块需引入共享 test-fixtures 模块（1,366 个契约 fixture 跨域共用）、app 装配模块（单 boot jar），并让**之后每一个批次**都多付一次"这属于哪个模块"的协调税；属数天机械迁移 + 低但真实的破坏风险，换来的主要是**审美上的边界**。
- **可证伪的重访触发条件**（写死，免得反复讨论）：① 出现 ArchUnit/脚本**没能拦住**的新边界违例；② 冷编译超过 ~2 分钟；③ 需要把某个子集作为**库**对外复用/单独发布。三者任一出现即重开此判定。
- **替代动作（本次不做，登记）**：① 若要构建提速 → 开 Gradle 并行/缓存 + 配置缓存，并按域拆测试任务；② 边界表达 → 继续用 B10 的四条规则 + 包级棘轮扩展（新增一条规则的成本是分钟级）。

**🔎 存储读侧投影：判定 + 护栏（2026-10-02，未施工合并）**
- **复核修正了原登记口径**：不是"同一个形状写了两遍"，而是**两套词汇、各自有真实读者**——

  | 面 | 写的人 | 读的人 | 凭据键（minio 以外） | 省略规则 |
  |---|---|---|---|---|
  | **引擎面** | `StorageFileResolver.storageBackendFromEnvironment`（env 回落行） | 本类各 provider 分支的**自校验**（`textOr(x.get("access_key"))`；minio/cos 另有各自键） | `access_key`/`secret_key` | **恒写**（空串/false 也落键） |
  | **落库面** | `StorageProviderEnv.*.writeConfig`（`DefaultStorageBackendProvisioner` 用） | `storage_backends.config` → `toStorageEngineConfig`（`renameConfigKeys`/`decryptCredentials` 认两族） | `access_key_id`/`secret_access_key` | **omitempty**（空串/假值整键省略） |

- **结论**：**不能只改一套写侧**——合并必须**先统一读侧**（两族命名硬编码在 `renameConfigKeys`/`decryptCredentials` 与各 provider 自校验里），属**中等偏大**批次；本次只落**护栏**：新增 `storage/fileserve/StorageProjectionVocabularyTest`（3 用例，逐 provider 钉住两侧键集合与省略语义）。今后任何统一必须**同一提交改两侧并更新该测试**，杜绝「改 A 忘 B、云凭据静默读不到」。
- **待核（合并前置调查，已写进测试类注释）**：落库面 s3/oss/obs/tos 写 `access_key_id`，引擎面自校验读 `access_key`——行模式下 s3 恰好「两边都空 ⇒ `hasKey == hasSecret` ⇒ 通过」（**不报错**），实际 SDK 层能否取到凭据**未验证**；合并前先查 `providerBacked(...)` 下游对 s3 凭据键的读取口径。
- **踩坑（小，但方法论同源）**：词汇钉测第一版喂**空值**——引擎面是恒写、键集合可见 ✓，而落库面是 omitempty → **一个键都不写** ✗（钉出空集合，等于没钉）。改用**全开值**才钉住；过程中还实测出 `Cos` 会写 `app_id`、`Oss` 全开时写 `use_temp_bucket`，期望表按实测修正（**钉测试的期望必须以实测为准，不是以读码印象为准**）。
- 闸门：`spotlessCheck` 绿 + storage/system 域测试绿（新钉测 3 用例含在内）。

**🐞 B14 修复（2026-10-02，真 bug：非 minio 的「行配置」云凭据被静默丢弃）**
- **前置调查结论（原登记项已闭环）**：`storage_backends.config` 存的是**统一 camel**（`accessKeyId`/`secretAccessKey`，密文；见 `dto/StorageConfig.java` 与 `StorageBackendService.serializeConfig`）；`toStorageEngineConfig` 的 `renameConfigKeys` 却把它们**统一改写成 minio 形态** `access_key_id`/`secret_access_key`；而引擎面各段的 `@JsonProperty` 并不统一——`MinioEngineConfig` 认 `access_key_id`/`secret_access_key`，`CosEngineConfig` 认 `secret_id`/`secret_key`，**其余（s3/tos/oss/ks3/obs）认 `access_key`/`secret_key`**。Jackson 配了 `FAIL_ON_UNKNOWN_PROPERTIES=false` ⇒ **键被静默丢弃** ⇒ `S3CompatibleFileService.Config` 无凭据。
- **影响面**：**行配置的 s3 / tos / oss / ks3 / obs / cos 全部**（只有 minio 恰好命中）；且行校验是「缺一才报错」，两边都空时 `hasKey == hasSecret` **通过**——表现为「云存储配好了却莫名 403 / 匿名请求」，属最难定位的一类。
- **既有测试为何漏掉**：`ProviderWiringTest` 的 W5γ5.2 用例断言的是**引擎面节点**上的键（`s3.access_key_id`），与实现同错——**测在了错的层**（没断言"绑定到类型化配置后凭据还在"）。
- **修复**：`renameConfigKeys(copy, provider)` 按 provider 分族落凭据键（minio→`access_key_id`/`secret_access_key`；cos→`secret_id`/`secret_key`；其余→`access_key`/`secret_key`），并把 `access_key` 纳入 `decryptCredentials` 名单（改名先于解密，须覆盖新键名）。
- **守卫**：修正既有用例的 s3 断言 + 新增 `ProviderWiringTest#backendRowCredentialsBindToProviderSection`（**行 → 引擎面 → 类型化绑定**三段直断，覆盖 minio/s3/cos）。
- **红态证明**：临时还原旧行为 → 两条用例失败（新增用例报 `expected: <AK-s3> but was: <>`）→ 恢复修复 → 全量测试绿 + `spotlessCheck` 绿。
- 说明：未做真机云端点验证（环境无可用云端点）；判定证据＝上述三段端到端单测 + 红态复现。

**✅ B14 合并完成（2026-10-02，两套词汇收成「一面一源」）**
- **第二个静默 bug（同源）**：`StorageProviderEnv.*.writeConfig`（启动期供给器的唯一投影）输出的是 **snake** 键，而落库面的读者 `StorageBackendService.configOf/serializeConfig` 用 `dto/StorageConfig`（**camel**）+ `FAIL_ON_UNKNOWN_PROPERTIES=false` ⇒ 环境供给的默认后端行**读回来全是空配置**（UI 显示空白、`validateForProvider`/连通性测试拿到空值；对 s3 家族还与引擎面 bug 叠加）。既有 `DefaultStorageBackendProvisionerTest` 断言的正是那套 snake 中间产物——**又是「测在中间节点、与实现同错」**。
- **合并**：确立**一面一源**——① 落库面＝camel（`StorageProviderEnv.*.writeConfig` 改为输出 camel，与 `dto/StorageConfig` 同族）；② 引擎面（snake，各 provider 凭据命名还不统一）**只由** `StorageFileResolver.renameConfigKeys(provider)` 单点派生；③ 解析器的 env 回落行（`storageBackendFromEnvironment`）不再手写 switch，改为复用同一投影 + 该改名器（语义逐键核对过：省略规则的差异对两侧读者等价，凭证/布尔缺省同义）。构造器新增 `List<ProviderEnvFamily>` 注入，另留一个「只带 local 族」的便捷构造器供纯单测（与「未设 `STORAGE_TYPE`」同形，7 处既有测试构造点零改动）。
- **验证**：
  - 单测：`StorageProjectionVocabularyTest` 重写为「两面一源」契约钉（3 用例：落库面 camel 键集合 / 引擎面 snake 键集合（经统一次名器）/ **两面之差只在凭据命名**）；`DefaultStorageBackendProvisionerTest` 改 camel 并加**消费者层**断言（落库配置必须能绑进 `StorageConfig`）。
  - 真机：`STORAGE_TYPE=s3` + `S3_*` 起服 → `POST /api/v1/tenants` 建租户 9 触发供给器 → 直查库：`provider=s3 | source=env | {"region":…, "useSsl":true, "endpoint":…, "bucketName":…, "pathPrefix":"b14/", "accessKeyId":"AK-b14", "forcePathStyle":true, "secretAccessKey":"SK-b14"}` ——**camel 到位**（修复前这里会是 snake，读侧静默丢空）。
  - 全量测试绿 + `spotlessCheck` 绿；期间 `AttachmentContractTest.previewStreamsFileWithGoHeaders` 在全量负载下失败一次、单跑与复跑全量均绿（**判定为超时抖动**，非回归）。
- **登记（本批未动）**：供给器直接经 `repository.create` 写行，**绕过了 `serializeConfig` 的加密** ⇒ 环境供给行的凭据在库里是**明文**（`configOf` 的「无前缀原样」使其功能正常，但违背「私钥落库为密文」的设计）。小批可修（供给器路由到同一序列化口或注入 `CryptoService`）。
- **dev 库留痕（我的操作）**：租户 **9 `b14-probe-1015`**（含其供给的 `storage_backends` 行 `03e70033-d00c-44b9-bb56-6ac9c3092826`）为本批真机验证所建，**可删**。

**✅ B15（2026-10-02，供给行明文落库——登记项闭环）**
- **问题**：`DefaultStorageBackendProvisioner` 直接 `repository.create(backend, backend.getConfig().toString())`，**绕过** `serializeConfig` 的加密 ⇒ 环境供给行的云凭据以**明文**落库（功能正常，但违背「私钥落库即密文」的设计；`configOf` 的「无前缀原样」掩盖了它）。
- **修法（唯一读写口）**：新增 `storage/service/StorageConfigCodec`（{@code @Component}：`encode(JsonNode)` 加密落库 / `decode(JsonNode)` 严格解密，语义与既有两条方法逐字一致）。`StorageBackendService.configOf/serializeConfig` 与之**委托**（其只为此而存在的 `CryptoService` 依赖随之下线），供给器注入同一读写口。**至此"写行"只有一条路径**——这条链路上已出过三个同源缺陷（引擎面键分族、落库面词汇、以及本次绕过加密），唯一口是结构性收口。
- **验证（真机 A/B，同一台 dev、同一份 .env）**：
  - 修复前的行（租户 9，B14 建的）：`cred_encrypted = f`——`"accessKeyId": "AK-b14"`, `"secretAccessKey": "SK-b14"` **明文**；
  - 修复后的行（租户 10，`STORAGE_TYPE=s3` 建租户触发）：`cred_encrypted = t`——`"accessKeyId": "enc:v1:WBqzK__…"`, `"secretAccessKey": "enc:v1:HwyS5VB-…"` ✓；
  - 单测：`DefaultStorageBackendProvisionerTest` 加 AES 密钥属性 + 断言「凭据带 `enc:v1:` 落库」+「经 `StorageBackendService.configOf` 解回明文」（消费者层）。
  - 全量测试绿 + `spotlessCheck` 绿。
- **副作用（良性，已核对）**：走 `StorageConfig` 往返后，供给行与接口建的行**形状归一**（含空串/false 的完整字段集）→ 引擎面读到时与"缺省"等价（逐字段核对：布尔缺省与显式 false 同义、`use_temp_bucket` 只由临时桶名决定）。既有明文行（若有）**无需迁移**：读侧「无前缀原样」仍能读。
- **dev 库留痕（我的操作，均可删）**：租户 **9 `b14-probe-1015`**（行 `03e70033…`，明文，B14 建的）与租户 **10 `b15-probe-1030`**（行 `3ebd7f75…`，密文，B15 建的）。

**📋 下一步候选（2026-10-02 实测盘点，待用户拍板）**
- **完成度快照（本仓实测，取代 §4/§7.2 的过时数字）**：
  - 代码内裸 `System.getenv` = **0**（B6 ✅ 十批结项）；`@JsonInclude` **227** 全落在 §15.3 冻结面。
  - `@JsonProperty` 全仓 **1009**：其中 §15.3 明文列出的冻结路径 **911**（tenantconfig 126 / datasource connector 351 / event 181 / llm 190 / stream 11 / SearchParams 13 / mcp oauth 10 / memory LLM 载荷 22 / …）；
    其余 **98 处 / 35 文件**（长尾多为 1~2 处/文件）**含少数冻结邻近项**——`rerank/RankResult` 6、`tracing/langfuse/TokenUsage` 7、`retrieval/engine/doris/DorisStreamLoadClient` 6、`common/context/TracingContext` 6、`retrieval/domain/ImageInfo` 7（§15.3 已点名 image_info）⇒ **实际待判定债务 ≈60~70 处**，较大者：`agent/AgentConfig` 14（落库 jsonb，读写两端一致的 §11.2 边界）、wiki page 面 8+3+3、`common/wiki/ExtractedItem` 7、`websearch` 7、auth controller 4、session 2、knowledge 6。
  - Go 锚点注释 **3884 处**（政策不变：随触碰清洗 + 棘轮守卫，不立专项）；≥800 行的类 = **4 个登记例外**（§14.3）。
- **候选（按价值排序）**：
  1. **「静默失效」定向扫描（推荐）**——B3b（KB 配置键名分裂）、B14/B15（存储两套词汇 + 绕过加密）三处真实缺陷同属**一类**：*同一份数据两条路径 ⇒ 配置被静默忽略、且不报错*。建议 1 批定向排查：枚举所有「配置/JSON 跨层且带改名或忽略未知键」的读写对（jsonb 读写、`JsonMappers.lenient()`、快照安装点、手写改名器），对照写读词汇并补**消费者层**钉测；产出「候选清单 + 判定 + 修复 + 守卫」。
  2. **换锚收尾（阶段 3 收口）**——把上述 ≈60~70 处按「真债 / 有意保留」逐条判定后，1~2 个小批清完（同批带前端，按 §2 第 3/4 条）。
  3. **登记小项（B6 批 7 遗留，仍开着）**——`JWT_SECRET` 短于 32 字节应**启动期**校验并给明确文案（现为登录时才抛 `UnsupportedKeyException`）；`AuthController` refresh javadoc 仍引用 Go 的 `refresh_token`（契约实为 camel `refreshToken`）。两项都小，可随任一批捎带。
  4. **产品项（需拍板）**——`process_overrides` 后端落地（用户已定调"未来补"，草图见本文件 15.1.1 的 B13 段）。
  5. **不做**：Go 锚点专项、Gradle 多模块（B11 判定）、§15.3 全部冻结面、`QaSearchTargets`(706) 内部细分（低价值，维持搁置）。

**✅ B16「静默失效」定向扫描（2026-10-02，用户拍板方案 A）**
- **方法**：先枚举「能静默丢数据」的**机制点**，再按「同一份数据被两条不同路径读写」缩小战场（B3b/B14/B15 的共同形态），最后逐对核对写读词汇。
- **机制点实测**：
  ① 忽略未知键的宽松反序列化点（按文件聚合；`datasource/connector` 属冻结第三方面，不计）；
  ② **显式键名映射表全仓仅 3 处**——`MemoryIndexStore.columnExists`（JDBC 探列大小写）、`WebSearchTempKbStateService.migrateLegacyKeys`（旧 Redis 载荷迁移）、`StorageFileResolver.renameConfigKeys`（B14 已修）⇒ 前两处**均有意保留，无缺陷**；
  ③ **启动期快照 install 点 8 个**——全部有装配调用（`RuntimeSnapshotWiring` / `RetrievalEngineWiringConfig` / `AppEnvLookupEnvironmentPostProcessor`）；
  ④ **jsonb TypeHandler 14 个**——其目标类型**均未被 controller 引用** ⇒ B3b 那种「前端/服务端/落库三方咬合」在这些面**不存在**；
  ⑤ 命名策略/别名——全仓仅 `datasource/connector/rss/RssConfig` 一处 `SNAKE_CASE`（随换锚判定）。
- **结论：未发现新的静默缺陷**（负面结果，如实记录）——上轮修掉那三处后，同类机制面是干净的。
- **产出（把「检查过」变成「守得住」）**：
  - 新增 `config/AppEnvLookupWiringTest`：**运行时**断言 EPP 真的装载（覆盖约 25 个散落读点：Ollama/OIDC/Gate/邀请 TTL/内置模型/启动恢复/临时文档 TTL/vectorstore 副本数…）。**红态证明**：去掉注册后该用例立刻失败（`expected: "probe-exact" but was: null`）——这正是「代码看着对、运行时不生效」那类回归的唯一自动化防线。
  - 新增 `common/web/JsonFaceVocabularyTest`：对 **9 个已换锚的我方载体面**（websearch params / vectorstore 双配置 / mcp auth / api-principal / model parameters / wiki config / memory extraction state / datasource config）做反射守卫——`@JsonProperty` 键名必须＝字段名、禁 `@JsonAlias`（防旧键回流）、禁类级下划线策略。**这是「第二套词汇」的防回流闸门**；换锚完成的面应持续加进登记表。
- **扫描附带盘出的欠账（→ B17 换锚收尾）**：`AgentConfig` 14（最大单点：落库 jsonb 逐字段 snake）、websearch controller 7（含 `is_default`）、wiki page 面 ≈15、auth controller ≈6、session 2、common 13、`datasource/domain`（`SyncCursor`/`SyncResult`/`DataSourceSyncPayload`/`DataSourceConfig` 的本地 mapper）等。
- **另登记（小，未做）**：① `WebSearchTempKbStateService.migrateLegacyKeys` 是「仅部署窗口用」的旧键迁移 shim，按 §2 第 2 条（产品未上线、无数据连续性负担）**可删**；② 约 50 处**自建**宽松 mapper 未接 §2 第 12 条的 `JsonMappers.lenient()` 工厂（约定债，机械可清，宜配 ArchUnit 规则）。
- 闸门：`spotlessCheck` 绿 + 全量测试绿（含两个新守卫）。

**🚧 B17 换锚收尾·判定批（2026-10-02）**——把「残余 @JsonProperty」逐条判定为三类，本批**无代码变更**（实验性删除已回退，全量复绿）。
- **口径修正**：此前「残余 98 处」混入了类级 `@JsonPropertyOrder`；**逐字段**精确计数 = **914**（冻结面 ≈848 + 我方面 ≈57）。我方面里：键名＝字段名 **31**、键名≠字段名 **26**。
- **一类：键名＝字段名（看似零风险）——踩到一个承重例外（血泪）**：实测删掉 11 处（websearch 控制器 5 / websearch 响应 1 / auth 三文件 5）→ **9 条契约测试失败**（`Key: 'createTenantRequest.Name' … required`）。根因：**这些请求 DTO 的字段是包级可见（无修饰符），Jackson 默认不识别非公开字段——是 `@JsonProperty` 强制其可见才绑上的**，注解**承重**（承载可见性，不只是键名）。
  **判据（写进方法库）**：删「键名＝字段名」的 `@JsonProperty` 前，先查字段可见性——`public`／有访问器／record 组件 ⇒ 可删；否则删了会**静默丢绑定**。且注意闸门表现：报错出现在**下游校验**（"某字段必填"），不是绑定异常——比直接报错更隐蔽。要清这类需先给可见性（改 record 或加访问器），属独立小批，**本批不做**。
- **二类：键名≠字段名（会动线上形状，B17 主体）**：`AgentConfig` 12（snake → camel 的最大单点，落库 jsonb，按 §2 第 11 条 + §2 第 2 条走 dev 库迁移 SQL，参照 model 域先例）、`websearch` 的 `is_default` 1（另须按 §2 第 4 条顺带把布尔字段去 `is` 前缀）、`AuthSessionOps` 等零星项。
- **三类：冻结面（不做）**：`MemoryExtractionLlm` 10 / `NewSlugFromCitation` 7 / `MemoryExtractPayload` 9（LLM 载荷）等。
- **附带**：`@JsonProperty` 与 `@JsonPropertyOrder` 混算过是本次口径错源——**统计契约注解要按 `@JsonProperty(` 精确匹配**。
- **安全子集已落地（6 处，全量绿）**：复核发现 9 条失败**只源于 `TenantCreateOps`（普通类的包级字段）**；**record 组件**（Jackson 经访问器识别）与 **public 字段**不受影响。据此只重放 `websearch/controller`（record 5 处）+ `websearch/dto`（public 字段 1 处）→ `spotlessCheck` 绿 + 全量测试绿（4710 用例）⇒ **判据经实证**：*record 组件 / public 字段 / 有访问器 ⇒ 可删；包级字段 ⇒ 注解承重，先给可见性*。`auth` 三文件的同类注解（包级字段）**保持原样**，待"给可见性"的小批一并处理。
- **B17 剩余＝两个域级换锚面（都需前后端同批 + 落库迁移，各自独立一批）**：
  ① **agent 配置面**：`agent/AgentConfig`（12 snake）+ `AgentConfigJson`（默认值/校验，键面 **≈60 个 snake**）+ `BuiltinAgentRegistry.CONFIG_KEYS`（≈60 键全集过滤）+ `session/service/AgentConfigAssembler`（≈20 个 `path("snake")` 读取点）+ 内置 agent 定义文件 + `custom_agents.config` jsonb 迁移 + `ag-*.json` 夹具重录 + 前端 `api/agent/index.ts` 类型（`max_iterations` 等）。
  ② **websearch provider 面**：响应 DTO（Java 侧已是 camel，但**线上输出 snake** —— 需先定位其序列化路径）+ 请求 DTO 的 `is_default`（按 §2 第 4 条连带改布尔前缀）+ `parameters` jsonb（`base_url`/`extra_config` 等）+ **43 个 `wsp-*.json` 夹具** + 前端映射层。
  建议顺序：**① 先做**（§2 第 11 条点名"落库格式走 Java 字段名"，且是 §11.2 最后一块边界），②随后。

**✅ B17② websearch 请求键收口（2026-10-02）**——`is_default` → camel `isDefault`（§2 第 4 条），**不留兼容别名**。
- **真机 A/B（同一 dev）**：修复前——camel `isDefault` 被**静默忽略**、snake `is_default` 生效；修复后——camel 生效（True）、snake 失效（False）。响应面本就 camel（`tenantId`/`isDefault`/`createdAt`）。
- 新增 `WebSearchProviderRequestBindingTest`（钉「camel 生效 + snake 不绑定」）。`spotlessCheck` 绿 + 全量 4711 用例绿（含新钉）。
- **纠正两个此前误判（如实记录）**：① websearch **响应面早已是 camel**——我此前据 `wsp-*.json` 夹具判"线上输出 snake"是**错的**（那批是 Go 期遗留夹具，与当前契约无关）；② 该请求键**前端与测试都不发**（前端"默认"走专用端点），故它**不是活 bug**，属契约一致性问题。
- **shell 环境新坑**：某轮命令后 `PATH` 被清空（`curl`/`cat`/`head` 全部 not found）；`.env` 里并无 PATH 行，来源未定。**对策：命令前显式 `export PATH=/usr/bin:/bin:/usr/sbin:/sbin:/opt/homebrew/bin`**（已记入长期记忆）。

**🔎 B17① agent 配置面：判定「暂不做」（建议，待用户拍板）**
- 盘面：≈60 个 snake 键 × 面——`AgentConfig` 12 / `AgentConfigJson` 默认值 ≈60 / `BuiltinAgentRegistry.CONFIG_KEYS` ≈60 / `AgentConfigAssembler` ≈20 个读取点 / 内置定义文件 / `custom_agents.config` jsonb / `ag-*.json` 夹具 / 前端 `api/agent/index.ts` 类型；引用量级实测：主代码 **625**、测试 **2150**、前端 **298** 处（含同名词干扰）。
- **判定理由**：① **不是缺陷**——读写两端一致（前端类型也是 snake）；② 收益纯风格（§2 第 11 条的一致性）；③ 代价大（≈60 键 × 4~5 面 + 用户数据迁移 + 夹具重录 + 前端）；④ **风险正是本会话一路在消的那一类**——迁移中任何漏改的 `path("snake")` 都会**静默回落默认值**（不报错）；⑤ 契约测试只覆盖 API 面，运行时读取面覆盖不全。
- **结论（2026-10-02 修订）**：原判定「不做」（同 B4/B11），**其后用户决定继续推进，已由 B18 完成**：后端 + 前端（按面判定）+ 落库迁移 V3 + 真机冒烟，后端 4711 / 前端 690 用例全绿。以下保留原判定的实证记录，作为「爆炸半径」的方法论归档：
  - （原）后端部分**曾完成且全量绿**（main 330 处 + 补充 11 处 + 内置定义 120 处，`AgentConfig` 14 处注解删除，夹具/测试 1933+133 处，4711 用例绿）——产出保存在临时分支 `feat/agent-config-camel`（`32d4fae`+`3dd1234`），**随后按用户决定删除该分支**，成果未合入。
  - **中止理由（实证，非推测）**：前端纠缠度远超键面清单——同批键名在 `frontend/src` 命中 **42 文件 / 850 处**，其中约 20 个确属 agent 面，但**另有一大批属别的面**（租户检索设置 `api/retrieval.ts`、系统提示词模板 `api/system/index.ts`、公共组件 `Input-field.vue`、新建对话 `creatChat.vue`、`utils/tool-capabilities.ts`、`stores/settings.ts`、模型选择器用例 …），**必须逐文件判定、不能批量改**；9 文件试改 644 处后即出现类型报错与散点 ⇒ 硬推会把"静默回落默认值"的风险带进前端。
  - **方法论沉淀**：*重命名/换锚类专项的"键面清单"会系统性低估纠缠度——同名键横跨多个面（前端尤甚），必须先按面判定、再动手；且判定"不做"与"做"都要以实测半径为准，而不是键数量。*

**✅ B18 agent 配置面 camel 换锚（专项，2026-10-02 完成）**——§2 第 11 条"落库格式走 Java 字段名"的最后一块大口子。⚠️ 本批一度按用户决定中止（见 main 上的定案提交），随后用户决定继续推进，最终**完成交付**。
- **后端**（23 文件）：main 330 处键字面量（agentm/session/im）+ 补充 11 处（agent/PromptTemplateCatalog、agent/AgentEngine 的配置读取、im/ImQaRequests、session/SessionAgentQaService）+ 内置定义 120 处（`builtin_agents.yaml`/`agent_type_presets.yaml`）；`AgentConfig` 按 B17 判据删除 14 处逐字段注解（private 字段有访问器 ⇒ 安全）。
- **边界（逐条判定后不动）**：租户配置 jsonb、事件载荷、LLM 载荷、检索参数、langfuse、**chat span 元数据**（`AgentEngine`/`ReActIteration`/chatpipeline 的 spanMap——曾误改，已回退）、SQL 表名/列名、**提示词占位符名**（`AgentPromptPlaceholders`，非 JSON 键）、`MessageJsonContractTest` 的负向断言（已回退）。
- **前端**（20 文件 / 761 处，按面判定）：`views/agent/AgentEditorModal.vue`（466）、`api/agent/index.ts`（65）、`utils/agentPromptTemplates.ts`+测试、`AgentList.vue`、`components/Input-field.vue`、`AgentSelector.vue`、`agent-readiness.ts`、`agentWebSearch.ts`、`PromptTemplateSelector.vue`、`api/system/index.ts`（提示词模板面）、`creatChat.vue`、`AgentEmbedChannelPanel.vue`、`stores/settings.ts`、两个 model-selector 测试、三个 `.mjs` 测试。
  **不改的别面**：租户检索/模型/系统设置、对话历史、工具结果载荷（`SearchResults.vue` 等）、能力名（`manage_mcp_services` 等**子串误伤已用词边界避开**）、localStorage 键名（`weknora_knowledge_bases` 等）。
- **落库迁移**：新增 `migrations/versioned/V3__agent_config_keys_camel.sql`（沿用 V2 范式：递归 camelize + 幂等 + 回滚说明）。实测扫描 dev 库**全部 jsonb 列**：仅 `custom_agents.config` 有存量（2 行 → 迁移后 camel，残留 snake 检查 0）；sessions/messages 等短名单列命中 0。
- **真机冒烟**：建 agent（camel 配置）→ 回读 `config` 全 camel（`agentMode`/`kbSelectionMode`/`questionSuggestions`…）✓；`GET /api/v1/agents/{id}/suggested-questions` 返回 curated 两条 ✓（读的正是迁移过的 `questionSuggestions`）；探针 agent 已删。
- **闸门**：后端 **4711 用例绿 + spotlessCheck 绿**；前端 `vue-tsc` 0 错 + **690 用例绿**。
- **方法论沉淀（写进记忆）**：换锚/重命名类专项的"键面清单"会系统性低估纠缠度——**必须先按面判定、用词边界正则防子串误伤、并在分支上试改探半径**；判"做/不做"以实测半径为准。



**✅ B19 前端契约键收口 + 双侧守卫（2026-10-02）**
- **发现并修掉两处真缺陷（都属「静默失效」类）**：
  1. **B18 回归**：`ModelService.agentBindings`（扫 agent 配置算「模型被谁引用」）**漏改**——B18 只改了 agentm/session/im 白名单，把该文件误判成「model 域」。后果：`chat_model`/`rerank_model`/`vlm_model`/`asr_model`/`query_understand_model`/`follow_up_model` 六类绑定**静默丢失**，而当时 4711 条测试全绿。已修。
  2. **前端 `tagScopes` 链路断**：`stores/settings.ts` 产出 snake（`knowledge_base_ids`/`knowledge_ids`/`tag_scopes` + 内层 `knowledge_base_id`/`tag_ids`），而 `api/agent` 侧收 camel，**中间没有映射** ⇒ 建议问题的范围（当前 KB/文件/标签）被静默丢弃——而该范围按其注释正是「防止后端把标签放大到整个 KB」的护栏。已修（store 改产 camel）。
- **嵌套层收口（B18 遗留）**：`questionSuggestions` 下的 7 个键（`followUps`/`maxContextTurns`/`suppressOnFallback`/`suppressWhenAnswerAsksQuestion`/`knowledgeFallback`/`allowRegenerate`/`additionalInstruction`）→ camel：后端 4 文件 33 处 + 前端 3 文件 40 处 + 夹具 17 个 247 处 + 测试 10 处。
- **落库迁移**：`V3__agent_config_keys_camel.sql` 扩到 **66 键**（含嵌套；函数本就递归）。⚠️ **修正 WHERE**：原条件只看**顶层**键 ⇒ 会漏掉「顶层已 camel、嵌套仍 snake」的行（实测残留 3 行）；改为「转换结果确有变化才更新」（jsonb 键序规范化 ⇒ 无改名时等值）。重跑后残留 **0**。
- **两个新守卫（本批最重要产出）**：
  1. `scripts/check-fe-contract-keys.py` + 基线 `scripts/fe-snake-contracts.baseline.json`（42 条，逐条带豁免理由）**接入 CI guards job**：前端一旦出现「后端只认 camel」的 snake 契约键即红（只许减不许增）。
  2. `AgentConfigKeyUsageTest`：键表**直接解析 V3 迁移**（单一来源），扫描主代码禁止再以 snake 读写 agent 配置键；带「同文件写读对豁免」（如 model-usage 载荷自洽）。**它当场抓出 `ModelService` 两处遗漏**——这类缺陷属源码级扫描才能兜住的盲区（测试全绿也照样漏）。
- **判定口径（写进两份守卫的注释）**：*这个键是不是「我们定义的、跨进程 JSON 字段」？是 ⇒ camel；否 ⇒ 看那一层的规范（DB=snake、Spring 配置=kebab、环境变量=UPPER_SNAKE、第三方=照抄）。*
- **登记（未做）**：① `api/knowledge-base` 的 `start_time`/`end_time`：前端仍拼、后端该端点**未声明** ⇒ 筛选不生效（要修的是接线或补参数，属产品取舍）；② `api/auth` 的 `owner_id`：前端**死字段**（后端 API 无此名，前端有 `|| user.id` 兜底）；③ `types/knowledgeProcess.ts` 那批 snake 属逐文档 `process_overrides` 面（已定「未来补后端」，届时一并 camel）。
- 闸门：后端 4711 用例 + `spotlessCheck` 绿；前端 `vue-tsc` 0 错 + **690 用例绿**；迁移已在 dev 应用且残留 0。
- **踩坑（Flyway，值得记）**：V3 文件在 **App 已应用过它之后**又被我扩展改写 ⇒ 启动期 `Migration checksum mismatch for version 3` 直接导致**服务起不来**。处置：删掉 `flyway_schema_history` 里 V3 那行让 Flyway **重放**（因 V3 幂等，重放无副作用），启动即恢复，历史表记录的校验和随之更新。**教训：迁移文件一旦被 App 应用过就别再改**（要么先定稿，要么用 repair/删行重放）；这也是"迁移必须写成幂等"的一条实际收益。

**🐞 B20（2026-10-02——用户追问 `/agents/placeholders` 的 snake 组键引出的修复）**
- **用户问题（结论）**：`frontend/src/api/agent/index.ts` 的 `PlaceholdersResponse` 里 `agent_system_prompt`/`rewrite_system_prompt`/`rewrite_prompt` 是 snake——与后端**逐键一致**（`agentm/service/AgentPlaceholders.data()`），故**当前不会静默失效**。但它不是「有意保留」的设计：B18 的批量改名只覆盖了恰好落在 agent 配置 60 键清单里的三个组键（`systemPrompt`/`contextTemplate`/`fallbackPrompt`），其余留 snake ⇒ **半改状态**；该文件原注释「键 = config 模板键，保持 config schema 的 snake」也已过期（B18 后 config schema 是 camel）。**已收口（B21，2026-10-02）**：3 个组键改为 `agentSystemPrompt`/`rewriteSystemPrompt`/`rewritePrompt`（详见下条 B21）。
- **同批挖出并修掉一个 B18 回归（我引入的）**：`AgentPlaceholders` 里 `new P("knowledge_bases", …)` 是**模板令牌**（数据值——渲染器按 `{{knowledge_bases}}` 替换，见 `AgentPrompts:278` 与渲染面表），被 B18 批量改名误改成 `knowledgeBases`，**且契约夹具在同一提交被同步改掉** ⇒ 全线绿、缺陷静默（用户从 UI 插入的该占位符将永远不被替换，提示词里留一段字面量）。
- **守卫**：新增 `agentm/service/AgentPlaceholdersTest`——HTTP 面令牌表必须与渲染面 `AgentPromptPlaceholders.placeholdersByFieldAgentSystemPrompt()` **逐字一致**，并逐令牌验证渲染器真能替换。**红态证明**：临时还原错令牌 → 用例精确报出 `["knowledgeBases", …]` vs `["knowledge_bases", …]`。
- **连带修正**：B19 的 `AgentConfigKeyUsageTest` 对该文件**假阳性**（此处的 snake 是模板令牌、非 agent 配置键）→ 加**具名豁免 + 理由**并指向新守卫。
- **方法论（与既有记忆同源，具体化）**：批量改名要区分**键**与**数据值**——「模板令牌/标识符/枚举值」与 JSON 键同名时会被一并误改；且**同步改夹具会把缺陷洗成绿色**（改 A 忘 B 的变体：改了「被测对象」又改了「期望值」）。对策＝跨面对照守卫 + 红态证明。
- 闸门：全量 **4713** 用例绿 + `spotlessCheck` 绿。

**✅ B21（2026-10-02，占位符组键收口 + 「同类情况」定向排查）**
- **收口完成**：`/agents/placeholders` 的 3 个组键 → `agentSystemPrompt` / `rewriteSystemPrompt` / `rewritePrompt`。落点：后端 `AgentPlaceholders.data()`（3 键；注释更新为「键＝前端字段面 camel；P 名＝模板令牌 snake，由 AgentPlaceholdersTest 守」）、夹具 `ag-placeholders.json`（**仅根键**，令牌值未动）、前端 `api/agent/index.ts` 接口 + `AgentEditorModal.vue`（内联类型、默认对象、3 处用法）。**模板面有意不动**：`PromptTemplateCatalog` 的 `agent_system_prompt` 与 FE `cfg.agent_system_prompt` / `api/system` / `PromptTemplateSelector` / `agentPromptTemplates.ts` 属**另一面**（提示词模板配置），本轮零接触。
- **同类排查（两道筛子，可复现）**：
  - **筛子 A「混搭面」**：扫描全部 **1344** 个契约夹具，标记「同一对象层级同时出现 camel 与 snake 键」→ 命中 8 个：① `ag-placeholders`（本批已收口）；② `ct-kv-get-prompt-templates`（**待收**：snake `agent_system_prompt`/`generate_session_title`/`generate_summary`/`keywords_extraction`，camel `systemPrompt`/`contextTemplate`/`intentPrompts`）；③ `ct-create-apikey`（`api_key` **待收**，后端 `TenantCreateOps:201`、`TenantAPIKeyBootstrap:94` 的 `m.put("api_key", token)`）；④ 凭证字段标识符（`api_key` 作**内部标识符**、HTTP 键用 camel `apiKey`——**有意**，与「值 vs 键」同理）；⑤ 余下 4 个（`doc-get`/`doc-list`/`wiki-lint`/`wiki-stats`/`wiki-revisions`）按名检索**无测试引用**——可能是孤儿夹具，也可能名字由拼接构造（初筛为启发式，**未定论**，需先判面死活）。
  - **筛子 B「值位置误伤」**：从改名提交反推 **73** 对映射，再全仓查 camel 形态是否出现在**值位置**（`{{令牌}}`/`new P(`/`case`/枚举/`id:`/`type=`）⇒ **只命中已修的 `knowledge_bases` 一处**；其余命中均良性（MyBatis 结果映射 id、websearch **值映射表** `search_std → searchStd`、旧载荷迁移表 `kbID → kbId`）。**结论：没有第二处「令牌被误伤」**。
  - **附带发现**：`frontend/src/api/web-search.ts` 是**死模块**（全 FE 无人 import；内含 snake `requires_api_key`，在用版本 `api/web-search-provider.ts` 是 camel）⇒ 建议删。
- **守卫判别力实证**：组键一改，`AgentPlaceholdersTest` 立刻变红（常量即旧组键名）——已同步更新。闸门：后端全量 4713 用例绿 + `spotlessCheck` 绿；FE `vue-tsc` 绿 + `tsx --test` **690** 用例绿 + FE 契约键棘轮无新增（基线 42）。未做真机（该响应有逐字节契约测试覆盖）。
- **下一批候选（同类，按性价比）**：① `ct-kv-get-prompt-templates` 模板面收口（4 键；动 `PromptTemplateCatalog` + FE `api/system`/`PromptTemplateSelector`/`agentPromptTemplates.ts` + 夹具）；② `ct-create-apikey` 的 `api_key`（后端 2 处 + 夹具；FE 无读者）；③ 删死模块 `api/web-search.ts`；④ 孤儿夹具核查（应写「夹具被引用」检查，不按名字猜）。

**✅ B22（2026-10-02，模板面 4 键 + `api_key` 收口 + 删死模块）**
- **模板面收口**：`PromptTemplateCatalog` 的 `agent_system_prompt`/`generate_session_title`/`generate_summary`/`keywords_extraction` → camel。**动手前核实**：KV `prompt-templates` 是**只读派生视图**——`TenantConfigOps` 的 PUT 白名单里没有它，GET 从 `config.yaml` 装载 + 按 `Accept-Language` 本地化 ⇒ 改输出键**没有写回副作用**（若该键可写，就必须先统一落库绑定键，否则会重演 B14 那类「读了 camel、写回 snake 被静默丢弃」）。
- **落点**：后端 `PromptTemplateCatalog`（4 键）；夹具 `ct-kv-get-prompt-templates.json`（4 个根键，各仅一处、形如 `"key":[`）；前端 `api/system/index.ts`（4 键 + 删死字段 `chat_summary`——无读者且后端从不产出）、`PromptTemplateSelector.vue`（type→键映射）、`agentPromptTemplates.ts` 与其测试、`AgentEditorModal.vue`（含 5 处 `cfg.agent_system_prompt` 读者 + 2 处注释/日志文案）。
- **有意不动**：磁盘资源名 `agent_system_prompt.yaml`、`generate_summary.yaml` 等——**文件名是数据值，不是 JSON 键**（B20 教训当场两处都用上了）。
- **`api_key` 收口**：`TenantCreateOps`、`TenantAPIKeyBootstrap` 两处 `m.put("api_key", token)` → `apiKey` + 夹具 `ct-create-apikey.json`；**测试侧同步**（契约测试的归一化正则与占位符 `<api_key>` → `<apiKey>`、bootstrap 两处期望含**键序**）。**有意不动**：DB 列名 `api_key`（MyBatis `@Result(column=…)`）、设置键 `tenant.auto_create_api_key`、租户配置 jsonb 里的 `api_key`（冻结面）、i18n 键 `system.api_key_created`。
- **删死模块**：`frontend/src/api/web-search.ts`（全 FE 无人 import；在用版本 `api/web-search-provider.ts` 已是 camel）。
- **闸门**：后端全量 **4713** 绿 + `spotlessCheck` 绿；FE `vue-tsc` 绿 + `tsx --test` **690** 绿 + 契约键棘轮无新增。期间 3 条后端失败均为**测试侧读旧键**（合同测试归一化失配、bootstrap 期望），已按"断言的是契约而非实现"逐条核对后同步。

**🧰 B23（2026-10-02，孤儿夹具审计：工具 + 首次结论）**
- **工具**：`scripts/audit-contract-fixtures.sh`——**按行为判定，不按名字猜**：① 静态粗筛候选（名字直引 + 明显前后缀拼接者排除）；② 把候选**临时移出** `contracts/` → 跑全量测试；③ 失败报告里被点名的＝**被引用**，其余＝**孤儿**；`trap` 保证无论如何都还原（本次实测工作区零残留）。
- **首次审计（1344 个夹具）**：候选 131、**被引用 16**、**孤儿 115**（≈8.6%）。被引用侧的失败形态已核实：`contracts/<name>.json.json] cannot be opened because it does not exist`——`golden()` 先试原名、不存在再补 `.json`，两者皆无则**抛异常**（不会静默跳过）⇒ 判定可靠；被引用的 16 个里含 `kg-rename.json`、`ct-create-disabled.json`、`cprev-empty-text.json` 等。
- **孤儿聚类（前缀）**：`w5a` 14、`wiki` 11、`sys` 10、`w5b` 7、`schk` 7、`ct` 7、`ks` 6、`faq` 5、`mcp` 4、`imc` 4、`emb` 4、`adm` 4、`wsp` 3、`sug` 3、`reg` 3、`ev` 3…（名录见产物 `/tmp/contract-fixture-audit/orphans.txt`，随时可复跑重生成）。
- **诚实说明（判定边界与删除建议）**：① 判定依赖"缺失即抛"——若某测试改用**带 `exists()` 护栏的自读**，缺文件会静默通过而误判为孤儿；全仓 `contracts` + `exists()` 仅 **2 处**（`W5cFileProxyContractTest:265`、`W5dTerminalEmbedContractTest:121`），且都是**路径选择**护栏（`exists(路径A) ? A : 路径B`，B 不再判存在）⇒ 缺失仍会抛异常，判定不受影响。② **孤儿 ≠ 无用**：它们可能是录制期/走查期的**原始素材**（Go 锚点证据），删除建议只做"对应测试确已删除"的那批，且删后仍可从 git 历史回捞。
- 本批未删任何夹具（工具与结论先落地，等拍板）。

**🔎 B24（2026-10-02，换锚长尾回头扫：注解面清零 + 真实残留面盘点）**
- **注解面（`@JsonProperty`）真债＝0**：非冻结面只剩 **26 文件 / 45 处**，其中含下划线的仅 **7 处**，逐条核实全部**有意保留**——① `common/context/TracingContext` 的 5 个 `lf_*`（**langfuse 线上字段**，`DataSourceSyncPayload` 的 javadoc 就写着该形态）；② `wiki/service/ingest/WikiIngestCitePipeline` 的 `new_slugs`（javadoc 原文「只用于**解析**模型输出，从不序列化出站」，且提示词 `WikiPrompts` 里就是 `new_slugs`）；③ `WikiIngestMapPhase` 的 `new_slugs`（既有**内部 jsonb 状态键**，改名需迁移存量行）。其余 38 处是单字键或已 camel（大小写中性，非债）。
- **但真实残留面比注解大得多**（本轮新发现）：经 `Map/JsonNode.put("snake")` 写的键，非冻结面 ≈ **309 键 / 119 文件**，按消费者分四桶：
  | 桶 | 规模 | 判定 |
  |---|---|---|
  | 第三方适配器族（embedding/im/websearch/rerank/asr/vlm provider 与客户端） | 30 文件 / 101 键 | **冻结**（键名由对方 API 定）——本轮已并入扫描器冻结清单 |
  | 内部/诊断/状态载荷（pipeline 进度、span、同步日志、审计、内存洞察…） | 33 文件 / 127 键 | 待判（多属"诊断面"，倾向登记保留） |
  | FE 可见 | 33 文件 / 42 键 | **真债候选**（如 `chatpipeline/support/ReferencesSupport` 的 `chunk_id/knowledge_title/display_type…`、`PipelineProgress` 的 `candidate_count/search_source…`、`PromptTemplateCatalog` 的 `has_knowledge_base/has_web_search`、mcp/datasource/agent 面若干） |
  | 仅夹具断言 | 23 文件 / 39 键 | 自有面，待判 |
- **工具**：`scripts/check-json-key-case.py`（`--list` 出待判清单；默认＝**报告**、退出 0；`--strict` 才是闸门，**当前未启用**——启用前置＝批甲/批乙判定完成，现基线只含 3 组已逐条复核的例外）。**边界诚实声明**：按文本模式匹配会命中 **SQL 参数 Map / MyBatis 列名**（`*Repository` 的 `deleted_at` 等）⇒ `--list` 是**待判**清单而非违规清单；判真债必须看**消费者**。基线当前仅含 3 组已核实例外（langfuse、模型契约 `new_slugs`、内部 jsonb `new_slugs`）。
- **方法论**：不要机械批改（B18 教训）——按**面**逐条判定，FE 可见的同批带前端与夹具；判不动的先登记，别猜。
- **下一批（批甲，建议）**：FE 可见的"自有 JSON 面"逐面收口——先做聊天管线两组（`ReferencesSupport`/`PipelineProgress`/`SearchSupport`）与模板载荷标志位（`has_knowledge_base`/`has_web_search`），每个面＝后端 + 前端 + 夹具 + 契约测试同批。

**✅ B25（2026-10-02，批甲：模板标志位收口；引用/进度载荷判为冻结）**
- **收口**：模板载荷标志位 `has_knowledge_base` / `has_web_search` → `hasKnowledgeBase` / `hasWebSearch`。落点：后端 `PromptTemplateCatalog` 的**输出面**（`o.put(...)`，2 处）；夹具 `ct-kv-get-prompt-templates.json`（9 + 1 处）；前端 `api/system/index.ts`（2）+ `PromptTemplateSelector.vue`（2，模板 tag 条件）。**有意不动**：同文件 126/127 行的 `t.path("has_knowledge_base")`——那是**磁盘 YAML 的文件键**（输入面），动了会读不到。
- **判定为冻结（不是改名）**：`ReferencesSupport` / `PipelineProgress` 的载荷——① 引用随 `messages.knowledge_references` 列（`SearchResultListTypeHandler`）**落库**；② 前端 `types/tool-results.ts` 以**非引号属性声明**（`chunk_id`/`knowledge_title`…）**按历史回放**渲染；③ 前端 `utils/rag-pipeline-history.ts` 还会用**同形键**（`search_source`/`doc_count`/`web_count`）**重建**该载荷 ⇒ 改名＝必须先出迁移方案（或双读）⇒ 已并入扫描器冻结清单并写明理由。
- **筛子缺陷（自我纠正）**：B24 的「FE 可见」分桶是按**引号形式** grep 的，而前端大量使用**属性声明/访问**（`knowledge_title: string;`、`x.knowledge_title`）⇒ 该桶**系统性低估**（`ReferencesSupport` 曾被我判成「未外露」）。本批两面已用非引号写法复核；B24 表里其余「未外露/仅夹具」项在批乙须**重筛一遍**。
- **仍待判（留批乙）**：`chatpipeline/support/SearchSupport`（`dropped_id`/`kept_id`/`match_type`，疑似检索调试载荷）、`chatpipeline/PipelineBuilder`（`chat_stream`/`rag_stream`/`chat_history_stream`，管线步名/事件名）。
- 闸门：后端全量 **4713** 绿 + `spotlessCheck` 绿；前端 `vue-tsc` 绿 + `tsx --test` **690** 绿 + 契约键棘轮无新增。

**🐞 B26（2026-10-02，批甲续：websearch 凭据面全链修复 + 其余四面判冻结）**
- **真 bug（B14 同类，由「逐面看消费者链」挖出）**：websearch provider 的**凭据面三处键名错位**——前端全按 camel、后端全按 snake ⇒
  ① `PUT /web-search-providers/{id}/credentials` 读 `body.get("api_key")`，而前端与**金鹰里录制的走查请求体**都是 `{"apiKey": …}` ⇒ **保存凭据静默失效**；
  ② `DELETE …/credentials/{field}` 只认 `api_key`、前端传 `apiKey` ⇒ **删除报 400**；
  ③ 响应 `credentials` map 键是 `api_key`，而前端 `credentialMeta` 按 `field.key='apiKey'` 索引 ⇒ **徽标恒显示「未配置」**。
- **金鹰忠实记录了缺陷态**：`wsp-cred-put.json`、`wsp-get-after-cred.json` 里都是 `configured:false`（「存了也没存上」）；修复后据实纠正为 `true`，并核对其余 `wsp-*` 夹具（共 11 个改键名）。
- **修复**：`WebSearchProviderResponse`、`WebSearchProviderCredentialsController`（读体 / 删除路径段 / 注释）、`WebSearchProviderService.clearCredential` 统一 camel；夹具与测试内的删除路径同步；**前端无需改**（它本来就是对的）。
- **验证**：契约测试（MockMvc 走真实 handler + 真实库）现在观察到 `configured:true`＝「PUT 真的存上了」的**绿灯证明**；websearch 域 47 用例绿、后端全量 **4713** 绿 + `spotlessCheck` 绿；前端 `vue-tsc` 绿 + **690** 绿 + 棘轮无新增。未另起真机（该契约测试即端到端）。
- **其余四面判冻结/登记**（逐条给理由，已入扫描器 BASELINE）：`datasource/dto/DataSourceResponse` 的 `feed_urls`（datasource 配置 jsonb·存量）；`McpMetadataService` 与 `McpUsageInstructionsOps` 的 `server_name`（存量 metadata 形态 + 提示词模板**变量**＝数据值）；`AuditLogService` 的 `raw_path`/`required_role`（审计 details jsonb·前端按历史读）；`KnowledgeFileService` 的 `.set("列名")`（**MyBatis 列名，非 JSON 键**＝扫描器按文本匹配的已知假阳性）。
- **方法论再次验证**：桶启发式只作粗排；**定性必须看消费者链**——本批真 bug 正是这样挖出的（若按名字批量 camel 化，`api_key` 这类内部标识符/第三方键会被一起改坏）。

**✅ B27（2026-10-02，批甲续 2：推荐问题键收口 + 6 类面判冻结）**
- **收口（真债·小）**：推荐问题面 `knowledge_base_id` → `knowledgeBaseId`。落点：后端 `AgentSuggestedQuestions`、前端 `api/agent` 的 `SuggestedQuestion` 类型、夹具 `ag-sq-faq.json`/`ag-sq-faq-knowledge.json`。**说明**：该面两端本来一致（snake↔snake，不是 bug），按 §2「我们自己的 JSON 面 ⇒ camel」收口；前端**无组件读者**（仅类型声明）故改动面极小。
- **判冻结（逐面给理由，全部入扫描器 BASELINE）**：
  - `chatpipeline/support/SearchSupport`（`kept_id`/`dropped_id`/`chunk_id`/`match_type`）＝ `PipelineLog` **观测面**（原注释即「观测面，非契约」）；
  - `chatpipeline/PipelineBuilder`（`chat_stream`/`rag_stream`/`chat_history_stream`）＝ **内部预设名**（`presets()` 仅 `EvaluationService` 内部查表，不经 HTTP 外露）；
  - **session 诊断桶**（`AgentStreamBridge` / `MessageSuggestionService` / `QaSupport` / `SessionKnowledgeQaService` / `SteerSinkBridge`）与 `agent/ActPhase` ＝ **`messages.agent_steps` 落库列**（`AgentStepListTypeHandler`）+ 历史回放；
  - **存储引擎面**（`StorageFileResolver` / `SystemController`）＝ B14 已确立的 snake 冻结面；
  - wiki 摄取**内部 jsonb 状态**（`WikiIngestReducePhase` / `WikiIngestRunSupport`）与**模型输出契约**（`NewSlugFromCitation.source_chunks`）。
- 扫描器待判清单 **80 → 66 文件**。
- 闸门：后端全量 **4713** 绿 + `spotlessCheck` 绿；前端 `vue-tsc` 绿 + **690** 绿 + 契约键棘轮无新增。
- **队列剩余**：`system/*`（`quota_bytes`/`quota_gb`/`new_value`/`is_revoked` 等）、`websearch/dto/WebSearchProviderTypes`（`label_key`/`description_key`——前端按 snake 读 ⇒ 真债候选）、`mcp/controller` 其余项、`wiki/*` 其余、以及 model 凭据面（snake，两端自洽）是否与 websearch/MCP 统一。

**✅ B28（2026-10-02，批甲续 3：websearch provider-types 配置字段面收口）**
- **收口**：`label_key` / `description_key` → `labelKey` / `descriptionKey`。落点：后端 `WebSearchProviderTypes`（7 处）；前端 `api/web-search-provider.ts`（类型 3 处）与 `WebSearchSettings.vue`（用法 3 处）；夹具 `wsp-types.json` / `wsp-legacy-providers.json`（各 10 处）。
- **判定说明**：该面两端本来一致（snake↔snake，**不是 bug**），按 §2「我们自己的 JSON 面 ⇒ camel」收口。特别地：这两键的**值**是国际化文案键（形如 `webSearchSettings.configFields.*`）——**值是数据、键才是 JSON 面**，故只改键、不动值（B20 教训的又一次应用）。
- 闸门：后端全量 **4713** 绿 + `spotlessCheck` 绿；前端 `vue-tsc` 绿 + **690** 绿 + 契约键棘轮无新增。
- **队列剩余**：`system/*`（`quota_bytes`/`quota_gb`/`new_value`/`old_value`/`value_type`/`is_revoked`…）、`mcp/controller` 其余项、`wiki/*` 其余项、`storage/fileserve/FileProxyService`（`file_path`）、以及 model 凭据面（snake，两端自洽）是否与 websearch/MCP 统一。

**✅ B29（2026-10-02，批甲/批乙收口：换锚待判清单 66 → 0）**
- **逐族判定**（每条都给了理由，全部入扫描器 BASELINE 或冻结前缀）：
  - **MyBatis 列名/参数族**（22 文件：`datasource/mapper/*`、`knowledge/repository/*`、`knowledge/service/Knowledge*`、`auth/service/Tenant*`、`model/service/*`、`system/service/SystemAdminUserService`、`wiki/service/DefaultWikiKnowledgeFinalizer`）——样本实测（`new UpdateWrapper<AuthToken>().eq("user_id",…).eq("is_revoked",false).set("is_revoked",true)`）确认是**列名，非 JSON 键**；
  - **观测/追踪族**（`memory/service/Memory*`、`retrieval/obs/`、`knowledge/service/SpanTracker`）——`PipelineLog`/`MemoryTrace`/langfuse 面，非契约；
  - **第三方引擎族**（`retrieval/engine/`＝ES/OpenSearch/Milvus/Qdrant 的 DSL 字段、`retrieval/graph/Neo4jGraphRepository`＝Cypher 字段、`config/OpenSearchAuditSinkAdapter`）——并入**冻结前缀**；
  - **存量/配置面**（租户与系统配置 jsonb、平台审计 details jsonb、`ModelOutput` 读 SQL/检索行键、`ImageInfo` 面、`WebResultConverter` web 引用载荷、`FileProxyService` 的 **HTTP query 参数名**、`RequestIdFilter` 的 **MDC 键**）；
  - **模板令牌**（`AgentPrompts` 的 `current_time`/`web_search_status`＝**数据值**）；
  - **model 凭据面**（`CredentialsResponse`/`ModelResponse` 的 `api_key`/`app_secret`）：**两端自洽、功能正常**——前端把 UI 内部标识符映射成 camel 发 HTTP（`ModelEditorDialog` 注释里就写着这条约定）⇒ **登记为例外**；是否与 MCP/websearch 统一留待拍板（“可以统一、不急”）。
- **结果**：扫描器待判清单 **66 → 0**（`--list` 只剩基线条目）。
- **已知边界（已写进脚本）**：BASELINE 条目是**文件级**——已登记文件里将来新增的 snake 键不会被点名；故在已登记文件中新增键时需按族复核。
- **本批零生产代码改动**（仅工具与文档）⇒ 未跑测试。

**🔧 B29 订正（2026-10-02）**：B29 的 BASELINE 插入代码多写了一个提前闭合字典的 `}`，脚本出现 `IndentationError`；而当时的"待判 66 → 0"是**用坏脚本 grep 出来的假象**（报错输出里自然数不到"新增"）。已修复：① 去掉多余大括号；② 整块重写 BASELINE（**68 文件 / 328 键，逐条带理由注释**），把漏登的 9 个文件按其**已判族**补齐（`KnowledgeFileService` 的列名、`McpUsageInstructionsOps` 的模板变量、`session/controller/*` 的 SSE/消息与附件载荷、`session/mapper/*` 的列名、`WikiIngestMapPhase` 的内部 jsonb）。**以可用脚本重验**：默认模式 `✓ 无新增`、`--strict` 退出码 **0** ⇒「待判清零」这次是真的。
**棘轮已接进 CI**（`.github/workflows/ci.yml` 的 guards 作业，与 go 锚点/前端契约键并列）——今后非冻结面出现**新的** snake JSON 键会直接红；基线 68 文件 / 328 键逐条带理由。

**✅ B30（2026-10-02，model 凭据面与 MCP/websearch 统一为 camel）**
- **范围界定（关键）**：只改 **API 层**——`CredentialsResponse` / `ModelResponse` 的响应 map、`ModelCredentialsController` 的删除路径段、`ModelService` 的字段 switch；**存量存储层一律不动**（`ModelRuntimeFactory` 读 params jsonb 的 `app_secret`、`WeKnoraCloudService` 的 `app_id`/`app_secret` 落库与第三方载荷）——那是**已落库**的判断键，改名须先出迁移方案。
- **顺带核实（确认没有第二个 B26）**：model 的凭据 **PUT 请求体本来就是 camel**（`req.apiKey()` / `req.appSecret()`），与前端一致 ⇒ 无「保存静默失效」缺口；与 B26 修的 websearch 面形成对照（那一处才是断的）。
- **落点**：后端 4 文件（`CredentialsResponse` 3 / `ModelResponse` 2 / `ModelCredentialsController` 1 / `ModelService` 2）；前端 `api/model` 的 `ModelCredentialField` 类型、`ModelEditorDialog` 的字段标识符与映射（现为恒等）与默认 meta，共 8 处；夹具 5 个各 2 处（**只改 `{"configured"` 的凭据元数据块，未动落库参数**）；`ModelContractTest` 的删除路径。
- **棘轮自证**：换锚棘轮提示 `CredentialsResponse` / `ModelResponse` 两条基线**已无命中** ⇒ 已清理（「只许减不许增」的**减**侧生效）。
- 闸门：后端全量 **4713** 绿 + `spotlessCheck` 绿；前端 `vue-tsc` 绿 + 契约键棘轮无新增 + 换锚棘轮 `✓ 无新增`。

**⚠️ B31（2026-10-02，B23 处置：结论修正 + 一次失败尝试的完整记录）**
- **结论修正（B23 的前提是错的）**：115 个"无人加载的孤儿"里，**102 个被 `scripts/record-*-golden.sh` 引用**（Go 期金鹰的**录制清单**——重录时的案例表），另 1 个被 `docs/known-issues/00-foundation.md` 引用 ⇒ 属**证据链，不可删**。此前"只删对应测试已删除的那批"的说法基于"只查测试引用"的不完整视图。
- **失败尝试（如实记录）**：我按"无测试加载 + 无仓外引用"再筛出 **13 个**并**真的删掉了** → 全量测试报 2 条失败（`ImContractTest.imChannelsFace`：`class path resource [contracts/imc-guard-viewer.json.json] cannot be opened`）⇒ **归因有系统性盲点**：本脚本"一次移出全部候选"，同类**共享 setup** 的首个失败会**掩盖**同批其余候选，使其被误判为孤儿。已 `git checkout` **全部还原**，复跑全量绿。
- **工具加固**：① 头部写入"**仅作人工复核线索，禁止按输出批量删除**"与上述盲点；② 增加"仓外引用（录制脚本/文档）"二次筛，输出 `keep-as-evidence.txt` 与 `no-reference-candidates.txt`；③ 输出改名以避免被读成"删除清单"。
- **最终处置：一个都不删**（102 个是证据链；13 个的"无引用"结论因方法粒度不可信）。"疑似无用"记为**待观察**，不作为删除理由。
- **方法论**："未被加载"的证据强度取决于方法粒度——批量移出的归因是**类级**的，不能用于**逐文件**裁决；要真判单个需逐个移出跑（成本不可接受）。**宁可不删，不留不可信的判定**。
- 复跑确认：后端全量 **4713** 用例绿。

**📊 现状总账（2026-10-02，用户问"所有模块是否都已重构完成"时落纸）**
- **结构重构各轴：全部完成**——
  ① 神类：≥800 行类只剩 **4 个登记例外**（§14.3），各域 ≥800 全为零；
  ② 裸环境变量：代码内 `System.getenv` **0**（B6 十批结项）；
  ③ 包结构/分层：ArchUnit 进 CI（B10）+ 环与分层棘轮；
  ④ 换锚：**注解面真债 0**；payload 键面逐族判定完毕（例外 68 文件/324 键逐条带理由），**换锚棘轮进 CI**（`--strict`）；
  ⑤ 前端契约键：棘轮 42 条已复核豁免，进 CI；
  ⑥ Go 锚点注释：政策＝**随触碰清洗 + 棘轮**（不立专项）；
  ⑦ 存储域：B14/B15 收口（一面一源 + 唯一读写口）。
- **剩余（都不是"重构未完成"，而是已知边界/登记项）**：
  1. `process_overrides` 后端落地——**用户 2026-10-02 定调：暂时不补**（保留现状）；
  2. `JWT_SECRET` 短于 32 字节应**启动期**校验并给明确文案——**登记未做**（现状：登录时才抛 `WeakKeyException`）；
  3. `AuthController` refresh 端点 javadoc 仍用 Go 的 `refresh_token` 字样——**措辞小项**；
  4. `AuthSessionOps.extractSwitchRefreshToken` 读 `refresh_token`（snake）——**兼容读取**：测试与前端都只发 `tenantId`，无仓内使用者 ⇒ 登记不动；
  5. **孤儿夹具 115 个**（B31）——其中 102 个是录制脚本清单（证据链）；**待观察，不删**。

**✅ B33（2026-10-02，包结构守卫红灯修复：两个工具类归位到最低层）**
- **触发**：复测 `scripts/check-package-cycles.py`（挂 CI guards job）为红灯——环 **5 组** / 依赖 `config` 的包 **1 → 11** / L2→L3 新增 1 条。全部由 **B6 批 10（ca86b3b）** 引入（`git grep` 前后对照：该提交前依赖 config 的只有 `stream` 1 个包）。
- **成因**（两处「工具放错层」）：
  - `config/AppEnvLookup`（41 行 env 查找面 holder）被 11 个包、16 处引用——它是平台工具，却被放进组合根 `config`（分层规则：config 只出不进）⇒ 4 组环（auth / common / llm / vectorstore ⇄ config）。
  - `llm/chat/ImageResolver` 为读本地存储根目录引了 `storage/fileserve/StoragePaths` ⇒ `llm ⇄ storage` 环 + L2→L3 直连新增 1 条。
- **修复（两刀，纯移动 + 引用改写，零行为变更）**：
  - 刀 1：`config/AppEnvLookup` → `common/deployment/AppEnvLookup`（与 `DeploymentProperties` 同族：运行环境读取入口）。装配点 `AppEnvLookupEnvironmentPostProcessor` 仍留 `config`（R4 规则要求 install 只许装配层调用，调用点未动）。17 个文件改 import。
  - 刀 2：`storage/config/StorageRuntimeEnv` → `common/storage/StorageRuntimeEnv`（与 `UploadLimits` 同族：跨域共享的存储层配置值）；`ImageResolver` 改直读该 holder（`trim` + 自有 `/data/files` 兜底，与 `StoragePaths.localStorageBaseDir()` 逐字等价——两层兜底归一已核验），去 storage 依赖；storage 域内 5 处 FQN 引用一并 import 化。
- **验证**：编译绿；守卫回绿（环 **0** / 依赖 config **1**（stream，基线内）/ L2→L3 **6** 条）；`ArchitectureRulesTest`（含 R4）+ `AppEnvLookupWiringTest` + `ImageResolverTest` + `StoragePathsTest` + `ChatLocalImageResolverWiringTest` 等 **212** 例绿；全量 **4713** 绿 + `spotlessCheck` 绿。
- **红态证明（天然探针）**：本批起点即闸门红（5 组环），修复后转绿——守卫「会红」由 B6 批 10 的真实引入证明，非空转。
- **纪律注记**：B6 批 10 的提交信息只写了「测试绿」，未跑/未记包结构守卫（教训：涉及新类落点的批次，守卫是必跑闸门，与测试同级）。

**✅ B34（2026-10-02，agent/tools 分包：94 文件单层 → 根 + 5 个能力子包）**
- **触发**：`docs/backend-package-map.md` 登记「`agent/tools`（94）待做——别按前缀切，等 agent 域自身重构时按能力分组」；agent 域自身重构（A/B 波）已于 2026-09-30 完成 ⇒ 前提满足、该待办悬空。
- **分类判据（按调用点定，不按名字猜）**：子包装「族内工具 + 族内辅助」；**根留**三类——① 框架/基建（`ToolRegistry`/`BaseTool`/执行上下文/预算/参数校验/结果持久化）；② 跨域或跨族共享的接缝与值类型（`SearchTarget` 被 chatpipeline/session/evaluation 13 处引用；`SearchAuth` 被 wiki/knowledge/sql/data 共用；`DocChunkSupport` 被 wiki/knowledge 共用）；③ 通用单件工具（`SequentialThinkingTool`/`TodoWriteTool`）。
- **结果**：`wiki/` 30、`knowledge/` 11、`sql/` 5、`data/` 4、`web/` 2；根 42（31 类 + MCP 10 + package-info）。5 个子包各配 package-info；根 package-info 重写（含 MCP 留根原因）。
- **MCP 族留根（本批关键判定）**：`McpCatalog`/`McpDiscoverTool`/`McpExposure`/`McpCatalogPagination` 与 `ToolRegistry` 同包紧耦合——`servers`/`preloadLock`/`preloadStarted`/`authorizeExecution` 等**包内可见成员**、`McpToolWrapper` 的 **protected 成员**互访（编译实测约 40 处）。硬移会强制把内部状态放宽 public（语义不佳）⇒ 整族暂留根，待注册表中的 MCP 段外提后再分组（已写进根 package-info）。
- **可见性放宽（逐条登记，main 8 处声明）**：`SearchAuth` 的 `dedupNonEmptyStrings`/`searchTargetScope`/`searchTargetIsWholeKb`/`Scope`（wiki 工具跨子包调用）、`KnowledgeSearchTool.RecordingSupportHolder` + `MAPPER`（data/sql 工具借用）、`ApprovalBridge` 类 + `toCancellation`（MCP 工具调用）、`DocChunkSupport.goFmtV`。测试侧：`RecordingSupport`/`Tools45cFakes` 放宽 public（跨族测试引用）并放宽其成员。
- **测试随之镜像**：12 个测试跟移到对应子包（wiki/knowledge/sql/data/web）；跨族 `DocToolsRecordingTest` 与 MCP 的 `McpStubABTest` 留根。
- **验证**：编译绿；守卫绿（环 0 / 依赖 config 1 / L2→L3 6 条）；全量 **4713** 绿 + `spotlessCheck` 绿；rename 识别 R093~R099；**忠实性核验**——非 package/import 的改动行只有上述 8 处可见性放宽 + package-info。
- **踩坑实录（harness 复用要点）**：① 先改文本后 `git mv` ⇒ 「同包判定」用旧包名 → 漏加 import；② 删除行后未重算 `last_import` 索引 → import 插进类体（`illegal start of type`），已重置重跑；③ 域外文件的词边界命中含同名类误报（`GoPath` 与 datasource/connector/gitlab 同名、`McpOAuthSupport` 与 mcp/protocol 同名、wiki 域嵌套 `RepairResult`）⇒ 域外只做「import/FQN 路径改写」，不新增 import。

**✅ B35（2026-10-02，`modelcontext` 并入 `agent`：顶层包 31 → 30）**
- **拍板**：用户 2026-10-02 定「并入 agent」（§3.5 登记的两个选项之一）。
- **执行**：`git mv` 顶层 `modelcontext/`（13 文件）→ `agent/modelcontext/`（test 3 文件同移）；全仓 23 个文件的 `com.ragagent.modelcontext` → `com.ragagent.agent.modelcontext`（含正文 FQN 与 javadoc `{@link}` 引用，零残留）。
- **语义**：模型输出上下文协议层（`SourceRegistry` / `HandleTable` / `StreamDecoder` / `ToolPolicy` / `ModelOutput` / `GoHtml` 等），依赖 llm(12) + common(6)；消费方 = agent(3) / chatpipeline(2) / session(2)。
- **agent 根 package-info 更新**（补子包地图：`tools` / `compaction` / `skills` / `domain` / `support` / `modelcontext`）。
- **验证**：编译绿；守卫绿（环 0 / 依赖 config 1 / L2→L3 6 条——chatpipeline→agent 与 session→agent 均单向，无新增）；全量 **4713** 绿 + `spotlessCheck` 绿。

**✅ B36（2026-10-03，`agentm` 并入 `agent`：顶层包 30 → 29）**
- **拍板**：用户 2026-10-03 定「agentm 合并到 agent」。
- **形态**：`agentm`（20 文件，标准五件套）→ `agent/management/`（子域形态，先例 = `auth/apikey/`）；agent 域由此获得唯一 HTTP 面（此前是纯引擎域）。
- **执行**：① `git mv` 三处——main 包 / test 包 / **资源目录** `resources/agentm` → `resources/agent/management`；② 全仓替换 `com.ragagent.agentm` → `com.ragagent.agent.management`（**61 个 java 文件**，含 MyBatis `typeHandler=` 注解串里的 FQN）；③ 资源路径字符串 `"agentm/` → `"agent/management/`（10 处，跨 7 个 loader 类）；④ 文案清理 12 处（javadoc/注释，含 `{@code agentm.service.X}` 简写形态）；⑤ 脚本 3 处（`acceptance.sh` 批次域列表去 agentm/modelcontext、`record-w5b-golden.sh` 注释路径）。
- **顺手修正**：`AsrTestAudio` 的错误文案写的是 `agentm/asr_test.wav`，实际资源在 `initialization/asr_test.wav`（既有笔误）。
- **残留（刻意保留）**：3 处历史说明（`agent/management`、`agent`、`initialization` 的 package-info 提到"由 agentm 并入/拆出"）。
- **验证**：编译绿；守卫绿（环 0 / 依赖 config 1 / L2→L3 6 条——embed/im/session/auth 对 `agent.management` 的引用属 L3→L3 域间，无新增违例）；全量 **4713** 绿 + `spotlessCheck` 绿。
- **无需改动项（已核对）**：`@ConfigurationPropertiesScan` 名单不含 agentm；`@MapperScan("com.ragagent.**.mapper")` 通配；资源目录改名后全部 loader 路径同步（核心 prompt 装载有测试覆盖）。

**🚧 B37（2026-10-03，档 3 第一刀：provider 请求面退役 Go 转义复刻）**
- **背景**：用户 2026-10-03 拍板开始「档 3」（彻底退役 Go 字节兼容层，25 个 `Go*` 类 / 2,798 行）。
- **第 1 步产出——字节流向盘（四类流向）**：① **provider 请求体**（低风险，本刀已退役）；② **Redis 事件**（`stream/StreamJson` 的 `GoJsonEscapes`——类注释明说「事件落 Go 与 Java **共用**的 Redis 键，跨语言 CAS 靠字节比对」⚠️ **前置问题：Go 版是否还在运行**）；③ **落库/响应**（`GoTimeSerializer` 含业务语义 `isGoZero` 零值时间判定、`GoJsonBindError` 是前端可见文案）；④ **工具输出/协议**（`GoJsonCodec`/`GoQuoting`/`GoHtml`/`GoValueStr`——进 LLM 提示词与 MCP 文案）。
- **本刀动作**：三份 provider 副本（`embedding/GoJson`、`rerank/GoJson`、`websearch/provider/GoJson`）去掉 `setCharacterEscapes(new GoJsonEscapes())`——provider 接受标准 JSON，语义等价；**探针先行**（先只改 embedding 跑该域绿，再同批改另两份）。
- **保留**：`GoJsonEscapes` 类本身（仍被 stream / langfuse / McpCatalog / `RemoteApiBodyCodec` 使用）。
- **验证**：三域测试绿；全量 **4713** 绿 + `spotlessCheck` 绿。
- **待决/待设计**：stream 面（Redis 跨语言 CAS）需确认「Go 版是否还在跑」；`GoTimeSerializer.isGoZero`（业务语义）与 `GoMapSerializer`（被 datasource 继承、`GoDoubleSerializer.format` 被当静态工具调）需单独设计退役方案，不能直删。

**✅ B38（2026-10-03，档 3 第二刀：stream 事件 / langfuse / LLM 请求体退役 Go 转义）**
- **关键决策登记（用户 2026-10-03 确认）**：**Go 版已下线、不再双跑** —— Go 字节对齐从此不再是活跃约束（stream 的 Redis 跨语言 CAS 前提解除；§15.3「Go 工具面 5 类」的冻结理由只剩"已实现行为不宜无理由变化"）。
- **本刀动作**：① `stream/StreamJson` 去 `GoJsonEscapes`（**保留** map 键排序与时间/容错配置——内部 CAS 用「读到的原文比对槽位」，需要稳定字节）；② `llm/chat/RemoteApiBodyCodec` 的 `GO_MARSHAL` 改标准 JsonMapper（保留两条键序归一：prompt-cache 与结构体声明序）；③ `tracing/langfuse/LangfuseAttributes` 去 escapes（上报面）。
- **测试改写（4 处「与 Go 字节对齐」断言 → 「字节稳定 + 标准 Jackson 形态」）**：`StreamJsonTest` 三例（改名 `eventBytesStableWithSortedKeys` / `stringEscapingIsStandardJackson` / `writeStringProducesQuotedJson`）+ `RedisStreamManagerTest`（`eventsRoundTripThroughRedisWithStableBytes`）；内部 CAS 依赖的「稳定字节 + 键序」性质全部保留。实测标准 Jackson 形态：`< > &` 原样、控制字符大写十六进制（`\u001F`）。
- **全局面核实（重要）**：`main` 侧 `setCharacterEscapes` 调用点收窄到 **4 处**（`event/EventJson`、`agent/tools/McpCatalog`、`agent/tools/TodoWriteTool`、`retrieval/support/GoJsonUtil`）；**HTTP 响应面的全局 mapper 本就是标准 Jackson**（从未装 escapes）；`MemoryEntityJsonTest` 注释提到的「JacksonConfig 会装 GoJsonEscapes」是过时表述（`JacksonConfig` 类已不存在）。
- **验证**：stream/tracing/llm.chat 三域绿；全量 **4713** 绿 + `spotlessCheck` 绿。
- **残留（下一批候选）**：上述 4 处 escapes（流向 = **内部事件总线 + 工具输出**，属 ④ 类；MCP/待办工具进 LLM 提示词，需探针）。

**✅ B39（2026-10-03，档 3 第三刀：工具面退役 + 发现「实录基线」硬约束）**
- **本刀退役**：`McpCatalog.GO_ENCODER`（保留插入序=struct 契约）、`TodoWriteTool`（steps_json）、`retrieval/support/GoJsonUtil` 三处去 `GoJsonEscapes`；测试侧 `SessionStreamControllerTest` 去不再需要的 escapes 配置 + `MemoryEntityJsonTest` 修正过时注释（`JacksonConfig` 已不存在）。
- **⚠️ 关键发现（档 3 的真正边界）**：`GoRecording45A/B/C`、`GoRecording46B/C` 等「Go 形态实录」文件**由录制脚本生成、头部明示禁止手改**，且录制源是 Go 服务（**已下线**）⇒ **无法重录**。凡被这些实录覆盖的输出路径，退役前必须先立起「基线重建机制」：
  - 实测：`event/EventJson` 去 escapes → 8 例 `EngineRecordingTest` 红；测试侧 `RecordingSupport` 去 escapes → 3 例 `ToolRegistryRecordingTest` 红——**两处已回退**（回退后全量复绿），`EventPayloadJsonTest` 的断言改写与 `GoJsonEscapes` 类删除一并回退。
  - **结论**：`GoJsonEscapes` 类本体**暂不能删**（`EventJson` 与 `RecordingSupport` 仍需引用）。
- **档 3 现状总账**：① provider 请求面 ✅（B37）；② stream / langfuse / LLM 请求体 ✅（B38）；③ 工具面（MCP / 待办 / 检索）✅（本刀）；④ **待解锁**：event 总线与所有被实录覆盖的面（需先做「实录基线重建」）；`GoTimeSerializer.isGoZero`（业务语义）与 `GoMapSerializer`（被继承）仍待单独设计。
- **验证**：全量 **4713** 绿 + `spotlessCheck` 绿（含回退后的复跑）。

**✅ B40（2026-10-03，实录基线重建——Go 转义复刻全量退役，`GoJsonEscapes` 类删除）**
- **前置**：用户拍板「档 3 做完」（B39 报告的选项 A）。真实边界＝「改完基线从哪来」：`GoRecording*` 实录由录制脚本生成、禁止手改、录制源（Go 服务）已下线 ⇒ 无法重录。
- **解法（不改实录，改比对方式）**：把实录比对从「逐字节」升级为**语义比较**——复用本仓 B2 批已确立的 `ContractJson.deep`（其 javadoc 早已写明「HTML 转义（`\u003c` vs 字面字符）不再构成断言目标」；Go 实录的比对是**漏网的字节级对比**，本批一并收编）。
- **落点**：① `event/EventJson` 去 escapes（事件总线退役；javadoc 四条→三条）；② 测试侧 `RecordingSupport.GO_MAPPER` 改标准 mapper；③ `EngineRecordingTest` **62 处** `assertThat(X).isEqualTo(GoRecording…)` 批量改写为 `assertRecording(X, …)`（两侧 `ContractJson.deep` 归一）；④ `ToolRegistryRecordingTest.assertResult` 辅助内两侧归一（一处覆盖 3 用例）；⑤ `EventPayloadJsonTest` 5 处期望值改标准形态（实测控制字符为**大写**十六进制）；⑥ **删除 `GoJsonEscapes` 类 + `GoJsonEscapesContractTest`**（全仓零残留）。
- **验证**：全量 **4709** 绿（较基线 −4 = 删除的契约测试用例）+ `spotlessCheck` 绿。
- **档 3 总账（escapes 面全清）**：① provider 请求 ✅（B37）｜② stream / langfuse / LLM 请求体 ✅（B38）｜③ 工具面（MCP / 待办 / 检索）✅（B39）｜④ event 总线 ✅（本批）＝ **`GoJsonEscapes` 面全部退役完成**。
- **剩余（非 escapes 面，待各自探针）**：`GoTimeSerializer.isGoZero`（业务语义，38 处调用）、`GoMapSerializer`（被 datasource 继承 + `GoDoubleSerializer.format` 被当静态工具调）、`GoJsonCodec` / `GoQuoting` / `GoHtml` / `GoValueStr`（工具协议面）。

**✅ B41（2026-10-03，档 3 第四刀：provider JSON 四副本收敛 + 删零引用类）**
- **收敛**：4 份包内副本（`embedding/GoJson` 86 行 / `rerank/GoJson` 66 行 / `websearch/provider/GoJson` 57 行 / `retrieval/support/GoJsonUtil` 39 行）→ 共享 `common/web/ProviderJson`（embedding 版为超集：`marshal` / `object` / `array` / `parse(String|byte[])` / `arrayOfStrings` / `floatArray`）。
  - 42 文件引用改写（`\bGoJson\b` 词边界替换——不误伤 `GoJsonCodec` / `GoJsonValues` / `GoJsonMarshal` / `GoJsonBindError`）+ 20 文件补 import（原同包引用现跨包，含 3 个 `package-info`）。
- **删零引用**：`GoFloatSerializer`（`GoDoubleSerializer` 的 float32 孪生，全仓 0 处引用，70 行）。
- **Go\* 类计数：26 → 21**。
- 验证：编译绿；全量 **4709** 绿 + `spotlessCheck` 绿。

**✅ B42（2026-10-03，档 3 第五刀：工具协议面——转义退役 + 四类去 Go 名）**
- **分类先行的发现**：A 类 6 个「工具协议面」实分两族——① **真·字节复刻**（`GoJsonCodec` / `GoValueStr` / `GoJsonMarshal` / `GoQuoting`）；② **只是名字带 Go**（`GoHtml` = HTML 实体处理、`GoJsonValues` = JSON 语义小工具，功能通用）。
- **改名（22 文件改写）**：`GoHtml` → `HtmlEntities`、`GoJsonValues` → `JsonValues`、`GoJsonCodec` → `ToolJson`、`GoQuoting` → `JsonQuoting`（后者是「JSON 双引号字符串」最小实现，非 Go 特有）。**行为未退役的类不改名**（`GoValueStr` / `GoJsonMarshal` 留原名——名字如实反映仍是 Go 语义）。
- **转义退役**：`ToolJson.writeString` 去掉 HTML 转义（`< > &` 原样输出）+ 控制字符大写十六进制；**保留**键排序（LLM 载荷确定性）与浮点形态（属高风险 C 类，另议）。
- **测试收编**：`RecordingSupport.normalizeEscapes`（共享辅助：实录文本的 Go 转义形态还原）；`DataAnalysisRecordingTest` output 断言两侧归一（`<nil>` 的 `\u003cnil\u003e` 字形）；`GoJsonCodecRecordingTest` → `ToolJsonRecordingTest`（对比升级为 `ContractJson.deep` 语义比较 + **键序单独钉住** `keysAreSortedAlphabetically`）。
- **Go\* 类计数：21 → 17**。
- **验证**：agent + wiki 探针绿；全量 **4710** 绿（+1 = 新增键序用例）+ `spotlessCheck` 绿。
- **⚠️ 口径修正（2026-10-03 用户质疑后回写）**：上列「改名」**不计退役进度**——它只修正两个误标类（`HtmlEntities`/`JsonValues` 本非 Go 复刻），`ToolJson`/`JsonQuoting` 当时**仍属待退役**。真实待退役 = 17 个仍叫 `Go*` 的 + `ToolJson` + `JsonQuoting` = **19 个**（而非 17）。**教训：改名≠退役；档 3 的完成标准是「Go 字节兼容层不存在」（行为换标准或类删除），不是名字消失。**

**✅ B43（2026-10-03，档 3 第六刀：工具协议面真退役——Java 原生替换，删三类）**
- **前置**：用户拍板「不要只是改名，用 Java 原生方式处理」——本批按原定档 3 套路（行为换标准 / 删类）执行。
- **退役动作**：
  - `JsonQuoting` **删除** → `ToolJson.quoted`（标准 Jackson `writeValueAsString`）；顺带收敛**第 5 份拷贝** `WeaviateGql.quoteGo`（4 处包装 + 1 处拷贝 → 一处）。
  - `GoValueStr` **删除** → Java 原生字符串化：`EntityExtraction.valueStr`（标量 `asText` / 容器 JSON 形态 / null → `"null"`）；`PluginSearchEntity` 内联 2 行；`<nil>` 惯用法退役。
  - `GoJsonMarshal` **删除** → `ToolJson.prettyJson`（Jackson `writerWithDefaultPrettyPrinter`）；`toJsonArray` 改 `compactJson`。
  - `ToolJson` 手写 writer 退役：删 `writeNode` / `writeNumber`（Go 浮点形态）/ `writeString`（手写转义）→ 标准 Jackson 序列化 + **仅保留递归键排序**（确定性需求，非 Go 复刻）。
- **测试收编**：`ParamCasterRecordingTest` / `GrepChunksRecordingTest.assertToolResult` 改 `ContractJson.deep` 语义比较（吸收数字形态差异）；`PipelineLifecycleRecordingTest` 三处 `assertRec` 改「本仓标准形态」基线（人工核验后硬编码）+ `render` 基线。
- **Go\* 类计数：17 → 15**（删 `GoValueStr` / `GoJsonMarshal`；`JsonQuoting` 删除后 `ToolJson` 完成退役、不再计为待退役对象）。
- **验证**：四域探针绿（含 9 例实录红的收编）；全量 **4710** 绿 + `spotlessCheck` 绿。

**✅ B44（2026-10-03，档 3 第七刀：connector 面三刀——Java 原生替换）**
- `GoBase64`（gitlab，180 行）**删除** → `java.util.Base64.getDecoder()`：GitLab 的 base64 每 60 字符换行，先去换行再解码；非法字符由 `IllegalArgumentException` 报出（原 Go 的 `CorruptInputException` 字节偏移诊断属复刻面、不保留）。提取包私有 `GitLabClient.decodeBase64Content` 保行为可测。
- `yuque/GoDuration`（169 行 Go duration 文法）**删除** → `Double.parseDouble` + `Duration.ofNanos`（与 `feishu/FeishuTransport#parseRetryAfter` 同款——两 connector 收敛）。**行为差异登记**：`"90m"` 不再解析为 90ms（回落 fallback）、`"1e2"` 按 Java 语义解析为 100 秒；测试改名为 `parseRetryAfterParsesSeconds` 并表达新契约。
- `common/time/GoDuration`（71 行 Go 时长格式化）**删除** → 标准 `Duration.toString()`（ISO-8601）；`ThinkPhase` 的 stall 文案与 `HousekeepingService` 的 stuck 文案改标准形态（`1h10m0s` → `PT1H10M`）。
- **测试收编**：`GoCompatTest` 删 base64 的 Go 对照段（含 20 条错误偏移表）→ 换 `base64SkipsLineBreaks` 行为用例；`YuqueClientTest` 5 个 Go duration 用例 → 1 个参数化新契约；`HousekeepingServiceTest` 文案断言更新。
- **Go\* 类计数：15 → 12**。
- **顺带发现（登记待退役）**：`LangfuseConfig.parseGoDurationMs` + `TenantInvitationService.parseGoDuration` + `HousekeepingServiceTest.documentProcessTimeoutFromEnvParsesGoDurations` —— **「Go duration 解析」另有 2-3 份副本**（配置 / env 面）。
- **验证**：datasource / agent / knowledge 探针绿；全量 **4706** 绿（−4 = 退役的 Go 对照用例）+ `spotlessCheck` 绿。

**✅ B45（2026-10-03，档 3 第八刀：GoStrings×2 收敛——Unicode 空白实现统一）**
- **收敛**：两份 `GoStrings`（`common.wiki` 96 行 + `gitlab` 82 行）**删除**，16 文件 / 45 处调用改写 → 新的 **`common/text/Whitespace`**（`isSpace` / `trimSpace`；Java 原生组合：`Character.isSpaceChar` + 6 个 ASCII 控制字符 = Unicode White_Space 精确等价）+ **`common/text/CodePointOrder`**（码点序比较，自 wiki 版迁出——Java 无该语义的标准 API，保留自实现：UTF-16 码元序对 emoji 会与实际顺序分歧）。
- **实现统一**：原两份**不一致**——wiki 版显式列举码点（精确）、gitlab 版 `isWhitespace` + 4 缺口（**多算 U+001C–U+001F**）；统一后以精确实现为准（gitlab 面对含这些控制字符的输入行为微变，实际不可能出现）。
- **`trim(x, '/')` 退役**：仅服务"去首尾斜杠"的两处 → `replaceAll("^/+|/+$", "")`（Java 原生正则一行）。
- **Go\* 类计数：12 → 10**。
- **顺带发现（登记）**：Unicode 空白判断全仓原共 **5 处**——除已收敛的 2 份外，还有 `rss/RssUtil.isGoSpace`（少 U+0085）、`MemoryScopes.isGoSpace`、`MemoryText.isGoSpace`（后两者为 `isSpaceChar`+6 写法，与新 `Whitespace` 同源）；下一批委托收敛（约 20 处调用）。
- **验证**：wiki / datasource / memory 三域探针绿；全量 **4706** 绿 + `spotlessCheck` 绿（`spotlessApply` 修正 import 序）。

**✅ B46（2026-10-03，档 3 第九刀：GoUrl / GoPath 验证驱动裁决——保留）**
- **方法**：写临时探针（跑完即删）对高风险面做**逐字符/逐样例对照**，用数据决定退役或保留。
- **`GoUrl`（219 行）→ 保留**。证据（95 位可打印 ASCII 全表）：
  - `pathEscape` vs Spring `UriUtils.encodePathSegment`：**7 处差异**（`! ' ( ) * , ;`——Go 转义、Spring 保留；`/`→`%2F` 两边一致）；
  - `queryEscape` vs `URLEncoder`：**2 处差异**（`*` 被转义、`~` 被编码）；
  - `pathUnescape` vs `UriUtils.decode`：**完全一致**。
  - 裁决理由：差异字符都是 RFC 3986 的 sub-delims（路径段内合法），**大概率安全但无真实 GitLab 环境可验证**（契约测试是 stub）；替换会改变出站 URL 字节（该类的存在意义即"GitLab 对 `files/<path>` 段的解码规则"），收益（删类）小于风险。**保留，待有真实环境时重评**。
- **`GoPath`×2 → 保留**：
  - `agent/tools/GoPath`：javadoc 已登记理由——"POSIX 斜杠语义的纯字符串实现；**不能用 `java.nio.file.Path`（平台相关）**"，服务 sandbox 路径校验（安全面）。
  - `gitlab/GoPath`（204 行）：`clean` 与 `Path.normalize()` 对照 **21/24 一致**，3 处差异全是"空结果"（Go `.` vs Java `""`，含 `""` 输入——**替换会让 GitLabConfig 的路径校验放行空串**，安全退步）；`join` 的空元素语义服务落库 key 构造（`docs-` / `-main` 退化形态）。**保留**。
- **`GoStyleErrorReportValve` → 误标（非退役对象）**：它是"容器级协议拒绝的纯文本报文"契约（golden `emb-pub-load-badvisitor`），Tomcat 默认是 HTML 错误页——**Java 无原生等价**，名字里的 Go 指历史来源。**保留**，与 `HtmlEntities` / `JsonValues` 同列"误标待统一修正"。
- **档 3 收口口径（本批确立）**：剩余类的处理标准从"Go\* 计数归零"改为 **"逐类裁决"**（退役 / 误标 / 契约保留，每类都要有数据或既有论证支撑）；无证据可证的类**不因名字带 Go 而强删**。
- **验证**：探针删除后编译绿；datasource 域复跑绿。

**✅ B47（2026-10-03，保留类改名——契约保留类的标签修正，行为零变更）**
- **前置**：用户拍板——B46 裁决"保留"的类，名字里的 `Go*` 在误导后人（暗示"Go 复刻、别动"），应修标签。**行为一字未动**（B46 已裁决保留）。
- **改名（~85 处引用替换 + 4 处类头 javadoc 补命名说明）**：
  - `GoUrl` → **`GitLabUrl`**（GitLab 出站 URL 转义原语）；
  - `GoPath`（gitlab）→ **`GitLabPath`**（项目/文件路径 POSIX 清洗与连接）；
  - `GoPath`（agent/tools）→ **`PosixPath`**——顺带**归位 `common/text`**（与 `Whitespace` / `CodePointOrder` 同族；服务 sandbox 路径校验）；
  - `GoStyleErrorReportValve` → **`PlainTextErrorReportValve`**（协议层拒绝的纯文本报文）；
  - 测试类 `GoCompatTest` → `GitLabCompatTest`。
- **口径**：**误标修正，不计退役进度**（与 B42 的教训区分：那次是拿改名冒充退役；本次是已裁决保留类的标签修正，用户主动要求）。
- **验证**：datasource / agent / embed 三域探针绿；全量 **4706** 绿 + `spotlessCheck` 绿。
- **现状**：`Go*` 名字只剩 C 类 5 个（`GoDoubleSerializer` / `GoTimeSerializer` / `GoMapSerializer` / `GoJsonBindError` / `GoNaiveOffsetDateTimeTypeHandler`）+ `GoogleProvider`（误报，是 "Google"）。

**📋 B48（2026-10-03，档 3 第十刀：C 类 5 个影响评估——决策材料，未改代码）**
- **侦察面**：5 类全部位于 `common/web`；引用跨 20 包；测试面分别 7 / 11 / 1 / 2 / 0 文件。
- **逐类评估**：
  1. **`GoDoubleSerializer`（138 行）**：double 输出对齐 Go（`0` 而非 `0.0`、`1e-7`、`1e+21`、最短往返）。引用 12+ 文件（租户配置 `RetrievalConfig`、数据源序列化、langfuse 上报、agent 工具参数）。**退役影响：JSON 语义等价**（`0.0` 与 `0` 同为数值零、`1.0E21` 与 `1e+21` 同值），仅**字节形态**变 → 需更新 7 个测试文件的 golden。**可退役，收益=删类+统一为标准形态**。
  2. **`GoTimeSerializer`（87 行）**：时间序列化 + **零值时间哨兵**（未赋值 → `"0001-01-01T00:00:00Z"` 而非 `null`——Java 侧 `null` 会凭空少一个字段）。引用 12+ 文件（datasource 全域）。**业务语义，保留**。
  3. **`GoMapSerializer`（110 行）**：Map 键序递归排序（服务「模型返回的嵌套 map 键序不稳定」→ prompt 字节确定性）。引用 10+ 文件。**退役影响：键序变 → prompt 前缀缓存失效**（与 B43 保留 `ToolJson` 键排序同一理由）→ **保留**；⚠️ **登记潜在重复**：与 `ToolJson.sorted`（B43）是"同一需求的两个实现"。
  4. **`GoJsonBindError`（135 行）**：把 Jackson 绑定错误仿真成 Go 措辞（golden 锁字节，如 `invalid character 'o' in literal null (expecting 'u')`）。引用 11+ controller。**退役影响：前端可见错误文案变化** → **需产品确认**。
  5. **`GoNaiveOffsetDateTimeTypeHandler`（50 行）**：naive 时间列的 `OffsetDateTime` 读写（Go/pgx 语义：写墙钟、读按 UTC）。引用 2 文件（session `TemporaryDocument`）。**退役影响：存量数据语义**（读出的时间值会变）→ **保留**。
- **结论**：5 类性质 = **1 可退役**（`GoDoubleSerializer`，语义等价）+ **4 契约保留**（时间哨兵 / 键序确定性 / 前端文案 / 存量数据语义）。
- **待决策**：① `GoDoubleSerializer` 退役做不做（语义安全、需 golden 更新）；② `GoJsonBindError` 需产品/前端确认文案契约。

**✅ B50（2026-10-03，档 3 第十二刀：GoDoubleSerializer 退役——数字形态换 Java 标准）**
- **用户拍板**：退役（B48 评估：JSON 语义等价、仅字节形态变）。
- **退役动作**：删类（138 行）；11 处静态调用 `format(x)` → `Double.toString(x)`（DataSourceMapSerializer / ParamCaster / ParamValidator / DocChunkSupport / ConversationSerializer / OllamaManageService×4）；2 处模块注册删除（`EventJson` 的 Double+Float 模块、`LangfuseAttributes` 的 Double 模块）；`GoNumber` → `RawNumber`（内部类去 Go 名）；顺带 `GO_ZERO_TIME_LITERAL` → `ZERO_TIME_LITERAL`、`GO_ZERO_INSTANT` → `ZERO_INSTANT`。
- **新形态**：`0` → `0.0`、`1e+21` → `1.0E21`、float→string `"42"` → `"42.0"`。
- **测试收编（本档最宽的一批，11 文件）**：`EventPayloadJsonTest` 90 处断言改 `assertSemantic`（语义比较）；`DataSourceJsonTest` / `DataSourceRepositoryTest` 期望更新为标准形态（原「不得出现 3.0/1.0E21」断言反转）；`ParamCaster` / `ParamValidator` / `ToolRegistry` / `ConversationSerializer` 四测试新增共享辅助 **`RecordingSupport.normalizeNumberText`**（数字 token 级归一：两侧统一到 Java `Double.toString` 形态、含科学计数；带词边界断言避免误伤 `\\u003c` 转义序列）；删除 `GoDoubleSerializerTest`（20 例 Go 形态语料）；`Engine46bStubSupport.goDouble` → `doubleText`。
- **发现（javadoc 过时）**：`RetrievalConfig` / `SearchResult` 声称"浮点字段挂 GoDoubleSerializer"**但实际从未挂载**——它们本就是 Jackson 默认行为（B48 评估里"前端契约 `0` vs `0.0`"的提示有误，实际无此契约）。
- **Go\* 类计数：10 → 9**。
- **验证**：event / agent / datasource / initialization / tracing 五域探针绿；全量 **4678** 绿（−28 = 删除的 Go 语料用例）+ `spotlessCheck` 绿。

**✅ B51（2026-10-03，档 3 第十三刀：GoJsonBindError 退役——绑定错误换 Jackson 标准消息）🎉 档 3 收官**
- **用户拍板**：换成 Java 标准（B48 评估待决项②；用户即产品方，直接拍）。
- **退役动作**：删类（135 行 Go 措辞仿真：`EOF` / `invalid character 'x' in literal null (expecting 'u')` / `... looking for beginning of value` / 字面量尾随扫描）；33 处调用点改 Jackson 原生：
  - `GoJsonBindError.message(rawBody, e.getMessage())` → `e.getMessage()`（20+ 处）；
  - `"EOF"`（空 body 硬编码，10+ 处）→ **`No content to map due to end-of-input`**（Jackson 标准消息）；
  - `QaRequestBinder` 的字段级类型错误仿真段（`valueKind` / `fieldTypeError`）整段删除 → 直接用 Jackson 消息。
- **新文案（用户可见）**：空 body → `No content to map due to end-of-input`；坏 JSON → `Unrecognized token 'not': was expecting (...)`（含 `at [Source: REDACTED …]` 尾注）；字段类型错 → ``Cannot deserialize value of type `long` from String …``（**camelCase 字段名保留**）。
- **收编**：18 个 golden 经 `-Dcontract.refresh=true` **重录**（本仓既定机制）；另 5 个（不走 `GoldenContract` 的）手工更新；`QaRequestBindingTest` / `TenantAPIKeyControllerTest` / `SteerContractTest` / `KnowledgeQaContractTest` 断言与方法名改（`…KeepsGoJsonWording` → `…UsesJacksonWording` 等）。
- **验证**：全量 **4678** 绿 + `spotlessCheck` 绿。
- **🎉 档 3 收官**：仓库里 `Go*` 类名**只剩 `GoogleProvider`（误报——那是 "Google"，非 "Go 复刻"）**。25 个 `Go*` 类的最终去向：**12+ 真退役**（去 escapes / 换 Jackson / 收敛副本 / 删类）、**5 个误标改名**（HtmlEntities / JsonValues / GitLabUrl / GitLabPath + PosixPath / PlainTextErrorReportValve）、**4 个契约保留并改名**（ZeroTimeSerializer / SortedMapSerializer / NaiveOffsetDateTimeTypeHandler / 码点序等语义资产）、**2 个拍板退役**（GoDoubleSerializer / GoJsonBindError）。

**✅ B49（2026-10-03，档 3 第十一刀：保留类去 Go 名——第二批）**
- **用户拍板**：① `GoDoubleSerializer` 退役；② `GoJsonBindError` 换成 Java 标准；③ 其余保留类名称去掉 Go。
- **本批（③ 的类名部分）**：`GoTimeSerializer` → **`ZeroTimeSerializer`**、`GoMapSerializer` → **`SortedMapSerializer`**、`GoNaiveOffsetDateTimeTypeHandler` → **`NaiveOffsetDateTimeTypeHandler`**（含 `"typeHandler=<FQN>"` 字符串形态）；顺带方法/常量名去 Go：`isGoZero` → `isZeroValue`、`GO_ZERO_DATE_TIME` → `ZERO_DATE_TIME`、`isGoZeroTime`（rss/datasource 的零值判断）→ `isZeroTime`。70 文件替换，**行为零变更**。
- **登记**：零值时间判断现存 3 处实现（`ZeroTimeSerializer.isZeroValue` / `RssUtil.isZeroTime` / `DataSourceSupport.isZeroTime`）——潜在收敛点。
- **验证**：datasource / session / llm / auth 四域探针绿。


**✅ B48（2026-10-03，注释大清洗专项——Go 锚点/翻译腔/过期引用全仓归零，B9 专项化收官）**
- **规模与终态**：起步基线扫描 11,932 匹配行 / 1,723 文件（main 9,058 + test 2,874；口径=对照 Go/波 N/Go 的/GORM/.go 引用/实录·照抄·复刻等翻译措辞/§·B 批次码/agentm·modelcontext 旧包名）；收官 main+test 棘轮基线 **3,999 → 0**（`check-go-anchors.py --write` 已刷新），base 模式余量 876 行**全部为白名单类**（见下）。
- **口径（先摘不变量，再删锚点）**：① 纯考古删（.go 文件行号/迁移号指针/波次·批次码/实录编号/`/tmp` 探针路径）；② 不变量中性改写（「Go 的 len() 是 UTF-8 字节数」→「按 UTF-8 字节数计」；「GORM 隐式行为清单」→「落库行为清单」逐条保留；「照抄/直译/复刻/对拍」去属性留行为）；③ 过期引用修（旧信封响应形态、旧包名、`§x.y` 悬空指针）；④ D 类错误陈述就地核实修正——典型：favorite 控制器类注释仍描述去信封前的 `{"data":…,"success":true}` 形态、`McpHttpContractTest` 键名方向写反（camelCase 断言配 snake 注释）、`RssCursorJsonTest` 时区描述与断言矛盾、`ElasticsearchV7` 命中类型标注与向量回填两处「照抄缺陷」注释滞后于已修代码。
- **裸形态盲区补扫**（base grep 抓不到，第一批实测反馈后并入协议）：`Go X:`/`照 Go`/`近似 Go`/`Go 注释原文`、不带 Go 字样的 Go 专名指针（mapper 层「对照 {@code Pluck("id", &survivors)}」「OnConflict DoUpdates」等）、`omitempty/nil/len/rune/ctx` 词汇 → Java 本位（恒输出/null/长度/码点/上下文）。
- **白名单五类（留档不追）**：① 域词——wiki ingest「批次」、句柄「翻译」（cite 管道运行时语义）、「可移植」（SQL 方言）；② `@DisplayName` 注解串与 `.as()`/assertEquals 断言消息（约 200 处，改则动契约）；③ 方法/类名标识符（`xxxMatchesGo`、`goFormatFloat`、`tierToGo`、`isGoSpace` 等）；④ 有效交叉引用（契约文档 §1.x/§2.x、`docs/known-issues`、本仓 Java 符号）；⑤ `GoRecording*`/`GoJsonBridge` 录制夹具（B39 已定禁止手改）与 `EventPayloadJsonTest` 64 条 `// Go: {…}` 期望形状（文件头注明保留为历史记录）。
- **执行方式**：逐域清单 + 并行 agent 批（main 21 域 + test 27 清单，~20 批）+ 小域内联；与档 3（B37~B47）及批量清洗提交 `ad704ca7`（1,338 文件，并行会话）**同窗交叉**，分发清单三次过期——收尾批改用「开工实时 grep」为准；档 3 退役留下的悬挂 `{@link GoXxx}`（约 15 文件）按 C 类过期引用清。
- **闸门与顺手修复**：compileJava/compileTestJava 绿；spotlessCheck 绿（顺手清 6 个测试文件的未用 import——并行功能提交遗留；修 1 处编辑事故 doubled `*/`，spotless 抓住）；全量 `:server:test` 绿（BUILD SUCCESSFUL）；棘轮绿。会话期间用户限速/配额三度打断，中断批以「协议 grep 逐文件自查」无损续跑。
- **待人工复核（3 项）**：① `SearchResult`/`MessageSearchGroupItem` 的 score 注释已按 GoDoubleSerializer 退役后的 Jackson 默认（1.0 形态）改写——若 SSE/接口 golden 仍按旧最短表示断言，需随档 3 口径重录；② `ModelParametersTypeHandler` 解密失败分支为空 if（注释改「保留观测点」），是否应有日志待定；③ `WikiPageRepository` 零值 page_type 落 `''` 而非 DDL `'summary'` 的语义差异已中性登记，是否属行为变更待裁决。

**✅ B52（2026-10-04，B48 排查批——MCP 工具审批事件流断链修复 + 三项疑点复核）**
- **断链实锤与修复**：B48 顺手发现 `AgentStreamBridge` 用 `instanceof event.payload.*` 过滤审批事件，但 `Gate` 构造的是 `common.approval.*` 同名 record，`ApprovalBridge.toEventBus` 又原样转投真实总线——**三类事件（tool approval required/resolved、mcp oauth resolved）的 instanceof 永不命中**，web 聊天流的审批卡静默消失（唯一幸存的 `MCPOAuthRequiredData` 是 `McpOAuthSupport` 直构 payload 形态）。修复＝在 `ApprovalBridge` 补 `toPayloadData` 四对 DTO 映射（approval 包 record → event.payload 线格式 bean，未知形态透传）；`event.payload` 类的 javadoc 本来就登记自己是 Gate 发射的线格式，转换只是迟到了。用户仍可经审批面板（`/mcp-services/{id}/tool-approvals` 列表 + resolve 端点）完成审批——这就是断链一直没被冒烟抓到的原因。测试侧此前 Gate 与 Bridge 各测各的，无接线测试。
- **测试**：新增 `ApprovalBridgeTest` 4 用例（required 全字段映射、resolved/oauth required/oauth resolved 转换、未知透传、null bus）；agent/common.approval/mcp/session 四域回归绿。
- **三项疑点复核（B48 待人工复核项，全部闭环）**：① score 字节形态（`"score":1` vs `1.0`）——`ContractJson` 语义比较器把整值浮点归一（`d == Math.rint(d)` 即按整数比），金片绿是真绿，字节变化对 JSON 消费方透明，**无需动作**；② `ModelParametersTypeHandler` 解密失败的空 if「观测点」——按 house 惯例（`DataSource`/`APIPrincipalConfig`/`WebSearchParams` 三处同类均为宽容置空、不记日志）**删除死分支**，注释并入口径说明；③ wiki 零值 `page_type` 分叉——四处创建点（`WikiPageServiceImpl`×2 / `WikiIngestReducePhase` / `AgentToolWikiBackends`）**全部显式 setPageType**，`''` 落库分叉不可达，**无需动作**。
- **闸门**：ApprovalBridgeTest 4/4 绿 + model 域绿 + spotlessCheck 绿。
- **教训**：两个包各有一个同名 DTO（`ToolApprovalRequiredData` 等），桥接层"原样转投"时类型失配是静默的——接线处（桥/适配器）必须对数据体做**类型验收**或单测锁形态，instanceof 防御分支吞掉不匹配事件时至少要 debug 日志。

**✅ B53（2026-10-04，走查修复：知识面卡片视图模型键 snake→camel 收口 + 失败原因接上接口）**
- **触发**：点检中用户问 `DocumentCardView.vue` 里的 `original_file_name` / `display_name` 这类 snake 键是否正常。逐个定性（写侧 / 读侧 / 接口实际键）：① `original_file_name`、`display_name` 是**前端内部视图模型键**（`useKnowledgeBase` 把接口项映射成卡片模型时写入，读侧仅下载名解析与标签编辑弹窗，写读同一套 ⇒ 功能正常，但属 Go 时代命名遗留）；② `error_message` 是**死字段**——接口下发的是 camel `errorMessage`，而前端全仓既无人写、也无人读（卡片接口声明后即被遗忘）。
- **用户拍板**：内部视图模型键一并 camel 收口；失败原因接上接口。口径确立：契约纪律只管对外 JSON 键名，**内部视图模型同样不留两套命名**（两套命名正是本仓多次「读错键、静默 undefined」故障的同源温床）。
- **收口（5 文件）**：写侧 `hooks/useKnowledgeBase.ts`（唯一生产者）`original_file_name` → `originalFileName`、`display_name` → `displayName`；`errorMessage` 靠该层已有的 `...item` 直通，**不建映射层**。读侧同步：`views/knowledge/knowledgeDownloadFileName.ts`（+ 其单测）、`KnowledgeBase.vue`（标签编辑弹窗 `knowledge-name`）、以及 `DocumentCardView.vue` / `KnowledgeBase.vue` 两处重复的卡片类型声明。
- **接线**：卡片悬停浮层的失败态新增原因行（读 `errorMessage`）。动机是实锤：**紧凑模式时间线不渲染 `lastError`**（`knowledge-processing-timeline.vue` 的 `v-if="compact"` 分支只有阶段点 + 耗时），故修复前卡片表面没有任何失败原因出口。样式沿用描述行排版（红色、三行截断 + 原生 title 显示全文），零新增 i18n 文案（文本来自后端）。
- **守卫（第 4 条 / 第三类形态）**：`components/crossFaceKeyContract.test.ts` 新增「知识面卡片视图模型：键一律 camel」——扫描 `views/knowledge/**` + `hooks/useKnowledgeBase.ts` 不得出现三个旧键名；同时钉住写侧必须提供 `originalFileName` / `displayName`、下载解析必须读 `originalFileName`、卡片必须消费 `errorMessage`（只删旧名不补新名同为断链）。**红态探针**：注入 `error_message` → 该用例红且报出文件与建议键名；还原 → 绿。
- **验证**：前端全量 **704/704**（原 696 + 其间提交进来的 7 条守卫 + 本批 1 条）、`vue-tsc --build` 0 错误、`scripts/check-fe-contract-keys.py` ✓ 无新增（基线 42 条）。
- **边界（未动）**：`types/tool-results.ts` + `views/chat/components/tool-results/*` 的 `error_message` / `summary_error_message` 属**工具结果载荷面**的另一套约定，需单独核实后再议，本批不碰。

**✅ B54（2026-10-04，api 面 snake 键收口——12 处真断链 + 裁撤死参数收尾 + 守卫扩面棘轮）**
- **触发**：B53 之后用户连续追问「前端还有哪些 snake 应该改 camel」→ 从「卡片视图模型」一路查到 **api 线格式面**。逐个定性（前端声明/读取 vs 后端真实键）后发现：api 面不仅有「死声明」，还有**成片的静默断链**。
- **修复（12 处，全部为「接口下发 camel / 前端读或发 snake」）**：
  - **改密（实测恒失败，用户可见）**：`api/auth` 的 `ChangePasswordRequest` + `views/settings/UserProfile.vue` 提交处送的是 `old_password/new_password`，后端 record 是 `oldPassword/newPassword` → 无损探针：snake body 恒 400 `newPassword/oldPassword: 不能为空`；camel body 正常报 `Current password is incorrect`。已改 camel。
  - **auth 时间键**：`userInfoFromApi` 读 `user.created_at/updated_at`（接口只给 camel）→ 兜底 `|| new Date().toISOString()` 把「注册时间」写成**当前时刻**（用户可见）；`activeTenant` 类型两键；`InviteLookup.expires_at` → `expiresAt`。
  - **邀请面 6 键**：`invitee_email/invitee_name/inviter_email/inviter_name` + `responded_at/created_at`（同接口 `invitedBy`/`expiresAt`/`isShareLink` 本就是 camel）。消费点 5 处（`TenantMembers.vue` 4 + `MyInvitationsDialog.vue` 1）：修前「邀请人」列回落显示**用户 ID**、成员表「被邀请人邮箱」行**恒不显示**、撤销确认弹窗显示 ID。实测响应键全 camel（`invitee_email` 不存在）。
  - **wiki 修订**：类型 `edit_source/editor_id/edited_at/page_id` + `WikiRevisionDrawer.vue` 4 处消费点 —— 该消费点是**修完类型后 `vue-tsc` 报错抓出**的（修订列表的编辑来源/时间此前恒空），提醒：类型修正本身就是探测器。
  - **KB 复制 / 标签排序**：`copyKnowledgeBase` 的 `source_id/target_id` → camel（探针：snake → 400 `sourceId: 不能为空`；camel → 404 正常解析；该函数目前无调用方，属待接线代码，键先对齐）；两个标签接口的 `sort_order` → `sortOrder`（调用方只传 `name`，暂未触发）。
  - **清理（死读 + 兜底掩盖）**：`knowledge-processing-timeline.vue` 去掉 snake 时间戳读取（有 camel 兜底故无感）、`manual-knowledge-editor.vue` 去掉 `parsed.updated_at` 兜底。
- **已核实为合法（不动，登记理由）**：`file_types`（后端 `ParserEngineRules` 按 snake 读规则 jsonb）；`tag_ids/start_time/end_time`（KB 列表筛选**查询参数**，后端按 snake 接收）；embed `channel_id/session_id`（宿主↔iframe 消息协议，两侧一致）；`chat-history` 的 `embedding_model_id/knowledge_base_id`（后端 `node.path(...)` 读 jsonb）；`modelUsage` snake 载荷（后端 `putObject` 构造）；SSE/事件面、系统设置与第三方（Ollama `modified_at`）。
- **裁撤死参数收尾**：`api/knowledge-base` **5 个接口**（列表 / 按 id 取 / 详情 / 批量 / 搜索）透传的 `agent_id`/`agent_source_tenant_id` 全删；`WikiKbAccessGuard` 描述已裁撤共享 agent 的过期 javadoc 删除。**根因**：空间分享裁撤（`d4d63e09`）按「消费点清单」清理（视图层传递链 + 后端读取分支都清了），**漏了夹在中间的 API 封装层**；且调用方已不再传参 → 死代码静默存活 4 天（TS 可选参数不报错、守卫不管"没人用的参数"）。
- **守卫（第 5、6 条）**：`crossFaceKeyContract.test.ts` —— ⑤ api 面 `*_at` 一律 camel（含 auth 映射钉住，防「注册时间」再退回）；⑥ **api 面 snake 记号棘轮**：32 键基线（8 键已核实合法 + 24 键标「待核实」）+ 5 个面级白名单（chat / system / initialization / retrieval / modelUsage）+ **只许减**（清理后不删基线行会报过期）+ 扫描前剥离字符串字面量（避免把枚举值 `'rbac.member_added'` 当键）。**红态探针两轮**：首版锚定行首 → 漏检行内对象字面量（`{ some_new_key: 1 }` 探针未红），收紧为 `(?<![\w$.])…` 后精确报出 `api/mcp-service.ts:some_new_key`。
- **验证**：前端全量 **706/706**、`vue-tsc --build` 0 错误、`check-fe-contract-keys.py` 无新增（基线 42 条）、后端 `compileJava + spotlessCheck` 绿（javadoc 改动）；守卫红/绿态各验一次。
- **遗留（B55 候选）**：棘轮基线里 24 处「待核实」需逐条对后端核实（agent 配置/类型过滤 8、auth 空间知识库摘要与部署开关 9、wiki 页面树/问题载荷 6、mcp `require_approval` 1）。本轮已证明该清单里**藏着真断链**（改密恒失败、KB 复制键错），建议下一批按「读/写方向 + 真实接口实测」逐条给出结论。

**✅ B55（2026-10-04，api 面 snake 记号逐条核实——32 键全部定性：13 改 camel / 6 删死字段 / 13 已核实合法）**
- **方法**：对 B54 冻结的 32 个 `file:token` 键逐个查「后端 DTO 字段名 / 显式 `put(...)` 的键 / `JsonNode.path(...)` 读取 / 真实接口实测」四类依据；有消费点的用无损探针（错凭据、不存在的 id、写后回读）坐实。
- **改 camel 13 键（6 处有真实消费点 = 用户可见故障）**：
  - **偏好 `last_activeTenant_id` → `lastActiveTenantId`**：后端 `UserPreferences` 只有 camel 字段。探针：snake PUT 返回 200 但 `preferences` 仍 `{}`；camel 立即写入（已验并还原）→ 修前「刷新 / 换设备回到上次空间」**永不生效**。
  - **`oidc_only_login` → `oidcOnlyLogin`**：`UserProfile.vue` 用它做改密门禁 → 恒 false（OIDC 专用账号仍被引导改密）。
  - **wiki 六键**（`page_count`/`has_children`/`familiar_count`/`issue_type`/`suspected_knowledge_ids`/`reported_by` → camel）：后端 `WikiFolderNode` / `WikiPageIssue` / `WikiGraph.Meta` 全是 camel Java 字段直出（无 `@JsonProperty`）。消费点 13 处 → 修前**文件夹页数恒 0、问题类型标签与举报人永不显示**、图 meta 熟悉度写错键。类型修正后 `vue-tsc` 直接报出消费点行号（再次验证：类型本身是探测器）。
  - **`require_approval` → `requireApproval`**：`McpTestResultBody.vue` 的「需人工审核」开关读它（`vue-tsc` 抓出）→ 修前开关初始态恒 undefined（后端已开启也显示关闭）。
  - **KB 摘要四键**（`creator_id`/`creator_name`/`chunk_count`/`document_count` → `creatorId`/`creatorName`/`chunkCount`/`knowledgeCount`）：实测 KB 对象键；注意线上是 **`knowledgeCount`**（`documentCount` 根本不存在），且 4 键当前无消费点（仅注释提及）。
  - **`is_default` → `isDefault`**：实测 `/api/v1/models` 下发 `isDefault`。
- **删死字段 6 键**：agent `reflection_enabled`（后端配置白名单不含、仅前端一个默认值）、`sandbox_config_id`（后端注释：随沙箱裁剪退役）、`welcome_message`（后端不产出、无消费点）；建议问题接口的 `knowledge_base_ids`（实现从未发送）；auth `browser_search_instructions`（后端注释：随浏览器连接裁撤）、`UserInfo.knowledge_bases`（后端 user 载荷无此键、无消费点）。
- **已核实合法 13 键（基线留档，逐条依据）**：`kb_filter`/`any_of`/`all_of`/`none_of`（后端 `AgentTypePresets` 从预设 JSON `item.putObject("kb_filter")` 原样透出）、`file_types`（`ParserEngineRules` 按 snake 读规则 jsonb）、`owner_id`（前端本地快照键）、chat-history 2 键（`node.path(...)` 读 jsonb）、embed 2 键（宿主↔iframe 消息协议）、KB 列表筛选 3 个查询参数。
- **守卫**：棘轮基线 **32 → 13 键**（只许减，每条附判定依据）；红态探针复验：回流 `page_count` → 精确报 `api/wiki/index.ts:355`，还原即绿。
- **验证**：前端全量 **706/706**、`vue-tsc --build` 0 错误、`check-fe-contract-keys.py` 无新增；api 面已无未定性 snake 记号（残留 13 键 = 基线本身）。
- **教训**：① `vue-tsc` 是这类断链的天然探测器——改类型，真消费点立刻报错；② 无损探针（错凭据 / 不存在的 id / 写后回读）能在不动数据的前提下坐实键名方向；③ 死字段与真断链要分开处理：前者删、后者改键并补消费点。

**✅ B56（2026-10-04，技能区文案纠正 + 死键清理 + 宿主技能目录 dev 配置）**
- **触发**：点检问「技能管理为什么没有了」。查明＝**计划内裁撤**（2026-09-28 用户定稿的第一批功能裁剪五项，PR3 `caef9d5`「移除沙箱 + 技能降级为指令型」）——前端删 `SkillSettings`/`SkillFilesPanel/Drawer`/`SkillInstallTimeline`/`EnvVarSettings`/`Sandbox*` 共 30 文件（含 `api/skill`、`api/env-vars`），技能能力保留、降级为指令型（SKILL.md 提示词注入）。
- **本批改动**：① `agent.editor` 技能区 4 条**在用**文案仍是沙箱/安装期口径（"先选择运行沙箱…没装的会显示「安装」"）→ 五语言改为指令型口径（技能=宿主目录指令包、勾选即注入）；② 删 **19 个零引用死键 × 5 语言**（`skillsNeedSandbox`/`installToThisSandbox`/`installShort`/`viewInstallProgress`/`skillNotInstalled`/`skillNotReady`/`skillDisabledOnSandbox`/`sandboxBackend*`×4/`sandboxNoConfigs`/`goSandboxSettings`/`goSkillSettings`/`skillsGroupAvailable`/`Unavailable`/`skillsInfoTitle`/`Content`/`selectSkills`——`settings.sandbox/skills` 块当时删了、`agent.editor` 块漏网）；③ dev 基建：新建宿主技能目录 `/Users/billy/weknora-host-skills`（2 个示例技能：`kb-faq-curator`（含 references 二级文件）、`wiki-style-reviewer`）+ `.env` 配 `WEKNORA_SKILLS_HOST_DIRS` + `dev-env.sh` 按 key 读取并导出；④ 更正 `SkillsCatalogController` javadoc 写错的 env 名。
- **踩坑（已记录）**：env 名必须是复数 **`WEKNORA_SKILLS_HOST_DIRS`**（Spring 把属性 `weknora.skills.host-dirs` 的 `.`/`-` 换成 `_` 再大写）；写成单数**不报任何错**，接口只回 `{"skills":[],"skillsAvailable":false}`（原 javadoc 恰好写错成单数，是这次踩坑的源头）。
- **验证**：i18n 审计 11/11、前端全量 706/706、`vue-tsc` 0 错误、后端 `compileJava + spotlessCheck` 绿；重启后 `GET /api/v1/skills` 实测返回 2 个技能（`skillsAvailable:true`）。
- **后续（B57）**：用户拍板技能改为**入库（DB）存储**（理由：未来多实例部署）+ **弃用宿主目录** → 本批的 ③（宿主目录 dev 配置）与 ④（该控制器 javadoc）将被 B57 取代并清理，此处留档。

**✅ B57（2026-10-04，技能管理回归：指令型技能入库 + 平台级 CRUD + 宿主目录退役）**
- **决策链**：点检追问「技能管理为何没了」→ 查明＝09-29 功能裁剪（技能降级为指令型、数据源为宿主目录）→ 用户要求恢复「技能管理」（查看/新建/删除）→ 方案三稿收敛：v1 出方案 → v2「**复用原接口** + 改指令型」→ v3「**入库**」。用户拍板五项：① 平台级 SystemAdmin；② 载荷 camel；③ 只在线填写（不做 zip/文件入库）；④ 删除硬拒 + `force` 强删；⑤ **入库**（理由：未来多实例部署），并明确 A（DB 落地后弃用宿主目录）、B（不导入存量技能）。
- **数据层**：`migrations/versioned/V4__skills.sql` 新增 `skills` 单表（平台级、软删 `deleted_at`、部分唯一索引只约束未删行以便同名重建）。主键按仓库既有约定用 `varchar(64)`——首版写成 `uuid` 被 PG 拒绝（`column "id" is of type uuid but expression is of type character varying`；同批未发布，改 V4 定义 + 重置空表与 flyway 记录后重跑，仓库口径「未上线可直接改基线」）。
- **运行期**：新增 `DbSkillSource implements SkillSource`（读表 → `Skill.parseSkillFile` 复用**同一套**解析/校验；每轮装配新建实例、单次 SELECT，因此新建/删除对**下一轮对话**生效，无需缓存失效）；`Manager` 数据源由 `Loader(dir)` 泛化为 `List<SkillSource>`（多源先到先得、单源失败不阻断）；装配处注入技能目录服务，读取失败**降级 + error 级留痕**（沿用"目录打错字不 500"口径）。
- **接口（复用裁剪前路径，载荷改 camel）**：`GET /api/v1/skills`（Viewer 选择器）改读表，形状不变；新增 `SkillCatalogController`：`GET/POST /api/v1/skills/catalog`、`DELETE /{id}?force=`、`GET /{id}/files`、`GET /{id}/files/content?path=`；**不实现** `POST /{id}/install`（指令型无安装）。写入契约：调用方只给 `{slug,name,description,content}`，**frontmatter 由服务端组装**（单引号 YAML 标量）后自校验；`requireRuntimeIdentity` 专门拦下会被运行期**静默改名**（如带空格→回落 slug）的名称——否则「管理页显示名」与「运行期身份（`selectedSkills`）」分叉，勾选后注入不上。RBAC：catalog 全端点 `addSystemAdminRule`（平台级资源，租户角色不放行）；审计：新增 `skill.created`/`skill.deleted`（后端常量 + 前端注册表 + 英文默认 + 五语言扁平标签）。
- **退役（用户选 A）**：删 `agent/skills/Loader.java`；`SessionAgentQaService` 去掉 `@Value(weknora.skills.host-dirs)` 与 `parseHostSkillDirs`；`AgentConfigAssembler` 去 `hostSkillDirs`；`QaAgentConfig` 删 `skillDirs`；`dev-env.sh` 去该 env 注入并注明退役；本地 `.env` 删除该键（仓库外）。B56 的③④随本批作废（记录留痕）。
- **前端**：`api/skills.ts` 扩 CRUD（camel）；新增 `views/system/SkillManagement.vue`（列表 / 新建对话框（实时校验 + SKILL.md 预览）/ 只读详情抽屉 / 删除确认——被引用时展示引用清单并要求"仍然删除"二次确认）；纯逻辑抽 `skillManagementForm.ts`（slug 与名称规则**严格对齐后端**、预览组装、删除影响面）+ 7 条单测（并抓到 `/[\p{L}]/` 缺 `u` 标志的真实类型错误）；挂进设置→系统管理组（`settingsAccess.ts` 的 `SYSTEM_ADMIN_SETTINGS_SECTIONS` + 其测试期望 + `Settings.vue` 导航与 section）；五语言 `skillManagement.*` 38 键。
- **验证**：后端**全量 4,702 测试 0 失败 + spotlessCheck 绿**（新增 13 条技能单测）；前端 **713/713**、`vue-tsc --build` 0 错误、`check-fe-contract-keys.py` 无新增。**真实接口实测**（SystemAdmin 账号）：新建 201 → 选择器立即可见 → `files`/`files/content` → 非法 slug **400** → 重复 slug **409** → 建引用探针 agent（列表 `referencedBy` 命中）→ 无 force 删除 **409 + 引用清单** → `force=true` **204**；审计落库实测 4 行（`skill.created`×3 + `skill.deleted`×1，target_type=skill）；库内留两个演示技能供点检。
- **点检当场暴露的一处漏挂（用户报「没看到技能管理菜单」）**：设置页的一个 section 要真正出现在菜单里，需要**三处**同时登记——① 权限集合 `config/settingsAccess.ts` 的 `SYSTEM_ADMIN_SETTINGS_SECTIONS`；② `Settings.vue` 的 `navGroups` 里 `pickItems([...])` **显式分组清单**；③ 内容容器（`currentSection === 'x'` 的 div + 组件 import）。本批做了①③、漏了② → 页面能直接访问、权限也放行，但**菜单永不渲染**（`navGroups` 用 `pickItem` 从 `navItems` 里挑选，没登记就等于不存在）。已补②，并新增守卫 `views/settings/settingsNavGroups.test.ts`（3 条：navItems 声明的 key 必须落在某个分组、权限集合的每个 section 必须出现在「系统管理」分组、分组 key 不重复；**红态探针**：摘掉 `'skill-management'` 即红并点名该 key）。教训：**「加一个设置 section」是三步动作，少任一步都是"静默无入口"**——与 B53/B55 的"写入侧有、读取侧漏"同族。
- **页面样式两处修复（用户点检反馈）**：① `section-description` 无样式——这类 `.section-*` 类**没有全局定义**，各页需在自己的 scoped 块里自备（本页漏了，已按系统管理组样板补：14px / secondary / max-width 560px / text-wrap pretty，并把 h2 对齐 20px·600·primary）；② 描述列悬浮气泡文字看不清——**TDesign 的 `ellipsis` 气泡直接复用单元格 VNode**（`es/table/components/ellipsis.mjs` 的 `content: () => cellNode`），原先包了一层 `color: secondary` 的 `<span>` → 灰字被带进深色气泡（实测底色 `rgb(36,36,36)`）。改法：去掉插槽与带色 span，次要色下放到**列级 `className`**（`:deep(.sm-desc-cell)`，只作用在 td 上）。**规范：表格列内不要包带颜色的 span（凡该列可能 ellipsis 截断）。**用本机已有 playwright（rev 1223 与 ms-playwright 缓存匹配，零下载）实测复核：`.section-description` 计算样式 14px/secondary/560px、气泡内为纯文本且白字深底、控制台 0 报错。
- **遗留**：① 附随文件（Level 3）、在线编辑 SKILL.md → 1.5 期；② 空间级技能（各空间自管）需加 `scope/tenant_id` 列 + 装配分层，属二期；③ 技能选择器在"全部技能"模式下会自动纳入新技能（`mode=all`），删除保护已计入该隐式引用。

**✅ B59（2026-10-04，技能编辑：PUT 更新 + 改名保护 + 前端编辑弹窗）**
- **需求**：用户要求「『查看内容』改为『编辑』，可编辑更新 skill；点击编辑和**新建一样弹窗**，修改后保存」。
- **后端**：① `PUT /api/v1/skills/catalog/{id}`（SystemAdmin）收 `{name, description, content}`，**slug 不可改**（接口寻址与去重键；改名语义 = 删旧建新），写入前重新组装 SKILL.md + 复用同一套 `Skill.parseSkillFile` 自校验 + `requireRuntimeIdentity`，`version` 自增、`updated_at` 刷新；② **改名保护**：name 是运行期身份（agent 配置的 `selectedSkills` 存的就是它）→ 被引用时改名返回 **409 + 引用清单**（新增 `RenameWhileReferencedException` 与纯逻辑 `requireRenameAllowed`；**同名不算改名**，故只改描述/正文永远放行）；③ `GET /api/v1/skills/catalog/{id}` 作**编辑草稿**：`content` 是**正文**（`parse(...).instructions` 剥掉 frontmatter，与组装严格可逆——单测钉住这条可逆性）；④ 审计新增 `skill.updated`（后端常量 + 前端注册表 + 英文默认 + 五语言扁平标签）。
- **前端**：① `api/skills.ts` 加 `getSkillCatalogItem` / `updateSkill`；② 行内「查看内容」→「**编辑**」，新建/编辑**共用同一弹窗**（`editorMode`）：编辑态打开时拉草稿回填（加载态 + 失败提示）、slug 只读灰显并提示不可改、**改名被引用时行内红字报错并禁用「保存」**（前端先行拦截，后端 409 仍兜底），移除只读详情抽屉；③ 纯逻辑抽 `validateSkillForm(values, mode)`（编辑态跳过 slug 校验）、`formFromDetail`、`toUpdatePayload`、`describeRenameBlock`（与后端同判据），单测 7 → **10 条**；④ 五语言删 4 个只读键（view/detailTitle/detailHint/loadingContent）、加 9 个编辑键。
- **验证（浏览器端到端实测，本机已有 playwright 复用缓存 chromium、零下载）**：① **回填**：slug 只读（disabled）+ 名称/描述/正文（`# Wiki 页面校对…`，247 字）全部带出；② **保存**：改描述 → 保存 → 弹窗关闭 + toast「技能已更新」→ 库内 `version+1`、审计落 `skill.updated`、重载列表显示新描述（实测数据事后还原为原演示文案）；③ **改名拦截**：把 `kb-faq-curator` 改成 `-renamed` → 行内红字「该技能正被 1 个智能体引用：Smart Reasoning…」且「保存」置灰（截图留档）；④ curl 侧：无引用改名 200（`rename-probe`→`rename-probe-v2`）、被引用改名 **409 + details**、只改描述 200、选择器联动（name 未变仍在列）。
- **闸门**：后端 **4,704 测试 0 失败 + spotlessCheck 绿**；前端 **720/720**、`vue-tsc` 0 错误、i18n 审计 11/11、契约键守卫无新增。
- **观察（供后续判断）**：`kb-faq-curator` 现被内建 **Smart Reasoning** 引用（`selected` 模式点名，用户点检时在智能体编辑器勾选的）→ 该技能将无法改名、删除需 force；这正是保护的设计意图（否则该 agent 会静默失去技能）。若希望"改名时同步改写引用它的 agent 配置"，属另一批（需要动 agent config 迁移策略）。

**✅ B60（2026-10-04，技能租户化：平台级 → 空间级 + 菜单并入「数据与扩展」）**
- **触发**：用户提出「把技能调整配租户配置，菜单移到数据与拓展」。核实后确认这不只是"更合理"，而是**在修隔离缺陷**：`GET /api/v1/skills` 是 Viewer 级且 `listActive()` 读全表、零租户过滤 → 任何空间的普通成员都能看到**别家的技能名与描述**；且 `slug` 全局唯一 → A 空间建过的 slug，B 空间不能再用。
- **方案（用户拍板"全做"）**：① 租户化（`tenant_id` 迁移 + 运行期按租户过滤 + RBAC 改空间 admin + 引用查询加租户条件）；② **保留平台内置层**（`tenant_id IS NULL`：全员可见、**只读**）；③ 存量归当前空间；④ 菜单挪到「数据与扩展」。
- **数据层**：`V5__skills_tenant.sql` 加 `tenant_id INTEGER`（NULL = 平台内置）；旧全局 slug 唯一索引退场 → `(COALESCE(tenant_id, 0), slug) WHERE deleted_at IS NULL`（平台层折叠成伪空间 0，故平台内置与各租户可同名 slug）；另加 `tenant_id` 部分索引。
- **服务层（`SkillCatalogService`）**：**可见范围单点表达** `visibleScope`（平台层 or 本空间；`tenantId == null` → **仅平台层**，fail closed —— 缺上下文绝不等于"看全部"）；读路径全部带租户（`listVisible` / `findVisibleById`，他空间 id 等同不存在 → 404）；写入 `requireTenantScope`（缺上下文不许建，否则等于造官方预置→越权）+ `requireTenantWritable`（平台行只读 → 403）；**命名冲突**：`name`（运行期身份）在可见范围内必须唯一（`SkillNameConflictException`，区分"与平台内置重名"/"本空间重名"话术），`slug` 同命名空间唯一。
- **引用清单**：按空间过滤 **且把 `is_builtin` 也算入** —— 实测发现内建 agent 是**全局一行**（`builtin-smart-reasoning` 的 `tenant_id=11`，取自首个物化它的空间），只按 `tenant_id = ?` 过滤会**漏掉它**，导致改名/删除漏报真正受影响的 agent。调用点：`AgentEngineAssembler`（运行期注入）与 `SkillsCatalogController`（选择器）都改为按当前空间读。
- **RBAC/审计**：catalog 规则由 `addSystemAdminRule` → `rbac.addRule(..., TenantRole.ADMIN)`（含新增的 `GET /{id}`、`PUT /{id}`）；审计 details 增 `tenantId`。
- **前端**：`settingsAccess.ts` 把 `skill-management` 从 `SYSTEM_ADMIN_SETTINGS_SECTIONS` 移到 `SETTINGS_SECTION_MIN_ROLE = 'admin'`（+测试期望）；`Settings.vue` 从「系统管理」组挪到「数据与扩展」组（`settingsNavGroups.test.ts` 守卫同步校验）；页面：平台内置行显示「内置」标记并**只给「查看」**（弹窗三模式 create/edit/view，view 态字段全禁用、无页脚），租户行保持 编辑/删除；五语言 `description` 改空间级口径 + 新增 `builtin`/`view`/`viewTitle`。
- **验证（跨租户隔离是核心验收，全部实测）**：① 迁移与归属：flyway v5 ✓，存量 4 行（含 2 条软删）归到空间 1，空间 1 目录 2 条、引用恢复 `Smart Reasoning`；② **第二空间**（`POST /auth/register` → 自动建租户 12）：目录与选择器**均为空**（此前会看到空间 1 的技能）；建**同名 slug** → **201**（此前 409）；跨空间按 id 直取 GET/PUT/DELETE **全 404**；两边的引用清单互相隔离；③ **平台内置层**（SQL 造 `citation-norm`）：两空间均可见且 `readOnly: true`，PUT/DELETE **403**，编辑草稿可读，选择器可勾选；④ **UI 浏览器实测**：「技能管理」位于**数据与扩展**组（该组：向量数据库引擎/解析引擎/存储引擎/网络搜索/MCP服务/技能管理）、内置行仅「查看」且查看态字段全禁用无页脚、**另一空间 owner（非系统管理员）同样可见**（证明 RBAC 已落到空间 admin）、控制台 0 报错。
- **闸门**：后端 **4,708 测试 0 失败 + spotlessCheck 绿**；前端 **720/720**、`vue-tsc` 0 错误、i18n 审计 11/11、契约键守卫无新增。
- **dev 数据留档**：平台内置示例 `citation-norm`（只读演示，可一行 SQL 删除）；隔离探针账号 `b60probe@test.local`（空间 12，owner）——保留用于后续隔离回归。
- **遗留**：平台内置层目前**无 UI 入口**（预置 = SQL/发布动作），将来做"平台管理端技能页"再接；"改名时同步改写引用它的 agent 配置"仍未做（现为 409 硬拦）。

**✅ B61（2026-10-04，技能按需读取打通 + @点名注入正文：read_file 工具 + skill_instructions 段）**
- **触发**：用户追问「Agent 是如何加载 skill 的？会先注入名称和描述、按需加载正文吗？」→ 核查发现**设计是三级渐进披露，但只有 Level 1 是通的**：① `skill://` 全后端只有 3 处提及，**全是"告诉模型去读"的提示词文案，没有任何解析器**；② `read_file` 是沙箱绑定工具（`AgentEngineAssembler` 里那条 case 是 `continue`，注释写明 dev 无沙箱恒跳过），而沙箱包已随裁剪退役 → **全仓无实现类**；③ `Manager.loadSkill/readSkillFile/listSkillFiles/getSkillInfo` **零调用者**（B57 建好读面后工具侧从未接上）。即"选项 B 指令型技能"里**注入正文这一半从没生效**。用户拍板 **A+B**。
- **A：`SkillReadFileTool`（`read_file`）**：契约**以 Go 录像为准**（`GoRecording45C` 的 31 条 `read_file/*`）——schema 与描述逐字复用 `description_skills_noshell`；输出三段式（`=== File: … ===` / `size=…, returned=… bytes` / 围栏内容）；data 键 `path/file_path/root/skill_name/size/returned_bytes/start_line/end_line/total_lines/truncated[/next_offset]`；错误文案逐条对齐（越界 = `skill resource must have a canonical relative file path without traversal`、名单外 = `skill "x" is not available to this agent`、技能关 = `skills are not enabled for this reader`、形态错、`line_offset` 仅 web、空 path、无源）。**行数语义也对齐**（尾随换行不额外算一行 → 12 字节的 `run.py` 是 `total_lines=1`）。**本部署无沙箱**：非 `skill://` 路径按录像 `exec_no_source` 明确报错（不假装读到）。注册时机：技能启用时由装配器注册（沙箱回归时需与 workspace 版 `read_file` 合并，注释已写明）。
- **B：@点名技能的正文注入**：新增系统提示词段 `skill_instructions`（`<skill_instructions source="selected_for_this_turn">` + 每个 `<skill name>正文</skill>`）；`PromptAssembly.resolvePinnedSkillInstructions` 从技能目录解析正文，**读不到就不注入**（该条回退按需读取，不让技能读取失败拖垮整轮）；`must_use` 措辞随之切换：注入成功 → `Apply the instructions of @Skill "X" (provided in <skill_instructions>)`，失败 → 保留 `Must call read_file(...)` 回退。顺带修：pinned 技能此前**描述传空串**（`PinnedSkillInfo(name, "")`）→ 现取元数据里的真描述。
- **验证**：① 单测 12 条：`SkillReadFileToolTest`（8：schema/描述逐字、入口与附随文件（含嵌套）的输出与 data、四类越界、形态错、名单外、技能关、无源、分页与字节预算、URI 纯逻辑）；`SkillPinnedInstructionsTest`（3：解析/回退/措辞）；`AgentPromptsTest` +1（注入段渲染与 XML 转义）。② **真实回合端到端**（记录型桩替换 chat 模型 + 既有桩 rerank）：模型收到的**工具清单含 `read_file`**、系统提示词含 **Level 1 目录段 + `skill_instructions` 注入正文**；桩回 `read_file(skill://kb-faq-curator/SKILL.md)` → **真实工具执行**，第二次请求的 tool 结果 = `=== File: skill://kb-faq-curator/SKILL.md ===\n\nsize=559 bytes…```\n# 知识库 FAQ 整理…`（格式与录像一致）；must_use 显示 B 分支措辞；SSE 含 `tool_call`/`tool_result`/`answer`/`complete`。
- **闸门**：后端 **4,720 测试 0 失败 + spotlessCheck 绿**；前端无改动（契约键守卫无新增）。
- **探针清理与环境**：记录型桩（/tmp，已停本进程）；`b0-stub-chat` baseUrl **已还原**为 `127.0.0.1:18090`；3 个探针 agent 已删。**新增 dev 便利件 `dev-stub-rerank`**：租户 1 此前**没有 Rerank 模型**，agent 模式对话会直接报 `rerank model is not configured`（本次验证时临时补的桩模型，留着方便后续自测）。
- **遗留**：`read_file` 的 workspace / `web://` 分支仍无实现（需沙箱或网页存储回归）；技能附随文件（Level 3）随 A 一起可用（`skill://<name>/<rel>` 已通）。

**✅ B62（2026-10-04，WeKnora Cloud 整功能裁撤）**
- **决策**：用户点检后要求「去掉 WeKnora Cloud 设置」。核查发现它**不只在设置页**（还是模型提供商之一、解析引擎之一、VLM/embedding/rerank 三处适配器），于是把范围摆成三选项请用户拍板 → 用户选**整功能裁撤**（干净、不留死链；dev 无存量数据）。
- **后端（35 文件；整文件删 8）**：删 `WeKnoraCloudService` / `WeKnoraCloudController` / `WeKnoraCloudStatusResponse` / `WeKnoraCloudCredentialsRequest` / `WeKnoraCloudProvider` / `WeknoraCloudEmbedder` / `WeknoraCloudSign` / `WeknoraCloudReranker`；去分支：`llm/provider/ProviderRegistry`（注册 + `allProviders` 序 + `weknora.weixin.qq.com` 探测）、`ProviderName`（枚举项——声明序即对外顺序，删**最后**一项最安全）、`ProviderBaseURLs`、`ProviderAdapters`（整块 `WeKnoraCloud` 适配器 + 注册项 + 三处 javadoc）、`ProviderAdapter.AuthCreds`（`appId/appSecret` 的**唯一消费者**是云适配器 → 缩成 `apiKey`，`RemoteHttpOps` 同步）、`RemoteApiChat`（AppID/AppSecret 构造期校验 + 两个随之变死的字段）、`EmbedderFactory`/`RerankerFactory`、`model/service/ProviderRegistry`（云目录条目）、`ModelRuntimeFactory`（云校验 + 租户凭据回落 → 方法改名 `modelCredentials`，只留模型级凭据）、`ModelConnectivityTestService`（3 处租户云凭据解析退场）、`VlmClient`（`VlmConfig.appId/appSecret`、`isWeKnoraCloud`、`predictWeKnoraCloud`、`effectiveCloudModelName`、`WEKNORA_CLOUD_VLM_PATH`、`Transport.postWithHeaders` 及其 `VlmHttpTransport` 实现）、`ParserEngineRegistry`（引擎常量 + 注册块（含"去设置"指引文案））、`SystemController`（`weknoracloud_app_id` override 注入 ×2 + `tenantWeKnoraCloudAppId`）、`WebConfig`（2 条 RBAC + 拦截器 pathPatterns）、`APIKeyRoutePolicies`（2 条路由）。
- **前端（20 文件；整文件删 2）**：删 `views/settings/WeKnoraCloudSettings.vue`、`utils/weknoraCloudModels.ts`；`Settings.vue`（导航项 / 分组 `models_runtime` / section 容器 / import / 自定义 W 图标）、`settingsAccess.ts`（section 角色项）、`api/model`（云凭据与状态接口）、`ModelEditorDialog.vue`（云提示块、`wkcCredentialState`、校验与禁用分支、3 处 `v-if="provider !== 'weknoracloud'"`、样式）、`ParserEngineSettings.vue`（云凭证状态块、`wkcState`/`checkWkcStatus`/`goToWkcSettings`、引擎顺序与文档映射、两处样式）、`thinkingControl(.test)`、`ModelSettings.vue` 注释、`i18n/localeKeyAudit.test.ts` 引擎名清单、**五语言**（删 `weknoraCloud.*` 整块 + 解析引擎条目 + `capabilityManageModelsHint` 里的提法）。
- **存量数据**：dev 库实测**零存量**——`tenants.credentials`（`? 'weknoracloud'`）、`parser_engine_config`、`knowledge_bases.chunking_config`、`custom_agents.config` 全无命中；迁移里从未建过云表/键 ⇒ **无需数据迁移**（引擎可用性原本就由 `tenants.credentials.weknoracloud.app_id` 推导，随代码一并消失）。
- **测试**：删 `VlmWeKnoraCloudTest`；`EmbeddingWireTest`/`RerankWireTest`/`ProviderAdapterRegistryTest`/`ProviderValidationTest`/`ModelContractTest`/`VlmDescriberWiringTest`/`RemoteApiChatTest`/`APIKeyRouteAuthorizerTest` 去云用例；`ProviderRegistryTest` 27→26；`SystemContractTest` 引擎清单去云；删 4 个云 fixture；golden `model-providers.json` / `model-providers-chat.json` 去云条目（紧凑 JSON，按结构改后重新序列化）。
- **验证**：① 后端 **4,704 测试 0 失败 + spotlessCheck 绿**（`spotlessApply` 清掉删代码后遗留的未用 import）；② 前端 **720/720** + `vue-tsc` 0 错误 + i18n 审计 11/11 + 契约键守卫无新增；③ **运行时实测**：`GET /api/v1/models/weknoracloud/status` → **404**、`POST /api/v1/weknoracloud/credentials` → **404**、`/api/v1/models/providers` → **26 家、不含 weknoracloud**、`/api/v1/system/parser-engines` → **9 个引擎、不含 weknoracloud**；④ **浏览器点检**：设置导航「模型」组只剩「模型管理 / Ollama」，其余分组与项不变（技能管理仍在「数据与扩展」），控制台 0 报错。
- **过程留痕（给后续裁撤参考）**：脚本化的"大括号配对删块"在**无花括号的语句**上会跑偏——本次在 `model/service/ProviderRegistry`（`new ProviderEntry(...)` 只有圆括号）与 `ParserEngineRegistry`（注释行 + `boolean cloud = …`）各误伤一次，**已从 HEAD 恢复并改用精确文本删除**；其余块删除（语句/方法/类含 `{`，模板用 `</template>` 配对）验证无误伤。教训：**删块前先确认锚点行自带花括号，否则用精确文本替换**。

**✅ B63（2026-10-04，embed 语言「跟随浏览器/宿主」失效：派生语言被写进持久值）**
- **触发**：用户点检 `http://localhost:5173/widget-test.html`——渠道默认语言设为「跟随浏览器 / 宿主」，但 widget 语言不是浏览器语言。
- **根因（真机实验坐实）**：embed 的 `applyEmbedLocale()` 一律 `localStorage.setItem('weknora-embed-locale', …)`，而模块初始化 `resolveInitialEmbedLocale()` 的取值顺序是 **URL → localStorage → 浏览器**。于是**派生语言**（渠道默认语言、预览/宿主的 `?locale=`）一旦应用就被持久化，此后**永远压过浏览器语言** ⇒ 渠道改回「跟随」也不生效。实验（浏览器 ja-JP）：干净访客显示日语 ✓；预置陈旧 `en-US` → 英文 ✗；预置陈旧 `zh-CN` → 中文 ✗。用户那条渠道 `default_locale` 实测为空（跟随）✓，且该渠道此前很可能配过默认语言（或预览过）→ 访客浏览器里留下了旧值。
- **修复（语义拆成"派生 / 显式"两条路）**：`applyEmbedLocale`（显式，宿主 `set_locale`）**才**写持久值；新增 `applyDerivedEmbedLocale`（渠道默认 / 浏览器 / URL `?locale=`）**只作用于本次加载**；新增 `clearStoredEmbedLocale()`；`syncEmbedLocaleFromUrl` 改走派生（预览不再污染访客存储）。`useEmbedBridge.bootstrap`：未 pin 时——渠道默认语言非空 → 派生应用；为空（跟随）→ **先清持久值再按浏览器语言渲染**。顺带把 `<html lang>` 与当前语言同步（原先恒为 `en`）。
- **widget 脚本补"跟随宿主"半场**：① 新增 `locale` 选项 / `data-locale` 属性 → 拼进 iframe URL `?locale=`（首屏即对语言，且被视作宿主 pin，不被渠道默认覆盖）；② `setLocale()` 在 iframe 握手前调用不再静默丢失（排队，ready 后补发）——旧实现直接 `postHostPayload` → `iframeReady=false` 时丢弃。
- **验证**：真机（Playwright，浏览器 locale=ja-JP）**6/6**——① 干净+跟随→ja；② 陈旧 en-US + 跟随→ja 且**存储被清**；③ 陈旧 zh-CN + 跟随→ja 且存储被清；④ `?locale=en-US`→en 且**不落存储**；⑤ `init({locale})` → iframe URL 带 `locale=ja-JP`；⑥ 握手前 `setLocale('ja-JP')` → 补发成功。另测渠道默认语言链路：DB 临时置 `en-US` → widget 显示 en 且不落存储，`?locale=zh-CN` 可覆盖（宿主优先），**验后已还原为空**。新增 2 条单测（持久化语义 + 2 条 URL/浏览器解析）+ 1 条源码守卫（**两个红态探针验过**：回退成 `applyEmbedLocale(res.defaultLocale…)` 或删掉 `clearStoredEmbedLocale()` 均点名报红）。前端 **723/723**、`vue-tsc` 0 错误、契约键守卫无新增。
- **口径沉淀**：**"能重新推导的派生值，不要写持久存储"**——派生值一旦落盘就会盖住后续配置变更（本次是语言；同类风险：主题、尺寸、默认模型）。只有用户/宿主的**显式选择**才值得持久化。

**✅ B64（2026-10-04，「跟随浏览器/宿主」只跟随了浏览器——补上宿主页面语言）**
- **触发**：用户追问「`widget-test.html` 宿主浏览器语言是 zh-CN 吗？为什么配置为跟随还是显示英语」。
- **实测三场景（Playwright）**：① 浏览器 `zh-CN` → widget **中文 ✓**；② 浏览器 `en-US` → 英文 ✓；③ **浏览器 `en-US` + 页面 `<html lang="zh-CN">` → 英文 ✗**。⇒ 两条结论：用户浏览器 UI 语言实为 `en*`（否则①会显示中文）；且「跟随宿主」此前只实现了**跟随浏览器**（`navigator.language`），**不读宿主页面的语言声明**——测试页明明写着 `<html lang="zh-CN">` 也没用。
- **修复（给"宿主"补一条弱信号通道）**：widget 脚本在**没有显式 `locale`/`data-locale`** 时读宿主页 `document.documentElement.lang`，经**独立参数 `?hostLocale=`** 转发（不能占用 `?locale=`——那会被当作"宿主已 pin"从而压过渠道默认语言）；embed 侧新增 `matchEmbedLocale()`（**严格**归一化：认不出返回 `null`，避免宿主写 `lang="de"` 被兜底成中文）与 `readHostPageLocaleFromUrl()`；`useEmbedBridge` 跟随分支改为 `matchEmbedLocale(readHostPageLocaleFromUrl()) || resolveBrowserEmbedLocale()`。语言优先级最终为：**宿主显式（`?locale=` / `set_locale`）> 渠道默认语言 > 宿主页面 `<html lang>` > 浏览器语言**，且四条派生路径全部不落持久值 ✓（B63 口径）。渠道设置说明同步改五语言（写明"宿主页面声明的语言 `<html lang>`"）。
- **验证**：真机 4/4——浏览器 en + 页面 zh-CN → **zh-CN**（原 ✗）；zh + zh → zh；页面 `de-DE`（不支持）→ 回落浏览器 `en-US`；显式 `init({locale:'ja-JP'})` → 压过页面声明。B63 六项回归全绿；新增/扩展单测 3 条 + 守卫补 2 条不变量（**两个红态探针**分别摘掉 widget 转发与 bridge 读取，均点名报红）。前端 **724/724**、`vue-tsc` 0 错误、i18n 审计 11/11、契约键守卫无新增。
- **口径**：**"跟随"类设置要把信号分层**——宿主显式 > 站点（渠道）配置 > 页面声明 > 浏览器/平台默认；且只有**显式**选择才持久化。本次的坑是把"宿主"简化成了"浏览器"（宿主页声明既没被读取，也没有可携带它的通道）。

**✅ B65（2026-10-04，嵌入渠道「保存后密钥丢失」：发布 Token 被列表刷新冲掉）**
- **触发**：用户报 `?section=integration-embed&agentId=builtin-smart-reasoning`——嵌入代码原本正常，**点保存后**变成 `<!-- 加载渠道密钥失败，请关闭后重新打开该渠道。 -->`。
- **根因（真机复现 + 三层契约实测）**：`publishToken` 只在**详情 / 创建 / 轮换**响应里返回（授权边界，见 `api/embed/index.ts` 顶部注释）；**列表行不带**，且**更新（PUT）响应也不带**（后端 `EmbedChannelMgmtController` 的 update 返回 `row(ch, false)`）。面板打开抽屉时靠详情响应 `mergeChannelDetail` 把 token 合进行里——但保存分支 `await load()` 会用**列表行整体重建** `allChannels` ⇒ 合并进来的 token 被冲掉 ⇒ `tokenFor()` 为空 ⇒ `drawerSnippet` 退化为 `<!-- ${t('embedPublish.tokenHint')} -->`。实测四层：创建带 token ✓ / 列表不带 ✓ / 详情带 ✓ / **更新不带 ✓**。
- **复现与验证（Playwright，真点 5 步向导 + 保存）**：保存前 snippet 含 `em_…` → 保存后**正是**用户看到的那行提示（一字不差）；修复后保存前后都含 `em_…`。
- **修复（前端，不动后端）**：新增 `components/embedChannelTokenRegistry.ts`——本会话按渠道 id 记住**已见过**的 token，`hydrate()` 在每次 `load()` 后贴回列表行，`forget()` 在删除渠道时清理；**只存内存、不落 localStorage/sessionStorage**（token 属敏感物，刷新后按既有提示重开抽屉再取）。面板三处接线：`mergeChannelDetail` → `remember`；`load()` → `hydrate`（替换裸赋值 `= res || []`）；`removeChannel` → `forget`。**刻意不改后端**：让 PUT 也返回 token 会扩大暴露面，而面板本就合法持有该 token。
- **成因归属**：`mergeChannelDetail` 与保存分支的 `await load()` 都来自**初始建仓**（`git log -S` → `6b80270b`）⇒ 长期潜伏 bug、非近期回归；用户是在**改渠道语言（B63/B64 工作流）后点保存**时撞上的。
- **同族扫查**：全仓 `publishToken` 消费点只有本面板（`IMChannelPanel` 不用它）✓；同族触发路径（启用/停用开关、新建、轮换）都走 `load()` ⇒ 同一处修复覆盖；轮换 `mergeChannelDetail(res)` 先写入**新值**再 `load()` ⇒ 新值生效 ✓。
- **守卫与测试**：`embedChannelTokenContract.test.ts`（3 条不变量：hydrate 贴回 / remember 唯一取值来源 / forget 清理）+ `embedChannelTokenRegistry.test.ts`（3 条：跨刷新保留、轮换覆盖+删除清理、空值安全）；**两个红态探针**（把 `load()` 改回裸赋值、去掉 `remember`）均点名报红。前端 **728/728**、`vue-tsc` 0 错误、i18n 审计 11/11、契约键守卫无新增；临时探针渠道与 agent 已删除（204/204，无残留）。
- **遗留**：非管理员视图（`hide-footer`）与卡片上的启用开关在自动化里不可达（元素被隐藏/拦截），该路径由单测语义覆盖，未做浏览器实测。

**✅ B66（2026-10-04，Agent 编辑器「提示词变量」全空：裸载荷漏适配）**
- **触发**：用户问 `/platform/agents` 的变量显示——「点击插入，或输入 `{{` 唤起列表」不工作。
- **根因（真机复现 + 端点实测）**：`GET /api/v1/agents/placeholders` 返回**裸载荷**（顶层键 `all / systemPrompt / agentSystemPrompt / contextTemplate / rewriteSystemPrompt / rewritePrompt / fallbackPrompt`，**无 `data` 键**），而 `api/agent/index.ts` 把它声明成 `get<{ data: PlaceholdersResponse }>` 且**没有适配层** → `editorResources` store 读 `placeholdersRes?.data` **恒 undefined** → `placeholderData` 七组全空 ⇒ **变量芯片不渲染、`{{` 弹出列表不出现、点击插入无效**。同仓既有约定是「裸载荷 + 消费端要 `{data}` → 在 api 层 `return { data: resp }` 适配」（见 `api/system` 的 KV 三兄弟、`api/retrieval`、`api/web-search-provider`）——这两处漏了。
- **同族第二处**：`getAgentTypePresets()` 同样漏适配（裸数组声明成 `{data}`）→ `agentTypePresets` 恒 `[]` → 编辑器「类型」下拉为空（用户未报，一并修）。**全仓扫查**：`get<{ data: … }>` 声明共 **2 处**，正是这两个——其余 6 处都显式适配 ✓ 无第三处。
- **修复**：两个函数改为「裸类型取数 + 显式适配」，并保留消费端既有 `{ data }` 契约（store 读侧不动、一处改即可）：
  `get<PlaceholdersResponse>(...).then((resp) => ({ data: resp }))`、`get<AgentTypePreset[]>(...).then((resp) => ({ data: Array.isArray(resp) ? resp : [] }))`。
- **验证（Playwright 真机，smoke 账号真开编辑器）**：修复前——变量芯片 **0 个**、输入 `{{` 无弹出（红态复现）；修复后——芯片出现（`{{knowledge_bases}}` 等）、输入 `{{` 弹出 **5 项**（带描述，如 `{{query}}用户当前的问题或查询内容`）、**点击芯片成功插入 `{{query}}`**；直读 pinia store：`agentTypePresets` **5** 条、`placeholders` 七组齐全（all 10 / agentSystemPrompt 4 / systemPrompt 5 / contextTemplate 5 / rewriteSystemPrompt 5 / rewritePrompt 5 / fallbackPrompt 2）。（下拉选项数用合成点击验不出，属测试手法限制；数据层已由 store 直读确认。）
- **守卫**：`crossFaceKeyContract.test.ts` 新增「裸载荷 API 必须在 api 层适配成 { data }」——禁止 `get<{ data: … }>(裸端点)` 写法、要求显式适配、并钉住 store 读侧契约（**红态探针**：把 `getPlaceholders` 改回声明 `{data}` → 点名报红）。前端 **729/729**、`vue-tsc` 0 错误、i18n 审计 11/11、契约键守卫无新增。
- **口径**：**裸载荷 ≠ 没有包裹**——要么消费端直读裸键，要么在 api 层显式适配；**绝不能"声明 `{data}` 而运行时是裸的"**（类型与运行时不一致，静态检查完全抓不到，症状是静默空值）。与 B58「信封读法清剿」同族：B58 清的是**消费端**按旧信封取数，本批补的是 **api 层漏做适配**。

**✅ B67（2026-10-04，KB 解析设置整页崩 + 规则读写键名错面）**
- **触发**：用户贴控制台报错——`KBParserSettings.vue:237 getEngineForGroup` → `TypeError: Cannot read properties of undefined (reading 'some')`（Unhandled Vue error，解析分区渲染不出）。
- **根因**：KB 配置面（`knowledge_bases.chunking_config.parserEngineRules`）按 **B3b** 已统一为 **camelCase**（`V2__kb_config_keys_camel.sql` 迁移 + 后端视图 `ChunkingConfigView.ParserEngineRuleView(fileTypes, engine, xlsxFirstRowAsHeader)`；库里实测 camel，用户那条「GACI」正是全仓唯一带 camel 规则的 KB），但**解析设置页仍按 snake 读**（`rule.file_types.some(...)`）→ 对 camel 数据 `.some` 于 undefined → 整页打崩。**真机复现**（给 smoke 的 KB 写入 camel 规则后打开其解析设置）：解析分区 **select 数 0** + 与用户完全一致的堆栈。
- **同族三处**：① **写入侧同类错**——组件 emit 的是 snake 规则，后端按 camel 反序列化 → 规则落库成 `fileTypes: []`（库里那几条空数组即证据；KB 级规则因此从未真正生效）；② `UploadConfirmDialog` 在两面之间**直接透传**（KB 配置 camel ↔ 上传覆盖 snake）→ 覆盖里的规则运行时读不到（后端 `ParserEngineRules.resolve` 读 `file_types`）；③ `KBChunkingSettings` / `UploadConfirmDialog` 的规则类型声明也是 snake。
- **修复**：新增 `utils/parserEngineRules.ts` 作为**两面互转的唯一出口**——`normalizeKbParserRules()` 宽容归一化（camel 与 legacy snake 都吃、畸形项丢弃而不抛错、`fileTypes` 必为数组），`toOverrideParserRules()` / `fromOverrideParserRules()` 显式互转；`KBParserSettings` 全面 camel 化（props 初始读取与 watch 都过归一化，`.some` 再加数组兜底）；`KBChunkingSettings` / `UploadConfirmDialog` 类型与转换改对；**刻意保留** `types/knowledgeProcess`（上传覆盖）与 `api/agent`（智能体 `chatParserEngineRules`）的 **snake**——那是后端运行时契约（`resolve` 读 `rule.get("file_types")`），不能"顺手统一"。
- **验证（真机，smoke 账号）**：修复前——解析分区 select 数 **0** + Unhandled Vue error（复现）；修复后——分区渲染 **22** 个 select、控制台 **0** 报错，且**读值正确**（PDF/Word/演示文稿=内置(默认)、**Excel=`simple`＝库里写入的值**）；再在 UI 里保存 → 查库：规则以 **camel 完整落库**（pdf/docx/pptx→builtin、xlsx/xls→simple）⇒ **读、写两侧皆通**。
- **守卫**：`crossFaceKeyContract.test.ts` 新增「解析引擎规则两面」——KB 配置面必须 camel 且必须过归一化、覆盖/智能体面必须保持 snake（防未来"顺手统一"打断运行时契约）；**两个红态探针**（去掉归一化读取 / 覆盖转换改回透传）均点名报红。3 条单测（归一化 / 畸形容错 / round-trip 不丢字段、不改入参）。前端 **733/733**、`vue-tsc` 0 错误、契约键守卫无新增。
- **口径**：**同一个概念在不同"面"上可能有不同键名约定**（本次：KB 配置 camel / 运行时规则 snake）——搬运时必须**显式转换**，且读侧要能容忍历史数据；`B3b` 只把 KB 配置的**写入侧 + 存量数据**统一了，读取侧漏改会以"整页崩"的形式暴露（比静默更强的信号）。
- **遗留**：smoke 空间的 `b3b-camel-kb` 现带一组完整 camel 规则（验证写入时产生，可当样板）、`d1-smoke-kb` 留 2 条；**用户空间（tenant 11）数据未动**。

**✅ B68（2026-10-04，智能体「推荐问题」恒空：读取侧键名与写入侧不一致）**
- **触发**：用户问「知识库 `bc2a8d00…`（GACI）有没有自动生成问题？」——顺带查出同族隐性 bug。
- **先回答用户**：**有**。`question_generation_config = {enabled: true, questionCount: 4}`；该库 24 个 chunk 里 **23 个 text chunk 都带非空 `generatedQuestions`**（多数 4 个/块，`generatedQuestionsRevision=0` 即首次自动生成），第 24 个是 `summary` 块（本就不生成）✓。
- **查出的 bug**：智能体推荐问题的**读取侧**查 **snake** `generated_questions` —— `AgentQuestionMapper.listRecentDocumentChunksWithQuestions` 的 `LIKE '%generated_questions%'` 过滤 + `AgentSuggestedQuestions.firstGeneratedQuestion` 的 `get("generated_questions")`；而**写入侧是 camel** `DocumentChunkMetadata.generatedQuestions`（Jackson 默认 camel），**从未有过改名迁移**，全库实测 **31 个 chunk 全 camel、0 snake** ⇒ SQL 恒命中 0 行 + 解析恒 null ⇒ **智能体「推荐问题」（"你可以这样问我"）永远为空**（真机：`GET /api/v1/agents/builtin-quick-answer/suggested-questions` 返回 `[]`，而库里明明有数据）。
- **修复**：SQL 改为 **camel 主 + OR 容忍 snake 存量行**；解析改为 **camel 优先、snake 兜底**（并兼容纯字符串数组元素）；三处注释同步（说明键名依据 = 写入侧 Java 域类型）。**未动写入侧**（本来就对）。
- **验证（真机红→绿）**：往 smoke 的一个 chunk 播一条 camel 生成问题 → 修复前端点返回 **`[]`**；重启后端（重新编译）后端点返回该问题 ✓（`{"question":"…","source":"document","knowledgeBaseId":"…"}`）；验完已把探针数据移除（`metadata - 'generatedQuestions'`）。
- **测试**：新增 `AgentSuggestedQuestionsTest`（2 条）——① 解析：camel 命中、snake 回落、纯字符串元素、空数组/缺键/null/畸形 JSON 一律 null 不抛错；② **扫描 `AgentQuestionMapper` 所有 `@Select`，凡按生成问题键过滤的必须用 camel 且容忍 snake**（并断言至少扫到 1 条，防守卫空转；第一版钉死方法名 `listRecommendedFaqChunks` 得到假红——那是 FAQ 面，不按生成问题过滤）。**两个红态探针**（SQL 改回 snake-only / 解析改回 snake-only）均点名报红。后端 **4,706 测试 0 失败 + spotlessCheck 绿**。
- **口径补一条**：本仓反复出现「**写入侧 camel、读取侧 snake（或反之）**」这一类错（B58 信封 / B62 键 / B66 裸载荷 / B67 规则面 / B68 这里）——凡按**字面键名**过滤或取值的地方（SQL `LIKE`、JSON path、`?` 判键），必须与写入侧对齐，并尽量容忍历史形态；静态检查抓不到，只能靠"以写入侧为基准 + 数据实测"来钉。

**✅ B58（2026-10-04，旧信封读法清剿：Go `{success,data}` 残留 → Java 裸载荷）**
- **触发**：用户点检 `?section=integration-api` 报「加载 API 集成设置失败」。
- **根因**：`/auth/me` 是**裸信封**（`{user, tenant, memberships, tenantRequired, capabilities, preferenceDefaults}`，`tenant` 在顶层），而页面读 `userResp.data.tenant`（Go 时代 `{success,data}` 形状）→ 恒 undefined → 直接抛错。curl 实测坐实（200 + 顶层键清单里无 `data`）。
- **同族清剿（8 处消费点 / 6 文件，全部先 curl 实测端点形状再改）**：① 集成设置页 `userResp.data.tenant` → `userResp.tenant`（**用户报错点**）+ `resp.data`(agents) → `resp.agents`（此前智能体列表静默为空）；② 聊天页开场建议 `res.data.questions` → 裸数组（此前永远为空；`creatChat.vue` 读法即规范）；③ FAQ 导入 `res.data.taskId` → `res.taskId`（`FaqTaskStartResponse`；此前导入进度条永不出现）；④ KB 文件夹树 `res.data` → `res`（此前 folderTree 恒 null）；⑤ KB 文件夹重命名 `res.data.moved_count` → `res.movedCount`（`FolderMoveResponse`；**成功也弹「重命名失败」**）；⑥ KB 跨库移动 `res.data.task_id` → `res.taskId`（`MoveKnowledgeResponse`）；⑦ 移动进度轮询 `res.data` → `res`（`KnowledgeMoveProgress` 直出；此前每次 tick 空转 early-return）；⑧ 轨迹探测 `res.success && hasTrace(res.data)` → `hasTrace(res)`（「查看处理轨迹」入口此前被隐藏）。
- **有意适配、未动（已核实）**：`api/web-search-provider.ts`、`api/tenant/invitations.ts` 把裸载荷**适配回**旧契约（注释已声明，`stores/auth.ts` 读 `resp.success` 走的就是它）；SSE/流事件与工具结果载荷属冻结面；`e?.response?.data?.error?.message` 系列是 axios 形状的死分支（有 `e.message` 兜底）；`KnowledgeBase.vue:130`、`DataSourceEditorDialog.vue`、`WikiBrowser.vue` 的 `?.data?.x || ?.x` 与 `res?.data || res` 自带兜底。
- **守卫**：`components/crossFaceKeyContract.test.ts` 新增「信封读法：Java 后端是裸载荷，不得再按 Go 的 {success,data} 取数」——**表格**钉住 9 条（集成页两处 + 上述 7 条 + 正确读法正断言），每条注明端点真实形状。**红态探针**：把 `chat/index.vue` 改回 `res?.data?.questions` → 红并报出文件与端点形状；还原即绿。
- **验证**：前端 **717/717**、`vue-tsc --build` 0 错误、`check-fe-contract-keys.py` 无新增；所有端点形状 curl 实测（folders/move-targets/spans/me/agents）。
- **教训**：Go→Java 的**信封收敛（改裸载荷）只改了写侧**，读侧散落在页面里按旧形状取数——症状分三类：**抛错**（①）、**静默为空**（②④⑥⑦⑧）、**误报失败**（⑤）。静态检查抓不到（键名与形状都"存在"），只能靠"**按端点实测形状 + 表格守卫**"。

**✅ B69（2026-10-05，持久层治理：架构师四条意见落地为机器强制面）**
- **触发**：用户拿架构师的 MyBatis-Plus 四条规约征求意见；先做全仓体检给出数据支撑的分层评估（用户拍板「同意方案」），再按 M0→M1→M2 落地。评估口径：按「失败模式」而非「工具」改写条款、机器能守的进 CI、棘轮不清存量。
- **① 全表改删防护（M0）**：`common/mybatis/FullTableWriteGuard`（挂 `MybatisPlusConfig` 插件链尾，分页在前防护殿后）。不用 MP 原生 `BlockAttackInnerInterceptor`——它对解析失败的 SQL 直接抛异常，而本仓 33 个注解 SQL 文件含 PG 方言（jsonb/向量操作符），误伤=运行期炸合法写路径；自研版 fail-open（wrapper 生成的 SQL 形态固定必可解析，防护覆盖主攻面；方言 SQL 归 R7 白名单纪律）。**规则撞真实业务一例**：`SystemContractTest` 抓出 `SystemAdminController` 的 `update(null, wrapper)` 全表 UPDATE（默认配额统一应用到全部租户）是合法业务 → 设计「显式登记通道」：全表写必须**具名成 Mapper 方法**并在 `FULL_TABLE_ALLOWED` 按语句 id 登记（`TenantMapper.applyDefaultStorageQuota` 首例），匿名全表写一律拦。
- **② R6-R9 棘轮规则（M0）**：沿用 R4 的代码内基线风格 + **双断言棘轮**（基线外新增违例=红拦增量；**基线条目不再违例也=红**，防止规则空转、强制随清理收紧——相当于把「探针自证」内建到每条基线规则里）。R6 禁 @Lazy、R7 裸 JDBC 白名单（28 类四类场景：方言探测/PG 专有 SQL/非业务库引擎/启动修复；**TypeHandler 是框架回调扩展点，豁免**；匿名内部类归并顶层类再对基线）、R8 禁字符串列名 wrapper、R9 `.last` 只许纯字面量（带注释剥离——javadoc 里的 `.last("LIMIT " + n)` 样例曾误报 PageRequests 自己）。R6 红态探针实测可红（临时 @Lazy 组件 → 精确点名 → 删探针复绿），R7/R8/R9 在实施中各自抓到过真实违例（R8 抓出两处 grep 漏数的**全限定名** `new com.baomidou…UpdateWrapper`：TenantService/SystemAdminUserService——字符串 grep 抓不全限定形式，规则比 grep 准）。
- **③ 分页拼接归零（M1-1）**：新增 `common/mybatis/PageRequests`——`cap(size)`（行帽）/`range(page,size)`（翻页）/`atOffset(offset,size)`（任意偏移）三工厂，统一 `setSearchCount(false)`（总数仍由调用方显式 `selectCount`，保持 count 查询形状可控——MP 自动 count 带 ORDER BY 时 H2 报错）。`atOffset` 的可行性依据：源码核实 MP 3.5.7 分页拦截器走 `page.offset()` 读偏移 → 覆写即可表达任意 offset。23 处拼接点全灭，`SessionRepository` 的手写 `OFFSET…ROWS FETCH NEXT` 方言分支由拦截器方言自适应替代。负数防御：MP 对 `size<0` 语义是「不限行数」（全表扫），PageRequests 一律钳 0。
- **④ 字符串 wrapper 清零（M1-2）**：knowledge 硬文件（ChunkRepository/FaqChunkRepository/KnowledgeSearchService）手改——jsonb 三参 set 走 **lambda 三参形式**（先例 TemporaryDocumentRepository.markReady）；JSON 路径 `apply(...)` 不是实体属性，保留 raw（`{0}` 占位本就参数化）。其余 21 文件 57 处三批并行委派。**唯一保留** `MessageRepository.update` 字符串 UpdateWrapper——作者注释明载 jsonb 列必须三参 set 挂 typeHandler 且 H2 实测过 lambda 退化形态，R8 基线登记理由永久保留。
- **⑤ knowledge 域 @Lazy 解环（M2）**：现状 8 类 11 注入点（KnowledgeService 门面 + 6 子服务 + KnowledgeProcessWorker 互环，`KnowledgeService.java:844` 注释自认「@Lazy 避免循环依赖」，**重构台账此前无此欠账条目**）。拆法三件：门面 helper（requireKb/findKb/getKnowledge/getKnowledgeInTenant/getKnowledgeBatch/WithSharedAccess）下沉 `KnowledgeAccessHelper`（子服务 facade 调用仅 10 处，替换成本极低）；**enqueue 端口外提** `knowledge/task/KnowledgeProcessingQueue`（原是门面嵌套接口 `KnowledgeService.KnowledgeProcessWorker`——嵌套形态让全部提交方反向依赖门面类型，含 datasource 桥跨域消费者 1 处；删除嵌套接口）；worker 的摘要触发改**同步领域事件** `KnowledgeProcessedEvent`（SummaryService `@EventListener`，发布线程内执行=与原直调语义逐字一致；**不能直连 SummaryService**——Worker→Summary→File→worker-port 是三角，会造新环）。主代码 @Lazy 11→**0**，R6 基线清空转纯禁令。门面 public API 全保留（委托化），控制器/其它域零感知。
- **⑥ 文档与评估（M3/M4）**：`docs/persistence-architecture-rules.md` 规约修订版（给架构师的回执：采纳/改写/缓行逐条 + 现状基线表）；`docs/persistence-tenant-filtering-evaluation.md`（架构师规约的盲区补位：手工 `.eq("tenant_id",…)` 9 文件 26 处 + B60 越权洞实证；**结论=不上 TenantLineInnerInterceptor**——与 B60 的 NULL=平台内置语义冲突、ignore 表清单维护、JSqlParser 对方言 SQL 无 fail-open、不覆盖 Neo4j/S3/向量库；建议「桥接面收敛 → 缺失过滤探测 fail-loud → 小域试点」三步）。
- **闸门**：后端 **4,719**/0 失败 + spotlessCheck 绿 + `check-package-cycles.py` 绿（新包 `common.mybatis` 无环）+ R6 红态探针红→绿闭环。每阶段全量门禁独立通过（M0 后/M1-1 后/M2 后）。
- **教训**：①「严禁 X」型规约必须给真实业务留**显式登记通道**（配额全表写一例即证），否则逼人换姿势绕过；② 字符串 grep 数存量必然漏**全限定名**形态（本批两例），ArchUnit 字节码规则比 grep 准；③ 解环前先画全 bean 图——worker 直连 SummaryService 的「显然改法」会造出新三角环，事件是最便宜的正确解；④ MP 拦截器没法直接单测（`MybatisPlusInterceptor` 是 MyBatis `Interceptor` 包装），行为测试直打 `InnerInterceptor` + `getInterceptors()` 断言装配顺序。

**✅ B70（2026-10-05，M3 落地：方言探测归一 + 租户过滤缺失探测 alert 档）**
- **范围**：B69 评估报告路线的 Step1/Step2。Step3（TenantLine 小域试点）维持「待数据」不立项。
- **① 方言探测归一**：`DatabaseDialects.isPostgres` 吸收八处本地副本（session 三仓 / MemoryIndexStore / MemoryRepository / McpMetadataRepository / StorageBackendRepository / MapperKnowledgeBridge）——失败语义逐字一致（按非 PG 走）；Bridge/Mcp 原有失败日志收敛进 DatabaseDialects 一处告警。两个口径决定：**StorageBackendRepository 原来 catch-Exception 静默默认**，归一后只捕 SQLException——连不上库=启动本身有问题，静默按错方言跑比启动失败更糟（注释已写明）；MemoryIndexStore 保留（它还有 JDBC 元数据**列存在性探测**，属另一类能力），R7 理由收窄。**R7 白名单 28→24**（7 条方言探测条目摘除、棘轮双断言自动验证摘除正确）；`JdbcClient` 纳入 R7 目标类型（storage 两仓储登记，堵住 R7 的一个既有盲区）。
- **② TenantFilterGuard**（fail-loud 探测，与 TenantLine 改写的分野见 B69 评估报告）：挂插件链末位（分页 → 全表防护 → 租户探测），`weknora.persistence.tenant-filter-guard` = off/alert/enforce 三档（默认 alert）；表注册表 **47 张由 migrations 推导**（V1 带 `tenant_id` 的 46 张 + V5 skills，解析脚本口径=CREATE TABLE 块内含 tenant_id 列）；v1 刻意从窄：只查 SELECT 主 from-item 表、UNION 不查、解析失败放行、WHERE 渲染串含 `tenant_id` 即过（子查询出现也算，宁漏勿误）——所有边界登记在类注释，收紧时改那里。单测：alert 档 Logback ListAppender 捕获告警、enforce 档异常红态、JOIN/UNION/FROM 子查询/非 SELECT 边界、装配顺序三件套。
- **③ 首次盘面**（alert × H2 契约全量）：**6,084 告警 / 73 条去重语句 / 21 张表**。头部 users 2799 / tenant_members 1021 / knowledge_bases 831 = 按 id 直查与成员关系的**合法传递面**；直连缺失候选在腰部：chunks 47、task_pending_ops 37、im_channels 25、wiki 族 ~700。逐表三选一（迁出注册表并注释传递语义 / 白名单 / 补条件）的收口清单写回 `docs/persistence-tenant-filtering-evaluation.md` §5；**enforce 切档挂起**，待 B71+ 逐表定性。
- **踩坑**：表注册表的 `"knowledge_bases"`/`"mcp_services"` 字面量撞 **B18 键名扫描器**（同一串既是表名也是 agent 配置键）→ 按其「表名=合法 snake 面」既有口径加 `common/mybatis/TenantFilterGuard` 路径豁免（该扫描器 javadoc 自述表名属合法面）。另一坑：MP `InnerInterceptor.beforeQuery` 是**六参**签名（带 RowBounds/ResultHandler/BoundSql），按记忆写三参编译不过——javap jar 确认后照抄，BoundSql 直接入参不再自取。
- **闸门**：后端 **4,725**/0 失败（+6：TenantFilterGuardTest）+ spotlessCheck 绿；盘面运行即全量套件本身（告警走日志不拦断言）。

**✅ B71（2026-10-05，租户探测切 enforce：逐表定性收口）**
- **定性方法**：B70 alert 盘面去探针后 72 条语句，逐条读 SQL + 调用方，按「这条查询的租户边界在哪一层」归五族（口径写在 `TenantFilterGuard.ALLOWED_STATEMENTS` javadoc）：认证面 7 / 调度面 4 / 跨空间身份关系面 4 / 按 id/父键传递 54 / 内部任务队列 5。代表性核验：`ImChannelMapper.listEnabled` 的调用方是 `ImService:262` 的全局投递循环（调度面）；`WikiPageMapper.listAll(kbId, …)` WHERE 带 kb_id（父键传递，kb 归属由上层 KB 守卫）；`TenantAPIKeyMapper.selectByHash` 是 key 认证路径（认证面）。
- **结论**：**真漏 0 条**。本仓租户边界模型是「UUID id + 上层守卫（requireKb/getKnowledgeInTenant/ChunkAccessGuard）」，SQL 直连谓词只服务于按租户列表的查询——B60 洞的根因是「选择器查询连守卫都没有」，不是「SQL 少谓词」。探测器的价值因此定位为：**未来新增的无守卫又无谓词的查询当场红**。
- **切档**：默认 enforce（yml 默认值 + `@Value` 默认值 + javadoc 三处同步）；`WEKNORA_TENANT_FILTER_GUARD=alert|off` 可降档排障。enforce 档全量 **4,725**/0 + spotlessCheck 绿。
- **残余风险（登记）**：测试未覆盖的冷路径首次触达会 500——响亮属设计意图；处置=按五族归入白名单（注明族别）或补条件。
- **教训**：盘面（alert）→ 定性（读调用方）→ 收口（enforce）一天走完的前提是**全量套件本身就是查询面的高覆盖回放**——4,725 条测试把 72 条违例语句全部打出来了，"跑一周"的时间窗被套件覆盖率替代。

**✅ B72（2026-10-05，前端契约键失配收口：snake 全量排查·修复批）**
- **排查口径**：前端 snake 记号全量 2,979 处 / 699 词 / 182 文件，三面并行分类（聊天/SSE、设置/系统/解析、i18n/杂项）+ 后端 Java 逐词交叉验证。定谳分层：**KEEP ~95%**（SSE 载荷显式 `@JsonProperty` ~70 键、`AgentToolNames` 29 工具名、设置 KV、连接器凭据 jsonb、embed iframe 协议、APIKey 能力码、审计动作码、`any_of/all_of` 词表、`@PathVariable` URL 面、RFC/厂商键）+ INTERNAL（i18n 键/localStorage `weknora_*`/CSS 类/视图模型键）+ **DRIFT 9 条**。
- **9 条 DRIFT 全部两侧证据修复**：① chunk 编辑面四键（doc-content：`expected_revision→expectedRevision` ×3、`is_enabled→enabled`——UpdateChunkRequest camel record，乐观锁与启用开关曾**静默失效**；`start_at/end_at→startAt/endAt`——ChunkResponse camel，合并预览间隙检测曾恒不触发；`content_revision→contentRevision`——块历史恒 v0）；② FAQ 标签 `seq_id→seqId`（KnowledgeTagResponse.seqId；曾恒「未分类」+选择器 undefined）；③ UploadConfirmDialog `kb.extractConfig?.custom_instructions→customInstructions`（KB 配置面 camel，ChunkExtractService:268；覆盖面写侧 snake 冻结不动——B67 型混读单键）；④ 图谱提取 `model_id→modelId`（TextExtractionTestService:49/134 读 modelId 空即 400，两按钮曾必失败）；⑤ Ollama `modified_at→modifiedAt`（OllamaManageService:85 出站已 camel，原白名单理由过时）；⑥ `expires_at_unix→expiresAtUnix`（PlatformAPIKeyCreateRequest，潜伏）；⑦ `processing_time→processingTime`；⑧ SSE 死读删除 ×3（useChatStreamHandler data.created_at——后端无事件发平名 created_at，agent_query 时间绑定走 bindServerTurnTimestamps；UserMessageInjectedData 无时间键）+ messageTimestamp 死 fallback；⑨ 集成页预览串与实发对齐（agentEnabled/agentId）。
- **Phase 0 实证（改判）**：`POST /api/v1/agent-chat/{session_id}` 是集成页**对外文档化** API（playground 展示原始 SSE 帧、apiPlaygroundSSE 按 snake `response_type` 解析；widget.js 不解析 SSE 载荷）⇒ SSE 载荷族=桶A∩桶C（无存量但有外部消费者），camel 化需协议版本化。立项设计稿（三路线+风险）：`docs/handoff/plans/sse-payload-camel-立项设计稿.md`。
- **守卫收口**：crossFaceKeyContract 新增 B72 回归钉测（修复面禁回流 snake，七组断言各带后果注释）；`api/initialization` 移出两处整面白名单（`*_at` 测试条目删除=modified_at 修复、棘轮 FACE_WHITELIST 移除），余量 11 键逐条登记精确理由；python 基线删过期 `seq_id`（42→41）、`processing_time` 理由改为「B72 已修，现仅存于守卫自身」。
- **python 守卫四个结构性盲区（登记，解释本批为何漏网）**：① TS 棘轮只盖 api/ 面，views/components 无棘轮（DRIFT ①②③ 全在盲区）；② python 守卫「后端已 camel」只认带引号字面量，record 组件名（expectedRevision/enabled/seqId/expiresAtUnix）全漏；③「后端无 snake」是全仓级判定——is_enabled/seq_id/modified_at 在后端 SQL/Langfuse/Ollama 入站有合法出现即整仓点亮跳过；④ api-system/api-initialization 曾整面豁免。收口方向：B19 时代整包豁免的基线条目逐步换按端点精确理由（本批已做 initialization 面）。
- **闸门**：前端 **734**/734（+1 钉测）+ vue-tsc 绿 + `check-fe-contract-keys` 41 条绿。

**✅ B73（2026-10-05，前端死键清理：snake 全量排查·清理批）**
- **删除清单（每项先验证读写两侧为零）**：creatChat `agent_config` 整块（CreateSessionRequest=record(title,description)，整键曾被 Jackson 静默丢弃；知识库选择本就随 QA 请求下发）；doc-content `getChunkMeta`+`char_count`/`token_count` 死读+恒空 `chunk-meta` span（ChunkResponse 无此二字段）；tool-results 死类型 `relation_type`（后端实为 `type`，GraphRelationView）/`type_icon`/`tools_to_use`（零生产零消费）；KnowledgeBaseList 死视图键 `updated_at`/`processing_count`（写入后全仓无读点，读侧本就用 camel）+ 失效 import；docreader_addr/docreader_transport 六处（ParserEngineSettings 默认值/读/写 + api/system 类型字段——Java ParserEngineConfig 无此字段，PUT 被静默丢、GET 恒走默认；连接状态显示走 GET /system/parser-engines 的 camel docreaderAddr/docreaderTransport，不受影响）。
- **翻案一则（价值实证）**：`page_size` 排查期被代理判为死键，type-check 在删除后当场抓到真实消费点 `knowledgeChunksDisplay.ts:21`（分页文案）→ 复核后端 `ListKnowledgeChunksTool:245/:303` 确在下发 ⇒ **活契约键，已恢复**。教训进 §15.2 纪律：死键判定必须「前端消费点 grep + 后端 put/字段 grep」双向闭环，类型系统的红是最便宜的复核器。
- **闸门**：前端 734/734 + vue-tsc 绿 + 守卫绿。

**✅ B74（2026-10-05，守卫基线精确化 + 前端 Go 锚点注释换锚：snake 排查·收尾批）**
- **python 基线 41 条理由全量重写**（B19 时代「已复核：本层约定或前端局部命名」整包话术归零），六类定性：① 守卫/测试自引用 5（agent_enabled/processing_time/welcome_message/display_name/web_search_enabled——B72 修复后 token 仅存于守卫禁用模式）；② 注释引用 10（after_id/agent_avatar/attachment_ids/context_window/creator_name/engine_type/extra_config/indexing_strategy/last_request_state/parser_engine，逐条注明 wire 实况如「wire 是 camel afterId，AuditLogController:74」）；③ BEM CSS 2（model__name/page__title）；④ i18n 值 2（max_concurrency/negative_questions）；⑤ **上传覆盖冻结面 11**（chunking_config/parser_engine_rules/chunk_size/chunk_overlap/parent_child 族/token_limit/table_metadata_instructions/custom_instructions/description_language/xlsx_first_row_as_header——理由=「B13 已知缺口：process_overrides 后端暂不消费、前端写读自洽；B67 两面规则禁混读」，custom_instructions 另注明 KB 配置面已 camel）；⑥ 死参数链 2（start_time/end_time——后端端点未实现过滤，注释自声明废弃）+ cos 家族 6 条补精确化。**窗口期零新 DRIFT**：被基线挡住的记号逐一核实均为非契约键。
- **前端 16 文件 ~20 处 Go 锚点注释换 Java 锚点**（活指针类）：audit-log 三处 `internal/types/audit_log.go`→`audit/domain/AuditAction`/`AuditLog`；members `tenant_member.go`→`common/tenant/TenantRole`；embedAllowedOrigins `embed_channel.go`→`EmbedChannelService#validateAllowedOrigins`；thinkingControl `provider.go`→`llm/chat/ThinkingStrategies`；api/agent `agent_type_preset.go`→`AgentTypePresets`；api/system `system_setting.go`→`SystemSettingRegistry`（两处）；user-favorites `user_resource_favorite.go`→`UserFavoriteController`；auth.ts 三处 rbac/auth.go→`config/WebConfig` rbac 规则 + /auth/me 角色解析；useRoleLabel/UserMenu/tenant-index 的 auth.go/router.go→AuthController/WebConfig；integrations g.Owner()→TenantAPIPrincipalController；AgentList custom_agent.go→`BuiltinAgentRegistry`；AgentEditorModal session_agent_qa.go→historyTurns 语义（AgentConfigJson 缺省补 5）。溯源类（chunker_debug.go 产夹具）标「Go 时代」保留史实不假换锚——每个新锚点都先在后端 find/grep 验证存在才落笔。
- **教训一则（基建）**：后台跑 gradle 用 `| tail` 收尾会吃掉真实退出码——首跑「exit 0」实为 `Unable to locate a Java Runtime`（本机无系统 JDK 注册，Homebrew openjdk@21 需显式 `JAVA_HOME`）；重跑改为日志落盘 + `echo BACKEND_EXIT=$?` 才是真闸门。管道退出码假绿与 §15.2.6「没探针的规则等于没规则」同族。
- **闸门**：前端 734/734 + vue-tsc 绿 + 守卫绿（基线 41 条零通用话术）；后端全量（toolchain 21）BUILD SUCCESSFUL 3m20s（2 executed / 12 up-to-date——`git diff 366eaa85..HEAD -- server/` 为空佐证增量复用合法：B71 后零后端变更），本会话三批收口。

**✅ B75（2026-10-05，@JsonPropertyOrder 遗产摘除：66 文件盘点 → 60 摘 7 留）**
- **盘点链（五步定谳，每步可证伪）**：①主代码 8 处摘要/签名候选（RssCursor/RssUtil/PromptCache/ImaFormats/MapperKnowledgeBridge/MemoryText/EmbedTokens/TenantAPIPrincipalController）逐个读输入——**全部是纯字符串/文件字节，无一吃 Jackson 序列化产物**；`McpConfigFingerprint.canonicalJson` 是手写 StringBuilder 规范化写出器（固定键序+map 排序+omitempty），指纹跨快照比对但注解免疫；②字节级金片 B2 已清零，6 处测试 `.getBytes()` 命中全是**夹具构造**（文件内容/请求体输入）；③Redis 流面 CAS 语义=`LINDEX ~= ARGV[2]` **原样读回比对**、`RedisStreamManager:432` needle 是 contains 搜索只依赖键名字节——序列化顺序不进任何比对；④LLM 出站体 `RemoteApiChat:251` 在 wire 前显式 `goSorted`/`structSorted` 重排（ObjectNode 手工构建，javadoc 自证「Java 侧的 ObjectNode 保持插入序，故需重排」）——Anthropic*/Ollama* 家族注解不在 wire 路径；⑤**唯一真承重=`PromptCache.promptPrefixFingerprint`**：`valueToTree` 序列化 system `ChatMessage`+`ChatOptions.tools`，javadoc 明示「字段序由各 DTO 的 @JsonPropertyOrder 注解钉住（指纹对序列化字节敏感）」。
- **动作**：60 文件摘除 861 行注解（含多行块）+ 55 条孤儿 import + 4 处 javadoc 改写（SearchResult/RankResult/WikiPage/WikiPageController 的「键序=注解钉住」表述改为「声明序=Jackson 默认序」）；7 个承重者（指纹链 5 + B38 键序决策面 stream/StreamEvent、LiveRunPayload 2）**原地注释理由**（「键序承重…勿删」），`WikiIngestLlmSupport`/`KnowledgeTag` 的 javadoc 引用核实仍成立不动。
- **过程事故（诚实记录）**：首版多行摘除脚本在注解收束行带尾注释时 `\)\s*$` 失配、**吞掉后续 class 行**（compileJava 报 "unnamed class" 即刻暴露）——`git restore server/` 回滚后改用括号配平正则（`@JsonPropertyOrder\s*\((?:[^()]|\([^()]*\))*\)`）整文件级替换重做。教训：多行注解/块删除用「单行锚+吞行」脚本不可靠，直接上配平正则或 AST 工具；编译器是最便宜的探针。
- **探针实录**：首轮全量 BUILD FAILED——红在 **spotlessJavaCheck**（注解摘除留格式偏差），`:server:test` 任务全绿=**零键序依赖的实证**；spotlessApply 修复后终轮 BUILD SUCCESSFUL 3m14s（test+spotlessCheck 双绿）。
- **闸门**：compileJava 0 错；后端全量 test + spotlessCheck 绿（5 executed / 9 up-to-date）。
**✅ B76（2026-10-07，方法级 Go 名收口：31 名去 Go + 死代码 + 常量/局部名）**
- **背景**：档 3（2026-10-03，B37~B51）只裁到**类级**（25 个 `Go*` 类 → 0，仓库仅余 `GoogleProvider` 误报）；**方法级** `go*` 从未裁决——31 个名 / ~150 处引用，另有常量/内部类/局部名同族。用户 2026-10-07 拍板：全量分析 + 尽可能按 Java 标准实现或退役，先做无契约风险的去名/换实现，契约文案与内容形态三块另批。
- **判定口径（谁在钉）**：实录（`GoRecording*`，禁手改、录制源已下线）逐字钉 ⇒ 只能换实现保输出或改名；只有自写单测/无钉 ⇒ 可真退役；contracts 金片逐字钉（auth 400 / ASR·VLM 文案 / 系统设置类型名）⇒ 契约重锚待拍板。
- **本批动作（零行为变更）**：
  - 删死代码：`McpCatalog.goEncoderJson`（main/test 0 引用）、测试侧 `Tools45cFakes.goJson`（0 引用）。
  - 主源码方法去名（33 项）：`goFmt4→formatScore4`（RetrievalObs，15 处）/ `goFmtV→valueText` / `goFormatV→duckValueText` / `goSliceString→sliceText`（4 份拷贝）/ `goSliceAlwaysPresent→alwaysPresentList` / `goValueOfNode→nodeText` / `goValue→floatText` / `goFormatFloat→plainFloatText` / `goItemsTypeMessage→itemsTypeMessage` / `goFieldTypeMessage→fieldTypeMessage` / `goFieldsJoin→fieldsJoin` / `goFields→splitFields` / `joinGoFields→joinFields` / `goFloatTree→numbersAsDouble` / `toGoJsonIndent→indentedJson` / `goTimeText→timeText` / `goTime→isoTimeText` / `goHtmlEscape→escapeHtml` / `goFormatValidationErrors→validationErrorsText` / `goQuote→quoted`（wiki；Registry 处 B77 删除）/ `goQueryEscape→queryEscape`（Yuque+OIDC）/ `goJsonString→jsonString`（IssueView+OidcStateCodec）/ `goJsonKind→jsonKindName` / `goStringField→stringFieldValue` / `goJsonErrorText→jsonErrorText` / `goOpenAiError→openAiErrorText` / `goTypeName→jsonTypeLabel`。
  - 常量/内部类/局部名：`GO_ENCODER→STRUCT_JSON`、`GO_KEY_ORDER→KEY_BYTE_ORDER`、`deepSortedGoMap→deepSortedMap`、`GoMarshalException→ArgsRenderException`、`GO_MARSHAL→REQUEST_BODY_JSON`（RemoteApiBodyCodec）、`AgentStreamBridge.GO_JSON→CAST_MAPPER`、`GoJsonBridge→JsonBridge`（类+文件同移，test）、`goStyleErrorReportValveCustomizer→plainTextErrorReportValveCustomizer`、`ZeroTimeSerializer.GO_ZERO_TIME→ZERO_TIME_INSTANT`、`EventJson` 局部 `goTime→zeroTimeModule`、`WikiRequestSupport` 形参 `goField→fieldName`、`WebSearchTool` 形参 `goType→typeLabel`、`RecordingSupport.GO_MAPPER→JSON_MAPPER`、`goJsonOfData→jsonOfData`、`ModelContextRecordingTest` 局部 `goSlice→recordedSlice`。
  - **真收敛一处**：`AgentResponses.GO_ZERO_TIME`（自有重复字符串常量）删除 → 复用 `common/web/ZeroTimeSerializer.ZERO_TIME_LITERAL`。
  - 测试侧中性化：`ToolRegistryRecordingTest.goJson→jsonText`、`SessionStreamControllerTest.goEscapingMapper→streamMapper`（顺带修陈旧 javadoc——`JacksonConfig` 早已退役）、`McpConfigFingerprintTest` 的 `goGoldens`/`matchesGoJsonAndDigest`/`goJson`/`goSha` → `fingerprintGoldens`/`matchesJsonAndDigestGoldens`/`expectedJson`/`expectedSha`（断言消息改「金片」）、`WikiIngestServiceTest.goQuoteMatchesGoVerb→quotedWrapsSlugs`。
  - 顺清 6 处陈旧 Go 注释：`ModelDebugController`×2（gin.H / Go map 键排序）、`VectorStoreController`（Go c.Error）、`SessionService`（Go 硬编码）、`TemporaryDocumentService`/`TemporaryDocument`（Go BeforeCreate）。
- **同批并入（此前在途、未提交且无记录）**：工作区另有一批同日方法级去名清扫（10-05 20:59 / 10-07 02:55~02:59 / 10-07 22:31~22:41 期间改动，与本批文件部分重叠，无法拆分），名族含 `goTrimSpace`/`goTrim`/`goTrimRight`/`goI`/`goSorted`/`goRel`/`goMessage`/`goNumber`/`goZeroToEpoch`/`goPathExt`/`goParseBool`/`goStringList`/`goStatusLine` 及对应测试名（`goTrimSpaceMatchesUnicodeSpaceSemantics`/`goRelMatchesFilepathRel`/…）；与 B76/B77 同树执行、共用一轮闸门，随本批一并提交（提交说明已注明）。
- **不动项（有证据）**：`GoRecording*` 实录全族（禁手改）；`ConversationSerializer` 族（`goMarshal`/`goMarshalInto`/`goEscapeString`）、`ObservePhase.goMarshal`、`NotionValues.goFormatG`（行为面，后续批）；测试侧其余 `go*` 局部名（`goErr`/`goCallBody`/`**Body` 族等，多与实录字段同名）登记为后续小批，清扫须避开 `GoRecording*` 文件。
- **闸门**：compile 0 错；全量 **4,823**/0 失败/6 跳过 + `spotlessCheck` 绿 + `check-package-cycles.py` 绿（环 0 / 依赖 config 1 / L2→L3 6）+ `check-go-anchors.py` 0。B76/B77 同树执行、共用一轮闸门。

**✅ B77（2026-10-07，方法级 Go 复刻换实现：两处换 Java 原生、一处评估保留）**
- **换实现（保输出）**：① `Registry.goQuote` 删除 → `ToolJson.quoted`（标准 Jackson 字面量）——实录 `routing_text` 用例（`GoRecording46A:180`，`ModelContextRecordingTest:643` **逐字比**）绿 ⇒ UUID/`msN` 句柄输出逐字不变；Go `%q` 的 `\xNN` 控制字符转义语义退役（写侧 `PromptAssembly` 本就是朴素拼接，转义分支对真实 ID 不可达）。② `ParamCaster.goFormatFloat` 手写 mant/exp 解析 → `BigDecimal.valueOf(v).stripTrailingZeros().toPlainString()`（`plainFloatText`）：非指数形态逐字不变（`42.0`/`123.5` 原样），指数形态去尾零（`0.00000010 → 0.0000001`；`1000000000000000000000` 不变）；`ParamCasterRecordingTest` 数字 token 归一化 ⇒ 不受影响。
- **评估后不动（2026-10-08 实证补记）**：`IssueView.indentedJson`（原 `toGoJsonIndent`）——契约强度：`WikiSmallRecordingTest:209` 对 `output` 做**逐字** `isEqualTo`（实录禁手改、录制源已下线）。Jackson **2.17.2 探针实测**：两空格缩进 `withObjectIndenter(new DefaultIndenter("  ","\n"))` ✓；数组逐元素换行 `withArrayIndenter(...)` ✓；字段分隔符 `": "` 需 `Separators.withObjectFieldValueSeparator(':') + withObjectFieldValueSpacing(Spacing.AFTER)` ✓（**注意该版本没有 `DefaultPrettyPrinter.withObjectFieldValueSeparator(String)`**，且 `Separators` 版只收 `char`）；**唯一配不出的是空数组**——Jackson 固定写 `[ ]`，契约要 `[]`，纠正必须 `extends DefaultPrettyPrinter` 覆写 `writeEndArray` 并自行维护 protected `_nesting` 记账。收益（省 IssueView 里 ~35 行显式拼接）＜ 代价（依赖 Jackson 内部实现 + 孤例抽象：全仓仅此一处手写缩进 JSON，`ToolJson.prettyJson` 默认 `" : "` 不能直用）⇒ **保留手写 writer + 中性名（`indentedJson`）**；若日后要收，走「自研通用 JsonNode 缩进打印器（空数组特判 `[]`）+ 新旧逐字对比测试」，而不是 Jackson printer 子类。
- **登记重复项**：`MessageSanitizer.escapeHtml` 与 `agent.modelcontext.HtmlEntities.escape` 是同表两份实现（五字符实体一致）——跨包可见性所限未收敛（tools → modelcontext 新增依赖会碰分层守卫），留待包结构调整批。
- **后续（用户 2026-10-07 已拍板，待排批）**：① 渲染面换标准——`ConversationSerializer` 族 + `ObservePhase.goMarshal`（改自测）；② Notion 数字 `goFormatG → Double.toString`（Notion 正文文本变，含数字列的源下次同步一次性内容 diff）；③ 契约文案换锚——auth `jsonKindName`/`stringFieldValue`、ASR/VLM `openAiErrorText`/`jsonErrorText`、`jsonTypeLabel`（同 B51 口径：换 Java 文案 + 重录 w5a/w5b/md 金片）；④ 余项——`<nil>` 形态（`ParamValidator.nodeText`、`DocChunkSupport.valueText`）、web 工具类型报错（无钉盲区，先补钉再换）、OIDC state/query escape（`OidcStateCodec.jsonString`→Jackson、`OidcService.queryEscape`→URLEncoder）、测试侧剩余 `go*` 局部名清扫。
**✅ B78（2026-10-07，压缩/观测渲染换 Java 标准：手写 JSON writer 退役）**
- **动作**：① `ConversationSerializer`：删手写 `goMarshal`/`goMarshalInto`/`goEscapeString`（含 HTML 转义 `<> &`→`\u003c`、U+2028/9、数字统一 `Double.toString`）与内部异常 `ArgsRenderException`；`renderToolArgs` 改为标准 Jackson——值走 `ToolJson.write`（递归键排序 + 标准紧凑输出），外层键仍按 **UTF-8 字节序**排序（同一调用渲染恒定），非对象/含非有限数（NaN/Inf 无标准 JSON 形态）回退截断原文。② `ObservePhase.goMarshal` → `argsJson`（体内本就是 `ToolJson.write` + 字节序归一，去名 + 修陈旧 javadoc「HTML 转义」）。
- **可见变化（送进压缩提示词的历史参数文本）**：HTML 实体不再转义（`\u003c` → `<`）、U+2028/9 原样输出；结构与键序不变；数字文本归一口径不变（测试 `fold` 覆盖）。
- **测试收编（不碰实录）**：`ConversationSerializerTest.fold` 增加 `RecordingSupport.normalizeEscapes`（与 B40 同口径：转义形态不再构成断言目标，键序/结构/数字仍被钉住）；测试名去 Go：`renderToolArgsMatchesGoByteForByte`→`renderToolArgsKeepsKeyOrderAndShape`、`serializeToolCallsMatchesGo`→`serializeToolCallsShape`。
- **闸门**：全量 **4,823**/0 失败（6 跳过）+ `spotlessCheck` + 包结构守卫 + 注释棘轮全绿。
**✅ B79（2026-10-07，Notion 数字形态换 Java 标准：goFormatG 族退役）**
- **动作**：`NotionValues.goFormatG` + `shortestRoundTrip`（~80 行手写 `%g`）删除；`jsonNumberToString` 整数分支保留、其余走 Java 标准 `Double.toString`；类 javadoc 的「%g ≠ Double.toString」条目改写为「数字先归一到 double」。
- **可见变化（Notion 条目正文文本，落库 → 检索/LLM）**：`1e+20 → 1.0E20`、`1.2345675e+06 → 1234567.5`、`1e-05 → 1.0E-5`；整数值形态不变（`1000000`）。已入库旧数据文本不变；含数字列的 Notion 源下次同步会产生一次性内容 diff（用户 2026-10-07 已拍板接受）。
- **测试收编**：`numbersFollowGoFormatting`→`numbersFollowJavaFormatting`、`goFormatGMatchesStrconv`→`jsonNumberToStringUsesJavaStandardForm`（断言按 Java 形态重写）。
- **闸门**：全量 **4,823**/0 失败（6 跳过）+ `spotlessCheck` 绿；**主源码 `go*`/`GO_*` 标识符归零**（类级 2026-10-03 清零 + 方法级 B76~B79 清零；其余仅 `GoRecording*` 实录与测试侧局部名）。
**🚧 B80（2026-10-07，契约文案换锚·第一部分：系统设置类型名 → JSON 类型名）**
- **本批只做无金片面**：`SystemSettingRegistry.jsonTypeLabel` 词表换 Java/JSON 标准——`<nil>→null`、`bool→boolean`、`float64→number`、`[]interface {}→array`、`map[string]interface {}→object`；`expected bool` → `expected boolean`（消息模板 `expected integer, got <类型名>` 等六条不变）。
- **原盲区补钉**：新增 `SystemSettingRegistryTest`（6 类型名 + 5 消息 + 索引分支，12 条断言）；**红态探针已验**（词表临时改回旧值 → 两条测试红；恢复后全绿）。金片零变化：已核实 0 个 contracts 夹具钉类型名分支；string 分支 `expected integer, got "abc"`（`adm-settings-put-badtype`）未动。
- **未完成部分与阻塞点（已查实）**：
  ① auth 类型错文案（`json: cannot unmarshal … into Go struct field …`，2 金片 `w5a-auth-switch-badtype` / `w5a-tenant-put-badjson`）：w5a 夹具由 `scripts/record-w5a-golden.sh` 录制（脚本缺省指向 Go `:8080`，可用 `W5A_TARGET_PORT` 指向 Java），**不支持 `-Dcontract.refresh`** ⇒ 需起本地 Java 服务（8083）+ dev 库重录（Postgres/Redis 当前已在跑）。
  ② ASR/VLM 错误文本（`error, status code: %d, status: %s, message: %s, body: %s`，约 7 金片 `w5b-asr-*` / `md-asr-401`）：同理由 `record-w5b-golden.sh` / `record-modeldebug-golden.sh` 录制，需服务 + 模型桩。
  ③ **新发现（超出原批准清单，待拍板）**：gin 校验文案 `Key: '…' Error:Field validation for '…' failed on the '…' tag` 仍钉在 **32 个金片、跨 8 域**（agent/member/reg/init/session/KB 等）——不在「auth/ASR/类型名」清单内，是否另立批换锚。
  ④ 同类非 go 名孪生（`WikiRequestSupport:205` / `McpServiceCrudOps:131` / `OllamaManageService:280` 的 `non-object into Go value …`）：**0 金片钉住**（盲区），改前建议先补钉。
- **闸门**：全量 **4,825**/0 失败（6 跳过）+ `spotlessCheck` 绿。
**✅ B81（2026-10-07，盲区孪生文案换 Java 标准 + 补钉）**
- **动作**：① 新增共享 `ToolJson.nodeTypeLabel(JsonNode)`——Jackson 节点类型名小写（null/boolean/number/string/array/object）；`SystemSettingRegistry.jsonTypeLabel` 收敛为委托（去掉第二份映射，B80 钉子仍绿）；② 三处「非 go 名孪生」换文案（对照 B80 的分析表 ④）：`WikiRequestSupport.readJsonBody` → `Invalid request body: expected JSON object, got …`；`McpServiceCrudOps.updateMCPService` → `expected JSON object, got …`；`OllamaManageService.bindJsonObject` → `expected JSON object, got …`（顺删本地 `jsonKindName`，其 `bool` 词表不合标准）。
- **原盲区补钉（原先 0 金片覆盖）**：新增 `WikiRequestSupportTest`（非对象 array/string + EOF 保持）、`McpServiceCrudOpsBindingTest`（`BizException.appError().message()` 精确断言）、`OllamaBindJsonObjectTest`（array/boolean）；**三处红态探针逐一验过**（临时改坏文案 → 三红；还原后绿）。
- **金片零变化**：已核实 0 个 contracts 夹具钉这三处文案。
- **闸门**：全量 **4,829**/0 失败（6 跳过）+ `spotlessCheck` 绿。
**✅ B82（2026-10-07，gin 校验文案面换锚：17 文件生成点收敛 + 32 金片重锚）**
- **背景**：`Key: '<Struct>.<Field>' Error:Field validation for '<Field>' failed on the '<tag>' tag` 是 gin 校验器文案，散布 **17 个文件**（7 份 helper 副本 + 10+ 内联字面量），钉在 32 个金片 / 8 域（agent/member/reg/init/session/KB/datasource/vectorstore/storage/websearch/im/embed）。
- **新文案（单一实现 `common/web/RequestFields`）**：`field '<camelCase 字段名>' is required` / `is not a valid email address` / `is below the minimum` / `is above the maximum`；其余 tag 兜底 `field 'x' failed validation: <tag>`。字段名 PascalCase→camelCase（`TenantID→tenantId`、`LLMModelID→llmModelId`）；单引号形态（免 JSON/Java 转义）。
- **动作**：① 新增 `RequestFields` + `RequestFieldsTest`（文案 5 条 + 转写 6 条）；② 7 份 helper（`TenantBindSupport`/`AuthBindingSupport`/`QaRequestBinder`/`WikiRequestSupport`/`WebSearchProviderController`/`StorageBackendController`/`VectorStoreController`）与 10+ 处内联字面量（`TenantMemberController`/`TenantInvitationController`/`DataSourceCredentialsController`/`AgentController`/`OllamaManageService`/`InitializationRequests`/`ModelConnectivityTestService`/`ImChannelController`/`SteerController`）全部改调 `RequestFields.message(...)`；③ 32 个金片 + 3 个测试文件内联断言按同一规则转换（含两处跨行拼接断言的重写 + 两个测试名去 Go）。
- **过程事故**：首版文案用双引号（`field "name" is required`）——写入 JSON 夹具需 `"`、写入 Java 字面量也需转义，`compileTestJava` 立即 9 错；改单引号后一次通过。教训：**换锚批的文案字符集先过「夹具/字面量是否需转义」这一关**。
- **登记（后续小批）**：① `structName` 形参保留签名不再渲染（~40 调用点清理）；② 原生错误体解析面（`datasource/connector/notion` 的 `cannot unmarshal non-array` 两处）属第三方连接器，随 connector 批处置。
- **闸门**：全量 **4,831**/0 失败（6 跳过）+ `spotlessCheck` 绿；主源码/金片/测试侧 `Error:Field validation` 残留 **0**。
**✅ B83（2026-10-07，auth 类型错文案 + ASR/VLM 错误文本换锚）**
- **auth（`json: cannot unmarshal …` 族退役）**：`TenantBindSupport.stringFieldValue`→`stringFieldTypeError` 用 `RequestFields.wrongType`；`TenantCrudOps` 非对象 → `ToolJson.expectedObjectMessage`；`AuthSessionOps` 六处（refresh 非对象 / refreshToken 类型 / switch 非对象 / tenantId 非数值 / 小数 / 越界）→ 字段级文案（`field 'tenantId' must be an integer, got string`、`… must be an integer`、`… is out of range`）；顺删三个 Go 面 helper：`AuthSessionOps.jsonKindName`、`TenantBindSupport.jsonKindName`、`AuthController.SWITCH_ANON_STRUCT_TYPE`。
- **ASR/VLM**：`openAiErrorText` 重写为 `HTTP <状态行>: <详情>`（详情优先 `error.message`（字符串/数组），取不到退回 body 原文）；**`jsonErrorText`（Go 逐字符 JSON 报错仿真：空体/非 JSON 起始/literal 中间）整体删除**；传参不变（VLM 侧 `VlmHttpTransport` 共用）。
- **金片**：2 个 auth + 6 个 ASR/VLM（w5b-asr-401/404/500text/modelmissing/storedkey、md-asr-401）按同规则转换，测试自证与实现一致。
- **过程事故**：`wrongType` 首版固定用 "a" → 产出 `must be a integer`，被 `w5a-auth-switch-badtype` 金片当场抓出；改为按首字母判冠词（an/a）后通过。
- **顺修陈旧注释**：`AsrTranscriber` 类 javadoc 的旧文案形态描述与 `%!s(<nil>)` 表述；`WikiIngestConstants` 的截断报错引文。
- **剩余（登记 B84）**：web 工具面 3 处（`WebSearchTool`/`WebFetchTool` 的 `json: cannot unmarshal … into Go struct field …`，LLM 可见、**0 金片**）、`SkillFrontmatter` 2 处（`yaml: unmarshal errors:…`）、Notion 连接器 4 处（`unexpected end of JSON input`/`cannot unmarshal non-array`）；三处均无钉，**先补钉再换**。
- **闸门**：全量 **4,831**/0 失败（6 跳过）+ `spotlessCheck` 绿。
**✅ B84（2026-10-08，最后三处盲区文案换锚 + 补钉）**
- **web 工具面（LLM 可见）**：`WebSearchTool` 五处类型错 → `RequestFields.wrongType`（字段 camelCase：`query`/`count`/`country`/`freshness`/`content`；`count` 期望 integer、`content` 期望 boolean），删本地 `jsonTypeOf`/`fieldTypeMessage`；`WebFetchTool.itemsTypeMessage` → `invalid argument 'items': expected an array of {url, offset?, limit?}, got <类型名>`（删本地类型映射，改用 `ToolJson.nodeTypeLabel`）。
- **技能面**：`SkillFrontmatter` 两处 Go yaml 文案 → `invalid frontmatter: expected a mapping of fields` / `invalid frontmatter: field '<key>' must be a string`。
- **Notion 连接器**：`parsePages`/`parseBlocks` 四处 → `invalid Notion response: 'results' is missing` / `… 'results' must be an array, got <类型名>`；块面前缀 `unmarshal blocks` → `invalid Notion blocks response`；两个 parse 方法改 **static**（纯函数，便于钉）。
- **补钉（原全盲区）**：新增 `WebSearchToolTest`（4 断言）/`WebFetchToolTest`（2 断言）/`SkillFrontmatterTest`（2 断言）+ `NotionClientTest.parseErrorsUseNeutralWording`（3 断言，并更新其原有的旧文案断言）；**四处红态探针逐一验过**（临时改坏 → 5 红；还原 → 绿）。
- **闸门**：全量 **4,836**/0 失败（6 跳过）+ `spotlessCheck` 绿；**主源码 Go 味文案残留归零**（`cannot unmarshal` / `Go value` / `Go struct` / `Error:Field validation` / `%!s(` / `unexpected end of JSON input` / `yaml: unmarshal` 全 0）。
- **长尾（登记，非文案面）**：① `structName` 死形参（~40 调用点）；② `MessageSanitizer.escapeHtml` 与 `HtmlEntities.escape` 重复实现；③ 测试侧剩余 `go*` 局部名（避开 `GoRecording*` 实录）；④ `IssueView.indentedJson` 手写 writer（换 Jackson 需自定义 printer，已评估保留）。
**✅ B85（2026-10-08，escapeHtml 四副本收敛 + 测试侧 go* 局部名清理）**
- **escapeHtml 收敛（真动作：收敛副本）**：盘点出 **4 份同表实现**——`agent/tools/MessageSanitizer.escapeHtml`、`common/prompt/MessageAttachmentsPrompt.escapeHtml`（公开）、`memory/domain/MemoryRender.escapeHtml`（被 `MemoryTextTest` 钉）、`agent/modelcontext/HtmlEntities.escape`（包内）；五字符表（`& ' < > "` → `&amp; &#39; &lt; &gt; &#34;`）逐字一致。新增 `common/web/HtmlText.escape`（**null → 空串**、单趟替换、不二次转义）作为单一实现，四份全部改为委托（后两者保留原签名，调用点零改动），顺带统一原先不一致的 null 行为（两处 NPE / 两处空串）。
- **测试侧 go* 局部名清理（避开实录）**：`RssPureFunctionsTest.goZero→zeroTime`、`StreamJsonTest.goRow→recordedRow`、`McpStubABTest` 八个 `go{Init,Notify,List,Call}Body?` → `recorded*`、`OssMultipartUploadTest.goSpecConstants→specConstantsAreStable`、`JiebaTokenizerDiffTest.goSideDictionaryIsEmpty→emptyDictionaryYieldsNoTokens`、`WikiIngestLanguageTest` 注释去 `goSpace`、`MemoryTextTest.escapeHtmlMatchesGoHtmlPackage→escapeHtmlEscapesFiveChars`。
- **刻意不动**：`GoRecording*` 实录全族；实录字段名字符串（`"goErr"` 等，作为录制 JSON 的键参与比对）；`datasource/connector/rss/HtmlEntities`（同名不同类的 XML 实体表）。
- **过程事故**：`McpStubABTest` 首轮用「只替换首个匹配」的方式改名，前缀重叠（`goInit` ⊂ `goInitBody`）导致声明改了、引用没改（compileTestJava 11 错）；改按「长名优先 + 全量词边界替换」后通过。教训：**改名批一律词边界 + 全量替换，不要 replace-first**。
- **闸门**：全量 **4,836**/0 失败（6 跳过）+ `spotlessCheck` + 包结构守卫绿。
**✅ B86（2026-10-08，structName 死形参清理）**
- **去参 8 处（15 文件）**：`TenantBindSupport.bindingError`、`AuthBindingSupport.bindingError`、`AuthController.bindingError`（含委托调用）、`QaRequestBinder.bindingError`、`TenantMemberController.requireFields`、`WikiRequestSupport.requiredFieldErrors`、`WebSearchProviderController.validatorError`/`bind`、`VectorStoreController.validator`/`parseOrValidator`——`structName` 在 B82 换锚后已无用途；调用点字面量首参（`"createTenantRequest"`/`"WikiPageMoveRequest"`/`null` 等，~31 处）同步摘除。
- **语义判别改造**：`AgentController.bindAgentRequest(rawBody, structName)` 的 `structName` 是真区分器（只有 Create 要求 name）→ 改 `boolean requireName`，调用点 `true`/`false`，字符串比较与 Go 式请求名退场。
- **复查**：全仓 `structName` 出现 **0 处**；`RequestFields.message` 仍是唯一文案出口。
- **闸门**：全量 **4,836**/0 失败（6 跳过）+ `spotlessCheck` + 包结构守卫绿。
