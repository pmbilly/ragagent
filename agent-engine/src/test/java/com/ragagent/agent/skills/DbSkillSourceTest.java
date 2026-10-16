package com.ragagent.agent.skills;

import com.ragagent.contract.agent.SkillCatalogPort;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 入库技能源：从 skills 表行（组装好的 SKILL.md）发现/加载技能；坏行跳过不致命。 */
class DbSkillSourceTest {

    private static SkillCatalogPort.SkillRow row(String slug, String name, String desc, String body) {
        return new SkillCatalogPort.SkillRow("id-" + slug, 1L, slug, name, desc,
                SkillCatalogService.assembleSkillFile(name, slug, desc, body), 1, "tester",
                OffsetDateTime.now(), OffsetDateTime.now());
    }

    @Test
    void discoversAndServesInstructionsFromRows() {
        DbSkillSource source = new DbSkillSource(List.of(row("kb-faq", "kb-faq", "整理 FAQ", "正文 A")));

        List<Skill.SkillMetadata> meta = source.discoverSkills();
        assertEquals(1, meta.size());
        assertEquals("kb-faq", meta.get(0).name());
        assertEquals("整理 FAQ", meta.get(0).description());

        Skill skill = source.loadSkillInstructions("kb-faq");
        assertTrue(skill.instructions.contains("正文 A"));
        assertEquals("db:kb-faq", source.getSkillBasePath("kb-faq"));
        assertEquals(List.of(SkillCatalogPort.SKILL_FILE_NAME), source.listSkillFiles("kb-faq"));

        String raw = source.loadSkillFile("kb-faq", SkillCatalogPort.SKILL_FILE_NAME).content();
        assertTrue(raw.contains("name: 'kb-faq'"), raw);
        assertTrue(raw.contains("正文 A"), raw);
    }

    @Test
    void unknownSkillOrFileThrowsValidationError() {
        DbSkillSource source = new DbSkillSource(List.of(row("kb-faq", "kb-faq", "整理 FAQ", "正文")));
        assertThrows(SkillCatalogPort.SkillValidationException.class, () -> source.loadSkillInstructions("nope"));
        assertThrows(SkillCatalogPort.SkillValidationException.class, () -> source.loadSkillFile("kb-faq", "other.md"));
        assertThrows(SkillCatalogPort.SkillValidationException.class, () -> source.getSkillBasePath("nope"));
    }

    @Test
    void unparsableRowIsSkippedNotFatal() {
        SkillCatalogPort.SkillRow broken = new SkillCatalogPort.SkillRow(
                "id-broken", 1L, "broken", "broken", "", "not a skill file", 1, "",
                OffsetDateTime.now(), OffsetDateTime.now());
        DbSkillSource source = new DbSkillSource(List.of(broken, row("ok", "ok", "说明", "正文")));
        assertEquals(1, source.discoverSkills().size());
        assertEquals("ok", source.discoverSkills().get(0).name());
    }
}
