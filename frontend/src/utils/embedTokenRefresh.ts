/**
 * Embed 访客会话票（`ems_`，后端 30 分钟 TTL）的续期入口。
 *
 * <p>换票能力由嵌入页（`useEmbedBridge`）持有——它有发布票或能向宿主重新要票；
 * axios 拦截器与 SSE 握手两条平面在 401 时从这里取新票并**重放一次**，
 * 于是"页面开久了票过期"对访客不可见。</p>
 *
 * <p>单飞：并发 401 只触发一次换票，其余调用方共享同一结果（`authRefresh` 同款范式）。</p>
 */

export type EmbedTokenRefresher = () => Promise<string | null>

let refresher: EmbedTokenRefresher | null = null
let inFlight: Promise<string | null> | null = null

/** 注册/注销换票函数（嵌套页挂载时注册、卸载时注销）。 */
export function registerEmbedTokenRefresher(fn: EmbedTokenRefresher | null): void {
  refresher = fn
  inFlight = null
}

export function hasEmbedTokenRefresher(): boolean {
  return refresher !== null
}

/** 单飞换票；无注册者或换票失败 → null（调用方按原错误上抛，不吞异常）。 */
export function refreshEmbedTokenShared(): Promise<string | null> {
  const fn = refresher
  if (!fn) return Promise.resolve(null)
  if (!inFlight) {
    inFlight = Promise.resolve()
      .then(() => fn())
      .catch(() => null)
      .finally(() => {
        inFlight = null
      })
  }
  return inFlight
}

/** 单测复位。 */
export function resetEmbedTokenRefreshForTests(): void {
  refresher = null
  inFlight = null
}
