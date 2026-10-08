# 持久层架构规约（修订版）

> 2026-10-05，依据架构师四条意见 + 本仓实测数据修订。原意见的方向均予采纳，
> 个别条款按「失败模式」而非「工具」改写，并补齐执行机制（机器强制 + 棘轮基线）。
> 数据底座见文末「现状基线」；机器强制面见
> `server/src/test/java/com/ragagent/arch/ArchitectureRulesTest.java`（A6-A9）
> 与 `common/mybatis/FullTableWriteGuard`。

## 1. 持久层

- 业务单表 CRUD 与条件组合查询一律走 MyBatis-Plus；条件构造器必须用
  **Lambda 方法引用**（`LambdaQueryWrapper` / `LambdaUpdateWrapper`），
  禁止字符串列名——硬编码列名没有编译期保护。
  - 例外（A8 棘轮基线登记）：jsonb 列的三参 `set(col, val, "typeHandler=…")`
    若依赖 lambda 表达不了的列形态，可保留字符串 wrapper（`MessageRepository.update`
    为现例，作者注释已说明 H2 实测错误形态）。
- **裸 JDBC 白名单制**（A7）：`JdbcTemplate` / `java.sql` 连接与语句 /
  `DataSource.getConnection` 只允许白名单类使用，分四类场景：
  ①方言探测（`DatabaseDialects` 为收敛点；各仓储本地 `detectPostgres` 待归一）；
  ②PG/方言专有 SQL（jsonb、向量操作符、批量写）；
  ③非业务库引擎（DuckDB / Doris / SQLite / pgvector）；
  ④启动期修复（`StartupTaskRecovery`）。
  新类进白名单必须在 PR 论证属于上述之一。
- **全表 UPDATE/DELETE 机器拦截**：`FullTableWriteGuard`（挂在
  `MybatisPlusConfig` 插件链尾）拦截无 WHERE 的 UPDATE/DELETE；
  业务上确需无边界写的，必须**具名成 Mapper 方法**并在
  `FullTableWriteGuard.FULL_TABLE_ALLOWED` 按语句 id 登记（附理由）——
  禁止 `update(null, 无条件 wrapper)` 匿名发起（现例：
  `TenantMapper.applyDefaultStorageQuota`）。
  方言 SQL 解析失败时 fail-open（复杂 SQL 归白名单纪律管辖，见 §2）。

## 2. SQL 编写策略

- **SELECT \* 禁止**（现状本为 0，护栏条款）。
- **内存分页禁止**；分页一律 DB 端：
  - page/size 语义 → `PageRequests.range(page, size)`；
  - offset/limit 语义 → `PageRequests.atOffset(offset, size)`；
  - 行帽（取前 N 条）→ `PageRequests.cap(n)`；
  - 总数由调用方显式 `selectCount` 提供（保持 count 查询形状可控——
    MP 自动 count 带 ORDER BY 时 H2 会报错，见 `TenantInvitationService` 注释）。
- `.last(...)` 只允许**纯字符串字面量**（A9 机器强制），如 `.last("LIMIT 1")`；
  拼接一律迁 `PageRequests`（负数会被钳到 0，不会退化成全表扫）。
- 复杂 SQL（多表 Join、动态 SQL、方言优化）的出口：**Mapper 接口注解 SQL 或
  白名单 Repository**，禁止散落 Service。
  不引入 Mapper XML 基建（mapper-locations/编译校验/DBA 流程的建设成本高于当前
  收益；若将来单条查询满足「≥3 表 Join 或大段动态 SQL」再评估）。
  现状 33 个注解 SQL 文件即为既成出口。

## 3. 架构与解耦

- **禁止 @Lazy 注入**（A6 机器强制）：循环依赖要拆——下沉公共逻辑至
  Helper/下层服务，或接口反转，或领域事件。
  - `knowledge` 域门面环在 A6 棘轮基线中，解环专项
    （下沉 `KnowledgeAccessHelper` + worker 接口反转）落地后清空基线。
- 领域事件解耦按需建设，不作通用要求（本仓 `ApplicationEventPublisher`
  现为 0 处使用；事件基础设施是投资项，不是规约条款）。

## 4. 包结构

- 域级组织（`com.ragagent.<域>.<子域>`）——本仓已合规（28 个顶层域）。
- 单包平铺类数 **建议** 10~15：超限先评估再拆，不设硬规则
  （domain 包类多常是表多，硬拆制造噪音）。已超限的 25 个包中，
  `wiki/service/ingest`（52 类）是唯一明确的拆分候选，挂台账观察。

## 5. 多租户（本仓专属新增条款）

- **租户过滤必须单点表达**：手工 `.eq("tenant_id", …)` 是越权洞的高发形态
  （B60 刚修过「技能读全表跨租户可见」）。收敛方案评估见
  `docs/persistence-tenant-filtering-evaluation.md`。

## 现状基线（2026-10-05 实测）

| 面 | 值 |
|---|---|
| 字符串 wrapper（A8） | 25 类 73 处 → 逐域 Lambda 化中，MessageRepository 1 处保留（jsonb） |
| `.last(` 拼接（A9） | 23 处 13 文件 → 20 处已迁 `PageRequests`，余 3 处在 knowledge 域 |
| @Lazy（A6） | 8 类 11 个注入点，全在 knowledge 域（解环专项待办） |
| 裸 JDBC（A7） | 28 类白名单登记，含 9 处可归一的本地 detectPostgres |
| 无条件全表写 | 匿名 0 处；具名登记 1 处（applyDefaultStorageQuota） |
| 注解 SQL / XML | 33 文件 / 0 |
| 手工租户过滤 | 9 文件 26 处 |
