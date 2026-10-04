package com.ragagent.agent.skills;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragagent.agent.skills.mapper.SkillMapper;

/**
 * 技能目录（B57 入库；B60 起**租户级 + 平台内置层**）。
 *
 * <p><b>可见性</b>：平台内置层（{@code tenant_id IS NULL}，官方预置，全员可见**只读**）
 * + 当前空间的技能。选任一处越权都会造成隔离缺陷，故所有读路径都必须带租户上下文，
 * 「平台层 or 本空间」这个范围由 {@link #visibleScope} 单点表达。</p>
 *
 * <p><b>写入</b>：技能 = 组装好的 SKILL.md（frontmatter + 正文），写入前用
 * {@link Skill#parseSkillFile(String)} 自校验（与运行期同一套解析，避免"存得下、跑不了"）。
 * 调用方只给「名称 / slug / 描述 / 正文」，frontmatter 由本类组装。
 * 平台内置行对租户管理员**只读**（改/删走平台侧手段，不在本接口）。</p>
 *
 * <p><b>命名</b>：{@code name} 是运行期身份（agent 配置的 {@code selectedSkills} 存的是它），
 * 因此**可见范围内必须唯一**（平台层 + 本空间），否则「勾了甲技能、注入了乙技能」；
 * {@code slug} 只在同一命名空间内唯一（DB 部分唯一索引兜底）。</p>
 */
@Service
public class SkillCatalogService {

    private static final Logger log = LoggerFactory.getLogger(SkillCatalogService.class);

    /** slug：小写字母/数字/连字符，首尾必须是字母或数字，最长 64。 */
    static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9-]{0,62}[a-z0-9])?$");

    /** 正文大小上限（SKILL.md 原文语义，256KB 足够放很长的指令）。 */
    static final int MAX_CONTENT_BYTES = 256 * 1024;

    private final SkillMapper mapper;
    private final JdbcTemplate jdbc;

    public SkillCatalogService(SkillMapper mapper, JdbcTemplate jdbc) {
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    /** 目录/详情投影（不含 deletedAt；content 是完整 SKILL.md 原文）。 */
    public record SkillRow(
            String id,
            Long tenantId,
            String slug,
            String name,
            String description,
            String content,
            Integer version,
            String createdBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        /** 平台内置层：全员可见、只读。 */
        public boolean platform() {
            return tenantId == null;
        }
    }

    /** 引用了某技能的智能体（删除/改名保护的提示来源）。 */
    public record SkillReference(String agentId, String agentName, boolean allMode) {
    }

    /** slug 已被占用（API 层映射 409）。 */
    public static final class DuplicateSlugException extends RuntimeException {
        public DuplicateSlugException(String slug) {
            super("skill slug already exists in this workspace: " + slug);
        }
    }

    /**
     * name 与可见范围内已有技能冲突（平台内置或本空间；API 层映射 409）。
     * name 是运行期身份，重名会让 {@code selectedSkills} 指向歧义。
     */
    public static final class SkillNameConflictException extends RuntimeException {
        public SkillNameConflictException(String name, boolean platformHit) {
            super(platformHit
                    ? "skill name is taken by a platform builtin: " + name
                    : "skill name already exists in this workspace: " + name);
        }
    }

    /** 平台内置行只读（API 层映射 403）。 */
    public static final class PlatformSkillReadOnlyException extends RuntimeException {
        public PlatformSkillReadOnlyException() {
            super("platform builtin skills are read-only for workspace admins");
        }
    }

    /** 改名被引用保护（API 层映射 409 + 引用清单）。 */
    public static final class RenameWhileReferencedException extends RuntimeException {

        private final transient List<SkillReference> references;

        public RenameWhileReferencedException(List<SkillReference> references) {
            super("skill is referenced by " + (references == null ? 0 : references.size())
                    + " agent(s); rename would orphan them");
            this.references = references == null ? List.of() : List.copyOf(references);
        }

        public List<SkillReference> references() {
            return references;
        }
    }

    // ── 读 ────────────────────────────────────────────────────────────────

    /**
     * 可见目录：平台内置层 + 当前空间（供选择器与运行期注入共用）。
     *
     * <p>{@code tenantId == null} 时**只返回平台层**（fail closed）——缺少租户上下文
     * 绝不意味着"看全部"，否则会跨空间泄露。</p>
     */
    public List<SkillRow> listVisible(Long tenantId) {
        LambdaQueryWrapper<SkillEntity> q = new LambdaQueryWrapper<SkillEntity>()
                .isNull(SkillEntity::getDeletedAt)
                .orderByAsc(SkillEntity::getSlug);
        visibleScope(q, tenantId);
        return mapper.selectList(q).stream().map(SkillCatalogService::toRow).toList();
    }

    /** 详情：仅可见范围（他空间的技能等同不存在 → 404）。 */
    public Optional<SkillRow> findVisibleById(String id, Long tenantId) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<SkillEntity> q = new LambdaQueryWrapper<SkillEntity>()
                .isNull(SkillEntity::getDeletedAt)
                .eq(SkillEntity::getId, id);
        visibleScope(q, tenantId);
        return Optional.ofNullable(mapper.selectOne(q)).map(SkillCatalogService::toRow);
    }

    // ── 写 ────────────────────────────────────────────────────────────────

    /**
     * 新建技能（归属当前空间）。frontmatter 由本类组装后再自校验，
     * 任何校验失败都抛 {@link Skill.SkillValidationException}（API 层映射 400）。
     */
    public SkillRow create(String slug, String name, String description, String body,
            String createdBy, Long tenantId) {
        requireTenantScope(tenantId);
        String cleanSlug = requireSlug(slug);
        String cleanName = requireName(name);
        String cleanDesc = requireDescription(description);
        String cleanBody = requireBody(body);
        requireSlugFree(cleanSlug, findVisibleByNameOrSlug(null, cleanSlug, null, tenantId));
        requireNameFree(cleanName, findVisibleByNameOrSlug(cleanName, null, null, tenantId));

        String content = assembleSkillFile(cleanName, cleanSlug, cleanDesc, cleanBody);
        // 存前自校验：与运行期同一个解析器（含 frontmatter 与长度规则）。
        Skill parsed = Skill.parseSkillFile(content);
        requireRuntimeIdentity(cleanName, parsed);

        OffsetDateTime now = OffsetDateTime.now();
        SkillEntity row = new SkillEntity();
        row.setId(UUID.randomUUID().toString());
        row.setTenantId(tenantId);
        row.setSlug(cleanSlug);
        row.setName(cleanName);
        row.setDescription(cleanDesc);
        row.setContent(content);
        row.setVersion(1);
        row.setCreatedBy(createdBy == null ? "" : createdBy);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        mapper.insert(row);
        log.info("[skills] created skill {} (slug={}, tenant={})", cleanName, cleanSlug, tenantId);
        return toRow(row);
    }

    /**
     * 更新技能（编辑弹窗）。**slug 不可改**（它是接口寻址与去重键，改名语义 = 删旧建新）。
     *
     * <p>name 是运行期身份（agent 配置的 {@code selectedSkills} 存的就是它），因此
     * <b>被智能体引用时禁止改名</b>——否则那些 agent 会静默失去这个技能；描述与正文随时可改。
     * 平台内置行只读。</p>
     */
    public SkillRow update(SkillRow current, String name, String description, String body, Long tenantId) {
        requireTenantWritable(current, tenantId);
        String cleanName = requireName(name);
        String cleanDesc = requireDescription(description);
        String cleanBody = requireBody(body);
        requireNameFree(cleanName,
                findVisibleByNameOrSlug(cleanName, null, current.id(), tenantId));
        requireRenameAllowed(current.name(), cleanName,
                listReferences(current.slug(), current.name(), tenantId));

        String content = assembleSkillFile(cleanName, current.slug(), cleanDesc, cleanBody);
        Skill parsed = Skill.parseSkillFile(content);
        requireRuntimeIdentity(cleanName, parsed);

        SkillEntity row = new SkillEntity();
        row.setId(current.id());
        row.setName(cleanName);
        row.setDescription(cleanDesc);
        row.setContent(content);
        row.setVersion((current.version() == null ? 1 : current.version()) + 1);
        row.setUpdatedAt(OffsetDateTime.now());
        mapper.updateById(row);
        log.info("[skills] updated skill {} (slug={}, tenant={}, v{})",
                cleanName, current.slug(), tenantId, row.getVersion());
        return findVisibleById(current.id(), tenantId).orElseThrow();
    }

    /** 软删（保留行供审计追溯）。平台内置行只读。 */
    public boolean softDelete(SkillRow current, Long tenantId) {
        requireTenantWritable(current, tenantId);
        SkillEntity update = new SkillEntity();
        update.setId(current.id());
        update.setDeletedAt(OffsetDateTime.now());
        update.setUpdatedAt(OffsetDateTime.now());
        mapper.updateById(update);
        log.info("[skills] deleted skill {} (slug={}, tenant={})", current.name(), current.slug(), tenantId);
        return true;
    }

    // ── 引用面 ────────────────────────────────────────────────────────────

    /**
     * 找出引用该技能的智能体：**本空间**的 + **内建 agent**（后者全局共享），
     * 判据是 {@code skillsSelectionMode == "all"}（隐式全选）或
     * {@code selectedSkills} 里点名了该技能 name / slug。
     *
     * <p>为什么按空间过滤：租户级之后不过滤的话，A 空间的技能会被 B 空间的 agent
     * "看起来引用"，改名/删除保护会给出错误理由；{@code tenantId == null} 时返回空（fail closed）。</p>
     *
     * <p>为什么内建要单独算：内建 agent 的行是**全局一行**（id 固定如
     * {@code builtin-smart-reasoning}），{@code tenant_id} 取自首个物化它的空间
     * （实测为别的空间）——只按 {@code tenant_id = ?} 过滤会漏掉它，导致
     * 「改名/删除时提示不到真正会被影响的 agent」。</p>
     */
    public List<SkillReference> listReferences(String slug, String name, Long tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        String sql = "SELECT id, name,"
                + " (config ->> 'skillsSelectionMode') = 'all' AS all_mode"
                + " FROM custom_agents"
                + " WHERE deleted_at IS NULL AND (tenant_id = ? OR is_builtin) AND ("
                + "   (config ->> 'skillsSelectionMode') = 'all'"
                + "   OR jsonb_exists(config -> 'selectedSkills', ?)"
                + "   OR jsonb_exists(config -> 'selectedSkills', ?)"
                + " )"
                + " ORDER BY name";
        return jdbc.query(sql, (rs, i) -> new SkillReference(
                        rs.getString("id"), rs.getString("name"), rs.getBoolean("all_mode")),
                tenantId, name == null ? "" : name, slug == null ? "" : slug);
    }

    // ── 纯逻辑（可单测）────────────────────────────────────────────────────

    /** 「平台内置层 or 本空间」这个可见范围的单点表达（读路径必须都经过它）。 */
    static void visibleScope(LambdaQueryWrapper<SkillEntity> q, Long tenantId) {
        if (tenantId == null) {
            q.isNull(SkillEntity::getTenantId);
            return;
        }
        q.and(w -> w.isNull(SkillEntity::getTenantId).or().eq(SkillEntity::getTenantId, tenantId));
    }

    /** 新建必须有空间上下文（缺了会把租户技能写成平台内置行 = 越权）。 */
    static void requireTenantScope(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalStateException("tenant context is required to create a skill");
        }
    }

    /** 平台内置行只读；跨空间行本不可见（读路径已过滤），此处兜底。 */
    static void requireTenantWritable(SkillRow row, Long tenantId) {
        if (row == null) {
            throw new IllegalArgumentException("skill row is required");
        }
        if (row.platform()) {
            throw new PlatformSkillReadOnlyException();
        }
        if (tenantId == null || !row.tenantId().equals(tenantId)) {
            throw new PlatformSkillReadOnlyException();
        }
    }

    /** name 在可见范围内必须唯一（平台内置 + 本空间）：它是运行期身份。 */
    static void requireNameFree(String name, List<SkillRow> sameNameRows) {
        if (sameNameRows == null || sameNameRows.isEmpty()) {
            return;
        }
        boolean platformHit = sameNameRows.stream().anyMatch(SkillRow::platform);
        throw new SkillNameConflictException(name, platformHit);
    }

    /** slug 在同一命名空间内必须唯一（DB 部分唯一索引兜底，这里给人话）。 */
    static void requireSlugFree(String slug, List<SkillRow> sameSlugRows) {
        if (sameSlugRows != null && !sameSlugRows.isEmpty()) {
            throw new DuplicateSlugException(slug);
        }
    }

    /** 改名前置校验（纯逻辑，便于单测）：无引用则可改名。 */
    static void requireRenameAllowed(String currentName, String nextName, List<SkillReference> refs) {
        if (currentName == null || currentName.equals(nextName)) {
            return;
        }
        if (refs != null && !refs.isEmpty()) {
            throw new RenameWhileReferencedException(refs);
        }
    }

    // ── 组装/校验 ─────────────────────────────────────────────────────────

    /** 组装 SKILL.md 原文：单引号 YAML 标量（内部单引号翻倍），正文原样附在后面。 */
    public static String assembleSkillFile(String name, String slug, String description, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("name: ").append(quoteYaml(name)).append('\n');
        sb.append("slug: ").append(quoteYaml(slug)).append('\n');
        if (description != null && !description.isBlank()) {
            sb.append("description: ").append(quoteYaml(description)).append('\n');
        }
        sb.append("---\n\n");
        sb.append(body == null ? "" : body.strip()).append('\n');
        return sb.toString();
    }

    /**
     * 运行期以 frontmatter 的 {@code name} 作为技能身份（agent 配置 {@code selectedSkills} 存的也是它）。
     * Skill.applyInstallName 会把非法名（如带空格）**静默回落成 slug**——必须在这里拦下，
     * 否则「管理页显示的名字」与「运行期身份」分叉，勾选后注入不上。
     */
    static void requireRuntimeIdentity(String requestedName, Skill parsed) {
        if (!parsed.name.equals(requestedName)) {
            throw new Skill.SkillValidationException(
                    "name must be a runtime-legal skill name (letters/numbers/hyphens/underscores only); '"
                            + requestedName + "' would be normalized to '" + parsed.name + "'");
        }
    }

    private static String quoteYaml(String value) {
        String flat = value == null ? "" : value.replace("\r", " ").replace("\n", " ");
        return "'" + flat.replace("'", "''") + "'";
    }

    static String requireSlug(String slug) {
        String v = slug == null ? "" : slug.strip();
        if (!SLUG_PATTERN.matcher(v).matches()) {
            throw new Skill.SkillValidationException(
                    "slug must match ^[a-z0-9]([a-z0-9-]{0,62}[a-z0-9])?$ (lowercase letters, digits, hyphens)");
        }
        return v;
    }

    static String requireName(String name) {
        String v = name == null ? "" : name.strip();
        if (v.isEmpty()) {
            throw new Skill.SkillValidationException("name: 不能为空");
        }
        if (v.length() > Skill.MAX_NAME_LENGTH) {
            throw new Skill.SkillValidationException("name: 超过 " + Skill.MAX_NAME_LENGTH + " 字符上限");
        }
        return v;
    }

    static String requireDescription(String description) {
        String v = description == null ? "" : description.strip();
        if (v.isEmpty()) {
            // 运行期 Skill.validate 也要求非空（描述会展示在技能选择器里）。
            throw new Skill.SkillValidationException("description: 不能为空");
        }
        if (v.length() > Skill.MAX_DESCRIPTION_LENGTH) {
            throw new Skill.SkillValidationException(
                    "description: 超过 " + Skill.MAX_DESCRIPTION_LENGTH + " 字符上限");
        }
        return v;
    }

    static String requireBody(String body) {
        String v = body == null ? "" : body.strip();
        if (v.isEmpty()) {
            throw new Skill.SkillValidationException("content: 不能为空（技能正文即注入给模型的指令）");
        }
        if (v.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
            throw new Skill.SkillValidationException("content: 超过 " + MAX_CONTENT_BYTES + " 字节上限");
        }
        return v;
    }

    // ── 内部查询 ──────────────────────────────────────────────────────────

    /**
     * 可见范围内按 name / slug 命中（两参数二选一），可排除自身 id。
     * 用于命名冲突判定：既挡同空间重名，也挡与平台内置层重名。
     */
    private List<SkillRow> findVisibleByNameOrSlug(String name, String slug, String excludeId, Long tenantId) {
        LambdaQueryWrapper<SkillEntity> q = new LambdaQueryWrapper<SkillEntity>()
                .isNull(SkillEntity::getDeletedAt);
        if (name != null) {
            q.eq(SkillEntity::getName, name);
        } else {
            q.eq(SkillEntity::getSlug, slug);
        }
        if (excludeId != null) {
            q.ne(SkillEntity::getId, excludeId);
        }
        visibleScope(q, tenantId);
        return mapper.selectList(q).stream().map(SkillCatalogService::toRow).toList();
    }

    private static SkillRow toRow(SkillEntity e) {
        return new SkillRow(e.getId(), e.getTenantId(), e.getSlug(), e.getName(), e.getDescription(),
                e.getContent(), e.getVersion(), e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
