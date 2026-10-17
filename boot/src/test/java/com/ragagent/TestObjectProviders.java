package com.ragagent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.springframework.beans.factory.ObjectProvider;

/**
 * 测试用的 {@link ObjectProvider} 桩：{@link #of(Object)} 有值、{@link #empty()} 无值。
 *
 * <p>集中一处 {@code @SuppressWarnings("unchecked")}：用例里直接写裸
 * {@code mock(ObjectProvider.class)} 会引出 unchecked 转换告警（参数位要求
 * {@code ObjectProvider<T>}）。</p>
 */
public final class TestObjectProviders {

    private TestObjectProviders() {
    }

    /** 有值的 provider（{@code getIfAvailable()} 返回该值）。 */
    @SuppressWarnings("unchecked")
    public static <T> ObjectProvider<T> of(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        if (value != null) {
            when(provider.getIfAvailable()).thenReturn(value);
        }
        return provider;
    }

    /** 无值的 provider（未装配形态）。 */
    @SuppressWarnings("unchecked")
    public static <T> ObjectProvider<T> empty() {
        return mock(ObjectProvider.class);
    }
}
