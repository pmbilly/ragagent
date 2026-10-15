package com.ragagent.session.qa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ragagent.platform.access.TenantContext;
import com.ragagent.platform.access.TenantContextSnapshot;
import com.ragagent.common.memory.MemoryExtractionPort;
import com.ragagent.session.service.MessageService;
import com.ragagent.session.service.suggestion.MessageSuggestionService;
import com.ragagent.session.service.SessionService;
import com.ragagent.session.service.TemporaryDocumentService;

import static org.mockito.Mockito.mock;

/**
 * A5：{@code runWithTenant} 在**新线程（身份为空）**上必须能用调用方快照补齐身份。
 *
 * <p>旧实现只做 capture → withTenantId → replay → restore：新线程的 capture 是空身份，
 * 于是 body 里看到的 principal/userId 全是 null——owner 推导成 {@code ""}、下游
 * 索引/建议/记忆 SessionNotFound。STOP watcher / 消费线程都属于这类调用点。</p>
 */
class QaTurnFinalizerTenantContextTest {

    private final QaTurnFinalizer finalizer = new QaTurnFinalizer(mock(SessionService.class),
            mock(MessageService.class), mock(MessageSuggestionService.class),
            mock(TemporaryDocumentService.class), mock(MemoryExtractionPort.class));

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("A5：新线程身份缺失时用 fallback 补齐（principal/userId 都在），并只换执行租户")
    void fallbackFillsIdentityOnFreshThread() throws Exception {
        // 模拟"请求线程"捕获到的身份快照（真实链路由拦截器设置）
        TenantContext.set(20001L, TenantContext.webUserPrincipal("u-9"), "user", false, "u-9", false);
        TenantContextSnapshot request = TenantContextSnapshot.capture();
        TenantContext.clear(); // 模拟新起的虚拟线程：身份为空

        AtomicReference<String> userSeen = new AtomicReference<>();
        AtomicReference<String> roleSeen = new AtomicReference<>();
        AtomicReference<Long> tenantSeen = new AtomicReference<>();
        AtomicReference<Object> principalSeen = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        TenantContextSnapshot fallback = request;
        Long executionTenant = 30003L;

        Thread.ofVirtual().start(() -> {
            finalizer.runWithTenant(executionTenant, fallback, () -> {
                userSeen.set(TenantContext.currentUserId());
                roleSeen.set(TenantContext.currentRole());
                tenantSeen.set(TenantContext.currentTenantId());
                principalSeen.set(TenantContext.currentPrincipal());
                done.countDown();
            });
        });

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(userSeen.get()).as("主人份必须补齐（否则 owner 推导成空）").isEqualTo("u-9");
        assertThat(roleSeen.get()).isEqualTo("user");
        assertThat(principalSeen.get()).as("授权面看调用方身份").isNotNull();
        assertThat(tenantSeen.get()).as("只换执行租户（仓库/模型解析范围）").isEqualTo(executionTenant);
    }

    @Test
    @DisplayName("A5：当前线程已有身份时以它为准（fallback 不覆盖调用方身份）")
    void currentThreadIdentityWinsOverFallback() {
        TenantContext.set(20001L, TenantContext.webUserPrincipal("u-fallback"), "user", false,
                "u-fallback", false);
        TenantContextSnapshot fallback = TenantContextSnapshot.capture();
        TenantContext.clear();

        TenantContext.set(20002L, TenantContext.webUserPrincipal("u-current"), "admin", false,
                "u-current", false);
        AtomicReference<String> userSeen = new AtomicReference<>();
        finalizer.runWithTenant(30003L, fallback, () -> userSeen.set(TenantContext.currentUserId()));

        assertThat(userSeen.get()).as("调用方身份优先").isEqualTo("u-current");
    }

    @Test
    @DisplayName("A5：无 fallback 时保持既有语义（只换租户），调用方上下文在收尾后原样恢复")
    void withoutFallbackKeepsLegacySemanticsAndRestoresCaller() {
        AtomicReference<Long> tenantSeen = new AtomicReference<>();
        AtomicReference<String> userAfter = new AtomicReference<>("unset");
        AtomicReference<Long> tenantAfter = new AtomicReference<>();

        finalizer.runWithTenant(40004L, () -> tenantSeen.set(TenantContext.currentTenantId()));

        userAfter.set(TenantContext.currentUserId());
        tenantAfter.set(TenantContext.currentTenantId());
        assertThat(tenantSeen.get()).isEqualTo(40004L);
        assertThat(userAfter.get()).as("调用方无身份 ⇒ 恢复等价于清空").isNull();
        assertThat(tenantAfter.get()).isNull();
    }
}
