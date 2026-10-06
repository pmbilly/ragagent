package com.ragagent.wiki.service.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link WikiDeletedTombstoneStore} 的 <b>Redis</b> 实现（跨实例可见的墓碑快路径）。
 *
 * <p>{@code wiki.redis-enabled=true} 时由 {@code WikiRedisWiring} 装配
 * （{@code @Primary} 覆盖 {@link InProcessWikiDeletedTombstoneStore}）。键直接复用
 * {@link WikiIngestConstants#deletedTombstoneKey}（{@code wiki:deleted:{kbID}:{knowledgeID}}），
 * TTL 与进程内实现同源（{@link WikiIngestConstants#DELETED_TTL}）。</p>
 *
 * <p><b>故障策略：全部 best-effort（吞 + warn）</b>——墓碑只是快路径，不是唯一防线：
 * {@code isKnowledgeGone} 在墓碑未命中时<b>会回落到数据库查询</b>。
 * 因此 {@code exists} 故障返回 false（方向安全：多查一次库），
 * 写入/清除故障只记日志（对结果无影响）。</p>
 */
public class RedisWikiDeletedTombstoneStore implements WikiDeletedTombstoneStore {

    private static final Logger log = LoggerFactory.getLogger(RedisWikiDeletedTombstoneStore.class);

    private final StringRedisTemplate template;

    public RedisWikiDeletedTombstoneStore(StringRedisTemplate template) {
        this.template = template;
    }

    @Override
    public boolean exists(String kbId, String knowledgeId) {
        try {
            return Boolean.TRUE.equals(
                    template.hasKey(WikiIngestConstants.deletedTombstoneKey(kbId, knowledgeId)));
        } catch (RuntimeException e) {
            // 方向安全：当作未命中，回落 DB 查询
            log.warn("wiki tombstones: exists check failed: {} (falling back to DB)", e.toString());
            return false;
        }
    }

    @Override
    public void markDeleted(String kbId, String knowledgeId) {
        try {
            template.opsForValue().set(WikiIngestConstants.deletedTombstoneKey(kbId, knowledgeId),
                    "1", WikiIngestConstants.DELETED_TTL);
        } catch (RuntimeException e) {
            // 墓碑没写上只是少一次快路径，正确性靠 DB 回落
            log.warn("wiki tombstones: mark deleted failed: {}", e.toString());
        }
    }

    @Override
    public void clear(String kbId, String knowledgeId) {
        try {
            template.delete(WikiIngestConstants.deletedTombstoneKey(kbId, knowledgeId));
        } catch (RuntimeException e) {
            log.warn("wiki tombstones: clear failed: {}", e.toString());
        }
    }
}
