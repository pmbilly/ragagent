/**
 * 检索的共享形状：{@code SearchParams}、{@code SearchResult}、{@code SearchTarget}、
 * {@code RetrieveGraphRepository}。
 *
 * <p>这四个类型被 contract 内其他包（{@code agent} / {@code session} / {@code knowledge} /
 * {@code model} / {@code pipeline}）或 platform 的 Jackson 注册表引用，因此不能移到引擎模块——
 * 否则 contract 会反向依赖引擎。检索端口、索引引擎、向量索引存储等已归位到
 * {@code com.ragagent.retrieval.api}。</p>
 */
package com.ragagent.contract.retrieval;
