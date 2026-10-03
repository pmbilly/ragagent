package com.ragagent.evaluation.metric;

import java.util.List;

/**
 * 评估指标输入：检索真值（多查询，每查询一个相关 ID 集合）+ 命中 ID 序列 +
 * 生成文本/参考答案。
 *
 * <p>public 字段；默认值为零值语义（null 列表 → 空列表、null → 空串）。</p>
 */
public final class MetricInput {

    /** 检索真值（每查询一个相关 ID 组）。 */
    public List<List<Integer>> retrievalGT = List.of();
    /** 命中序列（跨查询共享）。 */
    public List<Integer> retrievalIDs = List.of();
    /** 待评文本。 */
    public String generatedTexts = "";
    /** 参考答案。 */
    public String generatedGT = "";

    public MetricInput() {
    }

    public MetricInput(List<List<Integer>> retrievalGT, List<Integer> retrievalIDs,
                       String generatedTexts, String generatedGT) {
        this.retrievalGT = retrievalGT == null ? List.of() : retrievalGT;
        this.retrievalIDs = retrievalIDs == null ? List.of() : retrievalIDs;
        this.generatedTexts = generatedTexts == null ? "" : generatedTexts;
        this.generatedGT = generatedGT == null ? "" : generatedGT;
    }

    /** 检索类指标输入（只填检索真值与命中序列）。 */
    public static MetricInput retrieval(List<List<Integer>> gt, List<Integer> ids) {
        return new MetricInput(gt, ids, "", "");
    }

    /** 生成类指标输入（只填待评文本与参考答案）。 */
    public static MetricInput generation(String texts, String gt) {
        return new MetricInput(List.of(), List.of(), texts, gt);
    }
}
