# ragagent

企业级 RAG + Agent 平台：知识库问答、Wiki 摄取、Agent 工具族、IM 渠道、MCP 接入。
后端 **Java 21 / Spring Boot 3.3.5 / MyBatis-Plus**，前端 **Vue 3.5 + Vite 7**，单仓库前后端同构交付。

> **项目背景**：本仓由 [Tencent/WeKnora](https://github.com/Tencent/WeKnora) 的 Go 后端移植而来，
> 并已于 2026-09 从"逐文件复刻 Go"转为**Java 本位演进**（详见 [HANDOFF.md](HANDOFF.md) §0/§2）。
> 当前目标不再是与 Go 仓字节级一致，而是**按 Java 生态的主流标准做可读性与架构收敛**。
> **接手本仓请先读 [HANDOFF.md](HANDOFF.md)** —— 背景、已定决策、踩坑史与批次纪律都在那里。

---

## 总体架构

四个 Gradle 模块，**依赖单向、边界由编译期强制**——不是文档约定：往 `:engine` 里写一行业务域 import 就编译失败（ArchUnit + 包级棘轮双守）。

```
   :boot  ──────►  :domains  ──────►  :engine  ──────►  :common
  组合根              业务域             能力层            共享内核
  入口 + 装配         22 个业务包        8 个能力包        4 个平台包
  13 文件            1,383 文件         295 文件          219 文件
  1.7k 行            217k 行            50k 行            17.5k 行
```

| 模块 | 职责 | 依赖 |
|---|---|---|
| `:boot` | 组合根：入口类、全局装配（`config/` 的 `*WiringConfig`）、扫描、打包。**只出不进**（无任何模块反向依赖它） | → domains / engine / common |
| `:domains` | 业务域本体：HTTP 面（52 个 `*Controller`）+ 持久层（knowledge、agent、session、wiki、im、datasource…22 个包） | → engine / common |
| `:engine` | 能力层：检索引擎适配（8 家向量库）、LLM provider 族、chatpipeline 问答编排、embedding/rerank 客户端、modelcontext。**不得依赖业务域** | → common |
| `:common` | 共享内核：跨域端口与契约载荷、横切基础设施（web/error/approval）、事件总线（`event/`）、SSE 流管理（`stream/`）、追踪（`tracing/`）。**零 project 依赖** | 无 |

**跨模块解耦靠端口下沉**：`:engine` 需要业务词汇时，接口定义下沉到 `:common`（如 `KnowledgeBaseGateway`、`SessionMessagePort`），由 `:domains` 提供实现（`KnowledgeBaseService`、`MessageRepository`）。依赖永远指向下层，无环；`scripts/check-package-cycles.py` 守着。

> **包根共享，边界靠规则不靠前缀**：四个模块的 Java 包都挂在同一个 `com.ragagent` 根下——`:engine` 的包是
> `com.ragagent.llm` / `com.ragagent.retrieval` / `com.ragagent.chatpipeline`…（**没有** `com.ragagent.engine.*` 这层）。
> 因此"这段代码属于哪个模块"无法从包名看出来，模块边界由 ArchUnit 规则 + 包级棘轮脚本在**编译期/CI 强制**。
> 阅读代码时以 `docs/backend-package-map.md` 的模块归属为准。

**一次 RAG 问答的链路**（自上而下，全部单向）：

```
POST /api/v1/session/qa (session/*Controller)
  → session/service 编排（QaSseOrchestrator SSE 流）
    → :engine chatpipeline/ 23 个插件流水线（检索→重排→生成→引用装配）
      ├─ retrieval/ HybridSearchService（pgvector/BM25 混合检索 + 融合）
      ├─ llm/ provider 族（anthropic/openai/ollama… 出站请求体）
      └─ 经 :common 端口回调 :domains（knowledge 权限过滤、session 落库、audit 记账）
  → SSE 事件经 :common event/ 总线 + stream/ 管理器推给前端（键全 camel）
```

## 目录结构

```
ragagent/
├── boot/                 # :boot 组合根（入口 + 装配）
├── domains/              # :domains 业务域（22 包，见下）
├── engine/               # :engine 能力层（8 包）
├── common/               # :common 共享内核（4 包）
├── frontend/             # Vue 3 前端（477 个源文件；api/ 类型手写，双守卫防契约漂移）
├── scripts/              # 工程脚本：十一守卫（check-*.py）、起停服、契约重录、A/B
├── migrations/           # Flyway 单份基线迁移 versioned/V1__baseline.sql
├── docreader/            # 文档解析 gRPC proto（客户端 stub 在 :domains 构建期生成）
├── mcp-server/           # MCP Python 参考服务（独立交付物，不参与 Java 构建）
├── otlp-proto/           # OTLP 追踪 proto（tracing 用）
├── docs/                 # 31 份模块手册 + 专项规范 + 踩坑档案
├── gradle/libs.versions.toml  # 依赖版本唯一来源（模块 build 文件禁止写版本号）
├── .github/workflows/    # CI：guards · backend · docker · frontend 四个 job
├── docker-compose.yml    # 本地基础设施：ParadeDB(15432) + Redis(16379) + docreader(50051)
├── Dockerfile            # 镜像（CI 有迁移路径自检）
├── settings.gradle.kts   # 模块清单（4 个 include）
└── HANDOFF.md            # 接手必读：已定决策、批次总表、逐包重构范式、踩坑史
```

> 树中省略生成物与本地态：`build/`、`.gradle/`、`.env`、`.DS_Store` 等；根构建脚本还有 `build.gradle.kts` / `gradle.properties` / `gradlew`。

## 后端包结构

### `:domains` — 22 个业务包

| 包 | 一句话 | 包 | 一句话 |
|---|---|---|---|
| `knowledge` | KB 域：入库 → 分块/抽取/向量化 → 检索与 FAQ 供给 | `memory` | 记忆域：写入/检索/注入，recall 永不失败 |
| `wiki` | Wiki 域：页面摄取管线 + 页面/文件夹/链接图 | `session` | 会话域：会话/消息 CRUD + SSE 问答流 |
| `agent` | Agent 域：执行循环 + 工具族（36 个）+ 管理面 | `im` | IM 渠道域：飞书等渠道长连接 + 平台验签回调 |
| `datasource` | 外部数据源连接器族（feishu/gitlab/notion/rss/yuque…） | `mcp` | MCP 接入：协议族 + OAuth + 工具审批桥 |
| `websearch` | 网页搜索 provider 族与凭据管理 | `embedchannel` | 嵌入**渠道**域（HTTP 叶子；与 embedding 易混） |
| `auth` | 认证域：登录/租户/成员/邀请 + apikey 子域 | `tenant` | 租户配置类型族（jsonb 列的类型化读写 + 脱敏） |
| `system` | 系统设置 + SSRF 白名单 + admin 探测端点 | `settings` | 系统设置键注册表（键的类型化声明与网关） |
| `model` | models 表 CRUD + 运行时配置值出口 | `vectorstore` | 向量库**配置面**（CRUD/探测；运行时在 `:engine`） |
| `storage` | 存储后端配置 + 文件代理/URL 重写 | `initialization` | KB 配置第二路径 + 连通性测试 + Ollama 管理 |
| `favorite` | 收藏域（全仓最小完整域） | `audit` | 审计域：三条游标流，只追加表 |
| `approval` | 工具审批域（高危工具的人工闸门） | `evaluation` | 评测域：rag 预设重放 + 指标族（待排期可选项） |

### `:engine` — 8 个能力包

| 包 | 一句话 |
|---|---|
| `retrieval` | 检索引擎：HybridSearchService 混合检索融合 + `engine/` 九家向量库适配（pgvector、Elasticsearch 7/8、OpenSearch、Milvus、Qdrant、Weaviate、腾讯 VectoDB、Doris、SQLite） |
| `llm` | LLM 传输与 provider 族 + chat/extract/asr 子域（出站请求体的唯一出口） |
| `chatpipeline` | 问答编排管线：骨架 + `plugin/` 插件流水线 + `support/`（生产驱动方仅 session） |
| `embedding` | embedding provider 客户端族（9 家） |
| `rerank` | 重排 provider 客户端族（7 家） |
| `modelcontext` | 模型输出协议（工具调用/引用/思考块的解析与装配） |
| `tracing` | 检索链路 span（与 `:common` 的 Langfuse 追踪是两套，勿混） |
| `webfetch` | 受 SSRF 守卫的网页抓取（工具面） |

### `:common` — 4 个平台包

| 包 | 一句话 |
|---|---|
| `common` | L1 平台本体：web（统一外壳/SSRF）、error（全局错误形态）、approval、各域端口与契约视图（被全部业务包依赖） |
| `event` | 进程内事件总线 + `payload/` 事件载荷（SSE 线上契约面，键全 camel） |
| `stream` | SSE 流基础设施：Redis/Memory 双后端 StreamManager |
| `tracing` | Langfuse 追踪：伪装 langfuse-python 直写 OTLP 端点 |

### 域内分层约定

`controller/`（只放 `*Controller`）· `service/`（Spring 服务与用例）· `domain/`（实体）· `dto/`（请求响应）· `mapper/`（**只放 MyBatis-Plus 接口**）· `repository/`（仓储门面：软删/乐观锁/方言分支）· `support/`（无状态算法）。一类型一文件；每个子包有 `package-info.java` 说明职责与依赖判据。

> 每个包的详细手册（结构、改哪里、坑、测试）见 [`docs/README.md`](docs/README.md) 索引；分包地图与分层判据见 [`docs/backend-package-map.md`](docs/backend-package-map.md)。

## 技术栈

| 层 | 选型 |
|---|---|
| 运行时 | JDK 21（虚拟线程：管线并发、工具并行抓取） |
| 框架 | Spring Boot 3.3.5（Web / Validation / AOP / Redis）、MyBatis-Plus 3.5.7 + 手写 JdbcClient |
| 存储 | PostgreSQL 17（**ParadeDB** —— pgvector + BM25）、Redis 7（事件流 / 限流 / 跨实例协调） |
| 文档解析 | docreader gRPC（proto 已本仓化，`:domains` 内生成客户端） |
| 向量/检索引擎 | pgvector、Elasticsearch 7/8、OpenSearch、Milvus、Qdrant、Weaviate、腾讯 VectoDB、Doris、SQLite（九家，`retrieval/engine/` 一家一子包） |
| 数据分析 | DuckDB JDBC（`data_analysis` 工具，独立会话，不依赖沙箱） |
| 其他 | Flyway（**单份基线迁移**）、jjwt、OTLP 追踪导出、protobuf |
| 前端 | Vue 3.5 + Vite 7（~480 个源文件） |
| 测试 | JUnit 5 + AssertJ + MockMvc + H2（测试 schema `TestSchema`）+ 手写 HTTP 桩（stub LLM / Ollama / OAuth / Notion / 飞书…） |

## 快速开始

前置：JDK 21、Docker、Node 22+。

```bash
# 1) 基础设施：postgres(15432) + redis(16379) + docreader(50051)
docker compose up -d

# 2) 配置：cp .env.example .env，按需改（.env 已 gitignore）

# 3) 后端（本仓 dev 用 :8083，避免与旧仓 8082 冲突；端口取自 .env 的 SERVER_PORT）
bash scripts/java-server-up.sh          # 或 ./gradlew :boot:bootRun

# 4) 前端（:5173）
cd frontend && npm ci && npm run dev   # 代理默认指向本仓后端的 :8083（vite.config.ts 的 DEV_PROXY_TARGET 默认值）
```

本仓独立库不含种子数据 —— 首次使用先注册账号（注册者即该租户 owner）：

```bash
curl -s -X POST http://localhost:8083/api/v1/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"demo","email":"demo@example.com","password":"Passw0rd!"}'
```

**镜像部署**：

```bash
docker build -t ragagent-server .
docker run --rm -p 8080:8080 --env-file .env ragagent-server
```

> ⚠️ 镜像内 Flyway 迁移目录是 `/app/build/generated-migrations`（`application.yml` 用的是**相对工作目录**
> 的 `filesystem:` 路径）。改 `WORKDIR` 或迁移路径时必须同步改 Dockerfile，否则全新容器启动即无 schema
> —— 且**不会报错**，直到第一条查询才炸。CI 的 `docker` job 有一道自检守住这点。

## 配置

配置外置在 `boot/src/main/resources/application.yml`，全部走 `${ENV_VAR:default}`。没有 profile 分文件。

| 变量 | 缺省 | 说明 |
|---|---|---|
| `SERVER_PORT` | 8080 | 服务端口（**本仓 dev 用 8083**） |
| `DB_HOST` / `DB_PORT` | localhost / 15432 | ParadeDB |
| `REDIS_HOST` / `REDIS_PORT` | localhost / 16379 | 事件流 / 限流 |
| `DOCREADER_ADDR` | localhost:50051 | 文档解析 gRPC |
| `LOCAL_STORAGE_BASE_DIR` | — | 本地存储根，**必须放持久目录，严禁 `/tmp`** |
| `JWT_SECRET` / `SYSTEM_AES_KEY` | — | 密钥 |
| `STREAM_MANAGER_TYPE` | memory | `redis` = 跨实例共享 |
| `WIKI_REDIS_ENABLED` 等 | false | 各域 Redis 面开关（六处，独立） |
| `WEKNORA_TENANT_FILTER_GUARD` | enforce | 租户过滤探测：`enforce` / `alert` / `off` |

完整清单见 [`.env.example`](.env.example)。代码内**禁止**裸 `System.getenv()`（架构硬门 A1 守着；统一走 `AppEnvLookup` 查找面）。

## 质量门禁

这是本仓的核心工程资产 —— 重构全程"行为不变"就靠它兜底。

| 门 | 位置 | 作用 |
|---|---|---|
| **契约测试** | `domains/src/testFixtures/resources/contracts/`（1,429 个金片）+ 49 个 `*ContractTest` | 逐字节钉死 HTTP 响应形状；改契约必须重录或改金片 |
| **架构规则** | `boot/src/test/.../arch/ArchitectureRulesTest.java` + domains 侧 arch 测试（ArchUnit，A1–A15） | 禁裸 getenv、禁双装配、禁分层倒挂、Mapper 必须在 `..mapper..`、A14 逐字段 `@JsonProperty` 卫生、A15 禁自建 ObjectMapper… |
| **十一守卫** | `bash scripts/run-guards.sh`（**已接入 `check`**，见下） | 包循环 / Go 锚点 / 契约键 / JSON 键大小写 / 事件面 / 文件规模棘轮 / 目录卫生 / 死成员 / 统一响应外壳 / 异常语义 / **契约同步** |
| **跳过棘轮** | `scripts/test-summary.py`（CI 每轮跑） | 跳过 = 这段覆盖没跑。未登记的跳过 ⇒ 报红；白名单里每条都写明"为什么可以跳" |
| **覆盖率** | jacoco（**按需**：`-Pcoverage`） | **聚合口径（推荐）**`./gradlew testCodeCoverageReport -Pcoverage` → **行 67.8% / 分支 54.0%**；单模块 `:domains:jacocoTestReport -Pcoverage`。默认不插桩 —— 实测插桩让全量 +25%，本仓的 1.5 分钟闸门是刻意成果 |
| **CI** | `.github/workflows/ci.yml` | 4 个 job：guards（十一守卫）· backend（真 ParadeDB + 真 Redis + 灌基线 SQL + 全量测试）· docker（镜像构建 + 迁移自检）· frontend |

守卫大多是**棘轮**（ratchet）：登记基线后只许减不许增，例外须写明理由。新增违规当场变红。

> 十一守卫已作为 Gradle 任务 `:guards` **接入 `check`**（B214）⇒ 本地 `./gradlew build` 与 CI 同一口径，
> 不存在"本地绿、CI 红"。单独跑：`./gradlew guards`（迭代时 `./gradlew :domains:test --tests ...` 不会触发它）。
> 需要 `python3` 在 PATH 上。CI 会跑两遍守卫（guards job + backend job），是刻意取舍：两边同口径优先。

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

./gradlew :domains:test --tests "com.ragagent.session.*"   # 定向（迭代用，10~30s）
./gradlew build                                             # 全量闸门（spotless + 十一守卫 + 测试 + javadoc）
./gradlew guards                                            # 只跑十一守卫（build 里已含）
cd frontend && npx vue-tsc --build --force && npm test       # 前端
```

## 文档

| 文档 | 内容 |
|---|---|
| **[HANDOFF.md](HANDOFF.md)** | **接手必读**：背景与已定决策、踩坑史、逐包重构范式、批次总表与纪律 |
| [`docs/README.md`](docs/README.md) | 31 份模块手册索引 + 新人阅读路径 |
| [`docs/backend-package-map.md`](docs/backend-package-map.md) | 分包地图与分层约定 |
| [`docs/api-response-convention.md`](docs/api-response-convention.md) | 统一响应外壳（`@ApiResult`）与错误形态 |
| [`docs/knowledge-api-contract-v1.md`](docs/knowledge-api-contract-v1.md) | knowledge 域 API 契约（camelCase、可空显式 null） |
| [`docs/persistence-architecture-rules.md`](docs/persistence-architecture-rules.md) | 持久层架构规则 |
| [`docs/known-issues/`](docs/known-issues/) | 按批次分片的踩坑实录 |
| [`docs/handoff/`](docs/handoff/) | 域级作战计划与批次执行记录 |

## 高频坑速查

<details>
<summary>展开</summary>

- **跑完 `./gradlew build` 必须重启后端**：构建会**就地重写** `bootRun` 正在用的 `build/classes` ⇒ 在线服务**全站 500 + NoClassDefFoundError**。看起来像业务炸了，其实是陈旧类 —— **重启即解，别去查代码**。
- **前端"保存成功、重新打开却是空的"？先查代理指向**：`frontend/vite.config.ts` 的 `DEV_PROXY_TARGET` 默认值是**本仓后端的 :8083**（B222 修正）。此前它继承 Go 仓的 8080，而本仓后端在 8083 ⇒ 忘设 `VITE_DEV_PROXY_TARGET` 时**所有 `/api` 请求打到空端口**，浏览器侧只有 XHR 失败、**没有醒目提示**，症状就是"填了保存、重开为空"（数据根本没到后端）。
- **停服用 `bash scripts/java-server-down.sh`**（`--stop` 兼容）⇒ 停完记得重启。**别再手敲通配符**：仓库里只有这一个 `*-down.sh`（B216 补的，此前文档引用的脚本并不存在，照文档停服会静默失败、端口一直被占）。起服脚本现在会在端口占用时**明确报错**，不再打到旧进程误报 "ready"。
- **`LOCAL_STORAGE_BASE_DIR` 严禁 `/tmp`**：macOS 定期清理 ⇒ 入库文档原始文件丢失且**不可恢复**（列表正常、preview 全 500、无法重处理）。
- **H2 绿 ≠ PG 绿**：jsonb 键序、NOT NULL、DDL 默认值只在真 PG 暴露 ⇒ 验收必须连真 PG（CI 的 backend job 就是干这个）。
- **Redis 测试会跳过 —— 但有棘轮兜着**：`redis-server` 不在 PATH 时相关用例跳过（本地常见；CI 已装 ⇒ 真跑）。**跳过已升级为棘轮**：未登记的跳过会让 `scripts/test-summary.py` 报红，新增跳过必须去它的 `ALLOWED_SKIPS` 登记并写明理由（覆盖的消失是累积性的）。
- **`*IT` 不在默认构建里**：Milvus / Weaviate 的驱动集成测试需要真实服务，走独立任务 `./gradlew :engine:integrationTest`（env 门控：`WEKNORA_MILVUS_IT=true` 等）。此前它们**连手动都跑不了**（`*IT` 不匹配 Gradle 默认收集模式）。
- **改契约要同 PR 带前端**：前端 TS 类型是**手写**的（无 codegen），后端改字段前端照样编译通过。两道守卫兜着：`check-fe-contract-keys.py`（扫前端源码的 snake 键）+ **`check-contract-sync.py`（契约金片的键签名变了必须签字）**。
  ⇒ 改了后端响应字段后：核对 `frontend/src/api/**` 是否需要同步 → 跑 `python3 scripts/check-contract-sync.py --write` 签字（它会先打印改了什么）。
- **`Long != Long` 引用比较**：租户 id 超出 Long 缓存区，一律 `equals`。
- **加依赖版本要进 catalog**：`gradle/libs.versions.toml` 是唯一来源（B214 起）。别在模块 build 文件里写 `group:artifact:version` —— 那正是本次收拢掉的形态。
- **排查数据库先看后端启动日志的 jdbc URL**：同一实例下有旧仓的 `WeKnora` 库与本仓的 `ragagent` 库（`.env` 的 `DB_NAME`），查错库会得出"数据丢了"的假结论（B223 实测踩坑）。

</details>

---

*源自 Tencent/WeKnora（Apache-2.0）。本地参考仓只读对照，不参与构建与运行。*
