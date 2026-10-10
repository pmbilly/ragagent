package com.ragagent.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一响应外壳（B169 起；约定见 {@code docs/api-response-convention.md}）。
 *
 * <p>形态固定为 {@code {"code": 0, "message": "ok", "data": …}}：</p>
 * <ul>
 *   <li>{@code code} —— {@code 0} 表示成功；非 0 用 {@code ErrorCode} 的数字码
 *       （通用 1000 段 + 业务分段），与错误体**同码表**。</li>
 *   <li>{@code message} —— 成功默认 {@code "ok"}；失败为可展示文案。</li>
 *   <li>{@code data} —— 载荷本体（对象 / 数组 / null）；**恒存在**，前端据此可省判空。</li>
 * </ul>
 *
 * <p>HTTP 状态码仍表达协议语义（400/401/403/404/409/500…）——本外壳不改状态码口径。
 * 字段序即记录组件声明序 ⇒ 线上键序恒为 code → message → data。</p>
 *
 * <p>构造只经 {@link #ok(Object)} / {@link #ok(Object, String)} / {@link #fail(int, String, Object)}，
 * 保证「成功 ⇔ code == 0」不存在第二处真相。</p>
 *
 * @param <T> 载荷类型
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(int code, String message, T data) {

    /** 成功码。 */
    public static final int OK_CODE = 0;

    /** 成功默认文案。 */
    public static final String OK_MESSAGE = "ok";

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(OK_CODE, OK_MESSAGE, null);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(OK_CODE, OK_MESSAGE, data);
    }

    /** 成功但带自定义文案（如 DELETE 的 "Agent deleted successfully"）。 */
    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(OK_CODE,
                message == null || message.isEmpty() ? OK_MESSAGE : message, data);
    }

    /** 失败（无明细；错误处理器的默认形态）。 */
    public static ApiResponse<Object> fail(int code, String message) {
        return new ApiResponse<>(code, message == null ? "" : message, null);
    }

    /** 失败（业务码 + 文案 + 可选明细）。 */
    public static <T> ApiResponse<T> fail(int code, String message, T data) {
        return new ApiResponse<>(code, message == null ? "" : message, data);
    }
}
