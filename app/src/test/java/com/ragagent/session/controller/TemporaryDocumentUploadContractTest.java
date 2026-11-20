package com.ragagent.session.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ragagent.contract.agent.AgentLookup;
import com.ragagent.contract.error.BizException;
import com.ragagent.platform.access.TenantContext;
import com.ragagent.session.attachment.TemporaryDocumentService;
import com.ragagent.session.conversation.SessionService;
import com.ragagent.session.domain.document.TemporaryDocument;
import com.ragagent.session.service.AgentResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 附件上传入口的 agent 语义验收：
 * agent 门控（supported_file_types、音频 ASR）/ parser_engine 绑定与
 * agent 级回落。共享 agent 与 agent_source_tenant_id 面随空间分享裁撤。
 */
class TemporaryDocumentUploadContractTest {

    private static final long TENANT = 1L;

    private SessionService sessionService;
    private TemporaryDocumentService temporaryDocuments;
    private AgentResolver agentResolver;
    private TemporaryDocumentController controller;

    @BeforeEach
    void setUp() {
        sessionService = mock(SessionService.class);
        temporaryDocuments = mock(TemporaryDocumentService.class);
        agentResolver = mock(AgentResolver.class);
        when(agentResolver.resolve(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new AgentResolver.ResolvedAgent(null, 0L));
        controller = new TemporaryDocumentController(sessionService, temporaryDocuments,
                agentResolver);
        TenantContext.set(TENANT, null, null, false, "u-1", false);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static MockHttpServletRequest multipartRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType("multipart/form-data; boundary=x");
        return request;
    }

    private static MockMultipartFile file(String name) {
        return new MockMultipartFile("file", name, "application/octet-stream",
                "hello".getBytes());
    }

    private static AgentLookup.AgentView agent(String configJson) {
        return new AgentLookup.AgentView("a-1", "own-agent", "", "", configJson, TENANT);
    }

    private void stubCreate() {
        when(temporaryDocuments.create(anyLong(), anyString(), any(), any(), anyLong(), any(),
                any())).thenReturn(new TemporaryDocument());
    }

    private TemporaryDocumentService.CreateOptions capturedOptions() {
        ArgumentCaptor<TemporaryDocumentService.CreateOptions> captor =
                ArgumentCaptor.forClass(TemporaryDocumentService.CreateOptions.class);
        verify(temporaryDocuments).create(eq(TENANT), eq("s-1"), any(), any(), anyLong(), any(),
                captor.capture());
        return captor.getValue();
    }




    @Test
    void unsupportedFileTypeForAgentIs400() {
        AgentLookup.AgentView own = agent("{\"supportedFileTypes\":[\"pdf\"]}");
        when(agentResolver.resolve("a-1", 0L))
                .thenReturn(new AgentResolver.ResolvedAgent(own, 0L));

        assertThatThrownBy(() -> controller.upload("s-1", file("notes.txt"), "a-1", "",
                multipartRequest()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("file type is not supported by this agent");
    }

    @Test
    void supportedFileTypeMatchesWithDotAndCase() {
        AgentLookup.AgentView own = agent("{\"supportedFileTypes\":[\".TXT\"]}");
        when(agentResolver.resolve("a-1", 0L))
                .thenReturn(new AgentResolver.ResolvedAgent(own, 0L));
        stubCreate();

        var response = controller.upload("s-1", file("notes.txt"), "a-1", "",
                multipartRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(202);
    }

    @Test
    void audioWithoutAsrConfigIs400() {
        AgentLookup.AgentView own = agent("{\"audioUploadEnabled\":false}");
        when(agentResolver.resolve("a-1", 0L))
                .thenReturn(new AgentResolver.ResolvedAgent(own, 0L));

        assertThatThrownBy(() -> controller.upload("s-1", file("voice.mp3"), "a-1", "",
                multipartRequest()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("audio upload is not enabled or no ASR model is configured");
    }

    @Test
    void audioWithAsrConfigWritesAsrModelId() {
        AgentLookup.AgentView own = agent(
                "{\"audioUploadEnabled\":true,\"asrModelId\":\"asr-1\"}");
        when(agentResolver.resolve("a-1", 0L))
                .thenReturn(new AgentResolver.ResolvedAgent(own, 0L));
        stubCreate();

        controller.upload("s-1", file("voice.mp3"), "a-1", "", multipartRequest());

        assertThat(capturedOptions().asrModelId()).isEqualTo("asr-1");
    }

    @Test
    void agentParserRuleFillsEngineWhenNotExplicit() {
        AgentLookup.AgentView own = agent(
                "{\"chatParserEngineRules\":[{\"file_types\":[\"txt\"],\"engine\":\"markitdown\"}]}");
        when(agentResolver.resolve("a-1", 0L))
                .thenReturn(new AgentResolver.ResolvedAgent(own, 0L));
        stubCreate();

        controller.upload("s-1", file("notes.txt"), "a-1", "auto", multipartRequest());

        assertThat(capturedOptions().parserEngine()).isEqualTo("markitdown");
    }

    @Test
    void explicitParserEngineIsKept() {
        AgentLookup.AgentView own = agent(
                "{\"chatParserEngineRules\":[{\"file_types\":[\"txt\"],\"engine\":\"markitdown\"}]}");
        when(agentResolver.resolve("a-1", 0L))
                .thenReturn(new AgentResolver.ResolvedAgent(own, 0L));
        stubCreate();

        controller.upload("s-1", file("notes.txt"), "a-1", " simple ", multipartRequest());

        assertThat(capturedOptions().parserEngine()).isEqualTo("simple");
    }

    @Test
    void withoutAgentKeepsCallerScopeAndEmptyOptions() {
        when(agentResolver.resolve(null, 0L)).thenReturn(new AgentResolver.ResolvedAgent(null, 0L));
        stubCreate();

        var response = controller.upload("s-1", file("notes.txt"), null, null,
                multipartRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        TemporaryDocumentService.CreateOptions options = capturedOptions();
        assertThat(options.resourceTenantId()).isZero();
        assertThat(options.parserEngine()).isEmpty();
    }
}
