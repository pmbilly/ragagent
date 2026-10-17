#!/usr/bin/env python3
"""日志级别守卫：warn 只留给「需要人处理」的失败。

用法：
    python3 scripts/check-log-level.py

## 拦什么

`log.warn` 的文案里出现 **404 语义或跳过语义**：

  * `not found` / `不存在` —— 客户端查了个不存在的东西，属正常业务分支；
  * `skip` / `ignoring` —— 空数据、未装配时的主动跳过。

这两类应当 `log.debug`（排查时开）而不是 `log.warn`（默认可见）——否则真告警会被淹。
含 `failed` / `error` / `timeout` 的文案不拦：那些是要人处理的。

## 为什么不做「warn 总数」门

warn 数量取决于业务规模，不是质量问题；按语义分级才是。
"""

import pathlib
import re
import sys

import _source_roots as _sr

ROOTS = [d for _, _, d in _sr.java_roots(("main",))]
WARN = re.compile(r'log\.warn\(\s*"([^"]{0,200})')
BAD = re.compile(r"not found|不存在|skip|ignor", re.I)
OK = re.compile(r"failed|error|timeout|超时|失败", re.I)


def main() -> int:
    hits = []
    for root in ROOTS:
        for f in sorted(root.rglob("*.java")):
            for i, line in enumerate(f.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
                m = WARN.search(line)
                if not m:
                    continue
                text = m.group(1)
                if BAD.search(text) and not OK.search(text):
                    hits.append(f"{f}:{i}: {text[:80]}")
    if hits:
        print(f"✗ 日志级别守卫失败：{len(hits)} 条 warn 用了正常分支语义")
        for h in hits[:20]:
            print("   " + h)
        print("  处理：404/跳过类改 log.debug；只有需要人处理的失败才用 warn。")
        return 1
    print("✓ 日志级别：warn 未用于 404 / 跳过语义")
    return 0


if __name__ == "__main__":
    sys.exit(main())
