package com.ragagent.session.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ragagent.contract.model.LlmChatClient;
import com.ragagent.contract.pipeline.PipelinePorts;
import org.junit.jupiter.api.Test;

/**
 * 请求段只读视图的可消费性：模型准备只经 {@link TurnInputView} 取配置，
 * 不需要可变 {@link ChatManage}。
 */
class TurnInputViewTest {

    @Test
    void modelPreparationReadsOnlyTheRequestView() {
        ChatManage chatManage = new ChatManage();
        chatManage.setChatModelId("model-1");
        chatManage.setSummaryConfig(new SummaryConfig());

        PipelinePorts.ModelService modelService = mock(PipelinePorts.ModelService.class);
        LlmChatClient client = mock(LlmChatClient.class);
        when(modelService.getChatModel("model-1")).thenReturn(client);

        PipelineCommon.PreparedChatModel prepared =
                PipelineCommon.prepareChatModel(modelService, chatManage);

        assertThat(prepared.chatModel()).isSameAs(client);
    }
}
