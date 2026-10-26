# Jackson 注解卫生批次（B269）

> 立项问题：`@JsonInclude` / `@JsonProperty` / `@JsonIgnoreProperties` 是不是 Java 标准？对可读性有无影响？是否必须逐字段写？
>
> 结论：**不是 Java 标准**（Jackson 事实标准，Jackson 3 下注解仍留 `com.fasterxml.jackson.annotation` 包名属官方兼容设计）；可读性影响真实存在；**不必须**——本仓已有三条替代路线（contract→mixin 集中、knowledge→禁注解显式输出、本批清掉同名冗余）。本批次只做「零线格式变化」的收敛，改名键与序锚一律不动。

## 审计读数（2026-10-26）

| 项 | 读数 | 判定 |
|---|---|---|
| 同名 `@JsonProperty`（键=成员名） | 310 处 / 38 文件 | A 可删 242 / B 承重 68 |
| B-68 明细 | 可见性 40（包级私有/仅 getter）· is\* 改键 4（`ContractJsonMixinsEvent`）· 字段序锚 24 | 全留 |
| `ignoreUnknown=true` | 99 处 | **全冗余**（Jackson 3 默认翻转 + `WebConfig:51` + `JsonMappers.lenient():31` 三重保险；8 处手工 mapper 审计 0 黄）|
| 守卫 | `JsonPropertyHygieneTest` 基线 0，但只扫 domains+boot、混改名文件整文件跳过、`memberAfter` 多行注解盲区 | B269③ 收紧 |

## B269① 同名列摘除：**104 处**（record 面 + mixin 钉序面）

删除集（字节级快照证明零线格式变化）：
- **record 分量 95**：GitLabClient 嵌套 record 13、FeishuApiTypes record 45、DocxBlocks record 27、ExtraFieldConfig 8、Config 2
- **queue payload record 2**：`WikiIngestPayload`/`DataSourceSyncPayload` 的 `tracing`
- **mixin `@JsonPropertyOrder` 钉序 7**：ChatMessageMixin（role/content/name/images）、MessageContentPartMixin（type/text）、DocumentInfoMixin（text）
- 顺手修：`AgentFinalAnswerData` javadoc `is_fallback`→`isFallback`（mixin 与前端消费均为 camel，javadoc 漂移）

### G2 class 面实证回退（本批最重要的证据）

原计划删「纯反序列化 class」（Yuque/Ima/Notion/DocxBlock/WikiNode/DriveFile/OAuth 等 ~110 处，出口核查确认无序列化出口）。**临时序快照 harness（反射填充 + `JsonMappers.lenient()` 序列化，改前/改后字节 diff）实证**：混改名 class 删任一同名注解即触发全类型字段序重排——

- WikiNode：`title/creator/owner` 跳最前；DocxBlock：`block_id/parent_id/block_type` 移末尾；OAuthToken：`scope`/`expires_in` 互换
- `AuthServerMetadata.issuer` **整键消失**（setter-only 可见性——`@JsonProperty` 是序列化侧唯一通道，守卫 400 事故的镜像案例）

结论：**序锚 doctrine 对 class 面无条件成立**（与是否有序列化出口无关，任何未来出口都会看到重排字节）→ G2 约 110 处全数回退保留；`WikiIngestCitePipeline.citations` 因外层类无法填充快照、且文件混有 `new_slugs` 改名，同样保守保留。record 面零漂移（record 序=规范构造器序=声明序，定理实证）。

### 验证

- 归一化（剔除对象哈希）快照 diff **零差**
- `run-guards.sh` 48 绿（check-contract-sync / check-json-key-case --strict 零触碰）
- `mvn -pl domains,boot,model-runtime,platform -am test` 绿（含 Feishu/GitLab 进程内桩真 JSON 解析、DataSourceJsonTest 严格往返、金片契约）

## B269② `ignoreUnknown=true` 摘除（99 处）

- B8 裁决「全留」重裁：依据 = Jackson 3 `FAIL_ON_UNKNOWN_PROPERTIES` 默认已改 disabled + `WebConfig`/`lenient()` 双路径显式禁用 + 手工 mapper 审计 0 黄区
- D 类死重 3 文件整删（`SessionListItem`/`MessageSuggestionEvent`/`MessageSuggestionSet`，纯序列化面注解本就无效）
- R 类 3 处保留 value 部分：`ToolResultMixin{outputFiles}`、`SlugUpdateMixin{retractType}`、`ConnectionConfigMixin` 只删 `ignoreUnknown` 半边留 `endpoint`

## B269③ 守卫收紧（含一个存量缺陷修复）

**金丝雀暴露存量缺陷**：`SourceRoots.backend(sourceSet)` 契约是传源集名（内部自拼 `/java`），
而 `JsonPropertyHygieneTest` / boot `ArchitectureRulesTest` / `ClasspathResourcesTest` 三处调用方
传的是 `"main/java"` ⇒ 拼出 `src/main/java/java` 恒不存在 ⇒ **这几个守卫自 2026-10-10（df6a61ed5
诞生提交）起一直空扫描假绿**。已修三处调用方传参（`"main"`/`"test"`），唤醒后：
- domains+boot+model-runtime+platform 四模块真扫描零违规（唤醒未暴露存量违规）
- `MemoryExtractPayload`（Go 队列事故原型，现为 record）的 `language`/`tracing` 同名注解经
  **改前/改后字节校验零差**后删除（+2 处，累计 106）；事故记录是 class 时代历史，record 定理覆盖

其余收紧：
- `memberAfter` 多行注解感知（`tracing` 族漏检根因修复，括号深度跨行消费）
- 混改名整文件跳过细化：record 分量不再被文件级跳过保护（定理实证）
- `SourceRoots.MODULES` 扩 model-runtime + platform
- 类 javadoc 补 B269 判例（record 零漂移 / class 必重排 / B8 重裁）

## B269④ 面收敛盘点（doc-only，清理后读数 2026-10-26）

B269①~③ 后全仓剩余 `@JsonProperty`（改名/承重）467 处，按面分类与收敛判定：

| 面 | 处数 | 代表 | 可否收敛到 knowledge 规则（键名=Java 字段名） |
|---|---|---|---|
| 外部 API 镜像（连接器） | 223 | Feishu/GitLab/Yuque/Ima/Notion | **不可**——键名由外部 API 定死；唯一出路是同名注解（本批已清 record 面），class 面由序锚 doctrine 保护 |
| SSE/契约 mixin 权威面 | 108 | `ContractJsonMixins*` | **已收敛**——注解集中在 6 个 mixin 文件即终态；snake 键是前端契约（B72-B75 锚定），换键=换契约 |
| 模型协议镜像 | 73 | Anthropic/Ollama/llm provider | **不可**——OpenAI/Anthropic/Ollama 线协议定死（`max_tokens`/`top_p` 等） |
| OAuth 协议镜像 | 21 | mcp/oauth | **不可**——RFC 8414/OIDC 注册协议键 |
| jsonb 落库/队列载荷 | ~16 | memory/wiki 载荷 | **语义性保留**——落库键即迁移面（jsonb 已有存量数据），换键需数据迁移；record 同名已清 |
| Langfuse 上报 | 7 | `LangfuseTokenUsage`/`TracingContext` | **不可**——Langfuse ingestion 协议（`lf_*`/token 键） |
| 其余散点 | ~19 | session/websearch/storage 等 REST DTO | **逐面可议**——前后端同仓，理论可向 knowledge 规则收敛，但每处都是换线键=前端联动改动；收益（~19 处注解）对代价（逐面契约迁移）不划算，**建议维持现状**，新增 DTO 执行 knowledge 规则 |

结论：**无下一批机械清理项**。剩余 467 处全部落在「外部协议镜像」或「已集中/已锚定」面上，逐字段注解在
这些面是线格式文档而非噪音。防回流由收紧后的 `JsonPropertyHygieneTest`（四模块真扫描）承担。

## 非目标

- 改名键（248 处 snake 重命名）：外部协议面承重，不动
- `@JsonInclude` 省略协议（238 处）：三态编码是线格式协议本身，不动
- 全局 SNAKE_CASE 命名策略：线格式混杂（SSE snake + `isFallback` camel），不可行
