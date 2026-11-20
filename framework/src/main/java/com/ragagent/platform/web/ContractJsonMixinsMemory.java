package com.ragagent.platform.web;



/**
 * 记忆面契约载荷的 Jackson mixin（MemoryMessageCursor）。
 *
 * <p>为什么独立成文件：契约面（{@code contract.*}）要求<b>纯 JDK</b>——载荷类的线格式由
 * 这里的 mixin 承载（Jackson 只在读写时把注解套上去），契约类本身零框架引用。</p>
 */
public final class ContractJsonMixinsMemory {

    private ContractJsonMixinsMemory() {
    }

    /** MemoryMessageCursor 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class MemoryMessageCursorMixin {
    }
}
