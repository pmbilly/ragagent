package com.ragagent.wiki;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.ragagent.wiki.service.ingest.RedisWikiDeletedTombstoneStore;
import com.ragagent.wiki.service.ingest.RedisWikiFinalizeLock;
import com.ragagent.wiki.service.ingest.RedisWikiIdentityClaimStore;
import com.ragagent.wiki.service.ingest.RedisWikiInflightLimiter;
import com.ragagent.wiki.service.ingest.RedisWikiIngestTaskQueue;
import com.ragagent.wiki.service.ingest.WikiDeletedTombstoneStore;
import com.ragagent.wiki.service.ingest.WikiFinalizeLock;
import com.ragagent.wiki.service.ingest.WikiIdentityClaimStore;
import com.ragagent.wiki.service.ingest.WikiInflightLimiter;
import com.ragagent.wiki.service.ingest.WikiIngestService;
import com.ragagent.wiki.service.ingest.WikiIngestTaskHandler;
import com.ragagent.wiki.service.ingest.WikiIngestTaskQueue;
import com.ragagent.wiki.service.page.RedisWikiSlugLock;
import com.ragagent.wiki.service.page.WikiSlugLock;

/**
 * wiki 域的 Redis 面装配（多实例部署）。
 *
 * <p><b>开关</b>：{@code wiki.redis-enabled=true} 时把 wiki 的六个协调端口
 * 切成跨实例实现（各接口注释里"多副本部署需要换 Redis 实现"的那几处）：</p>
 * <ul>
 *   <li>{@link WikiSlugLock} → {@link RedisWikiSlugLock}（同 slug 读-改-写互斥）；</li>
 *   <li>{@link WikiFinalizeLock} → {@link RedisWikiFinalizeLock}（KB 级 finalize 互斥）；</li>
 *   <li>{@link WikiIdentityClaimStore} → {@link RedisWikiIdentityClaimStore}
 *       （身份认领——失效直接表现为"同标题建出两个页面"）；</li>
 *   <li>{@link WikiInflightLimiter} → {@link RedisWikiInflightLimiter}
 *       （按 KB 在途批次的全局上限）；</li>
 *   <li>{@link WikiIngestTaskQueue} → {@link RedisWikiIngestTaskQueue}
 *       （跨实例共享的任务表：TaskID 全局合并 + 崩溃回收重投）；</li>
 *   <li>{@link WikiDeletedTombstoneStore} → {@link RedisWikiDeletedTombstoneStore}
 *       （删除墓碑跨实例可见：删除后其它副本的在途任务也走快路径，少一次库查询）。</li>
 * </ul>
 *
 * <p><b>@Primary 必须</b>：六个 InProcess 实现都是无条件 {@code @Component}，
 * 共存时裸注入接口会 {@code NoUniqueBeanDefinitionException}——Redis 版以
 * {@code @Primary} 胜出。开关关闭时本类整体不生效，InProcess 实现是唯一实现。</p>
 *
 * <p><b>单实例部署</b>：保持缺省（不配开关）即全部走进程内实现，语义与
 * 接线前完全一致；开关打开时若 Redis 连不上则<b>启动失败</b>（不静默退化，
 * 与 {@code im.redis-enabled} 同口径）。</p>
 *
 * <p><b>与批次入口的联动</b>：本开关打开后在途限流器即 Redis 版，
 * {@code WikiIngestService.isLiteMode()} 随之翻转为 false——批次入口进入
 * Standard 路径（不做按 KB 独占、认领式拉行），这正是分布式协调的预期形态。
 * 任务队列同时换成 Redis 版后 {@code isQueueInProcess()} 变 false——
 * 启动重放（{@code WikiPendingOpReplayer}）自动关闭（持久队列自带重投），
 * 判据与锁族开关解耦。</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "wiki", name = "redis-enabled", havingValue = "true")
public class WikiRedisWiring {

    private static final Logger log = LoggerFactory.getLogger(WikiRedisWiring.class);

    /**
     * 启动即验：配置成 Redis 却连不上时不静默退化。
     */
    public WikiRedisWiring(StringRedisTemplate template) {
        try (var connection = template.getConnectionFactory().getConnection()) {
            connection.ping();
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "wiki.redis-enabled=true but Redis is not reachable: " + e.getMessage(), e);
        }
        log.info("[WikiRedis] wiki Redis 面已装配（多实例模式）："
                + "slug 锁 / finalize 锁 / 身份认领 / 在途限流器 全部走 Redis");
    }

    /** 同 slug 读-改-写互斥（5 分钟 TTL、50ms 轮询、fail-open）。 */
    @Bean
    @Primary
    public WikiSlugLock redisWikiSlugLock(StringRedisTemplate template) {
        return new RedisWikiSlugLock(template);
    }

    /** KB 级 finalize 互斥（60 秒 TTL、20 秒续期、fail-closed）。 */
    @Bean
    @Primary
    public WikiFinalizeLock redisWikiFinalizeLock(StringRedisTemplate template) {
        return new RedisWikiFinalizeLock(template);
    }

    /** 归一化身份的 slug 认领（2 小时 TTL；Redis 报错由调用方回落批次局部 map）。 */
    @Bean
    @Primary
    public WikiIdentityClaimStore redisWikiIdentityClaimStore(StringRedisTemplate template) {
        return new RedisWikiIdentityClaimStore(template);
    }

    /** 按 KB 在途批次的全局上限（90 秒租约、30 秒续期、fail-open）。 */
    @Bean
    @Primary
    public WikiInflightLimiter redisWikiInflightLimiter(StringRedisTemplate template) {
        return new RedisWikiInflightLimiter(template);
    }

    /** 跨实例共享的任务队列（替换进程内队列：TaskID 全局合并 + 崩溃回收重投）。 */
    @Bean
    @Primary
    public WikiIngestTaskQueue redisWikiIngestTaskQueue(StringRedisTemplate template,
            ObjectProvider<WikiIngestTaskHandler> handlerProvider,
            ObjectProvider<com.ragagent.wiki.mapper.TaskDeadLetterRepository> deadLetterProvider,
            ObjectProvider<WikiIngestService> ingestServiceProvider) {
        return new RedisWikiIngestTaskQueue(template, handlerProvider, deadLetterProvider,
                ingestServiceProvider);
    }

    /** 跨实例可见的删除墓碑（快路径；正确性不依赖它，DB 回落仍在）。 */
    @Bean
    @Primary
    public WikiDeletedTombstoneStore redisWikiDeletedTombstoneStore(StringRedisTemplate template) {
        return new RedisWikiDeletedTombstoneStore(template);
    }
}
