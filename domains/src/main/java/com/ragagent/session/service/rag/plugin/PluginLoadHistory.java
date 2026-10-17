package com.ragagent.session.service.rag.plugin;

import com.ragagent.contract.pipeline.PipelinePorts;
import com.ragagent.session.service.rag.ChatManage;
import com.ragagent.session.service.rag.History;
import com.ragagent.session.service.rag.PipelineCommon;
import com.ragagent.session.service.rag.PipelineEventType;
import com.ragagent.session.service.rag.PipelineLog;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LOAD_HISTORY 阶段插件：
 * MaxRounds ≤ 0 视为多轮显式关闭（跳过，不回落全局默认）；fetchCount = maxRounds*2+10。
 */
public final class PluginLoadHistory implements Plugin {

    private final PipelinePorts.MessageService messageService;

    public PluginLoadHistory(PipelinePorts.MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public PipelineEventType[] activationEvents() {
        return new PipelineEventType[] {PipelineEventType.LOAD_HISTORY};
    }

    @Override
    public PluginError onEvent(PipelineEventType eventType, ChatManage chatManage, Plugin.Chain next) {
        if (chatManage.getMaxRounds() <= 0) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("sessionId", chatManage.getSessionId());
            f.put("reason", "multi_turn_disabled");
            PipelineLog.info("LoadHistory", "skipped", f);
            return next.next();
        }
        int maxRounds = chatManage.getMaxRounds();

        Map<String, Object> in = new LinkedHashMap<>();
        in.put("sessionId", chatManage.getSessionId());
        in.put("max_rounds", maxRounds);
        PipelineLog.info("LoadHistory", "input", in);

        List<History> historyList;
        try {
            historyList = PipelineCommon.loadAndProcessHistory(messageService,
                    chatManage.getSessionId(), maxRounds, maxRounds * 2 + 10);
        } catch (RuntimeException e) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("sessionId", chatManage.getSessionId());
            f.put("error", e.getMessage());
            PipelineLog.warn("LoadHistory", "history_fetch", f);
            return next.next();
        }

        chatManage.setHistory(historyList);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sessionId", chatManage.getSessionId());
        out.put("history_rounds", historyList == null ? 0 : historyList.size());
        out.put("max_rounds", maxRounds);
        PipelineLog.info("LoadHistory", "output", out);

        return next.next();
    }
}
