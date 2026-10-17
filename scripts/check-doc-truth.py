#!/usr/bin/env python3
"""文档事实守卫：README 里的可验证数字必须与代码一致。

用法：
    python3 scripts/check-doc-truth.py

## 为什么需要

README 是新人第一份材料，数字漂了比没有更糟：曾出现"每个包有 package-info"
（实际 48% 缺失）、"统一响应外壳"（实际两处例外）这类失真。本守卫只校验
**机器可判定的少数事实**，不做全文正确性检查。

## 判据

  D1 `七个 Gradle 模块` ↔ `settings.gradle.kts` 的 include 数；
  D2 `N 个 HTTP 控制器` ↔ `domains` 下 `*Controller.java` 数；
  D3 `每个包有 package-info.java` ↔ 主码含 java 的包全部存在 package-info。

锚点消失（文案被改写）同样报红：守卫与文档必须同步演进，否则校验会静默失效。
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MODULES = ["contract", "platform", "model-runtime", "retrieval-engine", "agent-engine", "domains", "boot"]


def module_count() -> int:
    text = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
    return len(re.findall(r'^include\("', text, re.M))


def controller_count() -> int:
    return sum(1 for _ in (ROOT / "domains" / "src" / "main" / "java").rglob("*Controller.java"))


def missing_package_info() -> list[str]:
    missing = []
    for m in MODULES:
        for root in (ROOT / m / "src" / "main" / "java",):
            if not root.exists():
                continue
            for dp, _, fn in __import__("os").walk(root):
                if not any(f.endswith(".java") for f in fn):
                    continue
                if "package-info.java" not in fn:
                    missing.append(str(pathlib.Path(dp).relative_to(ROOT)))
    return missing


def main() -> int:
    readme = (ROOT / "README.md").read_text(encoding="utf-8")
    problems = []

    if "七个 Gradle 模块" in readme:
        actual = module_count()
        if actual != 7:
            problems.append(f"D1 README 称七个模块，settings.gradle.kts 实际 {actual}")
    else:
        problems.append("D1 README 缺少「七个 Gradle 模块」锚点——模块数变化时请同步守卫")

    m = re.search(r"(\d+)\s*个 HTTP 控制器", readme)
    if m:
        actual = controller_count()
        if int(m.group(1)) != actual:
            problems.append(f"D2 README 称 {m.group(1)} 个控制器，实际 {actual}")
    else:
        problems.append("D2 README 缺少「N 个 HTTP 控制器」锚点")

    if "每个包有 `package-info.java`" in readme:
        missing = missing_package_info()
        if missing:
            problems.append(f"D3 README 称每个包有 package-info，实际 {len(missing)} 个包缺失"
                            f"（例：{missing[0]}）")
    else:
        problems.append("D3 README 缺少 package-info 断言——若改为措辞请同步守卫")

    if problems:
        print("✗ 文档事实守卫失败：")
        for p in problems:
            print("   " + p)
        print("  改 README 与改代码必须同一提交；锚点消失也要同步更新本守卫。")
        return 1

    print("✓ 文档事实：模块数 / 控制器数 / package-info 覆盖与 README 一致")
    return 0


if __name__ == "__main__":
    sys.exit(main())
