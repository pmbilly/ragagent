package com.ragagent.evaluation.domain;

import java.util.List;

/**
 * 评估数据集的单条问答（问题 + 关联 passage + 参考答案）。
 *
 * @param qid      问题 ID
 * @param question 问题文本
 * @param pids     关联 passage ID（顺序 = qrels 表序）
 * @param passages 关联 passage 文本（与 pids 一一对应）
 * @param aid      答案 ID（无答案时 0）
 * @param answer   答案文本（无则 ""）
 */
public record QaPair(int qid, String question, List<Integer> pids, List<String> passages,
                     int aid, String answer) {
}
