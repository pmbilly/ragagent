# Embed Widget 思考过程显示开关 — 设计文档

日期：2026-09-15
范围：仅本地部署使用（不按上游 PR 标准补齐全量测试）

## 背景与目标

embed widget（浮窗聊天）目前完全不渲染模型的思考过程（thinking 流事件）。管理员希望有一个渠道级开关，控制访客是否能看到思考内容：

- **不显示（默认）**：思考进行中只显示三个点闪烁的指示器，不可展开，回答开始输出后消失
- **显示**：轻量折叠卡片，默认折叠，标题「思考中」，展开后 markdown 渲染思考内容

已确认的决策：

1. **前端不渲染**：后端照常下发 thinking 事件，由前端 gate；接受思考内容到达浏览器的事实
2. **纯显示开关**：模型是否思考仍由绑定的 agent 的 `thinking` 配置决定，渠道开关不参与生成控制（避免渠道开关与 agent 配置互相覆盖）
3. **默认隐藏**：零值 false，存量渠道行为不变
4. **轻量自包含组件**：不依赖主站 AgentStreamDisplay / agent store

## 总体设计

复用「渠道配置 → `/api/v1/embed/<channel_id>/config` 公开接口 → embed 页面渲染」链路，新增 `show_thinking` 配置项。前端在 `useEmbedChatSession` 中累积 thinking 流内容，按配置渲染两种模式之一。

## 改动点

### 1. 数据库迁移

- 目录：`migrations/versioned/`，新增 `000097_embed_show_thinking.up.sql` / `.down.sql`
- 内容：`ALTER TABLE embed_channels ADD COLUMN show_thinking BOOLEAN NOT NULL DEFAULT false;`（down 为 `DROP COLUMN`，附 COMMENT 沿用现有惯例）

### 2. 后端

- `internal/types/embed_channel.go`
  - `EmbedChannel` 加字段：`ShowThinking bool \`json:"show_thinking" gorm:"not null;default:false"\``
  - `EmbedChannelPublicConfig` 加 `ShowThinking bool \`json:"show_thinking,omitempty"\``
  - 渠道 create/update 请求结构体加 `show_thinking`
- `internal/handler/embed_channel.go`
  - create/update 接受 `show_thinking`（布尔，无特殊校验，沿用 `show_suggested_questions` 的处理模式）
  - 公开 config 接口响应带上 `show_thinking`
  - 管理端 GET/PUT 响应带上该字段（跟随现有逐字段返回模式）

### 3. 设置 UI

- `frontend/src/api/embed/index.ts`：`EmbedChannel` 与 `EmbedChannelPublicConfig` 类型加 `show_thinking?: boolean`
- `frontend/src/components/AgentEmbedChannelPanel.vue`（渠道外观设置区）
  - 新增「显示思考过程」开关，与 `show_suggested_questions` 等现有 toggle 同风格、同区块
  - i18n 补齐 zh-CN / en-US / ko-KR / ja-JP / ru-RU 词条

### 4. 前端渲染（调研后修订）

> **修订说明**：原方案假设 embed 完全不存在 thinking 渲染，计划新建累积逻辑 + 新组件。
> 实际调研发现共享的 `useChatStreamHandler` 已把 thinking 解析进消息对象
> （RAG 模式：`thinkContent`/`showThink`/`thinking` 字段，来自 content 中的 `<think>` 标签；
> Agent 模式：`agentEventStream` 中的 thinking 事件），且 embed 已在渲染：
> - RAG 模式：`EmbedBotMessage.vue` 已挂 `deepThink.vue`（折叠卡片，思考中文案 + 脉冲指示，完成后自动折叠）
> - Agent 模式：`AgentStreamDisplay.vue`（embeddedMode）已渲染 thinking-event-card
>
> 因此实现改为**复用现有渲染 + 新增 gate 与 dots 指示器**，不再新建累积逻辑。

- `frontend/src/utils/embedThinkingStatus.ts`（新）：从消息对象提取「思考是否进行中」状态（RAG 看 `thinking` 字段，Agent 模式扫描 `agentEventStream` 中未完成的 thinking 事件），供 dots 指示器使用；附单测
- `frontend/src/views/embed/EmbedThinkingDots.vue`（新）：三个点闪烁的纯 CSS 动画指示器，仅思考进行中显示
- `EmbedBotMessage.vue` 改造：
  - 新增 `showThinking` prop（渠道配置传入，默认 false）
  - `showThinking=true`：保持现有渲染（RAG 走 deepThink，Agent 走 AgentStreamDisplay），但 deepThink 的内容区从纯文本升级为 markdown 渲染（复用 `renderChatMarkdown` + security 工具，主站同步受益）
  - `showThinking=false`：RAG 模式隐藏 DeepThink；Agent 模式给 `AgentStreamDisplay` 传新增的 `suppressThinking` prop（在 `displayEvents` 中过滤 thinking 事件）；两种模式统一在思考进行中显示 EmbedThinkingDots
- 配置透传：`EmbedPage.vue` → `EmbedChatView.vue` → `EmbedChatCore.vue` → `EmbedBotMessage.vue` 新增 `showThinking` prop 链（模式同 `showSuggestedQuestions`）

## 数据流

1. 管理员在发布集成设置页打开「显示思考过程」→ 随 PUT `/api/v1/embed-channels/<id>` 落库
2. 访客打开 embed 聊天页 → 拉取公开 config → 得到 `show_thinking`
3. 流式会话中：thinking 事件累积 → 按配置渲染 dots 或 card；回答内容到达后按现有逻辑渲染

## 错误处理

| 场景 | 行为 |
|------|------|
| 未配置（存量渠道） | `show_thinking=false`，显示 dots 指示器 |
| agent 未开 thinking | 无 thinking 事件，两种模式都不出现任何思考 UI（卡片和点都不显示） |
| 思考中途报错/中断 | dots/card 随消息结束状态消失或定型，残留中间态可接受（与主站一致） |
| 历史消息重载 | 不显示思考内容（thinking 不落库，维持现状） |

## 测试

- 后端：`internal/application/service/embed_channel_update_test.go` 补充 `show_thinking` 的 Update 用例（nil 保留、true/false 写入）；`embed_channel_public_config_test.go` 补充 public config 下发字段用例
- 前端：`frontend/src/utils/embedThinkingStatus.test.ts` 覆盖两种模式的「思考进行中」判断
- 手工验证（本地）：
  1. `docker compose build app frontend && docker compose up -d`
  2. 渠道开关关闭 → 发起会话（agent 已开 thinking）→ 思考中出现闪烁三点，回答出现后消失
  3. 渠道开关打开 → 折叠卡片默认折叠，展开可见 markdown 思考内容
  4. 存量渠道（未动过开关）行为不变

## 非目标（YAGNI）

- 不做 thinking 内容持久化（历史消息不显示思考，需另立项）
- 不改 agent 的 thinking 生成逻辑、不做渠道级强制开启思考
- 不做后端过滤（已决策前端渲染层 gate）
- 不按上游 PR 标准补全量测试/i18n 审查
