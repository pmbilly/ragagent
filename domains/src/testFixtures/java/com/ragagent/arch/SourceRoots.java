package com.ragagent.arch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 后端源码根（多模块、**仓库根定位**）的单一事实来源 —— 跨模块测试夹具（testFixtures）。
 *
 * <p>为什么不用相对路径：测试的工作目录是本模块目录，而架构规则要同时扫多个模块；
 * 写死 `src/…` 的写法在测试搬家后立刻失效（实测踩到）。这里从工作目录向上找构建根
 * （{@code settings.gradle.kts} 或带 {@code <modules>} 的父 pom）定位仓库根，再枚举各模块。</p>
 */
public final class SourceRoots {

    private SourceRoots() {}

    /** 架构测试实际扫描的模块（`common`/`engine` 已解散，从列表移除；扩面需先核过规则覆盖）。 */
    private static final String[] MODULES = {"domains", "boot"};

    public static Path repoRoot() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !isBuildRoot(p)) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("找不到仓库根（settings.gradle.kts 或带 modules 的 pom.xml）");
        }
        return p;
    }

    /** 构建根：Gradle 看 settings.gradle.kts；Maven 看**父 pom**（子模块 pom 也含 pom.xml，会误判）。 */
    private static boolean isBuildRoot(Path dir) {
        if (Files.exists(dir.resolve("settings.gradle.kts"))) {
            return true;
        }
        Path pom = dir.resolve("pom.xml");
        if (!Files.exists(pom)) {
            return false;
        }
        try {
            return Files.readString(pom).contains("<modules>");
        } catch (IOException e) {
            return false;
        }
    }

    /** 返回 [(源集, 模块名, <模块>/src/<源集>/java)]，只含实际存在的目录。 */
    public static List<Path> backend(String sourceSet) {
        Path root = repoRoot();
        List<Path> out = new ArrayList<>();
        for (String m : MODULES) {
            Path d = root.resolve(m).resolve("src").resolve(sourceSet).resolve("java");
            if (Files.isDirectory(d)) {
                out.add(d);
            }
        }
        return out;
    }
}
