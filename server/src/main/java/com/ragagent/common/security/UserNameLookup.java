package com.ragagent.common.security;

/**
 * 用户名的只读端口（B97b）。
 *
 * <p>知识库等域需要在响应里回填创建者名字，但不应依赖 {@code auth} 的实现类。
 * 由 {@code auth.service.UserService} 实现（已有 {@code getUserById}）。</p>
 */
public interface UserNameLookup {

    /** 用户名；用户不存在或入参为空时返回 {@code null}。 */
    String usernameOf(String userId);
}
