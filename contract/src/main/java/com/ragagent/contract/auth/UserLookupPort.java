package com.ragagent.contract.auth;

import java.util.Collection;
import java.util.Map;

/**
 * 用户目录读端口：按 id 批量取展示信息（username / email）。
 *
 * <p>实现方负责租户范围与空集合短路；调用方只消费 {@link AuthUserView} 的展示字段，
 * 不依赖用户实体与持久化形状。</p>
 */
public interface UserLookupPort {

    /** 按 id 批量查询；返回顺序与入参无关，缺失的 id 不出现在结果中。 */
    Map<String, AuthUserView> byIds(Collection<String> ids);
}
