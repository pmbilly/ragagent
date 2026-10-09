package com.ragagent.evaluation.metric;

/** 评估指标接口：单一 Compute 入口。 */
public interface Metrics {

    double compute(MetricInput input);
}
