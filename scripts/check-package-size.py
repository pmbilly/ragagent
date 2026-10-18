#!/usr/bin/env python3
"""业务域包规模棘轮（C1）：单包文件数 >30 必须登记且只许降；26-30 只报警。

为什么只扫 `domains`：契约/运行时/平台是“能力与词汇”面，包规模另有 container 语义；
业务域是导航成本最高的地方，先把热点锁住。后续如需扩面，在 ROOT 上追加模块即可。

`--write` 只做“收紧”：更新已登记包的当前值、清理已降到阈值内的条目；
新增超限包必须先手工登记理由，脚本不代写。

红态探针：往任一已登记包临时加一个 `.java`（如 `agenttools/wiki`），本脚本必须非零退出。
"""

import json
import pathlib
import sys
from collections import Counter

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "domains" / "src" / "main" / "java"
BASELINE = ROOT / "scripts" / "package-size.baseline.json"


def scan() -> Counter:
    counts = Counter(
        p.parent.relative_to(ROOT).as_posix()
        for p in SRC.rglob("*.java")
        if "/build/" not in str(p)
    )
    return counts


def load() -> dict:
    return json.loads(BASELINE.read_text(encoding="utf-8"))


def render(data: dict, counts: Counter) -> str:
    allowed = data["allowed"]
    items = sorted(allowed.items(), key=lambda kv: (-counts.get(kv[0], 0), kv[0]))
    lines = [
        "{",
        f'  "warnThreshold": {data["warnThreshold"]},',
        f'  "enforceThreshold": {data["enforceThreshold"]},',
        f'  "note": {json.dumps(data["note"], ensure_ascii=False)},',
        '  "allowed": {',
    ]
    for index, (path, meta) in enumerate(items):
        comma = "," if index < len(items) - 1 else ""
        lines.append(f'    {json.dumps(path)}: {{')
        lines.append(f'      "files": {counts.get(path, meta["files"])},')
        lines.append(f'      "reason": {json.dumps(meta["reason"], ensure_ascii=False)},')
        lines.append(f'      "target": {meta["target"]}')
        lines.append(f"    }}{comma}")
    lines += ["  }", "}"]
    return "\n".join(lines) + "\n"


def main() -> int:
    data = load()
    warn_threshold = data["warnThreshold"]
    enforce_threshold = data["enforceThreshold"]
    allowed = data["allowed"]
    counts = scan()

    warnings = sorted(
        ((n, path) for path, n in counts.items() if warn_threshold < n <= enforce_threshold),
        reverse=True,
    )
    new = sorted(
        ((n, path) for path, n in counts.items() if n > enforce_threshold and path not in allowed),
        reverse=True,
    )
    growth = sorted(
        (
            (n, path)
            for path, n in counts.items()
            if path in allowed and n > allowed[path]["files"]
        ),
        reverse=True,
    )
    stale = sorted(
        path
        for path, meta in allowed.items()
        if counts.get(path, 0) < meta["files"] or counts.get(path, 0) <= enforce_threshold
    )

    if warnings:
        print("⚠ 包规模提醒（>25 且 ≤30，不判红）：")
        for n, path in warnings:
            print(f"   {n:3}  {path}")
        print("   目标：拆到 ≤25；超过 30 时进入基线并登记理由。")

    problems = []
    for n, path in new:
        problems.append(f"新增超限包：{path}（{n} 文件 > {enforce_threshold}）")
    for n, path in growth:
        problems.append(
            f"已登记包增长：{path}（{n} > 基线 {allowed[path]['files']}）"
        )

    if problems:
        print("✗ 业务域包规模违例：")
        for problem in problems:
            print("   " + problem)
        print("   处置：已登记包只许降（拆分/移出文件）；新增超限包先登记理由再同步基线。")
        return 1

    if "--write" in sys.argv:
        if new:
            print("✗ 存在新增超限包，拒绝 --write；请先手工登记理由。")
            return 1
        BASELINE.write_text(render(data, counts), encoding="utf-8")
        print(f"✓ 基线已收紧：{len(allowed)} 个包")
        return 0

    print(
        f"✓ 业务域包规模：{len(allowed)} 个已登记包未增长，"
        f"无新增 >{enforce_threshold} 的包"
    )
    if stale:
        print("   可收紧（已降到阈值内）：" + "、".join(stale))
        print("   对账后跑 python3 scripts/check-package-size.py --write")
    return 0


if __name__ == "__main__":
    sys.exit(main())
