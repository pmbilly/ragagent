#!/usr/bin/env python3
"""第十守卫（B217）：检索引擎层不得在 catch 块里把「条件/过滤」重置为空。

判据：`解析失败 ⇒ filter = {}` 把**权限/范围边界**降级成了"没有边界"。空 filter 对
ES/OpenSearch 而言是"匹配全库"——在 RAG 场景下，这意味着过滤条件丢失时不是报错，
而是**返回更宽的结果集**，可能把本该被租户 / KB / 启用状态挡掉的内容捞出来。

B217 修掉 Elasticsearch V7 的三处同款（向量路 / 关键词路 / 源索引批量读）后设此守卫，
**零基线纯禁令**：engine 的检索引擎目录下，catch 块内不得新建空 JSON 节点。

范围刻意的窄（只 `engine/src/main/java/com/ragagent/retrieval/engine/`）：
- 别处的 `catch → return createObjectNode()` 是"坏 jsonb 视为无数据"，属 §2-12 宽松读，
  不是本守卫的靶子；
- 这里判的只有一件具体的事：**边界条件不得被降级**。

口径：逐字符状态机先把注释与字符串字面量抹成空格（等长替换 ⇒ 行号不漂），再做 catch
块的大括号配对；块内出现 `createObjectNode()` 即红。合理降级请写在 catch 之外，
或改成具名异常分类（如 `classifyFailure` 那样：类型缺失不影响按状态码判定）。
"""

import pathlib
import re
import sys

SCAN_ROOT = pathlib.Path('engine/src/main/java/com/ragagent/retrieval/engine')
CATCH_HEAD = re.compile(r'\bcatch\s*\([^)]*\)\s*\{')
RESET_CALL = 'createObjectNode('


def blank_out(text: str) -> str:
    """把注释与字符串/字符字面量替换成等长空白（保留换行）⇒ 大括号可安全配对。"""
    out = []
    i, n = 0, len(text)
    state = None  # None | 'line' | 'block' | 'str' | 'chr'
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ''
        if state is None:
            if c == '/' and nxt == '/':
                state, out = 'line', out + ['  ']
                i += 2
                continue
            if c == '/' and nxt == '*':
                state, out = 'block', out + ['  ']
                i += 2
                continue
            if c == '"':
                state, out = 'str', out + [' ']
                i += 1
                continue
            if c == "'":
                state, out = 'chr', out + [' ']
                i += 1
                continue
            out.append(c)
            i += 1
            continue
        if state == 'line':
            out.append('\n' if c == '\n' else ' ')
            if c == '\n':
                state = None
            i += 1
            continue
        if state == 'block':
            if c == '*' and nxt == '/':
                state, out = None, out + ['  ']
                i += 2
                continue
            out.append('\n' if c == '\n' else ' ')
            i += 1
            continue
        # str / chr
        quote = '"' if state == 'str' else "'"
        if c == '\\':
            out.append('  ')
            i += 2
            continue
        if c == quote:
            state, out = None, out + [' ']
            i += 1
            continue
        out.append('\n' if c == '\n' else ' ')
        i += 1
    return ''.join(out)


def scan(path: pathlib.Path):
    raw = path.read_text(encoding='utf-8')
    clean = blank_out(raw)
    hits = []
    for m in CATCH_HEAD.finditer(clean):
        depth, j = 1, m.end()
        while j < len(clean) and depth > 0:
            if clean[j] == '{':
                depth += 1
            elif clean[j] == '}':
                depth -= 1
            j += 1
        body = clean[m.end():j]
        if RESET_CALL in body:
            line = clean[:m.start()].count('\n') + 1
            hits.append((line, body.strip().split('\n')[0][:80]))
    return hits


def main() -> int:
    if not SCAN_ROOT.is_dir():
        print(f'✗ 扫描根不存在：{SCAN_ROOT}（路径腐化？）')
        return 1
    violations = []
    for f in sorted(SCAN_ROOT.rglob('*.java')):
        for line, snippet in scan(f):
            violations.append(f'{f}:{line}')
    if violations:
        print('✗ 检索引擎层：catch 块内把条件/过滤重置为空（= 查询范围意外放大）：')
        for v in violations:
            print(f'    {v}')
        print('  → 边界条件解析失败必须抛出，不得降级为"没有边界"')
        return 1
    print('✓ 守卫通过：检索引擎层无"catch 内清空条件"的降级（边界条件失败即抛）。')
    return 0


if __name__ == '__main__':
    sys.exit(main())
