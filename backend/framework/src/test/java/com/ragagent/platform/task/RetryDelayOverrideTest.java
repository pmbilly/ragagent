package com.ragagent.platform.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RetryDelayOverrideTest {

    @Test
    void nullUsesFallback() {
        assertThat(new RetryDelayOverride().resolve(42L)).isEqualTo(42L);
    }

    @Test
    void overrideWins() {
        RetryDelayOverride override = new RetryDelayOverride();
        override.set(0L);
        assertThat(override.resolve(42L)).isZero();
    }

    @Test
    void negativeOverrideIsClampedToZero() {
        RetryDelayOverride override = new RetryDelayOverride();
        override.set(-5L);
        assertThat(override.resolve(42L)).isZero();
    }

    @Test
    void resetRestoresFallback() {
        RetryDelayOverride override = new RetryDelayOverride();
        override.set(1L);
        override.set(null);
        assertThat(override.resolve(42L)).isEqualTo(42L);
    }
}
