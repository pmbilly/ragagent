package com.ragagent.platform.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ragagent.contract.session.MessageExecutionContext;


/**
 * 会话面契约载荷的 Jackson mixin（MessageView 等）。
 *
 * <p>为什么独立成文件：契约面（{@code contract.*}）要求<b>纯 JDK</b>——载荷类的线格式由
 * 这里的 mixin 承载（Jackson 只在读写时把注解套上去），契约类本身零框架引用。</p>
 */
public final class ContractJsonMixinsSession {

    private ContractJsonMixinsSession() {
    }

    /** MessageView 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class MessageViewMixin {

        @JsonIgnore
        private String renderedContent;

        @JsonIgnore
        private long agentTenantId;

        @JsonIgnore
        private MessageExecutionContext executionContext;

    }
}
