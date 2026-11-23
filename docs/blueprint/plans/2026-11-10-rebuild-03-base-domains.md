# RagAgent 重建：基础域（tenant、auth、system、storage、model、vectorstore、websearch、audit）迁入 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把旧 `auth`、`system`、`initialization`、`storage`、`model`、`vectorstore`、`websearch`、`audit` 八个包重写进 `backend/app`，并把散落在 auth、system、storage 的租户代码归并为独立 `tenant` 域。完成后控制台登录、租户、模型、存储、向量库、网页搜索、系统设置各页在新栈上复现旧界面，API 冒烟里的登录一步转绿。

**Architecture:** 八个基础域（含新建 tenant）各自一个包，按 `api/controller/service/repository/mapper/entity/bo/vo/assembler` 分层，域名与路线图 3.3 模块号一一对应。tenant 是唯一写 `tenants` 表的属主，其他域按 D25 走 `tenant.api`。auth 用 Sa-Token 三套 `StpLogic`，旧手写过滤器与 290 条 API-Key 路由策略翻译为 `SaRouter` 与 `@SaCheckPermission`。storage 后端收敛到 framework 的 `OssTemplate`，本地目录补 `LocalOssTemplate`。model、vectorstore、storage 在各自 `api` 声明 ReferenceProvider，实现方在计划 04 与 06。租户级客户端统一走计划 01 的 `TenantClientRegistry`，配置覆盖顺序由 system 实现。三个引擎模块只被调用，不反向依赖 app。

**Tech Stack:** JDK 21、Spring Boot 3.5.16、MyBatis-Plus 3.5.x、Flyway、Spring Modulith 1.4、ArchUnit、Sa-Token（三 `StpLogic` 加 Redis DAO）、Nimbus `oauth2-oidc-sdk` 11.39、AWS SDK v2 S3、Caffeine、Redisson、Jackson 2、MapStruct、Testcontainers PostgreSQL、MinIO 容器、Playwright。

**规格来源**: `docs/optimization/specs/2026-11-09-architecture-rebuild-design.md`。路线图 `docs/optimization/plans/2026-11-10-rebuild-00-roadmap.md`，本文是其中 03 号计划，第 3 节约定全部继承。

## Global Constraints

- JDK 21、Spring Boot 3.5.16。Java 缩进 tab、续行一个 tab、行宽 140、中文 Javadoc、`@author jiangguifan`。
- 业务错误码 10 位 `1` + `001` + 模块号 + 3 位序号。本计划模块号：tenant 002、auth 003、system 004、storage 005、model 006、vectorstore 007、websearch 008、audit 009。枚举放各域 `api` 包，命名 `<Domain>Code`，启动时 `CodeCache.register`。
- 文案 key `code_<错误码>`，放 `backend/app/src/main/resources/i18n/app-messages*.properties`，七语种加默认文件齐全，非中英文由机器翻译出初稿并标待校对。
- Flyway 脚本放 `backend/app/src/main/resources/db/migration/allenv/`，命名 `V{yyyyMMdd}_{两位序号}__{DDL|DML}_rebuild_{域名}_{描述}_jiangguifan.sql`，版本与描述之间是双下划线。
- 主键 bigint 雪花 ID，对外序列化为字符串。租户表 `tenant_id bigint NOT NULL` 加行级安全，策略用计划 01 Task 5 的 fail-closed 模板。软删 `deleted_at timestamptz NOT NULL DEFAULT '1970-01-01 00:00:00+00'`，唯一索引含 `deleted_at`。
- 路径三组：`/api/admin/v1`、`/api/v1`、`/api/open/v1`。后台功能只挂 admin。请求体 VO 加 `@Valid`，响应 `RestResponse`。
- 租户 ID 只来自登录会话。请求头 `X-Tenant-Id` 在 `trust-header=false` 下忽略，租户切换只走 tenant 域定义的端点。
- 代码质量按路线图 3.11：方法 40 行、类 400 行、参数 5 个、圈复杂度 10、嵌套 3 层、构造器依赖 7 个，Checkstyle 拦截。类名不带 `Ops`、`Support`、`Helper`、`Facade`、`Manager`、`Common`。单实现不建接口。构造器注入用 `@RequiredArgsConstructor`。
- 事务内不做外部调用。模型探测、存储连通性、网页搜索、OIDC 换令牌都算外部调用，放在事务外或 `@Transactional(propagation = NOT_SUPPORTED)` 的探测方法里。
- 旧代码只读，旧系统副本 `../ragagent-legacy`。正文 `MVN` 代表 `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl app -am`，`-pl` 必带 `-am`。
- 路径缩写：`M` = `backend/app/src/main/java/com/ragagent`，`T` = `backend/app/src/test/java/com/ragagent`，`R` = `backend/app/src/main/resources`，`DB` = `R/db/migration/allenv`。`<D>` 为写脚本当天 `yyyyMMdd`，序号取 `ls DB | grep <D>` 的下一个。
- `git add` 只写显式路径。每个任务一次提交，提交后在 `docs/migration/progress.md` 追加一行，偏离写 `docs/migration/deviations.md`。

## Review Focus

以下五类最容易出错，每条在对应任务的测试里有断言。

1. 伪造租户头与未绑定会话的租户。期望：`X-Tenant-Id` 被忽略，租户只取 Token-Session；未绑定会话返回 `FrameworkCode.TENANT_CONTEXT_MISSING` 的 400，不出现 PostgreSQL `invalid input syntax`。归属 Task 3、Task 9、Task 10。
2. API Key 走 open 账号后越权访问 admin 面。期望：`X-API-Key` 只解出 open 身份，同一 token 访问 `/api/admin/v1/**` 返回 4402；能力不足返回 6041。归属 Task 9、Task 10。
3. 配置覆盖顺序错层。期望：租户设置有值时覆盖 `system_settings`，`system_settings` 有值时覆盖 yml 默认值，三层都空时取代码默认值，任一层为空串视为未设置。归属 Task 12。
4. 删除仍被引用的模型、向量库、存储后端。期望：返回 `MODEL_IN_USE` 等错误码并在 details 列出引用方，不返回 500。归属 Task 19、Task 21、Task 17。
5. 存储后端凭据与租户配置密钥泄漏。期望：入库列值不等于明文，任何响应只返回 `configuredFields`，日志无明文。归属 Task 16、Task 17、Task 12。

---

## 前置、交付、对外承诺

### 前置

| 来源 | 需要的产物 |
|---|---|
| 计划 01 | framework 的 `RestResponse`、`BusinessRuntimeException`、`CodeCache`、`I18nUtil`、`@IgnoreTenant`、`@Encrypted`、`TenantAwareJdbcTemplate`、`TenantClientRegistry`、`TenantConfigSource`、`OssTemplate`、`OssClients`、`S3OssTemplate`、`SaRouteRules`、`AdminStpLogic`、`VisitorStpLogic`、`OpenStpLogic`、`SaTokenAccountProvider`、`SaTokenTenantResolver`、`DistributedLock`、`PostgresContainerTest`、`OpenApiExportTest`；`@ragagent/api-client`；`tests/e2e/PAGES.md` 与基础域用例；`tests/api-smoke/tests/auth.spec.ts` |
| 计划 01 Task 17 | `docs/optimization/spikes/2026-11-11-nimbus-rfc9728.md`。文件不存在时先执行该 Task，结论未出前 Task 9 不开工 |
| 计划 02 | retrieval-engine 的 `api` 与 `spi`，本计划实现其中两个：`retrieval.spi.VectorStoreCatalog`（`Optional<VectorStoreView> byId(long tenantId, String storeId)`、`boolean isOwnedBy(String storeId, long tenantId)`，`VectorStoreView` 来自 `retrieval.api`）、`retrieval.spi.TenantRetrievalSettings`（`List<RetrievalDriverRef> effectiveEngines(long tenantId)`、`RetrievalTuning tuning(long tenantId)`）；model-runtime 的 `LlmChatClient`、`EmbeddingPort`、`ModelRuntime`，以及 `modelruntime.spi` 三个接口（`ModelCatalogReader`、`ModelFactsReader`、`ModelCredentialReader`，签名见计划 02 的「对外承诺」）；计划 02 把 `platform.security.SsrfGuard` 源码迁到 `framework.security`，本计划 Task 13 把它注册成 Spring Bean |
| 计划 04 | 本计划只声明 Provider，不依赖其产物。实现方在计划 04 |
| 计划 05 | 对计划 01 的 `DistributedLockKeyResolver` 加 `@ConditionalOnMissingBean` 的需求，见 1.15 |

### 交付

1. 八个基础域（含新建 tenant）的表、`api` 包、Service、Controller、前端 API 层与错误码七语文案。
2. `auth.api` 的当前主体、用户查询、权限点命名，供计划 06、07、08 消费。
3. `tenant.api` 的租户读取、配置、配额与默认存储后端读写，供 storage、knowledge、memory 消费。
4. `system.api` 的系统设置读取与配置覆盖，`storage.api` 的存储与文件读写，`model.api` 的模型查询与运行时配置。
5. `docs/migration/golden-sample.md`：tenant 域作为黄金样板，登记 Controller、Service、Repository、Assembler、测试路径。
6. `docs/migration/permission-points.md`：权限点逻辑名与旧角色、旧 API-Key 能力的对照，供计划 05、07、08 引用；缺失时按它们的“新增需求”登记偏离。

### 对外承诺

包名与签名如下。租户从当前上下文取，不作为参数传入的接口在方法注释里写明；跨租户系统通道见 1.9。

**tenant.api**（`com.ragagent.tenant.api`）：

| 接口 | 方法 |
|---|---|
| `TenantQueryApi` | `Optional<TenantBo> find(long tenantId)`；`boolean exists(long tenantId)`；`List<Long> listTenantIds()`（内部 `@IgnoreTenant`） |
| `TenantStorageApi` | `TenantStorageBo storageView(long tenantId)`；`void bindDefaultBackend(long tenantId, Long storageBackendId)`；`TenantQuotaBo quota(long tenantId)`；`void adjustUsed(long tenantId, long delta)`；`int applyDefaultQuota(long quotaBytes)`（`@IgnoreTenant`） |
| `TenantConfigApi` | `RetrievalTenantConfigBo retrieval(long tenantId)`；`WebSearchTenantConfigBo webSearch(long tenantId)`；`ParserTenantConfigBo parser(long tenantId)`；`MemoryTenantConfigBo memory(long tenantId)`；`ChatHistoryTenantConfigBo chatHistory(long tenantId)`；`WikiTenantConfigBo wiki(long tenantId)`；`String configVersion(long tenantId)`；`String sectionVersion(long tenantId, String section)` |
| `TenantSwitchPolicy` | `TenantSwitchDecisionBo decide(long tenantId, Principal principal)` |

`record TenantBo(long id, String name, String description, String status, String business, OffsetDateTime createdAt)`；`record TenantStorageBo(long tenantId, Long defaultStorageBackendId)`；`record TenantQuotaBo(long tenantId, long quota, long used)`；`record WikiTenantConfigBo(boolean enabled, String defaultLanguage, int ingestConcurrency, int maxInflight, int batchSize, int mapParallel, int reduceParallel, Map<String,String> overrides)`，section 缺失时全字段取默认值（`enabled` true、`defaultLanguage` `zh-CN`、并发取旧常量）。

**auth.api**（`com.ragagent.auth.api`）：

| 接口 | 方法 |
|---|---|
| `CurrentPrincipal` | `Principal get()`（未登录抛 `CommCode.NOLOGIN`）；`Optional<Principal> current()` |
| `UserQueryApi` | `Optional<UserBo> find(long userId)`；`Map<Long, String> displayNames(Collection<Long> userIds)`；`Optional<UserBo> findByUsername(String username)` |
| `RoleApi` | `String roleOf(long userId, long tenantId)`；`boolean hasRole(long userId, long tenantId, String role)` |
| `UserPreferenceApi` | `Optional<Long> lastActiveTenantId(long userId)`；`void saveLastActiveTenantId(long userId, Long tenantId)` |

`record Principal(PrincipalType type, Long id, Long tenantId, String tenantRole, boolean systemAdmin)`，`PrincipalType` 取 `USER`、`VISITOR`、`OPEN`。`record UserBo(long id, String username, String email, String avatar, boolean active, boolean systemAdmin, Long homeTenantId)`。

**system.api**：`SystemSettingApi`（`int intValue(SystemSettingKey key)`、`boolean boolValue(SystemSettingKey key)`、`String stringValue(SystemSettingKey key)`、`List<String> listValue(SystemSettingKey key)`）；`DeploymentApi`（`DeploymentCapabilitiesBo capabilities()`，`boolean isProduction()`）。`SystemSettingKey` 枚举收口全部 key 与默认值。

**storage.api**：

| 接口 | 方法 |
|---|---|
| `StorageBackendQueryApi` | `Optional<StorageBackendBo> find(long backendId)`；`List<StorageBackendBo> list()`；`Optional<StorageBackendBo> defaultBackend()` |
| `FileStorageApi` | `String store(FileWriteBo write)`；`Optional<byte[]> read(String resourceUrl)`；`String signUrl(String resourceUrl, Duration ttl)`；`void delete(String resourceUrl)`；`String storeResource(InputStream in, String fileName, String mediaType)` |
| `StorageReferenceProvider`（声明） | `List<ResourceReferenceBo> references(long storageBackendId)` |
| `ResourceReferenceProvider`（声明） | `Set<Long> knowledgeIdsOf(String resourceUrl)` |

`record ResourceReferenceBo(String resourceType, long resourceId, String name, String ownerDomain)`。**三个引用 Provider 共用这一个 record，定义在 `storage.api`。** model 与 vectorstore 只依赖 `storage.api` 的这一个类型，不依赖 storage 的其他类；这条同级依赖在决定三里写明理由。

**model.api**：`ModelQueryApi`（`Optional<ModelBo> find(long modelId)`；`List<ModelBo> listVisible()`；`Optional<ModelRuntimeSpecBo> runtimeSpec(long modelId)`；`Optional<Long> defaultModelId(ModelType type)`）；`ModelReferenceProvider`（声明，`List<storage.api.ResourceReferenceBo> references(long modelId)`）。model 的 `api` 包只放自己的类型，`ResourceReferenceBo` 从 `storage.api` 引用，不重复定义。

**vectorstore.api**：`VectorStoreQueryApi`（`Optional<VectorStoreBo> find(long storeId)`；`List<VectorStoreBo> list()`；`VectorStoreRuntimeConfigBo runtimeConfig(long storeId)`）；`VectorStoreReferenceProvider`（声明，`List<storage.api.ResourceReferenceBo> references(long storeId)`）。`runtimeConfig` 由 retrieval-engine 的 spi 适配类调用。

**websearch.api**：`WebSearchApi`（`List<WebSearchResultBo> search(WebSearchQueryBo query)`；`Optional<WebSearchProviderBo> defaultProvider()`）。

**audit.api**：`AuditApi`（`void record(AuditEntryBo entry)`；`void recordAccessDenied(AuditDeniedBo denied)`）。

**对路线图第 6 节的增补**：

| 声明方 | 接口 | 用途 | 实现方 |
|---|---|---|---|
| tenant（03） | `TenantStorageApi` | 默认存储后端读写、配额记账 | storage、knowledge |
| auth（03） | `CurrentPrincipal`、`UserQueryApi`、`RoleApi` | 当前身份与用户名回填 | 全部域 |
| system（03） | `SystemSettingApi`、`DeploymentApi` | 部署级开关 | 全部域 |
| audit（03） | `AuditApi` | 审计写入 | knowledge、wiki、datasource、agent |
| storage（03） | `StorageReferenceProvider`、`ResourceReferenceProvider`、`FileStorageApi` | 删除前统计、资源归属、附件落盘 | knowledge（04） |
| model（03） | `ModelReferenceProvider` | 删除模型前列出引用方 | knowledge（04）、agent（06） |
| vectorstore（03） | `VectorStoreReferenceProvider` | 删除向量库前统计绑定数 | knowledge（04） |
| retrieval-engine（02） | `spi.VectorStoreCatalog` | 引擎读租户向量库配置 | vectorstore（03）Task 21 |
| retrieval-engine（02） | `spi.TenantRetrievalSettings` | 租户有效引擎与检索调参 | tenant（03）Task 3 |
| model-runtime（02） | `spi.ModelCatalogReader`、`ModelFactsReader`、`ModelCredentialReader` | 引擎读模型行、事实与凭据 | model（03）Task 19 |



**后续计划对本计划的消费需求核对**（逐条来自它们的「前置」与「新增需求」）：

| 消费方 | 它要的东西 | 本计划提供 | 状态 |
|---|---|---|---|
| 计划 06 | `CurrentPrincipal.get()` 返回 `Principal(type, id)` | `auth.api.CurrentPrincipal` + `record Principal(PrincipalType, Long id, Long tenantId, String tenantRole, boolean systemAdmin)` | 满足。字段多出 `tenantId`、`tenantRole`、`systemAdmin`，计划 06 按需取 |
| 计划 06 | `UserQueryApi` 批量取用户名 | `UserQueryApi.displayNames(Collection<Long>)` | 满足 |
| 计划 06 | `tenant.api` 的 `memory-config` 一项 | `TenantConfigApi.memory(long)` | 满足 |
| 计划 06 | Spring Bean 形式的 `SsrfGuard` | 本计划 Task 13 创建 Bean（system 域，`system.service.SsrfGuardConfig`），依赖计划 02 的 `platform.security.SsrfGuard` 源码随引擎迁入 framework | 本计划是唯一创建方 |
| 计划 07 | `storage.api` 按 `resource://` 句柄读内容 | `FileStorageApi.read(String resourceUrl)` | 满足 |
| 计划 07 | `storage.api` 的 `readResource`/`storeResource` 两方法 | `FileStorageApi.read` 与 `storeResource` | 满足。计划 07 写的 `readResource` 对应 `read`，`storeResource` 同名；方法名差异在计划 07 执行时按偏离登记，本计划不改名 |
| 计划 07 | `system.api` 部署形态 | `DeploymentApi.isProduction()` | 满足 |
| 计划 07 | 权限点 `channel:read`、`channel:write`、`embed-channel:manage` | 本计划只给 `PermissionCatalog` 与命名规则，三个串由计划 07 在自身 `api` 追加并登记 | 部分满足。本计划交付 `PermissionCatalog` 与 `docs/migration/permission-points.md` 的命名规则，三个渠道串由计划 07 在自身 `api` 追加枚举值；这是一处跨计划依赖，本计划不改 `PermissionCatalog` 的现有取值 |
| 计划 08 | 当前主体与当前用户、`model.api`、`audit.api`、`storage.api` 附件与签名 | 同上 | 满足 |
| 计划 09 | open 账号体系令牌头、`SaRouteRules`、主体接口 | `OpenKeyAuthenticationFilter`、`AuthRouteRules`、`CurrentPrincipal` | 满足 |

**对前置计划的新增需求**

| 声明方 | 需要的接口或机制 | 用途 | 缺失时的处置 |
|---|---|---|---|
| 计划 01 | `DistributedLockKeyResolver` 的 `@Bean` 加 `@ConditionalOnMissingBean` | 计划 05 的 wiki slug 锁要声明 `@Primary` 解析器。本计划不使用该解析器，需求转交计划 01 | 计划 01 未改时，计划 05 Task 6 前按其“新增需求”登记偏离，本计划不受影响 |
| 计划 01 | `SaTokenConfig` 允许追加 `StpInterface` Bean，且以 `@ConditionalOnMissingBean` 注册默认实现 | auth 域要提供真实权限来源 | 已有默认实现，覆盖即可，无需改动 |
| 计划 02 | 五个 spi 实现签名已由计划 02 定死，本计划按原文实现：`ModelCatalogReader.findOrEmpty/requireVisible/requireRow`、`ModelFactsReader.find`、`ModelCredentialReader.apiKey/appSecret`、`VectorStoreCatalog.byId/isOwnedBy`、`TenantRetrievalSettings.effectiveEngines/tuning` | 五个 spi 的实现方在本计划 | 计划 02 落地后签名若变，按其新签名改本计划 Task 3、Task 19、Task 21 并在 `deviations.md` 登记 |

## 本计划新做的设计决定（待用户核对）

只列 spec 与路线图没定、由本计划新定的决定，按重要度排序。

### 一、新表结构与聚合根划分

1. **`users` 变全局表，不再带 `tenant_id`。** 决定：`users` 只存身份与凭据，`id bigint`、`username`、`email`、`password_hash`、`avatar`、`is_active`、`is_system_admin`、`preferences jsonb`，`(username, deleted_at)` 与 `(email, deleted_at)` 各一条唯一索引，不进 `ignore-tables` 之外的处理，不开行级安全。租户归属只由 `tenant_members` 表达。理由：旧表 `tenant_id` 可空且与成员关系重复，认证期按 email 定位用户本就跨租户。否决：保留 `tenant_id` 并开行级安全，认证查询必须在 `@IgnoreTenant` 下跑，等于每张登录路径都开例外。
2. **`tenants` 表把 12 个 jsonb 配置列按归属域拆出去，本域只留自有列。** 决定：新 `tenants` 只保留 `id`、`name`、`description`、`status`、`business`、`storage_quota`、`storage_used`、`created_at`、`updated_at`、`deleted_at`。12 个 jsonb 的去向见 1.13。实测冻结点 `V1__baseline.sql` 的 `CREATE TABLE tenants` 含 **12** 个 jsonb 列，spec §3 约束第 4 条与盘点写“13 个”，差 1 是计数口径问题，以 DDL 为准。理由：jsonb 列各有属主域，留在 tenant 表等于让 tenant 域成为所有域的配置仓库。否决：保留 12 列，用 `TenantConfigApi` 逐列透传，属主域仍要理解别人的列。
3. **tenant 域四个聚合：`TenantRepository`（`tenants`）、`TenantMemberRepository`（`tenant_members`）、`TenantInvitationRepository`（`tenant_invitations`）、`TenantConfigRepository`（`tenant_settings`）。** 理由：成员与邀请各有独立生命周期与唯一约束，配置段按 section 独立更新，合并成一个聚合会让成员增删拉上邀请表与配置表。否决：单 `TenantRepository` 一次管四表。
4. **`tenants` 与 `users` 进 `ignore-tables`，不进 `@IgnoreTenant` 白名单。** 决定：两张表没有 `tenant_id`，租户插件按表名跳过；跨租户遍历只能通过 `TenantQueryApi.listTenantIds()` 与登录解析，两处都标 `@IgnoreTenant` 并写原因注释。否决：给两表加 `tenant_id=0` 哨兵，策略模板每张表多一条分支。
5. **`auth_tokens` 表删除，令牌状态交给 Sa-Token 的 Redis DAO。** 决定：登录、刷新、注销、租户切换全部走 Sa-Token 会话，撤销即 `logout`，跨实例共享靠 `sa-token-redis-jackson`。理由：旧表与无状态 JWT 并用，撤销语义靠 `is_revoked` 位，两套事实。否决：保留表加自定义校验，等于重写一遍会话管理。
6. **`tenant_api_keys` 拆成 `open_api_keys` 与 `platform_api_keys` 两张表。** 决定：租户 Key 带 `tenant_id bigint NOT NULL` 开行级安全；平台 Key 是跨租户的系统凭据，单独成表、无 `tenant_id`、进 `ignore-tables`。理由：旧表用 `tenant_id IS NULL` 表达平台 Key，与 `NOT NULL` 和 fail-closed 策略冲突，且平台 Key 的授权面是全部租户。否决：单表加 `scope_type` 并在策略里放行 NULL，行级安全形同虚设。

### 二、新接口路径与 VO 形状

1. **租户 KV 换成具名配置子资源。** 决定：`/api/admin/v1/tenants/{id}/config/{section}`，`section` 取 `retrieval`、`parser`、`websearch`、`memory`、`chat-history`、`api-principal` 六个，替代旧 `GET/PUT /tenants/kv/{key}`。旧 KV 另有 2 个 key 不落本路径：`prompt-templates` 是仓库内静态 YAML 目录，改为 `GET /api/admin/v1/prompt-templates`（system 域，按 `Accept-Language` 渲染）；`storage-engine-config` 只读且写入口已 405，新实现删除，前端 `getStorageEngineConfig` 改为读 `GET /storage-backends`。前端函数 `getParserEngineConfig`、`getPromptTemplates`、`updateParserEngineConfig` 保持导出名，在 API 层内部映射。理由：旧 key 是 8 个魔法字符串，越权判定要解析 key，且其中两类不是租户配置。否决：保留 kv 路径，继续靠 key 白名单。
2. **租户切换独立成会话端点。** 决定：`POST /api/admin/v1/auth/session/tenant`，请求体 `{tenantId}`，响应 `{token, tenantId, tenantName, role}`；旧 token 立即失效。替代旧 `POST /auth/switch-tenant` 与请求头 `X-Tenant-ID`。前端 `tenantSwitch.ts` 改为调该端点后整页刷新。理由：路线图 3.7 第 1 条要求租户切换只在 tenant 域的切换策略里允许，头切换没有可审计的单点。
3. **类型目录统一命名。** `GET /api/admin/v1/storage-backends/provider-types`、`/vector-stores/engine-types`、`/web-search-providers/provider-types`，替代旧 `types`。理由：`types` 在三个资源下同义不同表，OpenAPI 生成后函数名要靠目录区分。
4. **平台运维面收敛到 `/api/admin/v1/system/**`。** 分段：`/system/settings`、`/system/runtime-queues`、`/system/audit-logs`、`/system/platform-api-keys`、`/system/users`、`/system/tenants/storage-quota-defaults`。旧 `/system/admin/*` 前缀删除。理由：`admin` 段与路径前缀 `admin` 重复。
5. **模型调试合并成一个端点。** 决定：`POST /api/admin/v1/models/{id}/debug`，请求体带 `kind`（`CHAT`、`EMBEDDING`、`RERANK`、`VLM`、`ASR`）与 `input`，响应按 kind 给不同 VO。旧实现是同一控制器里的五个分支，前端 `debugModel` 一个函数按 type 分支。否决：五个端点，OpenAPI 生成五个同义函数。
6. **`engine-types` 只返回三种引擎。** 决定：`pgvector`、`elasticsearch`、`milvus`。旧 `/vector-stores/types` 列 7 种（elasticsearch、qdrant、milvus、tencent_vectordb、weaviate、doris、opensearch）。新列表只留 elasticsearch 与 milvus，加默认引擎 pgvector（旧实现里 pgvector 只在 env store 路径可达，不在 types 列表）。其余六种（sqlite、opensearch、doris、qdrant、weaviate、tencent_vectordb）旧代码迁入但停用，不在 `VectorStoreEngineDescriptor` 里登记，不进 `engine-types` 下拉，不测试。前端下拉因此减少，交互不变。

### 三、Provider 与 spi 签名

1. **三个引用 Provider 的声明签名。** `ModelReferenceProvider`、`VectorStoreReferenceProvider`、`StorageReferenceProvider` 各一个方法：`List<ResourceReferenceBo> references(long resourceId)`，类型定义在 `storage.api`（见第 5 条）。调用方在删除前聚合所有实现的结果，非空即拒绝并带上 details。理由：旧实现直接 join `knowledge_bases`，基础域不能依赖业务域（spec 5.11 表格）。否决：Provider 返回 `boolean`，错误详情拿不到引用方。
2. **租户级客户端的配置版本取「属主表行 `updated_at` 的纪元毫秒」。** 决定：`TenantConfigSource.version(tenantId)` 对本域配置行做一次轻量查询，返回 `updated_at.toInstant().toEpochMilli()` 的字符串；配置跨多行时取最大值。理由：计划 01 的注册表每请求调一次 version，查询必须走索引且不加载 jsonb。否决：加版本号列，需要每条写路径维护。
3. **API Key 主体解析用窄接口加登记表。** 决定：`interface OpenKeyResolver { Optional<Principal> resolve(HttpServletRequest request); }`，三个实现 `HeaderApiKeyResolver`（`X-API-Key`）、`SignedTokenApiKeyResolver`（`X-API-Signature` 族）、`DirectHeaderApiKeyResolver`（`X-External-User-Id`）；`OpenKeyAuthenticator` 注入 `Map<OpenKeySource, OpenKeyResolver>` 按 `api_principal_config.mode` 选。理由：旧 `APIKeyAuthChannel` 371 行里三种模式混在一个方法，新增签名算法只加一个类。否决：继续 if 链，第四次改动必超 40 行。
4. **存储后端用窄接口加登记表。** 决定：`interface StorageBackendAdapter { StorageProvider provider(); OssTemplate create(StorageBackendConfig config); void probe(OssTemplate template); }`，实现 `S3CompatibleBackendAdapter`（覆盖 s3、minio、oss、cos、tos、obs、ks3 七个 key）与 `LocalDirectoryBackendAdapter`。理由：旧 9 个 provider key 里 7 个是 S3 协议，逐 key 建类是纯转发（反目标第 3 条）。否决：一个 key 一个实现，7 份重复。
5. **三个引用 Provider 共用一个 `ResourceReferenceBo`，定义在 `storage.api`。** 决定：model 与 vectorstore 各声明自己的 Provider 接口，但返回值类型统一用 `storage.api.ResourceReferenceBo`。理由：引用结果的形状一样，三份重复 record 会让调用方按来源写三套转换。否决：把 record 提到 framework（framework 不承载业务语义）；或三个域各定义一份（调用方要写三次映射）。
6. **权限串统一 `domain:resource:action`。** 决定：`PermissionCatalog` 枚举收口全部权限串，`RolePermissionProvider implements StpInterface` 按租户角色派生：`VIEWER` 给全部 `:read`，`CONTRIBUTOR` 加内容写，`ADMIN` 加管理写，`OWNER` 全量；`systemAdmin` 额外给 `platform:*`。open Key 的 `fullAccess` 映射为全部租户权限，scoped Key 只给能力对应的串。理由：旧系统有角色与 API-Key 能力两套独立叠加，迁移后要为 290 条路由给出统一注解。否决：两套并存，控制器写两条判定。

### 四、与旧行为不一致的地方

1. **令牌形态变化，前端只改 API 层。** 旧 `Authorization: Bearer <JWT>`，新为同头带 Sa-Token token，有效期与续期规则由 Sa-Token 配置决定。登录响应仍返回 `token` 字段，前端组件不改。
2. **API Key 仍支持 `X-API-Key` 头。** Sa-Token 只认 `Authorization`，新增 `OpenKeyAuthenticationFilter` 在 `SaInterceptor` 之前把 `X-API-Key` 解析成 open 会话。这样现有集成方与 mcp-server 不改调用方式。若配置了 `api_principal_config.mode` 为 `signed_token` 或 `direct_header`，对应头同样经该过滤器。
3. **错误提示按请求语言渲染。** 旧 650 处硬编码英文，新版按 `Accept-Language` 出七语文案。措辞不同，位置与交互不变。
4. **请求头不再参与租户选择。** 旧 `X-Tenant-ID` 可在请求期切租户，新实现只认会话，切换走独立端点并轮换 token。
5. **向量库引擎选项从 7 减到 3。** 其余六种旧引擎代码迁入但停用，不在 `VectorStoreEngineDescriptor` 里登记，不进 `engine-types` 下拉。界面下拉变短，交互不变。
6. **`system_settings` 与租户配置存在执行期叠加。** 旧代码两链互不读取，本计划按 spec 5.3 实现三层合并，结论与证据见 1.13。
7. **平台 API Key 与租户 API Key 分表后，平台 Key 不再能通过 `X-Tenant-ID` 选目标空间。** 平台 Key 调用租户接口时必须在请求体或路径里给出 `tenantId`，由 `TenantScopeGuard` 校验来源是平台主体。

# 1. 盘点（执行方核对，不重新盘点）

数据取自冻结点工作区。执行方在 Task 1 用 `find` 与 `wc -l` 抽查下列数字，有差异先改本节。

## 1.1 规模与目标包

| 旧包 | 文件 | 行数 | 新目标包 | 说明 |
|---|---:|---:|---|---|
| `auth` | 122 | 13,177 | `tenant` + `auth` | 拆分：租户、成员、邀请、切换策略归 tenant；用户、会话、OIDC、API Key 归 auth |
| `storage` | 62 | 8,143 | `storage` | 文件服务与资源目录 |
| `websearch` | 52 | 5,556 | `websearch` | 抓取与 13 个 provider（静态目录 13 项，`WebSearchProvider` 实现 13 个） |
| `model` | 38 | 3,080 | `model` | 模型行、凭据、Ollama、内置模型对账 |
| `system` | 18 | 2,100 | `system` | 设置、版本信息、平台用户与运维面 |
| `audit` | 20 | 1,445 | `audit` | 写入、查询、保留期清扫 |
| `initialization` | 11 | 1,583 | `system`（连通性探测）+ `model`（Ollama） | 旧包解散，见 1.11 |
| `vectorstore` | 20 | 1,555 | `vectorstore` | 3 种引擎 |
| 合计 | 343 | 36,639 | 8 个域 | |

目标包规模预估：`tenant` 1,300；`auth` 3,200；`system` 2,400；`storage` 2,600；`model` 2,200；`vectorstore` 900；`websearch` 2,400；`audit` 900。前端 API 层改动 11 个文件。

## 1.2 超过 400 行的旧类与拆分归宿

本批 17 个类超过 400 行，只有 3 个在新实现里对应单类，其余按变化原因拆分：

| 旧类 | 行 | 新归宿 |
|---|---:|---|
| `APIKeyRoutePolicies` | 703 | 不迁类。290 条策略按 1.14 翻译为 `SaRouter` 与 `@SaCheckPermission`；能力判定收口到 `PermissionCatalog` |
| `AuthController` | 643 | `SessionController`、`ProfileController`、`OidcController`、`RegistrationController`、`OnboardingController` 五个控制器 |
| `websearch.fetch.Fetcher` | 587 | `HtmlFetchService`（抓取与重定向）、`BrowserRenderService`（chromium 兜底）、`ContentExtractor`（正文抽取） |
| `ModelDebugController` | 580 | `ModelDebugController`（统一端点）+ `ModelDebugRequestBinder`（参数解析）+ `ModelDebugRunner`（按 kind 分派） |
| `SystemSettingService` | 566 | `SystemSettingService`（三层解析与副作用）、`SettingValueCodec`（jsonb 编解码与校验）、`SettingSideEffectDispatcher`（消费方推送） |
| `ModelService` | 546 | `ModelUsecase`（增删改查）、`ModelCredentialUsecase`（凭据）、`BuiltinModelSync`（YAML 对账）、`ModelDeletionGuard`（引用检查） |
| `auth.service.TenantService` | 542 | `TenantUsecase`（CRUD 与配额）、`TenantConfigUsecase`（6 段配置）、`TenantCascadeDeletion`（级联软删） |
| `initialization.ModelConnectivityTestService` | 525 | `ModelProbeService`（5 种探测）+ `ProbeRequestBinder` + `ProbeMediaPreparer`（图片与音频） |
| `initialization.InitializationConfigService` | 519 | 配置读写归 `knowledge`（计划 04）。本计划只迁探测部分 |
| `auth.service.UserService` | 510 | `UserUsecase`、`UserCredentialService`（改密与密码策略）、`UserPreferenceService` |
| `SystemAdminController` | 506 | `PlatformUserController`、`PlatformSettingController`、`RuntimeQueueController`、`PlatformApiKeyController` |
| `StorageConnectivityService` | 495 | `StorageProbeService` + `StorageBackendAdapter.probe` |
| `TenantInvitationService` | 476 | `TenantInvitationUsecase`、`InvitationLinkService`、`InvitationAcceptance` |
| `StorageProviders` | 475 | `S3CompatibleBackendAdapter` + `LocalDirectoryBackendAdapter` + `StorageProviderTypes` |
| `FileProxyService` | 468 | `ResourceFileService`、`ResourceGrantService`、`FileProxyController` |

## 1.3 超过 100 行的旧方法与归宿

| 旧方法 | 行 | 处置 |
|---|---:|---|
| `InitializationConfigService.configResponse` | 158 | 不迁（归计划 04 的知识库配置读面） |
| `InitializationConfigService.updateConfig` | 144 | 不迁（归计划 04） |
| `TenantCreateOps.createTenant` | 142 | 拆 `TenantUsecase.create`、`OnboardingService.bootstrapOwner`、`TenantQuotaPolicy.assertCanOwn` |
| `APIKeyRoutePolicies.registerKnowledgeAndWikiRoutes` | 137 | 不迁（策略表删除） |
| `WebSearchProviderTypes.all` | 118 | 拆为每个 provider 一个 `WebSearchProviderDescriptor` Bean，`all()` 只做收集与排序 |
| `AuthFilter.doFilterInternal` | 112 | 不迁（过滤器链换成 Sa-Token 拦截器加两个窄过滤器，每个 ≤ 40 行） |
| `ModelDebugController.debugChat` | 103 | `ModelDebugRunner.chat`，参数解析移出 |
| `VectorStoreTypes.all` | 101 | 引擎只剩 3 种，改为 3 个 `VectorStoreEngineDescriptor` Bean |

## 1.4 旧接口到新接口

规则：旧 `/api/v1` 一律改 `/api/admin/v1`，下表只写非机械变化与资源归属变动。“机械”表示只换前缀。

**auth**

| 旧 | 新 | 说明 |
|---|---|---|
| `POST /auth/login`、`/auth/logout`、`/auth/refresh`、`GET /auth/me`、`PUT /auth/me/preferences`、`POST /auth/change-password` | 同路径换前缀 | 机械 |
| `GET /auth/validate` | 删除 | Sa-Token 会话自证，前端 `validateToken` 改为 `GET /auth/me` |
| `POST /auth/register`、`/auth/auto-setup`、`/auth/register-by-invite`、`POST /auth/invitations/lookup`、`GET /auth/config` | 同路径换前缀 | 机械 |
| `GET /auth/oidc/config`、`/auth/oidc/url`、`/auth/oidc/callback` | 同路径换前缀 | `oidc/start` 删除，state 由 `url` 生成 |
| `POST /auth/switch-tenant` | `POST /auth/session/tenant` | 见决定二第 2 条 |
| `GET/POST /tenants/{id}/api-keys`、`PUT/DELETE .../{keyId}` | `GET/POST /tenants/{id}/open-api-keys`、`PUT/DELETE .../{keyId}` | 资源改名，响应加 `configuredFields` |
| `GET/POST /system/admin/api-keys`、`DELETE .../{keyId}` | `GET/POST /system/platform-api-keys`、`DELETE .../{keyId}` | 平台 Key 独立资源 |
| `GET/PUT /tenants/{id}/api-principal-config`、`POST /tenants/{id}/api-principal-test-token` | `GET/PUT /tenants/{id}/config/api-principal`、`POST /tenants/{id}/api-principal/test-token` | 配置段并入 config 子资源 |

**tenant**

| 旧 | 新 | 说明 |
|---|---|---|
| `POST /tenants`、`GET/PUT/DELETE /tenants/{id}`、`GET /tenants`、`/tenants/search`、`/tenants/all` | 同路径换前缀 | 机械 |
| `GET/PUT /tenants/kv/{key}` | `GET/PUT /tenants/{id}/config/{section}`；`prompt-templates` 改 `GET /prompt-templates`；`storage-engine-config` 删除 | 8 个 key 的去向见决定二第 1 条 |
| `GET/POST /tenants/{id}/members`、`PUT/DELETE .../{userId}`、`POST /tenants/{id}/leave` | 同路径换前缀 | 机械 |
| `GET/POST /tenants/{id}/invitations`、`DELETE .../{invId}`、`POST .../invite-links`、`GET /me/invitations`、`.../pending-count`、`POST /me/invitations/{invId}/accept`、`.../decline`、`POST /me/invitations/accept-by-token` | 同路径换前缀 | 机械 |

**system**

| 旧 | 新 | 说明 |
|---|---|---|
| `GET /system/capabilities`、`/system/info`、`/system/parser-engines`、`POST .../check`、`POST /system/docreader/reconnect` | 同路径换前缀 | 机械 |
| `POST /system/admin/promote`、`/revoke`、`GET /system/admin/list`、`POST /system/admin/users/reset-password`、`/users/create` | `POST /system/users/{id}/system-admin`、`POST /system/users/{id}/password-resets`、`POST /system/users`、`GET /system/users` | 动作资源化 |
| `GET /system/admin/settings`、`GET/PUT/DELETE /system/admin/settings/{key}` | `GET /system/settings`、`GET/PUT/DELETE /system/settings/{key}` | 机械 |
| `GET /system/admin/runtime/queues`、`.../{queue}/tasks`、`POST .../actions/{action}`、`DELETE .../archived` | `GET /system/runtime-queues`、`.../{queue}/tasks`、`POST .../{taskId}/actions`、`DELETE .../archived` | 动作进请求体 |
| `POST /system/admin/tenants/apply-default-storage-quota` | `POST /system/tenants/storage-quota-defaults` | 机械 |

**initialization（并入 system 与 model）**

| 旧 | 新 | 说明 |
|---|---|---|
| `GET/PUT /initialization/config/{kbId}` | 删除 | 归计划 04 的知识库配置 |
| `POST /initialization/remote/check`、`/embedding/test`、`/rerank/check`、`/asr/check`、`/multimodal/test` | `POST /system/model-probes`，请求体 `kind` 加连接参数 | 五个端点合一 |
| `POST /initialization/extract/text-relation`、`/extract/fabri-tag`、`/extract/fabri-text` | `POST /system/extraction-probes`，请求体 `kind` | 三个合一 |
| `GET/POST /models/ollama/*` 七条 | `/models/ollama/status`、`/models/ollama/models`、`/models/ollama/models/checks`、`/models/ollama/downloads`、`/models/ollama/downloads/{taskId}` | 复数化 |

**storage**

| 旧 | 新 | 说明 |
|---|---|---|
| `GET/POST /storage-backends`、`GET/PUT/DELETE /{id}`、`POST /{id}/test`、`PUT /{id}/default`、`POST /test` | 同路径换前缀 | `types` 改 `provider-types` |
| `GET /storage/engine-status`、`POST /storage/engine-check` | `GET /system/storage-engine`、`POST /system/storage-engine/checks` | 与系统信息同组 |
| `GET/HEAD /files`、`GET/HEAD /api/v1/files/presigned`、`/files/presigned-preview`、`GET/HEAD /r/{token}` | `GET /api/v1/files/{handle}`、`GET /api/admin/v1/files/{handle}/signed-url`、`GET /api/v1/resources/{grantToken}` | 三组前缀分别给访客、开放接口、后台 |

**model、vectorstore、websearch、audit**

| 旧 | 新 | 说明 |
|---|---|---|
| `/models` 全部、`/models/{id}/credentials`、`/models/providers` | 同路径换前缀 | 机械 |
| `POST /models/{id}/debug` | 同路径，请求体加 `kind` | 见决定二第 5 条 |
| `/vector-stores` 全部 | 同路径换前缀 | `types` 改 `engine-types`，返回 3 种 |
| `/web-search-providers` 全部 | 同路径换前缀 | `types` 改 `provider-types` |
| `GET /web-search/providers` | `GET /web-search-providers` | 旧路由刻意不登记 API Key 策略，新实现删除独立路由 |
| `GET /tenants/{id}/audit-log` | `GET /tenants/{id}/audit-logs` | 复数 |
| `GET /knowledge-bases/{id}/activity` | 归计划 04 | 不是 audit 域的资源 |
| `GET /system/admin/audit-log` | `GET /system/audit-logs` | 机械 |

## 1.5 旧表到新表

通用规则：旧 `id varchar` 改 bigint 雪花，`tenant_id integer/bigint` 统一 bigint，`timestamp` 改 `timestamptz`，软删改哨兵值，租户表开行级安全。下表只写差异与写属主。

聚合根划分：tenant 用 `Tenant`、`TenantMember`、`TenantInvitation` 三个聚合；auth 用 `User`、`OpenApiKey`、`PlatformApiKey`；system 用 `SystemSetting`；audit 用 `AuditLog`；storage 用 `StorageBackend`、`StorageResource`（绑定与授权随资源）；model 用 `Model`、`OllamaDownloadTask`；vectorstore 与 websearch 各一个聚合。下表“写属主”列即聚合的 Repository。

| 新表 | 旧表 | 差异 | 写属主 |
|---|---|---|---|
| `tenants` | `tenants` | 删 12 个 jsonb 列（去向见 1.13），`id` 改雪花 | `TenantRepository` |
| `tenant_members` | `tenant_members` | `user_id`、`invited_by` 改 bigint，唯一 `(tenant_id, user_id, deleted_at)` | `TenantMemberRepository` |
| `tenant_invitations` | `tenant_invitations` | `invitee_user_id`、`invited_by` 改 bigint，`token` 唯一 | `TenantInvitationRepository` |
| `tenant_settings` | 新表 | `(tenant_id, section)` 唯一，`config jsonb`，承接 6 段配置 | `TenantConfigRepository` |
| `users` | `users` | 删 `tenant_id`（见决定一第 1 条），`id` 改雪花 | `UserRepository` |
| `open_api_keys` | `tenant_api_keys` 的租户行 | `key_hash` 与 `api_key_ciphertext` 双列，`id` 改雪花 | `OpenApiKeyRepository` |
| `platform_api_keys` | `tenant_api_keys` 的平台行 | 无 `tenant_id`，进 `ignore-tables` | `PlatformApiKeyRepository` |
| `open_api_key_principal_config` | `tenants.api_principal_config` | `(tenant_id)` 唯一，`mode`、`hmac_secret_ciphertext` | `OpenApiKeyPrincipalRepository` |
| `system_settings` | `system_settings` | `last_modified_by` 改 bigint，删 `requires_restart` 列 | `SystemSettingRepository` |
| `storage_backends` | `storage_backends` | `config` 拆成五列，`provider` 收口为 8 个值 | `StorageBackendRepository` |
| `storage_resources` | `resources` | 改名强调属主，保留 `handle`、`location_hash`、`content_hash`、`size`、`state` | `ResourceRepository` |
| `storage_resource_bindings` | `resource_bindings` | `owner_id` 改 bigint | `ResourceRepository` |
| `storage_access_grants` | `resource_access_grants` | 补 `tenant_id bigint NOT NULL`，开行级安全 | `ResourceGrantRepository` |
| `models` | `models` | `id` 改雪花，凭据抽到 `credential_ciphertext jsonb`，删 `is_builtin`（`managed_by='yaml'` 表达） | `ModelRepository` |
| `ollama_download_tasks` | 进程内 Map | `tenant_id`、`task_id`、`status`、`progress jsonb` | `OllamaDownloadTaskRepository` |
| `vector_stores` | `vector_stores` | `engine_type` 收口为 3 个值，凭据抽列 | `VectorStoreRepository` |
| `web_search_providers` | `web_search_providers` | `parameters` 的 `apiKey` 抽到加密列 | `WebSearchProviderRepository` |
| `audit_logs` | `audit_logs` | `actor_user_id`、`target_user_id` 改 bigint | `AuditLogRepository` |
| `audit_denied_dedup` | 进程内 Map | 键 `(tenant_id, actor_id, path, action)` 唯一，列 `last_seen_at` | `AuditLogRepository` |

## 1.6 旧队列与后台任务去向

| 旧 | 机制 | 新 | 说明 |
|---|---|---|---|
| `AuditLogRetentionRunner` + `AuditRetentionLifecycle` | 虚拟线程定时 | JobRunr 周期作业 `audit-retention-purge`，每小时一次，保留天数取 `SystemSettingKey.AUDIT_RETENTION_DAYS` | 作业调度由计划 04 的 `JobScheduler` 承载，本计划提供作业类 |
| `BuiltinModelsReconciler` | `ApplicationRunner` | JobRunr 一次性作业 `builtin-models-sync`，固定 jobId 去重 | 多副本不重复 |
| `SystemSettingService.applyWhitelistOnStartup` | `ApplicationReadyEvent` 推静态白名单 | `SsrfGuard` Bean 构造时读 `SystemSettingApi.listValue(SSRF_WHITELIST)`，变更后由 `SettingSideEffectDispatcher` 调 `reloadWhitelist` | 静态字段删除 |
| `OllamaDownloadTaskStore` | 进程内静态 Map | 表 `ollama_download_tasks` | 旧实现多实例不共享，属缺陷 |
| `TenantAPIKeyService` 启动监听 | `ApplicationReadyEvent` | 删除 | 启动期无动作 |
| `WebSearchTempKbStateService` | Redis `tempkb:<sessionID>` | 保留键与语义，类改 `WebSearchSessionStateRepository` | 键前缀收口为常量 |
| `OssFileService` 分片上传线程池 | `Executors` | 保留 3 并发，执行器改虚拟线程 | 不新增长期线程池 |

## 1.7 跨域依赖与新做法

| 旧依赖 | 新做法 |
|---|---|
| `system.SystemAdminUserService` 调 `contract.auth.AdminUserPort` | system 调 `auth.api.UserQueryApi` 与 `RoleApi`，用户增删改留 auth |
| auth 读 `SystemSettingGateway` | auth 调 `system.api.SystemSettingApi` |
| initialization 读 `KnowledgeCatalogPort`、`ModelCatalogPort`、`StorageBackendPort` | 探测迁 system，走 `model.api` 与 `storage.api` |
| storage 读 `TenantConfigLookup` | storage 调 `tenant.api.TenantStorageApi` 取默认后端 |
| vectorstore 调 `VectorStoreEngineCatalogPort` | vectorstore 实现 retrieval-engine 的 spi |
| websearch 调 `KnowledgeMaintenancePort` | websearch 只存 Redis 状态，删除由 session 调 knowledge，websearch 不再调 knowledge |
| auth 直接 join knowledge 校验 KB 白名单 | auth 调 `knowledge.api.KnowledgeBaseQueryApi.filterExistingKbIds`（计划 04 已声明）。缺失时 Task 9 先不做白名单校验并登记偏离 |
| 全仓 22 处 import `auth.domain.User` | 改走 `auth.api.UserQueryApi` |
| 全仓 19 处 import `websearch.config.WebSearchProviderParams` | 改走 `websearch.api.WebSearchApi` |

## 1.8 旧测试处置

旧测试 35 个类、7,153 行。system、initialization、vectorstore 三域无测试。

| 旧测试类 | 测什么 | 处置 |
|---|---|---|
| `apiKey/APIKeyGateTest` | 门禁通过拒绝时序 | 丢弃。钉旧过滤器返回值，行为由 `AuthRouteRulesTest` 重写 |
| `apiKey/APIKeyRouteAuthorizerTest` | 策略注册与路径模板转换 | 丢弃。策略表删除，改由 `PermissionCatalogTest` 覆盖权限串完备性 |
| `apiKey/APIKeyAuthChannelPrincipalTest` | 三种 API 主体与 JWT 校验链 | 改写为 `OpenKeyResolverTest` |
| `apiKey/TenantAPIKeyBootstrapTest` | 建租户自动发 Key 与响应键序 | 移植业务断言：建租户后有 `open_api_keys` 行；键序断言丢弃 |
| `apiKey/TenantAPIKeyDomainTest` | 能力枚举与白名单三态 | 移植为 `OpenApiKeyCapabilityTest` |
| `apiKey/TenantAPIKeyServiceTest` | Key 服务语义 | 改写为 `OpenApiKeyUsecaseTest` |
| `apiKey/TenantAPIKeyValidatorTest` | 校验文案与顺序 | 改写为错误码断言 |
| `AuthFilterApiDocsTest` | 文档路径白名单 | 移植为 `permit-paths` 配置测试 |
| `TenantConfigRedactionTest` | 配置密钥打码与合并 | 移植 |
| `LoginTenantResolverTest` | 登录目标空间解析 | 移植，last-active 到 home 到 membership 顺序不变 |
| `TenantServiceLegacyStorageMigrationTest` | 旧 KV 存储配置启动迁移 | 丢弃。旧 KV 列删除 |
| `storage/fileserve/LocalFileContentServiceTest` | 本地路径规范化 | 移植为 `LocalOssTemplateTest` |
| `storage/fileserve/ProviderWiringTest` | provider 装配 | 改写为 `StorageBackendAdapterRegistryTest` |
| `storage/fileserve/StoragePathGuardTest` | local 双实现语义一致 | 移植为 `ObjectKeyGuardTest` |
| `storage/fileserve/StoragePathsTest` | 路径解析与签名 round-trip | 移植为 `ResourceHandleTest` |
| `storage/fileserve/StorageProjectionVocabularyTest` | env 到 provider 投影 | 丢弃。进程级 `STORAGE_TYPE` 覆盖删除 |
| `storage/fileserve/StorageUrlWiringConfigTest` | bean 存在性 | 丢弃。wire 类删除 |
| `storage/provider/LocalFileServiceTest` | 本地后端布局与路径遍历拒绝 | 移植为 `LocalOssTemplateTest` |
| `storage/provider/OssMultipartUploadTest` | OSS 大文件分片 | 移植为 `S3MultipartUploadTest`（MinIO 容器） |
| `storage/provider/ProviderBatchTwoTest` | oss、cos、tos 契约与校验文案 | 丢弃文案断言，移植 provider 解析断言 |
| `storage/provider/S3CompatibleFileServiceTest` | S3 协议族解析 | 移植 |
| `storage/provider/StorageProvidersTest` | 展示名单与凭据族命名 | 改写为 `StorageProviderTypesTest`，断言 8 个展示项顺序 |
| `storage/support/FileServiceResolverWiringTest` | 兜底顺序 | 丢弃。单请求缓存换成 `TenantClientRegistry` |
| `storage/support/RewriterForRequestTest` | 请求期解析器接线 | 丢弃。wire 类删除 |
| `model/ModelServiceConfigValidationTest` | 保存前结构校验、失败不落库 | 移植为 `ModelUsecaseTest` |
| `websearch/WebSearchProviderRequestBindingTest` | 请求体 snake 与响应 camel | 丢弃键名断言，移植绑定成功断言 |
| `websearch/WebFetchTest` | web_fetch 失败分支 | 移植 |
| `websearch/WebSearchProviderExecTest` | 请求体逐字节比对 | 丢弃。金片钉 Go 版字节形状；移植为 URL 与关键查询参数断言 |
| `websearch/WebSearchTempKbStateServiceTest` | 临时 KB 状态四分支 | 丢弃键序断言，移植四分支行为 |
| `websearch/WebSearchServiceExecTest` | 执行链过滤与黑名单 | 移植 |
| `audit/AuditLogControllerTest` | 端点契约与键序 | 丢弃键序与游标外形，移植分页与过滤断言 |
| `audit/AuditLogRetentionTest` | 保留期 cutoff 与幂等 | 移植为 `AuditRetentionJobTest` |
| `audit/AuditLogServiceTest` | 写入兜底与 1 分钟去重 | 移植，去重改查 `audit_denied_dedup` |
| `audit/RbacDeniedAuditTest` | 403 埋点落库 | 丢弃 403 形态，移植“被拒后写一行” |
| `audit/WikiActivityAuditRecorderTest` | Wiki 活动埋点 | 移交计划 05 |

## 1.9 `ignore-tables` 与 `@IgnoreTenant` 白名单

`gac.furnace.tenant.ignore-tables`（无 `tenant_id`，租户插件跳过）：

`tenants`、`users`、`system_settings`、`platform_api_keys`、`ollama_download_tasks`。`open_api_key_principal_config` 带 `tenant_id`，不在此列。

本计划 `@IgnoreTenant` 白名单（路线图 3.7 第 3 条，登记 `progress.md`）：

| 位置 | 原因 |
|---|---|
| `TenantQueryApiImpl.listTenantIds` | 平台管理面枚举全部租户，属认证后的平台系统通道 |
| `LoginTenantResolver.resolve` | 登录期无租户上下文，按用户成员关系解析目标租户 |
| `OpenKeyAuthenticationFilter.resolve` | Key 认证期按 hash 定位，请求尚无租户上下文 |
| `PlatformApiKeyRepository` 全部查询 | 平台 Key 是跨租户系统凭据，无租户维度 |
| `AuditLogRepository.listPlatform` | 平台审计流固定 `tenant_id=0`，读取时无租户上下文 |
| `AuditRetentionJob.purge` | 周期任务跨租户物理删除过期日志 |

每处写一行中文注释说明原因。

## 1.10 错误码清单

模块名称统一写 `ragagent`。模块号见码值第 4 到 6 位。

| 模块名称 | 功能点描述（key） | 业务场景 | 中文 | English |
|---|---|---|---|---|
| ragagent | `TENANT_NOT_FOUND` (1001002001) | 按 ID 读租户不存在或已删 | 租户不存在 | Tenant does not exist |
| ragagent | `TENANT_CREATION_DISABLED` (1001002002) | 非平台管理员自助建租户且开关关闭 | 自助创建租户已关闭，请通过邀请加入 | Self-service tenant creation is disabled, please join by invitation |
| ragagent | `TENANT_QUOTA_EXCEEDED` (1001002003) | 用户名下租户数达上限 | 名下租户数量已达上限 | You have reached the tenant limit |
| ragagent | `TENANT_SWITCH_FORBIDDEN` (1001002004) | 无成员关系时切租户 | 你不是目标租户的成员 | You are not a member of the target tenant |
| ragagent | `TENANT_NAME_REQUIRED` (1001002005) | 建租户或改名时名称为空 | 租户名称不能为空 | Tenant name is required |
| ragagent | `TENANT_MEMBER_NOT_FOUND` (1001002006) | 成员操作目标不存在 | 成员不存在 | Member does not exist |
| ragagent | `TENANT_INVITATION_NOT_FOUND` (1001002007) | 邀请不存在或已失效 | 邀请不存在或已失效 | Invitation does not exist or has expired |
| ragagent | `TENANT_INVITATION_CONFLICT` (1001002008) | 同租户同用户已有待接受邀请 | 已有待接受的邀请 | An invitation is already pending |
| ragagent | `TENANT_INVITEE_NOT_REGISTERED` (1001002009) | 被邀请邮箱未注册 | 该邮箱尚未注册，请先注册 | The invitee has not signed up yet |
| ragagent | `TENANT_ROLE_INVALID` (1001002010) | 角色值不在枚举内 | 角色取值无效 | Invalid role |
| ragagent | `TENANT_LAST_OWNER` (1001002011) | 移除或降级最后一个 owner | 不能移除或降级最后一个所有者 | The last owner cannot be removed or demoted |
| ragagent | `AUTH_BAD_CREDENTIALS` (1001003001) | 登录用户名或密码错 | 用户名或密码错误 | Incorrect username or password |
| ragagent | `AUTH_ACCOUNT_DISABLED` (1001003002) | 账号被禁用 | 账号已禁用 | Account is disabled |
| ragagent | `AUTH_REGISTRATION_CLOSED` (1001003003) | 仅邀请模式下的注册 | 当前仅支持邀请注册 | Registration is invite-only |
| ragagent | `AUTH_PASSWORD_POLICY` (1001003004) | 密码不满足复杂度 | 密码不满足复杂度要求 | Password does not meet the policy |
| ragagent | `AUTH_SAME_PASSWORD` (1001003005) | 新密码与旧密码相同 | 新密码不能与当前密码相同 | The new password must differ from the current one |
| ragagent | `AUTH_OIDC_UNAVAILABLE` (1001003006) | OIDC 未配置或发现失败 | 单点登录不可用 | Single sign-on is unavailable |
| ragagent | `AUTH_OIDC_STATE_INVALID` (1001003007) | 回调 state 校验失败或过期 | 登录状态已失效，请重新登录 | The login state has expired, please sign in again |
| ragagent | `AUTH_KEY_NAME_REQUIRED` (1001003008) | 创建 API Key 未填名称 | API Key 名称不能为空 | API key name is required |
| ragagent | `AUTH_KEY_CAPABILITY_UNKNOWN` (1001003009) | 能力值未知 | 能力取值未知 | Unknown capability |
| ragagent | `AUTH_KEY_KB_FORBIDDEN` (1001003010) | 白名单含非本租户知识库 | 知识库不在当前租户内 | The knowledge base is not in this tenant |
| ragagent | `AUTH_KEY_EXPIRY_INVALID` (1001003011) | 过期时间不晚于当前 | 过期时间必须晚于当前时间 | The expiry must be in the future |
| ragagent | `AUTH_SESSION_EXPIRED` (1001003012) | 会话过期 | 登录已过期，请重新登录 | Your session has expired, please sign in again |
| ragagent | `SYSTEM_SETTING_UNKNOWN` (1001004001) | 设置 key 未登记 | 未知的系统设置项 | Unknown system setting |
| ragagent | `SYSTEM_SETTING_VALUE_INVALID` (1001004002) | 设置值与类型不符 | 系统设置值不合法 | Invalid system setting value |
| ragagent | `SYSTEM_SETTING_READ_ONLY` (1001004003) | 改只读或退役 key | 该系统设置项不可修改 | This system setting is read-only |
| ragagent | `SYSTEM_TASK_QUEUE_UNKNOWN` (1001004004) | 运行时队列名未知 | 未知的任务队列 | Unknown task queue |
| ragagent | `SYSTEM_TASK_ACTION_INVALID` (1001004005) | 任务动作不在允许集合 | 不支持的任务操作 | Unsupported task action |
| ragagent | `SYSTEM_MODEL_PROBE_INPUT` (1001004006) | 探测缺少模型名或地址 | 模型名称和地址不能为空 | Model name and base URL are required |
| ragagent | `SYSTEM_PARSER_ENGINE_DISABLED` (1001004007) | 解析引擎被禁用 | 该解析引擎已被禁用 | This parser engine is disabled |
| ragagent | `SYSTEM_DOCREADER_UNAVAILABLE` (1001004008) | DocReader 探活失败 | DocReader 服务不可用 | DocReader service is unavailable |
| ragagent | `STORAGE_BACKEND_NOT_FOUND` (1001005001) | 按 ID 读存储后端不存在 | 存储后端不存在 | Storage backend does not exist |
| ragagent | `STORAGE_BACKEND_IN_USE` (1001005002) | 删除仍被知识库绑定的后端 | 存储后端仍被知识库使用 | The storage backend is still in use |
| ragagent | `STORAGE_PROVIDER_UNSUPPORTED` (1001005003) | provider 不在登记表 | 不支持的存储类型 | Unsupported storage provider |
| ragagent | `STORAGE_CONFIG_INCOMPLETE` (1001005004) | 必填字段缺失 | 存储配置不完整 | Storage configuration is incomplete |
| ragagent | `STORAGE_CONNECT_FAILED` (1001005005) | 连通性测试失败 | 存储连接失败 | Storage connection failed |
| ragagent | `STORAGE_ENDPOINT_BLOCKED` (1001005006) | 端点未过 SSRF 校验 | 存储端点被安全策略拒绝 | The storage endpoint is blocked |
| ragagent | `STORAGE_RESOURCE_NOT_FOUND` (1001005007) | 句柄不存在 | 资源不存在 | Resource does not exist |
| ragagent | `STORAGE_PATH_INVALID` (1001005008) | 对象键含路径穿越 | 文件路径不合法 | Invalid file path |
| ragagent | `STORAGE_BUCKET_CONFLICT` (1001005009) | 同桶名被其他租户占用 | 存储桶已被其他租户使用 | The bucket is already used by another tenant |
| ragagent | `MODEL_NOT_FOUND` (1001006001) | 按 ID 读模型不存在 | 模型不存在 | Model does not exist |
| ragagent | `MODEL_IN_USE` (1001006002) | 删除仍被引用的模型 | 模型仍被引用，无法删除 | The model is still referenced |
| ragagent | `MODEL_BUILTIN_READ_ONLY` (1001006003) | 非平台管理员改内置模型 | 仅系统管理员可修改内置模型 | Only system administrators can modify built-in models |
| ragagent | `MODEL_BUILTIN_NO_DELETE` (1001006004) | 删除内置模型 | 内置模型不可删除 | Built-in models cannot be deleted |
| ragagent | `MODEL_STATUS_ABNORMAL` (1001006005) | 读取下载中或失败的模型 | 模型状态异常 | The model is in an abnormal state |
| ragagent | `MODEL_TYPE_UNSUPPORTED` (1001006006) | 调试 kind 与模型类型不符 | 不支持的模型类型 | Unsupported model type |
| ragagent | `MODEL_DEBUG_INPUT` (1001006007) | 调试输入为空或超长 | 调试输入不合法 | Invalid debug input |
| ragagent | `MODEL_CREDENTIAL_FIELD_UNKNOWN` (1001006008) | 删除未知凭据字段 | 未知的凭据字段 | Unknown credential field |
| ragagent | `VECTOR_STORE_NOT_FOUND` (1001007001) | 按 ID 读向量库不存在 | 向量库不存在 | Vector store does not exist |
| ragagent | `VECTOR_STORE_IN_USE` (1001007002) | 删除仍被知识库绑定的向量库 | 向量库仍被知识库使用 | The vector store is still in use |
| ragagent | `VECTOR_STORE_ENGINE_UNSUPPORTED` (1001007003) | 引擎类型不在三种内 | 不支持的向量库引擎 | Unsupported vector store engine |
| ragagent | `VECTOR_STORE_CONNECT_FAILED` (1001007004) | 连通性测试失败 | 向量库连接失败 | Vector store connection failed |
| ragagent | `WEB_SEARCH_PROVIDER_NOT_FOUND` (1001008001) | provider 不存在 | 网页搜索提供商不存在 | Web search provider does not exist |
| ragagent | `WEB_SEARCH_PROVIDER_UNSUPPORTED` (1001008002) | provider 类型未知 | 不支持的网页搜索类型 | Unsupported web search provider |
| ragagent | `WEB_SEARCH_NOT_CONFIGURED` (1001008003) | 搜索时无可用提供商 | 未配置网页搜索提供商 | No web search provider is configured |
| ragagent | `WEB_SEARCH_CONNECT_FAILED` (1001008004) | 探活或搜索调用失败 | 网页搜索调用失败 | Web search request failed |
| ragagent | `WEB_SEARCH_FILTER_UNSUPPORTED` (1001008005) | 提供商不支持国家或时效过滤 | 该提供商不支持指定过滤条件 | The provider does not support these filters |
| ragagent | `AUDIT_LOG_QUERY_INVALID` (1001009001) | 时间范围或游标非法 | 审计查询条件不合法 | Invalid audit query |
| ragagent | `AUDIT_LOG_WRITE_INVALID` (1001009002) | 动作名为空或主体缺失 | 审计记录不完整 | Incomplete audit record |

## 1.11 旧类到新类映射

重点是 Ops 与 Support 的拆解。

| 旧类 | 旧行 | 新类与职责 |
|---|---:|---|
| `TenantCreateOps` | 261 | `TenantUsecase.create`、`TenantQuotaPolicy`、`OnboardingService` |
| `TenantCrudOps` | 183 | `TenantUsecase` 的 list、search、get、update、delete，每个 ≤ 40 行 |
| `TenantConfigOps` | 391 | `TenantConfigUsecase`、`TenantConfigRedaction`、`TenantConfigValidator` |
| `AuthSessionOps` | 208 | `SessionUsecase`、`TokenIssuer`（Sa-Token 会话绑定租户） |
| `AuthOidcOps` | 210 | `OidcUsecase`、`OidcStateCodec`、`OidcNonceCookie` |
| `UserSessionOps` | 165 | 并入 `SessionUsecase` |
| `AuthBindingSupport` | 58 | `LoginResponseAssembler`（MapStruct） |
| `TenantBindSupport` | 60 | 删除，请求体绑定交给 `@Valid` VO |
| `LoginTenantResolver` | 153 | 保留类名，实现 `TenantSwitchPolicy` |
| `AuthAccountService` | 62 | 并入 `SessionUsecase.login` |
| `TenantConfigRedaction` | 234 | 保留类名，只做打码与合并 |
| `StorageBackendLocator` | 76 | 并入 `StorageBackendUsecase` |
| `StorageConfigProjector` | 96 | `StorageBackendAssembler` |
| `StorageEnvProvisioner` | 47 | 删除，进程级 env 覆盖取消 |
| `FileServiceResolver` | 225 | 删除，换成 `TenantClientRegistry<StorageBackendConfig, OssTemplate>` |
| `FileserveStorageBackendResolver` | 71 | 删除 |
| `SystemAdminUserService` | 159 | `PlatformUserUsecase` |
| `DeploymentCapabilitiesHolder` | 68 | `DeploymentCapabilities`（`DeploymentApi` 实现） |
| `ModelRowViews` | 26 | `ModelAssembler` |
| `WebSearchProviderRegistry` | 56 | `WebSearchProviderRegistry`（`Map<WebSearchProviderType, WebSearchProviderFactory>`） |

## 1.12 前端 API 层切换表

第一个前端任务（Task 5）把 `@ragagent/api-client` 以 `file:` 依赖接进 `frontend/package.json`，并在 `frontend/vite.config.ts` 加 `resolve.alias` 与 `optimizeDeps.include`。之后各域任务按表切换，组件不改。

| 域 | 旧函数（文件） | 旧路径 | 新路径 | 形状 | 适配点 |
|---|---|---|---|---|---|
| tenant | `searchTenants`、`listAllTenants`、`createTenant`、`updateTenant`、`deleteTenant`（`api/tenant/index.ts`） | `/api/v1/tenants*` | `/api/admin/v1/tenants*` | 是 | 外壳解包，`id` 字符串转 number |
| tenant | `listMembers`、`addMember`、`updateMemberRole`、`removeMember`、`leaveTenant`（`tenant/members.ts`） | 同上 | 同上 | 是 | `userId` 字符串 |
| tenant | 邀请 9 个函数（`tenant/invitations.ts`） | 同上 | 同上 | 是 | 无 |
| tenant | `getParserEngineConfig`、`updateParserEngineConfig`（`api/system/index.ts`） | `/tenants/kv/parser-engine-config` | `/tenants/{id}/config/parser` | 否 | 返回值补 `defaults` 兼容旧组件 |
| system（Task 5 内一并切） | `getPromptTemplates`（`api/system/index.ts`） | `/tenants/kv/prompt-templates` | `/prompt-templates` | 是 | 静态目录，仍按 `Accept-Language` 渲染 |
| storage | `getStorageEngineConfig`（`api/system/index.ts`） | `/tenants/kv/storage-engine-config` | 删除，改读 `GET /storage-backends` | 否 | `editorResources.ts` 读的是默认后端，改为从列表取 `isDefault` 行 |
| auth | `login`、`getCurrentUser`、`updateMyPreferences`、`changePassword`、`logout` | `/api/v1/auth/*` | `/api/admin/v1/auth/*` | 是 | 登录响应 `token` 不变 |
| auth | `refreshToken` | `POST /auth/refresh` | 删除 | 否 | 移除函数，401 由 API 层统一跳登录页 |
| auth | `validateToken` | `/auth/validate` | `GET /auth/me` | 是 | 内部改路径 |
| auth | `getOIDCConfig`、`getOIDCAuthorizationURL` | `/auth/oidc/config`、`/url` | 同路径换前缀 | 是 | 无 |
| auth | `listTenantAPIKeys`、`createTenantAPIKey`、`updateTenantAPIKey`、`deleteTenantAPIKey` | `/tenants/{id}/api-keys` | `/tenants/{id}/open-api-keys` | 否 | 创建响应 `api_key` 字段名保持 |
| auth | `getAPIPrincipalConfig`、`updateAPIPrincipalConfig`、`createAPIPrincipalTestToken` | `/tenants/{id}/api-principal-*` | `/tenants/{id}/config/api-principal`、`.../api-principal/test-token` | 是 | 无 |
| auth | `listPlatformAPIKeys`、`createPlatformAPIKey`、`deletePlatformAPIKey`、`promoteUserToSystemAdmin`、`revokeSystemAdmin`、`listSystemAdmins`、`resetUserPassword`、`createSystemUser`（`api/system/index.ts`） | `/system/admin/*` | `/system/platform-api-keys`、`/system/users*` | 否 | 方法映射，页面调用不变 |
| system | `getSystemInfo`、`getDeploymentCapabilities`、`getParserEngines`、`checkParserEngines`、`reconnectDocReader`、`getStorageEngineStatus`、`checkStorageEngine` | `/system/*`、`/storage/engine-*` | `/system/*`、`/system/storage-engine*` | 是 | 无 |
| system | `listSystemSettings`、`getSystemSetting`、`updateSystemSetting`、`resetSystemSetting` | `/system/admin/settings*` | `/system/settings*` | 是 | 无 |
| system | `getRuntimeQueues`、`getRuntimeTasks`、`mutateRuntimeTask`、`purgeArchivedRuntimeTasks` | `/system/admin/runtime/*` | `/system/runtime-queues*` | 否 | action 进请求体 |
| system | `applyDefaultStorageQuotaToAllTenants` | `/system/admin/tenants/apply-default-storage-quota` | `/system/tenants/storage-quota-defaults` | 是 | 无 |
| storage | 8 个函数（`api/storage-backend.ts`） | `/storage-backends*` | 同路径换前缀 | 是 | `types` 改 `provider-types`，凭据改 `configuredFields` |
| model | 7 个模型函数 + 7 个 Ollama 函数（`api/model/index.ts`、`api/initialization/index.ts`） | `/models*` | 同路径换前缀 | 否 | `debugModel` 请求体加 `kind`，下载路径复数 |
| vectorstore | 7 个函数（`api/vector-store.ts`） | `/vector-stores*` | 同路径换前缀 | 是 | `types` 改 `engine-types`，下拉 3 项，组件读返回值 |
| websearch | 9 个函数（`api/web-search-provider.ts`） | `/web-search-providers*` | 同路径换前缀 | 是 | `types` 改 `provider-types`；`testWebSearchProvider` 两分支映射 `{id}/test` 与 `/test` |
| audit | `listAuditLog`、`listSystemAuditLog` | `/tenants/{id}/audit-log`、`/system/admin/audit-log` | `/tenants/{id}/audit-logs`、`/system/audit-logs` | 否 | 游标 `{items,nextCursor}` 映射 `PageData` |

## 1.13 `tenants` 表 jsonb 配置列归属拆解与两处未确认核实

实测 `V1__baseline.sql:1332-1381` 的 `CREATE TABLE tenants` 含 **12** 个 jsonb 列。实体 `Tenant.java` 映射 11 个（`agent_config` 未映射）。

| 旧 jsonb 列 | 新归属 | 落点 | 旧读写方 |
|---|---|---|---|
| `retriever_engines` | knowledge | 知识库行内配置，租户默认值放 `tenant_settings` 的 `retrieval` 段 | `TenantService.retrieverEngines` |
| `agent_config` | 删除 | 全仓 Java 无读写，只有 DDL 与 GIN 索引 | 无 |
| `context_config` | session | `tenant_settings` 的 `session` 段 | `TenantService.normalizeContextConfig` |
| `conversation_config` | 删除 | 全仓零读写 | 无 |
| `web_search_config` | websearch | `tenant_settings` 的 `websearch` 段 | `TenantConfigOps`、`TenantService.webSearchConfig` |
| `parser_engine_config` | system | `tenant_settings` 的 `parser` 段 | `TenantConfigOps`、`SystemController` |
| `storage_engine_config` | storage | 废弃，默认后端只由 `storage_backends` 行与 `tenants.default_storage_backend_id` 表达 | 写入口已关闭 |
| `chat_history_config` | knowledge | `tenant_settings` 的 `chat-history` 段 | `TenantConfigOps` |
| `retrieval_config` | tenant | `tenant_settings` 的 `retrieval` 段 | `TenantConfigOps`、session 检索参数 |
| `credentials` | 删除 | 无独立写入口，凭据分散在各属主表加密列 | `TenantService.toView` |
| `api_principal_config` | auth | `open_api_key_principal_config` 行 | `TenantAPIPrincipalController` |
| `memory_config` | memory（计划 06） | `tenant_settings` 的 `memory` 段 | `TenantConfigOps` |

`tenant_settings` 表：`id bigint`、`tenant_id bigint NOT NULL`、`section varchar(32) NOT NULL`、`config jsonb NOT NULL DEFAULT '{}'`、`updated_at timestamptz`，唯一 `(tenant_id, section)`，开行级安全。写属主 `TenantConfigRepository`，`TenantConfigApi` 只读，写只经 `TenantConfigUsecase`。

**未确认一：`TenantFilterGuard` 注释写 72 条，实现 73 条。核实结论：实现 73 条，注释过时，代码以 73 条为准。** 证据：用脚本对 `ALLOWED_STATEMENTS` 的 `Set.of(...)` 字面量做去重计数，得到 73 个互不重复的字符串，首条 `TenantAPIKeyMapper.listByPlaceholderHash`，末条 `TaskDeadLetterMapper.selectList`。类注释 `:75` 写“首次盘面 72 条”，是首次盘面时的计数；`git log -S "TemporaryDocumentMapper.selectList"` 显示后来 `1ff43344a` 补了定性条目，注释未同步。迁移处置：63 条随旧机制删除（`TenantFilterGuard` 删除，`tenant_id` 由插件与行级安全承担），余下 10 条属于 `@IgnoreTenant` 白名单，见 1.9。本计划不迁这张表，也不修正旧注释。

**未确认二：`system_settings` 与租户 jsonb 在执行期有无叠加。核实结论：无叠加，两链互不读取；新实现按 spec 5.3 引入三层合并。** 证据链：
1. `SystemSettingService.resolveRaw`（`:94-104`）只读 `system_settings` 行，缺失时回落 Spring `Environment` 的 ENV，再回落 `SystemSettingRegistry` 默认值，全程不读 `tenants` 任何列。
2. 全仓读设置的调用点共 8 处，都在 auth 域（`AuthController:416,510,515,523`、`UserService:496`、`TenantCreateOps:53,98,171,221`），读的都是部署级开关（自助建租户、默认配额、邀请自动接受、注册模式），不是配置段。
3. 租户 jsonb 的读取方（`TenantService` 的 `TenantConfigLookup` 实现、`TenantConfigOps`）不注入 `SystemSettingGateway`，反向也不成立。
4. 唯一相近的是 `parser_engine_config`（租户段）与 `SystemController.listParserEngines`（读租户段后再叠加 sandbox 开关），这是同域内两段落，不是 `system_settings` 覆盖租户配置。

结论与处置：旧系统靠调用方各自选层，没有统一顺序。新实现在 system 域引入 `EffectiveSettings`，顺序为 yml 默认值、`system_settings`、租户配置，任一层空串视为未设置。这是与旧行为不一致的地方，已记入决定四第 6 条。租户段消费方（`TenantConfigApi`）只提供租户层值，不感知上层。

## 1.14 认证通道翻译对照表

旧三通道 `AuthFilter` 加 290 条 `APIKeyRoutePolicies` 与 AuthFilter 周边的 12 个过滤器与拦截器（`WsAuthSupport`、`APIKeyAuthChannel`、`APIKeyGateInterceptor`、`APIKeyRouteAuthorizer`、`APIKeyRoutePolicy`、`APIKeyScopeCleanupFilter`、`AllowFileServeAPIKeyInterceptor`、`DenyAPIKeyPrincipalInterceptor`、`APIKeyGateResponses`、`ApiKeyAuthRequest`、`ApiKeyAuthResult`，加 boot 的 `WebConfig` 装配），翻译为 Sa-Token 的路由规则与注解。语义保持：同一路径在同一身份下给同一结果。

**通道层**

| 旧机制 | 新机制 | 语义保持点 |
|---|---|---|
| `AuthFilter` 通道 0：OPTIONS 放行 | `SaInterceptor` 首行对 OPTIONS 放行 | 预检不被拦 |
| `AuthFilter` 通道 0.5：`..` 段返回 400 | framework `PathTraversalGuard`（计划 01 Task 7 过滤器族） | 解码后含 `..` 返回 400 |
| `AuthFilter` 通道 1：`NO_AUTH_API` 14 条 | app `application.yml` 的 `gac.furnace.security.permit-paths` | 逐条迁移，路径换新前缀，新增 OIDC 回调与 MCP 回调 |
| `AuthFilter` 通道 1.1：API 文档 | `permit-paths` 加 `/v3/api-docs/**,/swagger-ui/**` | 生产关闭 swagger |
| `AuthFilter` 通道 1.5：`/api/v1/embed/**` 让路 | `/api/v1/embed/**` 归 visitor；无 token 时由 channel 的 `EmbedAuthFilter` 自行鉴权 | 旧实现让路是因 embed 自鉴权，新实现两级判定不冲突 |
| `AuthFilter` 通道 1.6：`/api/v1/im/callback/**` | `permit-paths` 放行，验签在 channel 适配器 | 平台签名自证 |
| `AuthFilter` 通道 1.7：`/r/{token}` | `permit-paths` 放行，`ResourceGrantService` 校验一次性令牌 | 能力 URL 自证 |
| `AuthFilter` 通道 1.8：沙箱终端 WS | `GET /api/admin/v1/sandboxes/{id}/terminal` 走 admin 登录，票据端点也在 admin 下 | 票据在 handler 内校验 |
| `AuthFilter` 通道 2：Bearer JWT | `AdminStpLogic.checkLogin()` | 未登录 4402 |
| `AuthFilter` 通道 3：`X-API-Key` | `OpenKeyAuthenticationFilter` 建立 open 会话，`OpenStpLogic` 判定 | 头名不变 |
| `AuthFilter` 全部未命中 401 | `SaInterceptor` fail-closed 到 admin | 同 |
| `APIKeyGateInterceptor` 未登记即 403 | 每条 open 路由必须显式注解，`OpenRouteAnnotationTest` 守 | default deny |
| `APIKeyScopeCleanupFilter` 清 ThreadLocal | Sa-Token 会话随请求结束失效，无需清理 | 无 ThreadLocal 作用域泄漏 |
| `AllowFileServeAPIKeyInterceptor` | 文件路由注解 `retrieve:kb` 或 `read:file` | 与旧 full-access 或 retrieve 等价 |
| `DenyAPIKeyPrincipalInterceptor` | `presigned-preview` 路由删除，改管理面签名端点 | 无对应的公开诊断入口 |
| `APIKeyGateResponses` 纯字符串 403 | `NotPermissionException` 处理器返回 6041 | 前端只判状态码 |

**能力与角色层**

| 旧策略类别 | 旧语义 | 新表达 |
|---|---|---|
| `any()` | 任何有效 Key | `@SaCheckLogin(type = "open")` |
| `fullAccess()` | 仅 full-access Key | `@SaCheckPermission(type = "open", value = "open:full-access")` |
| `platform(caps...)` | 平台 Key 且任一平台能力 | `@SaCheckPermission(type = "admin", value = {...}, mode = SaMode.OR)` |
| `manageXxx + fullAccess` | full-access 或显式能力 | `@SaCheckPermission(type = "open", value = "open:<resource>:<action>")` |
| 未登记即 403 | default deny | `SaRouteRules` 加注解守 |

**本批域 61 条旧策略逐组翻译**（组内路径机械映射）：

| 旧组（条数） | 新路径组 | 新权限 |
|---|---|---|
| model CRUD 与 debug（9） | `/api/admin/v1/models/**` 与 `/api/open/v1/models/**` | admin 侧 `manage:model`，open 侧 `open:model:manage` |
| Ollama 7 条 | `/models/ollama/**` 两前缀 | 同上 |
| initialization 探测 7 条 | `/system/model-probes`、`/system/extraction-probes` | `manage:model` |
| initialization 配置 2 条 | 迁计划 04 | 不属本计划 |
| system 信息与解析引擎 6 条 | `/system/info`、`/system/parser-engines*`、`/system/storage-engine*` | `read:system`，open 侧 `open:system:read` |
| system capabilities 1 条 | `/system/capabilities` | 登录即可 |
| system settings 3 条 | `/system/settings*` | `platform:settings:read`、`platform:settings:manage` |
| system 运行时 4 条 | `/system/runtime-queues*` | `platform:runtime:read`、`platform:runtime:manage` |
| system 租户配额 1 条 | `/system/tenants/storage-quota-defaults` | `platform:tenants:manage` |
| system 审计 1 条 | `/system/audit-logs` | `platform:audit:read` |
| 租户成员与邀请 8 条 | `/tenants/{id}/members*`、`/tenants/{id}/invitations*`、`/tenants/{id}/invite-links` | `manage:member` |
| 租户 api-principal 3 条 | 同路径 | `platform:tenants:read`、`platform:tenants:manage` |
| 租户 CRUD 与 KV 7 条 | `/tenants*`、`/tenants/{id}/config/*` | 平台侧 `platform:tenants:manage`，租户侧 `manage:tenant-settings` |
| 存储后端 7 条 | `/storage-backends*` | `manage:storage` |
| 向量库 7 条 | `/vector-stores*` | `manage:vector-store` |
| 网页搜索 10 条 | `/web-search-providers*` | `manage:web-search` |
| 文件 2 条 | `/api/v1/files/{handle}`、`/api/admin/v1/files/{handle}/signed-url` | 访客 `retrieve:kb`，后台 `read:file` |
| 会话续流 1 条 | 计划 08 | 不属本计划 |

刻意不登记的旧路由：4 条租户 api-keys、2 条 MCP 审批、`/tenants/{id}/leave`、5 条 `/me/invitations*`、`/web-search/providers`、3 条 logout/refresh/switch-tenant、`/system/admin` 的 5 条用户管理。新实现：前三组归 admin 且要求对应权限；`/web-search/providers` 删除；logout 与 refresh 由会话端点自身处理；用户管理走 `platform:users:manage`。

权限串规则：`domain:resource:action`，全部收口在 `PermissionCatalog` 枚举。角色派生：`VIEWER` 给全部 `:read`，`CONTRIBUTOR` 加内容写，`ADMIN` 加管理写，`OWNER` 全量，`systemAdmin` 额外给 `platform:*`。open Key 的 `fullAccess` 给全部 `open:*`，scoped Key 按 `APIKeyCapability` 映射。交付 `docs/migration/permission-points.md`，供计划 05、07、08 直接引用。

## 1.15 计划 05 的 `DistributedLockKeyResolver` 需求

需求：计划 05 要求 framework 的 `DistributedLockKeyResolver` Bean 加 `@ConditionalOnMissingBean`，允许 app 声明 `@Primary` 解析器，用于 wiki 的 `kbId:slug` 锁键。

本计划处置：本计划不使用分布式锁，不在本计划范围内，已列入“对前置计划的新增需求”。计划 01 未改时由计划 05 在 Task 6 前登记偏离，本计划不阻塞，也不在 `backend/framework` 下新增或修改任何类。

## 1.16 e2e 域标签与冒烟

用例文件沿用计划 01 Task 13 的清单，本计划不复制页面表。需转绿的标签：`auth`（4 个文件）、`tenant`（3 个）、`system`（5 个）、`storage`（1 个）、`model`（2 个）、`vectorstore`（1 个，不断言下拉项数）、`websearch`（1 个）、`audit`（1 个）。

冒烟：`tests/api-smoke/tests/auth.spec.ts` 的登录一步在本计划转绿（`POST /api/admin/v1/auth/login` 返回 `code=0000` 且 `data.token` 非空）。建知识库与上传两条留给计划 04。`skeleton-auth` 保持绿。

黄金样板：**tenant 域**。理由：它同时具备聚合根清晰（租户、成员、邀请）、有主从表写入、有行级安全、有前端配置段切换、有 Provider 实现四类典型形状；它不依赖其他域的表，可以独立先做；它是其余域的上游，先做出来能被 storage 与 knowledge 立刻复用。样板域按小方法、构造器注入、record VO 三条标准写，登记到 `docs/migration/golden-sample.md`。

# 2. 任务

每个任务五步：写失败测试、跑红、实现、跑绿、提交。跑红与跑绿都用 `MVN test -Dtest=<该任务测试类>`，跑红预期 FAIL 且原因是类不存在或断言不成立，跑绿预期 PASS。提交前跑 `MVN checkstyle:check`，预期无违规。提交后在 `docs/migration/progress.md` 追加一行。

## 批次 A：tenant（黄金样板）

### Task 1: 冻结点核对、tenant DDL 与 i18n 骨架

**Files:**
- Create: `DB/V<D>_01__DDL_rebuild_tenant_create_tables_jiangguifan.sql`
- Create: `R/i18n/app-messages.properties` 与七个语种文件
- Test: `T/tenant/TenantSchemaTest.java`

**Interfaces:**
- Consumes: `docs/migration/frozen-bugs.md`、`docs/optimization/spikes/2026-11-11-nimbus-rfc9728.md`、计划 01 Task 5 的 fail-closed 策略模板
- Produces: `tenants`、`tenant_members`、`tenant_invitations`、`tenant_settings` 四张表；8 个 i18n 文件；Flyway 当日序号起点

- [ ] **Step 1** 读 `frozen-bugs.md` 本域条目并抄进 `progress.md`；`test -f docs/optimization/spikes/2026-11-11-nimbus-rfc9728.md` 不存在则停下，先执行计划 01 Task 17。核对 `find domains/src/main/java/com/ragagent/<pkg> -name '*.java' | wc -l`：auth 122、storage 62、websearch 52、model 38、system 18、audit 20、initialization 11、vectorstore 20。有差异先改 1.1。
- [ ] **Step 2** `TenantSchemaTest`：四条 `tenant2CannotReadTenant1Rows_<表>` 与四条 `tenant2CannotInsertRowForTenant1_<表>`；`softDeleteThenRecreateSameNameAccepted`、`duplicateLiveMemberRejected`、`duplicateInvitationTokenRejected`、`tenantSettingsSectionUnique`。
- [ ] **Step 3** 跑红：`MVN test -Dtest=TenantSchemaTest`。实现四张表：`tenants` 不含 jsonb，`tenant_members` 唯一 `(tenant_id, user_id, deleted_at)`，`tenant_invitations` 唯一 `token`，`tenant_settings` 唯一 `(tenant_id, section)`；每张租户表 `ENABLE` 与 `FORCE ROW LEVEL SECURITY`，策略用 fail-closed 模板。同时建 8 个 `app-messages*.properties`，写入 4 个 tenant key，其余语种标 `# TODO 待人工校对`。
- [ ] **Step 4** 跑绿；反向验证：临时 `ALTER TABLE tenant_settings DISABLE ROW LEVEL SECURITY`，对应用例变红，确认后恢复。
- [ ] **Step 5** 提交：`git add DB/<脚本> R/i18n T/tenant/TenantSchemaTest.java`，`git commit -m "feat(tenant): 租户四张表、行级安全与文案骨架"`。

### Task 2: tenant Entity、Mapper、Repository 与 api

**Files:**
- Create: `M/tenant/entity/{Tenant,TenantMember,TenantInvitation,TenantSetting}.java`
- Create: `M/tenant/mapper/{TenantMapper,TenantMemberMapper,TenantInvitationMapper,TenantSettingMapper}.java`
- Create: `R/mapper/tenant/{TenantMapper,TenantMemberMapper,TenantInvitationMapper,TenantSettingMapper}.xml`
- Create: `M/tenant/repository/{TenantRepository,TenantMemberRepository,TenantInvitationRepository,TenantConfigRepository}.java`
- Create: `M/tenant/api/{TenantQueryApi,TenantStorageApi,TenantConfigApi,TenantSwitchPolicy,TenantCode,TenantBo,TenantCreateBo,TenantStorageBo,TenantQuotaBo,TenantSwitchDecisionBo,RetrievalTenantConfigBo,WebSearchTenantConfigBo,ParserTenantConfigBo,MemoryTenantConfigBo,ChatHistoryTenantConfigBo}.java`
- Create: `M/tenant/config/TenantCodeRegistration.java`
- Modify: `R/i18n/app-messages*.properties`（11 个 tenant key）
- Test: `T/tenant/{TenantRepositoryTest,TenantConfigRepositoryTest,TenantCodeMessagesTest}.java`

**Interfaces:**
- Produces: “对外承诺”里 tenant 的四个 api 与 BO；`TenantRepository.byIdAcrossTenants(long)`（`@IgnoreTenant`）、`search(String, long, int, int)`、`softDeleteCascade(long)`；`TenantMemberRepository.countOwnedBy(long)`、`softDeleteByTenant(long)`；`TenantInvitationRepository.byToken(String)`；`TenantConfigRepository.upsert(long, String, String)`（`ON CONFLICT` 写 XML）；`TenantCode` 十一个枚举值，码值见 1.10

- [ ] **Step 1** `TenantRepositoryTest.searchEscapesLikeWildcards`、`searchReturnsCreatedAtDesc`、`softDeleteCascadeHidesMembers`；`TenantConfigRepositoryTest.upsertOverwritesSameSection`、`missingSectionReturnsEmpty`、`configUpdateBumpsUpdatedAt`；`TenantCodeMessagesTest.everyCodeHasTextInSevenLocales`。
- [ ] **Step 2** 跑红。
- [ ] **Step 3** 实现：Entity `@Getter/@Setter`，`deletedAt` 标 `@TableLogic(value = "'1970-01-01 00:00:00+00'", delval = "now()")`；BO 全用 `record`，带中文 Javadoc 与 `@author jiangguifan`；`TenantCode implements Code`，`httpStatus()` 按语义给 400、403、404、429；`TenantCodeRegistration` 用 `@PostConstruct` 调 `CodeCache.register`。
- [ ] **Step 4** 跑绿。
- [ ] **Step 5** 提交：`git add M/tenant/entity M/tenant/mapper M/tenant/repository M/tenant/api M/tenant/config R/mapper/tenant R/i18n T/tenant/TenantRepositoryTest.java T/tenant/TenantConfigRepositoryTest.java T/tenant/TenantCodeMessagesTest.java`，`git commit -m "feat(tenant): 持久层、api 与错误码"`。

### Task 3: tenant Service 用例

**Files:**
- Create: `M/tenant/service/{TenantUsecase,TenantQuotaPolicy,OnboardingService,TenantConfigUsecase,TenantConfigValidator,TenantConfigRedaction,TenantCascadeDeletion,LoginTenantResolver,TenantQueryApiImpl,TenantStorageApiImpl,TenantConfigApiImpl,TenantRetrievalSettingsImpl}.java`
- Test: `T/tenant/{TenantUsecaseTest,TenantConfigUsecaseTest,LoginTenantResolverTest,TenantRetrievalSettingsImplTest}.java`

**Interfaces:**
- Consumes: `SystemSettingApi`（自助开关、默认配额、每用户租户上限）；`OpenApiKeyProvisioner`（Task 9 提供，本任务用测试替身）；计划 02 的 `retrieval.spi.TenantRetrievalSettings`、`RetrievalDriverRef`、`RetrievalTuning`
- Produces: `TenantUsecase.create(TenantCreateBo)`、`update`、`delete`、`search`；`TenantQuotaPolicy.assertCanOwn(long)`；`OnboardingService.bootstrapOwner(long, long)`；`TenantConfigUsecase.read/update(long, String, String)`；`LoginTenantResolver.resolve(long)` 顺序 last-active 到 home 到 membership；`TenantRetrievalSettingsImpl` 实现计划 02 的 spi，类放 `M/tenant/service` 内部包，不放 `api`，签名逐字照抄计划 02：`List<RetrievalDriverRef> effectiveEngines(long tenantId)`、`RetrievalTuning tuning(long tenantId)`

- [ ] **Step 1** `TenantUsecaseTest.selfServiceCreationRespectsSwitch`、`quotaPrecheckAndRecheck`、`createBootstrapsOwnerAndDefaultQuota`、`deleteCascadesMembers`；`TenantConfigUsecaseTest.configSectionMergeKeepsMaskedSecrets`、`retrievalThresholdsOutOfRangeRejected`、`parserSectionRequiresAdmin`；`LoginTenantResolverTest.prefersLastActiveThenHomeThenFirstMembership`、`noMembershipReturnsEmpty`；`TenantRetrievalSettingsImplTest.emptyEnginesFallBackToDefaultDriver`、`engineOrderFollowsRetrievalSection`、`missingRetrievalSectionReturnsDefaultTuning`。
- [ ] **Step 2** 跑红。
- [ ] **Step 3** 实现：`TenantUsecase.create` 拆三个私有方法（校验、落库、提交后引导），每个 ≤ 40 行；打码占位符用 `***`；`TenantConfigValidator` 按 section 校验范围（`vectorThreshold` 0 到 1，`rerankThreshold` 负 10 到 10，`embeddingTopK` 0 到 200）。`TenantRetrievalSettingsImpl` 的 `effectiveEngines` 读 `TenantConfigApi.retrieval(tenantId)` 的 `engines` 数组，缺失或空数组时回落到默认驱动一条（与旧 `EffectiveEngines.of` 同语义），配置几条按声明顺序返回几条；`tuning` 从同一段映射到 `RetrievalTuning`，段缺失时返回默认值。
- [ ] **Step 4** 跑绿；加事务断言：用例内无 HTTP 调用。
- [ ] **Step 5** 提交：`git add M/tenant/service T/tenant/TenantUsecaseTest.java T/tenant/TenantConfigUsecaseTest.java T/tenant/LoginTenantResolverTest.java T/tenant/TenantRetrievalSettingsImplTest.java`，`git commit -m "feat(tenant): 租户用例与配置段"`。

### Task 4: tenant Controller、VO 与 Assembler

**Files:**
- Create: `M/tenant/controller/{TenantController,TenantMemberController,TenantInvitationController,TenantConfigController}.java`
- Create: `M/tenant/vo/{TenantCreateVo,TenantUpdateVo,TenantSearchVo,TenantRespVo,MemberAddVo,MemberRoleVo,MemberRespVo,InvitationCreateVo,InvitationRespVo,InviteLinkVo,TenantConfigVo}.java`
- Create: `M/tenant/assembler/{TenantAssembler,MemberAssembler,InvitationAssembler}.java`
- Test: `T/tenant/{TenantControllerTest,TenantMemberControllerTest,TenantConfigControllerTest}.java`

**Interfaces:**
- Produces: 1.4 表里的 tenant 路径。本任务只加 `@SaCheckLogin`，权限串在 Task 10 统一补

- [ ] **Step 1** `TenantControllerTest.createReturnsStringId`、`searchPaged`、`updateDescriptionPersists`、`deleteIsIdempotent`；`TenantMemberControllerTest.addMemberTwiceRejected`、`removeLastOwnerRejected`；`TenantConfigControllerTest.readParserSectionMasksSecrets`、`writeRetrievalOutOfRangeReturns400`。
- [ ] **Step 2** 跑红。
- [ ] **Step 3** 实现：控制器只做绑定、`@Valid`、调一个 Service；VO 用 `record` 加校验注解，`@NotNull(message = "{code_...}")`；Assembler 用 MapStruct。
- [ ] **Step 4** 跑绿；`MVN test -Dtest=OpenApiExportTest` 通过，`target/openapi.json` 含 `/api/admin/v1/tenants`。
- [ ] **Step 5** 提交：`git add M/tenant/controller M/tenant/vo M/tenant/assembler T/tenant/TenantControllerTest.java T/tenant/TenantMemberControllerTest.java T/tenant/TenantConfigControllerTest.java`，`git commit -m "feat(tenant): 控制器与 VO"`。

### Task 5: 前端接入 `@ragagent/api-client` 与 tenant API 层

**Files:**
- Modify: `frontend/package.json`（`"@ragagent/api-client": "file:./packages/api-client"`）
- Modify: `frontend/vite.config.ts`（`resolve.alias` 与 `optimizeDeps.include`）
- Modify: `frontend/src/utils/request.ts`（改为 `createApiClient` 薄封装，保留原导出）
- Modify: `frontend/src/api/tenant/index.ts`、`frontend/src/api/tenant/members.ts`、`frontend/src/api/tenant/invitations.ts`
- Modify: `frontend/src/api/system/index.ts`（`getParserEngineConfig` 与 `updateParserEngineConfig` 改 config 段；`getPromptTemplates` 改 `/prompt-templates`）

**Interfaces:**
- Consumes: 计划 01 Task 12 的 `createApiClient`、`ApiError`、`paths`
- Produces: 统一传输层；tenant 与 config 段函数指向新路径

- [ ] **Step 1** `npm run type-check` 预期失败，`@ragagent/api-client` 未声明。
- [ ] **Step 2** 加依赖与 alias，`npm install`。`request.ts` 用 `createApiClient({baseUrl, getToken, getLanguage})`，把 `ApiError` 转成旧调用方期望的 `Error` 消息。
- [ ] **Step 3** 按 1.12 表切 tenant 行与三个 config 函数，保持导出名与返回形状。`getStorageEngineConfig` 在本任务暂留旧实现，Task 18 随 storage 一起切。
- [ ] **Step 4** `npm run type-check` 通过；`grep -rn "api/v1/tenants" frontend/src/api/tenant frontend/src/api/system` 无输出。
- [ ] **Step 5** 提交：`git add frontend/package.json frontend/package-lock.json frontend/vite.config.ts frontend/src/utils/request.ts frontend/src/api/tenant frontend/src/api/system/index.ts`，`git commit -m "feat(frontend): 接入 api-client 并切换租户 API 层"`。

### Task 6: tenant 界面转绿与黄金样板登记

**Files:**
- Create: `docs/migration/golden-sample.md`
- Modify: `docs/migration/progress.md`

**Interfaces:**
- Consumes: 计划 01 的 `tests/e2e`、新栈可启动（8080 与 5173）
- Produces: `[tenant]` 用例全绿；黄金样板路径清单

- [ ] **Step 1** 起新栈与前端，跑 `cd tests/e2e && npx playwright test --grep "\[tenant\]"`，三条用例的失败项逐条判断是断言口径还是实现差异；口径差异登记 `deviations.md`，不改组件。
- [ ] **Step 2** 写 `golden-sample.md`：租户域 Controller、Service、Repository、Assembler、VO、测试的绝对路径与一句说明；配三条写法标准（方法 ≤ 40 行、构造器注入、VO 用 record）在本域的具体例子。
- [ ] **Step 3** 跑 `MVN verify`，预期 BUILD SUCCESS，含 Modulith、ArchUnit、Checkstyle。
- [ ] **Step 4** 提交：`git add docs/migration/golden-sample.md docs/migration/progress.md`，`git commit -m "docs(migration): tenant 域作为黄金样板"`。
- [ ] **Step 5** 在 `progress.md` 追加 tenant 批次完成行与三个 e2e 用例结果；后续各域以 `golden-sample.md` 为写法参照，偏离写 `deviations.md`。

## 批次 B：auth

### Task 7: auth DDL、持久层与当前主体

**Files:**
- Create: `DB/V<D>_02__DDL_rebuild_auth_create_tables_jiangguifan.sql`
- Create: `M/auth/entity/{User,OpenApiKey,OpenApiKeyPrincipalConfig,PlatformApiKey}.java`
- Create: `M/auth/mapper/{UserMapper,OpenApiKeyMapper,OpenApiKeyPrincipalConfigMapper,PlatformApiKeyMapper}.java`
- Create: `R/mapper/auth/{UserMapper,OpenApiKeyMapper,PlatformApiKeyMapper}.xml`
- Create: `M/auth/repository/{UserRepository,OpenApiKeyRepository,PlatformApiKeyRepository}.java`
- Create: `M/auth/service/{CurrentPrincipalImpl,PrincipalHolder}.java`
- Test: `T/auth/{AuthSchemaTest,UserRepositoryTest,OpenApiKeyRepositoryTest,CurrentPrincipalTest}.java`

**Interfaces:**
- Produces: `users`、`open_api_keys`、`open_api_key_principal_config`、`platform_api_keys` 四张表；`UserRepository.byUsernameAcrossTenants(String)`、`byIdAcrossTenants(long)`（均 `@IgnoreTenant`）；`OpenApiKeyRepository.byHashAcrossTenants(String)`（`@IgnoreTenant`）；`PrincipalHolder` 从 Sa-Token 会话构造 `Principal`

- [ ] **Step 1** `AuthSchemaTest`：`tenant2CannotReadTenant1OpenKeys`、`tenant2CannotInsertKeyForTenant1`、`duplicateLiveUsernameRejected`、`recreateUsernameAfterSoftDeleteAccepted`、`keyHashUnique`、`principalConfigTenantUnique`、`usersAndPlatformKeysHaveNoTenantColumn`。`UserRepositoryTest.findByUsernameIgnoresTenantFilter`、`duplicateEmailRejected`。`OpenApiKeyRepositoryTest.hashLookupCrossesTenants`、`ciphertextColumnDiffersFromPlaintext`。`CurrentPrincipalTest.returnsUserPrincipalWithTenant`、`visitorPrincipalHasNoUserId`、`openPrincipalHasKeyId`、`unauthenticatedThrowsNologin`。
- [ ] **Step 2** 跑红。
- [ ] **Step 3** 实现：`users` 唯一 `(username, deleted_at)` 与 `(email, deleted_at)`，无 `tenant_id`；`open_api_keys` 带 `tenant_id` 与行级安全；`platform_api_keys` 无租户列、进 `ignore-tables`；密文列标 `@Encrypted`；`PrincipalHolder` 依赖 `SaTokenAccountProvider` 与 `TenantContextHolder`，不直连 `StpLogic`。
- [ ] **Step 4** 跑绿；反向验证关闭 `open_api_keys` 行级安全后用例变红。
- [ ] **Step 5** 提交：`git add DB/<脚本> M/auth/entity M/auth/mapper M/auth/repository M/auth/service/CurrentPrincipalImpl.java M/auth/service/PrincipalHolder.java R/mapper/auth T/auth/AuthSchemaTest.java T/auth/UserRepositoryTest.java T/auth/OpenApiKeyRepositoryTest.java T/auth/CurrentPrincipalTest.java`，`git commit -m "feat(auth): 用户与 Key 表、持久层、当前主体"`。

### Task 8: auth api、错误码、权限点与 StpInterface

**Files:**
- Create: `M/auth/api/{CurrentPrincipal,UserQueryApi,RoleApi,UserPreferenceApi}.java`
- Create: `M/auth/api/{AuthCode,Principal,PrincipalType,UserBo,PermissionCatalog}.java`
- Create: `M/auth/config/{AuthCodeRegistration,RolePermissionProvider}.java`
- Create: `M/auth/service/{UserQueryApiImpl,RoleApiImpl,UserPreferenceApiImpl}.java`
- Modify: `R/i18n/app-messages*.properties`（12 个 auth key）
- Test: `T/auth/{AuthCodeMessagesTest,PermissionCatalogTest,RolePermissionProviderTest}.java`

**Interfaces:**
- Produces: “对外承诺”里 auth 的四个 api；`PermissionCatalog` 全部权限串（`domain:resource:action`）；`RolePermissionProvider implements StpInterface`

- [ ] **Step 1** `AuthCodeMessagesTest` 七语种全量；`PermissionCatalogTest.everyPermissionMatchesNamingPattern`、`everyRoleHasAtLeastReadPermission`、`platformPermissionsReservedForSystemAdmin`；`RolePermissionProviderTest.viewerGetsReadOnly`、`adminGetsManageWrite`、`openFullAccessGetsAllOpenPermissions`、`scopedKeyGetsOnlyMappedCapabilities`。
- [ ] **Step 2** 跑红。
- [ ] **Step 3** 实现：角色派生 `VIEWER` 给全部 `:read`，`CONTRIBUTOR` 加内容写，`ADMIN` 加管理写，`OWNER` 全量，`systemAdmin` 额外给 `platform:*`；open Key 的 `fullAccess` 给全部 `open:*`，scoped 按 `APIKeyCapability` 映射。无租户上下文时返回空表。
- [ ] **Step 4** 跑绿。
- [ ] **Step 5** 提交：`git add M/auth/api M/auth/config M/auth/service/UserQueryApiImpl.java M/auth/service/RoleApiImpl.java M/auth/service/UserPreferenceApiImpl.java R/i18n T/auth/AuthCodeMessagesTest.java T/auth/PermissionCatalogTest.java T/auth/RolePermissionProviderTest.java`，`git commit -m "feat(auth): api、错误码、权限目录与 StpInterface"`。

### Task 9: auth 会话、OIDC 与 API Key 用例

**Files:**
- Create: `M/auth/service/{SessionUsecase,TokenIssuer,UserUsecase,UserCredentialService,OidcUsecase,OidcStateCodec,OidcNonceCookie,OidcDiscoveryClient}.java`
- Create: `M/auth/service/{OpenApiKeyUsecase,OpenApiKeyValidator,OpenApiKeyProvisioner,OpenPrincipalConfigUsecase,PlatformApiKeyUsecase}.java`
- Create: `M/auth/service/{OpenKeyAuthenticator,HeaderApiKeyResolver,SignedTokenApiKeyResolver,DirectHeaderApiKeyResolver}.java`
- Create: `M/auth/api/{OpenKeyResolver,OpenKeySource}.java`
- Test: `T/auth/{SessionUsecaseTest,OpenApiKeyUsecaseTest,OpenKeyResolverTest,OidcUsecaseTest}.java`

**Interfaces:**
- Consumes: Task 8 的 api；framework `SaTokenAccountProvider.bindTenant`；`system.api.SystemSettingApi`；计划 01 Task 17 的 Nimbus 结论
- Produces: `SessionUsecase.login/LoginResult`、`logout`、`switchTenant(long)`；`OpenApiKeyUsecase.create/update/revoke/validate`；`OpenKeyAuthenticator.authenticate(HttpServletRequest)`；`OidcUsecase.authorizationUrl(String)`、`exchange(String, String)`。`OpenKeyResolver` 是真实多实现点：4 个实现按 `api_principal_config.mode` 由 `Map<OpenKeySource, OpenKeyResolver>` 选，新增签名算法只加一个类

- [ ] **Step 1** `SessionUsecaseTest.loginBindsTenantIntoTokenSession`、`badPasswordReturnsAuthBadCredentials`、`switchTenantRotatesTokenAndDropsOld`、`switchToNonMemberTenantForbidden`、`logoutInvalidatesSession`；`OpenApiKeyUsecaseTest.fullAccessCannotCarryCapabilities`、`scopedKeyWithForeignKbRejected`、`createReturnsPlaintextOnceOnly`、`revokedKeyCannotAuthenticate`；`OpenKeyResolverTest.headerKeyResolvesOpenPrincipal`、`signedTokenModeRequiresValidSignature`、`directHeaderModeRequiresTrustedProxy`、`unknownModeFallsBackToHeader`；`OidcUsecaseTest.stateIsHmacSignedAndExpiresInTenMinutes`、`reusedStateRejected`。
- [ ] **Step 2** 跑红。
- [ ] **Step 3** 实现：登录成功后 `TokenIssuer` 调 `bindTenant("admin", token, tenantId)`；`switchTenant` 先 logout 再 login，`TenantSwitchPolicy.decide` 拒绝时抛 `TENANT_SWITCH_FORBIDDEN`；OIDC 用 Nimbus `OIDCProviderMetadata`、`AuthorizationRequest`、`AuthorizationCodeGrant`，state 沿用 HMAC 签名，出网经 `GuardedHttpSender` 接 `SsrfGuard`；Key 明文只在 create 响应返回一次。
- [ ] **Step 4** 跑绿。
- [ ] **Step 5** 提交：`git add M/auth/service M/auth/api/OpenKeyResolver.java M/auth/api/OpenKeySource.java T/auth/SessionUsecaseTest.java T/auth/OpenApiKeyUsecaseTest.java T/auth/OpenKeyResolverTest.java T/auth/OidcUsecaseTest.java`，`git commit -m "feat(auth): 会话、OIDC 与 API Key 用例"`。

### Task 10: auth Controller、过滤器与路由规则

**Files:**
- Create: `M/auth/controller/{SessionController,ProfileController,RegistrationController,OnboardingController,OidcController,OpenApiKeyController,PlatformApiKeyController}.java`
- Create: `M/auth/vo/{LoginVo,LoginRespVo,SessionTenantVo,ProfileVo,PreferenceVo,ChangePasswordVo,RegisterVo,RegisterByInviteVo,InvitationLookupVo,OidcConfigRespVo,OidcUrlRespVo,OpenApiKeyCreateVo,OpenApiKeyUpdateVo,OpenApiKeyRespVo,PrincipalConfigVo,PlatformApiKeyRespVo}.java`
- Create: `M/auth/assembler/{LoginAssembler,OpenApiKeyAssembler,UserAssembler}.java`
- Create: `M/auth/filter/OpenKeyAuthenticationFilter.java`
- Create: `M/auth/config/AuthRouteRules.java`
- Create: `M/auth/service/TenantScopeGuard.java`（平台 Key 调租户面时按请求体或路径的 `tenantId` 校验来源是平台主体，否则抛 `TENANT_SWITCH_FORBIDDEN`）
- Modify: `backend/app/src/main/resources/application.yml`（`permit-paths` 追加登录、注册、OIDC、MCP 回调）
- Test: `T/auth/{SessionControllerTest,OpenApiKeyControllerTest,AuthRouteRulesTest,OpenRouteAnnotationTest,TenantScopeGuardTest}.java`

**Interfaces:**
- Consumes: Task 9 用例；framework `SaRouteRules`、`SaInterceptor`
- Produces: 1.4 表里的 auth 路径；`OpenKeyAuthenticationFilter` 在 `SaInterceptor` 之前解析 `X-API-Key` 等头并建立 open 会话；`AuthRouteRules` 把 1.14 的翻译表写成 `SaRouter` 规则

- [ ] **Step 1** `SessionControllerTest.loginReturnsTokenAndTenant`、`loginWrongPasswordReturns4402`、`switchTenantReturnsNewToken`、`meReturnsProfile`、`changePasswordRejectsSamePassword`；`OpenApiKeyControllerTest.createReturnsPlaintextOnce`、`listOmitsSecret`、`updateUnknownFieldRejected`；`AuthRouteRulesTest.adminPrefixRequiresAdmin`、`visitorPrefixRejectsAdminToken`、`openPrefixRequiresOpenToken`、`unmatchedPathFailClosed`、`permitPathsOpenWithoutToken`；`TenantScopeGuardTest.platformKeyMayTargetAnyTenant`、`tenantKeyCannotTargetOtherTenant`；`OpenRouteAnnotationTest.everyOpenRouteIsGuardedOrPermitted`。
- [ ] **Step 2** 跑红。
- [ ] **Step 3** 实现：路径与注解按 1.4 与 1.14。过滤器只在无 `Authorization` 头时生效，解析成功后调 `SaManager.getStpLogic("open").login(id)` 并绑定租户。
- [ ] **Step 4** 跑绿；`MVN test -Dtest=OpenApiExportTest` 通过。
- [ ] **Step 5** 提交：`git add M/auth/controller M/auth/vo M/auth/assembler M/auth/filter M/auth/service/TenantScopeGuard.java M/auth/config/AuthRouteRules.java backend/app/src/main/resources/application.yml T/auth/SessionControllerTest.java T/auth/OpenApiKeyControllerTest.java T/auth/AuthRouteRulesTest.java T/auth/OpenRouteAnnotationTest.java T/auth/TenantScopeGuardTest.java`，`git commit -m "feat(auth): 控制器、Key 认证过滤器与路由规则"`。

### Task 11: auth 前端与登录冒烟

**Files:**
- Modify: `frontend/src/api/auth/index.ts`、`frontend/src/api/system/index.ts`（平台 Key 与用户管理）、`frontend/src/utils/tenantSwitch.ts`
- Modify: `tests/api-smoke/tests/auth.spec.ts`（去掉 `test.fixme`）
- Create: `docs/migration/permission-points.md`

**Interfaces:**
- Produces: 1.12 表里 auth 行全部切换；`[auth]` 四条 e2e 全绿；登录冒烟转绿；权限点对照表

- [ ] **Step 1** 按 1.12 切路径与字段；`validateToken` 改调 `/auth/me`；删 `refreshToken` 并清引用；`tenantSwitch.ts` 改为调 `POST /auth/session/tenant` 后存新 token 再整页刷新。
- [ ] **Step 2** 跑 `npm run type-check` 与 `npm run build-only`。跑 `cd tests/e2e && npx playwright test --grep "\[auth\]"`，失败项按口径差异或实现缺口分别处理。
- [ ] **Step 3** 写 `permission-points.md`：权限串、旧租户角色、旧 API-Key 能力三列对照。
- [ ] **Step 4** 跑 `npx playwright test tests/auth.spec.ts tests/skeleton-auth.spec.ts`，预期 2 passed；`grep -rn "api/v1/auth\|switch-tenant\|validateToken" frontend/src` 无旧路径。
- [ ] **Step 5** 提交：`git add frontend/src/api/auth/index.ts frontend/src/api/system/index.ts frontend/src/utils/tenantSwitch.ts tests/api-smoke/tests/auth.spec.ts docs/migration/permission-points.md`，`git commit -m "feat(auth): 前端切换与登录冒烟转绿"`。

## 批次 C：system

### Task 12: system 设置表、三层合并与副作用

**Files:**
- Create: `DB/V<D>_03__DDL_rebuild_system_create_tables_jiangguifan.sql`
- Create: `DB/V<D>_04__DML_rebuild_system_seed_settings_jiangguifan.sql`
- Create: `M/system/entity/SystemSetting.java`
- Create: `M/system/mapper/SystemSettingMapper.java`
- Create: `M/system/repository/SystemSettingRepository.java`
- Create: `M/system/api/{SystemSettingKey,SystemSettingApi,DeploymentApi,DeploymentCapabilitiesBo,SystemCode}.java`
- Create: `M/system/service/{SystemSettingService,SettingValueCodec,SettingSideEffectDispatcher,EffectiveSettings}.java`
- Create: `M/system/config/SystemCodeRegistration.java`
- Modify: `R/i18n/app-messages*.properties`（8 个 system key）
- Test: `T/system/{SystemSettingServiceTest,EffectiveSettingsTest,SystemSchemaTest,SystemCodeMessagesTest}.java`

**Interfaces:**
- Produces: `system_settings` 表（全局，进 `ignore-tables`）；`SystemSettingKey` 枚举（`key`、`defaultValue`、`valueType`、`category`、`requiresRestart`）；`SystemSettingService.list/get/update/reset`；`SettingValueCodec.decode`；`EffectiveSettings.resolve(SystemSettingKey, Supplier<TenantLayerValue>)`

- [ ] **Step 1** `SystemSchemaTest.settingsKeyUnique`、`settingsTableHasNoTenantColumn`、`settingsValueIsJsonb`；`SystemSettingServiceTest.dbRowWinsOverEnv`、`bootstrapDefaultRowFoldsBackToEnv`、`unknownKeyReturnsCode`、`invalidIntValueRejected`、`resetDeletesRow`、`updateDispatchesSsrfWhitelist`；`EffectiveSettingsTest.tenantValueWinsOverSystemSetting`、`systemSettingWinsOverYmlDefault`、`blankStringTreatedAsUnset`、`allLayersEmptyReturnsCodeDefault`；`SystemCodeMessagesTest` 七语种。
- [ ] **Step 2** 跑红。
- [ ] **Step 3** 实现：`value jsonb NOT NULL`、`last_modified_by bigint`，唯一 `(key)`；种子 DML 写 16 个 registry key 的默认行。三层顺序 yml 默认值、`system_settings`、租户配置，空串与缺失 json 都视为未设置。副作用只保留 `SSRF_WHITELIST` 与 `MODEL_MAX_CONCURRENCY` 两个。
- [ ] **Step 4** 跑绿；`EffectiveSettingsTest` 的四条用例即 Review Focus 第 3 条。
- [ ] **Step 5** 提交：`git add DB/<两个脚本> M/system R/i18n T/system/SystemSettingServiceTest.java T/system/EffectiveSettingsTest.java T/system/SystemSchemaTest.java T/system/SystemCodeMessagesTest.java`，`git commit -m "feat(system): 设置表、三层合并与副作用"`。

### Task 13: system 版本信息、平台运维与 initialization 探测

**Files:**
- Create: `M/system/service/{SystemInfoService,DeploymentCapabilities,ParserEngineCatalog,PlatformUserUsecase,RuntimeQueueViewRepository,ModelProbeService,ExtractionProbeService,ProbeRequestBinder,ProbeMediaPreparer}.java`
- Create: `M/system/controller/{SystemController,PlatformSettingController,PlatformUserController,RuntimeQueueController,ProbeController,PromptTemplateController}.java`
- Create: `M/system/adapter/{ProbeHttpSender,GuardedProbeSender}.java`
- Create: `M/system/vo/{SystemInfoRespVo,DeploymentCapabilitiesRespVo,ParserEngineRespVo,SettingRespVo,SettingUpdateVo,PlatformUserRespVo,PlatformUserCreateVo,PasswordResetVo,SystemAdminToggleVo,RuntimeQueueRespVo,RuntimeTaskRespVo,TaskActionVo,ModelProbeVo,ModelProbeResultVo,ExtractionProbeVo,ExtractionProbeResultVo,PromptTemplateRespVo}.java`
- Create: `M/system/assembler/{SystemInfoAssembler,SettingAssembler,PlatformUserAssembler}.java`
- Create: `M/system/service/PromptTemplateCatalog.java` 与 `R/prompt-templates.yaml`（从旧 `config.yaml` 的 prompt templates 段取内容，目录与模板正文照搬）
- Create: `M/system/service/SsrfGuardConfig.java`（`@Configuration`，`@Bean SsrfGuard ssrfGuard(SystemSettingApi settings)`，白名单随 `listValue(SSRF_WHITELIST)` 初始化，`reloadWhitelist` 由 Task 12 的 `SettingSideEffectDispatcher` 触发；`platform.security.SsrfGuard` 源码由计划 02 的引擎迁入一并移到 `framework.security`）
- Test: `T/system/{SystemControllerTest,PlatformUserControllerTest,SystemSettingControllerTest,ModelProbeServiceTest,ExtractionProbeServiceTest,ProbeControllerTest,PromptTemplateControllerTest}.java`

**Interfaces:**
- Consumes: `auth.api.UserQueryApi`、`RoleApi`；`audit.api.AuditApi`；model-runtime 的 `LlmChatClient`、`EmbeddingPort`；framework 的 `SsrfGuard` 类
- Produces: 1.4 表里的 system 路径；`GET /api/admin/v1/prompt-templates`（按 `Accept-Language` 渲染模板，替代旧 `GET /tenants/kv/prompt-templates`）；`POST /system/model-probes`（kind 取 `CHAT`、`EMBEDDING`、`RERANK`、`ASR`、`MULTIMODAL`）；`POST /system/extraction-probes`（kind 取 `TEXT_RELATION`、`FABRI_TAG`、`FABRI_TEXT`）；`SsrfGuard` Spring Bean，供计划 02、05、06 注入

- [ ] **Step 1** `SystemControllerTest.infoReturnsEnginesAndVersion`、`parserEnginesReflectTenantOverrides`、`reconnectDocReaderReturnsUnavailableCode`；`PlatformUserControllerTest.promoteAndRevokeAudited`、`resetOwnPasswordRejected`、`createUserReturnsStringId`；`SystemSettingControllerTest.updateInvalidValueReturns400WithCode`；`ModelProbeServiceTest.chatProbeRecordsLatencyAndError`、`missingNameOrUrlReturnsProbeInputCode`、`multimodalRejectsNonImage`、`multimodalRejectsOversize`、`probeEndpointPassesSsrfGuard`；`ExtractionProbeServiceTest.emptyTextRejected`、`textOverFiveThousandRejected`、`missingTagsRejected`；`ProbeControllerTest.kindDispatchesToService`；`PromptTemplateControllerTest.rendersByAcceptLanguage`、`unknownLanguageFallsBackToZhCn`。
- [ ] **Step 2** 跑红。
- [ ] **Step 3** 实现：`SystemInfoService` 从 `BuildProperties` 与 `DataSource` 取版本；`RuntimeQueueViewRepository` 读 JobRunr 表，队列名未知返回 `SYSTEM_TASK_QUEUE_UNKNOWN`；探测方法标 `@Transactional(propagation = NOT_SUPPORTED)`，出网经 `GuardedProbeSender`。`PromptTemplateCatalog` 启动时读 classpath YAML，`PromptTemplateController` 按请求语言渲染，语言回落顺序沿用旧 `WikiLanguageSupport.localeFromRequest` 的语义（`Accept-Language` 首 tag 到 zh-CN）。
- [ ] **Step 4** 跑绿；`OpenApiExportTest` 通过。
- [ ] **Step 5** 提交：`git add M/system/service M/system/controller M/system/adapter M/system/vo M/system/assembler R/prompt-templates.yaml T/system/SystemControllerTest.java T/system/PlatformUserControllerTest.java T/system/SystemSettingControllerTest.java T/system/ModelProbeServiceTest.java T/system/ExtractionProbeServiceTest.java T/system/ProbeControllerTest.java T/system/PromptTemplateControllerTest.java`，`git commit -m "feat(system): 版本信息、平台运维与探测"`。

### Task 14: system 前端与三页界面转绿

**Files:**
- Modify: `frontend/src/api/system/index.ts`、`frontend/src/api/initialization/index.ts`（探测与 Ollama）

**Interfaces:**
- Produces: `[system]` 五条 e2e 全绿

- [ ] **Step 1** `npm run type-check` 预期失败，探测函数仍引旧路径。
- [ ] **Step 2** 按 1.12 切 system 行；四个探测函数共用一个内部 `probe(kind, payload)`，保持导出名与参数形状；Ollama 函数指向 model 新路径。
- [ ] **Step 3** 跑 `npm run type-check`、`npx playwright test --grep "\[system\]"`。
- [ ] **Step 4** `grep -rn "system/admin\|initialization/" frontend/src/api` 无旧路径。
- [ ] **Step 5** 提交：`git add frontend/src/api/system/index.ts frontend/src/api/initialization/index.ts`，`git commit -m "feat(frontend): 切换系统设置 API 层"`。

## 批次 D：audit

### Task 15: audit 表、持久层、api、写入与查询

**Files:**
- Create: `DB/V<D>_05__DDL_rebuild_audit_create_tables_jiangguifan.sql`
- Create: `M/audit/entity/{AuditLog,AuditDeniedDedup}.java`
- Create: `M/audit/mapper/{AuditLogMapper,AuditDeniedDedupMapper}.java`
- Create: `R/mapper/audit/{AuditLogMapper,AuditDeniedDedupMapper}.xml`
- Create: `M/audit/repository/AuditLogRepository.java`
- Create: `M/audit/api/{AuditApi,AuditEntryBo,AuditDeniedBo,AuditAction,AuditOutcome,AuditCode}.java`
- Create: `M/audit/service/{AuditLogUsecase,AuditRetentionJob}.java`
- Create: `M/audit/controller/{TenantAuditLogController,PlatformAuditLogController}.java`
- Create: `M/audit/vo/{AuditLogQueryVo,AuditLogRespVo,AuditLogPageVo}.java`
- Create: `M/audit/assembler/AuditLogAssembler.java`
- Create: `M/audit/config/AuditCodeRegistration.java`
- Modify: `R/i18n/app-messages*.properties`（2 个 audit key）
- Modify: `frontend/src/api/tenant/audit-log.ts`、`frontend/src/api/system/index.ts`（审计部分）
- Test: `T/audit/{AuditSchemaTest,AuditLogUsecaseTest,AuditRetentionJobTest,AuditLogControllerTest,AuditCodeMessagesTest}.java`

**Interfaces:**
- Produces: `audit_logs`、`audit_denied_dedup` 两表；`AuditApi.record`、`recordAccessDenied`；`AuditRetentionJob.purge(int)`（`@IgnoreTenant`）；`GET /tenants/{id}/audit-logs`、`GET /system/audit-logs`

- [ ] **Step 1** `AuditSchemaTest.tenant2CannotReadTenant1Rows`、`tenantIdIndexForRecentQuery`；`AuditLogUsecaseTest.deniedWithinOneMinuteDeduped`、`deniedAfterWindowRecorded`、`emptyActionRejectedWithCode`；`AuditRetentionJobTest.purgesOlderThanCutoffAcrossTenants`、`secondRunIsIdempotent`；`AuditLogControllerTest.filtersByActionAndTime`、`cursorOutOfRangeReturnsQueryInvalid`、`platformStreamReturnsTenantZeroRows`、`tenantStreamRejectsOtherTenant`；`AuditCodeMessagesTest` 七语种。
- [ ] **Step 2** 跑红。
- [ ] **Step 3** 实现：去重查 `audit_denied_dedup` 唯一四列，`last_seen_at` 在一分钟内则跳过；保留期默认 90 天，取 `SystemSettingKey.AUDIT_RETENTION_DAYS`；`purge` 经 `TenantAwareJdbcTemplate` 显式设 `'system'`；平台流读 `tenant_id=0`。前端两个函数把 `PageData` 转回 `{items,nextCursor}`。
- [ ] **Step 4** 跑绿；`npx playwright test --grep "\[audit\]"`；`OpenApiExportTest` 通过。
- [ ] **Step 5** 提交：`git add DB/<脚本> M/audit R/mapper/audit R/i18n T/audit frontend/src/api/tenant/audit-log.ts frontend/src/api/system/index.ts`，`git commit -m "feat(audit): 审计表、写入、保留期与查询"`。

## 批次 E：storage

### Task 16: storage DDL 与持久层

**Files:**
- Create: `DB/V<D>_06__DDL_rebuild_storage_create_tables_jiangguifan.sql`
- Create: `M/storage/entity/{StorageBackend,StorageResource,StorageResourceBinding,StorageAccessGrant}.java`
- Create: `M/storage/mapper/{StorageBackendMapper,StorageResourceMapper,StorageAccessGrantMapper}.java`
- Create: `R/mapper/storage/{StorageBackendMapper,StorageResourceMapper,StorageAccessGrantMapper}.xml`
- Create: `M/storage/repository/{StorageBackendRepository,ResourceRepository,ResourceGrantRepository}.java`
- Test: `T/storage/{StorageSchemaTest,StorageBackendRepositoryTest,ResourceRepositoryTest}.java`

**Interfaces:**
- Produces: 1.5 的四张 storage 表；`ResourceRepository.register`、`bind`、`markDeleted`、`byHandle(String)`、`purgeExpiredGrants(int)`（`@IgnoreTenant`）；`ResourceGrantRepository.put/consume`

- [ ] **Step 1** `StorageSchemaTest` 四条 `tenant2CannotReadTenant1_<表>` 与四条插入拒绝；`resourceHandleUnique`、`bucketNameUniquePerProviderAcrossTenants`；`StorageBackendRepositoryTest.listByTenantOrderedByName`、`softDeleteHidesFromList`；`ResourceRepositoryTest.handleLookupReturnsLiveRow`、`grantConsumeIsSingleUse`、`purgeExpiredGrantsCrossesTenants`。
- [ ] **Step 2** 跑红。
- [ ] **Step 3** 实现：`storage_access_grants` 带 `tenant_id` 与行级安全；`handle` 用 22 位 base64url；桶唯一用 `(provider, bucket, deleted_at)` 部分索引；凭据列 `credential_ciphertext jsonb` 标 `@Encrypted`。
- [ ] **Step 4** 跑绿；反向验证关掉行级安全后对应用例变红。
- [ ] **Step 5** 提交：`git add DB/<脚本> M/storage/entity M/storage/mapper M/storage/repository R/mapper/storage T/storage/StorageSchemaTest.java T/storage/StorageBackendRepositoryTest.java T/storage/ResourceRepositoryTest.java`，`git commit -m "feat(storage): 后端、资源与授权表"`。

### Task 17: storage 适配器、api 与用例

**Files:**
- Create: `M/storage/api/{StorageBackendQueryApi,FileStorageApi,StorageReferenceProvider,ResourceReferenceProvider}.java`
- Create: `M/storage/api/{StorageCode,StorageBackendBo,ProviderType,FileWriteBo,ResourceReferenceBo,StorageConfigInputBo}.java`
- Create: `M/storage/service/{StorageBackendUsecase,StorageProbeService,StorageConfigValidator,ResourceFileService,ResourceGrantService}.java`
- Create: `M/storage/adapter/{StorageBackendAdapter,S3CompatibleBackendAdapter,LocalDirectoryBackendAdapter,LocalOssTemplate,StorageProviderTypes,ObjectKeyGuard}.java`
- Create: `M/storage/config/{StorageCodeRegistration,StorageAssemblyConfig}.java`
- Modify: `R/i18n/app-messages*.properties`（9 个 storage key）
- Test: `T/storage/{StorageBackendUsecaseTest,StorageBackendAdapterTest,StorageCodeMessagesTest,StorageProviderTypesTest}.java`

**Interfaces:**
- Consumes: framework `TenantClientRegistry`、`TenantConfigSource<StorageBackendConfig>`、`OssTemplate`、`S3OssTemplate`、`SsrfGuard`、`tenant.api.TenantStorageApi`
- Produces: “对外承诺”里 storage 的四个接口；`StorageBackendAdapter` 两个实现覆盖 8 个 provider；`LocalOssTemplate`（framework 只做 S3）；`StorageReferenceProvider`、`ResourceReferenceProvider` 只返回空表并注明实现方在计划 04

**为什么是真实多实现点：** 旧 9 个 provider key 中 7 个是 S3 协议族，另 1 个是本地目录；将来加 Azure Blob 或七牛只加一个 Adapter。接口方法固定为 `provider()`、`create(StorageBackendConfig)`、`probe(OssTemplate)`。

- [ ] **Step 1** `StorageBackendUsecaseTest.createRunsProbeBeforePersist`、`duplicateBucketAcrossTenantsRejected`、`deleteBlockedWhenReferenced`、`setDefaultCallsTenantStorageApi`、`credentialsStoredEncrypted`、`probeFailureRollsBack`；`StorageBackendAdapterTest.sevenKeysMapToS3Adapter`、`localMapsToDirectoryAdapter`、`unknownProviderRejected`、`pathTraversalRejected`；`StorageProviderTypesTest.eightExposedProvidersInOrder`；`StorageCodeMessagesTest` 七语种。
- [ ] **Step 2** 跑红。
- [ ] **Step 3** 实现：`StorageAssemblyConfig` 声明 `TenantClientRegistry<StorageBackendConfig, OssTemplate>` Bean，`closer` 调 `AutoCloseable`；删除前聚合 Provider，非空抛 `STORAGE_BACKEND_IN_USE` 并带 details；探测不在事务内。
- [ ] **Step 4** 跑绿。
- [ ] **Step 5** 提交：`git add M/storage/api M/storage/service M/storage/adapter M/storage/config R/i18n T/storage/StorageBackendUsecaseTest.java T/storage/StorageBackendAdapterTest.java T/storage/StorageCodeMessagesTest.java T/storage/StorageProviderTypesTest.java`，`git commit -m "feat(storage): 适配器、存储 api 与用例"`。

### Task 18: storage 控制器、文件代理与前端转绿

**Files:**
- Create: `M/storage/controller/{StorageBackendController,FileProxyController,ResourceGrantController}.java`
- Create: `M/storage/vo/{StorageBackendCreateVo,StorageBackendUpdateVo,StorageBackendRespVo,ProviderTypeRespVo,StorageProbeVo,SignedUrlRespVo}.java`
- Create: `M/storage/assembler/StorageBackendAssembler.java`
- Modify: `frontend/src/api/storage-backend.ts`、`frontend/src/api/system/index.ts`（`getStorageEngineConfig` 改为读 `GET /storage-backends` 取默认行）、`frontend/src/utils/protectedFileAccess.ts`
- Test: `T/storage/{StorageBackendControllerTest,FileProxyControllerTest}.java`

**Interfaces:**
- Produces: 1.4 表里的 storage 路径；`GET /api/v1/files/{handle}`、`GET /api/admin/v1/files/{handle}/signed-url`、`GET /api/v1/resources/{grantToken}`

- [ ] **Step 1** `StorageBackendControllerTest.createValidatesAndProbes`、`listOmitsCredentials`、`setDefaultAffectsTenantStorageApi`；`FileProxyControllerTest.handleServesBytesWithContentType`、`unknownHandleReturns404WithCode`、`grantTokenSingleUse`、`expiredGrantRejected`。
- [ ] **Step 2** 跑红。
- [ ] **Step 3** 实现：文件代理按 `handle` 查行，经 `TenantClientRegistry` 取模板读流，响应写 `Content-Type`、`Content-Length`、`Content-Disposition`，不整块加载。令牌一次性消费。
- [ ] **Step 4** 跑绿；`npx playwright test --grep "\[storage\]"`；`OpenApiExportTest` 通过。
- [ ] **Step 5** 提交：`git add M/storage/controller M/storage/vo M/storage/assembler T/storage/StorageBackendControllerTest.java T/storage/FileProxyControllerTest.java frontend/src/api/storage-backend.ts frontend/src/utils/protectedFileAccess.ts`，`git commit -m "feat(storage): 控制器、文件代理与前端切换"`。

## 批次 F：model

### Task 19: model 表、持久层、api 与用例

**Files:**
- Create: `DB/V<D>_07__DDL_rebuild_model_create_tables_jiangguifan.sql`
- Create: `M/model/entity/{Model,OllamaDownloadTask}.java`
- Create: `M/model/mapper/{ModelMapper,OllamaDownloadTaskMapper}.java`
- Create: `R/mapper/model/{ModelMapper,OllamaDownloadTaskMapper}.xml`
- Create: `M/model/repository/{ModelRepository,OllamaDownloadTaskRepository,ModelUsageQueryRepository}.java`
- Create: `M/model/api/{ModelQueryApi,ModelReferenceProvider,ModelCode,ModelBo,ModelType,ModelRuntimeSpecBo}.java`（`ResourceReferenceBo` 复用 `storage.api`，不在本域重复定义）
- Create: `M/model/service/{ModelUsecase,ModelCredentialUsecase,BuiltinModelSync,ModelDeletionGuard,ModelReferenceAggregator,ModelCatalogReaderImpl,ModelFactsReaderImpl,ModelCredentialReaderImpl}.java`
- Create: `M/model/config/{ModelCodeRegistration,ModelAssemblyConfig}.java`
- Modify: `R/i18n/app-messages*.properties`（8 个 model key）
- Test: `T/model/{ModelSchemaTest,ModelRepositoryTest,ModelUsecaseTest,ModelReferenceAggregatorTest,ModelCodeMessagesTest,ModelCatalogReaderImplTest,ModelFactsReaderImplTest,ModelCredentialReaderImplTest}.java`

**Interfaces:**
- Consumes: 计划 02 的 `modelruntime.spi.ModelCatalogReader`、`ModelFactsReader`、`ModelCredentialReader`，以及 `modelruntime.api.ModelRow`、`ModelFacts`
- Produces: `models`、`ollama_download_tasks` 两表；`ModelQueryApi`；`ModelReferenceProvider` 声明；`ModelReferenceAggregator` 注入 `ObjectProvider<List<ModelReferenceProvider>>`，删除前聚合，非空抛 `MODEL_IN_USE`；`BuiltinModelSync.sync()` 供 JobRunr 作业调用；三个 spi 适配类放 `M/model/service` 内部包，不放 `api`，签名逐字照抄计划 02：`ModelCatalogReader` 的 `Optional<ModelRow> findOrEmpty(String modelId)`、`ModelRow requireVisible(long tenantId, String modelId)`、`ModelRow requireRow(long tenantId, String modelId)`，`ModelFactsReader.find(String modelId)`，`ModelCredentialReader` 的 `String apiKey(long modelId)`、`String appSecret(long modelId)`

- [ ] **Step 1** `ModelSchemaTest.tenant2CannotReadTenant1Models`、`liveDuplicateNameInTypeBucketRejected`、`builtinRowVisibleToAllTenants`；`ModelRepositoryTest.visibleIncludesBuiltinAndOwnRows`、`clearDefaultLeavesOneDefaultPerBucket`、`softDeletedRowHiddenButReadableByIdIncludingDeleted`；`ModelUsecaseTest.createValidatesConfigStructure`、`onlySystemAdminUpdatesBuiltin`、`builtinCannotBeDeleted`、`debugRejectsAbnormalStatus`；`ModelReferenceAggregatorTest.emptyProvidersAllowDelete`、`oneProviderReportsReferences`、`multipleProvidersAggregatedInDetail`；`ModelCatalogReaderImplTest.findOrEmptyReturnsEmptyForUnknownId`、`requireRowRejectsCrossTenantId`、`requireVisibleAcceptsBuiltinRow`；`ModelFactsReaderImplTest.missingRowRaisesModelNotFound`、`factsCarryBaseUrlAndName`；`ModelCredentialReaderImplTest.apiKeyIsDecrypted`、`appSecretAbsentReturnsEmpty`；`ModelCodeMessagesTest` 七语种。
- [ ] **Step 2** 跑红。
- [ ] **Step 3** 实现：内置行 `tenant_id=0`、`managed_by='yaml'`，可见性由 Repository 的 `OR managed_by='yaml'` 表达；`credential_ciphertext jsonb` 标 `@Encrypted`；`BuiltinModelSync` 按 `managed_by` 做 UPSERT 与软删，固定 jobId `builtin-models-sync`。三个 spi 适配类只做行到 `ModelRow`/`ModelFacts` 的装配与 `@Encrypted` 列解密，不加业务逻辑；`requireRow` 按 `(id, tenantId)` 精确取，不看内置回落，`requireVisible` 接受本租户行与内置行。
- [ ] **Step 4** 跑绿。
- [ ] **Step 5** 提交：`git add DB/<脚本> M/model R/mapper/model R/i18n T/model`，`git commit -m "feat(model): 表、api、引用 Provider 与用例"`。

### Task 20: model 控制器、调试、Ollama 与前端

**Files:**
- Create: `M/model/controller/{ModelController,ModelCredentialController,ModelDebugController,ModelOllamaController}.java`
- Create: `M/model/service/{ModelDebugRunner,ModelDebugRequestBinder,OllamaManageService}.java`
- Create: `M/model/vo/{ModelCreateVo,ModelUpdateVo,ModelRespVo,ModelCredentialVo,ModelDebugVo,ModelDebugResultVo,OllamaStatusVo,OllamaModelVo,OllamaDownloadVo}.java`
- Create: `M/model/assembler/ModelAssembler.java`
- Modify: `frontend/src/api/model/index.ts`、`frontend/src/api/initialization/index.ts`（Ollama 部分）
- Test: `T/model/{ModelControllerTest,ModelDebugControllerTest,ModelOllamaControllerTest}.java`

**Interfaces:**
- Produces: 1.4 表里的 model 路径；`POST /models/{id}/debug` 的 `kind` 分派

- [ ] **Step 1** `ModelControllerTest.createReturnsStringId`、`deleteReferencedReturns409WithCode`、`credentialsNeverInResponse`；`ModelDebugControllerTest.chatReturnsAnswer`、`embeddingReturnsVectorLength`、`kindMismatchReturnsTypeUnsupported`、`optionsOutOfRangeRejected`；`ModelOllamaControllerTest.statusWhenUnavailableReturnsConnectFailed`、`downloadCreatesTaskRow`、`progressReadsTaskRow`。
- [ ] **Step 2** 跑红。
- [ ] **Step 3** 实现：`ModelDebugRunner` 按 kind 分派到私有方法，各 ≤ 40 行，调 model-runtime 的 `LlmChatClient`、`EmbeddingPort`、`Reranker`；Ollama 下载任务写表，进度由独立虚拟线程更新行。
- [ ] **Step 4** 跑绿；`npx playwright test --grep "\[model\]"`；`OpenApiExportTest` 通过。
- [ ] **Step 5** 提交：`git add M/model/controller M/model/service M/model/vo M/model/assembler T/model/ModelControllerTest.java T/model/ModelDebugControllerTest.java T/model/ModelOllamaControllerTest.java frontend/src/api/model/index.ts frontend/src/api/initialization/index.ts`，`git commit -m "feat(model): 控制器、调试与 Ollama"`。

## 批次 G：vectorstore 与 websearch

### Task 21: vectorstore 表、api、三种引擎与前端下拉

**Files:**
- Create: `DB/V<D>_08__DDL_rebuild_vectorstore_create_tables_jiangguifan.sql`
- Create: `M/vectorstore/entity/VectorStore.java`
- Create: `M/vectorstore/mapper/VectorStoreMapper.java`
- Create: `R/mapper/vectorstore/VectorStoreMapper.xml`
- Create: `M/vectorstore/repository/VectorStoreRepository.java`
- Create: `M/vectorstore/api/{VectorStoreQueryApi,VectorStoreReferenceProvider,VectorStoreCode,VectorStoreBo,VectorStoreEngine,VectorStoreRuntimeConfigBo}.java`（`ResourceReferenceBo` 复用 `storage.api`）
- Create: `M/vectorstore/service/{VectorStoreUsecase,VectorStoreReferenceAggregator,VectorStoreCatalogImpl,VectorStoreEngineDescriptor}.java`（`VectorStoreCatalogImpl` 同时实现 `retrieval.spi.VectorStoreCatalog` 与 `TenantConfigSource<VectorStoreRuntimeConfigBo>`，不另建第二个 spi 适配类）
- Create: `M/vectorstore/controller/VectorStoreController.java`
- Create: `M/vectorstore/vo/{VectorStoreCreateVo,VectorStoreUpdateVo,VectorStoreRespVo,EngineTypeRespVo,VectorStoreProbeVo}.java`
- Create: `M/vectorstore/assembler/VectorStoreAssembler.java`
- Create: `M/vectorstore/config/{VectorStoreCodeRegistration,VectorStoreAssemblyConfig}.java`
- Modify: `R/i18n/app-messages*.properties`（4 个 vectorstore key）
- Modify: `frontend/src/api/vector-store.ts`
- Test: `T/vectorstore/{VectorStoreSchemaTest,VectorStoreUsecaseTest,VectorStoreCatalogImplTest,VectorStoreControllerTest,VectorStoreCodeMessagesTest}.java`

**Interfaces:**
- Consumes: framework `TenantClientRegistry`、`TenantConfigSource<VectorStoreRuntimeConfigBo>`、`SsrfGuard`；retrieval-engine 的 `retrieval.spi.VectorStoreCatalog`、`retrieval.api.VectorStoreView`
- Produces: `vector_stores` 表；`VectorStoreQueryApi`；`VectorStoreReferenceProvider` 声明；`VectorStoreCatalogImpl` 实现计划 02 的 spi，类放 `M/vectorstore/service` 内部包，不放 `api`，签名逐字照抄计划 02：`Optional<VectorStoreView> byId(long tenantId, String storeId)`、`boolean isOwnedBy(String storeId, long tenantId)`；同一类实现 `TenantConfigSource<VectorStoreRuntimeConfigBo>` 的 `String version(Long tenantId)`、`VectorStoreRuntimeConfigBo load(Long tenantId)`；`GET /vector-stores/engine-types` 只返回 3 项

- [ ] **Step 1** `VectorStoreSchemaTest.tenant2CannotReadTenant1Rows`、`engineTypeRestrictedToThree`、`liveDuplicateNameRejected`；`VectorStoreUsecaseTest.createRejectsNonWhitelistedEngine`、`probeFailureRollsBack`、`deleteBlockedWhenReferenced`、`credentialsStoredEncrypted`；`VectorStoreCatalogImplTest.byIdReturnsEmptyForOtherTenant`、`isOwnedByRejectsCrossTenantStore`、`viewCarriesDecryptedPassword`、`versionChangesWhenRowUpdated`、`missingRowReturnsEmpty`；`VectorStoreControllerTest.engineTypesContainsExactlyThree`、`createValidatesAndProbes`、`updateKeepsUntouchedConnectionFields`、`listOmitsCredentials`；`VectorStoreCodeMessagesTest` 七语种。
- [ ] **Step 2** 跑红。
- [ ] **Step 3** 实现：`VectorStoreEngine` 枚举只有 `PGVECTOR`、`ELASTICSEARCH`、`MILVUS`，DDL 加 CHECK；引擎元数据用三个 `VectorStoreEngineDescriptor` Bean。qdrant、tencent_vectordb、weaviate、doris、opensearch、sqlite 六种旧引擎代码改包名迁入但停用，不在 `VectorStoreEngineDescriptor` 里登记，不进 `engine-types` 下拉，不测试；`version` 只查 `updated_at`；`references` 返回空表。前端 `listVectorStoreTypes` 改 `engine-types`，组件读返回数组，不写死选项。
- [ ] **Step 4** 跑绿；`npx playwright test --grep "\[vectorstore\]"`。用例不断言选项数量。
- [ ] **Step 5** 提交：`git add DB/<脚本> M/vectorstore R/mapper/vectorstore R/i18n T/vectorstore frontend/src/api/vector-store.ts`，`git commit -m "feat(vectorstore): 表、三种引擎与前端下拉"`。

### Task 22: websearch 表、13 个适配器、api 与控制器

**Files:**
- Create: `DB/V<D>_09__DDL_rebuild_websearch_create_tables_jiangguifan.sql`
- Create: `M/websearch/entity/WebSearchProvider.java`
- Create: `M/websearch/mapper/WebSearchProviderMapper.java`
- Create: `R/mapper/websearch/WebSearchProviderMapper.xml`
- Create: `M/websearch/repository/WebSearchProviderRepository.java`
- Create: `M/websearch/api/{WebSearchApi,WebSearchCode,WebSearchProviderBo,WebSearchQueryBo,WebSearchResultBo,WebSearchProviderType}.java`
- Create: `M/websearch/service/{WebSearchProviderUsecase,WebSearchService,WebSearchProviderRegistry,WebSearchSessionStateRepository,WebSearchProbeService}.java`
- Create: `M/websearch/adapter/{WebSearchProviderFactory,WebSearchProviderDescriptor}` 与 13 个 provider 实现（brave、duckduckgo、bing、google、tavily、ollama、searxng、baidu、keenable、metaso、zhipu、exa、bocha）
- Create: `M/websearch/controller/{WebSearchProviderController,WebSearchProviderCredentialController}.java`
- Create: `M/websearch/vo/{WebSearchProviderCreateVo,WebSearchProviderUpdateVo,WebSearchProviderRespVo,ProviderTypeRespVo,WebSearchProbeVo,CredentialPutVo}.java`
- Create: `M/websearch/assembler/WebSearchProviderAssembler.java`
- Create: `M/websearch/config/{WebSearchCodeRegistration,WebSearchAssemblyConfig}.java`
- Modify: `R/i18n/app-messages*.properties`（5 个 websearch key）
- Modify: `frontend/src/api/web-search-provider.ts`
- Test: `T/websearch/{WebSearchSchemaTest,WebSearchProviderUsecaseTest,WebSearchServiceTest,WebSearchProviderControllerTest,WebSearchCodeMessagesTest}.java`

**Interfaces:**
- Consumes: framework `TenantClientRegistry`、`TenantConfigSource<WebSearchProviderParams>`、`SsrfGuard`
- Produces: `web_search_providers` 表；`WebSearchApi`；`WebSearchProviderRegistry` 按 `WebSearchProviderType` 取；13 个 provider 的 `WebSearchProviderFactory` 实现；`GET /web-search-providers/provider-types`

- [ ] **Step 1** `WebSearchSchemaTest.tenant2CannotReadTenant1Rows`、`liveDuplicateNameRejected`；`WebSearchProviderUsecaseTest.createEncryptsApiKey`、`probeRecordsFailureWithoutPersisting`、`settingDefaultClearsPrevious`；`WebSearchServiceTest.defaultProviderFallsBackWhenExplicitMissing`、`blacklistFiltersResults`、`unsupportedFilterThrowsCode`、`noProviderConfiguredThrowsCode`；`WebSearchProviderControllerTest.providerTypesContainsThirteenEntries`、`createOmitsCredentials`、`testByIdUsesStoredConfig`、`testRawUsesBodyConfig`、`putCredentialsMasksOnRead`；`WebSearchCodeMessagesTest` 七语种。
- [ ] **Step 2** 跑红。
- [ ] **Step 3** 实现：每个 provider 一个 `WebSearchProviderFactory` Bean，`WebSearchProviderDescriptor` 提供展示元数据（替代旧 118 行大方法）；出网经 `SsrfGuard`；`WebSearchSessionStateRepository` 保留 Redis 键 `tempkb:<sessionId>`，前缀收口常量；凭据只在 PUT 时写密文，GET 返回 `configuredFields`。前端 `types` 改 `provider-types`，测试函数两分支映射 `{id}/test` 与 `/test`。
- [ ] **Step 4** 跑绿；`npx playwright test --grep "\[websearch\]"`；`OpenApiExportTest` 通过。
- [ ] **Step 5** 提交：`git add DB/<脚本> M/websearch R/mapper/websearch R/i18n T/websearch frontend/src/api/web-search-provider.ts`，`git commit -m "feat(websearch): 表、13 个适配器与控制器"`。

## 批次 H：收口

### Task 23: 架构规则、全量回归与闸门证据

**Files:**
- Modify: `docs/migration/progress.md`、`docs/migration/deviations.md`
- Modify: `backend/app/src/test/java/com/ragagent/arch/ArchitectureRulesTest.java`（仅在需补规则时）

**Interfaces:**
- Consumes: 前 22 个任务全部产物
- Produces: 闸门证据

- [ ] **Step 1** 跑 `MVN verify`，预期 BUILD SUCCESS，含 Checkstyle、ArchUnit（域依赖方向、后缀禁令、注解 SQL 禁令）、Modulith（无环）。
- [ ] **Step 2** `grep -rn "@IgnoreTenant" backend/app/src/main/java` 与 1.9 白名单逐条对齐，多出或缺失都登记偏离。
- [ ] **Step 3** 跑 `cd tests/e2e && npx playwright test --grep "\[auth\]|\[tenant\]|\[system\]|\[storage\]|\[model\]|\[vectorstore\]|\[websearch\]|\[audit\]"`，预期无 skipped、无 failed。
- [ ] **Step 4** 跑 `cd tests/api-smoke && npx playwright test tests/auth.spec.ts tests/skeleton-auth.spec.ts`，预期 2 passed；`frontend` 下 `npm run type-check` 通过；`frontend/packages/api-client` 下 `npm run check:api` 退出码 0。
- [ ] **Step 5** 提交：`git add docs/migration/progress.md docs/migration/deviations.md`，`git commit -m "chore(migration): 基础域闸门证据"`。

---

# 3. 完成判据

对应路线图第 2 节 03 号闸门：控制台登录、租户、模型、存储、向量库、网页搜索、系统设置各页的界面端到端在新栈转绿。

```bash
# 1. 全量构建，含 Checkstyle、ArchUnit、Modulith
mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml verify
# 预期：BUILD SUCCESS，无 Checkstyle 违规，抑制总数不超过 10

# 2. 基础域界面端到端（新栈，前端 5173，后端 8080）
cd tests/e2e && npx playwright test --grep "\[auth\]|\[tenant\]|\[system\]|\[storage\]|\[model\]|\[vectorstore\]|\[websearch\]|\[audit\]"
# 预期：全部通过，无 skipped

# 3. API 冒烟：登录一步转绿，骨架鉴权保持绿
cd tests/api-smoke && npx playwright test tests/auth.spec.ts tests/skeleton-auth.spec.ts
# 预期：2 passed

# 4. 前端类型与 API 漂移
cd frontend && npm run type-check
cd frontend/packages/api-client && npm run check:api
# 预期：均退出码 0

# 5. 错误码文案完整性
MVN -f backend/pom.xml test -Dtest='*CodeMessagesTest'
# 预期：8 个域的错误码七语种断言全绿
```

逐条对应：

1. 登录页可登录、个人资料与平台 API Key 可操作（`[auth]` 四条）。
2. 租户信息、成员、工作区引导可用（`[tenant]` 三条），这是黄金样板的验收。
3. 通用设置、版本信息、全局系统设置、任务队列、解析引擎可用（`[system]` 五条）。
4. 存储后端、模型、向量库、网页搜索四页可增删。
5. 审计日志过滤可用（`[audit]` 一条）。
6. 登录冒烟 `POST /api/admin/v1/auth/login` 返回 `code=0000` 且 `data.token` 非空。
7. `docs/migration/golden-sample.md` 已登记 tenant 域五个层路径。
8. `docs/migration/permission-points.md` 已交付，供计划 05、07、08 引用。

不通过的项不进入计划 04。
