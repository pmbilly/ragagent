package com.ragagent.agent.skills;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 数据库技能源（B57）：宿主目录退役后，技能来自 {@code skills} 表。
 *
 * <p><b>实例是「每次装配一份」</b>——{@code AgentEngineAssembler} 在装配技能管理器时新建，
 * 因此缓存生命周期＝一次问答装配：每轮对话天然重读数据库，新建/删除技能对下一轮生效，
 * 不需要缓存失效机制（宿主目录时代靠 Loader 重扫，语义保持一致）。</p>
 *
 * <p>键=frontmatter 的 {@code name}（与运行期 {@code selectedSkills} 的写法一致）；
 * 解析失败的行跳过并记 warn——写入侧已自校验，出现失败说明存量数据被手改。</p>
 */
public final class DbSkillSource implements SkillSource {

    private static final Logger log = LoggerFactory.getLogger(DbSkillSource.class);

    private final Map<String, Entry> byName = new LinkedHashMap<>();

    private record Entry(Skill skill, String rawContent, String slug) {
    }

    public DbSkillSource(List<SkillCatalogService.SkillRow> rows) {
        for (SkillCatalogService.SkillRow row : rows == null ? List.<SkillCatalogService.SkillRow>of() : rows) {
            try {
                Skill skill = Skill.parseSkillFile(row.content());
                skill.basePath = "db:" + row.slug();
                skill.filePath = skill.basePath + "/" + Skill.SKILL_FILE_NAME;
                byName.put(skill.name, new Entry(skill, row.content(), row.slug()));
            } catch (RuntimeException e) {
                log.warn("[skills] skip unparsable skill row slug={}: {}", row.slug(), e.getMessage());
            }
        }
    }

    @Override
    public List<Skill.SkillMetadata> discoverSkills() {
        List<Skill.SkillMetadata> out = new ArrayList<>(byName.size());
        for (Entry e : byName.values()) {
            out.add(e.skill().toMetadata());
        }
        return out;
    }

    @Override
    public Skill loadSkillInstructions(String name) {
        return require(name).skill();
    }

    @Override
    public Skill.SkillFile loadSkillFile(String name, String relativePath) {
        Entry e = require(name);
        if (relativePath == null || relativePath.isBlank()
                || relativePath.equals(Skill.SKILL_FILE_NAME)
                || relativePath.endsWith("/" + Skill.SKILL_FILE_NAME)) {
            return new Skill.SkillFile(e.skill().name, Skill.SKILL_FILE_NAME, e.rawContent(), false);
        }
        throw new Skill.SkillValidationException("file not found in skill: " + relativePath);
    }

    @Override
    public List<String> listSkillFiles(String name) {
        require(name);
        // 入库技能只有 SKILL.md 一个文件；附随文件（Level 3）留待后续批次。
        return List.of(Skill.SKILL_FILE_NAME);
    }

    @Override
    public String getSkillBasePath(String name) {
        return require(name).skill().basePath;
    }

    private Entry require(String name) {
        Entry e = name == null ? null : byName.get(name);
        if (e == null) {
            throw new Skill.SkillValidationException("skill not found: " + name);
        }
        return e;
    }
}
