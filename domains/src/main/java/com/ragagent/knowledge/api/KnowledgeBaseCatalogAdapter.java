package com.ragagent.knowledge.api;

import com.ragagent.contract.knowledge.KnowledgeBaseCatalogPort;
import com.ragagent.knowledge.domain.KnowledgeBase;
import com.ragagent.knowledge.service.management.KnowledgeBaseService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import com.ragagent.common.knowledge.KnowledgeBaseRowView;
import com.ragagent.knowledge.support.KnowledgeBaseRowViews;

/**
 * {@link KnowledgeBaseCatalogPort} 的 knowledge 侧实现。
 *
 * <p>域内经 {@code api/} 适配器对外暴露是既定模式（同 {@link ChunkPortAdapter}）： 规则禁的是
 * {@code agenttools}/{@code agentmanagement} **直连**{@code *.service}，而本类在域内、由端口解耦。</p>
 *
 * <p>语义逐字保留：直接委托 {@code KnowledgeBaseService.listKnowledgeBases(creator)}
 * （含能力默认值回填 {@code ensureDefaults}），只做投影。</p>
 */
@Component
public class KnowledgeBaseCatalogAdapter implements KnowledgeBaseCatalogPort {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseCatalogAdapter(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Override
    public List<KbCapability> listActiveCapabilities(String creator) {
        List<KnowledgeBase> rows = knowledgeBaseService.listKnowledgeBases(creator);
        List<KbCapability> out = new ArrayList<>(rows.size());
        for (KnowledgeBase kb : rows) {
            KnowledgeBaseRowView.Capabilities caps = KnowledgeBaseRowViews.rowView(kb).capabilities();
            out.add(new KbCapability(kb.getId(), caps.vector(), caps.keyword()));
        }
        return out;
    }
}
