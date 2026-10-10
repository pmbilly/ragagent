# settings 模块手册

> 数据口径：2026-10-11 实测（4 文件 / 463 行，`domains/src/main/java/com/ragagent/settings/`）。

## §0 一分钟速览

跨域共享的**设置类**小包，三件事：

| 类 | 职责 |
|---|---|
| `ConversationProperties` | `conversation.*` 前缀的对话配置（含 prompt 模板装载） |
| `SystemSettingGateway` | 系统设置的**只读端口**（`getString/getBool/getInt`），供 auth 等域跨域读取 |
| `SystemSettingRegistry` | `system_settings` 表的 **in-code 注册表**（17 个键，2026-10-11 复核实数）："哪些 key 合法 + 类型 + ENV 回退名 + 内置默认值"的唯一权威 |

零 controller、零 mapper——这不是一个"域"，是**被多个域共享的契约与注册表面**，因此放在顶层包而不是任何业务域内。

## §1 为什么在这个位置（依赖判据）

- `ConversationProperties` 原在 `config`（组合根），knowledge/session/initialization 都要读它——留在装配层会形成"域 → config"反向依赖（config 只出不进），2026-09-30 下沉至此。
- `SystemSettingGateway`：auth 的注册/邀请流程要读系统设置，而 `system` 依赖 `auth`——若 auth 直接注入 `SystemSettingService` 就成环。端口放在这里、由 system 侧实现，依赖方向变为"域 → 端口 ← 实现"，单向。
- `SystemSettingRegistry` 被 `auth/controller/AuthController` 与 `system/service/SystemSettingService` 共同使用——它是两域共享的**键词汇**，放任何一侧都会造反向依赖。

## §2 注册表语义（改设置面前必读）

- Update **拒绝任何不在表内的 key**；List/Get 对没有 DB 行的 key 产出**虚拟行**（值 = `ENV → cfg → default` 三层回退的生效值）。
- 每条 `Spec` 携带：`type` / `envName`（ENV 回退名）/ `defaultValue` / `enumOptions` / `category` / `description` / `requiresRestart`。
- **`description` 文案是响应体的一部分，不是注释**——改它会改 API 输出，别顺手润色。
- 新增设置键：在 `SystemSettingRegistry` 加 `spec(...)` 条目（类型 + ENV 回退 + 默认值），不要在消费方散落默认值。

## §3 坑与测试

- 改 `SystemSettingRegistry` 的键集会影响 system 域的 List/Get/Update 契约金片——改完跑 system 相关 `*ContractTest`。
- `getString/getBool/getInt` 的回退顺序（DB → env → 默认值）在 `SystemSettingGateway` 的 javadoc 钉着，实现侧（system）不得打乱。
- 相关手册：[`system-module-guide`](system-module-guide.md)（写侧与列表端点在这里）、[`auth-module-guide`](auth-module-guide.md)（读侧消费方）。
