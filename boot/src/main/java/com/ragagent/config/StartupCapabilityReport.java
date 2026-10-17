package com.ragagent.config;

import com.ragagent.auth.config.JwtProperties;
import com.ragagent.platform.config.RetrievalDriverProperties;
import com.ragagent.platform.stream.StreamProperties;
import com.ragagent.retrieval.engine.EngineRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 起服能力报告：把检索、JWT、存储、Redis 与流管理器的实际状态打成一行，并在
 * 存储不可写时直接失败。
 *
 * <p>这些配置的缺省值大多是"静默降级"（检索关闭、JWT 随机、存储不可写），
 * 新人拿到的是"活着但什么都不能干"的系统。报告不改变行为，只让降级可见。</p>
 */
@Component
@ConditionalOnProperty(name = "ragagent.capability-report.enabled",
        havingValue = "true", matchIfMissing = true)
public class StartupCapabilityReport implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(StartupCapabilityReport.class);

    private final RetrievalDriverProperties retrieval;
    private final EngineRegistry engineRegistry;
    private final JwtProperties jwt;
    private final StreamProperties stream;
    private final Environment environment;
    private final String storageBaseDir;

    public StartupCapabilityReport(
            RetrievalDriverProperties retrieval,
            EngineRegistry engineRegistry,
            JwtProperties jwt,
            StreamProperties stream,
            Environment environment,
            @Value("${weknora.storage.local-base-dir:${LOCAL_STORAGE_BASE_DIR:${user.home}/ragagent-data/files}}")
                    String storageBaseDir) {
        this.retrieval = retrieval;
        this.engineRegistry = engineRegistry;
        this.jwt = jwt;
        this.stream = stream;
        this.environment = environment;
        this.storageBaseDir = storageBaseDir;
    }

    @Override
    public void afterPropertiesSet() {
        String driver = retrieval.driver();
        boolean retrievalConfigured = hasText(driver);
        boolean jwtConfigured = hasText(jwt.secret());
        int engines = engineRegistry.getAllRetrieveEngineServices().size();

        verifyStorageWritable(storageBaseDir);

        if (!retrievalConfigured) {
            log.warn("[capabilities] RETRIEVE_DRIVER 为空：检索引擎关闭，问答不会带知识库上下文");
        } else if (engines == 0) {
            log.warn("[capabilities] RETRIEVE_DRIVER={} 但未注册任何引擎：检查引擎配置与依赖", driver);
        }
        if (!jwtConfigured) {
            log.warn("[capabilities] JWT_SECRET 未设置：本次进程随机生成，重启后所有登录态失效");
        }

        log.info("[capabilities] retrieval={} engines={} | jwt={} | storage={} | redis={} | stream={}",
                retrievalConfigured ? driver : "OFF",
                engines,
                jwtConfigured ? "env" : "RANDOM",
                Path.of(storageBaseDir).toAbsolutePath().normalize(),
                redisTarget(),
                stream.useRedis() ? "redis" : "memory");
    }

    /** 建目录 + 写探针；失败即抛，应用不启动（比"上传时才炸"早得多）。 */
    static void verifyStorageWritable(String baseDir) {
        Path dir;
        try {
            dir = Path.of(baseDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path probe = Files.createTempFile(dir, ".write-probe", ".tmp");
            Files.deleteIfExists(probe);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(
                    "本地存储目录不可写: " + baseDir + "（检查 LOCAL_STORAGE_BASE_DIR）", e);
        }
    }

    /** 只探 TCP 可达性，不做 AUTH（密码错由 scripts/doctor.sh 在起服前抓）。 */
    private String redisTarget() {
        String host = environment.getProperty("spring.data.redis.host", "localhost");
        int port = environment.getProperty("spring.data.redis.port", Integer.class, 6379);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 500);
            return host + ":" + port;
        } catch (IOException | RuntimeException e) {
            return host + ":" + port + "(unreachable)";
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
