package com.ragagent.arch;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 后端源码根（多模块、**仓库根定位**）的单一事实来源 —— B165 起是跨模块测试夹具。
 *
 * <p>为什么不用相对路径：测试的工作目录是本模块目录，而架构规则要同时扫
 * `server` / `common` / `engine` / `boot` 四个模块；写死 `src/…` + `../common/…`
 * 的写法在测试搬家后立刻失效（B164/B165 实测踩到）。这里从工作目录向上找
 * `settings.gradle.kts` 定位仓库根，再枚举各模块。
 */
public final class SourceRoots {

    private SourceRoots() {}

    private static final String[] MODULES = {"domains", "common", "engine", "boot"};

    public static Path repoRoot() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("settings.gradle.kts"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("找不到仓库根（settings.gradle.kts）");
        }
        return p;
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
