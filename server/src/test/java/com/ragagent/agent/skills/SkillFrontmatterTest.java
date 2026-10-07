package com.ragagent.agent.skills;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** frontmatter 解析错误的文案钉子。 */
class SkillFrontmatterTest {

    @Test
    void nonMappingFrontmatterUsesFieldWording() {
        assertThatThrownBy(() -> SkillFrontmatter.unmarshalSkillFrontmatter("- a\n- b\n", new Skill()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid frontmatter: expected a mapping of fields");
    }

    @Test
    void nonStringFieldUsesFieldWording() {
        assertThatThrownBy(() -> SkillFrontmatter.unmarshalSkillFrontmatter("name: {a: b}\n", new Skill()))
                .hasMessage("invalid frontmatter: field 'name' must be a string");
    }
}
