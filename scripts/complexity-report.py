#!/usr/bin/env python3
"""复杂度候选审查线（报告级，**不**作为 CI 硬门）。

方案 §12.5 的口径：行数棘轮只防膨胀、不能证明内聚，故补三类指标做**人工复核候选**：

  1. 方法分支数 > 15（近似：`if/for/while/case/&&/||/?:/catch` 计数，含嵌套）
  2. 构造器依赖数 > 8（参数个数；构造器注入是这件类的"扇入面"）
  3. 公开可变字段（非 static / 非 final / public；与 A18 同口径，此处只统计不判红）

  4. 包级 SCC：见 `scripts/check-package-cycles.py`（已有硬门 + 基线棘轮），本脚本不重复。

**为什么只是报告**：这三条是"候选审查线"而非全库硬门——存量超标处多为既有大件
（`MemoryService` / `KnowledgeService` 等），立刻判红会让守卫变成噪声（同 A19 的判据边界
取法）；先让数字可见，逐轮"碰到哪域降哪域"。要当门用时走 `--strict`（退出码非 0）。

用法：python3 scripts/complexity-report.py [--strict] [--top N]
"""

from __future__ import annotations

import pathlib
import re
import sys

REPO = pathlib.Path(__file__).resolve().parents[1]
ROOTS = ("domains", "engine", "common", "boot")
BRANCH = re.compile(r"\b(if|for|while|catch)\s*\(|\bcase\s|&&|\|\||\?:")
CTOR = re.compile(r"^(\s{4})(?:public|protected|private)?\s*([A-Z]\w*)\s*\(([^)]*)\)\s*\{")
FIELD = re.compile(r"^(\s{4})public\s+(?!static|final)([\w<>,.\[\]\s]+)\s+(\w+)\s*[;=]")


def java_files():
    for root in ROOTS:
        base = REPO / root / "src" / "main" / "java"
        if base.is_dir():
            yield from base.rglob("*.java")


def params_count(raw: str) -> int:
    raw = raw.strip()
    if not raw:
        return 0
    depth, n = 0, 1
    for ch in raw:
        if ch in "<([":
            depth += 1
        elif ch in ">)]":
            depth -= 1
        elif ch == "," and depth == 0:
            n += 1
    return n


def scan(path: pathlib.Path) -> tuple[list, list, list]:
    lines = path.read_text(encoding="utf-8", errors="ignore").splitlines()
    rel = str(path.relative_to(REPO))
    ctors, fields, methods = [], [], []
    cls = path.stem
    for i, line in enumerate(lines):
        m = CTOR.match(line)
        if m and m.group(2) == cls:
            n = params_count(m.group(3))
            if n > 8:
                ctors.append((rel, i + 1, n))
            continue
        f = FIELD.match(line)
        if f:
            fields.append((rel, i + 1, f.group(3).strip()))
        # 方法：4 空格缩进 + 返回类型 + 名 + (…)
        if re.match(r"^\s{4}(?:public|private|protected|static|final|synchronized|\s)*[\w<>\[\],.\s]+\s+\w+\s*\([^;]*\)\s*\{\s*$", line):
            depth, branches = 0, 0
            for j in range(i, min(i + 400, len(lines))):
                depth += lines[j].count("{") - lines[j].count("}")
                branches += len(BRANCH.findall(lines[j]))
                if depth <= 0 and j > i:
                    break
            if branches > 15:
                name = re.search(r"(\w+)\s*\([^;]*\)\s*\{\s*$", line)
                methods.append((rel, i + 1, (name.group(1) if name else line.strip())[:40], branches))
    return ctors, fields, methods


def main() -> int:
    strict = "--strict" in sys.argv
    top = 15
    if "--top" in sys.argv:
        top = int(sys.argv[sys.argv.index("--top") + 1])
    ctors, fields, methods = [], [], []
    for p in java_files():
        c, f, m = scan(p)
        ctors += c
        fields += f
        methods += m
    print(f"扫描 {len(list(java_files()))} 个主源文件（候选审查线，非硬门）")
    print(f"\n■ 方法分支数 > 15：{len(methods)} 处")
    for rel, ln, name, br in sorted(methods, key=lambda x: -x[3])[:top]:
        print(f"    {br:3d}  {rel}:{ln}  {name}")
    print(f"\n■ 构造器依赖 > 8：{len(ctors)} 处")
    for rel, ln, n in sorted(ctors, key=lambda x: -x[2])[:top]:
        print(f"    {n:3d}  {rel}:{ln}")
    print(f"\n■ 公开可变实例字段：{len(fields)} 处（A18 已判红同名口径，此处仅统计）")
    print("    ⚠️ 多数是 @ConfigurationProperties 绑定类（Spring 靠可变字段注入，属设计使然）——"
          "复核时先剔这一族，剩下的才是候选。")
    for rel, ln, name in fields[:top]:
        print(f"         {rel}:{ln}  {name}")
    print("\n■ 包级 SCC：python3 scripts/check-package-cycles.py（硬门 + 基线棘轮）")
    over = len(methods) + len(ctors) + len(fields)
    print(f"\n候选总数 {over}（--strict 时才作为门）")
    return 1 if strict and over else 0


if __name__ == "__main__":
    sys.exit(main())
