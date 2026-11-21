package com.ragagent.config.json;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ragagent.framework.shared.session.MessageExecutionContext;


/**
 * 会话面契约载荷的 Jackson mixin（MessageView 等）。
 *
 * <p>为什么独立成文件：契约面（{@code contract.*}）要求<b>纯 JDK</b>——载荷类的线格式由
 * 这里的 mixin 承载（Jackson 只在读写时把注解套上去），契约类本身零框架引用。</p>
 */
public final class SessionJsonMixins {

    private SessionJsonMixins() {
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
