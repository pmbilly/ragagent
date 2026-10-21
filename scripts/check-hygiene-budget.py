#!/usr/bin/env python3
"""卫生预算计数棘轮：三类「回潮」指标只许减。

## 为什么是计数

`check-method-length.py` 只钉 >150 行的方法；冲刺期的回潮恰恰发生在它下面一档
（>100 行方法）以及「构造器参数」「同一语义的私有助手副本」这两个不显眼的角落。
逐项登记 109 个 >100 行方法会让基线变成噪声，且每次合法拆分类都会改条目；
计数基线只回答一个问题：**这一轮整改有没有让整体卫生变差**。

## 口径（固定，可复现）

  methodsOver100  `scripts/_method_lengths.scan(100)` 的结果数 —— 跳过字符串/注释后
                  配对花括号，避免 JSON/正则里的 `{}` 虚高。
  trimCopies      主码里 `private [static] String trim(String x)` 的副本数 —— 通用空白
                  语义重复实现。`trimTrailingSlashes` 之类的专用助手不计入（语义不同）。
  ctorsOver8      主码里「类名 == 文件名」的构造器，参数 ≥8 个；跨行签名按括号配平计数。

历史对照：2026-10-21 评估报告记「trim 私拷贝 39→72」，实测是**口径漂移**——
39 是 private-only 正则，72 是放宽到所有 `String trimXxx(String)` 后的数；
本脚本固定为上面的 `trimCopies`（当前 11），不与旧口径混用。

## 判据

  B-a 某项高于基线 → 红（先拆/合并，或说明为何是合理增长再手工改基线）；
  B-b `--write` 只收紧（取 min），**不允许上调**；
  B-c 基线缺失 → 红（先跑 --write 建立）。

用法：
    python3 scripts/check-hygiene-budget.py
    python3 scripts/check-hygiene-budget.py --list
    python3 scripts/check-hygiene-budget.py --write
"""

import json
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from _method_lengths import scan  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parent.parent
BASELINE = ROOT / "scripts/hygiene-budget.baseline.json"
MODULES = ["contract", "platform", "model-runtime", "retrieval-engine",
           "agent-engine", "domains", "boot"]
METHOD_THRESHOLD = 100
CTOR_THRESHOLD = 8

TRIM = re.compile(r"^[ \t]*private\s+(?:static\s+)?String\s+trim\s*\(\s*String\s+\w+\s*\)", re.M)
CTOR = re.compile(r"^[ \t]*(?:public|protected|private)?[ \t]*([A-Z]\w*)[ \t]*\(", re.M)


def main_sources():
    for module in MODULES:
        base = ROOT / module / "src" / "main" / "java"
        if base.is_dir():
            yield from base.rglob("*.java")


def trim_copies() -> list[str]:
    found = []
    for path in main_sources():
        for _ in TRIM.finditer(path.read_text(encoding="utf-8", errors="ignore")):
            found.append(str(path.relative_to(ROOT)))
    return found


def params_count(raw: str) -> int:
    raw = raw.strip()
    if not raw:
        return 0
    depth, count = 0, 1
    for ch in raw:
        if ch in "<([{":
            depth += 1
        elif ch in ">)]}":
            depth -= 1
        elif ch == "," and depth == 0:
            count += 1
    return count


def ctors_over() -> list[str]:
    found = []
    for path in main_sources():
        text = path.read_text(encoding="utf-8", errors="ignore")
        for match in CTOR.finditer(text):
            if match.group(1) != path.stem:
                continue
            i, depth = match.end(), 1
            while i < len(text) and depth > 0:
                if text[i] == "(":
                    depth += 1
                elif text[i] == ")":
                    depth -= 1
                i += 1
            count = params_count(text[match.end():i - 1])
            if count >= CTOR_THRESHOLD:
                line = text[:match.start()].count("\n") + 1
                found.append(f"{path.relative_to(ROOT)}:{line} {match.group(1)}({count} 参)")
    return found


def measure() -> dict[str, list[str]]:
    return {
        "methodsOver100": [
            f"{rel}:{line} {name}（{size} 行）"
            for size, rel, name, line, _ in scan(METHOD_THRESHOLD, ROOT)
        ],
        "trimCopies": trim_copies(),
        "ctorsOver8": ctors_over(),
    }


def load() -> dict[str, int] | None:
    if not BASELINE.exists():
        return None
    return json.loads(BASELINE.read_text(encoding="utf-8"))["metrics"]


def render(metrics: dict[str, int]) -> str:
    data = {
        "note": "卫生预算计数基线：只许减；--write 只收紧，不上调。口径见 scripts/check-hygiene-budget.py。",
        "metrics": dict(sorted(metrics.items())),
    }
    return json.dumps(data, ensure_ascii=False, indent=2) + "\n"


def main() -> int:
    current = {key: len(value) for key, value in measure().items()}

    if "--list" in sys.argv:
        for key, value in measure().items():
            print(f"\n■ {key}：{len(value)}")
            for row in value:
                print("   " + row)
        return 0

    baseline = load()
    if "--write" in sys.argv:
        if baseline is None:
            BASELINE.write_text(render(current), encoding="utf-8")
            print(f"✓ 卫生预算基线已建立：{current}")
            return 0
        raised = {key: (baseline.get(key, 0), value) for key, value in current.items()
                  if value > baseline.get(key, 0)}
        if raised:
            print("✗ --write 拒绝上调（先收债，或人工说明后改基线）：")
            for key, (old, new) in sorted(raised.items()):
                print(f"   {key}: {old} → {new}")
            return 1
        tightened = {key: min(baseline.get(key, value), value) for key, value in current.items()}
        BASELINE.write_text(render(tightened), encoding="utf-8")
        print(f"✓ 卫生预算基线已收紧：{tightened}")
        return 0

    if baseline is None:
        print(f"✗ 缺少基线：{BASELINE.relative_to(ROOT)}（先跑 --write 建立）")
        return 1

    rows = measure()
    problems = []
    for key, value in sorted(current.items()):
        limit = baseline.get(key)
        if limit is None:
            problems.append(f"B-c 基线缺少指标 {key}")
        elif value > limit:
            problems.append(f"B-a {key} 回潮：{limit} → {value}（+{value - limit}）")
            for row in rows[key][:5]:
                print(f"   · {row}")
    if problems:
        print(f"✗ 卫生预算棘轮失败：{len(problems)} 处")
        for problem in problems:
            print("   " + problem)
        print("  正解：拆分/合并，不要用 --write 掩盖新增债务。")
        return 1

    tightened = [key for key, value in current.items() if value < baseline.get(key, value)]
    if tightened:
        print(f"  可收紧 {', '.join(tightened)}（跑 --write 更新基线）")
    print("✓ 卫生预算：" + " / ".join(f"{key}={value}" for key, value in sorted(current.items()))
          + " 均未超过基线")
    return 0


if __name__ == "__main__":
    sys.exit(main())
