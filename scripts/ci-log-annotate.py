#!/usr/bin/env python3
"""把失败构建日志的**关键行**变成注解（B175）—— B171 的同一手法，从「测试」扩到「整个构建」。

起因：CI 的 `Build & test` 步骤失败 ✗，而**测试汇总里一条失败用例都没有** ✗（B174 之后那次 run ✓）
—— 说明失败不在断言里（worker 崩溃 / 编译 / OOM 之类 ✓），可完整日志要鉴权才看得到 ✗。
于是让构建也自证：CI 用 `tee` 把 Gradle 输出落盘 ✓，本脚本挑出关键行发 `::error` 注解 ✓
⇒ 这些行在 job 页可见，也能用公开 API 读（`/check-runs/{id}/annotations`）✓。

用法：python3 scripts/ci-log-annotate.py /tmp/build.log [--max-lines 12]
      （找不到文件或没有关键行 ⇒ 安静退出 0 ✓，供 CI 的 failure() 步用）
"""
from __future__ import annotations

import os
import sys

# 这些行是"为什么会红"的直接线索；顺序即优先级。
ANCHORS = (
    "What went wrong",
    "Execution failed for task",
    "There were failing tests",
    "OutOfMemoryError",
    "Gradle Test Executor",
    "Process 'Gradle",
    "Daemon disappeared",
    "Could not resolve",
    "error:",
    "Caused by:",
    "FAILURE:",
    "BUILD FAILED",
    "Exit value",
    "> Task :",
)
# 命中这些词的行才值得从 "> Task :" 里挑出来（否则该锚点噪音太大）。
TASK_NOISE_OK = ("FAILED",)


def main() -> int:
    if len(sys.argv) < 2:
        print("用法：python3 scripts/ci-log-annotate.py <build.log>")
        return 0
    path = sys.argv[1]
    limit = 12
    if "--max-lines" in sys.argv:
        limit = int(sys.argv[sys.argv.index("--max-lines") + 1])
    if not os.path.exists(path):
        print("（没有构建日志文件 —— 跳过）")
        return 0

    with open(path, encoding="utf-8", errors="ignore") as fh:
        lines = [ln.rstrip("\n") for ln in fh]

    picks: list[tuple[int, str]] = []
    for i, ln in enumerate(lines):
        hit = None
        for a in ANCHORS:
            if a in ln:
                if a == "> Task :" and not any(t in ln for t in TASK_NOISE_OK):
                    continue
                hit = a
                break
        if hit is None:
            continue
        picks.append((i, ln.strip()[:220]))
        if hit == "What went wrong":                       # 这一块是根因正文，带上下文
            for j in range(i + 1, min(i + 7, len(lines))):
                if lines[j].strip():
                    picks.append((j, lines[j].strip()[:220]))

    print("=========== 构建日志关键行（失败自证，B175） ===========")
    if not picks:
        print("  没有匹配到关键行（构建可能其实没失败？）")
        return 0
    seen, uniq = set(), []
    for i, ln in sorted(picks):
        if (i, ln) in seen:
            continue
        seen.add((i, ln))
        uniq.append((i, ln))
    for i, ln in uniq[:limit * 3]:
        print("  %s" % ln)
    if os.getenv("GITHUB_ACTIONS") == "true":
        for i, ln in uniq[:limit]:
            print("::error title=构建失败线索（行 %d）::%s" % (i + 1, ln))
    return 0


if __name__ == "__main__":
    sys.exit(main())
