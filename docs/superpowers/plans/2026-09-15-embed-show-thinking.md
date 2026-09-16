# Embed Widget 思考过程显示开关 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 embed 渠道级 `show_thinking` 配置，控制浮窗聊天是否向访客展示模型思考过程（关闭=三点指示器，开启=折叠思考卡片）。

**Architecture:** 后端在 `embed_channels` 表加 `show_thinking` 列（默认 false），随渠道 CRUD 与公开 config 接口下发；前端经 EmbedPage → EmbedChatView → EmbedChatCore → EmbedBotMessage 的 prop 链传入，gate 现有 thinking 渲染（deepThink / AgentStreamDisplay），关闭时渲染新的 EmbedThinkingDots 指示器。thinking 数据的流式累积已由共享的 `useChatStreamHandler` 完成，本计划不重复实现。

**Tech Stack:** Go (Gin + GORM + 手写迁移 SQL)、Vue 3 + TDesign、node:test（`tsx --test`）

**Spec:** `docs/superpowers/specs/2026-09-15-embed-show-thinking-design.md`

---

### Task 1: 数据库迁移

**Files:**
- Create: `migrations/versioned/000097_embed_show_thinking.up.sql`
- Create: `migrations/versioned/000097_embed_show_thinking.down.sql`
- Create: `migrations/sqlite/000018_embed_show_thinking.up.sql`（SQLite 树，审查补充：两树需同步）
- Create: `migrations/sqlite/000018_embed_show_thinking.down.sql`

> SQLite 树风格与 versioned 树不同：单行语句、不带 `IF [NOT] EXISTS`、无注释头（参照 `migrations/sqlite/000017_embed_launcher_icon.*`）。

- [ ] **Step 1: 写迁移文件**

`000097_embed_show_thinking.up.sql`：

```sql
-- Migration 000097: embed channel show-thinking toggle.
--
-- Controls whether the embed widget renders the model's thinking process to
-- visitors. false (default) shows only a blinking-dots indicator while
-- thinking is in progress; true renders the collapsible thinking card.
ALTER TABLE embed_channels ADD COLUMN IF NOT EXISTS show_thinking BOOLEAN NOT NULL DEFAULT false;
```

`000097_embed_show_thinking.down.sql`：

```sql
ALTER TABLE embed_channels DROP COLUMN IF EXISTS show_thinking;
```

- [ ] **Step 2: Commit**

```bash
git add migrations/versioned/000097_embed_show_thinking.up.sql migrations/versioned/000097_embed_show_thinking.down.sql
git commit -m "feat: add show_thinking column migration for embed channels"
```

---

### Task 2: 后端字段、服务与 Handler

**Files:**
- Modify: `internal/types/embed_channel.go:27`（EmbedChannel 加字段）、`:118`（PublicConfig 加字段）
- Modify: `internal/types/interfaces/embed_channel.go:25`（Update 签名）
- Modify: `internal/application/service/embed_channel.go`（Create / Update / PublicConfig）
- Modify: `internal/handler/embed_channel.go:72`（请求结构体）、`:155-156`（create 解析）、`:176`（create 赋值）、`:267`（update 调用）、`:806`（响应）
- Test: `internal/application/service/embed_channel_update_test.go`（新增用例 + 5 处既有调用补 nil）
- Test: `internal/application/service/embed_channel_public_config_test.go`（新增用例）

- [ ] **Step 1: 写失败测试**

在 `internal/application/service/embed_channel_update_test.go` 末尾追加：

```go
func TestEmbedChannelUpdateShowThinking(t *testing.T) {
	repo := &stubEmbedChannelRepo{
		ch: &types.EmbedChannel{
			ID:       "ch-1",
			TenantID: 42,
			AgentID:  "agent-1",
			Name:     "Support",
		},
	}
	svc := &embedChannelService{repo: repo}

	// nil 指针不改动存量值（零值 false 保持 false）。
	updated, err := svc.Update(
		context.Background(),
		42,
		"ch-1",
		&types.EmbedChannel{},
		nil, nil, nil, nil, nil, nil, nil, nil, nil,
	)
	if err != nil {
		t.Fatalf("Update returned error: %v", err)
	}
	if updated.ShowThinking {
		t.Fatalf("ShowThinking = true, want false (untouched)")
	}

	// 显式开启。
	showThinking := true
	updated, err = svc.Update(
		context.Background(),
		42,
		"ch-1",
		&types.EmbedChannel{},
		nil, nil, &showThinking, nil, nil, nil, nil, nil, nil,
	)
	if err != nil {
		t.Fatalf("Update returned error: %v", err)
	}
	if !updated.ShowThinking {
		t.Fatalf("ShowThinking = false, want true")
	}

	// 显式关闭。
	showThinking = false
	updated, err = svc.Update(
		context.Background(),
		42,
		"ch-1",
		&types.EmbedChannel{},
		nil, nil, &showThinking, nil, nil, nil, nil, nil, nil,
	)
	if err != nil {
		t.Fatalf("Update returned error: %v", err)
	}
	if updated.ShowThinking {
		t.Fatalf("ShowThinking = true, want false")
	}
}
```

注意：上面调用已是**新签名**（`showThinking *bool` 插在 `showSuggested *bool` 之后，共 9 个指针参数）。在实现前编译会失败，这是预期。

在 `internal/application/service/embed_channel_public_config_test.go` 末尾追加（仿照同文件 `TestPublicConfigIncludesLauncherIcon`）：

```go
func TestPublicConfigIncludesShowThinking(t *testing.T) {
	svc := &embedChannelService{}
	cfg := svc.PublicConfig(context.Background(), &types.EmbedChannel{
		ID:           "ch-thinking",
		AgentID:      "agent-1",
		ShowThinking: true,
	})
	if !cfg.ShowThinking {
		t.Fatalf("expected show_thinking=true to be included in public config")
	}
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
go test ./internal/application/service/ -run 'TestEmbedChannelUpdateShowThinking|TestPublicConfigIncludesShowThinking' -v
```

预期：编译失败（`too many arguments in call to svc.Update` / `unknown field 'ShowThinking'`）。

- [ ] **Step 3: 实现后端**

`internal/types/embed_channel.go` — `EmbedChannel` 在第 27 行 `ShowSuggestedQuestions` 之后加：

```go
	ShowThinking             bool           `json:"show_thinking"             gorm:"not null;default:false"`
```

`EmbedChannelPublicConfig` 在第 118 行 `ShowSuggestedQuestions bool` 之后加（注意该结构体中布尔字段不带 omitempty，保持一致）：

```go
	ShowThinking           bool     `json:"show_thinking"`
```

`internal/types/interfaces/embed_channel.go:25` — `Update` 方法签名在 `showSuggested *bool` 之后插入 `showThinking *bool`：

```go
	Update(ctx context.Context, tenantID uint64, id string, req *types.EmbedChannel, enabled *bool, showSuggested *bool, showThinking *bool, allowWebSearch *bool, allowFileUpload *bool, defaultLocale *string, webhookURL *string, webhookSecret *string, launcherIcon *string) (*types.EmbedChannel, error)
```

`internal/application/service/embed_channel.go`：
- `Create`（约 L76 `ShowSuggestedQuestions: req.ShowSuggestedQuestions,` 之后）加 `ShowThinking: req.ShowThinking,`
- `Update`（`if showSuggested != nil {...}` 块之后）加：

```go
	if showThinking != nil {
		ch.ShowThinking = *showThinking
	}
```

- `PublicConfig` 返回值（`ShowSuggestedQuestions: ch.ShowSuggestedQuestions,` 之后）加 `ShowThinking: ch.ShowThinking,`

`internal/handler/embed_channel.go`：
- 请求结构体（L72 `ShowSuggestedQuestions *bool` 之后）加：

```go
	ShowThinking           *bool    `json:"show_thinking"`
```

- `CreateEmbedChannel`：在 `showSuggested := true` 块之后加：

```go
	showThinking := false
	if req.ShowThinking != nil {
		showThinking = *req.ShowThinking
	}
```

并在 `&types.EmbedChannel{...}` 字面量中 `ShowSuggestedQuestions: showSuggested,` 之后加 `ShowThinking: showThinking,`。

- `UpdateEmbedChannel` 的 `h.embedSvc.Update(...)` 调用（L267）在 `req.ShowSuggestedQuestions` 之后插入 `req.ShowThinking`。
- `embedChannelResponse`（L806 `"show_suggested_questions": ch.ShowSuggestedQuestions,` 之后）加 `"show_thinking": ch.ShowThinking,`。

- [ ] **Step 4: 修复既有测试调用点**

`internal/application/service/embed_channel_update_test.go` 中 5 处 `svc.Update(` 调用（L54、L80、L94、L108、L122）在 `showSuggested` 位置的实参后各补一个 `nil`（新参数 `showThinking` 传 nil = 不改动）。

- [ ] **Step 5: 跑测试确认通过 + 全量编译**

```bash
go test ./internal/application/service/ -run 'TestEmbedChannelUpdate|TestPublicConfig' -v
go build ./...
go test ./internal/handler/ -run Embed
```

预期：全部 PASS；`go build` 无输出；handler 测试无接口破坏。

- [ ] **Step 6: Commit**

```bash
git add internal/types/embed_channel.go internal/types/interfaces/embed_channel.go internal/application/service/embed_channel.go internal/application/service/embed_channel_update_test.go internal/application/service/embed_channel_public_config_test.go internal/handler/embed_channel.go
git commit -m "feat: add show_thinking to embed channel backend"
```

---

### Task 3: 前端 API 类型、设置面板开关与 i18n

**Files:**
- Modify: `frontend/src/api/embed/index.ts:14`（EmbedChannel）、`:39`（EmbedChannelPublicConfig）
- Modify: `frontend/src/components/AgentEmbedChannelPanel.vue`（模板开关 ~L173、defaultForm ~L533、编辑回填 ~L876 与 ~L1135、保存 payload ~L972）
- Modify: `frontend/src/i18n/locales/zh-CN.ts`（embedPublish 区块 ~L4431）
- Modify: `frontend/src/i18n/locales/en-US.ts`、`ko-KR.ts`、`ja-JP.ts`、`ru-RU.ts`（同上位置）

- [ ] **Step 1: API 类型**

`frontend/src/api/embed/index.ts` 两个 interface 中 `show_suggested_questions?: boolean`（EmbedChannel）/ `show_suggested_questions: boolean`（PublicConfig）之后各加一行：

```ts
  show_thinking?: boolean
```

- [ ] **Step 2: 面板模板开关**

`AgentEmbedChannelPanel.vue` 中「推荐问题」开关块（`show_suggested_questions` 的 `setting-row`，约 L168-176）之后插入：

```vue
            <div class="setting-row">
              <div class="setting-info">
                <label>{{ $t('embedPublish.showThinking') }}</label>
                <p class="desc">{{ $t('embedPublish.showThinkingDesc') }}</p>
              </div>
              <div class="setting-control">
                <t-switch v-model="form.show_thinking" :disabled="!isAdmin" size="small" />
              </div>
            </div>
```

- [ ] **Step 3: 表单字段三处**

- `defaultForm()`（~L533 `show_suggested_questions: true,` 之后）加：`show_thinking: false,`
- 编辑回填两处（~L876 与 ~L1135，均为 `show_suggested_questions: ch.show_suggested_questions !== false,`）各加一行：`show_thinking: ch.show_thinking === true,`
- 保存 payload（~L972 `show_suggested_questions: form.value.show_suggested_questions,` 之后）加：`show_thinking: form.value.show_thinking,`

- [ ] **Step 4: i18n 词条（5 语言，均加在各自 `showSuggestedQuestionsDesc` 之后）**

zh-CN：

```ts
    showThinking: '显示思考过程',
    showThinkingDesc: '向访客展示模型的思考过程；关闭后仅显示思考中的动态指示',
```

en-US：

```ts
    showThinking: 'Show thinking process',
    showThinkingDesc: "Show the model's thinking process to visitors; when off, only a blinking indicator is shown while thinking",
```

ko-KR：

```ts
    showThinking: '사고 과정 표시',
    showThinkingDesc: '방문자에게 모델의 사고 과정을 표시합니다. 꺼져 있으면 사고 중 깜박이는 표시만 나타납니다',
```

ja-JP：

```ts
    showThinking: '思考プロセスを表示',
    showThinkingDesc: '訪問者にモデルの思考プロセスを表示します。オフの場合は思考中の点滅インジケータのみが表示されます',
```

ru-RU：

```ts
    showThinking: 'Показывать процесс рассуждения',
    showThinkingDesc: 'Показывать посетителям процесс рассуждения модели; если выключено, отображается только мигающий индикатор во время рассуждения',
```

- [ ] **Step 5: 校验**

```bash
cd frontend && pnpm check-i18n && pnpm type-check
```

预期：localeKeyAudit 通过（新 key 五语言齐），vue-tsc 无报错。

- [ ] **Step 6: Commit**

```bash
git add frontend/src/api/embed/index.ts frontend/src/components/AgentEmbedChannelPanel.vue frontend/src/i18n/locales/
git commit -m "feat: add show_thinking toggle to embed channel settings"
```

---

### Task 4: showThinking 配置透传 prop 链

**Files:**
- Modify: `frontend/src/views/embed/EmbedPage.vue:38`（EmbedChatView 绑定处）
- Modify: `frontend/src/views/embed/EmbedChatView.vue`（模板 + props）
- Modify: `frontend/src/views/embed/EmbedChatCore.vue`（props + EmbedBotMessage 绑定）

- [ ] **Step 1: EmbedPage → EmbedChatView**

`EmbedPage.vue` 模板 `:show-suggested-questions="config.show_suggested_questions !== false"`（L38）之后加：

```vue
        :show-thinking="config.show_thinking === true"
```

- [ ] **Step 2: EmbedChatView 透传**

模板 `:show-suggested-questions="showSuggestedQuestions"` 之后加 `:show-thinking="showThinking"`；props 类型 `showSuggestedQuestions?: boolean` 之后加 `showThinking?: boolean`。

- [ ] **Step 3: EmbedChatCore 透传**

props 类型中 `showSuggestedQuestions?: boolean` 之后加 `showThinking?: boolean`；模板中 `<EmbedBotMessage`（L65）的 `:user-query="getUserQuery(index)"` 之后加 `:show-thinking="showThinking"`。

- [ ] **Step 4: 类型检查**

```bash
cd frontend && pnpm type-check
```

预期：通过（EmbedBotMessage 的 prop 在 Task 5 才加，vue-tsc 对未声明 prop 绑定会报错——如报错可先跳过，Task 5 完成后复跑；也可把 Task 4 的 prop 绑定与 Task 5 合并提交）。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/views/embed/EmbedPage.vue frontend/src/views/embed/EmbedChatView.vue frontend/src/views/embed/EmbedChatCore.vue
git commit -m "feat: pass showThinking config through embed view chain"
```

---

### Task 5: 思考状态工具函数 + 单测

**Files:**
- Create: `frontend/src/utils/embedThinkingStatus.ts`
- Test: `frontend/src/utils/embedThinkingStatus.test.ts`

- [ ] **Step 1: 写失败测试**

```ts
import assert from 'node:assert/strict'
import test from 'node:test'

import { isThinkingInProgress } from './embedThinkingStatus.ts'

test('isThinkingInProgress: quick-answer mode tracks thinking flag', () => {
  assert.equal(isThinkingInProgress({ thinking: true }), true)
  assert.equal(isThinkingInProgress({ thinking: false }), false)
  assert.equal(isThinkingInProgress({ showThink: true }), false)
  assert.equal(isThinkingInProgress(null), false)
  assert.equal(isThinkingInProgress(undefined), false)
})

test('isThinkingInProgress: agent mode scans thinking events', () => {
  assert.equal(
    isThinkingInProgress({
      isAgentMode: true,
      agentEventStream: [{ type: 'thinking', thinking: true, done: false }],
    }),
    true,
  )
  assert.equal(
    isThinkingInProgress({
      isAgentMode: true,
      agentEventStream: [{ type: 'thinking', thinking: false, done: true }],
    }),
    false,
  )
  assert.equal(
    isThinkingInProgress({
      isAgentMode: true,
      agentEventStream: [{ type: 'tool_call', thinking: false }],
    }),
    false,
  )
  assert.equal(isThinkingInProgress({ isAgentMode: true }), false)
})
```

- [ ] **Step 2: 跑测试确认失败**

```bash
cd frontend && pnpm test src/utils/embedThinkingStatus.test.ts
```

预期：失败（`Cannot find module './embedThinkingStatus.ts'`）。

- [ ] **Step 3: 实现**

```ts
export interface EmbedThinkingMessage {
  thinking?: boolean
  showThink?: boolean
  isAgentMode?: boolean
  agentEventStream?: Array<{ type?: string; thinking?: boolean; done?: boolean }>
}

/**
 * Whether a thinking process is currently in progress for a message.
 * Quick-answer (RAG) mode mirrors the `thinking` flag parsed from <think> tags
 * by useChatStreamHandler; agent mode scans the event stream for unfinished
 * thinking events.
 */
export function isThinkingInProgress(message?: EmbedThinkingMessage | null): boolean {
  if (!message) return false
  if (message.isAgentMode) {
    return (message.agentEventStream ?? []).some(
      (e) => e?.type === 'thinking' && e.thinking === true && e.done !== true,
    )
  }
  return message.thinking === true
}
```

- [ ] **Step 4: 跑测试确认通过**

```bash
cd frontend && pnpm test src/utils/embedThinkingStatus.test.ts
```

预期：4 个断言组全部通过。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/utils/embedThinkingStatus.ts frontend/src/utils/embedThinkingStatus.test.ts
git commit -m "feat: add embed thinking-status util"
```

---

### Task 6: EmbedThinkingDots 指示器组件

**Files:**
- Create: `frontend/src/views/embed/EmbedThinkingDots.vue`

- [ ] **Step 1: 实现组件**

```vue
<template>
  <span class="embed-thinking-dots" :aria-label="t('chat.thinkingAlt')">
    <span v-for="n in 3" :key="n" class="embed-thinking-dots__dot"
      :style="{ animationDelay: `${(n - 1) * 0.18}s` }" />
  </span>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<style scoped lang="less">
.embed-thinking-dots {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 10px 2px;

  &__dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--td-brand-color);
    animation: embed-thinking-dot 1.2s ease-in-out infinite;
  }
}

@keyframes embed-thinking-dot {
  0%,
  100% {
    opacity: 0.25;
    transform: translateY(0);
  }
  50% {
    opacity: 1;
    transform: translateY(-2px);
  }
}
</style>
```

（`chat.thinkingAlt` i18n key 已存在，`EmbedChatCore.vue:88` 正在使用。）

- [ ] **Step 2: 类型检查**

```bash
cd frontend && pnpm type-check
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/embed/EmbedThinkingDots.vue
git commit -m "feat: add embed thinking dots indicator component"
```

---

### Task 7: EmbedBotMessage 渲染 gate + AgentStreamDisplay suppressThinking

**Files:**
- Modify: `frontend/src/views/embed/EmbedBotMessage.vue`（模板 L5-15、props、import）
- Modify: `frontend/src/views/chat/components/AgentStreamDisplay.vue`（props ~L957、`displayEvents` ~L2076）

- [ ] **Step 1: EmbedBotMessage 改造**

模板中 `<DeepThink ...>`（L15）替换为两行（dots 在前）：

```vue
    <EmbedThinkingDots v-if="!showThinking && isThinkingInProgress(session)" />
    <DeepThink v-if="showThinking && session?.showThink && !session?.isAgentMode" :deep-session="session" />
```

两处 `<AgentStreamDisplay ...>`（L5-7 与 L11-13）各加属性 `:suppress-thinking="!showThinking"`。

script 中：
- import 区加：

```ts
import EmbedThinkingDots from '@/views/embed/EmbedThinkingDots.vue'
import { isThinkingInProgress, type EmbedThinkingMessage } from '@/utils/embedThinkingStatus'
```

- `EmbedSession` 类型加字段 `thinking?: boolean`：

```ts
type EmbedSession = {
  content?: string
  isRagMode?: boolean
  isAgentMode?: boolean
  showThink?: boolean
  thinking?: boolean
  hideContent?: boolean
  is_completed?: boolean
  agentEventStream?: Array<Record<string, unknown>>
  knowledge_references?: Array<{ chunk_type?: string; knowledge_id?: string; knowledge_title?: string }>
}
```

- props 加 `showThinking?: boolean`，withDefaults 默认 `showThinking: false`。
- `isThinkingInProgress(session)` 调用处类型不匹配时转换：`isThinkingInProgress(session as EmbedThinkingMessage | undefined)`。

- [ ] **Step 2: AgentStreamDisplay 加 suppressThinking prop**

props 类型（`ragMode?: boolean;` 之前）加：

```ts
  suppressThinking?: boolean;
```

`displayEvents` computed（L2076）在 `user_message_injected` 过滤之后、`props.ragMode` 分支之前插入：

```ts
  // Embed channels can hide reasoning text from visitors while still receiving
  // the events; the embed UI shows a lightweight dots indicator instead.
  if (props.suppressThinking) {
    return result.filter((e: any) => e.type !== 'thinking');
  }
```

- [ ] **Step 3: 类型检查 + 构建**

```bash
cd frontend && pnpm type-check && pnpm build
```

预期：通过。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/views/embed/EmbedBotMessage.vue frontend/src/views/chat/components/AgentStreamDisplay.vue
git commit -m "feat: gate embed thinking rendering with show_thinking config"
```

---

### Task 8: deepThink 内容区升级为 markdown 渲染

**Files:**
- Modify: `frontend/src/views/chat/components/deepThink.vue`（模板 L22、script、style）

- [ ] **Step 1: 实现**

模板 L22 替换：

```vue
        <div ref="contentInnerRef" class="content-inner markdown-content" v-html="thinkHTML" />
```

script（`const props = defineProps({...})` 之后）加：

```js
const thinkRenderer = createChatMarkdownRenderer({
    imageRenderer: ({ href, title, text }) => createSafeImage(href, text || '', title || ''),
    isValidImageUrl: isValidImageURL,
})

const thinkHTML = computed(() => {
    const text = String(props.deepSession?.thinkContent || '')
    if (!text.trim()) return ''
    return renderChatMarkdown(text, {
        renderer: thinkRenderer,
        escapeMarkdown: safeMarkdownToHTML,
        sanitizeHtml: sanitizeMarkdownHTML,
        streaming: props.deepSession?.thinking === true,
    })
})
```

import 区加：

```js
import { renderChatMarkdown, createChatMarkdownRenderer } from '@/utils/chatMarkdownRenderer';
import { safeMarkdownToHTML, sanitizeMarkdownHTML, createSafeImage, isValidImageURL } from '@/utils/security';
```

style：文件顶部 `.deep-think` 规则前加 import，并给 `.content-inner` 应用排版 mixin、去掉纯文本的 `white-space: pre-wrap`：

```less
@import '../../../components/css/chat-markdown.less';

.deep-think {
    ...
```
（注意：从 `views/chat/components/` 出发的正确路径是 `../../../components/css/chat-markdown.less`，与同目录 `botmsg.vue` 一致；`../../components/...` 会解析到不存在的 `views/components/`，实现时已按正确路径执行。）

```less
        .content-inner {
            padding: 8px 14px;
            .chat-markdown-typography();
            font-size: 12px;
            line-height: 1.6;
            color: var(--td-text-color-secondary);
            max-height: 200px;
            overflow-y: auto;
            word-break: break-word;
            ...
```

- [ ] **Step 2: 类型检查 + 构建**

```bash
cd frontend && pnpm type-check && pnpm build
```

预期：通过。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/chat/components/deepThink.vue
git commit -m "feat: render thinking content as markdown in deepThink card"
```

---

### Task 9: 全量验证

- [ ] **Step 1: 后端**

```bash
go build ./... && go test ./internal/application/service/ ./internal/handler/ -run Embed
```

预期：编译通过、测试全 PASS。

- [ ] **Step 2: 前端**

```bash
cd frontend && pnpm check-i18n && pnpm test src/utils/embedThinkingStatus.test.ts && pnpm type-check && pnpm build
```

预期：全部通过。

- [ ] **Step 3: 手工验证（本地 docker）**

```bash
docker compose build app frontend && docker compose up -d
```

1. 渠道设置页出现「显示思考过程」开关，默认关闭；打开保存后重进面板状态保持。
2. 绑定已开 `thinking` 的 agent 的渠道：**关闭**时发消息 → 思考阶段显示三点闪烁，回答出现后消失，无思考文本。
3. 同一渠道**打开**开关 → 折叠卡片显示「思考中」，展开可见 markdown 思考内容。
4. 存量渠道（未动开关）行为与线上一致。

- [ ] **Step 4: 刷新知识图谱**

```bash
graphify update .
```

- [ ] **Step 5: 收尾提交（如有零散改动）**

```bash
git add -A && git commit -m "chore: finalize embed show-thinking feature" || true
```
