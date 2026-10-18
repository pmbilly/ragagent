package com.ragagent.wiki.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ragagent.knowledge.api.FinalizeResult;
import com.ragagent.knowledge.api.KnowledgeFinalizeAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link DefaultWikiKnowledgeFinalizer} 的行为测试。
 *
 * <p><b>职责已收窄</b>：两条 UPDATE 的形状、钳零守卫、吞异常语义随实现搬到
 * {@code KnowledgeFinalizeAdapter}，其断言在 {@code KnowledgeFinalizeAdapterTest}。
 * 本类只测 wiki 侧薄壳：{@code Result → Outcome} 映射、空 id 短路、晋升日志路径不抛。</p>
 */
class WikiKnowledgeFinalizerTest {

    private final KnowledgeFinalizeAdapter finalizePort = mock(KnowledgeFinalizeAdapter.class);
    private final DefaultWikiKnowledgeFinalizer finalizer =
            new DefaultWikiKnowledgeFinalizer(finalizePort);

    @Test
    @DisplayName("Result → Outcome 直通映射")
    void mapsResultToOutcome() {
        when(finalizePort.finalizeSubtask(any(), any()))
                .thenReturn(new FinalizeResult(true, true));

        DefaultWikiKnowledgeFinalizer.Outcome outcome = finalizer.finalizeSubtask("kid-1");

        assertThat(outcome.decremented()).isTrue();
        assertThat(outcome.promoted()).isTrue();
    }

    /** 行已不在 finalizing（或计数未归零）时晋升未命中——预期，不是错误 */
    @Test
    @DisplayName("晋升未命中不是错误")
    void promoteMissIsNotAnError() {
        when(finalizePort.finalizeSubtask(any(), any()))
                .thenReturn(new FinalizeResult(true, false));

        DefaultWikiKnowledgeFinalizer.Outcome outcome = finalizer.finalizeSubtask("kid-1");

        assertThat(outcome.decremented()).isTrue();
        assertThat(outcome.promoted()).isFalse();
    }

    /** 端口降级返回"两步都未命中"时同样直通（端口内部已吞异常） */
    @Test
    @DisplayName("端口降级时不抛")
    void portDegradationDoesNotThrow() {
        when(finalizePort.finalizeSubtask(any(), any()))
                .thenReturn(new FinalizeResult(false, false));

        finalizer.finalizeWikiSubtask("kid-1");

        assertThat(finalizer.finalizeSubtask("kid-1").decremented()).isFalse();
    }

    /** 空 id 是安全 no-op（不碰端口） */
    @Test
    @DisplayName("空 id 短路")
    void emptyIdShortCircuits() {
        assertThat(finalizer.finalizeSubtask("").decremented()).isFalse();
        assertThat(finalizer.finalizeSubtask(null).decremented()).isFalse();
        verify(finalizePort, never()).finalizeSubtask(any(), any());
    }

    /** 晋升成功时 wiki 侧只额外记一行日志，不改变返回 */
    @Test
    @DisplayName("晋升成功走日志分支")
    void promotedLogs() {
        when(finalizePort.finalizeSubtask(any(), any()))
                .thenReturn(new FinalizeResult(true, true));

        finalizer.finalizeWikiSubtask("kid-1");

        verify(finalizePort).finalizeSubtask(any(), any());
    }
}
