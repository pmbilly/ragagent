/**
 * 平台事件总线面（由 {@code com.ragagent.event} 迁入，对齐方案 §8.2 的 {@code platform.event/}）：
 * {@code Event} / {@code EventBus} / {@code EventJson} 等 12 类——进程内事件总线与载荷编解码。
 *
 * <p>{@code com.ragagent.event.payload/*}（26 个 SSE 载荷）目标面是
 * {@code contract.event.payload}（§8.1），待「契约面收纳」批（需先去 Jackson）后再迁。</p>
 */
package com.ragagent.platform.event;
