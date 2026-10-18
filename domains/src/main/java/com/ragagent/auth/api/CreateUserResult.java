package com.ragagent.auth.api;

import com.ragagent.contract.auth.AdminUserView;

/** 建用户结果：新建成（带生成密码）或身份已存在（幂等）。 */
public sealed interface CreateUserResult {

    /** 新建成功；{@code generatedPassword} 仅一次可见（未生成时为空串）。 */
    record Created(AdminUserView user, String generatedPassword) implements CreateUserResult {
    }

    /** 身份完全一致 ⇒ 幂等返回已有行。 */
    record Idempotent(AdminUserView user) implements CreateUserResult {
    }
}
