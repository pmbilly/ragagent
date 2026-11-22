package com.ragagent.framework.jsonbind;

import com.fasterxml.jackson.databind.Module;

/**
 * JSON 模块的贡献点：让各模块把自己的 Jackson {@code Module} 交给 {@link JsonMappers}。
 *
 * <p><b>为什么需要它</b>：契约类型分散到引擎模块后，{@code platform} 不能反向依赖引擎。
 * 若仍在 {@code platform} 里集中注册所有 mixin，就会形成循环。改为由各模块经
 * {@code META-INF/services} 声明自己的 {@code Module}，{@link JsonMappers} 用
 * {@link java.util.ServiceLoader} 收集——注册顺序与模块依赖方向无关。</p>
 *
 * <p><b>实现约定</b>：{@link #module()} 每次返回新实例（Jackson 的 {@code Module} 带可变设置），
 * 且不得依赖 Spring 上下文——{@link JsonMappers} 在测试与静态初始化中也会调用。</p>
 */
public interface JsonModuleProvider {

    /** 本模块的 Jackson 模块（含 mixin 与显式 ser/deser）。 */
    Module module();
}
