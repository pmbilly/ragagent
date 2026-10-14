/**
 * 租户仓储面（由 {@code tenant} 根包迁出，对齐方案 §8.9 的 {@code repository/}）：
 * {@code Tenant}（租户实体）、{@code TenantLookup}（按 id/域名查找）与
 * {@code TenantConfigRedaction}（配置脱敏，供查询面复用）。
 */
package com.ragagent.tenant.repository;
