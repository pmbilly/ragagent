# RagAgent 重建：外围收尾与切换 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**规格来源**：`docs/optimization/specs/2026-11-09-architecture-rebuild-design.md`。路线图：`docs/optimization/plans/2026-11-10-rebuild-00-roadmap.md`，本文是其中的 09 号计划，第 3 节约定全部继承。

**Goal:** 完成第一阶段收尾：docreader 与 OCR 收进仓库并自建镜像，mcp-server 改用新 API，前端拆成 console 与 widget 两个应用并迁到 pnpm 工作区，部署分 api 与 worker 两种角色，可观测改用 OpenTelemetry 官方 SDK，Flyway 合并为单份 V1，旧模块、守卫脚本与 baseline 清零，CI 只跑新结构，spec 第 7 节八项验收全部满足。

**Architecture:** 顺序固定：补等价规则，删旧守卫，删旧模块，不可颠倒。删除前必须有等价规则兜底，删除后必须在干净克隆上构建通过。外围组件各自独立交付：`services/docreader`、`mcp-server`、`frontend` 工作区、`deploy` 编排。前序计划里的真实名字（角色开关、环境变量、测试类名）统一落盘到 `docs/migration/cutover-prereqs.md`，本计划用尖括号占位符引用，不在这里猜。

**Tech Stack:** Python 3.10 与 uv、gRPC、RapidOCR、pnpm 9、Vite 7、Vue 3.5、nginx 1.30、Docker Buildx、Spring Boot Actuator、Micrometer、OpenTelemetry Java SDK、Flyway、pg_dump、Checkstyle、Maven Enforcer、ArchUnit、Playwright、GitHub Actions。

## Global Constraints

- JDK 21、Spring Boot 3.5.16。Java 风格沿用用户全局规范：tab 缩进、行宽 140、中文 Javadoc、`@author jiangguifan`、魔法字符串收口。
- 下文 `MVN` 代表 `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml`，`-pl` 必带 `-am`，`JAVA_HOME` 指向 JDK 21。
- `git add` 只写显式路径。每个任务一次提交，提交前跑通该任务的验证命令，提交后在 `docs/migration/progress.md` 追加一行。与计划不一致的决定写进 `docs/migration/deviations.md`。
- XML、YAML、JSON、pom 用 Edit 整体编辑，禁止 sed。多行 Python 先写成系统临时目录里的 `.py` 再执行，用完删除，仓库里不留临时脚本。
- 旧代码在任务 16 之前只读。旧系统副本 `../ragagent-legacy` 必须在位（计划 01 Task 0），验收对拍要用它。
- ragagent 的 origin 是公开 MIT 仓库。引入第三方源码前先核许可证，命中 AGPL 或 GPL 依赖立即停止并询问用户，不自行取舍。
- 质量门沿用路线图 3.11：Checkstyle 在 `mvn verify` 内拦截，全仓抑制不超过 10 处，引擎豁免文件在第一阶段结束时必须为空。
- 端口：新后端业务 8080，管理 8081，数据库 25432，Redis 16379，console 开发 5173，widget 开发 5174。
- 清点文件一律以 `git ls-files` 为准。工作区里未跟踪的草稿（例如 `scripts/_tmp_` 开头的文件）不属于冻结点，不计入也不处理。

## Review Focus

1. 删除旧模块后，Dockerfile、CI、compose、文档里残留的旧路径。期望：干净克隆上 README 的构建与启动命令原样跑通。归属任务 16、17、18。
2. worker 角色没有业务 HTTP 入口。期望：健康检查仍能判定存活与就绪，业务路由返回 404 或连接被拒。归属任务 11。
3. 挂件被第三方宿主页以 iframe 嵌入。期望：widget 响应不带 `X-Frame-Options`，console 响应带 `SAMEORIGIN`，两个应用的静态资源路径不冲突。归属任务 10。
4. docreader 镜像里的 AGPL 或 GPL 依赖。期望：许可证扫描报告可读，命中即阻断。归属任务 2。
5. Flyway 合并后行级安全策略或索引丢失。期望：合并前后的 schema 导出逐行一致，策略集合与索引集合相等。归属任务 14。

## 前置、交付、对外承诺

**前置**：计划 01 到 08 的完成判据全部通过并登记在 `docs/migration/progress.md`。当前仓库里只有计划 01 与 06 的文件，其余按路线图第 2 节范围读取。具体名字在任务 1 用 grep 落盘，找不到的写 `缺失` 并停下问用户。

| 来源 | 读取内容 |
|---|---|
| 计划 01 | `backend/pom.xml`、`backend/config/checkstyle.xml` 与引擎豁免文件、`deploy/db/`、`tests/e2e`、`tests/api-smoke`、`tests/regression`、`frontend/packages/api-client`、CI 的 `api-drift` job、环境变量名 `DB_URL`、`DB_OWNER_USER`、`DB_APP_USER`、`RAGAGENT_ENCRYPT_KEY` 等 |
| 计划 02 | 引擎包、检索 IT 的 Profile 与类名、otlp-proto 与 Langfuse 追踪是否已处理 |
| 计划 03 | open 账号体系的令牌头、`SaRouteRules`、主体接口 |
| 计划 04 | api 与 worker 角色开关、JobRunr 表由谁建、docreader 客户端与环境变量、OCR 环境变量 |
| 计划 05 到 08 | `/api/open/v1` 下的实际路径、SSE 事件表、需要 nginx 特殊处理的路径（本地浏览器扩展、沙箱终端、技能包上传） |

**交付**：`services/docreader` 与它的两个镜像；改新 API 的 `mcp-server` 2.0.0；`frontend/apps/{console,widget}`、`frontend/packages/{api-client,i18n,ui}` 与 pnpm 锁文件；`backend/Dockerfile` 与 `deploy/` 全套编排；`docs/migration/guard-mapping.md`；单份 `V1__baseline.sql`；只聚合 `backend` 的根 pom；`docs/migration/acceptance.md`。

**对外承诺**：无 Provider，无 spi。第一阶段结束后第二阶段的计划依赖本计划的交付物，不依赖本计划的内部类。

## 本计划新做的设计决定（待用户核对）

**用户已确认（2026-11-10）**：下面第 1 条删除 ebooklib、第 2 条 mcp-server 升 2.0.0，以及单份 V1 含种子 DML（破 DDL 与 DML 分开的全局规则，仅限合并后的这一份基线，增量脚本仍分开）。其余条目仍待核对。

### 一、新表结构与聚合根划分

无。本计划不新建表，Flyway 合并只改文件组织，不改任何表结构。

### 二、新接口路径与 VO 形状

1. **管理端口。** 决定：Actuator 走独立管理端口 8081，暴露 `health`（含 liveness 与 readiness 探针）、`info`、`prometheus`，业务端口 8080 不暴露它们。理由：探针与指标不应经过业务端口的鉴权和反向代理，也不应对公网可见。否决：在业务端口放行 `/actuator/prometheus`，要在 permit-paths 开口子，还得靠 nginx 屏蔽。
2. **挂件静态资源路径。** 决定：widget 构建产物目录由 `/assets/` 改为 `/embed-assets/`，宿主页加载的 `ragagent-widget.js` 与 `/embed/<渠道ID>` 不变。理由：console 与 widget 拆成两个镜像后共用 `/assets/` 会让反向代理无法判断文件归属。否决：两个应用合并成一个镜像共用路径，违背 D10 按运行环境拆分。

### 三、Provider 与 spi 的签名

无。

### 四、与旧行为不一致的地方

1. **EPUB 解析不再使用 ebooklib。** 决定：docreader 删除 `ebooklib` 依赖，EPUB 只走上游 `epub_parser.py` 里已有的 ZIP 解析分支。理由：ebooklib 在 PyPI 的许可证是 AGPL-3.0 或更高，随网络服务镜像公开分发会把 AGPL 义务带进本仓库的 MIT 分发物。否决：保留并接受 AGPL，整个镜像要按 AGPL 披露。任务 2 的扫描若在 ebooklib 之外还有命中，停下问用户。
2. **mcp-server 工具面破坏性变更。** 决定：版本升到 2.0.0，删除 `list_shared_knowledge_bases` 与无工具调用的客户端方法 `get_tenant`、`update_knowledge_base`，租户类工具在 open 面没有对应路径时删除，基地址默认改 `/api/open/v1`，令牌头改为 open 账号体系的头。理由：共享知识库接口在冻结点后端已无路由，全仓 grep 无命中；其余按 open 面能力裁剪。否决：保留工具并返回固定错误，客户端会看到一个永远失败的工具。
3. **运维契约变化。** 决定：存活与就绪探针从 `/health` 改为管理端口的 `/actuator/health/liveness` 与 `/actuator/health/readiness`；`SYSTEM_AES_KEY` 改名 `RAGAGENT_ENCRYPT_KEY`；`JWT_SECRET` 随认证改 Sa-Token 而删除；其余环境变量逐个核对，对照表放 `deploy/ENVIRONMENT.md`。理由：旧值格式与新实现不兼容，别名只会掩盖配置错误。否决：保留旧变量名做别名。
4. **开发栈端口。** 决定：后端 8083 改 8080，数据库 15432 改 25432（自建镜像），Redis 16379 不变，widget 开发端口 5174 新增。理由：沿用计划 01 的端口约定，旧系统副本与新栈可同机并行。否决：新栈继续占 8083 与 15432，会与 `../ragagent-legacy` 冲突。

# 1. 盘点（执行方核对，不重新盘点）

以下四处早期盘点与源码不符，以源码为准：

1. `scripts/` 跟踪文件是 97 个，不是 108 个。`_tmp_` 开头的文件未跟踪，不属于冻结点。
2. 工作区里 `_tmp_` 开头的 `.py` 只有 3 个，盘点写的是 10 个。
3. 仓库根 `docreader/docreader.proto` 是 86 行，上游当前是 137 行，多出 `PreprocessImage`、`GetInfo` 两个 RPC 与 `SourceBlock` 字段，旧三个 RPC 与旧字段编号都保留。
4. 前端超 400 行的文件实测为 83 个 `.vue` 加 33 个 `.ts`，spec 与路线图写的 82 个是约数。本次都不拆。

## 1.1 scripts 去留（共 97 个）

| 类别 | 文件 | 动作 |
|---|---|---|
| 守卫 | `check-*.py` 42 个 | 删除，规则去向见 1.2 |
| 生成与检查 | `gen-arch-index`、`gen-contract-consumers`、`gen-domain-index`、`gen-module-map`、`gen-pipeline-map`、`gen-port-map` | 删除 |
| 基线与守卫入口 | `*.baseline.json` 18 个、`capability-kinds.json`、`run-guards.sh`、`_method_lengths.py`、`_source_roots.py` | 删除 |
| 一次性工具 | `audit-contract-fixtures.sh`、`fix-javadoc-links.py`、`migrate-domain.py`、`normalize-blank-lines.py`、`pkg-audit.py`、`restyle-embeds.py`、`route-recon.py`、`refactor-harness.sh`、`complexity-report.py`、`method-length-report.py`、`generate-api-docs.py` | 删除 |
| 旧验收 | `acceptance.sh`、`attachment-vision-check.sh`、`dev-stale-check.sh` | 删除 |
| 数据生成 | `gen-jieba-hmm.py` | 条件：`grep -rn jieba backend --include=*.java` 仍有命中就保留，否则删除 |
| 开发 | `dev-env.sh`、`dev-up.sh`、`doctor.sh`、`java-server-up.sh`、`java-server-down.sh`、`token.sh` | 保留，改内容，文件名不变 |
| 测试桩与 CI 辅助 | `stub-llm-server.py`、`stub-ollama-server.py`（e2e 依赖）、`ci-smoke.sh`（改写）、`ci-log-annotate.py`、`test-summary.py` | 保留 |
| OCR | `ocr-server.py` | 迁入 `services/docreader/ocr/`，见任务 3 |

收尾后 `git ls-files scripts | wc -l` 为 11，jieba 脚本保留时为 12。

## 1.2 旧守卫对照表（共 48 条）

新落点分三类：计划 01 已有、计划 02 到 08 已有、本计划任务 15 新增（标 N）。任务 15 的审计步骤会逐条 grep 落点名，找不到的改标为丢失并补理由。

| 旧脚本 | 旧规则 | 新落点 | 丢失 |
|---|---|---|---|
| check-package-cycles | 包环与分层违例只许减 | `ModuleBoundaryTest.modulesHaveNoCyclesAndRespectDeclaredBoundaries` | 否 |
| check-capability-kinds | 跨能力种类直连禁止 | `baseDomainsDoNotDependOnUpperDomains`、`businessDomainsDoNotDependOnOrchestrationDomains`、`everyDomainPackageIsClassified` | 否 |
| check-domain-contracts | 域间只经契约 | `noCrossDomainInternalAccess`、Modulith `verify` | 否 |
| check-module-graph | 模块依赖矩阵 | Maven 依赖方向、`enginesExposeOnlyApiAndSpi`、N `retrievalAndAgentEnginesAreIndependent` | 否 |
| check-wiring-points | `@Configuration` 须登记 | `enginesAreAssembledOnlyInAppConfig` | 是：登记清单取消，位置约束已由规则表达 |
| check-contract-purity | contract 只许纯 JDK | contract 模块已解散 | 不适用 |
| check-contract-shape、gen-contract-consumers | 端口方法数与字段数预算，每个契约类型至少一个消费者 | 路线图 3.11 的第二调用方要求，由 `code-review-mr` 执行 | 是：载体 contract 已解散，语义规则无法静态判定 |
| check-contract-sync | 契约金片键签名须签字 | `OpenApiExportTest` 加 CI `api-drift` | 否 |
| check-port-budget | 域内端口与无实现端口为零 | N `interfacesOutsideApiAndSpiHaveAtLeastTwoImplementations` | 否 |
| check-platform-shapes | platform 不得有业务词包与文件名 | platform 已解散，framework 不依赖 app 由 Maven 方向保证 | 是：业务词命名无法静态表达，评审覆盖 |
| check-file-size、check-method-length | 文件不超 600 行，方法不超 150 行 | Checkstyle `FileLength` 400、`MethodLength` 40 | 否，更严 |
| check-hygiene-budget | 超 100 行方法、trim 副本、8 参以上构造器 | `MethodLength`、`ParameterNumber` 5、`constructorsOfSpringBeansTakeAtMostSevenDependencies`、N `noGoCompatUtilities` | 否 |
| check-package-size | 单包超 30 个文件须登记 | `FileLength` 与类尺寸规则 | 是：由域内子包与类尺寸取代，包文件数不再设门 |
| check-package-dirs | 目录与 package 声明一致 | N Checkstyle `PackageDeclaration` | 否 |
| check-stray-dirs | 游离目录与死包目录 | `PackageDeclaration`，git 不跟踪空目录 | 否 |
| check-dead-members | 重复与未用 import、未用 Logger 与 ObjectMapper 字段 | N Checkstyle `UnusedImports`、`RedundantImport` | 是：未用 Logger 字段无规则，新代码用 `@Slf4j`，不再声明字段 |
| check-api-envelope | 控制器统一返回外壳 | N `controllersReturnRestResponseOrStream` | 否 |
| check-controller-purity、check-controller-entity-imports | 控制器不直连 Mapper，不导入实体 | `mapperOnlyUsedByRepository`、N `controllersDoNotUseMappersOrEntities` | 否 |
| check-route-policy | 每条路由须命中权限规则 | 计划 01 Task 8：Sa-Token 路由规则对未匹配路径 fail-closed，`SaTokenAccountsTest` | 否 |
| check-tenant-scope | 跨线程身份须走 TenantScope | 行级安全 fail-closed，`TenantIsolationTest.missingTenantContextIsReportedExplicitly` | 否 |
| check-fe-contract-keys、check-json-key-case | 前端 snake 契约键只许减，新增 JSON 键须 camel | 前端类型由 OpenAPI 生成，VO 字段名决定键名，CI `api-drift` | 否 |
| check-event-face-case | SSE 与事件命名口径 | 计划 02 的事件序列回归对拍（spec 5.10.4 第一组） | 否 |
| check-exception-semantics | 检索层 catch 不得清空条件 | 计划 02 的检索引擎故障注入用例 | 否 |
| check-tool-registry | 工具名常量、实现、分发一致 | 计划 08 的工具注册一致性测试 | 否 |
| check-log-exceptions、check-comment-decor | log 不得把 Throwable 转字符串，禁整行横线注释 | N Checkstyle `RegexpSinglelineJava` | 否 |
| check-version-consistency | Boot、springdoc、MyBatis-Plus 版本不漂移 | 单一 BOM，N Enforcer `requireUpperBoundDeps` | 否 |
| check-schema-parity | TestSchema 与迁移脚本对账 | 测试直接在真实 PostgreSQL 上跑 Flyway，TestSchema 不存在 | 不适用 |
| check-go-anchors | 注释不引用 Go 行号与移植波次 | `noGoCompatSuffixes`、`noRawRequestBodyString`、N `noGoCompatUtilities` 管代码 | 是：注释类约束只服务迁移期，新代码没有此来源 |
| check-internal-codes、check-comment-narrative | 注释不出现批次号、日期戳与迁移史 | 无 | 是：新代码没有此来源，叙事类规则无法静态判定，`code-review-mr` 覆盖 |
| check-log-level | warn 不用于 404 与跳过 | 无 | 是：风格项，评审覆盖 |
| check-known-gaps | 类注释声明缺口须指向登记 | 无 | 是：缺口改登记在 spec 遗留事项与 `deviations.md` |
| check-duplicate-classes | 跨模块同名类 | 无 | 是：按域分包后同名类在 import 时暴露，Bean 重名在启动时暴露 |
| check-package-info-fields | 域 package-info 四要素 | 无 | 是：域说明改放 `docs/architecture/overview.md` |
| check-reference-truth、check-doc-truth | 注释与文档里的引用、README 数字与代码一致 | 无 | 是：旧注释体系已不存在，README 不再写可漂移的数量，文档引用由任务 18 的验证命令一次性核对 |
| gen-arch-index、gen-module-map、gen-domain-index、gen-pipeline-map、gen-port-map、check-qa-flow、check-ingest-flow | 生成文档与代码同步，链路文档引用的类存在 | 无 | 是：不再生成这些文档，链路文档随任务 18 删除 |

另有两项不是脚本：CI 的 `spotless:check`（ratchet 自 `seed`）随根 pom 一起删除，新结构不设格式化插件，缩进与导入顺序由 IDE 与 Checkstyle 的导入规则承担，视为可丢；`boot/src/test/.../arch/ArchitectureRulesTest` 随 boot 删除，等价规则已在计划 01 的同名测试里重写。

## 1.3 mcp-server 调用的旧接口与映射骨架

31 个工具，主文件 1154 行，令牌头 `X-API-Key`，基地址默认 `http://localhost:8080/api/v1`。新路径一律以对应计划的接口表和 `backend/app/target/openapi.json` 为准，执行方在任务 6 填写，填不出的行走删除分支。

| 旧路径（相对 `/api/v1`） | 方法 | 工具 | 负责计划 | 新路径（`/api/open/v1` 下） |
|---|---|---|---|---|
| `/tenants` | POST GET | `create_tenant`、`list_tenants` | 03 | 待填，无则删工具 |
| `/tenants/{id}` | GET | 无工具调用 | 03 | 删除客户端方法 |
| `/knowledge-bases` | POST GET | `create_knowledge_base`、`list_knowledge_bases` | 04 | 待填 |
| `/shared-knowledge-bases` | GET | `list_shared_knowledge_bases` | 无 | 删除 |
| `/knowledge-bases/{id}` | GET DELETE | `get_knowledge_base`、`delete_knowledge_base` | 04 | 待填 |
| `/knowledge-bases/{id}` | PUT | 无工具调用 | 04 | 删除客户端方法 |
| `/knowledge-bases/{id}/hybrid-search` | POST | `hybrid_search` | 04 | 待填 |
| `/knowledge-bases/{id}/knowledge/file` | POST | `create_knowledge_from_file` | 04 | 待填，multipart |
| `/knowledge-bases/{id}/knowledge/url` | POST | `create_knowledge_from_url` | 04 | 待填 |
| `/knowledge-bases/{id}/knowledge/manual` | POST | `create_knowledge_from_text` | 04 | 待填 |
| `/knowledge/manual/{id}` | PUT | `update_knowledge_from_text` | 04 | 待填 |
| `/knowledge-bases/{id}/knowledge` | GET | `list_knowledge` | 04 | 待填，分页参数核对 |
| `/knowledge/{id}` | GET DELETE | `get_knowledge`、`delete_knowledge` | 04 | 待填 |
| `/models`、`/models/{id}` | POST GET | `create_model`、`list_models`、`get_model` | 03 | 待填 |
| `/sessions`、`/sessions/{id}` | POST GET DELETE | `create_session`、`get_session`、`list_sessions`、`delete_session` | 08 | 待填 |
| `/knowledge-chat/{sid}` | POST，SSE | `chat` | 08 | 待填 |
| `/agent-chat/{sid}` | POST，SSE | `agent_chat` | 08 | 待填 |
| `/agents`、`/agents/{id}` | GET | `list_agents`、`get_agent` | 06 | 待填，计划 06 已定 agents 同时挂 admin 与 open |
| `/chunks/{kid}`、`/chunks/{kid}/{cid}` | GET DELETE | `list_chunks`、`delete_chunk` | 04 | 待填 |
| `/knowledgebase/{kb}/wiki/{search,pages/{slug},index}` | GET | `wiki_search`、`wiki_read_page`、`wiki_index_view` | 05 | 待填 |

SSE 现状：`_consume_sse_stream` 只认 `data:` 行，按 JSON 里的 `response_type` 分派 `answer`、`references`、`error`、`complete`。新实现按 SSE 规范解析，事件字段以计划 08 的事件表为准。

## 1.4 其余外围事实

| 项 | 事实 |
|---|---|
| docreader 上游 | Tencent/WeKnora，最新 release `v0.9.0`（2026-10-29），`docreader/` 含 `parser`、`splitter`、`proto`、`scripts`、`tests`、`pyproject.toml`、`uv.lock`；Dockerfile 在 `docker/Dockerfile.docreader`，基础镜像 `python:3.10.18-bookworm`，运行阶段装 libreoffice、openjdk-17-jre-headless、antiword，Playwright webkit，`grpc_health_probe v0.4.24`；入口 `python -m docreader.container_entrypoint`，包名 `docreader`，导入写 `docreader.xxx` |
| docreader 环境变量 | `GRPC_AUTH_TOKEN`（置位启用鉴权）、`GRPC_TLS_ENABLED`、`GRPC_TLS_CERT`、`GRPC_TLS_KEY`、`GRPC_TLS_CA`、`LOG_LEVEL`，默认明文无鉴权 |
| OCR 现状 | `scripts/ocr-server.py` 235 行，依赖 `rapidocr_onnxruntime`（会带非 headless 的 opencv），`GET /health` 返回 `{"ok": true}`，`POST /ocr` 空体返回 400；上游 docreader 用的是另一个包 `rapidocr`，只做图片方向预处理 |
| 前端 | 174 个 `.vue`、345 个 `.ts`，142 个测试文件；`embed.html` 还加载 `/config.js`、`/embed-boot.css`、`/tdesign-icons/0.4.1/fonts/index.js`，所以这三项两个应用都要有；`public/` 里 5 个 demo 与测试页都嵌挂件 |
| nginx 特殊路径 | `/ragagent-widget.js`、`/embed/`、`/embed.html`（internal）、`/assets/`、`/files`、`/r/`、本地浏览器扩展 WebSocket 与 internal 的 404、沙箱终端 WebSocket、技能包上传两条集合路由放宽体积、`/api/` 通用代理（SSE 关缓冲，3600s 超时） |
| 追踪 | 自维护链路类在 `platform/tracing/langfuse`，`OtlpHttpExporter` 用 protobuf 直发 `{host}/api/public/otel/v1/traces`，头为 Basic 认证与 `x-langfuse-ingestion-version: 4`，固定 HTTP/1.1，属性值全是字符串；装饰器 `LangfuseChatClient`、`LangfuseEmbedder`、`LangfuseReranker`、`LangfuseVlm` |
| 受影响文档 | 跟踪文件里引用旧结构的共 60 个，其中 29 个在 `docs/internal`；旧系统唯一的业务指标是 `QaTerminalMetrics`，Actuator 仅在 boot 声明 |

# 2. 任务

### Task 1: 前置核对与名字落盘

**Files:**
- Create: `docs/migration/cutover-prereqs.md`

**Interfaces:**
- Consumes: 计划 01 到 08 的产物，`docs/migration/progress.md`
- Produces: 一张表，列为占位符、实际值、来源文件。占位符：`ROLE_PROP`、`ROLE_API`、`ROLE_WORKER`、`ROLE_ALL`（计划 04 的角色开关）；`OPEN_TOKEN_HEADER`；`DOCREADER_ENV`、`OCR_ENV`（变量名与默认值）；`PROTO_POM`（持有 protobuf-maven-plugin 的 pom）；`IT_PROFILE`、`IT_CLASSES`；`OTLP_STATE`、`TRACING_MODULE`；`JOBRUNR_TABLES`（谁建表）；`I18N_TEST_PATTERN`（默认 `*CodeMessagesTest`）、`RLS_TEST_PATTERN`（默认 `*RowLevelSecurityTest`）；`SSE_EVENTS`（事件表位置）

- [ ] **Step 1: 确认前序闸门并提取名字**
Run:
```bash
grep -nE "^\| *0?[1-8] " docs/migration/progress.md | tail -30
grep -rnE "role|worker" backend/app/src/main/resources/application*.yml
grep -rn "protobuf-maven-plugin" backend --include=pom.xml
grep -rnE "DOCREADER|OCR_" backend/app/src/main/resources/application*.yml
grep -rlE "OtlpHttpExporter|io.opentelemetry" backend --include=*.java --include=pom.xml
grep -rn "jobrunr" backend --include=pom.xml --include=application*.yml
grep -rlE "Milvus|Elasticsearch" backend/retrieval-engine/src/test --include=*IT.java
```
Expected: 计划 01 到 08 各有完成判据通过的记录，缺任何一份就停止并向用户报告；每个占位符都有确定值，找不到的写 `缺失`，登记 `deviations.md` 后问用户。

- [ ] **Step 2: 提交**
```bash
git add docs/migration/cutover-prereqs.md
git commit -m "docs(migration): 切换计划的前置名字与占位符落盘"
```

### Task 2: docreader 引入、许可证核实与扫描

**Files:**
- Create: `services/docreader/`（上游 `docreader/` 目录，固定到 release 标签）
- Create: `services/docreader/{UPSTREAM.md,LICENSE.upstream,NOTICE,THIRD_PARTY_LICENSES.md}`、`NOTICE`（仓库根）
- Create: `services/docreader/utils/license_policy.py`、`services/docreader/tests/test_license_policy.py`、`services/docreader/scripts/check_licenses.sh`

**Interfaces:**
- Consumes: Tencent/WeKnora 公开仓库
- Produces: `license_policy.check(report: list[dict]) -> list[str]`，输入是 `pip-licenses --format=json` 的条目，返回许可证串含 `Affero` 或 `GNU General Public` 且不含 `Lesser` 与 `Library` 的包名，按字母序

**许可证核实结论（已查网络，执行时复核）：**

1. 上游 `LICENSE` 是腾讯的 MIT 样式文本：文件头 `Tencent is pleased to support the open source community by making WeKnora available.`，版权行 `Copyright (C) 2026 Tencent. All rights reserved.`，正文 `WeKnora is licensed under the MIT except for the third-party components listed below.`，后附标准 MIT 条款。GitHub 识别为 NOASSERTION，是因为文件带组件清单。清单里只有 Go 组件，docreader 目录没有独立 LICENSE，`pyproject.toml` 没有 license 字段。
2. 结论：允许复制、修改、再分发，唯一义务是保留版权声明与许可声明。本仓库的缺口是没有第三方声明文件：根 `LICENSE` 是 `Copyright (c) 2026 Billy` 的 MIT，README 有致谢，`mcp-server/LICENSE` 保留了 `Copyright (c) 2024 WeKnora Team`，而 docreader 将是整目录复制的上游源码。补救动作在步骤 3。
3. 依赖风险：`ebooklib` 的 PyPI 元数据为 AGPL-3.0 或更高，`epub_parser.py` 导入了它，不可接受（见决定四之 1）。`textract==1.5.0` 的传递依赖由扫描确认。系统包 antiword（GPL-2.0 或更高）、LibreOffice（MPL-2.0）、OpenJDK（GPL-2.0 加 Classpath Exception）以独立程序随镜像分发，不与 MIT 代码链接，在 `NOTICE` 写明并指向 Debian 源码仓，具体许可证以镜像内 `/usr/share/doc/<包>/copyright` 为准。

- [ ] **Step 1: 固定上游版本并复核许可证**
Run:
```bash
UP=$(mktemp -d)
git clone --depth 1 --branch v0.9.0 https://github.com/Tencent/WeKnora.git "$UP"
git -C "$UP" rev-parse HEAD && head -8 "$UP/LICENSE"
```
Expected: 前两行与上面结论一致。`v0.9.0` 是编写时的最新 release，有更新的 release 就取更新的。许可证与结论不一致，或不再是 MIT，立即停止并询问用户。

- [ ] **Step 2: 写失败测试 `test_license_policy.py`，实现并扫描出红**
- `test_flags_agpl_package`：`ebooklib` 条目许可证为 `GNU Affero General Public License v3 or later (AGPLv3+)`，返回 `["ebooklib"]`。
- `test_accepts_permissive_and_lgpl`：BSD、Apache、`GNU Lesser General Public License v3` 各一个包，返回空列表。
- `test_flags_gpl_v2_and_v3`：两个 GPL 包都命中，结果按字母序。

Run: `cd services && uv run --project docreader pytest docreader/tests/test_license_policy.py -q`
Expected: FAIL，模块不存在。上游测试用 `docreader.xxx` 导入，所以所有 pytest 命令都在 `services/` 下执行，目录名 `docreader` 不能改。
然后实现 `utils/license_policy.py`，引入源码 `cp -R "$UP/docreader/." services/docreader/`，删除上游的 `.git` 痕迹。`check_licenses.sh` 执行 `uv sync --frozen` 后跑 `pip-licenses --format=json --from=mixed`，交给 `check`，非空则打印包名并退出 1。
Run: `cd services && bash docreader/scripts/check_licenses.sh`
Expected: 退出 1，输出恰好是 `ebooklib`。出现别的包，停止，把包名与许可证列给用户，不自行取舍。

- [ ] **Step 3: 补齐声明文件并提交**
`LICENSE.upstream` 存上游 `LICENSE` 原文。`UPSTREAM.md` 写上游地址、固定的完整提交哈希与标签、引入日期、与上游的差异清单（后续任务追加）。根 `NOTICE` 列 Tencent/WeKnora 的 MIT 条目，覆盖 `services/docreader` 与 `mcp-server`，写明版权行与许可证文件位置。`services/docreader/NOTICE` 列随镜像分发的系统包。`THIRD_PARTY_LICENSES.md` 由扫描结果整理。ebooklib 的处置在任务 3 做，本任务提交时扫描仍为红，提交信息注明。

```bash
git add services/docreader NOTICE
git commit -m "feat(docreader): 引入上游 WeKnora docreader 固定版本并补齐许可证声明"
```

### Task 3: 去 Go 产物、移除 ebooklib、OCR 并入

**Files:**
- Delete: `services/docreader/proto/*.pb.go`、`*_pb2.py`、`*_pb2_grpc.py`、`*_pb2.pyi`（生成物不入库）
- Modify: `services/docreader/scripts/generate_proto.sh`、`parser/epub_parser.py`、`tests/test_epub_external_images.py`、`pyproject.toml`、`uv.lock`、`UPSTREAM.md`、`.gitignore`
- Create: `services/docreader/tests/{test_proto_generation.py,test_epub_without_ebooklib.py}`
- Move: 仓库根 scripts/ocr-server.py 到 `services/docreader/ocr/server.py`
- Create: `services/docreader/ocr/{pyproject.toml,uv.lock,tests/test_ocr_server.py}`

**Interfaces:**
- Consumes: 旧 OCR 接口：`GET /health` 返回 `{"ok": true}`，`POST /ocr` 请求体是图片字节，返回 `{"text","lines","elapsedMs"}`，空体 400
- Produces: `python -m grpc_tools.protoc` 可从 `proto/docreader.proto` 生成 Python 桩；EPUB 解析不再导入 ebooklib；`python ocr/server.py <port>` 行为与旧版一致

OCR 的依赖锁独立：`rapidocr_onnxruntime` 会带非 headless 的 `opencv-python`，与 docreader 的 `opencv-python-headless` 放进同一份锁会互相覆盖 `cv2`。所以 OCR 与 docreader 同目录、同 Dockerfile、同版本标签，依赖锁各一份。

- [ ] **Step 1: 写三组失败测试**
- `test_proto_generation.py::test_generated_stub_exposes_all_rpcs`：调用 `generate_proto.sh` 生成到临时目录，导入后断言服务含 `Read`、`ReadStream`、`ListEngines`。
- `test_epub_without_ebooklib.py`：`test_parses_chapters_from_zip_only` 用 `zipfile` 现场造一个两章 EPUB，断言 markdown 含两章正文；`test_ebooklib_is_not_imported` 断言解析后 `"ebooklib" not in sys.modules`。
- `ocr/tests/test_ocr_server.py`：夹具在随机端口起服务，Pillow 画一张含 `HELLO` 的 PNG。`test_health_returns_ok`，`test_ocr_reads_rendered_text`（`text` 大写后含 `HELLO`），`test_ocr_rejects_empty_body`（400）。

Run: `cd services && uv run --project docreader pytest docreader/tests/test_proto_generation.py docreader/tests/test_epub_without_ebooklib.py -q`
Expected: FAIL。

- [ ] **Step 2: 实现**
1. `git rm` 全部生成物，`generate_proto.sh` 删除 Go 输出分支，`.gitignore` 排除生成物。镜像构建与本地测试都先生成桩。
2. `epub_parser.py` 删除 `import ebooklib` 与 ebooklib 分支，保留 ZIP 分支，行为不改；`tests/test_epub_external_images.py` 的夹具改用 `zipfile` 造包；`pyproject.toml` 删 `ebooklib`，`uv lock`。
3. `git mv scripts/ocr-server.py services/docreader/ocr/server.py`。`ocr/pyproject.toml` 依赖 `rapidocr-onnxruntime`、测试依赖 `pillow`、`pytest`。旧 Dockerfile 里是不带版本安装，这里用 `uv lock` 钉到解析出的版本。
4. `UPSTREAM.md` 的差异清单追加三条：删 Go 与生成物、删 ebooklib、新增 `ocr/`。
Run:
```bash
cd services
uv run --project docreader pytest docreader/tests -q
uv run --project docreader/ocr pytest docreader/ocr/tests -q
bash docreader/scripts/check_licenses.sh
```
Expected: 上游测试通过，失败项逐个登记到 `UPSTREAM.md` 的已知问题，不改上游逻辑；OCR 3 个用例通过；许可证脚本退出 0。

- [ ] **Step 3: 提交**
```bash
git add services/docreader
git rm scripts/ocr-server.py
git commit -m "feat(docreader): 移除 ebooklib，生成物不入库，OCR 并入 services/docreader"
```

### Task 4: docreader 与 ocr 镜像

**Files:**
- Create: `services/docreader/{Dockerfile,build.sh,smoke.sh,README.md}`
- Delete: `deploy/Dockerfile.ocr`、`deploy/build-ocr-image.sh`

**Interfaces:**
- Consumes: 上游 `docker/Dockerfile.docreader`，两份 `uv.lock`
- Produces:
  - 镜像 `ragagent/docreader:<上游短哈希>-<yyyyMMdd>` 与 `ragagent/docreader-ocr:<同>`，另打 `:ci`
  - Dockerfile 两个 target：`grpc`、`ocr`，构建上下文是 `services/`，因为包名要求目录叫 `docreader`
  - 基础镜像按摘要钉死；`grpc_health_probe` 下载后校验 sha256
  - `bash services/docreader/smoke.sh grpc|ocr <image>`

- [ ] **Step 1: 写失败的冒烟脚本并写 Dockerfile**
冒烟 `grpc`：起容器，用 `grpc_health_probe -addr=:50051` 通过，再用 Python 客户端调 `ListEngines` 返回非空，30 秒内。冒烟 `ocr`：`GET /health` 为 200，再 POST 一张内置 PNG 得到非空文本。先跑 `bash services/docreader/smoke.sh grpc ragagent/docreader:ci`，Expected: FAIL，镜像不存在。
Dockerfile 的 `grpc` target 沿用上游两阶段：构建阶段 `python:3.10.18-bookworm@sha256:<摘要>`，`uv sync --locked --no-dev`，生成 proto 桩；运行阶段同基础镜像，系统包照上游清单，Playwright webkit，`grpc_health_probe v0.4.24`，入口与命令沿用 `python -m docreader.container_entrypoint` 与 `python -m docreader.main`。`ocr` target 用 `python:3.11-slim@sha256:<摘要>`，装 `libgl1 libglib2.0-0 libxcb1 libsm6 libxext6 libxrender1 libgomp1`，`MALLOC_ARENA_MAX=2`，非 root，`EXPOSE 18092`，`CMD python /app/ocr/server.py 18092`。摘要用 `docker buildx imagetools inspect` 取得。

- [ ] **Step 2: 构建并冒烟**
Run:
```bash
bash services/docreader/build.sh
bash services/docreader/smoke.sh grpc ragagent/docreader:ci
bash services/docreader/smoke.sh ocr ragagent/docreader-ocr:ci
```
Expected: 两个冒烟通过。许可证报告随构建输出，镜像内 `pip-licenses` 结果与任务 3 一致。

- [ ] **Step 3: 提交**
```bash
git add services/docreader/Dockerfile services/docreader/build.sh services/docreader/smoke.sh services/docreader/README.md
git rm deploy/Dockerfile.ocr deploy/build-ocr-image.sh
git commit -m "feat(docreader): 自建 docreader 与 ocr 镜像并钉死基础镜像摘要"
```

### Task 5: proto 唯一来源与后端客户端

**Files:**
- Modify: `<PROTO_POM>`（`backend/` 下持有 `protobuf-maven-plugin` 的 pom）
- Delete: `docreader/`（仓库根的 proto 目录）
- Create: `backend/app/src/test/java/com/ragagent/docreader/DocreaderClientIT.java`，若计划 04 已有针对真实 docreader 的 IT，只改它的镜像来源，不新建

**Interfaces:**
- Consumes: `services/docreader/proto/docreader.proto`
- Produces: `protoSourceRoot` 为 `${project.basedir}/../../services/docreader/proto`；JUnit 标签 `docreader-it`，默认不跑，CI 的 docreader job 显式 `-Dgroups=docreader-it`

新 proto 是旧 proto 的超集：旧三个 RPC 与全部旧字段编号保留，`reserved 3` 仍在，Java 客户端只用旧三个 RPC，所以换来源不改客户端代码。

- [ ] **Step 1: 写失败的 IT**
`@Tag("docreader-it")` 加 `@Testcontainers`，容器用 `ragagent/docreader:ci`，暴露 50051。`readsPlainTextAsMarkdown`：读 `sample.txt`，`error` 为空，`markdownContent` 含两段文字。`streamedReadEmitsMetaBeforeImages`：`readStream` 首帧带 meta。`listEnginesIsNotEmpty`。
Run: `MVN -pl app -am test -Dgroups=docreader-it -Dtest=DocreaderClientIT`
Expected: FAIL，镜像未接入或桩未重新生成。

- [ ] **Step 2: 改来源、删旧目录并验证**
Edit `<PROTO_POM>` 的 `protoSourceRoot`，`git rm -r docreader`，再跑上面的命令。
Expected: BUILD SUCCESS，生成的桩仍在原包名下，IT 3 个用例通过。

- [ ] **Step 3: 提交**
```bash
git add backend services/docreader/proto
git rm -r docreader
git commit -m "refactor(docreader): proto 以 services/docreader 为唯一来源"
```

### Task 6: mcp-server 改用新 API

**Files:**
- Modify: `mcp-server/ragagent_mcp_server.py`、`pyproject.toml`、`Dockerfile`、`README.md`、`CHANGELOG.md`、`tests/test_kb_id_resolution.py`
- Create: `mcp-server/api_paths.py`、`mcp-server/sse.py`、`mcp-server/tests/{test_sse.py,test_paths_against_openapi.py}`

**Interfaces:**
- Consumes: 1.3 的映射骨架，`backend/app/target/openapi.json`，`<SSE_EVENTS>`，`<OPEN_TOKEN_HEADER>`
- Produces:
  - `api_paths.py`：每个调用一个常量，值是相对 `RAGAGENT_BASE_URL` 的路径
  - `sse.py`：`iter_events(lines: Iterable[bytes | str]) -> Iterator[SseEvent]`，`SseEvent(event: str, data: str, id: str | None)`
  - `RAGAGENT_BASE_URL` 默认 `http://localhost:8080/api/open/v1`，Dockerfile 里默认 `http://app:8080/api/open/v1`

- [ ] **Step 1: 写失败测试**
`test_sse.py`：`test_joins_multiline_data_fields`（`data: a`、`data: b` 加空行得 `a\nb`）；`test_ignores_comments_and_unknown_fields`；`test_handles_crlf_and_bom`；`test_flushes_last_event_without_blank_line`；`test_event_and_id_fields_are_captured`。
`test_paths_against_openapi.py`：读 `OPENAPI_FILE`（默认 `../backend/app/target/openapi.json`），遍历 `api_paths` 的常量与方法，断言 `/api/open/v1` 加路径存在且含该方法；文件缺失时用例失败，不跳过。
Run: `cd mcp-server && python -m pytest tests/test_sse.py tests/test_paths_against_openapi.py -q`
Expected: FAIL，模块不存在。

- [ ] **Step 2: 实现**
1. `sse.py` 按 SSE 规范：行以 CR、LF 或 CRLF 结束，首行去 BOM，冒号开头是注释，空行分发，`data` 多行用换行连接，未知字段忽略。
2. `api_paths.py` 按 1.3 的表逐项填，路径取自 `openapi.json`。
3. 主文件：所有 `_request` 用常量；`_consume_sse_stream` 改用 `iter_events`，事件字段以 `<SSE_EVENTS>` 为准；请求头 `X-API-Key` 改为 `<OPEN_TOKEN_HEADER>`；删 `list_shared_knowledge_bases`、`_normalize_kb_entries`、`get_tenant`、`update_knowledge_base`；`test_kb_id_resolution.py` 删共享库用例；租户类工具按 openapi 取舍。
4. `pyproject.toml` 与主文件的版本改 `2.0.0`，`CHANGELOG.md` 写明三项破坏性变更。

- [ ] **Step 3: 运行并提交**
Run:
```bash
MVN -pl app -am test -Dtest=OpenApiExportTest
cd mcp-server && python -m pytest -q
```
Expected: 新增用例全绿，`test_paths_against_openapi` 无缺失路径，其余旧用例相对改动前不新增失败（动手前先跑一次 `python -m pytest -q` 记录基线）。
```bash
git add mcp-server
git commit -m "feat(mcp-server): 改用 /api/open/v1 并按 SSE 规范解析"
```

### Task 7: 前端迁 pnpm 工作区（先不拆分）

> **2026-11-17 决策：前端不拆分。** 现有单项目已经是双入口（`src/main.ts` + `src/embed-main.ts`，`vite.config.ts` 的 `input.main` / `input.embed`）产出两份 bundle，`nginx.conf` 也按 `/embed/`、`/embed.html`、`/ragagent-widget.js` 单独放开了 `X-Frame-Options`——拆分要达成的两个技术目标（widget 体积、iframe 头）已经满足，放弃的只是「console / widget 独立发版、独立镜像」。因此本任务与 Task 8、Task 9 **作废**；Task 10 相应改为单镜像。


**Files:**
- Create: `frontend/{pnpm-workspace.yaml,.npmrc,pnpm-lock.yaml}`、`frontend/vendor/xlsx-0.20.2.tgz`、`docs/migration/frontend-baseline.json`
- Modify: `frontend/package.json`
- Delete: `frontend/package-lock.json`、`frontend/packages/api-client/package-lock.json`（若存在）

**Interfaces:**
- Consumes: 旧 `package-lock.json`，计划 01 的 `frontend/packages/api-client`，计划 03 接入的 `@ragagent/api-client` 依赖写法
- Produces: `packages: ["apps/*", "packages/*"]`；`frontend-baseline.json` 形如 `{"entries":{"main":{"jsGzipBytes":N},"embed":{"jsGzipBytes":N}},"testFiles":142}`

本任务起旧 `frontend/Dockerfile` 与 CI 的 frontend job 不可用，任务 10 与 17 修复，`rebuild` 分支上允许这段时间红。

- [ ] **Step 1: 迁移前录基线**
Run:
```bash
cd frontend && npm ci && npm run build
find src -name '*.test.*' | wc -l
```
用系统临时目录里的脚本统计 `dist/assets` 下 main 与 embed 入口及其静态依赖的 gzip 总字节，写入基线。Expected: 测试文件数 142。

- [ ] **Step 2: 迁移**
Run: `cd frontend && corepack enable && pnpm import && pnpm install --frozen-lockfile`
然后：`git mv packages/xlsx-0.20.2.tgz vendor/`，依赖改 `file:./vendor/xlsx-0.20.2.tgz`；`overrides` 与 `resolutions` 合并为 `pnpm.overrides`；`@ragagent/api-client` 的 `file:` 链接改 `workspace:*`；删两份 `package-lock.json`。

- [ ] **Step 3: 核对版本未漂移并构建**
Run: `pnpm ls --depth 0 --json`，与迁移前 `npm ls --depth 0 --json` 比对 `vue`、`vue-router`、`pinia`、`vue-i18n`、`tdesign-vue-next`、`vite`、`typescript`、`marked`、`mermaid`，再跑 `cd frontend && pnpm type-check && pnpm test && pnpm build && pnpm --filter @ragagent/api-client run check:api`。
Expected: 版本一致，不一致的登记 `deviations.md`；全部通过，测试数不低于 142。

- [ ] **Step 4: 提交**
```bash
git add frontend/pnpm-workspace.yaml frontend/.npmrc frontend/pnpm-lock.yaml frontend/package.json frontend/vendor frontend/packages/api-client docs/migration/frontend-baseline.json
git rm frontend/package-lock.json
git commit -m "build(frontend): 迁移到 pnpm 工作区并录制体积基线"
```

### Task 8: 前端拆分的分类与导入改写工具（2026-11-17 作废，见 Task 7 决策）

**Files:**
- Create: `frontend/tools/split/{classify.mjs,rewrite-imports.mjs,rewrite-imports.test.mjs,classification.json}`

**Interfaces:**
- Consumes: `frontend/src` 全部源文件，入口 `src/main.ts` 与 `src/embed-main.ts`
- Produces:
  - `classification.json`：`{ "<相对 src 的路径>": "console" | "widget" | "ui" | "i18n" }`，每个文件恰好一个归属
  - `classify(entries): Record<string, Owner>`：只被 widget 入口闭包到达的归 `widget`；两个入口都到达的归 `ui`，其中 `src/i18n/**` 归 `i18n`；其余归 `console`；测试文件跟随它第一个导入的非测试文件
  - `rewriteImports(source, ctx: { owner, fileRel, classification }): string`

- [ ] **Step 1: 写失败测试**
`keepsAliasForSameOwner`；`rewritesSharedTargetToPackageSpecifier`（console 引用归 ui 的目标，改为 `@ragagent/ui/...`）；`rewritesAliasInsidePackageToRelative`；`rewritesDynamicImport`（`import('@/views/chat/Z.vue')`）；`rewritesViMock`；`leavesNodeModulesUntouched`；`isIdempotent`；`aliasTargetMissingFromClassificationThrows`。
Run: `cd frontend && pnpm exec tsx --test tools/split/rewrite-imports.test.mjs`
Expected: FAIL，模块不存在。

- [ ] **Step 2: 实现并生成分类，核对**
只处理以 `@/` 开头的三类具名字符串：`from` 子句、动态 `import(...)`、`vi.mock(...)`，扩展名容错。依赖分析用 `dependency-cruiser`（开发依赖），配置 `@` 别名与 tsconfig。
Run:
```bash
cd frontend && pnpm exec node tools/split/classify.mjs > tools/split/classification.json
node -e "const c=require('./tools/split/classification.json');const n={};for(const o of Object.values(c))n[o]=(n[o]||0)+1;console.log(n,Object.keys(c).length)"
find src -type f \( -name '*.ts' -o -name '*.vue' -o -name '*.css' -o -name '*.less' \) | wc -l
```
Expected: 分类文件数等于源文件数，四类都有；`views/embed/**` 全归 `widget`；被 widget 引用的 `api/embed`、`api/message-suggestion` 归 `ui`，其余 `api/**` 归 `console`。

- [ ] **Step 3: 提交**
```bash
git add frontend/tools/split frontend/package.json frontend/pnpm-lock.yaml
git commit -m "build(frontend): 前端拆分的分类与导入改写工具"
```

### Task 9: 拆出 console、widget 与共享包（2026-11-17 作废，见 Task 7 决策）

**Files:**
- Move（`git mv`，按 `classification.json`）：`frontend/src/**` 到 `apps/console/src`、`apps/widget/src`、`packages/ui/src`、`packages/i18n/src`
- Create: `frontend/apps/console/{package.json,vite.config.ts,tsconfig*.json,index.html}`、`frontend/apps/widget/{package.json,vite.config.ts,tsconfig*.json,embed.html}`、`frontend/packages/{ui,i18n}/package.json`
- Move: `public/ragagent-widget.js`、`embed-boot.css`、5 个 demo 与测试页到 `apps/widget/public/`；`favicon.ico`、`ragagent-logo.webp` 到 `apps/console/public/`；`config.js` 与 `tdesign-icons/` 到 `packages/ui/public/`
- Test: `frontend/apps/widget/src/widgetBuildContract.test.ts`

**Interfaces:**
- Consumes: 任务 8 的分类与改写工具
- Produces:
  - 包 `@ragagent/ui`、`@ragagent/i18n`，`exports: {"./*": "./src/*"}`；`@ragagent/api-client` 保持原位
  - console：开发端口 5173，代理 `/api`、`/files` 到 `VITE_DEV_PROXY_TARGET`（默认 `http://localhost:8080`），代理 `/embed/`、`/embed-assets/`、`/ragagent-widget.js` 到 `http://localhost:5174`；不再有 embed 入口与 embed 开发回落插件；`@vue-office/pptx` 入口解析与 `xlsx` 只在这里
  - widget：开发端口 5174，单入口 `embed.html`，`build.assetsDir = 'embed-assets'`，保留 `modulePreload` 对 embed 重块的过滤与 `manualChunks`，开发回落插件改为本应用内的 `/embed/:channelId` 改写
  - 两个应用都用 `vite-plugin-static-copy` 把 `packages/ui/public/*` 复制到构建根，开发态同样生效；tdesign 图标 shim 别名两边都保留
  - 根脚本：`pnpm dev:console`、`pnpm dev:widget`、`pnpm -r type-check`、`pnpm -r test`、`pnpm -r build`

- [ ] **Step 1: 写失败测试 `widgetBuildContract.test.ts`**
```ts
test('embedAssetsLiveUnderEmbedAssetsDir', () => {
  const html = readFileSync('dist/embed.html', 'utf8')
  assert.match(html, /\/embed-assets\//)
  assert.doesNotMatch(html, /src="\/assets\//)
})
test('widgetLoaderConfigAndIconsArePublished', () => {
  for (const f of ['ragagent-widget.js', 'embed-boot.css', 'config.js', 'tdesign-icons/0.4.1/fonts/index.js']) assert.ok(existsSync(`dist/${f}`))
})
test('widgetFirstScreenJsNotLargerThanBaselinePlus5Percent', () => {
  const base = JSON.parse(readFileSync('../../../docs/migration/frontend-baseline.json', 'utf8')).entries.embed.jsGzipBytes
  assert.ok(measureEmbedEntryGzip('dist') <= base * 1.05)
})
```
Run: `cd frontend/apps/widget && pnpm build && pnpm exec tsx --test src/widgetBuildContract.test.ts`
Expected: FAIL，应用目录不存在。

- [ ] **Step 2: 移动文件并改写导入**
逐文件 `git mv`，再对全部文件调用 `rewriteImports` 写回。`check-i18n` 等 i18n 脚本随 `src/i18n` 移到 `packages/i18n`。组件超 400 行的不拆，只随归属移动。

- [ ] **Step 3: 全量验证与开发态联通**
Run: `cd frontend && pnpm install --frozen-lockfile && pnpm -r type-check && pnpm -r test && pnpm -r build`
Expected: 类型检查无错；各包测试总数不低于 142；两个应用都构建成功；步骤 1 的三个断言通过。再起新后端与两个 dev server，打开 `http://localhost:5173/embed/<渠道ID>`：页面由 5174 的 widget 提供，控制台无 404，用 `apps/widget/public/widget-test.html` 验证 iframe 与 postMessage 往返。

- [ ] **Step 4: 提交**
```bash
git add frontend/apps frontend/packages frontend/package.json frontend/pnpm-lock.yaml frontend/pnpm-workspace.yaml
git rm -r frontend/src frontend/public frontend/index.html frontend/embed.html frontend/vite.config.ts
git commit -m "refactor(frontend): 拆分 console 与 widget 并抽出 ui 与 i18n 共享包"
```

### Task 10: 前端镜像与 nginx（2026-11-17 复评：只做单镜像）

> **复评后的范围**：不建 `apps/*`，沿用现有 `frontend/Dockerfile`、`frontend/nginx.conf`、`frontend/nginx-api-proxy.conf`、`frontend/nginx-http.conf`、`frontend/docker-entrypoint.sh`。只做三件事：①镜像标签与 docreader 口径对齐（`ragagent/ui:<上游短哈希>-<yyyyMMdd>`，另打 `:ci`）；②保留 `frontend/tools/check-nginx-routes.mjs` 做特殊路径与 openapi 的核对；③删除 `deploy/Dockerfile.ui`。双镜像、`$WIDGET_HOST` 反代与两套 `config.js` 不再需要。


**Files:**
- Create: `frontend/apps/console/{Dockerfile,nginx/default.conf.template,nginx/api-proxy.conf,nginx/http.conf,docker-entrypoint.sh}`、`frontend/apps/widget/{Dockerfile,nginx/default.conf.template,docker-entrypoint.sh}`
- Create: `frontend/tools/{check-nginx-routes.mjs,check-nginx-routes.test.mjs}`
- Delete: `frontend/{Dockerfile,nginx.conf,nginx-api-proxy.conf,nginx-http.conf,docker-entrypoint.sh}`、`deploy/Dockerfile.ui`

**Interfaces:**
- Consumes: 旧 `nginx.conf` 的特殊路径（见 1.4），`openapi.json`，计划 06 到 08 的实际路径
- Produces:
  - console 镜像 `ragagent-console`：静态资源、`/api/` 反代到 `${APP_HOST}:${APP_PORT}`、特殊路径改到新前缀、`/embed/`、`/embed-assets/`、`/ragagent-widget.js` 反代到 `${WIDGET_HOST}:80`；`/` 与 `/assets/` 带 `X-Frame-Options: SAMEORIGIN`；入口脚本生成含三个键的 `config.js`
  - widget 镜像 `ragagent-widget`：只服务 `embed.html`、`/embed/`、`/embed-assets/`、`/ragagent-widget.js`、`config.js`、`tdesign-icons/`，把访客前缀 `/api/v1/` 反代到后端以支持独立子域部署；响应不带 `X-Frame-Options`；入口脚本只生成含 `DEFAULT_LOCALE` 的 `config.js`
  - `checkRoutes(nginxConf: string, openapi: object, allowlist: string[]): { unmatched: string[] }`

- [ ] **Step 1: 写失败测试**
```js
test('everySpecialLocationMatchesAnOpenApiPath', () => {
  assert.deepEqual(checkRoutes(readConf('console'), readOpenapi(), WEBSOCKET_ALLOWLIST).unmatched, [])
})
test('detectsStaleLocation', () => {
  const conf = 'location ~ ^/api/v1/sessions/[^/]+/sandbox/terminal$ { }'
  assert.equal(checkRoutes(conf, { paths: {} }, []).unmatched.length, 1)
})
```
Run: `cd frontend && pnpm exec tsx --test tools/check-nginx-routes.test.mjs`
Expected: FAIL，模块不存在。

- [ ] **Step 2: 改写 nginx 规则，写两个 Dockerfile 并验证**
旧特殊路径逐项落到新前缀：本地浏览器扩展 WebSocket 与 internal 的 404、沙箱终端 WebSocket、技能包上传两条集合路由（只对这两条放宽 body 上限，不抬全站上限）、`/files`、`/r/`。新路径取自 `openapi.json`。WebSocket 路径不在 OpenAPI 里，只能进 `WEBSOCKET_ALLOWLIST`，每条写明原因。SSE 所需的关缓冲与 3600s 超时、`api_no_query` 日志格式、`resolver 127.0.0.11` 加变量 upstream 的写法原样保留。
Dockerfile 构建阶段 `corepack enable && pnpm install --frozen-lockfile && pnpm --filter @ragagent/console... build`（widget 同理），上下文是 `frontend/`。基础镜像沿用旧 Dockerfile 的 node 与 `nginx:1.30.3-alpine` 摘要。运行阶段先清空 `/usr/share/nginx/html` 再 COPY，避免镜像累积旧哈希资源。
Run:
```bash
docker build -f frontend/apps/console/Dockerfile -t ragagent-console:ci frontend
docker build -f frontend/apps/widget/Dockerfile -t ragagent-widget:ci frontend
docker run --rm -e APP_HOST=x -e WIDGET_HOST=y ragagent-console:ci nginx -t
docker run --rm -e APP_HOST=x ragagent-widget:ci nginx -t
curl -sI http://localhost:<console>/ | grep -i x-frame-options
curl -sI http://localhost:<widget>/embed/abc | grep -ci x-frame-options
```
Expected: 两个 `nginx -t` successful；console 返回 `SAMEORIGIN`；widget 计数为 0；路由测试通过。两条 `curl` 在两个容器各自起来后执行。

- [ ] **Step 3: 提交**
```bash
git add frontend/apps/console frontend/apps/widget frontend/tools
git rm frontend/Dockerfile frontend/nginx.conf frontend/nginx-api-proxy.conf frontend/nginx-http.conf frontend/docker-entrypoint.sh deploy/Dockerfile.ui
git commit -m "build(frontend): console 与 widget 独立镜像与 nginx 路由校验"
```

### Task 11: 后端镜像、角色与健康检查

**Files:**
- Create: `backend/Dockerfile`、`backend/app/src/test/java/com/ragagent/ops/ManagementEndpointsTest.java`、`deploy/smoke-roles.sh`
- Modify: `backend/app/src/main/resources/application.yml`、`backend/app/pom.xml`、`.dockerignore`
- Delete: `Dockerfile`、`deploy/Dockerfile.app`

**Interfaces:**
- Consumes: `<ROLE_PROP>`、`<ROLE_API>`、`<ROLE_WORKER>`、`<ROLE_ALL>`，计划 01 Task 11b 的环境变量
- Produces:
  - `backend/Dockerfile` 三个 target：`base`（JRE 21、curl、ffmpeg、非 root uid 1001、`/app/data/files`）、`runtime`（容器内 Maven 构建）、`runtime-from-jar`（复制本机构建的 `app.jar`，供 `deploy/build-images.sh` 在 arm64 本机打 amd64 镜像）；构建上下文是仓库根，除 `backend/` 外只需 `services/docreader/proto`；Flyway 脚本在 jar 的 classpath 里，旧的 `generated-migrations` 目录消失
  - 管理端口 8081：`management.server.port`，暴露 `health,info,prometheus`，开启探针，`readiness` 组含 `readinessState,db,redis`，`liveness` 组含 `livenessState`
  - 镜像 `HEALTHCHECK`：`curl -fsS http://127.0.0.1:8081/actuator/health/readiness`
  - 角色启动参数：

| 项 | api | worker | 单实例 |
|---|---|---|---|
| `<ROLE_PROP>` | `<ROLE_API>` | `<ROLE_WORKER>` | `<ROLE_ALL>` |
| 业务端口 8080 | 对外 | 不映射，不加载业务控制器 | 对外 |
| 管理端口 8081 | 内部 | 内部 | 内部 |
| readiness 额外项 | 无 | JobRunr 后台服务器已运行的健康指示器，计划 04 没有就在本任务补 | 同 worker |
| JVM 与内存 | `-XX:MaxRAMPercentage=75`，上限 1200m 起 | 同，压测后调 | 同 |

- [ ] **Step 1: 写失败测试 `ManagementEndpointsTest`**
`@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "management.server.port=0")`，继承 `PostgresContainerTest`。
- `readinessIsUpWithoutCredentials`：管理端口 `GET /actuator/health/readiness` 为 200，体含 `"status":"UP"`。
- `prometheusExposesJvmAndHttpMetrics`：`/actuator/prometheus` 含 `jvm_memory_used_bytes` 与 `http_server_requests_seconds`。
- `businessPortDoesNotServeActuator`：业务端口 `GET /actuator/prometheus` 为 401 或 404。
- `qaTerminalMetricIsRegistered`：旧业务指标 `QaTerminalMetrics` 在新结构里的指标名（取自计划 08 的实现）出现在 prometheus 输出里。

Run: `MVN -pl app -am test -Dtest=ManagementEndpointsTest`
Expected: FAIL，未暴露或缺 `micrometer-registry-prometheus`。

- [ ] **Step 2: 配置、依赖、Dockerfile，运行并提交**
`application.yml` 增 `management.*`，原 `exposure.include: health` 改为三项；`pom.xml` 加 `micrometer-registry-prometheus`。旧 `/health` 控制器不迁移，它的消费者是旧 Dockerfile 与旧 CI 烟测，都在本计划里改掉。写 `backend/Dockerfile`，`.dockerignore` 排除 `**/node_modules`、`**/target`、`frontend/`。
Run:
```bash
MVN -pl app -am test -Dtest=ManagementEndpointsTest
docker build -f backend/Dockerfile --target runtime -t ragagent-server:ci .
```
Expected: 4 个用例通过，镜像构建成功。

- [ ] **Step 3: 角色冒烟 `deploy/smoke-roles.sh`，再提交**
同一镜像分别以 `<ROLE_API>` 与 `<ROLE_WORKER>` 启动（接 compose 里的 postgres 与 redis），断言：两者 120 秒内 readiness 为 UP；api 的 `GET /api/admin/v1/auth/me` 返回 401；worker 同一请求返回 404 或连接被拒。worker 能否关闭业务端口取决于 `server.port=-1` 与管理端口能否并存，不能并存就保留业务端口、不加载控制器，以断言为准，需运行时验证。
Run: `bash deploy/smoke-roles.sh ragagent-server:ci`
Expected: 4 项断言通过。
```bash
git add backend/Dockerfile backend/app .dockerignore deploy/smoke-roles.sh
git rm Dockerfile deploy/Dockerfile.app
git commit -m "feat(deploy): 后端镜像、api 与 worker 角色、管理端口与 Micrometer 指标"
```

### Task 12: 可观测改用 OpenTelemetry 官方 SDK

**Files:**
- 按 `<OTLP_STATE>` 分情形：计划 02 已改，核对 `otlp-proto/` 无人引用后直接删目录；未改，Create `backend/<TRACING_MODULE>/.../tracing/{LangfuseTracerConfig,LangfuseExportTest}.java`，Delete 自维护的 `BatchSpanProcessor`、`OtlpHttpExporter`、`Span`、`Trace`、`RecordedSpan`、`LangfuseContext`、`LangfuseTaskScope`
- Delete: `otlp-proto/`

**Interfaces:**
- Consumes: 旧 `OtlpHttpExporter` 的协议行为，`LangfuseAttributes` 的 `ATTR_` 常量，装饰器 `LangfuseChatClient`、`LangfuseEmbedder`、`LangfuseReranker`、`LangfuseVlm`
- Produces:
  - `OpenTelemetry langfuseOpenTelemetry(LangfuseProperties props)`：无公钥与私钥时返回 `OpenTelemetry.noop()`
  - 导出端点 `{host}/api/public/otel/v1/traces`，请求头 `Authorization: Basic <base64(public:secret)>` 与 `x-langfuse-ingestion-version: 4`，HTTP/protobuf
  - 发送器用 `opentelemetry-exporter-sender-okhttp`，因为旧代码的注释记录了 JDK `HttpClient` 默认走 HTTP/2，对明文自建 Langfuse 会先发 h2c 升级请求而断连，OkHttp 对明文只走 HTTP/1.1
  - 全部属性值用 `AttributeKey.stringKey`，与旧实现和 langfuse-python 一致；resource 属性 `service.name=ragagent`
  - 环境变量族 `LANGFUSE_*` 名称不变
  - 入队与续接：`W3CTraceContextPropagator` 把 traceparent 注入任务载荷，worker 侧提取续接，替代旧 `LangfuseTracing.inject` 与 `extract`

- [ ] **Step 1: 先 grep 确认状态，再写失败测试 `LangfuseExportTest`**
Run: `grep -rln "OtlpHttpExporter\|io.opentelemetry.proto" backend; ls otlp-proto 2>/dev/null`，输出须与 `<OTLP_STATE>` 一致。
JDK `HttpServer` 接收请求，建一个 generation span 并强制 flush。
- `postsToLangfuseOtlpPathWithRequiredHeaders`：路径、两个头断言。
- `usesHttp11WithoutUpgradeHeader`：请求协议为 HTTP/1.1，无 `Upgrade` 头。
- `exportedSpanCarriesEveryLangfuseAttributeKey`：用反射收集 `ATTR_` 常量，解析请求体，断言键全部出现且值类型为字符串。测试依赖 `opentelemetry-proto` 仅 test 作用域。
- `traceparentSurvivesJobHandoff`：注入到 `Map`，另一线程提取，trace id 相同。
- `withoutKeysTheBeanIsNoop`。

Run: `MVN -pl <TRACING_MODULE> -am test -Dtest=LangfuseExportTest`
Expected: FAIL。

- [ ] **Step 2: 实现，删 otlp-proto 并验证**
依赖 `opentelemetry-sdk`、`opentelemetry-exporter-otlp`，排除默认 JDK 发送器，加 okhttp 发送器。装饰器只把底层 span 创建换成 SDK 的 `Tracer`，对外行为不变。
Run:
```bash
git rm -r otlp-proto
MVN -pl app -am verify
```
Expected: BUILD SUCCESS，`LangfuseExportTest` 5 个用例通过，`grep -rn "otlp-proto" backend deploy .github` 无输出。

- [ ] **Step 3: 提交**
```bash
git add backend
git rm -r otlp-proto
git commit -m "refactor(observability): 改用 OpenTelemetry 官方 SDK 导出 Langfuse 链路"
```

### Task 13: 部署编排、环境变量与开发脚本

**Files:**
- Modify: `docker-compose.yml`、`deploy/docker-compose.prod.yml`、`deploy/build-images.sh`、`deploy/env.prod.example`、`.env.example`、`scripts/{dev-up.sh,dev-env.sh,doctor.sh,java-server-up.sh,java-server-down.sh,token.sh}`
- Create: `deploy/docker-compose.e2e.yml`、`deploy/db/init/20-app-password.sh`、`deploy/check-env.sh`、`deploy/ENVIRONMENT.md`

**Interfaces:**
- Consumes: 任务 4、10、11 的镜像，`<DOCREADER_ENV>`、`<OCR_ENV>`，计划 01 的 `deploy/db/`
- Produces:
  - 开发编排 `docker-compose.yml`：`include` 计划 01 的 `deploy/db/docker-compose.yml`（数据库 25432），`redis`（16379），`docreader`（50051，本地 build），`ocr`（18092）；Langfuse 编排不动
  - 生产编排服务：`postgres redis docreader ocr api worker console widget`，对外只暴露 `127.0.0.1:8000` 指向 console；`api` 与 `worker` 同镜像，用 `<ROLE_PROP>` 区分，各带 readiness 健康检查；profile `single` 下用单个 `app` 服务承担两个角色，命令 `docker compose --profile single up -d`；docreader 健康检查用 `grpc_health_probe`；`GRPC_AUTH_TOKEN` 默认不设
  - 环境变量：`DB_URL`、`DB_OWNER_USER`、`DB_OWNER_PASSWORD`、`DB_APP_USER`、`DB_APP_PASSWORD`、`REDIS_*`、`RAGAGENT_ENCRYPT_KEY`、`CORS_ALLOWED_ORIGINS`、`<DOCREADER_ENV>`、`<OCR_ENV>`、`APP_EXTERNAL_URL`、`LOCAL_STORAGE_BASE_DIR`
  - `20-app-password.sh`：库初始化时用 `DB_APP_PASSWORD` 改应用角色口令，覆盖计划 01 `10-roles.sql` 里的开发口令
  - `deploy/check-env.sh`：枚举 `application*.yml` 中无默认值的 `${VAR}`，缺失于 `deploy/env.prod.example` 即退出 1
  - e2e 编排：在生产编排上加 `stub-llm`（`scripts/stub-llm-server.py`）
  - `deploy/build-images.sh` 产出六个镜像：server、console、widget、docreader、docreader-ocr、数据库（调用 `deploy/db/build.sh`），沿用 `--platform linux/amd64` 与 `--save`
  - `ENVIRONMENT.md`：旧变量到新变量的对照，被删除的变量写明理由

- [ ] **Step 1: 写失败的检查脚本，再改 compose、env 与脚本**
先写 `deploy/check-env.sh`，跑 `bash deploy/check-env.sh`，Expected: FAIL，列出尚未写入示例的变量。然后改：
1. 数据库角色分离：Flyway 用属主账号，应用用非属主账号，行级安全才对应用生效。
2. `api` 与 `worker` 的 `depends_on` 等 postgres、redis、docreader 就绪。
3. `java-server-up.sh` 改为构建并运行 `backend/app`，默认端口 8080，就绪探测改管理端口；`dev-env.sh` 删旧项；`token.sh` 改走新登录端点；`doctor.sh` 增 docker、pnpm、JDK 21 检查，去掉旧模块检查。
4. `.env.example` 按 `ENVIRONMENT.md` 重写。

- [ ] **Step 2: 验证并全栈起停**
Run:
```bash
bash deploy/check-env.sh
docker compose -f docker-compose.yml config -q
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/env.prod.example config -q
docker compose -f deploy/docker-compose.prod.yml --profile single --env-file deploy/env.prod.example config -q
bash scripts/doctor.sh
docker compose -f deploy/docker-compose.prod.yml -f deploy/docker-compose.e2e.yml --env-file deploy/env.prod.example up -d --wait
```
Expected: `check-env.sh` 退出 0；compose 校验通过；`doctor.sh` 无阻断项；所有服务 healthy，`curl -fsS http://127.0.0.1:8000/` 返回控制台首页，`docker compose ps` 里 worker 没有宿主端口映射。

- [ ] **Step 3: 提交**
```bash
git add docker-compose.yml deploy .env.example scripts/dev-up.sh scripts/dev-env.sh scripts/doctor.sh scripts/java-server-up.sh scripts/java-server-down.sh scripts/token.sh
git commit -m "build(deploy): 新编排、数据库角色分离与环境变量对照"
```

### Task 14: Flyway 合并为单份 V1

**Files:**
- Create: `backend/app/src/main/resources/db/migration/allenv/V1__baseline.sql`、`deploy/db/{merge-migrations.sh,compare-schema.sh}`
- Modify: 引用占位迁移版本 `20261110.01` 的测试（计划 01 Task 11b 的 `flywayRecognisesAllenvDirectory`，改为断言版本 `1`）
- Delete: `allenv/` 下所有其他 `V*.sql`

**Interfaces:**
- Consumes: `allenv/` 下全部分域脚本，计划 01 的数据库镜像与 `deploy/db/init`
- Produces:
  - `merge-migrations.sh <容器名> <输出文件>`：读该容器库里 `flyway_schema_history` 按 `installed_rank` 的脚本顺序，把对应文件逐个拼接，每段前加 `-- source: <原文件名>`，不重排、不折叠 ALTER。顺序与 Flyway 实际执行顺序一致，计划 01 的授权占位脚本因此仍在最前，`ALTER DEFAULT PRIVILEGES` 先于所有建表
  - `compare-schema.sh <容器A> <容器B>`：六项比对，任一不同退出 1：(1) `pg_dump --schema-only --no-owner` 导出，过滤 `\restrict`、`\unrestrict` 与 `-- Dumped` 行后 diff，授权不过滤；(2) `pg_policies`；(3) `pg_indexes`；(4) 每表 `relrowsecurity` 与 `relforcerowsecurity`；(5) 序列与枚举；(6) 各表精确行数（`query_to_xml` 计数）
  - 单份 V1 含 DDL 与种子 DML，因为路线图 3.5 与用户要求是单份 V1；增量脚本仍按用户全局规则 DDL 与 DML 分开。单份含 DML 与全局规则的差异登记 `deviations.md`
  - 文件名双下划线，Flyway 位置仍是 `classpath:db/migration/allenv`，后续增量脚本版本号为日期型，大于 1

- [ ] **Step 1: 写失败测试并实现自测**
`compare-schema.sh --self-test`：同一容器自比对必须通过；对 B 故意多建一个索引、少一条策略、多一行种子各一次，每次必须失败。先跑 `bash deploy/db/compare-schema.sh --self-test`，Expected: FAIL，脚本不存在。实现后再跑，Expected: 输出 `self-test ok`，三种人为差异都被识别。

- [ ] **Step 2: 起两个空库，逐版本迁移 A，合并后只应用 V1 到 B**
Run:
```bash
IMG=ragagent/postgres:17-pgvector-pgroonga
for n in a b; do docker run -d --name rb-$n -e POSTGRES_PASSWORD=pw -v "$PWD/deploy/db/init:/docker-entrypoint-initdb.d" $IMG; done
FLYWAY=$(MVN -pl app -am dependency:tree | grep -o 'flyway-core:jar:[0-9.]*' | head -1 | cut -d: -f3)
docker run --rm -v "$PWD/backend/app/src/main/resources/db/migration/allenv:/flyway/sql:ro" flyway/flyway:$FLYWAY \
  -url=jdbc:postgresql://<rb-a 的地址>:5432/postgres -user=postgres -password=pw migrate
bash deploy/db/merge-migrations.sh rb-a /tmp/V1__baseline.sql
mkdir -p /tmp/v1only && cp /tmp/V1__baseline.sql /tmp/v1only/
docker run --rm -v /tmp/v1only:/flyway/sql:ro flyway/flyway:$FLYWAY -url=jdbc:postgresql://<rb-b 的地址>:5432/postgres -user=postgres -password=pw migrate
bash deploy/db/compare-schema.sh rb-a rb-b
```
Expected: A 上所有分域脚本按序成功；比对退出 0，六项全部一致。`<rb-a 的地址>` 取 `docker inspect` 的容器 IP，容器与 Flyway 放同一 docker 网络。出现差异时修合并产物，不动 allenv 原文。一个脚本里先加枚举值、后一个脚本使用它，合并成单事务会失败，此时在合并产物里把该处拆开并记录。JobRunr 的表若由 JobRunr 启动时自建（见 `<JOBRUNR_TABLES>`），不在比对范围。

- [ ] **Step 3: 校验行级安全与索引未丢，并做反向验证**
对 B 执行：
```sql
SELECT c.relname FROM pg_class c JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname = 'tenant_id'
WHERE c.relkind = 'r' AND NOT (c.relrowsecurity AND c.relforcerowsecurity);
```
Expected: 空，除 `gac.furnace.tenant.ignore-tables` 里的表。再手工删掉合并产物里任一条 `CREATE POLICY` 重跑 B 与 `compare-schema.sh`，必须退出 1，确认后恢复。

- [ ] **Step 4: 替换 allenv 并全量测试**
Run:
```bash
git rm backend/app/src/main/resources/db/migration/allenv/V2*.sql
cp /tmp/V1__baseline.sql backend/app/src/main/resources/db/migration/allenv/V1__baseline.sql
MVN verify
```
Expected: BUILD SUCCESS，全部依赖迁移的测试在 V1 上通过，占位版本断言已改为 `1`。清理 `rb-a`、`rb-b` 容器与 `/tmp` 里的文件。

- [ ] **Step 5: 提交**
```bash
git add backend/app/src/main/resources/db/migration/allenv/V1__baseline.sql backend/app/src/test deploy/db/merge-migrations.sh deploy/db/compare-schema.sh docs/migration/deviations.md
git commit -m "refactor(db): 合并分域迁移脚本为单份 V1 并校验等价"
```

### Task 15: 补齐替代规则与对照表审计

**Files:**
- Modify: `backend/config/checkstyle.xml`、`backend/pom.xml`
- Create: `backend/app/src/test/java/com/ragagent/arch/CutoverRulesTest.java`、`docs/migration/guard-mapping.md`（1.2 的表原样落盘）

**Interfaces:**
- Consumes: 1.2 的表中标 N 的行，计划 01 的 `ArchitectureRulesTest`
- Produces:
  - `CutoverRulesTest` 五条规则：
    - `controllersReturnRestResponseOrStream`：`..controller..` 的公开方法返回 `RestResponse`、`SseEmitter`、`StreamingResponseBody`、`void` 之一，或在 `RESPONSE_EXEMPTIONS` 里。豁免只写在该常量，每条带中文理由，超过 10 条停下问用户（渠道回调要求纯文本回包是可预见的例外）
    - `controllersDoNotUseMappersOrEntities`
    - `retrievalAndAgentEnginesAreIndependent`：`com.ragagent.retrieval..` 与 `com.ragagent.agentengine..` 互不依赖，两者都可依赖 `modelruntime`
    - `interfacesOutsideApiAndSpiHaveAtLeastTwoImplementations`：排除 `..api..`、`..spi..`、`..mapper..`、`@org.mapstruct.Mapper`、`@FunctionalInterface`、注解类型与 framework
    - `noGoCompatUtilities`：类名与方法名不匹配 `trimGo`、`orGoZero`、`GoJsonBindError`、`.*Go(Zero|Json|Trim).*`，补上 spec 7.2 里计划 01 没覆盖的后半句
  - Checkstyle 新增：`UnusedImports`、`RedundantImport`、`AvoidStarImport`、`PackageDeclaration`；`RegexpSinglelineJava` 两条，一条拦日志传 `getMessage()`（`\blog(ger)?\.(trace|debug|info|warn|error)\(.*\.getMessage\(\)`），一条拦整行横线注释（`//\s*[-=─]{4,}\s*$`），两条都忽略 Javadoc 与字符串
  - Enforcer：`requireJavaVersion [21,22)`、`requireUpperBoundDeps`、`bannedDependencies`（`net.devh:*`、`org.springframework.boot:spring-boot-starter-webflux`）。`requireUpperBoundDeps` 首次红时在 `backend/pom.xml` 的 `dependencyManagement` 钉版本，不加豁免

- [ ] **Step 1: 写测试并反向验证规则会拦**
写出 `CutoverRulesTest` 的五条规则，先对现有主码运行 `MVN -pl app -am test -Dtest=CutoverRulesTest`。Expected: 通过，或只因真实违规而红。有真实违规就改代码，不降低规则，不扩大豁免。
再在 `backend/app/src/main/java/com/ragagent/zzprobe/` 临时放探针：返回 `String` 的控制器方法、引用 Mapper 的控制器、让 `retrieval` 包引用 `agentengine` 包的类、只有一个实现的接口、名为 `trimGo` 的方法，外加一个 50 行的方法、6 个参数的方法、`log.warn("x", e.getMessage())` 与一行 `// ----`。
Run: `MVN verify`
Expected: BUILD FAILURE，ArchUnit 五条与 Checkstyle 的 `MethodLength`、`ParameterNumber`、两条 `RegexpSinglelineJava` 逐条报红。确认后删除探针目录，重跑 `MVN verify`，Expected: BUILD SUCCESS，`backend/config/checkstyle-suppressions-engines.xml` 里 `<suppress ` 数为 0。

- [ ] **Step 2: 审计对照表**
把 1.2 的表写入 `docs/migration/guard-mapping.md`，对"新落点"列里每个测试名与规则名逐个 grep：
```bash
grep -rn "<落点名>" backend/*/src/test backend/config backend/pom.xml | head -1
```
Expected: 每个落点都有命中。没有命中的两种处理：现在补测试，或把该行改标为丢失并写理由。最终每个"丢失为是"的行都带理由。

- [ ] **Step 3: 提交**
```bash
git add backend/config/checkstyle.xml backend/pom.xml backend/app/src/test/java/com/ragagent/arch/CutoverRulesTest.java docs/migration/guard-mapping.md
git commit -m "test(arch): 补齐替代旧守卫的架构规则、Checkstyle 与 Enforcer"
```

### Task 16: 删除旧模块、守卫脚本与旧基线

**Files:**
- Delete: `contract/`、`platform/`、`domains/`、`boot/`、`agent-engine/`、`model-runtime/`、`retrieval-engine/`、`migrations/`、`otlp-proto/`（若任务 12 未删）、`docreader/`（若任务 5 未删）
- Delete: 1.1 表中"删除"的全部 `scripts/` 文件
- Modify: `pom.xml`（整体替换）、`.gitignore`、`scripts/ci-smoke.sh`、`scripts/test-summary.py`、`scripts/ci-log-annotate.py`

**Interfaces:**
- Consumes: 任务 15 全绿，旧系统副本与标签 `rebuild-freeze`
- Produces: 根 `pom.xml` 只含 `<modules><module>backend</module></modules>`，坐标 `com.ragagent:ragagent:0.1.0-SNAPSHOT`，无依赖管理与插件配置，`backend/pom.xml` 仍自成一体不继承它；`scripts/` 恰好 11 个跟踪文件

- [ ] **Step 1: 确认等价规则已生效且无旧引用**
Run:
```bash
MVN verify
test -d ../ragagent-legacy/.git && git -C ../ragagent-legacy describe --tags
grep -rnIE "(^|[^a-z-])(contract|platform|domains|boot)/" backend deploy frontend tests mcp-server services .github scripts docker-compose.yml --exclude-dir=node_modules --exclude-dir=target | head
grep -rn "jieba" backend --include=*.java -l | head -2
```
Expected: `BUILD SUCCESS`；第二条输出 `rebuild-freeze`；第三条为空，有输出先修引用；第四条决定 `gen-jieba-hmm.py` 的去留。不绿不删。

- [ ] **Step 2: 改写保留的 CI 辅助并删除**
`ci-smoke.sh`：起 `backend/app` 的 jar，等管理端口 readiness 为 UP，断言 `/v3/api-docs` 在默认配置下为 404，再停进程。`test-summary.py` 与 `ci-log-annotate.py` 的报告路径改为 `backend/*/target/surefire-reports`。再删除：
Run:
```bash
git rm -r contract platform domains boot agent-engine model-runtime retrieval-engine migrations
git rm scripts/check-*.py scripts/gen-arch-index.py scripts/gen-contract-consumers.py scripts/gen-domain-index.py scripts/gen-module-map.py scripts/gen-pipeline-map.py scripts/gen-port-map.py
git rm scripts/*.baseline.json scripts/capability-kinds.json scripts/run-guards.sh scripts/_method_lengths.py scripts/_source_roots.py
git rm scripts/audit-contract-fixtures.sh scripts/fix-javadoc-links.py scripts/migrate-domain.py scripts/normalize-blank-lines.py scripts/pkg-audit.py scripts/restyle-embeds.py scripts/route-recon.py scripts/refactor-harness.sh scripts/complexity-report.py scripts/method-length-report.py scripts/generate-api-docs.py
git rm scripts/acceptance.sh scripts/attachment-vision-check.sh scripts/dev-stale-check.sh
git ls-files scripts | wc -l
git ls-files scripts | grep -cE 'check-|baseline'
```
Expected: 第一个计数 11（保留 jieba 脚本时 12），第二个 0。jieba 无命中时再 `git rm scripts/gen-jieba-hmm.py`。

- [ ] **Step 3: 替换根 pom 与忽略文件，提交，干净克隆验证**
用 Edit 整体替换根 `pom.xml`；`.gitignore` 去掉旧模块条目，加 `services/docreader/proto/*_pb2*`、`frontend/**/dist`、`deploy/.build/`。
```bash
git add pom.xml .gitignore scripts/ci-smoke.sh scripts/test-summary.py scripts/ci-log-annotate.py
git commit -m "refactor: 删除旧模块、守卫脚本与旧迁移基线，根 pom 只聚合 backend"
git clone . "$(mktemp -d)/clean" && cd "$_" && mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -B verify
```
Expected: BUILD SUCCESS。克隆缺文件说明有未提交依赖，补齐后重做。删除类变更已由 `git rm` 暂存。

### Task 17: CI 重写

**Files:**
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: 前面各任务的命令
- Produces: 下表的 job 清单；保留 `ubuntu-24.04` 钉版与原注释记录的原因，action 版本沿用现有大版本

| job | 内容 | 触发 |
|---|---|---|
| `backend` | `mvn -B -f backend/pom.xml verify`，含 Checkstyle、Enforcer、ArchUnit、Modulith、Testcontainers，Redis 与 PostgreSQL 由 Testcontainers 提供；失败时跑 `ci-log-annotate.py`，总是跑 `test-summary.py` | push、PR |
| `api-drift` | 计划 01 已有，改为 pnpm 安装与 `pnpm --filter @ragagent/api-client run check:api` | push、PR |
| `frontend` | pnpm 安装，`pnpm -r type-check`、`pnpm -r test`、`pnpm -r build`，`check-i18n` | push、PR |
| `images` | 构建 server、console、widget 与数据库镜像，自检 jar 内含 `db/migration/allenv/V1__baseline.sql`，`nginx -t`，`smoke-roles.sh`，`check-env.sh`，数据库镜像 `smoke.sh` 与 `compare-schema.sh --self-test` | push、PR |
| `docreader` | 构建两个镜像，`pytest`，两个 `smoke.sh`，`check_licenses.sh`，`DocreaderClientIT`；另含 `mcp-server` 的 `pytest`（含对 `openapi.json` 的路径契约测试） | push、PR，路径含 `services/docreader`、`mcp-server` 或 `backend/` |
| `integration` | 检索引擎真实驱动 IT，Milvus 与 Elasticsearch，`-P<IT_PROFILE>`；沿用"发现 0 个 IT 类即失败"检查，路径改 `backend/retrieval-engine` | push、PR |
| `e2e` | 生产编排加 e2e 编排起全栈，跑 `tests/e2e`、`tests/api-smoke`、`tests/regression`，要求 0 skipped（条件页与未定位页除外） | main 推送、每日定时、手动；全栈起 8 个容器耗时长，不在每个 PR 上跑 |

- [ ] **Step 1: 写 workflow 并校验**
删除 `guards` job、ParadeDB 服务容器与灌迁移步骤、Weaviate、spotless 与 `ratchetFrom` 的说明、`fetch-depth: 0`、对 `boot/` 与旧根 `Dockerfile` 的引用、装 `redis-server` 的步骤（Redis 测试改用 Testcontainers，以 `backend` job 实测为准）。
Run: `docker run --rm -v "$PWD":/repo -w /repo rhysd/actionlint:latest -color`
Expected: 无错误。

- [ ] **Step 2: 在 PR 上跑通**
推送分支并开草稿 PR。Expected: 除 `e2e` 外所有 job 绿，`e2e` 手动触发一次也绿。推送由用户决定，未经确认不推；执行机无法推送时，本地逐 job 执行等价命令并把输出贴进 `progress.md`。

- [ ] **Step 3: 提交**
```bash
git add .github/workflows/ci.yml
git commit -m "ci: 只跑新结构，增 docreader、mcp-server、images 与 e2e job"
```

### Task 18: README 与文档

**Files:**
- Modify: `README.md`、`docs/28-部署基线.md`、`docs/architecture/overview.md`（重写，不超过 150 行）
- Modify（只加标注行）：其余引用旧结构的跟踪文档，共 60 个减去上面改写的，含 `docs/internal/**`
- Delete: `docs/architecture/{packages,domain-index,module-package-map,pipeline-map,port-map,qa-flow,ingest-flow,route-policy-map}.md`

**Interfaces:**
- Consumes: spec，`docs/migration/guard-mapping.md`，`deploy/ENVIRONMENT.md`
- Produces: 标注行 `> 本文描述 rebuild-freeze 标签之前的旧结构，新结构见 docs/architecture/overview.md 与 docs/optimization/specs/2026-11-09-architecture-rebuild-design.md。`

- [ ] **Step 1: 列出受影响文档**
Run:
```bash
git -c core.quotepath=off ls-files -z docs README.md | tr '\0' '\n' | grep -v '^docs/optimization\|^docs/migration' | tr '\n' '\0' \
  | xargs -0 grep -lE '(contract|platform|domains|boot)/|:(contract|platform|domains|boot)\b|check-[a-z-]+\.py|run-guards|8083'
```
Expected: 约 60 个文件。逐个判定改写或标注，默认标注。

- [ ] **Step 2: 改写 README、部署文档，标注旧文档，删生成文档**
README 只改：仓库结构树（对齐 spec 5.1）、构建与启动命令（`mvn -f backend/pom.xml`、`pnpm`、`docker compose`）、环境变量表（按 `ENVIRONMENT.md`）、守卫说明改为 Checkstyle 与 ArchUnit、docreader 指向 `services/docreader`、测试命令。致谢段保留，许可段加一句指向根 `NOTICE`。`docs/28-部署基线.md` 按新镜像清单与角色改写。对未改写的文档，在第一行插入标注行，其后空一行，可用系统临时目录里的小脚本批量插入，用完删除。

- [ ] **Step 3: 验证**
Run:
```bash
git -c core.quotepath=off ls-files -z docs README.md | tr '\0' '\n' | grep -v '^docs/optimization\|^docs/migration' | tr '\n' '\0' \
  | xargs -0 grep -lE '(contract|platform|domains|boot)/|:(contract|platform|domains|boot)\b' \
  | while read -r f; do head -1 "$f" | grep -q "rebuild-freeze" || echo "未标注 $f"; done
grep -nE "check-[a-z-]+\.py|run-guards|8083" README.md | wc -l
ls docs/architecture
```
Expected: 无"未标注"输出；README 命中数 0；`docs/architecture` 不再含被删的 8 份。再按 README 的构建与启动命令在干净克隆上原样执行一遍，必须跑通。

- [ ] **Step 4: 提交**
```bash
git add README.md docs/28-部署基线.md docs/architecture/overview.md docs/*.md docs/internal docs/architecture
git commit -m "docs: 更新 README 与架构概览，旧文档加标注，删除生成文档"
```

### Task 19: 第一阶段总验收

**Files:**
- Create: `docs/migration/acceptance.md`、`docs/migration/evidence/<序号>-<名称>.txt`（命令输出用 `tee` 落盘）

**Interfaces:**
- Consumes: 全部前序产物，旧系统副本 `../ragagent-legacy`
- Produces: `acceptance.md`，每项含命令、预期、实际输出摘要、证据文件、结论；末尾"整体代码审查"小节

- [ ] **Step 1: 在干净克隆上跑 spec 第 7 节八项**
| 项 | 命令 | 预期 |
|---|---|---|
| 1 无 contract 与禁用后缀 | `test ! -e contract && find backend -path '*/src/main/*' \( -name '*Ops.java' -o -name '*Support.java' -o -name '*Helper.java' -o -name '*Facade.java' \) \| wc -l`；`MVN -pl app -am test -Dtest=ArchitectureRulesTest` | 目录不存在，计数 0，测试绿 |
| 2 无 `@RequestBody String` 与 Go 工具 | `ArchitectureRulesTest#noRawRequestBodyString`、`CutoverRulesTest#noGoCompatUtilities`；`grep -rnE "trimGo\|orGoZero\|GoJsonBindError\|rawBody" backend --include=*.java \| wc -l` | 测试绿，计数 0 |
| 3 无手写 `tenant_id` 条件、全表行级安全、跨租户被拒 | `grep -rnE "tenant_id *(=\|in\|IN)" backend/app/src/main/java backend/app/src/main/resources/mapper \| wc -l`；Task 14 Step 5 的 SQL；`MVN verify -Dtest='<RLS_TEST_PATTERN>'` | 计数 0，SQL 无行，测试绿，跨租户用例数不少于租户表数 |
| 4 错误码有 i18n、文案七语种齐全 | `MVN verify -Dtest='<I18N_TEST_PATTERN>'`；每个 `*Code.java` 都有同域的 `*CodeMessagesTest`（shell 循环核对）；`grep -c "待人工校对" backend/app/src/main/resources/i18n/app-messages_*.properties` | 测试绿，枚举与测试一一对应，待校对计数为 0 |
| 5 前端类型全部来自 OpenAPI | `cd frontend && pnpm --filter @ragagent/api-client run check:api`；`grep -rlE "from 'axios'" frontend/apps frontend/packages --include=*.ts --include=*.vue \| wc -l`；`grep -rnE "^export interface \|^export type [A-Za-z0-9_]+ = \{" frontend/apps/*/src/api \| wc -l` | 无漂移，两个计数都为 0；命中项逐条改为引用生成类型，或登记 `deviations.md` 由用户确认 |
| 6 架构规则全为 Java 测试 | `git ls-files scripts \| grep -c 'check-'`；`ls backend/app/src/test/java/com/ragagent/arch` | 0；列出 `ArchitectureRulesTest`、`ModuleBoundaryTest`、`CodeQualityRulesTest`、`CutoverRulesTest` |
| 7 检索回归与冒烟 | `cd tests/regression && node record.mjs --adapter rebuild --out candidate.json && node compare.mjs --baseline baseline.json --candidate candidate.json --min-expected 1.0`；`cd tests/api-smoke && API_BASE=http://127.0.0.1:8000 npx playwright test` | `expectedRecall` 不低于基线；冒烟 7 passed，0 skipped，0 fixme |
| 8 界面端到端在新栈全绿 | 起 e2e 编排后 `cd tests/e2e && BASE_URL=http://127.0.0.1:8000 npx playwright test` | 0 failed；skipped 只含 `PAGES.md` 里的条件页与未定位页，且都有 `fixme` 理由并登记 |

项 4 的机器翻译草稿必须有人工校对记录才算通过，校对记录写在 `docs/migration/i18n-review.md`，由用户安排；计数非 0 时本项标"条件通过"并提醒用户。

补充项：

| 项 | 命令 | 预期 |
|---|---|---|
| Checkstyle 全绿与引擎豁免为空 | `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -B verify`；`grep -c "<suppress " backend/config/checkstyle-suppressions-engines.xml` | BUILD SUCCESS；计数 0 |
| 全仓抑制数 | `grep -rE '@SuppressWarnings\("checkstyle:' backend --include=*.java \| wc -l`；`CodeQualityRulesTest#checkstyleSuppressionsStayWithinBudget` | 不超过 10，测试绿 |
| 单份基线迁移 | `ls backend/app/src/main/resources/db/migration/allenv` | 只有 `V1__baseline.sql` |
| 旧结构清零 | `ls -d contract platform domains boot agent-engine model-runtime retrieval-engine otlp-proto docreader migrations 2>/dev/null \| wc -l` | 0 |
| 镜像可用 | `bash deploy/build-images.sh`；`bash deploy/smoke-roles.sh ragagent-server:ci`；`bash services/docreader/smoke.sh grpc ragagent/docreader:ci`；`bash deploy/db/smoke.sh ragagent/postgres:17-pgvector-pgroonga` | 六个镜像构建成功，三个冒烟通过 |
| 新旧界面一致 | 在 `../ragagent-legacy` 上跑同一套 `tests/e2e`，新旧各出 aria 快照目录后 `diff -r` | 差异只允许是错误提示措辞，逐条登记 |

- [ ] **Step 2: 整体代码审查与闸门判定**
对 `rebuild-freeze..rebuild` 的变更用 `code-review-mr` 技能做一次整体审查。变更量很大，按目录分批调用同一技能：`backend/framework`；`backend/{model-runtime,retrieval-engine,agent-engine}`；`backend/app` 按基础域、业务域、编排域三批；`frontend`；`services`、`mcp-server`、`deploy`、`.github`。每个发现在 `acceptance.md` 的"整体代码审查"小节登记一行：位置、问题、处置（修复提交哈希，或驳回及理由）。审查代理的输出当参考，误报结合上下文当场驳回，不直接传递。结论同步登记 `progress.md`。
`acceptance.md` 中所有项为通过，整体审查里没有未处置的高置信问题，才算第一阶段结束。任何一项不通过，修复后重跑该项并更新记录，不改验收标准。

- [ ] **Step 3: 提交**
```bash
git add docs/migration/acceptance.md docs/migration/evidence docs/migration/progress.md
git commit -m "docs(migration): 第一阶段验收记录与整体代码审查结论"
```

## 遗留事项

1. 路线图需回写：第 2 节 09 号范围补上前端工作区拆分、部署角色、可观测切换、mcp-server 破坏性变更；第 7 节环境清单补上 pnpm 9、`uv`、`pip-licenses` 与 `actionlint`；第 3.5 节的"单份 V1"补一句含种子 DML 与增量脚本 DDL、DML 分开的关系。单份 V1 含 DML，与用户全局的 DDL、DML 分开规则不一致，需用户核对。
2. 任务 2 的扫描若在 ebooklib 之外命中 AGPL 或 GPL，计划在该步停下，交用户决定，没有预设答案。
3. 国际化译文的人工校对不是代码任务，验收项 4 依赖用户安排，未完成时本项只能条件通过。
4. 计划 02 到 05、07、08 的文件在编写本计划时不存在，任务 1 的占位符是对它们产物的假设，名字不一致按偏离登记。
5. 旧系统的 `attachment-vision-check.sh` 随任务 16 删除，附件进视觉模型的复验没有替代用例，建议计划 08 的冒烟补一条。
6. widget 的 `build.assetsDir` 改名后，第三方站点缓存的旧 `embed.html` 会引用旧资源路径。系统未上线不处理，上线后要考虑缓存失效。

## 完成判据

对应路线图 09 号闸门，即 spec 第 7 节八项全部满足。以下命令全部通过：

```bash
# 构建与规则：Checkstyle、Enforcer、ArchUnit、Modulith、Testcontainers
mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -B verify

# 旧结构清零与脚本清零
ls -d contract platform domains boot agent-engine model-runtime retrieval-engine otlp-proto docreader migrations 2>/dev/null | wc -l   # 0
git ls-files scripts | wc -l                                                                                                         # 11，保留 jieba 脚本时 12
git ls-files scripts | grep -cE 'check-|baseline'                                                                                    # 0

# 迁移单份基线与质量门
ls backend/app/src/main/resources/db/migration/allenv                                                                                # 只有 V1__baseline.sql
grep -c "<suppress " backend/config/checkstyle-suppressions-engines.xml                                                              # 0

# 外围组件
cd frontend && pnpm install --frozen-lockfile && pnpm -r type-check && pnpm -r test && pnpm -r build
cd mcp-server && python -m pytest -q
cd services && uv run --project docreader pytest docreader/tests -q && bash docreader/scripts/check_licenses.sh
bash services/docreader/smoke.sh grpc ragagent/docreader:ci
bash deploy/smoke-roles.sh ragagent-server:ci
bash deploy/check-env.sh

# 全栈验收，证据落 docs/migration/evidence，结论落 docs/migration/acceptance.md
docker compose -f deploy/docker-compose.prod.yml -f deploy/docker-compose.e2e.yml --env-file deploy/env.prod.example up -d --wait
cd tests/e2e && BASE_URL=http://127.0.0.1:8000 npx playwright test
cd tests/api-smoke && API_BASE=http://127.0.0.1:8000 npx playwright test                       # 7 passed，0 skipped
cd tests/regression && node compare.mjs --baseline baseline.json --candidate candidate.json --min-expected 1.0
```

spec 第 7 节八项与任务 19 第 1 步的八行验收表一一对应，逐项对应的任务是：1 与 2 对应任务 15、16，3 对应任务 14 与各域行级安全测试，4 对应各域 `*CodeMessagesTest`，5 对应任务 7、9 与 CI `api-drift`，6 对应任务 15、16 与 1.2 对照表，7 与 8 对应任务 13、17 的 `e2e` job。
