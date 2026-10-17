#!/usr/bin/env python3
"""迁移代号守卫：主码注释不再出现内部批次/迁移代号与日期戳。

历史代号（B2xx / K4 / A18 / R3d / W5γ / P3b / O9 / G1 / §8.1 / 2026-10-16 …）
是迁移期的记账，不是当前代码的事实；留在注释里会让新人以为它们仍是有效契约。
`GoRecording*` 是旧夹具名，已行为化改名，禁止回流。

判据（硬零）：
  C1 主码 `src/main` 的注释里不得出现代号/裸日期戳；ISO-8601 时间示例（`2026-09-18T10:00:00Z`）保留。
  C2 任意 `src/test` / `src/testFixtures` 不得再出现 `GoRecording`。

红态探针：临时在主码注释写 `// B999`，本脚本必须非零退出；跑完删除探针。
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MODULES = ["contract", "platform", "model-runtime", "retrieval-engine", "agent-engine", "domains", "boot"]
COMMENT_RE = re.compile(r"//[^\n]*|/\*.*?\*/", re.S)
CODE_RE = re.compile(
    r"\b(?:B\d{2,3}|K\d{1,2}|A\d{2}|R\d[a-z]?|W\d[a-zγ]?|P\d[a-z]?|O\d{1,2}|G\d|D\d|E\d|F\d|C\d)"
    r"(?:[-–][A-Za-z0-9②③④⑤]+)*\b"
    r"|20\d{2}-\d{2}-\d{2}(?!T\d)"
    r"|§\d+(?:\.\d+)*"
)
LEGACY_NAME = "GoRecording"


def sources(module: str, kind: str):
    return (ROOT / module / "src" / kind / "java").rglob("*.java")


def main() -> int:
    problems: list[str] = []

    for module in MODULES:
        for path in sources(module, "main"):
            text = path.read_text(encoding="utf-8")
            found = sorted({m.group(0) for c in COMMENT_RE.findall(text) for m in CODE_RE.finditer(c)})
            if found:
                rel = path.relative_to(ROOT)
                problems.append(f"C1 {rel}: {', '.join(found)}")

    for module in MODULES:
        for kind in ("test", "testFixtures"):
            for path in sources(module, kind):
                if LEGACY_NAME in path.read_text(encoding="utf-8"):
                    problems.append(f"C2 {path.relative_to(ROOT)}: 旧夹具名 {LEGACY_NAME} 回流")

    if problems:
        print("✗ 迁移代号守卫失败：")
        for p in problems:
            print("   " + p)
        print("  注释只讲当前代码；历史代号留在 docs/internal/ 的批次记录里。")
        return 1

    print("✓ 迁移代号：主码注释 0 处代号/日期戳；旧夹具名 0 处")
    return 0


if __name__ == "__main__":
    sys.exit(main())
