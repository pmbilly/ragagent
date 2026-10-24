package com.ragagent.contract.vectorstore;

import java.util.function.Predicate;

/**
 * 控制面必填规则：{@code satisfied} 为 false 时用 {@code message} 报 400。
 *
 * <p>文案逐字固定（golden 用例钉住）；条件写成谓词是为了表达
 * "addr 或 use_default_connection 二选一"这类组合约束。</p>
 */
public record VectorStoreRequiredField(String message, Predicate<ConnectionConfig> satisfied) {
}
