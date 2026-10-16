package com.ragagent.contract.model;

import java.util.List;

import com.ragagent.contract.model.chat.ChatMessage;
import com.ragagent.contract.model.chat.ChatOptions;
import com.ragagent.contract.model.chat.ChatResponse;

/**
 * 聊天客户端。
 *
 * 流式语义：{@link #chatStream} 是**先同步建立连接、返回通道**，
 * 建立阶段的错误立即抛出；进入流之后的错误通过流内的 ERROR 型 StreamResponse 传递
 * （FinishReason 记为 "incomplete"）。
 *
 * Java 侧用 {@link ChatStream}（队列 + 显式关闭）承载（内部跑虚拟线程），与既有
 * KnowledgeProcessWorker 的"asyncio → 虚拟线程队列"取舍同类。
 */
public interface LlmChatClient {

    /** 非流式聊天 */
    ChatResponse chat(List<ChatMessage> messages, ChatOptions options);

    /**
     * 流式聊天：建立连接后返回响应**句柄**；消费到 done=true 的元素即流结束。
     *
     * <p>消费者在提前离开（用户取消 / 停顿超时 / 异常收尾）时必须 {@link ChatStream#close()}——
     * "停止消费"不等于"取消模型"：不关会让 provider 连接继续读、并发 permit 继续被占
     * （只能等放弃阈值才靠猜释放）。正常读到 done 收尾可以不关，实现层已在那时释放。</p>
     */
    ChatStream chatStream(List<ChatMessage> messages, ChatOptions options);

    /** 模型名称 */
    String getModelName();

    /** 模型 ID */
    String getModelId();
}
