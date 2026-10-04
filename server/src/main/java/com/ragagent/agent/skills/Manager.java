package com.ragagent.agent.skills;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * skill 生命周期管理——**指令型（playbook）**。
 *
 * <p>2026-09 裁剪定稿（选项 B）：技能 = 提示词注入的指令文档。模型凭 SKILL.md 指令
 * 用现有工具执行；沙箱镜像源、staging、shell 环境注入随沙箱一起退役（执行型扩展
 * 需求引导走 MCP）。本类只剩三级注入面：元数据目录（Level 1）、完整指令（Level 2）、
 * 附属文件（Level 3）。</p>
 *
 * <p><b>数据源抽象（B57）</b>：由 {@link SkillSource} 列表驱动——入库后生产实现是
 * {@link DbSkillSource}（skills 表）；宿主目录扫描（{@code Loader}）已随“技能入库”退役。
 * 多源时按配置顺序取第一个命中（同名先到先得）。</p>
 *
 * <p>实例是「每次装配一份」（见 {@code AgentEngineAssembler}），因此元数据缓存的生命周期
 * 就是一次问答装配——每轮重读数据库，无需缓存失效机制。</p>
 */
public final class Manager {

    private static final Logger log = LoggerFactory.getLogger(Manager.class);

    private final List<SkillSource> sources;
    private final List<String> allowedSkills;
    private final boolean enabled;

    private final ReentrantReadWriteLock mu = new ReentrantReadWriteLock();
    private List<Skill.SkillMetadata> metadataCache = new ArrayList<>();

    /** Manager 的配置。 */
    public record ManagerConfig(List<SkillSource> sources, List<String> allowedSkills, boolean enabled) {
    }

    public Manager(ManagerConfig config) {
        ManagerConfig cfg = config != null ? config : new ManagerConfig(List.of(), List.of(), false);
        this.sources = cfg.sources() == null ? List.of() : List.copyOf(cfg.sources());
        this.allowedSkills = cfg.allowedSkills() == null ? List.of() : cfg.allowedSkills();
        this.enabled = cfg.enabled();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 发现全部 skills 并缓存元数据；装配时调用。 */
    public void initialize() {
        reload();
    }

    /** 刷新 skill 缓存（重读全部数据源）。 */
    public void reload() {
        if (!enabled) {
            return;
        }
        List<Skill.SkillMetadata> metadata = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SkillSource source : sources) {
            try {
                for (Skill.SkillMetadata meta : source.discoverSkills()) {
                    if (meta != null && seen.add(meta.name())) {
                        metadata.add(meta);
                    }
                }
            } catch (Exception e) {
                // 单源失败不阻断其他源（与宿主目录时代“目录打错字不 500”同口径）
                log.warn("[skills] source discovery failed: {}", e.getMessage());
            }
        }
        if (!allowedSkills.isEmpty()) {
            metadata = filterAllowedSkills(metadata);
        }
        mu.writeLock().lock();
        try {
            metadataCache = metadata;
        } finally {
            mu.writeLock().unlock();
        }
    }

    private List<Skill.SkillMetadata> filterAllowedSkills(List<Skill.SkillMetadata> metadata) {
        if (allowedSkills.isEmpty()) {
            return metadata;
        }
        Set<String> allowedSet = new HashSet<>(allowedSkills);
        List<Skill.SkillMetadata> filtered = new ArrayList<>();
        for (Skill.SkillMetadata meta : metadata) {
            if (allowedSet.contains(meta.name())) {
                filtered.add(meta);
            }
        }
        return filtered;
    }

    /** 全部已发现 skill 的元数据；系统提示词注入用（Level 1）。 */
    public List<Skill.SkillMetadata> getAllMetadata() {
        if (!enabled) {
            return null;
        }
        mu.readLock().lock();
        try {
            return new ArrayList<>(metadataCache);
        } finally {
            mu.readLock().unlock();
        }
    }

    /** 加载一个 skill 的完整指令（Level 2）。 */
    public Skill loadSkill(String skillName) throws Exception {
        requireUsable(skillName);
        return fromSources(source -> source.loadSkillInstructions(skillName));
    }

    /** 读 skill 目录里的一个额外文件（Level 3）。 */
    public String readSkillFile(String skillName, String filePath) throws Exception {
        requireUsable(skillName);
        return fromSources(source -> source.loadSkillFile(skillName, filePath)).content();
    }

    /** 列出 skill 目录的全部文件。 */
    public List<String> listSkillFiles(String skillName) throws Exception {
        requireUsable(skillName);
        if (sources.isEmpty()) {
            return List.of();
        }
        for (SkillSource source : sources) {
            try {
                return source.listSkillFiles(skillName);
            } catch (Skill.SkillValidationException e) {
                // 该源没有这个 skill，换下一个
            }
        }
        throw new Skill.SkillValidationException("skill not found: " + skillName);
    }

    /** 第一个命中的数据源结果；全都不认时抛「找不到」。 */
    private <T> T fromSources(SourceCall<T> call) throws Exception {
        Skill.SkillValidationException notFound = null;
        for (SkillSource source : sources) {
            try {
                return call.apply(source);
            } catch (Skill.SkillValidationException e) {
                notFound = e;
            }
        }
        throw notFound != null ? notFound : new Skill.SkillValidationException("skill not found");
    }

    @FunctionalInterface
    private interface SourceCall<T> {
        T apply(SkillSource source) throws Exception;
    }

    private void requireUsable(String skillName) {
        if (!enabled) {
            throw new Skill.SkillValidationException("skills are not enabled");
        }
        if (!isSkillAllowed(skillName)) {
            throw new Skill.SkillValidationException("skill not allowed: " + skillName);
        }
    }

    private boolean isSkillAllowed(String skillName) {
        if (allowedSkills.isEmpty()) {
            return true;
        }
        for (String name : allowedSkills) {
            if (name.equals(skillName)) {
                return true;
            }
        }
        return false;
    }

    /** 详细信息。 */
    public record SkillInfo(String name, String description, String basePath, String instructions, List<String> files) {
    }

    public SkillInfo getSkillInfo(String skillName) throws Exception {
        requireUsable(skillName);
        Skill skill = loadSkill(skillName);
        List<String> files;
        try {
            files = listSkillFiles(skillName);
        } catch (Exception e) {
            files = List.of(); // 非致命错误
        }
        return new SkillInfo(skill.name, skill.description, skill.basePath, skill.instructions, files);
    }
}
