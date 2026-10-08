#!/usr/bin/env python3
"""包依赖守卫（棘轮）：**环只许减不许增**，分层违例数只许降不许升。

用法：
    python3 scripts/check-package-cycles.py            # 检查（新增环/违例变多 → 退出码 1）
    python3 scripts/check-package-cycles.py --write    # 解掉环后刷新基线（需在 PR 说明里写明减了哪些）

规则：
  R1 环棘轮：顶层包两两双向依赖（A→B 且 B→A）不得出现基线之外的新组合；
  R1b 间接环棘轮（SCC）：三包以上的环（A→B→C→A）两两依赖查不出来——按强连通分量登记，
    成员只许减不许增（2026-10-08 B91 补：实测曾存在 {audit,auth,knowledge,model,retrieval,storage,wiki}
     与 {config,im,session,stream} 两组，后者已随 StreamProperties 搬家消除）；
  R2 组合根单向：任何域不得 import `config`（config 是 Spring 装配层，应只出不进）；
  R3 能力层不依赖业务层：L2 = llm/retrieval/embedding/rerank/chatpipeline/modelcontext/searchutil/
     storageurl/webfetch，不得 import L3 业务域（基线计数只减不增）——这是阶段 4 模块化的前置。
"""
import json
import pathlib
import re
import sys
from collections import defaultdict

ROOT = pathlib.Path("server/src/main/java/com/ragagent")
BASELINE = pathlib.Path("scripts/package-cycles.baseline.json")
L2 = {"llm", "retrieval", "embedding", "rerank", "chatpipeline", "modelcontext",
      "searchutil", "storageurl", "webfetch"}
L1 = {"common", "event", "stream", "tracing", "config"}
L3 = sorted({d.name for d in ROOT.iterdir() if d.is_dir()} - L2 - L1)

IMP = re.compile(r"^import com\.ragagent\.(\w+)\.", re.M)
edge = defaultdict(set)
for p in sorted(ROOT.iterdir()):
    if not p.is_dir():
        continue
    for f in p.rglob("*.java"):
        for m in IMP.finditer(f.read_text(encoding="utf-8")):
            if m.group(1) != p.name:
                edge[p.name].add(m.group(1))

cycles = sorted({tuple(sorted((a, b))) for a, t in edge.items() for b in t if a in edge.get(b, ())})
to_config = sorted(a for a in edge if "config" in edge[a])
l2_to_l3 = sorted((a, b) for a in L2 for b in edge[a] if b in L3)

# 强连通分量（Tarjan）：三包以上的环
import sys as _sys
_sys.setrecursionlimit(10000)
_nodes = sorted(set(edge) | {b for v in edge.values() for b in v})
_index, _low, _on, _stack, _sccs, _counter = {}, {}, {}, [], [], [0]


def _strong(v):
    _index[v] = _low[v] = _counter[0]
    _counter[0] += 1
    _stack.append(v)
    _on[v] = True
    for w in edge.get(v, ()):
        if w not in _index:
            _strong(w)
            _low[v] = min(_low[v], _low[w])
        elif _on.get(w):
            _low[v] = min(_low[v], _index[w])
    if _low[v] == _index[v]:
        comp = []
        while True:
            w = _stack.pop()
            _on[w] = False
            comp.append(w)
            if w == v:
                break
        if len(comp) > 1:
            _sccs.append(sorted(comp))


for _n in _nodes:
    if _n not in _index:
        _strong(_n)
sccs = sorted(_sccs)

state = {
    "cycles": [list(c) for c in cycles],
    "sccs": [list(c) for c in sccs],
    "depend_on_config": to_config,
    "l2_to_l3": [list(x) for x in l2_to_l3],
}

if "--write" in sys.argv:
    BASELINE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"✓ 基线已写入 {BASELINE}：两两环 {len(cycles)} 组 / 间接环 {len(sccs)} 组 / "
          f"依赖 config {len(to_config)} 包 / L2→L3 {len(l2_to_l3)} 条")
    sys.exit(0)

old = json.loads(BASELINE.read_text(encoding="utf-8")) if BASELINE.exists() else {}
new_cycles = [c for c in [tuple(x) for x in state["cycles"]] if list(c) not in old.get("cycles", [])]
fixed = [c for c in old.get("cycles", []) if c not in state["cycles"]]
new_cfg = [x for x in to_config if x not in old.get("depend_on_config", [])]
new_l23 = [list(x) for x in state["l2_to_l3"] if list(x) not in old.get("l2_to_l3", [])]
old_sccs = [tuple(c) for c in old.get("sccs", [])]
full = {m for c in old_sccs for m in c}
new_scc_members = sorted({m for c in sccs for m in c} - full)
fixed_sccs = [c for c in old_sccs if list(c) not in state["sccs"]]

print(f"环：{len(cycles)} 组（基线 {len(old.get('cycles', []))}）")
print(f"  新增环：{len(new_cycles)}" + ("" if not new_cycles else " " + ", ".join(f"{a}⇄{b}" for a, b in new_cycles)))
if fixed:
    print(f"  已消除（请刷新基线）：{len(fixed)} → " + ", ".join(f"{a}⇄{b}" for a, b in fixed))
print(f"依赖 config 的包：{len(to_config)}（基线 {len(old.get('depend_on_config', []))}）"
      + ("" if not new_cfg else f"；新增：{new_cfg}"))
print(f"间接环（SCC）：{len(sccs)} 组（基线 {len(old.get('sccs', []))}）"
      + ("" if not new_scc_members else f"；新增成员：{new_scc_members}"))
if fixed_sccs:
    print(f"  已消除（请刷新基线）：{len(fixed_sccs)} 组 → " + " | ".join(",".join(c) for c in fixed_sccs))
print(f"L2 → L3 直连：{len(l2_to_l3)} 条（基线 {len(old.get('l2_to_l3', []))}）"
      + ("" if not new_l23 else "；新增：" + ", ".join(f"{a}→{b}" for a, b in new_l23)))

if new_cycles or new_cfg or new_l23 or new_scc_members:
    print("\n✗ 守卫失败：出现新的环（含间接环）或新的分层违例（见上）。")
    sys.exit(1)
print("\n✓ 守卫通过：环与分层违例均未增加。")
