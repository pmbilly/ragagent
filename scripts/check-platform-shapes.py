#!/usr/bin/env python3
"""C8：platform 只剩技术适配（判定标准二）。

四条判据（命中即必须在 `scripts/platform-shapes.baseline.json` 的 allowed 里，
逐条附理由；新增 ⇒ ✗；基线条目不再命中 ⇒ 提示可清理，不失败）：

  1. **业务词包**：`platform/knowledge`、`platform/settings` 下的文件——包名本身是域词汇；
  2. **业务词文件**：文件名含 Knowledge/Chunk/Faq/Doc/Session/Agent/Retrieval/Memory/Wiki/VectorStore；
  3. **红旗命名**：文件名以 View/Port/Request/Response/Payload/Facts/Gateway 结尾；
  4. `ContractJsonMixins*` 是契约注解注册表，按设计 import 业务形状，豁免。

处置建议：搬 contract（跨域形状）/ 归域（业务规则）/ 中性化（端口）/ 改名（纯技术件）；
若确属技术适配（HTTP 壳 · 事件/流基建存储形态 · 映射载体），登记基线并写明理由。
"""

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "platform/src/main/java"
BASELINE = ROOT / "scripts/platform-shapes.baseline.json"
SUFFIXES = ("View", "Port", "Request", "Response", "Payload", "Facts", "Gateway")
BUSINESS_PACKAGES = {"knowledge", "settings"}
BUSINESS_WORDS = (
    "Knowledge",
    "Chunk",
    "Faq",
    "Doc",
    "Session",
    "Agent",
    "Retrieval",
    "Memory",
    "Wiki",
    "VectorStore",
)


def hit_reason(path: Path):
    rel_parts = path.relative_to(SRC).parts
    if len(rel_parts) >= 4 and rel_parts[3] in BUSINESS_PACKAGES:
        return f"业务词包 {rel_parts[3]}"
    stem = path.stem
    if stem.startswith("ContractJsonMixins"):
        return None
    if stem.endswith(SUFFIXES):
        return "红旗命名"
    for word in BUSINESS_WORDS:
        if word in stem:
            return f"业务词 {word}"
    return None


def main() -> int:
    hits = {}
    for p in sorted(SRC.rglob("*.java")):
        if p.name == "package-info.java":
            continue
        reason = hit_reason(p)
        if reason:
            hits[str(p.relative_to(SRC))] = reason

    data = json.loads(BASELINE.read_text(encoding="utf-8")) if BASELINE.exists() else {"allowed": {}}
    allowed = data.get("allowed", {})
    if isinstance(allowed, list):
        allowed = {k: "" for k in allowed}

    new = sorted(k for k in hits if k not in allowed)
    stale = sorted(set(allowed) - set(hits))

    print("▶ platform 业务形状（C8：业务词包 / 业务词文件 / 红旗命名）")
    if new:
        print("✗ 守卫失败：platform 出现未登记业务形状：")
        for rel in new:
            print(f"    {rel}（{hits[rel]}）")
        print("    处置：搬 contract / 归域 / 中性化 / 改名；确属技术适配请登记基线并写明理由。")
        return 1
    print(f"✓ 守卫通过：命中 {len(hits)}，全部有登记（基线 {len(allowed)} 条）")
    if stale:
        print(f"    注意：基线条目已不再命中，可清理收紧：{stale}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
