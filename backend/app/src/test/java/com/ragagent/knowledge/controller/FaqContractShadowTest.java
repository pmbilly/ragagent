package com.ragagent.knowledge.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * 在 {@code faq-new} profile 下重跑 {@link FaqContractTest} 的全部断言与金片。
 *
 * <p>父类验默认实现，本类验 {@link FaqControllerShadow}。两侧用同一批 {@code faq-*} 金片，
 * 全绿即表示两个实现对同一批请求的输出逐字节相同。</p>
 *
 * <p>profile 进 Spring 测试上下文缓存键，本类因此持有独立上下文，不与父类串态。</p>
 */
@ActiveProfiles("faq-new")
class FaqContractShadowTest extends FaqContractTest {

    @Autowired
    private ApplicationContext context;

    /**
     * 保证本上下文对外服务的确实是影子实现。
     *
     * <p>没有这条断言，父类金片全部通过也可能只是又跑了一遍默认实现，等效性判据会失效。</p>
     */
    @Test
    @DisplayName("faq-new 下唯一生效的 handler 是影子实现")
    void shadowIsTheOnlyActiveHandler() {
        String[] names = context.getBeanNamesForType(FaqController.class);
        assertEquals(1, names.length, "faq-new 下应只有 1 个 FAQ handler，实际 " + names.length);
        Object bean = context.getBean(names[0]);
        assertTrue(
                bean instanceof FaqControllerShadow,
                "faq-new 下生效的应是 FaqControllerShadow，实际 " + bean.getClass().getName());
    }
}
