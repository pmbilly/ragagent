#!/usr/bin/env python3
"""文件行数棘轮（B121）：主源码**不得新增超过阈值的大文件**；既有大文件只报告增量。

设计取舍（为什么不把"既有文件长大"也设成硬门）：
  - 行数是**粗指标**。一个有内聚的 650 行类不该被阻止，而 68 个既有大文件正在被改动中；
    若对每个既有文件都执行"只许减"，日常功能开发会被反复卡住（与 R3b 那种"处数只减"
    的语义不同：那里的一处是**违例**，这里的一行只是**体量**）。
  - 真正的膨胀源是**新增**的大文件（一个新的 800 行 service）⇒ 这一条设硬门。
  - 既有文件的增量在输出里醒目标出（含 Δ 行数），拆小后请 `--write` 收紧基线。

阈值 600 行的来历：B121 盘点时全仓 1,888 个主源码文件里 >600 行有 68 个、
400~600 有 120 个；600 是"明显需要拆分"的起点，再低会把大量正常文件卷进来。
"""

import json
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import _source_roots as _sr  # noqa: E402

BASELINE = pathlib.Path("scripts/file-size.baseline.json")
THRESHOLD = 600
TOP_N = 12


def scan() -> dict[str, int]:
    """{相对仓库根的路径: 行数}，只含超过阈值的文件。"""
    out = {}
    for p in _sr.all_java_files(sources=("main",)):
        n = len(p.read_text(encoding="utf-8").splitlines())
        if n > THRESHOLD:
            out[str(p.relative_to(_sr.REPO))] = n
    return out


def main() -> int:
    write = "--write" in sys.argv
    current = scan()
    old = json.loads(BASELINE.read_text(encoding="utf-8")) if BASELINE.exists() else {}

    if write:
        BASELINE.write_text(json.dumps(dict(sorted(current.items())), ensure_ascii=False, indent=2) + "\n",
                            encoding="utf-8")
        print(f"✓ 基线已写入 {BASELINE}：> {THRESHOLD} 行文件 {len(current)} 个")
        return 0

    new_files = sorted(set(current) - set(old))
    shrunk = sorted(set(old) - set(current))          # 已拆小/删除 ⇒ 该收紧基线
    grown = sorted(((f, current[f] - old[f]) for f in set(old) & set(current) if current[f] > old[f]),
                   key=lambda x: -x[1])

    print(f"大文件（> {THRESHOLD} 行）：{len(current)} 个（基线 {len(old)}）")
    print(f"  最大的 {TOP_N} 个：")
    for f, n in sorted(current.items(), key=lambda x: -x[1])[:TOP_N]:
        print(f"    {n:5}  {f}")

    if grown:
        print(f"  较基线增长：{len(grown)} 个（提示，不阻塞）")
        for f, d in grown[:5]:
            print(f"    +{d:4}  {f}（{old[f]} → {current[f]}）")
    if shrunk:
        print(f"  已低于阈值/消失：{len(shrunk)} 个 → 请用 --write 收紧基线")
        for f in shrunk[:5]:
            print(f"    -     {f}（基线 {old[f]}）")

    problems = []
    for f in new_files:
        problems.append(f"新增超大文件 {f}（{current[f]} 行 > {THRESHOLD}）：请拆分，或在确有内聚时说明后 --write")

    if problems:
        print("\n✗ 守卫失败：")
        for p in problems:
            print("   " + p)
        return 1
    print("\n✓ 守卫通过：未新增超大文件。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
