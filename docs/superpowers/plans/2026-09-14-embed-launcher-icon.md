# 网页嵌入 Widget 浮标图片 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让发布集成的网页嵌入渠道支持上传浮标图片，Widget 右下角浮标显示自定义图片替代默认 💬。

**Architecture:** 图片在设置页转为 base64 data URL，存入 `embed_channels.launcher_icon` 文本字段；公开接口 `GET /api/v1/embed/<id>/config` 下发该字段；widget.js 在拿到 token 后新增一次 config 拉取（现状不拉取），拿到 `launcher_icon` 后渲染圆形 `<img>`，失败/为空回退 💬。

**Tech Stack:** Go (gin/gorm, golang-migrate), Vue 3 + TDesign, 原生 JS (widget.js), PostgreSQL(ParadeDB)/SQLite 迁移。

**Spec:** `docs/superpowers/specs/2026-09-14-embed-launcher-icon-design.md`

**设计细化（spec 补充）:** spec 假设 widget.js 已拉取 config；实际它没有。本计划在 widget.js 中新增一次 `GET /api/v1/embed/<channel_id>/config`（带 `Authorization: Embed <token>`），在 token 就绪后异步执行，拿到 `launcher_icon` 后再替换浮标内容（先显示 💬，config 返回后换成图片）。无需改动嵌入代码片段（snippet）。

---

### Task 1: 数据库迁移文件

**Files:**
- Create: `migrations/versioned/000096_embed_launcher_icon.up.sql`
- Create: `migrations/versioned/000096_embed_launcher_icon.down.sql`
- Create: `migrations/sqlite/000017_embed_launcher_icon.up.sql`
- Create: `migrations/sqlite/000017_embed_launcher_icon.down.sql`

背景：`migrations/versioned/` 是 PostgreSQL/ParadeDB 用的（golang-migrate，app 启动时经 `internal/database/migration.go: RunMigrationsWithOptions` 自动执行），`migrations/sqlite/` 是 SQLite 用的（SQLite 的 `ADD COLUMN` 不支持 `IF NOT EXISTS`）。

- [ ] **Step 1: 创建 versioned (PostgreSQL) 迁移**

`migrations/versioned/000096_embed_launcher_icon.up.sql`:
```sql
-- Migration 000096: embed channel launcher icon.
--
-- Optional custom launcher image for the website-embed widget, stored as a
-- base64 data URL (e.g. "data:image/png;base64,..."). Empty string means the
-- default 💬 launcher is rendered.
ALTER TABLE embed_channels ADD COLUMN IF NOT EXISTS launcher_icon TEXT NOT NULL DEFAULT '';
```

`migrations/versioned/000096_embed_launcher_icon.down.sql`:
```sql
ALTER TABLE embed_channels DROP COLUMN IF EXISTS launcher_icon;
```

- [ ] **Step 2: 创建 sqlite 迁移**

`migrations/sqlite/000017_embed_launcher_icon.up.sql`:
```sql
ALTER TABLE embed_channels ADD COLUMN launcher_icon TEXT NOT NULL DEFAULT '';
```

`migrations/sqlite/000017_embed_launcher_icon.down.sql`:
```sql
ALTER TABLE embed_channels DROP COLUMN launcher_icon;
```

- [ ] **Step 3: Commit**

```bash
git add migrations/versioned/000096_embed_launcher_icon.* migrations/sqlite/000017_embed_launcher_icon.*
git commit -m "feat: add launcher_icon column migration for embed channels"
```

---

### Task 2: 后端 — 模型字段与校验函数（TDD)

**Files:**
- Modify: `internal/types/embed_channel.go`
- Modify: `internal/application/service/embed_channel.go`（顶部 import 区与错误定义区，参考现有 `ErrEmbedWebhookURLInvalid` / `ValidateEmbedWebhookURL` 所在位置）
- Test: `internal/application/service/embed_channel_launcher_icon_test.go`（新建）

- [ ] **Step 1: 写失败测试**

新建 `internal/application/service/embed_channel_launcher_icon_test.go`:
```go
package service

import (
	"encoding/base64"
	"errors"
	"strings"
	"testing"
)

func iconDataURL(mediaType string, size int) string {
	return "data:" + mediaType + ";base64," + base64.StdEncoding.EncodeToString(make([]byte, size))
}

func TestValidateEmbedLauncherIcon(t *testing.T) {
	cases := []struct {
		name    string
		input   string
		wantErr bool
	}{
		{"empty clears icon", "", false},
		{"png ok", iconDataURL("image/png", 128), false},
		{"jpeg ok", iconDataURL("image/jpeg", 128), false},
		{"svg ok", iconDataURL("image/svg+xml", 128), false},
		{"webp ok", iconDataURL("image/webp", 128), false},
		{"gif rejected", iconDataURL("image/gif", 128), true},
		{"not a data url", "https://example.com/icon.png", true},
		{"bad base64", "data:image/png;base64,!!!not-base64!!!", true},
		{"too large", iconDataURL("image/png", MaxEmbedLauncherIconBytes+1), true},
		{"at limit", iconDataURL("image/png", MaxEmbedLauncherIconBytes), false},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			err := ValidateEmbedLauncherIcon(tc.input)
			if tc.wantErr && !errors.Is(err, ErrEmbedLauncherIconInvalid) {
				t.Fatalf("ValidateEmbedLauncherIcon(%q) = %v, want ErrEmbedLauncherIconInvalid", tc.input[:min(len(tc.input), 40)], err)
			}
			if !tc.wantErr && err != nil {
				t.Fatalf("ValidateEmbedLauncherIcon() error = %v, want nil", err)
			}
		})
	}
}

func TestValidateEmbedLauncherIconRejectsOversizeString(t *testing.T) {
	// 即使 base64 合法,超长字符串也应快速拒绝(解码后超限)
	huge := iconDataURL("image/png", MaxEmbedLauncherIconBytes*2)
	if err := ValidateEmbedLauncherIcon(huge); !errors.Is(err, ErrEmbedLauncherIconInvalid) {
		t.Fatalf("error = %v, want ErrEmbedLauncherIconInvalid", err)
	}
	if !strings.HasPrefix(huge, "data:image/png;base64,") {
		t.Fatal("test setup broken")
	}
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `go test ./internal/application/service/ -run TestValidateEmbedLauncherIcon -v`
Expected: 编译失败，`undefined: ValidateEmbedLauncherIcon` / `undefined: ErrEmbedLauncherIconInvalid`

- [ ] **Step 3: 实现校验与模型字段**

`internal/types/embed_channel.go` — `EmbedChannel` 结构体在 `WebhookSecret` 字段后加：
```go
	LauncherIcon           string         `json:"launcher_icon"             gorm:"type:text;not null;default:''"`
```
（保持与现有字段相同的对齐风格；加在 `WebhookSecret` 之后、`CreatedAt` 之前。）

同文件 `EmbedChannelPublicConfig` 加：
```go
	LauncherIcon           string   `json:"launcher_icon,omitempty"`
```

`internal/application/service/embed_channel.go` — 在 `ErrEmbedWebhookURLInvalid` / `ValidateEmbedWebhookURL` 附近加（先 grep 找到它们的位置，保持同区）：
```go
// ErrEmbedLauncherIconInvalid marks a launcher icon that is not an allowed
// base64 image data URL or exceeds the decoded size cap.
var ErrEmbedLauncherIconInvalid = errors.New("invalid embed launcher icon")

// MaxEmbedLauncherIconBytes caps the decoded size of an embedded launcher icon.
const MaxEmbedLauncherIconBytes = 200 * 1024

var launcherIconDataURLPattern = regexp.MustCompile(`^data:image/(png|jpeg|svg\+xml|webp);base64,`)

// ValidateEmbedLauncherIcon enforces the data-URL format and decoded size cap.
// An empty string clears the icon and is always valid.
func ValidateEmbedLauncherIcon(v string) error {
	if v == "" {
		return nil
	}
	loc := launcherIconDataURLPattern.FindStringIndex(v)
	if loc == nil || loc[0] != 0 {
		return fmt.Errorf("%w: must be a base64 data URL of png/jpeg/svg/webp", ErrEmbedLauncherIconInvalid)
	}
	decoded, err := base64.StdEncoding.DecodeString(v[loc[1]:])
	if err != nil {
		return fmt.Errorf("%w: invalid base64 payload", ErrEmbedLauncherIconInvalid)
	}
	if len(decoded) > MaxEmbedLauncherIconBytes {
		return fmt.Errorf("%w: image exceeds %d bytes", ErrEmbedLauncherIconInvalid, MaxEmbedLauncherIconBytes)
	}
	return nil
}
```
并按需在文件头 import 加 `encoding/base64`、`regexp`（`errors`、`fmt` 应已存在，没有则补上）。

- [ ] **Step 4: 运行测试确认通过**

Run: `go test ./internal/application/service/ -run TestValidateEmbedLauncherIcon -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add internal/types/embed_channel.go internal/application/service/embed_channel.go internal/application/service/embed_channel_launcher_icon_test.go
git commit -m "feat: add launcher icon field and validation for embed channels"
```

---

### Task 3: 后端 — Service 层 Update / PublicConfig（TDD)

**Files:**
- Modify: `internal/application/service/embed_channel.go`（`Update` 方法 ~L119、`PublicConfig` 方法 ~L236）
- Test: `internal/application/service/embed_channel_update_test.go`、`internal/application/service/embed_channel_public_config_test.go`

- [ ] **Step 1: 写失败测试**

`internal/application/service/embed_channel_update_test.go` 末尾追加：
```go
func TestEmbedChannelUpdateLauncherIcon(t *testing.T) {
	repo := &stubEmbedChannelRepo{
		ch: &types.EmbedChannel{ID: "ch-1", TenantID: 42, AgentID: "agent-1", Name: "Support"},
	}
	svc := &embedChannelService{repo: repo}

	// 不传指针(nil)= 不动该字段;先预置一个旧值验证不被清掉
	repo.ch.LauncherIcon = "data:image/png;base64,b2xk"
	updated, err := svc.Update(
		context.Background(), 42, "ch-1",
		&types.EmbedChannel{},
		nil, nil, nil, nil, nil, nil, nil, nil,
	)
	if err != nil {
		t.Fatalf("Update() error = %v", err)
	}
	if updated.LauncherIcon != "data:image/png;base64,b2xk" {
		t.Fatalf("LauncherIcon = %q, want unchanged", updated.LauncherIcon)
	}

	// 传入合法 data URL = 更新
	icon := "data:image/png;base64,bmV3"
	updated, err = svc.Update(
		context.Background(), 42, "ch-1",
		&types.EmbedChannel{},
		nil, nil, nil, nil, nil, nil, nil, &icon,
	)
	if err != nil {
		t.Fatalf("Update() error = %v", err)
	}
	if updated.LauncherIcon != icon {
		t.Fatalf("LauncherIcon = %q, want %q", updated.LauncherIcon, icon)
	}

	// 传入空串 = 清除
	empty := ""
	updated, err = svc.Update(
		context.Background(), 42, "ch-1",
		&types.EmbedChannel{},
		nil, nil, nil, nil, nil, nil, nil, &empty,
	)
	if err != nil {
		t.Fatalf("Update() error = %v", err)
	}
	if updated.LauncherIcon != "" {
		t.Fatalf("LauncherIcon = %q, want cleared", updated.LauncherIcon)
	}

	// 传入非法值 = 报错
	bad := "https://evil.example.com/x.png"
	if _, err = svc.Update(
		context.Background(), 42, "ch-1",
		&types.EmbedChannel{},
		nil, nil, nil, nil, nil, nil, nil, &bad,
	); !errors.Is(err, ErrEmbedLauncherIconInvalid) {
		t.Fatalf("Update() error = %v, want ErrEmbedLauncherIconInvalid", err)
	}
}
```
（文件头 import 需加 `"errors"`。）

`internal/application/service/embed_channel_public_config_test.go` 末尾追加：
```go
func TestPublicConfigIncludesLauncherIcon(t *testing.T) {
	svc := &embedChannelService{}
	cfg := svc.PublicConfig(context.Background(), &types.EmbedChannel{
		ID:           "ch-icon",
		AgentID:      "agent-1",
		LauncherIcon: "data:image/png;base64,aWNvbg==",
	})
	if cfg.LauncherIcon != "data:image/png;base64,aWNvbg==" {
		t.Fatalf("launcher_icon = %q, want data URL", cfg.LauncherIcon)
	}
}
```
注意：现有测试构造 `svc.Update(...)` 是 7 个尾参；本计划把签名扩成 8 个，`embed_channel_update_test.go` 里已有的 `TestEmbedChannelUpdateAgentID` 调用也要补一个 `nil`。

- [ ] **Step 2: 运行测试确认失败**

Run: `go test ./internal/application/service/ -run 'TestEmbedChannelUpdate|TestPublicConfig' -v`
Expected: 编译失败（Update 参数数量不匹配 / `LauncherIcon` 未定义）

- [ ] **Step 3: 实现**

`internal/application/service/embed_channel.go` `Update` 签名尾部加 `launcherIcon *string`：
```go
func (s *embedChannelService) Update(
	ctx context.Context, tenantID uint64, id string, req *types.EmbedChannel,
	enabled *bool, showSuggested *bool, allowWebSearch *bool, allowFileUpload *bool,
	defaultLocale *string, webhookURL *string, webhookSecret *string, launcherIcon *string,
) (*types.EmbedChannel, error) {
```
方法体内（`webhookSecret` 处理块之后）加：
```go
	if launcherIcon != nil {
		trimmed := strings.TrimSpace(*launcherIcon)
		if err := ValidateEmbedLauncherIcon(trimmed); err != nil {
			return nil, err
		}
		ch.LauncherIcon = trimmed
	}
```

`PublicConfig` 返回结构体加一行（`DefaultLocale` 之后）：
```go
		LauncherIcon:            ch.LauncherIcon,
```

检查 `internal/types/interfaces` 中 `EmbedChannelService` 接口若声明了 `Update`，同步加参（grep `Update(` in `internal/types/interfaces/embed*.go`）。

- [ ] **Step 4: 运行测试确认通过**

Run: `go test ./internal/application/service/ -run 'TestEmbedChannelUpdate|TestPublicConfig' -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add internal/application/service/ internal/types/
git commit -m "feat: wire launcher icon through embed channel service"
```

---

### Task 4: 后端 — Handler 接线

**Files:**
- Modify: `internal/handler/embed_channel.go`

- [ ] **Step 1: 请求结构体加字段**

`embedChannelRequest`（~L62）在 `AgentID` 后加：
```go
	LauncherIcon           *string  `json:"launcher_icon"`
```

- [ ] **Step 2: Create 与 Update 接线**

`CreateEmbedChannel`：校验块（`validateAllowedOrigins` 调用之后）加：
```go
	if req.LauncherIcon != nil {
		if err := service.ValidateEmbedLauncherIcon(strings.TrimSpace(*req.LauncherIcon)); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}
	}
```
创建 `types.EmbedChannel{...}` 字面量加：
```go
		LauncherIcon:           stringOrEmpty(req.LauncherIcon),
```

`UpdateEmbedChannel`：`req.WebhookURL != nil` 校验块之后加：
```go
	if req.LauncherIcon != nil {
		if err := service.ValidateEmbedLauncherIcon(strings.TrimSpace(*req.LauncherIcon)); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}
	}
```
`h.embedSvc.Update(...)` 调用尾部追加实参 `req.LauncherIcon`。

- [ ] **Step 3: 响应与错误映射**

`embedChannelResponse`（~L778）的 `row` map 在 `"default_locale"` 后加：
```go
		"launcher_icon":            ch.LauncherIcon,
```

`writeEmbedMgmtError`（~L808）switch 加分支：
```go
	case errors.Is(err, service.ErrEmbedLauncherIconInvalid):
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
```

- [ ] **Step 4: 编译 + 跑全部 embed 相关测试**

Run: `go build ./internal/... && go test ./internal/handler/ -run Embed -v && go test ./internal/application/service/ -run Embed -v`
Expected: 编译通过，全部 PASS

- [ ] **Step 5: Commit**

```bash
git add internal/handler/embed_channel.go
git commit -m "feat: accept and return launcher icon in embed channel APIs"
```

---

### Task 5: 前端 — API 类型

**Files:**
- Modify: `frontend/src/api/embed/index.ts`

- [ ] **Step 1: 加类型字段**

`EmbedChannel` 接口（`primary_color?: string` 后）加：
```ts
  launcher_icon?: string
```
`EmbedChannelPublicConfig` 接口同样位置加：
```ts
  launcher_icon?: string
```

- [ ] **Step 2: 类型检查**

Run: `cd frontend && npx vue-tsc --noEmit -p tsconfig.json 2>&1 | tail -5`（若项目有 `npm run type-check` 之类的脚本优先用它，见 `frontend/package.json` scripts）
Expected: 无新增报错

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api/embed/index.ts
git commit -m "feat: add launcher_icon to embed channel types"
```

---

### Task 6: 前端 — 设置面板上传控件与表单接线

**Files:**
- Modify: `frontend/src/components/AgentEmbedChannelPanel.vue`
- Modify: `frontend/src/i18n/locales/zh-CN.ts`、`en-US.ts`、`ja-JP.ts`、`ko-KR.ts`、`ru-RU.ts`（`embedPublish` 段，紧跟 `primaryColor` key;key 必须 5 个语言包一致，有 `localeKeyAudit.test.ts` 校验）

- [ ] **Step 1: 模板 — 上传控件**

在 primaryColor 的 `form-item`（~L235-239）之后插入：
```html
          <div class="form-item">
            <label class="form-label">{{ $t('embedPublish.launcherIcon') }}</label>
            <div class="launcher-icon-field">
              <div class="launcher-icon-preview"
                :style="{ background: form.primary_color || defaultPrimaryColor }" aria-hidden="true">
                <img v-if="form.launcher_icon" :src="form.launcher_icon" alt="" />
                <t-icon v-else name="chat" />
              </div>
              <t-button size="small" variant="outline" :disabled="!isAdmin" @click="triggerLauncherIconPick">
                {{ $t('embedPublish.launcherIconUpload') }}
              </t-button>
              <t-button v-if="form.launcher_icon" size="small" variant="text" :disabled="!isAdmin"
                @click="form.launcher_icon = ''">
                {{ $t('embedPublish.launcherIconRemove') }}
              </t-button>
              <input ref="launcherIconInput" type="file" style="display:none"
                accept="image/png,image/jpeg,image/svg+xml,image/webp" @change="handleLauncherIconChange" />
            </div>
            <p class="form-desc">{{ $t('embedPublish.launcherIconDesc') }}</p>
          </div>
```

并把同区预览浮标（~L245-248）改为：
```html
                <button type="button" class="preview-launcher"
                  :style="{ background: form.primary_color || defaultPrimaryColor }" aria-hidden="true">
                  <img v-if="form.launcher_icon" :src="form.launcher_icon" class="preview-launcher__img" alt="" />
                  <t-icon v-else name="chat" />
                </button>
```

- [ ] **Step 2: 脚本 — 状态与处理函数**

`defaultForm()`（~L502）返回对象加：
```ts
  launcher_icon: '',
```

`form` 声明后加：
```ts
const launcherIconInput = ref<HTMLInputElement | null>(null)
const LAUNCHER_ICON_MAX_BYTES = 200 * 1024
const LAUNCHER_ICON_TYPES = ['image/png', 'image/jpeg', 'image/svg+xml', 'image/webp']

function triggerLauncherIconPick() {
  launcherIconInput.value?.click()
}

function handleLauncherIconChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!LAUNCHER_ICON_TYPES.includes(file.type)) {
    MessagePlugin.warning(t('embedPublish.launcherIconInvalidType'))
    return
  }
  if (file.size > LAUNCHER_ICON_MAX_BYTES) {
    MessagePlugin.warning(t('embedPublish.launcherIconTooLarge'))
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    form.value.launcher_icon = typeof reader.result === 'string' ? reader.result : ''
  }
  reader.readAsDataURL(file)
}
```
（`MessagePlugin`、`t`、`ref` 均已导入，若缺则补导入。）

加载渠道到表单的赋值处（~L815-830 `form.value = {...}`）加：
```ts
    launcher_icon: ch.launcher_icon || '',
```

保存 payload（~L911-928）加：
```ts
      launcher_icon: form.value.launcher_icon,
```

预览构造（~L1012-1019 `previewChannel.value = {...}`）加：
```ts
      launcher_icon: opts?.useDraft ? form.value.launcher_icon : ch.launcher_icon,
```

模板底部 `<EmbedChannelPreview ...>`（~L389-392）加 prop：
```html
      :launcher-icon="previewChannel?.launcher_icon"
```

- [ ] **Step 3: 样式**

文件 `<style>` 区追加：
```css
.launcher-icon-field {
  display: flex;
  align-items: center;
  gap: 8px;
}
.launcher-icon-preview {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  overflow: hidden;
  flex-shrink: 0;
}
.launcher-icon-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.preview-launcher {
  overflow: hidden;
}
.preview-launcher__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
```

- [ ] **Step 4: i18n 词条（5 个语言包，加在 `embedPublish` 段 `primaryColor` 之后）**

zh-CN.ts:
```ts
    launcherIcon: '浮标图片',
    launcherIconDesc: '自定义右下角浮标的图片,支持 PNG/JPEG/SVG/WebP,不超过 200KB。留空则显示默认对话图标。',
    launcherIconUpload: '上传图片',
    launcherIconRemove: '清除',
    launcherIconTooLarge: '图片不能超过 200KB',
    launcherIconInvalidType: '仅支持 PNG、JPEG、SVG 或 WebP 图片',
```
en-US.ts:
```ts
    launcherIcon: 'Launcher icon',
    launcherIconDesc: 'Custom image for the floating launcher button. PNG/JPEG/SVG/WebP up to 200 KB. Leave empty for the default chat icon.',
    launcherIconUpload: 'Upload image',
    launcherIconRemove: 'Remove',
    launcherIconTooLarge: 'Image must be 200 KB or smaller',
    launcherIconInvalidType: 'Only PNG, JPEG, SVG or WebP images are supported',
```
ja-JP.ts:
```ts
    launcherIcon: 'ランチャー画像',
    launcherIconDesc: '右下のフローティングボタンの画像。PNG/JPEG/SVG/WebP、200KB以下。空の場合はデフォルトアイコン。',
    launcherIconUpload: '画像をアップロード',
    launcherIconRemove: 'クリア',
    launcherIconTooLarge: '画像は200KB以下にしてください',
    launcherIconInvalidType: 'PNG、JPEG、SVG、WebPのみ対応しています',
```
ko-KR.ts:
```ts
    launcherIcon: '런처 아이콘',
    launcherIconDesc: '오른쪽 하단 플로팅 버튼 이미지. PNG/JPEG/SVG/WebP, 200KB 이하. 비워두면 기본 아이콘 표시.',
    launcherIconUpload: '이미지 업로드',
    launcherIconRemove: '지우기',
    launcherIconTooLarge: '이미지는 200KB 이하여야 합니다',
    launcherIconInvalidType: 'PNG, JPEG, SVG, WebP만 지원합니다',
```
ru-RU.ts:
```ts
    launcherIcon: 'Иконка кнопки',
    launcherIconDesc: 'Своя картинка плавающей кнопки. PNG/JPEG/SVG/WebP до 200 КБ. Пусто — значок по умолчанию.',
    launcherIconUpload: 'Загрузить',
    launcherIconRemove: 'Убрать',
    launcherIconTooLarge: 'Картинка должна быть не больше 200 КБ',
    launcherIconInvalidType: 'Поддерживаются только PNG, JPEG, SVG или WebP',
```

- [ ] **Step 5: 校验**

Run: `cd frontend && npx vue-tsc --noEmit 2>&1 | tail -5 && node --test src/i18n/localeKeyAudit.test.ts 2>&1 | tail -5`
Expected: 无类型错误；locale 测试 pass

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/AgentEmbedChannelPanel.vue frontend/src/i18n/locales/
git commit -m "feat: launcher icon upload in embed channel settings"
```

---

### Task 7: 前端 — EmbedChannelPreview 预览组件

**Files:**
- Modify: `frontend/src/components/EmbedChannelPreview.vue`

- [ ] **Step 1: 加 prop 与模板**

props（`primaryColor?: string` 后）加：
```ts
  /** Custom launcher image as a data URL; empty falls back to the chat icon. */
  launcherIcon?: string
```

widget 模式浮标按钮（~L29-33）改为：
```html
          <button type="button" class="widget-launcher" :style="{ background: primaryColor || 'var(--td-brand-color)' }"
            :aria-label="widgetOpen ? $t('common.close') : $t('embedPublish.preview')"
            @click="widgetOpen = !widgetOpen">
            <img v-if="!widgetOpen && launcherIcon" :src="launcherIcon" class="widget-launcher__img" alt="" />
            <t-icon v-else :name="widgetOpen ? 'close' : 'chat'" />
          </button>
```

- [ ] **Step 2: 样式**

`.widget-launcher` 规则加 `overflow: hidden;`，并新增：
```css
.widget-launcher__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
```

- [ ] **Step 3: 类型检查 + Commit**

Run: `cd frontend && npx vue-tsc --noEmit 2>&1 | tail -5`
Expected: 无新增报错

```bash
git add frontend/src/components/EmbedChannelPreview.vue
git commit -m "feat: show launcher icon in embed channel preview"
```

---

### Task 8: Widget — weknora-widget.js 渲染浮标图片

**Files:**
- Modify: `frontend/public/weknora-widget.js`

- [ ] **Step 1: 浮标内容渲染函数**

把 launcher 创建处（~L160 `launcher.textContent = '💬';`）替换为状态 + 渲染函数。在 `launcher.style.cssText = [...]` 赋值之后插入：
```js
    var launcherIconUrl = '';
    var launcherImg = null;

    function renderLauncherContent() {
      launcher.textContent = '';
      if (panelOpen) {
        launcher.textContent = '✕';
        return;
      }
      if (launcherIconUrl) {
        if (!launcherImg) {
          launcherImg = document.createElement('img');
          launcherImg.src = launcherIconUrl;
          launcherImg.alt = '';
          launcherImg.style.cssText =
            'width:100%;height:100%;object-fit:cover;border-radius:50%;' +
            'pointer-events:none;display:block';
          launcherImg.onerror = function () {
            launcherIconUrl = '';
            launcherImg = null;
            renderLauncherContent();
          };
        }
        launcher.style.overflow = 'hidden';
        launcher.appendChild(launcherImg);
        return;
      }
      launcher.textContent = '💬';
    }
    renderLauncherContent();
```
并删掉原来的 `launcher.textContent = '💬';` 一行。

- [ ] **Step 2: 打开/关闭切换改用渲染函数**

把 toggle 处的（~L336）：
```js
          launcher.textContent = panelOpen ? '✕' : '💬';
```
改为：
```js
          renderLauncherContent();
```

- [ ] **Step 3: 拉取渠道公开 config 应用浮标图片**

在 launcher 相关代码之后（launcher 被 `appendChild` 的附近）加：
```js
    // Fetch the channel's public config for appearance extras (launcher icon).
    // Runs after the token is available; failures keep the default 💬 launcher.
    function loadChannelAppearance() {
      loadToken().then(function (tok) {
        return fetch(baseUrl + '/api/v1/embed/' + encodeURIComponent(channelId) + '/config', {
          headers: { Authorization: 'Embed ' + tok, Accept: 'application/json' },
        });
      }).then(function (res) {
        if (!res || !res.ok) return null;
        return res.json();
      }).then(function (payload) {
        var cfg = payload && payload.data;
        if (cfg && typeof cfg.launcher_icon === 'string' && cfg.launcher_icon) {
          launcherIconUrl = cfg.launcher_icon;
          launcherImg = null;
          renderLauncherContent();
        }
      }).catch(function () { /* keep default launcher */ });
    }
    loadChannelAppearance();
```

- [ ] **Step 4: 语法检查 + Commit**

Run: `node --check frontend/public/weknora-widget.js && echo OK`
Expected: OK

```bash
git add frontend/public/weknora-widget.js
git commit -m "feat: render custom launcher icon in embed widget"
```

---

### Task 9: 本地构建与端到端验证

**Files:** 无（运维操作）

- [ ] **Step 1: 重建镜像并重启**

```bash
cd /Users/billy/WeKnora
docker compose build app frontend
docker compose up -d
```
Expected: app 启动日志含迁移到 000096;`docker compose ps` 全部 healthy
（app 构建较慢；如想加速可在 .env 设 `WITH_ANYDOC=0` 后重建。）

- [ ] **Step 2: 验证迁移生效**

Run: `docker exec WeKnora-postgres psql -U postgres -d WeKnora -c "\d embed_channels" | grep launcher_icon`
Expected: 输出含 `launcher_icon | text | not null`

- [ ] **Step 3: 验证后端 API**

Run（替换 `<JWT>` 为登录后 token，或在 UI 操作代替）:
```bash
curl -s http://localhost:8081/api/v1/embed/b5fd1a34-f1d3-4ea6-9ca9-aca2b7df121c/config \
  -H "Authorization: Embed em_RIcI0-X4_IGbHM7f1Uc5ZN5NluhjyAmC-5YSrpnxLPs" | grep -o launcher_icon
```
Expected: 输出 `launcher_icon`（未配置时因 omitempty 可能缺省，属正常；在 UI 上传图片后应返回 data URL）

- [ ] **Step 4: UI 手工验证**

1. 打开 http://localhost → 发布集成 → 编辑「智能推理 · 网页嵌入」渠道 → 外观步骤
2. 「浮标图片」上传一张 PNG(≤200KB)→ 预览浮标显示图片 → 保存
3. 打开 http://localhost/pages/ → 右下角浮标显示该图片
4. 点开面板 → 浮标变 ✕;收起 → 恢复图片
5. 回到设置清除图片并保存 → 刷新 /pages/ → 浮标回退 💬

- [ ] **Step 5: Commit（如有验证期修复）**

```bash
git add -A && git commit -m "fix: issues found during launcher icon e2e verification"
```
