# ragagent

企业级 RAG + Agent 后端。**Spring Boot 3.3.5 / JDK 21 / MyBatis-Plus**，前端 Vue 3 + Vite。

> **项目背景**：本仓由 [Tencent/WeKnora](https://github.com/Tencent/WeKnora) 的 Go 后端移植而来，
> 并已于 2026-09 从"逐文件复刻 Go"转为**Java 本位演进**（详见 [HANDOFF.md](HANDOFF.md) §0/§2）。
> 当前目标不再是与 Go 仓字节级一致，而是**按 Java 生态的主流标准做可读性与架构收敛**。
> **接手本仓请先读 [HANDOFF.md](HANDOFF.md)** —— 背景、已定决策、踩坑史与批次纪律都在那里。

---

## 架构

四个 Gradle 模块，**依赖单向、边界由编译期强制**（不是文档约定：往 `:engine` 里写一行业务域 import 就编译失败）：

```
   :boot  ──────►  :domains  ──────►  :engine  ──────►  :common
  组合根              业务域             能力层            共享内核
  入口 + 装配         22 个业务域        8 个能力域        4 个底座包
  13 文件            1,380 文件         295 文件          219 文件
                     217k 行            50k 行            17.5k 行
```

| 模块 | 职责 | 依赖 |
|---|---|---|
| `:boot` | 组合根：入口类、全局装配、扫描、打包。**只出不进**（无任何模块反向依赖它） | → domains / engine / common |
| `:domains` | 业务域本体：HTTP 面 + 持久层（knowledge、agent、session、wiki、auth、datasource…） | → engine / common |
| `:engine` | 能力层：LLM 客户端族、检索引擎适配、embedding/rerank、chatpipeline、MCP。**不得依赖业务域** | → common |
| `:common` | 共享内核：跨域端口与契约载荷、横切基础设施、事件总线、流管理、追踪。**零 project 依赖** | 无 |

**跨模块解耦靠端口**：`:engine` 需要业务词汇时，接口下沉到 `:common`（如 `KnowledgeBaseGateway`、`SessionMessagePort`），由 `:domains` 提供实现（`KnowledgeBaseService`、`MessageRepository`）。依赖永远指向下层，无环。

**域内分层约定**：`controller/`（只放 `*Controller`）· `service/`（Spring 服务与用例）· `domain/`（实体）· `dto/`（请求响应）· `mapper/`（**只放 MyBatis-Plus 接口**）· `repository/`（仓储门面：软删/乐观锁/方言分支）· `support/`（无状态算法）。一类型一文件。

> 完整包地图见 [`docs/backend-package-map.md`](docs/backend-package-map.md)，29 个顶层包各有手册（`docs/<包名>-module-guide.md`）。

## 技术栈

| 层 | 选型 |
|---|---|
| 运行时 | JDK 21（虚拟线程：管线并发、工具并行抓取） |
| 框架 | Spring Boot 3.3.5（Web / Validation / AOP / Redis）、MyBatis-Plus 3.5.7 + 手写 JdbcClient |
| 存储 | PostgreSQL 17（**ParadeDB** —— pgvector + BM25）、Redis 7（事件流 / 限流 / 跨实例协调） |
| 文档解析 | docreader gRPC（proto 已本仓化，`:domains` 内生成客户端） |
| 向量/检索引擎 | pgvector、Milvus、Qdrant、Weaviate、OpenSearch、Elasticsearch 7/8、Doris、SQLite |
| 数据分析 | DuckDB JDBC（`data_analysis` 工具，独立会话，不依赖沙箱） |
| 其他 | Flyway（**单份基线迁移**）、jjwt、OTLP 追踪导出、protobuf |
| 前端 | Vue 3.5 + Vite 7 |
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

完整清单见 [`.env.example`](.env.example)。代码内**禁止**裸 `System.getenv()`（架构硬门 A1 守着，目前全仓仅 5 处白名单例外）。

## 质量门禁

这是本仓的核心工程资产 —— 重构全程"行为不变"就靠它兜底。

| 门 | 位置 | 作用 |
|---|---|---|
| **契约测试** | `domains/src/testFixtures/resources/contracts/`（1,345 个金片）+ 30 个 `*ContractTest` | 逐字节钉死 HTTP 响应形状；改契约必须重录或改金片 |
| **架构规则** | `boot/src/test/.../arch/ArchitectureRulesTest.java`（ArchUnit，A1–A13） | 禁裸 getenv、禁双装配、禁分层倒挂、Mapper 必须在 `..mapper..`… |
| **序列化卫生** | `domains/src/test/.../arch/JsonPropertyHygieneTest.java`（A14） | 基线 0：我们自己的面上不得有冗余 `@JsonProperty`（四类必要形态除外） |
| **十一守卫** | `bash scripts/run-guards.sh`（**已接入 `check`**，见下） | 包循环 / Go 锚点 / 契约键 / JSON 键大小写 / 事件面 / 文件规模棘轮 / 目录卫生 / 死成员 / 统一响应外壳 / 异常语义 / **契约同步** |
| **跳过棘轮** | `scripts/test-summary.py`（CI 每轮跑） | 跳过 = 这段覆盖没跑。未登记的跳过 ⇒ 报红；白名单里每条都写明"为什么可以跳" |
| **覆盖率** | jacoco（**按需**：`-Pcoverage`） | **聚合口径（推荐）**`./gradlew testCodeCoverageReport -Pcoverage` → **行 67.8% / 分支 54.0%**；单模块 `:domains:jacocoTestReport -Pcoverage`。默认不插桩 —— 实测插桩让全量 +25%（1m24s→1m49s），本仓的 1.5 分钟闸门是刻意成果 |
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
| [`docs/README.md`](docs/README.md) | 29 个顶层包手册索引 + 新人阅读路径 |
| [`docs/backend-package-map.md`](docs/backend-package-map.md) | 分包地图与分层约定 |
| [`docs/api-response-convention.md`](docs/api-response-convention.md) | 统一响应外壳与错误形态 |
| [`docs/knowledge-api-contract-v1.md`](docs/knowledge-api-contract-v1.md) | API 契约标准 v1（camelCase、无信封、可空显式 null） |
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
- **加依赖版本要进 catalog**：`gradle/libs.versions.toml` 是唯一来源（B214 起）。别在模块 build 文件里写 `group:artifact:version` —— 那正是本次收拢掉的形态。无覆盖率工具（jacoco）仍是缺口。

</details>

---

*源自 Tencent/WeKnora（Apache-2.0）。本地参考仓只读对照，不参与构建与运行。*
