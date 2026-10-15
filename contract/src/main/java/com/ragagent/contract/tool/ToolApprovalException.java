package com.ragagent.contract.tool;

/**
 * 审批闸门的错误哨兵（四种可判别的失败 + 一种内部错误）。
 *
 * <p>handler 用 {@code e.kind()} 做 switch 判别并映射 HTTP 状态码；
 * {@code Kind} 的默认消息保留原始英文文案，便于日志对照。</p>
 *
 * <p>另外 {@code Kind#INTERNAL} 承载非哨兵的一般内部错误
 * （事件总线为 null、emit 失败、跨实例订阅失败等）。</p>
 *
 * <p>本异常是 <b>unchecked</b>：checker/事件总线实现（Spring/MyBatis）抛的都是运行时异常，
 * 统一走异常通道，无需 checked 声明。</p>
 */
public class ToolApprovalException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 可判别的错误类别 */
    public enum Kind {
        PENDING_NOT_FOUND("tool approval pending not found"),
        TENANT_MISMATCH("workspace mismatch for tool approval"),
        ALREADY_RESOLVED("tool approval already resolved"),
        USER_MISMATCH("user mismatch for tool approval"),
        /** 非哨兵的一般内部错误（带自定义消息）。 */
        INTERNAL("tool approval internal error");

        private final String defaultMessage;

        Kind(String defaultMessage) {
            this.defaultMessage = defaultMessage;
        }

        public String defaultMessage() {
            return defaultMessage;
        }
    }

    private final Kind kind;

    private ToolApprovalException(Kind kind, String message, Throwable cause) {
        super(message != null ? message : kind.defaultMessage(), cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public boolean is(Kind candidate) {
        return kind == candidate;
    }

    public static ToolApprovalException pendingNotFound() {
        return new ToolApprovalException(Kind.PENDING_NOT_FOUND, null, null);
    }

    public static ToolApprovalException tenantMismatch() {
        return new ToolApprovalException(Kind.TENANT_MISMATCH, null, null);
    }

    public static ToolApprovalException alreadyResolved() {
        return new ToolApprovalException(Kind.ALREADY_RESOLVED, null, null);
    }

    public static ToolApprovalException userMismatch() {
        return new ToolApprovalException(Kind.USER_MISMATCH, null, null);
    }

    /** 非哨兵的内部错误（自定义消息）。 */
    public static ToolApprovalException internal(String message) {
        return new ToolApprovalException(Kind.INTERNAL, message, null);
    }

    /** 非哨兵的内部错误，保留原因链。 */
    public static ToolApprovalException internal(String message, Throwable cause) {
        return new ToolApprovalException(Kind.INTERNAL, message, cause);
    }
}
