#!/usr/bin/env python3
"""控制器 → 领域实体导入棘轮（C10a）。

管理面控制器的响应应走读模型/契约视图；直接 `import ...domain.X` 会让实体字段变更
直接涟漪到 HTTP 层。本守卫按“控制器文件 → 域实体 import 数”登记基线：
新增文件或数量上涨即红；只允许通过迁移读模型来下降。

用法：
  python3 scripts/check-controller-entity-imports.py
  python3 scripts/check-controller-entity-imports.py --write   # 只收紧基线
"""

import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DOMAINS = ROOT / "domains/src/main/java"
BASELINE = ROOT / "scripts/controller-entity-imports.baseline.json"
IMPORT = re.compile(r"^import com\.ragagent\.(?:[\w]+\.)+domain\.[\w.]+;", re.M)


def scan() -> dict[str, int]:
    counts = {}
    for path in DOMAINS.rglob("*Controller.java"):
        found = len(IMPORT.findall(path.read_text(encoding="utf-8")))
        if found:
            counts[str(path.relative_to(DOMAINS))] = found
    return counts


def load() -> dict[str, int]:
    if not BASELINE.exists():
        return {}
    data = json.loads(BASELINE.read_text(encoding="utf-8"))
    return data.get("files", {})


def render(counts: dict[str, int]) -> str:
    data = {
        "note": "C10a 控制器领域实体导入基线：只许降；新增控制器/新增 import 即红。"
                "迁移到读模型/契约视图后跑 --write 收紧。",
        "files": dict(sorted(counts.items())),
    }
    return json.dumps(data, ensure_ascii=False, indent=2) + "\n"


def main() -> int:
    current = scan()
    baseline = load()
    if "--write" in sys.argv:
        if not baseline:
            tightened = dict(current)
        else:
            tightened = {path: min(n, current.get(path, 0)) for path, n in baseline.items()}
        tightened = {path: n for path, n in tightened.items() if n > 0}
        BASELINE.write_text(render(tightened), encoding="utf-8")
        print(f"✓ 基线已收紧：{len(tightened)} 个控制器 / {sum(tightened.values())} 处 import")
        return 0

    problems = []
    for path, count in sorted(current.items()):
        old = baseline.get(path)
        if old is None:
            problems.append(f"新增控制器实体 import：{path}（{count} 处）")
        elif count > old:
            problems.append(f"控制器实体 import 上涨：{path}（{count} > 基线 {old}）")

    stale = sorted(set(baseline) - set(current))
    if stale:
        problems.append("基线已失效（控制器已无实体 import，请 --write）：" + ", ".join(stale[:10]))

    if problems:
        print(f"✗ 控制器实体导入：{len(problems)} 处问题"
              f"（当前 {len(current)} 个控制器 / {sum(current.values())} 处 import）")
        for problem in problems:
            print("   " + problem)
        return 1

    print(f"✓ 控制器实体导入：{len(current)} 个控制器 / {sum(current.values())} 处 import"
          f"（基线 {len(baseline)} / {sum(baseline.values())}）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
