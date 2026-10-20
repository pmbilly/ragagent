#!/usr/bin/env python3
"""生成模块/包映射（docs/architecture/module-package-map.md）。

回答两个问题：
  1. 各模块有哪些主码包根、多少包/文件/控制器；
  2. 各业务域对应哪个包、多少文件/控制器（域数从源码实测，不写死）。

`--check` 校验生成物与源码一致；接入 `run-guards.sh`，改包/加控制器后不更新文档即红。
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "architecture" / "module-package-map.md"
MODULES = [
    ("contract", ":contract"),
    ("platform", ":platform"),
    ("model-runtime", ":model-runtime"),
    ("retrieval-engine", ":retrieval-engine"),
    ("agent-engine", ":agent-engine"),
    ("domains", ":domains"),
    ("boot", ":boot"),
]
PKG_RE = re.compile(r"^package\s+([\w.]+);", re.M)


def java_files(module: str, kind: str = "main"):
    return [p for p in (ROOT / module / "src" / kind / "java").rglob("*.java") if "/build/" not in str(p) and "/target/" not in str(p)]


def package_of(path: pathlib.Path) -> str:
    m = PKG_RE.search(path.read_text(encoding="utf-8"))
    return m.group(1) if m else ""


def root_of(pkg: str) -> str:
    if pkg == "com.ragagent":
        return "(根包)"
    prefix = "com.ragagent."
    return pkg[len(prefix):].split(".")[0] if pkg.startswith(prefix) else pkg


def module_row(module: str, label: str):
    files = java_files(module)
    packages = {package_of(p) for p in files if package_of(p)}
    roots = sorted({root_of(p) for p in packages})
    controllers = sum(1 for p in files if p.name.endswith("Controller.java"))
    roots_text = "、".join(f"`{r}`" for r in roots)
    return f"| `{label}` | {roots_text} | {len(packages)} | {len(files)} | {controllers} |"


def domain_rows():
    base = ROOT / "domains" / "src" / "main" / "java" / "com" / "ragagent"
    rows = []
    for d in sorted(p for p in base.iterdir() if p.is_dir()):
        files = [p for p in d.rglob("*.java") if "/build/" not in str(p) and "/target/" not in str(p)]
        controllers = sum(1 for p in files if p.name.endswith("Controller.java"))
        rows.append(f"| `{d.name}` | `com.ragagent.{d.name}` | {len(files)} | {controllers} |")
    return rows


def package_info_count() -> int:
    return sum(1 for module, _ in MODULES for p in java_files(module) if p.name == "package-info.java")


def boot_test_roots() -> int:
    base = ROOT / "boot" / "src" / "test" / "java"
    roots = set()
    for p in base.rglob("*.java"):
        pkg = package_of(p)
        if pkg:
            roots.add(root_of(pkg))
    return len(roots)


def render() -> str:
    domains = domain_rows()
    lines = [
        "<!-- 由 scripts/gen-module-map.py 生成；不要手改。 -->",
        "# 模块与包映射",
        "",
        "这张表回答\"类/包属于哪个模块\"，包内职责见 [`packages.md`](packages.md)，",
        "整体分层与设计原则见 [`overview.md`](overview.md)。",
        "",
        f"## {len(MODULES)} 个模块",
        "",
        "| 模块 | 主码包根 | 包数 | Java 文件 | 控制器 |",
        "|---|---|---:|---:|---:|",
    ]
    lines.extend(module_row(m, label) for m, label in MODULES)
    lines += [
        "",
        f"## {len(domains)} 个业务域（domains）",
        "",
        "| 域 | 主码包 | 文件 | 控制器 |",
        "|---|---|---:|---:|",
    ]
    lines.extend(domains)
    lines += [
        "",
        "## 查找规则",
        "",
        "1. **先看包根**：`com.ragagent.contract.*` → `:contract`；`platform` → `:platform`；",
        "   `agent` → `:agent-engine`；`retrieval` → `:retrieval-engine`；",
        "   `embedding` / `llm` / `modelcontext` / `modelruntime` / `rerank` → `:model-runtime`；",
        f"   其余 {len(domains)} 个包根 → `:domains`；`com.ragagent.config` 与根包 → `:boot`。",
        "2. **HTTP 路由**在 `domains/src/main/java/com/ragagent/<域>/controller/`；",
        "   跨模块公开类型在 `contract/`，端口实现与全局 Bean 在 `boot/config/`。",
        "3. **测试**：业务域测试住在 `domains/src/test/`；HTTP 集成测试住在 `boot/src/test/`；",
        "   共享录制/桩夹具在 `platform/src/testFixtures/`。",
        "",
        "## 刻意例外",
        "",
        "- `:model-runtime` 有 5 个主码包根（`embedding` / `llm` / `modelcontext` / `modelruntime` / `rerank`），",
        "  按能力族拆分，不与模块同名。",
        f"- `:boot` 的测试树有 {boot_test_roots()} 个业务包根：集成测试必须从 HTTP 面进，只能住在组合根。",
        "- `domains` 测试树仍有 10 个跨模块测试（platform 5、model-runtime 4、contract 1），",
        "  依赖 domains 载荷，迁走会形成反向边；登记在 `scripts/check-module-graph.py` 的",
        "  `MISPLACED_TEST_ALLOW`（带理由，只许减）。",
        "- 测试夹具包为 `com.ragagent.fixtures`；`com.ragagent.common` / `com.ragagent.support`",
        "  是已解散模块的旧包，守卫禁止回流。",
        "",
        "## 相关",
        "",
        f"- [`packages.md`](packages.md)：{package_info_count()} 份 `package-info` 的包级职责索引。",
        "- [`overview.md`](overview.md)：模块依赖图、设计原则、代码在哪。",
        "",
    ]
    return "\n".join(lines)


def main() -> int:
    expected = render()
    if "--check" in sys.argv:
        actual = OUT.read_text(encoding="utf-8") if OUT.is_file() else ""
        if actual != expected:
            print(f"✗ 模块/包映射已漂移：{OUT.relative_to(ROOT)}")
            print("  运行 python3 scripts/gen-module-map.py 重新生成。")
            return 1
        print("✓ 模块/包映射与源码一致")
        return 0
    OUT.write_text(expected, encoding="utf-8")
    print(f"✓ 已生成 {OUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
