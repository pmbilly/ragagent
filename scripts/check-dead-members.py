#!/usr/bin/env python3
"""死成员守卫（B131）：重复 import / 未使用 import / 自 import / 未使用的 Logger 与 ObjectMapper 字段。

判据来自一次真实事故：B111 的「内联全限定名 → import」机械转换**重复添加过 import**
（同 FQN 出现两次，24 处散布 21 个文件）；而 **javac 不报未使用/重复 import，
spotless 的 removeUnusedImports 也不去重** ⇒ 只能靠 IDE 偶发提示，躺了一个月才被发现。
同批还抓到 4 个「搬走代码后遗留」的死字段（3 个 Logger/ObjectMapper + 1 个已被
`RequestFields` 接管的校验文案常量）。

B154 补三件事（都由 IDE 告警顺出来的）：
  · 新增 **D-d 自 import**：`StorageEngineConfig` 导入了**自己的嵌套类**，而 spotless 与
    旧版 D-b 都按"简单名在文件里出现过"判断——嵌套类本来就以简单名引用 ⇒ **两个工具同时被骗**；
  · 扫描面从 `main` 扩到 **`main` + `test`**：4 条重复 import 全在 `src/test`（D-a 那类）却无人管；
  · **D-c 收紧为只查 `private`**：旧版把 `public`/`protected` 一起收，而它们可被**别的文件**引用
    ⇒ 只看本文件必然误报（实测 `RecordingSupport.JSON_MAPPER` 就被跨文件用着）。

四条规则（口径刻意保守，避免误报）：
  D-a 重复 import：同一 FQN 在一个文件里 import 两次 ⇒ 删一条（纯冗余，无副作用）
  D-b 未使用 import：简单名在**除 import 行外**的任何位置都不出现 ⇒ 删
      （javadoc `{@link}` 里出现算"使用"—— 与 B119 的 javadoc 引用守卫不打架）
  D-c 未使用的 **private** Logger / ObjectMapper 字段：字段名只出现在声明行 ⇒ 删
      （§14.5 点名的两类。**刻意不查"所有私有字段"**：常量族的成员常为词汇表性质
       ——`AuditAction` 65 / `EventType` 38 / `LangfuseAttributes` 25 这类"声明但未引用"，
       实测全仓 934 处，做硬门只会变噪声；那种属人工复核。）
  D-d 自 import：import 的 FQN = 本文件 package + 本文件顶层类（或其后代）⇒ 必然冗余（零误报）

用法（仓库根目录）：python3 scripts/check-dead-members.py
"""

import pathlib
import re
import sys
from collections import Counter

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import _source_roots as _sr  # noqa: E402

IMPORT = re.compile(r'^import (?:static )?([\w.]+);')
PKG = re.compile(r'^package ([\w.]+);')
TOP_TYPE = re.compile(r'^\s*(?:public |final |abstract |sealed |non-sealed |strictfp )*'
                      r'(?:class|record|enum|interface)\s+(\w+)')
# D-c 只认 private 的这两类（§14.5 点名；public/protected 可被跨文件引用 ⇒ 本文件内看不见不算死）
DEAD_TYPE = re.compile(r'^    private\s+(?:static\s+)?(?:final\s+)?'
                       r'(?:Logger|ObjectMapper)\s+(\w+)\s*[;=]')


def scan_file(p: pathlib.Path) -> list[str]:
    lines = p.read_text(encoding='utf-8').splitlines()
    problems = []
    # D-a / D-b / D-d
    imports = [(i, l) for i, l in enumerate(lines) if l.startswith('import ')]
    for fq, n in Counter(l.rstrip(';') for _, l in imports).items():
        if n > 1:
            problems.append(f'D-a 重复 import（×{n}）{fq};')
    pkg = next((m.group(1) for l in lines if (m := PKG.match(l))), '')
    top = next((m.group(1) for l in lines if (m := TOP_TYPE.match(l))), '')
    own = f'{pkg}.{top}' if pkg and top else ''
    for i, l in imports:
        m = IMPORT.match(l)
        if not m:
            continue
        fq = m.group(1)
        simple = fq.split('.')[-1]
        if own and (fq == own or fq.startswith(own + '.')):
            problems.append(f'D-d 自 import（导入本文件的类型或其嵌套类）{fq};')
            continue
        if not any(re.search(r'(?<![\w.])' + re.escape(simple) + r'(?![\w])', x)
                   for j, x in enumerate(lines) if j != i and not x.startswith('import ')):
            problems.append(f'D-b 未使用 import {l.strip()}')
    # D-c
    for i, l in enumerate(lines):
        m = DEAD_TYPE.match(l)
        if not m:
            continue
        name = m.group(1)
        if any(re.search(r'(?<![\w.])' + re.escape(name) + r'(?![\w])', x)
               for j, x in enumerate(lines) if j != i):
            continue
        problems.append(f'D-c 未使用的 {"Logger" if "Logger" in l else "ObjectMapper"} 字段 {name}'
                        f'（搬走代码后的遗留）')
    return problems


def main() -> int:
    problems = []
    for p in _sr.all_java_files(("main", "test")):
        for msg in scan_file(p):
            problems.append(f'{p.relative_to(_sr.REPO)}: {msg}')
    if problems:
        print('✗ 守卫失败：')
        for x in problems:
            print('   ' + x)
        return 1
    print('✓ 守卫通过：无重复/未使用/自 import、无遗留的 private Logger/ObjectMapper 字段'
          '（扫 main + test）。')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
