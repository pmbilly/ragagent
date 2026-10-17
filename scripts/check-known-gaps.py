#!/usr/bin/env python3
"""已知缺口守卫：类注释声明「不支持」时必须指向 `docs/known-gaps.md`。

用法：
    python3 scripts/check-known-gaps.py

## 为什么

`未实现` / `暂缺` / `未接线` 散落在类注释里时，新人分不清「设计取舍」与「缺陷」，
也找不到替代路径。统一登记到 `docs/known-gaps.md`，类注释只留一行指针。

## 判据

类/接口/枚举/record 的**类级 Javadoc** 里出现 `未实现|暂缺|未接线|不实现|规划中` 时，
同一注释块必须含 `known-gaps`。方法内注释不在范围内（大量正常降级语义）。
"""

import pathlib
import re
import sys

import _source_roots as _sr

ROOTS = [d for _, _, d in _sr.java_roots(("main",))]
GAP = re.compile(r"未实现|暂缺|未接线|不实现|规划中")
TYPE = re.compile(r"^\s*(?:public\s+|final\s+|abstract\s+|sealed\s+)*(?:class|interface|enum|record)\s+\w+")


def main() -> int:
    hits = []
    for root in ROOTS:
        for f in sorted(root.rglob("*.java")):
            lines = f.read_text(encoding="utf-8", errors="replace").splitlines()
            for i, line in enumerate(lines):
                if not TYPE.match(line):
                    continue
                j = i - 1
                while j >= 0 and lines[j].strip() == "":
                    j -= 1
                if j < 0 or not lines[j].strip().endswith("*/"):
                    continue
                k = j
                while k >= 0 and not lines[k].strip().startswith("/**"):
                    k -= 1
                if k < 0:
                    continue
                # 中间必须全是注释行（避免把字段/方法的 javadoc 当成类级注释）
                if any(not (ln.strip() == "" or ln.lstrip().startswith(("*", "/*", "//")))
                       for ln in lines[k:j + 1]):
                    continue
                block = "\n".join(lines[k:j + 1])
                if GAP.search(block) and "known-gaps" not in block:
                    flat = " ".join(x.strip() for x in block.splitlines())
                    hits.append(f"{f}:{i + 1}: {flat[:90]}")
    if hits:
        print(f"✗ 已知缺口守卫失败：{len(hits)} 处类注释声明了缺口但未指向 docs/known-gaps.md")
        for h in hits:
            print("   " + h)
        print("  处理：在 docs/known-gaps.md 登记该能力，类注释加「见 docs/known-gaps.md §N」。")
        return 1
    print("✓ 已知缺口：类注释的缺口声明均指向 docs/known-gaps.md")
    return 0


if __name__ == "__main__":
    sys.exit(main())
