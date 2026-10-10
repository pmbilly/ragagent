# 统一响应外壳约定（ApiResponse）

> 决策时间：**2026-10-10（B183）**。
> 本项目已脱离 Go 仓 —— 响应格式**不再以 Go 线格式为准**，改按 **Java/Spring 工程的通行做法**
> 自定一套：**一套外壳 + 一个解包点**。

## 1. 外壳

成功（HTTP 200 / 201 …）：

```json
{ "code": 0, "message": "ok", "data": { "…载荷…": "…" } }
```

失败（HTTP 400 / 401 / 403 / 404 / 409 / 429 / 500 / 503 …）：

```json
{ "code": 1003, "message": "Agent not found", "data": null }
```

| 字段 | 约定 |
|---|---|
| `code` | `0` = 成功；非 0 用 `ErrorCode` 数字码（通用 1000 段 + 业务分段，如 agent 2100 段）。与错误体**同码表** |
| `message` | 可展示文案。成功默认 `"ok"`；需要时自定义（如 DELETE 的 `"Agent deleted successfully"`）|
| `data` | 载荷本体（对象 / 数组 / null），**恒存在**。失败时承载 details（如字段级校验明细）|

- 键序恒为 `code → message → data`（Jackson 记录组件序）。
- **`data` 放什么**：放该端点的载荷本体。容器型载荷（列表 + 伴随字段，如 `{agents, disabledOwnAgentIds}`）
  保持原容器形状，**不要为了对称再套一层**。

## 2. 三条铁律

1. **`success ⇔ code == 0`**，不存在第二个真相 —— 外壳只经 `ApiResponse.ok(...)` / `ApiResponse.fail(...)`
   构造（record 的规范构造由工厂封装，物理上不给独立赋值的机会）。
2. **HTTP 状态码仍表达协议语义**（401 / 404 / 409 / 500 …），body 里再给业务 `code`。
   不做"一律 200" —— 否则网关重试、监控告警、日志统计全部失明。
3. **所有出参只经一个 advice** —— 控制器**不手搓**外壳（`Map.of("success", …)` 一律退役）。

## 3. 落地机制

| 侧 | 实现 | 说明 |
|---|---|---|
| 成功 | `common/web/ApiResultAdvice`（`ResponseBodyAdvice`）+ 类级注解 `@ApiResult` | 控制器方法**只返回载荷**；`ResponseEntity` 的 201 等状态码不受影响；已是 `ApiResponse` 的原样放行（要自定义 message 时自己返回）；非 JSON（下载 / SSE / text）跳过 |
| 错误 | `common/error/GlobalExceptionHandler` | 按「请求是否命中 `@ApiResult` 控制器」分派：命中 ⇒ 新外壳；否则 ⇒ 历史 `{"error":{code,message,details}}` |
| 打标 | `common/web/ApiResultInterceptor`（注册于 `config/WebConfig`，`order = -100`）| 早于 API-Key / RBAC 门禁运行 |
| 自写响应的**中间件** | `common/web/RbacInterceptor`（403）| **读打标**决定体形态：命中 `@ApiResult` ⇒ `{code:1002,message,data:null}`，否则历史纯字符串。**规则**：任何「不走异常处理器、自己写响应体」的 HandlerInterceptor 都必须这样读打标（B185）|
| 前端 | `frontend/src/utils/request.ts` 响应拦截器 | **全前端唯一解包点**：`2xx + 外壳` ⇒ 解包出 `data`；`code != 0` ⇒ reject；`4xx/5xx` ⇒ 摊平错误体（`err.message` 恒可用，`err.code` / `err.error?.code` 按形态各自可读）|

判定外壳用的是「**恰好**由 code/message/data 三键构成、且 `code` 为数字」——避免把业务载荷里
恰好叫 `code`/`data` 的对象误判。后端 `ApiResponse` 若扩展键集，前端判定同步扩展。

## 4. 已知例外（本机制覆盖不到，保持原样）


- **外部平台回调**：`ImCallbackController`（`/api/v1/im/callback/**`）全部端点 `void` + 直接写 `HttpServletResponse`（微信/云之家 ACK 等**平台协议原样**）。`ApiResultAdvice.supports()` 对 `void` 返回类型**直接跳过**（B191 ✓）⇒ 既不会包壳也不会双写。响应体形状是平台协议，不属于本仓 API 约定。
- **二进制 / 非 JSON**：`FileProxyController`（下载、Range）与任何 `produces` 非 `application/json` 的端点 —— advice 按 `selectedContentType` 跳过（B190 ✓）。
- **API-Key 过滤器**：`SystemAdminController` 的 API-key 门禁（`X-API-Key`）在 **DispatcherServlet 之前**
  写出 `{"error":"Forbidden: …"}`（拿不到路由打标 ✗）⇒ 保持原样，`adm-guard-platformkey.json` 金片按实际旧形态定稿（B192 ✓）。与 AuthFilter 同类。
- **auth 包的两个过滤器直写者**（B201 ✓）：`auth/filter/WsAuthSupport.java` 的 `writePlainError(403, "Forbidden: not a member of the target workspace")` 与
  `auth/apikey/filter/APIKeyAuthChannel.java` 的 `X-Tenant-ID` 校验（400/403 手写）——都在 **DispatcherServlet 之前**写出，
  形态保持旧样（`{"error":"…"}`）；对应金片（`x-tenant-id-forbidden-403` / `w5c-files-key-*` 等）**有意保持旧形态** ✓。
  ⚠️ 注意区分：**RBAC 拦截器**（`RbacInterceptor`）在 handler 之后，能拿到路由打标 ⇒ 输出**新外壳** ✓（B201 里 converge 自纠过一个误判 ✓）。
- **基础设施探针 `GET /health`**（`common/web/HealthController`）：k8s/docker healthcheck 与外部监控依赖
  `{"status":"ok"}` ⇒ **保持原样，不进外壳** ✗（B200 撤回了注解 ✓）。
- **AuthFilter 的 401/403**：在 DispatcherServlet 之前写出，不进 advice（B189 ✓ 已实测：金片保持原样也匹配 ✓）。

- **Filter 写的 401/403**（`AuthFilter` / `APIKeyAuthChannel` / `WsAuthSupport`）：在 DispatcherServlet **之前**就写出响应，打标尚未发生 ⇒ 形态不变。（**自写响应的 HandlerInterceptor 不属此列** —— 它们跑在打标之后，须读打标：`RbacInterceptor` 已于 B185 改造 ✓。）
  实测：`ag-noauth` / `ag-badtoken` / `ag-sq-noauth` 金片保持纯字符串 `{"error":"Unauthorized: …"}`
  且契约测试仍绿 —— 即该边界被实测确认，不是猜的。要统一需在 Filter 层单独改造（P3）。
- **未映射路径的 404**：`text/plain "404 page not found"`（没有 Handler ⇒ 打不上标）。
- **非 JSON 端点**（文件下载 / SSE）：外壳只在 `application/json` 上成立。
- **204 退役**：`204 No Content` 的空体与「外壳恒存在」冲突 ⇒ DELETE 类端点统一
  `200 + {code:0,message:"ok",data:null}`。

## 5. 迁移

- 开关是**类级注解** `@ApiResult`；未标注的控制器行为**完全不变**（零风险增量迁移）。
- **新控制器默认必须合规**：守卫 `scripts/check-api-envelope.py`（九守卫之一）要求每个控制器
  要么标注 `@ApiResult`，要么在 `scripts/api-envelope.baseline.json` 的 `pending` 清单里；清单
  **只许减不许增**，`--write` 只清理已迁完 / 幽灵条目（不替你豁免新条目）。
- 进度：**5 / 52 个控制器**（agent 17 端点 = 试点 B183；favorite 3 + vectorstore 8 = B185，2026-10-10）。

| 步 | 内容 | 状态 |
|---|---|---|
| P0 | 本约定 + 现状盘点（52 控制器 / ~370 端点 / 4 种形状混装）| ✅ |
| P1 | 设施：`ApiResponse` / `@ApiResult` / advice / 错误分派 / 前端解包点 / 守卫 | ✅ |
| P2 | 按域迁移：**agent ✅ → favorite ✅ / vectorstore ✅** → knowledge → wiki → session → … | 进行中 |
| P3 | **Filter 层**错误（AuthFilter / APIKeyAuthChannel / WsAuthSupport 的 401/403）与文件下载面的形态统一 | 待办 |

## 6. 已退役的旧规则（别再引用）

- ~~"响应以 Go 线格式为准"~~ —— 迁移期产物，随脱 Go 退役。
- ~~`{success:true,data:…}` 手搓外壳~~ —— `AgentResponses.envelope/deletedEnvelope` 已删。
- ~~"两种形态并存是有意为之，别统一"~~（`DataSourceCredentialsController` 类注释）—— Go 期结论，作废。
- ~~"裸载荷 API 必须在 api 层适配成 `{data}`"~~ —— 解包点唯一化后作废
  （前端契约测试 `crossFaceKeyContract.test.ts` 已改写为"一处解包"）。
- ~~在 `@ApiResult` 控制器里再手搓外壳~~ —— 守卫 R-d 抓"双壳"。

## 五、FE 消费点排查（每批固定一步 ✓）

**每批迁移后必跑这三条 grep**（覆盖 `frontend/src` + `frontend/packages` ✓，**不要**只按域名/文件名 grep ✗ —— 那会漏掉名字无关的调用方）：

```bash
grep -rnE '\\.success\\b' frontend/src frontend/packages --include='*.ts' --include='*.vue' | grep -vE 'MessagePlugin|\\.test\\.'
grep -rnE '\\b(response|res|resp|r|result)\\.data\\b' frontend/src frontend/packages --include='*.ts' --include='*.vue' | grep -vE '\\.test\\.'
grep -rnE 'error\\.(code|message|details)|\\.data\\.error\\b' frontend/src frontend/packages --include='*.ts' --include='*.vue'
```

**拦截器已双形态**（`frontend/src/utils/request.ts`）⇒ 大多数消费点**零改动** ✓：
- 成功侧（L144-150）：`code` 是数字且 `!== 0` ⇒ 抛错；`=== 0` ⇒ `withHttpStatus(data.data)` **拆一层** ✓；旧形态原样放行 ✓
- 错误侧（L216-226）：`error` 字符串 / `error.message` / **`data.message`** 依次兜底 ✓ ⇒ 新外壳的错误文案照常可用 ✓
- 副作用：`err` 上**摊平**了 `data`（L230）⇒ `err.code` 在新形态下可读 ✓

### ⚠️ 观察名单（迁移这些域时**必须**回改 FE ✗）

| 位置 | 读法 | 何时出事 |
|---|---|---|
| ~~`stores/auth.ts:326,391` · `utils/authRefresh.ts:131` · `utils/tenantSwitch.ts:92` · `api/tenant/members.ts:102` · `components/MyInvitationsDialog.vue:141` · `views/settings/TenantMembers.vue:817`~~ | `resp.success` / `resp.data`（旧遗留壳形状）| ✅ **B201 已落地**：统一改为「外壳后 `resp` 即载荷 + 旧壳容忍」（`(resp as any)?.data ?? resp` / `success !== false`）|
| ~~`App.vue:155`~~ | ~~`response.success`~~ | ❌ **误判更正（B201）**：那是 **OIDC 片段解码**（`decodeOIDCResult`），不是 HTTP 外壳 ⇒ **不要改** |
| `views/settings/ChatHistorySettings.vue:89,160` · `views/settings/RetrievalSettings.vue:146,179` | `response.data` ✗（依赖 api 层的 `{data: resp}` 包装 ✓）| 迁 **tenant KV** 时核对 |
| `components/McpMetadataPanel.vue:109` | `e?.response?.data?.error?.message` ✗（有 `e.message` 兜底 ⇒ 降级不崩 ✓）| 已迁 mcp ✓，可顺手清理 |
| `api/web-search-provider.ts:101` | `response.data ?? response`（**刻意容忍** ✓）| 无需改 ✓ |

**易混淆（不受影响 ✓，勿误改 ✗）**：载荷字段叫 `success` 的端点 —— `views/settings/StorageBackendSettings.vue:343`（`r.success`）✓、`views/settings/components/McpTestResultBody.vue` ✓、`composables/useEmbedCitationPopover.ts` ✓、以及所有 **SSE 事件**里的 `event.success` ✓（流不是 HTTP 外壳 ✓）。

### 附：B201 的两条新经验（auth 批）

1. **测试里的 login 助手必须下钻** ✗ —— 8 个契约测试各自从 `/api/v1/auth/login` 响应按**顶层**取 token（`node.get("token")`）；
   外壳化后 token 在 `data` 里 ⇒ 取到 null ⇒ 后续全部 401/NPE（本轮实测 **48 例失败** ✗，跨 7 个看似无关的测试）。
   取值点共 7 处（FaqContractTest / WebSearchProviderContractTest / W5cFileProxyContractTest / StorageBackendContractTest /
   VectorStoreContractTest / W5bInitializationContractTest / ValidationContractTest）+ W5c 的 api-key 取值 1 处。
2. **金片脚本只许跳过已含 `code` 的字典** ✗ —— 早先只跳过 `code == 0`，于是把 `{"code":1005,…}` 这类**错误体**又包了一层 ⇒
   出现**双层外壳**金片（本轮拆了 8 个 ✓）。
