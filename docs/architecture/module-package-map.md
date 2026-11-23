<!-- 由 scripts/gen-module-map.py 生成；不要手改。 -->
# 模块与包映射

这张表回答"类/包属于哪个模块"，包内职责见 [`packages.md`](packages.md)，
整体分层与设计原则见 [`overview.md`](overview.md)。

## 6 个模块

| 模块 | 主码包根 | 包数 | Java 文件 | 控制器 |
|---|---|---:|---:|---:|
| `:contract` |  | 0 | 0 | 0 |
| `:framework` | `framework` | 22 | 165 | 1 |
| `:model-runtime` | `embedding`、`llm`、`modelcontext`、`modelruntime`、`rerank` | 25 | 234 | 0 |
| `:retrieval-engine` | `retrieval` | 17 | 141 | 0 |
| `:agent-engine` | `agent`、`agentengine` | 11 | 126 | 0 |
| `:app` | `(根包)`、`agentmanagement`、`agenttools`、`audit`、`auth`、`channel`、`config`、`datasource`、`favorite`、`initialization`、`knowledge`、`mcp`、`memory`、`model`、`session`、`storage`、`vectorstore`、`websearch` | 292 | 1981 | 51 |

## 17 个业务域（domains）

| 域 | 主码包 | 文件 | 控制器 |
|---|---|---:|---:|
| `agentmanagement` | `com.ragagent.agentmanagement` | 32 | 2 |
| `agenttools` | `com.ragagent.agenttools` | 68 | 0 |
| `audit` | `com.ragagent.audit` | 33 | 1 |
| `auth` | `com.ragagent.auth` | 182 | 8 |
| `channel` | `com.ragagent.channel` | 147 | 3 |
| `config` | `com.ragagent.config` | 26 | 0 |
| `datasource` | `com.ragagent.datasource` | 147 | 2 |
| `favorite` | `com.ragagent.favorite` | 17 | 1 |
| `initialization` | `com.ragagent.initialization` | 11 | 1 |
| `knowledge` | `com.ragagent.knowledge` | 559 | 9 |
| `mcp` | `com.ragagent.mcp` | 176 | 4 |
| `memory` | `com.ragagent.memory` | 110 | 1 |
| `model` | `com.ragagent.model` | 47 | 4 |
| `session` | `com.ragagent.session` | 228 | 8 |
| `storage` | `com.ragagent.storage` | 103 | 3 |
| `vectorstore` | `com.ragagent.vectorstore` | 25 | 1 |
| `websearch` | `com.ragagent.websearch` | 68 | 3 |

## 查找规则

1. **先看包根**：`com.ragagent.contract.*` → `:contract`；`platform` → `:framework`；
   `agent` → `:agent-engine`；`retrieval` → `:retrieval-engine`；
   `embedding` / `llm` / `modelcontext` / `modelruntime` / `rerank` → `:model-runtime`；
   其余 17 个包根 → `:domains`；`com.ragagent.config` 与根包 → `:boot`。
2. **HTTP 路由**在 `backend/app/src/main/java/com/ragagent/<域>/controller/`；
   跨模块公开类型在 `contract/`，端口实现与全局 Bean 在 `boot/config/`。
3. **测试**：业务域测试住在 `backend/app/src/test/`；HTTP 集成测试住在 `boot/src/test/`；
   共享录制/桩夹具在 `backend/framework/src/testFixtures/`。

## 刻意例外

- `:model-runtime` 有 5 个主码包根（`embedding` / `llm` / `modelcontext` / `modelruntime` / `rerank`），
  按能力族拆分，不与模块同名。
- `:boot` 的测试树有 0 个业务包根：集成测试必须从 HTTP 面进，只能住在组合根。
- `domains` 测试树仍有 10 个跨模块测试（platform 5、model-runtime 4、contract 1），
  依赖 domains 载荷，迁走会形成反向边；登记在 `scripts/check-module-graph.py` 的
  `MISPLACED_TEST_ALLOW`（带理由，只许减）。
- 测试夹具包为 `com.ragagent.fixtures`；`com.ragagent.common` / `com.ragagent.support`
  是已解散模块的旧包，守卫禁止回流。

## 相关

- [`packages.md`](packages.md)：367 份 `package-info` 的包级职责索引。
- [`overview.md`](overview.md)：模块依赖图、设计原则、代码在哪。
