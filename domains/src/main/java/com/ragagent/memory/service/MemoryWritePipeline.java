package com.ragagent.memory.service;

import com.ragagent.contract.memory.MemoryConfig;
import com.ragagent.contract.memory.MemoryKeys;
import com.ragagent.contract.memory.MemoryKinds;
import com.ragagent.memory.domain.MemoryRender;
import com.ragagent.memory.domain.MemoryText;
import com.ragagent.memory.domain.item.MemoryItem;
import com.ragagent.memory.domain.scope.MemoryScope;
import com.ragagent.memory.repository.MemoryRepository;
import com.ragagent.memory.support.MemoryConflictException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 记忆写入管线：清洗 → 脱敏 → 墓碑 → 冲突/包含去重 → 落库 → 容量 → 常驻块 → 向量。
 *
 * <p>顺序本身是语义；{@link MemoryService} 只保留包级入口与门面委托，
 * 兄弟类（catalog/insight/extraction）仍经门面调用，不直接依赖本类。</p>
 */
final class MemoryWritePipeline {

    private static final Logger log = LoggerFactory.getLogger(MemoryWritePipeline.class);

    private final MemoryRepository repo;
    private final MemoryVectorService vectorService;

    MemoryWritePipeline(MemoryRepository repo, MemoryVectorService vectorService) {
        this.repo = repo;
        this.vectorService = vectorService;
    }

    MemoryItem write(MemoryScope scope, MemoryConfig cfg, MemoryItem item) {
        return writeReplacing(scope, cfg, item, "");
    }

    MemoryItem writeReplacing(MemoryScope scope, MemoryConfig cfg, MemoryItem item, String targetId) {
        String content = MemoryText.sanitizeMemoryContent(item.getContent());
        if (content.isEmpty()) {
            throw new MemoryScopeExceptions.EmptyContent();
        }
        // 在任何东西看这条陈述之前先脱敏。记忆会被注入到之后每一轮的系统提示词里，
        // 所以一条到达存储的凭据不只是被留存，而是被反复发给模型。
        MemoryText.Redaction redaction = MemoryText.redactSensitive(content);
        if (redaction.changed()) {
            if (MemoryText.isMostlyRedacted(redaction.content())) {
                log.info("memory: dropped a statement that was mostly sensitive material");
                throw new MemoryScopeExceptions.SensitiveContent();
            }
            log.info("memory: redacted sensitive material before storing");
            content = MemoryText.sanitizeMemoryContent(redaction.content());
        }
        if (!MemoryKinds.isValid(item.getKind())) {
            item.setKind(MemoryKinds.KIND_FACT);
        }

        // 用户刻意忘掉的东西，不能在蒸馏下次读到那条消息时回来。两次检查，因为重新推导出来的
        // 陈述通常措辞略有不同、哈希对不上：精确指纹，以及"它来自的那条消息曾经产出过
        // 一条被用户拒绝的记忆"。
        boolean forgotten = repo.hasTombstone(scope, MemoryText.fingerprint(content));
        if (!forgotten && !item.getSourceMessageId().isEmpty()
                && MemoryKinds.ORIGIN_EXTRACTED.equals(item.getOrigin())) {
            // 只有后台路径被这样拦。显式的"记住这个"是用户在**再次**要求，永远该赢。
            forgotten = repo.hasTombstoneForMessage(scope, item.getSourceMessageId(),
                    MemoryService.REJECTED_MESSAGE_WINDOW);
        }
        if (forgotten) {
            log.info("memory: skipped a statement the user previously deleted");
            throw new MemoryScopeExceptions.PreviouslyForgotten();
        }
        repo.ensureSubject(scope);

        String topic = MemoryText.sanitizeMemoryTopic(item.getTopic());
        String normalizedKey = MemoryKeys.itemKey(topic, content);
        MemoryItem existing;
        if (targetId != null && !targetId.isEmpty()) {
            existing = repo.getItem(scope, targetId);
            if (existing == null) {
                throw new MemoryConflictException();
            }
        } else {
            existing = repo.findActiveByKey(scope, normalizedKey);
        }
        if (existing != null && MemoryKinds.STATUS_ACTIVE.equals(existing.getStatus())
                && MemoryText.sanitizeMemoryContent(existing.getContent()).equals(content)) {
            // 同一个主题上的同一句话：什么都没变，所以保留原来的时间戳，
            // 而不是每一轮都把这一行翻搅一遍。
            return existing;
        }
        if (existing == null) {
            // 同一个事实常常到两次：一次因为用户说了"记住…"，又一次来自后台蒸馏，
            // 措辞略有不同（"我们的生产库是 X" vs "生产库是 X"）。它们拿到不同的主题
            // key，所以只靠 key 匹配会让两条都进来，用户就看到自己的记忆重复了。
            ContainedDuplicate duplicate = findContainedDuplicate(scope, item.getKind(), content);
            MemoryItem candidate = duplicate.item();
            if (candidate != null && !duplicate.longer()
                    && (MemoryKinds.STATUS_ACTIVE.equals(candidate.getStatus())
                    || MemoryKinds.STATUS_PENDING.equals(statusForWrite(item)))) {
                return candidate;
            }
            // 新陈述包住了旧的，让它取代。
            existing = candidate;
        }

        MemoryItem stored = new MemoryItem();
        stored.setId(UUID.randomUUID().toString());
        stored.setTenantId(scope.tenantId());
        stored.setSubjectId(scope.subjectId());
        stored.setKind(item.getKind());
        stored.setContent(content);
        stored.setTopic(topic);
        stored.setNormalizedKey(normalizedKey);
        stored.setImportance(MemoryText.clampImportance(item.getImportance()));
        stored.setOrigin(item.getOrigin());
        stored.setStatus(statusForWrite(item));
        stored.setSourceSessionId(item.getSourceSessionId());
        stored.setSourceMessageId(item.getSourceMessageId());
        stored.setValidFrom(OffsetDateTime.now());
        stored.setExpiresAt(item.getExpiresAt());
        if (stored.getOrigin().isEmpty()) {
            stored.setOrigin(MemoryKinds.ORIGIN_EXTRACTED);
        }
        if (existing != null) {
            targetId = existing.getId();
        }
        repo.saveItem(scope, stored, targetId);

        enforceCapacity(scope, cfg);
        rebuildBlock(scope);
        // 一条没有向量的记忆对语义召回是不可见的，所以这跑在**每一次**写入上。
        // 尽力而为：嵌入失败不能让写入失败，补扫会捡起漏掉的。
        vectorService.storeItemEmbedding(scope, cfg, stored);
        return stored;
    }

    /** {@link #findContainedDuplicate} 的结果。 */
    private record ContainedDuplicate(MemoryItem item, boolean longer) {
    }

    /**
     * 在同 kind 的**活着**的记忆里找一条，
     * 其陈述包含（或被包含于）新来的这条。
     *
     * <p>包含是刻意的全部规则。它便宜、对一个读自己记忆列表的用户可解释，
     * 而且它不会把两条仅仅同主题的陈述合并——只合并"短的那条没有说出长的没说的东西"
     * 这种情形。返回的 bool 报告新陈述是不是两者中更长的那条。</p>
     */
    private ContainedDuplicate findContainedDuplicate(MemoryScope scope, String kind, String content) {
        List<MemoryItem> candidates = repo.listLive(scope, kind, 200);
        String normalized = MemoryText.normalizeMemoryForMatch(content);
        if (normalized.isEmpty()) {
            return new ContainedDuplicate(null, false);
        }
        if (candidates == null) {
            return new ContainedDuplicate(null, false);
        }
        for (MemoryItem candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String existing = MemoryText.normalizeMemoryForMatch(candidate.getContent());
            if (existing.isEmpty()) {
                continue;
            }
            if (existing.contains(normalized)) {
                return new ContainedDuplicate(candidate, false);
            }
            if (normalized.contains(existing)) {
                return new ContainedDuplicate(candidate, true);
            }
        }
        return new ContainedDuplicate(null, false);
    }

    /**
     * 一条记忆是立刻生效还是等用户确认。
     *
     * <p>用户**说**的东西立刻生效。系统**猜**的关于他的东西（他的角色、他的领域，
     * 从提问里推断出来的）则提出来等他确认。推断既是价值所在也是伤害所在：
     * 一个被悄悄当成事实的错误猜测，是记忆功能彻底失去信任的方式；
     * 而且与 ChatGPT 的后台层不同，这一层始终可审计。</p>
     */
    static String statusForWrite(MemoryItem item) {
        if (item.isInferred()
                && !MemoryKinds.ORIGIN_EXPLICIT.equals(item.getOrigin())
                && !MemoryKinds.ORIGIN_MANUAL.equals(item.getOrigin())) {
            return MemoryKinds.STATUS_PENDING;
        }
        return MemoryKinds.STATUS_ACTIVE;
    }

    /**
     * 主体超过上限后归档排名最低的条目。
     * 这是系统里**唯一**的自动遗忘。
     */
    void enforceCapacity(MemoryScope scope, MemoryConfig cfg) {
        int maxItems = cfg.effectiveMaxItems();
        long count;
        try {
            count = repo.countActive(scope);
        } catch (RuntimeException e) {
            log.warn("memory: count active failed", e);
            return;
        }
        if (count <= maxItems) {
            return;
        }
        long archived;
        try {
            archived = repo.archiveLowestRanked(scope, maxItems);
        } catch (RuntimeException e) {
            log.warn("memory: archive overflow failed", e);
            return;
        }
        log.info("memory: archived {} items over the {} cap", archived, maxItems);
    }

    /**
     * 重新渲染常驻块，让读路径始终只是一次主键查找。
     * 每次变更之后都会调用。
     */
    void rebuildBlock(MemoryScope scope) {
        List<MemoryItem> items;
        try {
            items = repo.listActiveResident(scope, 60);
        } catch (RuntimeException e) {
            log.warn("memory: rebuild block load failed", e);
            return;
        }
        long count;
        try {
            count = repo.countActive(scope);
        } catch (RuntimeException e) {
            log.warn("memory: rebuild block count failed", e);
            return;
        }
        String block = MemoryRender.renderMemoryBlock(items);
        try {
            repo.updateSubjectBlock(scope, block, (int) count);
        } catch (RuntimeException e) {
            log.warn("memory: rebuild block store failed", e);
        }
    }
}
