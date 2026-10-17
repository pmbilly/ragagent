#!/usr/bin/env python3
"""主码注释迁移叙事守卫（棘轮）：**只许减不许增**。

用法：
    python3 scripts/check-comment-narrative.py            # 检查
    python3 scripts/check-comment-narrative.py --write    # 清洗后刷新基线

## 拦什么

主码（`src/main/java/**/*.java`）**注释行**里"以过去时叙述实现历史"的表述：

  * ``退役`` —— "旧形态已退役"这类记录；
  * ``备案`` / ``已知差异`` / ``已知降级`` —— 把设计取舍写成迁移备忘；
  * ``Go``（词边界）—— 迁移来源锚点（不含 ``DuckDuckGo`` 等产品名）；
  * ``迁移期`` / ``迁移遗留``；
  * ``旧实现`` / ``旧形态`` / ``旧版`` / ``旧行为`` / ``旧路径`` / ``旧口径`` / ``旧逻辑`` / ``旧写法`` / ``旧代码``。

## 口径（刻意窄）

**只扫注释行**（`//`、`*`、`/*` 开头），不扫字符串与代码——错误文案里的"退役"
或变量名里的 `old*` 不算。

**不统计**（各自有正当用法）：
  * ``历史消息`` / ``历史记录`` —— 业务术语（会话历史）；
  * ``迁移``（单独出现）—— Flyway 迁移是本仓正式机制；
  * ``L1/L2/L3`` —— 分层术语；
  * ``此前`` / ``原先`` —— 普通时间副词，不必然是迁移叙事。

## 为什么是棘轮而不是一次性清扫

769 处叙事分布在约 160 个文件里，一次扫完无法逐条判断"历史说明 / 当前约束 / 设计取舍"。
按批次清零：每批把一批文件的叙事改写成现在时，`--write` 收紧基线；
清零后基线为 `{}`，此后任何一处命中都会直接报红（自动升级为硬零）。
"""

import json
import pathlib
import re
import sys

import _source_roots as _sr

ROOTS = [d for _, _, d in _sr.java_roots(("main",))]
BASELINE = pathlib.Path("scripts/comment-narrative.baseline.json")

PATTERNS = {
    "退役": re.compile(r"退役"),
    "备案/已知差异": re.compile(r"备案|已知差异|已知降级"),
    "Go 锚点": re.compile(r"(?<![A-Za-z])Go(?![A-Za-z])"),
    "迁移期": re.compile(r"迁移期|迁移遗留"),
    "旧实现": re.compile(r"旧(实现|形态|版|行为|路径|口径|逻辑|写法|代码)"),
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

    print(f"迁移叙事：{total} 处 / {len(state)} 文件（基线 {old_total} / {len(old)}）"
          + (f"，本次净减 {cleaned}" if cleaned > 0 else ""))
    if not increased:
        print("✓ 无新增（棘轮只许减不许增）")
        return 0

    print(f"✗ {len(increased)} 个文件超出基线：")
    for f, (before, after) in sorted(increased.items()):
        print(f"    {f}: {before} → {after}")
    print("\n处理方式：注释改写为现在时（说清当前约束），历史说明删掉，设计取舍移 "
          "\n`docs/known-gaps.md`；确属合理保留的跑 `--write` 并在 PR 说明原因。")
    return 1


if __name__ == "__main__":
    sys.exit(main())
