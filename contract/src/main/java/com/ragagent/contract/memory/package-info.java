/**
 * 记忆契约面：配置/条目视图/键与类目/召回与检索上下文。
 *
 * <p>{@code MemoryKeys} 同时承载 jsonb/线格式键与两个契约共享文本函数
 * （{@code runeLength} / {@code runeSlice}，被 {@code MemoryConfig} 使用），
 * 故留契约面而不是下移 memory 域。</p>
 */
package com.ragagent.contract.memory;
