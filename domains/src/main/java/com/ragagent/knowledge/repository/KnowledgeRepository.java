package com.ragagent.knowledge.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragagent.knowledge.domain.Knowledge;
import com.ragagent.knowledge.mapper.KnowledgeMapper;
import org.springframework.stereotype.Component;

/**
 * knowledge 行级读写（B263⑧：由 {@code service.KnowledgeService} 的直连 mapper 抽出；对应方案
 * §8.8.2 的 {@code repository/KnowledgeRepository}）。
 *
 * <p>契约（避免复活已断的环）：本层**只做数据访问**——不抛业务异常（重复冲突由 service 侧判定并
 * 抛 {@code DuplicateKnowledgeException}）、不依赖任何 service 类型；方向恒为 repository ← service。</p>
 */
@Component
public class KnowledgeRepository {

    private final KnowledgeMapper knowledgeMapper;

    public KnowledgeRepository(KnowledgeMapper knowledgeMapper) {
        this.knowledgeMapper = knowledgeMapper;
    }

    /** 同 KB 内按文件哈希找未软删的重复行（找不到返回 {@code null}）。 */
    public Knowledge findDuplicateByHash(String kbId, String fileHash) {
        return knowledgeMapper.selectOne(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getKnowledgeBaseId, kbId)
                .eq(Knowledge::getFileHash, fileHash)
                .isNull(Knowledge::getDeletedAt)
                .last("LIMIT 1"));
    }

    /** 同 KB 内按来源 URL 找未软删的重复行（找不到返回 {@code null}）。 */
    public Knowledge findDuplicateBySource(String kbId, String source) {
        return knowledgeMapper.selectOne(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getKnowledgeBaseId, kbId)
                .eq(Knowledge::getSource, source)
                .isNull(Knowledge::getDeletedAt)
                .last("LIMIT 1"));
    }

    /** 插入新行。 */
    public void insert(Knowledge k) {
        knowledgeMapper.insert(k);
    }

    /** 按主键整行更新。 */
    public void updateById(Knowledge k) {
        knowledgeMapper.updateById(k);
    }
}
