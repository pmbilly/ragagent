package com.ragagent.session.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ragagent.memory.service.MemoryExtractionService;
import com.ragagent.session.domain.Message;
import com.ragagent.session.service.MessageService;
import com.ragagent.session.service.MessageSuggestionService;
import com.ragagent.session.service.SessionService;
import com.ragagent.session.service.TemporaryDocumentService;

/**
 * A2：全轮一次性提交门（完成收尾幂等 + 更新失败短路）。
 *
 * <p>收尾有多个并发入口（STOP watcher / HTTP 消费 / runner finally）——只有完成态 CAS
 * 的**赢家**才许启动 KB 索引 / 追问建议 / 记忆蒸馏；消息更新失败时必须短路，
 * 否则会产生重复 knowledge 与孤立产物。</p>
 */
class QaTurnFinalizerCommitGateTest {

    private final MessageService messageService = mock(MessageService.class);
    private final MessageSuggestionService suggestionService = mock(MessageSuggestionService.class);
    private final MemoryExtractionService memoryExtraction = mock(MemoryExtractionService.class);
    private final QaTurnFinalizer finalizer = new QaTurnFinalizer(mock(SessionService.class),
            messageService, suggestionService, mock(TemporaryDocumentService.class), memoryExtraction);

    private static Message assistantMessage() {
        Message m = new Message();
        m.setId("am-1");
        m.setSessionId("s-1");
        m.setContent("answer");
        m.setModelId("model-1");
        return m;
    }

    @Test
    @DisplayName("双收尾只提交一次下游（CAS 输家不发索引/建议/记忆）")
    void downstreamRunsOnceWhenTwoPathsComplete() throws Exception {
        // 第一路赢、第二路输（模拟 STOP watcher 与 runner finally 并发收尾）
        when(messageService.completeMessageOnce(any())).thenReturn(true, false);
        CountDownLatch indexed = new CountDownLatch(1);
        AtomicInteger indexCalls = new AtomicInteger();
        doAnswer(inv -> {
            indexCalls.incrementAndGet();
            indexed.countDown();
            return null;
        }).when(messageService).indexMessageToKb(anyString(), anyString(), anyString(), anyString());

        finalizer.completeAssistantMessage(assistantMessage(), "q", "um-1", 1L);
        finalizer.completeAssistantMessage(assistantMessage(), "q", "um-1", 1L);

        assertThat(indexed.await(3, TimeUnit.SECONDS)).as("赢家已启动索引").isTrue();
        assertThat(indexCalls.get()).as("索引只跑一次（输家不得重复起）").isEqualTo(1);
        verify(messageService, times(2)).completeMessageOnce(any());
        verify(messageService, times(1)).updateMessage(any());
        verify(memoryExtraction, times(1)).scheduleExtraction(anyString(), anyString(), anyString());
        // 建议生成也跑在虚拟线程上：必须等待（轮询断言），否则是竞态假红（B250 闸门实测抓到）
        verify(suggestionService, timeout(5_000).times(1))
                .ensureFollowUps(anyString(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("消息更新失败：短路，不启动索引/建议/记忆")
    void updateFailureBlocksDownstream() {
        doThrow(new RuntimeException("db down")).when(messageService).completeMessageOnce(any());

        finalizer.completeAssistantMessage(assistantMessage(), "q", "um-1", 1L);

        verify(messageService, never()).updateMessage(any());
        verify(messageService, never()).indexMessageToKb(anyString(), anyString(), anyString(), anyString());
        verify(suggestionService, never()).ensureFollowUps(anyString(), anyString(), anyBoolean());
        verifyNoInteractions(memoryExtraction);
    }
}
