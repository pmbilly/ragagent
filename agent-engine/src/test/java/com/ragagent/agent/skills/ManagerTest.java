package com.ragagent.agent.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 技能管理器：多源聚合、allowedSkills 过滤、同名先到先得、禁用/未命中语义。 */
class ManagerTest {

    /** 最小假源：一个源只认识一个技能。 */
    private static SkillSource fakeSource(String name) {
        return new SkillSource() {
            @Override
            public List<Skill.SkillMetadata> discoverSkills() {
                return List.of(new Skill.SkillMetadata(name, name + " 的说明", "db:" + name));
            }

            @Override
            public Skill loadSkillInstructions(String requested) {
                Skill skill = new Skill();
                if (!name.equals(requested)) {
                    throw new Skill.SkillValidationException("skill not found: " + requested);
                }
                skill.name = name;
                skill.description = name + " 的说明";
                skill.instructions = name + " 的正文";
                skill.loaded = true;
                return skill;
            }

            @Override
            public Skill.SkillFile loadSkillFile(String requested, String relativePath) {
                throw new Skill.SkillValidationException("file not found in skill: " + relativePath);
            }

            @Override
            public List<String> listSkillFiles(String requested) {
                if (!name.equals(requested)) {
                    throw new Skill.SkillValidationException("skill not found: " + requested);
                }
                return List.of(Skill.SKILL_FILE_NAME);
            }

            @Override
            public String getSkillBasePath(String requested) {
                return "db:" + name;
            }
        };
    }

    private static Manager manager(List<SkillSource> sources, List<String> allowed) {
        Manager manager = new Manager(new Manager.ManagerConfig(sources, allowed, true));
        manager.initialize();
        return manager;
    }

    @Test
    void aggregatesMultipleSources() {
        List<Skill.SkillMetadata> meta = manager(List.of(fakeSource("a"), fakeSource("b")), List.of())
                .getAllMetadata();
        List<String> names = new ArrayList<>();
        meta.forEach(m -> names.add(m.name()));
        assertEquals(List.of("a", "b"), names);
    }

    @Test
    void allowedSkillsFiltersMetadataAndGuardsLoads() throws Exception {
        Manager manager = manager(List.of(fakeSource("a"), fakeSource("b")), List.of("a"));
        assertEquals(1, manager.getAllMetadata().size());
        assertEquals("a", manager.getAllMetadata().get(0).name());
        assertEquals("a 的正文", manager.loadSkill("a").instructions);
        assertThrows(Skill.SkillValidationException.class, () -> manager.loadSkill("b"));
    }

    @Test
    void firstSourceWinsOnSameName() {
        SkillSource first = fakeSource("dup");
        SkillSource second = new SkillSource() {
            @Override
            public List<Skill.SkillMetadata> discoverSkills() {
                return List.of(new Skill.SkillMetadata("dup", "second", "db:second"));
            }

            @Override
            public Skill loadSkillInstructions(String name) {
                throw new Skill.SkillValidationException("not used");
            }

            @Override
            public Skill.SkillFile loadSkillFile(String name, String relativePath) {
                throw new Skill.SkillValidationException("not used");
            }

            @Override
            public List<String> listSkillFiles(String name) {
                throw new Skill.SkillValidationException("not used");
            }

            @Override
            public String getSkillBasePath(String name) {
                return "db:second";
            }
        };
        Manager manager = manager(List.of(first, second), List.of());
        assertEquals(1, manager.getAllMetadata().size());
        assertEquals("dup 的说明", manager.getAllMetadata().get(0).description());
    }

    @Test
    void unknownSkillReportsNotFound() {
        Manager manager = manager(List.of(fakeSource("a")), List.of());
        Skill.SkillValidationException e = assertThrows(Skill.SkillValidationException.class,
                () -> manager.loadSkill("missing"));
        assertTrue(e.getMessage().contains("not found"), e.getMessage());
    }

    @Test
    void disabledManagerHasNoMetadataAndRejectsLoads() {
        Manager manager = new Manager(new Manager.ManagerConfig(List.of(fakeSource("a")), List.of(), false));
        manager.initialize();
        assertNull(manager.getAllMetadata());
        assertThrows(Skill.SkillValidationException.class, () -> manager.loadSkill("a"));
    }
}
