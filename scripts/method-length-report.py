#!/usr/bin/env python3
"""长方法报告（只报告，不设门）。

用法：
    python3 scripts/method-length-report.py            # 列 >150 行的方法
    python3 scripts/method-length-report.py --threshold 100

## 为什么单独一个脚本

常见统计用「花括号计数」，但字符串里的 `{` / `}`（JSON 模板、正则、SQL）会让结果
严重虚高——实测 `Fetcher.detectContentType` 被算成 374 行（真实 15 行）、
`ToolPolicy.normalizeMCPCallArguments` 276 行（真实 22 行）。本脚本跳过字符串/字符
字面量与行注释后再配对花括号（实现见 `_method_lengths.py`）。

**报告不设门**：长方法分两类——「阶段多」（该拆）与「规则/数据多」（拆了只增加跳转）。
机器分不出这两类，所以这里只出报告，由人判断。防回流由 `check-method-length.py`
按「基线只减」承担：它不要求拆存量，只拦新增与回涨。
"""

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from _method_lengths import scan  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parent.parent
THRESHOLD = 150


def main() -> int:
    threshold = THRESHOLD
    if "--threshold" in sys.argv:
        threshold = int(sys.argv[sys.argv.index("--threshold") + 1])
    rows = scan(threshold, ROOT)
    print(f"主码中 > {threshold} 行的方法：{len(rows)} 个")
    for size, rel, name, line, _ in rows:
        print(f"  {size:4d}  {rel}:{line}  {name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
