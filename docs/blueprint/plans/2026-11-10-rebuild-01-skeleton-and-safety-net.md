# RagAgent 重建：骨架与安全网 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建出五模块新骨架（framework 的响应体、错误码、多租户与行级安全、多语言、序列化、Web、安全、租户客户端注册表与 OSS、Redis 锁，加 app 组合根、运行配置与架构规则测试）、自建 PostgreSQL 17 + pgvector + PGroonga 镜像，并在冻结点的旧系统上录出三类迁移安全网（按页面清单覆盖的界面端到端、检索回归基线、API 冒烟骨架），另完成 MCP SDK 与 Nimbus 两项开工前验证。

**Architecture:** 新代码独立于旧构建：`backend/pom.xml` 是自成一体的 Maven 聚合，不继承根 pom，用 Boot 3.5.16 BOM；旧模块保持 Boot 3.3.13 继续可运行，作为界面录制与检索基线的被测系统。framework 提供团队 Java 习惯的基础设施，所有配置类注册为自动配置，app 靠自动配置装入，不靠包扫描。app 目前只有组合根、运行配置与架构规则，业务域在后续计划里逐域迁入。

**Tech Stack:** JDK 21、Spring Boot 3.5.16、MyBatis-Plus 3.5.x、Flyway、Spring Modulith 1.4.x、ArchUnit、Testcontainers PostgreSQL、Sa-Token、Redisson、MapStruct、springdoc、Playwright、openapi-typescript、openapi-fetch。

**规格来源**: `docs/optimization/specs/2026-11-09-architecture-rebuild-design.md`

**Spec:** `docs/optimization/specs/2026-11-09-architecture-rebuild-design.md`

**路线图:** `docs/optimization/plans/2026-11-10-rebuild-00-roadmap.md`。全局约定以它为准，与本计划冲突时按路线图执行并登记偏离。

## Global Constraints

- JDK 21；Spring Boot 3.5.16；Jackson 2 版本随 Boot BOM（3.5.16 为 2.21.4），不单独覆盖。spec §3 约束第 2 条写「Jackson 2.19」，与 D16 的 2.21.4 冲突；以 D16 为准，本节即结论。
- Java 缩进用 tab，续行缩进一个 tab，行宽 140。
- import 顺序：java → javax → org → com → 其他 → static import，各组间空行，禁止通配符导入。
- 注释统一中文、独立行；public 类必须有类级 Javadoc，public 方法必须有方法 Javadoc；`@author jiangguifan`。
- 出现 2 次以上的魔法字符串（算法类型、请求头名、Redis 键前缀、配置 key、默认值）收口为常量或枚举。
- Entity 用 `@Getter`/`@Setter`，不用 `@Data`；时间字段一律 `OffsetDateTime`（库中 `timestamptz`）。
- 逻辑删除统一用 `deleted_at` 的哨兵值 `1970-01-01 00:00:00+00` 表示未删除，删除时写 `now()`。唯一索引必须包含 `deleted_at`，否则软删后无法重建同键。禁用 `NULL` 作为「未删除」标记：PostgreSQL 唯一索引把 `NULL` 视为互不相同。
- Repository 用 `@Service`，不用 `@Repository`。
- 主键 bigint 雪花 ID（MyBatis-Plus `ASSIGN_ID`），对外序列化为字符串。
- 配置前缀统一 `gac.furnace.*`，字段 kebab-case，布尔开关以 `enabled` 结尾（spec 5.3 要求与 furnace 一致）。具体配置 key 名由本计划钉住，不是从 furnace 逐项核对得来。
- **错误码两段式**：4 位 `CommCode`（框架通用）+ 10 位业务码（各域自定义），两套并存，与 furnace 一致。10 位码为 `1` + 系统号 `001` + 3 位模块号 + 3 位序号，模块号见路线图 3.3，framework 为 `001`。HTTP 状态码不在 `Code` 里推导，由 `Code.httpStatus()` 给出默认值并允许枚举覆盖，见 Task 2。
- **租户 ID 只来自登录会话。** 请求头 `X-Tenant-Id` 默认不被信任，仅 `gac.furnace.tenant.trust-header=true`（测试与本地调试）时才读取。
- **framework 配置类一律注册为自动配置**（`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`），不加 `@Component` 靠包扫描装配。Bean 全部在配置类里用 `@Bean` 声明。
- **测试装配约定。** framework 不设全局测试宿主应用。每个测试类用 `@SpringBootTest(classes = {需要的配置类, 本测试自带的夹具配置})` 显式声明所需装配。夹具类（控制器、实体、Mapper、配置）放在 `backend/framework/src/test/java/testfixture/framework/<能力>/`，包根不在 `com.ragagent` 之下，避免被 app 的包扫描与 ArchUnit 扫到。公共基类 `PostgresContainerTest` 与 `FrameworkWebTest` 放在 `testfixture.support`，随 framework 的 test-jar 提供给 app，test-jar 只打包 `testfixture/support/**`。
- **Sa-Token 的 token 读取**：请求头 `Authorization`，值必须带 `Bearer ` 前缀。配置 `token-prefix=Bearer` 后，不带前缀的裸 token 读不到，按未登录处理。
- 事务内不做外部调用（HTTP、MQ、LLM、向量库）。
- **代码质量硬门**按路线图 3.11：方法 40 行、类 400 行、参数 5 个、圈复杂度 10、嵌套 3 层由 Checkstyle 在 `validate` 阶段拦截（Task 11c），Spring Bean 构造器参数不超过 7 个由 ArchUnit 守，抑制总数不超过 10 处。本计划已写的 framework 主码必须满足，超标先拆方法，不加抑制。
- **Flyway 脚本文件名**版本号与描述之间用两个下划线：`V{yyyyMMdd}_{两位序号}__{DDL|DML}_rebuild_{域名}_{描述}_jiangguifan.sql`。路线图 3.5 写的单下划线在 Flyway 里是无效文件名，已在 Task 11b 说明并登记偏离。
- 不拷贝 furnace 源码。本仓 origin 是公开 MIT 仓库，furnace 是内部基座，只能按其文档写的 API 形状重新实现。
- Maven 命令统一带 `-s C:/Users/jiangguifan/.m2/settings-coding-uat.xml`。执行机若该文件不存在或 localRepository 不同，在 Task 1 Step 0 用 `test -f` 确认后替换全文该参数，并登记到 `docs/migration/deviations.md`。
- **所有 `-pl <module>` 的命令必须带 `-am`。** `backend` 是独立聚合，兄弟模块不 install 到本地仓库时，不带 `-am` 会报 `Could not resolve dependencies`。
- **`JAVA_HOME` 必须指向 JDK 21。** 本机默认可能是 JDK 11，`release 21` 会编译失败。每条 Maven 命令前确认 `mvn -v` 显示 Java 21；不确定时在命令前加 `export JAVA_HOME="<jdk21 路径>"`。
- `git add` 只写显式路径，禁止 `git add -A` / `git add .`。
- 旧模块（`contract/`、`platform/`、`domains/`、`boot/`、三个引擎）在本次计划内不改业务代码，保持 Boot 3.3.13 可构建可运行。根 pom 的 `<modules>` 不列 `backend/`，两套构建互不感知。
- 每个 Task 完成后在 `docs/migration/progress.md` 追加一行，与计划不一致的决定写入 `docs/migration/deviations.md`，规则见路线图 3.10。

## Review Focus

以下七类输入/场景 spec 没有逐条写测试，但实现时最容易出错。每条都在对应任务的测试里有断言。

1. 请求缺失或带非法租户头。期望：返回明确的「租户上下文缺失」错误码与可读文案，不是 PostgreSQL 的 `invalid input syntax for type bigint: ""`。归属 Task 5、Task 7。
2. `Accept-Language` 是未支持语种（如 `fr-FR`）或完全缺失。期望：回落 `zh-CN`，任何时候都不返回 null 或 key 原文。归属 Task 4。
3. 浏览器跨域预检 `OPTIONS`。期望：不被鉴权拦截，返回正确的 CORS 响应头。归属 Task 7、Task 8。
4. 超过 2^53 的 bigint 主键。期望：JSON 序列化为字符串，前端不丢精度。归属 Task 3。
5. 后台任务（worker 角色）没有 HTTP 请求上下文时如何获得租户。期望：只能由调用方显式设置，未设置时立刻失败而不是查全库。归属 Task 5。
6. 伪造 `X-Tenant-Id` 请求头。期望：默认配置下被忽略，租户只来自登录会话。归属 Task 5、Task 8。
7. 不带 `Bearer ` 前缀的裸 token。期望：按未登录返回 `4402`，不是 500，也不是放行。归属 Task 8。

---

## 偏离与待定（先看这一段）

计划范围来自 spec 第 6 节第 1、2 步与 D26、D27，另加自建数据库镜像（Task 18）与迁移登记（Task 0）。以下几处是我在本计划内做出的取舍，执行时不要自行扩大：

1. **前端 workspace 拆分推迟。** spec 5.1 的目标是 `frontend/apps/console` + `apps/widget` + `packages/`，包管理用 pnpm workspace。本计划只建立 `frontend/packages/api-client`（类型生成流水线与 `createApiClient` 传输），用 npm；不移动现有 `frontend/src`，也不改 `frontend/package-lock.json`。把 `@ragagent/api-client` 接进 `frontend/package.json` 由计划 03 的第一个前端任务做。原因：旧系统必须保持可运行以供界面录制，移动会同时改 Dockerfile、nginx 与 CI。`apps/` 拆分与 pnpm 迁移放在挂件改造计划里。
2. **本计划自建数据库镜像。** spec 5.9 要求 PostgreSQL 17 + pgvector + PGroonga。Task 18 产出 `deploy/db/Dockerfile`、构建脚本与 CI 用法。没有这个镜像，framework 的 RLS 集成测试、检索回归对比、以及后续所有域的表结构验证都跑在错误的数据库上。PGroonga 不在 Debian 或 PGDG 官方源，构建依赖 `packages.groonga.org`。
3. **API 冒烟用例先红后绿。** 6 条流程的端点属于新 API，尚未实现。本计划把端点定下来写成用例并标 `test.fixme()`，各域迁入时逐个去掉标注转绿。另加一条立刻可跑的新骨架鉴权用例（`skeleton-auth`），本计划完成时它必须是绿的。
4. **三个引擎模块只建骨架。** `backend/model-runtime`、`retrieval-engine`、`agent-engine` 本次只有 `pom.xml` 与 `package-info.java`，声明依赖方向。代码改包名搬入是计划 02。检索引擎要用的数据库镜像在本计划里做完（Task 18）。
5. **framework 能力的取舍。** 已做：响应体与错误码、Jackson 与脱敏、多语言、多租户与行级安全、MyBatis 扩展、Web、Sa-Token 三账号、租户客户端注册表与 OSS、分布式锁。本计划不做：
   - **logback 脱敏 Appender**：放到第一个业务域迁入时，与该域真实日志一起做。
   - **业务异常码转换器**：已有 `CodeCache` 承担反查，spec 里的「转换器」在这个设计下没有独立职责。
   - **两级缓存与 Redis 失效广播总线**：租户客户端注册表已做（Task 9），「配置变更即生效」用「每次先查一次轻量版本号」实现，不依赖广播。广播是后续的优化项，不在首批域迁入的路径上。
   - **OIDC 客户端**：Task 17 只做 Nimbus 可行性核实；真正实现 OIDC 登录在 auth 域迁入计划里。`mcp` 的 OAuth 同属那一批。
   - **配置覆盖顺序**（yml、`system_settings`、租户配置）：framework 只提供注册表与 `TenantConfigSource` 接口，分层合并由 system 域在计划 03 实现。
6. **main 分支冻结登记在 Task 0 落地。** 建 `docs/migration/frozen-bugs.md`、`progress.md`、`deviations.md`，格式见路线图。

存储后端的五种实现（spec 5.3）也在后续计划：本计划只做 `S3OssTemplate` 与客户端注册表，阿里云 OSS、腾讯云 COS、火山 TOS 都兼容 S3 协议，可由同一实现覆盖；本地目录存储等 storage 域迁入时补。

其余几处 spec 未定、本计划钉住的值，后续计划可改但要同步改这里：

- 业务错误码沿用 furnace 规则：10 位，`1` + 3 位系统号（本项目 `001`）+ 3 位模块号 + 3 位序号。framework 自身的两个码是 `FrameworkCode.TENANT_CONTEXT_MISSING = 1001001001` 与 `TENANT_HEADER_INVALID = 1001001002`。4 位 `CommCode` 与 10 位业务码并存，`Code.httpStatus()` 分别处理两套首字符语义。
- framework 的 Jackson 时间序列化用 ISO-8601 带偏移（`OffsetDateTime.toString()` 的默认形状），不自定义格式。
- `sys_dict`、`sys_config` 这类全局表放进 `gac.furnace.tenant.ignore-tables`；其余业务表一律开行级安全。
- 行级安全策略统一写成 fail-closed：`tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint OR current_setting('app.tenant_id', true) = 'system'`。**禁止**用「会话变量为空则放行」：PostgreSQL 的 `SET LOCAL` 结束后，池化连接上 `current_setting` 返回空串而不是 NULL，写成空则放行等于给这条连接永久开全租户权限。系统通道用显式哨兵 `'system'`。
- 控制台里的问答、会话、记忆、收藏接口归 `/api/admin/v1`，`/api/v1` 只给访客身份（路线图 3.6）。
- 匿名端点（登录、访客换 token、挂件引导）统一登记在 app 的 `application.yml` 的 `gac.furnace.security.permit-paths` 里，域计划追加。
- 界面端到端的判据用 Playwright aria 快照加文本断言，不做像素截图（路线图第 9 节第 5 条）。

`gac.furnace.*` 下的具体配置 key 名是本计划按 furnace 文档风格钉住的，不是逐项从 furnace 源码核对得来。执行时若发现某个 key 与 furnace 实际不一致，改 key 名不影响任何设计意图，但要同步改本节与对应 Task。

## 与初稿的差异

本次修订相对初稿的改动，方便核对：

| 编号 | 改动 |
|---|---|
| R0 | 新增 Task 0：冻结点标签 `rebuild-freeze`、分支 `rebuild`、迁移登记三文件、旧系统 worktree。Task 13、14 在该 worktree 起旧系统。规格来源改为路线图规定格式 |
| R1 | Task 1 的 `PostgresContainerTest` 不再引用 `TenantAwareJdbcTemplate`，该字段在 Task 5 加入基类。Task 2 不再重复创建夹具 |
| R2 | 废除全局 `TestApplication`。每个测试显式声明装配，framework 配置类注册为自动配置，test-jar 只含 `support` 夹具，夹具类放 `testfixture.*` 包 |
| R3 | Task 6 的提交不再包含 `GlobalExceptionHandler`，唯一键冲突的异常处理兜底挪到 Task 7 |
| R4 | Task 8 的测试请求头改为 `Bearer <token>`，新增裸 token 用例；未匹配路由 fail-closed，非 `/api` 路径由 `permit-paths` 放行 |
| R5 | 租户来源改为登录会话。新增 `TenantResolver`，`trust-header` 默认 false，`TenantContextMissingException` 继承业务异常 |
| R6 | 新增 `FrameworkCode`，Task 4 补两个码的七语文案与文件完整性测试，Task 7 补 HTTP 级租户缺失测试 |
| R7 | Task 9 改为泛型 `TenantClientRegistry` 加 OSS 特化，修正 `OssConfig` 构造参数不一致，补 Caffeine 依赖，用轻量 `version()` 查询代替广播 |
| R8 | Task 10 用 `AspectJProxyFactory` 让 `@Around` 路径真正被测，补拿不到锁的用例，修复静态计数不重置 |
| R9 | Task 11 删除 `noGetenv`、`noFieldInjection`，新增域级别依赖方向规则与域包归类校验 |
| R10 | 新增 Task 11b：app 运行配置、dev profile、README、`AppBootsTest`、Flyway 占位迁移与角色初始化脚本 |
| R11 | Task 12 的 `OpenApiExportTest` 继承 Postgres 基类，新增 `createApiClient` 与三个单测，红绿循环与文件路径对齐 |
| R12 | Task 13 重写为页面覆盖版：先盘点页面清单，按域标签分批录制，判据用 aria 快照 |
| R13 | Task 14 基线比对键改为内容哈希，不再用 chunk ID |
| R14 | Task 15 的 `skeleton-auth` 改测 `GET /api/admin/v1/auth/me`，控制台问答路径改到 `/api/admin/v1` |
| R15 | Task 18 的基础镜像改 `postgres:17-trixie`，补 Groonga apt 源，补回退入口；不改根 `docker-compose.yml` 与 CI 的 `backend` job（旧系统依赖 ParadeDB），新栈开发库用 `deploy/db/docker-compose.yml` |
| R16 | 本节与 Review Focus 同步更新 |
| R17 | 完成判据与 spec 第 7 节对照表同步更新，Task 总数 21（新增 Task 0、Task 11b、Task 11c） |
| R18 | 新增 Task 11c：Checkstyle 尺寸硬门、抑制预算、构造器参数规则，Task 11 的后缀规则扩展到 Manager 与 Common（路线图 3.11） |
| R19 | Flyway 文件名改为双下划线（Flyway 规则），与路线图 3.5 的单下划线写法不同，已登记偏离 |
| R20 | 新增 `TenantFilterConfig`，把租户过滤器注册与依赖数据库的 `TenantLineConfig` 拆开，Task 8 的租户来源测试不需要数据库 |

---

## 文件结构

```
backend/
├── pom.xml                                  独立聚合（不继承根 pom），Boot 3.5.16 BOM
├── framework/
│   ├── pom.xml                              含 test-jar（只打包 support）
│   ├── src/main/java/com/ragagent/framework/
│   │   ├── FrameworkMarker.java             Modulith 模块占位类型
│   │   ├── core/constant/{Code,CommCode,FrameworkCode}.java
│   │   ├── core/exception/{BusinessRuntimeException,CodeCache}.java
│   │   ├── core/i18n/{I18nUtil,AcceptLanguageLocaleResolver,FrameworkI18nConfig}.java
│   │   ├── core/resp/{RestResponse,RestResponseBuilder,PageData}.java
│   │   ├── core/json/{JacksonConfig,LongToStringSerializer,DesensitizeModule,SlideDesensitizationTypeEnum,RegexDesensitizationTypeEnum}.java
│   │   ├── core/annotation/{JsonSlideDesensitize,JsonRegexDesensitize}.java
│   │   ├── tenant/{TenantContextHolder,IgnoreTenant,IgnoreTenantAspect,TenantResolver,MyTenantFilter,TenantLineConfig}.java
│   │   ├── tenant/{TenantContextMissingException,RowLevelSecurityInterceptor,TenantProperties,TenantAwareJdbcTemplate,TenantFilterConfig}.java
│   │   ├── tenant/{TenantConfigSource,TenantClientRegistry}.java
│   │   ├── mybatis/{MybatisFrameworkConfig,FrameworkMetaObjectHandler,EncryptTypeHandler,Encrypted,EncryptKeyValidator}.java
│   │   ├── mybatis/{SqlTraceInnerInterceptor,MybatisConstraintInterceptor}.java
│   │   ├── web/{WebMvcConfig,CorsConfig,SecurityHeaderFilter,RepeatableReadRequestFilter,TraceContextFilter,FilterOrders}.java
│   │   ├── web/error/GlobalExceptionHandler.java
│   │   ├── security/{SaTokenConfig,SecurityConstants,AdminStpLogic,VisitorStpLogic,OpenStpLogic,SaRouteRules}.java
│   │   ├── security/{SaTokenAccountProvider,SaTokenTenantResolver}.java
│   │   ├── oss/{OssTemplate,OssClients,OssConfig,S3OssTemplate}.java
│   │   └── redis/{DistributedLock,DistributedLockAspect,DistributedLockKeyResolver,DefaultDistributedLockKeyResolver,RedissonConfig}.java
│   ├── src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   ├── src/main/resources/i18n/messages{,_zh_CN,_en_US,_zh_HK,_es_ES,_pt_BR,_ru_RU,_th_TH}.properties
│   ├── src/test/java/testfixture/support/{PostgresContainerTest,FrameworkWebTest}.java    进入 test-jar
│   ├── src/test/java/com/ragagent/framework/…                                              各能力用例
│   └── src/test/java/testfixture/framework/…                                               夹具，不进 test-jar
├── config/{checkstyle.xml,checkstyle-suppressions-engines.xml}    Task 11c
├── model-runtime/                           本次只建骨架，代码搬入见计划 02
├── retrieval-engine/                        同上
├── agent-engine/                            同上
└── app/
    ├── pom.xml
    ├── scripts/dev-db.sh                    本地起开发库
    ├── README.md                            本地运行说明
    ├── src/main/java/com/ragagent/{RagAgentApplication.java,config/EngineAssemblyConfig.java}
    ├── src/main/resources/{application.yml,application-dev.yml,application-openapi.yml}
    ├── src/main/resources/db/migration/allenv/V20261110_01__DDL_rebuild_framework_placeholder_jiangguifan.sql
    └── src/test/java/com/ragagent/{arch,openapi}/、AppBootsTest、RagAgentApplicationTests    arch 含 ArchitectureRulesTest、ModuleBoundaryTest、CodeQualityRulesTest
deploy/db/                                   自建数据库镜像（PG17 + pgvector + PGroonga）与角色初始化脚本
tests/
├── e2e/                                     Playwright 界面端到端（PAGES.md 为页面清单，测旧系统与新系统）
├── api-smoke/                               Playwright API 冒烟（先红后绿）
└── regression/                              检索回归集录制与对比
frontend/packages/api-client/                openapi-typescript 生成产物、createApiClient 与生成脚本
docs/migration/                              冻结点后的迁移登记（Task 0）
docs/optimization/spikes/                     两个验证工程与结论文档
```

---

### Task 0: 冻结点、rebuild 分支与迁移登记

**Files:**
- Create: `docs/migration/frozen-bugs.md`
- Create: `docs/migration/progress.md`
- Create: `docs/migration/deviations.md`
- Add（提交已存在的文件）: `docs/optimization/specs/`、`docs/optimization/plans/`、`docs/optimization/spikes/`

**Interfaces:**
- Consumes: 无
- Produces:
  - 标签 `rebuild-freeze`，指向 main 的冻结点提交
  - 分支 `rebuild`，此后所有计划的提交都进这个分支
  - 旧系统只读副本 `../ragagent-legacy`（`git worktree`，相对仓库根的上一级目录），Task 13、14 在这里起旧系统
  - 三个登记文件，格式固定，后续所有计划按路线图 3.10 追加

- [ ] **Step 1: 确认前置**

Run:
```bash
git branch --show-current
git status --short
test -f docs/optimization/specs/2026-11-09-architecture-rebuild-design.md && echo "spec OK"
ls docs/optimization/plans
```
Expected: 当前分支 `main`；`spec OK`；`plans` 下有 `2026-11-10-rebuild-00-roadmap.md` 与 `2026-11-10-rebuild-01-skeleton-and-safety-net.md`。允许存在、但不提交的无关未跟踪项：`.codegraph/`、`docs/architecture/`。

若 spec 或计划文件不存在，说明执行机没有拿到它们，停下来问用户，不要自己重写。若当前分支不是 `main`，停下来问用户。

- [ ] **Step 2: 打冻结标签并建分支**

Run:
```bash
git tag --list rebuild-freeze
git tag rebuild-freeze
git switch -c rebuild
git branch --show-current
```
Expected: 第一条命令无输出（标签尚不存在）；最后一条输出 `rebuild`。若第一条已有输出，说明冻结点已经打过，停下来问用户，不要移动标签。

- [ ] **Step 3: 写三个登记文件**

`docs/migration/frozen-bugs.md`：标题、一段说明（main 冻结后只修阻断性 bug，每次修复追加一行，域计划的第一个任务读取本文件并把本域条目带入）、表头 `| 日期 | 提交哈希 | 涉及域 | 描述 |`。表体留空。

`docs/migration/progress.md`：标题、一段说明（每份计划开始前写一行开工记录，每个任务完成后追加一行，计划 03 之后的域计划另在“白名单”小节登记 `@IgnoreTenant` 的使用位置）、表头 `| 计划 | 任务 | 提交哈希 | 日期 | 备注 |`、另有二级标题 `## IgnoreTenant 白名单`，表头 `| 计划 | 类 | 原因 |`。

`docs/migration/deviations.md`：标题、一段说明（与计划或 spec 不一致的决定逐条登记，影响后续计划的在同一提交里改掉受影响的计划文本）、表头 `| 计划 | 任务 | 原计划 | 实际做法 | 原因 | 影响后续计划 |`。

- [ ] **Step 4: 提交计划文件与登记文件**

Run: `git status --short`
Expected: 只出现 `?? .codegraph/`、`?? docs/architecture/`（若存在）、`?? docs/optimization/`、`?? docs/migration/` 这几类。出现别的路径时先弄清来源再继续。

Run:
```bash
git add docs/optimization docs/migration
git commit -m "docs: 重建计划、规格与迁移登记文件"
git log -1 --format=%h
```
Expected: 提交成功，打印短哈希。

- [ ] **Step 5: 取旧系统只读副本**

Run:
```bash
test -e ../ragagent-legacy && echo "目录已存在，停下问用户" || git worktree add ../ragagent-legacy rebuild-freeze
git worktree list
test -f ../ragagent-legacy/pom.xml && echo "legacy OK"
```
Expected: `git worktree list` 列出两行，`legacy OK`。该目录是冻结点的只读副本，后续只在里面起旧系统、读旧代码，不在里面改文件、不提交。

- [ ] **Step 6: 登记 Task 0**

在 `docs/migration/progress.md` 表体追加一行：计划 `01`、任务 `0`、Step 4 的提交哈希、当天日期、备注写“冻结点 rebuild-freeze”。

```bash
git add docs/migration/progress.md
git commit -m "docs(migration): 登记计划 01 Task 0"
```

推送分支与标签（`git push -u origin rebuild`、`git push origin rebuild-freeze`）会影响共享状态，是否推送由用户决定，未经确认不推。

---

### Task 1: backend 独立聚合与最小可启动应用

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/framework/pom.xml`
- Create: `backend/model-runtime/pom.xml`
- Create: `backend/retrieval-engine/pom.xml`
- Create: `backend/agent-engine/pom.xml`
- Create: `backend/model-runtime/src/main/java/com/ragagent/modelruntime/package-info.java`
- Create: `backend/retrieval-engine/src/main/java/com/ragagent/retrieval/package-info.java`
- Create: `backend/agent-engine/src/main/java/com/ragagent/agentengine/package-info.java`
- Create: `backend/framework/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `backend/framework/src/test/java/testfixture/support/PostgresContainerTest.java`
- Create: `backend/app/pom.xml`
- Create: `backend/app/src/main/java/com/ragagent/RagAgentApplication.java`
- Create: `backend/app/src/main/resources/application.yml`
- Create: `backend/app/src/test/java/com/ragagent/RagAgentApplicationTests.java`
- Modify: 无（旧根 pom 不动）

**Interfaces:**
- Consumes: Task 0 的 `rebuild` 分支
- Produces:
  - 聚合坐标 `com.ragagent:ragagent-backend:0.1.0-SNAPSHOT`（pom），子模块 `framework`、`model-runtime`、`retrieval-engine`、`agent-engine`、`app`，各子模块 `<version>` 写 `0.1.0-SNAPSHOT`，不用 `${revision}`
  - `com.ragagent.RagAgentApplication`（`@SpringBootApplication` 主类，包 `com.ragagent`）
  - `testfixture.support.PostgresContainerTest`：抽象基类，`@Testcontainers`，静态 `PostgreSQLContainer<?> POSTGRES`，`@DynamicPropertySource` 注入 `spring.datasource.url/username/password`，`@Autowired protected DataSource dataSource`、`@Autowired protected ApplicationContext context`，类上带 `@ImportAutoConfiguration(DataSourceAutoConfiguration.class)`。**此时不含任何 jdbc 模板字段**，Task 5 才把 `TenantAwareJdbcTemplate jdbc` 与数据源角色拆分加进来
  - framework 的 test-jar，只打包 `testfixture/support/**`
  - `AutoConfiguration.imports` 文件，此时只有一行注释；之后每个 Task 在文件末尾追加自己的配置类全限定名
  - Boot 版本属性名 `spring-boot.version`，值 `3.5.16`

- [ ] **Step 0: 确认构建参数**

Run:
```bash
test -f C:/Users/jiangguifan/.m2/settings-coding-uat.xml && echo "settings OK" || echo "settings 缺失，后续命令去掉 -s 参数或换成实际路径，并登记到 deviations.md"
mvn -v
docker info >/dev/null && echo "docker OK"
node -v
python --version
```
Expected: `mvn -v` 输出的 `Java version` 是 **21**。若是 11 或其它版本，先把 `JAVA_HOME` 指到 JDK 21 再继续，`release 21` 在低版本 JDK 上会直接编译失败。Node 主版本不低于 22。路线图第 7 节的其余环境项也在这一步一起自检。

- [ ] **Step 1: 写失败测试 `RagAgentApplicationTests`**

```java
@SpringBootTest
class RagAgentApplicationTests extends PostgresContainerTest {

	@Test
	void contextLoadsOnPostgres17() throws Exception {
		try (Connection c = POSTGRES.createConnection("")) {
			assertThat(c.getMetaData().getDatabaseMajorVersion()).isEqualTo(17);
		}
	}
}
```

`PostgresContainerTest` 用 `@Testcontainers` 与 `@DynamicPropertySource` 把容器 URL 注入 `spring.datasource.*`，库名 `ragagent_test`。

镜像用本计划自建的 `ragagent/postgres:17-pgvector-pgroonga`（Task 18 产出）。**Task 1 执行时它还不存在**，所以本步骤先用占位常量：

```java
// Task 18 完成后改成自建镜像名；此处用上游镜像让 Task 1 能独立跑通
private static final String PG_IMAGE = "pgvector/pgvector:pg17";
```

Task 18 完成时把这一行改成 `docker.io/ragagent/postgres:17-pgvector-pgroonga`，并全局搜索确认没有别处硬编码。这样 Task 1 不被 Task 18 阻塞，切换本身由 Task 18 的验证步骤守住。

- [ ] **Step 2: 运行，确认失败**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl app -am test -Dtest=RagAgentApplicationTests`
Expected: FAIL，`No POM in this directory` 或找不到 parent。`-am` 在这里不会掩盖失败，backend 聚合本身还不存在。

- [ ] **Step 3: 写五个 pom 与自动配置清单**

`backend/pom.xml` 要点：

- `<groupId>com.ragagent</groupId>`、`<artifactId>ragagent-backend</artifactId>`、`<version>0.1.0-SNAPSHOT</version>`、`<packaging>pom</packaging>`
- **不写 `<parent>`**，因此不继承根 pom 的 Boot 3.3.13
- `<modules>` 列五个：`framework`、`model-runtime`、`retrieval-engine`、`agent-engine`、`app`
- `dependencyManagement` 只 import `spring-boot-dependencies:3.5.16`
- `properties` 钉住：`mybatis-plus.version 3.5.16`、`spring-modulith.version 1.4.14`、`springdoc.version 2.8.17`、`archunit.version 1.3.0`、`sa-token.version 1.45.0`、`redisson.version 3.52.0`、`mapstruct.version 1.6.3`
- 任一版本在镜像源解析失败时，改为该库最近的稳定版，并登记到 `deviations.md`
- `maven-compiler-plugin` 沿用根 pom 配置：`release 21`、`parameters true`、UTF-8
- `spotless-maven-plugin` 用与根 pom 相同的 `importOrder`（`\#,`）、`removeUnusedImports`、`trimTrailingWhitespace`、`endWithNewline`，但**不配 `ratchetFrom`**（新代码全量检查）
- `maven-surefire-plugin` 的 `argLine` 用 `@{argLine}`

三个引擎模块本次只建骨架：`pom.xml` 声明依赖方向、`src/main/java/com/ragagent/<engine>/package-info.java` 写包的职责。代码搬入放在计划 02。

`backend/framework/pom.xml`：`spring-boot-starter-web`、`starter-validation`、`starter-data-redis`、`mybatis-plus-spring-boot3-starter`、`mybatis-plus-jsqlparser`（3.5.9 起租户插件依赖的 jsqlparser 拆成独立依赖）、`spring-boot-starter-aop`、`sa-token-spring-boot3-starter`、`redisson-spring-data-35`、`caffeine`、`mapstruct`、`software.amazon.awssdk:s3`、`testcontainers`（`postgresql` 与 `junit-jupiter` 模块，MinIO 与 Redis 用 `GenericContainer`，不需要额外模块）（test）、`archunit-junit5`（test）。另外加 `maven-jar-plugin` 的 `test-jar` execution，`<includes>` 只放 `testfixture/support/**`，让 app 能复用测试夹具。

`backend/model-runtime/pom.xml`：只依赖 `framework`。

`backend/retrieval-engine/pom.xml`：依赖 `framework`、`model-runtime`（spec 9 第 1 条已定，`retrieval-engine` 直接依赖 `model-runtime`）。

`backend/agent-engine/pom.xml`：依赖 `framework`、`model-runtime`。

`backend/app/pom.xml`：依赖 `framework`、`agent-engine`、`retrieval-engine`、`framework` 的 `test-jar`（scope `test`）、`spring-boot-starter-actuator`、`flyway-core`、`flyway-database-postgresql`、`postgresql`、`springdoc-openapi-starter-webmvc-api`、`spring-modulith-starter-core`、`spring-modulith-starter-test`（test）、`spring-boot-starter-test`（test）、`testcontainers-postgresql`（test）。

`AutoConfiguration.imports` 内容只有一行 `# framework 自动配置清单，每个 Task 在末尾追加自己的配置类`。

- [ ] **Step 4: 写主类与配置**

`RagAgentApplication` 加 `@Modulithic(systemName = "ragagent", sharedModules = "framework")`（Modulith 依赖此时已在 classpath）。

`application.yml` 此时只有：`spring.application.name: ragagent`、`spring.threads.virtual.enabled: true`、`server.port: ${SERVER_PORT:8080}`、`mybatis-plus.global-config.banner: false`、`mybatis-plus.configuration.map-underscore-to-camel-case: true`。数据源、Redis、Flyway 等运行配置在 Task 11b 补齐。

- [ ] **Step 5: 运行，确认通过**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml verify`
Expected: BUILD SUCCESS，`RagAgentApplicationTests` 通过。

- [ ] **Step 6: 确认旧构建未被破坏**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -q -DskipTests compile`
Expected: BUILD SUCCESS（旧模块仍用 Boot 3.3.13）。

- [ ] **Step 7: Commit**

```bash
git add backend/pom.xml backend/framework/pom.xml backend/app/pom.xml \
  backend/model-runtime/pom.xml backend/retrieval-engine/pom.xml backend/agent-engine/pom.xml \
  backend/model-runtime/src/main/java/com/ragagent/modelruntime/package-info.java \
  backend/retrieval-engine/src/main/java/com/ragagent/retrieval/package-info.java \
  backend/agent-engine/src/main/java/com/ragagent/agentengine/package-info.java \
  backend/framework/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports \
  backend/framework/src/test/java/testfixture/support/PostgresContainerTest.java \
  backend/app/src/main/java/com/ragagent/RagAgentApplication.java \
  backend/app/src/main/resources/application.yml \
  backend/app/src/test/java/com/ragagent/RagAgentApplicationTests.java
git commit -m "feat(backend): 独立聚合与最小可启动应用（Boot 3.5.16）"
```

在 `docs/migration/progress.md` 追加本任务一行并随下一次提交带上。

---

### Task 2: framework core：响应体、错误码、业务异常

**Files:**
- Create: `backend/framework/src/main/java/com/ragagent/framework/core/constant/Code.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/core/constant/CommCode.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/core/constant/FrameworkCode.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/core/exception/BusinessRuntimeException.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/core/exception/CodeCache.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/core/resp/RestResponse.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/core/resp/RestResponseBuilder.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/core/resp/PageData.java`
- Test: `backend/framework/src/test/java/com/ragagent/framework/core/CoreTypesTest.java`

测试夹具 `PostgresContainerTest` 已在 Task 1 建好，本任务不再创建。framework 不设全局测试宿主应用，见 Global Constraints 的「测试装配约定」。

**Interfaces:**
- Consumes: 无
- Produces:
  - `interface Code { String getCode(); String getMessage(); default int httpStatus() { ... } }`，默认实现：`"0000"` → 200；4 位码按首位映射 `4`→400、`5`→500、`6`→400；其余 500；10 位码一律 400
  - `enum CommCode implements Code`，至少含 `SUCCESS("0000")`、`ERROR("9999")`、`PARAM_ERROR("4400")`、`NOLOGIN("4402")`、`FORBIDDEN("4403")`、`NOT_FOUND("4404")`、`METHOD_NOT_ALLOWED("4405")`、`TOO_MANY_REQUESTS("4429")`、`INTERNAL_SERVER_ERROR("5500")`、`OBJECT_NOT_EXIST("6001")`、`OBJECT_IS_EXIST("6002")`、`PERMIT_ERROR("6041")`。`NOLOGIN` 覆盖 `httpStatus()` 返回 401，`FORBIDDEN`/`PERMIT_ERROR` 返回 403，`NOT_FOUND` 返回 404，`METHOD_NOT_ALLOWED` 返回 405，`TOO_MANY_REQUESTS` 返回 429
  - `enum FrameworkCode implements Code`：`TENANT_CONTEXT_MISSING("1001001001")`、`TENANT_HEADER_INVALID("1001001002")`，`httpStatus()` 覆盖为 400，`getMessage()` 返回中文兜底文案
  - `class RestResponse<T>`，字段 `String code`、`String msg`、`boolean success`、`long timestamp`、`T data`、`Map<String,Object> extension`
  - `final class RestResponseBuilder { static <T> RestResponse<T> makeOKRsp(); static <T> RestResponse<T> makeOKRsp(T data); static <T> RestResponse<T> makeFailRsp(Code code); static <T> RestResponse<T> makeFailRsp(Code code, String msg); }`
  - `class PageData<T>`，字段 `int pageNo`、`int pageSize`、`int totalCount`、`int pageCount`、`List<T> list`；`PageData(int pageNo,int pageSize,long totalCount,long pageCount)`、`PageData(int pageNo,int pageSize,long totalCount,long pageCount,List<T> list)`、`<R> PageData<R> withSameInstance(List<R> list)`
  - `class BusinessRuntimeException extends RuntimeException`，构造器 `(Code code)`、`(Code code, Object... args)`、`(Code code, Throwable cause, Object... args)`，getter `getCode()`、`getArgs()`，静态方法 `static String wireText(Throwable err)`
  - `wireText` 返回错误的线上文本，用于 SSE 终止错误帧与工具失败文案。语义沿用旧 `contract/src/main/java/com/ragagent/contract/error/BizException.java:59` 的 `wireText`：
    - 沿 cause 链从外向内找最近的 `BusinessRuntimeException`，返回 `"error code: " + code.getCode() + ", error message: " + code.getMessage()`。文本由 `getCode()` 现算，不取异常自身的 `getMessage()`，因为 `TenantContextMissingException` 等子类会覆盖它
    - 链上没有业务异常时，返回链上最深的非空 `getMessage()`
    - 所有 message 都为空时返回 `err.toString()`；`err` 为 `null` 返回 `""`；`getCause() == this` 时终止遍历
    - 纯静态，不依赖 Spring，所以不走 `I18nUtil`，`error message` 段取 `Code.getMessage()` 的中文兜底文案。格式串收口为常量 `WIRE_TEXT_FORMAT`
    - 旧调用点共 7 处：agent-engine 主码 4 处（`ToolBatchExecutor:89`、`ToolRegistry:257`、`AgentEngine:512`、`ActPhase:459`）加 `ToolRegistryRecordingTest` 3 处断言；domains 的 `QaTurnExecutor:291` 另有 1 处，随计划 08 迁入
  - `enum CodeCache { static Optional<Code> find(String code); static void register(Code... codes); }`，静态块登记 `CommCode` 与 `FrameworkCode` 全部取值

- [ ] **Step 1: 写失败测试**

```java
class CoreTypesTest {

	@Test
	void successResponseHasZeroedCode() {
		RestResponse<String> rsp = RestResponseBuilder.makeOKRsp("x");
		assertThat(rsp.getCode()).isEqualTo("0000");
		assertThat(rsp.isSuccess()).isTrue();
		assertThat(rsp.getData()).isEqualTo("x");
		assertThat(rsp.getTimestamp()).isPositive();
	}

	@Test
	void failResponseIsNotSuccess() {
		RestResponse<Void> rsp = RestResponseBuilder.makeFailRsp(CommCode.OBJECT_NOT_EXIST);
		assertThat(rsp.isSuccess()).isFalse();
		assertThat(rsp.getCode()).isEqualTo("6001");
	}

	@Test
	void pageDataCopiesPagingWhenReplacingList() {
		PageData<Long> ids = new PageData<>(2, 20, 45L, 3L, List.of(1L, 2L));
		PageData<String> names = ids.withSameInstance(List.of("a"));
		assertThat(names.getPageNo()).isEqualTo(2);
		assertThat(names.getTotalCount()).isEqualTo(45);
		assertThat(names.getList()).containsExactly("a");
	}

	@Test
	void businessExceptionCarriesCodeAndArgs() {
		BusinessRuntimeException ex = new BusinessRuntimeException(CommCode.PARAM_ERROR, "name");
		assertThat(ex.getCode()).isSameAs(CommCode.PARAM_ERROR);
		assertThat(ex.getArgs()).containsExactly("name");
	}

	@Test
	void codeCacheResolvesKnownCodeAndMissesUnknown() {
		assertThat(CodeCache.find("6001")).contains(CommCode.OBJECT_NOT_EXIST);
		assertThat(CodeCache.find("1234567890")).isEmpty();
	}

	@Test
	void httpStatusIsDerivedFromCodeWithExplicitOverrides() {
		assertThat(CommCode.SUCCESS.httpStatus()).isEqualTo(200);
		assertThat(CommCode.PARAM_ERROR.httpStatus()).isEqualTo(400);
		assertThat(CommCode.OBJECT_NOT_EXIST.httpStatus()).isEqualTo(400);
		assertThat(CommCode.NOLOGIN.httpStatus()).isEqualTo(401);
		assertThat(CommCode.PERMIT_ERROR.httpStatus()).isEqualTo(403);
		assertThat(CommCode.INTERNAL_SERVER_ERROR.httpStatus()).isEqualTo(500);
	}

	@Test
	void frameworkCodesAreTenDigitBadRequests() {
		assertThat(FrameworkCode.TENANT_CONTEXT_MISSING.getCode()).isEqualTo("1001001001");
		assertThat(FrameworkCode.TENANT_HEADER_INVALID.getCode()).isEqualTo("1001001002");
		assertThat(FrameworkCode.TENANT_CONTEXT_MISSING.httpStatus()).isEqualTo(400);
		assertThat(CodeCache.find("1001001002")).contains(FrameworkCode.TENANT_HEADER_INVALID);
	}

	@Test
	void wireTextUsesNearestBusinessExceptionThroughWrappers() {
		Throwable wrapped = new RuntimeException(new IllegalStateException(
			new BusinessRuntimeException(CommCode.OBJECT_NOT_EXIST)));
		assertThat(BusinessRuntimeException.wireText(wrapped))
			.isEqualTo("error code: 6001, error message: " + CommCode.OBJECT_NOT_EXIST.getMessage());

		Throwable outerWins = new BusinessRuntimeException(CommCode.PARAM_ERROR,
			new BusinessRuntimeException(CommCode.OBJECT_NOT_EXIST), "name");
		assertThat(BusinessRuntimeException.wireText(outerWins)).startsWith("error code: 4400, error message: ");
	}

	@Test
	void wireTextIgnoresOverriddenGetMessageOfSubclasses() {
		BusinessRuntimeException subclass = new BusinessRuntimeException(FrameworkCode.TENANT_CONTEXT_MISSING) {
			@Override
			public String getMessage() {
				return "tenant context missing";
			}
		};
		assertThat(BusinessRuntimeException.wireText(subclass))
			.startsWith("error code: 1001001001, error message: ");
	}

	@Test
	void wireTextFallsBackToDeepestNonEmptyMessage() {
		assertThat(BusinessRuntimeException.wireText(new RuntimeException("boom"))).isEqualTo("boom");
		assertThat(BusinessRuntimeException.wireText(new RuntimeException("outer", new IllegalStateException("inner"))))
			.isEqualTo("inner");
	}

	@Test
	void wireTextHandlesEmptyNullAndSelfReferencingCause() {
		assertThat(BusinessRuntimeException.wireText(new RuntimeException())).isEqualTo("java.lang.RuntimeException");
		assertThat(BusinessRuntimeException.wireText(null)).isEmpty();
		Throwable selfCause = new RuntimeException() {
			@Override
			public synchronized Throwable getCause() {
				return this;
			}
		};
		assertThat(BusinessRuntimeException.wireText(selfCause)).isNotBlank();
	}
}
```

`httpStatus()` 放在 `Code` 而不是在异常处理器里按字符串首位判断：10 位业务码首字符是 `1`，与 4 位 `CommCode` 的首字符语义不同，用统一规则会算错。默认实现只对 4 位码按首位映射；10 位码一律返回 400，需要别的状态码时在枚举里覆盖。

- [ ] **Step 2: 运行，确认失败**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl framework -am test -Dtest=CoreTypesTest`
Expected: FAIL，编译错误，类不存在。

- [ ] **Step 3: 实现各类型**

`RestResponse` 构造器里 `success` 由 `"0000".equals(code)` 计算，不提供 setter 之外的第二个真相来源。`CodeCache` 在静态块里扫描 `CommCode.values()` 与 `FrameworkCode.values()`，并留一个 `register(Code...)` 供各域启动时登记自有枚举。`FrameworkCode` 没有错误码文案之外的内容，文案由 Task 4 的 i18n 文件承担。

`wireText` 的遍历写成一个循环加一个小的私有方法，满足 Task 11c 的方法 40 行与嵌套 3 层上限。旧实现把「找业务异常」与「记录最深非空 message」放在同一个循环里，新实现保持这个结构，不拆成两遍遍历，否则自引用 cause 的终止条件要写两份。

- [ ] **Step 4: 运行，确认通过**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl framework -am test -Dtest=CoreTypesTest`
Expected: PASS，11 个用例全绿。

- [ ] **Step 5: Commit**

```bash
git add backend/framework/src/main/java/com/ragagent/framework/core
git add backend/framework/src/test/java/com/ragagent/framework/core/CoreTypesTest.java
git commit -m "feat(framework): 响应体、错误码与业务异常"
```

---

### Task 3: framework JSON：Jackson 模块与脱敏

**Files:**
- Create: `backend/framework/src/main/java/com/ragagent/framework/core/json/JacksonConfig.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/core/json/LongToStringSerializer.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/core/json/DesensitizeModule.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/core/annotation/JsonSlideDesensitize.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/core/annotation/JsonRegexDesensitize.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/core/json/SlideDesensitizationTypeEnum.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/core/json/RegexDesensitizationTypeEnum.java`
- Modify: `backend/framework/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（追加 `JacksonConfig`）
- Test: `backend/framework/src/test/java/com/ragagent/framework/core/JsonConfigTest.java`

**Interfaces:**
- Consumes: Task 2 的包结构与命名约定
- Produces:
  - `@interface JsonSlideDesensitize { SlideDesensitizationTypeEnum type(); }`
  - `@interface JsonRegexDesensitize { RegexDesensitizationTypeEnum type(); }`
  - `enum SlideDesensitizationTypeEnum { PHONE_NUMBER, ID_CARD_NO, BANK_CARD_NO }`
  - `enum RegexDesensitizationTypeEnum { EMAIL, CHINESE_NAME }`
  - `class JacksonConfig`：`@AutoConfiguration`，公开方法 `ObjectMapper objectMapper()`（`@Bean @Primary`），注册 `LongToStringSerializer`、`JavaTimeModule`（`WRITE_DATES_AS_TIMESTAMPS=false`）、`DesensitizeModule`、`DeserializationFeature` 与 `FAIL_ON_UNKNOWN_PROPERTIES=false`。单测直接 `new JacksonConfig().objectMapper()`，不起 Spring 上下文

- [ ] **Step 1: 写失败测试**

```java
class JsonConfigTest {

	private final ObjectMapper mapper = new JacksonConfig().objectMapper();

	@Test
	void bigintIdSerializesAsString() throws Exception {
		assertThat(mapper.writeValueAsString(new IdHolder(9_007_199_254_740_993L)))
			.isEqualTo("{\"id\":\"9007199254740993\"}");
	}

	@Test
	void longBelowSafeRangeAlsoSerializesAsString() throws Exception {
		assertThat(mapper.writeValueAsString(new IdHolder(1L))).isEqualTo("{\"id\":\"1\"}");
	}

	@Test
	void offsetDateTimeKeepsOffset() throws Exception {
		String json = mapper.writeValueAsString(
			new TimeHolder(OffsetDateTime.of(2026, 11, 10, 8, 30, 0, 0, ZoneOffset.ofHours(8))));
		assertThat(json).contains("\"t\":\"2026-11-10T08:30:00+08:00\"");
	}

	@Test
	void phoneNumberIsMasked() throws Exception {
		assertThat(mapper.writeValueAsString(new UserVo("13812345678", "a@b.com")))
			.contains("\"mobile\":\"138****5678\"");
	}

	@Test
	void emailIsMasked() throws Exception {
		assertThat(mapper.writeValueAsString(new UserVo("13812345678", "alice@example.com")))
			.contains("\"email\":\"a***@example.com\"");
	}

	record IdHolder(Long id) {}
	record TimeHolder(OffsetDateTime t) {}
	record UserVo(@JsonSlideDesensitize(type = SlideDesensitizationTypeEnum.PHONE_NUMBER) String mobile,
			@JsonRegexDesensitize(type = RegexDesensitizationTypeEnum.EMAIL) String email) {}
}
```

- [ ] **Step 2: 运行，确认失败**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl framework -am test -Dtest=JsonConfigTest`
Expected: FAIL，类不存在。

- [ ] **Step 3: 实现**

`LongToStringSerializer` 的 `serialize` 里对 `null` 原样写出 `null`，其余写 `String.valueOf(value)`，用 `gen.writeString`。

`DesensitizeModule` 用 `SimpleModule` + `BeanSerializerModifier.changeProperties`，对带注解的属性把 `JsonSerializer` 换成脱敏实现。

注意 `record` 的注解要能取到：`changeProperties` 里通过 `BeanPropertyDefinition.getPrimaryMember()` 读注解，取不到时回退到 `getAccessor()`。

- [ ] **Step 4: 运行，确认通过**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl framework -am test -Dtest=JsonConfigTest`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add backend/framework/src/main/java/com/ragagent/framework/core/json \
  backend/framework/src/main/java/com/ragagent/framework/core/annotation \
  backend/framework/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports backend/framework/src/test/java/com/ragagent/framework/core/JsonConfigTest.java
git commit -m "feat(framework): Jackson 模块与序列化脱敏"
```

---

### Task 4: framework 多语言：Locale 解析与错误文案渲染

**Files:**
- Create: `backend/framework/src/main/java/com/ragagent/framework/core/i18n/I18nUtil.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/core/i18n/AcceptLanguageLocaleResolver.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/core/i18n/FrameworkI18nConfig.java`
- Create: `backend/framework/src/main/resources/i18n/messages.properties`
- Create: `backend/framework/src/main/resources/i18n/messages_zh_CN.properties`
- Create: `backend/framework/src/main/resources/i18n/messages_en_US.properties`
- Create: `backend/framework/src/main/resources/i18n/messages_zh_HK.properties`
- Create: `backend/framework/src/main/resources/i18n/messages_es_ES.properties`
- Create: `backend/framework/src/main/resources/i18n/messages_pt_BR.properties`
- Create: `backend/framework/src/main/resources/i18n/messages_ru_RU.properties`
- Create: `backend/framework/src/main/resources/i18n/messages_th_TH.properties`
- Modify: `backend/framework/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（追加 `FrameworkI18nConfig`）
- Test: `backend/framework/src/test/java/com/ragagent/framework/core/I18nTest.java`

**Interfaces:**
- Consumes: Task 2 的 `Code`、`CommCode`、`FrameworkCode`
- Produces:
  - `class I18nUtil`（在 `FrameworkI18nConfig` 里用 `@Bean` 注册，实例方法）：`String getMessage(String code, Object... args)`、`String getMessage(Code code, Object... args)`。**偏离 furnace**：furnace 的 `I18nUtil` 是静态工具，这里改为 Bean，原因是静态工具要把 `MessageSource` 塞进静态字段，测试之间会互相污染。
  - `class FrameworkI18nConfig`：`@AutoConfiguration`
  - `class AcceptLanguageLocaleResolver implements LocaleResolver`
  - 配置项 `gac.furnace.multilang.enabled`、`gac.furnace.multilang.default-lang`（默认 `zh_CN`）、`gac.furnace.multilang.support-langs`（默认七种）、`gac.furnace.multilang.basenames`（默认 `i18n/messages`，app 在 Task 11b 配成 `i18n/messages,i18n/app-messages` 以装入业务域文案）
  - i18n key 约定 `code_{错误码}`，例如 `code_6001=数据不存在，请检查后重试`
  - 本任务写入的文案覆盖 `CommCode` 12 个与 `FrameworkCode` 2 个，共 14 个 key。`FrameworkCode` 的七语文案：

    | 码 | zh-CN | zh-HK | en-US |
    |---|---|---|---|
    | `1001001001` | 缺少租户上下文，请重新登录 | 缺少租戶上下文，請重新登入 | Tenant context is missing, please sign in again |
    | `1001001002` | 租户标识无效 | 租戶標識無效 | Tenant identifier is invalid |

    | 码 | es-ES | pt-BR | ru-RU | th-TH |
    |---|---|---|---|---|
    | `1001001001` | Falta el contexto del inquilino, inicie sesión de nuevo | Contexto do tenant ausente, faça login novamente | Отсутствует контекст арендатора, войдите снова | ไม่พบบริบทผู้เช่า กรุณาเข้าสู่ระบบอีกครั้ง |
    | `1001001002` | El identificador del inquilino no es válido | Identificador do tenant inválido | Недопустимый идентификатор арендатора | ตัวระบุผู้เช่าไม่ถูกต้อง |

- [ ] **Step 1: 写失败测试**

```java
@SpringBootTest(classes = FrameworkI18nConfig.class)
class I18nTest {

	@Autowired
	private I18nUtil i18nUtil;

	private void withLanguage(String header) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		if (header != null) {
			request.addHeader("Accept-Language", header);
		}
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
	}

	@AfterEach
	void clearContext() {
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void rendersSimplifiedChinese() {
		withLanguage("zh-CN");
		assertThat(i18nUtil.getMessage(CommCode.OBJECT_NOT_EXIST)).startsWith("数据不存在");
	}

	@Test
	void rendersEnglish() {
		withLanguage("en-US");
		assertThat(i18nUtil.getMessage(CommCode.OBJECT_NOT_EXIST)).startsWith("Data does not exist");
	}

	@Test
	void unsupportedLanguageFallsBackToZhCn() {
		withLanguage("fr-FR");
		assertThat(i18nUtil.getMessage(CommCode.OBJECT_NOT_EXIST)).startsWith("数据不存在");
	}

	@Test
	void missingHeaderFallsBackToZhCn() {
		withLanguage(null);
		assertThat(i18nUtil.getMessage(CommCode.OBJECT_NOT_EXIST)).startsWith("数据不存在");
	}

	@Test
	void unknownKeyReturnsCodeNotLiteralKey() {
		withLanguage("zh-CN");
		assertThat(i18nUtil.getMessage("1999000001")).isEqualTo("1999000001");
	}

	@Test
	void argsAreSubstituted() {
		withLanguage("zh-CN");
		assertThat(i18nUtil.getMessage(CommCode.PARAM_ERROR, "name")).contains("name");
	}

	@Test
	void frameworkCodeRendersInEnglish() {
		withLanguage("en-US");
		assertThat(i18nUtil.getMessage(FrameworkCode.TENANT_CONTEXT_MISSING)).startsWith("Tenant context is missing");
	}

	@Test
	void everyLanguageFileDefinesEveryFrameworkAndCommCode() throws Exception {
		List<Code> codes = new ArrayList<>(List.of(CommCode.values()));
		codes.addAll(List.of(FrameworkCode.values()));
		// 直接读属性文件，避免 ResourceBundle 回落到默认文件让缺失的 key 显得存在
		for (String suffix : List.of("", "_zh_CN", "_zh_HK", "_en_US", "_es_ES", "_pt_BR", "_ru_RU", "_th_TH")) {
			Properties props = new Properties();
			try (InputStream in = getClass().getResourceAsStream("/i18n/messages" + suffix + ".properties");
					Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				props.load(reader);
			}
			for (Code code : codes) {
				assertThat(props.getProperty("code_" + code.getCode()))
					.as("messages%s.properties 缺少 code_%s", suffix, code.getCode())
					.isNotBlank();
			}
		}
	}
}
```

- [ ] **Step 2: 运行，确认失败**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl framework -am test -Dtest=I18nTest`
Expected: FAIL，类不存在。

- [ ] **Step 3: 实现**

`AcceptLanguageLocaleResolver` 读 `Accept-Language` 头，按 `support-langs` 精确匹配（大小写不敏感、`zh-CN`/`zh_CN` 两种写法都接受），未命中返回 `default-lang` 的 Locale。

`FrameworkI18nConfig` 是 `@AutoConfiguration`，注册 `ResourceBundleMessageSource`：`setBasenames` 取配置 `gac.furnace.multilang.basenames`、`setDefaultEncoding("UTF-8")`、`setFallbackToSystemLocale(false)`，并以 `@Bean` 注册 `AcceptLanguageLocaleResolver` 与 `I18nUtil`。

`I18nUtil` 注入 `MessageSource` 与 `LocaleResolver`（从 `RequestContextHolder` 取当前请求；无请求上下文时用 `default-lang`）。`getMessage` 的 key 为 `"code_" + code.getCode()`，找不到时返回 `code` 字符串本身。

`messages.properties`（默认，与 `zh_CN` 内容一致）与七个语种文件写 `CommCode` 的 12 条与 `FrameworkCode` 的 2 条，共 14 条，`FrameworkCode` 的译文见 Interfaces 的表。其余文案用机器翻译出初稿，之后人工校对（spec 7.4）。

- [ ] **Step 4: 运行，确认通过**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl framework -am test -Dtest=I18nTest`
Expected: PASS，8 个用例全绿。

- [ ] **Step 5: Commit**

```bash
git add backend/framework/src/main/java/com/ragagent/framework/core/i18n \
  backend/framework/src/main/resources/i18n \
  backend/framework/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
git add backend/framework/src/test/java/com/ragagent/framework/core/I18nTest.java
git commit -m "feat(framework): 多语言资源与错误文案渲染"
```

---

### Task 5: framework 多租户与 PostgreSQL 行级安全

**Files:**
- Create: `backend/framework/src/main/java/com/ragagent/framework/tenant/TenantContextHolder.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/tenant/IgnoreTenant.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/tenant/IgnoreTenantAspect.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/tenant/TenantResolver.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/tenant/MyTenantFilter.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/tenant/TenantFilterConfig.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/tenant/TenantLineConfig.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/tenant/TenantContextMissingException.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/tenant/RowLevelSecurityInterceptor.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/tenant/TenantAwareJdbcTemplate.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/tenant/TenantProperties.java`
- Modify: `backend/framework/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（追加 `TenantFilterConfig`、`TenantLineConfig`）
- Modify: `backend/framework/src/test/java/testfixture/support/PostgresContainerTest.java`（加 `jdbc` 字段、应用角色与属主账号、`execAsOwner`，见 Step 3 末尾）
- Create: `backend/framework/src/test/resources/tenant-test.sql`
- Create: `backend/framework/src/test/resources/tenant-test-reset.sql`
- Create: `backend/framework/src/test/java/testfixture/framework/tenant/OrdersFixtureConfig.java`（`@Configuration`，`@MapperScan` 本包，`@Import(OrdersRepository.class)`）
- Create: `backend/framework/src/test/java/testfixture/framework/tenant/{Order,OrderMapper,OrdersRepository}.java`
- Test: `backend/framework/src/test/java/com/ragagent/framework/tenant/MyTenantFilterTest.java`
- Test: `backend/framework/src/test/java/com/ragagent/framework/tenant/TenantIsolationTest.java`

**Interfaces:**
- Consumes: Task 2 的 `Code` 体系与 `FrameworkCode`（`TenantContextMissingException` 继承 `BusinessRuntimeException`，code 为 `FrameworkCode.TENANT_CONTEXT_MISSING`，10 位业务码 `1001001001`）；Task 1 的 `PostgresContainerTest`
- Produces:
  - `final class TenantContextHolder { static void set(Long tenantId); static Long get(); static void setIgnore(boolean); static boolean isIgnore(); static void clear(); }`
  - `@interface IgnoreTenant`（方法或类上）
  - `interface TenantResolver { Optional<Long> resolve(HttpServletRequest request); }`：租户 ID 的来源。默认实现 `SaTokenTenantResolver` 在 Task 8，从登录会话读取
  - `class MyTenantFilter extends OncePerRequestFilter`：构造器 `(ObjectProvider<TenantResolver> resolver, TenantProperties properties, HandlerExceptionResolver exceptionResolver)`。逻辑：先问 `TenantResolver`；`trust-header=true` 且 resolver 没给出结果时，才读请求头 `X-Tenant-Id`；`trust-header=false`（默认）时请求头一律忽略。头值非数字时通过 `exceptionResolver` 抛 `BusinessRuntimeException(FrameworkCode.TENANT_HEADER_INVALID)`，由 Task 7 的异常处理器渲染。`finally` 里清理 `TenantContextHolder`。注册顺序暂用字面量 `-50`（在 Spring 的 `RequestContextFilter` 之后，该过滤器顺序是 `-105`），Task 7 创建 `FilterOrders` 后改为引用常量
  - 配置项 `gac.furnace.tenant.enabled`（默认 true）、`mode`（`line`）、`column`（`tenant_id`）、`trust-header`（默认 **false**，仅测试与本地调试设 true）、`tenant-code-header`（`X-Tenant-Id`）、`ignore-tables`
  - `class TenantProperties`：`@ConfigurationProperties("gac.furnace.tenant")`
  - `class TenantFilterConfig`：`@AutoConfiguration`，注册 `MyTenantFilter` 的 `FilterRegistrationBean`，不依赖数据库
  - `class TenantLineConfig`：`@AutoConfiguration(before = JdbcTemplateAutoConfiguration.class, after = DataSourceAutoConfiguration.class)`，提供 `MybatisPlusInterceptor`（含 `TenantLineInnerInterceptor`）、`RowLevelSecurityInterceptor`、`TenantAwareJdbcTemplate`、`IgnoreTenantAspect`。必须排在 `JdbcTemplateAutoConfiguration` 之前，让后者因已有 `JdbcOperations` 而退让
  - `class RowLevelSecurityInterceptor`：MyBatis 拦截器，在 `Executor.query`/`Executor.update` 前设置租户会话变量
  - `class TenantContextMissingException extends BusinessRuntimeException`：`getMessage()` 返回英文日志文案 `tenant context missing`，面向用户的文案由 i18n 按错误码渲染
  - `class TenantAwareJdbcTemplate extends JdbcTemplate`，重写 `execute` 入口，取连接后按统一规则设置 `app.tenant_id` 会话变量。framework 注册它的 Bean，**主码里凡需 `JdbcTemplate` 一律注入这个类型**，不要注入裸 `JdbcTemplate`。生产代码的 `JdbcTemplate` 只允许出现在 `framework.tenant`（这个类）与 `retrieval-engine`（pgvector 检索）两个包，由 Task 11 的 ArchUnit 规则守
  - `PostgresContainerTest` 的新增成员（Task 1 的基类上改）：`@Autowired protected TenantAwareJdbcTemplate jdbc`、`protected static void execAsOwner(String classpathScript)`（用超级用户连接执行 classpath 上的 SQL 脚本，供 `@BeforeAll` 建表）、`@DynamicPropertySource` 改为注入应用角色 `ragagent_app` 的 `spring.datasource.*` 与属主账号的 `spring.flyway.url/user/password`，另注入 `gac.furnace.redis.enabled=false`

- [ ] **Step 1: 写失败测试**（真实 PostgreSQL，不是 H2）

建表与策略放在测试资源 `tenant-test.sql`：

```sql
CREATE TABLE orders (id bigint PRIMARY KEY, tenant_id bigint NOT NULL, content text);
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders FORCE ROW LEVEL SECURITY;
CREATE POLICY orders_tenant ON orders
  USING (
    current_setting('app.tenant_id', true) = 'system'
    OR tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint
  )
  WITH CHECK (
    current_setting('app.tenant_id', true) = 'system'
    OR tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint
  );
```

种子数据放在 `tenant-test-reset.sql`，每个用例前由 `execAsOwner` 执行，保证用例互不污染：

```sql
TRUNCATE orders;
INSERT INTO orders VALUES (1, 1, 'a1'), (2, 1, 'a2'), (3, 2, 'b1');
```

**策略必须 fail-closed。** 校验实测：PostgreSQL 的 `current_setting('app.x', true)` 在从未设置时返回 NULL，但一旦某条连接上执行过 `SET LOCAL`，事务结束后同一连接上它返回**空串**而不是 NULL，且 `RESET` 也不会回到 NULL。池化连接会把空串带到后续所有请求上。若策略写成「空串则放行」，一个请求的 `SET LOCAL` 会让这条连接从此对所有租户全开。所以：
- 空串 `''` 经 `nullif(...)::bigint` 变成 NULL，比较结果非真，**拒绝**；
- 未设置（NULL）同样非真，**拒绝**；
- 系统通道用显式哨兵 `'system'`，**放行**；
- 正常租户写 `'<id>'`，按 `tenant_id` 过滤。

```java
@SpringBootTest(classes = {TenantLineConfig.class, OrdersFixtureConfig.class})
class TenantIsolationTest extends PostgresContainerTest {

	// jdbc、dataSource 来自父类 PostgresContainerTest，jdbc 的类型是 TenantAwareJdbcTemplate。
	@Autowired private OrdersRepository orders;

	@BeforeAll
	static void createTables() throws Exception {
		execAsOwner("/tenant-test.sql");
	}

	@BeforeEach
	void resetRows() throws Exception {
		// 每个用例前把 orders 复位成三行种子数据，用例之间不互相污染
		execAsOwner("/tenant-test-reset.sql");
	}

	@Test
	void pluginScopesQueriesToCurrentTenant() {
		TenantContextHolder.set(1L);
		assertThat(orders.findContents()).containsExactly("a1", "a2");
		TenantContextHolder.set(2L);
		assertThat(orders.findContents()).containsExactly("b1");
		TenantContextHolder.clear();
	}

	@Test
	void rowLevelSecurityBlocksRawSqlFromOtherTenant() {
		TenantContextHolder.set(1L);
		assertThat(jdbc.queryForList("SELECT content FROM orders", String.class))
			.containsExactlyInAnyOrder("a1", "a2");
		TenantContextHolder.clear();
	}

	@Test
	void ignoreTenantReadsAcrossTenants() {
		// 系统通道：@IgnoreTenant 时 framework 把会话变量设成 'system'，策略放行。
		assertThat(orders.findAllAcrossTenants()).hasSize(3);
	}

	@Test
	void staleSessionVariableAfterTransactionDeniesAllRows() throws Exception {
		// 校验发现的坑：SET LOCAL 结束后同一连接上 current_setting 返回空串。
		// 用裸连接复现：先设过再清空，然后不设变量直接查，必须一行都看不到。
		try (Connection c = dataSource.getConnection()) {
			c.setAutoCommit(false);
			try (Statement s = c.createStatement()) {
				s.execute("SET LOCAL app.tenant_id = '1'");
			}
			c.commit();
			try (Statement s = c.createStatement();
					ResultSet rs = s.executeQuery("SELECT content FROM orders")) {
				List<String> rows = new ArrayList<>();
				while (rs.next()) {
					rows.add(rs.getString(1));
				}
				assertThat(rows).isEmpty();
			}
		}
	}

	@Test
	void missingTenantContextIsReportedExplicitly() {
		assertThatThrownBy(() -> orders.findContents())
			.isInstanceOf(TenantContextMissingException.class)
			.hasMessageContaining("tenant context missing");
	}

	@Test
	void missingTenantContextOnRawJdbcIsTranslated() {
		assertThatThrownBy(() -> jdbc.queryForList("SELECT content FROM orders", String.class))
			.hasMessageContaining("tenant context missing");
	}

	@Test
	void insertStampsTenantIdAutomatically() {
		TenantContextHolder.set(3L);
		orders.save(new Order(null, "c1"));
		assertThat(jdbc.queryForObject(
			"SELECT tenant_id FROM orders WHERE content = 'c1' AND tenant_id = 3", Long.class))
			.isEqualTo(3L);
		TenantContextHolder.clear();
	}

	@Test
	void ignoreScopeRestoresPreviousValueNotFalse() {
		// 嵌套：外层已处于忽略态时，内层 @IgnoreTenant 调用结束不得把外层豁免清掉。
		TenantContextHolder.set(1L);
		TenantContextHolder.setIgnore(true);
		try {
			assertThat(orders.findAllAcrossTenants()).hasSize(3);
			assertThat(TenantContextHolder.isIgnore()).isTrue();
		} finally {
			TenantContextHolder.setIgnore(false);
			TenantContextHolder.clear();
		}

		// 反向：外层未忽略时，一次 @IgnoreTenant 调用结束必须回到未忽略。
		TenantContextHolder.set(1L);
		try {
			assertThat(orders.findAllAcrossTenants()).hasSize(3);
			assertThat(TenantContextHolder.isIgnore()).isFalse();
		} finally {
			TenantContextHolder.clear();
		}
	}
}
```

`OrdersRepository` 与 `Order` 是测试夹具，放在 `backend/framework/src/test/java/testfixture/framework/tenant/`（包根不在 `com.ragagent` 之下，见 Global Constraints 的测试装配约定）。`OrdersRepository` 用 `@Service` 继承 `ServiceImpl<OrderMapper, Order>`，方法 `List<String> findContents()` 用 `lambdaQuery().select(Order::getContent).list()` 取内容；`List<Order> findAllAcrossTenants()` 带 `@IgnoreTenant`，内部用 `list()`。`OrdersFixtureConfig` 是 `@Configuration`，用 `@MapperScan` 扫本包并 `@Import(OrdersRepository.class)`。

租户过滤器的来源规则用纯单元测试覆盖，不需要数据库，也不起 Spring 上下文：

```java
class MyTenantFilterTest {

	private final TenantProperties properties = new TenantProperties();
	private final HandlerExceptionResolver exceptionResolver = mock(HandlerExceptionResolver.class);

	private Long runFilter(TenantResolver resolver, boolean trustHeader, String headerValue) throws Exception {
		properties.setTrustHeader(trustHeader);
		MyTenantFilter filter = new MyTenantFilter(provider(resolver), properties, exceptionResolver);
		MockHttpServletRequest request = new MockHttpServletRequest();
		if (headerValue != null) {
			request.addHeader("X-Tenant-Id", headerValue);
		}
		AtomicReference<Long> seen = new AtomicReference<>();
		filter.doFilter(request, new MockHttpServletResponse(), (req, rsp) -> seen.set(TenantContextHolder.get()));
		return seen.get();
	}

	@Test
	void headerIsIgnoredWhenTrustHeaderIsFalse() throws Exception {
		assertThat(runFilter(r -> Optional.empty(), false, "99")).isNull();
	}

	@Test
	void resolverResultWinsOverHeader() throws Exception {
		assertThat(runFilter(r -> Optional.of(7L), true, "99")).isEqualTo(7L);
	}

	@Test
	void headerIsUsedOnlyWhenTrustedAndResolverIsEmpty() throws Exception {
		assertThat(runFilter(r -> Optional.empty(), true, "99")).isEqualTo(99L);
	}

	@Test
	void contextIsClearedAfterTheRequest() throws Exception {
		runFilter(r -> Optional.of(7L), false, null);
		assertThat(TenantContextHolder.get()).isNull();
	}

	@Test
	void nonNumericTrustedHeaderIsReportedThroughTheExceptionResolver() throws Exception {
		runFilter(r -> Optional.empty(), true, "abc");
		verify(exceptionResolver).resolveException(any(), any(), isNull(), argThat(e ->
			e instanceof BusinessRuntimeException b && b.getCode() == FrameworkCode.TENANT_HEADER_INVALID));
	}
}
```

`provider(resolver)` 是测试里的小工具，把 `TenantResolver` 包成 `ObjectProvider`（可用 `StaticListableBeanFactory` 加 `getBeanProvider`）。

`staleSessionVariableAfterTransactionDeniesAllRows` 是本任务最重要的一条：它把「池化连接上的残留空串」这个真实风险钉成回归用例。没有它，策略写成 `= ''` 放行时所有其它用例仍然是绿的。

- [ ] **Step 2: 运行，确认失败**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl framework -am test -Dtest='TenantIsolationTest,MyTenantFilterTest'`
Expected: FAIL，类不存在。

- [ ] **Step 3: 实现**

`MyTenantFilter` 的取值顺序与 Interfaces 一致：先问 `TenantResolver`（默认实现读登录会话，Task 8），没有结果且 `trust-header=true` 才读请求头。**默认不信任请求头**：如果租户 ID 取自客户端可任意伪造的头，任何登录用户都能读到别的租户的数据。缺失或解析不出租户时请求继续，但 `TenantContextHolder.get()` 为空，后续数据访问抛 `TenantContextMissingException`，按 `Review Focus` 第 1 条处理。**不要**在过滤器里放行到无租户的全表查询。头值非数字（仅 `trust-header=true` 时可能出现）按 Interfaces 里的方式交给 `HandlerExceptionResolver`。

`TenantContextMissingException#getMessage()` 返回英文日志文案 `tenant context missing`。

`TenantLineConfig` 注册 `MybatisPlusInterceptor`，`TenantLineInnerInterceptor` 的 `tenantIdColumn` 为配置的列，`ignoreTable` 读 `ignore-tables`，并忽略 `TenantContextHolder.isIgnore()` 为 true 时的表。

`IgnoreTenantAspect` 环绕 `@IgnoreTenant` 注解的方法或类，**保存原值再恢复，不能写死 `setIgnore(false)`**：

```java
@Around("@annotation(com.ragagent.framework.tenant.IgnoreTenant)"
		+ " || @within(com.ragagent.framework.tenant.IgnoreTenant)")
public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
	boolean previous = TenantContextHolder.isIgnore();
	TenantContextHolder.setIgnore(true);
	try {
		return joinPoint.proceed();
	} finally {
		TenantContextHolder.setIgnore(previous);
	}
}
```

写死 `false` 时：外层方法已进入忽略态，内层调用一个 `@IgnoreTenant` 方法，内层结束后外层的豁免被清掉，外层剩下的语句悄悄恢复租户过滤。症状是跨租户任务查到一半数据不见了，不报错。customer_work 踩过同一个坑，它们的注释是「MyBatis-Plus 的 clearIgnoreStrategy() 是无条件 remove，内层作用域结束会把外层的豁免一并清掉」。这一层的开关由 framework 自己控制，所以保存原值就够，不需要计数器。`ignoreScopeRestoresPreviousValueNotFalse` 用例同时覆盖正反两个方向。

`RowLevelSecurityInterceptor` 注册为 MyBatis 拦截器：在 `Executor.update/query` 前决定会话变量的值：

| 线程状态 | 写入 `app.tenant_id` | 结果 |
|---|---|---|
| 有租户、不在忽略态 | `'<tenantId>'` | 按租户过滤 |
| 无租户、不在忽略态 | 不写 SQL，直接抛 `TenantContextMissingException` | fail-closed |
| 忽略态（`@IgnoreTenant`） | `'system'` | 策略放行，跨租户读写 |

**必须显式写 `'system'`，不能什么都不写。** 校验实测：`SET LOCAL` 结束后同一池化连接上 `current_setting('app.tenant_id', true)` 返回空串而不是 NULL。若忽略态下不写值，空串经 `nullif(...)::bigint` 变成 NULL、比较为假，系统通道会被策略拒绝，功能不可用。

`SET LOCAL` 只在事务内有效。非事务调用时降级为 `set_config('app.tenant_id', ?, false)`（会话级）并记录 debug 日志；会话级设置会粘在连接上，所以 `execute` 结束后要 `set_config(..., '', false)` 清回空串。

`TenantAwareJdbcTemplate` 继承 `JdbcTemplate`，重写 `execute(ConnectionCallback)` 之前的入口，读连接后按上表同样规则设置会话变量。framework 暴露它的 Bean，app 与测试一律注入这个类型。**不这么做时**，`JdbcTemplate` 的 SQL 看到的是残留的空串，策略判为拒绝，虽然不会越权，但功能全挂；若策略写错成「空串放行」，那就是越权。两个方向都由 Step 5 的反向验证覆盖。

`TenantContextMissingException` 由 `RowLevelSecurityInterceptor` 与 `TenantAwareJdbcTemplate` 共同抛出；同时兜住 PostgreSQL 的 `invalid input syntax for type bigint: ""`（SQLState `22P02`）与 `insufficient_privilege`（`42501`）并转成该异常，前者是 spike 发现的坑（spec 5.10.5 要求 framework 收口），后者是策略拒绝时的真实错误码。

数据源必须用非属主账号：`PostgresContainerTest` 里额外创建一个 `ragagent_app` 角色并授权，`spring.datasource.username` 用它。否则 RLS 对表属主不生效，测试会假绿。容器默认的 `postgres` 是超级用户，用它连接做三件事：`@BeforeAll` 级的静态初始化里建 `ragagent_app` 角色并 `GRANT USAGE ON SCHEMA public`、`ALTER DEFAULT PRIVILEGES ... GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES`；`execAsOwner` 执行建表脚本；`@DynamicPropertySource` 把 `spring.datasource.*` 指向应用角色、把 `spring.flyway.url/user/password` 指向超级用户（供 app 的 Flyway 用）。`jdbc` 字段用 `@Autowired` 注入，所以所有继承这个基类的测试，其上下文都必须含 `TenantLineConfig`。

**Task 1 的 `PostgresContainerTest` 在这里改**：加 `jdbc` 字段、加 `execAsOwner`、改 `@DynamicPropertySource` 的数据源账号与 Flyway 属性、加 `gac.furnace.redis.enabled=false`（让继承它的上下文不需要 Redis），类上追加 `@ImportAutoConfiguration` 引入 `DataSourceTransactionManagerAutoConfiguration`、`TransactionAutoConfiguration`、`MybatisPlusAutoConfiguration`。改完先重跑 Task 1 的 `RagAgentApplicationTests` 确认仍绿。

- [ ] **Step 4: 运行，确认通过**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl framework -am test -Dtest='TenantIsolationTest,MyTenantFilterTest'`
Expected: PASS，`TenantIsolationTest` 8 个、`MyTenantFilterTest` 5 个用例全绿。

再跑 `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl app -am test -Dtest=RagAgentApplicationTests`，Expected: PASS，说明基类改动没有破坏 Task 1。

- [ ] **Step 5: 反向验证测试真的在测 RLS**

做三次反向验证，每次改完恢复：

1. 把 `RowLevelSecurityInterceptor` 的 `SET LOCAL` 注释掉，重跑 `rowLevelSecurityBlocksRawSqlFromOtherTenant`。
   Expected: FAIL（读到其他租户数据或报错）。
2. 把 `TenantAwareJdbcTemplate` 的会话变量设置注释掉，只留普通 `JdbcTemplate`，重跑同一用例。
   Expected: FAIL（报权限错误或返回空）。
3. 把 RLS 策略的 `nullif(current_setting(...), '')::bigint` 改成 `current_setting(...)::bigint`（去掉 nullif），重跑 `staleSessionVariableAfterTransactionDeniesAllRows`。
   Expected: FAIL，报 `invalid input syntax for type bigint: ""`。这一条确认「空串」路径确实存在，而不是理论推测。
4. 把 `MyTenantFilter` 临时改成不管 `trust-header` 配置都读请求头，重跑 `headerIsIgnoredWhenTrustHeaderIsFalse`。
   Expected: FAIL。确认「伪造租户头」这条防线真的由测试守着。

这四步是必须的：不做反向验证时，RLS 用例可能在账号权限不足、策略未生效的情况下"通过"。

- [ ] **Step 6: Commit**

```bash
git add backend/framework/src/main/java/com/ragagent/framework/tenant
git add backend/framework/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
git add backend/framework/src/test/java/testfixture/support/PostgresContainerTest.java
git add backend/framework/src/test/java/testfixture/framework/tenant
git add backend/framework/src/test/java/com/ragagent/framework/tenant/TenantIsolationTest.java \
  backend/framework/src/test/java/com/ragagent/framework/tenant/MyTenantFilterTest.java
git add backend/framework/src/test/resources/tenant-test.sql backend/framework/src/test/resources/tenant-test-reset.sql
git commit -m "feat(framework): 多租户插件、租户来源解析与 PostgreSQL 行级安全"
```

---

### Task 6: framework MyBatis 扩展

**Files:**
- Create: `backend/framework/src/main/java/com/ragagent/framework/mybatis/FrameworkMetaObjectHandler.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/mybatis/EncryptTypeHandler.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/mybatis/Encrypted.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/mybatis/SqlTraceInnerInterceptor.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/mybatis/MybatisConstraintInterceptor.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/mybatis/EncryptKeyValidator.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/mybatis/MybatisFrameworkConfig.java`
- Create: `backend/framework/src/main/resources/db/tenant-unique-index.sql`
- Modify: `backend/framework/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（追加 `MybatisFrameworkConfig`）
- Modify: `backend/framework/src/test/java/testfixture/support/PostgresContainerTest.java`（加动态属性 `gac.furnace.mybatis.encrypt-key`，值为测试用的 Base64 32 字节密钥）
- Create: `backend/framework/src/test/resources/mybatis-test.sql`
- Create: `backend/framework/src/test/resources/mybatis-test-reset.sql`
- Create: `backend/framework/src/test/java/testfixture/framework/mybatis/{Secret,SecretMapper,SecretRepository,SecretsFixtureConfig}.java`
- Test: `backend/framework/src/test/java/com/ragagent/framework/mybatis/MybatisExtensionTest.java`
- Test: `backend/framework/src/test/java/com/ragagent/framework/mybatis/EncryptKeyValidatorTest.java`

**Interfaces:**
- Consumes: Task 5 的租户上下文、`TenantLineConfig`、`PostgresContainerTest.execAsOwner`
- Produces:
  - `class MybatisFrameworkConfig`：`@AutoConfiguration`，注册 `SqlTraceInnerInterceptor`、`MybatisConstraintInterceptor`、`FrameworkMetaObjectHandler`、`EncryptKeyValidator` 与全局字段策略
  - `class EncryptKeyValidator`：`void validate(String base64Key)`，空、不是合法 Base64、解码后不是 32 字节时抛 `IllegalStateException`，消息写明环境变量名 `RAGAGENT_ENCRYPT_KEY` 与要求（Base64 编码的 32 字节）。启动时由 `MybatisFrameworkConfig` 调用
  - 配置项 `gac.furnace.mybatis.encrypt-key`（AES-256，Base64）
  - `@interface Encrypted`
  - 全局字段策略：`insertStrategy = NOT_NULL` 保持默认，`updateStrategy = NOT_NULL`（spec 5.11 表格第 3 行要求可被个别列显式覆盖，用 `@TableField(updateStrategy = FieldStrategy.ALWAYS)`）
  - 逻辑删除：`@TableLogic`，列名 `deleted_at`，`timestamptz`
  - 逻辑删除唯一键方案（spec 5.7.1 第 4 条）：唯一索引建在 `(tenant_id, <业务键>, deleted_at)` 上，删除时把 `deleted_at` 写为当前时间而不是布尔位。同一业务键可以有多条已删除行，活着的行只会有一条

- [ ] **Step 1: 写失败测试**

```java
@SpringBootTest(classes = {TenantLineConfig.class, MybatisFrameworkConfig.class, SecretsFixtureConfig.class})
class MybatisExtensionTest extends PostgresContainerTest {

	@Autowired private SecretRepository repo;

	@BeforeAll
	static void createTables() throws Exception {
		execAsOwner("/mybatis-test.sql");
		execAsOwner("/db/tenant-unique-index.sql");
	}

	@BeforeEach
	void resetRows() throws Exception {
		execAsOwner("/mybatis-test-reset.sql");
	}

	@Test
	void encryptedColumnRoundTripsAndCiphertextDiffersFromPlaintext() {
		TenantContextHolder.set(1L);
		repo.save(new Secret(null, "13812345678"));
		String raw = jdbc.queryForObject("SELECT phone FROM secrets", String.class);
		assertThat(raw).isNotEqualTo("13812345678");
		assertThat(repo.findPhones()).containsExactly("13812345678");
		TenantContextHolder.clear();
	}

	@Test
	void logicDeleteHidesRowFromQueries() {
		TenantContextHolder.set(2L);
		Secret saved = repo.save(new Secret(null, "13900000000"));
		repo.removeById(saved.getId());
		assertThat(repo.findPhones()).isEmpty();
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM secrets WHERE deleted_at <> '1970-01-01 00:00:00+00'", Long.class))
			.isEqualTo(1L);
		TenantContextHolder.clear();
	}

	@Test
	void insertWithNullFieldLetsDatabaseDefaultApply() {
		TenantContextHolder.set(3L);
		repo.save(new Secret(null, null));
		assertThat(jdbc.queryForObject(
			"SELECT status FROM secrets WHERE phone IS NULL AND tenant_id = 3", String.class))
			.isEqualTo("active");
		TenantContextHolder.clear();
	}

	@Test
	void uniqueKeySurvivesLogicalDeleteAndRecreate() {
		TenantContextHolder.set(4L);
		Secret first = repo.save(new Secret(null, "13800000001"));
		repo.removeById(first.getId());
		Secret second = assertDoesNotThrow(() -> repo.save(new Secret(null, "13800000001")));
		assertThat(second.getId()).isNotEqualTo(first.getId());
		assertThat(repo.findPhones()).containsExactly("13800000001");
		TenantContextHolder.clear();
	}

	@Test
	void uniqueKeyStillRejectsDuplicateLiveRow() {
		TenantContextHolder.set(5L);
		repo.save(new Secret(null, "13800000002"));
		assertThatThrownBy(() -> repo.save(new Secret(null, "13800000002")))
			.isInstanceOf(BusinessRuntimeException.class);
		TenantContextHolder.clear();
	}
}
```

`secrets` 表放在测试资源 `mybatis-test.sql`：`id bigint PK`、`tenant_id bigint NOT NULL`、`phone text`、`status text NOT NULL DEFAULT 'active'`、`deleted_at timestamptz NOT NULL DEFAULT '1970-01-01 00:00:00+00'`。表同样开启并强制行级安全，策略与 Task 5 的 `tenant-test.sql` 相同。`mybatis-test-reset.sql` 只有一句 `TRUNCATE secrets;`，每个用例前执行。夹具 `Secret` 的 `phone` 字段标 `@TableField(typeHandler = EncryptTypeHandler.class)`，实体开 `@TableName(autoResultMap = true)`，`deletedAt` 字段标 `@TableLogic`。

唯一索引不进 `mybatis-test.sql`，放在 `backend/framework/src/main/resources/db/tenant-unique-index.sql` 作为 framework 交付物（各域迁入时按本模板为自己的业务键建索引）：

```sql
CREATE UNIQUE INDEX ux_secrets_tenant_phone
  ON secrets (tenant_id, phone, deleted_at);
```

`deleted_at` 参与索引后，写入必须保证活着的行 `deleted_at` 不是 `NULL`，因为 PostgreSQL 的唯一索引把 `NULL` 当成互不相同的值，`(1, '138...', NULL)` 可以有无数条。所以 `deleted_at` 列用「未删除 = 特殊哨兵值 `'1970-01-01 00:00:00+00'`」而不是 `NULL`，`@TableLogic(value = "'1970-01-01 00:00:00+00'", delval = "now()")`。若 MyBatis-Plus 把 `now()` 当字符串字面量加引号，改用 `delval = "CURRENT_TIMESTAMP"` 并在 `deviations.md` 登记；`logicDeleteHidesRowFromQueries` 会暴露这个问题。

三件事一起验证：加密列双向、逻辑删除后能重建同键、活着的行仍互斥。第三条是很多人漏掉的，只测前两条时，唯一索引可能根本没建对。

`insertWithNullFieldLetsDatabaseDefaultApply` 这条用例一旦通过，就是在验证 spec 5.11 表格第 3 行：全局 `NOT_NULL` 策略下 null 字段不进 INSERT，库默认值生效，不需要为它手写 SQL。

加密密钥校验是纯单元测试：

```java
class EncryptKeyValidatorTest {

	private final EncryptKeyValidator validator = new EncryptKeyValidator();

	@Test
	void blankKeyFailsWithEnvironmentVariableHint() {
		assertThatThrownBy(() -> validator.validate(""))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("RAGAGENT_ENCRYPT_KEY");
	}

	@Test
	void keyThatDecodesToTheWrongLengthFails() {
		String sixteenBytes = Base64.getEncoder().encodeToString(new byte[16]);
		assertThatThrownBy(() -> validator.validate(sixteenBytes)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void base64KeyOfThirtyTwoBytesPasses() {
		assertThatCode(() -> validator.validate(Base64.getEncoder().encodeToString(new byte[32]))).doesNotThrowAnyException();
	}
}
```

- [ ] **Step 2: 运行，确认失败**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl framework -am test -Dtest='MybatisExtensionTest,EncryptKeyValidatorTest'`
Expected: FAIL，类不存在。

- [ ] **Step 3: 实现**

`EncryptTypeHandler extends BaseTypeHandler<String>`：`setNonNullParameter` 用 AES/GCM 加密后写 Base64；`getNullableResult` 解密。密钥从 `gac.furnace.mybatis.encrypt-key` 读，`MybatisFrameworkConfig` 启动时调 `EncryptKeyValidator.validate`，不合格则启动失败，错误消息里写明环境变量名。`PostgresContainerTest` 加一条动态属性给出测试用的合法密钥。

`SqlTraceInnerInterceptor` 实现 `Interceptor`，在 `plugin()` 里只对 `StatementHandler.prepare` 做耗时与 SQL 的 debug 日志，参数值不进日志（脱敏由日志组件负责）。

`FrameworkMetaObjectHandler` 填充 `create_time`/`update_time` 为 `OffsetDateTime.now()`；`create_by`/`update_by` 从 Sa-Token 当前会话取。Task 8 完成前该方法返回 `null`，并留一条 `// TODO Task 8：接入 SaTokenAccountProvider` 的注释。这是全计划唯一允许的 TODO，Task 8 Step 3 负责删除。

`BusinessRuntimeException` 的来源：`uniqueKeyStillRejectsDuplicateLiveRow` 里 PostgreSQL 抛唯一约束冲突。**不能靠 Spring 的 `PersistenceExceptionTranslator` 转业务异常**：校验确认该接口的返回类型是 `DataAccessException`，契约上只能把一个数据访问异常转成另一个，转不出业务异常，照着写会编译不过。

正确做法是在 MyBatis `Interceptor`（plugin）里 catch `PersistenceException`，识别 SQLState `23505` 后抛 `BusinessRuntimeException(CommCode.OBJECT_IS_EXIST)`。`SqlSessionTemplate` 对非 `PersistenceException` 的 `RuntimeException` 原样抛出，业务异常能穿出去。这条路径同时覆盖事务内的 Service 调用与非 HTTP 场景（后台任务、JobRunr worker）。放在 `MybatisConstraintInterceptor` 里，与 `SqlTraceInnerInterceptor` 平级。

JDBC 路径（`TenantAwareJdbcTemplate`）抛的是 Spring 的 `DuplicateKeyException`，不经过 MyBatis 拦截器。它的兜底处理器在 `GlobalExceptionHandler` 里，随 Task 7 一起创建并测试，本任务不碰那个文件。

- [ ] **Step 4: 运行，确认通过**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl framework -am test -Dtest='MybatisExtensionTest,EncryptKeyValidatorTest'`
Expected: PASS，`MybatisExtensionTest` 5 个、`EncryptKeyValidatorTest` 3 个用例全绿。

- [ ] **Step 5: 反向验证唯一索引真的存在**

把 `tenant-unique-index.sql` 里的 `CREATE UNIQUE INDEX` 临时注释掉，重跑 `uniqueKeyStillRejectsDuplicateLiveRow`。
Expected: FAIL（能写进两条同 phone 的活行）。确认后恢复。

- [ ] **Step 6: Commit**

```bash
git add backend/framework/src/main/java/com/ragagent/framework/mybatis
git add backend/framework/src/main/resources/db/tenant-unique-index.sql
git add backend/framework/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
git add backend/framework/src/test/java/testfixture/support/PostgresContainerTest.java
git add backend/framework/src/test/java/testfixture/framework/mybatis
git add backend/framework/src/test/resources/mybatis-test.sql backend/framework/src/test/resources/mybatis-test-reset.sql
git add backend/framework/src/test/java/com/ragagent/framework/mybatis/MybatisExtensionTest.java \
  backend/framework/src/test/java/com/ragagent/framework/mybatis/EncryptKeyValidatorTest.java
git commit -m "feat(framework): MyBatis 加密字段、逻辑删除唯一键与 SQL 追踪"
```

---

### Task 7: framework Web：上下文、统一异常、CORS 与安全头

**Files:**
- Create: `backend/framework/src/main/java/com/ragagent/framework/web/FilterOrders.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/web/TraceContextFilter.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/web/RepeatableReadRequestFilter.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/web/CorsConfig.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/web/SecurityHeaderFilter.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/web/WebMvcConfig.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/web/error/GlobalExceptionHandler.java`
- Modify: `backend/framework/src/main/java/com/ragagent/framework/tenant/TenantFilterConfig.java`（`MyTenantFilter` 的注册顺序改为引用 `FilterOrders.TENANT`）
- Create: `backend/framework/src/test/java/testfixture/support/FrameworkWebTest.java`
- Create: `backend/framework/src/test/java/testfixture/framework/web/WebFixtureConfig.java`
- Create: `backend/framework/src/test/java/testfixture/framework/web/OrdersController.java`
- Modify: `backend/framework/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（追加 `WebMvcConfig`、`CorsConfig`）
- Test: `backend/framework/src/test/java/com/ragagent/framework/web/WebStackTest.java`
- Test: `backend/framework/src/test/java/com/ragagent/framework/web/TenantWebTest.java`

**Interfaces:**
- Consumes: Task 2（响应体、`Code`、`httpStatus()`、`FrameworkCode`）、Task 4（`I18nUtil`、`FrameworkI18nConfig`）、Task 5（`TenantContextMissingException`、`TenantLineConfig`、`TenantFilterConfig`、`MyTenantFilter`、`OrdersRepository` 夹具）、Task 6（`MybatisConstraintInterceptor` 已把 MyBatis 路径的唯一键冲突转成业务异常）
- Produces:
  - `FilterOrders`（常量类）：`CORS = Ordered.HIGHEST_PRECEDENCE + 10`、`TRACE = Ordered.HIGHEST_PRECEDENCE + 20`、`REPEATABLE_READ = -100`、`SECURITY_HEADERS = -70`、`TENANT = -50`。Spring Boot 的 `RequestContextFilter` 顺序是 `-105`，租户过滤器必须排在它之后才能读到登录会话。Task 5 的 `MyTenantFilter` 注册时引用 `FilterOrders.TENANT`，该常量本任务才创建，所以 Task 5 的实现先用字面量 `-50` 并留一行中文注释说明原因，本任务把它改成引用常量
  - `@interface FrameworkWebTest`（测试夹具，`testfixture.support`）：`@Retention(RUNTIME)`，带 `@ImportAutoConfiguration` 引入 `ServletWebServerFactoryAutoConfiguration`、`DispatcherServletAutoConfiguration`、`WebMvcAutoConfiguration`、`HttpMessageConvertersAutoConfiguration`、`ValidationAutoConfiguration`、`JacksonAutoConfiguration`。Task 8 的测试也用它
  - `TraceContextFilter`：请求头 `X-Request-Id` 缺失时生成，写入 MDC 的 `tracerId` 与响应头同名
  - `GlobalExceptionHandler`：一个 `@RestControllerAdvice` 内分多个 `@ExceptionHandler`，覆盖 spec 5.3 的「分类异常处理器」：

    | 处理器 | 覆盖 | HTTP 状态 | 响应 code |
    |---|---|---|---|
    | `handleBiz` | `BusinessRuntimeException` 及其全部子类，含 `TenantContextMissingException` | `code.httpStatus()` | `code.getCode()` |
    | `handleDuplicateKey` | Spring 的 `DuplicateKeyException`（`TenantAwareJdbcTemplate` 等 JDBC 路径抛出，MyBatis 路径已由 Task 6 转换） | 400 | `6002` |
    | `handleValidation` | `MethodArgumentNotValidException`、`BindException`、`ConstraintViolationException` | 400 | `4400`，`msg` 里带出错的字段路径 |
    | `handleMissingParam` | `MissingServletRequestParameterException`、`MissingPathVariableException` | 400 | `4400` |
    | `handleNotFound` | `NoResourceFoundException`、`NoHandlerFoundException` | 404 | `4404` |
    | `handleMethodNotAllowed` | `HttpRequestMethodNotSupportedException` | 405 | `4405` |
    | `handleTimeout` | `AsyncRequestTimeoutException`（SSE 超时） | 503 | `5500` |
    | `handleOther` | `Exception` | 500 | `5500`，日志带完整堆栈 |
  - 所有处理器返回 `RestResponse`，`msg` 经 `I18nUtil` 渲染；`handleOther` 不回原始异常消息给客户端
  - 配置项 `gac.furnace.web.cors.allowed-origins`（列表，默认空，空则不放行任何跨域）、`gac.furnace.web.security-headers.enabled`（默认 true）

- [ ] **Step 1: 写失败测试**

先建 `FrameworkWebTest` 注解与夹具 `WebFixtureConfig`。`WebFixtureConfig` 是 `@Configuration`，用 `@Bean` 注册夹具控制器（`/test/ok`、`/test/not-found`、`/test/echo`、`/test/validate`、`/test/needs-param`、`/test/boom`、`/test/dup`）与一个 `BodyPeekFilter`。`BodyPeekFilter` 排在 `RepeatableReadRequestFilter` 之后，把请求体读成字符串放进请求属性 `seen`，供 `/test/echo` 回显。夹具控制器行为：

- `GET /test/ok` 返回 `RestResponseBuilder.makeOKRsp("ok")`
- `GET /test/not-found` 抛 `new BusinessRuntimeException(CommCode.OBJECT_NOT_EXIST)`
- `POST /test/echo` 以 `@RequestBody Map<String,Object>` 接收，返回 `{"seen": <属性 seen>, "a": <入参 a>}`
- `POST /test/validate` 接 `@Valid` 的 record，含 `@NotBlank String name`
- `GET /test/needs-param` 要求 `@RequestParam String id`
- `GET /test/boom` 抛 `new IllegalStateException("secret-detail")`
- `GET /test/dup` 抛 `new DuplicateKeyException("x")`

```java
@FrameworkWebTest
@SpringBootTest(classes = {WebMvcConfig.class, CorsConfig.class, FrameworkI18nConfig.class, JacksonConfig.class,
		WebFixtureConfig.class},
		properties = {
			"gac.furnace.web.cors.allowed-origins=https://console.example.com",
			"spring.mvc.throw-exception-if-no-handler-found=true",
			"spring.web.resources.add-mappings=false"})
@AutoConfigureMockMvc
class WebStackTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void businessExceptionRendersI18nInEnvelope() throws Exception {
		mockMvc.perform(get("/test/not-found").header("Accept-Language", "en-US"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("6001"))
			.andExpect(jsonPath("$.msg").value(startsWith("Data does not exist")));
	}

	@Test
	void tracerIdIsGeneratedAndEchoed() throws Exception {
		mockMvc.perform(get("/test/ok"))
			.andExpect(header().exists("X-Request-Id"));
	}

	@Test
	void suppliedTracerIdIsPreserved() throws Exception {
		mockMvc.perform(get("/test/ok").header("X-Request-Id", "abc123"))
			.andExpect(header().string("X-Request-Id", "abc123"));
	}

	@Test
	void corsPreflightIsAnsweredForAllowedOrigin() throws Exception {
		mockMvc.perform(options("/test/ok")
				.header("Origin", "https://console.example.com")
				.header("Access-Control-Request-Method", "POST"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "https://console.example.com"));
	}

	@Test
	void corsPreflightFromUnknownOriginIsRejected() throws Exception {
		mockMvc.perform(options("/test/ok")
				.header("Origin", "https://evil.example.com")
				.header("Access-Control-Request-Method", "POST"))
			.andExpect(status().isForbidden());
	}

	@Test
	void securityHeadersArePresent() throws Exception {
		mockMvc.perform(get("/test/ok"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
	}

	@Test
	void bodyCanBeReadTwiceInFilterAndController() throws Exception {
		mockMvc.perform(post("/test/echo").contentType(APPLICATION_JSON).content("{\"a\":1}"))
			.andExpect(content().json("{\"seen\":\"{\\\"a\\\":1}\",\"a\":1}"));
	}

	@Test
	void validationFailureReturns4400WithFieldName() throws Exception {
		mockMvc.perform(post("/test/validate").contentType(APPLICATION_JSON).content("{\"name\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("4400"))
			.andExpect(jsonPath("$.msg").value(containsString("name")));
	}

	@Test
	void missingRequestParamReturns4400() throws Exception {
		mockMvc.perform(get("/test/needs-param"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("4400"));
	}

	@Test
	void unknownPathReturns4404() throws Exception {
		mockMvc.perform(get("/test/no-such-endpoint"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("4404"));
	}

	@Test
	void wrongMethodReturns4405() throws Exception {
		mockMvc.perform(delete("/test/ok"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.code").value("4405"));
	}

	@Test
	void unhandledExceptionReturns500WithoutLeakingMessage() throws Exception {
		mockMvc.perform(get("/test/boom"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("5500"))
			.andExpect(jsonPath("$.msg").value(not(containsString("secret-detail"))));
	}

	@Test
	void springDuplicateKeyExceptionReturns6002() throws Exception {
		mockMvc.perform(get("/test/dup"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("6002"));
	}
}
```

租户相关的 HTTP 级测试单独成类，因为它需要真实 PostgreSQL。`OrdersController` 夹具：`GET /test/orders` 调用 Task 5 的 `OrdersRepository.findContents()` 并返回列表。`orders` 表与种子数据由基类的 `execAsOwner("/tenant-test.sql")` 与 `execAsOwner("/tenant-test-reset.sql")` 在 `@BeforeAll` 建好（见 Task 5）。

```java
@FrameworkWebTest
@SpringBootTest(classes = {TenantLineConfig.class, TenantFilterConfig.class, WebMvcConfig.class, FrameworkI18nConfig.class,
		JacksonConfig.class, OrdersFixtureConfig.class, OrdersController.class},
		properties = "gac.furnace.tenant.trust-header=true")
@AutoConfigureMockMvc
class TenantWebTest extends PostgresContainerTest {

	@Autowired
	private MockMvc mockMvc;

	@BeforeAll
	static void createTables() throws Exception {
		execAsOwner("/tenant-test.sql");
		execAsOwner("/tenant-test-reset.sql");
	}

	@Test
	void missingTenantContextReturns400WithReadableMessage() throws Exception {
		mockMvc.perform(get("/test/orders").header("Accept-Language", "en-US"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("1001001001"))
			.andExpect(jsonPath("$.msg").value(startsWith("Tenant context is missing")));
	}

	@Test
	void missingTenantContextIsRenderedInChineseByDefault() throws Exception {
		mockMvc.perform(get("/test/orders"))
			.andExpect(jsonPath("$.code").value("1001001001"))
			.andExpect(jsonPath("$.msg").value(startsWith("缺少租户上下文")));
	}

	@Test
	void nonNumericTenantHeaderReturns1001001002() throws Exception {
		mockMvc.perform(get("/test/orders").header("X-Tenant-Id", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("1001001002"));
	}

	@Test
	void numericTenantHeaderScopesRowsWhenTrusted() throws Exception {
		mockMvc.perform(get("/test/orders").header("X-Tenant-Id", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0]").value("a1"));
	}
}
```

- [ ] **Step 2: 运行，确认失败**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl framework -am test -Dtest='WebStackTest,TenantWebTest'`
Expected: FAIL，类不存在。

- [ ] **Step 3: 实现**

`TraceContextFilter` 用 `MDC.putCloseable` 保证清理。

`RepeatableReadRequestFilter` 必须**子类化** `ContentCachingRequestWrapper` 并覆盖 `getInputStream()`/`getReader()`。校验确认：`ContentCachingRequestWrapper` 的 `getInputStream()` 每次返回**同一个**已耗尽的流，它只把下游读走的字节记进 `cachedContent`，本身不能重放；只有 `getContentAsByteArray()` 能拿到全量。所以要让过滤器和控制器各读一次，必须在过滤器进入时一次性读完请求体，之后每次 `getInputStream()` 从缓存字节新建流返回。

`CorsConfig` 的 `CorsFilter` 注册顺序用 `FilterOrders.CORS`，在鉴权之前，预检请求直接返回，不进鉴权链。

`SecurityHeaderFilter` 追加 `X-Content-Type-Options: nosniff`、`X-Frame-Options: SAMEORIGIN`、`Referrer-Policy: no-referrer`。**不加** CSP：挂件以 iframe 被宿主页加载，CSP 归属在挂件侧（本计划未覆盖，留给挂件计划）。

`WebMvcConfig` 是 `@AutoConfiguration`，用 `@Import(GlobalExceptionHandler.class)` 装入异常处理器，并以 `@Bean` 注册 `TraceContextFilter`、`RepeatableReadRequestFilter`、`SecurityHeaderFilter` 的 `FilterRegistrationBean`，顺序取 `FilterOrders`。回头把 Task 5 的 `TenantFilterConfig` 里 `MyTenantFilter` 的注册改成引用 `FilterOrders.TENANT`。

Spring 默认把 `NoResourceFoundException` 交给默认处理器返回 404 空体，`NoHandlerFoundException` 需要在配置里打开 `spring.mvc.throw-exception-if-no-handler-found=true` 才会抛。两者都要显式声明处理，否则前端拿到的是空体而不是 `RestResponse`。app 的 `application.yml` 在 Task 11b 打开该开关与 `spring.web.resources.add-mappings=false`。

`MyTenantFilter` 遇到非数字租户头（`trust-header=true` 时）不直接写响应，而是调用 `HandlerExceptionResolver`（Bean 名 `handlerExceptionResolver`）解析 `BusinessRuntimeException(FrameworkCode.TENANT_HEADER_INVALID)`，让异常进入 `GlobalExceptionHandler` 统一渲染。这一行为的实现在 Task 5，测试在本任务的 `TenantWebTest`。

- [ ] **Step 4: 运行，确认通过**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl framework -am test -Dtest='WebStackTest,TenantWebTest'`
Expected: PASS，`WebStackTest` 13 个、`TenantWebTest` 4 个用例全绿。

- [ ] **Step 5: 回归整个 framework 模块**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl framework -am test`
Expected: BUILD SUCCESS，Task 2 到 Task 7 的全部用例通过。

- [ ] **Step 6: Commit**

```bash
git add backend/framework/src/main/java/com/ragagent/framework/web
git add backend/framework/src/main/java/com/ragagent/framework/tenant/TenantFilterConfig.java
git add backend/framework/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
git add backend/framework/src/test/java/testfixture/support/FrameworkWebTest.java
git add backend/framework/src/test/java/testfixture/framework/web
git add backend/framework/src/test/java/com/ragagent/framework/web/WebStackTest.java \
  backend/framework/src/test/java/com/ragagent/framework/web/TenantWebTest.java
git commit -m "feat(framework): 链路上下文、统一异常、CORS 与安全响应头"
```

---

### Task 8: framework 安全：Sa-Token 三账号体系

**Files:**
- Create: `backend/framework/src/main/java/com/ragagent/framework/security/SecurityConstants.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/security/AdminStpLogic.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/security/VisitorStpLogic.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/security/OpenStpLogic.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/security/SaTokenConfig.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/security/SaRouteRules.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/security/SaTokenAccountProvider.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/security/SaTokenTenantResolver.java`
- Create: `backend/framework/src/test/java/testfixture/framework/security/SecurityFixtureConfig.java`
- Modify: `backend/framework/src/main/java/com/ragagent/framework/web/error/GlobalExceptionHandler.java`（追加未登录与无权限两个处理器）
- Modify: `backend/framework/src/main/java/com/ragagent/framework/mybatis/FrameworkMetaObjectHandler.java`（回填 Task 6 的 TODO）
- Modify: `backend/framework/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（追加 `SaTokenConfig`）
- Test: `backend/framework/src/test/java/com/ragagent/framework/security/SaTokenAccountsTest.java`

**Interfaces:**
- Consumes: Task 5 的 `TenantResolver`、`TenantFilterConfig`、`TenantContextHolder`；Task 6 的 `FrameworkMetaObjectHandler`；Task 7 的 `GlobalExceptionHandler`、`FrameworkWebTest`、`CorsConfig`
- Produces:
  - `SecurityConstants`：`LOGIN_TYPE_ADMIN = "admin"`、`LOGIN_TYPE_VISITOR = "visitor"`、`LOGIN_TYPE_OPEN = "open"`、`TENANT_ID_ATTR = "tenantId"`、`TOKEN_PREFIX = "Bearer"`、`ADMIN_PREFIX = "/api/admin/v1"`、`VISITOR_PREFIX = "/api/v1"`、`OPEN_PREFIX = "/api/open/v1"`、`CHANNEL_CALLBACK_PATH = "/api/open/v1/channels/*/callbacks"`
  - `AdminStpLogic`、`VisitorStpLogic`、`OpenStpLogic`：三个 `StpLogic` 的 `@Bean`，loginType 取上面三个常量
  - 路由约定按 spec 5.6 第 1 条严格一对一：`/api/admin/v1/**` 只认 admin，`/api/v1/**` 只认 visitor，`/api/open/v1/**` 只认 open，三组前缀互不授权。后台登录用户用到的所有接口（含控制台问答、会话、记忆、收藏）归 `/api/admin/v1`，`/api/v1` 只给访客身份（路线图 3.6）
  - 未匹配上面三组前缀、也不在 `permit-paths` 里的路由，一律要求 admin 登录（fail-closed）
  - `SaRouteRules`：`static String loginTypeOf(String path)`，返回 `admin`、`visitor`、`open` 或 `null`（路径不属于三组前缀）；`static boolean isPermitted(String path, List<String> permitPaths)`
  - **渠道回调的内置 permit 例外**：`SaRouteRules.isPermitted` 先匹配 `SecurityConstants.CHANNEL_CALLBACK_PATH`，命中即放行，再匹配 `permit-paths`。该例外不依赖配置，`permit-paths` 被覆盖或清空时它仍在。原因：平台服务器直调回调接口，没有 Sa-Token，验签由渠道域自己做（计划 07）。Ant 匹配里 `*` 只匹配单段，所以只放行 `/api/open/v1/channels/{channelId}/callbacks` 这一种形状，多一段或少一段都不放行。`loginTypeOf` 对该路径仍返回 `open`，不受影响。例外只对该路径生效，不放宽开放接口组的其他路径，也不放宽 `admin`、`visitor` 两组
  - `SaTokenAccountProvider`：
    - `void bindTenant(String loginType, String token, Long tenantId)`：把租户 ID 写进该 token 的 Token-Session，属性名 `SecurityConstants.TENANT_ID_ATTR`。登录成功后由登录代码调用。选 Token-Session 而不是账号级 Session，是因为同一账号在多个终端各自切换租户时互不影响
    - `Optional<Long> currentTenant(String loginType)`：读当前请求 token 的 Token-Session，未登录或未绑定返回空，**不抛 `NotLoginException`**
    - `Optional<Long> currentAccountId(String loginType)`：admin 返回登录 ID，visitor 返回访客 ID，open 返回空
  - `SaTokenTenantResolver implements TenantResolver`：按请求路径取 `loginTypeOf`，路径不属于三组前缀则返回空，否则返回 `currentTenant(loginType)`。`SaTokenConfig` 以 `@ConditionalOnMissingBean(TenantResolver.class)` 注册它
  - 一个默认的 `StpInterface`（权限与角色均返回空列表，`@ConditionalOnMissingBean`），保证 `@SaCheckPermission` 能抛出 `NotPermissionException`。真实权限数据由计划 03 的 auth 域提供
  - 配置项 `gac.furnace.security.enabled`（默认 true）、`gac.furnace.security.permit-paths`（Ant 风格列表，默认 `/actuator/health,/v3/api-docs/**,/swagger-ui/**`）。匿名端点（登录、访客换 token、挂件引导）一律登记在 app 的 `application.yml` 里，域计划追加
  - token 读取：请求头 `Authorization`，值必须带 `Bearer ` 前缀，由 Sa-Token 的 `token-prefix` 配置裁剪
  - token 存储此处用 Sa-Token 默认的进程内存。多实例共享用 Redis DAO，由计划 03 的 auth 域接入

- [ ] **Step 1: 写失败测试**

`SecurityFixtureConfig` 是夹具配置，用 `@Bean` 注册九个夹具端点，路径与注解：

| 路径 | 注解 | 返回 |
|---|---|---|
| `GET /api/admin/v1/test` | `@SaCheckLogin(type = "admin")` | `makeOKRsp("ok")` |
| `GET /api/admin/v1/restricted` | `@SaCheckPermission(type = "admin", value = "test:restricted")` | `makeOKRsp("ok")` |
| `GET /api/admin/v1/whoami` | `@SaCheckLogin(type = "admin")` | `makeOKRsp(String.valueOf(TenantContextHolder.get()))` |
| `GET /api/v1/test` | `@SaCheckLogin(type = "visitor")` | `makeOKRsp("ok")` |
| `GET /api/open/v1/test` | 无，由拦截器按 open 组校验 | `makeOKRsp("ok")` |
| `GET /api/open/v1/channels/{channelId}/callbacks` | 无，靠内置 permit 例外放行 | `makeOKRsp("ok")` |
| `POST /api/open/v1/channels/{channelId}/callbacks` | 无，同上 | `makeOKRsp("ok")` |
| `GET /other/ping` | 无 | `makeOKRsp("ok")` |
| `GET /test/open` | 无，路径在测试的 `permit-paths` 里 | `makeOKRsp("ok")` |

```java
@FrameworkWebTest
@SpringBootTest(classes = {SaTokenConfig.class, TenantFilterConfig.class, WebMvcConfig.class, CorsConfig.class,
		FrameworkI18nConfig.class, JacksonConfig.class, SecurityFixtureConfig.class},
		properties = {
			"gac.furnace.security.permit-paths=/test/open",
			"gac.furnace.web.cors.allowed-origins=https://console.example.com"})
@AutoConfigureMockMvc
class SaTokenAccountsTest {

	private static final String ADMIN_PREFIX = "/api/admin/v1";
	private static final String C_PREFIX = "/api/v1";
	private static final String BEARER = "Bearer ";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SaTokenAccountProvider accounts;

	private String newToken(String loginType, long id) {
		return SaManager.getStpLogic(loginType).createLoginSession(id);
	}

	@Test
	void adminRouteWithoutTokenReturns4402Envelope() throws Exception {
		mockMvc.perform(get(ADMIN_PREFIX + "/test"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("4402"))
			.andExpect(jsonPath("$.success").value(false));
	}

	@Test
	void bareTokenWithoutBearerPrefixIsTreatedAsNotLoggedIn() throws Exception {
		String token = newToken("admin", 1L);
		mockMvc.perform(get(ADMIN_PREFIX + "/test").header("Authorization", token))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("4402"));
	}

	@Test
	void visitorTokenCannotAccessAdminRoute() throws Exception {
		mockMvc.perform(get(ADMIN_PREFIX + "/test").header("Authorization", BEARER + newToken("visitor", 9L)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void adminTokenCannotAccessCEndRoute() throws Exception {
		mockMvc.perform(get(C_PREFIX + "/test").header("Authorization", BEARER + newToken("admin", 1L)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void adminTokenPassesAdminRoute() throws Exception {
		mockMvc.perform(get(ADMIN_PREFIX + "/test").header("Authorization", BEARER + newToken("admin", 1L)))
			.andExpect(status().isOk());
	}

	@Test
	void visitorTokenPassesCEndRoute() throws Exception {
		mockMvc.perform(get(C_PREFIX + "/test").header("Authorization", BEARER + newToken("visitor", 9L)))
			.andExpect(status().isOk());
	}

	@Test
	void missingPermissionReturns6041() throws Exception {
		mockMvc.perform(get(ADMIN_PREFIX + "/restricted").header("Authorization", BEARER + newToken("admin", 1L)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("6041"));
	}

	@Test
	void unmatchedRouteRequiresAdminLogin() throws Exception {
		mockMvc.perform(get("/other/ping"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("4402"));
	}

	@Test
	void permitPathsAreOpenWithoutToken() throws Exception {
		mockMvc.perform(get("/test/open")).andExpect(status().isOk());
	}

	@Test
	void channelCallbackPathIsNotBlockedBySaToken() throws Exception {
		mockMvc.perform(get("/api/open/v1/channels/42/callbacks")).andExpect(status().isOk());
		mockMvc.perform(post("/api/open/v1/channels/42/callbacks")).andExpect(status().isOk());
	}

	@Test
	void channelCallbackExemptionIsExactShapeOnly() {
		// 夹具里没有这些路径的处理器，请求会先得到 404，到不了拦截器，所以形状判定直接测规则本身
		assertThat(SaRouteRules.isPermitted("/api/open/v1/channels/42/callbacks", List.of())).isTrue();
		assertThat(SaRouteRules.isPermitted("/api/open/v1/channels/42/callbacks/extra", List.of())).isFalse();
		assertThat(SaRouteRules.isPermitted("/api/open/v1/channels/42/7/callbacks", List.of())).isFalse();
		assertThat(SaRouteRules.isPermitted("/api/open/v1/test", List.of())).isFalse();
		assertThat(SaRouteRules.isPermitted("/api/admin/v1/channels/42/callbacks", List.of())).isFalse();
		assertThat(SaRouteRules.isPermitted("/api/v1/channels/42/callbacks", List.of())).isFalse();
		assertThat(SaRouteRules.loginTypeOf("/api/open/v1/channels/42/callbacks")).isEqualTo("open");
	}

	@Test
	void otherOpenRoutesStillRequireOpenToken() throws Exception {
		mockMvc.perform(get("/api/open/v1/test"))
			.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("4402"));
		mockMvc.perform(get("/api/open/v1/test").header("Authorization", BEARER + newToken("admin", 1L)))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/open/v1/test").header("Authorization", BEARER + newToken("open", 5L)))
			.andExpect(status().isOk());
	}

	@Test
	void corsPreflightIsNotBlockedByAuth() throws Exception {
		mockMvc.perform(options(ADMIN_PREFIX + "/test")
				.header("Origin", "https://console.example.com")
				.header("Access-Control-Request-Method", "GET"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "https://console.example.com"));
	}

	@Test
	void tenantComesFromLoginSession() throws Exception {
		String token = newToken("admin", 1L);
		accounts.bindTenant("admin", token, 7L);
		mockMvc.perform(get(ADMIN_PREFIX + "/whoami").header("Authorization", BEARER + token))
			.andExpect(jsonPath("$.data").value("7"));
	}

	@Test
	void forgedTenantHeaderIsIgnored() throws Exception {
		String token = newToken("admin", 1L);
		accounts.bindTenant("admin", token, 7L);
		mockMvc.perform(get(ADMIN_PREFIX + "/whoami")
				.header("Authorization", BEARER + token).header("X-Tenant-Id", "99"))
			.andExpect(jsonPath("$.data").value("7"));
	}

	@Test
	void unboundTenantMeansEmptyContext() throws Exception {
		mockMvc.perform(get(ADMIN_PREFIX + "/whoami").header("Authorization", BEARER + newToken("admin", 2L)))
			.andExpect(jsonPath("$.data").value("null"));
	}
}
```

- [ ] **Step 2: 运行，确认失败**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl framework -am test -Dtest=SaTokenAccountsTest`
Expected: FAIL，类不存在。

- [ ] **Step 3: 实现**

三个 `StpLogic` 用 `StpLogic("admin")` 等构造，注册为 `@Bean`（`StpLogic` 构造器自动 `SaManager.putStpLogic(this)`）。测试里取实例用 `SaManager.getStpLogic("admin")`。**`StpUtil.getStpLogic(String)` 不存在**，`StpUtil` 只有无参版本；校验已确认。

**剥 `Bearer ` 前缀走配置，不重写 `getTokenValue()`。** 校验确认登录主路径 `getLoginId()`/`checkLogin()` 调的是 `getTokenValue(true)`，它直接走 `getTokenValueNotCut()`，不经过无参 `getTokenValue()`，只重写后者对鉴权无效。`SaTokenConfig` 里构造 Sa-Token 自己的 `cn.dev33.satoken.config.SaTokenConfig` Bean（与本类同名，import 用全限定名；整包应用里 starter 已提供同名 Bean 时改为 `@ConditionalOnMissingBean` 条件装配，取值统一在 `afterSingletonsInstantiated` 写入，见 deviations）：`tokenName=Authorization`、`tokenPrefix=Bearer`、`isReadHeader=true`、`isReadCookie=false`、`isReadBody=false`。Sa-Token 文档明确：配了前缀后，前端提交 token 必须带前缀，否则读不到，这正是 `bareTokenWithoutBearerPrefixIsTreatedAsNotLoggedIn` 要钉住的行为。

配置方式的代价：`token-prefix` 对所有 `StpLogic` 生效，因此前端必须统一发 `Bearer <token>`。Step 3 期间用 `grep -n "Authorization" frontend/src/utils/request.ts` 确认旧系统前端现在的发送格式；若旧值是裸 token 无前缀，在 `deviations.md` 登记，新前端 API 层（计划 03）按 `Bearer` 发送。

`SaTokenConfig` 注册 `SaInterceptor`，匹配全部路径 `/**`，处理函数按顺序：OPTIONS 请求直接放行；命中 `SaRouteRules.isPermitted`（内置渠道回调例外加 `permit-paths`）放行；`/api/admin/v1/**` 调 admin 的 `checkLogin()`；`/api/v1/**` 调 visitor 的；`/api/open/v1/**` 调 open 的；其余调 admin 的（fail-closed）。注解鉴权（`@SaCheckLogin`、`@SaCheckPermission`）沿用 Sa-Token 默认行为。

`GlobalExceptionHandler` 追加两个处理器：`NotLoginException` 返回 401 加 `CommCode.NOLOGIN`（4402）；`NotPermissionException` 与 `NotRoleException` 返回 403 加 `CommCode.PERMIT_ERROR`（6041）。拦截器里抛出的异常经 `HandlerExceptionResolver` 进入 `@RestControllerAdvice`。

`SaTokenTenantResolver` 在 `MyTenantFilter` 里被调用，此时 Sa-Token 的上下文来自 `RequestContextHolder`，所以过滤器顺序必须晚于 `RequestContextFilter`（见 Task 7 的 `FilterOrders.TENANT = -50`）。`currentTenant` 内部先判断 `stpLogic.isLogin()`，未登录直接返回空，不让 `NotLoginException` 逃出过滤器。

`FrameworkMetaObjectHandler`：删除 Task 6 留下的那条 `TODO Task 8` 注释，`create_by`/`update_by` 取 `SaTokenAccountProvider.currentAccountId(loginType)`，`loginType` 由 `SaRouteRules.loginTypeOf` 按请求路径得出，无请求上下文（后台任务）时不填。这是全计划唯一允许的 TODO，到此清零。

- [ ] **Step 4: 运行，确认通过**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl framework -am test -Dtest=SaTokenAccountsTest`
Expected: PASS，16 个用例全绿。

- [ ] **Step 5: 回归整个 framework 模块并确认 TODO 已清零**

Run:
```bash
mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl framework -am test
grep -rn "TODO" backend/framework/src/main/java || echo "no TODO"
```
Expected: BUILD SUCCESS；`no TODO`。

- [ ] **Step 6: Commit**

```bash
git add backend/framework/src/main/java/com/ragagent/framework/security
git add backend/framework/src/main/java/com/ragagent/framework/web/error/GlobalExceptionHandler.java
git add backend/framework/src/main/java/com/ragagent/framework/mybatis/FrameworkMetaObjectHandler.java
git add backend/framework/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
git add backend/framework/src/test/java/testfixture/framework/security
git add backend/framework/src/test/java/com/ragagent/framework/security/SaTokenAccountsTest.java
git commit -m "feat(framework): Sa-Token 三账号体系、路由规则与租户会话解析"
```

---

### Task 9: framework 租户客户端注册表与 OSS

**Files:**
- Create: `backend/framework/src/main/java/com/ragagent/framework/tenant/TenantConfigSource.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/tenant/TenantClientRegistry.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/oss/OssTemplate.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/oss/OssConfig.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/oss/S3OssTemplate.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/oss/OssClients.java`
- Test: `backend/framework/src/test/java/com/ragagent/framework/tenant/TenantClientRegistryTest.java`
- Test: `backend/framework/src/test/java/com/ragagent/framework/oss/OssTemplateTest.java`

**Interfaces:**
- Consumes: 无
- Produces:
  - `interface TenantConfigSource<C> { String version(Long tenantId); C load(Long tenantId); }`：`version` 是**轻量**查询，例如只查配置行的 `updated_at` 或版本号列，不加载整条配置；`load` 才加载完整配置
  - `class TenantClientRegistry<C, T>`：构造器 `(TenantConfigSource<C> source, Function<C, T> factory, Consumer<T> closer)`；方法 `T forTenant(Long tenantId)`。行为：先调 `source.version(tenantId)` 得到版本，缓存键 `tenantId + ":" + version`；命中则直接返回缓存的客户端；未命中才调 `source.load(tenantId)`，再用 `factory` 构建并缓存。用 Caffeine，`expireAfterAccess(30, MINUTES)`，被淘汰或被新版本顶替的旧客户端由 `closer` 关闭。同一键并发构建只构建一次
  - `interface OssTemplate { void put(String key, InputStream in, long size, String contentType); InputStream get(String key); String presignGet(String key, Duration ttl); String presignPut(String key, Duration ttl); void delete(String key); boolean exists(String key); }`
  - `record OssConfig(String endpoint, String region, String accessKey, String secretKey, String bucket, String cdnDomain, String version)`，另有 `OssConfig withVersion(String version)` 返回新实例
  - `class S3OssTemplate implements OssTemplate, AutoCloseable`，构造器 `(OssConfig config)`，另有 `void createBucketIfAbsent()` 供测试与 storage 域初始化使用
  - `class OssClients`：构造器 `(TenantConfigSource<OssConfig> source)`，内部持有一个 `TenantClientRegistry<OssConfig, S3OssTemplate>`，方法 `OssTemplate forTenant(Long tenantId)`。配置来源由 app 的 storage 域实现 `TenantConfigSource<OssConfig>`

本任务只提供注册表与 OSS 实现。配置覆盖顺序（yml、`system_settings`、租户配置）由 system 域在计划 03 实现。「配置变更后下一个请求即生效」用每次先查一次 `version()` 实现，不依赖 Redis 失效广播，广播推迟。model、storage、vectorstore、websearch 各域后续都用同一个注册表，不各自再写一套缓存。

存储后端的种类收敛（spec 5.3 的「五种后端」）不在本任务内。现有五种后端中阿里云 OSS、腾讯云 COS、火山 TOS 都兼容 S3 协议，`S3OssTemplate` 一套即可覆盖；本地目录存储等 storage 域迁入时补。

- [ ] **Step 1: 写失败测试**

注册表的行为用纯单元测试覆盖，不起容器：

```java
class TenantClientRegistryTest {

	private final AtomicInteger versionCalls = new AtomicInteger();
	private final AtomicInteger loadCalls = new AtomicInteger();
	private final AtomicReference<String> version = new AtomicReference<>("v1");
	private final List<String> closed = new CopyOnWriteArrayList<>();

	private TenantClientRegistry<String, String> registry() {
		TenantConfigSource<String> source = new TenantConfigSource<>() {
			@Override
			public String version(Long tenantId) {
				versionCalls.incrementAndGet();
				return version.get();
			}

			@Override
			public String load(Long tenantId) {
				loadCalls.incrementAndGet();
				return "cfg-" + tenantId + "-" + version.get();
			}
		};
		return new TenantClientRegistry<>(source, cfg -> "client:" + cfg, closed::add);
	}

	@Test
	void sameTenantAndVersionReusesClientWithoutReloadingConfig() {
		TenantClientRegistry<String, String> registry = registry();
		String first = registry.forTenant(1L);
		String again = registry.forTenant(1L);
		assertThat(again).isSameAs(first);
		assertThat(loadCalls.get()).isEqualTo(1);
		assertThat(versionCalls.get()).isEqualTo(2);
	}

	@Test
	void differentTenantsGetSeparateClients() {
		TenantClientRegistry<String, String> registry = registry();
		assertThat(registry.forTenant(1L)).isNotEqualTo(registry.forTenant(2L));
		assertThat(loadCalls.get()).isEqualTo(2);
	}

	@Test
	void versionChangeRebuildsClient() {
		TenantClientRegistry<String, String> registry = registry();
		String before = registry.forTenant(1L);
		version.set("v2");
		String after = registry.forTenant(1L);
		assertThat(after).isNotEqualTo(before);
		assertThat(after).isEqualTo("client:cfg-1-v2");
	}

	@Test
	void replacedClientIsClosed() {
		TenantClientRegistry<String, String> registry = registry();
		String before = registry.forTenant(1L);
		version.set("v2");
		registry.forTenant(1L);
		registry.cleanUp();
		assertThat(closed).contains(before);
	}

	@Test
	void concurrentRequestsForSameKeyBuildOnce() throws Exception {
		TenantClientRegistry<String, String> registry = registry();
		ExecutorService pool = Executors.newFixedThreadPool(8);
		List<Future<String>> results = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			results.add(pool.submit(() -> registry.forTenant(1L)));
		}
		for (Future<String> f : results) {
			f.get(10, TimeUnit.SECONDS);
		}
		pool.shutdown();
		assertThat(loadCalls.get()).isEqualTo(1);
	}
}
```

`TenantClientRegistry` 需要暴露包可见或 public 的 `cleanUp()`，转调 Caffeine 的 `cleanUp()`，让淘汰回调在测试里同步触发。

OSS 实现用 LocalStack 的 S3 容器验证（MinIO 官方镜像已要求登录、匿名拉取 401，见 deviations）：

```java
@Testcontainers
class OssTemplateTest {

	@Container
	static final GenericContainer<?> S3 = new GenericContainer<>("localstack/localstack:3")
		.withExposedPorts(4566)
		.withEnv("SERVICES", "s3")
		.withEnv("EAGER_SERVICE_LOADING", "1")
		.waitingFor(Wait.forLogMessage(".*Ready\\..*", 1).withStartupTimeout(Duration.ofMinutes(5)));

	private S3OssTemplate oss;

	@BeforeEach
	void setUp() {
		oss = new S3OssTemplate(new OssConfig(
			"http://" + S3.getHost() + ":" + S3.getMappedPort(4566),
			"us-east-1", "test", "test12345", "ragagent-test", null, "v1"));
		oss.createBucketIfAbsent();
	}

	@AfterEach
	void tearDown() {
		oss.close();
	}

	@Test
	void putThenGetReturnsSameBytes() throws Exception {
		byte[] payload = "hello".getBytes(UTF_8);
		oss.put("a/b.txt", new ByteArrayInputStream(payload), payload.length, "text/plain");
		assertThat(oss.get("a/b.txt").readAllBytes()).isEqualTo(payload);
	}

	@Test
	void existsReflectsPutAndDelete() {
		assertThat(oss.exists("c.txt")).isFalse();
		oss.put("c.txt", new ByteArrayInputStream(new byte[]{1}), 1, "text/plain");
		assertThat(oss.exists("c.txt")).isTrue();
		oss.delete("c.txt");
		assertThat(oss.exists("c.txt")).isFalse();
	}

	@Test
	void presignedGetUrlIsSignedAndReachable() throws Exception {
		oss.put("d.txt", new ByteArrayInputStream("x".getBytes(UTF_8)), 1, "text/plain");
		String url = oss.presignGet("d.txt", Duration.ofMinutes(5));
		assertThat(url).contains("X-Amz-Signature");
		HttpResponse<String> rsp = HttpClient.newHttpClient()
			.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString());
		assertThat(rsp.statusCode()).isEqualTo(200);
		assertThat(rsp.body()).isEqualTo("x");
	}

	@Test
	void clientsFacadeBuildsPerTenantTemplateFromConfigSource() {
		AtomicInteger loads = new AtomicInteger();
		TenantConfigSource<OssConfig> source = new TenantConfigSource<>() {
			@Override
			public String version(Long tenantId) {
				return "v1";
			}

			@Override
			public OssConfig load(Long tenantId) {
				loads.incrementAndGet();
				return new OssConfig("http://" + S3.getHost() + ":" + S3.getMappedPort(4566),
					"us-east-1", "test", "test12345", "ragagent-test", null, "v1");
			}
		};
		OssClients clients = new OssClients(source);
		assertThat(clients.forTenant(1L)).isSameAs(clients.forTenant(1L));
		assertThat(loads.get()).isEqualTo(1);
	}
}
```

`createBucketIfAbsent()` 是 `S3OssTemplate` 上的 public 方法，只在测试与 storage 域初始化时用，生产写入路径不调用它。

- [ ] **Step 2: 运行，确认失败**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl framework -am test -Dtest='TenantClientRegistryTest,OssTemplateTest'`
Expected: FAIL，类不存在。

- [ ] **Step 3: 实现**

`S3OssTemplate` 用 AWS SDK v2 `S3Client` + `S3Presigner`，`endpointOverride` 支持 MinIO 与 S3 兼容服务，`forcePathStyle(true)`，`close()` 关闭两者。

`TenantClientRegistry` 用 Caffeine，`expireAfterAccess(30, MINUTES)`，`removalListener` 里调 `closer`。`forTenant` 每次先调 `source.version(tenantId)` 得到版本，再以 `tenantId + ":" + version` 为键 `cache.get(key, k -> factory.apply(source.load(tenantId)))`。同时用一个 `ConcurrentHashMap<Long, String>` 记录每个租户当前的缓存键，出现新版本键时主动 `invalidate` 该租户上一个键，淘汰回调随即调 `closer` 关掉旧客户端。这是「配置变更后下一个请求即生效」的全部实现，不靠 Redis 广播。调用方每次都经 `forTenant` 取客户端，不长期持有引用，否则旧客户端被关闭后会失败。

`OssClients` 只是对 `TenantClientRegistry<OssConfig, S3OssTemplate>` 的薄封装，`closer` 为 `S3OssTemplate::close`。

- [ ] **Step 4: 运行，确认通过**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl framework -am test -Dtest='TenantClientRegistryTest,OssTemplateTest'`
Expected: PASS，`TenantClientRegistryTest` 5 个、`OssTemplateTest` 4 个用例全绿。

- [ ] **Step 5: 回归整个 framework 模块**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl framework -am test`
Expected: BUILD SUCCESS。

- [ ] **Step 6: Commit**

```bash
git add backend/framework/src/main/java/com/ragagent/framework/tenant/TenantConfigSource.java \
  backend/framework/src/main/java/com/ragagent/framework/tenant/TenantClientRegistry.java
git add backend/framework/src/main/java/com/ragagent/framework/oss
git add backend/framework/src/test/java/com/ragagent/framework/tenant/TenantClientRegistryTest.java \
  backend/framework/src/test/java/com/ragagent/framework/oss/OssTemplateTest.java
git commit -m "feat(framework): 租户客户端注册表与 OSS 模板"
```

---

### Task 10: framework Redis：分布式锁

**Files:**
- Create: `backend/framework/src/main/java/com/ragagent/framework/redis/DistributedLock.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/redis/DistributedLockAspect.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/redis/DistributedLockKeyResolver.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/redis/DefaultDistributedLockKeyResolver.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/redis/RedissonConfig.java`
- Modify: `backend/framework/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（追加 `RedissonConfig`）
- Test: `backend/framework/src/test/java/com/ragagent/framework/redis/DistributedLockTest.java`

**Interfaces:**
- Consumes: Task 2 的 `BusinessRuntimeException`、`CommCode.TOO_MANY_REQUESTS`
- Produces:
  - `@interface DistributedLock { String lockName() default ""; int waitTime() default 60; int leaseTime() default 60; TimeUnit timeUnit() default SECONDS; }`
  - `interface DistributedLockKeyResolver { String resolve(DistributedLock lock, Method method, Object[] args); }`
  - `@Aspect class DistributedLockAspect`：构造器 `(RedissonClient redisson, DistributedLockKeyResolver resolver)`，`@Around("@annotation(lock)") Object around(ProceedingJoinPoint point, DistributedLock lock)`
  - `class RedissonConfig`：`@AutoConfiguration`，方法 `RedissonClient redissonClient(RedisProperties props)`，`DistributedLockKeyResolver` 与 `DistributedLockAspect` 的 `@Bean`。`DistributedLockKeyResolver` 的 `@Bean` 方法（返回 `DefaultDistributedLockKeyResolver`）加 `@ConditionalOnMissingBean(DistributedLockKeyResolver.class)`，业务域声明自己的 `DistributedLockKeyResolver` Bean 时框架默认的不装配，`DistributedLockAspect` 注入的就是业务域那一个。计划 05 的 wiki 靠它按 `kbId` 与 `slug` 生成锁键。`DistributedLockAspect` 的 `@Bean` 同样加 `@ConditionalOnMissingBean`，其余不变
  - 不在 `RedissonConfig` 里提供 `@Primary` 变体：业务域的 Bean 与默认 Bean 不会同时存在，`@ConditionalOnMissingBean` 已足够。条件注解对自动配置的评估顺序有要求：`RedissonConfig` 作为自动配置晚于用户配置类，所以用户 Bean 先注册，条件判断才可靠。这是 Task 11 之后域代码用 `@Configuration` 声明覆盖 Bean 的前提
  - 配置项 `gac.furnace.redis.enabled`（默认 true）

两级缓存与失效广播总线不在本任务内，也不在本计划内：首批域迁入不依赖它，租户级配置的即时生效由 Task 9 的版本号查询承担。此处只记录决策，避免提前造一个没人用的缓存抽象。

- [ ] **Step 1: 写失败测试**（Redis 用 Testcontainers）

被测服务通过 `AspectJProxyFactory` 加切面得到代理，这样 `@Around` 路径与 `@DistributedLock` 注解解析都被真实执行，不是手工调内部方法。

```java
@Testcontainers
class DistributedLockTest {

	@Container
	static final GenericContainer<?> REDIS =
		new GenericContainer<>("redis:7.0-alpine").withExposedPorts(6379);

	private static final int TASKS = 8;
	private static final AtomicInteger RUNNING = new AtomicInteger();
	private static final AtomicInteger MAX_CONCURRENT = new AtomicInteger();

	private static RedissonClient client;
	private final DefaultDistributedLockKeyResolver resolver = new DefaultDistributedLockKeyResolver();
	private LockedService service;

	@BeforeAll
	static void connect() {
		RedisProperties props = new RedisProperties();
		props.setHost(REDIS.getHost());
		props.setPort(REDIS.getMappedPort(6379));
		client = new RedissonConfig().redissonClient(props);
	}

	@AfterAll
	static void disconnect() {
		client.shutdown();
	}

	@BeforeEach
	void setUp() {
		RUNNING.set(0);
		MAX_CONCURRENT.set(0);
		AspectJProxyFactory factory = new AspectJProxyFactory(new LockedService());
		factory.addAspect(new DistributedLockAspect(client, resolver));
		service = factory.getProxy();
	}

	@Test
	void sameKeyRunsOneAtATime() throws Exception {
		runAll(i -> "tenant-1");
		assertThat(MAX_CONCURRENT.get()).isEqualTo(1);
		assertThat(RUNNING.get()).isZero();
	}

	@Test
	void differentKeysRunConcurrently() throws Exception {
		runAll(i -> "tenant-" + i);
		assertThat(MAX_CONCURRENT.get()).isGreaterThan(1);
	}

	@Test
	void defaultLockNameDependsOnMethodAndArgs() throws Exception {
		Method method = LockedService.class.getMethod("doWork", String.class);
		DistributedLock ann = method.getAnnotation(DistributedLock.class);
		assertThat(resolver.resolve(ann, method, new Object[]{"a"}))
			.isNotEqualTo(resolver.resolve(ann, method, new Object[]{"b"}));
	}

	@Test
	void explicitLockNameIsUsedLiterally() throws Exception {
		Method method = LockedService.class.getMethod("doWorkWithExplicitName", String.class);
		assertThat(resolver.resolve(method.getAnnotation(DistributedLock.class), method, new Object[]{"a"}))
			.isEqualTo("lock:explicit-name");
	}

	@Test
	void failsFastWhenLockIsHeldByAnotherThread() throws Exception {
		Method method = LockedService.class.getMethod("quick", String.class);
		String key = resolver.resolve(method.getAnnotation(DistributedLock.class), method, new Object[]{"k"});
		RLock held = client.getLock(key);
		held.lock();
		try {
			// 在另一个线程调用，Redisson 锁可重入，同线程会直接拿到锁
			CompletableFuture<Void> call = CompletableFuture.runAsync(() -> service.quick("k"));
			assertThatThrownBy(() -> call.get(10, TimeUnit.SECONDS))
				.hasCauseInstanceOf(BusinessRuntimeException.class)
				.cause().extracting(e -> ((BusinessRuntimeException) e).getCode())
				.isEqualTo(CommCode.TOO_MANY_REQUESTS);
		} finally {
			held.unlock();
		}
	}

	@Test
	void defaultResolverIsRegisteredWhenBusinessDoesNotProvideOne() {
		contextRunner().run(ctx -> {
			assertThat(ctx).hasSingleBean(DistributedLockKeyResolver.class);
			assertThat(ctx.getBean(DistributedLockKeyResolver.class)).isInstanceOf(DefaultDistributedLockKeyResolver.class);
		});
	}

	@Test
	void businessResolverReplacesFrameworkDefault() {
		contextRunner().withUserConfiguration(BusinessResolverConfig.class).run(ctx -> {
			assertThat(ctx).hasSingleBean(DistributedLockKeyResolver.class);
			assertThat(ctx.getBean(DistributedLockKeyResolver.class)).isInstanceOf(BusinessResolver.class);
			assertThat(ctx).doesNotHaveBean(DefaultDistributedLockKeyResolver.class);
			assertThat(ctx).hasSingleBean(DistributedLockAspect.class);
		});
	}

	private ApplicationContextRunner contextRunner() {
		return new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(RedissonConfig.class))
			.withPropertyValues("spring.data.redis.host=" + REDIS.getHost(),
				"spring.data.redis.port=" + REDIS.getMappedPort(6379));
	}

	@Configuration(proxyBeanMethods = false)
	static class BusinessResolverConfig {

		@Bean
		DistributedLockKeyResolver businessResolver() {
			return new BusinessResolver();
		}
	}

	static class BusinessResolver implements DistributedLockKeyResolver {

		@Override
		public String resolve(DistributedLock lock, Method method, Object[] args) {
			return "biz:" + lock.lockName();
		}
	}

	private void runAll(IntFunction<String> keyOf) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(TASKS);
		for (int i = 0; i < TASKS; i++) {
			String key = keyOf.apply(i);
			pool.submit(() -> service.doWork(key));
		}
		pool.shutdown();
		assertThat(pool.awaitTermination(60, SECONDS)).isTrue();
	}

	/** 被测服务：按参数加锁、用固定锁名、1 秒等不到就失败三种。 */
	public static class LockedService {

		@DistributedLock(waitTime = 30, leaseTime = 30)
		public void doWork(String taskKey) {
			work();
		}

		@DistributedLock(lockName = "explicit-name", waitTime = 30, leaseTime = 30)
		public void doWorkWithExplicitName(String taskKey) {
			work();
		}

		@DistributedLock(waitTime = 1, leaseTime = 30)
		public void quick(String taskKey) {
			work();
		}

		private void work() {
			int now = RUNNING.incrementAndGet();
			MAX_CONCURRENT.accumulateAndGet(now, Math::max);
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} finally {
				RUNNING.decrementAndGet();
			}
		}
	}
}
```

`differentKeysRunConcurrently` 依赖 8 个线程同时进入 50 毫秒的临界区，Redis 往返加线程调度在 CI 上偶有不够并发的情况。若该用例在执行机上不稳定，把临界区睡眠加到 200 毫秒，不要放宽断言。

- [ ] **Step 2: 运行，确认失败**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl framework -am test -Dtest=DistributedLockTest`
Expected: FAIL，类不存在。

- [ ] **Step 3: 实现**

`DistributedLockAspect` 用 `@Aspect` 加 `@Around("@annotation(lock)")`，调用 `resolver.resolve(...)` 算 key，`RLock lock = redisson.getLock(key)`，`lock.tryLock(waitTime, leaseTime, timeUnit)`，失败抛 `BusinessRuntimeException(CommCode.TOO_MANY_REQUESTS)`，`finally` 里仅当 `lock.isHeldByCurrentThread()` 时 `unlock()`。

`DefaultDistributedLockKeyResolver`：`lockName` 非空时返回 `"lock:" + lockName` 字面量；为空时返回 `"lock:" + md5(方法全签名 + 逗号拼接的参数 toString)`。

`RedissonConfig` 从 `spring.data.redis.*` 构造 `Config`，类上 `@ConditionalOnProperty(prefix = "gac.furnace.redis", name = "enabled", matchIfMissing = true)`。`enabled=false` 时不注册 `RedissonClient`，`DistributedLockAspect` 也随之不注册；此时标注了 `@DistributedLock` 的方法不会加锁，不做静默降级的提示由调用方负责，本地调试与不需要锁的测试才关它。

两条覆盖测试用 `ApplicationContextRunner` 加 `AutoConfigurations.of(RedissonConfig.class)`，不起完整应用，Redis 仍用同一个 Testcontainers 容器。`businessResolverReplacesFrameworkDefault` 钉住的是「业务 Bean 存在时框架默认 Bean 不进容器」，不是「两个 Bean 并存再靠 `@Primary` 选一个」。

- [ ] **Step 4: 运行，确认通过**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl framework -am test -Dtest=DistributedLockTest`
Expected: PASS，7 个用例全绿。

- [ ] **Step 5: 回归整个 framework 模块**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl framework -am test`
Expected: BUILD SUCCESS。

- [ ] **Step 6: Commit**

```bash
git add backend/framework/src/main/java/com/ragagent/framework/redis
git add backend/framework/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
git add backend/framework/src/test/java/com/ragagent/framework/redis/DistributedLockTest.java
git commit -m "feat(framework): 分布式锁注解"
```

---

### Task 11: app 组合根、Modulith 与 ArchUnit 规则

**Files:**
- Modify: `backend/app/src/main/java/com/ragagent/RagAgentApplication.java`
- Create: `backend/app/src/main/java/com/ragagent/package-info.java`
- Create: `backend/app/src/test/java/com/ragagent/arch/ArchitectureRulesTest.java`
- Create: `backend/app/src/test/java/com/ragagent/arch/ModuleBoundaryTest.java`
- Create: `backend/app/src/main/java/com/ragagent/config/EngineAssemblyConfig.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/FrameworkMarker.java`

**Interfaces:**
- Consumes: Task 1 的主类与三个引擎骨架模块
- Produces:
  - `ArchitectureRulesTest` 与 `ModuleBoundaryTest` 作为后续所有域迁入的自动闸门
  - `com.ragagent.config.EngineAssemblyConfig`：app 唯一的装配点，为引擎模块的 `spi` 接口提供实现并注册 Bean。本计划内它只声明 `@Configuration` 与类级 Javadoc，没有具体 Bean，引擎代码迁入时在同一个类里补
  - `com.ragagent.framework.FrameworkMarker`：**必需**。校验确认 `@Modulithic(sharedModules = "framework")` 里的名字必须对应真实存在的模块，否则 `ApplicationModules.of()` 在构造期就抛 `IllegalArgumentException: Module framework does not exist!`。`package-info.java` 不算类，不能单独撑起一个模块，所以 framework 根包下要有一个真实类型。这个类只有 Javadoc，无成员
  - 包约定：所有装配类必须放在 `com.ragagent.config` 下，ArchUnit 按这个包名放行

- [ ] **Step 1: 写 Modulith 校验测试**

```java
class ModuleBoundaryTest {

	@Test
	void modulesHaveNoCyclesAndRespectDeclaredBoundaries() {
		ApplicationModules modules = ApplicationModules.of(RagAgentApplication.class);
		assertThatCode(modules::verify).doesNotThrowAnyException();
	}
}
```

- [ ] **Step 2: 运行，确认当前通过（此时 app 下还没有业务域）**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl app -am test -Dtest=ModuleBoundaryTest`
Expected: PASS。若报 `Module framework does not exist!`，说明 framework 根包下没有真实类型，加 `FrameworkMarker` 后重跑。若报依赖环，说明 `sharedModules` 没生效，检查注解取值形式（`sharedModules = "framework"` 是合法的单元素数组写法）。

- [ ] **Step 3: 写 ArchUnit 规则测试**

```java
// 规则集必须挂在 @AnalyzeClasses 上才会被 JUnit 5 执行。
// DoNotIncludeTests：只查生产代码。否则测试夹具里的 JdbcTemplate 字段也会被规则命中。
@AnalyzeClasses(packages = "com.ragagent", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

	// 本计划内还没有 Mapper，`allowEmptyShould(true)` 让规则先空跑；有 Mapper 后自动生效。
	@ArchTest
	static final ArchRule mapperOnlyUsedByRepository =
		classes().that().haveSimpleNameEndingWith("Mapper")
			.should().onlyBeAccessed().byClassesThat().haveSimpleNameEndingWith("Repository")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule noAnnotationSqlInMainCode =
		classes().should(new ArchCondition<>("不应使用注解 SQL") {
			@Override
			public void check(JavaClass item, ConditionEvents events) {
				for (JavaAnnotation<JavaClass> a : item.getAnnotations()) {
					String name = a.getRawType().getName();
					if (name.equals("org.apache.ibatis.annotations.Select")
							|| name.equals("org.apache.ibatis.annotations.Update")
							|| name.equals("org.apache.ibatis.annotations.Insert")
							|| name.equals("org.apache.ibatis.annotations.Delete")) {
						events.add(SimpleConditionEvent.violated(item,
							item.getName() + " 使用了注解 SQL，应改写为 Mapper XML"));
					}
				}
			}
		});

	@ArchTest
	static final ArchRule jdbcTemplateAllowedOnlyInTenantAndRetrieval =
		noClasses().that().resideOutsideOfPackages(
				"com.ragagent.framework.tenant..", "com.ragagent.retrieval..")
			.should().dependOnClassesThat().areAssignableTo(JdbcTemplate.class);

	@ArchTest
	static final ArchRule noCrossDomainInternalAccess =
		classes().should(new ArchCondition<>("跨域只能访问对方的 api 包") {
			@Override
			public void check(JavaClass item, ConditionEvents events) {
				String from = domainOf(item.getPackageName());
				if (from == null) {
					return;
				}
				for (JavaAccess<?> access : item.getAccessesFromSelf()) {
					String to = targetDomainOf(access.getTarget().getOwner().getPackageName());
					if (to == null || to.equals(from)) {
						continue;
					}
					events.add(SimpleConditionEvent.violated(access,
						item.getName() + " 直接访问了 " + to + " 域的 " + access.getTarget().getName()));
				}
			}
		});

	/**
	 * 后缀禁用清单取自路线图 3.11。framework 基类不受此限，用包白名单豁免。
	 */
	@ArchTest
	static final ArchRule noGoCompatSuffixes =
		noClasses().that().resideOutsideOfPackage("com.ragagent.framework..")
			.should().haveSimpleNameEndingWith("Ops")
			.orShould().haveSimpleNameEndingWith("Support")
			.orShould().haveSimpleNameEndingWith("Helper")
			.orShould().haveSimpleNameEndingWith("Facade")
			.orShould().haveSimpleNameEndingWith("Manager")
			.orShould().haveSimpleNameEndingWith("Common");

	@ArchTest
	static final ArchRule noRawRequestBodyString =
		methods().that().haveRawParameterTypes(String.class)
			.should(new ArchCondition<JavaMethod>("String 参数不得标 @RequestBody") {
				@Override
				public void check(JavaMethod method, ConditionEvents events) {
					method.getParameters().stream()
						.filter(p -> p.getRawType().isAssignableTo(String.class))
						.filter(p -> p.isAnnotatedWith(RequestBody.class))
						.forEach(p -> events.add(SimpleConditionEvent.violated(p,
							method.getFullName() + " 用 String 参数接请求体")));
				}
			});

	/**
	 * 引擎模块只暴露 api 与 spi。framework 与 app 内部包不受此限。
	 */
	@ArchTest
	static final ArchRule enginesExposeOnlyApiAndSpi =
		classes().that().resideInAnyPackage(
				"com.ragagent.modelruntime..", "com.ragagent.retrieval..", "com.ragagent.agentengine..")
			.should().resideInAnyPackage(
				"com.ragagent.modelruntime.api..", "com.ragagent.modelruntime.spi..",
				"com.ragagent.retrieval.api..", "com.ragagent.retrieval.spi..",
				"com.ragagent.agentengine.api..", "com.ragagent.agentengine.spi..")
			.orShould().haveSimpleNameEndingWith("package-info")
			.allowEmptyShould(true);

	/**
	 * 除装配层外，任何类不得直接 new 出引擎 spi 包里的对象。
	 * 校验确认 ArchUnit 1.3.0 没有 com.tngtech.archunit.core.domain.Constructor 这个类，
	 * 也没有 isDeclaredInClassesThat()。正确入口是 callConstructorWhere + JavaCall.Predicates.target。
	 */
	@ArchTest
	static final ArchRule enginesAreAssembledOnlyInAppConfig =
		noClasses().that().resideOutsideOfPackages("com.ragagent.config..")
			.should().callConstructorWhere(
				JavaCall.Predicates.target(
					AccessTarget.Predicates.declaredIn(
						JavaClass.Predicates.resideInAnyPackage("com.ragagent.modelruntime.spi..",
							"com.ragagent.retrieval.spi..", "com.ragagent.agentengine.spi.."))))
			.allowEmptyShould(true);

	/** 域分三级（spec 5.4），名字取自路线图 3.3 的域名。新增域只需把包名放进对应清单，规则不用改。 */
	private static final List<String> BASE_DOMAINS =
		List.of("tenant", "auth", "system", "storage", "model", "vectorstore", "websearch", "audit");
	private static final List<String> BUSINESS_DOMAINS = List.of("knowledge", "wiki", "datasource", "agent", "mcp",
		"memory", "favorite", "customer", "channel", "moderation", "lead", "handoff");
	private static final List<String> ORCHESTRATION_DOMAINS = List.of("session", "agenttools");

	/** 只允许上级依赖下级：基础域不得依赖业务域与编排域。 */
	@ArchTest
	static final ArchRule baseDomainsDoNotDependOnUpperDomains =
		noClasses().that().resideInAnyPackage(packagesOf(BASE_DOMAINS))
			.should().dependOnClassesThat().resideInAnyPackage(packagesOf(BUSINESS_DOMAINS, ORCHESTRATION_DOMAINS))
			.allowEmptyShould(true);

	/** 业务域不得依赖编排域。 */
	@ArchTest
	static final ArchRule businessDomainsDoNotDependOnOrchestrationDomains =
		noClasses().that().resideInAnyPackage(packagesOf(BUSINESS_DOMAINS))
			.should().dependOnClassesThat().resideInAnyPackage(packagesOf(ORCHESTRATION_DOMAINS))
			.allowEmptyShould(true);

	/** 每个域包都必须在上面三份清单之一，否则上面两条规则对它静默失效。 */
	@ArchTest
	static final ArchRule everyDomainPackageIsClassified =
		classes().should(new ArchCondition<>("所在域必须在 BASE、BUSINESS、ORCHESTRATION 清单里") {
			@Override
			public void check(JavaClass item, ConditionEvents events) {
				String domain = domainOf(item.getPackageName());
				if (domain != null && !classified(domain)) {
					events.add(SimpleConditionEvent.violated(item,
						item.getName() + " 所在的域 " + domain + " 没有归入三级清单"));
				}
			}
		});

	private static boolean classified(String domain) {
		return BASE_DOMAINS.contains(domain) || BUSINESS_DOMAINS.contains(domain) || ORCHESTRATION_DOMAINS.contains(domain);
	}

	@SafeVarargs
	private static String[] packagesOf(List<String>... domainLists) {
		return Stream.of(domainLists).flatMap(List::stream).map(d -> "com.ragagent." + d + "..").toArray(String[]::new);
	}

	/**
	 * 包名第二段即域：com.ragagent.knowledge.controller.X → knowledge。
	 * framework、engine 三模块与 app 的装配包 config 不算域，返回 null。
	 */
	private static String domainOf(String pkg) {
		List<String> parts = List.of(pkg.split("\\."));
		if (parts.size() < 3 || !parts.get(0).equals("com") || !parts.get(1).equals("ragagent")) {
			return null;
		}
		String second = parts.get(2);
		if (second.equals("framework") || second.equals("modelruntime") || second.equals("config")
				|| second.equals("retrieval") || second.equals("agentengine")) {
			return null;
		}
		return second;
	}

	/** 只有 repository / mapper / entity 三个包算域内部实现。 */
	private static String targetDomainOf(String pkg) {
		String domain = domainOf(pkg);
		List<String> parts = List.of(pkg.split("\\."));
		if (domain == null || parts.size() < 4) {
			return null;
		}
		String fourth = parts.get(3);
		boolean internal = fourth.equals("repository") || fourth.equals("mapper") || fourth.equals("entity");
		return internal ? domain : null;
	}
}
```

`noRawRequestBodyString` 用自定义 `ArchCondition`，不用 `notExist()`。校验确认 ArchUnit 1.3.0 没有 `notExist()` 这个方法；而且 `@RequestBody` 的 `@Target` 只有 `PARAMETER`，`methods().that().areAnnotatedWith(RequestBody.class)` 永远匹配 0 个方法，规则会静默空转。必须在方法参数层面判断。

`noCrossDomainInternalAccess` 用自定义 `ArchCondition` 而不是 `ignoreDependency`：ArchUnit 的 `ignoreDependency` 按类匹配，表达不了「同域放行」这种按包前缀的关系。

`enginesExposeOnlyApiAndSpi` 现在通过 `allowEmptyShould(true)` 空跑：三个引擎模块只有 `package-info.java`，还没有类。**这条规则在本计划里是空转的**，等引擎代码搬入后才真正生效。保留它是为了让下一份计划不必再想规则怎么写，同时 `Step 5` 的反向验证会加一个故意放在 `internal` 包下的 fixture 类，确认规则确实会命中。

`enginesAreAssembledOnlyInAppConfig` 同理，本计划内无被引用类，也是空转。

- [ ] **Step 4: 运行，确认通过**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl app -am test -Dtest='ArchitectureRulesTest,ModuleBoundaryTest'`
Expected: PASS。此时 app 下只有框架代码，规则应全通过；若有失败，说明 framework 自身违规，先修 framework。

`-Dtest` 用逗号分隔，不要用 `+`。注意 ArchUnit 的 `@ArchTest` 字段需要类上有 `@AnalyzeClasses` 才会被 JUnit 5 扩展识别；没有该注解时测试会因为「没有测试方法」而报错，而不是静默跳过。

- [ ] **Step 5: 反向验证规则会失败**

在 `backend/app/src/test/java/com/ragagent/arch/fixture/` 下临时加几个故意违规的类，先是两个命名与参数类的：

```java
class FakeOps {}                                        // 触发 noGoCompatSuffixes
class FakeController {                                  // 触发 noRawRequestBodyString
	void accept(@RequestBody String raw) {}
}
```

再在 `com.ragagent.agentengine.internal` 下加一个空类，验证 `enginesExposeOnlyApiAndSpi`。再加三个域级别的 fixture：`com.ragagent.tenant.FakeBase` 里引用 `com.ragagent.knowledge.FakeUpper`（触发 `baseDomainsDoNotDependOnUpperDomains`）；`com.ragagent.knowledge.FakeBusiness` 里引用 `com.ragagent.session.FakeOrchestration`（触发 `businessDomainsDoNotDependOnOrchestrationDomains`）；`com.ragagent.unlisted.FakeUnknown`（触发 `everyDomainPackageIsClassified`）。

**这几组 fixture 要跑两个不同配置的反向验证测试类**：

- `ArchitectureRulesTest` 用的是 `DoNotIncludeTests`，它看不见 fixture。这是故意的：正式规则不应该因为 fixture 而变绿或变红。
- 另建 `ArchitectureRulesReverseCheckTest`，内容是 `ArchitectureRulesTest` 的副本，只把 `@AnalyzeClasses` 改成 `@AnalyzeClasses(packages = "com.ragagent")`（不加 `DoNotIncludeTests`），只在 Step 5 临时存在。注意 `noRawRequestBodyString` 的 fixture 必须是**方法参数**上的 `@RequestBody`，写成 record 组件或字段都不会命中。

重跑 Step 4 的命令，再跑反向验证类。
Expected: `noGoCompatSuffixes`、`noRawRequestBodyString`、`enginesExposeOnlyApiAndSpi`、`baseDomainsDoNotDependOnUpperDomains`、`businessDomainsDoNotDependOnOrchestrationDomains`、`everyDomainPackageIsClassified` 六条 FAIL，其余 PASS。确认后删除 fixture 目录与 `ArchitectureRulesReverseCheckTest`，重跑恢复全绿。

不做这一步时，ArchUnit 规则很可能因为包路径写错而空跑通过。

- [ ] **Step 6: 提交**

```bash
git add backend/app/src/main/java/com/ragagent/package-info.java
git add backend/app/src/main/java/com/ragagent/RagAgentApplication.java
git add backend/app/src/main/java/com/ragagent/config/EngineAssemblyConfig.java
git add backend/framework/src/main/java/com/ragagent/framework/FrameworkMarker.java
git add backend/app/src/test/java/com/ragagent/arch
git commit -m "feat(app): Modulith 模块校验与 ArchUnit 架构规则"
```

---

### Task 11b: app 运行配置与本地可启动

**Files:**
- Modify: `backend/app/pom.xml`（加 `spring-boot-maven-plugin`，让 `spring-boot:run` 可用）
- Modify: `backend/app/src/main/resources/application.yml`
- Create: `backend/app/src/main/resources/application-dev.yml`
- Create: `backend/app/src/main/resources/db/migration/allenv/V20261110_01__DDL_rebuild_framework_placeholder_jiangguifan.sql`
- Create: `deploy/db/init/10-roles.sql`
- Create: `backend/app/scripts/dev-db.sh`
- Create: `backend/app/README.md`
- Test: `backend/app/src/test/java/com/ragagent/AppBootsTest.java`

**Interfaces:**
- Consumes: Task 5 的 `PostgresContainerTest`（已含应用角色与 Flyway 属主账号两套数据源属性）、Task 6 的加密密钥校验、Task 7 的 `spring.mvc.throw-exception-if-no-handler-found`、Task 8 的 `permit-paths`
- Produces:
  - 环境变量约定（名称写死，部署与测试都用它们）：`DB_URL`、`DB_OWNER_USER`、`DB_OWNER_PASSWORD`、`DB_APP_USER`、`DB_APP_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`RAGAGENT_ENCRYPT_KEY`、`SERVER_PORT`、`CORS_ALLOWED_ORIGINS`
  - 两套数据库账号：Flyway 用属主账号建表，应用运行时用**非属主**账号，行级安全才对应用生效
  - `application-dev.yml`：对应旧开发栈的 `localhost:15432`（PostgreSQL）与 `localhost:16379`（Redis），数据库名用独立的 `ragagent_rebuild`，不碰旧系统的 `RagAgent` 库
  - Flyway 迁移位置 `classpath:db/migration/allenv`，命名规则 `V{yyyyMMdd}_{两位序号}__{DDL|DML}_rebuild_{域名}_{描述}_jiangguifan.sql`

**版本号与描述之间必须是两个下划线**（`V20261110_01__DDL_...`），这是 Flyway 的文件名规则，版本号里的单个下划线会被当作小数点，即 `20261110.01`。路线图 3.5 与用户全局规则里的单下划线写法在 Flyway 里是无效文件名，已在 `deviations.md` 登记，所有域计划的 Flyway 脚本都按本任务的写法。

- [ ] **Step 1: 写失败测试 `AppBootsTest`**

```java
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "management.health.redis.enabled=false")
class AppBootsTest extends PostgresContainerTest {

	@Autowired
	private TestRestTemplate rest;

	@Test
	void healthEndpointIsUp() {
		ResponseEntity<String> rsp = rest.getForEntity("/actuator/health", String.class);
		assertThat(rsp.getStatusCode().value()).isEqualTo(200);
		assertThat(rsp.getBody()).contains("\"status\":\"UP\"");
	}

	@Test
	void flywayRecognisesAllenvDirectory() {
		Long applied = jdbc.queryForObject(
			"SELECT count(*) FROM flyway_schema_history WHERE version = '20261110.01' AND success", Long.class);
		assertThat(applied).isEqualTo(1L);
	}
}
```

测试里关掉 Redis 健康检查，是因为这个基类不起 Redis 容器。

- [ ] **Step 2: 运行，确认失败**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl app -am test -Dtest=AppBootsTest`
Expected: `flywayRecognisesAllenvDirectory` FAIL（`flyway_schema_history` 不存在或无该版本），`healthEndpointIsUp` 通过。

- [ ] **Step 3: 写运行配置与迁移**

`application.yml` 在 Task 1 的五项基础上补全，键名与取值如下：

| 键 | 值 |
|---|---|
| `spring.datasource.url / username / password` | `${DB_URL}`、`${DB_APP_USER}`、`${DB_APP_PASSWORD}` |
| `spring.flyway.url / user / password` | `${DB_URL}`、`${DB_OWNER_USER}`、`${DB_OWNER_PASSWORD}` |
| `spring.flyway.locations` | `classpath:db/migration/allenv` |
| `spring.data.redis.host / port / password` | `${REDIS_HOST:localhost}`、`${REDIS_PORT:6379}`、`${REDIS_PASSWORD:}` |
| `spring.mvc.throw-exception-if-no-handler-found` | `true` |
| `spring.web.resources.add-mappings` | `false` |
| `management.endpoints.web.exposure.include` | `health` |
| `management.endpoint.health.probes.enabled` | `true` |
| `springdoc.api-docs.enabled` | `false`（Task 12 的 `openapi` profile 打开） |
| `mybatis-plus.mapper-locations` | `classpath*:mapper/**/*.xml` |
| `gac.furnace.tenant.*` | `enabled: true`、`mode: line`、`column: tenant_id`、`trust-header: false`、`ignore-tables: []` |
| `gac.furnace.multilang.*` | `enabled: true`、`default-lang: zh_CN`、`support-langs` 七种、`basenames: i18n/messages,i18n/app-messages` |
| `gac.furnace.mybatis.encrypt-key` | `${RAGAGENT_ENCRYPT_KEY:}`，留空时由 Task 6 的校验器拒绝启动，报错里写明环境变量名 |
| `gac.furnace.redis.enabled` | `true` |
| `gac.furnace.web.cors.allowed-origins` | `${CORS_ALLOWED_ORIGINS:}` |
| `gac.furnace.security.permit-paths` | `/actuator/health`、`/v3/api-docs/**`、`/swagger-ui/**`；域计划追加匿名端点 |

`application-dev.yml`：`DB_URL` 默认 `jdbc:postgresql://localhost:15432/ragagent_rebuild`，`DB_OWNER_USER=postgres`、`DB_OWNER_PASSWORD=postgres123!@#`（旧开发栈的口令，仅开发用），`DB_APP_USER=ragagent_app`、`DB_APP_PASSWORD=ragagent_app_dev`，`REDIS_PORT` 默认 `16379`，`RAGAGENT_ENCRYPT_KEY` 默认一个开发专用的 Base64 32 字节密钥 `MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=`。

`deploy/db/init/10-roles.sql` 创建应用角色，不放进 Flyway 迁移，因为迁移由属主账号执行，角色创建属于库初始化：

```sql
DO $$
BEGIN
	IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ragagent_app') THEN
		-- 开发口令，生产环境由 DBA 建角色并设置真实口令
		CREATE ROLE ragagent_app LOGIN PASSWORD 'ragagent_app_dev';
	END IF;
END
$$;
```

`V20261110_01__DDL_rebuild_framework_placeholder_jiangguifan.sql`：只含授权，没有表。内容是 `GRANT USAGE ON SCHEMA public TO ragagent_app;` 加两条 `ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ragagent_app;` 与 `... GRANT USAGE, SELECT ON SEQUENCES TO ragagent_app;`。`ALTER DEFAULT PRIVILEGES` 对执行迁移的属主账号之后创建的对象生效，所以后续各域的表自动授权给应用角色。`PostgresContainerTest` 在容器里先建同名角色（Task 5），所以测试与生产用同一份迁移。

`backend/app/scripts/dev-db.sh`：`set -euo pipefail`，对本机开发栈的 PostgreSQL 容器执行：用 `deploy/db/init/10-roles.sql` 建应用角色，再 `CREATE DATABASE ragagent_rebuild`（已存在则跳过）。用 `docker exec -i <容器> psql -U postgres -v ON_ERROR_STOP=1`，从标准输入喂 SQL。容器名取 `PG_CONTAINER`，默认 `WeKnora-postgres-dev`（本机发布 15432 的就是它；计划原写的 `RagAgent-postgres-dev` 无端口映射，见 deviations）。容器不存在时打印“先执行 bash scripts/dev-up.sh 起开发栈”并退出非零。

`backend/app/README.md`：本地起法三步，起开发栈、执行 `dev-db.sh`、打包后用 `java -jar backend/app/target/app-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev` 启动；环境变量表；`curl localhost:8080/actuator/health`。

- [ ] **Step 4: 运行，确认通过**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl app -am test -Dtest=AppBootsTest`
Expected: PASS，2 个用例全绿。

- [ ] **Step 5: 在开发栈上手工验证**

Run:
```bash
bash scripts/dev-up.sh        # 若开发栈未起；该脚本会起后端，这里只需要其中的 PostgreSQL 与 Redis
bash backend/app/scripts/dev-db.sh
export REDIS_PASSWORD="$(grep '^REDIS_PASSWORD=' .env | cut -d= -f2)"
mvn -f backend/pom.xml -pl app -am -DskipTests package
java -jar backend/app/target/app-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
```
另开终端：`curl -s localhost:8080/actuator/health`
Expected: 返回含 `"status":"UP"` 的 JSON（此时 Redis 健康检查需要开发栈 Redis 在线）。验证后停掉应用。旧后端占用 8083，新后端占用 8080，两者不冲突。

- [ ] **Step 6: 回归整个 backend**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml verify`
Expected: BUILD SUCCESS。

- [ ] **Step 7: Commit**

```bash
git add backend/app/pom.xml backend/app/src/main/resources/application.yml \
  backend/app/src/main/resources/application-dev.yml \
  backend/app/src/main/resources/db/migration/allenv/V20261110_01__DDL_rebuild_framework_placeholder_jiangguifan.sql \
  deploy/db/init/10-roles.sql backend/app/scripts/dev-db.sh backend/app/README.md \
  backend/app/src/test/java/com/ragagent/AppBootsTest.java
git commit -m "feat(app): 运行配置、dev profile、Flyway allenv 与本地起法"
```

---

### Task 11c: Checkstyle 尺寸硬门与抑制预算

对应路线图 3.11 的尺寸标准。硬门由 Checkstyle 在 `validate` 阶段拦截，超标构建失败。旧仓库的守卫是方法 150 行、文件 600 行的棘轮，这里更严，且没有基线豁免。

**Files:**
- Create: `backend/config/checkstyle.xml`
- Create: `backend/config/checkstyle-suppressions-engines.xml`
- Modify: `backend/pom.xml`（加 `maven-checkstyle-plugin`，绑定 `validate`）
- Modify: `backend/framework/pom.xml`、`backend/model-runtime/pom.xml`、`backend/retrieval-engine/pom.xml`、`backend/agent-engine/pom.xml`、`backend/app/pom.xml`（各加属性 `checkstyle.config.dir`）
- Test: `backend/app/src/test/java/com/ragagent/arch/CodeQualityRulesTest.java`

**Interfaces:**
- Consumes: Task 1 的聚合 pom 与五个子模块
- Produces:
  - 属性 `checkstyle.config.dir`：父 pom 取 `${project.basedir}/config`，五个子模块各自覆盖为 `${project.parent.basedir}/config`
  - `checkstyle.xml` 规则：`FileLength` 400；`MethodLength` 40 且 `countEmpty=false`（不计空行与纯注释行）；`ParameterNumber` 5；`CyclomaticComplexity` 10；`NestedIfDepth`、`NestedForDepth`、`NestedTryDepth` 各取 2（最外层为 0，即允许三层）。Checkstyle 没有跨 if、for、try 合计的检查，三条各自独立，是 3.11「合计 3 层」的等价设置。`FileLength` 按文件总行数计，含注释，比路线图的「类 400 行」略严
  - `SuppressWarningsFilter` 加 `SuppressWarningsHolder`，允许 `@SuppressWarnings("checkstyle:<规则名>")`
  - `SuppressionFilter` 指向 `checkstyle-suppressions-engines.xml`，初始是只含空 `<suppressions/>` 的文件，计划 02 对迁入的引擎旧代码临时豁免，整理完成时逐包删除
  - 检查范围只含 `src/main/java`，不含测试代码
  - `CodeQualityRulesTest` 三条：`constructorsOfSpringBeansTakeAtMostSevenDependencies`、`checkstyleSuppressionsStayWithinBudget`、`everyCheckstyleSuppressionIsExplainedInChinese`

`@SuppressWarnings` 的保留策略是 SOURCE，不进字节码，ArchUnit 看不到它。所以抑制数与理由检查用扫描源文件的 JUnit 测试实现，不用 ArchUnit 的字节码规则。构造器参数上限仍用 ArchUnit。

- [ ] **Step 1: 写测试 `CodeQualityRulesTest`**

```java
@AnalyzeClasses(packages = "com.ragagent", importOptions = ImportOption.DoNotIncludeTests.class)
class CodeQualityRulesTest {

	private static final int MAX_CONSTRUCTOR_PARAMETERS = 7;
	private static final int MAX_SUPPRESSIONS = 10;
	private static final Pattern SUPPRESSION = Pattern.compile("@SuppressWarnings\\(.*\"checkstyle:");
	private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");

	@ArchTest
	static final ArchRule constructorsOfSpringBeansTakeAtMostSevenDependencies =
		constructors().that().areDeclaredInClassesThat().areMetaAnnotatedWith(Component.class)
			.should(new ArchCondition<JavaConstructor>("参数不超过 " + MAX_CONSTRUCTOR_PARAMETERS + " 个") {
				@Override
				public void check(JavaConstructor constructor, ConditionEvents events) {
					int count = constructor.getRawParameterTypes().size();
					if (count > MAX_CONSTRUCTOR_PARAMETERS) {
						events.add(SimpleConditionEvent.violated(constructor,
							constructor.getFullName() + " 有 " + count + " 个参数，类承担了不止一件事"));
					}
				}
			})
			.allowEmptyShould(true);

	@Test
	void checkstyleSuppressionsStayWithinBudget() throws IOException {
		assertThat(suppressionLines()).as("全仓 checkstyle 抑制数").hasSizeLessThanOrEqualTo(MAX_SUPPRESSIONS);
	}

	@Test
	void everyCheckstyleSuppressionIsExplainedInChinese() throws IOException {
		for (SourceLine line : suppressionLines()) {
			assertThat(line.previousLine())
				.as("%s 第 %d 行的抑制前一行必须是中文理由注释", line.file(), line.number())
				.matches(l -> l.trim().startsWith("//") && CJK.matcher(l).find());
		}
	}

	/** 扫描 backend 下所有模块的主码源文件，测试在 backend/app 目录运行。 */
	private static List<SourceLine> suppressionLines() throws IOException {
		Path backend = Path.of("..").toAbsolutePath().normalize();
		List<SourceLine> found = new ArrayList<>();
		try (Stream<Path> files = Files.walk(backend)) {
			for (Path file : files.filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
					.filter(p -> p.toString().endsWith(".java")).toList()) {
				List<String> lines = Files.readAllLines(file);
				for (int i = 0; i < lines.size(); i++) {
					if (SUPPRESSION.matcher(lines.get(i)).find()) {
						found.add(new SourceLine(file, i + 1, i == 0 ? "" : lines.get(i - 1)));
					}
				}
			}
		}
		return found;
	}

	private record SourceLine(Path file, int number, String previousLine) {
	}
}
```

- [ ] **Step 2: 运行，确认当前通过（空转风险在 Step 6 反向验证）**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl app -am test -Dtest=CodeQualityRulesTest`
Expected: PASS。此时仓库内没有任何抑制，也没有超过 7 个参数的 Spring Bean。

- [ ] **Step 3: 写 Checkstyle 配置与接线**

`backend/config/checkstyle.xml`：根为 `Checker`，子模块依次是 `SuppressWarningsFilter`、`SuppressionFilter`（`file` 取 `${checkstyle.suppressions.file}`）、`FileLength`（`max=400`），再是 `TreeWalker`，其下 `SuppressWarningsHolder`、`MethodLength`（`max=40`、`countEmpty=false`）、`ParameterNumber`（`max=5`）、`CyclomaticComplexity`（`max=10`）、`NestedIfDepth`（`max=2`）、`NestedForDepth`（`max=2`）、`NestedTryDepth`（`max=2`）。文件头注释写明每条规则对应路线图 3.11 的哪一行，以及三条嵌套检查是合计 3 层的等价设置。

`backend/config/checkstyle-suppressions-engines.xml`：Checkstyle 抑制文件的标准头（`<?xml version="1.0"?>` 与 `suppressions_1_2.dtd` 的 DOCTYPE）加一对空的 `<suppressions>` 标签，一条抑制都没有。头部注释写明用途：计划 02 对迁入引擎旧代码临时豁免，整理完成时逐包删除。

`backend/pom.xml`：`maven-checkstyle-plugin` 3.6.0，依赖 `com.puppycrawl.tools:checkstyle:10.21.4`（支持 Java 21 的 record 与模式匹配；镜像源解析失败时取最近的 10.x 并登记偏离）。配置 `configLocation=${checkstyle.config.dir}/checkstyle.xml`，`propertyExpansion` 传入 `checkstyle.suppressions.file=${checkstyle.config.dir}/checkstyle-suppressions-engines.xml`，`includeTestSourceDirectory=false`，`failOnViolation=true`，`violationSeverity=warning`，`consoleOutput=true`。一个 execution，`phase=validate`，`goal=check`。五个子模块 pom 各自覆盖属性 `checkstyle.config.dir`。

- [ ] **Step 4: 运行，确认现有代码通过**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml validate`
Expected: BUILD SUCCESS。若 Task 2 到 Task 10 的 framework 主码有超标方法，**拆方法**修掉，不加抑制。

- [ ] **Step 5: 全量跑一次**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml verify`
Expected: BUILD SUCCESS，`CodeQualityRulesTest` 与之前所有用例通过。

- [ ] **Step 6: 反向验证规则真的会拦**

临时在 `backend/framework/src/main/java/com/ragagent/framework/QualityProbe.java` 写三样东西：一个 45 行有效代码的方法；一个 6 个参数的方法；一个标了 `@Component`、构造器带 8 个参数的类。

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml validate`
Expected: BUILD FAILURE，输出里同时出现 `MethodLength` 与 `ParameterNumber` 两条违规。

再给 45 行方法加 `@SuppressWarnings("checkstyle:MethodLength")` 且**不写**中文理由，重跑：

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl app -am test -Dtest=CodeQualityRulesTest`
Expected: `validate` 已通过（抑制生效），但 `everyCheckstyleSuppressionIsExplainedInChinese` FAIL，`constructorsOfSpringBeansTakeAtMostSevenDependencies` FAIL。

确认后删除 `QualityProbe.java`，重跑 Step 5 恢复全绿。不做这一步，Checkstyle 的配置路径写错时会静默空转。

- [ ] **Step 7: Commit**

```bash
git add backend/config/checkstyle.xml backend/config/checkstyle-suppressions-engines.xml
git add backend/pom.xml backend/framework/pom.xml backend/model-runtime/pom.xml \
  backend/retrieval-engine/pom.xml backend/agent-engine/pom.xml backend/app/pom.xml
git add backend/app/src/test/java/com/ragagent/arch/CodeQualityRulesTest.java
git commit -m "build(backend): Checkstyle 尺寸硬门、抑制预算与构造器参数规则"
```

---

### Task 11d: framework 流分发与只读 SQL 执行器

计划 08 的前置需求。流分发放 framework 而不是 session：渠道域要写停止事件，业务域不得依赖编排域，framework 是唯一的共同下层。只读 SQL 执行器同理，agenttools 不能自建 `JdbcTemplate`，连接、账号、超时与租户会话变量收进 framework。

**旧代码依据。** 读 `platform/src/main/java/com/ragagent/platform/stream/` 17 个文件 1439 行。`StreamEvent` 的 `type` 与 `usage` 字段分别 import `com.ragagent.contract.model.ResponseType` 与 `com.ragagent.contract.model.TokenUsage`，两者是真实类型，不是字符串或 map 字段：`ResponseType` 是 23 个取值的枚举，`TokenUsage` 是带累加语义的类，还引用 `PromptCacheStatus` 四值枚举。contract 解散后 spec 5.2 把 `contract.model` 划给 `modelruntime.api`，但 framework 不依赖引擎，`com.ragagent.framework.stream.StreamEvent` 无法引用 `modelruntime.api`。因此本 Task 把 `ResponseType`、`TokenUsage`、`PromptCacheStatus` 一并迁入 `com.ragagent.framework.stream`，作为 SSE 帧契约的唯一归属；计划 02 Task 1 的 `modelruntime.api` 清单里删除这三个类，改为 import framework 的类型。这一取舍登记到 `deviations.md`。

**Files:**
- Create: `backend/framework/src/main/java/com/ragagent/framework/stream/{StreamEvent,StreamBatch,LiveRun,LiveRunPayload,StreamEventLog,MemoryStreamEventLog,RedisStreamEventLog,StreamRedisKeys,StreamRedisScripts,StreamJson,StreamEventLogConfig,StreamProperties,LiveRunExistsException,StreamStoreException,ResponseType,TokenUsage,PromptCacheStatus}.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/sql/{ReadOnlySqlExecutor,PostgresReadOnlySqlExecutor,ReadOnlySqlProperties,ReadOnlySqlConfig}.java`
- Create: `backend/framework/src/test/java/com/ragagent/framework/stream/StreamEventLogTest.java`
- Create: `backend/framework/src/test/java/com/ragagent/framework/sql/ReadOnlySqlExecutorTest.java`
- Modify: `backend/framework/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（追加 `com.ragagent.framework.stream.StreamEventLogConfig`、`com.ragagent.framework.sql.ReadOnlySqlConfig`）
- Modify: `backend/app/src/test/java/com/ragagent/arch/ArchitectureRulesTest.java`（`jdbcTemplateAllowedOnlyInTenantAndRetrieval` 的放行包追加 `"com.ragagent.framework.sql.."`）

**Interfaces:**
- Consumes: Task 5 的 `TenantContextHolder`、`TenantContextMissingException`；Task 11 的 `ArchitectureRulesTest`
- Produces:
  - `record StreamEvent(String id, ResponseType type, String content, boolean done, Map<String,Object> data, TokenUsage usage, OffsetDateTime at)`，`at` 的 JSON 键名固定为 `timestamp`（`@JsonProperty("timestamp")`），字段序 `id`、`type`、`content`、`done`、`timestamp`、`data`、`usage`，`data` 与 `usage` 用 `NON_EMPTY` 省略
  - `record StreamBatch(List<StreamEvent> events, int nextOffset)`，静态 `empty(int fromOffset)`
  - `record LiveRun(long assistantMessageId, String requestId, OffsetDateTime startedAt)`，静态 `NONE = new LiveRun(0L, "", null)`，`boolean isPresent()`
  - `interface StreamEventLog`：`void append(long sessionId, long messageId, StreamEvent event)`、`StreamBatch read(long sessionId, long messageId, int fromOffset)`、`void appendSteer(long sessionId, long messageId, List<StreamEvent> events)`、`StreamBatch readSteer(long sessionId, long messageId, int fromOffset)`、`boolean updateSteerData(long sessionId, long messageId, String eventId, Map<String,Object> data)`、`boolean deleteSteer(long sessionId, long messageId, String eventId)`、`void setLiveRun(long sessionId, long assistantMessageId, String requestId)`、`void claimLiveRun(long sessionId, long assistantMessageId, String requestId)`、`LiveRun getLiveRun(long sessionId)`、`void clearLiveRun(long sessionId, long assistantMessageId)`
  - `class MemoryStreamEventLog implements StreamEventLog`（构造器 `MemoryStreamEventLog(Duration ttl)`）、`class RedisStreamEventLog implements StreamEventLog`（构造器 `RedisStreamEventLog(StringRedisTemplate template, String prefix, Duration ttl)`）、`class LiveRunExistsException extends RuntimeException`、`class StreamStoreException extends RuntimeException`、`class StreamEventLogConfig`（`@AutoConfiguration`，`@EnableConfigurationProperties(StreamProperties.class)`，Bean `StreamEventLog` 带 `@ConditionalOnMissingBean`；选 redis 时启动 Ping 一次，失败即启动失败）
  - `class StreamProperties`：`@ConfigurationProperties("gac.furnace.stream")`，`type`（`memory` 默认、`redis`）、`prefix`（默认 `stream:events`）、`ttl`（默认 1 小时）
  - `enum ResponseType`：23 个枚举与 `value()`、`fromValue(String)`，取值逐字沿用 `contract.model.ResponseType`（`answer`、`references`、`thinking`、`toolCall`、`toolResult`、`installOutput`、`commandOutput`、`error`、`reflection`、`sessionTitle`、`agentQuery`、`complete`、`artifactsPending`、`toolApprovalRequired`、`toolApprovalResolved`、`mcpOauthRequired`、`mcpOauthResolved`、`memoryRecalled`、`steer`、`userMessageInjected`、`contextCompacted`、`installPrompt`、`stop`），`value()` 上标 `@JsonValue`，`fromValue` 上标 `@JsonCreator`
  - `class TokenUsage`：字段 `prompt_tokens`、`completion_tokens`、`total_tokens`、`cached_tokens`、`cache_read_tokens`、`cache_write_tokens`、`cache_miss_tokens`、`cache_reported`、`cache_status`（`PromptCacheStatus`），前三个与 `cache_reported` 恒输出，后五个 `NON_DEFAULT`；方法 `setPromptCacheUsage(int,int,int,boolean)`、`markPromptCacheUnsupported()`、`accumulate(TokenUsage)`、`promptCacheHitRate()` 与只读 getter，语义照旧
  - `enum PromptCacheStatus`：`UNSUPPORTED("unsupported")`、`UNREPORTED("unreported")`、`MISS("miss")`、`HIT("hit")`，`value()` 标 `@JsonValue`，`fromValue` 标 `@JsonCreator`
  - `interface ReadOnlySqlExecutor`：`List<Map<String, Object>> query(String sql, int maxRows)`
  - `class PostgresReadOnlySqlExecutor implements ReadOnlySqlExecutor`：独立数据源、只读事务、`statement_timeout`、行数上限、每查询先写 `app.tenant_id` 会话变量
  - `class ReadOnlySqlProperties`：`@ConfigurationProperties("gac.furnace")`，嵌套 `readonlyDatasource.{url,username,password}` 与 `sql.{statementTimeout,maxRows}`；`statement-timeout` 默认 `5s`、`max-rows` 默认 `2000`
  - 配置项：`gac.furnace.stream.{type,prefix,ttl}`、`gac.furnace.readonly-datasource.{url,username,password}`、`gac.furnace.sql.{statement-timeout,max-rows}`

存储字节形态沿用旧 `StreamJson`：map 键按字母序、`OffsetDateTime` 用本地时区加 `ISO_OFFSET_DATE_TIME`、容忍未知属性。`RedisStreamManager` 改名为 `RedisStreamEventLog`，`MemoryStreamManager` 改名为 `MemoryStreamEventLog`：旧名里的 Manager 不描述职责，这两个类只存取事件与 live-run 标记，不管理 SSE 生命周期，且 `noGoCompatSuffixes` 规则禁用 Manager 后缀，按端口名命名让实现与 `StreamEventLog` 一一对应。

- [ ] **Step 1: 写失败测试**

`StreamEventLogTest` 用 `@ParameterizedTest` 一份用例跑两个后端：Redis 与内存的契约完全相同，子类写法会让四个用例各写两遍；容器只在静态字段启动一次，`@BeforeEach` 用 `flushDb()` 隔离，不用两个测试类。

```java
@Testcontainers
class StreamEventLogTest {

	@Container
	static final GenericContainer<?> REDIS =
		new GenericContainer<>("redis:7.0-alpine").withExposedPorts(6379);

	private static StringRedisTemplate template;
	private static final AtomicLong SESSION_SEQ = new AtomicLong(1000);

	@BeforeAll
	static void connect() {
		LettuceConnectionFactory factory =
			new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
		factory.afterPropertiesSet();
		template = new StringRedisTemplate(factory);
		template.afterPropertiesSet();
	}

	@BeforeEach
	void flush() {
		template.getConnectionFactory().getConnection().serverCommands().flushDb();
	}

	static Stream<Arguments> logs() {
		return Stream.of(
			Arguments.of("memory", new MemoryStreamEventLog(Duration.ofHours(1))),
			Arguments.of("redis", new RedisStreamEventLog(template, "stream:events", Duration.ofHours(1))));
	}

	private static StreamEvent answer(String id, String content) {
		return new StreamEvent(id, ResponseType.ANSWER, content, false, Map.of(), null, null);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("logs")
	void appendThenReadFromOffsetReturnsOnlyNewEvents(String backend, StreamEventLog log) {
		long session = SESSION_SEQ.incrementAndGet();
		log.append(session, 7L, answer("e1", "a"));
		log.append(session, 7L, answer("e2", "b"));
		log.append(session, 7L, answer("e3", "c"));

		StreamBatch all = log.read(session, 7L, 0);
		assertThat(all.events()).extracting(StreamEvent::id).containsExactly("e1", "e2", "e3");
		assertThat(all.nextOffset()).isEqualTo(3);
		assertThat(all.events().get(0).at()).isNotNull();

		StreamBatch tail = log.read(session, 7L, 2);
		assertThat(tail.events()).extracting(StreamEvent::id).containsExactly("e3");
		assertThat(tail.nextOffset()).isEqualTo(3);
		assertThat(log.read(session, 7L, 3).events()).isEmpty();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("logs")
	void steerListIsSeparateFromEventList(String backend, StreamEventLog log) {
		long session = SESSION_SEQ.incrementAndGet();
		log.append(session, 8L, answer("e1", "user visible"));
		StreamEvent steer = new StreamEvent("s1", ResponseType.STEER, "", false,
			new LinkedHashMap<>(Map.of("status", "queued")), null, null);
		log.appendSteer(session, 8L, List.of(steer));
		// 同 ID 重复追加被去重
		log.appendSteer(session, 8L, List.of(steer));

		assertThat(log.read(session, 8L, 0).events()).extracting(StreamEvent::id).containsExactly("e1");
		assertThat(log.readSteer(session, 8L, 0).events()).extracting(StreamEvent::id).containsExactly("s1");

		assertThat(log.updateSteerData(session, 8L, "s1", Map.of("consumed", true))).isTrue();
		assertThat(log.readSteer(session, 8L, 0).events().get(0).data()).containsEntry("consumed", true);
		assertThat(log.deleteSteer(session, 8L, "s1")).isFalse();  // consumed 的不许删
		assertThat(log.deleteSteer(session, 8L, "missing")).isFalse();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("logs")
	void secondLiveRunIsRejected(String backend, StreamEventLog log) {
		long session = SESSION_SEQ.incrementAndGet();
		log.setLiveRun(session, 10L, "req-1");
		log.setLiveRun(session, 10L, "req-1");                     // 同一消息幂等
		assertThatThrownBy(() -> log.setLiveRun(session, 20L, "req-2"))
			.isInstanceOf(LiveRunExistsException.class);
		assertThat(log.getLiveRun(session).assistantMessageId()).isEqualTo(10L);
		assertThat(log.getLiveRun(session).startedAt()).isNotNull();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("logs")
	void clearLiveRunIgnoresMovedTarget(String backend, StreamEventLog log) {
		long session = SESSION_SEQ.incrementAndGet();
		log.setLiveRun(session, 10L, "req-1");
		log.claimLiveRun(session, 20L, "req-2");
		log.clearLiveRun(session, 10L);                            // 指向已改，不清
		assertThat(log.getLiveRun(session).assistantMessageId()).isEqualTo(20L);
		log.clearLiveRun(session, 20L);
		assertThat(log.getLiveRun(session)).isEqualTo(LiveRun.NONE);
	}
}
```

`ReadOnlySqlExecutorTest` 自建容器与只读角色，不起 Spring 上下文：

```java
@Testcontainers
class ReadOnlySqlExecutorTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES =
		new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg17"))
			.withDatabaseName("ragagent_test");

	private static PostgresReadOnlySqlExecutor executor;

	@BeforeAll
	static void prepareRoleAndTable() throws Exception {
		try (Connection owner = POSTGRES.createConnection("");
				Statement s = owner.createStatement()) {
			s.execute("CREATE ROLE ragagent_ro LOGIN PASSWORD 'ro_test' NOSUPERUSER");
			s.execute("CREATE TABLE ro_probe (id bigint PRIMARY KEY, tenant_id bigint NOT NULL, content text)");
			s.execute("ALTER TABLE ro_probe ENABLE ROW LEVEL SECURITY");
			s.execute("ALTER TABLE ro_probe FORCE ROW LEVEL SECURITY");
			s.execute("CREATE POLICY ro_probe_tenant ON ro_probe USING ("
				+ "current_setting('app.tenant_id', true) = 'system' "
				+ "OR tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint)");
			s.execute("GRANT USAGE ON SCHEMA public TO ragagent_ro");
			s.execute("GRANT SELECT ON ro_probe TO ragagent_ro");
			s.execute("INSERT INTO ro_probe VALUES (1, 1, 'a1'), (2, 1, 'a2'), (3, 2, 'b1')");
		}
		HikariDataSource ds = new HikariDataSource();
		ds.setJdbcUrl(POSTGRES.getJdbcUrl());
		ds.setUsername("ragagent_ro");
		ds.setPassword("ro_test");
		executor = new PostgresReadOnlySqlExecutor(ds, Duration.ofMillis(300), 2000);
	}

	@AfterAll
	static void close() {
		((HikariDataSource) executor.dataSource()).close();
	}

	@BeforeEach
	void tenant() {
		TenantContextHolder.set(1L);
	}

	@AfterEach
	void clearTenant() {
		TenantContextHolder.clear();
	}

	@Test
	void rejectsWriteStatement() {
		assertThatThrownBy(() -> executor.query("INSERT INTO ro_probe VALUES (9, 1, 'x')", 10))
			.isInstanceOf(DataAccessException.class)
			.hasRootCauseMessage("ERROR: cannot execute INSERT in a read-only transaction");
	}

	@Test
	void killsQueryOverStatementTimeout() {
		assertThatThrownBy(() -> executor.query("SELECT pg_sleep(10)", 10))
			.isInstanceOf(DataAccessException.class)
			.hasRootCauseMessage("ERROR: canceling statement due to statement timeout");
	}

	@Test
	void tenantsAreIsolatedThroughRowLevelSecurity() {
		assertThat(executor.query("SELECT content FROM ro_probe ORDER BY id", 10))
			.extracting(r -> r.get("content")).containsExactly("a1", "a2");
		TenantContextHolder.set(2L);
		assertThat(executor.query("SELECT content FROM ro_probe", 10))
			.extracting(r -> r.get("content")).containsExactly("b1");
	}

	@Test
	void capsRowsAtConfiguredMax() {
		assertThat(executor.query("SELECT id FROM ro_probe", 1)).hasSize(1);
	}
}
```

`tenant_id` 策略必须 fail-closed，与 Task 5 的模板逐字一致；`ro_probe` 的属主是容器超级用户，`ragagent_ro` 非属主，RLS 才生效。`executor.dataSource()` 是包可见访问器，只供测试关池。

- [ ] **Step 2: 跑红**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl framework -am test -Dtest='StreamEventLogTest,ReadOnlySqlExecutorTest'`
Expected: FAIL，`package com.ragagent.framework.stream does not exist`。

- [ ] **Step 3: 实现**

1. 建 `com.ragagent.framework.stream` 的契约类型。`ResponseType`、`TokenUsage`、`PromptCacheStatus` 从 `contract.model` 原样迁入，只加 Jackson 注解（`@JsonValue`/`@JsonCreator`、`@JsonProperty` 蛇形键名、`NON_DEFAULT` 省略），取值与字段名不改。`StreamEvent` 用 record，`at` 标 `@JsonProperty("timestamp")`，类上 `@JsonPropertyOrder({"id","type","content","done","timestamp","data","usage"})`；另给包可见方法 `StreamEvent withAt(OffsetDateTime value)`，写路径先补时间戳再落存储，不改调用方对象。
2. `StreamJson` 照旧 `StreamJson` 重建：`ORDER_MAP_ENTRIES_BY_KEYS`、容忍未知属性、`OffsetDateTime` 本地时区序列化、失败抛 `StreamStoreException`；去掉旧版的 `ContractJsonModule` 依赖，枚举与 `TokenUsage` 的线格式已由注解承担。`LiveRunPayload` 用 record `(long assistantMessageId, String requestId, OffsetDateTime startedAt)`，字段序固定；`assistantMessageId` 是数值，`clearLiveRun` 的 CAS needle 因此是 `"assistantMessageId":<id>`（不带引号），不能用 `StreamJson.writeString`。
3. `MemoryStreamEventLog` 从 `MemoryStreamManager` 迁入：`sessionId`/`messageId` 改 `long`，方法名按新接口改名，保留 `ttl/2` 节流全扫、外层读写锁加每条流独立的读写锁、steer 按 ID 去重、`consumed` 的 steer 不许删、live-run 排他语义。`StreamEvent` 不可变，`updateSteerData` 用「合并后的新 `LinkedHashMap` 包成新 record」替换列表槽位，不原地改 data；入参可能是 `Map.of()` 这类不可变 map，实现不得写入它。
4. `RedisStreamEventLog` 从 `RedisStreamManager` 迁入，键布局不变：`{prefix}:{sessionId}:{messageId}`、`...:steer`、`{prefix}:{sessionId}:live-run`。**旧文件 467 行，超过 Task 11c 的 400 行硬门**，按职责拆三个文件：`StreamRedisKeys`（键拼接与 prefix 回落 `stream:events`）、`StreamRedisScripts`（三个 Lua 与 `RedisScript` 常量）、`RedisStreamEventLog`（业务方法）。`updateSteerData` 与 `deleteSteer` 各自拆出「定位槽位」与「CAS 提交」两个私有方法，保证单方法不超过 40 行；live-run 的 TTL 续期保留 `touchLiveRun` 并吞掉 `DataAccessException`，与旧实现一致。`setLiveRun` 用 `setIfAbsent`，读回同 ID 幂等，空标记重抢一次，仍失败抛 `LiveRunExistsException`；`claimLiveRun` 直接覆盖；`clearLiveRun` 走 Lua 的 CAS，只在该标记仍含目标 ID 时删。
5. `StreamEventLogConfig` 从 `StreamManagerConfig` 迁入，改 `@AutoConfiguration` 加 `@EnableConfigurationProperties(StreamProperties.class)`，`StreamEventLog` Bean 加 `@ConditionalOnMissingBean`；`redis` 类型启动时 `ping()` 一次，`StringRedisTemplate` 缺失或 Ping 失败都抛 `IllegalStateException`，默认走内存实现。
6. 建 `com.ragagent.framework.sql`。`ReadOnlySqlProperties` 一个类承载两段前缀：`@ConfigurationProperties("gac.furnace")`，嵌套 `ReadOnlyDatasource`（`url`、`username`、`password`）与 `Sql`（`Duration statementTimeout = Duration.ofSeconds(5)`、`int maxRows = 2000`），嵌套类提供 getter/setter 供 Binder 绑定。`ReadOnlySqlConfig` 用 `@ConditionalOnProperty(prefix = "gac.furnace.readonly-datasource", name = "url")` 控制装配，未配置只读库时 app 照常启动；两个 Bean：`@Bean(destroyMethod = "close")` 的 `HikariDataSource`（`maximumPoolSize=4`、`readOnly=true`、`poolName="readonly-sql"`），与 `PostgresReadOnlySqlExecutor`。框架 pom 不新增依赖，`mybatis-plus-spring-boot3-starter` 已带入 `spring-boot-starter-jdbc` 与 HikariCP。
7. `PostgresReadOnlySqlExecutor.query`：取 `TenantContextHolder.get()`，为空抛 `TenantContextMissingException`；有效行数取 `Math.min(maxRows <= 0 ? 默认值 : maxRows, 默认值)`。连接上依次 `setReadOnly(true)`、`setAutoCommit(false)`、`SET TRANSACTION READ ONLY`、`SELECT set_config('app.tenant_id', ?, true)`、`SET LOCAL statement_timeout = <毫秒数>`（整数拼接，不接受外部字符串），`Statement.setMaxRows` 限行，查完 `commit()`，`finally` 恢复 `autoCommit` 与 `readOnly` 并关连接。所有 `SQLException` 包成 `UncategorizedSQLException(sql, e)` 抛出，测试按 `DataAccessException` 与 root cause 断言。
8. `AutoConfiguration.imports` 末尾追加两行全限定名（`com.ragagent.framework.stream.StreamEventLogConfig`、`com.ragagent.framework.sql.ReadOnlySqlConfig`），不新建其他注册文件。
9. `ArchitectureRulesTest` 的 `jdbcTemplateAllowedOnlyInTenantAndRetrieval` 放行包改为 `"com.ragagent.framework.tenant..", "com.ragagent.retrieval..", "com.ragagent.framework.sql.."`，其余规则不动。

行数预算：`MemoryStreamEventLog` 与拆后的 `RedisStreamEventLog` 都必须在 400 行内，超出时继续按职责拆分（live-run 那组方法可再拆到 `StreamRedisLiveRun`），**不加 Checkstyle 抑制**。

- [ ] **Step 4: 跑绿**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl framework -am test -Dtest='StreamEventLogTest,ReadOnlySqlExecutorTest'`
Expected: PASS。`StreamEventLogTest` 4 个用例 × 2 个后端 = 8 条（显示为 `appendThenReadFromOffsetReturnsOnlyNewEvents[memory]` 等），`ReadOnlySqlExecutorTest` 4 条。

再跑全模块与架构规则：

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl framework -am test`
Expected: BUILD SUCCESS，之前所有 framework 用例仍绿。

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl app -am test -Dtest=ArchitectureRulesTest`
Expected: PASS。若 `jdbcTemplateAllowedOnlyInTenantAndRetrieval` FAIL，先确认 `framework.sql` 没有残留的裸 `JdbcTemplate` 依赖，再核对放行包名拼写。

反向验证一次：把 `PostgresReadOnlySqlExecutor` 的 `set_config('app.tenant_id', ?, true)` 注释掉，重跑 `tenantsAreIsolatedThroughRowLevelSecurity`。
Expected: FAIL，租户 1 读到 0 行（策略 fail-closed）。确认后恢复。不做这一步时，策略未生效、只读角色权限过大都会假绿。

- [ ] **Step 5: Commit**

```bash
git add backend/framework/src/main/java/com/ragagent/framework/stream
git add backend/framework/src/main/java/com/ragagent/framework/sql
git add backend/framework/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
git add backend/framework/src/test/java/com/ragagent/framework/stream/StreamEventLogTest.java \
  backend/framework/src/test/java/com/ragagent/framework/sql/ReadOnlySqlExecutorTest.java
git add backend/app/src/test/java/com/ragagent/arch/ArchitectureRulesTest.java
git commit -m "feat(framework): 流分发存储与只读 SQL 执行器"
```

---

### Task 12: OpenAPI 导出与前端类型生成流水线

**Files:**
- Create: `backend/app/src/main/resources/application-openapi.yml`
- Create: `backend/app/src/test/java/com/ragagent/openapi/OpenApiExportTest.java`
- Create: `frontend/packages/api-client/package.json`
- Create: `frontend/packages/api-client/tsconfig.json`
- Create: `frontend/packages/api-client/scripts/generate.mjs`
- Create: `frontend/packages/api-client/scripts/check-drift.mjs`
- Create: `frontend/packages/api-client/src/schema.d.ts`（生成产物，首次提交）
- Create: `frontend/packages/api-client/src/client.ts`
- Create: `frontend/packages/api-client/src/client.test.ts`
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: Task 11b 的 app 运行配置（`springdoc.api-docs.enabled: false` 默认值与 `permit-paths` 里的 `/v3/api-docs/**`）
- Produces:
  - `backend/app/target/openapi.json`：由测试写出，作为生成输入
  - `npm run gen:api`（在 `frontend/packages/api-client` 下）读取 `OPENAPI_FILE`（默认 `<repo>/backend/app/target/openapi.json`）生成 `src/schema.d.ts`
  - `npm run check:api` 重新生成后与工作区比对，有差异则非 0 退出
  - `npm run typecheck` 对 `src` 跑 `tsc --noEmit`；`npm test` 跑 `node --experimental-strip-types --test src/client.test.ts`
  - `createApiClient(options: ApiClientOptions)`，返回 `openapi-fetch` 的 `Client<paths>`；`ApiClientOptions { baseUrl: string; getToken: () => string | null; getLanguage: () => string; fetch?: typeof fetch }`；`class ApiError extends Error { code: string; msg: string }`
  - GitHub Actions 新增 job `api-drift`：跑 `mvn ... -pl app -am test -Dtest=OpenApiExportTest`，再 `npm run check:api`，失败即红

- [ ] **Step 1: 写失败测试 `OpenApiExportTest`**

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("openapi")
class OpenApiExportTest extends PostgresContainerTest {

	@Autowired
	private TestRestTemplate rest;

	@Test
	void exportsOpenApiDocumentToBuildDirectory() throws Exception {
		String json = rest.getForObject("/v3/api-docs", String.class);
		assertThat(json).contains("\"openapi\"");
		Files.writeString(Path.of("target/openapi.json"), json);
	}
}
```

`/v3/api-docs` 在 Task 11b 的 `permit-paths` 里，匿名可访问，不会被 Task 8 的 fail-closed 默认规则拦成 401。

- [ ] **Step 2: 运行，确认失败**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl app -am test -Dtest=OpenApiExportTest`
Expected: FAIL，响应体不含 `"openapi"`（404）。红灯来自 Task 11b 把 `springdoc.api-docs.enabled` 默认关掉，而 `openapi` profile 的配置文件还不存在。

校验已确认：springdoc 2.8.17 的 `springdoc.api-docs.enabled` 默认是 **true**（`matchIfMissing = true`），尽管它的 `spring-configuration-metadata.json` 里错标成 `false`，IDE 提示会误导，以 `matchIfMissing` 为准。所以必须靠 Task 11b 显式写 `false` 才有这条红灯。

- [ ] **Step 3: 实现导出与生成**

`application-openapi.yml`（放 `src/main/resources`）用 profile `openapi` 激活：

```yaml
springdoc:
  api-docs:
    enabled: true
    path: /v3/api-docs
  paths-to-match: /api/**
```

此时 app 还没有任何 `/api/**` 控制器，导出的文档 `paths` 为空对象，生成的 `schema.d.ts` 只有空的 `paths`。这是预期的，随各域迁入逐步变丰满。

`frontend/packages/api-client/package.json`：
```json
{
  "name": "@ragagent/api-client",
  "private": true,
  "type": "module",
  "scripts": {
    "gen:api": "node scripts/generate.mjs",
    "check:api": "node scripts/check-drift.mjs",
    "typecheck": "tsc --noEmit -p tsconfig.json",
    "test": "node --experimental-strip-types --test src/client.test.ts"
  },
  "dependencies": { "openapi-fetch": "^0.13.0" },
  "devDependencies": {
    "@types/node": "^22.10.0",
    "openapi-typescript": "^7.6.1",
    "typescript": "^5.7.0"
  }
}
```

`generate.mjs` 从环境变量 `OPENAPI_FILE`（默认 `<repo>/backend/app/target/openapi.json`）读 JSON，用 `openapi-typescript` 的 Node API 生成：

```js
import openapiTS, { astToString } from 'openapi-typescript'
import { writeFileSync } from 'node:fs'

const ast = await openapiTS(new URL('file://' + inputPath))
writeFileSync(outPath, '// 由 generate.mjs 生成，勿手改\n' + astToString(ast))
```

校验已确认 v7 导出 `astToString`，`openapiTS` 接受 `URL`/字符串/Buffer。脚本先打印实际安装的版本号，版本不匹配时立刻失败，不要 `try/catch` 吞掉。

`check-drift.mjs`：先调 `generate.mjs`，再 `git diff --exit-code -- src/schema.d.ts`。

`src/client.ts` 签名见 Interfaces。行为：`createClient<paths>({ baseUrl, fetch })`，再 `client.use(middleware)`。中间件 `onRequest` 给请求加 `Authorization: Bearer <getToken()>`（`getToken()` 为空则不加）与 `Accept-Language: <getLanguage()>`；`onResponse` 解析 JSON 外壳 `RestResponse`，`success` 为 false 或 `code` 不是 `"0000"` 时抛 `ApiError`，否则返回只含 `data` 字段内容的新 `Response`（保持原状态码与头）。流式响应（`content-type` 为 `text/event-stream`）原样返回，不解包。

- [ ] **Step 4: 写前端单测并运行**

`src/client.test.ts` 用 `node:test`，注入伪造的 `fetch`，三个用例：

```ts
test('adds Authorization and Accept-Language headers', async () => { /* 断言伪 fetch 收到 "Bearer t1" 与 "zh-CN" */ })
test('unwraps data from a successful envelope', async () => { /* 响应 {code:"0000",success:true,data:{id:"1"}}，断言调用方拿到 {id:"1"} */ })
test('throws ApiError carrying code and msg on a failed envelope', async () => { /* 响应 {code:"6001",success:false,msg:"x"}，断言抛 ApiError 且 code 为 "6001" */ })
```

Run:
```bash
mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl app -am test -Dtest=OpenApiExportTest
ls -l backend/app/target/openapi.json
cd frontend/packages/api-client && npm install && npm run gen:api && npm run check:api && npm run typecheck && npm test
```
Expected: `target/openapi.json` 存在；生成 `src/schema.d.ts`；`check:api` 退出码 0；`typecheck` 无错误；`npm test` 3 个用例通过。Node 低于 22.6 时 `--experimental-strip-types` 不可用，Task 1 Step 0 已要求 Node 22 以上。

- [ ] **Step 5: 接入 CI**

在 `.github/workflows/ci.yml` 新增独立 job（不改现有 `backend` 与 `guards` job）：

```yaml
  api-drift:
    name: OpenAPI 类型无漂移
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@v7
        with:
          fetch-depth: 0
      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - uses: actions/setup-node@v7
        with:
          node-version: '22'
      # -am 会把 -Dtest 也施到 framework 上，没匹配用例时 surefire 默认报错，必须关掉
      - run: mvn -B -f backend/pom.xml -pl app -am test -Dtest=OpenApiExportTest -Dsurefire.failIfNoSpecifiedTests=false
      - run: cd frontend/packages/api-client && npm ci && npm run check:api && npm run typecheck && npm test
```

`fetch-depth: 0` 是必要的：`check-drift.mjs` 里的 `git diff` 需要完整的 git 历史。`npm ci` 要求仓库里提交了 `package-lock.json`，Step 4 的 `npm install` 会生成它，一起提交。

- [ ] **Step 6: Commit**

```bash
git add backend/app/src/main/resources/application-openapi.yml \
  backend/app/src/test/java/com/ragagent/openapi/OpenApiExportTest.java
git add frontend/packages/api-client
git add .github/workflows/ci.yml
git commit -m "feat(api): OpenAPI 导出、前端类型生成与 createApiClient 传输"
```

注意 `frontend/packages/` 下已有 `xlsx-0.20.2.tgz`，`git add frontend/packages/api-client` 只加新目录，不碰它。

---

### Task 13: 安全网：界面端到端录制（旧系统，按页面清单分批）

spec 第 6 节第 2 步要求「在冻结点的旧系统上录制控制台与挂件的主要界面流程」，它是第一阶段验收依据（spec §7.8）。判据只看界面，不看接口：每个页面一份 aria 快照，加关键交互用例。前端组件与页面在第一阶段不改，所以同一组用例在新系统上必须同样通过。

**Files:**
- Create: `tests/e2e/package.json`
- Create: `tests/e2e/playwright.config.ts`
- Create: `tests/e2e/global-setup.ts`
- Create: `tests/e2e/global-teardown.ts`
- Create: `tests/e2e/helpers/{auth,regions,waits,knowledgeBase,model,channel,settings}.ts`
- Create: `tests/e2e/stub/start-stub-llm.sh`
- Create: `tests/e2e/fixtures/sample.txt`
- Create: `tests/e2e/PAGES.md`
- Create: `tests/e2e/README.md`
- Create: `tests/e2e/tests/<域标签>/<页面>.spec.ts`，清单见下表，快照文件在各自的 `<页面>.spec.ts-snapshots/*.aria.yml`
- Modify: `.gitignore`（排除 `tests/e2e/.auth/`、`tests/e2e/test-results/`）

**Interfaces:**
- Consumes: Task 0 的旧系统副本 `../ragagent-legacy`、旧系统的 `scripts/dev-up.sh` 与 `scripts/stub-llm-server.py`
- Produces:
  - 环境变量：`BASE_URL`（被测前端，默认 `http://localhost:5173`）、`E2E_USER`、`E2E_PASSWORD`、`E2E_CHANNEL_ID`（挂件用例用，由 `channel` 批次创建后回填）
  - 用例标题以 `[域标签]` 开头，域标签与路线图 3.3 的域名一致，可单域运行：`npx playwright test --grep "\[knowledge\]"`
  - `tests/e2e/PAGES.md`：页面清单，列：路由或入口、页面名、域标签、覆盖用例文件、状态（已录制、条件页、未定位）
  - 一份在旧系统上通过的快照基线，之后每份域计划的 h 步用它在新栈上转绿

**判据规范（所有用例遵守）：**
1. 页面判据用 `toMatchAriaSnapshot({ name: '<页面>-<状态>.aria.yml' })` 加文本与角色断言，不做像素截图（像素在不同机器上不稳定）。
2. 不断言任何 URL 路径之外的接口信息：不用 `waitForResponse`、不用 `route` 拦截、不断言响应体。
3. 不断言错误提示的措辞，只断言「提示节点出现」与页面停留状态，spec 允许措辞变化。
4. 动态内容（时间、计数、ID、随机后缀）在 aria 快照里用正则占位。
5. 用例自己通过界面造数据，结束时通过界面清理，不依赖接口种子，也不依赖前一个用例留下的数据。知识库名、渠道名等带时间戳后缀。
6. 登录态用 `storageState` 复用，在 `global-setup.ts` 里登录一次，存到 `tests/e2e/.auth/admin.json`。
7. 快照范围取各页面的主内容区域，由 `helpers/regions.ts` 里每个页面一个具名定位器，不抓侧边栏与全局顶栏，避免无关改动让全部快照一起失效。
8. 需要 LLM 的流程（问答、挂件、工具调用）用旧系统自带的 `scripts/stub-llm-server.py` 作为模型，回答固定。`model` 批次负责在界面里建这个模型并设为默认，其余批次依赖它。
9. Playwright 版本要求支持 `toMatchAriaSnapshot` 的具名快照文件（1.51 及以上），写进 `package.json`。

**页面清单**（盘点来源：冻结点 `frontend/src/router/index.ts`、`views/settings/Settings.vue` 的分组与 `config/integrations.ts`。Step 3 用浏览器在旧系统上逐页核对，核对结果与偏差写进 `PAGES.md` 并登记 `deviations.md`）

| 域标签 | 页面 | 路由或入口 | 快照用例 | 关键交互用例 | 文件 |
|---|---|---|---|---|---|
| auth | 登录页 | `/login` | 未登录登录页 | 正确账号进入知识库列表；错误密码停留在登录页并出现错误提示节点 | `auth/login.spec.ts` |
| auth | 个人资料 | 设置 `?section=userprofile` | 资料页 | 打开修改密码对话框再取消 | `auth/user-profile.spec.ts` |
| auth | 平台 API Key | 设置 `?section=platform-api-keys` | 列表页 | 创建 Key 后列表出现，删除后消失 | `auth/platform-api-keys.spec.ts` |
| auth | API 集成 | 设置 `?section=integration-api` | 集成页 | 无，只做快照 | `auth/integration-api.spec.ts` |
| tenant | 租户信息 | 设置 `?section=tenant` | 信息页 | 修改描述并保存，刷新后仍在 | `tenant/tenant-info.spec.ts` |
| tenant | 成员管理 | 设置 `?section=members` | 成员列表 | 打开添加成员对话框再取消 | `tenant/tenant-members.spec.ts` |
| tenant | 工作区引导 | `/onboarding/workspace` | 引导页 | 条件页，只有无租户账号能到达，见 Step 3 | `tenant/onboarding.spec.ts` |
| system | 通用设置 | 设置 `?section=general` | 设置页 | 切换界面语言再切回 | `system/general-settings.spec.ts` |
| system | 版本信息 | 设置 `?section=system` | 信息页 | 无 | `system/version-info.spec.ts` |
| system | 全局系统设置 | 设置 `?section=system-global` | 设置页 | 修改一项并保存，恢复原值 | `system/system-global.spec.ts` |
| system | 任务队列 | 设置 `?section=runtime-queues` | 队列页 | 无 | `system/runtime-queues.spec.ts` |
| system | 解析引擎 | 设置 `?section=parser` | 设置页 | 切换一个选项后还原 | `system/parser-engine.spec.ts` |
| audit | 审计日志 | 设置 `?section=system-audit-log` | 日志页 | 在过滤控件输入条件后列表刷新 | `audit/audit-log.spec.ts` |
| storage | 存储后端 | 设置 `?section=storage` | 列表页 | 新建后端，列表出现，删除 | `storage/storage-backend.spec.ts` |
| model | 模型管理 | 设置 `?section=models` | 列表页 | 新增指向 stub 的对话模型，列表出现，测试连接，设为默认；其余批次依赖它，不在用例末尾删除，由 `global-teardown` 统一清理 | `model/model-settings.spec.ts` |
| model | Ollama | 设置 `?section=ollama` | 设置页 | 无 | `model/ollama.spec.ts` |
| vectorstore | 向量库 | 设置 `?section=vectorstore` | 列表页 | 打开新增对话框并取消，不断言下拉选项数量（第一阶段会删 6 种引擎） | `vectorstore/vectorstore-settings.spec.ts` |
| websearch | 网页搜索 | 设置 `?section=websearch` | 列表页 | 新增提供商，列表出现，删除 | `websearch/websearch-settings.spec.ts` |
| knowledge | 知识库列表 | `/platform/knowledge-bases` | 空态与有数据两种 | 新建、编辑、删除；全局命令面板输入关键字出现结果 | `knowledge/kb-list.spec.ts` |
| knowledge | 文档管理 | `/platform/knowledge-bases/:kbId` 文档页签 | 空态与有文档两种 | 上传 `sample.txt` 并等待解析完成；卡片与列表视图切换；新建文件夹；管理标签；批量选择栏出现；删除文档 | `knowledge/kb-documents.spec.ts` |
| knowledge | FAQ | 同上 FAQ 页签 | 列表页 | 新增条目，列表出现，删除 | `knowledge/kb-faq.spec.ts` |
| knowledge | 知识库设置 | 同上设置页签，含模型、分块、解析、存储、向量库、高级、索引策略、动态、图谱 | 每个子页签一份 | 修改分块大小并保存，刷新后保持 | `knowledge/kb-settings.spec.ts` |
| wiki | Wiki 浏览 | 同上 Wiki 页签 | 浏览页 | 新建页面，打开版本抽屉；需要先在知识库里开启 Wiki | `wiki/wiki-browser.spec.ts` |
| datasource | 数据源 | 同上数据源设置 | 列表页 | 打开数据源编辑对话框选类型后取消；同步日志页快照 | `datasource/datasource-settings.spec.ts` |
| agent | 智能体列表 | `/platform/agents` | 列表页 | 新建，列表出现，编辑，删除 | `agent/agent-list.spec.ts` |
| agent | 技能管理 | 设置 `?section=skill-management` | 列表页 | 新建技能，列表出现，删除 | `agent/skill-management.spec.ts` |
| mcp | MCP 服务 | 设置 `?section=mcp` | 列表页 | 新增服务（URL 指向不可达地址），列表出现，点击测试后结果面板出现，删除 | `mcp/mcp-settings.spec.ts` |
| memory | 记忆工作区设置 | 设置 `?section=memory` | 设置页 | 切换开关并保存，刷新后保持 | `memory/memory-workspace-settings.spec.ts` |
| memory | 我的记忆 | 设置 `?section=mymemory` | 列表页 | 打开添加对话框再取消 | `memory/my-memory.spec.ts` |
| favorite | 收藏 | 入口未在路由中出现 | 待定位 | 待定位，Step 3 核对 | `favorite/favorites.spec.ts` |
| channel | IM 渠道 | 设置 `?section=integration-im` | 列表页 | 打开新建对话框选平台后取消 | `channel/integration-im.spec.ts` |
| channel | 嵌入渠道 | 设置 `?section=integration-embed` | 列表页 | 新建嵌入渠道，嵌入代码出现，记录渠道 ID 回填 `E2E_CHANNEL_ID` | `channel/integration-embed.spec.ts` |
| channel | 挂件首屏 | `/embed/:channelId`（独立入口 `embed.html`） | 首屏：欢迎语与输入框 | 无 | `channel/widget-shell.spec.ts` |
| session | 新建对话 | `/platform/creatChat`、`/platform/knowledge-bases/:kbId/creatChat` | 两个入口各一份 | 选择知识库进入对话页 | `session/new-chat.spec.ts` |
| session | 对话页 | `/platform/chat/:chatid` | 回答完成后的页面 | 发送问题；流式期间「停止」可见，结束后消失；回答气泡出现；引用卡片出现；历史列表出现该会话 | `session/chat.spec.ts` |
| session | 对话历史设置 | 设置 `?section=chathistory` | 设置页 | 修改保留设置，刷新后保持 | `session/chat-history-settings.spec.ts` |
| session | 挂件对话 | `/embed/:channelId` | 回答完成后的页面 | 输入问题，回答气泡出现，打开历史抽屉 | `session/widget-chat.spec.ts` |
| agenttools | 工具审批 | 智能体对话页内的审批卡片 | 卡片出现时的页面 | 触发需审批的工具，点击允许，工具结果渲染；依赖 stub 能返回工具调用，Step 3 核对 | `agenttools/tool-approval.spec.ts` |

跳转型路由（`/`、`/platform`、`/platform/tenant`、`/integrations`、`/system/*`、`/knowledge-search`、`/join`）和开发用页面 `/platform/dev/markdown` 不是页面，不进清单。`/register` 与受邀注册共用登录组件，需要邀请 token，Step 3 核对是否可达。

- [ ] **Step 1: 在旧系统副本里起旧系统与 stub 模型**

Run:
```bash
cd ../ragagent-legacy
cp -n .env.example .env
bash scripts/dev-up.sh
curl -s localhost:8083/health
grep -n "VITE_DEV_PROXY_TARGET" frontend/vite.config.ts
```
Expected: 旧后端在 8083 可访问，`curl` 返回非空。前端代理目标的环境变量名是 `VITE_DEV_PROXY_TARGET`，默认 `http://localhost:8083`，与旧后端一致，无需设置。`dev-up.sh` 不带 `--with-frontend` 时只起基础设施与后端；前端另起：

```bash
cd ../ragagent-legacy/frontend && npm ci && npm run dev
```
Expected: 前端在 5173 可访问。`npm run dev` 在前台阻塞，用另一个终端或后台运行，确认 5173 可访问后再继续。

确认管理员账号怎么来：读 `../ragagent-legacy/.env.example` 与 `docs/17-多租户认证与RBAC.md`，并在浏览器打开 `/login` 看是否走 `autoSetup` 免登录。把答案写进 `tests/e2e/README.md`。若旧系统走免登录，`auth/login.spec.ts` 的账号密码用例标 `test.fixme(true, '旧系统免登录')`，其余用例用 `global-setup.ts` 里取得的会话。

`stub/start-stub-llm.sh`：起 `../ragagent-legacy/scripts/stub-llm-server.py`，打印监听地址，`model` 批次用这个地址建模型。

- [ ] **Step 2: 写基础设施**

`package.json` 依赖 `@playwright/test@^1.52.0`，脚本 `test`、`test:update`（`playwright test --update-snapshots`）。

`playwright.config.ts`：`testDir: './tests'`、`use.baseURL: process.env.BASE_URL ?? 'http://localhost:5173'`、`retries: 0`、`trace: 'retain-on-failure'`、`video: 'retain-on-failure'`、`timeout: 120_000`（入库轮询需要）、`workers: 1`（用例共享同一租户的数据，串行避免互相污染）、`globalSetup`、`use.storageState: 'tests/e2e/.auth/admin.json'`（登录用例自己覆盖为空）。

`helpers/` 里每个文件一类可复用的界面操作，全部走界面，不调接口：`auth.ts` 登录；`regions.ts` 各页面主内容区域的具名定位器；`waits.ts` 等待页面状态（例如文档解析完成的文案出现，最长 90 秒）；`knowledgeBase.ts` 建库、上传、删库；`model.ts` 建 stub 模型；`channel.ts` 建嵌入渠道；`settings.ts` 打开设置页指定分区。

- [ ] **Step 3: 盘点核对并写 `PAGES.md`**

用 chrome-devtools 工具在旧系统上逐页打开上表的每个页面，核对三件事并把结果写进 `tests/e2e/PAGES.md`：路由或入口是否存在、页面能否按表里的方式到达、表里没有但实际存在的页面。重点核对四处：`tenant/onboarding`（是否有办法造出无租户账号）、`favorite`（收藏入口在哪个页面）、`agenttools/tool-approval`（`stub-llm-server.py` 是否支持返回工具调用的场景，不支持则在 `PAGES.md` 登记并在计划 08 开始前补 stub 场景）、`/register`（受邀注册是否可达）。

状态列只允许三个值：`已录制`、`条件页`（只有特定账号状态才能到达）、`未定位`。未定位的页面不写用例，登记到 `deviations.md`，由对应域计划的 h 步在迁入时补上。

核对结果与上表不一致时，同一提交里改本计划的表。

- [ ] **Step 4: 批次流程（每个域标签一个批次，下面所有批次共用）**

对一个域标签 `<tag>`：
1. 按上表为该域的每个页面写 spec。快照用例先写好空的 `toMatchAriaSnapshot({ name: '...' })`，交互用例写完整断言。
2. 第一次运行带 `--update-snapshots` 生成基线：`cd tests/e2e && npx playwright test --grep "\[<tag>\]" --update-snapshots`。
3. 人工翻看生成的 `*.aria.yml`，把时间、计数、ID 等动态内容改成正则占位。基线里出现的任何依赖本机数据的文字都要处理。
4. 不带更新参数连续运行两次：`npx playwright test --grep "\[<tag>\]"`。
Expected: 两次都全绿。第二次与第一次结果不同说明有不稳定的内容，回到第 3 步，不要靠重试掩盖。
5. 提交：`git add tests/e2e/tests/<tag> tests/e2e/helpers tests/e2e/PAGES.md`，`git commit -m "test(e2e): <tag> 页面端到端与快照基线"`。在 `progress.md` 追加一行。

任何一条在旧系统上不绿，先修测试而不是改业务代码，安全网必须是对旧系统行为的忠实记录。

- [ ] **Step 5: 批次 auth（先做，兼作全局登录）**

先写 `global-setup.ts` 与 `auth/login.spec.ts`，再按 Step 4 的流程跑通 `[auth]` 全部页面。完成时 `.auth/admin.json` 已能被其余批次复用。

- [ ] **Step 6: 批次 model（先于所有需要 LLM 的批次）**

`[model]` 批次在界面里建指向 stub 的模型并设为默认。由于 knowledge、agent、session 等批次依赖它，这个模型由 `global-setup.ts` 在登录后确保存在（不存在则通过界面创建），由 `global-teardown.ts` 在全部结束后统一删除。

- [ ] **Step 7: 其余批次**

按下面的顺序各做一个批次，每个批次走 Step 4 的流程，各自提交：`tenant`、`system`、`audit`、`storage`、`vectorstore`、`websearch`、`knowledge`、`wiki`、`datasource`、`agent`、`mcp`、`memory`、`channel`、`session`、`agenttools`、`favorite`。

顺序约束：`channel` 的嵌入渠道用例产出 `E2E_CHANNEL_ID`，要在 `session` 的挂件对话之前；`knowledge` 要在 `wiki`、`datasource`、`session` 之前，因为它们都需要知识库与已入库文档。`favorite` 与 `agenttools` 放最后，它们是 Step 3 里最可能标为未定位或依赖 stub 扩展的两项。

- [ ] **Step 8: 全量运行并确认覆盖**

Run:
```bash
cd tests/e2e
E2E_USER=*** E2E_PASSWORD=*** npx playwright test
```
Expected: 全部通过，`skipped` 只包含 `PAGES.md` 里状态为 `条件页` 或 `未定位` 的用例，且每一个都有 `fixme` 理由。再核对：`PAGES.md` 里每个页面要么有用例文件，要么状态为 `未定位` 并已登记 `deviations.md`。

- [ ] **Step 9: 登记**

在 `progress.md` 追加 Task 13 的汇总行，写明页面总数、已录制数、条件页数、未定位数。

---

### Task 14: 安全网：检索回归基线

**Files:**
- Create: `tests/regression/package.json`
- Create: `tests/regression/corpus/{zh,en,ja,ar}/*.md`
- Create: `tests/regression/cases.json`
- Create: `tests/regression/adapters/legacy.mjs`
- Create: `tests/regression/record.mjs`
- Create: `tests/regression/compare.mjs`
- Create: `tests/regression/compare.test.mjs`
- Create: `tests/regression/baseline.json`（录制产物，提交）
- Create: `tests/regression/README.md`

**Interfaces:**
- Consumes: Task 0 的旧系统副本、旧系统检索接口 `POST /api/v1/knowledge-bases/{kbId}/hybrid-search`（已存在，请求体 `{"queryText": "..."}`，响应是**裸 JSON 数组**）。每个结果项的字段（snake_case，见 `contract/src/main/java/com/ragagent/contract/retrieval/SearchResult.java`）：`id`、`content`、`knowledge_id`、`chunk_index`、`knowledge_title`、`knowledge_filename`、`score`、`match_type`
- Produces:
  - **比对键是内容哈希，不是 chunk ID。** 新系统重新入库，主键又改成雪花 ID（D22），同一个分块在新旧系统里的 ID 必然不同。比对键 `contentSha1` 的算法：内容去首尾空白，连续空白折叠成一个空格，取 SHA-1 的十六进制小写。`knowledge_filename` 只用于报告
  - 语料放进仓库（`corpus/`），新系统用同一批文件重新入库，保证两边分块输入相同
  - `cases.json` 形状：`[{"id":"q01","kbName":"e2e-regression","query":"...","language":"zh-CN","expected":[{"docName":"manual-zh-01.md","contentSha1":"<40 位十六进制>"}]}]`
  - `baseline.json` 形状：`[{"id":"q01","hits":[{"contentSha1":"...","docName":"manual-zh-01.md","chunkIndex":3}]}]`，每条最多 10 个命中，按名次
  - 适配器接口：`adapters/<name>.mjs` 导出 `ingestCorpus(corpusDir): Promise<{ kbName: string }>` 与 `search(kbName, query, language): Promise<Array<{ content: string; knowledgeFilename: string; chunkIndex: number }>>`。本任务只写 `legacy.mjs`，新系统的适配器 `rebuild.mjs` 由计划 04 写
  - `record.mjs --adapter legacy --out baseline.json`
  - `compare.mjs --baseline a.json --candidate b.json [--min-expected 1.0] [--min-same 0]`，输出两个数并按阈值决定退出码：
    - `expectedRecall`：`expected` 中 `contentSha1` 出现在对应问题命中里的比例
    - `sameAsBaseline`：候选 top10 的 `contentSha1` 序列与基线完全一致的问题占比，仅作报告，默认阈值 0。原因：第一阶段 PGroonga 替换 ParadeDB 后关键词打分不同，名次会变，只有 RRF 融合后的召回要求不退化

`expected` 是人工标注的「正确答案」。spec 第 6 节第 2 步写的是「固定一组问题与期望命中的分块」，只用旧系统 top10 作基线不够：旧系统漏掉的问题，新系统同样漏也会被算满分。标注方式：先跑一次旧系统拿到 top10，人工从其中挑出真正相关的前 1 到 3 个分块填进 `expected`；某个问题旧系统一个都没命中时，人工从语料里找出正确分块，用相同的哈希算法算出 `contentSha1` 填进去，这样这条用例同时能测出新旧系统的差距。

**embedding 模型。** 检索质量取决于真实的 embedding 模型。`scripts/stub-llm-server.py` 的 `/embeddings` 固定返回 3 维向量 `[0.1, 0.2, 0.3]`，用它建出的向量检索没有语义，不能作为召回基线。执行方在旧系统里配置一个真实的 embedding 模型，把模型名与 endpoint 写进 `README.md`。新旧系统必须用同一个模型，否则对比无意义。API Key 不进仓库，用环境变量 `EMBEDDING_API_KEY`。

- [ ] **Step 1: 写语料与问题集**

语料围绕同一个虚构产品「星河云盘」的用户手册节选，每种语言 3 到 5 篇、每篇 800 到 2000 字：`zh` 4 篇、`en` 4 篇、`ja` 3 篇、`ar` 3 篇，文件名形如 `manual-zh-01.md`。用虚构产品是为了让每个问题只有明确的分块能回答，不受外部常识干扰。

`cases.json` 写 20 条问题，分布：中文 8、英文 5、日语 3、阿拉伯语 2、跨语言 2（用英文问中文文档里的内容，反之亦然）。`expected` 先留空，Step 4 标注。

- [ ] **Step 2: 写适配器与 `record.mjs`**

`adapters/legacy.mjs`：`ingestCorpus` 通过旧系统接口建知识库 `e2e-regression`、逐个上传 `corpus/**/*.md`、轮询到全部解析完成；`search` 调 `hybrid-search`，把响应项映射成适配器契约的三个字段。请求头带 `Authorization: <E2E_TOKEN>` 与 `Accept-Language: <language>`。

`E2E_TOKEN` 的取法与旧系统一致：不要自己拼 `Bearer ` 前缀，先用**旧系统实际接受的那个值**跑通一条请求，确认后再写进脚本。旧后端有多个鉴权通道（会话 token、API Key），取值方式见 `domains/src/main/java/com/ragagent/auth/apikey/controller/filter/APIKeyAuthChannel.java` 与 Task 13 抓到的登录行为。这一步不通时先解决鉴权，不要改基线口径。

`record.mjs`：读 `cases.json`，逐条调适配器的 `search`，对每个结果项算 `contentSha1`，取前 10 个，写 `--out` 指定的文件。

- [ ] **Step 3: 运行，确认产出基线**

Run:
```bash
cd tests/regression && npm install
E2E_TOKEN=*** EMBEDDING_API_KEY=*** BASE_URL=http://localhost:8083 node record.mjs --adapter legacy --out baseline.json
node -e "const b=JSON.parse(require('fs').readFileSync('baseline.json','utf8'));console.log(b.length, b.map(x=>x.hits.length).join(','))"
```
Expected: 20 条，每条 10 个命中。

若某条 query 在旧系统返回 0 命中，说明问题集或知识库有问题，**先修问题集或语料**，不要把空结果录进基线。

- [ ] **Step 4: 标注 `expected` 并回写**

对每条问题，打开 `baseline.json` 里它的 top10，对照语料挑出真正相关的分块，把 `docName` 与 `contentSha1` 填进 `cases.json` 的 `expected`。旧系统没命中正确分块的问题，按上面的办法手算哈希填入。标注完用 Step 3 的命令重跑一次，确认 `baseline.json` 没有变化（说明录制是确定的）。

- [ ] **Step 5: 写 `compare.mjs` 与它的单测**

`compare.test.mjs` 用 `node:test`，三个用例，内联构造两份小数据：

```js
test('self comparison gives recall 1 and same 1', () => { /* 断言 expectedRecall === 1、sameAsBaseline === 1、退出码 0 */ })
test('candidate missing an expected hit lowers recall and exits non-zero under min-expected 1.0', () => { /* 删掉候选里一个期望命中，断言 expectedRecall < 1 且判定为失败 */ })
test('reordered candidate lowers sameAsBaseline but keeps recall under default min-same 0', () => { /* 打乱名次，断言 sameAsBaseline < 1、recall 仍为 1、判定为通过 */ })
```

Run: `cd tests/regression && node --test compare.test.mjs`
Expected: 3 个用例通过。

- [ ] **Step 6: 自比对**

Run: `cd tests/regression && node compare.mjs --baseline baseline.json --candidate baseline.json --min-expected 1.0`
Expected: `expectedRecall = 1.000`，`sameAsBaseline = 1.000`，退出码 0。

两个指标各管一件事：`expectedRecall` 抓质量，`sameAsBaseline` 抓行为漂移。只看前者放过「同样的召回、不同的排序」，只看后者在引擎替换后会误报。召回阈值设 1.0 起步，域迁入实测后再决定是否放宽。

- [ ] **Step 7: Commit**

```bash
git add tests/regression
git commit -m "test(regression): 检索回归语料、基线与内容哈希对比脚本"
```

---

### Task 15: 安全网：API 冒烟骨架（先红后绿）

**Files:**
- Create: `tests/api-smoke/package.json`
- Create: `tests/api-smoke/playwright.config.ts`
- Create: `tests/api-smoke/tests/skeleton-auth.spec.ts`
- Create: `tests/api-smoke/tests/auth.spec.ts`
- Create: `tests/api-smoke/tests/knowledge-flow.spec.ts`
- Create: `tests/api-smoke/tests/chat-flow.spec.ts`
- Create: `tests/api-smoke/README.md`

**Interfaces:**
- Consumes: Task 11b 的可启动 app（`/actuator/health` 可达）、新 API 的三组前缀（spec 5.6）：`/api/admin/v1`、`/api/v1`、`/api/open/v1`
- Produces: 六条流程的冒烟用例，全部标 `test.fixme()`；另有一条**立刻可跑**的鉴权用例，验证新骨架上的登录拦截

`test.fixme()` 的语义是「跳过」，`test.fixme(condition, description)` 只在 condition 为真时跳过。本计划用 `test.fixme(true, '待 <域> 迁入')` 写在测试体第一行。**不要**写成 `test.skip()`：`skip` 在报告里和「主动不测」混在一起，`fixme` 在 Playwright 报告里单列，更容易看出来还有多少未转绿。

另起一条不属于 fixme 的用例 `skeleton-auth.spec.ts`，现在就能跑：对新骨架 `GET /api/admin/v1/auth/me` 不带 token，期望 401 加 `code=4402`。选这个端点是因为 auth 域迁入后它仍然要求登录、仍返回 401，这条用例不会随域迁入转红。它同时验证「新骨架能起、异常处理器生效、Sa-Token 拦截就位」。若它红了，说明 Task 7 或 Task 8 或 Task 11b 有问题。

本计划钉住的端点（后续可改，改动同步改本表）：

| 步骤 | 方法与路径 | 请求 | 响应断言 |
|---|---|---|---|
| 登录 | `POST /api/admin/v1/auth/login` | `{username,password}` | `code=0000`，`data.token` 非空 |
| 建库 | `POST /api/admin/v1/knowledge-bases` | `{name}` | `code=0000`，`data.id` 为字符串 |
| 上传 | `POST /api/admin/v1/knowledge-bases/{kbId}/documents` | multipart，字段 `file` | `code=0000`，`data.id` |
| 入库状态 | `GET /api/admin/v1/knowledge-bases/{kbId}/documents/{docId}` | 无 | 轮询到 `data.status=COMPLETED` |
| 控制台问答 | `POST /api/admin/v1/knowledge-chat/{sessionId}` | `{query}` | `text/event-stream`，至少一条 data 帧 |
| 挂件问答 | `POST /api/v1/embed/{channelId}/chat` | `{sessionId,query}` | `text/event-stream`，至少一条 data 帧 |

控制台问答归 `/api/admin/v1`，挂件问答归 `/api/v1`，与路线图 3.6 一致：后台登录用户用的接口都在管理面，`/api/v1` 只给访客身份。

- [ ] **Step 1: 写骨架**

每个 `test()` 第一行加 `test.fixme(true, '待 <域> 迁入')`，测试体写完整断言，不写空壳。

另写 `skeleton-auth.spec.ts`，不加 `fixme`：

```ts
test('新骨架未登录访问管理面返回 4402', async ({ request }) => {
  const rsp = await request.get(`${process.env.API_BASE ?? 'http://localhost:8080'}/api/admin/v1/auth/me`)
  expect(rsp.status()).toBe(401)
  const body = await rsp.json()
  expect(body.code).toBe('4402')
  expect(body.success).toBe(false)
})
```

- [ ] **Step 2: 运行，确认跳过与通过符合预期**

先按 Task 11b Step 5 把新骨架在 8080 起起来（`spring-boot:run` 加 `dev` profile），再：

Run: `cd tests/api-smoke && npm install && npx playwright test`
Expected: 1 passed（`skeleton-auth`），6 skipped，0 failed。

API 冒烟的两套目标用两个环境变量区分：`API_BASE`（新骨架，默认 8080）与 `LEGACY_BASE`（旧系统，默认 8083）。`skeleton-auth` 跑新骨架，其余 6 条转绿后也跑新骨架。新骨架未启动时 `skeleton-auth` 会失败，这是预期，它本来就是「新骨架能起」的检查。

- [ ] **Step 3: Commit**

```bash
git add tests/api-smoke
git commit -m "test(api): 新 API 冒烟骨架（按域迁入逐个转绿）"
```

---

### Task 16: D26：MCP 官方 SDK 半天验证

**Files:**
- Create: `docs/optimization/spikes/2026-11-11-mcp-sdk-verification.md`
- Create: `docs/optimization/spikes/mcp-sdk-probe/pom.xml`
- Create: `docs/optimization/spikes/mcp-sdk-probe/src/main/java/probe/RedirectProbe.java`
- Create: `docs/optimization/spikes/mcp-sdk-probe/src/main/java/probe/TokenProbe.java`

**Interfaces:**
- Consumes: 无
- Produces: 一份结论文档，两个必答问题：
  1. 每一条 302/307 重定向能否被应用自己的 HTTP 拦截器看到并做 SSRF 校验
  2. OAuth 访问令牌能否按「用户身份 + 请求」逐次注入，而不是客户端构造时固定

- [ ] **Step 1: 搭探测工程**

依赖 `io.modelcontextprotocol.sdk:mcp-core:2.1.0`（Maven Central 最新稳定版；若 2.1.0 不在镜像源，用 2.0.1 并在文档里注明实际版本）。不依赖 Spring。

先确认镜像源能解析，再往下做：

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml dependency:get -Dartifact=io.modelcontextprotocol.sdk:mcp-core:2.1.0`
Expected: `BUILD SUCCESS`。失败时降版本重试，并在文档首段写明实际用的版本。

- [ ] **Step 2: 重定向探测（多跳 + 两种传输）**

spec D26 的通过标准是「每一跳」。只探一跳不足以支撑这个结论，要覆盖：

| 维度 | 取值 | 为什么必须测 |
|---|---|---|
| 跳数 | 1 跳、3 跳 | 单跳通过不代表 SDK 用的是逐跳可控的客户端；有的 HTTP 客户端一次调用内部跟随全部跳数 |
| 状态码 | 301、302、307、308 | 304 无关；301/302 可能被降级成 GET，307/308 保持方法与 body |
| 跨主机 | 同端口换 path、换端口、换 host（`127.0.0.1` → `localhost`） | SSRF 校验要按目标 host 判断，同端口内重定向不会触发 |
| 传输 | Streamable HTTP（普通 JSON 响应）、SSE（`text/event-stream`） | spec 5.12 现用 SSE 与 Streamable HTTP 都要能接；SSE 长连接下重定向发生在建流阶段，与普通请求不是同一代码路径 |

用三个本地服务构成 A → B → C，A 与 B 都返回重定向，C 返回合法 `initialize` 响应，用不同参数组合跑上表。

每个组合记录：SDK 是否跟随、跟随了几跳、能否在每一跳前插入自定义校验（替换 `HttpClient` 或注册拦截器）、重定向后 `Authorization` 头是否被保留（安全上要确认不会把令牌带到第三方主机）。

通过标准：**上表每一格都能在每一跳前插入校验，且令牌不会跨主机泄漏**。任一格做不到记为未通过，并在文档里写明是哪一格。

- [ ] **Step 3: 令牌注入探测**

`TokenProbe` 起一个记录 `Authorization` 头的本地服务，连续两次调用（模拟用户 U1、U2），断言服务端看到 `Bearer t1`、`Bearer t2`。

验证 `McpSyncClient` 的 OAuth 提供者接口是否支持「每次请求取当前用户令牌」，而不是构造期绑定。

通过标准：**能**按请求注入。只能构造期绑定即未通过。

- [ ] **Step 4: 写结论**

文档结构：结论（采用/不采用）、两个问题的实测结果与证据（代码位置、抓到的请求头、重定向次数）、若采用的适配层改动点、若退回自研的缺口补齐清单。

- [ ] **Step 5: 更新 spec**

若任一项未通过，修改 spec 的 D26，把「官方 SDK」改为「自研并补齐 X」。

- [ ] **Step 6: Commit**

```bash
git add docs/optimization/spikes/2026-11-11-mcp-sdk-verification.md docs/optimization/spikes/mcp-sdk-probe
git add docs/optimization/specs/2026-11-09-architecture-rebuild-design.md
git commit -m "spike(mcp): 官方 SDK 重定向与令牌注入验证"
```

---

### Task 17: D27：Nimbus 对 RFC 9728 的支持核实

**Files:**
- Create: `docs/optimization/spikes/2026-11-11-nimbus-rfc9728.md`
- Create: `docs/optimization/spikes/nimbus-probe/pom.xml`
- Create: `docs/optimization/spikes/nimbus-probe/src/main/java/probe/ResourceMetadataProbe.java`

**Interfaces:**
- Consumes: 无
- Produces: 一份结论文档，回答三件事：
  1. `com.nimbusds:oauth2-oidc-sdk` 是否有受保护资源元数据（RFC 9728）的类
  2. 若没有，是否只需在此基础上补三个类（元数据获取、缓存、挑战头解析）
  3. 后台 OIDC 登录（授权码 + PKCE）与 MCP 授权能否共用同一套 `OIDCProviderMetadata` / `TokenRequest`

- [ ] **Step 1: 查包**

Run:
```bash
mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml dependency:get -Dartifact=com.nimbusds:oauth2-oidc-sdk:11.39
unzip -l $(find ~/.m2 d:/MavenRepository -name "oauth2-oidc-sdk-11.39.jar" 2>/dev/null | head -1) | grep -i "resource\|metadata"
```
Expected: 列出全部候选类名。

- [ ] **Step 2: 写探测代码**

`ResourceMetadataProbe` 用本地 HTTP 服务模拟 `/.well-known/oauth-protected-resource`，验证：能否解析 `WWW-Authenticate: Bearer resource_metadata="..."`、能否据元数据构造令牌请求。若 SDK 没有对应类，就在探测工程里手写三个类并统计行数，这个行数就是「适配层补齐」的成本。

- [ ] **Step 3: 写结论并回写 spec**

文档写：支持程度（原生 / 补 N 个类 / 补更多）、实测行数、两处共用 Nimbus 的可行性。

**不设行数阈值。** spec D27 的决策是「两处共用 Nimbus」，本任务只负责核实「支持到什么程度、缺口有多大」，是否仍然共用由你看过数据后决定。文档里把实测行数与缺口清单列清楚即可；真要改决策，说明理由后回写 spec 的 D27。

- [ ] **Step 4: 提交前确认 Windows 下 unzip 可用**

Step 1 用到 `unzip`。Windows Git Bash 不一定自带，先确认：

Run: `command -v unzip || command -v jar || echo "两者都没有"`
Expected: 至少有一个。都没有时改用 `jar tf <jar 路径> | grep -i "resource\|metadata"`，`jar` 随 JDK 一定存在。

jar 的查找路径要同时覆盖两处本地仓库：`~/.m2/repository` 与 `d:/MavenRepository`（`settings-coding-uat.xml` 里的 `localRepository`）。用 `mvn help:evaluate -Dexpression=settings.localRepository -q -DforceStdout` 取准确路径，不要写死。

- [ ] **Step 5: Commit**

```bash
git add docs/optimization/spikes/2026-11-11-nimbus-rfc9728.md docs/optimization/spikes/nimbus-probe
git add docs/optimization/specs/2026-11-09-architecture-rebuild-design.md
git commit -m "spike(oauth): Nimbus 对 RFC 9728 的支持核实"
```

---
### Task 18: 自建数据库镜像：PostgreSQL 17 + pgvector + PGroonga

**Files:**
- Create: `deploy/db/Dockerfile`
- Create: `deploy/db/build.sh`
- Create: `deploy/db/README.md`
- Create: `deploy/db/init/00-extensions.sql`
- Create: `deploy/db/smoke.sh`
- Create: `deploy/db/docker-compose.yml`（新栈开发库，端口 25432，独立数据卷）
- Modify: `backend/framework/src/test/java/testfixture/support/PostgresContainerTest.java`（把 `PG_IMAGE` 换成自建镜像名）
- Modify: `backend/app/src/main/resources/application-dev.yml`（`DB_URL` 默认指向 25432）
- Modify: `backend/app/scripts/dev-db.sh`（改为对新栈开发库容器建角色与库）
- Modify: `backend/app/README.md`

根目录的 `docker-compose.yml` 与 `.github/workflows/ci.yml` 里现有的 `backend` job **不改**：旧系统依赖 ParadeDB 的 `pg_search`，在第 09 份计划删旧模块之前，旧开发栈和旧 CI 继续用 ParadeDB 镜像。新栈用 `deploy/db/docker-compose.yml`。

**Interfaces:**
- Consumes: Task 11b 的 `deploy/db/init/10-roles.sql`、`backend/app/scripts/dev-db.sh`
- Produces:
  - 镜像 `ragagent/postgres:17-pgvector-pgroonga`，基于 `postgres:17-trixie`，含扩展 `vector`、`pgroonga`、`pg_trgm`、`uuid-ossp`
  - `PG_IMAGE` 常量在 `PostgresContainerTest` 里，值改为 `docker.io/ragagent/postgres:17-pgvector-pgroonga`
  - 构建脚本 `bash deploy/db/build.sh`，产出本地镜像并可 `--push` 推到仓库，支持用环境变量传代理
  - 冒烟脚本 `bash deploy/db/smoke.sh <image>`：起容器，断言四个扩展可 `CREATE EXTENSION`、PGroonga 能建索引并命中、pgvector 能做 `<=>` 排序
  - `deploy/db/docker-compose.yml`：服务 `rebuild-postgres`，容器名 `ragagent-db-dev`，端口 `25432`，数据卷 `ragagent-db-data`，挂载 `deploy/db/init` 到初始化目录

**依赖外部包源。** PGroonga 不在 Debian 官方源，也不在 PGDG 源里。Groonga 项目自己的 apt 源 `packages.groonga.org` 提供 `postgresql-17-pgdg-pgroonga`，构建时要先安装 `groonga-apt-source`。所以镜像构建依赖 `packages.groonga.org` 可达，「不依赖任何外部制品库」的说法不成立。官方安装页当前只列出 Debian trixie，所以基础镜像用 `postgres:17-trixie`，不用 bookworm。`postgresql-17-pgvector` 在 PGDG 源里，官方 `postgres` 镜像已启用 PGDG 源，直接安装。

- [ ] **Step 1: 写 Dockerfile**

```dockerfile
# PostgreSQL 17 + pgvector + PGroonga，供 dev、CI 与 Testcontainers 共用。
# pgvector 来自 PGDG 源；PGroonga 来自 packages.groonga.org，需先装 groonga-apt-source。
FROM postgres:17-trixie

RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates lsb-release wget \
 && wget -q https://packages.groonga.org/debian/groonga-apt-source-latest-$(lsb_release --codename --short).deb \
 && apt-get install -y --no-install-recommends ./groonga-apt-source-latest-$(lsb_release --codename --short).deb \
 && rm -f ./groonga-apt-source-latest-*.deb \
 && apt-get update \
 && apt-get install -y --no-install-recommends \
        postgresql-17-pgvector \
        postgresql-17-pgdg-pgroonga \
 && rm -rf /var/lib/apt/lists/*

# 扩展在 initdb 阶段由官方镜像的 entrypoint 自动执行 init/*.sql
COPY init/ /docker-entrypoint-initdb.d/

# 固定时间戳到镜像标签，便于排查
ARG BUILD_DATE=unknown
LABEL org.opencontainers.image.title="ragagent-postgres" \
      org.opencontainers.image.description="PostgreSQL 17 with pgvector and PGroonga" \
      org.opencontainers.image.created="${BUILD_DATE}"
```

`init/00-extensions.sql`：

```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgroonga;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
```

- [ ] **Step 2: 写构建脚本**

`deploy/db/build.sh` 要点：

- `set -euo pipefail`，`cd "$(dirname "$0")"` 后按脚本所在目录工作
- 变量 `TAG="${TAG:-17-pgvector-pgroonga}"`、`REPO="${REPO:-ragagent/postgres}"`、`PLATFORM="${PLATFORM:-linux/amd64}"`
- `docker build --platform "$PLATFORM" --build-arg BUILD_DATE="$(date -u +%Y-%m-%dT%H:%M:%SZ)" ${HTTP_PROXY:+--build-arg HTTP_PROXY="$HTTP_PROXY"} ${HTTPS_PROXY:+--build-arg HTTPS_PROXY="$HTTPS_PROXY"} -t "$REPO:$TAG" .`，环境里设了 `HTTP_PROXY`、`HTTPS_PROXY` 就透传给构建（访问 `packages.groonga.org` 需要）
- 参数 `--push` 时额外 `docker push "$REPO:$TAG"`
- `--platform linux/amd64` 是必要的：本机可能是 arm64，CI 与生产是 x86

- [ ] **Step 3: 写冒烟脚本并确认失败（镜像还不存在）**

`deploy/db/smoke.sh <image>`：起容器 → 等 `pg_isready` → `psql` 断言

```sql
SELECT extname FROM pg_extension WHERE extname IN ('vector','pgroonga','pg_trgm','uuid-ossp');
```

四行都返回；再建一张表、建 PGroonga 索引、用 `&@` 查询命中；再建一个 `vector(3)` 列并做 `<=>` 排序。

Run: `bash deploy/db/smoke.sh ragagent/postgres:17-pgvector-pgroonga`
Expected: FAIL，`Unable to find image`。

- [ ] **Step 4: 构建并跑冒烟**

Run:
```bash
bash deploy/db/build.sh
bash deploy/db/smoke.sh ragagent/postgres:17-pgvector-pgroonga
```
Expected: 镜像构建成功；冒烟脚本输出 `SMOKE PASS` 并退出 0。

**构建这一步同时验证了两件 spec 假设**：`packages.groonga.org` 上确实有适用于 trixie 与 PG 17 的 `postgresql-17-pgdg-pgroonga`；PGroonga 在 PG 17 上能建索引。任一不成立，在这里就暴露，而不是等检索域迁入。

若 trixie 下包解析失败（例如 Groonga 源暂未提供 trixie 的 PG 17 包），回退方案是改为源码编译 PGroonga：新增 `deploy/db/Dockerfile.source`，在 `postgres:17-trixie` 上装 `groonga` 开发包与编译工具链，再 `make && make install` PGroonga 源码。命令入口 `docker build -f deploy/db/Dockerfile.source -t ragagent/postgres:17-pgvector-pgroonga deploy/db`，具体步骤按 PGroonga 官方「从源码安装」文档，这里不展开。用了回退方案要在 `deviations.md` 登记，并在 `README.md` 写明原因。

- [ ] **Step 5: 切换 Testcontainers 并接入新栈开发库**

- `PostgresContainerTest.PG_IMAGE` 改为 `docker.io/ragagent/postgres:17-pgvector-pgroonga`
- `deploy/db/docker-compose.yml`：服务 `rebuild-postgres`，镜像同上，容器名 `ragagent-db-dev`，`ports: "25432:5432"`，`POSTGRES_USER=postgres`、`POSTGRES_PASSWORD=postgres123!@#`、`POSTGRES_DB=ragagent_rebuild`，数据卷 `ragagent-db-data`，挂载 `./init` 到 `/docker-entrypoint-initdb.d`（含 `10-roles.sql`，所以应用角色自动建好）
- `backend/app/src/main/resources/application-dev.yml`：`DB_URL` 默认改为 `jdbc:postgresql://localhost:25432/ragagent_rebuild`
- `backend/app/scripts/dev-db.sh`：改为 `docker compose -f deploy/db/docker-compose.yml up -d --wait`，不再对旧开发栈容器操作
- `backend/app/README.md`：本地起法里的数据库一步改为上面这条命令

根目录 `docker-compose.yml` 与 CI 的 `backend` job 保持不动，见本任务开头。

Run: `grep -rn "pgvector/pgvector" --include=*.java --include=*.yml . | grep -v target`
Expected: 无输出，说明 Task 1 的占位镜像常量已被替换。

- [ ] **Step 6: 回归确认**

Run:
```bash
mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml verify
docker compose -f deploy/db/docker-compose.yml up -d --wait
docker exec ragagent-db-dev psql -U postgres -d ragagent_rebuild -c "SELECT extname FROM pg_extension"
```
Expected: framework 与 app 全部用例在新镜像上通过；`pg_extension` 里能看到 `pgroonga` 与 `vector`。验证后 `docker compose -f deploy/db/docker-compose.yml down` 停掉。

`pgroonga` 扩展默认加载到 `template1` 才有意义（新建库自动带）。若 CI 用 `POSTGRES_DB` 新建库，扩展会因 initdb 只对首个库执行而缺失，此时在 `00-extensions.sql` 之外再加 `init/01-template.sql`，在 `template1` 上 `CREATE EXTENSION`。Step 6 的 `pg_extension` 查询就是用来发现这个问题的。

- [ ] **Step 7: Commit**

```bash
git add deploy/db/Dockerfile deploy/db/build.sh deploy/db/README.md deploy/db/init deploy/db/smoke.sh deploy/db/docker-compose.yml
git add backend/framework/src/test/java/testfixture/support/PostgresContainerTest.java
git add backend/app/src/main/resources/application-dev.yml backend/app/scripts/dev-db.sh backend/app/README.md
git commit -m "feat(db): 自建 PostgreSQL 17 + pgvector + PGroonga 镜像与新栈开发库"
```

---

## 完成判据

全部任务完成后，以下命令全绿即视为本计划完成：

```bash
# 新骨架：含 framework 全部单元与集成用例、Checkstyle 尺寸硬门、Modulith 与 ArchUnit 规则、AppBootsTest
mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml verify

# 旧构建没被破坏（根 pom 不含 backend，两套构建互不感知）
mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -q -DskipTests compile

# 安全网在旧系统上可执行（旧系统在 ../ragagent-legacy 里起，stub 模型已就绪）
cd tests/e2e && npx playwright test
cd tests/regression && node --test compare.test.mjs \
  && node compare.mjs --baseline baseline.json --candidate baseline.json --min-expected 1.0
cd tests/api-smoke && API_BASE=http://localhost:8080 npx playwright test   # 1 passed，6 skipped

# 类型生成无漂移，传输层单测通过
cd frontend/packages/api-client && npm run check:api && npm run typecheck && npm test

# 自建数据库镜像可用
bash deploy/db/smoke.sh ragagent/postgres:17-pgvector-pgroonga
```

另有四项状态检查，全部成立：

```bash
git tag --list rebuild-freeze                 # 输出 rebuild-freeze
git worktree list                             # 含 ../ragagent-legacy
ls docs/migration                             # frozen-bugs.md、progress.md、deviations.md
ls docs/optimization/spikes                    # 含两份 spike 结论文档与本计划新增的两份
```

逐条对应 spec 第 7 节的验收项：

| spec §7 | 本计划覆盖方式 |
|---|---|
| 1 无 contract 模块、无 Ops/Support/Helper/Facade 后缀 | `ArchitectureRulesTest.noGoCompatSuffixes`，后缀清单扩展为 Ops、Support、Helper、Facade、Manager、Common，framework 基类以包白名单 `com.ragagent.framework..` 豁免 |
| 2 无 `@RequestBody String`、无 Go 兼容工具 | `ArchitectureRulesTest.noRawRequestBodyString` |
| 3 无手写 `tenant_id`、租户表开 RLS、跨租户被拒有测试 | `TenantIsolationTest` 含反向验证；`TenantWebTest` 与 `SaTokenAccountsTest` 覆盖租户缺失与租户头伪造 |
| 4 业务异常带错误码与 i18n key，messages 覆盖七语种 | Task 4 的八份文件与「每个语言文件定义全部错误码」测试，覆盖 framework 自身的 14 个码；各域错误码的文案完整性由各域计划按路线图 3.4 补测 |
| 5 前端类型全部由 OpenAPI 生成 | Task 12 的 `check:api` 与 CI job `api-drift` |
| 6 架构规则由 Java 测试表达 | `ArchitectureRulesTest`、`ModuleBoundaryTest` 与 `CodeQualityRulesTest`；尺寸规则由 Checkstyle 在 `validate` 阶段表达，路线图 3.11 |
| 7 检索回归不劣于基线、端到端冒烟全部通过 | 本计划做到「基线已录、冒烟骨架已建」，**这两条在域迁入完成后才能判** |
| 8 界面端到端在新系统上全部通过 | 本计划在旧系统上按页面清单录制并校准快照基线，**新系统判据要等 QA 链路迁完** |

数据库层面另有两条由 Task 18 覆盖：镜像含 PGroonga 与 pgvector（spec 5.9），以及 dev 与 CI 用同一镜像（避免本地能跑、CI 挂掉）。

**本计划明确不覆盖、也不假装覆盖的三项**：

1. §7.4 中各业务域错误码的七语种完整性。本计划只覆盖 framework 的 14 个码，业务域的码随各域迁入补测。
2. §7.7 的端到端冒烟全部通过。Task 15 交付的是可执行骨架，6 条用例仍是 `fixme`。
3. §7.8 的界面端到端在新系统上通过。Task 13 交付的是在旧系统上录制好的用例与快照基线，`PAGES.md` 里标为条件页与未定位的页面在对应域迁入时补齐。

前两项属于 spec 第 6 节第 4 步「按域迁入」的验收，第 3 项属于第 5 步。本计划做完时，这三项都还是红的，这是计划设计如此，不是遗漏。

两份 spike 文档存在且各自给出明确的「采用 / 不采用」结论。

---

### 附：Task 清单与顺序

Task 0、1、2、3、4、5、6、7、8、9、10、11、11b、11c、11d、12、13、14、15、16、17、18，共 22 个。Task 11b 依赖 Task 5 到 Task 8，Task 11c 依赖 Task 11，Task 11d 依赖 Task 5 与 Task 11，Task 12 依赖 Task 11b，Task 15 依赖 Task 11b，Task 13 与 Task 14 依赖 Task 0。Task 18 完成后回头把 Task 1 的 `PG_IMAGE` 常量换成自建镜像名。

