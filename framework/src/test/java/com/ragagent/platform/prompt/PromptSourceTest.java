package com.ragagent.platform.prompt;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 提示词模板单一装载器的守门：清单 ⇄ 资源目录双向一致、每份可读且非空、
 * 模板 id 全仓唯一（保证"首个 id 命中"与文件顺序无关）。
 */
class PromptSourceTest {

    /** 资源目录（Gradle 测试工作目录 = 模块根）。 */
    private static final Path DIR = Path.of("src/main/resources/prompts");

    /** 模板 id 行（{@code - id: xxx} / {@code id: xxx}，兼容引号）。 */
    private static final Pattern ID_LINE =
            Pattern.compile("^\\s*-?\\s*id:\\s*[\"']?([A-Za-z0-9_.\\-]+)", Pattern.MULTILINE);

    @Test
    @DisplayName("清单与资源目录双向一致：新增/改名模板不登记即红")
    void manifestMatchesDirectory() throws Exception {
        assertTrue(Files.isDirectory(DIR), "资源目录不存在：" + DIR.toAbsolutePath());
        List<String> onDisk = new ArrayList<>();
        try (var stream = Files.list(DIR)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".yaml"))
                    .forEach(p -> onDisk.add(p.getFileName().toString()));
        }
        onDisk.sort(String::compareTo);
        List<String> manifest = new ArrayList<>(PromptSource.files());
        manifest.sort(String::compareTo);
        assertEquals(onDisk, manifest, "PromptSource.files() 与 resources/prompts/ 的实际文件不一致");
    }

    @Test
    @DisplayName("每份模板可从 classpath 读到且非空")
    void everyTemplateReadableAndNotEmpty() throws Exception {
        for (String file : PromptSource.files()) {
            try (InputStream in = PromptSource.open(file)) {
                assertNotNull(in, "不在 classpath：" + PromptSource.DIR + file);
                assertTrue(in.read() != -1, "0 字节空文件（会让解析静默降级）：" + file);
            }
            assertTrue(!PromptSource.read(file).isEmpty(), "read() 返回空：" + file);
        }
    }

    @Test
    @DisplayName("模板 id 跨文件唯一：FindTemplateByID 的首个命中与文件顺序无关")
    void templateIdsGloballyUnique() {
        Map<String, String> owner = new HashMap<>();
        List<String> dups = new ArrayList<>();
        for (String file : PromptSource.files()) {
            Matcher m = ID_LINE.matcher(PromptSource.read(file));
            while (m.find()) {
                String prev = owner.putIfAbsent(m.group(1), file);
                if (prev != null) {
                    dups.add(m.group(1) + "（" + prev + " / " + file + "）");
                }
            }
        }
        assertTrue(dups.isEmpty(), "模板 id 重复，需显式决定查找序：" + dups);
    }
}
