package com.ragagent.knowledge.repository;

import java.time.OffsetDateTime;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

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

    /** 分页查询（条件由调用方以 wrapper 传入：查询构建仍在用例侧，本层只执行）。 */
    public Page<Knowledge> selectPage(
            Page<Knowledge> page,
            LambdaQueryWrapper<Knowledge> qw) {
        return knowledgeMapper.selectPage(page, qw);
    }

    /** 仅按 id 取未软删行（无租户过滤；守卫/权限解析用）。 */
    public Knowledge findByIdOnly(String id) {
        return knowledgeMapper.selectOne(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getId, id)
                .isNull(Knowledge::getDeletedAt)
                .last("LIMIT 1"));
    }

    /** 软删 knowledge 行（置 deleted_at）。 */
    public void softDelete(String id, OffsetDateTime now) {
        knowledgeMapper.update(null, new LambdaUpdateWrapper<Knowledge>()
                .eq(Knowledge::getId, id).set(Knowledge::getDeletedAt, now));
    }

    /** 按主键整行更新。 */
    public void updateById(Knowledge k) {
        knowledgeMapper.updateById(k);
    }
}
