package com.ragagent.llm.api;


/** 取模型实例的工厂面：按 modelId 返回聊天 / 重排客户端。 */
public interface ModelFactoryPort {

    LlmChatClient getChatModel(String modelId);

    Reranker getRerankModel(String modelId);
}
