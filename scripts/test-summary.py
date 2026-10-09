#!/usr/bin/env python3
"""测试结果汇总：把**失败**与**跳过**摆到台面上（B171）。

起因（两次排查的真实成本 ✗）：CI 只在日志尾部写 "N tests completed, M failed"，失败用例名散在
日志中部；而"跳过"更彻底 —— `1312 tests completed, 2 failed, 4 skipped` 里那 4 个跳过是谁、为什么，
日志里根本没有 ✗（B170 实测：CI 静默跳过了 4 个 Redis 测试 = 隐形覆盖损失，直到用收窄 PATH 复现
才看清）。**跳过 = 这段覆盖没跑**，它和失败一样需要被看见。

本脚本做两件事：
  ① 把各模块 JUnit XML 汇总成一段可读清单：总数 / 失败用例名 + 首行消息 / **跳过用例名 + 原因**；
  ② 在 GitHub Actions 里额外发 `::error` / `::notice` 工作流命令 ⇒ 失败与跳过变成**注解**，
     既在 job 页可见，也能用**公开 API** 读到（`/check-runs/{id}/annotations`）⇒ 不必再让人
     去日志里捞信息。

用法：python3 scripts/test-summary.py       （找不到 XML 就安静退出 0，供 CI 的 always() 步用；
                                             只报告，退出码恒 0 —— 红不红由 Gradle 决定）
"""
from __future__ import annotations

import glob
import os
import sys
import xml.etree.ElementTree as ET

MODULES = ("domains", "engine", "common", "boot")
IN_ACTIONS = os.getenv("GITHUB_ACTIONS") == "true"
MAX_ANNOTATE = 20


def main() -> int:
    rows, fails, skips = [], [], []
    found_any = False
    for mod in MODULES:
        t = f = s = 0
        for p in sorted(glob.glob(f"{mod}/build/test-results/test/TEST-*.xml")):
            found_any = True
            r = ET.parse(p).getroot()
            t += int(r.get("tests") or 0)
            f += int(r.get("failures") or 0) + int(r.get("errors") or 0)
            s += int(r.get("skipped") or 0)
            cls = (r.get("name") or "").split(".")[-1]
            for tc in r.iter("testcase"):
                name = f'{cls}.{tc.get("name")}'
                for bad in list(tc.iter("failure")) + list(tc.iter("error")):
                    msg = (bad.get("message") or "").strip().replace("\n", " ")[:200]
                    fails.append((mod, name, msg))
                sk = tc.find("skipped")
                if sk is not None:
                    skips.append((mod, name, (sk.get("message") or "无原因").strip()[:160]))
        if t or f or s:
            rows.append((mod, t, f, s))
    if not found_any:
        print("（没有测试结果 XML —— 跳过汇总）")
        return 0

    print("================ 测试汇总（失败 / 跳过清单） ================")
    for mod, t, f, s in rows:
        print("  %-8s tests=%5d failures=%d skipped=%d" % (mod, t, f, s))
    print("  【失败】%d 条" % len(fails))
    for mod, n, m in fails:
        print("    ✗ %s  %s\n         %s" % (mod, n, m))
    print("  【跳过】%d 条（跳过 = 这段覆盖没跑，请核原因是否合理）" % len(skips))
    for mod, n, m in skips:
        print("    ⏭ %s  %s\n         %s" % (mod, n, m))

    if IN_ACTIONS:
        for mod, n, m in fails[:MAX_ANNOTATE]:
            print("::error title=测试失败（%s）::%s — %s" % (mod, n, m))
        for mod, n, m in skips[:MAX_ANNOTATE]:
            print("::notice title=测试跳过（%s，覆盖未跑）::%s — %s" % (mod, n, m))
    return 0


if __name__ == "__main__":
    sys.exit(main())
