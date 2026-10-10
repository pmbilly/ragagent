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
| 打标 | `common/web/ApiResultInterceptor`（注册于 `config/WebConfig`，`order = -100`）| 早于 API-Key / RBAC 门禁运行 ⇒ 被门禁拒绝的请求同样拿到新形态 |
| 前端 | `frontend/src/utils/request.ts` 响应拦截器 | **全前端唯一解包点**：`2xx + 外壳` ⇒ 解包出 `data`；`code != 0` ⇒ reject；`4xx/5xx` ⇒ 摊平错误体（`err.message` 恒可用，`err.code` / `err.error?.code` 按形态各自可读）|

判定外壳用的是「**恰好**由 code/message/data 三键构成、且 `code` 为数字」——避免把业务载荷里
恰好叫 `code`/`data` 的对象误判。后端 `ApiResponse` 若扩展键集，前端判定同步扩展。

## 4. 已知例外（本机制覆盖不到，保持原样）

- **`AuthFilter` 等 Filter 写的 401**：在 DispatcherServlet **之前**就写出响应，拿不到控制器打标。
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
- 进度：**3 / 52 个控制器**（agent 域 17 个端点为试点，2026-10-10 B183）。

| 步 | 内容 | 状态 |
|---|---|---|
| P0 | 本约定 + 现状盘点（52 控制器 / ~370 端点 / 4 种形状混装）| ✅ |
| P1 | 设施：`ApiResponse` / `@ApiResult` / advice / 错误分派 / 前端解包点 / 守卫 | ✅ |
| P2 | 按域迁移：**agent ✅** → knowledge → wiki → session → … | 进行中 |
| P3 | Filter 层错误（401）与文件下载面的形态统一 | 待办 |

## 6. 已退役的旧规则（别再引用）

- ~~"响应以 Go 线格式为准"~~ —— 迁移期产物，随脱 Go 退役。
- ~~`{success:true,data:…}` 手搓外壳~~ —— `AgentResponses.envelope/deletedEnvelope` 已删。
- ~~"两种形态并存是有意为之，别统一"~~（`DataSourceCredentialsController` 类注释）—— Go 期结论，作废。
- ~~"裸载荷 API 必须在 api 层适配成 `{data}`"~~ —— 解包点唯一化后作废
  （前端契约测试 `crossFaceKeyContract.test.ts` 已改写为"一处解包"）。
- ~~在 `@ApiResult` 控制器里再手搓外壳~~ —— 守卫 R-d 抓"双壳"。
