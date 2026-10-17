# API 响应约定

> 所有 REST 端点（除下表的例外）共用一套外壳。本文是**唯一口径**，代码注释引用此处。

## 成功

```json
{ "code": 0, "message": "ok", "data": <载荷> }
```

- 标注 `@ApiResult` 的控制器由 `ApiResultAdvice` 自动包壳——控制器返回裸对象即可。
- `data` **恒存在**（无载荷时为 `null`），前端无需判空键。
- HTTP 状态码表达协议语义：创建 201、读取/更新 200；删除返 200（不用 204）。
- 分页载荷统一 `{ items, page, pageSize, total }`。

## 错误

```json
{ "code": <非 0>, "message": "<可展示文案>", "data": <明细或 null> }
```

- 由 `GlobalExceptionHandler` 统一输出；业务代码抛 `BizException(AppError)` 即可。
- `code` 见 `contract.error.ErrorCode`：通用段 1000+，业务分段按域分配。
- HTTP 状态码与 `code` **同时**表达语义（400/401/403/404/409/500）。
- 500 不透出内部异常文案：日志保留完整堆栈，响应固定 `Internal server error`。

## 例外（有意为之，勿"统一"）

| 端点 | 形态 | 原因 |
|---|---|---|
| `GET /health` | `{"status":"ok"}` | k8s / docker healthcheck 依赖固定形态 |
| `/api/v1/im/callback/*` | 平台协议原样 | 微信 / 云之家等第三方 ACK 由对方规定 |
| 未映射路径 | `text/plain "404 page not found"` | 无 Handler，打不上 `@ApiResult` 标 |

## 前端解析

- `frontend/src/utils/request.ts` 的响应拦截器：2xx 取 `data`，非 2xx 取 `message`。
- 拦截器保留旧形态兜底（`typeof data.error === 'string'`）：外部回调与历史响应仍可能出现。
- 业务代码不要再读 `data.error.message`——已统一为 `data.message`。
