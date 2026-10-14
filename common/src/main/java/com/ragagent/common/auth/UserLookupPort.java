package com.ragagent.common.auth;

import java.util.Collection;
import java.util.Map;

/**
 * 用户目录端口：按 id 批量取**展示所需**的用户视图，供适配面使用
 * （规则：{@code agenttools}/{@code agentmanagement} 不直连 {@code auth.service.*}）。
 *
 * <p>实现留在 auth 侧（{@code api/UserLookupAdapter}）——委托 {@code UserService#getUsersByIds}
 * 并只投影展示字段；**空集合短路与异常语义由调用方 try/catch 处理，与迁移前逐字一致**。</p>
 */
public interface UserLookupPort {

    /** 展示视图：id + username/email（调用方按 username → email 回退，与原逻辑同）。 */
    record UserView(String id, String username, String email) {
    }

    /** 按 id 批量取（缺失 id 不入结果；空集合返回空表）。 */
    Map<String, UserView> byIds(Collection<String> ids);
}
