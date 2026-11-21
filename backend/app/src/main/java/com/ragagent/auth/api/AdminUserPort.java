package com.ragagent.auth.api;

import com.ragagent.framework.pagination.PageResult;

/**
 * 管理员用户的查询端口。
 *
 * <p>system 域需要读管理员用户，但不依赖 auth 的实现类：查询走本端口，写操作走
 * {@link AdminUserCommandPort}。两者分开是因为合并后会超过契约形状的端口方法数上限，
 * 且调用方不同（设置页只查、管理员页读写）。</p>
 *
 * <p>查不到返回 {@code null}，不抛异常——调用方据此决定 404 还是空视图。</p>
 */
public interface AdminUserPort {

    /** 按 id 查管理员视图；不存在返回 null。 */
    AdminUserView byId(String id);

    /** 按邮箱查；不存在返回 null。 */
    AdminUserView byEmail(String email);

    /** 按用户名查；不存在返回 null。 */
    AdminUserView byUsername(String username);

    /** 分页列出系统管理员。 */
    PageResult<AdminUserView> listSystemAdmins(int offset, int limit);
}
