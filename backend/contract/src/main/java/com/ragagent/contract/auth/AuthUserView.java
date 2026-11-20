package com.ragagent.contract.auth;

/**
 * 用户展示视图（跨域）：id + username / email，调用方按 username → email 回退。
 *
 * <p>只供“批量取用户展示信息”使用，不含角色与管理字段；由
 * {@link UserLookupPort} 的批量读面返回。</p>
 */
public record AuthUserView(String id, String username, String email) {
}
