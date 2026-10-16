package com.ragagent.platform.web;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ragagent.contract.model.TokenUsage;
import com.ragagent.contract.model.chat.ToolCall;
import com.ragagent.contract.retrieval.SearchResult;


/**
 * 模型面契约载荷的 Jackson mixin（{@code StreamResponse}）。
 *
 * <p>为什么独立成文件：契约面（{@code contract.*}）要求<b>纯 JDK</b>——载荷类的线格式由
 * 这里的 mixin 承载（Jackson 只在读写时把注解套上去），契约类本身零框架引用。</p>
 *
 * <p>键序由 {@link SortedMapSerializer} 在序列化侧递归归一（map 按字母序），
 * 与 mixin 无关。</p>
 */
public final class ContractJsonMixinsModel {

    private ContractJsonMixinsModel() {
    }

    /** StreamResponse 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class StreamResponseMixin {

        /** 为空时整键省略。 */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private List<SearchResult> knowledgeReferences;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String sessionId;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String assistantMessageId;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private List<ToolCall> toolCalls;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Map<String, Object> data;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private TokenUsage usage;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String finishReason;

    }
}
