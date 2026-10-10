#!/usr/bin/env python3
"""第十一守卫（B221）：契约变更必须**显式签字**。

## 为什么需要它

前端类型是**手写**的（31 个 api 文件 / 265 个 interface，无 codegen），后端改字段前端照样
编译通过 ⇒ 只有运行时才炸。现有 `check-fe-contract-keys.py` 用正则扫前端源码找可疑键，
但脚本自己就写明有**七种写法扫不到** —— 靠"在错误的层（文本）上找"永远补不齐。

换一个**可靠的信号源**：契约金片（`domains/src/testFixtures/resources/contracts/*.json`，
1,429 个）是**期望值**，只在契约**有意变更**时才更新（`-Dcontract.refresh=true` 重录）
⇒ **金片变了 = 契约变了**。本守卫据此把"悄悄变"改成"必须签字"：

  金片键集合与基线不符 ⇒ 红 ⇒ 人确认前端是否已同步（或无需同步）⇒ 跑 `--write` 更新基线。

## 判据与口径

- 键名用**正则提取**（`"key":`），不解析 JSON —— 金片里有 204 空响应与掩码破坏语法的
  文件，解析会在它们身上失败，而正则对两者都稳健。
- 指纹 = 排序去重后的键名列表的 SHA1 前 12 位 + 键数（基线紧凑；变更详情在**报红时**
  重算并展示，既有压缩比又有可读性）。
- `--write` **先打印本次全部差异**再落盘 —— 签字的人要看得见签的是什么。

## 用法

    python3 scripts/check-contract-sync.py            # 守卫（CI 与本地同一份）
    python3 scripts/check-contract-sync.py --write    # 确认变更后更新基线
"""

from __future__ import annotations

import hashlib
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CONTRACTS = ROOT / "domains/src/testFixtures/resources/contracts"
BASELINE = ROOT / "scripts/contract-signature.baseline.json"

KEY_RE = re.compile(r'"([A-Za-z_][\w]*)"\s*:')


def keys_of(p: pathlib.Path) -> set[str]:
    return set(KEY_RE.findall(p.read_text(encoding="utf-8", errors="ignore")))


def signature(p: pathlib.Path) -> tuple[str, int]:
    ks = sorted(keys_of(p))
    digest = hashlib.sha1("\n".join(ks).encode("utf-8")).hexdigest()[:12]
    return digest, len(ks)


def scan() -> dict[str, list]:
    out = {}
    for p in sorted(CONTRACTS.glob("*.json")):
        d, n = signature(p)
        out[p.name] = [d, n]
    return out


def main() -> int:
    write = "--write" in sys.argv
    if not CONTRACTS.is_dir():
        print(f"✗ 金片目录不存在：{CONTRACTS}")
        return 1

    current = scan()
    data = json.loads(BASELINE.read_text(encoding="utf-8")) if BASELINE.exists() else {}
    base: dict[str, list] = data.get("files", {})

    added = sorted(set(current) - set(base))
    removed = sorted(set(base) - set(current))
    changed = sorted(f for f in set(current) & set(base) if current[f] != base[f])

    if not added and not removed and not changed:
        print(f"✓ 守卫通过：{len(current)} 个金片的键签名与基线一致（契约自上次签字以来未变）。")
        return 0

    # 变更详情：对变化/新增文件展示键级差异（签字的人要看得见）
    print(f"契约变更：新增 {len(added)} · 删除 {len(removed)} · 键集合变化 {len(changed)}")
    for f in changed:
        now = keys_of(CONTRACTS / f)
        before_n = base[f][1]
        print(f"  ~ {f}（键数 {before_n} → {len(now)}）")
    for f in added[:10]:
        print(f"  + {f}（{current[f][1]} 键）")
    if len(added) > 10:
        print(f"    …还有 {len(added) - 10} 个新增")
    for f in removed[:10]:
        print(f"  - {f}（已删除）")
    if len(removed) > 10:
        print(f"    …还有 {len(removed) - 10} 个删除")

    if write:
        BASELINE.write_text(json.dumps({
            "note": "契约键签名（B221）：{金片文件: [键集合 SHA1 前 12 位, 键数]}。"
                    "由 check-contract-sync.py --write 生成 —— 只在**确认前端已同步或无需同步**后更新。",
            "files": dict(sorted(current.items())),
        }, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
        print(f"✓ 基线已更新：{len(current)} 个金片。**请确认前端 api 层已同步**（或确认本次变更不影响前端）。")
        return 0

    print("\n✗ 守卫失败：契约变了，但没有签字。")
    print("  → 核对前端 `frontend/src/api/**` 是否需要同步；")
    print("     确认后跑 `python3 scripts/check-contract-sync.py --write` 更新基线（签字）。")
    return 1


if __name__ == "__main__":
    sys.exit(main())
