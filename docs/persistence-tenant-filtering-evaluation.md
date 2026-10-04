# 租户过滤单点化 · 评估报告

> M3 评估产出（2026-10-05）。结论先行：**不建议全仓引入 MP 的
> TenantLineInnerInterceptor**；建议「域内仓储单点 + 小域试点验证 + 缺失过滤探测」
> 三步走。试点通过前，维持手工过滤现状。
>
> **进展（同日 B70）**：Step 1（detectPostgres 八处归一）与 Step 2（探测拦截器
> `TenantFilterGuard`，alert 档）已落地，首次盘面结果见文末 §5。

## 1. 现状盘点

手工租户过滤 `.eq("tenant_id", …)` 共 **9 文件 26 处**，集中在跨域桥接面：

| 文件 | 处数 | 面貌 |
|---|---|---|
| knowledge/repository/ChunkRepository | 14 | 分页过滤主面 |
| knowledge/repository/FaqChunkRepository | 5 | FAQ 扫描 |
| datasource/service/MapperKnowledgeBridge | 3+ | 跨域写桥 |
| session/mapper/{Message,MessageSuggestion}Repository | ~2 | 消息检索 |
| 其余 5 处散点 | — | auth/system 等 |

风险实证：B60（2026-10-04）刚修掉「技能目录读全表 → 跨租户可见」的越权洞——
教训是**每新增一条查询都要人肉记得带租户条件**，漏一处就是数据面越权。

## 2. 方案对比

### A. `TenantLineInnerInterceptor`（SQL 级兜底）

原理：JSqlParser 改写所有 SQL，自动追加 `tenant_id = ?`。

| 维度 | 评估 |
|---|---|
| 兜底强度 | ★★★★ 漏写不可能（SQL 级） |
| **B60 平台语义** | **✗ 冲突**：拦截器只会生成 `tenant_id = ?`，表达不了
  「`tenant_id IS NULL` = 平台内置行，所有人可见」（skills 表 V5 的部分唯一索引语义）。
  平台行会被静默过滤掉——这是正确性回退，不是风格问题 |
| 表覆盖模型 | 需维护「ignore 表清单」（无 tenant_id 列的表全部列出）；
  新表忘登记 = 直接 SQL 报错，错法响亮但烦 |
| 方言风险 | 走 JSqlParser 解析**全部**语句；本仓 33 个注解 SQL 文件含 PG 专有语法
  （jsonb 路径、向量操作符），解析失败即运行期异常——MP 原生拦截器无 fail-open 选项
  （对比：自研 FullTableWriteGuard 对解析失败放行） |
| 非 DB 面 | ✗ 不覆盖：Neo4j 图库、S3 族存储、Milvus/Qdrant/Weaviate 向量库的租户过滤
  都在仓储代码里——拦截器会造成「已有单点」的错觉 |

### B. 仓储层单点（轻量）

把跨域桥接面的手工 `.eq("tenant_id", …)` 收敛为单点 helper
（构造期取 `TenantContext`，出参即带条件的 wrapper 工厂）。优点零行为风险；
缺点仍是纪律约束——漏写靠 review。**可作为 Step 1 先做桥接面 9 处**。

### C. 缺失过滤探测（fail-loud，不改写 SQL）

自研 MyBatis 拦截器：按「必须带租户条件的表白名单」，对所有 SELECT 做
**静态检查**——SQL 里缺 `tenant_id` 谓词就抛异常（或测试期断言）。
不重写 SQL → 无方言解析风险；漏写在**测试期**就红，而不是等线上越权。
与 R6-R9 同属「机器守增量」哲学。**推荐作为主力机制**，实现成本与
FullTableWriteGuard 同量级。

## 3. 建议路线

1. **Step 1（低成本）**：桥接面 9 文件的手工过滤收敛为仓储单点 helper；
   顺手把 9 处本地 `detectPostgres` 归一到 `DatabaseDialects`（R7 白名单随之缩小）。
2. **Step 2（主力）**：实现方案 C 的探测拦截器，先以「告警模式」在契约测试里跑一周，
   盘出真实漏网面，再切「抛异常模式」。
3. **Step 3（可选试点）**：若 Step 2 数据显示 SQL 级兜底仍有必要，选
   **audit_logs（单表、无平台行语义）** 做 TenantLine 试点，自定义 TenantLineHandler
   + 全表 ignore 清单，验证 H2 契约测试与 PG 方言 SQL 全绿后再谈推广。

## 4. 决策请求

- Step 1/2 无争议可直接排期；
- Step 3 依赖 Step 2 的盘点数据，试点前不需立项。

## 5. 首次盘面结果（2026-10-05，alert 档 × H2 契约全量）

探测面：47 张注册表（V1 baseline 46 张带 `tenant_id` + V5 skills）；哨声面：
**6,084 条告警 / 73 条去重语句 / 21 张表**。分布：

| 表 | 告警 | 表 | 告警 |
|---|---|---|---|
| users | 2799 | sync_logs | 75 |
| tenant_members | 1021 | memory_item_embeddings | 65 |
| knowledge_bases | 831 | tenant_api_keys | 63 |
| wiki_pages | 564 | embed_channels | 59 |
| knowledges | 144 | chunks | 47 |
| data_sources | 142 | task_pending_ops | 37 |
| wiki_folders | 93 | 其余 7 表 | ~137 |

**定性（初步）**：头部三家是「按 id 直查 / 按用户-成员关系查」的合法传递范围
（`users` 按 id 登录后取行、`tenant_members` 按 user_id）；真正的
**直连租户过滤缺失候选**在腰部——`chunks`（47 条，部分查询按 knowledge_id 传递）、
`task_pending_ops`/`task_dead_letters`（后台任务表，调用方已按 scope 三元组收敛）、
`im_channels`（25 条）。

**切 enforce 前的收口清单**（按表逐一定性，三选一：迁出注册表并注释传递语义 /
登记白名单 / 补 `tenant_id` 条件）：
1. `users`、`tenant_members`、`knowledge_bases` → 大概率整体迁出（auth 面按 id/成员关系是既有设计）；
2. `chunks`、`knowledges`、`wiki_*` → 逐条看：传递范围迁出，真漏补条件；
3. 后台任务表 → 若确认 scope 三元组即租户边界，迁出并注释。

建议按此清单单独立批（B71+）处置后再切 enforce；本批保持 alert 盘面。
