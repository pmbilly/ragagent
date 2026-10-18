#!/usr/bin/env python3
"""业务域 `package-info` 四要素守卫（C0b）。

每个业务域的 `package-info.java` 必须声明四项：
  种类 / 入口 / 对外契约面 / 依赖

种类必须与 `scripts/capability-kinds.json` 的 label 一致；新增/删除域由
`gen-domain-index.py` 先拦一次，本守卫负责“打开域就能看到分类”。

红态探针：删掉任一 `<b>入口</b>：` 行，本脚本必须非零退出。
"""

import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CONFIG = ROOT / "scripts" / "capability-kinds.json"
DOMAINS = ROOT / "domains" / "src" / "main" / "java" / "com" / "ragagent"
FIELDS = ("种类", "入口", "对外契约面", "依赖")
VALUE_RE = {field: re.compile(rf"<b>{field}</b>：([^<\n]+)") for field in FIELDS}


def main() -> int:
    data = json.loads(CONFIG.read_text(encoding="utf-8"))
    kinds = data["kinds"]
    domains = data["domains"]
    problems = []

    for name, meta in sorted(domains.items()):
        path = DOMAINS / name / "package-info.java"
        if not path.is_file():
            problems.append(f"{name}: 缺少 package-info.java")
            continue
        text = path.read_text(encoding="utf-8")
        for field in FIELDS:
            marker = f"<b>{field}</b>："
            count = text.count(marker)
            if count != 1:
                problems.append(f"{name}: {field} 标记出现 {count} 次（要求 1）")
                continue
            value = VALUE_RE[field].search(text)
            if not value or not value.group(1).strip():
                problems.append(f"{name}: {field} 为空")
        kind_match = VALUE_RE["种类"].search(text)
        expected = kinds[meta["kind"]]["label"]
        if kind_match and kind_match.group(1).strip() != expected:
            problems.append(
                f"{name}: 种类={kind_match.group(1).strip()}，"
                f"与 capability-kinds.json 的 {expected} 不一致"
            )

    if problems:
        print("✗ 业务域 package-info 四要素不完整：")
        for problem in problems:
            print("   " + problem)
        print("  要求：种类 / 入口 / 对外契约面 / 依赖 各一行；种类必须与分类配置一致。")
        return 1

    print(f"✓ 业务域 package-info 四要素：{len(domains)}/{len(domains)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
