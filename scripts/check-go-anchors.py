#!/usr/bin/env python3
"""迁移锚点守卫：主码注释不再引用旧实现的行号、Go 语法与移植波次。

用法：
    python3 scripts/check-go-anchors.py            # 检查（有命中即非零退出）
    python3 scripts/check-go-anchors.py --write    # 例外登记后刷新基线

## 拦什么（只扫注释行）

  * ``对照 Go`` / ``波 N`` —— 移植期黑话；
  * 行号引用 ``L141-144`` / ``L100/L129`` / 裸 ``L2302`` —— 指向已不存在的实现行；
  * Go 语法残留 ``uuid.New`` / ``gorm`` / ``s.ID =`` —— 注释在描述 Go 代码而非本仓实现。

## 不统计

``L1/L2/L3``（分层术语，1~2 位数字）、``DuckDuckGo`` 等产品名。
行号模式要求 2 位以上且带分隔符，或 3 位以上裸数字，避免误伤分层记号。
"""

import json
import pathlib
import re
import sys

import _source_roots as _sr

ROOTS = [d for _, _, d in _sr.java_roots(("main",))]
BASELINE = pathlib.Path("scripts/go-anchors.baseline.json")

PATTERNS = {
    "对照 Go": re.compile(r"对照\s*Go|波\s*\d"),
    "行号区间": re.compile(r"\bL\d{2,4}\s*[-–/]\s*L?\d{1,4}\b"),
    "裸行号": re.compile(r"(?<![\w.])L\d{3,4}(?![\w-])"),
    "Go 语法": re.compile(r"uuid\.New|\bgorm\b|\.ID\s*="),
}

COMMENT_LINE = re.compile(r"^\s*(//|/\*|\*)")


def count(path: pathlib.Path) -> int:
    n = 0
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if not COMMENT_LINE.match(line):
            continue
        n += sum(len(p.findall(line)) for p in PATTERNS.values())
    return n


def scan() -> dict:
    state = {}
    for root in ROOTS:
        for f in sorted(root.rglob("*.java")):
            n = count(f)
            if n:
                state[str(f.relative_to(root))] = n
    return state


def main() -> int:
    state = scan()
    total = sum(state.values())

    if "--write" in sys.argv:
        BASELINE.write_text(json.dumps(state, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
                            encoding="utf-8")
        print(f"✓ 基线已写入 {BASELINE}：{total} 处 / {len(state)} 文件")
        return 0

    old = json.loads(BASELINE.read_text(encoding="utf-8")) if BASELINE.exists() else {}
    old_total = sum(old.values())

    increased = {f: (old.get(f, 0), n) for f, n in state.items() if n > old.get(f, 0)}
    cleaned = old_total - total

    print(f"迁移锚点：{total} 处 / {len(state)} 文件（基线 {old_total} / {len(old)}）"
          + (f"，本次净减 {cleaned}" if cleaned > 0 else ""))
    if not increased:
        print("✓ 无新增（棘轮只许减不许增）")
        return 0

    print(f"✗ {len(increased)} 个文件超出基线：")
    for f, (before, after) in sorted(increased.items()):
        print(f"    {f}: {before} → {after}")
    print("\n处理方式：注释只描述本仓当前实现——删掉行号与 Go 语法，"
          "\n需要指路时写类名/方法名；确属合理保留的跑 `--write` 并说明原因。")
    return 1


if __name__ == "__main__":
    sys.exit(main())
