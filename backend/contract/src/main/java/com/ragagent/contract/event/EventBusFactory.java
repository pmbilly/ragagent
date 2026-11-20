package com.ragagent.contract.event;

/**
 * 事件总线的工厂端口：每轮运行 / 每请求需要一个**独立实例**的事件总线时经本端口创建。
 *
 * <p>为什么是工厂而不是注入单例：QA 轮次、IM 回复、Agent 运行各自订阅-发射的
 * 总线生命周期互不相干（轮次结束即弃），消费方按需 {@link #create()}，
 * 不共享订阅表。实现由平台装配层提供（默认同步总线）；业务侧不 new 实现类。</p>
 */
public interface EventBusFactory {

    /** 新建一个事件总线实例；生命周期由调用方管理。 */
    EventBusInterface create();
}
