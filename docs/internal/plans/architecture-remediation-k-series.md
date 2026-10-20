# K 系列：扩展面与文档真实性收敛（v0.6.x 并行专项）

- 出处：`docs/architecture-review/01-architecture-assessment.md`（本地草稿，已被 `.gitignore` 排除，故结论落本文）
- 定位：**不动七模块拓扑、不拆 domains、不加新模块**；只收"扩展注册面 / 装配点可见性 / 跨域实体 / 文档计数"四件结构性摩擦
- 与 H 系列关系：正交，可并行；每批独立全绿，闸门同 H（43 守卫 + 全量测试 + spotlessCheck）

## 1. 报告核实结论

实跑复核（2026-10-20）：**拓扑与契约类结论全部属实**——16 条主码模块边、`boot → agent-engine` 主码 0 处、契约 0 违例、域间 6 边/33 类（clearable 档 0）、包环 0、42 条守卫。

四处需要更正，后续批次以本文为准：

| # | 报告说法 | 实测 | 处置 |
|---|---|---|---|
| 1 | "1,349 份金片"漂移，实测 1,433 | **不实**：`contracts/*.json` 恰 1,349；1,432 个文件里含 `.body`/`.headers`/`.bin`/`.tsv` 配套件与清册，一个样本被数成 2–3 份；另有 `jieba_baseline.json` 不是契约金片。真实样本 = 1,349 JSON + 5 非 JSON 组 | 守卫按"JSON 数 + `.body` 数"两个口径钉 |
| 2 | "全部模块合计 51 个 HTTP 控制器"漂移 | 数字对（51 个业务控制器），全仓 52 个 `@RestController`（含 `/health`）。是**措辞**不是计数 | README 改为"51 个业务控制器（另有 1 个 `/health` 探针）" |
| 3 | domains 1556 文件 / contract 353 文件 / `session/service` 26 文件 | 已随 H 批次增长为 1572 / 360 / 27 | 不写死在文档里，只有守卫看守的数字才落文档 |
| 4 | domains 内 19 个 `@Configuration` | 精确 10 个 `@Configuration`（全部是真装配）+ 7 个 `@ConfigurationProperties`；"19"是子串匹配把 javadoc 与嵌套注解算进去了 | K4 按 10 个登记 |

## 2. 采纳与否决

| 报告项 | 处置 | 理由 |
|---|---|---|
| T1 统一"类型→工厂注册表"协议 | **缩窄采纳**：每族一张表，**不建** `:platform` 通用 `CapabilityRegistry<T>` | 只有向量库/存储两个消费方，配置形状不同；通用件会把注册顺序、错误文案、SSRF 绑定从 1 个文件摊到 3 个模块。真实缺陷是同一 engine-type 维度有 **4 张表**（`EngineFactory` 构造 + 运行时 SSRF、`VectorStoreConfigService` 必填校验 + 保存面 SSRF）。**更正报告一处措辞**：两套 SSRF 策略虽跨模块各写一遍，但**两者都是 fail-closed**（漏登记即报错，不是静默放行）⇒ 属口径一致性成本，不是安全漏洞 |
| T2 装配权集中 | **只做登记**（ADR-003 a）；**否决**把 `QaWiring` 上移 boot | `QaWiring` 装配 17 个 `session.rag.plugin` + 5 个域内协作者；上移等于把域内实现面拉进组合根（boot 19→25+ 文件，全是 session 内部类型） |
| T3 跨域实体清零 | **采纳**，但**不新造** `AgentConfigView` | `contract.agent.AgentLookup.AgentView` 就是为此建的（注释写明"不再传实体"）；只差 `tenantId`——session 有两处安全相关读取：`QaKbScope` 定检索租户、`QaMentionTagScope` 做租户相等判断 |
| T4 文档计数守卫 | **采纳**（按上表口径） | 注释里的计数一律删除，能机械校验的数字才写进 README |
| T5 LLM 双目录单源化 | **采纳单源化**；`validateConfig` 接生产校验**暂缓** | `ProviderInfo` 已带 `displayName/description/defaultUrls/modelTypes`，目录可由 `ProviderRegistry.list()` 生成；`validateConfig` 接生产路径会改变模型保存行为，先登记 `known-gaps.md` |

## 3. 批次

| 批 | 内容 | 验收 | 状态 |
|---|---|---|---|
| **K0** 文档口径 | README/overview/构建注释五处计数对齐；`check-doc-truth.py` 扩 D2（业务控制器 + 探针）/D4（金片双口径）/D5（provider 注册数），三条各配红态探针 | 守卫绿；红态探针三条均红并复原 | ✅ 2026-10-20 |
| **K4** 装配点登记 | `overview.md` 增"装配点清单"（22 个装配类按组合根/域内/模块内三档登记）；登记"agent-engine 经根包组件扫描装配"；新增 `check-wiring-points.py`（W1 集合相等 / W2 分类落点 / W3 理由 / W4 文档同步） | 四条判据红态探针均验证 | ✅ 2026-10-20 |
| **K1** 引擎注册合表 | `EngineFactory`：9 个引擎合成一张 `SPECS`（构造 + 地址策略同行登记），`EngineSpec` 强制"声明地址字段或显式免检"，否则类初始化失败；删掉不可达的 `unsupported engine type` 分支；新增"每个 `ENGINE_*` 常量必须有注册项"的完整性测试（摘掉一条注册即红）。`FileServiceFactory`：删 `IMPLEMENTED`/`PENDING` 死集合与配套不可达分支，测试断言改为行为断言 | 文案逐字不变；43 守卫绿 · **5026 / 0 / 4** · build 2m16s；**未做**跨模块 SSRF 元数据共享（理由见 §2 T1） | ✅ 2026-10-20 |
| **K2** 实体渗漏清零 | `AgentView` 补 `tenantId`（视图原就为此设计，未新造投影）；8 个 session 文件改持视图，两处从未使用的实体参数一并删除；`ResolvedAgent` 删恒 false 的 `sharedAgentReadOnly` 并清 6 处消费点（含死分支与过滤方法）；`check-domain-contracts.py` 增 E1"跨域 `.domain.` import 即红"，基线 0。**副作用**：域间边参考类 33 → 27（session→agentmanagement 11 → 5） | 跨域实体 import 0 · 43 守卫绿 · **5026 / 0 / 4** · build 2m28s | ✅ 2026-10-20 |
| **K3** LLM 目录单源化 | `ModelProviderCatalog.ENTRIES` 由 `ProviderRegistry.list()` 生成（展示名/描述/默认 URL/模型类型全部来自 `ProviderInfo`，`toFrontend` 只做表示层映射）；`ProviderCatalogParityTest` 删除（已同义反复），换成 `ProviderRegistryCompletenessTest`（`ProviderName` 全集 == `list()`）；`check-module-graph` 的错位测试登记 10 → 9；`validateConfig` 登记 `known-gaps.md` 第 19 条 | `model-providers.json` / `model-providers-chat.json` 金片字节一致；43 守卫绿 · **5026 / 0 / 4** | ✅ 2026-10-20 |

**顺序**：K0 ✅ → K4 ✅ → K1 ✅ → K2 ✅ → K3 ✅（K 系列收口）。

**收口读数（2026-10-20）**：43 守卫 · 5026 / 0 / 4 · 跨域 `.domain.` 实体 import 0 · 域间边参考类 33 → 27 · 错位测试登记 10 → 9 · 新增 provider 从 4 处降到 3 处（Provider 类 + 枚举 + 注册表一行，目录层零改动）。

## 4. 不做

- 不建跨能力通用注册表（T1 缩窄理由）
- 不搬 `QaWiring` 上移 boot（T2）
- 不为"实体字段清单"造 god 端口——复用既有窄视图；这条与 `domain-contracts.baseline.json` 的立规理由一致
- 不把"金片数"写成"1,400+"这类范围表述（守卫要能精确报红）
