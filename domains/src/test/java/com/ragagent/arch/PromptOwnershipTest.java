package com.ragagent.arch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 提示词单源守门（F 波，配套 {@code common.prompt.PromptSource}）：
 *
 * <ol>
 *   <li><b>A1 归属</b>：11 个模板文件名的字符串字面量只允许出现在
 *       {@code common/prompt}（唯一权威包）——别的域再写一份就是"第二份清单"回潮；</li>
 *   <li><b>A2 残迹</b>：已废弃的旧模板目录 {@code agent/management/prompt_templates} 零残留；</li>
 *   <li><b>A3 装载方式</b>：模板资源只能经 {@code PromptSource} 打开，
 *       禁止任何地方自拼 {@code getResourceAsStream("prompts/…")} 之类的路径。</li>
 * </ol>
 *
 * <p>与 {@code ClasspathResourcesTest} 分工：那边守"资源真的在 classpath 上且非空"
 * （打包面），这边守"谁有权引用它"（归属面）。</p>
 */
class PromptOwnershipTest {

    /** 仓库根（Gradle 测试工作目录 = 模块目录）。 */
    private static final Path REPO = Path.of("..").toAbsolutePath().normalize();

    /** 唯一权威包：模板文件名与目录字符串只允许出现在这里。 */
    private static final Path ALLOWED_PACKAGE =
            REPO.resolve("common/src/main/java/com/ragagent/common/prompt");

    private static final List<String> MODULES = List.of("common", "engine", "domains", "boot");

    private static final List<String> TEMPLATE_NAMES = List.of(
            "agent_system_prompt.yaml", "system_prompt.yaml", "context_template.yaml",
            "fallback.yaml", "generate_summary.yaml", "generate_session_title.yaml",
            "rewrite.yaml", "graph_extraction.yaml", "generate_questions.yaml",
            "keywords_extraction.yaml", "intent_prompts.yaml");

    /** 自拼模板资源路径（绕开 PromptSource）的形态。 */
    private static final Pattern HAND_ROLLED_OPEN =
            Pattern.compile("getResourceAsStream\\(\\s*\"[^\"]*(?:prompts?/|prompt_templates)");

    /** 本守门测试自身（说明文字里必然出现被禁字符串，扫描时跳过）。 */
    private static final Path SELF =
            REPO.resolve("domains/src/test/java/com/ragagent/arch/PromptOwnershipTest.java");

    private static boolean isSelf(Path file) {
        return SELF.equals(file.toAbsolutePath().normalize());
    }

    @Test
    @DisplayName("A1：模板文件名字面量只能出现在 common/prompt（唯一权威包）")
    void templateNamesOnlyInAuthorityPackage() throws IOException {
        List<String> hits = scan(codeRoots("main"), (file, line) ->
                !file.toAbsolutePath().normalize().startsWith(ALLOWED_PACKAGE)
                        && TEMPLATE_NAMES.stream().anyMatch(name -> line.contains('"' + name + '"')));
        assertThat(hits)
                .as("模板文件名出现第二份来源——请改引 PromptSource 常量（A1）")
                .isEmpty();
    }

    @Test
    @DisplayName("A2：旧模板目录（已下沉 :common/prompts）零残留")
    void legacyTemplateDirectoryGone() throws IOException {
        List<String> hits = scan(codeRoots("main", "test", "testFixtures"),
                (file, line) -> !isSelf(file) && line.contains("agent/management/prompt_templates"));
        assertThat(hits)
                .as("旧模板目录字符串回潮——模板已下沉 :common 的 prompts/（A2）")
                .isEmpty();
    }

    @Test
    @DisplayName("A3：模板资源必须经 PromptSource 打开（禁止自拼 classpath 路径）")
    void templatesOpenedThroughPromptSourceOnly() throws IOException {
        List<String> hits = scan(codeRoots("main"),
                (file, line) -> HAND_ROLLED_OPEN.matcher(line).find());
        assertThat(hits)
                .as("发现自拼模板路径的 getResourceAsStream —— 必须改走 PromptSource（A3）")
                .isEmpty();
    }

    /** 逐行扫描给定根下的 Java 源码，收集命中（"相对路径:行号 行内容"）。 */
    private static List<String> scan(List<Path> roots, BiPredicate<Path, String> match) throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                List<Path> files = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
                for (Path file : files) {
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        if (match.test(file, line)) {
                            hits.add(REPO.relativize(file.toAbsolutePath().normalize()) + ":" + (i + 1)
                                    + "  " + line.trim());
                        }
                    }
                }
            }
        }
        return hits;
    }

    /** 拼接各模块（或指定源码集）的源码根。 */
    private static List<Path> codeRoots(String... sourceKinds) {
        List<Path> roots = new ArrayList<>();
        for (String module : MODULES) {
            for (String kind : sourceKinds) {
                roots.add(REPO.resolve(module + "/src/" + kind + "/java"));
            }
        }
        return roots;
    }
}
