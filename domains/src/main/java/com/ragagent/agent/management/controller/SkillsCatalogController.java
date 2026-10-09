package com.ragagent.agent.management.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ragagent.agent.skills.SkillCatalogService;
import com.ragagent.common.context.TenantContext;

/**
 * 指令型技能选择器的数据源（智能体编辑器 → 技能区）。
 *
 * <p>B57 起技能存于 {@code skills} 表（入库），不再是宿主 skillDirs 里的 SKILL.md；
 * 响应形状与宿主目录时代保持一致（{@code {skills:[{name,description}], skillsAvailable}}），
 * 前端与 embed/chat 侧无需改动。</p>
 *
 * <p>{@code skillsAvailable} 恒为 true：技能能力始终可用，列表为空由前端渲染
 * 「还没有技能」空态。管理面（新建/删除）见 {@link SkillCatalogController}。</p>
 */
@RestController
public class SkillsCatalogController {

    private final SkillCatalogService catalog;

    public SkillsCatalogController(SkillCatalogService catalog) {
        this.catalog = catalog;
    }

    /** 响应条目（与裁剪前 GET /api/v1/skills 的 {name, description} 同形）。 */
    public record SkillInfoResponse(String name, String description) {
    }

    /**
     * 前端技能选择器的数据源：**可见范围 = 平台内置层 + 当前空间**（B60 起技能归属空间）。
     *
     * <p>此前读全表，任何 Viewer 都能看到别家技能（跨租户泄露）；现按 TenantContext 过滤，
     * 且 {@code tenantId == null} 时只回平台层（fail closed）。</p>
     */
    @GetMapping("/api/v1/skills")
    public ResponseEntity<Map<String, Object>> listSkills() {
        Map<String, Object> body = new TreeMap<>();
        List<SkillInfoResponse> response = new ArrayList<>();
        for (SkillCatalogService.SkillRow row : catalog.listVisible(TenantContext.currentTenantId())) {
            response.add(new SkillInfoResponse(row.name(), row.description()));
        }
        body.put("skills", response);
        body.put("skillsAvailable", true);
        return ResponseEntity.ok(body);
    }
}
