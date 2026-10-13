#!/usr/bin/env python3
"""路径/包对应守卫（B263㉖）：Java 文件的**目录**必须与它声明的 `package` 一致。

背景（判据来自一次真实事故）：B259 把 `com.ragagent.stream` 改成 `com.ragagent.platform.stream` 时，
脚本只枚举了 `src/main` 与 `src/test` 两个源码集，**漏了 `src/testFixtures`** ⇒
`common/src/testFixtures/java/com/ragagent/stream/EmbeddedRedis.java` 的包声明被改成了
`com.ragagent.platform.stream`，**文件却留在旧目录**里。

它躺了整个阶段 6 没人发现，因为：

  · `javac` **不校验**目录与包的对应关系——它照样把类编到 `com/ragagent/platform/stream/` 下，
    所以**构建、测试、我们的全量闸门全是绿的**；
  · 只有 **IDE**（jdt）报 "declared package does not match the expected package"，而 IDE 报错
    不在任何守卫的输入里 ⇒ 只能等人在编辑器里撞见。

规则（零容忍，无基线）：源码集 `*/src/*/java/**` 下的每个 `.java`，其**相对包根目录**
必须等于包名逐段展开的路径；不一致即红。

排除：`*/build/**`（生成物）、`bin/`。

**换锚/搬包时的配套动作**（本守卫的由来）：
  1. 改包 ⇒ 必须同时搬目录（`git mv`），且**枚举所有源码集**：`main` / `test` / `testFixtures`；
  2. 搬完看一眼本守卫（它会在 IDE 之前先报）。
"""

import glob
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

PKG_RE = re.compile(r"^package\s+([\w.]+)\s*;", re.M)


def expected_dir(rel_path: str, pkg: str) -> str:
    """由 `<mod>/src/<sourceSet>/java/<...>` 推出该包应有的目录前缀。"""
    mod, rest = rel_path.split("/src/", 1)
    source_set = rest.split("/", 1)[0]
    return f"{mod}/src/{source_set}/java/{pkg.replace('.', '/')}/"


def check() -> list[str]:
    problems = []
    pattern = os.path.join(ROOT, "*/src/*/java/**/*.java")
    for abs_path in sorted(glob.glob(pattern, recursive=True)):
        rel = os.path.relpath(abs_path, ROOT)
        if "/build/" in rel or rel.startswith("build/"):
            continue
        with open(abs_path, encoding="utf-8") as fh:
            text = fh.read()
        m = PKG_RE.search(text)
        if not m:
            problems.append(f"P1 {rel}：没有 package 声明（Java 源码必须有）")
            continue
        pkg = m.group(1)
        if not rel.startswith(expected_dir(rel, pkg)):
            problems.append(
                f"P2 {rel}：声明 `package {pkg};` 但目录与包不一致"
                f"（应位于 {expected_dir(rel, pkg)}）——改包必须同时搬目录，"
                f"且记得枚举 testFixtures 等**所有**源码集；javac 不报错、只有 IDE 报"
            )
    return problems


def main() -> int:
    problems = check()
    if problems:
        print("✗ 守卫失败：")
        for p in problems:
            print("   " + p)
        return 1
    print("✓ 守卫通过：所有源码集的目录与包声明一致。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
