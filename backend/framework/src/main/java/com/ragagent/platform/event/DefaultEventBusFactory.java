package com.ragagent.platform.event;

import com.ragagent.contract.event.EventBusFactory;
import com.ragagent.contract.event.EventBusInterface;
import org.springframework.stereotype.Component;

/**
 * {@link EventBusFactory} 的默认实现：同步总线（按注册顺序执行 handler）。
 *
 * <p>每请求一个实例的语义由工厂方法承担——本类自身是无状态单例。</p>
 */
@Component
public class DefaultEventBusFactory implements EventBusFactory {

    @Override
    public EventBusInterface create() {
        return new EventBus();
    }
}
