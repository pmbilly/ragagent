#!/usr/bin/env python3
"""装配点清单（K4）：每个 `@Configuration` 类必须在 `scripts/wiring-points.baseline.json` 登记。

## 为什么需要

组合根在 `:boot`，但 `domains` 内有 10 个**域内装配点**（`QaWiring` 等）。此前
"跨模块 Bean 去哪找"只能靠读代码，而新增一个 `@Configuration` 类没有任何守卫会注意。
本守卫把装配面变成清单：**新增即红**（必须显式登记并写清理由），**删除也要清登记**
（否则清单指向已不存在的类）。文档侧同步：`docs/architecture/overview.md` 的
「装配点」一节必须与清单逐个对齐，避免"清单在脚本里、人看的是文档"两套事实。

## 判据

  W1 扫描集（全部模块 `src/main` 的 `@Configuration`）== 清单键集；
  W2 分类与落点一致：composition-root 只能是 `com.ragagent.config`（:boot），
     domain-internal 只能在 domains，module-internal 只能在能力模块；
  W3 每条登记必须有 `why`（≥8 字），防占位式登记；
  W4 overview.md「装配点」一节列出的类名集 == 清单类名集。

## 用法

    python3 scripts/check-wiring-points.py
    python3 scripts/check-wiring-points.py --list     # 按分类打印
"""

import argparse
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
BASELINE = ROOT / "scripts/wiring-points.baseline.json"
OVERVIEW = ROOT / "docs/architecture/overview.md"
MODULES = ["contract", "platform", "model-runtime", "retrieval-engine",
           "agent-engine", "domains", "boot"]
# 短形式（可带参数）与全限定形式都要认：只认短形式会漏掉 FQN 写法
CONFIG = re.compile(
    r"^@Configuration(?:\s*\([^)]*\))?\s*$"
    r"|@org\.springframework\.context\.annotation\.Configuration\b",
    re.M)
PACKAGE = re.compile(r"^package ([\w.]+);", re.M)
DOC_NAME = re.compile(r"`([A-Z]\w*(?:Wiring|Config))`")
KIND_RULES = {
    "composition-root": lambda fqcn, module: fqcn.startswith("com.ragagent.config."),
    "domain-internal": lambda fqcn, module: module == "domains",
    "module-internal": lambda fqcn, module: module in {
        "platform", "model-runtime", "retrieval-engine", "agent-engine"},
}


def scan() -> dict[str, str]:
    found = {}
    for module in MODULES:
        src = ROOT / module / "src" / "main" / "java"
        if not src.exists():
            continue
        for path in src.rglob("*.java"):
            text = path.read_text(encoding="utf-8")
            if not CONFIG.search(text):
                continue
            pkg = PACKAGE.search(text)
            if pkg is None:
                continue
            found[f"{pkg.group(1)}.{path.stem}"] = module
    return found


def documented() -> set[str]:
    text = OVERVIEW.read_text(encoding="utf-8")
    section = re.search(r"^## 装配点.*?$(.*?)(?=^## |\Z)", text, re.M | re.S)
    if section is None:
        return set()
    return set(DOC_NAME.findall(section.group(1)))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--list", action="store_true", help="按分类打印登记项")
    args = parser.parse_args()

    data = json.loads(BASELINE.read_text(encoding="utf-8"))
    points = data.get("points", {})
    actual = scan()
    problems = []

    for fqcn in sorted(actual.keys() - points.keys()):
        problems.append(f"W1 未登记装配点：{fqcn}（{actual[fqcn]}）——登记到 "
                        f"scripts/wiring-points.baseline.json 并写明分类与理由")
    for fqcn in sorted(points.keys() - actual.keys()):
        problems.append(f"W1 陈旧登记：{fqcn} 已不存在或不再是 @Configuration，请删除该条目")

    for fqcn, entry in sorted(points.items()):
        kind = entry.get("kind", "")
        rule = KIND_RULES.get(kind)
        module = actual.get(fqcn, "")
        if rule is None:
            problems.append(f"W2 {fqcn} 的分类 \"{kind}\" 不在三种之内")
        elif module and not rule(fqcn, module):
            problems.append(f"W2 {fqcn} 分类为 {kind}，但落在 {module} 模块")
        if len(entry.get("why", "").strip()) < 8:
            problems.append(f"W3 {fqcn} 缺少足够具体的 why（≥8 字）")

    doc = documented()
    expect = {fqcn.rsplit(".", 1)[-1] for fqcn in points}
    for name in sorted(expect - doc):
        problems.append(f"W4 overview.md「装配点」一节缺 {name}")
    for name in sorted(doc - expect):
        problems.append(f"W4 overview.md 列了未登记的装配点 {name}")

    if problems:
        print("✗ 装配点清单守卫失败：")
        for p in problems:
            print("   " + p)
        return 1

    if args.list:
        for kind in KIND_RULES:
            names = sorted(f.rsplit(".", 1)[-1] for f, e in points.items() if e["kind"] == kind)
            print(f"{kind}（{len(names)}）：{'、'.join(names)}")

    counts = {kind: sum(1 for e in points.values() if e["kind"] == kind) for kind in KIND_RULES}
    print(f"✓ 装配点清单：{len(points)} 个（组合根 {counts['composition-root']} / "
          f"域内 {counts['domain-internal']} / 模块内 {counts['module-internal']}），"
          f"扫描与文档均一致")
    return 0


if __name__ == "__main__":
    sys.exit(main())
