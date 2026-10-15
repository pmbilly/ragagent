#!/usr/bin/env python3
"""把 `:contract` 里**无法解析**的 javadoc `{@link ...}` 降级为 `{@code ...}`。

背景（HANDOFF §12/M2）：搬移/回域后，javadoc 里的跨模块链接会失效（该坑已出现 12 次）。
- 之前只处理 **FQN 形态**（`{@link com.ragagent.common.X}`）⇒ **裸名形态**（`{@link X}`，
  X 在 L1/域侧、不在契约内）会漏网 ⇒ 本脚本补上。
- 只动 `contract/` 下的文件：契约是零依赖底层，**只允许**链接到契约内类型。

判定：`{@link X}`（含 `X#member`）可保留 ⇔
  ① 同包存在 `X.java`；或 ② 该文件 import 了以 `.X` 结尾的类型；或 ③ X 是 JDK 类型（首字母大写且无点、
  且 import 了 `java.*` 下同名类型 ⇒ 由 ② 覆盖）。
否则降级 `{@code X...}`。

用法：python3 scripts/fix-javadoc-links.py [--write]   # 默认 dry-run 只报数
"""
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CONTRACT = ROOT / "contract" / "src" / "main" / "java"


def main() -> int:
    write = "--write" in sys.argv
    files = list(CONTRACT.rglob("*.java"))
    same_pkg = {f.stem for f in files}
    fixed_total = 0
    files_touched = []
    for f in files:
        src = f.read_text(encoding="utf-8")
        imports = set(re.findall(r"^import\s+(?:static\s+)?([\w.]+);", src, re.M))
        imported_simple = {i.rsplit(".", 1)[-1]: i for i in imports}
        out = []
        changed = False
        for m in re.finditer(r"\{@link\s+([^}]+)\}", src):
            target = m.group(1).strip()
            # 只处理类名（可能带 #member / 泛型）
            head = re.split(r"[#\s(]", target, 1)[0].strip()
            if "com.ragagent" in head:
                if head.startswith("com.ragagent.contract."):
                    continue  # FQN 契约内 → 保留
                replacement = "{@code " + target + "}"
                out.append((m.start(), m.end(), replacement))
                changed = True
                continue
            if head.startswith("#") or "." in head:
                continue
            simple = re.sub(r"<.*>", "", head)
            if simple in same_pkg or simple in imported_simple:
                continue
            replacement = "{@code " + target + "}"
            out.append((m.start(), m.end(), replacement))
            changed = True
        if not changed:
            continue
        new = src
        for start, end, rep in reversed(out):
            new = new[:start] + rep + new[end:]
        fixed_total += len(out)
        files_touched.append(f.relative_to(ROOT))
        if write:
            f.write_text(new, encoding="utf-8")
    mode = "已写入" if write else "dry-run（加 --write 落盘）"
    print(f"✓ javadoc 链接降级：{fixed_total} 处 / {len(files_touched)} 文件（{mode}）")
    for p in files_touched[:20]:
        print("   ", p)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
