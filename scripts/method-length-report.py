#!/usr/bin/env python3
"""长方法报告（只报告，不设门）。

用法：
    python3 scripts/method-length-report.py            # 列 >150 行的方法
    python3 scripts/method-length-report.py --threshold 100

## 为什么单独一个脚本

常见统计用「花括号计数」，但字符串里的 `{` / `}`（JSON 模板、正则、SQL）会让结果
严重虚高——实测 `Fetcher.detectContentType` 被算成 374 行（真实 15 行）、
`ToolPolicy.normalizeMCPCallArguments` 276 行（真实 22 行）。本脚本跳过字符串/字符
字面量与行注释后再配对花括号。

**不设门**：长方法分两类——「阶段多」（该拆）与「规则/数据多」（拆了只增加跳转）。
机器分不出这两类，所以只出报告，由人判断。
"""

import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MODULES = ["contract", "platform", "model-runtime", "retrieval-engine",
           "agent-engine", "domains", "boot"]
THRESHOLD = 150

SIG = ("    public ", "    private ", "    protected ", "    static ", "    final ",
       "    synchronized ")


def method_lengths(path: pathlib.Path):
    lines = path.read_text(encoding="utf-8", errors="ignore").splitlines()
    out = []
    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        if (not line.startswith(SIG) or "(" not in line or "class " in line
                or "interface " in line or "record " in line or "enum " in line):
            i += 1
            continue
        # 排除字段初始化（`private static final Logger log = X.y(...)`）与委托赋值
        if "=" in line and not line.rstrip().endswith("{"):
            i += 1
            continue
        depth = 0
        started = False
        in_str = None
        esc = False
        end = None
        for j in range(i, min(i + 800, len(lines))):
            text = lines[j]
            k = 0
            while k < len(text):
                c = text[k]
                if in_str:
                    if esc:
                        esc = False
                    elif c == "\\":
                        esc = True
                    elif c == in_str:
                        in_str = None
                else:
                    if c in "\"'":
                        in_str = c
                    elif c == "/" and k + 1 < len(text) and text[k + 1] == "/":
                        break
                    elif c == "{":
                        depth += 1
                        started = True
                    elif c == "}":
                        depth -= 1
                k += 1
            if started and depth <= 0:
                end = j
                break
        if end is not None:
            name = stripped.split("(")[0].split()[-1]
            out.append((end - i + 1, name, i + 1))
            i = end + 1
        else:
            i += 1
    return out


def main() -> int:
    threshold = THRESHOLD
    if "--threshold" in sys.argv:
        threshold = int(sys.argv[sys.argv.index("--threshold") + 1])
    rows = []
    for module in MODULES:
        for f in (ROOT / module / "src/main/java").rglob("*.java"):
            for size, name, line in method_lengths(f):
                if size > threshold:
                    rel = str(f.relative_to(ROOT))
                    rows.append((size, rel, name, line))
    rows.sort(reverse=True)
    print(f"主码中 > {threshold} 行的方法：{len(rows)} 个")
    for size, rel, name, line in rows:
        print(f"  {size:4d}  {rel}:{line}  {name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
