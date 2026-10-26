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

## B269③ 守卫收紧

- `memberAfter` 多行注解盲区修复（`tracing` 族漏检根因）
- 混改名整文件跳过细化：record 分量不再被文件级跳过保护（定理实证）
- `SourceRoots.MODULES` 扩 model-runtime + platform

## B269④ 面收敛盘点（doc-only）

剩余 `@JsonProperty` 改名键按面分类（外部 API 镜像 / jsonb 落库 / REST 响应 / SSE 契约 / 模型协议），逐面给出「可否收敛到 knowledge 规则」与代价，供拍板。不在本批动代码。

## 非目标

- 改名键（248 处 snake 重命名）：外部协议面承重，不动
- `@JsonInclude` 省略协议（238 处）：三态编码是线格式协议本身，不动
- 全局 SNAKE_CASE 命名策略：线格式混杂（SSE snake + `isFallback` camel），不可行
