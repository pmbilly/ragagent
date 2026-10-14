package com.ragagent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ragagent.platform.event.Event;
import com.ragagent.platform.event.EventType;
import com.ragagent.event.payload.AgentCompleteData;

/**
 * AGENT 失败收束的终态契约（整改 A1-AGENT）。
 *
 * <p>原实现的顺序是「终态帧（agentComplete，来自 executeLoop 的 finally）→ 错误事件（外层 catch）」；
 * 而终态帧正是 SSE 的退出条件——错误帧会落在流关闭之后（客户端只看到"成功收尾"）。
 * 失败收束必须：**错误事件在前、终态帧在后**，且终态载荷显式携带失败 outcome。</p>
 */
class AgentEngineFailureTerminalTest {

    @Test
    @DisplayName("A1-AGENT：模型失败时错误事件先于终态帧，终态载荷带 outcome=failed")
    void failureEmitsErrorBeforeTerminalCompletion() {
        Engine46bStubSupport.StubChat chat = new Engine46bStubSupport.StubChat(List.of(
                List.of(Engine46bStubSupport.errorChunk("provider exploded", "incomplete"))));
        AgentEngine engine = Engine46bStubSupport.newEngine(chat);
        List<Event> events = new ArrayList<>();
        engine.getBusForTest().on(EventType.EVENT_ERROR, events::add);
        engine.getBusForTest().on(EventType.EVENT_AGENT_COMPLETE, events::add);

        assertThatThrownBy(() -> engine.execute("sess-1", "msg-1", "q",
                Engine46bStubSupport.emptyMessages()))
                .isInstanceOf(AgentEngineException.class);

        assertThat(events).extracting(Event::getType)
                .as("失败收束：错误在前、终态在后（且各恰好一次）")
                .containsExactly(EventType.EVENT_ERROR, EventType.EVENT_AGENT_COMPLETE);
        AgentCompleteData complete = (AgentCompleteData) events.get(1).getData();
        assertThat(complete.getExtra()).as("失败 outcome 必须显式进终态载荷").isNotNull();
        assertThat(complete.getExtra()).containsEntry("outcome", "failed");
        assertThat(complete.getExtra().get("error")).asString().contains("provider exploded");
    }

    @Test
    @DisplayName("回归：成功路径只发一帧终态（不追加失败 outcome）")
    void successPathEmitsSinglePlainCompletion() {
        Engine46bStubSupport.StubChat chat = new Engine46bStubSupport.StubChat(List.of(
                List.of(Engine46bStubSupport.msgChunk("done", true, "stop"))));
        AgentEngine engine = Engine46bStubSupport.newEngine(chat);
        List<Event> events = new ArrayList<>();
        engine.getBusForTest().on(EventType.EVENT_ERROR, events::add);
        engine.getBusForTest().on(EventType.EVENT_AGENT_COMPLETE, events::add);

        engine.execute("sess-1", "msg-1", "q", Engine46bStubSupport.emptyMessages());

        assertThat(events).extracting(Event::getType)
                .containsExactly(EventType.EVENT_AGENT_COMPLETE);
        AgentCompleteData complete = (AgentCompleteData) events.get(0).getData();
        assertThat(complete.getExtra()).as("成功收尾不带 outcome（缺省即成功，保持线格式）").isNull();
    }
}
