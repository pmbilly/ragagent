package com.ragagent.session.rag;

import com.ragagent.contract.session.PipelineUsedMemoryView;
import java.util.List;

/**
 * 管线状态：请求段只读视图 + 六组状态 + 轮次流转字段。
 *
 * <p>插件只依赖本接口，不再依赖 {@link ChatManage} 具体类；`fork()` 给并发分支
 * 提供独立副本（查询计划/实体/记忆等复制面由实现决定）。</p>
 */
public interface PipelineState extends TurnInputView {

    QueryPlan getQueryPlan();

    EntityContext getEntityContext();

    SearchOutcome getSearchOutcome();

    RerankOutcome getRerank();

    MergedContext getMerged();

    AnswerState getAnswer();

    TurnRuntime getRuntime();

    List<History> getHistory();

    void setHistory(List<History> v);

    List<PipelineUsedMemoryView> getUsedMemories();

    void setUsedMemories(List<PipelineUsedMemoryView> v);

    /** 当前轮次是否需要检索（web_search 意图等由实现判定）。 */
    boolean needsRetrieval();

    /** 引用开关（null 视为开启）。 */
    boolean citationsEnabled();

    /** 重排阈值可被降级重试临时改写（写入方只此一处）。 */
    void setRerankThreshold(double v);

    /** 并发分支的独立副本（不复制检索产出/答复）。 */
    PipelineState fork();
}
