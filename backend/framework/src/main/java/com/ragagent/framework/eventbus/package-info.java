/**
 * 进程内事件总线的实现面（每轮 QA/Agent 运行的局部订阅-发射机制）：
 * {@code EventBus}（本体）、{@code EventMiddleware}（日志/计时/panic 恢复）、
 * {@code EventIds}（帧 id 生成）、{@code EventJson}（载荷专用 mapper）。
 *
 * <p>与 {@code framework.event} 的分工：event 是<b>契约</b>（Event 包络/事件名/Handler 接口，
 * 纯 JDK，被引擎与域安全 import）；本包是<b>机制</b>（Jackson/Spring 依赖住在实现侧，
 * 契约纯度守卫的边界——契约包零第三方依赖）。事件契约面未来接 Spring Modulith 事件发布登记表时，
 * 本包按蓝图 D19 整体替换。</p>
 */
package com.ragagent.framework.eventbus;
