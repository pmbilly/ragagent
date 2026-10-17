#!/usr/bin/env python3
"""错误语义守卫：三条规则，各有各的扫描范围（都不宽，避免变成噪声源）。

规则一（B217）：检索引擎层不得在 catch 块里把「条件/过滤」重置为空。
    判据：`解析失败 ⇒ filter = {}` 把**权限/范围边界**降级成了"没有边界"。空 filter 对
    ES/OpenSearch 而言是"匹配全库"——在 RAG 场景下，这意味着过滤条件丢失时不是报错，
    而是**返回更宽的结果集**，可能把本该被租户 / KB / 启用状态挡掉的内容捞出来。
    范围刻意窄（只 `retrieval-engine/.../retrieval/engine/`）：别处的
    `catch → return createObjectNode()` 是"坏 jsonb 视为无数据"，属宽松读，不是本规则靶子。

规则二：非边界层不得有"空转 catch"。
    判据：`catch (Exception|Throwable)` 块内**既没有语句、也没有理由注释** ⇒ 红。
    真实 bug 被吞的风险正是靠"这条 catch 为什么可以忍"的注释与 review 兜住的；
    注释本身就是判据（写清理由的容错是常态，沉默的空转不是）。
    边界层（controller/api/task/channel/client/mapper）豁免：那里的兜底是 HTTP/队列语义的一部分。

规则三：Jackson 空体文案只许出现在 `platform/web/JsonBodyErrors.java`。
    值 `"No content to map due to end-of-input"` 是**线上行为**（契约金片逐字节钉住），
    但它同时与 Jackson 版本耦合（消息文案可能变）。收敛到一处常量后，升级只需改一个文件；
    本规则防止调用点再次各自复制字面量。

口径：逐字符状态机先把注释与字符串字面量抹成空格（等长替换 ⇒ 偏移与行号不漂），
再做 catch 块的大括号配对；规则三只看代码、不看注释（注释可以继续引用该文案）。
"""

import pathlib
import re
import sys

# ── 规则一：检索层 catch 内清空条件 ──────────────────────────────────────────
ENGINE_ROOT = pathlib.Path('retrieval-engine/src/main/java/com/ragagent/retrieval/engine')
RESET_CALL = 'createObjectNode('

# ── 规则二：非边界层空转 catch ──────────────────────────────────────────────
SOURCE_ROOTS = ['contract', 'platform', 'model-runtime', 'retrieval-engine',
                'agent-engine', 'domains', 'boot']
BOUNDARY_SEGMENTS = ('/controller/', '/api/', '/task/', '/channel/', '/client/', '/mapper/')

# ── 规则三：Jackson 空体文案只许出现在收敛点 ─────────────────────────────────
BODY_ERROR_LITERAL = '"No content to map due to end-of-input"'
BODY_ERROR_OWNER = 'platform/src/main/java/com/ragagent/platform/web/JsonBodyErrors.java'

CATCH_HEAD = re.compile(r'\bcatch\s*\(\s*(?:final\s+)?([A-Za-z0-9_.]*)\s*[A-Za-z0-9_]*\s*\)\s*\{')


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


def blank_comments(text: str) -> str:
    """只抹注释、保留字符串（规则三要查代码里的字面量）。"""
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
                state = 'str'
            elif c == "'":
                state = 'chr'
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
        # 字符串/字符内部：原样保留，只处理转义与收尾
        if c == '\\':
            out.append(text[i:i + 2])
            i += 2
            continue
        quote = '"' if state == 'str' else "'"
        if c == quote:
            state = None
        out.append(c)
        i += 1
    return ''.join(out)


def catch_blocks(clean: str):
    """产出 (起始偏移, 头部结束偏移, 块结束偏移, 异常类型)。"""
    for m in CATCH_HEAD.finditer(clean):
        depth, j = 1, m.end()
        while j < len(clean) and depth > 0:
            if clean[j] == '{':
                depth += 1
            elif clean[j] == '}':
                depth -= 1
            j += 1
        yield m.start(), m.end(), j, m.group(1)


def engine_reset_violations():
    if not ENGINE_ROOT.is_dir():
        return ['扫描根不存在：%s（路径腐化？）' % ENGINE_ROOT]
    hits = []
    for f in sorted(ENGINE_ROOT.rglob('*.java')):
        clean = blank_out(f.read_text(encoding='utf-8'))
        for start, body_start, end, _ in catch_blocks(clean):
            if RESET_CALL in clean[body_start:end]:
                hits.append(f'{f}:{clean[:start].count(chr(10)) + 1}')
    return hits


def empty_catch_violations():
    hits = []
    for root in SOURCE_ROOTS:
        base = pathlib.Path(root)
        if not base.is_dir():
            continue
        for f in base.glob('src/main/java/**/*.java'):
            rel = '/' + str(f).replace('\\', '/')
            if any(seg in rel for seg in BOUNDARY_SEGMENTS):
                continue
            raw = f.read_text(encoding='utf-8')
            clean = blank_out(raw)
            for start, body_start, end, kind in catch_blocks(clean):
                short = kind.rsplit('.', 1)[-1]
                if short not in ('Exception', 'Throwable'):
                    continue
                code_body = clean[body_start:end - 1]
                raw_body = raw[body_start:end - 1]
                has_statement = bool(code_body.strip())
                has_reason = ('//' in raw_body) or ('/*' in raw_body)
                if not has_statement and not has_reason:
                    hits.append(f'{f}:{clean[:start].count(chr(10)) + 1}')
    return hits


def body_message_violations():
    hits = []
    for root in SOURCE_ROOTS:
        base = pathlib.Path(root)
        if not base.is_dir():
            continue
        for f in base.glob('src/main/java/**/*.java'):
            rel = str(f).replace('\\', '/')
            if rel == BODY_ERROR_OWNER:
                continue
            code = blank_comments(f.read_text(encoding='utf-8'))
            if BODY_ERROR_LITERAL in code:
                line = code[:code.index(BODY_ERROR_LITERAL)].count('\n') + 1
                hits.append(f'{f}:{line}')
    return hits


def main() -> int:
    failed = False

    reset = engine_reset_violations()
    if reset:
        failed = True
        print('✗ 检索引擎层：catch 块内把条件/过滤重置为空（= 查询范围意外放大）：')
        for v in reset:
            print(f'    {v}')
        print('  → 边界条件解析失败必须抛出，不得降级为"没有边界"')

    empty = empty_catch_violations()
    if empty:
        failed = True
        print('✗ 非边界层空转 catch（既无语句也无理由注释）：')
        for v in empty:
            print(f'    {v}')
        print('  → 要么真的处理/上抛，要么在块内写一行"为什么可以忍"的注释')

    spread = body_message_violations()
    if spread:
        failed = True
        print('✗ Jackson 空体文案外泄（应引用 JsonBodyErrors.EMPTY_BODY）：')
        for v in spread:
            print(f'    {v}')
        print('  → 该文案是与 Jackson 版本耦合的耦合点，只许出现在 JsonBodyErrors')

    if failed:
        return 1
    print('✓ 守卫通过：检索层无"catch 内清空条件"；非边界层无空转 catch；'
          'Jackson 空体文案只在 JsonBodyErrors。')
    return 0


if __name__ == '__main__':
    sys.exit(main())
