#!/usr/bin/env python3
"""R10（A③）：业务域之间只经契约——方案 §5.1「domains/ 业务域；跨域只经 contracts」。

口径
====
业务域 = `domains/src/main/java/com/ragagent/<域>/**`，域名取自方案 §8.9 的域清单（本文件 DOMAINS）。
跨域调用只许经 **`contract.*`（端口 / 载荷）** 或**装配层**（`config` / `platform` / `common`）——
即：业务域 A 不得 `import com.ragagent.<业务域B>.*`（A≠B）。

为什么是棘轮而不是零容忍
========================
立规实测（2026-10-14）：**64 条域→域边 / 约 500 处引用**（`session→knowledge` 53、`agenttools→agent` 114 …），
一次清零＝K 级重构 ✗。按仓内惯例（A16/A17/A11b 同族）：**基线化、只许减不许增** ——
新增的"边"或"边内新增的引用类"立即判红，减小后用 `--write` 收紧。

`--write` 的安全阀：**存在新增项时拒绝写入**（否则"顺手 --write"会把新增一起吞掉，
与 `check-package-cycles.py` 的教训同族）。

为什么是脚本而不是 ArchUnit：本规则是**包（域）级**判据 + 数百条基线，
与 `check-package-cycles.py` / `check-json-key-case.py` 同类；类级规则仍在 ArchUnit（A1~A20）。

用法
====
  python3 scripts/check-domain-contracts.py            # --strict（默认）：有新增即退出码 1
  python3 scripts/check-domain-contracts.py --list     # 打印全部边与计数（收敛时看这个）
  python3 scripts/check-domain-contracts.py --write    # 收紧基线（有新增则拒绝）
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
DOMAINS = [
    'agent', 'agentmanagement', 'agenttools', 'approval', 'audit', 'auth', 'datasource',
    'embedchannel', 'evaluation', 'favorite', 'im', 'initialization', 'knowledge', 'mcp',
    'memory', 'model', 'session', 'settings', 'storage', 'system', 'tenant', 'vectorstore',
    'websearch', 'wiki',
]


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
    if not os.path.exists(BASELINE):
        return {}
    return json.load(open(BASELINE, encoding='utf-8')).get('edges', {})


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--list', action='store_true', help='列出全部边与计数')
    ap.add_argument('--write', action='store_true', help='收紧基线（有新增则拒绝）')
    args = ap.parse_args()

    actual = scan()
    base = load_baseline()

    if args.list:
        print(f'域→域边 {len(actual)} 条（基线 {len(base)} 条）：')
        for k in sorted(actual, key=lambda k: -len(actual[k])):
            mark = ' ' if k in base else '＋'
            print(f'  {mark} {k:34s} {len(actual[k]):3d} 类  {actual[k][:3]}')
        print('\n收敛方向：把该域对外所需的**能力**提到 contract 端口，'
              '调用方只 import contract；已解耦的边从基线删掉（--write）。')
        return 0

    new_edges = sorted(k for k in actual if k not in base)
    new_classes = {k: sorted(set(actual[k]) - set(base.get(k, [])))
                   for k in actual if k in base and set(actual[k]) - set(base.get(k, []))}
    gone = {k: sorted(set(base.get(k, [])) - set(actual.get(k, [])))
            for k in base if set(base.get(k, [])) - set(actual.get(k, [])) or k not in actual}

    if args.write:
        # 首建（基线文件不存在）＝登记现状，允许写入；此后只许减。
        bootstrap = not os.path.exists(BASELINE)
        if (new_edges or new_classes) and not bootstrap:
            print('✗ 拒绝 --write：基线之外出现**新增**（先按下面清单收口，再收紧基线）')
            for k in new_edges:
                print(f'    ＋ 新边 {k}（{len(actual[k])} 类）{actual[k][:4]}')
            for k, v in new_classes.items():
                print(f'    ＋ {k} 新增类 {v}')
            return 1
        payload = {
            'note': ('R10「域间只经契约」棘轮基线（方案 §5.1）。键＝"源域→目标域"，值＝引用该域的源类名。'
                     '只许减：新增边/新增类即红。收紧用 --write（存在新增时会被拒绝）。'
                     'target=0：跨域能力提到 contract 端口，调用方只 import contract。'),
            'edges': actual,
        }
        with open(BASELINE, 'w', encoding='utf-8') as fh:
            fh.write(json.dumps(payload, ensure_ascii=False, indent=2) + '\n')
        print(f'✓ 基线已收紧：{len(actual)} 条边 / {sum(len(v) for v in actual.values())} 个类')
        return 0

    if not new_edges and not new_classes:
        print(f'✓ 域间只经契约：{len(actual)} 条边（基线 {len(base)}）/ '
              f'{sum(len(v) for v in actual.values())} 个类，无新增')
        if gone:
            print(f'  可收紧 {len(gone)} 处（减小了）——对账后跑 --write：')
            for k, v in list(sorted(gone.items()))[:6]:
                print(f'    － {k}: {v[:4]}')
        return 0

    print('✗ 域间出现**新增**直接依赖（跨域只经 contract / 装配层）：')
    for k in new_edges:
        print(f'    ＋ 新边 {k}（{len(actual[k])} 类）{actual[k][:5]}')
    for k, v in sorted(new_classes.items()):
        print(f'    ＋ {k} 新增 {v}')
    print('  正解：把该能力提到 contract 端口（或改经装配层注入），不要直接 import 他域实现类。')
    return 1


if __name__ == '__main__':
    sys.exit(main())
