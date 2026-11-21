#!/usr/bin/env python3
"""能力种类边界守卫（C0c）：跨 kind 直接 import 禁止，同 kind 允许。

判据：
  - 域 → 域的直接 import 必须同 kind（产品能力 / 集成适配 / 控制面 / 能力配置 /
    初始化向导 / 策略库）；
  - 跨 kind 一律经 `contract` 端口，不在实现包之间直连；
  - 同 kind 内的族关系（如 knowledge ⇄ wiki、session ⇄ memory）由
    `check-domain-contracts.py` 的 R10 规则继续管。

红态探针：在任一 `product` 域临时加一行 `import com.ragagent.auth...;`，
本脚本必须非零退出。
"""

import json
import pathlib
import re
import sys
from collections import defaultdict

ROOT = pathlib.Path(__file__).resolve().parent.parent
CONFIG = ROOT / "scripts" / "capability-kinds.json"
DOMAINS = ROOT / "backend" / "app" / "src" / "main" / "java" / "com" / "ragagent"
IMPORT_RE = re.compile(r"^import\s+(?:static\s+)?com\.ragagent\.(\w+)\.(\w+)\.", re.M)

#: 跨 kind 只允许 import 对方的契约包；实现包（service/repository/adapter/domain/mapper）一律禁
LEGAL_IMPORT_PKG = {"api", "dto"}


def main() -> int:
    data = json.loads(CONFIG.read_text(encoding="utf-8"))
    kinds = data["kinds"]
    domains = data["domains"]
    kind_of = {name: meta["kind"] for name, meta in domains.items()}
    level_of = {name: meta.get("level") for name, meta in domains.items()}

    violations = defaultdict(list)
    for source in sorted(domains):
        source_dir = DOMAINS / source
        for path in source_dir.rglob("*.java"):
            text = path.read_text(encoding="utf-8", errors="ignore")
            for target, sub_pkg in IMPORT_RE.findall(text):
                if target not in domains or target == source:
                    continue
                if kind_of[source] == kind_of[target]:
                    continue
                # 跨 kind：仅当「上级 → 下级」或「同级」且目标是契约包（api/dto）时放行。
                # 依据：蓝图 §5.4 只规定三级域依赖，没有 kind 轴；kind 是本地补充，
                # 不该否决蓝图明确允许的方向。同级放行与 R10 保持一致（2026-11-21）：
                # 判据统一为 level + api/dto，避免两条守卫对同一条边给出相反结论。
                src_lv = level_of.get(source)
                tgt_lv = level_of.get(target)
                if (
                    src_lv is not None
                    and tgt_lv is not None
                    and src_lv >= tgt_lv
                    and sub_pkg in LEGAL_IMPORT_PKG
                ):
                    continue
                violations[(source, target)].append(str(path.relative_to(ROOT)))

    if violations:
        print("✗ 能力种类边界违例：跨 kind 只允许「上级→下级 或 同级」的 api/dto，其余改走契约")
        for (source, target), files in sorted(violations.items()):
            print(
                f"   {source}（{kinds[kind_of[source]]['label']}）"
                f" → {target}（{kinds[kind_of[target]]['label']}）：{len(files)} 个文件"
            )
            for file in files[:5]:
                print(f"      {file}")
            if len(files) > 5:
                print(f"      … 另有 {len(files) - 5} 个")
        return 1

    print(f"✓ 能力种类边界：{len(domains)} 域 / 0 条违规跨 kind 依赖")
    return 0


if __name__ == "__main__":
    sys.exit(main())
