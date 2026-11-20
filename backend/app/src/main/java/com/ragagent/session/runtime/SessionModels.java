package com.ragagent.session.runtime;

import com.ragagent.contract.model.LlmChatClient;
import com.ragagent.contract.model.Reranker;
import com.ragagent.platform.tracing.decorators.LangfuseChatClient;
import com.ragagent.platform.tracing.decorators.LangfuseReranker;

/**
 * 会话域的模型装饰入口：把观测装饰收在一处。
 *
 * <p>装饰是"运行面"职责——用例层拿到的客户端已带观测，不需要知道装饰器类型。
 * 未启用观测时两个 wrap 原样返回。</p>
 */
public final class SessionModels {

    private SessionModels() {
    }

    /** 给对话客户端套观测（未启用时原样返回）。 */
    public static LlmChatClient chat(LlmChatClient client) {
        return LangfuseChatClient.wrap(client);
    }

    /** 给重排器套观测（未启用时原样返回）。 */
    public static Reranker rerank(Reranker reranker) {
        return LangfuseReranker.wrap(reranker);
    }
}
