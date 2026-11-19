package com.ragagent.contract.agent;

import java.util.List;

/**
 * 建议问题生成端口。
 *
 * <p>返回 JSON 数组文本，元素形如
 * {@code {"question": "...", "source": "...", "knowledgeBaseId": "..."}}。</p>
 *
 * <p>返回 {@code String} 而不是结构化类型，是因为契约模块不引入 Jackson
 * （`contract` 只依赖 JDK 与测试库）；调用方用本模块已有的 JSON 工具解析即可。
 * 这也让 session 不必认识 agentmanagement 的实现类。</p>
 */
public interface AgentSuggestionPort {

    /**
     * 按知识库 / 文档 / 标签作用域生成候选建议问题。
     *
     * @param limit 候选池上限
     * @param locale 语言标签，null 表示按当前上下文推断
     */
    String knowledgeSuggestedQuestions(String agentId,
                                       List<String> knowledgeBaseIds,
                                       List<String> knowledgeIds,
                                       List<TagScope> tagScopes,
                                       int limit,
                                       String locale);
}
