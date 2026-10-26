package com.ragagent.session.rag.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ragagent.contract.event.Event;
import com.ragagent.contract.event.EventBusInterface;
import com.ragagent.contract.event.payload.AgentFinalAnswerData;
import com.ragagent.platform.event.EventType;
import com.ragagent.session.rag.PipelineState;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 异常/超时收束补的终态帧必须带 outcome=FAILED——它只表示流关闭，不是业务成功。 */
class PluginChatCompletionStreamEndStreamTest {

    @Test
    void abnormalEndMarksOutcomeFailed() {
        EventBusInterface bus = mock(EventBusInterface.class);

        PluginChatCompletionStream.emitTerminalAnswer(bus, mock(PipelineState.class),
                "a-1", new AtomicBoolean(false), true);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(bus).emit(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(EventType.EVENT_AGENT_FINAL_ANSWER);
        AgentFinalAnswerData data = (AgentFinalAnswerData) captor.getValue().getData();
        assertThat(data.getOutcome()).isEqualTo("FAILED");
        assertThat(data.isDone()).isTrue();
    }
}
