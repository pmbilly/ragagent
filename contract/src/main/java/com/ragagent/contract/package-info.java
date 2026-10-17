/**
 * 契约面（contracts，阶段 6 起逐步收纳）：跨模块公开的 API / SPI 与不可变载荷。
 *
 * <p><b>硬约束</b>（方案 + 规则）：本面**只依赖 JDK**——不得引入 Spring / MyBatis /
 * Redis / Jackson / Langfuse / 云 SDK；wire 序列化由 `controller` / `dto` 承担。
 * 业务域之间只经本面协作；本域 `repository`/内部 port 不出域。</p>
 */
package com.ragagent.contract;
