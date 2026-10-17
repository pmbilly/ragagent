#!/usr/bin/env python3
"""注释装饰守卫：禁止整行横线分隔。

用法：
    python3 scripts/check-comment-decor.py

## 拦什么

整行只有装饰横线的注释：

    // ══════════════════════════ 同步控制 ══════════════════════════
    // ==================== ImageInfo ====================
    // ----------------------------------------

统一改成 `// ── 段名 ──`：分段意图保留，横线长度不再随编辑漂移。

## 为什么

横线分隔线在 diff 里会整行失效、在窄窗口里折行，且 491 处纯装饰行没有任何信息。
本守卫只拦"整行横线"，不拦正文里的分隔符。
"""

import pathlib
import re
import sys

import _source_roots as _sr

ROOTS = [d for _, _, d in _sr.java_roots(("main",))]
DECOR = re.compile(r"^\s*//\s*(?:[═=━]{4,}|[-─—_]{6,})")


def main() -> int:
    hits = []
    for root in ROOTS:
        for f in sorted(root.rglob("*.java")):
            for i, line in enumerate(f.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
                if DECOR.match(line):
                    hits.append(f"{f}:{i}: {line.strip()[:80]}")
    if hits:
        print(f"✗ 注释装饰守卫失败：{len(hits)} 处整行横线")
        for h in hits[:20]:
            print("   " + h)
        print("  改成 `// ── 段名 ──`；分段意图保留，横线不要拉长。")
        return 1
    print("✓ 注释装饰：无整行横线分隔")
    return 0


if __name__ == "__main__":
    sys.exit(main())
