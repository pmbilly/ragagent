# v0.5.0 可读性专项整改方案

> 2026-10-18 拍板。执行锚点＝本文件 + `HANDOFF.md` §5 批次表 + §12 执行记录。
> 分支：`v0.5.0`（自 `main` 切出）。推送只在用户发话后执行。

## 1. 目标

新人第 2 天能独立追完一条主链路，第 1 周能改一个 API 而不撞墙。

| 指标 | 基线（2026-10-18） | 目标 |
|---|---|---|
| 一条问答链路的"必读文件数" | ~20 | ≤10 |
| 主码注释中的迁移叙事 | 769 处（退役 98 / 旧 254 / 历史 313 / 备案 33 / 已知差异 60 / Go 11） | 0（守卫硬零） |
| 文档与代码不一致 | 7 处 | 0（守卫防再生） |
| 控制器手工 catch | 151 处 | 0 |
| 控制器直连 Mapper | 22 处 | 0 |
| 注释密度 | 18.6% | 仅报告，不设门（密度会奖励删信息） |
| `>600 行` 主码文件 | 59 个 | 不增加（现有棘轮） |
| `package-info` 覆盖率 | 166/322（48% 缺失） | 100% |
| warn 日志 | 893 条 | 777 条（404/跳过类已清零；不设总数门） |

## 2. 边界

**不做**：

1. 不重写 RAG 管线、`StreamManager`、`TenantContext`（1,349 金片 + 4,653 测试是资产）。
2. 不加功能、不换框架、不动 DB schema。
3. 不为"架构形式"拆模块（B210/B211 回退的教训）。
4. 前端大文件拆分单独立项（`WikiBrowser.vue` 6,593 行不属本次范围）。

**允许**：

- 删类、改名、合并包、删死代码、删注释、拆出 `:knowledge`。
- 利用"未上生产"窗口清理 Go 兼容语义（错误文案、空值语义、字段命名、双形态响应），不留兼容分支。
- 金片重录只在 L10/L13 两个"行为变更批"发生，且单独提交、逐条 diff。

## 3. 拍板记录（2026-10-18）

| # | 决策 | 结论 |
|---|---|---|
| 1 | L10 错误形态一次性统一 | ✅ 做。删字符串分支，只留 `{code,message,data}` |
| 2 | L13 实体/DTO 分离 | ✅ 做。限 4 个核心实体（`Session`/`Knowledge`/`Chunk`/`WikiPage`） |
| 3 | L19 拆 `:knowledge` | ✅ 做。三问全过；其余域不拆 |
| 4 | 前端是否纳入 | ⏸ 单独立项 |

## 4. 基线读数（2026-10-18 实测）

- 主码：2,209 文件 / 299,199 行（contract 345/20,625 · platform 181/17,303 · model-runtime 145/17,971 · retrieval-engine 82/20,404 · agent-engine 66/10,839 · domains 1,372/209,949 · boot 18/2,108）
- 测试：483 文件 / 4,653 `@Test`
- 控制器 51 / 业务域 22 / Mapper 60 / Repository 28 / `*Adapter` 31
- 接口 322（单实现 127）· 单行 getter 1,456 / setter 282 · `@Transactional` 30
- 模块间 import：3,768 次，零反向、零循环；`boot → domains` import = 0（纯组件扫描）
- 域间 import：61 处 / 3 条边（`session→agenttools` 42、`session→agentmanagement` 16、`auth→tenant` 3），0 对互依
- 守卫 23 条全绿（L1 起 24 条）；`doctor.sh` 全绿；`:contract`/`:platform` 编译通过

## 5. 批次总表

### M1 清理（让代码只讲现在）

| 批 | 内容 | 验收 |
|---|---|---|
| L1 | 迁移叙事清零（769 处）：历史说明→删 / 当前约束→改现在时 / 设计取舍→移 `known-gaps.md` | `check-comment-narrative.py` 硬零 |
| L2 | 行号与 Go 语法锚点（`L141-144`、`uuid.New()`、`Order("...")`、旧模块名 `common`）；构建脚本 31 处批次号 | `check-go-anchors` 归零；`check-internal-codes` 覆盖 `*.kts` |
| L3 | 修 7 处文档失真（README ×4、3 个类注释） | `check-doc-truth.py` |
| L4 | 补 156 个 `package-info`（职责 / 依赖 / 从哪读起） | `gen-arch-index --check` 100% |
| L5 | 注释装饰清理：删 491 行纯装饰分隔线、216 行统一为 `// ── 段名 ──`；不加密度硬指标（本仓注释承载契约与线程纪律，删减会丢信息） | `check-comment-decor` 硬零 |

### M2 收敛（消除三套并存）

| 批 | 内容 | 验收 |
|---|---|---|
| L6 | "未实现/暂缺"收敛到 `docs/known-gaps.md`，类注释留指针 | `check-known-gaps.py` |
| L7 | 死代码删除：空包、未接线 port、`@Deprecated`、`platform/session`、`platform/tool` | `check-dead-members` 扩面 |
| L8 | 日志降噪：正常降级 warn→debug | warn ≤200 |
| L9 | 删 151 处控制器 catch；`BizException.internal(msg, cause)` 保留堆栈 | 守卫禁 controller catch |
| L10 | 错误形态统一（**行为变更**）：删 `PlainErrorException`/`GuardForbidden` 字符串分支 | 金片重录（单独提交） |
| L11 | `check-exception-semantics` 扩到 domains 全量 | 红态探针 |

### M3 分层（让"该放哪"有唯一答案）

| 批 | 内容 | 验收 |
|---|---|---|
| L12 | 数据访问定规：单表→Mapper，跨表/方言/事务→Repository；22 处 controller 下沉 | 守卫禁 controller import Mapper |
| L13 | 4 个核心实体 → `XxxView` record（**行为变更**） | 金片键序/键名逐字对比后重录 |
| L14 | 事务三套模板收敛为一套；多步写补声明或注释理由 | 缺失清单棘轮 |
| L15 | `ChatManage` 127 方法 → `QaRequestConfig` + `QaPipelineState`；DTO/View 批量 record 化 | `ChatManage` ≤40 方法；getter 数 −50% |

### M4 显式化（让控制流看得见）

| 批 | 内容 | 验收 |
|---|---|---|
| L16 | `QaTurnExecutor.executeQA` 200 行 → 6 个具名步骤 | 方法 ≤50 行 |
| L17 | `scripts/gen-pipeline-map.py`：事件 → 插件 → 读/写字段 → 兜底 | 生成 `pipeline-map.md` + `--check` 守卫 |
| L18 | 17 个插件类头三行契约（读什么/写什么/失败谁兜底） | 守卫抽查 |

### M5 结构（只做有明确收益的切割）

| 批 | 内容 | 验收 |
|---|---|---|
| L19 | 拆 `:knowledge`（241 文件；切边 2 处；登记 7 个面） | `domains` 1,372→1,131；测试守恒 4,653 |
| L20 | `modelcontext` 下沉 `contract`，剪 `domains`/`agent-engine` 两条依赖边 | 模块图边消失 |
| L21 | `platform/knowledge` 7 类、`platform/model` 2 类归位 | `check-platform-shapes` 清零 |
| L22 | 工具三处收敛：`BaseTool` → `agent-engine`，删 `platform/tool` | 搜 `Tool` 只剩两处 |

## 6. 新增守卫（各配红态探针）

| 守卫 | 拦什么 |
|---|---|
| `check-comment-narrative.py` | 主码注释中的迁移叙事关键词 |
| `check-doc-truth.py` | README 数字/覆盖率与代码不符 |
| `check-controller-purity.py` | controller 里的 `catch`、`Mapper`、业务赋值 |
| `check-pipeline-map.py` | 插件表与代码不同步 |
| `check-known-gaps.py` | "未实现"声明没有登记 |

另：`scripts/readability-report.py` 输出 6 个指标，接进全量闸门的收尾输出。

## 7. 执行纪律

1. 一次只动一个轴，独立提交、独立全绿。
2. 每批收尾跑一次全量闸门：`spotlessCheck build` + 全部守卫 + `test-summary.py`。
3. 守卫改动必配红态探针；扩面用棘轮基线（登记理由、只许减、过期报红）。
4. 金片批次（L10/L13）单独提交，重录前保存旧快照逐条 diff。
5. 推送只在用户发话后执行。
6. 触及 HTTP 面做真实起服冒烟。
7. 每批写回 `HANDOFF.md` §12。

## 8. 里程碑

- **M1（L1~L5）**：新人读到的是"现在是什么"。
- **M2（L6~L11）**：排障不再丢堆栈，错误形态唯一。
- **M3（L12~L15）**：知道新代码该放哪。
- **M4（L16~L18）**：能追一条完整链路。
- **M5（L19~L22）**：模块边界与业务边界一致。

## 9. 风险

| 风险 | 应对 |
|---|---|
| L1 批量删注释误删真实约束 | 三分法逐类判断；每 50 处一次编译；守卫只拦关键词不替代判断 |
| L10/L13 金片重录掩盖真实回归 | 重录前存快照，diff 必须只含预期变更；单独提交 + 单独闸门 |
| L19 拆模块引入装配断裂 | 三问已实测；切边 2 处；拆后立即跑金片与启动冒烟 |
| L15 record 化触碰 MyBatis 实体 | 明确不动 `@TableName` 实体，只动 DTO/View |
