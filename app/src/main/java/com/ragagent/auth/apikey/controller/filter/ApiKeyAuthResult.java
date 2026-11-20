package com.ragagent.auth.apikey.controller.filter;

/** 认证结果：{@code Passed} 已写上下文（继续过滤链）；{@code Rejected} 由边界写出响应。 */
public sealed interface ApiKeyAuthResult {

    /** 通过；principal / scope 上下文已写入。 */
    record Passed() implements ApiKeyAuthResult {
    }

    /** 拒绝；{@code status} 与 {@code jsonBody} 是契约原文，边界逐字写出。 */
    record Rejected(int status, String jsonBody) implements ApiKeyAuthResult {
    }
}
