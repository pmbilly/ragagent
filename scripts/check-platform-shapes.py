#!/usr/bin/env python3
"""C8：platform 只剩技术适配（判定标准二）——业务形状命名红旗只许减不许增。

红旗命名（文件名即主类型名）：View / Port / Request / Response / Payload / Facts / Gateway 结尾。
命中即必须在 scripts/platform-shapes.baseline.json 的 allowed 里（逐条附登记理由）；
新增 ⇒ ✗；基线条目不再命中 ⇒ 提示可清理（不失败）。

处置建议：搬 contract（跨域形状）/ 中性化（端口）/ 改名（纯技术件）；
若确属技术适配（HTTP 壳 · 事件/流基建存储形态 · 映射载体），把相对路径登记进基线并写明理由。
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "platform/src/main/java"
BASELINE = ROOT / "scripts/platform-shapes.baseline.json"
SUFFIXES = ("View", "Port", "Request", "Response", "Payload", "Facts", "Gateway")


def main() -> int:
    hits = sorted(
        str(p.relative_to(SRC))
        for p in SRC.rglob("*.java")
        if p.stem.endswith(SUFFIXES)
    )
    data = json.loads(BASELINE.read_text(encoding="utf-8")) if BASELINE.exists() else {"allowed": {}}
    allowed = data.get("allowed", {})
    if isinstance(allowed, list):
        allowed = {k: "" for k in allowed}

    current = set(hits)
    new = [h for h in hits if h not in allowed]
    stale = sorted(set(allowed) - current)

    print(f"▶ platform 业务形状（C8：只剩技术适配；红旗命名 = {'/'.join(SUFFIXES)}）")
    if new:
        print("✗ 守卫失败：platform 出现未登记业务形状：")
        for h in new:
            print(f"    {h}")
        print("    处置：搬 contract / 中性化 / 改名；确属技术适配（HTTP 壳·事件基建存储形态·映射载体）")
        print("    请登记入 scripts/platform-shapes.baseline.json（allowed 附理由）后重跑。")
        return 1
    print(f"✓ 守卫通过：platform 无未登记业务形状（命中 {len(hits)}，全部有登记；基线 {len(allowed)} 条）")
    if stale:
        print(f"    注意：基线条目已不再命中，可清理收紧：{stale}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
