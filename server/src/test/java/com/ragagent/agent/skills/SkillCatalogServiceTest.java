package com.ragagent.agent.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 技能入库的**纯逻辑**校验（无 Spring/DB）：组装 → 运行期解析器自校验 → 身份一致性。
 *
 * <p>DB 写入/查询由 {@code SkillCatalogController} 的实测冒烟覆盖（建→列表→409→force 删除）。</p>
 */
class SkillCatalogServiceTest {

    @Test
    void assembleProducesParsableSkillFile() {
        String content = SkillCatalogService.assembleSkillFile(
                "kb-faq-curator", "kb-faq-curator", "把文档整理成 FAQ", "## 步骤\n1. 先检索");

        Skill parsed = Skill.parseSkillFile(content);
        assertEquals("kb-faq-curator", parsed.name);
        assertEquals("kb-faq-curator", parsed.slug);
        assertEquals("把文档整理成 FAQ", parsed.description);
        assertTrue(parsed.instructions.contains("1. 先检索"), parsed.instructions);
        SkillCatalogService.requireRuntimeIdentity("kb-faq-curator", parsed);
    }

    @Test
    void descriptionWithYamlSpecialCharsStillParses() {
        String desc = "it's a test: with 'quotes' and: colons";
        String content = SkillCatalogService.assembleSkillFile("skill-a", "skill-a", desc, "正文");
        Skill parsed = Skill.parseSkillFile(content);
        assertEquals(desc, parsed.description);
    }

    @Test
    void nameWithSpaceIsRejectedByRuntimeIdentityCheck() {
        // 带空格的 name 会被 Skill.applyInstallName 静默回落成 slug → 入库前必须拦下
        String content = SkillCatalogService.assembleSkillFile("KB FAQ 整理", "kb-faq-curator", "说明", "正文");
        Skill parsed = Skill.parseSkillFile(content);
        assertEquals("kb-faq-curator", parsed.name); // 证明运行期确实改了名
        Skill.SkillValidationException e = assertThrows(Skill.SkillValidationException.class,
                () -> SkillCatalogService.requireRuntimeIdentity("KB FAQ 整理", parsed));
        assertTrue(e.getMessage().contains("normalized to"), e.getMessage());
    }

    @Test
    void slugRules() {
        assertEquals("kb-faq", SkillCatalogService.requireSlug("kb-faq"));
        assertThrows(Skill.SkillValidationException.class, () -> SkillCatalogService.requireSlug("KB_FAQ"));
        assertThrows(Skill.SkillValidationException.class, () -> SkillCatalogService.requireSlug("-lead"));
        assertThrows(Skill.SkillValidationException.class, () -> SkillCatalogService.requireSlug("trail-"));
        assertThrows(Skill.SkillValidationException.class, () -> SkillCatalogService.requireSlug("a".repeat(65)));
    }

    @Test
    void descriptionAndBodyAreRequired() {
        assertThrows(Skill.SkillValidationException.class, () -> SkillCatalogService.requireDescription("  "));
        assertThrows(Skill.SkillValidationException.class, () -> SkillCatalogService.requireBody("  "));
        assertThrows(Skill.SkillValidationException.class, () -> SkillCatalogService.requireName(" "));
    }
}
