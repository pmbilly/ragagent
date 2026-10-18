package com.ragagent.auth.api;

import com.ragagent.contract.auth.AdminUserView;
import java.util.List;

/** 系统管理员分页结果（count + created_at DESC, id ASC）。 */
public record AdminUserPage(List<AdminUserView> users, long total) {
}
