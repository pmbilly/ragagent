# 模块手册索引

> `com.ragagent` 全部 **29 个顶层包**各有一份模块手册（`docs/<模块>-module-guide.md`），结构与粒度对齐样板篇 `knowledge-module-guide.md`（§0 一分钟速览 → §10 地标速查）。
> **数据口径**：除 knowledge（2026-09-30）外，均为 **2026-10-08 实测**（`wc -l` 口径）。仓库级作业规范见仓库根 `HANDOFF.md`（§12 结构地图、§13 踩坑清单、§14 逐包重构范式）；包性质判定与结构决策见 `docs/backend-package-map.md`。

## 业务域（19）

| 模块 | 手册 | 规模（文件/行） | 一句话 |
|---|---|---|---|
| knowledge | [knowledge-module-guide](knowledge-module-guide.md) | 197 / 2.48 万（09-30 口径） | KB 域：文档入库 → 分块/抽取/向量化 → 检索与 FAQ 供给（**样板篇**） |
| agent | [agent-module-guide](agent-module-guide.md) | 188 / 3.59 万 | agent 引擎：执行循环、工具族（tools/）、管理面（management/）、模型输出协议（modelcontext/） |
| wiki | [wiki-module-guide](wiki-module-guide.md) | 156 / 2.39 万 | wiki 域：页面摄取管线（ingest/）+ 页面/文件夹/链接（page/） |
| datasource | [datasource-module-guide](datasource-module-guide.md) | 129 / 2.77 万 | 外部数据源连接器族（feishu/gitlab/notion/rss/yuque/ima…，入向耦合近零） |
| session | [session-module-guide](session-module-guide.md) | 111 / 2.20 万 | 会话域：会话/消息 CRUD + 3 条 SSE 问答流（REST camel、SSE snake） |
| mcp | [mcp-module-guide](mcp-module-guide.md) | 116 / 1.28 万 | MCP 服务器接入：协议族（protocol/）+ OAuth（oauth/）+ 审批桥接 |
| im | [im-module-guide](im-module-guide.md) | 76 / 1.74 万 | IM 渠道域：九渠道一渠道一子包 + runtime；回调面免 JWT 靠平台验签 |
| memory | [memory-module-guide](memory-module-guide.md) | 74 / 1.28 万 | 记忆域：写入/检索/注入；recall 永不失败原则 |
| storage | [storage-module-guide](storage-module-guide.md) | 55 / 0.90 万 | 存储域：存储后端配置面 + 文件代理/URL 重写 |
| websearch | [websearch-module-guide](websearch-module-guide.md) | 32 / 0.44 万 | 网页搜索 provider 族（13 家）与凭据管理 |
| model | [model-module-guide](model-module-guide.md) | 26 / 0.28 万 | 模型域：models 表 CRUD + 运行时配置值出口（⚠️ 既是域名又是层名） |
| evaluation | [evaluation-module-guide](evaluation-module-guide.md) | 21 / 0.21 万 | 评测域：rag 预设重放 + 指标族；零外部引用、待排期可选项 |
| embed | [embedchannel-module-guide](embedchannel-module-guide.md) | 16 / 0.29 万 | 嵌入**渠道**域（HTTP 叶子，0 包引用；与 embedding 易混，改名 embedchannel 后议） |
| initialization | [initialization-module-guide](initialization-module-guide.md) | 14 / 0.24 万 | 初始化域：KB 配置第二路径（`PUT /initialization/config/{kbId}`）+ 连通性测试 + Ollama 管理 |
| audit | [audit-module-guide](audit-module-guide.md) | 14 / 0.15 万 | 审计域：三条游标流（空间/KB/平台），只追加表 |
| vectorstore | [vectorstore-module-guide](vectorstore-module-guide.md) | 13 / 0.18 万 | 向量库**配置面**（vector_stores 表 CRUD/探测/凭据）；运行时在 retrieval/engine |
| system | [system-module-guide](system-module-guide.md) | 11 / 0.29 万 | 系统设置域：settings + SSRF 白名单 + admin 探测端点 |
| favorite | [favorite-module-guide](favorite-module-guide.md) | 5 / 0.03 万 | 收藏域：全仓最小完整域（3 端点/单表/零外部引用） |
| auth | [auth-module-guide](auth-module-guide.md) | 96 / 1.29 万 | 认证域：登录/租户/成员/邀请 + apikey 子域（HANDOFF §11 冻结面） |

## 库式域（4，无 controller 是对的）

| 模块 | 手册 | 规模（文件/行） | 一句话 |
|---|---|---|---|
| retrieval | [retrieval-module-guide](retrieval-module-guide.md) | 92 / 2.16 万 | 检索引擎：HybridSearchService 融合 + engine/ 八家向量店读写（L2） |
| llm | [llm-module-guide](llm-module-guide.md) | 104 / 1.16 万 | LLM 传输与 provider 族 + chat/extract/asr 子域（L2，被 18 个包消费） |
| chatpipeline | [chatpipeline-module-guide](chatpipeline-module-guide.md) | 44 / 0.77 万 | 问答编排管线：根骨架 + plugin/(23 插件) + support/（L2，生产驱动方仅 session） |
| event | [event-module-guide](event-module-guide.md) | 40 / 0.36 万 | 进程内事件总线 + payload/ 26 个事件载荷（SSE 线上契约面） |

## 基础设施 / 平台层

| 模块 | 手册 | 规模（文件/行） | 一句话 |
|---|---|---|---|
| common | [common-module-guide](common-module-guide.md) | 125 / 0.90 万 | L1 平台 + 中立契约层：被 28/28 包依赖；web/error/approval/各域端口 |
| config | [config-module-guide](config-module-guide.md) | 13 / 0.17 万 | L4 组合根：WebConfig（322 条 RBAC 规则）+ 各 *WiringConfig；只出不进 |
| stream | [stream-module-guide](stream-module-guide.md) | 13 / 0.13 万 | SSE 流基础设施：Redis/Memory 双后端 StreamManager |
| tracing | [tracing-module-guide](tracing-module-guide.md) | 25 / 0.29 万 | Langfuse 追踪：伪装 langfuse-python 直写 OTLP 端点（与 knowledge_spans 两套 span 勿混） |
| embedding | [embedding-module-guide](embedding-module-guide.md) | 20 / 0.16 万 | embedding **provider 客户端族**（9 家；与 embed 渠道域易混） |
| rerank | [rerank-module-guide](rerank-module-guide.md) | 14 / 0.14 万 | 重排 provider 客户端族（7 家；RankResult snake 键是冻结面） |

## 新人阅读路径建议

1. 先读仓库根 `HANDOFF.md`（作业规范）+ `docs/backend-package-map.md`（分包地图）；
2. 再读自己要接手的域手册 §0/§1/§7（速览、结构、坑）；
3. 动手前过一遍该手册 §5（改哪里）与 §8（测试与验证）；跨域改动回看 §1.2 依赖方向与 `scripts/check-package-cycles.py` 守卫基线。
