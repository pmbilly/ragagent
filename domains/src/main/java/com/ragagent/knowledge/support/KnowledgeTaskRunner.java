package com.ragagent.knowledge.support;

/**
 * 后台任务提交端口：用例层只管"提交一个任务"，不关心线程池形态。
 *
 * <p>实现侧负责队列、线程与拒绝策略；{@code shutdown} 属于装配生命周期，不在本端口。</p>
 */
public interface KnowledgeTaskRunner {

    /** 提交一个无名任务。 */
    void submit(Runnable task);

    /** 提交一个带名字的任务（名字用于线程名与日志定位）。 */
    void submit(String taskName, Runnable task);
}
