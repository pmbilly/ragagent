# 阶段 4 盘点与方案：Gradle 多模块 + ArchUnit 边界固化（2026-10-08）

> **用途**：阶段 4（§7.3 第 3 条）的决策输入。含**实测数据**、目标模块图、**必须先解的跨包环（含具体边）**、
> 分档迁移顺序与每步闸门、ArchUnit 规则清单、风险与非目标。
> **复测命令**：`python3 scripts/pkg-audit.py`、`python3 scripts/check-package-cycles.py`、
> 本文件 §1 的依赖矩阵脚本口径（`import com.ragagent.<pkg>.` 计数）。

## 0. 一句话结论

**环不在"两两双向"层（守卫基线 0 组），而在"三包以上间接环"（实测 2 个 SCC，覆盖 11 个包）**；
其中 **1 处是一行配置搬家的零风险切割**（`stream → config`），另 **1 个 SCC 的 5 条大边是真正的模块化前置**
（`wiki→knowledge` 43 处、`knowledge→retrieval` 47 处、`knowledge→auth` 17 处、`storage→auth` 16 处、`audit→wiki` 1 处）。
ArchUnit 1.3.0 **已是测试依赖**（`server/build.gradle.kts:111`），可直接落规则。

## 1. 现状实测（2026-10-08）

### 1.1 规模与包清单

- 主源码 **1,862 文件 / 284,531 行**，顶层包 **30 个**（`RagAgentApplication` 在根）。
- 文件/行数 top8：`agent` 188/35.9k、`knowledge` 218/26.6k、`datasource` 129/27.7k、`wiki` 156/23.9k、
  `session` 111/22.0k、`retrieval` 92/21.6k、`im` 76/17.4k、`auth` 96/12.9k。
- 测试 **418 个 `*Test.java`**，按域分布（agent 58 / session 38 / datasource 38 / wiki 32 / retrieval 31 / mcp 27 / knowledge 27 / im 25 / llm 20 / storage 19 …）。

### 1.2 依赖方向（出度/入度）

| 视角 | 结果 |
|---|---|
| 入度最高（被依赖的底） | `common` **28** 包、`llm` 18、`auth` 14、`knowledge` 13、`retrieval`/`model` 7 |
| 出度最高（依赖别人的顶） | `session` **17** 包、`chatpipeline` 10、`config` 10、`agent` 9、`knowledge` 9 |
| 枢纽类（被导入次数） | `common.error.BizException` 140、`common.context.TenantContext` 120、`common.error.AppError` 75、`common.security.SsrfGuard` 71、`knowledge.domain.{KnowledgeBase,Knowledge,Chunk}` 63/58/58、`llm.domain.ChatMessage` 54 |

> 读法：`common` 在底、业务域在顶，方向总体健康；问题集中在**少数"跨域直连"边**（见 §2）。

### 1.3 环：既有守卫只覆盖"两两双向"，真实环是间接的

`scripts/check-package-cycles.py` 的 R1 只查 `A→B 且 B→A`（**当前 0 组**），
但按"包级强连通分量"（SCC）实测存在 **2 个间接环**：

| SCC | 成员 | 状态（2026-10-08 B91） |
|---|---|---|
| **SCC-A（7）** | ~~`audit`、`auth`、`knowledge`、`model`、`retrieval`、`storage`、`wiki`~~ | ✅ **已解**（B94/C8：把 `WikiActivityAudit` 端口由 `wiki.domain` 搬入 **`common.audit`** ⇒ `audit → wiki` 这条边消失，**整个 7 包环随之瓦解**——它在环中只依赖这一条出边；守卫 `间接环 1 → 0 组`，**包图现为 DAG**） |
| **SCC-B（4）** | ~~`config`、`im`、`session`、`stream`~~ | ✅ **已解**（C1：`StreamProperties` 由 `config` 搬入 `stream`；脚本已补 SCC 棘轮 `R1b` 防回归，探针验过） |

**这就是阶段 4 的真正前置**：模块化 = 把包图变成 DAG，间接环不解决，模块无法切。

### 1.4 装配与守卫现状

- `RagAgentApplication`：`@SpringBootApplication`（根包扫描）+ `@MapperScan("com.ragagent.**.mapper")`
  + `@ConfigurationPropertiesScan({…14 个包白名单…})`——**注释明确禁止改成根包扫描**（会绑定未注册的配置类，属行为变化）。
  ⇒ 模块化时：新增模块若含配置属性类，必须把包加进白名单（这一步是**逐模块搬家**，不能漏）。
- 既有守卫：`check-package-cycles.py`（环/分层棘轮）、`pkg-audit.py`（分包体检）、
  `check-json-key-case.py`（JSON 键名 camel 棘轮 260 条）、`check-go-anchors.py`（Go 锚点棘轮）、
  `check-fe-contract-keys.py`（前后端跨面键名），外加 ~25 个 `ab-*.sh` 验收脚本。
- 构建：单模块 `server`（`settings.gradle.kts` 只 include("server")），产物 Spring Boot jar；
  其它目录 `docreader/`（Go 容器）、`frontend/`、`mcp-server/`、`otlp-proto/` 不在 Gradle 内。

## 2. 必须先解的环（切割清单，按性价比排序）

### 2.1 零风险（一行搬家）

| # | 边 | 处数 | 位置 | 修法 |
|---|---|---|---|---|
| ✅ C1 | `stream → config` | 1 | `stream/StreamManagerConfig` 用 `config.StreamProperties` | **已完成（B91）**：`StreamProperties` 搬入 `com.ragagent.stream`（扫描名单同步加 `com.ragagent.stream`）；守卫 `依赖 config 的包` 1→0，SCC 2→1 组 |

破环后 SCC-B 变成 `config → {im → session → stream}`（单向，装配层允许 →域），模块化可直接按 §3 落。

### 2.2 需要端口化（SCC-A 的 5 条大边）

| # | 边 | 处数 | 样例 | 修法（端口/门面化） |
|---|---|---|---|---|
| C2 | `wiki → knowledge` | **43** | `wiki/controller/WikiPageController → mapper.KnowledgeBaseMapper`、`WikiKbAccessGuard → domain.KnowledgeBase` | 引入 `knowledge` 的只读门面（`KnowledgeBaseLookup` 端口，暴露 `id/name/type` 视图），wiki 只依赖端口 —— **本条是最大工程**，也是模块化的关键收益点 |
| C3 | `knowledge → retrieval` | **47** | `KnowledgeBaseController → HybridSearchService`、`ImageInfoEnricher → retrieval.domain.ImageInfo` | 方向本身合法（业务域 → 能力层）；环来自 `retrieval → auth`（C6）⇒ 修 C6 即断环，本边**保持**（模块图中体现为 `domain-* → engine`） |
| ✅ C4 | `knowledge → auth` | 17 | `knowledge/security/KnowledgeRouteGuards → apikey.domain.TenantAPIKeyScope` | 把「API key scope / 路由守卫」下沉为共享端口（`common.security` 下只读接口 + auth 实现） |
| ✅ C5 | `storage → auth` | 16 | `storage/provider/FileServiceFactory → domain.tenantconfig.StorageEngineConfig` | 租户配置（`tenantconfig`）是**跨域共享配置 jsonb**：下沉到 `common.tenant`（与 `TenantProperties` 同址），auth 只负责读写端点 |
| ✅ C6 | `retrieval → auth` | 3 | `retrieval/HybridSearchService → domain.Tenant` | 改为参数/端口传入（租户 ID 与隔离策略由调用方给）⇒ 断 `knowledge → retrieval → auth` 链 |
| ✅ C7 | `model → auth` | 1 | `model/service/ModelService → service.TenantService` | 同上端口化（租户查询） |
| ✅ C8 | `audit → wiki` | 1 | `audit/service/WikiActivityAuditRecorder → domain.WikiActivityAudit` | **已完成（B94）**：**搬端口而非搬实现**——接口移到 `common.audit`（实现仍在 audit，`wiki`/`audit` 同时只依赖中性包）⇒ `audit` 成为纯叶子，且 SCC-A 整体消失 |

> C2~C8 全部做完后，SCC-A 消失，包图成为 DAG，`audit`/`common`/`llm` 在底，业务域在顶。

## 3. 目标模块图（**6 个模块，不是 30 个**）

分档推进，每档独立可验收；最终仍产出**单个 Boot jar**（部署形态不变）。

```
:app                  装配层：RagAgentApplication、config/、@MapperScan/@ConfigurationPropertiesScan 白名单
  └── :domain-*       业务域（每域一个模块 or 按扇出分组）
        └── :engine   能力层：llm、retrieval、embedding、rerank、chatpipeline、websearch、vectorstore
              └── :infra  基础设施：stream、event、tracing、（storage 的 provider 抽象？见下）
                    └── :platform  common（无 Spring 业务依赖的共享：error/context/web/llm DTO…）
```

允许方向（ArchUnit 固化）：`app → domain-* → engine → infra → platform`，
`config`（装配）可指向任意层（组合根单向），其余**禁止反向**。

### 档 A（低风险，1 批 ≈ 半天）

1. 解 C1；2. 建 `:platform`（`common/**`）+ `:infra`（`stream`、`event`、`tracing`）；3. `server` 依赖二者。
闸门：`./gradlew build`（含 4,836 测试 + spotless）+ `check-package-cycles.py` 基线刷新 + 新增 ArchUnit 规则 R-A1（platform 不依赖 com.ragagent 其它包）。

### 档 B（能力层，1~2 批）

1. 建 `:engine`（`llm`、`retrieval`、`embedding`、`rerank`、`chatpipeline`、`websearch`、`vectorstore`）；
2. 解 R3 残项（基线 `L2 → L3 直连：6 条`）。
闸门：同上 + R-B1（engine 不依赖 domain-*）。

### 档 C（域层，多批，按域推进）

1. 先解 C2~C8（可独立于模块化做，本身就是**契约净化**）；
2. 域模块分组建议（按扇出，兼顾团队边界）：
   - `:domain-knowledge`（knowledge + wiki，因 C2 修完仍高频互调）
   - `:domain-session`（session + agent + memory）
   - `:domain-platform-admin`（auth、model、system、audit、storage、favorite、evaluation、initialization）
   - `:domain-connectors`（datasource、im、mcp、embed）
   也可**每域一模块**（30 个），但 Gradle 配置开销与搬迁成本随之上升——建议先 6 个，按需细分。
3. 每域模块的 `api`（端口/视图）与 `impl`（内部）分包：跨模块只允许 import `..api..`（ArchUnit 规则可退化实现为"包名白名单"）。

### 档 D（收口）

`config/` 与 `RagAgentApplication` 归 `:app`；`@ConfigurationPropertiesScan` 白名单逐模块覆核；
`settings.gradle.kts` 模块清单与 `docs/backend-package-map.md` 同步。

## 4. 架构规则（**已有基座**，本阶段续号落地）

**已存在的两层基座**（不要另起一套）：

| 层 | 载体 | 现有内容 |
|---|---|---|
| 代码级 | `com.ragagent.arch.ArchitectureRulesTest` | **R1~R9**：禁裸 `System.getenv` / `@ConfigurationProperties` 必须在扫描名单 / 禁双装配 / `install*` 只许装配层 / 禁裸 NUL / 禁 `@Lazy` / 裸 JDBC 白名单 / 禁字符串列名 wrapper / `.last(` 只许字面量。风格：能硬断言就断言，有存量违例走"代码内基线 + ratchet 双断言"；**不引入 FreezingArchRule 存储文件** |
| 包级 | `scripts/check-package-cycles.py`（CI guards） | 两两环棘轮（R1）/ **间接环 SCC 棘轮（R1b，B91 新增）** / 装配层单向（R2：域不得 import `config`）/ L2 不依赖 L3（R3） |

**本阶段已落地（B91，2026-10-08，含红态探针）**

| 编号 | 规则 | 现状 |
|---|---|---|
| **R10** | `service` 不得依赖 `controller`；`domain` 不得依赖 `service`/`controller`（分层倒挂） | 实测 **0 违例** ⇒ 直接断言；两条探针均验红 |
| **R11** | 以 `Mapper` 结尾的**顶层接口**必须落在 `..mapper..` 包（护 `@MapperScan("com.ragagent.**.mapper")`） | 实测 0 违例；收窄为顶层接口（排除嵌套 helper `DorisSqlExecutor$RowMapper`），探针验红 |
| **脚本 R1b** | 顶层包**间接环**（SCC）棘轮（基线 1 组：SCC-A） | 探针（重建 `stream → config`）验证报"新增成员"并非零退出 |

**其余候选（按需续号；每条落地前先量当前违例数）**

| 候选 | 断言 | 现状 |
|---|---|---|
| R12 模块方向 | `platform`（`common`）不依赖业务域；`engine` 不依赖域 | 待档 A/B 模块化落地（L2→L3 已有脚本棘轮，基线 6 条） |
| R13 controller 不经 mapper | `..controller..` 不依赖 `..mapper..` | 实测 **6 处**（`embed`/`auth/apikey`/`wiki`×2/`initialization`/`system`）；wiki 两处随 C2 门面化消失 ⇒ 届时按 R6-R9 基线模式棘轮化 |
| R14 装配层单向 | 域不得依赖 `config` | 已由脚本 R2 覆盖（当前 0 包） |
| R15 顶层包无环 | `slices(...).beFreeOfCycles()` | 已由脚本 R1b 覆盖；ArchUnit 版留待模块化后 |

## 5. 风险与缓解

| 风险 | 说明 | 缓解 |
|---|---|---|
| Spring 配置属性漏注册 | `@ConfigurationPropertiesScan` 是**白名单**，模块搬迁易漏 ⇒ 启动期静默少绑定 | 每次搬迁把包名同步进白名单；新增 ArchUnit 规则：含 `@ConfigurationProperties` 的类必须落在白名单包内（可用"清单比对"脚本） |
| MyBatis 扫描 | `@MapperScan("com.ragagent.**.mapper")` 依赖包名约定 | R-B2 固化；mapper XML（若有）随模块搬 `resources` |
| 循环依赖在启动期才暴露 | 模块化后若 bean 依赖成环，Spring 报错在运行期 | 档 C 每批跑 `acceptance.sh`/相关 `ab-*.sh` 冒烟（现有 ~25 个脚本） |
| 事件总线跨模块 | 自研 EventBus + Spring 事件混用，模块拆分后监听器装配面变化 | 保留 `event/` 在 `:infra`，事件类型（`event.Topic`/payload）作为共享契约（platform 或 infra 的 api） |
| 构建时长/配置开销 | 6 模块后 Gradle configuration 变慢，但增量编译/测试并行变好 | 先 6 模块；`org.gradle.parallel=true`、构建缓存；必要时启用 configuration cache |
| 测试搬迁 | 418 个测试类按模块分布（含大量 Spring 上下文测试） | 每批"同模块同搬"，夹具（`test/resources/contracts`）保持单点，不复制 |
| 冻结面误伤 | 换锚已收官，模块化不得顺带改契约 | 非目标（§6）；闸门里保留 `check-json-key-case.py --strict`、`check-fe-contract-keys.py` |

## 6. 非目标（本阶段明确不做）

- 不改 HTTP 契约 / 冻结面（connector 线格式、事件载荷、tenantconfig jsonb、provider 请求体、SearchParams…）。
- 不拆前端（`frontend/` 维持单仓单包）；不动 `docreader/`（Go 容器）与 `mcp-server/`。
- 不引入微服务 / 多仓库 / 独立部署单元：**仍单 Boot jar**（多模块只是编译期边界）。
- 不为"分模块"而重命名既有包（包名保持，模块 = 包集合）。

## 7. 批次建议（可作 §15.1 后续排期输入）

| 批次 | 内容 | 规模 | 前置 |
|---|---|---|---|
| **B91** ✅ 完成（2026-10-08） | 解 C1（`StreamProperties` 搬家，SCC-B 破环）+ 守卫补 SCC 棘轮 + ArchUnit R10/R11（含红态探针） | 小 | 无 |
| **B92** | 端口化 C4/C5/C7（`auth` 依赖下沉：apikey scope、tenantconfig、TenantService） | 中 | 无 |
| **B93** | 端口化 C6 + C8（`retrieval→auth`、`audit→wiki`）⇒ SCC-A 消失 | 小 | B92 |
| **B94** | 门面化 C2（`wiki→knowledge` 43 处，引入 `KnowledgeBaseLookup`） | 大 | 无（可与 B92/93 并行） |
| **B95** | 档 A 模块化（`:platform` + `:infra`） | 中 | B91 |
| **B96** | 档 B 模块化（`:engine`，清 R3 残项 6 条） | 中 | B93/B95 |
| **B97+** | 档 C 域模块（按 §3.3 分组逐批） | 大 | B94/B96 |

> 每批独立全绿 + 回填 §15.1 与 `batch-records.md`；批内不改契约（非目标）。
