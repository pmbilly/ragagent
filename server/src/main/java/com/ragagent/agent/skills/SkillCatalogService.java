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
 * 平台级技能目录（B57 入库版）。
 *
 * <p>技能 = 组装好的 SKILL.md（frontmatter + 正文），写入前用
 * {@link Skill#parseSkillFile(String)} 自校验（与运行期同一套解析，避免“存得下、跑不了”）。
 * 管理面（新建/删除）由 {@code SkillCatalogController} 暴露给 SystemAdmin；
 * 运行期由 {@link DbSkillSource} 读同一张表注入提示词。</p>
 *
 * <p><b>写入组装约定</b>：调用方只给「名称 / slug / 描述 / 正文」，frontmatter 由本类组装
 * （单引号 YAML 标量 + 内部单引号翻倍），用户无需手写 YAML。</p>
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

    /** 列表/详情投影（不含 deletedAt；content 是完整 SKILL.md 原文）。 */
    public record SkillRow(
            String id,
            String slug,
            String name,
            String description,
            String content,
            Integer version,
            String createdBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    /** 引用了某技能的智能体（删除保护的提示来源）。 */
    public record SkillReference(String agentId, String agentName, boolean allMode) {
    }

    /** slug 已被占用（API 层映射 409）。 */
    public static final class DuplicateSlugException extends RuntimeException {
        public DuplicateSlugException(String slug) {
            super("skill slug already exists: " + slug);
        }
    }

    public List<SkillRow> listActive() {
        return mapper.selectList(new LambdaQueryWrapper<SkillEntity>()
                        .isNull(SkillEntity::getDeletedAt)
                        .orderByAsc(SkillEntity::getSlug))
                .stream()
                .map(SkillCatalogService::toRow)
                .toList();
    }

    public Optional<SkillRow> findActiveById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        SkillEntity row = mapper.selectOne(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getId, id)
                .isNull(SkillEntity::getDeletedAt));
        return Optional.ofNullable(row).map(SkillCatalogService::toRow);
    }

    public Optional<SkillRow> findActiveBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        SkillEntity row = mapper.selectOne(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getSlug, slug)
                .isNull(SkillEntity::getDeletedAt));
        return Optional.ofNullable(row).map(SkillCatalogService::toRow);
    }

    /**
     * 新建技能。frontmatter 由本类组装后再自校验，任何校验失败都抛
     * {@link Skill.SkillValidationException}（API 层映射 400）。
     */
    public SkillRow create(String slug, String name, String description, String body, String createdBy) {
        String cleanSlug = requireSlug(slug);
        String cleanName = requireName(name);
        String cleanDesc = requireDescription(description);
        String cleanBody = requireBody(body);
        if (findActiveBySlug(cleanSlug).isPresent()) {
            throw new DuplicateSlugException(cleanSlug);
        }

        String content = assembleSkillFile(cleanName, cleanSlug, cleanDesc, cleanBody);
        // 存前自校验：与运行期同一个解析器（含 frontmatter 与长度规则）。
        Skill parsed = Skill.parseSkillFile(content);
        requireRuntimeIdentity(cleanName, parsed);

        OffsetDateTime now = OffsetDateTime.now();
        SkillEntity row = new SkillEntity();
        row.setId(UUID.randomUUID().toString());
        row.setSlug(cleanSlug);
        row.setName(cleanName);
        row.setDescription(cleanDesc);
        row.setContent(content);
        row.setVersion(1);
        row.setCreatedBy(createdBy == null ? "" : createdBy);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        mapper.insert(row);
        log.info("[skills] created skill {} (slug={})", cleanName, cleanSlug);
        return toRow(row);
    }

    /** 软删（保留行供审计追溯）。返回是否命中未删行。 */
    public boolean softDelete(String id) {
        Optional<SkillRow> row = findActiveById(id);
        if (row.isEmpty()) {
            return false;
        }
        SkillEntity update = new SkillEntity();
        update.setId(id);
        update.setDeletedAt(OffsetDateTime.now());
        update.setUpdatedAt(OffsetDateTime.now());
        mapper.updateById(update);
        log.info("[skills] deleted skill {} (slug={})", row.get().name(), row.get().slug());
        return true;
    }

    /**
     * 找出引用该技能的智能体：{@code skillsSelectionMode == "all"} 的（隐式全选，
     * 删除会影响它）或 {@code selectedSkills} 里点名了该技能 name / slug 的。
     */
    public List<SkillReference> listReferences(String slug, String name) {
        String sql = "SELECT id, name,"
                + " (config ->> 'skillsSelectionMode') = 'all' AS all_mode"
                + " FROM custom_agents"
                + " WHERE deleted_at IS NULL AND ("
                + "   (config ->> 'skillsSelectionMode') = 'all'"
                + "   OR jsonb_exists(config -> 'selectedSkills', ?)"
                + "   OR jsonb_exists(config -> 'selectedSkills', ?)"
                + " )"
                + " ORDER BY name";
        return jdbc.query(sql, (rs, i) -> new SkillReference(
                        rs.getString("id"), rs.getString("name"), rs.getBoolean("all_mode")),
                name == null ? "" : name, slug == null ? "" : slug);
    }

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

    private static SkillRow toRow(SkillEntity e) {
        return new SkillRow(e.getId(), e.getSlug(), e.getName(), e.getDescription(), e.getContent(),
                e.getVersion(), e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
