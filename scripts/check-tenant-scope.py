#!/usr/bin/env python3
"""租户作用域守卫：跨线程身份必须走 TenantScope，禁止裸 capture/replay。

判据（硬零）：
  T1 主码里不得出现 `.replay()`——唯一允许的实现是 TenantScope。
  T2 出现 `TenantContextSnapshot.capture()` 的文件必须同时使用 `TenantScope.`，
     否则就是把快照手工传进线程/任务，退出时没有恢复。

红态探针：在 domains 主码临时放一个 `snap.replay()` 或只 capture 不引 TenantScope
的文件，本脚本必须非零退出；跑完删除探针。
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
ROOTS = [
    ROOT / "domains" / "src" / "main" / "java",
    ROOT / "agent-engine" / "src" / "main" / "java",
    ROOT / "retrieval-engine" / "src" / "main" / "java",
    ROOT / "model-runtime" / "src" / "main" / "java",
    ROOT / "platform" / "src" / "main" / "java",
    ROOT / "boot" / "src" / "main" / "java",
]
SCOPE_FILE = "TenantScope.java"

COMMENT_BLOCK_RE = re.compile(r"/\*.*?\*/", re.S)
COMMENT_LINE_RE = re.compile(r"//[^\n]*")
REPLAY_RE = re.compile(r"\.replay\s*\(")
CAPTURE_RE = re.compile(r"TenantContextSnapshot\s*\.\s*capture\s*\(")


def strip_comments(text: str) -> str:
    return COMMENT_LINE_RE.sub("", COMMENT_BLOCK_RE.sub("", text))


def java_files() -> list[pathlib.Path]:
    files = []
    for root in ROOTS:
        files.extend(p for p in root.rglob("*.java") if "/build/" not in str(p))
    return sorted(files)


def main() -> int:
    problems = []
    capture_files = 0
    for path in java_files():
        if path.name == SCOPE_FILE:
            continue
        code = strip_comments(path.read_text(encoding="utf-8"))
        rel = path.relative_to(ROOT)
        if REPLAY_RE.search(code):
            problems.append(f"T1 {rel}: 出现裸 .replay()；改用 TenantScope.run/supply/callable")
        if CAPTURE_RE.search(code):
            capture_files += 1
            if "TenantScope." not in code:
                problems.append(f"T2 {rel}: 有 capture() 但未使用 TenantScope")

    if problems:
        print("✗ 租户作用域守卫失败：")
        for problem in problems:
            print("   " + problem)
        print("  跨虚拟线程的身份传递只允许经 platform/access/TenantScope。")
        return 1

    print(f"✓ 租户作用域：{capture_files} 个 capture 站点均经 TenantScope；无裸 replay")
    return 0


if __name__ == "__main__":
    sys.exit(main())
