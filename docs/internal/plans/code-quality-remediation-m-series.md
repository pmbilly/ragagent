# 后端代码质量评估（03）核实与整改方案（M 系列）

> 2026-10-20 立项。评估报告：`docs/architecture-review/03-backend-code-quality.md`（外部评估，基线 `953815f29`，10-18）。
> 核实基线：`v0.6.x` @ `77020ebf3`（10-20 17:24，较评估基线晚 135 个提交）。
> **结论先行**：报告的方向基本成立，但**不能照单全收**——12 条 backlog 中 6 条可直接采、4 条要改范围、2 条已过期或技术上不成立；量化数字有 5 处失真。
> 本文件是执行方案，不是第三份评估。执行锚点＝本文件 + `HANDOFF.md` §5.0e + §12 执行记录。

## 1. 核实结果（先给判词）

| 报告论断 | 实测（当前 HEAD） | 判词 |
|---|---|---|
| 301 个生产文件 >300 行，承载 47.3% LOC | **302 个 / 142,355 行 / 47.3%** | 属实（数字随提交漂移 1 个文件） |
| >500 行 17 个；>700 行 13 个 | **>500 = 113 个；>700 = 21 个** | **不属实**（报告这两行与自己列出的“最大 17 个”互相矛盾） |
| MemoryService 175 行单行委托、双开关并行、4 处孤儿注释 | 120-294 共 175 行委托；`enabledScope`/`scopeDisableReason` 两套；687-693 四个空节标题 | 属实 |
| KnowledgeService 35 个单行委托、注释腐烂 | 50 个 public 方法，控制器实际调用 34 个；委托约 35 个；57-71 / 80 / 365 / 407 多处腐注释 | 属实 |
| UserService 90 行手写补偿、auth 0 处事务 | `register` 432-521 手写 tenant→user→member 回滚；auth 域 0 个 `@Transactional` | 属实 |
| domains 共 22 处 `@Transactional`，只覆盖三域 | **22 处**：wiki 10 / knowledge 11 / memory 1 | 属实（域内分布略有出入） |
| 手工 SHA-256 18 处 / 5 模块 | **20 个文件**：domains 13、platform 2、contract 1、model-runtime 2、retrieval 1、agent 1 | 数量偏少；且“全部下沉 platform”**不成立**（contract 不能依赖 platform） |
| `deleted_at IS NULL` 约 160 处 / 60 文件 | **193 处 / 52 文件** | 量级属实；但 H5b 已于 10-20 上线（26 表、enforce、0 违规），报告的修法已过期 |
| `TenantContext.currentTenantId()` 146 处 / 87 文件 | **146 / 87** | 完全属实 |
| `BizException` 862 处，其中 controller 层 534 处 | **609 处，controller 包 349 处（57%）**；303 处在 `*Controller.java` | **数字不可复现**，但“HTTP 层偏厚”的方向成立 |
| N+1 4 处 | SessionService 2 处属实；KnowledgeSearchService 属实（同一 KB 查两次，低）；**ChunkAccessGuard 不是递归树**——只查“本块 + 父块”两层，最多 2 次查询 | 一半属实；“树遍历逐层查库”是误读 |
| 钉钉 `static STREAMS` 跨租户串流 | `static` 全局表属实；key 是 `dt:<userId>:<messageId>`，**只有 userId 碰撞不够，还需 messageId 相同**；token 惊群与 `expireIn-300` 负 TTL 属实 | 缺陷存在，但报告的风险描述过强；修法仍值得做 |
| 51 控制器、22 个 >300 行 | 52 个（含 platform 探针）、22 个 >300 行、中位数 261.5 | 属实 |
| 22 个方法 >150 行 | **32 个**（包级方法被报告漏扫；文本块/注解假阳性已在本仓脚本剔除） | 报告少算 10 个 |
| package-info 覆盖 249/261（95.4%），12 个目录待补 | **353/353（100%）** | **已过期**（L4/K 系列已补齐） |
| Service/Ops/Support 三方言 69/20/9 | 现象属实；文件数实测 97/74/23（口径不同） | 现象属实，数字不必当真 |
| 响应信封双轨 | `DataSourceController` 明示直写 `{"error"}`；EmbedChannel 仍有 8 处 `"error"` 键 | 属实；但 DataSource 的形态是文档化契约，改动需会签 |

## 2. 对 12 条 backlog 的逐条裁决

| # | 报告建议 | 裁决 | 实际做法与理由 |
|---|---|---|---|
| 1 | 大文件治理，300 行 ratchet 先行，再拆 7 个文件 | **部分采纳** | **不设 300 行硬门**：与既有 600/800 政策、以及“按自然接缝切、不为数字硬切”的定案冲突；302 个基线只会制造豁免噪音。改为：方法 >150 行做棘轮（基线 32）、7 个文件按接缝拆、其余 >700 行文件不新增债务。 |
| 2 | 软删过滤：`@TableLogic` 或 ArchUnit 源码扫描 | **已闭环，不采纳** | H5b 已做 `SoftDeleteFilterGuard`（运行期探测、26 表、默认 enforce、允许清单为空）。`@TableLogic` 在 H5 已明确否决（回收站/审计/恢复/对账会误伤）。残余 v1 边界（只查 SELECT 顶层、JOIN/子查询不查）登记 `known-gaps` 即可。 |
| 3 | 手工 SHA-256 18 处全部下沉 platform | **采纳，但改范围** | 建 `platform.security.Hashes`（`sha256Hex` / `sha256Base64Url` / `utf8`），替换 19 处；**contract 的 1 处保留**（纯 JDK 红线，不能依赖 platform），在类注释写明例外。替换前逐处核对 hex 大小写/截断长度。 |
| 4 | MemoryService 删 175 行委托、Ops 改 Bean 直注 | **部分采纳，删委托方案不成立** | 双开关合并、写管线/模型解析提取、孤儿注释清理都做；**不删委托**：三个 Ops 是包级类且回调 `MemoryService` 的 `enabledScope/write/rebuildBlock`，改成 Bean 会形成循环依赖并暴露内部面。委托是门面的既定成本，保留并写明理由。 |
| 5 | auth 零事务，register 改 `@Transactional` | **采纳（限 register）** | `register` 改 `@Transactional(rollbackFor = Exception.class)`，删外层手写补偿，保留逐字符文案；加“成员行失败→租户/用户零残留”的失败注入测试。其余域先做**多写路径审计**，不搞一律加事务（mcp/storage 的建单条写、session 的清理是刻意 best-effort）。 |
| 6 | N+1 4 处批量化 | **部分采纳** | 只做 SessionService 两处（批量取会话 + 批量删建议）与 KnowledgeSearchService（首轮缓存 KB，去掉重复 `findById`）；**ChunkAccessGuard 不做**（不是 N+1）。 |
| 7 | 响应信封统一 | **暂缓，需拍板** | 属 HTTP 线格式变更；系统虽未上线，但 DataSource/EmbedChannel 的形态被前端与金片依赖。先登记为“待产品/前端会签”，不夹在代码质量批里做。 |
| 8 | 钉钉流表加租户维度，修 token 惊群/负 TTL | **采纳** | 流 key 改 `dt:<channelId>:<userId>:<messageId>`（工厂已知 channelId）；token 改单飞（同实例合并并发取 token）；`expireIn-300` 加下限。与适配器拆分同批做，避免两次改同一文件。 |
| 9 | 协作者命名判据 + 12 个 package-info + api/ 说明 | **部分采纳** | 命名判据写进 `docs/architecture/overview.md`；**不做批量改名**（74 个 Ops / 23 个 Support，收益是形式统一，成本是大 diff）。package-info 已 100%，无需补。api/ 包补一段用途说明。 |
| 10 | FAQ 复制尾块 ×4 + 上帝命令服务 | **采纳** | 抽 `FaqEntryProjector`（顺带修掉“同一条 tag 查两次”）、`FaqDuplicateChecker`、`FaqBatchFieldUpdater`；活动审计走 M3 的共享记录器。 |
| 11 | McpCatalog 常量 + 工具汤提文件 | **部分采纳** | 抽 `McpJsonCodec`、`McpToolNaming`，删本地 sha256/hex/b64（走 M3）；**提示词常量不资源化**（改提示词要动编译是真，但资源化引入 I/O 失败面，收益低）；DTO 巢居保留（字节契约，巢居是内聚）。 |
| 12 | 注释卫生专项清理 | **采纳，但随文件批做** | KnowledgeService/MemoryService 的腐注释随 M5/M6 清；**不做全仓脚本守卫**（“悬空 javadoc”与“合法空行”难机械区分，误报成本高于收益）。 |

## 3. M 系列批次

> 量级：S ≤1 天 / M 2-5 天 / L 1-2 周。每批独立提交、独立全绿；结构搬迁批加跑 `check-package-cycles.py`；新增守卫必配红态探针。

| 批 | 内容 | 量级 | 验收 |
|---|---|---|---|
| **M0** | 核实 03 报告：大文件/事务/SHA/软删/N+1 实测；产出本方案 | S | 本文件；数字可复现 |
| **M1** | 规模棘轮：`method-length-report.py` 升级为棘轮（>150 行基线 32；新增即红，存量只许减、允许 +10 行 bugfix 余量）；接入 `run-guards.sh` | S | 新守卫绿；注入 151 行方法即红；不设 300 行文件硬门 |
| **M2a** | 钉钉：`DingtalkCallbackParser` / `DingtalkApiClient` / `DingtalkCardStream` 三刀；流 key 加 channelId；token 单飞 + TTL 下限 | M | 超时/SSRF/降级链保持；两渠道同 user/message 不串流；并发取 token 只打一次 |
| **M2b** | 会话批量删除：`SessionRepository.getByIds` + `MessageSuggestionRepository.deleteBySessionIds`；知识检索首轮缓存 KB | S | 批量删不再逐条查/删；检索每 scope 只查一次 |
| **M3a** | `platform.security.Hashes` + 19 处替换（contract 例外登记） | M | NIST 向量测试；hex 小写/截断长度逐处等价；金片不变 |
| **M3b** | `platform.audit.ActivityAuditRecorder` 合并 Faq / KnowledgeTag / DataSourceSupport 三份 `recordKbActivity` | S-M | details 字母序、best-effort、outcome/task 语义不变 |
| **M3c** | `LogSanitizer` 替换 4 份 `sanitizeForLog`；`trimUnicodeWhitespace` 移 `platform.text` | S | 4 → 0；auth 控制器不再反向依赖 `UserService` 静态工具 |
| **M4a** | `UserService.register` 改声明式事务；删外层补偿；失败注入测试 | M | 成员行失败后 tenant/user 均无残留；成功路径文案不变 |
| **M4b** | 事务边界约定（写入 `docs/architecture/mechanisms.md`）；mcp/storage/session/im/datasource 多写审计 | S-M | 表格式列“必须事务 / 刻意 best-effort / 单条写”；只补清晰缺口 |
| **M5** | Memory：`MemoryScopeGate.evaluate()` 单次求值（scope+cfg+ok+reason）；提 `MemoryWritePipeline`、`MemoryExtractionModelResolver`；清孤儿注释/双 javadoc；**保留门面委托** | M | 770 → 约 480；五个开关分支测试；`memoryAvailable` 与禁用原因不再分叉 |
| **M6a** | FaqEntryCommandService：projector（单次 tag 查询）、duplicate checker、batch field updater；4 处尾块归一 | M | 770 → 约 400；FAQ 金片不变；批量字段更新可单测 |
| **M6b** | UserService：`LoginTenantResolver`（状态机 5 方法）、`UserRegistrationService`；静态工具走 M3c；保留 `generateTokensFor(Object)`（C10a 控制器实体导入棘轮） | M | 738 → 约 480；登录空间解析专项测试 |
| **M6c** | StorageFileResolver：删“provider 完备性 switch”的重复校验（工厂已是唯一文案源）；提 `StorageBackendResolver`、`StorageConfigProjector`（解密高危区）、`StorageEnvProvisioner`、`ResourceCatalogFileService` | M-L | 778 → 约 450；presigned 400 文案逐字不变；投影/解密专项测试 |
| **M6d** | McpCatalog：`McpJsonCodec`、`McpToolNaming`；删静态工具汤；保留提示词常量与 DTO 巢居 | M | 723 → 约 300；MCP 字节契约金片不变 |
| **M6e** | KnowledgeService：`KnowledgeTagBatchService`（含 N+1 写）、注释卫生；保留门面与 6 个事务包装；创建路径视接缝再定 | M | 711 → 约 600（或 450）；tag 批量只发一条 `IN` 删除/插入 |
| **M7** | 卫生：Service/Ops/Support 判据、api/ 包说明、README/overview 指针；响应信封登记待拍板 | S | 文档可判；零批量改名 |

**顺序**：M1 → M2a → M2b → M3a → M3b → M3c → M4a → M4b → M5 → M6a → M6b → M6c → M6d → M6e → M7。

**进度（2026-10-20）**：M1 ✅（方法长度守卫上线，基线 32，红态探针已验；45 守卫绿）· M2a ✅（钉钉拆 3 类、流表 key 带渠道、token 单飞 + 60s TTL 下限）· M2b ✅（会话批量删除、KB 单次查询）· M3c ✅（4 份 `sanitizeForLog` 归零，`UnicodeText.trim` 收 auth/RSS 副本）。全量构建 5034 / 0 / 4。M3a/M3b 待启动。

## 4. 明确不做 / 待拍板

**不做**：

1. 300 行文件硬门（302 个基线没有治理价值；保留 600/800 与“只减”政策）。
2. `@TableLogic` 自动软删（H5 已否决）。
3. MemoryService / KnowledgeService 的委托面整体删除（循环依赖、事务边界、端口实现三重问题）。
4. Service/Ops/Support 批量改名（只写判据，管新代码）。
5. MCP 提示词常量资源化（收益低、引入 I/O 失败面）。
6. `generateTokensFor(User)` 类型化重载（会触发 C10a 控制器实体导入棘轮）。
7. `TenantContext.currentTenantId()` 146 处统一改 `Tenants.requireCurrent()`（错误文案分 401/400 两套，收益低）。

**待拍板**：

- **M8 响应信封统一**：把 DataSource/EmbedChannel 的 `{"error"}` 并入 `{code,message,data}`，同步前端与 8+ 金片。属线格式变更，需产品/前端会签；不排进 M 系列默认路径。

**已过期（不需要做）**：

- 软删过滤（H5b 已闭环）、package-info 补全（已 100%）、跨域实体渗漏（K2 已闭环）、引擎常量双写（L1 已闭环）。

## 5. 目标度量

| 指标 | 现状 | 目标 |
|---|---:|---:|
| >700 行生产文件 | 21 | 14（7 个目标文件出榜） |
| 方法 >150 行 | 32 | 不增加（棘轮） |
| SHA-256 实现文件 | 20 | 2（platform + contract 例外） |
| `sanitizeForLog` 副本 | 4 | 0（统一 `LogSanitizer`） |
| `recordKbActivity` 实现 | 3 | 1（platform） |
| auth `@Transactional` | 0 | 1（register） |
| Session 批量删除查询 | 1 + N + N | 3 条批量语句内 |
| MemoryService / KnowledgeService | 770 / 711 | ~480 / ~600 |

## 6. 风险与纪律

| 风险 | 缓解 |
|---|---|
| SHA/钉钉/FAQ 的字节级输出变化 | 每处替换先锁测试向量；金片为零变化闸门 |
| `register` 加事务后行为变了 | 失败注入测试 + 成功路径文案逐字符断言；先只包 register，不动 `createTenant` 内部补偿 |
| 钉钉拆分类改变超时/SSRF/降级 | 逐段搬迁、不重写；超时与 SSRF 调用点保留并加测试 |
| Memory 门面改出分叉 | `MemoryScopeGate` 单次求值；五个分支参数化测试 |
| 守卫误报（方法长度/注释） | 方法长度做棘轮不做硬门；注释只随文件清，不做全仓守卫 |
| 大文件拆分引入包环 | 每批跑 `check-package-cycles.py`；按同包/子包落点，不跨域搬 |

**批次纪律**：迭代只跑定向测试；每批收尾跑一次全量闸门（`spotlessCheck build` + 守卫 + 重启 dev 后端）；结构搬迁批另跑包环守卫。推送只在用户发话后执行。

## 7. 核对手法

```bash
# 文件数 / LOC / >300 / >500 / >700：遍历 7 模块 src/main，排除 build、test、testFixtures，按行计数
# 事务：只看行首注解，排除 javadoc 里的 {@code @Transactional}
grep -rn '^[[:space:]]*@Transactional' domains/src/main --include='*.java' | wc -l
# SHA-256：精确到算法字符串，避免把 SHA-1/MD5 算进来
grep -rln 'MessageDigest.getInstance("SHA-256")' --include='*.java' */src/main | wc -l
# 软删谓词 / 租户上下文 / 异常抛出点
grep -rn 'deleted_at IS NULL' --include='*.java' domains/src/main | wc -l
grep -rn 'TenantContext.currentTenantId()' --include='*.java' domains/src/main | wc -l
grep -rn 'throw new BizException' --include='*.java' */src/main | wc -l
```

> 报告的行号与文件数会随提交漂移；宣称“已完成/待办”前先按本节重跑，**以守卫与实测为准**。
