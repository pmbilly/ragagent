# agent 引擎独立成模块 实施计划（阶段 A 契约切断 → 阶段 B 模块化）

> **目标**：把 agent 引擎抽成独立 Gradle 模块 `:agent`（只依赖 `:common` / `:engine`），
> 管理面（`com.ragagent.agent.management`，HTTP + MyBatis CRUD）留在 `domains`。
>
> **判据**：`agent/build.gradle.kts` 只需 `api(project(":engine"))` + `api(project(":common"))`，
> 且 `:agent` 源码对 `:domains` 的 import 为 0（编译期即证）。

## 0. 实测基线（2026-10-12，脚本按"最长前缀匹配"归属模块）

| 方向 | 边数 | 结论 |
|---|---|---|
| `engine` → `agent` | **0** | ✅ 无"能力层反向依赖业务域"的非法方向 |
| 管理面 → 引擎侧 | **0** | ✅ 管理面可原地留 `domains` |
| 引擎侧 → 管理面 | **0** | ✅ 两半已天然分离 |
| agent → 自身子包 | 238 | 随模块一起搬，零成本 |
| **agent → domains 外部** | **59 条 / ~25 文件 / 6 个包** | ⚠️ 阶段 A 要切到 0 |
| domains → agent | 124 条 | 切完变成合法的 `domains → :agent` |

**59 条边明细**（`agent` 侧不含 `management/`）：

| 目标包 | 条数 | 主要使用者 | 处置 |
|---|---|---|---|
| `approval` | 16 | `ApprovalBridge`、`Mcp*` 族 | 随 MCP 适配层外移 |
| `mcp.protocol` | 15 | `McpCatalog` / `McpExposure` / `McpToolWrapper` / `McpOAuthSupport` | 同上 |
| `mcp.domain` | 9 | 上述 + `ToolRegistry`（1 处） | 同上 |
| `mcp.oauth` | 2 | `McpCatalog` / `McpOAuthSupport` | 同上 |
| `knowledge.domain.Chunk` | 9 | `DocChunkSupport`、`tools/knowledge/*`、`SearchAuth`、`WikiReadSourceDocTool`、`FaqSnippet` | 改用 L1 载荷（`ChunkView` / `FaqChunkMetadata`） |
| `audit.domain` | 3 | 仅 `management/controller` | 留 `domains`，零成本 |
| `knowledge.domain.KnowledgeBase`（1）/ `knowledge.service`（1）/ `auth.domain`+`auth.service`（2） | 4 | 均在 `management/service` | 留 `domains`，零成本 |
| `websearch.service` | 1 | `WebSearchTool` | ✅ **已完成**（见下） |

## 1. 阶段 A 三刀

### A-1 ✅ 已完成（提交 `cd8aaaf6`）：websearch 边
- 新增 L2 载荷 `engine/…/retrieval/domain/WebSearchRequest`（租户段 + per-call `maxResults`/`WebSearchFilters`）。
- `WebSearchTool`：`WebSearchBackend.search(long, String, WebSearchRequest, String)`；租户配置改用 L1 `common.tenant.WebSearchConfig`。
- `AgentToolBackends`：`loadTenantWebSearchConfig()` 直接返回 L1 形状；新增 `toServiceConfig(L2 → 域内配置)` 适配。

### A-2 待做：`Chunk` 边（9 条）
- 目标：agent 侧不再 import `com.ragagent.knowledge.domain.Chunk`。
- 路径：`FaqSnippet.faqStandardQuestion(Chunk)` → 改收 L1 `FaqChunkMetadata`；各检索工具内部构造的 `Chunk` → 改 L1 `ChunkView`（字段：id/content/chunkType/chunkIndex/startAt/endAt）。
  - ⚠️ `common/knowledge/package-info` 明确"需要更多字段时先改载荷，别把实体漏出去"——**按此口径扩载荷，不要下沉实体**。
- 影响面：`tools/knowledge/{FaqSnippet,GetDocumentInfoTool,GrepChunksTool,KnowledgeSearchOutputFormatter,KnowledgeSearchTool,ListKnowledgeChunksTool}`、`tools/DocChunkSupport`、`tools/runtime/SearchAuth`、`tools/wiki/WikiReadSourceDocTool` + 相关测试/实录。
- 验证：`*RecordingTest`（knowledge 工具族）逐字节 + 全量。

### A-3 待做：MCP 适配层外移（42 条，最大一刀）
**结论（已核）**：MCP 族本质是"工具注册表 ↔ MCP 域"的**适配器**，应整体搬出引擎；引擎侧只留窄端口。

**搬迁（`com.ragagent.agent.tools` → `com.ragagent.mcp.tools`）**：
`McpCatalog`、`McpCatalogPagination`、`McpExposure`、`McpToolWrapper`、`McpOAuthSupport`（**改名** `McpOAuthPromptSupport`，避开与 `mcp.protocol.McpOAuthSupport` 同名）、`McpDiscoverTool`、`McpCallTool`、`McpRegisteredTool`、`McpInputSchemaValidator`；`ApprovalBridge` → `com.ragagent.approval.AgentApprovalBridge`（`toEventBus` 需 public）。
> `McpCatalogPagination`（包私有）与 `McpDiscoverTool`/`McpCallTool` 有**包私有构造器调用**，三者必须同包同搬。

**新增端口（`:agent` 内新包 `com.ragagent.agent.tools.spi`，只依赖 `:common`）**：

```java
public interface McpToolingGateway {          // 由 MCP 侧实现（装配期注入）
    void prepare(boolean direct);             // 取代 ToolRegistry:359/364（含 1s 宽限，消除对 McpExposure.MCP_STARTUP_GRACE 的读取）
    void refresh();                           // 取代 ToolRegistry:439-504
    void rememberHistory(List<ChatMessage> messages);
    ToolCallTarget callTarget(String toolName, JsonNode rawArgs);
    boolean hasServer(String serviceId);
}

public interface ToolRegistryGateway {         // 由 ToolRegistry 实现（方法 public）
    void registerTool(AgentTool tool);
    void registerDeferredTool(AgentTool tool);
    void setDeferred(String toolName, boolean deferred);   // 必需：MCP 侧要翻 deferred 位
    void removeTool(String toolName);
    AgentTool findTool(String toolName);                   // null 语义，不暴露 ToolNotFoundException
    List<String> toolNames();
    ToolResult executeInternal(ToolRequest request, AgentTool tool);
}
```

**关键点（照做，别踩）**：
1. `ToolRegistry` 里 MCP 相关方法与字段（`mcpCatalog/isMcpPrepared/isMcpDirect/mcpRegisteredTools`，字段 `mcpDirect/mcpPrepared`）**整体删除**，逻辑搬进 MCP 侧的 gateway 实现。
2. `McpToolWrapper` 的 5 个 `protected` 字段 + `withOAuthWaiter` 是"复制实例"的依据 → **把复制逻辑一起搬进 MCP 包**，不要为了跨包去加一堆 getter。
3. `MCP_CALL_ARGUMENTS_HINT`（现 `ToolRegistry:55`）与 `quotedGo`（`:615`）**文本/语义是录制契约** → 常量搬 MCP 包（`McpCatalog.quoteGo` 已存在可复用），**不得改字**。
4. 装配点：`AgentEngineAssembler`（domains）继续 `McpExposure.registerMcpTools(...)`，末尾注入网关（参照 `setSkillsManager` 形态）。
5. 守卫：`McpCatalogGuardedTool` 接口留 `:agent`（`ToolRegistry` 的 instanceof 检查用）。
6. 搬包后 `boot/EngineRecordingTest` 不受影响（不引用 MCP 类型），但 `McpStubABTest` / `ApprovalBridgeTest` 需随包改名。

**顺手登记（不扩大范围）**：① `McpDiscoverTool` 未 `implements OutputLimitProvider` ⇒ `ToolRegistry` 的 instanceof 永不命中，256KB 上限未生效；② `McpExposure.MCP_CATALOG_LOAD_TIMEOUT` 零引用；③ `McpExposure.mcpToolNamesByServiceId` / `getMcpToolsInfo` / `serializeMcpToolResult` 零调用方。

## 2. 阶段 B：建 `:agent` 模块

1. `settings.gradle.kts` 加 `include("agent")`；新建 `agent/build.gradle.kts`（`api(project(":engine"))` + `api(project(":common"))`，测试依赖照 `domains` 现有口径）。
2. `git mv` 主代码 179 文件 → `agent/src/main/java/com/ragagent/agent/**`（`management/` **除外**，留 domains）。
3. 测试树：`domains/src/test/java/com/ragagent/agent/**`（55）+ `domains/src/testFixtures/java/com/ragagent/agent/**`（6）随迁；`boot/src/test/java/com/ragagent/agent/{EngineRecordingTest,management/**}` 中依赖同包 seam 的随迁（split package）。
4. `domains/build.gradle.kts` 加 `implementation(project(":agent"))`；清掉 domains 侧对已迁类的重复声明。
5. 管理面：包名从 `com.ragagent.agent.management` 改回历史名 **`com.ragagent.agentm`**（避免"agent 的包挂在 domains"的语义别扭；2026-10-03 B36 之前即此名）。
6. 验收：全量测试 + `spotlessCheck` + `scripts/check-package-cycles.py`（棘轮）+ `pkg-audit`；`:agent` 的 build 文件只有两个 `api(...)`。

## 3. 风险与已知偶发

- **split package**：`boot` 侧 3 个测试与主树同包，模块化后仍可编译（classpath 模式），但上 JPMS 会断 → 随迁即可。
- **守卫缺口**：`ArchitectureRulesTest`（A1–A15）只管代码级规则，**不校验模块边界** → 模块化的边界保证来自 Gradle 本身（反向 import 直接 compileJava 失败）。
- **已知偶发（与本计划无关）**：`EmbedRateLimiterTest`（✅ 已修，提交 `778ded2a`）、`RedisDataSourceSyncTaskQueueTest.taskIdConflictAcrossInstances`（单跑 3/3 绿，全量偶发一次，待查失败断言）。
