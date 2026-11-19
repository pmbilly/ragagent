/**
 * 检索引擎的对外契约：向量库查找端口、视图、必填字段与引擎类型。
 *
 * <p>从 {@code contract.vectorstore} 归位而来。配置形状（{@code ConnectionConfig} /
 * {@code IndexConfig}）仍留在 contract，因为 platform 的 Jackson 注册表需要认识它们，
 * 而 platform 不能反向依赖引擎模块。</p>
 */
package com.ragagent.retrieval.api;
