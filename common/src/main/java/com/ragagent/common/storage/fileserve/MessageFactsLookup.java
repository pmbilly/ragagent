package com.ragagent.common.storage.fileserve;
import com.ragagent.contract.storage.fileserve.MessageFileFacts;

/**
 * 消息事实加载回调（L1）：调用方（会话域）按 session/message id 取事实；
 * 存储侧只在需要时调用它，<b>不做缓存</b>——实现应内含会话可见性判定。
 */
@FunctionalInterface
public interface MessageFactsLookup {

    /** 取消息事实；消息不可见 / 不存在时的语义由实现决定（返回 {@code null} 视为不可授权）。 */
    MessageFileFacts lookup(String sessionId, String messageId);
}
