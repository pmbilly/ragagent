package com.ragagent.contract.auth;

/**
 * 认证令牌的**管理写面**（契约面端口）：管理面（如 system 的系统管理员用户管理）
 * 只能经本端口吊销某用户的全部令牌，**不直连 auth 的 mapper**。
 *
 * <p><b>为什么是写端口</b>：令牌实体的写入者应唯一在 auth 域（§9.1 精神；实体 `AuthToken`
 * 属 auth 域内部，见 HANDOFF §12.0 判据 1）；消费方只表达"吊销该用户全部令牌"这一用例。</p>
 *
 * <p><b>失败语义</b>：与域内实现一致（DB 异常原样上抛）；调用方按既有语义 catch 后 warn 并继续
 * （管理面"改密后吊销会话"失败不致命）。</p>
 */
public interface AuthTokenAdminPort {

    /** 把该用户的全部令牌置为已吊销。 */
    void revokeAllForUser(String userId);
}
