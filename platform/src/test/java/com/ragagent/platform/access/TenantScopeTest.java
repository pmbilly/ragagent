package com.ragagent.platform.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantScopeTest {

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void runsWithSnapshotInAnotherThread() throws InterruptedException {
        TenantContext.set(7L, TenantContext.webUserPrincipal("u-7"), "Owner", false, "u-7", false);
        TenantContextSnapshot snapshot = TenantContextSnapshot.capture();
        TenantContext.clear();

        AtomicReference<Long> seenTenant = new AtomicReference<>();
        AtomicReference<String> seenUser = new AtomicReference<>();
        Thread worker = Thread.ofVirtual().start(() -> TenantScope.run(snapshot, () -> {
            seenTenant.set(TenantContext.currentTenantId());
            seenUser.set(TenantContext.currentUserId());
        }));
        worker.join();

        assertThat(seenTenant.get()).isEqualTo(7L);
        assertThat(seenUser.get()).isEqualTo("u-7");
        assertThat(TenantContext.currentTenantId()).isNull();
    }

    @Test
    void restoresPreviousContextAfterScope() {
        TenantContext.set(3L, TenantContext.webUserPrincipal("u-3"), "Viewer", false, "u-3", false);
        TenantContextSnapshot next = new TenantContextSnapshot(
                9L, TenantContext.webUserPrincipal("u-9"), "Owner", false, "u-9", false, null, null);

        TenantScope.run(next, () -> {
            assertThat(TenantContext.currentTenantId()).isEqualTo(9L);
            assertThat(TenantContext.currentUserId()).isEqualTo("u-9");
        });

        assertThat(TenantContext.currentTenantId()).isEqualTo(3L);
        assertThat(TenantContext.currentUserId()).isEqualTo("u-3");
    }

    @Test
    void supplyReturnsValueAndRestores() {
        TenantContextSnapshot snapshot = new TenantContextSnapshot(
                5L, null, null, false, null, false, null, null);

        String value = TenantScope.supply(snapshot, () -> "tenant=" + TenantContext.currentTenantId());

        assertThat(value).isEqualTo("tenant=5");
        assertThat(TenantContext.currentTenantId()).isNull();
    }

    @Test
    void callablePropagatesValueAndRestores() throws Exception {
        TenantContextSnapshot snapshot = new TenantContextSnapshot(
                6L, null, null, false, null, false, null, null);

        assertThat(TenantScope.callable(snapshot,
                () -> "tenant=" + TenantContext.currentTenantId()).call()).isEqualTo("tenant=6");
        assertThat(TenantContext.currentTenantId()).isNull();
    }
}
