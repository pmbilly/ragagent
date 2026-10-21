#!/usr/bin/env python3
"""工具注册一致性（L3）：工具名常量 ↔ agenttools 实现 ↔ 分发 switch 三方对齐。

## 为什么

`session/service/AgentToolBackends` 用三个 switch 按工具名构造实例，工具实现按业务族分在
`agenttools` 的 5 个包里，工具名常量又集中在 `contract.tool.ToolDefinitions`。
三处靠人工同步：新增一个工具类但忘了加 case（或删了 case 留死实现）都不会编译失败，
只会在运行期表现为"Unknown tool"或死代码。

## 判据

  T1 `agenttools` 里每个 `super(ToolDefinitions.TOOL_X, …)` 都必须在分发 switch 有 case；
  T2 分发里的每个 `case ToolDefinitions.TOOL_X` 都必须有对应的实现类；
  T3 `ToolDefinitions` 的 `TOOL_*` 常量值不得重复；
  T4 分发里的每个 case 都必须被 `AgentEngineAssembler.buildTools` 覆盖
     （后者的 default 只 `log.warn`，漏 case 的后果是运行期静默丢工具）。

范围：只覆盖 `agenttools`（22 个业务工具）。agent-engine 内置的 3 个工具与 MCP 动态工具
不走这两个 switch，不在本判据内。

## 用法

    python3 scripts/check-tool-registry.py
    python3 scripts/check-tool-registry.py --list
"""

import argparse
import collections
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DEFINITIONS = ROOT / "contract/src/main/java/com/ragagent/contract/tool/ToolDefinitions.java"
AGENTTOOLS = ROOT / "domains/src/main/java/com/ragagent/agenttools"
DISPATCH = ROOT / "domains/src/main/java/com/ragagent/session/service/AgentToolBackends.java"

CONSTANT = re.compile(r"public static final String (TOOL_\w+)\s*=\s*\"([^\"]+)\"")
IMPLEMENTED = re.compile(r"super\(\s*ToolDefinitions\.(TOOL_\w+)")
DISPATCHED = re.compile(r"case ToolDefinitions\.(TOOL_\w+)")
ASSEMBLER = ROOT / "domains/src/main/java/com/ragagent/session/service/AgentEngineAssembler.java"


def definitions() -> dict[str, str]:
    text = DEFINITIONS.read_text(encoding="utf-8")
    return dict(CONSTANT.findall(text))


def implemented() -> dict[str, list[str]]:
    found = collections.defaultdict(list)
    for path in sorted(AGENTTOOLS.rglob("*.java")):
        for name in IMPLEMENTED.findall(path.read_text(encoding="utf-8")):
            found[name].append(str(path.relative_to(ROOT)))
    return dict(found)


def dispatched() -> set[str]:
    return set(DISPATCHED.findall(DISPATCH.read_text(encoding="utf-8")))


def assembler_covered() -> set[str]:
    """`AgentEngineAssembler.buildTools` 的 switch 覆盖了哪些工具常量。

    该 switch 的 default 只 `log.warn("Unknown tool")`（不抛、不编译失败），
    漏 case 的表现是**运行期静默丢工具**——模型选了工具、装配层却造不出实例。
    这是第四条清单，之前没有任何守卫盯着它与 AgentToolBackends 的包含关系。
    """
    src = ASSEMBLER.read_text(encoding="utf-8")
    start = src.find("private List<AgentTool> buildTools(")
    if start < 0:
        raise SystemExit("✗ 未找到 AgentEngineAssembler.buildTools（判据失效，请更新守卫）")
    end = src.find("default ->", start)
    segment = src[start:end if end > 0 else len(src)]
    return set(re.findall(r"ToolDefinitions\.(TOOL_\w+)", segment))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--list", action="store_true", help="按工具名打印实现与分发")
    args = parser.parse_args()

    consts = definitions()
    impl = implemented()
    dispatch = dispatched()
    problems = []

    for name in sorted(set(impl) - dispatch):
        problems.append(f"T1 {name} 有实现（{impl[name][0]}）但分发 switch 没有 case")
    for name in sorted(dispatch - set(impl)):
        problems.append(f"T2 分发引用了 {name}，但 agenttools 里没有对应实现")
    covered = assembler_covered()
    for name in sorted(dispatch - covered):
        problems.append(
            f"T4 {name} 在 AgentToolBackends 有 case，但 AgentEngineAssembler.buildTools 未覆盖"
            "（运行期会落到 default 被静默丢弃）")
    for name in sorted(set(impl) & dispatch):
        if name not in consts:
            problems.append(f"T3 {name} 不在 ToolDefinitions 常量表")

    by_value = collections.defaultdict(list)
    for name, value in consts.items():
        by_value[value].append(name)
    for value, names in sorted(by_value.items()):
        if len(names) > 1:
            problems.append(f"T3 工具名重复：\"{value}\" ← {', '.join(sorted(names))}")

    if problems:
        print("✗ 工具注册一致性守卫失败：")
        for p in problems:
            print("   " + p)
        print("  正解：工具实现与分发 case 同批增删；工具名只在 ToolDefinitions 声明一次。")
        return 1

    if args.list:
        for name in sorted(consts):
            mark = "✓" if name in dispatch else "·"
            owners = impl.get(name)
            print(f"  {mark} {consts[name]:24s} {name:36s} "
                  + (owners[0].split("/")[-1] if owners else "（非 agenttools）"))
        print()

    print(f"✓ 工具注册一致：{len(impl)} 个 agenttools 实现 / {len(dispatch)} 条分发 / "
          f"{len(consts)} 个工具名常量 / 装配 switch 覆盖 {len(covered)} 个")
    return 0


if __name__ == "__main__":
    sys.exit(main())
