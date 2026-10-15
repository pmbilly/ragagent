# A③「实体上提」欠债清单与收窄路径（2026-10-15）

> **判据**：整改方案 **§5.1/§5.2**（`contracts` ＝ 纯 JDK：公开 API / SPI / **不可变载荷**；`platform` 不含业务状态机；域间只经 `contracts`）＋ HANDOFF **§12.0**。
> **登记**：本欠债由 A③「域间边清零」产生——该运动达成了 **①档 0 条 / 0 类**（边 64 → 7、类 317 → 52），但**载体偏离判据**：为清边把持久化实体上提到 L1。
> **门禁**：`scripts/check-contracts-purity.py`（C1/C2/C3，只许减）已上线，**通道已冻结**（禁止再上提实体）。

## 1. 现状总量（本文件所有数字为 2026-10-15 实测）

| 实体 | 行数 | 跨域主源 | 跨域测试 | 域内 | 已有 `contract.*` 视图可对齐 |
|---|---|---|---|---|---|
| `Tenant` | 145 | **52** | 7 | 3 | `common.tenant.TenantStorageView` · `TenantQuotaView`（部分） |
| `User` | 91 | **52** | 7 | 20 | 无 |
| `Knowledge` | 139 | **31** | 11 | 42 | `contract.knowledge.KnowledgeView` |
| `KnowledgeBase` | 219 | **27** | 7 | 41 | `contract.knowledge.KnowledgeBaseView` |
| `Model` | 80 | **17** | 5 | 10 | `contract.model.ModelFacts` · `common.model.ModelRowView` |
| `Session` | 292 | **17** | 9 | 17 | `contract.session.PipelineMessageView`（部分） |
| `Chunk` | 139 | **16** | 10 | 33 | `contract.knowledge.ChunkView` |
| `Message` | 394 | **13** | 12 | 28 | `contract.session.PipelineMessageView` 族 |
| `StorageBackend` | 56 | **8** | 3 | 6 | 无 |
| `WikiPage` | 297 | **7** | 5 | 24 | 无 |
| `WikiPageIssue` | 102 | **4** | 0 | 7 | 无 |
| `KnowledgeTag` | 63 | **3** | 1 | 13 | 无 |
| `MessageSuggestionSet` | 281 | **2** | 0 | 5 | 无 |
| `AuthToken` | 48 | **1** | 0 | 4 | 无 |
| **合计** | — | **≈252** | **≈77** | — | 7 个实体已有一半对齐面 |

另有 **12 个域侧 TypeHandler** 随实体上提（`ModelParametersTypeHandler` · `APIPrincipalConfigTypeHandler` · `WikiStringListTypeHandler` · `session/domain` 9 个）；`platform/persistence/` 下 2 个属**合法**基础设施，不在欠债内。

## 2. 四条关键观察（决定分批）

1. **消费面普遍很窄**：绝大多数跨域消费者只用 1~4 个 getter（如 `WikiKbAccessGuard` 只用 `getTenantId`、`DataSourceController` 只用 `getTenantId`、`ImQaRunner` 只用 `getId/getContent`）⇒ **视图化在技术上可行**，成本主要是"逐个消费者换类型 + 字段核对"。
2. **"宽字段 + 写面"才是真 K 级**：`InitResponses` 需 **16 个 `Model` 字段**、`SystemAdminUserService` 是用户/令牌**写面** ⇒ 这类按 §12.0 判据应走「**用例移入属主域**」，不是造宽视图。
3. **一半实体已有现成视图**（`Chunk`/`Knowledge`/`KnowledgeBase`/`Model`/`Message`/`Session`/`MessageAttachment`）⇒ **最便宜的第一批**。
4. **无视图且跨域消费小的 6 个**（`AuthToken` 1 · `MessageSuggestionSet` 2 · `KnowledgeTag` 3 · `WikiPageIssue` 4 · `WikiPage` 7 · `StorageBackend` 8）⇒ **第二批**（造窄视图，风险低）。
5. **`Tenant`(52)/`User`(52) 放最后**：跨域面最大，且其消费者多是**管理面**（`system/admin`、`auth` 管理链）⇒ 按判据应"用例归位"，属设计级工作。

## 3. 收窄路径（四批；每批可独立提交、独立全绿）

### 批 1 · 有现成视图的 6 实体（机械为主）
`Chunk` → `Knowledge` → `KnowledgeBase` → `Model` → `Message` → `Session`（按跨域面从小到大排，`Chunk` 建议打头：16 跨域主源 + `ChunkView` 已存在 + 消费字段窄）。
每实体一做：①核视图字段是否覆盖消费者用度（缺则补视图字段，**线格式不变**）②跨域消费者换视图/端口 ③实体退回 `domains/<域>/` ④TypeHandler 随行 ⑤C2 计数 −1。

### 批 2 · 无视图且消费小的 6 实体（造窄视图）
`AuthToken` · `MessageSuggestionSet` · `KnowledgeTag` · `WikiPageIssue` · `WikiPage` · `StorageBackend`。
每实体先定视图字段（= 消费者实际用度，不照搬实体），再同上。

### 批 3 · 管理面/宽字段（**设计级，须先立项**）
- `Model` 的 init 面（`InitResponses` 16 字段 + 写面）⇒ **用例移入 `model` 域**（或 model 侧提供管理用宽载荷 + 写端口）
- `Tenant`(52) / `User`(52) ⇒ **管理用例归位属主域**（§8.9 规划：租户/成员/RBAC 管理面在 `auth`；system 的用户管理面按 §8.9 在 `system` ⇒ 其 auth 写入改经 auth 端口）
- `system→auth{SystemAdminUserService}` 与 `auth→tenant{TenantService}` 两条 ②owner 边**保持登记不动**（§8.9 规划态）

### 批 4 · 收尾
- 12 个域侧 TypeHandler 全部随实体回域；
- 复跑 C1/C2/C3 `--write`，把基线收紧到"实体 0 / 域侧 handler 0"；
- 此时 `common` 只余纯契约与平台内容 ⇒ **阶段 6 第一步（`contracts`/`platform` 分离）具备前提**。

## 4. 统一验收协议（每批逐条）

1. `env -u SYSTEM_AES_KEY ./gradlew spotlessCheck build` 全绿 + **4991 / 失败 0 / 跳过 4** 基线不变；
2. **14 守卫全绿**，其中 C1/C2/C3 **只许减**（实体 14 → 13 → … → 0）；
3. `check-contract-sync` 绿（**线格式不变**——这是本专项的红线）；
4. 定向测试（受影响域）+ dev 重启 + 冒烟（受影响的 HTTP 面）；
5. **R10 边数不增**（视图属 L1 ⇒ 消费者改视图后边应保持 0）；
6. 单独提交，提交信息标注 `实体收窄·批N`。

## 5. 风险与对策

| 风险 | 对策 |
|---|---|
| **线格式**：部分 API 直接序列化实体（`KnowledgeBaseResponse.from(kb)` · `MessageController.loadMessages` 返回 `List<Message>` · `SessionListItem` 平铺字段）| 视图字段**等价**为前提；`check-contract-sync` 金片为证；必要时视图保留同名字段 |
| jsonb 列的 `typeHandler` 依赖 | 映射留在域内（视图只承载**已解析的值**） |
| 宽字段面（init / admin）返工 | **不造宽视图**，走"用例归位"（批 3，先设计） |
| 收窄过程中又出现"最省事的短路" | C1/C2/C3 门禁已冻结通道；新增实体上提必红 |

## 6. 首批（批 1 第一子批）建议

**`Chunk`**：跨域主源 16（`agenttools` 3 · `boot` 3 · `embedchannel` 2 · `engine` 2 · 其余分散）、`contract.knowledge.ChunkView` 已存在、消费者字段窄（`getId`/`getContent`/`getKnowledgeBaseId`/`getTenantId`/`getMetadata`）。
预期：1 个子批（约 20 文件）⇒ C2 实体 14 → 13、`common` 依赖根不变；**边数不增**。
