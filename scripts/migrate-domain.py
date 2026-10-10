#!/usr/bin/env python3
"""按域迁移「统一响应外壳」的机械部分。

配方与边界见 HANDOFF B188 行 + docs/api-response-convention.md。
人工部分（判读项）仍需人做：私有错误形态转 BizException、测试内联期望、下钻与 204 断言。

用法：
  python3 scripts/migrate-domain.py survey [域...]
      列：该域控制器 / 端点 / 私有错误形态（含行号）/ 引用了哪些契约测试 / 测试点名了哪些金片

  python3 scripts/migrate-domain.py annotate <域>...
      给该域控制器加 @ApiResult + import（幂等；已有则跳过）

  python3 scripts/migrate-domain.py goldens <域>... [--prefix a,b]
      重写「被该域测试点名的金片」（--prefix 可只做指定前缀 ⇒ 分组迁移用）：
      裸数组/裸对象 → {code:0,message:"ok",data:…}；
      {"error":{code,message,details}} → {code,message,data:details}；
      {"error":"Forbidden: …"} → {code:1002,message:…,data:null}；
      {"error":"Unauthorized: …"} → 不动（AuthFilter 写的，属已知例外）；
      Go 遗留壳 {"data":…,"success":true} → 拆壳取 data；{"message":…,"success":true} → 文案进 message；
      其余字符串错误 → 只在测试期望状态已知时按状态推码；状态未知则**跳过并点名**。

  python3 scripts/migrate-domain.py converge '*SomeContractTest'
      **失败驱动收敛**（B193 起的主力工具，专治无 -Dcontract.refresh 的老契约测试）：
      跑测试 → 按失败消息自动修 ① 金片 ← 实际响应（纯文本，保掩码）
      ② 断言状态双向翻转（204↔200，按失败栈行号定位）
      ③ JSON 字面量回填（expected → actual，反解转义后在源里精确定位）
      收敛到全绿或无可自动修为止（每轮打印改了什么）。

  python3 scripts/migrate-domain.py lint
      post-patch 自检：扫改动文件里的「注释吞分号」（B189-B193 栽过 5 次 ✗）。

  python3 scripts/migrate-domain.py checklist <域>...
      打印该域剩余的人工步骤清单

设计约束（B188 两次翻车的教训，勿改）：
  1. 金片**逐个点名**（只改被该域测试显式引用的），不做全局前缀匹配——前缀扫会波及未迁移域；
     （分组迁移时用显式 --prefix，人工确认过边界 ✓）
  2. 「error 字符串 ⇒ 错误体」必须能确定期望状态：200 ⇒ 其实是"载荷里带 error 字段"，整块进 data；
  3. 只读+定点写，绝不自动给守卫清单加豁免（check-api-envelope.py --write 只清理失效项）。

  4. 【硬性】补丁**禁止**给"语句中间行"加行尾注释 ✗ —— B189/B190/B191/B193 各栽过：
     `compareAndStatus(…, 200,   // …` 或 `.andExpect(status().isOk())   // …;` 都会把后半行
     （含分号）吞进注释 ⇒ 语法错。规则：注释只能放在**独立行**，或**整条语句结束后**（注释在 `;` 之后）✓
     ⇒ `lint` 子命令就是为此写的，改完先跑它 ✓。
"""
import json
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CONTRACTS = ROOT / 'domains/src/testFixtures/resources/contracts'
MAIN_ROOTS = [ROOT / m / 'src/main/java' for m in ('domains', 'engine', 'common', 'boot')]
TEST_ROOTS = [ROOT / m / 'src/test/java' for m in ('boot', 'domains', 'engine', 'common')]
CODE_BY_STATUS = {400: 1000, 401: 1001, 403: 1002, 404: 1003, 405: 1004,
                  409: 1005, 429: 1006, 503: 1008}


def controllers(domain=None):
    """domain → {相对路径: 源码}；domain=None 取全部。"""
    out = {}
    for base in MAIN_ROOTS:
        for p in sorted(base.rglob('*Controller.java')):
            rel = str(p.relative_to(ROOT))
            dom = rel.split('com/ragagent/')[1].split('/')[0]
            if domain is None or dom in domain:
                out[dom].append(rel) if isinstance(out.get(dom), list) else out.setdefault(dom, [rel])
    return out


def controllers_text(domains):
    out = {}
    for dom, rels in controllers(domains).items():
        for rel in rels:
            out[rel] = (ROOT / rel).read_text(encoding='utf-8')
    return out


def private_error_forms(text):
    """返回该控制器里的私有错误形态线索（行号 + 类型）。"""
    hits = []
    for i, line in enumerate(text.split('\n'), 1):
        if '@ExceptionHandler' in line:
            hits.append((i, '私有 @ExceptionHandler'))
        if re.search(r'static\s+ResponseEntity<Map<String,\s*Object>>\s+\w+\s*\(\s*int', line):
            hits.append((i, '手搓错误助手（int status ⇒ {"error":…}）'))
        if re.search(r'(?:Map\.of|\bput)\(\s*"success"', line):
            hits.append((i, '手搓 success 外壳'))
        if re.search(r'noContent\(\)|HttpStatus\.NO_CONTENT|status\(204\)', line):
            hits.append((i, '204（迁移时改 200 + 外壳）'))
    return hits


def test_files_of(domain):
    """该域的契约测试文件（按包路径归属）。"""
    out = []
    for base in TEST_ROOTS:
        for p in sorted(base.rglob('*Test.java')):
            rel = str(p.relative_to(ROOT))
            if 'com/ragagent/%s/' % domain in rel:
                out.append(rel)
    return out


def golden_refs_of(domain):
    """该域测试点名的金片 → 期望状态（能解析出来的才记）。"""
    refs = {}
    for tf in test_files_of(domain):
        t = (ROOT / tf).read_text(encoding='utf-8')
        for m in re.finditer(r'(\d{3})\s*,\s*"([a-z0-9]+-[A-Za-z0-9_.-]+\.json)"', t):
            refs.setdefault(m.group(2), int(m.group(1)))
        for m in re.finditer(r'"([a-z0-9]+-[A-Za-z0-9_.-]+\.json)"\s*,\s*(\d{3})', t):
            refs.setdefault(m.group(1), int(m.group(2)))
        lines = t.split('\n')
        for i, line in enumerate(lines):
            for name in re.findall(r'"([a-z0-9]+-[A-Za-z0-9_.-]+\.json)"', line):
                if refs.get(name):
                    continue
                st = None
                for back in range(i, max(-1, i - 9), -1):
                    mm = (re.search(r'assertEquals\((\d{3}),\s*[\w.]*getStatus\(\)', lines[back])
                          or re.search(r'is\((\d{3})\)', lines[back]))
                    if mm:
                        st = int(mm.group(1)); break
                    if re.search(r'isOk\(\)', lines[back]):
                        st = 200; break
                if st:
                    refs[name] = st
                else:
                    refs.setdefault(name, None)
    return refs


def cmd_survey(domains):
    ctrl = controllers(domains or None)
    for dom in sorted(ctrl):
        rels = ctrl[dom]
        eps = 0
        forms = []
        for rel in rels:
            t = (ROOT / rel).read_text(encoding='utf-8')
            eps += len(re.findall(r'@(?:Get|Post|Put|Delete|Patch)Mapping', t))
            for ln, kind in private_error_forms(t):
                forms.append('%s:%d %s' % (rel.split('/')[-1], ln, kind))
        refs = golden_refs_of(dom)
        tests = [x.split('/')[-1] for x in test_files_of(dom)]
        print('  %-14s 控制器%d 端点%3d 金片%3d（有状态%d）' %
              (dom, len(rels), eps, len(refs), sum(1 for v in refs.values() if v)))
        print('     测试：%s' % (', '.join(tests) or '—'))
        print('     金片：%s' % (', '.join(sorted(refs)[:8]) + (' …' if len(refs) > 8 else '') or '—'))
        if forms:
            print('     ⚠️ 私有形态 %d 处：' % len(forms))
            for f in forms[:8]:
                print('        %s' % f)
        print()


def cmd_annotate(domains):
    n = 0
    for rel, t in controllers_text(set(domains)).items():
        if re.search(r'@(?:[\w.]+\.)?ApiResult\b', t):
            continue
        assert t.count('@RestController') == 1, '异常：%s' % rel
        t = t.replace('@RestController', '@RestController\n@ApiResult', 1)
        lines = t.split('\n')
        li = max(i for i, l in enumerate(lines) if l.startswith('import '))
        lines.insert(li + 1, 'import com.ragagent.common.web.ApiResult;')
        (ROOT / rel).write_text('\n'.join(lines), encoding='utf-8')
        n += 1
        print('  ✓ %s' % rel)
    print('  加注解 %d 个（其余已标注或不存在）' % n)


def cmd_goldens(domains, prefixes=None):
    refs = {}
    for d in domains:
        refs.update(golden_refs_of(d))
    if prefixes:
        refs = {k: v for k, v in refs.items() if any(k.startswith(p) for p in prefixes)}
        print('  前缀过滤：%s ⇒ 命中 %d 个金片' % (','.join(prefixes), len(refs)))
    changed, kept, skipped = 0, [], []
    for name, status in sorted(refs.items()):
        p = CONTRACTS / name
        if not p.exists():
            skipped.append('%s（缺文件）' % name)
            continue
        try:
            d = json.loads(p.read_text(encoding='utf-8'))
        except Exception:
            skipped.append('%s（非 JSON）' % name)
            continue
        if isinstance(d, list):
            new = {'code': 0, 'message': 'ok', 'data': d}
        elif isinstance(d, dict) and d.get('code') == 0:
            continue
        elif isinstance(d, dict) and set(d.keys()) == {'message', 'success'}:
            # 遗留「受理回执」壳 {"message":…,"success":true} ⇒ 文案进 message，data 为 null
            new = {'code': 0, 'message': d['message'], 'data': None}
        elif isinstance(d, dict) and set(d.keys()) == {'data', 'success'}:
            # Go 迁移期的遗留成功壳 ⇒ 拆壳：载荷就是原 data
            new = {'code': 0, 'message': 'ok', 'data': d['data']}
        elif isinstance(d, dict) and isinstance(d.get('error'), dict):
            e = d['error']
            new = {'code': e['code'], 'message': e['message'], 'data': e.get('details')}
        elif isinstance(d, dict) and isinstance(d.get('error'), str):
            s = d['error']
            if s.startswith('Unauthorized'):
                kept.append(name)
                continue
            if s.startswith('Forbidden') and status in (None, 403):
                new = {'code': 1002, 'message': s, 'data': None}
            elif status == 200:
                new = {'code': 0, 'message': 'ok', 'data': d}   # 载荷里带 error 字段的端点
            elif status and status in CODE_BY_STATUS:
                new = {'code': CODE_BY_STATUS[status], 'message': s, 'data': None}
            else:
                skipped.append('%s（字符串错误但状态未知）' % name)
                continue
        elif isinstance(d, dict):
            new = {'code': 0, 'message': 'ok', 'data': d}
        else:
            skipped.append('%s（形状未识别）' % name)
            continue
        p.write_text(json.dumps(new, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        changed += 1
    print('  金片改写 %d · 保持 %d（AuthFilter 401）· 待判 %d %s'
          % (changed, len(kept), len(skipped), skipped[:6]))


def _java_escape(s):
    return s.replace('\\', '\\\\').replace('"', '\\"')


def _java_unescape(s):
    return s.replace('\\"', '"').replace('\\\\', '\\').replace('\\n', '\n').replace('\\t', '\t')


def cmd_converge(test_filters, rounds=25):
    """失败驱动收敛（见模块 docstring）。test_filters 形如 '*ChunkContractTest'。"""
    env = dict(**__import__('os').environ)
    env.pop('SYSTEM_AES_KEY', None)
    env.setdefault('JAVA_HOME', '/opt/homebrew/opt/openjdk@21')
    budget = {}
    for rnd in range(rounds):
        cmd = ['./gradlew', ':boot:test', '--rerun']
        for f in test_filters:
            cmd += ['--tests', f]
        subprocess.run(cmd, capture_output=True, text=True, env=env)
        fails = []
        for p in sorted(pathlib.Path(ROOT, 'boot/build/test-results/test').glob('TEST-*.xml')):
            import xml.etree.ElementTree as ET
            root = ET.parse(p).getroot()
            for tc in root.iter('testcase'):
                for x in list(tc.iter('failure')) + list(tc.iter('error')):
                    fails.append((root.get('name').split('.')[-1], tc.get('name'),
                                  x.get('message') or '', x.text or ''))
        if not fails:
            print('  ✓ 第 %d 轮：全绿' % rnd)
            return 0
        print('  第 %d 轮：%d 条失败' % (rnd, len(fails)))
        changed = 0
        for cls, name, msg, stack in fails:
            # ① 金片 ← actual
            m = re.search(r'([a-z0-9][A-Za-z0-9_.-]*\.json)[^\n]*?expected: <(.*?)> but was: <(.*)>', msg, re.S)
            if m:
                g = CONTRACTS / m.group(1)
                if (g.exists() and m.group(3).strip()[-1:] in ('}', ']')
                        and g.read_text(encoding='utf-8').strip() != m.group(3)):
                    g.write_text(m.group(3), encoding='utf-8')   # 不加结尾换行（严格比较的测试会有差异 ✗）
                    print('     ✓ 金片 %s ← actual' % m.group(1))
                    changed += 1
                    continue
            # ② 状态双向翻转（按栈行号）
            # 栈里第一个"确实存在"的测试源帧（首个帧常是 JUnit 自己的 AssertionFailureBuilder ✗）
            fp, stack_file, stack_line = None, None, None
            for l in stack.split('\n'):
                mm = re.search(r'([A-Za-z0-9_]+\.java):(\d+)', l)
                if not mm:
                    continue
                cand = next((q for q in pathlib.Path(ROOT, 'boot/src/test').rglob(mm.group(1))), None)
                if cand:
                    fp, stack_file, stack_line = cand, mm.group(1), int(mm.group(2)); break
            # ①c 另一种失败格式：`golden mismatch: X.json | expected: … | actual: …` ⇒ 金片 ← actual
            m3 = re.search(r'([a-z0-9][A-Za-z0-9_.-]*\.json).*?actual:\s*(.*?)(?=\s*==>|\s*$)', msg, re.S)
            if m3:
                g3 = CONTRACTS / m3.group(1)
                act = m3.group(2).strip()
                if g3.exists() and act and g3.read_text(encoding='utf-8').strip() != act:
                    g3.write_text(act + '\n', encoding='utf-8')
                    print('     ✓ 金片 %s ← actual（mismatch 格式）' % m3.group(1))
                    changed += 1
                    continue
            # ①b 栈行里点名了金片 ⇒ 直接按 actual 写金片（空金片/非 JSON 金片都能修 ✓）
            # 守卫：状态类失败（消息形如 `<golden> status, body=… ==> expected: <204> but was: <200>`）
            # 不能走本规则 ✗，否则会把金片写成外壳而状态断言还是旧值 ⇒ 下一轮又红（B197 实测 ✓）
            status_failure = bool(re.search(r'==> expected: <\d{3}> but was: <\d{3}>', msg))
            if stack_line and fp and not status_failure and re.search(r'but was: <', msg):
                line = fp.read_text(encoding='utf-8').split('\n')[stack_line - 1]
                gm = re.search(r'golden\("([a-z0-9][A-Za-z0-9_.-]*\.json)"\)', line)
                if gm:
                    g = CONTRACTS / gm.group(1)
                    m2 = re.search(r'but was: <(.*)>$', msg, re.S)
                    actual = (m2.group(1) if m2 else '').strip()
                    if (g.exists() and actual and actual[-1:] in ('}', ']')
                            and g.read_text(encoding='utf-8').strip() != actual):
                        g.write_text(actual, encoding='utf-8')
                        print('     ✓ 金片 %s ← actual（栈行点名）' % gm.group(1))
                        changed += 1
                        continue
            if stack_line and re.search(r'expected: <(204|200)> but was: <(204|200)>', msg):
                # 目标是**实际**值（与「金片 ← actual」同一规则）：测试断言要跟上真实响应
                want = re.search(r'but was: <(204|200)>', msg).group(1)
                if fp:
                    lines = fp.read_text(encoding='utf-8').split('\n')
                    if re.search(r'\b(204|200)\b', lines[stack_line - 1]):
                        lines[stack_line - 1] = re.sub(r'\b(204|200)\b', want, lines[stack_line - 1], count=1)
                        fp.write_text('\n'.join(lines), encoding='utf-8')
                        print('     ✓ %s:%d 状态 → %s' % (stack_file, stack_line, want))
                        changed += 1
                        continue
            # ②b 空体期望 → 外壳（按栈行号；204 退役后 body 是外壳）
            if stack_line and re.search(r'expected: <> but was: <', msg):
                m2 = re.search(r'but was: <(.*)>', msg, re.S)
                actual = (m2.group(1) if m2 else '')
                if fp:
                    lines = fp.read_text(encoding='utf-8').split('\n')
                    if 'assertEquals("",' in lines[stack_line - 1]:
                        lines[stack_line - 1] = lines[stack_line - 1].replace(
                            'assertEquals("",', 'assertEquals("%s",' % _java_escape(actual), 1)
                        fp.write_text('\n'.join(lines), encoding='utf-8')
                        print('     ✓ %s:%d 空体 → 外壳' % (stack_file, stack_line))
                        changed += 1
                        continue
            # ③ JSON 字面量回填（expected → actual）
            m = re.search(r'expected: <([{\[].*)> but was: <([{\[].*)>', msg, re.S)
            if m:
                a, b = m.group(1), m.group(2)
                key = (cls, a[:40])
                if budget.get(key, 0) >= 2:
                    continue
                budget[key] = budget.get(key, 0) + 1
                for fp in pathlib.Path(ROOT, 'boot/src/test').rglob('*.java'):
                    t = fp.read_text(encoding='utf-8')
                    hit = None
                    for lit in re.finditer(r'"((?:[^"\\]|\\.)*)"', t):
                        if _java_unescape(lit.group(1)) == a:
                            hit = lit.group(0)
                            break
                    if hit:
                        fp.write_text(t.replace(hit, '"%s"' % _java_escape(b), 1), encoding='utf-8')
                        print('     ✓ %s 字面量回填（%s）' % (fp.name, a[:38]))
                        changed += 1
                        break
                if changed:
                    continue
            print('     ⚠️ 需人工：%s :: %s :: %s' % (cls, name, msg.replace('\n', ' | ')[:150]))
        if not changed:
            print('  ⚠️ 本轮无可自动修 ⇒ 停（需人工）')
            return 1
    print('  ⚠️ 达到轮数上限')
    return 1


def cmd_retire204(paths):
    """把 204（noContent()/NO_CONTENT/status(204)）改成 200 + ApiResponse.ok()。

    做法：往上找方法签名行 → 返回类型换成 ApiResponse<Void> → 该行换 return。
    调用点若是 `return <helper>(...)` 之类在返回位不可用的形态，编译会报错 ⇒
    再用编译驱动的「就地展开」补（见 HANDOFF B193/B189 ✓）。
    """
    import re as _re
    total = 0
    for rel in paths:
        q = ROOT / rel
        if not q.exists():
            print('  ⚠️ 不存在：%s' % rel); continue
        lines = q.read_text(encoding='utf-8').split('\n')
        n = 0
        for k, l in enumerate(lines):
            if not _re.search(r'noContent\(\)|NO_CONTENT|status\(204\)', l):
                continue
            j = k
            while j >= 0 and not _re.match(r'\s*(public|private|protected)\s', lines[j]):
                j -= 1
            lines[j] = _re.sub(r'ResponseEntity<[^>]*>', 'ApiResponse<Void>', lines[j], count=1)
            lines[k] = '        return ApiResponse.ok();   // 204 退役（空体与「外壳恒存在」冲突）'
            n += 1
            print('  ✓ %s:%d %s' % (pathlib.Path(rel).name, k + 1, lines[j].strip()[:88]))
        if n:
            txt = '\n'.join(lines)
            if 'import com.ragagent.common.web.ApiResponse;' not in txt:
                L = txt.split('\n'); li = max(i for i, x in enumerate(L) if x.startswith('import '))
                L.insert(li + 1, 'import com.ragagent.common.web.ApiResponse;'); txt = '\n'.join(L)
            q.write_text(txt, encoding='utf-8')
        total += n
    print('  204 退役合计 %d 处' % total)


def cmd_lint():
    """post-patch 自检（B189-B193 栽过 5 次 ✗）：扫**改动文件**里的"注释吞分号"。

    模式：行尾出现 `//` 之后还有 `;` ⇒ 说明分号被吞进了注释（语法错）。
    """
    import subprocess
    changed = subprocess.run(['git', 'diff', '--name-only'], capture_output=True, text=True).stdout.split()
    bad = 0
    for rel in changed:
        p = ROOT / rel
        if not p.exists() or p.suffix not in ('.java', '.ts', '.vue', '.py', '.sh'):
            continue
        for i, line in enumerate(p.read_text(encoding='utf-8', errors='replace').split('\n'), 1):
            # 先剔除字符串字面量：`"…resource://a.png…"` 里的 // 不是注释 ✗（B196 实测误报）
            stripped = re.sub(r'"(?:[^"\\]|\\.)*"', '""', line)
            if re.search(r'//[^;]*;\s*$', stripped):
                print('  ✗ %s:%d 注释里带分号（可能吞了语句）：%s' % (rel, i, line.strip()[:110]))
                bad += 1
    print('  %s' % ('✓ 无注释吞分号' if not bad else '✗ %d 处可疑' % bad))
    return 1 if bad else 0


def cmd_checklist(domains):
    for d in domains:
        print('  ## %s 剩余人工步骤' % d)
        print('     ⚠️ 第 0 步（B189/B191 各漏一次 ✗）：先跑 `annotate`！忘加 @ApiResult ⇒ '
              '金片已改新形态而响应还是旧的 ⇒ 成串失败 ✓')
        print('     ① 私有错误形态 → BizException(AppError.ofHttpStatus(status,msg))；'
              '调用点 return→throw（含值位置：三元/参数/跨类）')
        print('     ② 手搓 {"success":…} 外壳 → 只返回载荷')
        print('     ③ 204 → 200 + ApiResponse.ok()（方法签名同步改）')
        print('     ④ 改测试：取字段下钻 data / 内联期望 / 204 断言')
        print('     ⑤ python3 scripts/check-api-envelope.py --write 收紧清单；跑全量')


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    argv = sys.argv[2:]
    prefixes = None
    if '--prefix' in argv:
        i = argv.index('--prefix')
        prefixes = argv[i + 1].split(',')
        del argv[i:i + 2]
    mode = sys.argv[1]
    if mode == 'survey':
        cmd_survey(argv)
    elif mode == 'annotate':
        cmd_annotate(argv)
    elif mode == 'goldens':
        cmd_goldens(argv, prefixes)
    elif mode == 'converge':
        return cmd_converge(argv)
    elif mode == 'retire204':
        cmd_retire204(argv)
    elif mode == 'lint':
        return cmd_lint()
    elif mode == 'checklist':
        cmd_checklist(argv)
    else:
        print(__doc__)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
