package com.ragagent.platform.web;

import com.ragagent.contract.tenant.StorageEngineConfig;

import com.ragagent.contract.tenant.RetrievalConfig;

import com.ragagent.contract.tenant.ChatHistoryConfig;

import com.ragagent.contract.tenant.APIPrincipalConfig;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;

import com.ragagent.contract.agent.AgentStep;
import com.ragagent.contract.agent.AgentToolCall;
import com.ragagent.contract.agent.ToolCallTarget;
import com.ragagent.contract.event.payload.AgentActionData;
import com.ragagent.contract.event.payload.AgentCompleteData;
import com.ragagent.contract.event.payload.AgentFinalAnswerData;
import com.ragagent.contract.event.payload.AgentPlanData;
import com.ragagent.contract.event.payload.AgentQueryData;
import com.ragagent.contract.event.payload.AgentToolCallData;
import com.ragagent.contract.event.payload.AgentToolResultData;
import com.ragagent.contract.error.AppError;
import com.ragagent.contract.event.payload.ChatData;
import com.ragagent.contract.vectorstore.ConnectionConfig;
import com.ragagent.contract.event.payload.ContextCompactedData;
import com.ragagent.contract.event.payload.ErrorData;
import com.ragagent.contract.wiki.ExtractedItem;
import com.ragagent.contract.vectorstore.IndexConfig;
import com.ragagent.contract.event.payload.MCPOAuthRequiredData;
import com.ragagent.contract.event.payload.MCPOAuthResolvedData;
import com.ragagent.contract.event.payload.MergeData;
import com.ragagent.contract.memory.MemoryMessageCursor;
import com.ragagent.contract.session.MessageView;
import com.ragagent.contract.event.payload.QueryData;
import com.ragagent.contract.event.payload.RerankData;
import com.ragagent.contract.event.payload.RetrievalData;
import com.ragagent.contract.retrieval.SearchParams;
import com.ragagent.contract.retrieval.SearchResult;
import com.ragagent.contract.wiki.SlugUpdate;
import com.ragagent.contract.event.payload.StopData;
import com.ragagent.contract.model.TokenUsage;
import com.ragagent.contract.event.payload.ToolApprovalRequiredData;
import com.ragagent.contract.event.payload.ToolApprovalResolvedData;
import com.ragagent.contract.tool.ToolResult;
import com.ragagent.contract.event.payload.UserMessageInjectedData;
import com.ragagent.contract.tenant.WebSearchConfig;
import com.ragagent.contract.model.ResponseType;
import com.ragagent.contract.model.PromptCacheStatus;

/**
 * 契约载荷的 Jackson 装配：把线格式（键名/省略策略/枚举字面量）从契约类里剥离到 mixin 与
 * 显式 ser/deser，使 {@code contract.*} 保持纯 JDK。**所有**读写这些载荷的 mapper 都必须注册本模块，
 * 否则键名与省略策略会回退成 Jackson 默认（= 线格式静默变化）。
 *
 * <p>注册点：{@link JsonMappers#lenient()}（jsonb 读路径）、{@code platform.event.EventJson}（事件）、
 * {@code platform.stream.StreamJson}（SSE 流）、{@code modelcontext.JsonValues}（上下文预算）、
 * boot 的 Spring MVC mapper、Langfuse 上报 mapper。</p>
 */
public final class ContractJsonModule extends SimpleModule {

    private static final long serialVersionUID = 1L;

    /** 构造即完成全部注册（幂等：一次装配一份）。 */
    public ContractJsonModule() {
        super("ragagent-contract-json");
        setMixInAnnotation(AgentActionData.class, ContractJsonMixinsEvent.AgentActionDataMixin.class);
        setMixInAnnotation(AgentCompleteData.class, ContractJsonMixinsEvent.AgentCompleteDataMixin.class);
        setMixInAnnotation(AgentFinalAnswerData.class, ContractJsonMixinsEvent.AgentFinalAnswerDataMixin.class);
        setMixInAnnotation(AgentPlanData.class, ContractJsonMixinsEvent.AgentPlanDataMixin.class);
        setMixInAnnotation(AgentQueryData.class, ContractJsonMixinsEvent.AgentQueryDataMixin.class);
        setMixInAnnotation(AgentStep.class, ContractJsonMixinsAgent.AgentStepMixin.class);
        setMixInAnnotation(AgentToolCall.class, ContractJsonMixinsAgent.AgentToolCallMixin.class);
        setMixInAnnotation(AgentToolCallData.class, ContractJsonMixinsEvent.AgentToolCallDataMixin.class);
        setMixInAnnotation(AgentToolResultData.class, ContractJsonMixinsEvent.AgentToolResultDataMixin.class);
        setMixInAnnotation(AppError.class, ContractJsonMixinsCore.AppErrorMixin.class);
        setMixInAnnotation(ChatData.class, ContractJsonMixinsEvent.ChatDataMixin.class);
        setMixInAnnotation(ConnectionConfig.class, ContractJsonMixinsCore.ConnectionConfigMixin.class);
        setMixInAnnotation(ContextCompactedData.class, ContractJsonMixinsEvent.ContextCompactedDataMixin.class);
        setMixInAnnotation(ErrorData.class, ContractJsonMixinsEvent.ErrorDataMixin.class);
        setMixInAnnotation(ExtractedItem.class, ContractJsonMixinsCore.ExtractedItemMixin.class);
        setMixInAnnotation(IndexConfig.class, ContractJsonMixinsCore.IndexConfigMixin.class);
        setMixInAnnotation(MCPOAuthRequiredData.class, ContractJsonMixinsEvent.MCPOAuthRequiredDataMixin.class);
        setMixInAnnotation(MCPOAuthResolvedData.class, ContractJsonMixinsEvent.MCPOAuthResolvedDataMixin.class);
        setMixInAnnotation(MergeData.class, ContractJsonMixinsEvent.MergeDataMixin.class);
        setMixInAnnotation(MemoryMessageCursor.class, ContractJsonMixinsMemory.MemoryMessageCursorMixin.class);
        setMixInAnnotation(MessageView.class, ContractJsonMixinsSession.MessageViewMixin.class);
        setMixInAnnotation(QueryData.class, ContractJsonMixinsEvent.QueryDataMixin.class);
        setMixInAnnotation(RerankData.class, ContractJsonMixinsEvent.RerankDataMixin.class);
        setMixInAnnotation(RetrievalData.class, ContractJsonMixinsEvent.RetrievalDataMixin.class);
        setMixInAnnotation(SearchParams.class, ContractJsonMixinsCore.SearchParamsMixin.class);
        setMixInAnnotation(SearchResult.class, ContractJsonMixinsRetrieval.SearchResultMixin.class);
        setMixInAnnotation(SlugUpdate.class, ContractJsonMixinsCore.SlugUpdateMixin.class);
        setMixInAnnotation(StopData.class, ContractJsonMixinsEvent.StopDataMixin.class);
        setMixInAnnotation(TokenUsage.class, ContractJsonMixinsCore.TokenUsageMixin.class);
        setMixInAnnotation(ToolApprovalRequiredData.class, ContractJsonMixinsEvent.ToolApprovalRequiredDataMixin.class);
        setMixInAnnotation(ToolApprovalResolvedData.class, ContractJsonMixinsEvent.ToolApprovalResolvedDataMixin.class);
        setMixInAnnotation(ToolCallTarget.class, ContractJsonMixinsAgent.ToolCallTargetMixin.class);
        setMixInAnnotation(ToolResult.class, ContractJsonMixinsCore.ToolResultMixin.class);
        setMixInAnnotation(UserMessageInjectedData.class, ContractJsonMixinsEvent.UserMessageInjectedDataMixin.class);
        setMixInAnnotation(WebSearchConfig.class, ContractJsonMixinsCore.WebSearchConfigMixin.class);
        setMixInAnnotation(APIPrincipalConfig.class, ContractJsonMixinsTenant.APIPrincipalConfigMixin.class);
        setMixInAnnotation(ChatHistoryConfig.class, ContractJsonMixinsTenant.ChatHistoryConfigMixin.class);
        setMixInAnnotation(RetrievalConfig.class, ContractJsonMixinsTenant.RetrievalConfigMixin.class);
        setMixInAnnotation(StorageEngineConfig.class, ContractJsonMixinsTenant.StorageEngineConfigMixin.class);
        addSerializer(ResponseType.class, new ResponseTypeSerializer());
        addDeserializer(ResponseType.class, new ResponseTypeDeserializer());
        addSerializer(PromptCacheStatus.class, new PromptCacheStatusSerializer());
        addDeserializer(PromptCacheStatus.class, new PromptCacheStatusDeserializer());
    }

    private static final class ResponseTypeSerializer extends JsonSerializer<ResponseType> {
        @Override
        public void serialize(ResponseType v, JsonGenerator g, SerializerProvider p) throws IOException {
            g.writeString(v.value());
        }
    }

    private static final class ResponseTypeDeserializer extends JsonDeserializer<ResponseType> {
        @Override
        public ResponseType deserialize(JsonParser p, DeserializationContext c) throws IOException {
            return ResponseType.fromValue(p.getText());
        }
    }

    private static final class PromptCacheStatusSerializer extends JsonSerializer<PromptCacheStatus> {
        @Override
        public void serialize(PromptCacheStatus v, JsonGenerator g, SerializerProvider p) throws IOException {
            g.writeString(v.value());
        }
    }

    private static final class PromptCacheStatusDeserializer extends JsonDeserializer<PromptCacheStatus> {
        @Override
        public PromptCacheStatus deserialize(JsonParser p, DeserializationContext c) throws IOException {
            return PromptCacheStatus.fromValue(p.getText());
        }
    }
}
