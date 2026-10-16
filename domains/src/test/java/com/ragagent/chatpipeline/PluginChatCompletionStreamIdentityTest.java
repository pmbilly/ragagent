package com.ragagent.chatpipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ragagent.chatpipeline.plugin.PluginChatCompletionStream;
import com.ragagent.platform.access.TenantContext;
import com.ragagent.contract.model.ResponseType;
import com.ragagent.contract.model.ChatStream;
import com.ragagent.contract.model.LlmChatClient;
import com.ragagent.modelruntime.adapter.provider.chat.common.QueuedChatStream;
import com.ragagent.contract.model.chat.ChatMessage;
import com.ragagent.contract.model.chat.ChatOptions;
import com.ragagent.contract.model.chat.ChatResponse;
import com.ragagent.contract.model.StreamResponse;

/**
 * A5：NORMAL 流消费线程必须显式继承请求身份。
 *
 * <p>ThreadLocal 不跨线程：生产者线程起消费虚拟线程时若不 capture/replay，消费线程看到的是
 * 空身份——owner 推导成 {@code ""}、下游索引/建议/记忆全部 SessionNotFound（历史事故）。
 * 这里在**消费线程**首次读取分片时探针身份。</p>
 */
class PluginChatCompletionStreamIdentityTest {

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("A5：消费线程读取分片时能看到请求身份（principal + userId + 租户）")
    void consumerThreadSeesRequestIdentity() throws Exception {
        Rec46cSupport.RecBus bus = new Rec46cSupport.RecBus();
        Rec46cSupport.StubModelService ms = new Rec46cSupport.StubModelService();
        IdentityProbeChat probe = new IdentityProbeChat();
        ms.chatModels.put("cm", probe);
        PluginChatCompletionStream p = new PluginChatCompletionStream(ms);

        // 请求线程身份（真实链路由拦截器设置）
        TenantContext.set(10002L, TenantContext.webUserPrincipal("u-777"), "user", false, "u-777", false);
        p.onEvent(PipelineEventType.CHAT_COMPLETION_STREAM, chatManage("ss-ident", bus), () -> null);

        assertThat(probe.consumed.await(5, TimeUnit.SECONDS)).as("消费线程已读取分片").isTrue();
        assertThat(probe.userIdSeen.get())
                .as("消费线程身份缺失 ⇒ owner 推导成空、下游 SessionNotFound")
                .isEqualTo("u-777");
        assertThat(probe.tenantSeen.get()).isEqualTo(10002L);
        assertThat(probe.principalSeen.get()).as("principal 必须一起过去（授权面看调用方）").isNotNull();
    }

    // ── 夹具 ────────────────────────────────────────────────────────────────

    /** 在**消费线程**读取分片时记录身份的桩。 */
    static final class IdentityProbeChat implements LlmChatClient {
        final CountDownLatch consumed = new CountDownLatch(1);
        final AtomicReference<String> userIdSeen = new AtomicReference<>("");
        final AtomicReference<Long> tenantSeen = new AtomicReference<>();
        final AtomicReference<TenantContext.Principal> principalSeen = new AtomicReference<>();

        @Override
        public ChatResponse chat(List<ChatMessage> messages, ChatOptions options) {
            throw new UnsupportedOperationException("stream-only stub");
        }

        @Override
        public ChatStream chatStream(List<ChatMessage> messages, ChatOptions options) {
            return new QueuedChatStream(null) {
                @Override
                public StreamResponse poll(long timeout, TimeUnit unit) {
                    userIdSeen.set(String.valueOf(TenantContext.currentUserId()));
                    tenantSeen.set(TenantContext.currentTenantId());
                    principalSeen.set(TenantContext.currentPrincipal());
                    consumed.countDown();
                    return StreamResponse.of(ResponseType.ANSWER, "回答", true);
                }
            };
        }

        @Override
        public String getModelName() {
            return "identity-probe";
        }

        @Override
        public String getModelId() {
            return "ip-1";
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
}
