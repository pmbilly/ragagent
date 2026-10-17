package com.ragagent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.ragagent.agent.skills.Manager;
import com.ragagent.agent.skills.Skill;
import com.ragagent.agent.skills.SkillSource;
import com.ragagent.contract.agent.PinnedSkillInfo;
import com.ragagent.contract.agent.PinnedSkillInstructions;
import com.ragagent.contract.agent.SkillCatalogPort;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 装配层接线：@ 点名的技能正文被解析出来并随系统提示词注入；
 * must_use 的措辞随之切换（注入成功 → 直接照做；注入失败 → 回退按需 read_file）。
 *
 * <p>模型侧行为由 {@code SkillReadFileToolTest} 与录像契约覆盖；本类钉住装配层的取数与措辞。</p>
 */
class SkillPinnedInstructionsTest {

    private static final class FakeSource implements SkillSource {

        private final boolean broken;

        FakeSource(boolean broken) {
            this.broken = broken;
        }

        @Override
        public List<Skill.SkillMetadata> discoverSkills() {
            return List.of(new Skill.SkillMetadata("kb-faq-curator", "整理 FAQ", "skill://kb-faq-curator"));
        }

        @Override
        public Skill loadSkillInstructions(String name) {
            if (broken || !"kb-faq-curator".equals(name)) {
                throw new SkillCatalogPort.SkillValidationException("skill not found: " + name);
            }
            return Skill.parseSkillFile("""
                    ---
                    name: 'kb-faq-curator'
                    slug: 'kb-faq-curator'
                    description: '整理 FAQ'
                    ---

                    一个问答只覆盖一个事实点。
                    """);
        }

        @Override
        public Skill.SkillFile loadSkillFile(String name, String relativePath) {
            throw new SkillCatalogPort.SkillValidationException("file not found in skill: " + relativePath);
        }

        @Override
        public List<String> listSkillFiles(String name) {
            return new ArrayList<>(List.of(SkillCatalogPort.SKILL_FILE_NAME));
        }

        @Override
        public String getSkillBasePath(String name) {
            return "skill://" + name;
        }
    }

    private static Manager manager(boolean enabled, boolean broken) {
        Manager manager = new Manager(new Manager.ManagerConfig(
                List.of(new FakeSource(broken)), List.of(), enabled));
        manager.initialize();
        return manager;
    }

    private static List<PinnedSkillInfo> pinned() {
        return List.of(new PinnedSkillInfo("kb-faq-curator", "整理 FAQ"));
    }

    @Test
    void pinnedSkillBodyIsResolvedFromCatalog() {
        List<PinnedSkillInstructions> resolved =
                ConversationAssembler.resolvePinnedSkillInstructions(manager(true, false), pinned());

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).name()).isEqualTo("kb-faq-curator");
        assertThat(resolved.get(0).instructions()).contains("一个问答只覆盖一个事实点");

        // 注入段渲染出正文
        String section = AgentPrompts.formatPinnedSkillInstructions(resolved);
        assertThat(section).contains("<skillInstructions source=\"selected_for_this_turn\">");
        assertThat(section).contains("<skill name=\"kb-faq-curator\">");
        assertThat(section).contains("一个问答只覆盖一个事实点");
    }

    @Test
    void unreadablePinnedSkillIsSkippedNotFatal() {
        // 技能在提问后被删/改名：不注入、不抛错（该条回退按需读取）
        assertThat(ConversationAssembler.resolvePinnedSkillInstructions(manager(true, true), pinned())).isEmpty();
        // 技能关着：同样不注入
        assertThat(ConversationAssembler.resolvePinnedSkillInstructions(manager(false, false), pinned())).isEmpty();
        assertThat(ConversationAssembler.resolvePinnedSkillInstructions(null, pinned())).isEmpty();
        assertThat(ConversationAssembler.resolvePinnedSkillInstructions(manager(true, false), null)).isEmpty();
    }

    @Test
    void mustUseWordingFollowsInjectionResult() {
        String injected = ConversationAssembler.buildMustUseBlock(List.of(), pinned(), List.of("kb-faq-curator"));
        assertThat(injected)
                .contains("Apply the instructions of @Skill \"kb-faq-curator\" (provided in <skillInstructions>)")
                .doesNotContain("Must call read_file");

        String fallback = ConversationAssembler.buildMustUseBlock(List.of(), pinned(), List.of());
        assertThat(fallback)
                .contains("Must call read_file(path=\"skill://kb-faq-curator/SKILL.md\")");
    }
}
