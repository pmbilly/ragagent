/**
 * 向量库配置域：向量库类型目录与配置校验/持久化，对外 {@code /api/v1/vector-stores}。
 * 检索实现在 {@code retrieval-engine}，本域只做配置面。
 *
 * <p><b>种类</b>：能力配置<br>
 * <b>入口</b>：{@code VectorStoreController}<br>
 * <b>对外契约面</b>：无（连接配置经 {@code contract.vectorstore} 载荷）<br>
 * <b>依赖</b>：{@code contract}、{@code platform}。</p>
 */
package com.ragagent.vectorstore;
