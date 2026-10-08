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
  R8 禁内联全限定名（依赖图完整性）：R1/R1b/R3 只解析 import 行，代码里写成
     `com.ragagent.x.y.Z` 的引用是图盲区——字节码依赖早已存在，图里却没有边。
     唯一允许形态是「必要消歧」（简单名已被已导入类型 / 本文件声明 / 同包顶层类型占用）。

2026-10-08（B100）R8 上线时的基线重刷说明 —— **下面这些不是新增违例，而是"原先看不见"**：
  清掉 1331 处内联 FQ 后，依赖图第一次完整暴露真实形态：
    · 两两环 0 → 2 组：agent⇄auth、agent⇄im
    · 间接环 0 → 1 组（8 域）：{agent,auth,chatpipeline,datasource,im,memory,session,webfetch}
    · L2→L3 1 → 2 条：chatpipeline→knowledge 处数 12→15；webfetch→datasource 2 处（此前 0 边）
  即：此前"环 0 / L2→L3 1 条"是**代理指标**的读数。基线已改用真实读数，之后只许减。
  解环与 webfetch→datasource 端口化另立批次（阶段 4 前置）。
"""
import json
import pathlib
import re
import sys
from collections import defaultdict

# 多模块（B116）：源码根的单一事实来源在 _source_roots.py（新增模块只改那里）。
# 拆出 contracts 后此处若仍写死 server/...，R5/R6/R8 会**静默失覆盖**。
import _source_roots as _sr

PKG_ROOTS = _sr.backend_pkg_roots()
DOMAIN_FILES = defaultdict(list)          # 域 → 各模块下的 .java（合并）
for _r in PKG_ROOTS:
    for _p in sorted(_r.iterdir()):
        if _p.is_dir():
            DOMAIN_FILES[_p.name].extend(_p.rglob("*.java"))
ALL_FILES = sorted(f for _fs in DOMAIN_FILES.values() for f in _fs)


def rel(f):
    """相对其所在模块的 com/ragagent 包根（报错信息用）。"""
    for _r in PKG_ROOTS:
        try:
            return str(f.relative_to(_r))
        except ValueError:
            continue
    return str(f)


BASELINE = pathlib.Path("scripts/package-cycles.baseline.json")
L2 = {"llm", "retrieval", "embedding", "rerank", "chatpipeline", "modelcontext",
      "searchutil", "storageurl", "webfetch"}
L1 = {"common", "event", "stream", "tracing", "config"}
L3 = sorted(set(DOMAIN_FILES) - L2 - L1)
# 已完成端口化、禁止回流的包对（R4）
DECOUPLED = [("wiki", "knowledge")]

IMP = re.compile(r"^import com\.ragagent\.(\w+)\.", re.M)
edge = defaultdict(set)
for _dom, _fs in DOMAIN_FILES.items():
    for f in _fs:
        for m in IMP.finditer(f.read_text(encoding="utf-8")):
            if m.group(1) != _dom:
                edge[_dom].add(m.group(1))

cycles = sorted({tuple(sorted((a, b))) for a, t in edge.items() for b in t if a in edge.get(b, ())})
to_config = sorted(a for a in edge if "config" in edge[a])
l2_to_l3 = sorted((a, b) for a in L2 for b in edge[a] if b in L3)
# R3b：每条 L2→L3 边的 import 处数（棘轮：只许减不许增）
IMP_EDGE = re.compile(r"^import com\.ragagent\.VICTIM\.", re.M)
l2_to_l3_sites = {}
for _a, _b in l2_to_l3:
    _pat = re.compile(r"^import com\.ragagent\." + _b + r"\.", re.M)
    l2_to_l3_sites[_a + "->" + _b] = sum(
        len(_pat.findall(_p.read_text(encoding="utf-8"))) for _p in DOMAIN_FILES.get(_a, ()))
relapsed = sorted((a, b) for a, b in DECOUPLED if b in edge.get(a, ()))

# L1 核心底座：不得依赖业务域（R5）
L1_CORE = {"common", "event", "stream", "tracing"}
l1_to_l3 = sorted((a, b) for a in L1_CORE for b in edge.get(a, ()) if b in L3)

# common 实现痕迹（R6）：按子包登记，只许减不许增
BEAN_RE = re.compile(r"^\s*@(Component|Service|Repository|Configuration)\b", re.M)
PERSIST_RE = re.compile(r"^import com\.ragagent\.[\w.]+\.(mapper|repository)\.", re.M)
# package 声明 ↔ 路径（R7）
miss_decl = []
for _mod in _sr.MODULE_DIRS:
    for _root in ("main/java", "test/java"):
        _base = pathlib.Path(_mod) / "src" / _root
        if not _base.is_dir():
            continue
        for _p in _base.rglob("*.java"):
            _m = re.search(r"^package\s+([\w.]+);", _p.read_text(encoding="utf-8"), re.M)
            _exp = str(_p.parent.relative_to(_base)).replace("/", ".")
            if _m and _m.group(1) != _exp:
                miss_decl.append(f"{_p}（声明 {_m.group(1)}，应为 {_exp}）")
miss_decl = sorted(miss_decl)

# R8：内联全限定名（依赖图完整性）——上面 R1/R1b/R3 都只解析 import 行，写成
#     `com.ragagent.x.y.Z` 的引用是**图盲区**：字节码依赖早就存在，图里却没有边。
#     2026-10-08 清理 1331 处后实测暴露：agent⇄auth、agent⇄im 两组环与一组 8 域
#     间接环此前完全不可见（守卫一直报"环 0"）。
#     允许的唯一形态是「必要消歧」：简单名在本文件作用域内已被占用——已导入其它包 /
#     本文件已声明 / 同包有顶层同名类型。此时内联 FQ 不产生隐形依赖（依赖已被其它
#     途径表达），且换成 import 根本无法编译。
_R8_FQ = re.compile(r"(?<![.\w])com\.ragagent\.[A-Za-z][\w.]*")
_R8_PKG = re.compile(r"^package\s+([\w.]+);", re.M)
_R8_IMP = re.compile(r"^import\s+(?:static\s+)?([\w.]+);", re.M)
_R8_TOP = re.compile(r"^(?:(?:public|final|abstract|sealed|non-sealed|strictfp)\s+)*"
                     r"(?:class|interface|enum|record|@interface)\s+(\w+)", re.M)
_R8_DECL = re.compile(r"^\s*(?:(?:public|protected|private|static|final|abstract|sealed|"
                      r"non-sealed|strictfp)\s+)*(?:class|interface|enum|record|@interface)\s+(\w+)",
                      re.M)


def _r8_spans(line, in_block):
    """切出非注释、非字符串片段（跨行块注释用状态机跟踪）。"""
    spans, i, n, start = [], 0, len(line), 0
    while i < n:
        if in_block:
            j = line.find("*/", i)
            if j < 0:
                return spans, True
            i = j + 2
            start, in_block = i, False
            continue
        c = line[i]
        if c in "\"'":
            if start < i:
                spans.append((start, i))
            q, i = c, i + 1
            while i < n:
                if line[i] == "\\":
                    i += 2
                    continue
                if line[i] == q:
                    i += 1
                    break
                i += 1
            start = i
        elif c == "/" and i + 1 < n and line[i + 1] == "/":
            if start < i:
                spans.append((start, i))
            return spans, False
        elif c == "/" and i + 1 < n and line[i + 1] == "*":
            if start < i:
                spans.append((start, i))
            in_block, i = True, i + 2
            start = i
        else:
            i += 1
    if start < n:
        spans.append((start, n))
    return spans, in_block


_r8_types, _r8_pkg_types = set(), defaultdict(set)
for _p in ALL_FILES:
    _t = _p.read_text(encoding="utf-8")
    _pm = _R8_PKG.search(_t)
    if not _pm:
        continue
    _pk = _pm.group(1)
    _r8_types |= {f"{_pk}.{x}" for x in _R8_TOP.findall(_t)}
    _r8_types.add(f"{_pk}.{_p.stem}")
    _r8_pkg_types[_pk] |= set(_R8_TOP.findall(_t))

r8_allowed, r8_violations = 0, []
for _p in sorted(ALL_FILES):
    _t = _p.read_text(encoding="utf-8")
    _pm = _R8_PKG.search(_t)
    if not _pm:
        continue
    _pk = _pm.group(1)
    _imp = {x.rsplit(".", 1)[-1]: x for x in _R8_IMP.findall(_t)}
    _decl = set(_R8_DECL.findall(_t)) | {_p.stem}
    _in_blk = False
    for _no, _line in enumerate(_t.splitlines(), 1):
        if _in_blk:
            _sp, _in_blk = _r8_spans(_line, _in_blk)
            continue
        _st = _line.strip()
        if _st.startswith(("package ", "import ")):
            continue
        _sp, _in_blk = _r8_spans(_line, False)
        for _a, _b in _sp:
            _seg = _line[_a:_b]
            if "com.ragagent." not in _seg:
                continue
            for _m in _R8_FQ.finditer(_seg):
                _parts = _m.group(0).split(".")
                _tf = next((".".join(_parts[:k]) for k in range(4, len(_parts) + 1)
                            if ".".join(_parts[:k]) in _r8_types), None)
                if _tf is None:
                    continue
                _simple, _tp = _tf.rsplit(".", 1)[-1], _tf.rsplit(".", 1)[0]
                if ((_simple in _imp and _imp[_simple] != _tf)
                        or (_simple in _decl and _tp != _pk)
                        or (_tp != _pk and _simple in _r8_pkg_types.get(_pk, ()))):
                    r8_allowed += 1
                else:
                    r8_violations.append(f"{rel(_p)}:{_no} → {_m.group(0)}")
r8_violations = sorted(r8_violations)

common_beans, common_persistence = {}, {}
_COMMON_DIRS = [r / "common" for r in PKG_ROOTS if (r / "common").is_dir()]
common_persistence["(mapper/repository 子包)"] = sum(
    1 for _cd in _COMMON_DIRS for _d in _cd.rglob("*")
    if _d.is_dir() and _d.name in ("mapper", "repository") and any(_d.glob("*.java")))
for _cd in _COMMON_DIRS:
    for _p in sorted(_cd.rglob("*.java")):
        _pkg = str(_p.parent.relative_to(_cd)) if _p.parent != _cd else "."
        _txt = _p.read_text(encoding="utf-8")
        if BEAN_RE.search(_txt):
            common_beans[_pkg] = common_beans.get(_pkg, 0) + 1
    if PERSIST_RE.search(_txt):
        common_persistence[_pkg] = common_persistence.get(_pkg, 0) + 1

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
    "l2_to_l3_sites": l2_to_l3_sites,
    "common_beans": common_beans,
    "common_persistence": common_persistence,
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
old_sites = old.get("l2_to_l3_sites", {})
grew_sites = sorted(k for k, v in l2_to_l3_sites.items() if v > old_sites.get(k, 0))
fixed_sccs = [c for c in old_sccs if list(c) not in state["sccs"]]
new_beans = sorted(k for k, v in common_beans.items() if v > old.get("common_beans", {}).get(k, 0))
new_persist = sorted(k for k, v in common_persistence.items()
                     if v > old.get("common_persistence", {}).get(k, 0))

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
      + "；处数 " + ", ".join(f"{k}={v}" for k, v in sorted(l2_to_l3_sites.items()))
      + ("" if not new_l23 else "；新增边：" + ", ".join(f"{a}→{b}" for a, b in new_l23))
      + ("" if not grew_sites else f"；✗ 处数反弹：{grew_sites}"))

print(f"已解耦包对（R4）：{len(DECOUPLED)} 对" + ("" if not relapsed
      else "；✗ 回流：" + ", ".join(f"{a}→{b}" for a, b in relapsed)))

print(f"package 声明↔路径（R7）：{len(miss_decl)} 处不一致"
      + ("" if not miss_decl else "；✗ " + " | ".join(miss_decl[:3])))
print(f"L1 底座（common/event/stream/tracing）→ 业务域：{len(l1_to_l3)} 条"
      + ("" if not l1_to_l3 else "；✗ " + ", ".join(f"{a}→{b}" for a, b in l1_to_l3)))
print(f"common 实现痕迹（R6）：bean {sum(common_beans.values())} 个 / 域持久层引用 "
      f"{sum(common_persistence.values())} 处（基线 "
      f"{sum(old.get('common_beans', {}).values())}/{sum(old.get('common_persistence', {}).values())}）"
      + ("" if not (new_beans or new_persist) else
         f"；✗ 新增：bean {new_beans} / 持久层 {new_persist}"))
print(f"内联全限定名（R8）：必要消歧 {r8_allowed} 处（允许）；违规 {len(r8_violations)} 处"
      + ("" if not r8_violations else "；✗ " + " | ".join(r8_violations[:3])))

if (new_cycles or new_cfg or new_l23 or new_scc_members or relapsed or l1_to_l3 or new_beans
        or new_persist or miss_decl or grew_sites or r8_violations):
    print("\n✗ 守卫失败：出现新的环（含间接环）、新的分层违例，或已解耦包对回流（见上）。")
    sys.exit(1)
print("\n✓ 守卫通过：环与分层违例均未增加。")
