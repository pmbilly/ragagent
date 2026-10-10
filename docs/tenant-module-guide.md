# tenant 模块手册

> 数据口径：2026-10-11 实测（11 文件 / 1,083 行，`domains/src/main/java/com/ragagent/tenant/`）。

## §0 一分钟速览

租户域本体（自 `common.tenant` 搬出）：租户实体 + MyBatis mapper + 租户级配置 VO + 配置脱敏。

| 类型 | 职责 |
|---|---|
| `Tenant` / `TenantMapper` | 租户实体与持久层 |
| `APIPrincipalConfig` / `ChatHistoryConfig` / `ParserEngineConfig` / `RetrievalConfig` / `StorageEngineConfig` | 租户级配置 VO（`tenants` 表 jsonb 列的类型化读写） |
| `TenantConfigRedaction` | 响应面密钥打码 / 更新面保留旧值（WebSearch、ParserEngine、StorageEngine 三族） |
| `TenantLookup` | 租户**实体**查询端口（`tenantById`），只允许 L3 业务域使用 |

## §1 为什么从 common 搬出（依赖判据）

实体 + mapper + 业务配置放在 common，等于"共享内核里住着数据模型与持久层"——全仓最强的"common 不纯"信号（R6 棘轮正为此而设）。搬出后出向依赖仅 `common`，不引入环。

**与 `common.tenant` 的分工**（改代码前先分清站在哪一侧）：

| 侧 | 内容 | 原因 |
|---|---|---|
| `common.tenant`（留） | `TenantRole`（角色门禁词汇）、`TenantProperties`（RBAC 配置）、`TenantConfigLookup`（L1 安全端口：只给 `JsonNode` 配置与存储视图）、`WebSearchConfig`（L2 chatpipeline 也读） | 跨域**词汇**，L1/L2 都要 |
| `tenant`（本域） | 实体、mapper、配置 VO、`TenantLookup`（返回实体） | 返回实体的端口若留 common，实体会被迫留在 common；若实体进本域而端口留 common，common 反向依赖本域（R5 会红） |

## §2 脱敏语义（坑）

`TenantConfigRedaction` 的 `preserveIfRedacted` 是"**空串或占位符都保留旧值**"——**唯一例外是 S3**：空串意味着"切到 AWS 默认凭据链"，只有占位符才回退旧值。改打码/合并逻辑前先读该类 javadoc 的三族说明。

## §3 消费方与后续方向

- 主要消费方：auth（filter/controller/dto/service）、knowledge（service/storage）、datasource、embedchannel、`common.mybatis`（TypeHandler）。
- 后续可选：把 `TenantLookup` 的返回收窄成视图（对齐 `common.knowledge.ChunkView` 的做法），届时可并回 `common.tenant`——动之前先过 R5/R6 棘轮与 `scripts/check-package-cycles.py`。
- 相关手册：[`auth-module-guide`](auth-module-guide.md)（租户管理面）、[`common-module-guide`](common-module-guide.md)（L1 词汇侧）。
