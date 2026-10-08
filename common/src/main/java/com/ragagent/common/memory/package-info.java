/**
 * 记忆域**跨域词汇**：记忆配置（{@code MemoryConfig}）、键名（{@code MemoryKeys}）与
 * 类型/默认值（{@code MemoryKinds}）。
 *
 * <p><b>为什么在 common 而不在 {@code memory} 域</b>：这三个类型被<b>多个域</b>读取——
 * {@code memory}（域内主体）、{@code auth}（租户配置里的 {@code MemoryConfig} 字段，
 * 见 {@code TenantConfigOps}）、{@code datasource}；若放进 {@code memory} 域，
 * {@code auth → memory} 与既有的 {@code memory → auth}（{@code MemoryService} 用
 * {@code TenantService}）会立刻形成 SCC 环（B103 实测被守卫抓出并回退）。
 * 依赖方向因此是"消费域 → {@code common.memory} ← memory 域"。</p>
 */
package com.ragagent.common.memory;
