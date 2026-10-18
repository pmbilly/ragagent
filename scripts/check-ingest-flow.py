#!/usr/bin/env python3
"""入库链路文档守卫：`docs/architecture/ingest-flow.md` 引用的类必须真实存在。

与 `check-qa-flow.py` 同一口径：把 `{@code Xxx}` 的大写标识符当作类名，
逐个检查仓库里有没有同名 `.java`；另外钉住四个关键小节，防止文档退化成空壳。
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DOC = ROOT / "docs/architecture/ingest-flow.md"
CODE = re.compile(r"\{@code ([A-Z]\w+)\}")
LINK = re.compile(r"\{@link\s+([\w.#]+)")
SECTIONS = ("## 一分钟版", "## 主阶段", "## 想改 X 去哪", "## 读代码顺序")


def main() -> int:
    if not DOC.exists():
        print(f"✗ 缺少 {DOC.relative_to(ROOT)}")
        return 1
    text = DOC.read_text(encoding="utf-8")
    missing_sections = [section for section in SECTIONS if section not in text]
    if missing_sections:
        print("✗ 入库链路文档缺少关键小节：")
        for section in missing_sections:
            print("   " + section)
        return 1

    names = set(CODE.findall(text))
    for link in LINK.findall(text):
        target = link.split("#")[0]
        if target.startswith("com.ragagent"):
            names.add(target)
        elif target[:1].isupper():
            names.add(target)

    missing = []
    for name in sorted(names):
        if name.startswith("com.ragagent"):
            package, simple = name.rsplit(".", 1)
            expected = f"/src/main/java/{package.replace('.', '/')}/"
            if not any(expected in str(path)
                       for path in ROOT.rglob(f"{simple}.java")):
                missing.append(name)
        elif not any(ROOT.rglob(f"{name}.java")):
            missing.append(name)

    if missing:
        print(f"✗ 入库链路文档引用了 {len(missing)} 个不存在的类：")
        for name in missing:
            print(f"    {name}")
        print("  处理：类改名/删除后同步 ingest-flow.md。")
        return 1

    print(f"✓ 入库链路文档：{len(names)} 个类/链接均存在，关键小节齐全")
    return 0


if __name__ == "__main__":
    sys.exit(main())
