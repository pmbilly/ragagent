package com.ragagent.knowledge.repository;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ragagent.knowledge.domain.Knowledge;
import com.ragagent.knowledge.mapper.KnowledgeMapper;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

/**
 * 文档解析的**行级读写**（由 {@code service.KnowledgeParseService} 抽出；对应方案
 * §8.8.2 的 {@code repository/DocumentParser}）。
 *
 * <p>解析触发/取消要读写 knowledge 行（未软删、租户内），原先这些 MyBatis 语句直接写在
 * service 里（A17"service 不得依赖具体技术"的登记债）。抽到本层后：service 侧只保留编排与
 * 状态机判定，**方向恒为 repository ← service**（本类不依赖任何 service）。</p>
 */
@Component
public class DocumentParser {

    private final KnowledgeMapper knowledgeMapper;
    private final KnowledgeRepository knowledgeRepository;

    public DocumentParser(KnowledgeMapper knowledgeMapper, KnowledgeRepository knowledgeRepository) {
        this.knowledgeMapper = knowledgeMapper;
        this.knowledgeRepository = knowledgeRepository;
    }

    /** 按 id 取租户内未软删行（软删不可见；找不到返回 {@code null}）。 */
    public Knowledge findById(long tenantId, String id) {
        // 与 KnowledgeRepository.findById 同一查询 ⇒ 委托，保持单一实现。
        return knowledgeRepository.findById(tenantId, id);
    }

    /** 落"用户取消解析"终态：状态 + 错误文案 + 子任务计数归零 + updated_at。 */
    public void markCancelled(String id, OffsetDateTime now, String message) {
        knowledgeMapper.update(null, new LambdaUpdateWrapper<Knowledge>()
                .eq(Knowledge::getId, id)
                .set(Knowledge::getParseStatus, Knowledge.PARSE_CANCELLED)
                .set(Knowledge::getErrorMessage, message)
                .set(Knowledge::getPendingSubtasksCount, 0)
                .set(Knowledge::getUpdatedAt, now));
    }
}
