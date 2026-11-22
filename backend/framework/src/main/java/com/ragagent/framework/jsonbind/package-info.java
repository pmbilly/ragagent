/**
 * JSON 绑定：Jackson 的 mapper 工厂、契约 mixin 注册表与各模块贡献的模块 SPI。
 *
 * <p>为什么独立于 {@code web}：绑定是"对象 ↔ 线格式"的横切关注点，被 Web 之外的模块
 * （事件总线、持久化 jsonb、追踪上报、各引擎）共同使用；把它留在 web 里会让 HTTP 适配层
 * 变成所有模块的隐式入口。</p>
 *
 * <p>为什么不能放进 {@code framework.json}：那个包是纯 JDK 契约面，而本包依赖 Jackson。</p>
 */
package com.ragagent.framework.jsonbind;
