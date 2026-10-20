#!/usr/bin/env python3
"""长方法棘轮：>150 行的方法只许减，新增即红。

判据：
  R-a 新增长方法：基线里没有的 >150 行方法 → 红（先拆，或手工登记理由）
  R-b 回涨：基线内方法比基线多出 > growthSlack（10）行 → 红
  R-c 基线失效：基线里的方法已不存在（拆了/改名/删了）→ 红，跑 --write 收紧
  R-d 理由必填：基线每条必须写 reason；空理由 → 红
  R-e 只收紧：`--write` 只更新现有条目（取 min）并清理失效项，**不新增条目**

设计取舍：
  - 不设「>150 行必须拆」的硬门——`method-length-report.py` 的注释已说明，长方法分
    「阶段多」（该拆）与「规则/数据多」（拆了只增加跳转），机器分不出。棘轮只负责
    「不再新增」，存量由人工按价值流动拆。
  - `--write` 不新增条目：新增长方法必须人工在 baseline 里补一行并写理由。这样
    `--write` 不能被用来掩盖新增债务（file-size 的 `--write` 是可绕过的，本守卫不重复
    那个缺陷）。

用法：
    python3 scripts/check-method-length.py
    python3 scripts/check-method-length.py --write   # 只收紧/清理，不新增
    python3 scripts/check-method-length.py --list    # 只列当前 >150 行方法
"""

import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from _method_lengths import scan  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parent.parent
BASELINE = ROOT / "scripts/method-length.baseline.json"
THRESHOLD = 150
GROWTH_SLACK = 10


def current_rows():
    return {key: (size, rel, name, line)
            for size, rel, name, line, key in scan(THRESHOLD, ROOT)}


def load_baseline():
    if not BASELINE.exists():
        return None
    data = json.loads(BASELINE.read_text(encoding="utf-8"))
    return {key: (int(value["lines"]), value.get("reason", ""))
            for key, value in data.get("methods", {}).items()}


def render(methods: dict[str, tuple[int, str]]) -> str:
    data = {
        "note": ">150 行方法基线：只许减；新增必须人工登记理由；--write 不新增条目。",
        "threshold": THRESHOLD,
        "growthSlack": GROWTH_SLACK,
        "methods": {key: {"lines": lines, "reason": reason}
                    for key, (lines, reason) in sorted(methods.items())},
    }
    return json.dumps(data, ensure_ascii=False, indent=2) + "\n"


def write_baseline(baseline, current) -> int:
    if baseline is None:
        methods = {key: (size, "存量基线（M1 首次登记，2026-10-20）")
                   for key, (size, _, _, _) in current.items()}
        BASELINE.write_text(render(methods), encoding="utf-8")
        print(f"✓ 方法长度基线已建立：{len(methods)} 个 >{THRESHOLD} 行方法")
        return 0

    new_keys = sorted(set(current) - set(baseline))
    if new_keys:
        print("✗ --write 拒绝新增条目：以下方法是基线外的新增长方法，"
              "请先拆分，或手工在 baseline 登记理由：")
        for key in new_keys:
            size, rel, name, line = current[key]
            print(f"   {size:4d}  {rel}:{line}  {name}")
        return 1

    methods = {}
    for key, (old_lines, reason) in baseline.items():
        if key not in current:
            continue
        methods[key] = (min(old_lines, current[key][0]), reason)
    BASELINE.write_text(render(methods), encoding="utf-8")
    dropped = len(baseline) - len(methods)
    print(f"✓ 方法长度基线已收紧：保留 {len(methods)} 个，清理失效 {dropped} 个")
    return 0


def main() -> int:
    current = current_rows()

    if "--list" in sys.argv:
        for size, rel, name, line, _ in scan(THRESHOLD, ROOT):
            print(f"  {size:4d}  {rel}:{line}  {name}")
        print(f"共 {len(current)} 个 >{THRESHOLD} 行方法")
        return 0

    baseline = load_baseline()
    if "--write" in sys.argv:
        return write_baseline(baseline, current)

    if baseline is None:
        print(f"✗ 缺少基线：{BASELINE.relative_to(ROOT)}（先跑 --write 建立）")
        return 1

    problems = []
    for key, (lines, reason) in sorted(baseline.items()):
        if not reason.strip():
            problems.append(f"R-d 基线缺少理由：{key}")
        if key not in current:
            problems.append(f"R-c 基线失效 {key}：方法已不存在——跑 --write 收紧")
            continue
        size, rel, name, line = current[key]
        if size > lines + GROWTH_SLACK:
            problems.append(
                f"R-b 方法回涨 {rel}:{line} {name}：{lines} → {size}"
                f"（+{size - lines} > {GROWTH_SLACK}）——请拆一刀，而不是继续堆")
    for key, (size, rel, name, line) in sorted(current.items()):
        if key not in baseline:
            problems.append(
                f"R-a 新增长方法 {rel}:{line} {name}（{size} 行）"
                f"——请拆分；确需保留则手工在 baseline 登记理由")

    tightened = [key for key, (size, _, _, _) in current.items()
                 if key in baseline and size < baseline[key][0]]
    if tightened:
        print(f"  可收紧 {len(tightened)} 个（跑 --write 更新基线）")

    if problems:
        print(f"\n✗ 方法长度棘轮失败：{len(problems)} 处")
        for problem in problems:
            print("   " + problem)
        return 1
    print(f"✓ 方法长度棘轮：{len(current)} 个 >{THRESHOLD} 行方法均在基线内；"
          f"无新增、无回涨 >{GROWTH_SLACK} 行")
    return 0


if __name__ == "__main__":
    sys.exit(main())
