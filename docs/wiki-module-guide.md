# wiki 模块手册

> **面向读者**：第一次接手 `com.ragagent.wiki` 的架构师 / 高级开发者。
> **目标**：30 分钟建立全局观 → 能定位改动点 → 能安全迭代（本模块有 32 个测试类 / 479 个用例 + 16 个契约金片兜底，改错会立刻红）。
> **数据口径**：2026-10-11 实测（`wc -l` 口径）：**156 个 java 文件 / 23,707 行 / 8 个子包**。
> **本文档的地位**：模块级导览；仓库级作业规范见仓库根 `HANDOFF.md`（§12 结构地图、§13 踩坑清单、§14 逐包重构范式）。

---

## 0. 一分钟速览

**它是什么**：知识库的**内联百科（wiki）域**——把 knowledge 域处理完的文档**再加工成一套人读的页面**，并提供完整的浏览与编辑面。

- 页面体系：页面 CRUD / 修订历史与回滚 / 文件夹树 / 索引页 / 关系图谱 / 统计 / 搜索
- 摄取管线：knowledge 文档处理完成 → 异步 **Map（逐文档抽取）→ Reduce（逐 slug 落页）→ Finalize（索引重建 / 死链清理 / 交叉链接）**，带任务队列、锁、幂等凭据、身份去重
- 撤回管线：文档删除 → 单来源页直接删、多来源页走 LLM 撤回、回收空目录
- 质量面：死链 lint / 自动修复 / 人工问题（issue）三态流转

**它不是什么**（别在这里改）：

| 不负责 | 归属 |
|---|---|
| 文档解析 / 分块 / 向量化 / 摘要（wiki 的"原料"来源） | `knowledge`（两域互相 **0 直接 import**：wiki 经 `common/knowledge` 视图端口读它的数据，经 `common/wiki` 两端口被它触发，见 §1.2/§4.3） |
| agent 的 wiki 十件工具（`WikiReadPageTool` 等）的参数解析与视图 | `agent/tools/wiki`（数据经 `session/AgentToolWikiBackends` 桥到本包 `WikiPageService`） |
| 审计落库 | `audit`（实现接缝 `common/audit/WikiActivityAudit`；缺 bean 时退化为 debug 日志） |
| LLM 调用与重试传输 | `llm`（本域经 `WikiModelResolver` 解析合成模型，`WikiLlmRetryPolicy` 只管策略） |
| wiki 功能开关 | `knowledge` 的 `KnowledgeBaseIndexingStrategy`（`isWikiEnabled`；本域只承载 `wiki_config` 调节项） |

### 图 1：模块全景

```mermaid
graph TB
    subgraph 前端
        FE["wiki 浏览器 / 编辑器 / 图谱 / lint 面板"]
    end

    subgraph wiki_模块
        CTRL["controller/（9 文件：1 个 controller 274 行 + 7 个 ops 协作者）<br/>21 个端点全部在此，@ApiResult 外壳"]
        PAGE["service/page/（22 文件）<br/>页面门面 / 文件夹 / 链接与 lint / slug 锁"]
        ING["service/ingest/（57 文件，域内最大）<br/>Map→Reduce→Finalize 管线 + 队列 / 锁 / 幂等 / 去重"]
        PORT["service/ 根（12 文件）<br/>跨切面端口：ChunkCleaner / ImageEnricher /<br/>KnowledgeFinalizer / ModelResolver + 缺省实现<br/>+ WikiPromptInstructions（一行委托 common）"]
        PRM["prompt/（3 文件）<br/>三条 prompt 模板"]
        REPO["mapper/（7 文件）<br/>MyBatis-Plus 接口"]
        RPO2["repository/（5 文件）<br/>仓储门面 WikiPage · WikiFolder · 死信 · 待重放"]
        DOM["domain/（39 文件）<br/>6 实体 + 载荷 + 常量 + 异常"]
        WIRE["根：WikiRedisWiring<br/>六个协调端口装配"]
    end

    subgraph 相邻域
        KB["knowledge<br/>文档处理"]
        AGT["agent/tools/wiki<br/>wiki 十件工具"]
        SES["session<br/>AgentToolWikiBackends 桥"]
        AUD["audit<br/>WikiActivityAuditRecorder"]
    end

    subgraph 外部
        PG[("PostgreSQL<br/>6 张表 + jsonb")]
        REDIS[["Redis（可选）"]]
        LLM["LLM / VLM"]
    end

    FE --> CTRL
    SES --> PAGE
    CTRL --> PAGE & ING
    ING --> PORT & PRM & PAGE
    PAGE --> REPO
    ING --> REPO
    REPO --> PG
    KB -- "WikiIngestPort / WikiFinalizePort<br/>(common/wiki)" --> ING
    AGT -. 经 session 桥 .-> SES
    AUD -- 实现 WikiActivityAudit --> CTRL
    ING --> LLM
    WIRE -. 切换 InProcess/Redis .-> ING & PAGE
    ING & PAGE -.-> REDIS
    CTRL -.-> DOM
    REPO -.-> DOM
```

**三个必须知道的数字**：**21 个端点全部收在 1 个 controller 里**（knowledge 域是 8 个 controller 68 个端点——两域形态完全不同）；`service/` 三层（根 + ingest + page）合计 16,257 行 ≈ **69%** 的代码量，其中 ingest 一个子包 11,130 行；最大类 760 行（`WikiIngestCitePipeline`），域内 ≥800 行 = 0。

---

## 1. 目录结构与职责

### 1.1 子包清单（放什么 / 不放什么）

| 子包 | 文件/行数 | 放什么 | **不放什么** |
|---|---|---|---|
| 根 `wiki/` | 2 / 130（1 类 + package-info） | `WikiRedisWiring`：六个协调端口的 InProcess ↔ Redis 装配（`wiki.redis-enabled=true` 切换） | 业务逻辑 |
| `controller/` | 9 / 1,633（1 个 controller 274 行 + 7 个 ops 协作者 + package-info） | `WikiPageController` 只做转发（`@ApiResult` 外壳入口）；`WikiPageOps`/`WikiFolderOps`/`WikiStatsOps`/`WikiMaintenanceOps` 拼载荷；`WikiKbAccessGuard` 守卫；`WikiRequestSupport` 解析/错误 | 业务规则（→ `service/page`）、SQL |
| `service/` | 12 / 593 | **跨切面端口与缺省实现**：`WikiChunkCleaner` / `WikiImageEnricher` / `WikiKnowledgeFinalizer` / `WikiModelResolver`（各带 Default 实现）+ LLM 重试策略与记账元数据 + `WikiPromptInstructions`（公共实现上提 `common/prompt` 后的一行委托） | 具体管线步骤（→ 两个子包） |
| `service/ingest/` | 57 / 11,130 | 摄取管线：门面 `WikiIngestService`、Map/Reduce/Finalize 三相、cite 引用管线、任务队列与锁（InProcess/Redis 双实现）、幂等凭据、身份去重、死信与孤儿 op 重放 | 页面读写语义（→ `service/page`） |
| `service/page/` | 22 / 4,534 | 页面服务（接口门面 `WikiPageService` + 729 行实现）、文件夹支撑、链接与 lint（`WikiLinkify`/`WikiCrossLinker`/`WikiDeadLinks`）、slug 锁与匹配、编辑上下文 | 摄取批次编排（→ `ingest`） |
| `mapper/` | 7 / 1,035 | 6 个 MyBatis-Plus 接口（`@MapperScan` 扫描范围）+ `package-info` | — |
| `repository/` | 5 / 1,314 | 4 个仓储门面（`WikiPageRepository` 710 行：软删 / 时间戳 / 链接重算；另有文件夹、死信槽位、待重放队列）+ `package-info` | 业务判断 |
| `domain/` | 39 / 2,504 | 6 个 `@TableName` 实体 + 请求/响应形状 + `WikiConstants`（页面类型 / 状态 / 修订保留策略）+ 异常族 + `WikiConfig` 值类型 | 队列载荷（→ `ingest` 的 `WikiIngestPayload`） |
| `prompt/` | 3 / 834 | `WikiPrompts`（三条 prompt 正文）+ 模板渲染支撑 | 键名解析（解析类型在 `ingest`/`common/wiki`，键被 prompt 钉住，见 §7.6） |

### 1.2 依赖方向

```mermaid
graph LR
    A["controller"] --> B["service/page"]
    A --> C["service/ingest"]
    C --> B
    C --> D["service 根端口"]
    C --> E["prompt"]
    B --> F["mapper"]
    B --> F2["repository"]
    C --> F
    F --> G["domain"]
    B --> G

    B -.->|"读知识数据<br/>经 common/knowledge 视图端口"| H["knowledge（适配器侧）"]
    C -.-> H
    K["knowledge"] -.->|"common/wiki 两端口"| C
    S["session/audit"] -.-> B
    S -.-> G

    style A fill:#e3f2fd
    style C fill:#fff3e0
    style G fill:#e8f5e9
    style H fill:#eceff1
```

**枢纽有两个**：`WikiPageService`（页面门面接口，HTTP 与 session 桥两侧都只认它）和 `WikiIngestService`（实现 `common/wiki` 的 `WikiIngestPort`，对 knowledge 只暴露一个 `enqueueWikiIngest` 方法）。**方向红线**：`knowledge ⇄ wiki` 的包级 import 双向都保持为 **0**——knowledge 的数据 wiki 经 `common/knowledge` 的视图/网关端口读（`KnowledgeBaseLookup` / `ChunkPort` / `KnowledgeSpanPort` 等，knowledge 侧用 `service/` 下的 `KnowledgeBaseLookupAdapter` / `ChunkPortAdapter` / `KnowledgeSpanAdapter` 等实现），解环由 `scripts/check-package-cycles.py` 守卫（基线环 0 组）。要给 wiki 补知识数据面，先在 `common/knowledge` 加端口、knowledge 加适配器，别直接 import。

---

## 2. 数据模型

### 2.1 ER 图（6 张表）

```mermaid
erDiagram
    wiki_folders ||--o{ wiki_pages : "folder_id（空串 = wiki 根）"
    wiki_pages ||--o{ wiki_page_revisions : "slug + version"
    wiki_pages ||--o{ wiki_page_issues : "slug"
    knowledge_bases ||--o{ wiki_pages : "knowledge_base_id"
    knowledge_bases ||--o{ task_pending_ops : "scope_id（task_type=wiki:ingest）"
    task_pending_ops ||--o{ task_dead_letters : "重试耗尽后转移"

    wiki_pages {
        string id PK
        long tenant_id
        string knowledge_base_id FK
        string slug "库内唯一（部分唯一索引，deleted_at IS NULL）"
        string title
        string page_type "summary/entity/concept/index/synthesis/comparison"
        string status "draft/published/archived"
        string content "markdown 正文"
        string folder_id "目录树唯一真相来源"
        string category_path "派生缓存（面包屑）"
        jsonb aliases "字符串数组"
        jsonb source_refs "id|title 约定"
        jsonb chunk_refs "chunk UUID 列表"
        jsonb in_links "反向链接 slug"
        jsonb out_links "出链 slug"
        jsonb page_metadata "任意 JSON"
        int version "乐观锁，仅内容字段变化时递增"
        string last_edit_source "pipeline/agent/user/revert"
        datetime deleted_at
    }
    wiki_page_revisions {
        string id PK
        string slug
        int version
        string content "整页快照"
        string edit_source
        string editor_id
    }
    wiki_folders {
        string id PK
        string knowledge_base_id FK
        string parent_id
        string name
    }
    wiki_page_issues {
        string id PK
        string slug FK
        string issue_type
        jsonb suspected_knowledge_ids
        string status "pending/ignored/resolved"
        string reported_by
    }
    task_pending_ops {
        long id PK
        string task_type "wiki:ingest / wiki:finalize"
        string scope "knowledge_base"
        string scope_id
        string op "ingest/retract（finalize：slug/change/folder_prune）"
        string dedup_key "幂等"
        jsonb payload
        int fail_count
        datetime claimed_at
    }
    task_dead_letters {
        long id PK
        string task_type
        string related_id "knowledge_id，失败聚拢"
        jsonb payload
        string last_error
        int fail_count
        datetime failed_at
    }
```

> 两点结构说明：① `wiki_pages.category_path`/`wiki_path`/`depth` 是从 `folder_id` 派生的**反规范化缓存**，每次写入重算，换取 list/index/search 免 join（`WikiPage` javadoc）；② `task_pending_ops`/`task_dead_letters` 是 wiki 自有的任务持久化，取代了历史 Redis `wiki:pending:<kbID>` 列表（4 万文档规模下会被 TTL 驱逐，`WikiIngestService` javadoc）。

### 2.2 jsonb 列与值类型对照

| 列 | 类型 | 说明 |
|---|---|---|
| `wiki_pages.aliases` / `category_path` / `source_refs` / `chunk_refs` / `in_links` / `out_links` | `WikiStringListTypeHandler`（字符串数组） | 六列同一家族；空列表序列化为 `null`（`EmptyListAsNullSerializer`） |
| `wiki_pages.page_metadata` | `PgJsonTypeHandler` + `insertStrategy = ALWAYS` | 任意 JSON；**必须 ALWAYS 插入**——MyBatis-Plus 默认对 null 省列会落到 DB 默认 `'{}'`，而契约 golden 里该键是 `null`（字段处注释） |
| `wiki_page_issues.suspected_knowledge_ids` | `WikiStringListTypeHandler` | 列可空，读回空列表 |
| `task_pending_ops.payload` / `task_dead_letters.payload` | `PgJsonTypeHandler` | 队列 op 载荷 |
| `knowledge_bases.wiki_config`（knowledge 域的表） | `WikiConfig`（值类型在本域 `domain/`） | `synthesisModelId`/`maxPagesPerIngest`/`extractionGranularity`/`contentInstructions`；由 KB 更新请求的 `config.wiki_config` **原样落库**，知识域不重写内层键 |

**硬约定（踩过坑）**：六个 `@TableName` 全部带 `autoResultMap = true`；`WikiPage` 的派生访问器必须 `@JsonIgnore`（见 §7.5）；`WikiConfig` 读路径用 `FAIL_ON_UNKNOWN_PROPERTIES=false` 的 mapper（历史行里有 `enabled`/`auto_ingest` 等退役键）。

### 2.3 状态与枚举

| 枚举 / 常量 | 取值 | 用在哪 |
|---|---|---|
| `WikiConstants.PAGE_TYPES` | `summary` / `entity` / `concept` / `index` / `synthesis` / `comparison` | `wiki_pages.page_type`（synthesis/comparison 只能由 agent 工具创建，ingest 不自动生成） |
| `WikiConstants.STATUSES` | `draft` / `published` / `archived`（SQL 默认 `published`） | `wiki_pages.status` |
| `EDIT_SOURCE_*` | `pipeline` / `agent` / `user` / `revert`（历史空串回落 pipeline） | `last_edit_source`，"页面被谁写过"的信号 |
| `WikiExtractionGranularity` | `standard` / `exhaustive`（空值与未知值归一 standard） | `wiki_config.extraction_granularity`，控制抽取粒度 |
| `WikiPageIssue.status` | `pending` / `ignored` / `resolved`（SQL 默认 `pending`） | lint 问题三态（`WikiMaintenanceOps` 校验） |
| `WikiPendingOp.op` | `ingest` / `retract` | `task_pending_ops`（`task_type="wiki:ingest"`） |
| finalize op | `slug` / `change` / `folder_prune` | `task_pending_ops`（`task_type="wiki:finalize"`，独立通道） |
| 图谱模式 | `overview` / `ego` | `GET /graph?mode=` |
| 修订保留 | 软上限 50 / 页（可清理 `""` 与 `pipeline` 来源）/ 硬上限 200 / 页 | `WikiConstants` 修订清理策略 |

---

## 3. HTTP 接口面

### 3.1 端点分组（1 个 controller / 21 个端点）

唯一前缀：`/api/v1/knowledgebase/{kbId}/wiki`（路径变量 `{kbId}`/`{folderId}`/`{issueId}` 为 **camelCase**；catch-all `{*slug}` 捕获值带前导 "/"，见 §7.4）。

所有端点都声明在 `WikiPageController`（`@ApiResult`），执行体分派给四个 ops 协作者：

**页面 CRUD**（`WikiPageOps`，5 个）

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/pages` | 页面列表（分页/类型/文件夹/路径过滤），`data` 内 `{pages,total,page,pageSize,totalPages}` |
| POST | `/pages` | 创建页面 → **201**（body 仍是外壳） |
| GET | `/pages/{*slug}` | 页面详情（catch-all slug） |
| PUT | `/pages/{*slug}` | 编辑（乐观锁 `version`；冲突 **409**，`data` 内 `{currentVersion,error}`） |
| DELETE | `/pages/{*slug}` | 删除（软删）→ **200 + 外壳**（204 已退役） |

**修订历史**（2 个）：`GET /revisions/{*slug}`（列表）、`POST /revert`（回滚到历史版本）。

**文件夹树**（`WikiFolderOps`，5 个）：`GET /folders`（树）、`POST /folders`（**201**）、`PUT /folders/{folderId}`（重命名/移动）、`DELETE /folders/{folderId}`（空才能删，**200 + 外壳**）、`PUT /move-page`（页面换文件夹）。

**索引 / 图谱 / 统计 / 搜索**（`WikiStatsOps`，4 个）：`GET /index`（目录页）、`GET /graph`（关系图谱，overview/ego）、`GET /stats`、`GET /search`（`data` 内 `{pages:[...]}`）。

**维护 / 问题**（`WikiMaintenanceOps`，5 个）：`POST /rebuild-links`、`GET /lint`（体检报告）、`POST /auto-fix`（`data` 内 `{fixed,message}`）、`GET /issues`（`data` 内**裸数组**）、`PUT /issues/{issueId}/status`（三态流转）。

### 3.2 契约约定（改接口前必读，本域与 knowledge 域**不同**）

| 约定 | 说明 |
|---|---|
| 字段名 | **JSON 名 = Java 字段名（camelCase）**——载荷键、路径变量皆是；**包根 / `domain` package-info 与 `WikiPage` javadoc 里"JSON 键为 snake"的陈述已过期**，以 fixture（`wiki-page-get.json`）与 `WikiConstants` 为准 |
| 信封 | **统一响应外壳**：控制器 `@ApiResult`，成功一律 `{code:0, message:"ok", data:…}`；实体直出 / 裸数组 / 单键 map 都装进 `data`。`data` 里的**载荷形态**仍分三路：实体直出（键序 = 领域字段声明序）、raw map（`{currentVersion,error}`、`{fixed,message}` 等，按**键字母序**）、裸数组（issues）；search 是 `{pages:[...]}` |
| 状态码 | 语义在 HTTP 状态上：创建 **201**、乐观锁冲突 **409**、删除 **200**（204 退役）；advice 只包 body 不改状态——409 的响应体也是外壳包着 `{currentVersion,error}`（契约样例逐字节钉死） |
| 键序 / 空值 | 键序 = 领域字段声明序；**键恒输出**（`deletedAt:null` 也在）；空列表 → `null`（`EmptyListAsNullSerializer`） |
| 错误 | **统一外壳**：ops 抛 `RawJsonError` → 控制器 handler 经 `AppError.ofHttpStatus` 映射成 `{code:N, message, data:null}`（400/403/404 ⇒ 1010/1002/1003），HTTP 状态保持原样；KB 访问拒绝走全局 `GlobalExceptionHandler`，同一个壳。文案仍是**英文**，KB 校验文案保留 `error code: %d, error message: %s` 前缀（如 KB 未启用 wiki → 400 + 前缀文案） |
| 守卫 | 读端点：Viewer+ 角色 + KB 读权限（跨租户经 org-share / shared-agent 只读授予）；写端点：创建者本人或 Admin+（否则 403）+ KB 写权限。角色下限在 `WebConfig` 注册，KB 访问判定由 `WikiKbAccessGuard` 承接 |

---

## 4. 核心链路

### 4.1 摄取管线（knowledge 处理完成 → 四阶段落页）

```mermaid
sequenceDiagram
    participant KW as KnowledgeProcessWorker<br/>(knowledge 域)
    participant P as common/wiki<br/>WikiIngestPort
    participant S as WikiIngestService（门面）
    participant DB as task_pending_ops
    participant Q as 任务队列<br/>(InProcess/Redis)
    participant H as WikiIngestBatchHandler
    participant M as MapPhase（逐文档）
    participant R as ReducePhase（逐 slug）
    participant F as FinalizePhase
    participant PS as WikiPageService

    KW->>P: enqueueWikiIngest(tenant, kb, knowledge)
    P->>S: 委托（WikiIngestService 实现端口）
    S->>DB: 写 op（op=ingest，dedup_key 幂等）
    S->>Q: 入队（TaskID 合并 / 在途限流）
    Q->>H: 取批次（claim 互不相交的行）
    H->>M: ① Map：逐文档 LLM 抽取/摘要/分类
    Note over M: 三条 prompt 在 prompt/WikiPrompts<br/>键名解析面冻结（§7.6）
    H->>R: ② Reduce：逐 slug 落页
    Note over R: slug 锁互斥 + 身份认领去重<br/>（认领失效 = 同标题建出两页）
    R->>PS: 写 wiki_pages + 修订
    H->>F: ③ Finalize（独立 finalize 通道）
    Note over F: 索引导语重建 / 死链清理 /<br/>交叉链接注入 / 空目录剪枝
    F->>PS: rebuildIndexPage / injectCrossLinks
    Note over H: cite 引用管线（WikiIngestCitePipeline，域内最大类）<br/>写 source_refs "id|title" 与 chunk_refs
```

**失败与重试**：op 重试预算耗尽 → 落 `task_dead_letters`（`related_id=knowledge_id` 让失败聚拢到源文档）；孤儿 op 由 `WikiPendingOpReplayer` 启动重放；`WikiCleanupScope` 保证批次异常退出时释放认领，不用干等 `CLAIM_STALE_AFTER`（90 分钟）。**并发模型**：Standard 模式同 KB 多批次并发、靠认领互不相交行；Lite 模式（无 Redis 单进程）每 KB 串行。

### 4.2 页面编辑链路（HTTP → 门面 → 仓储）

```mermaid
flowchart TD
    A["PUT /pages/{*slug}"] --> G["WikiKbAccessGuard<br/>KB 启用 wiki? 角色与所有权"]
    G -->|"拒绝"| X1["403/400<br/>外壳 + error code 前缀文案"]
    G --> O["WikiPageOps<br/>解析 rawBody → WikiEditContext<br/>(edit_source=user)"]
    O -->|"version 不匹配"| X2["409 外壳<br/>data: {currentVersion, error}"]
    O --> S["WikiPageService 门面<br/>（HTTP 与 session 桥共用）"]
    S --> I["WikiPageServiceImpl<br/>slug 清洗 / 链接修复 repairContentLinks"]
    I --> R["WikiPageRepository<br/>软删 deleted_at IS NULL 显式写出<br/>时间戳补齐 / 链接重算"]
    R --> DB[("wiki_pages<br/>+ wiki_page_revisions")]
    I --> REV["version 仅在 title/content/summary/<br/>page_type/status 真变化时递增"]
```

**要点**：`version` 是"页面被编辑过"的真实信号——链接维护、同内容重摄取、后台同步**不递增**它（`WikiPage.version` javadoc）；每次用户可见变更落一条整页快照进 `wiki_page_revisions`，`last_edit_source` 随快照进历史，`POST /revert` 产生 `edit_source=revert` 的版本。文件夹归组的唯一真相是 `folder_id`，`categoryPath` 等只是缓存。

### 4.3 与 knowledge 域的协作（端口接缝）

```mermaid
flowchart LR
    subgraph knowledge_域
        W["KnowledgeProcessWorker<br/>处理五阶段完成"]
        F2["finalizeWikiSubtask 调用点"]
        AD["service/ 适配器：<br/>ChunkPortAdapter / KnowledgeSpanAdapter /<br/>KnowledgeBaseLookupAdapter / KnowledgeFinalizeAdapter"]
    end

    subgraph common 端口层
        P1["common/wiki：WikiIngestPort<br/>（enqueueWikiIngest 1 方法）"]
        P2["common/wiki：WikiFinalizePort"]
        CG["common/knowledge：ChunkPort /<br/>KnowledgeSpanPort / KnowledgeBaseLookup /<br/>KnowledgeFinalizePort + 视图值类型"]
        VT["common/wiki：ExtractedItem / SlugUpdate /<br/>WikiImageMarkup / LanguageProperties"]
    end

    subgraph wiki_域
        S["WikiIngestService<br/>实现 WikiIngestPort"]
        D["DefaultWikiKnowledgeFinalizer<br/>经 KnowledgeFinalizePort 递减计数，归零晋升 completed"]
    end

    W --> P1 --> S
    F2 --> P2 --> D
    W -.->|"读 chunk/span 走端口"| CG
    CG -.-> AD
    S & D -.->|"经 CG 读知识数据（0 直接 import）"| K2["knowledge 表"]
    S -.-> VT
```

**为什么这样**：两域曾经互相 import 成环，解法是把知识数据读面也端口化——wiki 侧（ingest 三相、cite 管线、lint、页面服务共 20 个文件）只认 `common/knowledge` 的网关/视图（`ChunkPort` / `KnowledgeSpanPort` / `KnowledgeBaseLookup` 等），knowledge 侧用 `service/` 下的 `*Adapter` 实现它们；`DefaultWikiKnowledgeFinalizer` 的"递减 + 晋升"写也走 `common/knowledge/KnowledgeFinalizePort`。**现在两域包级 import 双向为 0**（环守卫基线环 0 组）。**撤回**走同一队列的 `retract` op：`WikiRetractPayload` 携带文档写过的 pageSlugs 与 folderIds → 单来源页删、多来源页 LLM 撤回、回收空目录。

---

## 5. 改哪里（场景 → 文件）

| 我想… | 主要改这里 | 别忘了 |
|---|---|---|
| 改页面 CRUD / 编辑语义 | `service/page/WikiPageService`（门面）+ `WikiPageServiceImpl` | 409 冲突形态与英文文案在 `controller/WikiPageOps`；补 `wiki-*` fixture |
| 改 HTTP 形态 / 错误文案 | `controller/WikiPageOps` + `WikiRequestSupport` | 信封已是统一外壳（§3.2）；`data` 里的载荷形态（实体直出/raw map/裸数组）是对 Go 期契约的保真，改它们 = 改前端 |
| 改抽取 prompt / 文案风格 | `prompt/WikiPrompts` + `service/ingest/WikiIngestLlmSupport` | LLM 解析面键名（`source_chunks` 等，见 §7.6）被 prompt 正文钉住，改键必须连 prompt 一起改 |
| 改 Reduce 落页 / chunk 引用 | `service/ingest/WikiIngestReducePhase` + `WikiIngestCitePipeline` | `source_refs` 是 `id` 与 `title` 竖线拼接的约定，展示侧按竖线切分 |
| 改 Finalize（索引/死链/交叉链接） | `service/ingest/WikiIngestFinalizePhase` + `page/WikiDeadLinks` / `WikiCrossLinker` | finalize 是独立任务通道（`wiki:finalize`，op 三种） |
| 改去重 / 身份认领 | `service/ingest/WikiIdentityDedup` + `WikiIdentityClaimStore`（InProcess/Redis） | 认领失效直接表现为"同标题建出两个页面" |
| 改文件夹树 | `service/page/WikiPageFolderSupport` + `repository/WikiFolderRepository` | `folder_id` 是唯一真相，`categoryPath` 是派生缓存，写入要重算 |
| 改 lint / 自动修复 | `service/page/WikiLintService` | issue 三态校验在 `controller/WikiMaintenanceOps` |
| 改 slug 规则 / 别名匹配 | `service/page/WikiSlugHandles` + `SlugFuzzy` + `ingest/NewSlugFromCitation` | 唯一索引是**部分唯一**（`WHERE deleted_at IS NULL`），已删 slug 可复用 |
| 改多副本协调 | 根 `WikiRedisWiring` + 各端口 Redis 实现 | `wiki.redis-enabled=true` 才切 Redis；@Primary 必须因 InProcess 是无条件 @Component |
| 加表 / 加字段 | `domain/` 实体 + `mapper/` | **schema 两处同改**：`migrations/versioned/V1__baseline.sql` + `domains/src/testFixtures/java/com/ragagent/TestSchema.java`；jsonb 记得 `autoResultMap` |
| 改图谱 / 统计 | `service/ingest/WikiGraphCalculator` + `controller/WikiStatsOps` | 图谱"熟悉知识"叠加层（FamiliarKnowledgeIDs）恒 null，是已知差异 |
| 改 agent 的 wiki 工具行为 | **不在本包**：`agent/tools/wiki/`，数据面在 `session/AgentToolWikiBackends` | 工具只认 `WikiPageService` 门面；实录测试在 `agent/tools/wiki/` |

---

## 6. 常见迭代 SOP

> 铁律：**一次只动一个轴**；每步结束**全绿**再走下一步（哪怕只是移动文件）。

```bash
# 每次改动后必跑（约 3 分钟）
cd ~/ragagent && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :domains:test :domains:spotlessCheck
# 结构/契约类守卫（包环、键名口径、外壳合规等十一守卫）可单跑：
./gradlew :guards
# 若动了前端可见契约（字段名/信封/状态码），同批带前端：
cd frontend && npx vue-tsc --build --force && npm test
```

**A. 改页面语义**：`WikiPageService` 接口 → `WikiPageServiceImpl` → 视情况动 `WikiPageRepository`（SQL 面）→ 补 `wiki-page-*.json` fixture → 三绿 → 提交。

**B. 改摄取行为**：先判断动的是哪一相（Map=prompt 面 / Reduce=落页面 / Finalize=收尾面）→ 对应 Phase 类 → prompt 改动要同步核对解析面键名 → `WikiIngest*Test` 全绿 → 提交。

**C. 加端点**：`WikiPageController` 加转发方法 + 对应 Ops 协作者加执行体 → 形态遵守 §3.2（外壳恒存在；`data` 载荷形态先对齐同类端点）→ 补 fixture → 三绿 → 提交。

**D. 加字段**：`domain/` 实体（jsonb 列记得 typeHandler + `autoResultMap`）→ baseline SQL + `TestSchema` → fixture 重录（`-Dcontract.refresh=true` 可批量）→ **重录后必须结构化复核差异** → 三绿；前端可见则同批改 `frontend/src/api/wiki/`。

**E. 重构（拆类/移动）**：沿 `// ════ 段 ════` 边界拆 → 门面留薄委托 → 测试随类同包 `git mv` → 每包补 `package-info` → 三绿 → 提交（落刀写盘一律单空行，`wc -l` 榜单先看空白率；不得新增 >600 行主源码——大文件棘轮守卫）。

---

## 7. 模块约定与坑（必读）

1. **两域包级 import 双向保持 0**：knowledge → wiki 与 wiki → knowledge 都不准直接 import（现状 0，`scripts/check-package-cycles.py` 守卫基线环 0 组）。wiki 要读知识数据走 `common/knowledge` 端口，knowledge 触发 wiki 走 `common/wiki` 两端口。
2. **代码里的"JSON 键为 snake"注释已过期**：包根 package-info、`domain/package-info`、`WikiPage` javadoc 仍写着 snake——现状是 **camelCase**（载荷、路径变量皆是）。**以 fixture 和 `WikiConstants` 为准，别照这些注释写 snake 键**；改到相关文件时顺手更正。
3. **`data` 载荷形态是前端契约，别"顺手归一"**：统一外壳落地后错误已单轨（见 §3.2），但 `data` 里实体直出 / raw map 键字母序 / 裸数组的三路形态、错误**英文文案**与 `error code: %d` 前缀仍是对 Go 期契约的保真——改形态 = 改前端，须同批。
4. **catch-all slug 带前导 "/"**：`/pages/{*slug}` 捕获值带斜杠，取用前必须剥掉 + trim（`WikiRequestSupport.getSlugParam`），否则查不到页。
5. **派生访问器必须 `@JsonIgnore`**：`WikiPage.sourceKnowledgeIDs()`/`builtFrom()` 不加注解会被 Jackson 当属性序列化，回读 jsonb 还会触发 `UnrecognizedPropertyException`——`WikiPage` javadoc 称之为"复发率最高的坑"。
6. **LLM 输出解析面冻结**：`@JsonProperty` 现存 10 处、分布在 4 个类——`common/wiki/ExtractedItem`（`name`/`slug`/`aliases`/`description`/`details`/`source_chunks` 6 处）、`page/NewSlugFromCitation`（`source_chunks`）、`ingest/WikiIngestCitePipeline.CitationBatchResult`（`citations`/`new_slugs`）、`ingest/WikiIngestPayload`（`tracing`）；`CombinedExtraction` 走 camel 字段名（`entities`/`concepts`）。这些键名由 `WikiPrompts` 三条 prompt 正文钉住——改 Java 侧键名必须连 prompt 一起改，属行为面，另批处理（HANDOFF §15 边界清单）。
7. **`page_metadata` 必须 `insertStrategy = ALWAYS`**：MyBatis-Plus 默认对 null 字段省列会落到 DB 默认 `'{}'`，而契约 golden 里该键是 `null`（字段处注释）。
8. **KB 配置写侧键名**：`knowledge_bases.wiki_config` 由 KB 更新请求原样落库，**必须用 `WikiConfig` 的 camelCase 字段名**；历史 snake 键被静默忽略——表现为"界面选了合成模型但 ingest 报 `missing_synthesis_model`"（`WikiConfig` javadoc）。
9. **多副本协调默认是单 JVM 语义**：六个协调端口（slug 锁 / finalize 锁 / 身份认领 / 在途限流 / 任务队列 / 删除墓碑）默认全 InProcess，多实例部署必须 `wiki.redis-enabled=true`；漏配的症状是跨实例互斥全部失效。
10. **队列载荷的追踪键是平铺 legacy**：五个 `lf_*` 组件平在 `WikiIngestPayload` record 上（键名刻意 snake、空值整键省略），结构视图收在嵌套键 `tracing` 里；四个域的队列载荷同形，别在本域单方面"纠正"它们。

---

## 8. 测试与验证

- **规模**：32 个测试类 / 479 个用例——`:domains` 侧 `domains/src/test/java/com/ragagent/wiki/` 25 类（315 用例，摄取管线/页面服务/解析单测为主），`:boot` 侧 `boot/src/test/java/com/ragagent/wiki/` 7 类（164 用例：`WikiContractTest`、`controller/WikiHttpContractTest` 覆盖 21 个端点，另有仓储与服务测试）。
- **邻接测试**：`agent/tools/wiki/` 2 个实录类（17 用例）、`boot` 的 `session/service/AgentToolBackendsWikiTest`（12 用例）。
- **fixture**：`domains/src/testFixtures/resources/contracts/` 下前缀 **`wiki-*` 共 16 个**（覆盖 21 个端点的代表性形态：创建 403、空文件夹、图谱、索引、lint、404、乐观锁更新等）。注意两种存法：**错误/创建/更新类金片存完整外壳**（`wiki-page-not-found.json` 含 `code/message/data`，逐字节比对）；**载荷类金片存 `data` 下钻后的形态**（`wiki-page-get.json` 无外壳，测试先 `payload()` 下钻再比）。
- **比较口径**：契约比较器是**语义比较**（键序 / 转义归一化后比），fixture 锚定的是**本仓自己的行为**；掩码正则按键名锚定，**键改名必须同步放宽 `[a-z_]+` → `[A-Za-z_]+`**（否则掩码静默失效）。
- **已知偶发 2 例**（全量并发下偶发，遇到先单独重跑，别误判回归）：
  - `WebToolsRecordingTest.searchWithContentFetchesLeadingPagesViaSharedFetchTool`
  - `EvaluationContractTest.getTerminalRunsExecution`（单独 `--tests "*EvaluationContractTest"` 通过）
- **另防环境泄漏**：source 过 `.env` 的 shell 跑全量会带出 `SYSTEM_AES_KEY`，出现"孤零零 1 个环境相关失败"先 `env | grep SYSTEM_AES`。
- **改前端可见契约时**：后端与前端**同批**改完再提交（`frontend/src/api/wiki/` 是本域类型面）；金片键签名变化会被契约同步守卫拦下要求签字。

---

## 9. 已知待办与风险（接手后优先看）

| 项 | 性质 | 建议 |
|---|---|---|
| 包根 / `domain` package-info 与 `WikiPage` javadoc 的"snake 键"陈述过期 | **文档债** | 改到相关文件时顺手更正，防止下一个新人照注释写 snake 键（§7.2） |
| `process_overrides` 移植缺口（逐文档摄取参数不生效且无提示） | **产品缺口（用户定调）** | 2026-10-02 拍板：保留现状、界面控件不动、未来补后端；**别"顺手修"** |
| LLM 解析面 `@JsonProperty` 10 处 + `lf_*` 5 键 | 永久冻结 | 前者键名连 prompt（§7.6），后者是平铺追踪载具；都不在本域换锚范围内 |
| 图谱"熟悉知识"叠加层恒 null | 已知差异 | 无对应模块（controller javadoc"已知差异"条）；前端要接时先立产品项 |
| 任务侧 langfuse tracing 未接入 | 观测缺口 | `WikiIngestBatchHandler` javadoc"已知取舍"明示；HTTP 入口侧已接，任务侧另批 |
| Standard 模式同 KB 并发批次依赖认领 + 90 分钟 stale 回收 | 设计取舍 | 崩溃释放已由 `WikiCleanupScope` 兜底；改并发语义前先读 `WikiIngestBatchHandler` javadoc |
| 域内 ≥800 行 = 0，最大 760（`WikiIngestCitePipeline`） | 已达标 | 无需再拆；新代码照 §1.1 的子包职责落位（大文件棘轮：不得新增 >600 行主源码） |

---

## 10. 速查：本模块的"地标"

| 想知道 | 看这里 |
|---|---|
| 某个接口怎么走 | `controller/WikiPageController` → 对应 Ops 协作者 → `service/page/WikiPageService` |
| 摄取全流程 | §4.1 + `service/ingest/WikiIngestBatchHandler`（Map→Reduce→Finalize） |
| 页面为什么"没被后台写花" | `WikiPage.version` 的递增判据 + `last_edit_source`（§4.2） |
| 抽取 prompt 在哪 | `prompt/WikiPrompts`（三条 prompt；解析面键名被它钉住，§7.6） |
| 同标题为什么不会建两页 | `service/ingest/WikiIdentityClaimStore`（InProcess/Redis）+ slug 锁 |
| 文档删除后页面怎么撤 | `WikiRetractPayload` + `WikiIngestEnqueueOps.enqueueWikiRetract`（retract op） |
| 多副本怎么部署 | 根 `WikiRedisWiring`（`wiki.redis-enabled=true` 切六个 Redis 实现） |
| wiki 配置长什么样 | `domain/WikiConfig`（javadoc 即契约）+ `knowledge` 的 `KnowledgeBaseIndexingStrategy.isWikiEnabled`（开关） |
| 死链 / 问题怎么流转 | `service/page/WikiLintService` + `controller/WikiMaintenanceOps`（三态） |
| 目录为什么这样分 | 本文 §1 + 9 份 `package-info.java`（⚠️ 其"snake"陈述已过期，见 §7.2） |
| 结构决策的来龙去脉 | `docs/backend-package-map.md`（`wiki/service` 拆分、`knowledge ⇄ wiki` 解环与 common 端口化） |
