package com.ragagent.framework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 全局安全响应头配置段（weknora.web.security-headers / RAGAGENT_WEB_SECURITY_HEADERS_*）。
 *
 * <p>只钉开关，不定头清单：三个头的名字与取值语义在
 * {@link com.ragagent.framework.web.SecurityHeadersFilter} 里，头清单可配置等有第二个
 * 消费方再做（YAGNI）。enabled=false 的场景是反向代理已统一注入安全头。</p>
 */
@ConfigurationProperties(prefix = "weknora.web.security-headers")
public class SecurityHeaderProperties {

    /** 默认开启：未显式关闭即注入三件安全响应头。env: RAGAGENT_WEB_SECURITY_HEADERS_ENABLED */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
