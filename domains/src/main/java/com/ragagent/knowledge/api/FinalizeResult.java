package com.ragagent.knowledge.api;

/** 完成收尾结果：{@code decremented} 表示本轮递减了 finalizing 槽，{@code promoted} 表示已推进终态。 */
public record FinalizeResult(boolean decremented, boolean promoted) {
}
