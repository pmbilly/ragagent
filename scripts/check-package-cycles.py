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
  R9 包级 SCC 棘轮：R1b 只看**顶层域**，子包级的环（如 knowledge 的
     repository/security/service/task、agent 的 compaction/prompt 回引根包）在它视野之外。
     按完整包（com.ragagent.a.b）聚合 import 边求强连通分量，分组/成员只许减；
     **新增即红、已消除/变形也红**——刷新基线须在 PR 说明里写明变化原因（棘轮基线不许悄悄过期）。

2026-10-08（B100）R8 上线时的基线重刷说明 —— **下面这些不是新增违例，而是"原先看不见"**：
  清掉 1331 处内联 FQ 后，依赖图第一次完整暴露真实形态：
    · 两两环 0 → 2 组：agent⇄auth、agent⇄im
    · 间接环 0 → 1 组（8 域）：{agent,auth,chatpipeline,datasource,im,memory,session,webfetch}
    · L2→L3 1 → 2 条：chatpipeline→knowledge 处数 12→15；webfetch→datasource 2 处（此前 0 边）
  即：此前"环 0 / L2→L3 1 条"是**代理指标**的读数。基线已改用真实读数，之后只许减。
  解环与 webfetch→datasource 端口化另立批次（阶段 4 前置）。

2026-10-13（B232）两项变更（同属"原先看不见"）：
  · R6 持久层引用检查修复：原实现把 PERSIST_RE 检查放在内层循环之外 ⇒ 每个 common
    根目录只检查**最后读到的 1 个文件**；修复后全量重扫实测 0 命中 ⇒ 读数不变
    （纯漏检面修复；红态探针：注入 common/probe 持久层引用 ⇒ 修前计数 0、修后计数 1）。
  · R9 上线：包级 SCC 实测 13 组入基线（R1b 顶层域视角下全部不可见）。

2026-10-16（R2a）包级 SCC 8 → 9 组（**新增 1 组**：{contract.model, contract.model.chat}）：
  · 原因：契约面"模型运行时而"上提（R2a）——`contract.model` 收 `LlmChatClient`（聊天客户端端口）
    与 `StreamResponse`（聊天流帧），前者签名取 `ChatMessage/ChatOptions/ChatResponse`、
    后者含 `List<ToolCall>`；反向 `contract.model.chat.ChatResponse` 持 `contract.model.TokenUsage`。
  · 性质：**父子包内聚**（同 skills/mapper、embedding/provider、rerank/provider 三组既有先例），
    不是跨族缠绕；两包同属契约模型词汇表，互相引用是该词汇表的固有形状。
  · 为什么不在本批消除：方案 §8.1 的目标面（`ModelRuntime`/`ModelCall`/`ModelChunk`/`ModelResponse`
    在 `contract.model`）同样要引用聊天形状 ⇒ 挪包只能把同一判定推到下一个批次，不解决结构问题。
  · 仍受棘轮：本组只许减不许增；若将来把 `TokenUsage`/`ResponseType` 一并归入 chat 面，
    本组应随之消除并刷新（已消除同样报红，不会悄悄过期）。

2026-10-16（R3b-3）包级 SCC 9 组不变但**一组变形**：{contract.model, contract.model.chat}
  → {contract.model, contract.model.chat, contract.retrieval}。
  · 原因：R3a 起检索面契约化——`contract.retrieval.IndexEngine` 取 `contract.model.Embedder`
    （写入需先向量化）；反向 `contract.model.StreamResponse` 早已持 `contract.retrieval.SearchResult`
    （R2a-1 上提时即如此：SSE 帧要回传检索结果）。
  · 性质：**契约词汇表内的固有形状互引**（检索载荷 ⇄ 模型能力载体），与既有的 model⇄model.chat
    同族，不是跨族缠绕；三个包全是纯 JDK 词汇/端口，无框架依赖。
  · 为什么不在本批消除：要拆只能把 `Embedder` 或 `SearchResult` 挪去对方包（都是错误落点），
    或让 `StreamResponse` 放弃检索载荷（API 形状变化）。
  · 仍受棘轮：本组只许减不许增。

2026-10-16（R2c-3d）同一组再变形：{contract.model, contract.model.chat, contract.retrieval}
  → **加 contract.knowledge**。
  · 原因：新增 `contract.knowledge.EntityExtractionPort`（抽取能力端口）取
    `contract.model.LlmChatClient`（模型句柄）与 `contract.graph.EntityExtractionResult`
    （结果形状）；反向 `contract.model → contract.knowledge` 早已存在（模型面持有知识侧
    形状，如知识库/知识行视图）。
  · 性质：同上一处——**契约词汇表内的固有形状互引**（能力端口 ⇄ 载荷形状），
    四个包全是纯 JDK 词汇/端口，无框架依赖。
  · 为什么不在本批消除：要拆只能让"抽取端口"不引模型句柄（它必须以模型为入参），
    或让模型面不持知识形状（API 形状变化）。
  · 仍受棘轮：本组只许减不许增。
"""
import json
import pathlib
import re
import sys
from collections import defaultdict

# 多模块（B116）：源码根的单一事实来源在 _source_roots.py（新增模块只改那里）。
# 拆出 contracts 后此处若仍写死 domains/...，R5/R6/R8 会**静默失覆盖**。
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
L2 = {"llm", "modelruntime", "retrieval", "embedding", "rerank", "chatpipeline", "modelcontext",
      "searchutil", "storageurl", "webfetch"}
L1 = {"common", "event", "stream", "tracing", "config", "contract", "platform"}  # B258 契约面 / B259 平台面 属底座
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
L1_CORE = {"common", "event", "stream", "tracing", "contract", "platform"}  # B258/B259：同上
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
        # 逐文件检查（B232 修复：原先此检查在内层循环之外，每个 common 根目录
        # 只检查最后读到的 1 个文件 ⇒ 持久层引用可静默漏检）。
        if PERSIST_RE.search(_txt):
            common_persistence[_pkg] = common_persistence.get(_pkg, 0) + 1

# 强连通分量（Tarjan）——顶层域（R1b）与完整包（R9）共用一份实现
import sys as _sys
_sys.setrecursionlimit(10000)


def sccs_of(nodes, adjacency):
    """返回 >1 成员的强连通分量（分量内/分量间均按名排序）。"""
    _index, _low, _on, _stack, _out, _counter = {}, {}, {}, [], [], [0]

    def _strong(v):
        _index[v] = _low[v] = _counter[0]
        _counter[0] += 1
        _stack.append(v)
        _on[v] = True
        for w in adjacency.get(v, ()):
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
                _out.append(sorted(comp))

    for _n in nodes:
        if _n not in _index:
            _strong(_n)
    return sorted(_out)


_nodes = sorted(set(edge) | {b for v in edge.values() for b in v})
sccs = sccs_of(_nodes, edge)

# R9 包级 SCC：把 import 边聚合到**完整包**（R1b 只看顶层域，子包级的环它看不见）。
# 两趟：先登记全部包，再收边——单趟会让"目标包尚未读到"的 import 静默漏边。
_PKG_DECL = re.compile(r"^package\s+([\w.]+);", re.M)
_FULL_IMP = re.compile(r"^import\s+(?:static\s+)?com\.ragagent\.[\w.]+;", re.M)
_pkg_files = defaultdict(list)
for _p in ALL_FILES:
    _m = _PKG_DECL.search(_p.read_text(encoding="utf-8"))
    if _m:
        _pkg_files[_m.group(1)].append(_p)
_pkg_edge = defaultdict(set)
for _pkg, _fs in _pkg_files.items():
    for _f in _fs:
        for _im in _FULL_IMP.finditer(_f.read_text(encoding="utf-8")):
            _tgt = _im.group(0)[len("import "):].rstrip(";").strip()
            if _tgt.startswith("static "):
                _tgt = _tgt[len("static "):]
            _parts = _tgt.split(".")
            for _k in range(len(_parts) - 1, 2, -1):
                _cand = ".".join(_parts[:_k])
                if _cand in _pkg_files:
                    if _cand != _pkg:
                        _pkg_edge[_pkg].add(_cand)
                    break
package_sccs = sccs_of(sorted(_pkg_files), _pkg_edge)

state = {
    "cycles": [list(c) for c in cycles],
    "sccs": [list(c) for c in sccs],
    "package_sccs": [list(c) for c in package_sccs],
    "depend_on_config": to_config,
    "l2_to_l3": [list(x) for x in l2_to_l3],
    "l2_to_l3_sites": l2_to_l3_sites,
    "common_beans": common_beans,
    "common_persistence": common_persistence,
}

if "--write" in sys.argv:
    BASELINE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"✓ 基线已写入 {BASELINE}：两两环 {len(cycles)} 组 / 间接环 {len(sccs)} 组 / "
          f"包级 SCC {len(package_sccs)} 组 / 依赖 config {len(to_config)} 包 / "
          f"L2→L3 {len(l2_to_l3)} 条")
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
old_pkg_sccs = [list(c) for c in old.get("package_sccs", [])]
new_pkg_sccs = [c for c in package_sccs if c not in old_pkg_sccs]
gone_pkg_sccs = [c for c in old_pkg_sccs if c not in package_sccs]
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
print(f"包级 SCC（R9）：{len(package_sccs)} 组（基线 {len(old_pkg_sccs)}）"
      + ("" if not new_pkg_sccs else "；✗ 新增/变形：" + " | ".join(",".join(c) for c in new_pkg_sccs))
      + ("" if not gone_pkg_sccs
         else "；已消除/变形（请 --write 刷新基线）：" + " | ".join(",".join(c) for c in gone_pkg_sccs)))
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
        or new_persist or miss_decl or grew_sites or r8_violations or new_pkg_sccs
        or gone_pkg_sccs):
    print("\n✗ 守卫失败：出现新的环（含间接环/包级环）、新的分层违例、已解耦包对回流，"
          "或包级 SCC 基线需要刷新（见上）。")
    sys.exit(1)
print("\n✓ 守卫通过：环与分层违例均未增加，包级 SCC 基线未变。")
