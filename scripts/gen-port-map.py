#!/usr/bin/env python3
"""生成 Port 对照表：接口 → 实现 → 调用方 → 是否跨界。

用法：
    python3 scripts/gen-port-map.py          # 生成 docs/architecture/port-map.md
    python3 scripts/gen-port-map.py --check  # 校验生成物（守卫用）

## 为什么需要

`contract` 里有 40 个 `*Port`，读代码时 IDE 从接口跳不到实现（实现分散在
domains / retrieval-engine 等模块）。本表把「谁调、谁实现、为什么存在」放在一处，
避免每次靠 grep 反查。

## 分类规则

  * **跨模块**：调用方与实现不在同一 Gradle 模块 ⇒ 端口必要（打破编译依赖）；
  * **域间**：同模块但不同业务域 ⇒ 端口服务于域解耦；
  * **域内**：同域 ⇒ 多为多余抽象，可评估内联。

生成物是派生数据：改 Port / 实现 / 调用方后重跑本脚本，不要手改。
"""

import os
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))
import _source_roots as _sr  # noqa: E402

OUT = ROOT / "docs/architecture/port-map.md"
IMPL = re.compile(r"\bimplements\s+([^{]+)")
USES = re.compile(r"^import\s+com\.ragagent\.contract\.[\w.]*\.(\w+Port);", re.M)
EXTENDS = re.compile(r"\binterface\s+(\w+)\s+extends\s+([^{]+)\{")


def module_of(path: pathlib.Path) -> str:
    rel = str(path.relative_to(ROOT))
    for m in _sr.MODULE_DIRS:
        if rel.startswith(m + "/"):
            return m
    return "?"


def domain_of(path: pathlib.Path) -> str:
    parts = path.parts
    if "domains" not in parts:
        return module_of(path)
    i = parts.index("domains")
    # domains/src/main/java/com/ragagent/<域>/...
    return parts[i + 6] if len(parts) > i + 6 and parts[i + 4] == "com" else "?"


def main() -> int:
    ports = {}
    for p in sorted((ROOT / "contract/src/main/java").rglob("*Port.java")):
        ports[p.stem] = p

    impls = {}
    users = {}
    for f in _sr.all_java_files():
        text = f.read_text(encoding="utf-8", errors="ignore")
        for m in IMPL.finditer(text):
            for name in re.findall(r"\b(\w+Port)\b", m.group(1)):
                if name in ports:
                    impls.setdefault(name, []).append(f)
        for m in USES.finditer(text):
            if m.group(1) in ports:
                users.setdefault(m.group(1), []).append(f)

    # 被其它 Port extends 的窄面：随复合端口评估，不单独判「域内/无实现」
    facets = set()
    for p in ports.values():
        for m in EXTENDS.finditer(p.read_text(encoding="utf-8", errors="ignore")):
            for name in re.findall(r"\b(\w+Port)\b", m.group(2)):
                if name in ports and name != m.group(1):
                    facets.add(name)

    rows = []
    for name in sorted(ports):
        imps = impls.get(name, [])
        impl = imps[0] if imps else None
        impl_set = set(imps)
        callers = sorted({domain_of(f) for f in users.get(name, []) if f not in impl_set})
        impl_mod = module_of(impl) if impl else "?"
        caller_mods = sorted({module_of(f) for f in users.get(name, []) if f not in impl_set})
        if name in facets:
            kind = "复合面（随复合端口）"
        elif impl and any(m != impl_mod for m in caller_mods):
            kind = "跨模块（必要）"
        elif impl and any(d != domain_of(impl) for d in callers):
            kind = "域间（解耦）"
        elif impl:
            kind = "域内（可评估内联）"
        else:
            kind = "无实现（检查）"
        rows.append((name, impl, impl_mod, callers, kind))

    lines = [
        "# Port 对照表",
        "",
        "> 由 `scripts/gen-port-map.py` 从代码生成，**不要手改**；`--check` 守卫防漂移。",
        "> 读代码时先查这张表：接口 → 实现在哪个类 → 谁在调用。",
        "",
        "## 统计",
        "",
    ]
    stat = {}
    for r in rows:
        stat[r[4]] = stat.get(r[4], 0) + 1
    for k in sorted(stat):
        lines.append(f"- {k}：**{stat[k]}**")
    lines += ["",
              "## 明细",
              "",
              "| Port | 实现类 | 实现模块 | 调用方域 | 类型 |",
              "|---|---|---|---|---|"]
    for name, impl, impl_mod, callers, kind in rows:
        impl_txt = f"`{impl.name[:-5]}`" if impl else "—"
        lines.append(f"| `{name}` | {impl_txt} | {impl_mod} | {'、'.join(callers) or '—'} | {kind} |")
    lines.append("")
    content = "\n".join(lines)

    if "--check" in sys.argv:
        cur = OUT.read_text(encoding="utf-8") if OUT.exists() else ""
        if cur != content:
            print(f"✗ {OUT.relative_to(ROOT)} 与代码不同步；运行 python3 scripts/gen-port-map.py")
            return 1
        print(f"✓ Port 对照表与代码一致（{len(rows)} 个）")
        return 0
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(content, encoding="utf-8")
    print(f"✓ 已生成 {OUT.relative_to(ROOT)}：{len(rows)} 个 Port")
    return 0


if __name__ == "__main__":
    sys.exit(main())
