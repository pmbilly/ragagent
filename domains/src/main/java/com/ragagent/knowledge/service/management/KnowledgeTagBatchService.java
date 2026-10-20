package com.ragagent.knowledge.service.management;

import com.ragagent.contract.error.BizException;
import com.ragagent.knowledge.domain.Knowledge;
import com.ragagent.knowledge.domain.KnowledgeTag;
import com.ragagent.knowledge.repository.KnowledgeTagRepository;
import com.ragagent.knowledge.service.KnowledgeAccessHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识库标签批量更新：一次授权范围校验、一次标签读取、逐条删旧插新。
 *
 * <p>从 {@link KnowledgeService} 抽出；事务边界留在本方法（门面委托不再叠一层）。
 * {@code authorizedKBID} 为空表示未显式给 kb_id，由首条 knowledge 推导授权范围。</p>
 */
@Service
public class KnowledgeTagBatchService {

    private final KnowledgeAccessHelper access;
    private final KnowledgeFolderService folderService;
    private final KnowledgeTagRepository tagRepository;

    public KnowledgeTagBatchService(KnowledgeAccessHelper access,
                                    KnowledgeFolderService folderService,
                                    KnowledgeTagRepository tagRepository) {
        this.access = access;
        this.folderService = folderService;
        this.tagRepository = tagRepository;
    }

    @Transactional
    public void updateTagBatch(String authorizedKBID, Map<String, List<String>> updates) {
        if (updates == null || updates.isEmpty()) {
            return;
        }
        List<String> knowledgeIDs = new ArrayList<>(updates.keySet());
        knowledgeIDs.sort(String::compareTo);
        // 授权 KB = 显式 kb_id；无 kb_id 时 = 首条（排序后最靠前的）knowledge 所属 KB
        String grantedKbId = authorizedKBID;
        if (grantedKbId == null || grantedKbId.isEmpty()) {
            Knowledge first = access.getKnowledge(knowledgeIDs.get(0));
            grantedKbId = first.getKnowledgeBaseId();
        }
        List<Knowledge> knowledgeList = folderService.loadKnowledgeWriteBatch(knowledgeIDs, grantedKbId);
        long tenantId = knowledgeList.get(0).getTenantId();

        if (authorizedKBID != null && !authorizedKBID.isEmpty()) {
            if (knowledgeList.size() != updates.size()) {
                throw BizException.forbidden("some knowledge IDs are not accessible in the authorized scope");
            }
            for (Knowledge k : knowledgeList) {
                if (!k.getKnowledgeBaseId().equals(authorizedKBID)) {
                    throw BizException.forbidden("knowledge " + k.getId()
                            + " does not belong to authorized knowledge base");
                }
            }
        }
        // 收集 + 校验标签
        Set<String> tagIDSet = new TreeSet<>();
        for (List<String> tagIDs : updates.values()) {
            for (String tagID : tagIDs) {
                if (tagID != null && !tagID.isEmpty()) {
                    tagIDSet.add(tagID);
                }
            }
        }
        Map<String, KnowledgeTag> tagMap = new HashMap<>();
        if (!tagIDSet.isEmpty()) {
            List<KnowledgeTag> tags = tagRepository.listTagsByIds(tenantId, List.copyOf(tagIDSet));
            for (KnowledgeTag tag : tags) {
                tagMap.put(tag.getId(), tag);
            }
        }
        for (Knowledge k : knowledgeList) {
            List<String> tagIDs = updates.get(k.getId());
            if (tagIDs == null) {
                continue;
            }
            for (String tagID : tagIDs) {
                if (tagID == null || tagID.isEmpty()) {
                    continue;
                }
                KnowledgeTag tag = tagMap.get(tagID);
                if (tag == null) {
                    throw BizException.badRequest("标签 " + tagID + " 不存在");
                }
                if (tag.getTenantId() == null || tag.getTenantId() != tenantId
                        || !tag.getKnowledgeBaseId().equals(k.getKnowledgeBaseId())) {
                    throw BizException.badRequest("标签 " + tagID + " 不属于知识库 " + k.getKnowledgeBaseId());
                }
            }
        }
        for (String knowledgeID : knowledgeIDs) {
            setKnowledgeTags(knowledgeID, updates.get(knowledgeID));
        }
    }

    /** 删旧 + 插新（空/重复 id 跳过）。 */
    private void setKnowledgeTags(String knowledgeId, List<String> tagIDs) {
        tagRepository.deleteRelations(knowledgeId);
        if (tagIDs == null || tagIDs.isEmpty()) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String tagID : tagIDs) {
            if (tagID != null && !tagID.isEmpty() && seen.add(tagID)) {
                tagRepository.insertRelation(knowledgeId, tagID);
            }
        }
    }
}
