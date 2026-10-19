package com.ragagent.datasource.domain;

import java.time.Duration;

/**
 * 数据源调度端口：登记/撤销一个数据源的定时同步。
 *
 * <p>端口的存在是为让用例层不依赖具体调度器实现（cron 解析、线程池都在实现侧）。
 * 重试与超时参数是**调用约定**，随端口一起定义。</p>
 */
public interface DataSourceSchedulePort {

    /** 任务失败后的最大重试次数。 */
    int MAX_RETRY = 5;

    /** 单次任务执行的超时。 */
    Duration TASK_TIMEOUT = Duration.ofHours(2);

    /** 新增或更新调度项（数据源启停/变更后调用）。 */
    void addOrUpdate(DataSource dataSource);

    /** 撤销调度项（数据源删除/停用后调用）。 */
    void remove(String dataSourceId);
}
