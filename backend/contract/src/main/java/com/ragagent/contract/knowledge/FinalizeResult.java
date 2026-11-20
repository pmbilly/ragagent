package com.ragagent.contract.knowledge;

/**
 * 完成收尾结果：{@code decremented} 表示本轮递减了 finalizing 槽，{@code promoted} 表示已推进终态。
 *
 * <p>原在 {@code knowledge.api}，随 {@link KnowledgeFinalizePort} 上提——wiki 侧要消费该结果，
 * 而契约不能反向依赖域内包。</p>
 */
public record FinalizeResult(boolean decremented, boolean promoted) {
}
