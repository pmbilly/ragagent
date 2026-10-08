// API client for per-user starred resources (DB-backed; see migration
// 000047 and favorite/controller/UserFavoriteController).
//
// The backend authoritatively scopes favorites to the active (user, tenant)
// pair from the auth context — these helpers therefore never pass userId
// or tenantId, and a tenant switch automatically points reads at the
// right namespace.

import { get, post, del } from '@/utils/request'

export type FavoriteResourceType = 'kb' | 'agent'

export interface FavoriteEntry {
  userId: string
  tenantId: number
  resourceType: FavoriteResourceType
  resourceId: string
  /** ISO timestamp from the server (createdAt column). */
  createdAt: string
}

export function listFavorites(type: FavoriteResourceType) {
  return get<FavoriteEntry[]>(`/api/v1/user/favorites?type=${encodeURIComponent(type)}`)
}

export function addFavorite(type: FavoriteResourceType, id: string) {
  return post('/api/v1/user/favorites', { type, id })
}

export function removeFavorite(type: FavoriteResourceType, id: string) {
  return del(`/api/v1/user/favorites/${encodeURIComponent(type)}/${encodeURIComponent(id)}`)
}
