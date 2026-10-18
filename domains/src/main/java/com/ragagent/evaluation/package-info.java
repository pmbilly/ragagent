/**
 * 评估域：评估任务与指标计算（Rouge 等），对外入口 {@code /api/v1/evaluation}。
 *
 * <p><b>种类</b>：产品能力<br>
 * <b>入口</b>：{@code EvaluationController}<br>
 * <b>对外契约面</b>：无（问答能力经 {@code contract.pipeline.KnowledgeQaRunner} 调用）<br>
 * <b>依赖</b>：{@code contract}、{@code platform}。</p>
 */
package com.ragagent.evaluation;
