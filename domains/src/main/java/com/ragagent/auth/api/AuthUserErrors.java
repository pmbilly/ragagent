package com.ragagent.auth.api;

/** 身份冲突的固定错误文案（契约原文，前端按此展示）。 */
public final class AuthUserErrors {

    /** email 已被占用。 */
    public static final String EMAIL_EXISTS = "user with this email already exists";

    /** username 已被占用。 */
    public static final String USERNAME_EXISTS = "user with this username already exists";

    /** email 与 username 指向两个不同身份。 */
    public static final String IDENTITY_CONFLICT =
            "email and username refer to conflicting existing identities";

    private AuthUserErrors() {
    }
}
