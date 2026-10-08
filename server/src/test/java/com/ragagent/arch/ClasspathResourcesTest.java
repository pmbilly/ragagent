package com.ragagent.arch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * classpath 资源存在性断言（B118，对应 phase4 方案 §5.2 风险 #2）。
 *
 * <h2>为什么需要它</h2>
 * <p>主源码共 15 处 classpath 资源读取，其中 **13 处缺资源不报错**——写法是
 * {@code if (in == null) return/continue}。也就是说资源一旦"没跟着模块走"，症状不是异常而是
 * <b>功能悄悄降级</b>：模板为空、内置 agent 列表为空、jieba 分词退化、甚至
 * {@code META-INF/spring.factories} 里的 EnvironmentPostProcessor 不注册。</p>
 *
 * <p>B116 把 {@code common}/{@code event} 抽成 {@code :common} 模块时已经搬过一批资源
 * （{@code common/text/*.txt}），这类"搬家没带上资源"的风险从此刻起是**真实**的。</p>
 *
 * <h2>两条断言</h2>
 * <ol>
 *   <li><b>清单存在性</b>：{@link #RESOURCES} 里每个路径都必须能从 classpath 读到，
 *       且<b>不能是 0 字节</b>（空 yaml 同样会静默降级）；</li>
 *   <li><b>反漂移</b>：源码里每个 {@code getResourceAsStream("字面量")} 必须是清单里的精确路径，
 *       或清单里某条路径的目录前缀——新增资源没登记就会红。</li>
 * </ol>
 *
 * <p><b>维护方式</b>：新增 classpath 资源时在 {@link #RESOURCES} 加一行（写清消费方），
 * 两条断言自动覆盖；不需要改别的。</p>
 *
 * <p>与 {@link ArchitectureRulesTest} 的分工：那边是<b>代码级</b>规则（注解/JDBC/分层），
 * 这里是<b>打包级</b>规则（资源是否真的躺在 classpath 上）。同为 {@code com.ragagent.arch}
 * 下的守卫。</p>
 */
class ClasspathResourcesTest {

    /**
     * 资源路径 → 消费方（供报错时定位）。
     *
     * <p>失败于本清单某一条时，先看消费方的读法：{@code Class.getResourceAsStream("…")}
     * 是类相对（容易被搬家打断），{@code getClassLoader().getResourceAsStream("…")} 是
     * classloader 绝对（搬家无感）——B116 已逐个核实，本仓全部是后者（或 {@code "/…"} 绝对形式）。</p>
     */
    private static final Map<String, String> RESOURCES = new LinkedHashMap<>();

    static {
        // ── 提示词模板：三处文件列表都在读同一个目录（ConversationProperties 11 /
        //    BuiltinAgentRegistry 11 / PromptTemplateCatalog 9），任一缺失都是静默降级 ──
        for (String f : List.of("agent_system_prompt", "system_prompt", "context_template",
                "fallback", "generate_summary", "generate_session_title", "rewrite",
                "graph_extraction", "generate_questions", "keywords_extraction", "intent_prompts")) {
            RESOURCES.put("agent/management/prompt_templates/" + f + ".yaml",
                    "ConversationProperties/BuiltinAgentRegistry/PromptTemplateCatalog/QaWiring 等");
        }
        RESOURCES.put("agent/management/builtin_agents.yaml", "BuiltinAgentRegistry#load");
        RESOURCES.put("agent/management/agent_type_presets.yaml", "AgentTypePresets 构造器");
        // ── 其余按域 ──
        RESOURCES.put("initialization/extract_config.yaml", "llm.extract.ExtractPrompts");
        RESOURCES.put("initialization/asr_test.wav", "initialization.AsrTestAudio（fail-fast，仍纳入）");
        RESOURCES.put("dataset/samples.json", "evaluation.DatasetService");
        RESOURCES.put("jieba/hmm_model.json", "retrieval…JiebaTokenizer");
        RESOURCES.put("common/text/TSPhrases.txt", "common.text.TextConv（fail-fast，仍纳入）");
        RESOURCES.put("common/text/TSCharacters.txt", "common.text.TextConv（fail-fast，仍纳入）");
        RESOURCES.put("META-INF/spring.factories", "AppEnvLookupEnvironmentPostProcessor 注册（EPP 不注册=env 后处理失效）");
    }

    /** {@code getResourceAsStream("字面量")}——只看单字面量实参，拼接形式（{@code DIR + file}）天然不命中。 */
    private static final Pattern LITERAL_ARG = Pattern.compile("getResourceAsStream\\(\\s*\"([^\"]+)\"");

    @Test
    @DisplayName("R12a：清单里的每个 classpath 资源都必须真实存在且非空")
    void resourcesExistAndAreNotEmpty() {
        ClassLoader cl = ClasspathResourcesTest.class.getClassLoader();
        List<String> problems = new ArrayList<>();
        RESOURCES.forEach((path, consumer) -> {
            try (InputStream in = cl.getResourceAsStream(path)) {
                if (in == null) {
                    problems.add(path + "（" + consumer + "）：不在 classpath 上");
                } else if (in.read() == -1) {
                    problems.add(path + "（" + consumer + "）：是 0 字节空文件（会让解析静默降级）");
                }
            } catch (Exception e) {
                problems.add(path + "（" + consumer + "）：读取失败 " + e);
            }
        });
        assertThat(problems).as("classpath 资源缺失——多数消费方是 if (in == null) 静默降级，不会抛异常")
                .isEmpty();
    }

    @Test
    @DisplayName("R12b：源码里的 getResourceAsStream 字面量必须在清单里登记")
    void literalsAreInventoried() throws Exception {
        List<String> unregistered = new ArrayList<>();
        for (Path root : ArchitectureRulesTest.backendSourceRoots("main/java")) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var walk = Files.walk(root)) {
                for (Path file : walk.filter(Files::isRegularFile)
                        .filter(f -> f.toString().endsWith(".java")).toList()) {
                    Matcher m = LITERAL_ARG.matcher(Files.readString(file));
                    while (m.find()) {
                        String literal = m.group(1).replaceFirst("^/", "");
                        boolean known = RESOURCES.containsKey(literal)
                                || RESOURCES.keySet().stream().anyMatch(k -> k.startsWith(literal));
                        if (!known) {
                            unregistered.add(file + " → \"" + m.group(1) + "\"");
                        }
                    }
                }
            }
        }
        assertThat(unregistered)
                .as("新增/改动的资源路径未登记进 ClasspathResourcesTest.RESOURCES（登记后 R12a 会自动校验存在性）")
                .isEmpty();
    }
}
