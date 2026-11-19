package com.ragagent.contract.wiki;

/**
 * 知识文档的「wiki 子任务已终结」记账端口。
 *
 * <p><b>为什么是端口</b>：finalizing 计数归 knowledge 所有；wiki 在 ingest op 到达终态时通过它
 * 释放该文档在计数里的槽位，而不必依赖 knowledge 的内部实现。原在 {@code wiki.service}，
 * 随解环上提到契约层——knowledge 侧也要调用它，而契约不能反向依赖域内包。</p>
 *
 * <h2>业务语义</h2>
 * <p>对应的 +1 由知识处理链路在「即将生成 wiki」时播种。调用方<b>只能对 ingest op</b> 调用它——
 * retract op 针对的是已删除的文档，没有计数器需要排空。</p>
 *
 * <p>对一个已经完成、或计数已为 0 的行调用是<b>安全的</b>：递减（{@code count > 0}）与晋升
 * （{@code parse_status = finalizing AND count = 0}）两个条件同时守住，因此在计数器机制上线
 * 之前入队的 op 调用它是无害的 no-op。</p>
 *
 * <p>实现必须使用<b>脱钩的执行路径</b>：wiki 批次 worker 可能在应用关闭中、或调用上下文已被取消；
 * 此时吞掉失败会把父文档永久留在 finalizing 状态。</p>
 */
public interface WikiKnowledgeFinalizer {

    /**
     * 把该知识文档的 wiki 子任务标记为终结。
     *
     * @param knowledgeId 知识文档 id
     */
    void finalizeWikiSubtask(String knowledgeId);
}
