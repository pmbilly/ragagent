/**
 * 记忆域：用户长期记忆的抽取、去重与合并、检索与生命周期（租约、墓碑、容量）。
 * 落库走 jsonb，读路径统一接 {@code JsonMappers} 工厂。
 *
 * <p><b>种类</b>：产品能力<br>
 * <b>入口</b>：{@code MemoryController}<br>
 * <b>对外契约面</b>：无（跨域读写经 {@code contract.memory} 载荷）<br>
 * <b>依赖</b>：{@code contract}、{@code platform}。</p>
 */
package com.ragagent.memory;
