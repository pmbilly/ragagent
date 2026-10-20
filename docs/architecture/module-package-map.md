<!-- 由 scripts/gen-module-map.py 生成；不要手改。 -->
# 模块与包映射

这张表回答"类/包属于哪个模块"，包内职责见 [`packages.md`](packages.md)，
整体分层与设计原则见 [`overview.md`](overview.md)。

## 7 个模块

| 模块 | 主码包根 | 包数 | Java 文件 | 控制器 |
|---|---|---:|---:|---:|
| `:contract` | `contract` | 33 | 353 | 0 |
| `:platform` | `platform` | 25 | 194 | 1 |
| `:model-runtime` | `embedding`、`llm`、`modelcontext`、`modelruntime`、`rerank` | 20 | 152 | 0 |
| `:retrieval-engine` | `retrieval` | 15 | 95 | 0 |
| `:agent-engine` | `agent` | 9 | 67 | 0 |
| `:domains` | `agentmanagement`、`agenttools`、`approval`、`audit`、`auth`、`datasource`、`embedchannel`、`favorite`、`im`、`initialization`、`knowledge`、`mcp`、`memory`、`model`、`session`、`storage`、`system`、`vectorstore`、`websearch`、`wiki` | 249 | 1556 | 51 |
| `:boot` | `(根包)`、`config` | 2 | 19 | 0 |

## 20 个业务域（domains）

| 域 | 主码包 | 文件 | 控制器 |
|---|---|---:|---:|
| `agentmanagement` | `com.ragagent.agentmanagement` | 27 | 3 |
| `agenttools` | `com.ragagent.agenttools` | 58 | 0 |
| `approval` | `com.ragagent.approval` | 7 | 0 |
| `audit` | `com.ragagent.audit` | 20 | 1 |
| `auth` | `com.ragagent.auth` | 118 | 6 |
| `datasource` | `com.ragagent.datasource` | 147 | 2 |
| `embedchannel` | `com.ragagent.embedchannel` | 25 | 1 |
| `favorite` | `com.ragagent.favorite` | 13 | 1 |
| `im` | `com.ragagent.im` | 102 | 2 |
| `initialization` | `com.ragagent.initialization` | 13 | 1 |
| `knowledge` | `com.ragagent.knowledge` | 257 | 8 |
| `mcp` | `com.ragagent.mcp` | 148 | 4 |
| `memory` | `com.ragagent.memory` | 88 | 1 |
| `model` | `com.ragagent.model` | 35 | 3 |
| `session` | `com.ragagent.session` | 187 | 8 |
| `storage` | `com.ragagent.storage` | 57 | 3 |
| `system` | `com.ragagent.system` | 18 | 2 |
| `vectorstore` | `com.ragagent.vectorstore` | 20 | 1 |
| `websearch` | `com.ragagent.websearch` | 52 | 3 |
| `wiki` | `com.ragagent.wiki` | 164 | 1 |

## 查找规则

1. **先看包根**：`com.ragagent.contract.*` → `:contract`；`platform` → `:platform`；
   `agent` → `:agent-engine`；`retrieval` → `:retrieval-engine`；
   `embedding` / `llm` / `modelcontext` / `modelruntime` / `rerank` → `:model-runtime`；
   其余 20 个包根 → `:domains`；`com.ragagent.config` 与根包 → `:boot`。
2. **HTTP 路由**在 `domains/src/main/java/com/ragagent/<域>/controller/`；
   跨模块公开类型在 `contract/`，端口实现与全局 Bean 在 `boot/config/`。
3. **测试**：业务域测试住在 `domains/src/test/`；HTTP 集成测试住在 `boot/src/test/`；
   共享录制/桩夹具在 `platform/src/testFixtures/`。

## 刻意例外

- `:model-runtime` 有 5 个主码包根（`embedding` / `llm` / `modelcontext` / `modelruntime` / `rerank`），
  按能力族拆分，不与模块同名。
- `:boot` 的测试树有 24 个业务包根：集成测试必须从 HTTP 面进，只能住在组合根。
- `domains` 测试树仍有 10 个跨模块测试（platform 5、model-runtime 4、contract 1），
  依赖 domains 载荷，迁走会形成反向边；登记在 `scripts/check-module-graph.py` 的
  `MISPLACED_TEST_ALLOW`（带理由，只许减）。
- 测试夹具包为 `com.ragagent.fixtures`；`com.ragagent.common` / `com.ragagent.support`
  是已解散模块的旧包，守卫禁止回流。

## 相关

- [`packages.md`](packages.md)：353 份 `package-info` 的包级职责索引。
- [`overview.md`](overview.md)：模块依赖图、设计原则、代码在哪。
