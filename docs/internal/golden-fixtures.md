# Go 录制金片（golden fixtures）

`GoRecording*.java` 里的 JSON 已外置为资源，Java 只保留常量名与加载代码。

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
- 常量字段名保持原 API（`GoRecording45A.R_*`、`GoRecording46C.constant(group, key)`），测试代码无需改。

## 重录

1. 重跑 Go 录制程序，生成新的 JSONL / JSON；
2. 用同一 key 规则生成 `<bundle>.json`（key 为常量名，46C 为 `group/key`）；
3. 重新生成 `<bundle>.json.sha256`；
4. 跑引用该 bundle 的测试与全量闸门。

禁止直接手改 JSON 或 `.sha256`。新增 bundle 时同步在 `platform/src/testFixtures/resources/golden/` 放置两个文件，并让使用方模块依赖 `testFixtures(project(":platform"))`。

## 已知遗留

类名 `GoRecording45A..46C` 仍保留 Go 录制波次编号；行为化命名与 `docs/architecture/glossary.md` 的历史代号说明已在 O16 范围。
