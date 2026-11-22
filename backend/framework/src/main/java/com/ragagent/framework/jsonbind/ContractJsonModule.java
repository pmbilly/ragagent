package com.ragagent.framework.jsonbind;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * 契约载荷的 Jackson 装配：把线格式（键名/省略策略/枚举字面量）从契约类里剥离到 mixin 与
 * 显式 ser/deser，使 {@code contract.*} 保持纯 JDK。**所有**读写这些载荷的 mapper 都必须注册本模块，
 * 否则键名与省略策略会回退成 Jackson 默认（= 线格式静默变化）。
 *
 * <p>注册点：{@link JsonMappers#lenient()}（jsonb 读路径）、{@code platform.event.EventJson}（事件）、
 * {@code platform.stream.StreamJson}（SSE 流）、{@code modelcontext.JsonValues}（上下文预算）、
 * boot 的 Spring MVC mapper、Langfuse 上报 mapper。</p>
 */
public final class ContractJsonModule extends SimpleModule {

    private static final long serialVersionUID = 1L;

    /** 构造即完成全部注册（幂等：一次装配一份）。 */
    public ContractJsonModule() {
        super("ragagent-contract-json");
        ContractJsonMixins.mappings().forEach(this::setMixInAnnotation);
    }

    /** ServiceLoader 入口：让本模块可被 {@link JsonMappers} 自动发现。 */
    public static final class Provider implements JsonModuleProvider {

        @Override
        public Module module() {
            return new ContractJsonModule();
        }
    }
}
