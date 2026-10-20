#!/usr/bin/env python3
"""模块图守卫（R6）：主码依赖只许走方案矩阵 · 主码不得跨模块同包 · 夹具不得占用别模块主码包。

方案 §8.1 的目标模块图（2026-10-16 R5 收官后实测达标）：

    contract       （纯 JDK）
    platform       → contract
    model-runtime  → contract, platform
    retrieval-engine → contract, platform
    agent-engine   → contract, platform, model-runtime   （modelcontext = 方案认可的终态边）
    domains        → contract, platform, model-runtime   （仅 modelcontext；其余已在 R2c-3d 清零）
    boot           → 全部（组合根）

本守卫的判据（A~E）：

  A) **主码依赖边**（src/main 的 import 解析到另一模块的 src/main 类）必须在上表矩阵内。
     新增边即红——除非同时把理由写进 ALLOWED_MAIN 并改这里（棘轮语义：矩阵只许减不许增）。
  B) **主码不得跨模块同包**：同一个包声明出现在两个模块的 src/main 里会成为 split package
     （classpath 上合并、同名类互相遮蔽，历史上靠"实测"才发现）。硬性为 0。
  C) **测试期依赖边**必须 ⊆ 矩阵 ∪ ALLOWED_TEST_EXTRA。
     · `domains → agent-engine`：消费其 **testFixtures**（录制夹具 RecordingSupport 等）
       ——夹具随 agent 树迁出后仍被 domains/boot 的录制测试复用（同向依赖、不成环）。
     · `domains → model-runtime`：测试桩直接用模型侧类型（Embedder/Reranker 等），
       比再造一层契约桩更贴近真实装配。
     · `boot → *`：组合根测试天然要看到全部模块。
  D) **夹具不得与别模块主码同包**（`testFixtures` 会被别的模块当依赖吃进来，与对方主码同包
     等于把测试件混进业务包——R6 前的 `com.ragagent.agenttools` / `com.ragagent.support` 就是这种
     情形，已改名 `com.ragagent.agent.fixtures`）。
     · 唯一豁免：**根包 `com.ragagent`**（仓库惯例：`com.ragagent.TestSchema` 一类通用夹具 +
       组合根的启动类都放根包，类名不冲突、无遮蔽风险）。
     · 另有硬性判据 **D2：同一 FQN 不得出现在两个模块**（真重复类，classpath 上直接冲突）。
     · 测试树（src/test）沿用目标模块包名是**惯例**（为够到包私有成员），不在 D 的范围内。

  E) **测试不得住在别模块的主码包**（`src/test` / `src/testFixtures`）。
     这类文件会让"按模块找测试"的直觉失效，并掩盖模块边。存量以
     `MISPLACED_TEST_ALLOW` 登记理由（棘轮：只许减）；新增即红，登记项消失也红
     （提示同步删除登记）。另硬性禁止已解散模块的夹具包
     `com.ragagent.common` / `com.ragagent.support` 回流。

为什么不写成 ArchUnit：ArchUnit 看的是类/包，看不到 **Gradle 模块边界**；模块边是构建期事实，
用脚本按源集判最直接（与 R9 包级 SCC 守卫同源）。
"""

from __future__ import annotations

import collections
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MODULES = ["contract", "platform", "model-runtime", "retrieval-engine", "agent-engine", "domains", "boot"]

# ── 判据 A 的矩阵（主码依赖）──────────────────────────────────────────────
ALLOWED_MAIN: dict[str, set[str]] = {
    "contract": set(),
    "platform": {"contract"},
    "model-runtime": {"contract", "platform"},
    "retrieval-engine": {"contract", "platform"},
    # modelcontext：agent 工具注册表/流解码属模型侧词汇，方案明示为 agent-engine 的合法终态边
    "agent-engine": {"contract", "platform", "model-runtime"},
    # modelcontext：同上（4 处；R2c-3d 已把其余模型族边清零）
    "domains": {"contract", "platform", "model-runtime"},
    "boot": set(MODULES[:-1]),  # 组合根
}

# ── 判据 C 的额外测试期边（每条都要理由）──────────────────────────────────
ALLOWED_TEST_EXTRA: dict[tuple[str, str], str] = {
    ("domains", "agent-engine"): "消费 agent-engine 的 testFixtures（录制夹具），同向不成环",
    ("domains", "model-runtime"): "测试桩直接用模型侧类型（Embedder/Reranker/ModelRuntime）",
}

IMPORT_RE = re.compile(r"^import\s+(?:static\s+)?(com\.ragagent\.[\w.]+);", re.M)
PKG_RE = re.compile(r"^package\s+([\w.]+);", re.M)

# ── 判据 E 的存量登记（路径 → 理由；只许减）────────────────────────────────
MISPLACED_TEST_ALLOW: dict[str, str] = {
    "platform/src/test/java/com/ragagent/contract/knowledge/rule/ParserEngineRulesTest.java":
        "测契约规则，但需要 platform 的 JsonMappers；platform test → contract 是合法方向",
    "domains/src/test/java/com/ragagent/embedding/EmbeddingWireTest.java":
        "wire A/B 使用 domains 的 Model/ModelRowViews；迁 model-runtime 会形成 test→domains 反向边",
    "domains/src/test/java/com/ragagent/rerank/RerankWireTest.java":
        "同上：wire A/B 使用 domains 的 Model/ModelRowViews",
    "domains/src/test/java/com/ragagent/llm/provider/ProviderRegistryTest.java":
        "provider 目录测试使用 domains 的 Model/ModelRowViews；迁 model-runtime 会形成反向边",
    "domains/src/test/java/com/ragagent/platform/access/TracingCarrierNestingTest.java":
        "用 domains 的任务载荷验证平台 tracing carrier；迁 platform 会形成反向边",
    "domains/src/test/java/com/ragagent/platform/web/JsonFaceVocabularyTest.java":
        "用 domains 的 DTO/配置验证平台 JSON 口径；迁 platform 会形成反向边",
    "domains/src/test/java/com/ragagent/platform/text/JsonContractRoundTripTest.java":
        "全仓 JSON 往返矩阵，天然需要 domains 载荷；迁 platform 会形成反向边",
    "domains/src/test/java/com/ragagent/platform/tracing/langfuse/LangfuseEndToEndTest.java":
        "端到端 trace 依赖 domains 的 MemoryTrace；迁 platform 会形成反向边",
    "domains/src/test/java/com/ragagent/platform/tracing/langfuse/LangfuseTracingTest.java":
        "同上：trace 测试依赖 domains 的 MemoryTrace",
}

GHOST_FIXTURE_PREFIXES = ("com.ragagent.common", "com.ragagent.support")


def sources(module: str, kind: str) -> list[pathlib.Path]:
    return [f for f in pathlib.Path(ROOT / module).glob(f"src/{kind}/java/**/*.java") if "/build/" not in str(f)]


def build_fqn_map() -> tuple[dict[str, set[str]], dict[str, set[str]], dict[str, set[str]]]:
    """返回 (fqn→modules, main_pkg→modules, fixtures_pkg→modules)。"""
    fqn: dict[str, set[str]] = collections.defaultdict(set)
    main_pkg: dict[str, set[str]] = collections.defaultdict(set)
    fix_pkg: dict[str, set[str]] = collections.defaultdict(set)
    for m in MODULES:
        for kind, pkg_index in (("main", main_pkg), ("test", None), ("testFixtures", fix_pkg)):
            for f in sources(m, kind):
                t = f.read_text(encoding="utf-8")
                pm = PKG_RE.search(t)
                if not pm:
                    continue
                pkg = pm.group(1)
                fqn[f"{pkg}.{f.stem}"].add(m)
                if pkg_index is not None:
                    pkg_index[pkg].add(m)
    return fqn, main_pkg, fix_pkg


def resolve(fqn: str, fqn_map: dict[str, set[str]]) -> set[str] | None:
    parts = fqn.split(".")
    while len(parts) > 3:
        mods = fqn_map.get(".".join(parts))
        if mods:
            return mods
        parts = parts[:-1]
    return fqn_map.get(fqn)


def scan_edges(kinds: list[str], fqn_map) -> tuple[dict[tuple[str, str], int], list[str]]:
    edges: dict[tuple[str, str], int] = collections.Counter()
    unresolved: list[str] = []
    for m in MODULES:
        for kind in kinds:
            for f in sources(m, kind):
                t = f.read_text(encoding="utf-8")
                for mm in IMPORT_RE.finditer(t):
                    mods = resolve(mm.group(1), fqn_map)
                    if not mods:
                        unresolved.append(mm.group(1))
                        continue
                    for target in mods:
                        if target != m:
                            edges[(m, target)] += 1
    return edges, unresolved


def main() -> int:
    fqn_map, main_pkg, fix_pkg = build_fqn_map()
    problems: list[str] = []

    # A) 主码边
    main_edges, _ = scan_edges(["main"], fqn_map)
    print("主码模块依赖（实测）：")
    for (src, dst), n in sorted(main_edges.items()):
        mark = "✓" if dst in ALLOWED_MAIN.get(src, set()) else "✗"
        if mark == "✗":
            problems.append(f"A 主码依赖越界：{src} → {dst}（{n} 处）——矩阵里没有这条边")
        print(f"  {mark} {src:16s} → {dst:16s} {n:5d} 处")
    # 顺带报告矩阵中声明但实测为 0 的边（收紧提示，不算失败）
    for src, allowed in ALLOWED_MAIN.items():
        for dst in sorted(allowed):
            if (src, dst) not in main_edges and dst in MODULES:
                print(f"  ·  {src:16s} → {dst:16s}     0 处（矩阵已声明，当前无引用）")

    # B) 主码同包
    splits = {p: sorted(ms) for p, ms in main_pkg.items() if len(ms) > 1}
    if splits:
        for p, ms in sorted(splits.items()):
            problems.append(f"B 主码跨模块同包：{p} —— {ms}")
    print(f"\n主码跨模块同名包：{len(splits)} 个（要求 0）")

    # C) 测试期边
    test_edges, _ = scan_edges(["test", "testFixtures"], fqn_map)
    print("\n测试期模块依赖（实测）：")
    for (src, dst), n in sorted(test_edges.items()):
        allowed = dst in ALLOWED_MAIN.get(src, set()) or (src, dst) in ALLOWED_TEST_EXTRA
        mark = "✓" if allowed else "✗"
        reason = ALLOWED_TEST_EXTRA.get((src, dst), "")
        if not allowed:
            problems.append(f"C 测试期依赖越界：{src} → {dst}（{n} 处）")
        print(f"  {mark} {src:16s} → {dst:16s} {n:5d} 处{f'　（{reason}）' if reason else ''}")

    # D) 夹具占用别模块主码包（根包豁免）
    ROOT_PKG_EXEMPT = {"com.ragagent"}
    collisions = {}
    for p in fix_pkg:
        if p not in main_pkg or p in ROOT_PKG_EXEMPT:
            continue
        fx, mn = sorted(fix_pkg[p]), sorted(main_pkg[p])
        other = [m for m in mn if m not in fx]        # 只算**跨模块**：同模块夹具↔主码同包是正常的
        if other:
            collisions[p] = (fx, other)
            problems.append(f"D 夹具包与别模块主码同包：{p} —— 夹具在 {fx}，主码在 {other}")
    print(f"\n夹具包占用别模块主码包：{len(collisions)} 个（要求 0；根包 {sorted(ROOT_PKG_EXEMPT)} 豁免）")

    # D2) 同一 FQN 出现在两个模块（真重复类）
    dup = {fqn: sorted(mods) for fqn, mods in fqn_map.items() if len(mods) > 1}
    for fqn, mods in sorted(dup.items()):
        problems.append(f"D2 同一类出现在多个模块：{fqn} —— {mods}")
    print(f"跨模块重复 FQN：{len(dup)} 个（要求 0）")

    # E) 测试/夹具不得住在别模块的主码包（存量登记只许减）
    used_allow: set[str] = set()
    misplaced = 0
    ghost = 0
    for m in MODULES:
        # 组合根的有意例外：boot 的 src/test 是 HTTP 集成测试聚合地，按业务包组织是刻意设计。
        if m == "boot":
            continue
        for kind in ("test", "testFixtures"):
            for f in sources(m, kind):
                pm = PKG_RE.search(f.read_text(encoding="utf-8"))
                if not pm:
                    continue
                pkg = pm.group(1)
                rel = str(f.relative_to(ROOT))
                others = {
                    other
                    for main_pkg_name, mods in main_pkg.items()
                    # 根包 com.ragagent 是组合根启动类/通用夹具的惯例位置，不作为归属前缀。
                    if main_pkg_name != "com.ragagent"
                    and (pkg == main_pkg_name or pkg.startswith(main_pkg_name + "."))
                    for other in mods
                    if other != m
                }
                if others:
                    misplaced += 1
                    if rel in MISPLACED_TEST_ALLOW:
                        used_allow.add(rel)
                    else:
                        problems.append(
                            f"E 测试/夹具住在别模块主码包：{rel}（{pkg} → {sorted(others)}）"
                        )
                if kind == "testFixtures" and any(
                        pkg == g or pkg.startswith(g + ".") for g in GHOST_FIXTURE_PREFIXES):
                    ghost += 1
                    problems.append(f"E 已解散模块的夹具包回流：{rel}（{pkg}）")

    for rel, reason in sorted(MISPLACED_TEST_ALLOW.items()):
        if rel not in used_allow:
            problems.append(
                f"E 登记失效或已不跨模块：{rel} —— 请从 MISPLACED_TEST_ALLOW 删除（理由：{reason}）"
            )
    print(f"测试/夹具跨模块错位：{misplaced} 个（登记 {len(MISPLACED_TEST_ALLOW)} 条，只许减）")
    print(f"已解散模块夹具包回流：{ghost} 个（要求 0）")

    if problems:
        print("\n✗ 模块图违例：")
        for p in problems:
            print("   " + p)
        return 1
    print("\n✓ 模块图合规（主码矩阵 · 主码无同包 · 测试边已登记 · 测试无错位 · 夹具包无占用）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
