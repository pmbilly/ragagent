/**
 * JSON 绑定：Jackson 的 mapper 工厂、契约 mixin 注册表与各模块贡献的模块 SPI。
 *
 * <p>与 {@code framework.json} 的分工：json 是<b>契约</b>（JsonValues/ZeroTimes/ByteKeyOrder，
 * 纯 JDK，契约载荷内部使用）；本包是<b>绑定</b>（Jackson 的 mapper 工厂、mixin 注册表、
 * 序列化器——契约纯度守卫禁止 Jackson 进契约包）。绑定横跨 Web 之外的模块
 * （事件总线、持久化 jsonb、追踪上报、各引擎），故独立于 web。</p>
 *
 * <p>为什么不能放进 {@code framework.json}：那个包是纯 JDK 契约面，而本包依赖 Jackson。</p>
 */
package com.ragagent.framework.jsonbind;
