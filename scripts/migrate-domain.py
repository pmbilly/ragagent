#!/usr/bin/env python3
"""按域迁移「统一响应外壳」的机械部分。

配方与边界见 HANDOFF B188 行 + docs/api-response-convention.md。
人工部分（判读项）仍需人做：私有错误形态转 BizException、测试内联期望、下钻与 204 断言。

用法：
  python3 scripts/migrate-domain.py survey [域...]
      列：该域控制器 / 端点 / 私有错误形态（含行号）/ 引用了哪些契约测试 / 测试点名了哪些金片

  python3 scripts/migrate-domain.py annotate <域>...
      给该域控制器加 @ApiResult + import（幂等；已有则跳过）

  python3 scripts/migrate-domain.py goldens <域>...
      重写「被该域测试点名的金片」：裸数组/裸对象 → {code:0,message:"ok",data:…}；
      {"error":{code,message,details}} → {code,message,data:details}；
      {"error":"Forbidden: …"} → {code:1002,message:…,data:null}；
      {"error":"Unauthorized: …"} → 不动（AuthFilter 写的，属已知例外）；
      其余字符串错误 → 只在测试期望状态已知时按状态推码；状态未知则**跳过并点名**（等测试报出 actual 再回填）。

  python3 scripts/migrate-domain.py checklist <域>...
      打印该域剩余的人工步骤清单（照 B188 五类）

设计约束（B188 两次翻车的教训，勿改）：
  1. 金片**逐个点名**（只改被该域测试显式引用的），不做全局前缀匹配——前缀扫会波及未迁移域；
  2. 「error 字符串 ⇒ 错误体」必须能确定期望状态：200 ⇒ 其实是"载荷里带 error 字段"，整块进 data；
  3. 只读+定点写，绝不自动给守卫清单加豁免（check-api-envelope.py --write 只清理失效项）。

  4. 【硬性】补丁**禁止**给"语句中间行"加行尾注释 ✗ —— B189/B190/B191 各栽一次：
     `compareAndStatus(…, 200,   // …` 或 `.andExpect(status().isOk())   // …;` 都会把后半行
     （含分号）吞进注释 ⇒ 语法错。规则：注释只能放在**独立行**，或**整条语句结束后**（注释在 `;` 之后）✓
     ⇒ 将来若用正则批量加注释，先跑一次编译/语法门禁再继续 ✓
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


def cmd_goldens(domains):
    refs = {}
    for d in domains:
        refs.update(golden_refs_of(d))
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
            # Go 迁移期的遗留成功壳 {"data":…,"success":true} ⇒ 拆壳：载荷就是原 data
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
            if re.search(r'//[^\n]*;\s*$', line) and 'http' not in line:
                print('  ✗ %s:%d 注释里带分号（可能吞了语句）：%s' % (rel, i, line.strip()[:110]))
                bad += 1
    print('  %s' % ('✓ 无注释吞分号' if not bad else '✗ %d 处可疑' % bad))
    return 1 if bad else 0


def cmd_checklist(domains):
    for d in domains:
        print('  ## %s 剩余人工步骤' % d)
        print('     ⚠️ 第 0 步（B189/B191 各漏一次 ✗）：先跑 `annotate`！忘加 @ApiResult ⇒ 金片已改新形态而响应还是旧的 ⇒ 成串失败 ✓')
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
    mode, domains = sys.argv[1], sys.argv[2:]
    if mode == 'survey':
        cmd_survey(domains)
    elif mode == 'annotate':
        cmd_annotate(domains)
    elif mode == 'goldens':
        cmd_goldens(domains)
    elif mode == 'lint':
        return cmd_lint()
    elif mode == 'checklist':
        cmd_checklist(domains)
    else:
        print(__doc__)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
