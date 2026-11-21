package com.ragagent.auth.api;

/**
 * 管理员用户的写操作端口。
 *
 * <p>与 {@link AdminUserPort}（查询）分开，是因为合并后方法数会超过契约形状上限
 * （端口 ≤7 方法），且两者被不同调用方使用：system 的设置页只查、管理员页读写。</p>
 */
public interface AdminUserCommandPort {

    /** 把既有用户提升为管理员。 */
    AdminUserView promote(AdminUserView user);

    /** 撤销管理员身份；{@code callerId} 用于阻止自我撤销。 */
    AdminUserView revoke(String userId, String callerId);

    /** 重置密码（调用方负责策略校验）。 */
    void resetPassword(String userId, String newPassword);

    /**
     * 建用户（不改既有身份为管理员）。
     *
     * @param passwordPresent 调用方是否显式提供了密码；false 时按策略生成
     * @param provisioning    预置项（可空）
     * @param complexEnabled  是否启用复杂度策略
     */
    CreateUserResult createUser(String rawUsername, String rawEmail,
                                String password, boolean passwordPresent,
                                String provisioning, boolean complexEnabled);

    /** 校验密码策略；返回空串表示通过，否则为人类可读的拒绝原因。 */
    String validatePasswordPolicy(String password, boolean complexEnabled);

    /** 按当前策略生成一个合规密码。 */
    String generatePolicyCompliantPassword(boolean complexEnabled);
}
