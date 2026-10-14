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

**已知边界（B174 实测）**：Gradle 的 XML 对 `assumeTrue` 型跳过只写 `<skipped/>` ✗
（**不带原因** ✓，属性与文本都空 ✓）⇒ 这类跳过在汇总里显示"无原因" ✓，请看用例名回源码查 guard 旁的注释 ✓
（脚本仍会读 message 属性与文本：别的 JUnit 输出格式会带上原因 ✓）。

用法：python3 scripts/test-summary.py       （找不到 XML 就安静退出 0，供 CI 的 always() 步用）

**B218 起会失败**：跳过清单已升级成**棘轮** —— 只有登记在 {@code ALLOWED_SKIPS} 里的跳过被放行，
出现未登记的跳过 ⇒ 退出码 1（CI 红）。理由同"棘轮守卫"：覆盖的消失是累积性的，
今天多跳一个不吭声，半年后就说不清哪些覆盖还在。**新增跳过必须在此登记并写明为什么可以跳**。
"""
from __future__ import annotations

import fnmatch
import glob
import os
import sys
import xml.etree.ElementTree as ET

MODULES = ("domains", "engine", "common", "boot", "contract")
IN_ACTIONS = os.getenv("GITHUB_ACTIONS") == "true"
MAX_ANNOTATE = 20

# 允许的跳过（棘轮）：模式 → 为什么可以跳。
# 判定用 fnmatch（`*` 通配），名字形如 `类名.方法名`。
ALLOWED_SKIPS: tuple[tuple[str, str], ...] = (
    ("SpringRedisPubSubTest.*",
     "redis-server 不在 PATH（本地常见；CI 已装 ⇒ CI 真跑）"),
    ("RemoteApiChatTest.liveRemoteApiChat*",
     "需真实 provider API key（联网 + 计费），刻意不自动化"),
    ("JiebaTokenizerDiffTest.*",
     "需 Go sdkTokenize 的对照产物（不在本仓）"),
)


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
                    msg = (bad.get("message") or "").strip().replace("\n", " ")[:800]   # 800：够看到 expected/actual 的分叉点（B179）
                    fails.append((mod, name, msg))
                sk = tc.find("skipped")
                if sk is not None:
                    # JUnit 把跳过消息放在**元素文本**里（属性常为空 ✗）⇒ 两处都要读。
                    msg = sk.get("message") or (sk.text or "") or "无原因"
                    skips.append((mod, name, msg.strip()[:160]))
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

    # 跳过棘轮（B218）：未登记的跳过 = 覆盖无声消失，报红。
    unregistered = [(mod, n, m) for mod, n, m in skips
                    if not any(fnmatch.fnmatch(n, pat) for pat, _ in ALLOWED_SKIPS)]
    if unregistered:
        print("\n✗ 未登记的跳过 %d 条（覆盖无声消失）—— 补测，或在 test-summary.py 的 "
              "ALLOWED_SKIPS 登记并写明理由：" % len(unregistered))
        for mod, n, m in unregistered:
            print("    %s  %s" % (mod, n))

    if IN_ACTIONS:
        for mod, n, m in fails[:MAX_ANNOTATE]:
            print("::error title=测试失败（%s）::%s — %s" % (mod, n, m))
        for mod, n, m in skips[:MAX_ANNOTATE]:
            print("::notice title=测试跳过（%s，覆盖未跑）::%s — %s" % (mod, n, m))
        for mod, n, _ in unregistered[:MAX_ANNOTATE]:
            print("::error title=未登记的跳过（%s）::%s" % (mod, n))
    return 1 if unregistered else 0


if __name__ == "__main__":
    sys.exit(main())
