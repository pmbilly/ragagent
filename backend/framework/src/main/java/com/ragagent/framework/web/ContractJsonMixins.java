package com.ragagent.framework.web;

import com.ragagent.framework.error.AppError;
import com.ragagent.framework.event.payload.AgentActionData;
import com.ragagent.framework.event.payload.AgentCompleteData;
import com.ragagent.framework.event.payload.AgentFinalAnswerData;
import com.ragagent.framework.event.payload.AgentPlanData;
import com.ragagent.framework.event.payload.AgentQueryData;
import com.ragagent.framework.event.payload.AgentToolCallData;
import com.ragagent.framework.event.payload.AgentToolResultData;
import com.ragagent.framework.event.payload.ChatData;
import com.ragagent.framework.event.payload.ContextCompactedData;
import com.ragagent.framework.event.payload.ErrorData;
import com.ragagent.framework.event.payload.MCPOAuthRequiredData;
import com.ragagent.framework.event.payload.MCPOAuthResolvedData;
import com.ragagent.framework.event.payload.MergeData;
import com.ragagent.framework.event.payload.QueryData;
import com.ragagent.framework.event.payload.RerankData;
import com.ragagent.framework.event.payload.RetrievalData;
import com.ragagent.framework.event.payload.StopData;
import com.ragagent.framework.event.payload.ToolApprovalRequiredData;
import com.ragagent.framework.event.payload.ToolApprovalResolvedData;
import com.ragagent.framework.event.payload.UserMessageInjectedData;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 契约载荷的 mixin 映射表（纯 JDK 类型，不绑定 Jackson 版本）。
 *
 * <p>{@link ContractJsonModule} 注册这张表，所有读写契约载荷的 mapper 共用同一份映射，
 * 避免各 mapper 的键名/省略策略漂移。</p>
 */
public final class ContractJsonMixins {

    private ContractJsonMixins() {
    }

    /** 目标类型 → mixin 类型（顺序稳定，便于差异对比）。 */
    public static Map<Class<?>, Class<?>> mappings() {
        Map<Class<?>, Class<?>> m = new LinkedHashMap<>();
        m.put(AgentActionData.class, ContractJsonMixinsEvent.AgentActionDataMixin.class);
        m.put(AgentCompleteData.class, ContractJsonMixinsEvent.AgentCompleteDataMixin.class);
        m.put(AgentFinalAnswerData.class, ContractJsonMixinsEvent.AgentFinalAnswerDataMixin.class);
        m.put(AgentPlanData.class, ContractJsonMixinsEvent.AgentPlanDataMixin.class);
        m.put(AgentQueryData.class, ContractJsonMixinsEvent.AgentQueryDataMixin.class);
        m.put(AgentToolCallData.class, ContractJsonMixinsEvent.AgentToolCallDataMixin.class);
        m.put(AgentToolResultData.class, ContractJsonMixinsEvent.AgentToolResultDataMixin.class);
        m.put(AppError.class, ContractJsonMixinsCore.AppErrorMixin.class);
        m.put(ChatData.class, ContractJsonMixinsEvent.ChatDataMixin.class);
        m.put(ContextCompactedData.class, ContractJsonMixinsEvent.ContextCompactedDataMixin.class);
        m.put(ErrorData.class, ContractJsonMixinsEvent.ErrorDataMixin.class);
        m.put(MCPOAuthRequiredData.class, ContractJsonMixinsEvent.MCPOAuthRequiredDataMixin.class);
        m.put(MCPOAuthResolvedData.class, ContractJsonMixinsEvent.MCPOAuthResolvedDataMixin.class);
        m.put(MergeData.class, ContractJsonMixinsEvent.MergeDataMixin.class);
        m.put(QueryData.class, ContractJsonMixinsEvent.QueryDataMixin.class);
        m.put(RerankData.class, ContractJsonMixinsEvent.RerankDataMixin.class);
        m.put(RetrievalData.class, ContractJsonMixinsEvent.RetrievalDataMixin.class);
        m.put(StopData.class, ContractJsonMixinsEvent.StopDataMixin.class);
        m.put(ToolApprovalRequiredData.class, ContractJsonMixinsEvent.ToolApprovalRequiredDataMixin.class);
        m.put(ToolApprovalResolvedData.class, ContractJsonMixinsEvent.ToolApprovalResolvedDataMixin.class);
        m.put(UserMessageInjectedData.class, ContractJsonMixinsEvent.UserMessageInjectedDataMixin.class);
        return Map.copyOf(m);
    }
}
