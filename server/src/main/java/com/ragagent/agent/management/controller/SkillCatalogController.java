package com.ragagent.agent.management.controller;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ragagent.agent.skills.Skill;
import com.ragagent.agent.skills.SkillCatalogService;
import com.ragagent.audit.domain.AuditAction;
import com.ragagent.audit.domain.AuditLog;
import com.ragagent.audit.domain.AuditOutcome;
import com.ragagent.audit.service.AuditLogService;
import com.ragagent.common.context.TenantContext;
import com.ragagent.common.error.AppError;
import com.ragagent.common.error.BizException;

/**
 * 技能管理（平台级，SystemAdmin）：查看 / 新建 / 删除。
 *
 * <p><b>路径沿用裁剪前的 {@code /api/v1/skills/catalog}</b>（当年是沙箱期技能目录），
 * 语义改为「指令型技能入库」——技能 = SKILL.md（frontmatter + 正文），运行期注入提示词，
 * 无安装、无执行面（原 {@code POST /{id}/install} 不实现）。</p>
 *
 * <p>写入约束：frontmatter 由服务端组装并自校验（{@link Skill#parseSkillFile(String)}），
 * 调用方只提供 slug/name/description/正文。删除是软删；被智能体引用时拒绝，
 * {@code force=true} 才强删（引用清单进 details 与审计）。</p>
 *
 * <p>响应一律 camelCase + 裸资源信封（契约 §2）；错误体走标准 {@code {error:{...}}}。</p>
 */
@RestController
public class SkillCatalogController {

    private static final ObjectMapper AUDIT_MAPPER = new ObjectMapper();

    private final SkillCatalogService catalog;
    private final AuditLogService auditService;

    public SkillCatalogController(SkillCatalogService catalog, AuditLogService auditService) {
        this.catalog = catalog;
        this.auditService = auditService;
    }

    /** 新建请求体（content = 技能正文，不含 frontmatter）。 */
    public record CreateSkillRequest(String slug, String name, String description, String content) {
    }

    /** 列表/详情投影（不含 content：内容走 files/content 端点，与裁剪前一致）。 */
    public record SkillSummary(
            String id,
            String slug,
            String name,
            String description,
            int version,
            String createdBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            List<SkillCatalogService.SkillReference> referencedBy) {
    }

    /** 技能文件条目（入库技能只有 SKILL.md）。 */
    public record SkillFileEntry(String name, String path) {
    }

    @GetMapping("/api/v1/skills/catalog")
    public ResponseEntity<Map<String, Object>> listCatalog() {
        List<SkillSummary> skills = new ArrayList<>();
        for (SkillCatalogService.SkillRow row : catalog.listActive()) {
            skills.add(summary(row, catalog.listReferences(row.slug(), row.name())));
        }
        return ResponseEntity.ok(Map.of("skills", skills));
    }

    @PostMapping("/api/v1/skills/catalog")
    public ResponseEntity<SkillSummary> createCatalog(@RequestBody(required = false) CreateSkillRequest req) {
        if (req == null) {
            throw new BizException(AppError.badRequest("request body is required"));
        }
        SkillCatalogService.SkillRow row;
        try {
            row = catalog.create(req.slug(), req.name(), req.description(), req.content(),
                    TenantContext.currentUserId());
        } catch (Skill.SkillValidationException e) {
            throw new BizException(AppError.badRequest(e.getMessage()));
        } catch (SkillCatalogService.DuplicateSlugException e) {
            throw new BizException(AppError.conflict(e.getMessage()));
        }
        audit(AuditAction.SKILL_CREATED, row, List.of());
        return ResponseEntity.status(201).body(summary(row, List.of()));
    }

    @DeleteMapping("/api/v1/skills/catalog/{id}")
    public ResponseEntity<Void> deleteCatalog(
            @PathVariable("id") String id,
            @RequestParam(value = "force", required = false) Boolean force) {
        SkillCatalogService.SkillRow row = requireRow(id);
        List<SkillCatalogService.SkillReference> refs = catalog.listReferences(row.slug(), row.name());
        if (!refs.isEmpty() && !Boolean.TRUE.equals(force)) {
            throw new BizException(AppError.conflict(
                    "skill is referenced by " + refs.size() + " agent(s); pass force=true to delete anyway")
                    .withDetails(referenceDetails(refs)));
        }
        catalog.softDelete(row.id());
        audit(AuditAction.SKILL_DELETED, row, refs);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/skills/catalog/{id}/files")
    public ResponseEntity<Map<String, Object>> listCatalogFiles(@PathVariable("id") String id) {
        requireRow(id);
        // 入库技能只有 SKILL.md（附随文件留待后续批次）；保持裁剪前的数组形状。
        return ResponseEntity.ok(Map.of("files", List.of(new SkillFileEntry(Skill.SKILL_FILE_NAME, Skill.SKILL_FILE_NAME))));
    }

    @GetMapping("/api/v1/skills/catalog/{id}/files/content")
    public ResponseEntity<Map<String, Object>> getCatalogFile(
            @PathVariable("id") String id,
            @RequestParam(value = "path", required = false) String path) {
        SkillCatalogService.SkillRow row = requireRow(id);
        if (path != null && !path.isBlank()
                && !path.equals(Skill.SKILL_FILE_NAME)
                && !path.endsWith("/" + Skill.SKILL_FILE_NAME)) {
            throw new BizException(AppError.notFound("file not found in skill: " + path));
        }
        return ResponseEntity.ok(Map.of(
                "name", Skill.SKILL_FILE_NAME,
                "path", Skill.SKILL_FILE_NAME,
                "content", row.content()));
    }

    private SkillCatalogService.SkillRow requireRow(String id) {
        Optional<SkillCatalogService.SkillRow> row = catalog.findActiveById(id);
        if (row.isEmpty()) {
            throw new BizException(AppError.notFound("skill not found"));
        }
        return row.get();
    }

    private static SkillSummary summary(SkillCatalogService.SkillRow row, List<SkillCatalogService.SkillReference> refs) {
        return new SkillSummary(row.id(), row.slug(), row.name(), row.description(),
                row.version() == null ? 1 : row.version(), row.createdBy(), row.createdAt(), row.updatedAt(), refs);
    }

    private static String referenceDetails(List<SkillCatalogService.SkillReference> refs) {
        List<String> parts = new ArrayList<>(refs.size());
        for (SkillCatalogService.SkillReference ref : refs) {
            parts.add(ref.agentName() + (ref.allMode() ? "(全部技能)" : ""));
        }
        return "引用它的智能体：" + String.join("、", parts);
    }

    private void audit(String action, SkillCatalogService.SkillRow row,
            List<SkillCatalogService.SkillReference> refs) {
        AuditLog entry = new AuditLog();
        entry.setTenantId(TenantContext.currentTenantId() == null ? 0L : TenantContext.currentTenantId());
        entry.setActorUserId(TenantContext.currentUserId() == null ? "" : TenantContext.currentUserId());
        entry.setActorRole(TenantContext.currentRole() == null ? "" : TenantContext.currentRole());
        entry.setAction(action);
        entry.setTargetType("skill");
        entry.setTargetUserId("");
        entry.setOutcome(AuditOutcome.SUCCESS);
        ObjectNode details = AUDIT_MAPPER.createObjectNode();
        details.put("id", row.id());
        details.put("slug", row.slug());
        details.put("name", row.name());
        if (!refs.isEmpty()) {
            List<String> names = new ArrayList<>(refs.size());
            for (SkillCatalogService.SkillReference ref : refs) {
                names.add(ref.agentName());
            }
            details.put("referencedBy", String.join(",", names));
        }
        entry.setDetails(details);
        auditService.logBestEffort(entry);
    }
}
