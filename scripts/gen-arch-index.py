#!/usr/bin/env python3
"""从 package-info.java 生成包索引（docs/architecture/packages.md）。

用法：
  python3 scripts/gen-arch-index.py          # 生成/更新
  python3 scripts/gen-arch-index.py --check  # 校验生成物与源码一致（守卫用）
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "architecture" / "packages.md"

JAVADOC_RE = re.compile(r"/\*\*(.*?)\*/", re.S)
PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+);", re.M)
HTML_RE = re.compile(r"<[^>]+>")
INLINE_RE = re.compile(r"\{@(?:link|code|literal)\s+([^}]*)\}")


def package_files() -> list[pathlib.Path]:
    return sorted(ROOT.glob("*/src/main/java/**/package-info.java"))


def first_sentence(text: str) -> str:
    package_match = PACKAGE_RE.search(text)
    prefix = text[:package_match.start()] if package_match else text
    blocks = JAVADOC_RE.findall(prefix)
    if not blocks:
        return "（无 package-info 摘要）"

    lines = []
    for line in blocks[-1].splitlines():
        line = line.strip()
        if line.startswith("*"):
            line = line[1:].strip()
        lines.append(line)
    body = " ".join(lines)
    body = INLINE_RE.sub(r"\1", body)
    body = HTML_RE.sub("", body)
    body = re.sub(r"\s+", " ", body).strip()
    body = body.replace("|", "\\|")
    if not body:
        return "（无 package-info 摘要）"

    for sep in ("。", "；", ". "):
        idx = body.find(sep)
        if 0 < idx < 120:
            return body[: idx + len(sep)].strip()
    return body[:100].strip()


def build() -> str:
    rows = []
    for path in package_files():
        rel = path.relative_to(ROOT)
        module = rel.parts[0]
        text = path.read_text(encoding="utf-8")
        package_match = PACKAGE_RE.search(text)
        package = package_match.group(1) if package_match else str(rel.parent)
        rows.append((module, package, first_sentence(text)))

    lines = [
        "<!-- 由 scripts/gen-arch-index.py 生成；不要手改。 -->",
        "# 包索引",
        "",
        f"{len(rows)} 份 `package-info.java` 的首句摘要，按模块分组。找代码时先按模块，再按包名。",
        "",
        "| 模块 | 包 | 职责 |",
        "|---|---|---|",
    ]
    for module, package, summary in sorted(rows, key=lambda r: (r[0], r[1])):
        lines.append(f"| `:{module}` | `{package}` | {summary} |")
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    content = build()
    if "--check" in sys.argv:
        if not OUT.is_file():
            print(f"✗ 架构索引不存在：{OUT.relative_to(ROOT)}")
            print("  运行 python3 scripts/gen-arch-index.py 生成。")
            return 1
        if OUT.read_text(encoding="utf-8") != content:
            print(f"✗ 架构索引过期：{OUT.relative_to(ROOT)} 与 package-info 不一致")
            print("  运行 python3 scripts/gen-arch-index.py 重新生成。")
            return 1
        print(f"✓ 架构索引与 {len(package_files())} 份 package-info 同步")
        return 0

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(content, encoding="utf-8")
    print(f"✓ 已生成 {OUT.relative_to(ROOT)}：{len(package_files())} 个包")
    return 0


if __name__ == "__main__":
    sys.exit(main())
