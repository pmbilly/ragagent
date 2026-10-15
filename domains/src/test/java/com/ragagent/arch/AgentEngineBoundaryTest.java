package com.ragagent.arch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Agent 引擎边界守门（A 阶段成果的棘轮，2026-10-12）：
 *
 * <ol>
 *   <li><b>B0 扫描面自检</b>：扫描到的引擎侧源码文件数必须过阈值——防止路径漂移让守卫静默空转；</li>
 *   <li><b>B1 主规则</b>：{@code com.ragagent.agent} 引擎侧（{@code management/} 除外）只允许依赖
 *       <b>引擎底座</b>（L1 {@code :common} 的 {@code common}/{@code platform}（含旧 {@code event}/{@code stream}）
 *       + L2 {@code :engine} 的各能力包）与自身；其余顶层包一律视为<b>业务域</b>；</li>
 *   <li><b>B2 例外登记 + 过期报红</b>：{@code management/}（唯一 HTTP 面，装配租户/空间/会话配置）
 *       按例外排除；一旦它不再依赖任何业务域，说明例外已无必要 ⇒ 本条转红，提示把管理面纳入 B1 扫描；</li>
 *   <li><b>B3 红态探针</b>：判定函数对业务域 import 必红、对底座 import 不误报（证明守卫真的能红）。</li>
 * </ol>
 *
 * <p>为什么要有这条守卫：A 阶段把引擎侧对业务域的 59 处 import 清到 0（websearch→L2 载荷、
 * Chunk 实体→L1 {@code ChunkFacts}、MCP 编排抽端口外移）。但"0 边"目前只是<b>既成事实</b>——
 * 新工具顺手 import 一个业务域类型不会被任何东西拦住。包不构成编译边界，故用本条脚本级守卫钉住。</p>
 *
 * <p>背景与决策见 {@code docs/superpowers/plans/2026-10-12-agent-module-extraction-plan.md}（阶段 A/B）
 * 与 {@code docs/agent-module-guide.md}。</p>
 */
class AgentEngineBoundaryTest {

    /** 仓库根（Gradle 测试工作目录 = 模块目录）。 */
    private static final Path REPO = Path.of("..").toAbsolutePath().normalize();

    /** 引擎侧源码根。 */
    private static final Path ENGINE_ROOT = REPO.resolve("domains/src/main/java/com/ragagent/agent");

    /** 按例外排除的管理面子树（其内部依赖由 B2 单独守）。 */
    private static final String MANAGEMENT_SEGMENT = "/agentmanagement/"; // 改名自 /management/

    /**
     * 引擎底座允许清单（L1 / L2 的顶层包名）+ 自身。
     *
     * <p>清单含义是"引擎侧允许站在什么之上"，不是"当前恰好用到什么"——所以即便当下 0 引用，
     * 底座包（如 {@code embedding}/{@code rerank}/{@code platform}）也登记在册；反过来，
     * 任何新出现的顶层包都要被当作业务域审视一次（这正是本守卫的棘轮作用）。</p>
     */
    private static final Set<String> ALLOWED_TOP_LEVEL = Set.of(
            // 工具适配包改名后与 agent 平级（原 agent.tools 因同属 agent.* 隐式通过）；
            // 真正的解耦（引擎不反向依赖工具实现）见/。
            "agenttools",
            "agent",        // 自身
            "common",       // L1（:common 主包）
            "platform",     // L1（:common 的平台面：B268 起 event/stream/tracing.langfuse 均在此）
            "contract",     // L1（:common 的契约面：A② 起 common/* 分流至此）
            "event",        // L1（:common 的 event 包——现只剩 payload，待迁 com.ragagent.contract.event.payload）
            "stream",       // L1 旧名（已并入 platform.stream，保留登记）
            "llm",          // L2 能力
            "modelcontext", // L2 能力（模型输出上下文协议；物理位于 :engine）
            "retrieval",    // L2 能力
            "embedding",    // L2 能力
            "rerank",       // L2 能力
            "chatpipeline", // L2 能力（快答链路）
            "webfetch",     // L2 能力（抓取支撑）
            "tracing");     // L1 core（:common）+ L2 装饰器（:engine）

    /** {@code com.ragagent.<top>.} 形态的 import（含 static import）。 */
    private static final Pattern RAGAGENT_IMPORT =
            Pattern.compile("^import\\s+(?:static\\s+)?com\\.ragagent\\.([a-zA-Z0-9_]+)\\.");

    /**
 * 扫描面下限（低于此值说明路径漂移，守卫在空转）。
 *
 * <p> 改名后 `agent/tools` 迁出为平级 `agenttools/`（91 文件），引擎侧本面
 * 从 ≈150 降到 ≈60；阈值随之收紧到 55。</p>
 */
    private static final int MIN_SCANNED_FILES = 55;

    @Test
    @DisplayName("B0：扫描面自检（防止路径漂移导致守卫静默空转）")
    void scanSurfaceIsNotEmpty() throws IOException {
        assertThat(engineFiles())
                .as("扫描到的引擎侧源码文件数——低于阈值说明路径写错/目录被搬走，守卫会永远绿（B0）")
                .hasSizeGreaterThan(MIN_SCANNED_FILES);
    }

    @Test
    @DisplayName("B1：引擎侧只依赖引擎底座（L1/L2）+ 自身，不得依赖任何业务域")
    void engineSideDependsOnFoundationOnly() throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path file : engineFiles()) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (isDomainImport(lines.get(i))) {
                    hits.add(REPO.relativize(file) + ":" + (i + 1) + "  " + lines.get(i).trim());
                }
            }
        }
        assertThat(hits)
                .as("引擎侧 import 了业务域包——请改走下层载荷/端口（见 A 阶段三刀：载荷下沉、"
                        + "L1 事实类型、spi 端口）；确属引擎底座的包请登记进 ALLOWED_TOP_LEVEL（B1）")
                .isEmpty();
    }

    @Test
    @DisplayName("B2：management/ 例外仍必要（不再依赖业务域时应转红、把管理面纳入 B1）")
    void managementExceptionStillJustified() throws IOException {
        List<String> domainImports = new ArrayList<>();
        Path management = ENGINE_ROOT.resolve("../agentmanagement").normalize(); // 已迁出 agent/ 子树
        if (Files.isDirectory(management)) {
            try (Stream<Path> walk = Files.walk(management)) {
                for (Path file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                        if (isDomainImport(line)) {
                            domainImports.add(REPO.relativize(file) + "  " + line.trim());
                        }
                    }
                }
            }
        }
        assertThat(domainImports)
                .as("management/ 已不再依赖业务域 ⇒ 例外过期：请把管理面并入 B1 扫描范围并删掉本条（B2）")
                .isNotEmpty();
    }

    @Test
    @DisplayName("B3（红态探针）：判定函数对业务域必红、对底座不误报")
    void predicateTurnsRedOnDomainImports() {
        assertThat(isDomainImport("import com.ragagent.knowledge.service.management.KnowledgeService;")).isTrue();
        // C 批起：持久化实体已**回归各域**（`knowledge.domain.Chunk` 属业务域 ⇒ 必红）；
        // 底座对应面改由 L1 视图承接（`common.knowledge.ChunkRowView` ⇒ 不误报）
        assertThat(isDomainImport("import com.ragagent.knowledge.domain.Chunk;")).isTrue();
        assertThat(isDomainImport("import com.ragagent.common.knowledge.ChunkRowView;")).isFalse();
        assertThat(isDomainImport("import com.ragagent.session.service.QaWiring;")).isTrue();
        assertThat(isDomainImport("import com.ragagent.mcp.domain.McpService;")).isTrue();
        assertThat(isDomainImport("import static com.ragagent.wiki.service.WikiPrompts.GO_SHA256;")).isTrue();
        assertThat(isDomainImport("import com.ragagent.platform.web.JsonMappers;")).isFalse();
        assertThat(isDomainImport("import com.ragagent.modelcontext.Registry;")).isFalse();
        assertThat(isDomainImport("import com.ragagent.platform.event.EventBus;")).isFalse();
        assertThat(isDomainImport("import java.util.List;")).isFalse();
        assertThat(isDomainImport(" * 说明文字里的 com.ragagent.knowledge 不是 import")).isFalse();
    }

    /** 判定：该行是否为"引擎底座之外"的 {@code com.ragagent.*} import（＝业务域）。 */
    static boolean isDomainImport(String line) {
        Matcher m = RAGAGENT_IMPORT.matcher(line);
        return m.find() && !ALLOWED_TOP_LEVEL.contains(m.group(1));
    }

    /** 引擎侧源码文件清单（按例外排除 management 子树）。 */
    private static List<Path> engineFiles() throws IOException {
        try (Stream<Path> walk = Files.walk(ENGINE_ROOT)) {
            return walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().replace('\\', '/').contains(MANAGEMENT_SEGMENT))
                    .sorted()
                    .toList();
        }
    }
}
