package com.ragagent.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ragagent.contract.model.ResponseType;
import com.ragagent.platform.stream.StreamEvent;
import org.junit.jupiter.api.Test;

class TurnLifecycleTest {

    @Test
    void firstFinishWinsAndTerminalIsImmutable() {
        TurnLifecycle lifecycle = new TurnLifecycle();

        assertThat(lifecycle.finish(TurnLifecycle.State.DONE)).isTrue();
        assertThat(lifecycle.state()).isEqualTo(TurnLifecycle.State.DONE);
        assertThat(lifecycle.isTerminal()).isTrue();

        assertThat(lifecycle.finish(TurnLifecycle.State.FAILED))
                .as("已终态不得被改写").isFalse();
        assertThat(lifecycle.state()).isEqualTo(TurnLifecycle.State.DONE);
    }

    @Test
    void finalizeOnlyOnce() {
        TurnLifecycle lifecycle = new TurnLifecycle();

        assertThat(lifecycle.beginFinalize()).isTrue();
        assertThat(lifecycle.beginFinalize()).as("收尾段只能进一次").isFalse();
        assertThat(lifecycle.finish(TurnLifecycle.State.DONE)).isTrue();
    }

    @Test
    void finishRejectsNonTerminalState() {
        TurnLifecycle lifecycle = new TurnLifecycle();
        assertThatThrownBy(() -> lifecycle.finish(TurnLifecycle.State.STREAMING))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void terminalEventClassification() {
        assertThat(TurnLifecycle.isTerminalEvent(event(ResponseType.COMPLETE, true))).isTrue();
        assertThat(TurnLifecycle.isTerminalEvent(event(ResponseType.ERROR, true))).isTrue();
        assertThat(TurnLifecycle.isTerminalEvent(event(ResponseType.ERROR, false))).isFalse();
        assertThat(TurnLifecycle.isTerminalEvent(event(ResponseType.ANSWER, true))).isFalse();
        assertThat(TurnLifecycle.isTerminalEvent(null)).isFalse();
    }

    private static StreamEvent event(ResponseType type, boolean done) {
        StreamEvent event = new StreamEvent();
        event.setType(type);
        event.setDone(done);
        return event;
    }
}
