package com.ragagent.contract.session;

/**
 * embed 会话操作面的响应（中性：状态码 + 载荷）。
 *
 * <p>状态码是 embed 对外线格式的一部分（如 {@code 202} = 建议生成中）；错误经
 * {@code BizException} 信封原样上抛（400/401/403/404 纯字符串信封口径见各端点）。</p>
 */
public record EmbedReply<T>(int status, T body) {

    /** {@code 200} + 载荷。 */
    public static <T> EmbedReply<T> ok(T body) {
        return new EmbedReply<>(200, body);
    }

    /** {@code 202}（生成中）+ 载荷。 */
    public static <T> EmbedReply<T> accepted(T body) {
        return new EmbedReply<>(202, body);
    }
}
