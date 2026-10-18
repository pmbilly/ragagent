#!/usr/bin/env python3
"""链路文档守卫：`docs/architecture/qa-flow.md` 引用的类必须真实存在。

用法：
    python3 scripts/check-qa-flow.py

## 为什么

链路文档最容易腐化：类改名/删除后，文档还在讲一个不存在的类。本守卫只做一件事——
把文档里 `{@code Xxx}` 形式的大写标识符当作类名，逐个检查仓库里有没有同名 `.java`。

小写开头（`null` / `true`）跳过；`{@code PluginError}` 这类必须真的存在。
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DOC = ROOT / "docs/architecture/qa-flow.md"
CODE = re.compile(r"\{@code ([A-Z]\w+)\}")


def main() -> int:
    if not DOC.exists():
        print(f"✗ 缺少 {DOC.relative_to(ROOT)}")
        return 1
    text = DOC.read_text(encoding="utf-8")
    names = sorted(set(CODE.findall(text)))
    missing = [n for n in names if not any(ROOT.rglob(f"{n}.java"))]
    if missing:
        print(f"✗ 链路文档引用了 {len(missing)} 个不存在的类：")
        for n in missing:
            print(f"    {n}")
        print("  处理：类改名/删除后同步 qa-flow.md。")
        return 1
    print(f"✓ 链路文档：{len(names)} 个类名均存在")
    return 0


if __name__ == "__main__":
    sys.exit(main())
