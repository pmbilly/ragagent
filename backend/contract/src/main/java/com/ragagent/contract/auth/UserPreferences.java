package com.ragagent.contract.auth;


/**
 * users.preferences jsonb 列。
 *
 * 三个字段全为可空（null = 客户端没传这个 key，部分更新时保留原值）。
 * 空对象序列化为 {}
 * （users 表该列 NOT NULL DEFAULT '{}'）。
 *
 * last_active_tenant_id 的特殊值：0 = 清除偏好的哨兵；N = 偏好空间 id。
 *
 * <p>线格式注解由 {@code platform.web.ContractJsonMixinsAuth} 的 mixin 承载（契约面纯 JDK）。</p>
 */
public class UserPreferences {

    private Long lastActiveTenantId;
    private Boolean oidcOnlyLogin;

    public Long getLastActiveTenantId() { return lastActiveTenantId; }
    public void setLastActiveTenantId(Long v) { lastActiveTenantId = v; }
    public Boolean getOidcOnlyLogin() { return oidcOnlyLogin; }
    public void setOidcOnlyLogin(Boolean v) { oidcOnlyLogin = v; }
}
