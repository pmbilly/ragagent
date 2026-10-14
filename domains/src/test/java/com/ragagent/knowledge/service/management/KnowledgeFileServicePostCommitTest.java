package com.ragagent.knowledge.service.management;


import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * K7：外部副作用（删旧索引 + batchIndex）必须排在**事务提交后**——回滚时一次都不执行。
 *
 * <p>本地事务回滚不了外部索引的写入，所以"先在事务内索引、后回滚"会留下脏索引。</p>
 */
class KnowledgeFileServicePostCommitTest {

    @AfterEach
    void clearTxSync() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("K7：无事务上下文 ⇒ 内联执行（直调/单测行为不变）")
    void runsInlineWithoutTransaction() {
        AtomicInteger calls = new AtomicInteger();
        KnowledgeFileService.afterCommit(calls::incrementAndGet);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("K7：事务内注册不立即执行；回滚（不触发 afterCommit）⇒ 零外部副作用")
    void rollbackLeavesNoExternalEffect() {
        AtomicInteger calls = new AtomicInteger();
        TransactionSynchronizationManager.initSynchronization();
        KnowledgeFileService.afterCommit(calls::incrementAndGet);
        assertThat(calls.get()).as("提交前不得触碰外部索引").isZero();
        TransactionSynchronizationManager.clearSynchronization(); // 回滚：回调不触发
        assertThat(calls.get()).as("回滚后仍为零").isZero();
    }

    @Test
    @DisplayName("K7：提交 ⇒ 恰好执行一次")
    void commitRunsExactlyOnce() {
        AtomicInteger calls = new AtomicInteger();
        TransactionSynchronizationManager.initSynchronization();
        KnowledgeFileService.afterCommit(calls::incrementAndGet);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("K7：提交后外部失败只记日志，不向调用方抛出（DB 已提交，不能变 500）")
    void postCommitFailureIsNotThrown() {
        TransactionSynchronizationManager.initSynchronization();
        KnowledgeFileService.afterCommit(() -> {
            throw new IllegalStateException("engine down");
        });
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
    }
}
