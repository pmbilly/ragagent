package com.ragagent.platform.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;


/**
 * 检索面契约载荷的 Jackson mixin（{@code SearchResult} 等）。
 *
 * <p>为什么独立成文件：契约面（{@code contract.*}）要求<b>纯 JDK</b>——载荷类的线格式由
 * 这里的 mixin 承载（Jackson 只在读写时把注解套上去），契约类本身零框架引用。</p>
 */
public final class ContractJsonMixinsRetrieval {

    private ContractJsonMixinsRetrieval() {
    }

    /** SearchResult 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class SearchResultMixin {

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Object chunkMetadata;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String matchedContent;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String knowledgeDescription;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String knowledgeCustomMetadata;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String knowledgeBaseId;

        @JsonIgnore
        private int contentRevision;

        @JsonIgnore
        private boolean contentRewritten;

    }
}
