# 后端分包与目录结构重构方案（2026-11-19 起）

> **性质**：从 `docs/blueprint/architecture-blueprint.md` 抽取"后端分包与目录结构"一项单独实施，不是蓝图整体重建。
> **基线**：2026-11-19 的 `v0.11.x`。历史开发过程见 `docs/internal/handoff/archive/HANDOFF-v0.10.x-2026-11-19.md`。
> **指挥面**：批次表与执行记录在 `docs/internal/HANDOFF.md` §5 / §12，本文件只写方案与判据。

## 1. 目标

让 19 个业务域的目录与职责边界一眼可读，把"靠 Python 脚本维持的规则"改成"由 Java 测试表达"，为后续按域重写铺路。

不追求：蓝图 5.5 的 `entity / vo / bo / assembler` 全套命名，也不做蓝图 5.2 的 contract 解散。

## 2. 边界（越界即停）

| # | 边界 | 判据 |
|---|---|---|
| 1 | **不动前端** | 路由、请求/响应字段、错误码、SSE 帧格式全不变；1,346 金片 + OpenAPI 快照守 |
| 2 | **不动数据库** | 无 DDL、无迁移脚本、无表结构变更 |
| 3 | **不做 contract 解散** | `contract` 保持现状；`domains` 仍是单个 Maven 模块 |

排除项（与上述边界冲突或属于域重写范畴）：行级安全与租户插件、表结构重建、JobRunr、PGroonga、Sa-Token、MCP 官方 SDK 替换、agentscope。

## 3. 为什么不做蓝图 5.5 的包名对齐

| 蓝图 5.5 | 现状 | 直接照做的后果 |
|---|---|---|
| `api/` = 对其他域公开的接口与 BO | `api/` 装的是 `*Adapter`（实现 contract 接口的适配器） | 语义相反：内部实现细节被标注成对外契约 |
| `entity/` = 持久化实体 | `domain/` 混了实体、值对象（`KnowledgeBaseChunkingConfig`）、事件消费者（`ChunkExtractConsumer`）、Go 兼容类（`WireValued`） | 改名即错标 |
| `dto/` 拆成 `vo/` + `bo/` | `dto/` 是控制器出入参 | 纯改名，无收益，diff 大 |

因此本次只做**职责归位**与**边界可执行化**，命名沿用 §8.2 的现有分层。

## 4. 五批计划

| 批 | 内容 | 验收 |
|---|---|---|
| **P0** | 三份只读基线：OpenAPI 快照、Bean 清单 + 端点清单、跨域引用清单 | 快照入库；后续每批 diff 必须为空或差异可解释 |
| **P1** | 结构规则先行：4 条 ArchUnit + AOP 切点 / 日志配置 grep 排查 | 先出现状违规清单（只量不改）；违规数只减不增 |
| **P2** | 纯包移动：只改目录与 `package` 声明，不改类名、不改逻辑 | 编译 + 全量闸门 + 三份快照 diff 为空 |
| **P3** | 类职责归位：合并 `*Ops`（50）/ `*Support`（21）/ `*Wiring`（13） | 该域定向测试 + 金片 + 全量闸门 |
| **P4** | `api/` 包语义纠正 + 域间边界改为测试校验 | 结构校验通过；跨域直连引用只减不增 |

### 4.1 P1 的四条规则

1. `mapper` 只被 `repository` 引用。
2. 主码无 `@Select` / `@Update` / `@Insert` / `@Delete`（自定义 SQL 进 XML 或 Repository）。
3. `JdbcTemplate` / `JdbcClient` 只在白名单包（当前 14 + 3 个文件）。
4. 跨域不引用对方的 `repository` / `mapper` / `domain`（现状实体在 `domain/`，不叫 `entity/`）。

规则 4 与现有 `check-domain-contracts.py`（R10 域间只经契约）重叠，前者是 Java 侧表达，后者保留为棘轮。

### 4.2 P2 顺序（按文件数从小到大）

`favorite`(13) → `system`(18) → `vectorstore`(20) → `audit`(20) → `embedchannel`(25) → `agentmanagement`(27) → `model`(38) → `websearch`(52) → `agenttools`(59) → `storage`(62) → `memory`(91) → `im`(106) → `auth`(122) → `datasource`(147) → `wiki`(165) → `mcp`(171) → `session`(212) → `knowledge`(283)

每个域一批、独立提交。前 6 个域合计约 130 个文件，用来把流程跑顺。

### 4.3 已知阻塞项

`knowledge ⇄ wiki` 域级循环：`knowledge/task/KnowledgeProcessWiring` 引 `wiki.service.*`，反向 5 个文件引 `knowledge.api.*`，且是**实现类直连**而非 Port。P1 规则 4 上线后第一天会红，必须在 P3 之前解开（建议：`wiki` 侧改为实现 `knowledge` 在 `api` 声明的接口，或下沉为事件）。

## 5. "搬家漏登记"风险清单

编译期抓不到、运行期才炸的六项，P1 阶段一次性 grep 清干净：

| 项 | 后果 | 检出方式 |
|---|---|---|
| `@MapperScan` / `typeAliasesPackage` | 启动失败 | 启动冒烟 |
| `AutoConfiguration.imports` 写死类全名 | 启动失败 | 启动冒烟 |
| `@ComponentScan` 范围变化 | Bean 静默消失 | **Bean 清单 diff** |
| **AOP 切点写成包路径** | 事务/日志/权限切面静默失效 | **grep，无运行时信号** |
| **`logging.level.com.ragagent.xxx`** | 日志静默变少 | **grep，无运行时信号** |
| Jackson 多态默认用类名做 type id | 输出类型名变化 | 金片 |

最后两项说明：界面录屏对这类问题同样无能为力，本次不采用浏览器录制。

## 6. 为什么不用界面录制验收

"用户可见行为不变"可以拆成三层，每层都有更便宜、更精确的抓法：

```
用户看到的
├── 页面长什么样 ── 前端不改（人工走查清单，迁移前后各一遍）
├── 接口返回什么 ── 1,346 契约金片 + OpenAPI 快照 diff（零成本，可重跑）
└── 检索/回答什么 ── 检索快照 + SSE 事件对拍（脚本级）
```

浏览器录制的价值是"可重复回归"，不是"一次性证明"；本次改动面在后端内部，界面出错的主因只可能是接口或检索变化，两者都有脚本级证据。待第二阶段真正改界面时再做 Playwright。

## 7. 验收标准

1. 全量闸门 `mvn -B clean verify` 绿，测试条数守恒（不因移动而少跑）。
2. 48 条守卫全绿，`check-reference-truth` / `check-package-dirs` / `check-package-cycles` 零断链、零错位、零新增环。
3. 三份快照 diff 为空，或差异逐条有解释。
4. 主码无 `*Ops` / `*Support` / `*Wiring` 后缀类（P3 完成后）。
5. 跨域对 `repository` / `mapper` / `domain` 的引用数为 0（P4 完成后）。

## 8. 不在本次范围

H7 / H8 / H9 / L11 / Q6 / Q8b / Q9 / Q10 / Q11 / R2c-3d 余项 / M8（见 `HANDOFF.md` §5.1）。已否决项见归档 §5.0b。

## 9. 终态与留路（contract 解散后长什么样）

> 本节记录长期目标，以及本次方案如何为它铺路。**本次不做 contract 解散**，本节只用于判断"现在这一步会不会返工"。

### 9.1 本次实施后的形态（7 模块，contract 仍在）

```text
ragagent/
├── contract/                      只剩「引擎面 + 通用面」
├── platform/                      技术适配（不改）
├── model-runtime/  retrieval-engine/  agent-engine/
├── domains/                       19 个域，目录不变
│   └── <domain>/
│       ├── api/                   ← P4 后：域对外契约（现在装 *Adapter）
│       ├── adapter/               ← P4 新增：*Adapter 从 api/ 迁出
│       ├── controller/ service/ repository/ mapper/ domain/ dto/ config/
└── boot/                          装配（不改）
```

目录基本不变，变的只有 `api/` 里装什么。

### 9.2 contract 解散后的终态（5 模块）

```text
ragagent/
└── backend/                       1 个 jar，api / worker 两角色
    ├── framework/                 现 platform + 通用面（error · event · json · text · pagination）
    ├── model-runtime/api/         ← contract.model
    ├── retrieval-engine/  api/ + spi/   ← contract.retrieval · vectorstore
    ├── agent-engine/      api/ + spi/   ← contract.agent · tool
    └── app/                       现 domains + boot/config
        ├── <domain>/              api/ controller/ service/ repository/ mapper/ domain/ dto/ adapter/ config/
        └── config/                现 boot 的 17 个装配类
```

模块数 7 → 5；`contract` 消失；`domains` 改名 `app`；`platform` 改名 `framework`；`boot` 并入 `app`。

### 9.3 24 个 contract 包的去向（实测消费者）

统计口径：各模块 `src/main/java` 中 `import com.ragagent.contract.<包>.` 的文件数（2026-11-19 实测）。

| 去向 | 包 | 消费者模块数（主要） |
|---|---|---|
| **framework** | `error` | **5**（domains 125 · model-runtime 32 · platform 6 · retrieval 4 · agent-engine 4） |
| | `event` `json` `text` `pagination` | 1–3；`text` / `pagination` 只有 domains |
| **model-runtime/api** | `model` | 5（model-runtime 51 · domains 101 · agent-engine 26 · platform 14） |
| **retrieval-engine/api** | `retrieval` `vectorstore` | 6 / 4 |
| **agent-engine/api+spi** | `agent` `tool` | 3 / 4 |
| **各域 api** | `knowledge` `wiki` `memory` `session` `storage` `tenant` `auth` `apikey` `audit` `settings` `websearch` `doc` `graph` | 1–4，主体是 domains |
| **待定** | `pipeline` | domains 27 + agent-engine 3；建议随 `session/api`，agent-engine 走 spi |

### 9.4 本次三个动作如何留路

| 本次动作 | 对解散的作用 |
|---|---|
| P1 规则 4（跨域不引对方 `repository` / `mapper` / `domain`） | Modulith 边界规则的预演，将来直接换校验器 |
| P3 消掉 `*Ops` / `*Support` / `*Wiring` | 域内不再有外挂依赖，`api/` 才有资格承载契约 |
| P4 把 `*Adapter` 从 `api/` 迁到 `adapter/` | **唯一有顺序要求的一步**：`api/` 必须先腾空，否则域契约搬进来会和 Adapter 混住 |

结论：本次实施后解散 contract 是"加一层"（删模块 + 改 import），不是"改一遍"。

### 9.5 解散的起点不是搬域契约

24 个包里只有 4 个（`doc` / `pagination` / `settings` / `text`）只被 domains 消费；其余 20 个全部跨模块，`knowledge` 还被 `retrieval-engine` 与 `platform` 各用 4 处。

正确顺序：**先把 `error` / `event` / `json` / `text` / `pagination` 下沉 framework**（技术面、无业务形状、不动任何域），contract 剩下的才是纯中介，之后按引擎面、域面两块搬。该步骤不在本次范围，登记为解散批次的第一刀。

### 9.6 两个前置坑

1. **包根不统一**：`domains` 的源根是 `com.ragagent.<域>`，不是 `com.ragagent.domains.<域>`。改名 `app` 时若顺手统一包根，会触碰全部 1,642 个文件 —— **只改模块名，不动包根**。
2. **`boot` 并入 `app`** 后，17 个装配类落到 `app/config/`，`check-wiring-points.py`（W1~W4）的登记表与 `docs/architecture` 的装配清单要同步。
