# 模块手册索引

> `com.ragagent` 的 **34 个顶层包**由 **31 份模块手册**覆盖（`docs/<模块>-module-guide.md`），结构与粒度对齐样板篇 `knowledge-module-guide.md`（§0 一分钟速览 → §10 地标速查）。
> 未单独立册的三个包：`modelcontext`（随 `agent-module-guide` 讲）、`webfetch`（4 个文件，见包内 `package-info`）、`engine/tracing`（与 `:common` 的 tracing 合用一册）。
> **数据口径**：**2026-10-11 实测**（`wc -l` 口径，仅 main Java）。仓库级作业规范见仓库根 `HANDOFF.md`（§12 结构地图、§13 踩坑清单、§14 逐包重构范式）；包性质判定与结构决策见 `docs/backend-package-map.md`。

全仓规模：`:boot` 13 文件/1.7k 行 · `:domains` 1,383/217k（22 包）· `:engine` 295/50k（8 包）· `:common` 219/17.5k（4 包）；52 个控制器、49 个 `*ContractTest`、1,429 个契约金片、全量测试 4,876 条。

## `:domains` 业务包（22）

| 包 | 手册 | 规模（文件/行） | 一句话 |
|---|---|---|---|
| knowledge | [knowledge-module-guide](knowledge-module-guide.md) | 218 / 2.60 万 | KB 域：文档入库 → 分块/抽取/向量化 → 检索与 FAQ 供给（**样板篇**） |
| agent | [agent-module-guide](agent-module-guide.md) | 169 / 3.07 万 | agent 引擎：执行循环、工具族（36 个工具）、管理面；`modelcontext/`（:engine）协议解析随册讲 |
| wiki | [wiki-module-guide](wiki-module-guide.md) | 156 / 2.37 万 | wiki 域：页面摄取管线（ingest/）+ 页面/文件夹/链接图 |
| datasource | [datasource-module-guide](datasource-module-guide.md) | 125 / 2.68 万 | 外部数据源连接器族（feishu/gitlab/notion/rss/yuque/ima…，入向耦合近零） |
| session | [session-module-guide](session-module-guide.md) | 114 / 2.22 万 | 会话域：会话/消息 CRUD + SSE 问答流（REST 与事件面键全 camel） |
| mcp | [mcp-module-guide](mcp-module-guide.md) | 117 / 1.28 万 | MCP 服务器接入：协议族（protocol/）+ OAuth（oauth/）+ 审批桥接 |
| im | [im-module-guide](im-module-guide.md) | 80 / 1.77 万 | IM 渠道域：渠道一子包 + runtime 协作者族；回调面免 JWT 靠平台验签 |
| memory | [memory-module-guide](memory-module-guide.md) | 75 / 1.28 万 | 记忆域：写入/检索/注入；recall 永不失败原则 |
| auth | [auth-module-guide](auth-module-guide.md) | 83 / 1.13 万 | 认证域：登录/租户/成员/邀请 + apikey 子域 |
| storage | [storage-module-guide](storage-module-guide.md) | 55 / 0.90 万 | 存储域：存储后端配置面 + 文件代理/URL 重写 |
| websearch | [websearch-module-guide](websearch-module-guide.md) | 32 / 0.44 万 | 网页搜索 provider 族（13 家）与凭据管理 |
| model | [model-module-guide](model-module-guide.md) | 26 / 0.28 万 | 模型域：models 表 CRUD + 运行时配置值出口（⚠️ 既是域名又是层名） |
| embedchannel | [embedchannel-module-guide](embedchannel-module-guide.md) | 17 / 0.29 万 | 嵌入**渠道**域（HTTP 叶子，0 包引用；与 embedding 易混） |
| evaluation | [evaluation-module-guide](evaluation-module-guide.md) | 21 / 0.21 万 | 评测域：rag 预设重放 + 指标族；零外部引用、待排期可选项 |
| initialization | [initialization-module-guide](initialization-module-guide.md) | 14 / 0.24 万 | 初始化域：KB 配置第二路径 + 连通性测试 + Ollama 管理 |
| audit | [audit-module-guide](audit-module-guide.md) | 14 / 0.15 万 | 审计域：三条游标流（空间/KB/平台），只追加表 |
| vectorstore | [vectorstore-module-guide](vectorstore-module-guide.md) | 12 / 0.17 万 | 向量库**配置面**（CRUD/探测/凭据）；运行时在 `:engine` retrieval/engine |
| system | [system-module-guide](system-module-guide.md) | 11 / 0.29 万 | 系统设置 + SSRF 白名单 + admin 探测端点 |
| tenant | [tenant-module-guide](tenant-module-guide.md) | 11 / 0.11 万 | 租户配置类型族：jsonb 列的类型化读写 + 租户查询端口 + 脱敏 |
| settings | [settings-module-guide](settings-module-guide.md) | 4 / 0.05 万 | 系统设置键注册表（键的类型化声明 + 网关；与 system/ 分工） |
| favorite | [favorite-module-guide](favorite-module-guide.md) | 5 / 0.03 万 | 收藏域：全仓最小完整域（3 端点/单表/零外部引用） |
| approval | —（随 mcp/agent 手册的审批桥接节） | 24 / 0.18 万 | 工具审批域：高危工具的人工闸门（MCP 工具调用审批） |

> `approval` 目前无独立手册：它是 mcp 工具审批的落地域，入口见 `mcp-module-guide` §审批桥接与包内 `package-info`。

## `:engine` 能力包（8）

| 包 | 手册 | 规模（文件/行） | 一句话 |
|---|---|---|---|
| retrieval | [retrieval-module-guide](retrieval-module-guide.md) | 93 / 2.16 万 | 检索引擎：HybridSearchService 混合检索融合 + engine/ 九家向量库适配（L2） |
| llm | [llm-module-guide](llm-module-guide.md) | 102 / 1.16 万 | LLM 传输与 provider 族 + chat/extract/asr 子域（L2，全仓 40 个包 import） |
| chatpipeline | [chatpipeline-module-guide](chatpipeline-module-guide.md) | 43 / 0.77 万 | 问答编排管线：骨架 + plugin/（23 插件）+ support/（L2，生产驱动方仅 session） |
| embedding | [embedding-module-guide](embedding-module-guide.md) | 20 / 0.16 万 | embedding **provider 客户端族**（9 家；与 embedchannel 渠道域易混） |
| rerank | [rerank-module-guide](rerank-module-guide.md) | 14 / 0.14 万 | 重排 provider 客户端族（7 家） |
| modelcontext | —（随 agent-module-guide） | 13 / 0.41 万 | 模型输出协议：工具调用/引用/思考块的解析与装配 |
| tracing | [tracing-module-guide](tracing-module-guide.md)（与 `:common` tracing 合册） | 6 | 检索链路 span（knowledge_spans 面；与 Langfuse 追踪两套勿混） |
| webfetch | —（4 文件，见包内 `package-info`） | 4 | 受 SSRF 守卫的网页抓取（工具面） |

## `:common` / `:boot` 平台层（5）

| 包 | 手册 | 规模（文件/行） | 一句话 |
|---|---|---|---|
| common | [common-module-guide](common-module-guide.md) | 146 / 1.07 万 | L1 平台 + 中立契约层：web/error/approval/各域端口，被全部业务包依赖 |
| event | [event-module-guide](event-module-guide.md) | 40 / 0.36 万 | 进程内事件总线 + payload/ 事件载荷（SSE 线上契约面，键全 camel） |
| stream | [stream-module-guide](stream-module-guide.md) | 14 / 0.13 万 | SSE 流基础设施：Redis/Memory 双后端 StreamManager |
| tracing | [tracing-module-guide](tracing-module-guide.md) | 19 / 0.21 万 | Langfuse 追踪：伪装 langfuse-python 直写 OTLP 端点 |
| config（:boot） | [config-module-guide](config-module-guide.md) | 12 / 0.17 万 | L4 组合根：WebConfig（RBAC 规则表）+ 各 *WiringConfig；只出不进 |

## 新人阅读路径建议

1. 先读仓库根 `HANDOFF.md`（作业规范）+ `docs/backend-package-map.md`（分包地图）；
2. 再读自己要接手的域手册 §0/§1/§7（速览、结构、坑）；
3. 动手前过一遍该手册 §5（改哪里）与 §8（测试与验证）；跨域改动回看 §1.2 依赖方向与 `scripts/check-package-cycles.py` 守卫基线。

## 附：`docs/site/` 的陈旧生成物

`site/architecture.json` / `agent-workflow.json` 是架构图的**源数据**，已随 2026-09-28 功能裁剪同步（沙箱节点已移除）。
但同目录的 `*.html` 与 `index.html` 是**外部 Archify 工具的渲染产物**，本仓无该工具、无法重新生成，
因此其中**仍含裁剪前的组件（如「Docker 沙箱」），与当前代码不一致** —— 阅读时请以本文件、`backend-package-map.md`
与源码为准，不要把 HTML 图当作现状依据。重建计划：用 Archify 基于已修正的 `*.json` 重渲染。
