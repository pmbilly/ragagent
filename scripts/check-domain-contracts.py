#!/usr/bin/env python3
"""R10（A③）：业务域之间只经契约——方案 §5.1「domains/ 业务域；跨域只经 contracts」。

口径
====
业务域 = `domains/src/main/java/com/ragagent/<域>/**`，域名取自方案 §8.9 的域清单（本文件 DOMAINS）。
跨域调用只许经 **`contract.*`（端口 / 载荷）** 或**装配层**（`config` / `platform` / `common`）——
即：业务域 A 不得 `import com.ragagent.<业务域B>.*`（A≠B）。

三档口径（2026-10-14 定；数据在 scripts/domain-contracts.baseline.json 的 `tiers`）
=================================================================================
目标**不是"0 条边"，而是"0 条未分类边"**：每条边要么清掉，要么有明确的分级理由 ——
一刀切清零会逼出假抽象（给实体字段清单造端口 ✗）。三档：

  ① clearable     目标 0（A③ 主线）：跨域能力 / 配置 / 数据读取 ⇒ 提到 contract 端口，调用方只 import contract
  ② owner         同数据属主（**登记，允许存在**）：管理面与他域实体/持久层是同一份数据
                  ⇒ 条件：读侧走 L1 端口、写/管理面集中在属主域（**本档新增仍判红，只许减**）
  ③ intra-family  运行面族内（**允许直接依赖**）：族定义在基线 `tiers.intra-family.families`；
                  两端同族即豁免（含**新增边**与新增类）——这是 v0.6.0「分层解耦」规则；**跨族**仍按 ①
未在 `tiers` 中登记的边**默认 ①**。

为什么是棘轮而不是零容忍
========================
立规实测（2026-10-14）：**64 条域→域边 / 约 500 处引用**（`session→knowledge` 53、`agenttools→agent` 114 …），
一次清零＝K 级重构 ✗。按仓内惯例（A16/A17/A11b 同族）：**基线化、只许减不许增** ——
新增的"边"或"边内新增的引用类"立即判红，减小后用 `--write` 收紧。

登记不是新增许可：把一条**新增**边标成 owner 不能让它通过（棘轮先于分档）✓；
但「族定义」是**规则**而非登记：两端同族的边天然豁免，新增也放行（分层解耦）。

`--write` 的安全阀：**存在新增项时拒绝写入**（否则"顺手 --write"会把新增一起吞掉，
与 `check-package-cycles.py` 的教训同族）；同时自动清理**陈旧登记**（边已消失的 tiers 条目）。

为什么是脚本而不是 ArchUnit：本规则是**包（域）级**判据 + 数百条基线，
与 `check-package-cycles.py` / `check-json-key-case.py` 同类；类级规则仍在 ArchUnit（A1~A20）。

用法
====
  python3 scripts/check-domain-contracts.py            # --strict（默认）：有新增即退出码 1
  python3 scripts/check-domain-contracts.py --list     # 打印全部边：档位 + 计数（收敛时看这个）
  python3 scripts/check-domain-contracts.py --write    # 收紧基线（有新增则拒绝；自动清理陈旧登记）
"""

import argparse
import collections
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOMAIN_SRC = os.path.join(ROOT, 'domains/src/main/java/com/ragagent')
BASELINE = os.path.join(ROOT, 'scripts/domain-contracts.baseline.json')

# 业务域清单（方案 §8.9）。新增域时同步这里——否则新域的跨域引用不会被本守卫看见。
# R5a（2026-10-16）：`agent` 已切出为模块 `:agent-engine` ⇒ 不再计入域清单
# （跨模块边由模块图治理，见 §8.1；R10 只管 domains 内部各域之间）。
DOMAINS = [
    'agentmanagement', 'agenttools', 'approval', 'audit', 'auth', 'datasource',
    'embedchannel', 'evaluation', 'favorite', 'im', 'initialization', 'knowledge', 'mcp',
    'memory', 'model', 'session', 'storage', 'system', 'tenant', 'vectorstore',
    'websearch', 'wiki',
]

TIER_CLEARABLE = 'clearable'
TIER_OWNER = 'owner'
TIER_FAMILY = 'intra-family'
TIER_MARK = {TIER_CLEARABLE: '①', TIER_OWNER: '②', TIER_FAMILY: '③'}


def scan():
    """返回 {(源域, 目标域): [源类名, ...]}（按类名排序、去重）。"""
    edges = collections.defaultdict(set)
    for a in DOMAINS:
        base = os.path.join(DOMAIN_SRC, a)
        if not os.path.isdir(base):
            print(f'✗ 域目录不存在：{a}（与方案 §8.9 的域清单不一致，请同步 DOMAINS）')
            sys.exit(1)
        for dp, _, fs in os.walk(base):
            if '/build/' in dp:
                continue
            for f in fs:
                if not f.endswith('.java') or f == 'package-info.java':
                    continue
                s = open(os.path.join(dp, f), encoding='utf-8', newline='').read()
                for imp in re.findall(r'^import (?:static )?com\.ragagent\.([\w.]+);', s, re.M):
                    b = imp.split('.')[0]
                    if b in DOMAINS and b != a:
                        edges[(a, b)].add(f[:-5])
    return {f'{a}→{b}': sorted(v) for (a, b), v in edges.items()}


def load_baseline():
    """返回 (edges, tiers)。基线文件不存在或没有 tiers 时，分档退化为"全部 ①"。"""
    if not os.path.exists(BASELINE):
        return {}, {}
    d = json.load(open(BASELINE, encoding='utf-8'))
    tiers = d.get('tiers') or {}
    tiers.setdefault(TIER_CLEARABLE, {})
    tiers.setdefault(TIER_OWNER, {})
    tiers.setdefault(TIER_FAMILY, {})
    return d.get('edges', {}), tiers


def tier_of(tiers, edge):
    """档位：显式 owner / 显式族内 / **族定义推导的族内** / 默认 clearable。"""
    if edge in (tiers.get(TIER_OWNER) or {}):
        return TIER_OWNER
    if edge in ((tiers.get(TIER_FAMILY) or {}).get('edges') or {}):
        return TIER_FAMILY
    a, b = edge.split('→')
    for doms in ((tiers.get(TIER_FAMILY) or {}).get('families') or {}).values():
        if a in doms and b in doms:
            return TIER_FAMILY
    return TIER_CLEARABLE


def family_of(tiers, edge):
    return ((tiers.get(TIER_FAMILY) or {}).get('edges') or {}).get(edge)


def audit_tiers(tiers, actual):
    """自检分档元数据：族内登记必须与族定义一致；返回 (问题, 陈旧条目)。"""
    problems, stale = [], []
    fams = (tiers.get(TIER_FAMILY) or {}).get('families') or {}
    for e, fam in ((tiers.get(TIER_FAMILY) or {}).get('edges') or {}).items():
        a, b = e.split('→')
        doms = fams.get(fam)
        if doms is None:
            problems.append(f'族内登记引用了未定义的族：{e} → {fam}')
        elif a not in doms or b not in doms:
            problems.append(f'族内登记与族定义不一致：{e}（族 {fam} = {doms}）')
    for e in list((tiers.get(TIER_OWNER) or {})) + list(((tiers.get(TIER_FAMILY) or {}).get('edges') or {})):
        if e not in actual:
            stale.append(e)
    return problems, stale


def summarize(tiers, edges):
    """返回 (档 → (边数, 类数)) 与档位文字摘要。"""
    agg = collections.defaultdict(lambda: [0, 0])
    for k, v in edges.items():
        t = tier_of(tiers, k)
        agg[t][0] += 1
        agg[t][1] += len(v)
    fams = (tiers.get(TIER_FAMILY) or {}).get('families') or {}
    lines = []
    for t, label in ((TIER_CLEARABLE, '目标 0（A③ 主线）'), (TIER_OWNER, '登记允许（条件：读侧走 L1 端口）'),
                     (TIER_FAMILY, '族内豁免+冻结')):
        n, c = agg[t]
        extra = ''
        if t == TIER_FAMILY and fams:
            extra = '　族＝' + ', '.join(f'{k}({"/".join(v)})' for k, v in fams.items())
        lines.append(f'  {TIER_MARK[t]} {t:12s} {n:3d} 条 / {c:4d} 类   ← {label}{extra}')
    return agg, lines


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--list', action='store_true', help='列出全部边与计数（含档位）')
    ap.add_argument('--write', action='store_true', help='收紧基线（有新增则拒绝）')
    args = ap.parse_args()

    actual = scan()
    base, tiers = load_baseline()
    problems, stale = audit_tiers(tiers, actual)

    if args.list:
        _, lines = summarize(tiers, actual)
        print(f'域→域边 {len(actual)} 条（基线 {len(base)} 条）· 三档进度：')
        print('\n'.join(lines))
        if problems:
            print('✗ 分档元数据自检失败：' + '；'.join(problems))
        if stale:
            print(f'  陈旧登记 {len(stale)} 条（边已消失，可用 --write 清理）：{sorted(stale)[:6]}')
        print()
        for k in sorted(actual, key=lambda k: (tier_of(tiers, k), -len(actual[k]))):
            mark = ' ' if k in base else '＋'
            t = TIER_MARK[tier_of(tiers, k)]
            fam = family_of(tiers, k)
            print(f'  {mark}{t} {k:30s} {len(actual[k]):3d} 类  {actual[k][:3]}'
                  + (f'  [{fam}]' if fam else ''))
        owner = tiers.get(TIER_OWNER) or {}
        why = (tiers.get(TIER_FAMILY) or {}).get('why') or {}
        if owner or why:
            print('\n②③ 档理由（登记依据；① 档默认"跨域能力/配置/数据读取"）：')
            for k in sorted(owner):
                print(f'  ② {k}：{owner[k]}')
            for k in sorted(why):
                print(f'  ③ {k}：{why[k]}')
        print('\n收敛方向：① 把该域对外所需的**能力/配置/数据读取**提到 contract 端口，'
              '调用方只 import contract；② 属主边只要求"读侧走端口、写侧集中在属主域"；'
              '③ 族内边冻结不涨。减小后用 --write 收紧。')
        return 1 if problems else 0

    new_edges = sorted(k for k in actual
                       if k not in base and tier_of(tiers, k) != TIER_FAMILY)
    new_classes = {k: sorted(set(actual[k]) - set(base.get(k, [])))
                   for k in actual
                   if k in base and set(actual[k]) - set(base.get(k, []))
                   and tier_of(tiers, k) != TIER_FAMILY}
    gone = {k: sorted(set(base.get(k, [])) - set(actual.get(k, [])))
            for k in base if set(base.get(k, [])) - set(actual.get(k, [])) or k not in actual}

    if args.write:
        # 首建（基线文件不存在）＝登记现状，允许写入；此后只许减。
        bootstrap = not os.path.exists(BASELINE)
        if (new_edges or new_classes) and not bootstrap:
            print('✗ 拒绝 --write：基线之外出现**新增**（先按下面清单收口，再收紧基线）')
            for k in new_edges:
                print(f'    ＋ 新边 {k}（{len(actual[k])} 类）{actual[k][:4]}'
                      f'  ⇒ 默认入 {TIER_MARK[TIER_CLEARABLE]} clearable（目标 0）')
            for k, v in new_classes.items():
                print(f'    ＋ {k} 新增类 {v}')
            return 1
        if problems:
            print('✗ 拒绝 --write：分档元数据自检失败：' + '；'.join(problems))
            return 1
        # 清理陈旧登记（只在 --write 下动 tiers）
        for e in stale:
            (tiers.get(TIER_OWNER) or {}).pop(e, None)
            ((tiers.get(TIER_FAMILY) or {}).get('edges') or {}).pop(e, None)
        payload = {'note': base_note(), 'tiers': tiers, 'edges': actual}
        with open(BASELINE, 'w', encoding='utf-8') as fh:
            fh.write(json.dumps(payload, ensure_ascii=False, indent=2) + '\n')
        _, lines = summarize(tiers, actual)
        print(f'✓ 基线已收紧：{len(actual)} 条边 / {sum(len(v) for v in actual.values())} 个类'
              + (f'（清理陈旧登记 {len(stale)} 条）' if stale else ''))
        print('\n'.join(lines))
        return 0

    if problems:
        print('✗ R10 分档元数据自检失败（守卫自身的口径错了，先修基线 tiers）：')
        for p in problems:
            print(f'    {p}')
        return 1

    if not new_edges and not new_classes:
        _, lines = summarize(tiers, actual)
        print(f'✓ 域间只经契约：{len(actual)} 条边（基线 {len(base)}）/ '
              f'{sum(len(v) for v in actual.values())} 个类，无新增')
        print('\n'.join(lines))
        if stale:
            print(f'  可清理 {len(stale)} 条陈旧登记（边已消失）——跑 --write')
        if gone:
            print(f'  可收紧 {len(gone)} 处（减小了）——对账后跑 --write：')
            for k, v in list(sorted(gone.items()))[:6]:
                print(f'    － {k}: {v[:4]}')
        return 0

    print('✗ 域间出现**新增**直接依赖（跨域只经 contract / 装配层）：')
    for k in new_edges:
        print(f'    ＋ 新边 {k}（{len(actual[k])} 类）{actual[k][:5]}'
              f'  ⇒ 默认入 {TIER_MARK[TIER_CLEARABLE]} clearable（目标 0）：把该能力提到 contract 端口')
    for k, v in sorted(new_classes.items()):
        print(f'    ＋ {k}（{TIER_MARK[tier_of(tiers, k)]} {tier_of(tiers, k)}）新增 {v}')
    print('  正解：把该能力提到 contract 端口（或改经装配层注入），不要直接 import 他域实现类。')
    print('  注意：**登记不是新增许可** —— 把新边写进 owner/intra-family 也不能放行（棘轮先于分档）。')
    return 1


def base_note():
    return ('R10「域间只经契约」棘轮基线（方案 §5.1）。edges 键＝"源域→目标域"，值＝引用该域的源类名；'
            'tiers 为三档口径：clearable（目标 0）/ owner（同数据属主，登记允许）/ intra-family'
            '（运行面族内，豁免+冻结），未登记的边默认 clearable。只许减：新增边/新增类即红，'
            '登记不是新增许可。收紧用 --write（存在新增时会被拒绝，并清理陈旧登记）。')


if __name__ == '__main__':
    sys.exit(main())
