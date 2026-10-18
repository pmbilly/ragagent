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
DOMAINS = ROOT / "domains" / "src" / "main" / "java" / "com" / "ragagent"
IMPORT_RE = re.compile(r"^import\s+com\.ragagent\.(\w+)\.", re.M)


def main() -> int:
    data = json.loads(CONFIG.read_text(encoding="utf-8"))
    kinds = data["kinds"]
    domains = data["domains"]
    kind_of = {name: meta["kind"] for name, meta in domains.items()}

    violations = defaultdict(list)
    for source in sorted(domains):
        source_dir = DOMAINS / source
        for path in source_dir.rglob("*.java"):
            text = path.read_text(encoding="utf-8", errors="ignore")
            targets = set(IMPORT_RE.findall(text)) & set(domains)
            for target in sorted(targets):
                if target == source:
                    continue
                if kind_of[source] != kind_of[target]:
                    violations[(source, target)].append(
                        str(path.relative_to(ROOT))
                    )

    if violations:
        print("✗ 能力种类边界违例：跨 kind 不得直连实现包，请改走 contract 端口")
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

    print(f"✓ 能力种类边界：{len(domains)} 域 / 0 条跨 kind 直连")
    return 0


if __name__ == "__main__":
    sys.exit(main())
