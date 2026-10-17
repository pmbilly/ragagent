#!/usr/bin/env python3
"""控制器纯度守卫：HTTP 面只编排。

用法：
    python3 scripts/check-controller-purity.py

## 判据

  C1 控制器不得 import 数据访问类型（`*.mapper.*Mapper`）——查询下沉 service / repository。
     当前违例 0（硬零）。
  C2 控制器的 `catch (RuntimeException ...)` 数量只许减（棘轮）：
     裸转发交给全局处理器即可；带业务语义的转换保留，但不得新增。

C2 的基线写在 `scripts/controller-catch.baseline.json`（`--write` 收紧）。
"""

import json
import pathlib
import re
import sys

ROOT = pathlib.Path("domains/src/main/java")
BASELINE = pathlib.Path("scripts/controller-catch.baseline.json")
MAPPER_IMPORT = re.compile(r"^import\s+com\.ragagent\.[\w.]*mapper\.[\w.]+Mapper;", re.M)
CATCH = re.compile(r"catch\s*\(\s*RuntimeException\b")


def main() -> int:
    problems = []
    catches = {}
    for f in sorted(ROOT.rglob("*Controller.java")):
        text = f.read_text(encoding="utf-8")
        rel = str(f.relative_to(ROOT.parent.parent.parent))
        for m in MAPPER_IMPORT.finditer(text):
            line = text[:m.start()].count("\n") + 1
            problems.append(f"C1 {rel}:{line} 直连数据访问层（{m.group(0).strip()}）")
        n = len(CATCH.findall(text))
        if n:
            catches[rel] = n

    total = sum(catches.values())
    old = json.loads(BASELINE.read_text(encoding="utf-8")) if BASELINE.exists() else {}
    old_total = sum(old.values())

    if "--write" in sys.argv:
        BASELINE.write_text(json.dumps(catches, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
                            encoding="utf-8")
        print(f"✓ 基线已写入：{total} 处 / {len(catches)} 文件")
        return 0

    increased = {f: (old.get(f, 0), n) for f, n in catches.items() if n > old.get(f, 0)}
    for f, (before, after) in sorted(increased.items()):
        problems.append(f"C2 {f}: RuntimeException catch {before} → {after}（只许减）")

    if problems:
        print("✗ 控制器纯度守卫失败：")
        for p in problems:
            print("   " + p)
        print("  C1：数据访问下沉 service/repository；C2：裸转发交给全局处理器。")
        return 1

    print(f"✓ 控制器纯度：无直连 Mapper；RuntimeException catch {old_total} → {total}"
          + ("（净减）" if total < old_total else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
