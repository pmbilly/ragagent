package com.ragagent.common.auth;

import com.ragagent.common.auth.domain.User;

/**
 * 用户目录读端口（L1）：按 id 取用户行（系统设置/审计展示等只读消费面）。
 *
 * <p>实体 {@link User} 已在 L1 ⇒ 端口直接搬运实体。写面不在此端口：用户/令牌的写入
 * 仍归 auth 域（§9.1 状态唯一写入者）。</p>
 */
public interface UserDirectoryPort {

    /** 按主键取用户；不存在 ⇒ null。 */
    User userById(String id);
}
