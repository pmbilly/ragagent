#!/usr/bin/env python3
"""主码内联全限定类名守卫（棘轮：只许减不许增）。

**为什么单列一条**：内联全限定名（`java.util.List.of()`、`com.ragagent.x.Y`）编译能过、
spotless 不管、大部分测试也不管——只有域级约定测试会拦，而那只覆盖一个域。
抽成脚本后进快闸，改完立刻可见，不必等全量 `mvn verify`。

判定（与 `KnowledgeCodeConventionsTest#noFullyQualifiedNamesInCode` 同口径）：

  * 只扫 `src/main/java` 的代码行，跳过 `import` / `package` 行、`//` 与块注释；
  * 正则 `(?:[a-z][\\w]*\\.){2,}[A-Z]\\w*`，即「至少两段小写包名 + 大驼峰类名」；
  * 行内引号数为奇数时视为字符串字面量，跳过（错误文案里出现全限定名不算）。

用法：

  python3 scripts/check-fully-qualified-names.py            # 检查（有新增即退出 1）
  python3 scripts/check-fully-qualified-names.py --write    # 收紧基线（有新增则拒绝）
  python3 scripts/check-fully-qualified-names.py --bootstrap  # 一次性建基线（存量债务）
"""

import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
BASELINE = ROOT / "scripts" / "fully-qualified-names.baseline.json"
FQN = re.compile(r"(?:[a-z][\w]*\.){2,}[A-Z]\w*")


def sources():
    for module in sorted((ROOT / "backend").iterdir()):
        base = module / "src/main/java"
        if not base.is_dir():
            continue
        for path in sorted(base.rglob("*.java")):
            if "/target/" in str(path) or "/build/" in str(path):
                continue
            yield path


def scan() -> tuple[dict, dict]:
    """返回 (计数表, 明细表)。基线只存计数——按行号登记会因插入 import 而误报。"""
    hits = {}
    detail = {}
    for path in sources():
        rel = path.relative_to(ROOT).as_posix()
        in_block = False
        found = []
        for idx, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            trimmed = line.strip()
            if "/*" in line and "*/" not in line:
                in_block = True
            code_line = (
                not in_block
                and not trimmed.startswith("*")
                and not trimmed.startswith("//")
                and not trimmed.startswith("import ")
                and not trimmed.startswith("package ")
            )
            if code_line:
                for m in FQN.finditer(line):
                    if line[: m.start()].count('"') % 2 == 0:
                        found.append("%d:%s" % (idx, m.group()))
            if "*/" in line:
                in_block = False
        if found:
            hits[rel] = len(found)
            detail[rel] = sorted(found)
    return hits, detail


def main() -> int:
    write = "--write" in sys.argv
    bootstrap = "--bootstrap" in sys.argv
    actual, detail = scan()
    base = json.loads(BASELINE.read_text(encoding="utf-8")) if BASELINE.is_file() else {}

    added = {k: v for k, v in actual.items() if v > base.get(k, 0)}

    if write or bootstrap:
        if write and added:
            print("✗ 拒绝 --write：基线之外出现新增内联全限定名（先改掉再收紧）")
            for k, v in sorted(added.items()):
                print("    ＋ %s  基线 %d → 实际 %d" % (k, base.get(k, 0), v))
            return 1
        BASELINE.write_text(json.dumps(actual, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
                            encoding="utf-8")
        print("✓ 基线已收紧：%d 个文件 / %d 处" % (len(actual), sum(actual.values())))
        return 0

    if added:
        print("✗ 主码出现内联全限定类名（请 import 后用短名）：")
        for k, v in sorted(added.items()):
            print("    %s  基线 %d → 实际 %d" % (k, base.get(k, 0), v))
            for item in detail[k][:5]:
                print("        %s" % item)
        return 1

    total = sum(actual.values())
    print("✓ 内联全限定名：0 处新增（基线 %d 处 / %d 个文件）" % (total, len(actual)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
