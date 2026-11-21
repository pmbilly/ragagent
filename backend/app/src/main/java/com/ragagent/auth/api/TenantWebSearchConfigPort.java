package com.ragagent.auth.api;


/** 当前租户的 web 搜索配置读取面。 */
public interface TenantWebSearchConfigPort {

    /**
     * 当前租户的 web 搜索配置（按租户上下文实时读取）；无租户上下文 → null
     * （调用方按空配置走缺省合并分支）。
     */
    default WebSearchConfig currentWebSearchConfig() {
        return null;
    }
}
