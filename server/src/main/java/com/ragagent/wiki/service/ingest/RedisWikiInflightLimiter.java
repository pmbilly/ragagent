package com.ragagent.wiki.service.ingest;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * {@link WikiInflightLimiter} 的 <b>Redis</b> 实现（跨实例全局上限）。
 *
 * <p>键 {@code wiki:inflight:{kbID}} 的 ZSET：每个运行中的批次占一个成员
 * （token），score 是<b>租约到期时刻</b>。预留脚本一次原子完成
 * 「清过期槽位 → 计数 → 未超限则登记」，与 Go 的
 * {@code wikiInflightReserveScript} 逐行同语义。</p>
 *
 * <ul>
 *   <li><b>自愈</b>：崩溃 worker 的槽位随租约过期，被下一次预留清掉；</li>
 *   <li><b>续期</b>：本实例持有的 token 由共享调度器每
 *       {@link WikiIngestConstants#INFLIGHT_RENEW}（30 秒）ZADD 新 score
 *       （租约 {@link WikiIngestConstants#INFLIGHT_TTL} 90 秒，必须宽于续期间隔），
 *       长跑批次不会因漏一次续期丢槽位；</li>
 *   <li><b>fail-open</b>：Redis 报错 → warn + {@link Reservation#allow()}
 *       （一次 Redis 抖动不该让 wiki 生成停摆，池子大小仍兜住总工作量）。</li>
 * </ul>
 *
 * <p><b>装配</b>：普通类（<b>不是</b> {@code @Component}），由
 * {@code WikiRedisWiring} 在 {@code wiki.redis-enabled=true} 时注册
 * bean（{@code @Primary} 覆盖 {@link InProcessWikiInflightLimiter}）。</p>
 */
public class RedisWikiInflightLimiter implements WikiInflightLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisWikiInflightLimiter.class);

    /**
     * 预留脚本：清过期 → 计数 → 未超限则登记并给键续 TTL。
     * KEYS[1]=KB 的槽位集合；ARGV[1]=now 毫秒；ARGV[2]=到期毫秒；
     * ARGV[3]=maxInflight；ARGV[4]=token；ARGV[5]=ttl 毫秒。
     */
    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = new DefaultRedisScript<>(
            "local now = tonumber(ARGV[1])\n"
            + "local expiry = tonumber(ARGV[2])\n"
            + "local maxInflight = tonumber(ARGV[3])\n"
            + "local token = ARGV[4]\n"
            + "local ttl = tonumber(ARGV[5])\n"
            + "redis.call('zremrangebyscore', KEYS[1], 0, now)\n"
            + "if redis.call('zcard', KEYS[1]) >= maxInflight then\n"
            + "  return 0\n"
            + "end\n"
            + "redis.call('zadd', KEYS[1], expiry, token)\n"
            + "redis.call('pexpire', KEYS[1], ttl)\n"
            + "return 1\n",
            Long.class);

    private final StringRedisTemplate template;

    /** 本实例当前持有的槽位：kbID → token 集合（续期与释放都只看自己的账本）。 */
    private final ConcurrentHashMap<String, Set<String>> held = new ConcurrentHashMap<>();

    private final ScheduledExecutorService renewer =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "wiki-inflight-redis-renew");
                t.setDaemon(true);
                return t;
            });

    public RedisWikiInflightLimiter(StringRedisTemplate template) {
        this.template = template;
        renewer.scheduleAtFixedRate(this::renewAll,
                WikiIngestConstants.INFLIGHT_RENEW.toMillis(),
                WikiIngestConstants.INFLIGHT_RENEW.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    @Override
    public Reservation reserve(String kbId, int maxInflight) {
        if (maxInflight <= 0) {
            // 上限未配置（<= 0）→ 无条件放行的 no-op 槽位
            return Reservation.allow();
        }
        String key = WikiIngestConstants.INFLIGHT_PREFIX + kbId;
        String token = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        long expiry = now + WikiIngestConstants.INFLIGHT_TTL.toMillis();
        Long res;
        try {
            res = template.execute(RESERVE_SCRIPT, List.of(key),
                    Long.toString(now),
                    Long.toString(expiry),
                    Integer.toString(maxInflight),
                    token,
                    Long.toString(WikiIngestConstants.INFLIGHT_TTL.toMillis()));
        } catch (RuntimeException e) {
            log.warn("wiki inflight: reserve failed for KB {}: {} (running uncapped)",
                    kbId, e.toString());
            return Reservation.allow();
        }
        if (res == null || res != 1L) {
            return Reservation.deny();
        }
        held.computeIfAbsent(kbId, k -> ConcurrentHashMap.newKeySet()).add(token);
        return new Reservation(() -> release(kbId, token), true);
    }

    /** 释放槽位：先从本地账本移除（让续期停手），再 ZREM。 */
    private void release(String kbId, String token) {
        Set<String> tokens = held.get(kbId);
        if (tokens != null) {
            tokens.remove(token);
            if (tokens.isEmpty()) {
                held.remove(kbId, tokens);
            }
        }
        try {
            template.opsForZSet().remove(WikiIngestConstants.INFLIGHT_PREFIX + kbId, token);
        } catch (RuntimeException e) {
            // 释放失败只让槽位等到租约过期，不需要让调用方感知
            log.warn("wiki inflight: release failed for KB {}: {}", kbId, e.toString());
        }
    }

    /**
     * 后台续期：把本实例每个持有 token 的租约向前推。
     * 续期失败只记日志——租约过期后槽位自愈，无需调用方介入。
     */
    void renewAll() {
        long expiry = System.currentTimeMillis() + WikiIngestConstants.INFLIGHT_TTL.toMillis();
        for (var entry : held.entrySet()) {
            String key = WikiIngestConstants.INFLIGHT_PREFIX + entry.getKey();
            for (String token : entry.getValue()) {
                try {
                    template.opsForZSet().add(key, token, expiry);
                    template.expire(key, WikiIngestConstants.INFLIGHT_TTL);
                } catch (RuntimeException e) {
                    log.warn("wiki inflight: renew failed for KB {}: {}",
                            entry.getKey(), e.toString());
                }
            }
        }
    }

    /** 关停续期线程（测试收尾用）。 */
    public void shutdown() {
        renewer.shutdownNow();
    }
}
