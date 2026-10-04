package com.ragagent.agent.skills;

import java.time.OffsetDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 平台级技能行（指令型技能入库，B57）。
 *
 * <p>{@code content} 是**组装好的 SKILL.md 原文**（frontmatter + 正文），
 * 运行期由 {@link DbSkillSource} 交给 {@link Skill#parseSkillFile(String)} 解析——
 * 与宿主目录时代的文件内容逐字同形，因此解析/校验逻辑零新增。</p>
 *
 * <p>平台级：不带 tenant_id。软删（{@code deletedAt}）保留行以便审计追溯；
 * 唯一性由部分索引只约束未删行。</p>
 */
@TableName("skills")
public class SkillEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String slug;
    private String name;
    private String description;
    private String content;
    private Integer version;
    private String createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;

    public String getId() { return id; }
    public void setId(String v) { id = v; }

    public String getSlug() { return slug; }
    public void setSlug(String v) { slug = v; }

    public String getName() { return name; }
    public void setName(String v) { name = v; }

    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }

    public String getContent() { return content; }
    public void setContent(String v) { content = v; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer v) { version = v; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { createdBy = v; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { createdAt = v; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { updatedAt = v; }

    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime v) { deletedAt = v; }
}
