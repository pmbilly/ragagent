#!/usr/bin/env python3
"""游离目录守卫（B124）：抓「看不见的目录」。

背景（判据来自一次真实事故）：B108 期间为一次搬迁**预建目标目录**时基准路径写错，
在仓库根留下了一个空的 `webfetch/`（本该是 `server/src/main/java/com/ragagent/webfetch/`）。
它躺了**一周没人发现**，因为：

  · git **对空目录完全无感**——不出现在 `git status`、不进提交、`git ls-files` 也查不到；
  · `.gitignore`/文档/构建文件都没提到它 ⇒ 常规手段全都看不见。

两条规则：
  S1 游离目录：仓库根与各模块根的条目，若**既无被跟踪文件**、**又未被 gitignore**、
     且**不在白名单**里 ⇒ 红。（被忽略的 `build/`、`.gradle/`、`node_modules/` 不算。）
  S2 死包目录：源码树里**整棵子树不含任何 .java 文件**的目录 ⇒ 红。
     （搬迁把文件全搬走后留下的空包目录，同样对 git 不可见。）

白名单只放「有正当理由存在的未忽略本地目录」，每项须写理由——与其它守卫同口径：
**要放过必须说明为什么，而不是静默跳过。**
"""

import subprocess
import sys
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent

# 仓库根：允许存在的未跟踪、未忽略目录（须给理由）
ROOT_ALLOW: dict[str, str] = {}

# 模块根（Gradle 模块 + 独立子项目）：同上
MODULE_ALLOW: dict[str, str] = {}

# 源码树根（S2 扫描范围）
SOURCE_ROOTS = (
    "server/src/main/java",
    "server/src/test/java",
    "common/src/main/java",
    "common/src/test/java",
    "engine/src/main/java",
    "engine/src/test/java",
    "boot/src/main/java",
    "boot/src/test/java",
)


def _git(*args: str) -> str:
    return subprocess.run(["git", *args], capture_output=True, text=True, cwd=ROOT).stdout


def _tracked(path: pathlib.Path) -> bool:
    """该目录下是否有被跟踪的文件。"""
    rel = str(path.relative_to(ROOT))
    return bool(_git("ls-files", "--", rel).strip())


def _ignored(path: pathlib.Path) -> bool:
    rel = str(path.relative_to(ROOT))
    r = subprocess.run(["git", "check-ignore", "-q", rel], cwd=ROOT)
    return r.returncode == 0


def check_stray_dirs() -> list[str]:
    problems = []
    scan = [(ROOT, ROOT_ALLOW)]
    for mod in (ROOT / "server", ROOT / "common", ROOT / "engine", ROOT / "boot", ROOT / "frontend", ROOT / "mcp-server",
                ROOT / "docreader", ROOT / "otlp-proto", ROOT / "migrations", ROOT / "docs",
                ROOT / "scripts"):
        if mod.is_dir():
            scan.append((mod, MODULE_ALLOW))
    for base, allow in scan:
        for child in sorted(base.iterdir()):
            if not child.is_dir() or child.name.startswith("."):
                continue
            if _tracked(child) or _ignored(child):
                continue
            if child.name in allow:
                continue
            problems.append(f"S1 游离目录 {child.relative_to(ROOT)}：无跟踪文件、未被 gitignore、"
                            f"也不在白名单（若是本地临时目录，请加进 .gitignore；"
                            f"若确需保留，请在本脚本白名单登记理由）")
    return problems


def check_dead_packages() -> list[str]:
    problems = []
    for rel in SOURCE_ROOTS:
        base = ROOT / rel
        if not base.is_dir():
            continue
        for d in sorted(base.rglob("*")):
            if not d.is_dir() or d.name.startswith("."):
                continue
            if not any(d.rglob("*.java")):
                problems.append(f"S2 死包目录 {d.relative_to(ROOT)}：整棵子树无 .java 文件"
                                f"（文件已搬走的空包目录对 git 不可见，请删除）")
    return problems


def main() -> int:
    problems = check_stray_dirs() + check_dead_packages()
    if problems:
        print("✗ 守卫失败：")
        for p in problems:
            print("   " + p)
        return 1
    print("✓ 守卫通过：无游离目录、无死包目录。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
