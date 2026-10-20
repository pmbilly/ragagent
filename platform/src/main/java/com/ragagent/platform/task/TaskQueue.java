package com.ragagent.platform.task;

/**
 * 任务队列的公共契约：命名、启停与测试钩子。
 *
 * <p>各域的投递方法保留自己的语义（有的带 taskId/重试上限/超时，有的带延迟），
 * 不强行归一成一个 {@code submit}——那会丢掉去重与重试语义。H4b 迁到统一基座时，
 * 由基座实现本接口的完整版本（lease / renew / progress / retry / park）。</p>
 */
public interface TaskQueue {

    /** 队列名（日志/指标）；默认取实现类简名。 */
    default String name() {
        return getClass().getSimpleName();
    }

    /** 启动消费循环；无异步循环的实现可 no-op。 */
    default void start() {
    }

    /** 停止消费与释放资源；幂等。 */
    default void stop() {
    }

    /** 测试钩子：覆盖重试延迟（秒）；{@code null} = 用各队列的默认退避公式。 */
    default void setRetryDelayOverrideSeconds(Long seconds) {
    }
}
