package com.ragagent.knowledge.wiki.repository.ingest;

import com.ragagent.framework.concurrent.DistributedLock;
import com.ragagent.framework.concurrent.RedisDistributedLock;
import com.ragagent.knowledge.wiki.domain.ingest.WikiIngestConstants;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link WikiFinalizeLock} 的 <b>Redis</b> 实现。
 *
 * <p>语义要点：</p>
 * <ul>
 *   <li>SetNX 取锁 —— TTL
 *       {@link WikiIngestConstants#FINALIZE_LOCK_TTL}（60 秒）；</li>
 *   <li><b>失败即 fail CLOSED</b>：SetNX 报错返回 {@link AcquireResult#FAILED}，
 *       让调用方让任务失败并重试，而不是在无锁状态下与并发 finalize 抢同一条索引页
 *       （读→改→写的丢失更新）；</li>
 *   <li>持有期间每 {@link WikiIngestConstants#FINALIZE_LOCK_RENEW}（20 秒）续期一次
 *       （{@link ScheduledExecutorService} 调度，见 {@link com.ragagent.knowledge.wiki.domain.page.WikiSlugLock} 的同类取舍）；</li>
 *   <li>释放时先停掉续期任务再 DEL 键。</li>
 * </ul>
 *
 * <p><b>装配</b>：与 {@link com.ragagent.knowledge.wiki.repository.page.RedisWikiSlugLock} 同一模式——本类是普通类
 * （<b>不是</b> {@code @Component}），由主装配会话显式注册 bean：</p>
 * <pre>{@code
 * @Bean
 * @Primary   // ⚠️ 必须：InProcessWikiFinalizeLock 是无条件 @Component
 * public WikiFinalizeLock redisWikiFinalizeLock(StringRedisTemplate template) {
 *     return new RedisWikiFinalizeLock(template);
 * }
 * }</pre>
 */
public class RedisWikiFinalizeLock implements WikiFinalizeLock {

    private static final Logger log = LoggerFactory.getLogger(RedisWikiFinalizeLock.class);

    private final StringRedisTemplate template;
    private final DistributedLock lock;
    private final Duration ttl;
    private final Duration renew;
    private final ConcurrentHashMap<String, DistributedLock.LockHandle> handles =
            new ConcurrentHashMap<>();

    /**
     * 续期线程池。守护线程，单个 finalize 的续期任务极轻（每 20 秒一次 EXPIRE），
     * 因此进程内一个共享调度器足够。
     */
    private final ScheduledExecutorService renewer =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "wiki-finalize-lock-renew");
                t.setDaemon(true);
                return t;
            });

    /** kbID → 当前持有者的续期任务（用于 release 时取消） */
    private final java.util.concurrent.ConcurrentHashMap<String, ScheduledFuture<?>> renewals =
            new ConcurrentHashMap<>();

    public RedisWikiFinalizeLock(StringRedisTemplate template) {
        this(template, WikiIngestConstants.FINALIZE_LOCK_TTL,
                WikiIngestConstants.FINALIZE_LOCK_RENEW);
    }

    public RedisWikiFinalizeLock(StringRedisTemplate template, Duration ttl, Duration renew) {
        this.template = template;
        this.lock = new RedisDistributedLock(template);
        this.ttl = ttl;
        this.renew = renew;
    }

    @Override
    public AcquireResult tryAcquire(String kbId) {
        if (kbId == null || kbId.isEmpty()) {
            return AcquireResult.BUSY;
        }
        String key = WikiFinalizeLock.lockKey(kbId);
        Optional<DistributedLock.LockHandle> handle;
        try {
            handle = lock.tryLock(key, ttl);
        } catch (RuntimeException e) {
            // fail CLOSED：无锁放行会让两个 finalize 批次排空同一批待办、重复重建索引页
            log.warn("wiki finalize: SetNX failed for KB {} (retrying)", kbId, e);
            return AcquireResult.FAILED;
        }
        if (handle.isEmpty()) {
            // 另一个 finalize 正在跑；它会排空通道并在还有行时重排。安全地 no-op。
            return AcquireResult.BUSY;
        }
        handles.put(kbId, handle.get());
        // 续期：每 renew 一次 EXPIRE，上限 ttl
        ScheduledFuture<?> task = renewer.scheduleAtFixedRate(() -> {
            try {
                template.expire(key, ttl);
            } catch (RuntimeException e) {
                log.warn("wiki finalize: lock renew failed for KB {}", kbId, e);
            }
        }, renew.toMillis(), renew.toMillis(), TimeUnit.MILLISECONDS);
        renewals.put(kbId, task);
        return AcquireResult.ACQUIRED;
    }

    @Override
    public void release(String kbId) {
        if (kbId == null || kbId.isEmpty()) {
            return;
        }
        // 先停续期，再释放锁（token 比对 DEL：TTL 到期后锁被别的 finalize 重建时，
        // 原持有者的 close 不会删掉新持有者的锁）。
        ScheduledFuture<?> task = renewals.remove(kbId);
        if (task != null) {
            task.cancel(false);
        }
        DistributedLock.LockHandle handle = handles.remove(kbId);
        if (handle == null) {
            return;
        }
        try {
            handle.close();
        } catch (RuntimeException e) {
            log.warn("wiki finalize: lock release failed for KB {}", kbId, e);
        }
    }

    /** 供测试：生成一个唯一的持有者标识（预留扩展点） */
    static String newToken() {
        return UUID.randomUUID().toString();
    }
}
