/**
 * chat 管线的**插件实现**：{@link Plugin} 接口 + 18 个步骤实现
 * （query_understand → search → merge → rerank → into_chat_message → chat_completion 等）。
 *
 * <p>每个插件响应哪些事件、读写了 {@code PipelineState} 的哪些字段：
 * {@code docs/architecture/pipeline-map.md}（由代码生成）。</p>
 *
 * <p>装配在 {@link com.ragagent.session.rag.PipelineBuilder}（根包），
 * 跨域 seam 在 {@link com.ragagent.contract.pipeline.PipelinePorts}（根包）；
 * 纯逻辑辅助在 {@link com.ragagent.session.rag.support}（同域子包）。
 * 部分静态/实例辅助方法放宽为 {@code public}：测试在 {@code com.ragagent.session.rag}
 * 根包直接探针，跨子包需可见。</p>
 */
package com.ragagent.session.rag.plugin;
