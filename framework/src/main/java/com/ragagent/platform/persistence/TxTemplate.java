package com.ragagent.platform.persistence;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 事务模板：把多步写包进同一个事务。
 *
 * <h2>为什么是独立 bean</h2>
 * <p><b>Spring 的自调用不走代理</b>：仓储内部直接调自己的 {@code @Transactional} 方法，
 * 注解根本不生效——两步写会跑在各自独立的自动提交事务里，留下半成品行。
 * 提成独立 bean 后，调用穿过代理，事务才真的生效。</p>
 *
 * <h2>回调里为什么能用别的 mapper</h2>
 * <p>Spring 事务是<b>线程绑定</b>的：回调在同一线程执行，各 mapper 的 SqlSession 会加入
 * 同一个事务（{@code DataSourceTransactionManager} + MyBatis 的
 * {@code SpringManagedTransaction}）。</p>
 *
 * <p>需要行锁语义时不要用本类——见 memory 域的 {@code MemoryTxTemplate}
 * （它在事务里先 {@code FOR UPDATE} 锁主体行）。</p>
 */
@Component
public class TxTemplate {

    /** 在事务里执行 {@code work}；抛 {@link RuntimeException} 即回滚。 */
    @Transactional
    public void inTransaction(Runnable work) {
        work.run();
    }

    /** {@link #inTransaction(Runnable)} 的带返回值版本。 */
    @Transactional
    public <T> T inTransaction(Supplier<T> work) {
        return work.get();
    }
}
