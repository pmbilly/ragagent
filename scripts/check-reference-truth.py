#!/usr/bin/env python3
"""引用真实性守卫（C2）：包说明与公开文档里的类/包/文件路径必须真实存在。

三条判据：
  R1 `{@link ...}`：`com.ragagent` 全限定名必须能解析到真实类型或包；
  R2 旧包名黑名单：`common.session` / `chatpipeline` / `agent.management` /
     `agentm` / `:engine` 等不得出现在主码 `package-info` 与公开文档；
  R3 具体文件路径：反引号里以仓库模块开头、带文件扩展名且不含省略号的路径必须存在。

**不扫** `docs/internal/**`（过程记录，允许谈历史）与生成物
（`packages.md` / `domain-index.md` / `module-package-map.md` / `port-map.md` /
`pipeline-map.md`，由各自的生成器守卫）。

红态探针：在任一 package-info 加一行 `{@link com.ragagent.dead.Type}`，
或在公开文档加一个不存在的 `docs/architecture/dead.md`，本脚本必须非零退出。
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
GENERATED = {
    "docs/architecture/packages.md",
    "docs/architecture/domain-index.md",
    "docs/architecture/module-package-map.md",
    "docs/architecture/port-map.md",
    "docs/architecture/pipeline-map.md",
}
DEAD_NAMES = (
    "common.session",
    "common.tenant",
    "common.approval",
    "common.prompt",
    "common.knowledge",
    "chatpipeline",
    "agent.management",
    "agentm",
    ":engine",
    "platform.model",
    "ModelGateway",
    "EmbeddingGateway",
    "docs/superpowers/plans/",
)
PATH_ROOTS = (
    "domains/",
    "contract/",
    "platform/",
    "model-runtime/",
    "retrieval-engine/",
    "agent-engine/",
    "boot/",
    "scripts/",
    "docs/",
)
PATH_EXTS = (".java", ".md", ".json", ".sh", ".py", ".kts", ".yml", ".yaml", ".sql")
LINK_RE = re.compile(r"\{@link\s+([^}\s]+)")
CODE_SPAN_RE = re.compile(r"`([^`\n]+)`")
PKG_RE = re.compile(r"^package\s+([\w.]+);", re.M)


def build_index():
    fqns = set()
    packages = set()
    stems = set()
    by_package = {}
    for path in ROOT.rglob("*.java"):
        if "/build/" in str(path) or "/target/" in str(path):
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        match = PKG_RE.search(text)
        if not match:
            continue
        pkg = match.group(1)
        packages.add(pkg)
        fqns.add(f"{pkg}.{path.stem}")
        stems.add(path.stem)
        by_package.setdefault(pkg, set()).add(path.stem)
    return fqns, packages, stems, by_package


def resolve_link(link: str, current_pkg: str, fqns, packages, stems, by_package) -> bool:
    target = link.split("#")[0].strip()
    if not target or target.startswith("#"):
        return True
    if target.startswith("com.ragagent"):
        if target in fqns or target in packages:
            return True
        parts = target.split(".")
        # 嵌套类型：只允许剥掉大写开头的尾段（Outer.Inner → Outer）。
        for index in range(len(parts) - 1, 0, -1):
            prefix = ".".join(parts[:index])
            tail = parts[index:]
            if prefix in fqns and all(part[:1].isupper() for part in tail):
                return True
            # 包路径：只允许全小写尾段（...apikey.domain → ...apikey）。
            if prefix in packages and all(part[:1].islower() for part in tail):
                return True
        return False
    # 简单名：只校验仓库内确有同名类型；JDK/第三方类型不强行解析。
    simple = target.split(".")[-1]
    if simple in by_package.get(current_pkg, set()):
        return True
    return simple in stems


def target_files():
    files = []
    for path in ROOT.rglob("package-info.java"):
        if "/build/" in str(path) or "/target/" in str(path) or "/target/" in str(path) or "/docs/" in str(path):
            continue
        files.append(path)
    docs = [ROOT / "README.md"]
    docs += sorted((ROOT / "docs").glob("*.md"))
    docs += sorted((ROOT / "docs" / "architecture").glob("*.md"))
    for path in docs:
        rel = path.relative_to(ROOT).as_posix()
        if path.is_file() and rel not in GENERATED:
            files.append(path)
    return files


def scan_file(path: pathlib.Path, index):
    fqns, packages, stems, by_package = index
    text = path.read_text(encoding="utf-8")
    current_pkg = ""
    match = PKG_RE.search(text)
    if match:
        current_pkg = match.group(1)

    problems = []
    for lineno, line in enumerate(text.splitlines(), 1):
        for name in DEAD_NAMES:
            if name == "agentm":
                hit = re.search(r"\bagentm\b", line)
            else:
                hit = name in line
            if hit:
                problems.append((lineno, f"旧包名 {name}"))
        for link in LINK_RE.findall(line):
            if not resolve_link(link, current_pkg, fqns, packages, stems, by_package):
                problems.append((lineno, f"断链 {{@link {link}}}"))
        for span in CODE_SPAN_RE.findall(line):
            candidate = span.strip().split(":")[0]
            if not candidate.startswith(PATH_ROOTS):
                continue
            if not candidate.endswith(PATH_EXTS):
                continue
            if any(token in candidate for token in ("...", "*", "{", "}", "<", ">")):
                continue
            if not (ROOT / candidate).exists():
                problems.append((lineno, f"路径不存在 {candidate}"))
    return problems


def main() -> int:
    index = build_index()
    failures = []
    for path in target_files():
        problems = scan_file(path, index)
        for lineno, message in problems:
            failures.append(f"{path.relative_to(ROOT)}:{lineno}: {message}")

    if failures:
        print(f"✗ 引用真实性：{len(failures)} 处")
        for failure in failures:
            print("   " + failure)
        print("   处理：改类/包/文档后同步 package-info 与公开文档；过程记录放 docs/internal/。")
        return 1

    print(f"✓ 引用真实性：{len(target_files())} 个文件无断链/旧包名/死路径")
    return 0


if __name__ == "__main__":
    sys.exit(main())
