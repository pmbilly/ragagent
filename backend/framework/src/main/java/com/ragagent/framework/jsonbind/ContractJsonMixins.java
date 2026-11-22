package com.ragagent.framework.jsonbind;

import com.ragagent.framework.error.AppError;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 契约载荷的 mixin 映射表（纯 JDK 类型，不绑定 Jackson 版本）。事件 payload 的 mixin 已随载荷迁各属主模块（agentengine.json / app config.json）。
 *
 * <p>{@link ContractJsonModule} 注册这张表，所有读写契约载荷的 mapper 共用同一份映射，
 * 避免各 mapper 的键名/省略策略漂移。</p>
 */
public final class ContractJsonMixins {

    private ContractJsonMixins() {
    }

    /** 目标类型 → mixin 类型（顺序稳定，便于差异对比）。 */
    public static Map<Class<?>, Class<?>> mappings() {
        Map<Class<?>, Class<?>> m = new LinkedHashMap<>();
        m.put(AppError.class, ContractJsonMixinsCore.AppErrorMixin.class);
        return Map.copyOf(m);
    }
}
