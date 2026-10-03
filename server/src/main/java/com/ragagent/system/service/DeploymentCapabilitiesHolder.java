package com.ragagent.system.service;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ragagent.common.deployment.DeploymentProperties;
import com.ragagent.system.dto.SystemDtos;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * GET /system/capabilities 的启动快照（组合根装配期一次性绑定）。
 *
 * <p>可用性判定 = "对应模块的控制器/服务 bean 是否存在"——用 {@link ObjectProvider}
 * 在启动期探测，结果绑定进本 holder（快照 = 启动期装配，运行期不重算；docker 的
 * 活值覆盖在 controller 的 overlayLiveDockerSandboxCapability）。</p>
 *
 * <p>当前部署的注册状态（= 快照值，随部署形态而变化——这正是该端点的语义）：
     * 全部能力路由已注册 → supported=true；唯 settings.sandbox.docker 因 Java 进程无 docker
     * 后端执行体 → supported=false +
     * "docker_backend_disabled"（活值覆盖在 controller 的 overlayLiveDockerSandboxCapability）。
     * 与 dev 部署的差异属于<b>部署状态漂移</b>，A/B 时按部署各自断言。</p>
 */
@Component
public class DeploymentCapabilitiesHolder {

    /** 部署形态（{@code WEKNORA_EDITION} 走属性绑定，不读裸 env）。 */
    private final DeploymentProperties deploymentProperties;

    private volatile SystemDtos.DeploymentCapabilitiesData snapshot =
            new SystemDtos.DeploymentCapabilitiesData("standard", new LinkedHashMap<>());

    public DeploymentCapabilitiesHolder(DeploymentProperties deploymentProperties) {
        this.deploymentProperties = deploymentProperties;
    }

    public SystemDtos.DeploymentCapabilitiesData snapshot() {
        return snapshot;
    }

    /**
     * 组合根（WebConfig）在装配完成后调用一次。
     */
    public void bind(boolean agents, boolean im, boolean embed,
                     boolean api, boolean mcp, boolean webSearch, boolean vectorStore,
                     boolean storage) {
        Map<String, SystemDtos.DeploymentCapability> caps = new LinkedHashMap<>();
        // 构造顺序无语义——encoding/json 对 map 恒按字母序输出（Jackson 用 record 声明序
        // 序列化字段、LinkedHashMap 保插入序，因此这里要按 Go 输出的字母序插入）。
        caps.put("agents", capability(agents));
        caps.put("integrations.api", capability(api));
        caps.put("integrations.embed", capability(embed));
        caps.put("integrations.im", capability(im));
        caps.put("settings.mcp", capability(mcp));
        caps.put("settings.storage", capability(storage));
        caps.put("settings.vectorstore", capability(vectorStore));
        caps.put("settings.websearch", capability(webSearch));
        snapshot = new SystemDtos.DeploymentCapabilitiesData(edition(), caps);
    }

    private String edition() {
        return deploymentProperties.editionOrDefault();
    }

    private static SystemDtos.DeploymentCapability capability(boolean present) {
        return present ? SystemDtos.DeploymentCapability.yes()
                : SystemDtos.DeploymentCapability.notRegistered();
    }
}
