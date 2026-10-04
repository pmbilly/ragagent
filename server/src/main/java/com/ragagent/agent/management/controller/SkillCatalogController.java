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
import org.springframework.web.bind.annotation.PutMapping;
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
 * 技能管理（**空间级**，租户 admin）：查看 / 新建 / 编辑 / 删除。
 *
 * <p><b>B60 起技能归属空间</b>（此前为平台级）：列出与编辑的对象是
 * 「平台内置层（只读）+ 当前空间」；写入只作用于当前空间，平台内置行返回 403。</p>
 *
 * <p><b>路径沿用裁剪前的 {@code /api/v1/skills/catalog}</b>（当年是沙箱期技能目录），
 * 语义为「指令型技能入库」——技能 = SKILL.md（frontmatter + 正文），运行期注入提示词，
 * 无安装、无执行面（原 {@code POST /{id}/install} 不实现）。</p>
 *
 * <p>写入约束：frontmatter 由服务端组装并自校验（{@link Skill#parseSkillFile(String)}），
 * 调用方只提供 slug/name/description/正文。删除是软删；被**本空间**智能体引用时拒绝，
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

    /** 更新请求体（编辑弹窗）：slug 不可改，故不接收。 */
    public record UpdateSkillRequest(String name, String description, String content) {
    }

    /**
     * 编辑草稿投影：content = **正文字段**（已从落库的 SKILL.md 里剥掉 frontmatter），
     * 这样编辑弹窗回填的就是用户当初输入的内容，保存后由服务端重新组装 frontmatter。
     */
    public record SkillDetail(
            String id,
            String slug,
            String name,
            String description,
            String content,
            int version,
            String createdBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            List<SkillCatalogService.SkillReference> referencedBy) {
    }

    /**
     * 列表投影（不含 content：内容走 files/content 端点，与裁剪前一致）。
     *
     * @param readOnly 平台内置层为 true（前端据此隐藏编辑/删除入口）
     */
    public record SkillSummary(
            String id,
            String slug,
            String name,
            String description,
            int version,
            boolean readOnly,
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
        Long tenant = TenantContext.currentTenantId();
        List<SkillSummary> skills = new ArrayList<>();
        for (SkillCatalogService.SkillRow row : catalog.listVisible(tenant)) {
            skills.add(summary(row, catalog.listReferences(row.slug(), row.name(), tenant)));
        }
        return ResponseEntity.ok(Map.of("skills", skills));
    }

    @PostMapping("/api/v1/skills/catalog")
    public ResponseEntity<SkillSummary> createCatalog(@RequestBody(required = false) CreateSkillRequest req) {
        if (req == null) {
            throw new BizException(AppError.badRequest("request body is required"));
        }
        Long tenant = requireTenant();
        SkillCatalogService.SkillRow row;
        try {
            row = catalog.create(req.slug(), req.name(), req.description(), req.content(),
                    TenantContext.currentUserId(), tenant);
        } catch (Skill.SkillValidationException e) {
            throw new BizException(AppError.badRequest(e.getMessage()));
        } catch (SkillCatalogService.DuplicateSlugException
                | SkillCatalogService.SkillNameConflictException e) {
            throw new BizException(AppError.conflict(e.getMessage()));
        }
        audit(AuditAction.SKILL_CREATED, row, List.of());
        return ResponseEntity.status(201).body(summary(row, List.of()));
    }

    /** 编辑草稿：正文从落库的 SKILL.md 还原（parse → instructions）。 */
    @GetMapping("/api/v1/skills/catalog/{id}")
    public ResponseEntity<SkillDetail> getCatalogItem(@PathVariable("id") String id) {
        Long tenant = TenantContext.currentTenantId();
        SkillCatalogService.SkillRow row = requireRow(id, tenant);
        return ResponseEntity.ok(new SkillDetail(
                row.id(), row.slug(), row.name(), row.description(), skillBody(row),
                row.version() == null ? 1 : row.version(), row.createdBy(),
                row.createdAt(), row.updatedAt(),
                catalog.listReferences(row.slug(), row.name(), tenant)));
    }

    /** 更新技能（slug 不可改；被本空间智能体引用时禁止改名；平台内置行只读）。 */
    @PutMapping("/api/v1/skills/catalog/{id}")
    public ResponseEntity<SkillSummary> updateCatalog(
            @PathVariable("id") String id,
            @RequestBody(required = false) UpdateSkillRequest req) {
        if (req == null) {
            throw new BizException(AppError.badRequest("request body is required"));
        }
        Long tenant = TenantContext.currentTenantId();
        SkillCatalogService.SkillRow current = requireRow(id, tenant);
        SkillCatalogService.SkillRow updated;
        try {
            updated = catalog.update(current, req.name(), req.description(), req.content(), tenant);
        } catch (Skill.SkillValidationException e) {
            throw new BizException(AppError.badRequest(e.getMessage()));
        } catch (SkillCatalogService.PlatformSkillReadOnlyException e) {
            throw new BizException(AppError.forbidden(e.getMessage()));
        } catch (SkillCatalogService.SkillNameConflictException e) {
            throw new BizException(AppError.conflict(e.getMessage()));
        } catch (SkillCatalogService.RenameWhileReferencedException e) {
            throw new BizException(AppError.conflict(
                    "skill is referenced by " + e.references().size()
                            + " agent(s); rename would make them lose the skill")
                    .withDetails(referenceDetails(e.references())));
        }
        List<SkillCatalogService.SkillReference> refs =
                catalog.listReferences(updated.slug(), updated.name(), tenant);
        audit(AuditAction.SKILL_UPDATED, updated, refs);
        return ResponseEntity.ok(summary(updated, refs));
    }

    @DeleteMapping("/api/v1/skills/catalog/{id}")
    public ResponseEntity<Void> deleteCatalog(
            @PathVariable("id") String id,
            @RequestParam(value = "force", required = false) Boolean force) {
        Long tenant = TenantContext.currentTenantId();
        SkillCatalogService.SkillRow row = requireRow(id, tenant);
        if (row.platform()) {
            throw new BizException(AppError.forbidden(
                    "platform builtin skills are read-only for workspace admins"));
        }
        List<SkillCatalogService.SkillReference> refs =
                catalog.listReferences(row.slug(), row.name(), tenant);
        if (!refs.isEmpty() && !Boolean.TRUE.equals(force)) {
            throw new BizException(AppError.conflict(
                    "skill is referenced by " + refs.size() + " agent(s); pass force=true to delete anyway")
                    .withDetails(referenceDetails(refs)));
        }
        catalog.softDelete(row, tenant);
        audit(AuditAction.SKILL_DELETED, row, refs);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/skills/catalog/{id}/files")
    public ResponseEntity<Map<String, Object>> listCatalogFiles(@PathVariable("id") String id) {
        requireRow(id, TenantContext.currentTenantId());
        // 入库技能只有 SKILL.md（附随文件留待后续批次）；保持裁剪前的数组形状。
        return ResponseEntity.ok(Map.of("files", List.of(new SkillFileEntry(Skill.SKILL_FILE_NAME, Skill.SKILL_FILE_NAME))));
    }

    @GetMapping("/api/v1/skills/catalog/{id}/files/content")
    public ResponseEntity<Map<String, Object>> getCatalogFile(
            @PathVariable("id") String id,
            @RequestParam(value = "path", required = false) String path) {
        SkillCatalogService.SkillRow row = requireRow(id, TenantContext.currentTenantId());
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

    /** 创建必须落在某个空间（缺上下文时不能默默写成平台内置行）。 */
    private static Long requireTenant() {
        Long tenant = TenantContext.currentTenantId();
        if (tenant == null) {
            throw new BizException(AppError.unauthorized("tenant context is required to create a skill"));
        }
        return tenant;
    }

    /** 可见范围内的行（平台内置 + 当前空间）；他空间的 id 等同不存在 → 404。 */
    private SkillCatalogService.SkillRow requireRow(String id, Long tenant) {
        Optional<SkillCatalogService.SkillRow> row = catalog.findVisibleById(id, tenant);
        if (row.isEmpty()) {
            throw new BizException(AppError.notFound("skill not found"));
        }
        return row.get();
    }

    /** 从落库的 SKILL.md 还原正文（与组装严格可逆：assembled body 与 instructions 都 strip）。 */
    private static String skillBody(SkillCatalogService.SkillRow row) {
        return Skill.parseSkillFile(row.content()).instructions;
    }

    private static SkillSummary summary(SkillCatalogService.SkillRow row, List<SkillCatalogService.SkillReference> refs) {
        return new SkillSummary(row.id(), row.slug(), row.name(), row.description(),
                row.version() == null ? 1 : row.version(), row.platform(),
                row.createdBy(), row.createdAt(), row.updatedAt(), refs);
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
        if (!row.platform()) {
            details.put("tenantId", row.tenantId());
        }
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
