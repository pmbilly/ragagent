/**
 * 流式请求意外结束后的兜底恢复。
 *
 * SSE 被浏览器/网络关闭时，服务端往往仍在跑（甚至已经完成），但前端只收到
 * “连接结束”而拿不到 done 事件 ⇒ 消息永远停在未完成，UI 一直显示“正在思考”。
 *
 * 这里做一次有界的补拉：服务端已完成 → 用快照覆盖内存消息；仍未完成 →
 * 收束该消息并给出可读提示，避免无限转圈。
 *
 * 纯函数式依赖注入，便于单测（不依赖 Vue / axios）。
 */

export type StreamRecoveryOutcome = 'completed' | 'interrupted' | 'none'

export interface StreamRecoveryDeps {
  /** 当前仍未完成的 assistant 消息（没有则无需恢复）。 */
  findPending: () => { id?: string } | undefined
  /** 拉一次服务端快照，返回与 pending 对应的行（可为空）。 */
  fetchSnapshot: (pending: { id?: string }) => Promise<unknown | undefined>
  /** 用服务端快照覆盖内存消息；成功返回 true。 */
  applySnapshot: (pending: unknown, row: unknown) => boolean
  /** 始终没等到完成：把消息收束成失败态（停止指示器 + 展示错误）。 */
  markInterrupted: (pending: unknown) => void
  /** 轮询次数（含首次），默认 3。 */
  attempts?: number
  /** 两次尝试之间的间隔，默认 2000ms。 */
  delayMs?: number
  /** 便于测试注入的 sleep。 */
  sleep?: (ms: number) => Promise<void>
}

const defaultSleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms))

export async function recoverInterruptedTurn(deps: StreamRecoveryDeps): Promise<StreamRecoveryOutcome> {
  const pending = deps.findPending()
  if (!pending) return 'none'

  const attempts = Math.max(1, deps.attempts ?? 3)
  const delayMs = deps.delayMs ?? 2000
  const sleep = deps.sleep ?? defaultSleep

  for (let attempt = 0; attempt < attempts; attempt += 1) {
    try {
      const row = await deps.fetchSnapshot(pending)
      if (row && deps.applySnapshot(pending, row)) return 'completed'
    } catch {
      // 网络可能还没恢复：继续下一次尝试，最终仍会走 markInterrupted。
    }
    if (attempt < attempts - 1 && delayMs > 0) await sleep(delayMs)
  }

  deps.markInterrupted(pending)
  return 'interrupted'
}
