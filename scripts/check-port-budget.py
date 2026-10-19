#!/usr/bin/env python3
"""R1：端口准入（域内端口 / 无实现端口必须为 0）。

数据源是 `docs/architecture/port-map.md`（由 `gen-port-map.py` 生成）：
  - 「域内（可评估内联）」= 实现与调用方同域，端口没有解耦收益；
  - 「无实现（检查）」= 契约悬空。

两者都必须为 0；`scripts/port-budget.baseline.json` 只允许登记带理由的存量
（当前为空，新增即红）。

为什么不自己重新解析 import：类型判定（实现模块 vs 调用方模块/域）已在
`gen-port-map.py` 里做过一次，重复实现会出现两套口径。

红态探针：把 `port-map.md` 任一行的类型改成「域内（可评估内联）」，
本脚本必须非零退出；`python3 scripts/gen-port-map.py` 重生成即恢复。
"""

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PORT_MAP = ROOT / "docs/architecture/port-map.md"
BASELINE = ROOT / "scripts/port-budget.baseline.json"
BAD_KINDS = ("域内（可评估内联）", "无实现（检查）")


def main() -> int:
    if not PORT_MAP.exists():
        print("✗ 守卫失败：docs/architecture/port-map.md 不存在（先跑 gen-port-map.py）")
        return 1
    data = json.loads(BASELINE.read_text(encoding="utf-8")) if BASELINE.exists() else {}
    allowed = set(data.get("allowed", []))

    bad = []
    for line in PORT_MAP.read_text(encoding="utf-8").splitlines():
        if not line.startswith("| `"):
            continue
        cols = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cols) < 5:
            continue
        port = cols[0].strip("`")
        kind = cols[-1]
        if kind in BAD_KINDS:
            bad.append((port, kind))

    print("▶ 端口准入（域内端口 / 无实现端口 = 0）")
    unexpected = [(p, k) for p, k in bad if p not in allowed]
    if unexpected:
        print("✗ 守卫失败：出现无收益或悬空的端口：")
        for port, kind in unexpected:
            print(f"    {port} → {kind}")
        print("    处置：域内端口内联回域；悬空端口补实现或删除。")
        print("    确属存量债务请登记 scripts/port-budget.baseline.json（附理由，只许减）。")
        return 1

    stale = sorted(allowed - {p for p, _ in bad})
    print(f"✓ 守卫通过：域内 0 / 无实现 0（基线 {len(allowed)} 条）")
    if stale:
        print(f"    注意：基线条目已不再命中，可清理收紧：{stale}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
