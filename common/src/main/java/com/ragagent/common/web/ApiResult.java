package com.ragagent.common.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 类级开关：打在控制器上 ⇒ 该控制器的**成功响应**由 {@link ApiResultAdvice} 统一包成
 * {@link ApiResponse}（{@code {code,message,data}}），**错误响应**由
 * {@code GlobalExceptionHandler} 按同一形态输出（门禁类错误经
 * {@link ApiResultInterceptor} 打标识别）。
 *
 * <p>迁移期约定（B169）：未标注的控制器保持历史形状不变；新控制器**必须**标注，或进
 * {@code scripts/api-envelope.baseline.json} 的待迁移清单 —— 由守卫 {@code check-api-envelope}
 * 强制，清单**只许减不许增**。</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiResult {
}
