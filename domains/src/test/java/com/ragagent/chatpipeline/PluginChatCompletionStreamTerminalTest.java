package com.ragagent.chatpipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ragagent.chatpipeline.plugin.PluginChatCompletionStream;
import com.ragagent.contract.model.ResponseType;
import com.ragagent.contract.event.Event;
import com.ragagent.platform.event.EventType;
import com.ragagent.contract.event.payload.AgentFinalAnswerData;
import com.ragagent.llm.ChatStream;
import com.ragagent.modelruntime.adapter.provider.chat.common.QueuedChatStream;
import com.ragagent.llm.LlmChatClient;
import com.ragagent.contract.model.chat.ChatMessage;
import com.ragagent.contract.model.chat.ChatOptions;
import com.ragagent.contract.model.chat.ChatResponse;
import com.ragagent.llm.domain.StreamResponse;

/**
 * NORMAL 流的终态收口（整改 A1）。
 *
 * <p>轮的生命周期挂在「答案 done」上——{@code QaTurnExecutor} 的 FINAL_ANSWER 处理器只在
 * {@code done=true} 时完成 assistant 消息并发 COMPLETE。异常收束（生产者关闭 / 空闲超时 /
 * ERROR+done）原先只 flush 不补终态 ⇒ assistant 停在未完成、SSE 永远轮询。</p>
 */
class PluginChatCompletionStreamTerminalTest {

    @Test
    @DisplayName("A1：流无终态帧（生产者关闭 / 空闲超时同一条路径）也补 done=true 终态答案")
    void emitsTerminalAnswerWhenQueueEndsWithoutDone() throws Exception {
        Rec46cSupport.RecBus bus = new Rec46cSupport.RecBus();
        Rec46cSupport.StubModelService ms = new Rec46cSupport.StubModelService();
        ms.chatModels.put("cm", new NullQueueChat());
        PluginChatCompletionStream p = new PluginChatCompletionStream(ms);

        p.onEvent(PipelineEventType.CHAT_COMPLETION_STREAM, chatManage("ss-terminal", bus), () -> null);
        Thread.sleep(200);

        List<Event> finals = finalAnswers(bus);
        assertThat(finals).as("异常收束必须补一帧终态答案").isNotEmpty();
        assertThat(((AgentFinalAnswerData) finals.get(finals.size() - 1).getData()).isDone())
                .as("最后一帧答案必须是终态（done=true）").isTrue();
        assertThat(finals.stream()
                .filter(e -> ((AgentFinalAnswerData) e.getData()).isDone()).count())
                .as("终态帧恰好一帧（幂等）").isEqualTo(1);
    }

    @Test
    @DisplayName("A1：模型 ERROR+done 时错误事件后仍补终态答案（客户先见错误、轮再收口）")
    void emitsTerminalAnswerAfterErrorDone() throws Exception {
        Rec46cSupport.RecBus bus = new Rec46cSupport.RecBus();
        Rec46cSupport.StubModelService ms = new Rec46cSupport.StubModelService();
        ms.chatModels.put("cm", new Rec46cSupport.StubChat(List.of(
                Rec46cSupport.streamResponse(ResponseType.ERROR, "上游 500", true))));
        PluginChatCompletionStream p = new PluginChatCompletionStream(ms);

        p.onEvent(PipelineEventType.CHAT_COMPLETION_STREAM, chatManage("ss-err", bus), () -> null);
        Thread.sleep(200);

        List<Event> all = bus.all();
        int errorIdx = firstIndexOf(all, EventType.EVENT_ERROR);
        assertThat(errorIdx).as("错误事件必须送达").isNotNegative();
        List<Event> finals = finalAnswers(bus);
        assertThat(((AgentFinalAnswerData) finals.get(finals.size() - 1).getData()).isDone()).isTrue();
        assertThat(firstIndexOf(all, EventType.EVENT_AGENT_FINAL_ANSWER))
                .as("错误先于终态帧（顺序不能反）").isGreaterThan(errorIdx);
    }

    @Test
    @DisplayName("回归：正常 ANSWER+done 只发一帧终态（不追加空终态帧）")
    void normalCompletionEmitsSingleTerminalFrame() throws Exception {
        Rec46cSupport.RecBus bus = new Rec46cSupport.RecBus();
        Rec46cSupport.StubModelService ms = new Rec46cSupport.StubModelService();
        ms.chatModels.put("cm", new Rec46cSupport.StubChat(List.of(
                Rec46cSupport.streamResponse(ResponseType.ANSWER, "完整答案", true))));
        PluginChatCompletionStream p = new PluginChatCompletionStream(ms);

        p.onEvent(PipelineEventType.CHAT_COMPLETION_STREAM, chatManage("ss-ok", bus), () -> null);
        Thread.sleep(200);

        List<Event> finals = finalAnswers(bus);
        assertThat(finals).hasSize(1);
        AgentFinalAnswerData data = (AgentFinalAnswerData) finals.get(0).getData();
        assertThat(data.isDone()).isTrue();
        assertThat(data.getContent()).isEqualTo("完整答案");
    }

    // ── 夹具 ────────────────────────────────────────────────────────────────

    /** 队列 poll 立即返回 null：瞬时驱动"生产者关闭 / 空闲超时"分支（同一条代码路径）。 */
    static final class NullQueueChat implements LlmChatClient {

        @Override
        public ChatResponse chat(List<ChatMessage> messages, ChatOptions options) {
            throw new UnsupportedOperationException("stream-only stub");
        }

        @Override
        public ChatStream chatStream(List<ChatMessage> messages,
                                                              ChatOptions options) {
            return new QueuedChatStream(null) {
                @Override
                public StreamResponse poll(long timeout, TimeUnit unit) {
                    return null;
                }
            };
        }

        @Override
        public String getModelName() {
            return "null-queue";
        }

        @Override
        public String getModelId() {
            return "nq-1";
        }
    }

    private static ChatManage chatManage(String sessionId, Rec46cSupport.RecBus bus) {
        ChatManage cm = new ChatManage();
        cm.setSessionId(sessionId);
        cm.setChatModelId("cm");
        cm.setQuery("q");
        cm.setLanguage("zh");
        SummaryConfig sc = new SummaryConfig();
        sc.setPrompt("SYS {{query}}");
        cm.setSummaryConfig(sc);
        cm.setIntent(QueryIntent.CHITCHAT);
        cm.setUserContent("U");
        cm.setEventBus(bus);
        return cm;
    }

    private static List<Event> finalAnswers(Rec46cSupport.RecBus bus) {
        List<Event> out = new ArrayList<>();
        for (Event e : bus.all()) {
            if (e.getType() == EventType.EVENT_AGENT_FINAL_ANSWER) {
                out.add(e);
            }
        }
        return out;
    }

    private static int firstIndexOf(List<Event> events, String type) {
        for (int i = 0; i < events.size(); i++) {
            if (type.equals(events.get(i).getType())) {
                return i;
            }
        }
        return -1;
    }
}
