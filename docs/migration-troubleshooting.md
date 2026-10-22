# 数据库迁移失败排障

> 适用：后端启动失败、日志出现 Flyway 迁移错误、或系统信息页提示数据库版本异常。
> 迁移由 Flyway 在启动时执行，记录在 `flyway_schema_history`。

## 先确认三件事

1. **连的是哪个库**
   - `DB_HOST` / `DB_PORT` / `DB_NAME` 是否指向预期实例。
   - 如果刚改过库名（例如 `WeKnora` → `RagAgent`），复用旧数据卷时数据库不会自动重命名：应用连新库名会报“database does not exist”。
2. **数据库是否可达**
   - 端口、账号密码、网络/容器网络是否通。
   - 托管数据库还要看 IP 白名单。
3. **当前迁移版本**
   - 系统信息接口：`GET /api/v1/system/info` 的 `dbVersion`。
   - 或直接查：`SELECT MAX(CAST(version AS INTEGER)) FROM flyway_schema_history WHERE success = TRUE;`

> 系统信息页预留了 `dbMigrationError` 字段；当前版本该字段可能为空，**以启动日志中的异常栈为准**。

## 常见原因

| 日志/现象 | 常见原因 | 处理 |
|---|---|---|
| `database "xxx" does not exist` | `DB_NAME` 与容器初始化库名不一致；复用旧卷但改了库名 | 改回旧库名，或新建库/迁移数据 |
| `Connection refused` / `timeout` | 数据库未启动、端口错、网络隔离 | 先确认 `psql` 能连，再起应用 |
| `relation ... does not exist` / 扩展缺失 | 未启用 `pgvector` 等扩展 | 在目标库执行 `CREATE EXTENSION IF NOT EXISTS vector;`（按需） |
| `Validate failed` / checksum mismatch | 迁移脚本被改过；手工改过表 | 不要手改 `flyway_schema_history`；恢复原脚本或按 Flyway 官方流程 `repair` |
| `Found non-empty schema without schema history table` | 非空库没有基线 | 确认是否误连旧库/业务库；必要时初始化基线或换空库 |
| 版本跳跃/重复 | 多分支并行产生同版本号迁移 | 合并版本号，保留单一顺序 |
| 启动一半失败后卡住 | 上次迁移中断留下锁/半成品 | 备份后检查失败版本对应表，修复后重跑 |

## 处理原则

1. **先备份**：`pg_dump` 目标库；至少确认可回滚。
2. **看完整异常栈**：第一条 `Caused by` 通常才是根因，后面的“迁移失败”只是包装。
3. **不要跳过校验**：`flyway.validate-on-migrate=false`、手删 `flyway_schema_history` 行，都会把问题推迟到运行期。
4. **区分“新库”和“旧库”**：
   - 新库：允许重建空库后从头迁移。
   - 旧库/生产：优先修复，不要直接重建。
5. **改库名要配套迁移**：复用旧数据卷时，`DB_NAME` 必须保持旧名，或先 `ALTER DATABASE` / dump-restore 再切。

## 求助时带上

- RagAgent 版本、commit、前端版本（系统信息页可复制）
- `DB_NAME` / `DB_HOST`（不要贴密码）
- `flyway_schema_history` 最后 5 行（`installed_rank, version, success`）
- 启动日志里第一条 `Caused by`
