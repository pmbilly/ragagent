import { StorageKeys } from './storageKeys'
/**
 * Shared access-token refresh used by both axios and the chat SSE client.
 *
 * Refresh tokens rotate, so concurrent 401s must queue behind one in-flight
 * /auth/refresh. The SSE handshake runs on raw fetch and cannot use the
 * axios interceptor; both planes call {@link refreshAccessTokenShared}.
 */

const AUTH_STORAGE_KEYS = [
  StorageKeys.token,
  StorageKeys.refreshToken,
  StorageKeys.user,
  StorageKeys.tenant,
  StorageKeys.knowledgeBases,
  StorageKeys.currentKb,
  StorageKeys.selectedTenantIdSnake,
  StorageKeys.selectedTenantName,
  StorageKeys.memberships,
] as const

let isRefreshing = false
let failedQueue: Array<{
  resolve: (token: string) => void
  reject: (error: unknown) => void
}> = []

export type TokenRefreshResult = {
  success: boolean
  data?: { token: string; refreshToken: string }
  message?: string
}

export type RefreshAccessTokenOptions = {
  refresh?: (refreshToken: string) => Promise<TokenRefreshResult>
  messages?: {
    pleaseRelogin: string
    tokenRefreshFailed: string
  }
}

const defaultMessages = {
  pleaseRelogin: 'Please log in again',
  tokenRefreshFailed: 'Token refresh failed',
}

export class StreamAuthError extends Error {
  constructor(readonly status: number) {
    super(`HTTP ${status}`)
    this.name = 'StreamAuthError'
  }
}

export function isStreamAuthError(err: unknown): boolean {
  if (!err || typeof err !== 'object') return false
  if (err instanceof StreamAuthError) return true
  return (err as { name?: string }).name === 'StreamAuthError'
}

export function isEmbedPage(): boolean {
  if (typeof window === 'undefined') return false
  return window.location.pathname.startsWith('/embed/')
}

export function redirectToLogin() {
  if (typeof window === 'undefined') return
  if (window.location.pathname === '/login') return
  if (isEmbedPage()) return
  window.location.href = '/login'
}

export function clearAuthStorage() {
  for (const key of AUTH_STORAGE_KEYS) {
    localStorage.removeItem(key)
  }
}

export function forceReloginRedirect() {
  clearAuthStorage()
  redirectToLogin()
}

function processQueue(error: unknown, token: string | null = null) {
  failedQueue.forEach(({ resolve, reject }) => {
    if (error) {
      reject(error)
    } else {
      resolve(token as string)
    }
  })
  failedQueue = []
}

async function defaultRefresh(refreshToken: string): Promise<TokenRefreshResult> {
  const { refreshToken: refreshTokenAPI } = await import('../api/auth/index')
  return refreshTokenAPI(refreshToken)
}

/**
 * Refresh the access token, de-duplicated across all callers.
 *
 * Resolves with the new access token. On failure it has already cleared
 * credentials and redirected to /login.
 */
export async function refreshAccessTokenShared(
  options: RefreshAccessTokenOptions = {},
): Promise<string> {
  const messages = { ...defaultMessages, ...options.messages }
  const refresh = options.refresh ?? defaultRefresh

  if (isRefreshing) {
    return new Promise<string>((resolve, reject) => {
      failedQueue.push({ resolve, reject })
    })
  }

  isRefreshing = true
  const storedRefreshToken = localStorage.getItem(StorageKeys.refreshToken)

  if (!storedRefreshToken) {
    clearAuthStorage()
    const noRefreshTokenError = new Error(messages.pleaseRelogin)
    processQueue(noRefreshTokenError, null)
    isRefreshing = false
    redirectToLogin()
    throw noRefreshTokenError
  }

  try {
    const response = await refresh(storedRefreshToken)

    // 统一外壳后 response 即载荷；旧的 {data,success} 壳也容忍
    const data: any = (response as any)?.data ?? response
    if (!data?.token) {
      throw new Error((response as any)?.message || messages.tokenRefreshFailed)
    }

    const { token, refreshToken: newRefreshToken } = data
    localStorage.setItem(StorageKeys.token, token)
    if (newRefreshToken) {
      localStorage.setItem(StorageKeys.refreshToken, newRefreshToken)
    }
    processQueue(null, token)
    return token
  } catch (refreshError) {
    clearAuthStorage()
    processQueue(refreshError, null)
    redirectToLogin()
    throw refreshError
  } finally {
    isRefreshing = false
  }
}

/**
 * Replay an SSE handshake once after a 401. Refresh failure already
 * redirected; a second 401 after a successful refresh is surfaced to the
 * caller without wiping the newly minted session (axios does the same).
 *
 * <p>Embed 访客没有 JWT：给 {@link refreshEmbedToken} 时按"换会话票 + 重放一次"
 * 处理（票 30 分钟过期，页面开久了必然遇到）；不给则维持旧行为（直接上抛）。
 * JWT 平面与嵌入平面的刷新互不串门——嵌入访客既不会被跳登录页，也不会用到 JWT 刷新。</p>
 *
 * Returns undefined when a newer send or an abort superseded the replay.
 */
export async function runStreamWithAuthRetry<T>(options: {
  run: (token: string) => Promise<T>
  initialToken: string
  isEmbed: boolean
  isCurrent: () => boolean
  refreshAccessToken: () => Promise<string>
  /** 嵌入平面：换一张会话票；返回 null 表示换不到（按原 401 上抛）。 */
  refreshEmbedToken?: () => Promise<string | null>
  reloginMessage: string
}): Promise<T | undefined> {
  try {
    return await options.run(options.initialToken)
  } catch (err) {
    if (!isStreamAuthError(err)) throw err
    if (options.isEmbed) {
      if (!options.refreshEmbedToken) throw err
      const fresh = await options.refreshEmbedToken().catch(() => null)
      if (!fresh) throw err
      if (!options.isCurrent()) return undefined
      return await options.run(fresh)
    }

    let refreshedToken: string
    try {
      refreshedToken = await options.refreshAccessToken()
    } catch {
      throw new Error(options.reloginMessage)
    }

    if (!options.isCurrent()) return undefined

    try {
      return await options.run(refreshedToken)
    } catch (retryErr) {
      if (isStreamAuthError(retryErr)) {
        throw new Error(options.reloginMessage)
      }
      throw retryErr
    }
  }
}

/** Reset module locks between unit tests. */
export function resetAuthRefreshStateForTests() {
  isRefreshing = false
  failedQueue = []
}
