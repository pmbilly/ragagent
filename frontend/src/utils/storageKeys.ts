/**
 * 浏览器本地存储键（唯一事实来源：改前缀/改名只改这里）。
 *
 * <p>历史前缀是 `weknora_`，产品改名后统一为 `ragagent_`；旧键不主动清理，
 * 未上线环境重新登录即可（新代码只读这里的键）。</p>
 */
export const STORAGE_PREFIX = 'ragagent_'

export const StorageKeys = {
  token: 'ragagent_token',
  refreshToken: 'ragagent_refreshToken',
  refreshTokenSnake: 'ragagent_refresh_token',
  user: 'ragagent_user',
  tenant: 'ragagent_tenant',
  memberships: 'ragagent_memberships',
  knowledgeBases: 'ragagent_knowledge_bases',
  currentKb: 'ragagent_current_kb',
  selectedTenantId: 'ragagent_selected_tenantId',
  selectedTenantIdSnake: 'ragagent_selected_tenant_id',
  selectedTenantName: 'ragagent_selected_tenantName',
  liteMode: 'ragagent_lite_mode',
  liteLastPath: 'ragagent_lite_last_path',
  pendingInviteToken: 'ragagent_pending_invite_token',
  pendingTenantSwitchToast: 'ragagent_pending_tenant_switch_toast',
  lastChatModelId: 'ragagent_last_chat_model_id',
  autoSetupFailed: 'ragagent_auto_setup_failed',
  cmdkRecent: 'ragagent_cmdk_recent',
} as const

export type StorageKey = (typeof StorageKeys)[keyof typeof StorageKeys]
