package com.ragagent.wiki.service.ingest;

import java.util.Collections;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * {@link WikiIdentityClaimStore} 的 <b>Redis</b> 实现（跨实例）。
 *
 * <p>键 {@code wiki:identity:{kbID}:{pageType}:{identity}}，TTL
 * {@link WikiIngestConstants#IDENTITY_CLAIM_TTL}（2 小时）。裁决语义与 Lua
 * 脚本照接口注释：authoritative 无条件覆盖；既有且前缀匹配的值获胜并续期；
 * 缺失/损坏/前缀不符的值被提议值替换。</p>
 *
 * <p><b>失败行为</b>：Redis 报错时<b>抛 RuntimeException</b>——调用方
 * {@code WikiIngestDedupService.claimWikiIdentitySlug} 有既定回落链：
 * catch + warn + 退回批次局部 map（Redis 异常时不阻断摄取）。
 * 本类刻意不做静默 fail-open，否则"Redis 抖一下
 * 就悄悄放弃跨批次收敛"会无声地放大「同标题建两页」窗口。</p>
 *
 * <p><b>装配</b>：本类是普通类（<b>不是</b> {@code @Component}），由
 * {@code WikiRedisWiring} 在 {@code wiki.redis-enabled=true} 时注册
 * bean（{@code @Primary} 覆盖 {@link InProcessWikiIdentityClaimStore}）。</p>
 */
public class RedisWikiIdentityClaimStore implements WikiIdentityClaimStore {

    /**
     * 认领脚本：authoritative 直接覆盖；既有且前缀匹配的值获胜并续期；
     * 其余（缺失/损坏/前缀不符）用提议值替换。
     * KEYS[1]=认领键；ARGV[1]=proposedSlug；ARGV[2]=ttl 秒；
     * ARGV[3]=authoritative（"1"/"0"）；ARGV[4]=requiredPrefix。
     */
    private static final DefaultRedisScript<String> CLAIM_SCRIPT = new DefaultRedisScript<>(
            "local proposed = ARGV[1]\n"
            + "local ttl = tonumber(ARGV[2])\n"
            + "local authoritative = ARGV[3]\n"
            + "local prefix = ARGV[4]\n"
            + "if authoritative == '1' then\n"
            + "  redis.call('set', KEYS[1], proposed, 'ex', ttl)\n"
            + "  return proposed\n"
            + "end\n"
            + "local existing = redis.call('get', KEYS[1])\n"
            + "if type(existing) == 'string' and string.sub(existing, 1, string.len(prefix)) == prefix then\n"
            + "  redis.call('expire', KEYS[1], ttl)\n"
            + "  return existing\n"
            + "end\n"
            + "redis.call('set', KEYS[1], proposed, 'ex', ttl)\n"
            + "return proposed\n",
            String.class);

    private final StringRedisTemplate template;

    public RedisWikiIdentityClaimStore(StringRedisTemplate template) {
        this.template = template;
    }

    @Override
    public String claim(String kbId, String pageType, String identity,
                        String proposedSlug, boolean authoritative, String requiredPrefix) {
        String key = WikiIngestConstants.identityClaimKey(kbId, pageType, identity);
        // 防御性钳位：TTL 至少 1 秒
        long ttlSeconds = Math.max(1, WikiIngestConstants.IDENTITY_CLAIM_TTL.toSeconds());
        String prefix = requiredPrefix == null ? "" : requiredPrefix;
        String result = template.execute(CLAIM_SCRIPT, Collections.singletonList(key),
                proposedSlug, Long.toString(ttlSeconds), authoritative ? "1" : "0", prefix);
        // 脚本只会返回 proposed 或前缀匹配的 existing；null（键在脚本执行外被删的竞态）
        // 回落为提议值。
        return result == null ? proposedSlug : result;
    }

    @Override
    public void release(String kbId, String pageType, String identity) {
        template.delete(WikiIngestConstants.identityClaimKey(kbId, pageType, identity));
    }
}
