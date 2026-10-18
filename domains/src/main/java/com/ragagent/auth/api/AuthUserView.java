package com.ragagent.auth.api;

/**
 * 用户展示视图：id + username / email（调用方按 username → email 回退）。
 *
 * <p>只供「批量取用户展示信息」使用，不含角色与管理字段。</p>
 */
public record AuthUserView(String id, String username, String email) {
}
