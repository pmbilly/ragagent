/**
 * 渠道发布密钥（publishToken）的**本会话记忆**。
 *
 * 授权边界（见 `api/embed/index.ts` 顶部注释）：**列表行不带 token**，只有
 * 「详情 / 创建 / 轮换」响应带。而保存（PUT）响应同样**不带** token，且保存后
 * `load()` 会用列表行**整体重建**渠道数组 —— 于是打开抽屉时合并进来的 token 被
 * 冲掉，嵌入代码退化成 `<!-- 加载渠道密钥失败，请关闭后重新打开该渠道。 -->`
 * （B65 真机复现：保存前 snippet 含 `em_…`，保存后变成该提示）。
 *
 * 这里把「本会话已见过的 token」按渠道 id 记下来，在每次 `load()` 之后重新贴回行上；
 * 仅存内存（刷新页面即失效，与既有提示「关闭后重新打开该渠道」一致），
 * 不落 localStorage/sessionStorage —— token 属敏感物，能取到时才在内存里留一份。
 */

export interface EmbedChannelTokenCarrier {
  id: string
  publishToken?: string
}

export interface EmbedChannelTokenRegistry {
  /** 详情 / 创建 / 轮换响应带 token 时记下（轮换会用**新值**覆盖旧值）。 */
  remember(detail: EmbedChannelTokenCarrier | null | undefined): void
  /** 列表行不含 token：把本会话记过的贴回去（列表刷新后仍能显示/复制嵌入代码）。 */
  hydrate<T extends EmbedChannelTokenCarrier>(rows: T[] | null | undefined): T[]
  /** 渠道删除后清理，避免 id 复用或内存里留无用凭据。 */
  forget(id: string): void
  /** 本会话是否已知该渠道的 token（测试/诊断用）。 */
  peek(id: string): string
}

export function createEmbedChannelTokenRegistry(): EmbedChannelTokenRegistry {
  const tokens = new Map<string, string>()
  return {
    remember(detail) {
      if (detail?.id && detail.publishToken) {
        tokens.set(detail.id, detail.publishToken)
      }
    },
    hydrate(rows) {
      return (rows || []).map((row) => {
        const token = tokens.get(row.id)
        return token ? { ...row, publishToken: token } : row
      })
    },
    forget(id) {
      tokens.delete(id)
    },
    peek(id) {
      return tokens.get(id) ?? ''
    },
  }
}
