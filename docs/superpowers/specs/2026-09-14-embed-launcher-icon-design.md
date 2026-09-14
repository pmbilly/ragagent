# 网页嵌入 Widget 浮标图片自定义 — 设计文档

日期：2026-09-14
范围：仅本地部署使用（不按上游 PR 标准补齐 i18n/全量测试）
方案：上传图片 → 前端转 base64 data URL → 存 `embed_channels` 新字段 → Widget config 下发 → 浮标渲染 `<img>`

## 背景与目标

发布集成 → 网页嵌入渠道的浮标（右下角圆形按钮）目前固定显示 💬 emoji（`frontend/public/weknora-widget.js` 第 160 行 `launcher.textContent = '💬'`）。用户希望在渠道外观设置中上传一张图片，浮标显示该图片，嵌入第三方站点时同样生效。

## 总体设计

复用现有的「渠道配置 → `/api/v1/embed/<channel_id>/config` 公开接口 → widget.js 渲染」链路，新增一个 `launcher_icon` 配置项，值为 base64 data URL（如 `data:image/png;base64,...`）。

选择 data URL 而非文件存储 URL 的原因：嵌入场景下第三方站点直接可用，无需处理资源 URL 鉴权、`RESOURCE_URL_MODE`、跨域等问题。代价是图片字节存 DB，对几十 KB 的浮标图标可接受。

## 改动点

### 1. 数据库迁移

- 目录：`migrations/versioned/`，新增 `000096_embed_launcher_icon.up.sql` / `.down.sql`
- 内容：`ALTER TABLE embed_channels ADD COLUMN launcher_icon text NOT NULL DEFAULT '';`（down 为 `DROP COLUMN`）
- 启动时 app 自动执行迁移（沿用现有机制）

### 2. 后端

- `internal/types/embed_channel.go`
  - `EmbedChannel` 模型加字段：`LauncherIcon string \`json:"launcher_icon" gorm:"type:text;not null;default:''"\``
  - `EmbedChannelPublicConfig` 加 `LauncherIcon string \`json:"launcher_icon,omitempty"\``
  - 更新请求结构体加 `launcher_icon` 字段
- `internal/handler/embed_channel.go`
  - 更新渠道时校验 `launcher_icon`：允许空字符串或 `data:image/(png|jpeg|svg+xml|webp);base64,` 前缀，解码后 ≤ 200KB，超限/格式错误返回 400
  - 公开 config 接口（`GET /api/v1/embed/<id>/config`）响应带上 `launcher_icon`
  - 管理端 GET/PUT 响应带上该字段（现有 embed channel CRUD 已逐字段返回，跟随现有模式即可）

### 3. 设置 UI

- `frontend/src/components/AgentEmbedChannelPanel.vue`（渠道外观设置区，紧跟「主题色」之后）
  - 新增「浮标图片」控件：上传按钮（`accept="image/png,image/jpeg,image/svg+xml,image/webp"`）、FileReader 读为 data URL、前端预校验（≤200KB）、缩略预览、清除按钮
  - 保存时随渠道更新请求提交 `launcher_icon`
- `frontend/src/components/EmbedChannelPreview.vue`
  - 预览浮标：有 `launcher_icon` 时显示圆形裁剪的图片，否则保持 💬

### 4. Widget（`frontend/public/weknora-widget.js`）

- config 响应解析出 `launcher_icon`
- 创建 launcher 按钮时：若 `launcher_icon` 非空，创建 `<img>`（`width/height:100%`、`object-fit:cover`、`border-radius:50%`、`pointer-events:none`）填充按钮，`overflow:hidden` 圆形裁剪；否则保持 `💬`
- 面板打开/关闭切换逻辑（现第 336 行 `launcher.textContent = panelOpen ? '✕' : '💬'`）改为：打开时隐藏图片显示 ✕，关闭时恢复图片
- 图片 `onerror` 时移除 `<img>` 回退到 💬

## 数据流

1. 管理员在发布集成设置页上传图片 → base64 data URL 随 PUT `/api/v1/embed-channels/<id>` 落库
2. 访客打开嵌入了 Widget 的页面 → widget.js 拉取公开 config → 得到 `launcher_icon`
3. 浮标渲染图片；面板展开时显示 ✕，收起恢复图片

## 错误处理

| 场景 | 行为 |
|------|------|
| 上传图片 >200KB 或格式不支持 | 前端拦截并提示；后端二次校验返回 400 |
| 第三方站点加载图片失败 | `onerror` 回退 💬 |
| 未配置图片（默认） | 与现状完全一致，💬 |
| 旧数据（无该列） | 迁移默认 `''`，视为未配置 |

## 测试

- 后端：`internal/handler/embed_channel_test.go` 补充 launcher_icon 校验用例（合法 data URL、超限、非法前缀）
- 手工验证（本地）：
  1. `docker compose build frontend && docker compose up -d`（app 镜像含 Go 代码，也需 `docker compose build app`）
  2. 设置页上传图片 → http://localhost/pages/ 浮标显示图片
  3. 清除图片 → 回退 💬
  4. 打开/收起面板，✕ 与图片切换正常

## 非目标（YAGNI)

- 不做 URL 引用外部图片、不做图片裁剪编辑器、不做 i18n 多语言词条、不做上游 PR 级测试覆盖
- 不改 ✕ 打开状态样式、不改浮标尺寸
