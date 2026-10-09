package com.ragagent.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 契约金片的**唯一解析入口**（B165 定）。
 *
 * <p>为什么需要它：金片历史上用「模块相对路径 + {@code server/…} 兜底」两候选读**文件路径**
 * （{@code Path.of("src/test/resources/contracts/x.json")}），而集成测试从 {@code :server}
 * 迁到 {@code :boot} 后工作目录变成 {@code boot/} ⇒ 两个候选同时落空（B165 实测 97 处失败）。
 * 另有一大批调用点走**类路径**读（{@code ClassPathResource("contracts/…")}）。两套读法并存
 * 本身就是脆弱源 ✗，故统一到本类：从工作目录向上找 {@code settings.gradle.kts} 定位仓库根，
 * 再按候选目录顺序解析 —— 与模块、与 CWD 都无关。
 */
public final class ContractPaths {

    private ContractPaths() {}

    /** 候选目录（相对仓库根，按优先级）。 */
    private static final List<String> DIRS = List.of(
            "server/src/testFixtures/resources/contracts",
            "server/src/test/resources/contracts",
            "src/test/resources/contracts");

    private static Path repoRoot() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("settings.gradle.kts"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("找不到仓库根（settings.gradle.kts）");
        }
        return p;
    }

    /** 解析单个金片文件（保留原调用点的文件名语义）。 */
    public static Path resolve(String name) {
        Path root = repoRoot();
        for (String d : DIRS) {
            Path f = root.resolve(d).resolve(name);
            if (Files.exists(f)) {
                return f;
            }
        }
        throw new IllegalStateException("金片文件找不到：" + name + "（候选目录：" + DIRS + "，仓库根：" + root + "）");
    }

    /** 解析 {@code contractsDir} 形态的调用点（{@code src/test/resources/contracts}）下的金片。 */
    public static Path resolveIn(String contractsDir, String name) {
        return resolve(name);
    }

    /** 写场景（REFRESH 重录）：返回金片目录下的目标路径，不要求已存在。 */
    public static Path resolveForWrite(String name) {
        return dir().resolve(name);
    }

    /** 金片目录（供需要目录本身的调用点）。 */
    public static Path dir() {
        Path root = repoRoot();
        for (String d : DIRS) {
            Path f = root.resolve(d);
            if (Files.isDirectory(f)) {
                return f;
            }
        }
        throw new IllegalStateException("金片目录找不到（候选：" + DIRS + "）");
    }
}
