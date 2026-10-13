package com.ragagent.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 阶段 5：`controller` 包只做**协议适配**（路由 + 请求/响应壳），编排类另放。
 *
 * <p>验收口径：`controller` 包内无 `Executor` / `Parser` / `Orchestrator` 类——这三类名字
 * 正是"业务编排藏在控制器包里"的信号（QA 轮的解析、SSE 编排、整轮执行此前都在这儿）。</p>
 */
class SessionControllerPackagingTest {

    private static final Path CONTROLLER_DIR =
            Path.of("src/main/java/com/ragagent/session/controller");
    private static final Pattern ORCHESTRATION_CLASS =
            Pattern.compile("(Executor|Parser|Orchestrator)\\.java$");

    @Test
    @DisplayName("session/controller 包内不得有 Executor / Parser / Orchestrator 类")
    void controllerPackageHoldsNoOrchestrationClass() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(CONTROLLER_DIR)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> ORCHESTRATION_CLASS.matcher(p.getFileName().toString()).find())
                    .forEach(p -> offenders.add(p.getFileName().toString()));
        }

        assertThat(offenders)
                .as("编排类请放 `com.ragagent.session.qa`（controller 只做协议适配）")
                .isEmpty();
    }
}
