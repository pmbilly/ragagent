# Go 录制金片（golden fixtures）

录制金片常量类（`*Golden.java`）里的 JSON 已外置为资源，Java 只保留常量名与加载代码。

## 布局

```
platform/src/testFixtures/resources/golden/
├── agent-45a.json / .sha256
├── agent-45b.json / .sha256
├── agenttools-45c.json / .sha256
├── agent-46.json  / .sha256
├── agent-46b.json / .sha256
├── chat-pipeline-46c.json / .sha256
└── modelcontext-46a.json  / .sha256
```

- 加载器：`com.ragagent.platform.fixtures.GoldenResources`（platform testFixtures）。
- 每次加载按同名 `.sha256` 校验；手改资源一个字节，引用它的测试立即失败。
- 常量字段名保持原 API（`ToolContractGoldenA.R_*`、`ChatPipelineGolden.constant(group, key)`），测试代码无需改。

## 重录

> Go 仓已冻结、录制脚本已删除（2026-10-21）。"重录"现在的含义是**按新实现同步常量**，不是重跑录制程序。

1. 从当前 Java 实现导出同一 bundle 的 JSON（key 规则不变：常量名，46C 为 `group/key`）；
2. 重新生成 `<bundle>.json.sha256`；
3. 跑引用该 bundle 的测试与全量闸门（`mvn -B verify`）。

禁止直接手改 JSON 或 `.sha256`。新增 bundle 时同步在 `platform/src/testFixtures/resources/golden/` 放置两个文件，并让使用方模块依赖 `platform` 的 `test-jar`（Maven 侧的 `testFixtures` 等价物）。

## 类名

行为化命名（O16）：

| 旧名 | 新名 | 用途 |
|---|---|---|
| `GoRecording` | `AgentPromptGolden` | 摘要/提示词字节 |
| `GoRecording45A/B/C` | `ToolContractGoldenA/B/C` | 工具契约录制 |
| `GoRecording46A` | `ModelContextGolden` | modelcontext 回放 |
| `GoRecording46B` | `AgentExecutionGolden` | agent 执行事件 |
| `GoRecording46C` | `ChatPipelineGolden` | 检索管线回放 |

资源 bundle 名（`agent-45a` 等）是录制产物标识，不改。
