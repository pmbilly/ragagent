package com.ragagent.knowledge.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragagent.knowledge.domain.UserKbPin;
import com.ragagent.knowledge.mapper.UserKbPinMapper;
import org.springframework.stereotype.Component;

/**
 * user_kb_pin 行级读写（由 {@code service.KnowledgeBaseService} 的直连 pinMapper 抽出）。
 *
 * <p>方案 的 repository 面未给这张表列类名，本类按本批既有命名（表 → XxxRepository）落位；
 * 契约同其它仓储：只做数据访问、不抛业务异常、不依赖 service。</p>
 */
@Component
public class UserKbPinRepository {

    private final UserKbPinMapper pinMapper;

    public UserKbPinRepository(UserKbPinMapper pinMapper) {
        this.pinMapper = pinMapper;
    }

    /** per-(tenant,user,kb) 取置顶行（找不到返回 {@code null}）。 */
    public UserKbPin findByUserAndKb(long tenantId, String userId, String knowledgeBaseId) {
        return pinMapper.selectOne(new LambdaQueryWrapper<UserKbPin>()
                .eq(UserKbPin::getTenantId, tenantId)
                .eq(UserKbPin::getUserId, userId)
                .eq(UserKbPin::getKnowledgeBaseId, knowledgeBaseId)
                .last("LIMIT 1"));
    }

    /** 删除 per-(tenant,user,kb) 置顶行（幂等：无行则 0 行） */
    public void deleteByUserAndKb(long tenantId, String userId, String knowledgeBaseId) {
        pinMapper.delete(new LambdaQueryWrapper<UserKbPin>()
                .eq(UserKbPin::getTenantId, tenantId)
                .eq(UserKbPin::getUserId, userId)
                .eq(UserKbPin::getKnowledgeBaseId, knowledgeBaseId));
    }

    /** 删库时清理该 KB 的全部置顶行。 */
    public void deleteByKnowledgeBaseId(String knowledgeBaseId) {
        pinMapper.delete(new LambdaQueryWrapper<UserKbPin>()
                .eq(UserKbPin::getKnowledgeBaseId, knowledgeBaseId));
    }

    /** 插入置顶行。 */
    public void insert(UserKbPin pin) {
        pinMapper.insert(pin);
    }
}
