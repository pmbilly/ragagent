package com.ragagent.knowledge.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.ragagent.knowledge.service.ChunkExtractService;
import com.ragagent.knowledge.service.QuestionGenerationService;

/**
 * knowledge 域的 Redis 面装配（多实例部署）。
 *
 * <p>{@code knowledge.redis-enabled=true} 时把四个面切成跨实例实现：</p>
 * <ul>
 *   <li>{@link ChunkExtractTaskQueue} → {@link RedisChunkExtractTaskQueue}；</li>
 *   <li>{@link QuestionGenerationTaskQueue} → {@link RedisQuestionGenerationTaskQueue}；</li>
 *   <li>{@link FaqImportTaskStore} → {@link RedisFaqImportTaskStore}
 *       （导入进度跨实例可见 + "同一知识库已有导入任务"的拦截跨实例成立）；</li>
 *   <li>{@link KnowledgeTaskProgressStore} → {@link RedisKnowledgeTaskProgressStore}
 *       （move/clone 进度跨实例可见，轮询路由到任意副本都读得到）。</li>
 * </ul>
 *
 * <p><b>@Primary 必须</b>：四个 InProcess 实现都是无条件 {@code @Component}，
 * 共存时裸注入接口会 {@code NoUniqueBeanDefinitionException}——Redis 版以
 * {@code @Primary} 胜出。开关关闭时本类整体不生效（单实例语义与接线前一致）；
 * 开关打开但 Redis 连不上时<b>启动失败</b>（不静默退化，与 im/wiki 的开关同口径）。</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "knowledge", name = "redis-enabled", havingValue = "true")
public class KnowledgeRedisWiring {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRedisWiring.class);

    /** 启动即验：配置成 Redis 却连不上时不静默退化。 */
    public KnowledgeRedisWiring(StringRedisTemplate template) {
        try (var connection = template.getConnectionFactory().getConnection()) {
            connection.ping();
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "knowledge.redis-enabled=true but Redis is not reachable: " + e.getMessage(), e);
        }
        log.info("[KnowledgeRedis] knowledge Redis 面已装配（多实例模式）："
                + "chunk extract / question generation 队列 + FAQ 导入状态 + move/clone 进度");
    }

    /** 跨实例共享的分块抽取队列。 */
    @Bean
    @Primary
    public ChunkExtractTaskQueue redisChunkExtractTaskQueue(StringRedisTemplate template,
            ChunkExtractService service) {
        return new RedisChunkExtractTaskQueue(template, service);
    }

    /** 跨实例共享的问题生成队列。 */
    @Bean
    @Primary
    public QuestionGenerationTaskQueue redisQuestionGenerationTaskQueue(StringRedisTemplate template,
            QuestionGenerationService service) {
        return new RedisQuestionGenerationTaskQueue(template, service);
    }

    /** 跨实例共享的 FAQ 导入进度与锁。 */
    @Bean
    @Primary
    public FaqImportTaskStore redisFaqImportTaskStore(StringRedisTemplate template) {
        return new RedisFaqImportTaskStore(template);
    }

    /** 跨实例共享的 move/clone 进度。 */
    @Bean
    @Primary
    public KnowledgeTaskProgressStore redisKnowledgeTaskProgressStore(StringRedisTemplate template) {
        return new RedisKnowledgeTaskProgressStore(template);
    }
}
