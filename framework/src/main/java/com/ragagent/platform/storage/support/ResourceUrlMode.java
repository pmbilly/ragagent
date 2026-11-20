package com.ragagent.platform.storage.support;

import com.ragagent.contract.storage.support.Mode;
import com.ragagent.platform.storage.config.StorageRuntimeEnv;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 资源 URL 模式解析的**域边界入口**：部署级 env（{@code RESOURCE_URL_MODE}）在此读取、
 * 坏值在此告警（**契约侧 {@link Mode} 零依赖**：既收参、也不持 logger）。
 *
 * <p>判据来源：清 {@code :common} 子批 C —— 契约模块 build 零依赖，连 slf4j 都不可用
 * ⇒ 日志/env 一律上移到边界。</p>
 */
public final class ResourceUrlMode {

    private static final Logger log = LoggerFactory.getLogger(ResourceUrlMode.class);

    /** 每个不同的坏值只告警一次（值每次重读：运维改回正确值无需重启）。 */
    private static final Set<String> WARNED_BAD_DEFAULTS = ConcurrentHashMap.newKeySet();

    private ResourceUrlMode() {
    }

    /** 部署级默认模式（env 未设/坏值 ⇒ {@code HANDLE}）。 */
    public static Mode defaultMode() {
        String raw = StorageRuntimeEnv.resourceUrlMode();
        warnIfBad(raw);
        return Mode.defaultMode(raw);
    }

    /** 合并每请求值与部署默认，并施加不可绕过的限制（见 {@link Mode#resolve}）。 */
    public static Mode resolve(String queryValue) {
        String raw = StorageRuntimeEnv.resourceUrlMode();
        warnIfBad(raw);
        return Mode.resolve(queryValue, raw);
    }

    private static void warnIfBad(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            Mode.parse(raw);
        } catch (RuntimeException e) {
            if (WARNED_BAD_DEFAULTS.add(raw.trim())) {
                log.debug("ignoring {}: invalid value \"{}\"; falling back to \"{}\"",
                        Mode.ENV_VAR, raw.trim(), Mode.HANDLE.value());
            }
        }
    }
}
