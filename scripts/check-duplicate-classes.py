#!/usr/bin/env python3
"""跨模块同名类守卫：同一简单类名不得出现在多个模块的主码里。

同名类会让 IDE 自动 import 出现多个候选（历史上 `LogSanitizer` / `JsonValues` /
`HtmlEntities` / `ZhipuProvider` 各有一组），拿错实现往往在运行期才暴露。

判据：`src/main/java` 的类文件名（去掉 .java）在模块间唯一；`package-info` 豁免。
这是硬零判据，不设基线——同名就改名或合并，不做"登记例外"。

红态探针：在两个模块各放一个 `DuplicateProbe.java`，本脚本必须非零退出；
跑完删除探针。
"""

import collections
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MODULES = ["contract", "platform", "model-runtime", "retrieval-engine", "agent-engine", "domains", "boot"]
EXEMPT = {"package-info", "module-info"}


def main() -> int:
    owners: dict[str, set[str]] = collections.defaultdict(set)
    for module in MODULES:
        for path in (ROOT / module / "src" / "main" / "java").rglob("*.java"):
            if path.stem in EXEMPT:
                continue
            owners[path.stem].add(module)

    duplicates = {name: sorted(modules) for name, modules in owners.items() if len(modules) > 1}
    if duplicates:
        print("✗ 跨模块同名类守卫失败：")
        for name, modules in sorted(duplicates.items()):
            print(f"   {name}: {modules}")
        print("  同名类会让 IDE 自动 import 拿错实现；请改名或合并到单一模块。")
        return 1

    print(f"✓ 跨模块同名类：{len(owners)} 个简单类名均唯一")
    return 0


if __name__ == "__main__":
    sys.exit(main())
