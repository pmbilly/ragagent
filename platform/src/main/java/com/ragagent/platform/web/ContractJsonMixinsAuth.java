package com.ragagent.platform.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;


/**
 * 认证面契约载荷的 Jackson mixin（UserPreferences）。
 *
 * <p>为什么独立成文件：契约面（{@code contract.*}）要求<b>纯 JDK</b>——载荷类的线格式由
 * 这里的 mixin 承载（Jackson 只在读写时把注解套上去），契约类本身零框架引用。</p>
 */
public final class ContractJsonMixinsAuth {

    private ContractJsonMixinsAuth() {
    }

    /** UserPreferences 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public abstract static class UserPreferencesMixin {
    }
}
