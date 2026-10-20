#!/usr/bin/env python3
"""生成 chat 管线事件表：事件 → 插件 → 读/写字段。

用法：
    python3 scripts/gen-pipeline-map.py          # 生成 docs/architecture/pipeline-map.md
    python3 scripts/gen-pipeline-map.py --check  # 校验生成物（守卫用）

## 数据来源

  * 插件响应哪些事件：`activationEvents()` 里的 `PipelineEventType.X`；
  * 插件注册顺序：`QaWiring` 里 `mgr.register(new XxxPlugin(` 的出现序；
  * 读/写字段：插件代码里 `cm.getXxx()` / `cm.setXxx()`，变量名取自
    `onEvent(..., ChatManage cm, ...)` 的形参。

生成物是**派生数据**：改了插件或注册顺序，跑一次本脚本即可，不要手改。
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
PLUGIN_DIR = ROOT / "domains/src/main/java/com/ragagent/session/rag/plugin"
WIRING = ROOT / "domains/src/main/java/com/ragagent/session/assembly/QaWiring.java"
EVENT_ENUM = ROOT / "domains/src/main/java/com/ragagent/session/rag/PipelineEventType.java"
OUT = ROOT / "docs/architecture/pipeline-map.md"


def event_order() -> list[str]:
    text = EVENT_ENUM.read_text(encoding="utf-8")
    return re.findall(r"^\s{4}([A-Z_]+)\(", text, re.M)


def registration_order() -> list[str]:
    text = WIRING.read_text(encoding="utf-8")
    return re.findall(r"mgr\.register\(new (\w+)\(", text)


def plugin_meta() -> dict:
    meta = {}
    for f in sorted(PLUGIN_DIR.glob("*.java")):
        text = f.read_text(encoding="utf-8")
        if "implements Plugin" not in text:
            continue
        name = f.stem
        events = re.findall(r"PipelineEventType\.([A-Z_]+)", text)
        var = re.search(r"ChatManage\s+(\w+)\s*[,)]", text)
        accesses = re.findall(rf"\b{re.escape(var.group(1))}\.(get|set|is)([A-Z]\w*)\(", text) if var else []
        # 状态组经 getter 链访问：cm.getAnswer().setUserContent(...) / cm.getRuntime().getEventBus()
        if var:
            accesses += re.findall(
                rf"\b{re.escape(var.group(1))}\.get(?:Answer|Runtime|Rerank|Merged|QueryPlan|EntityContext|SearchOutcome)\(\)\.(get|set|is)([A-Z]\w*)\(",
                text)
        reads, writes = set(), set()
        state_wrappers = {"answer", "runtime", "rerank", "merged", "queryPlan",
                          "entityContext", "searchOutcome"}
        for kind, field in accesses:
            field_name = field[0].lower() + field[1:]
            if field_name in state_wrappers:
                continue
            if kind == "set":
                writes.add(field_name)
            else:
                reads.add(field_name)
        meta[name] = dict(events=sorted(set(events)), reads=sorted(reads), writes=sorted(writes))
    return meta


def render() -> str:
    meta = plugin_meta()
    order = registration_order()
    order += [n for n in sorted(meta) if n not in order]
    events = event_order()

    lines = [
        "# chat 管线事件表",
        "",
        "> 由 `scripts/gen-pipeline-map.py` 从代码生成，**不要手改**；`--check` 守卫防漂移。",
        "> 读/写字段来自插件里对 `ChatManage` 的 getter/setter 调用，是粗略数据流。",
        "",
        "## 事件 → 插件（按注册顺序）",
        "",
    ]
    for ev in events:
        rows = [n for n in order if ev in meta.get(n, {}).get("events", [])]
        if not rows:
            continue
        lines.append(f"### `{ev}`")
        lines.append("")
        lines.append("| 插件 | 读 | 写 |")
        lines.append("|---|---|---|")
        for name in rows:
            m = meta[name]
            reads = "、".join(f"`{x}`" for x in m["reads"][:6]) or "—"
            writes = "、".join(f"`{x}`" for x in m["writes"][:6]) or "—"
            lines.append(f"| {name} | {reads} | {writes} |")
        lines.append("")
    lines.append("## 注册顺序（`QaWiring`）")
    lines.append("")
    for i, name in enumerate(order, 1):
        lines.append(f"{i}. {name}")
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    content = render()
    if "--check" in sys.argv:
        current = OUT.read_text(encoding="utf-8") if OUT.exists() else ""
        if current != content:
            print(f"✗ {OUT.relative_to(ROOT)} 与代码不同步；运行 python3 scripts/gen-pipeline-map.py")
            return 1
        print("✓ 管线事件表与代码一致")
        return 0
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(content, encoding="utf-8")
    print(f"✓ 已生成 {OUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
