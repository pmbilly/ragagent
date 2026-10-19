#!/usr/bin/env python3
"""生成业务域索引（docs/architecture/domain-index.md）。

回答三个问题：
  1. 这个域属于哪类能力（6 类：产品能力 / 集成适配 / 控制面 / 能力配置 / 初始化向导 / 策略库）；
  2. 它有没有 HTTP 面、前端页面在哪；
  3. 它对外提供哪些契约端口。

分类配置在 `scripts/capability-kinds.json`；新增/删除域必须同步，否则本脚本直接失败。
`--check` 校验生成物与源码一致；接入 `run-guards.sh`，改域/加控制器后不更新文档即红。
"""

import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CONFIG = ROOT / "scripts" / "capability-kinds.json"
OUT = ROOT / "docs" / "architecture" / "domain-index.md"
DOMAINS = ROOT / "domains" / "src" / "main" / "java" / "com" / "ragagent"
CONTRACT = ROOT / "contract" / "src" / "main" / "java" / "com" / "ragagent" / "contract"
FRONTEND_SRC = ROOT / "frontend" / "src"

IMPLEMENTS_RE = re.compile(r"\bimplements\s+([^{]+)\{")
INTERFACE_RE = re.compile(r"\binterface\s+(\w+)")


def frontend_paths(raw: str):
    """登记值是 `views/a · views/b.vue`；括号里是说明文字，不参与校验。"""
    cleaned = re.sub(r"（[^）]*）", "", raw)
    return [part.strip() for part in cleaned.split("·") if part.strip()]


def load_config():
    data = json.loads(CONFIG.read_text(encoding="utf-8"))
    kinds = data["kinds"]
    domains = data["domains"]
    actual = {p.name for p in DOMAINS.iterdir() if p.is_dir()}

    problems = []
    missing = sorted(actual - set(domains))
    extra = sorted(set(domains) - actual)
    if missing:
        problems.append("未登记域：" + "、".join(missing))
    if extra:
        problems.append("登记的域不存在：" + "、".join(extra))
    for name, meta in sorted(domains.items()):
        if meta.get("kind") not in kinds:
            problems.append(f"{name}: 未知种类 {meta.get('kind')!r}")
        if not meta.get("frontend"):
            problems.append(f"{name}: 缺少 frontend 登记")
        else:
            for path in frontend_paths(meta["frontend"]):
                if not (FRONTEND_SRC / path).exists():
                    problems.append(f"{name}: 前端路径不存在 → {path}")
    for kind, meta in kinds.items():
        if not any(m.get("kind") == kind for m in domains.values()):
            problems.append(f"种类 {kind!r} 没有任何域使用")
    if problems:
        print("✗ 能力分类配置不完整：")
        for problem in problems:
            print("   " + problem)
        raise SystemExit(1)
    return kinds, domains


def java_files(domain_dir: pathlib.Path):
    return [p for p in domain_dir.rglob("*.java") if "/build/" not in str(p)]


def controllers(domain_dir: pathlib.Path):
    return sorted(p.stem for p in domain_dir.rglob("*Controller.java"))


def contract_surfaces():
    """契约面的跨域接口：*Port 与 *Gateway（后者待统一为 *Port）。"""
    surfaces = []
    for path in list(CONTRACT.rglob("*Port.java")) + list(CONTRACT.rglob("*Gateway.java")):
        text = path.read_text(encoding="utf-8")
        if INTERFACE_RE.search(text):
            surfaces.append(path.stem)
    return sorted(set(surfaces))


def implemented_surfaces(domain_dir: pathlib.Path, surface_names: set[str]):
    found = set()
    for path in domain_dir.rglob("*.java"):
        text = path.read_text(encoding="utf-8", errors="ignore")
        for match in IMPLEMENTS_RE.finditer(text):
            for part in match.group(1).split(","):
                simple = part.split("<")[0].strip().split(".")[-1]
                if simple in surface_names:
                    found.add(simple)
    return sorted(found)


def mono(text: str) -> str:
    return text if text.startswith("—") else f"`{text}`"


def render(kinds, domains) -> str:
    surface_names = set(contract_surfaces())
    rows = []
    for name, meta in domains.items():
        domain_dir = DOMAINS / name
        files = java_files(domain_dir)
        ctrls = controllers(domain_dir)
        ports = implemented_surfaces(domain_dir, surface_names)
        rows.append({
            "name": name,
            "kind": meta["kind"],
            "files": len(files),
            "controllers": ctrls,
            "ports": ports,
            "frontend": meta["frontend"],
        })

    lines = [
        "<!-- 由 scripts/gen-domain-index.py 生成；不要手改。 -->",
        "# 业务域索引（按能力种类）",
        "",
        "> 三个问题：这个域属于哪类能力？有没有 HTTP 面？对外提供哪些契约面（Port/Gateway）？",
        "> 分类配置在 [`scripts/capability-kinds.json`](../../scripts/capability-kinds.json)；",
        "> 端口权威表在 [`port-map.md`](port-map.md)。",
        "",
        "## 六类能力",
        "",
        "| 种类 | 域数 | 文件 | 控制器 | 说明 |",
        "|---|---:|---:|---:|---|",
    ]
    for kind, meta in kinds.items():
        kind_rows = [r for r in rows if r["kind"] == kind]
        kind_files = sum(r["files"] for r in kind_rows)
        kind_ctrls = sum(len(r["controllers"]) for r in kind_rows)
        lines.append(
            f"| **{meta['label']}** | {len(kind_rows)} | {kind_files} | {kind_ctrls} | {meta['description']} |"
        )

    lines += [
        "",
        "## 域明细",
        "",
        "| 域 | 种类 | 文件 | 控制器 | 对外契约面 | 前端页面 |",
        "|---|---|---:|---:|---:|---|",
    ]
    for kind, meta in kinds.items():
        for row in sorted((r for r in rows if r["kind"] == kind),
                          key=lambda r: (-r["files"], r["name"])):
            lines.append(
                f"| `{row['name']}` | {meta['label']} | {row['files']} | "
                f"{len(row['controllers']) or '—'} | {len(row['ports']) or '—'} | "
                f"{mono(row['frontend'])} |"
            )

    lines += [
        "",
        "> 文件数含 `package-info`，与 `module-package-map.md` 同口径；契约面含 `*Port` 与 `*Gateway`"
        "（后者在 C5b 统一为 `*Port`）。",
    ]

    lines += [
        "",
        "## 入口与契约面",
        "",
        "| 域 | HTTP 入口 | 对外契约面 |",
        "|---|---|---|",
    ]
    for kind, meta in kinds.items():
        for row in sorted((r for r in rows if r["kind"] == kind),
                          key=lambda r: (-r["files"], r["name"])):
            ctrls = "、".join(f"`{c}`" for c in row["controllers"]) or "—（无 HTTP 面）"
            ports = "、".join(f"`{p}`" for p in row["ports"]) or "—"
            lines.append(f"| `{row['name']}` | {ctrls} | {ports} |")

    lines += [
        "",
        "## 怎么用",
        "",
        "1. **找需求落点**：先按“种类”判断，再看域明细；新增域必须先在 "
        "[`capability-kinds.json`](../../scripts/capability-kinds.json) 登记。",
        "2. **依赖规则**：`check-capability-kinds.py` 上线后执行（产品能力不得直连集成/控制面等）。",
        "3. **端口明细**的权威来源是 [`port-map.md`](port-map.md)；本页只做域级汇总。",
        "4. **前端页面**是人工登记；改页面路由时同步改 JSON——路径不存在时 `--check` 会报红。",
        "",
    ]
    return "\n".join(lines)


def main() -> int:
    kinds, domains = load_config()
    expected = render(kinds, domains)
    if "--check" in sys.argv:
        actual = OUT.read_text(encoding="utf-8") if OUT.is_file() else ""
        if actual != expected:
            print(f"✗ 业务域索引已漂移：{OUT.relative_to(ROOT)}")
            print("  运行 python3 scripts/gen-domain-index.py 重新生成。")
            return 1
        print(f"✓ 业务域索引与源码一致（{len(domains)} 域 / {len(kinds)} 类）")
        return 0
    OUT.write_text(expected, encoding="utf-8")
    print(f"✓ 已生成 {OUT.relative_to(ROOT)}（{len(domains)} 域 / {len(kinds)} 类）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
