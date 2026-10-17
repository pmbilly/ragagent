package com.ragagent.agent;

import com.ragagent.agent.budget.TokenEstimator;
import com.ragagent.contract.model.TokenUsage;
import com.ragagent.contract.model.chat.ChatMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 上下文预算的纯函数：usage 还原与工具结果裁剪。
 *
 * <p>不持有引擎状态；裁剪返回消息副本，绝不改 SSE、诊断与持久化共用的
 * ToolResult 对象。assistant 工具调用消息不动，保住 provider 要求的
 * call/result 配对。</p>
 */
final class ContextBudget {

    private ContextBudget() {
    }

    /** usage 报告还原为它描述的上下文大小（缓存计数不加回）。 */
    static int contextTokensFromUsage(TokenUsage usage) {
        if (usage == null) {
            return 0;
        }
        if (usage.getTotalTokens() > 0) {
            return usage.getTotalTokens();
        }
        return usage.getPromptTokens() + usage.getCompletionTokens();
    }

    /** 裁剪结果：(messages, 是否有改动)。 */
    record TrimOutcome(List<ChatMessage> messages, boolean ok) {
    }

    /**
     * 返回下一次模型调用的消息副本。
     *
     * <p>每个工具结果都是候选——压缩后保留窗口全部是"近期"的，需要裁的那个大结果
     * 在头在尾都可能。</p>
     */
    static TrimOutcome trimToolResultsToBudget(List<ChatMessage> messages,
            TokenEstimator estimator, int budget) {
        if (estimator == null || budget <= 0 || messages.isEmpty()) {
            return new TrimOutcome(messages, false);
        }

        List<Integer> toolIndexes = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < messages.size(); i++) {
            if ("tool".equals(messages.get(i).getRole())) {
                toolIndexes.add(i);
                total += estimator.estimateMessage(messages.get(i));
            }
        }
        if (total <= budget || toolIndexes.isEmpty()) {
            return new TrimOutcome(messages, false);
        }

        List<ChatMessage> out = new ArrayList<>(messages);
        Map<Integer, Integer> baseCosts = new HashMap<>();
        int remaining = budget;
        for (int idx : toolIndexes) {
            ChatMessage copy = ObservePhase.shallowCopy(out.get(idx));
            copy.setContent(ObservePhase.compactedToolResultMarker(messages.get(idx).getContent()));
            out.set(idx, copy);
            int cost = estimator.estimateMessage(out.get(idx));
            baseCosts.put(idx, cost);
            remaining -= cost;
        }
        if (remaining < 0) {
            remaining = 0;
        }

        // 剩余预算从最新往最旧花；装不下完整版的拿到装得下的最大头尾预览。
        for (int i = toolIndexes.size() - 1; i >= 0; i--) {
            int idx = toolIndexes.get(i);
            int fullCost = estimator.estimateMessage(messages.get(idx));
            int extra = fullCost - baseCosts.get(idx);
            if (extra <= remaining) {
                out.set(idx, messages.get(idx));
                remaining -= extra;
                continue;
            }
            out.set(idx, ObservePhase.compactToolMessage(messages.get(idx),
                    baseCosts.get(idx) + remaining, estimator));
            remaining = 0;
        }
        return new TrimOutcome(out, true);
    }
}
