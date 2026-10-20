# L 系列：业务链路对齐整改（v0.6.x）

- 出处：`docs/architecture-review/02-business-link-assessment.md`（本地草稿，已被 `.gitignore` 排除，故结论落本文）
- 定位：不动七模块 / 20 域骨架；只处理"同一业务事实在多处登记"与"文档承诺不存在的功能"
- 与 K 系列关系：K 已收口（文档真实性 / 装配点 / 引擎注册 / 实体渗漏 / LLM 目录）；L 处理剩下的业务链路错配

## 1. 报告核实

整体 verdict（链路健康、病灶在多点登记）成立。四处需要更正：

| # | 报告说法 | 实测 | 处置 |
|---|---|---|---|
| 1 | "评测"全仓（含文档）检索不到，但功能口径列了它 | 代码与路由确实为 0；**承诺就在 README**（L48 能力清单 + L227 `/api/v1/evaluation` 端点表）；另 `KnowledgeQaEventPort` 注释残留"评测域" | 已删 README 两处（2026-10-20 拍板：评测不做） |
| 2 | catalog 空挂 17 类型，"用户配了即失败" | 前端**硬编码 9 个 `available: true`**、0 个 false，且 `getConnectorTypes()` 无调用点——后端目录与 `/datasource/types` 未被消费 | 不是"可配不可用"，而是**后端目录/接口死面 + 前后端两份清单**。本轮不动（删公开端点风险大于收益），登记为已知冗余 |
| 3 | session 190 vs 文档 187、contract 24 vs 文档 23，"其余漂移无人看守" | 这两份是**生成式文档**（`gen-module-map` / `gen-domain-index`），当前已自动同步为 199 / 24，且 `--check` 守卫在跑 | "无人看守"不成立；手写数字才需要守卫（K0 已补 D2/D4/D5） |
| 4 | domains 1559 / contract 353 / 33 个 `*Port` | 1572 / 360 / **40**（H6-1 拆窄端口 +6、G2 `ParserEngineCatalogPort` +1） | 报告后增长，不是缺陷 |

另：boot 的 `@Configuration` 精确 8 个（全部真装配，报告写 11/6）；domains 的"19 个"含 `@ConfigurationProperties`，真装配 10 个（与 01 报告同源误差）。

## 2. 采纳与否决

| 报告项 | 处置 | 理由 |
|---|---|---|
| P1-2 provider 单目录 | 已完成（K3） | 目录由 `ProviderRegistry.list()` 生成 |
| P1-5 引擎常量单源化 | **采纳 → L1** | 9 vs 7 属实；纯常量、零行为风险 |
| P1-1 embedding 单路径 | **采纳 → L2** | `EmbedderClient`（164 行）是 `EmbeddingPort` 唯一实现且住在 knowledge 域；删它需 model-runtime 提供端口实现。收益真实，但 pg 路径的错误文案/分批/桩测试都在，需独立一批 |
| P1-3 工具注册端口化 | **降级 → L3 只加守卫，端口化暂缓** | 工具是**请求级构造**（targets/reranker/ownerId/sessionId），且分发散在 `createTool`/`createWikiTool`/`createWebTool` 三个 switch；端口化要连 7 个接缝适配器一起搬。该边族内冻结、模块拆分已推迟，投入产出不划算。先消灭"加工具漏登记"的静默风险 |
| P1-4 RBAC 单表化 | **否决合并 → L3 守卫扩面** | 两张表主语不同（JWT 租户角色 vs API Key 能力）、路径语法不同（Ant vs gin 模板）、语义不同（首命中 vs 默认拒绝）。合并会造双语义 god 表。真实风险是 `check-route-policy.py` 只校验 RBAC 表 |
| P2-1 IM 装配归域 | 暂缓 | 技术上可行、收益中等；K4 刚登记为组合根装配点，移动需同步清单。等 L3 守卫稳定后再评估 |
| P2-2 switch 改注册表 | 部分完成（K1） | `EngineFactory` 已合表；`FileServiceFactory` 保持 switch（8 个分支形状异构，注册表收益 < 抽象成本） |
| P2-3 命名治理 | 仅做 `Skill(s)` 一对（并入 L3） | `KnowledgeQaController` 改名牵动生成式文档与测试，收益低 |
| P3-1 评测 | **已做：删 README 承诺** | 2026-10-20 用户拍板；补做属新产品功能，不是整改 |
| P3-2 catalog 空挂 | 更正 + 不做 | 见上表 #2 |
| P3-3 用户归口 | 不做 | 租户自治 vs 平台超管是产品语义差异，合并制造新耦合 |
| §8.4 拆 agent-runtime | 不做 | 与 ADR-001 / B211 实证冲突；前置条件（工具边清零）已暂缓 |

## 3. 批次

| 批 | 内容 | 验收 | 状态 |
|---|---|---|---|
| **L0** 文档与口径 | README 删"评测"两处；本报告 4 处更正落档 | D1~D5 守卫绿 | ✅ 2026-10-20 |
| **L1** 引擎常量单源化 | 新增 `contract.vectorstore.VectorEngineTypes`（9 全集 + 7 可配置 + 差额=env 引擎）；`EngineTypes` 改别名、`VectorStoreEngines` 引用契约；两条测试钉住 | 43 守卫绿；常量测试绿；金片零变化 | ✅ 2026-10-20 |
| **L3** 扩展清单守卫 | ① `ToolDefinitions` ↔ `agenttools` 实现 ↔ 三个分发 switch 一致性；② `check-route-policy.py` 扩到 API-Key 策略表（幽灵规则）；③ `SkillCatalogController`/`SkillsCatalogController` 改名 | 新守卫各配红态探针 | 待做 |
| **L2** embedding 单路径 | model-runtime 提供 `EmbeddingPort` 实现；删 `knowledge/client/EmbedderClient`；pg 路径改走 `ModelRuntime` | 金片零变化；embedding 桩测试逐条对拍 | 待做 |

**顺序**：L0 ✅ → L1 ✅ → L3 → L2。

## 4. 不做

- 不合并两张 RBAC 表（P1-4）
- 不把工具装配端口化（P1-3，暂缓；先守卫）
- 不拆 agent-runtime 族、不并 auth/system 用户管理、不动 IM 装配
- 不删后端连接器目录/端点（公开接口，删了风险大于收益）
