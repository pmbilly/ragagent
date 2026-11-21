#!/usr/bin/env python3
"""契约消费者答案：每个 api 契约类型必须能回答「谁在用它」。

## 为什么

api 包的准入标准是「跨域词汇的唯一一处」，但类型一多就会出现第二种失守：
某个类只剩自己或某一只域在用，仍留在 api 里，慢慢变成「第二个 shared」。
本脚本把消费者算成可查的答案，并用 `--check` 拦住**零消费者**的公共类型。

## 口径

  * 类型：各模块主码里包路径含 `api` 段的顶层 class/interface/record/enum
    （去掉注释后按行首匹配，嵌套类型不计）；
  * 消费者：`import com.ragagent...<Type>;`（各模块 main/test/testFixtures），
    以及同包文件的简单名引用；
  * `--check` 只判「零消费者」——单域消费者是合法答案（是否回域由人工按形状判断）。

## 用法

    python3 scripts/gen-contract-consumers.py            # 汇总 + 零消费者清单
    python3 scripts/gen-contract-consumers.py --check    # 零消费者即红
    python3 scripts/gen-contract-consumers.py MemoryKeys # 单类：谁在消费我
    python3 scripts/gen-contract-consumers.py --relocate # 还乡可行性：闭包 + 目标域 + 阻断
    python3 scripts/gen-contract-consumers.py --relocate MessageImage  # 单类可行性
"""

import collections
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))
import _source_roots as _sr  # noqa: E402

API_SEG = "api"
TYPE = re.compile(r"^(?:public\s+)?(?:final\s+|abstract\s+|sealed\s+)?"
                  r"(?:class|interface|record|enum)\s+(\w+)", re.M)
IMPORT = re.compile(r"^import\s+(com\.ragagent\.[\w.]+);", re.M)
PKG = re.compile(r"^package\s+([\w.]+);", re.M)


def strip_comments(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def top_level_types(text: str) -> list[str]:
    """只取顶层类型：嵌套记录/类不计入（它们跟着外层类型一起被消费）。"""
    names = []
    depth = 0
    for line in text.splitlines():
        match = TYPE.match(line)
        if match and depth == 0:
            names.append(match.group(1))
        depth += line.count("{") - line.count("}")
    return names


def _api_files(sources: tuple[str, ...]):
    """主码里包路径含 `api` 段的文件：(源集, 模块, 路径)。"""
    for source_set, module, base in _sr.java_roots(sources):
        for path in base.rglob("*.java"):
            if API_SEG in path.relative_to(base).parts[:-1]:
                yield source_set, module, path


def _all_files(sources: tuple[str, ...]):
    for source_set, module, base in _sr.java_roots(sources):
        for path in base.rglob("*.java"):
            yield source_set, module, path


def contract_types() -> dict[str, pathlib.Path]:
    found = {}
    for _, _, path in _api_files(("main",)):
        text = strip_comments(path.read_text(encoding="utf-8", errors="ignore"))
        pkg = PKG.search(text)
        if not pkg:
            continue
        for name in top_level_types(text):
            found[f"{pkg.group(1)}.{name}"] = path
    return found


def consumer_label(module: str, path: pathlib.Path) -> str:
    if module == "backend/app":
        match = re.search(r"src/(?:main|test|testFixtures)/java/com/ragagent/(\w+)/",
                          path.as_posix())
        return f"domains:{match.group(1)}" if match else "app"
    return module


def _reference_labels(types: dict[str, pathlib.Path], sources: tuple[str, ...],
                      split_kind: bool) -> dict[str, set[str]]:
    """类型 → 消费者标签集合（不含声明文件自身）。

    同包简单名引用也算消费者：api 包内互相引用的类型同样「有人在用」，
    少了这一支，纯同包工具类会全部落进零消费者。
    """
    by_pkg: dict[str, dict[str, str]] = collections.defaultdict(dict)
    for fq in types:
        pkg, name = fq.rsplit(".", 1)
        by_pkg[pkg][name] = fq

    labels = collections.defaultdict(set)
    for source_set, module, path in _all_files(sources):
        text = path.read_text(encoding="utf-8", errors="ignore")
        label = consumer_label(module, path)
        if split_kind and source_set != "main":
            label = f"{label}[{source_set}]"

        for imported in IMPORT.findall(text):
            if imported in types and types[imported] != path:
                labels[imported].add(label)

        body = strip_comments(text)
        pkg_match = PKG.search(body)
        if not pkg_match:
            continue
        for name, fq in by_pkg.get(pkg_match.group(1), {}).items():
            if types[fq] != path and re.search(rf"\b{name}\b", body):
                labels[fq].add(label)
    return labels


def scan(types: dict[str, pathlib.Path]) -> dict[str, set[str]]:
    return _reference_labels(types, ("main", "test", "testFixtures"), split_kind=False)


def scan_detailed(types: dict[str, pathlib.Path]):
    """返回（全部消费者标签，仅 main 消费者标签）；后者用于还乡阻断判定。"""
    all_labels = _reference_labels(types, ("main", "test", "testFixtures"), split_kind=True)
    return all_labels, scan(types)


def reference_graph(types: dict[str, pathlib.Path]) -> dict[str, set[str]]:
    """api 内部引用图：同包裸名 + 跨包 import。"""
    simple = {fq.rsplit(".", 1)[-1]: fq for fq in types}
    graph: dict[str, set[str]] = collections.defaultdict(set)
    for fq, path in types.items():
        body = strip_comments(path.read_text(encoding="utf-8", errors="ignore"))
        pkg = fq.rsplit(".", 1)[0]
        for name, target in simple.items():
            if target == fq:
                continue
            if pkg == target.rsplit(".", 1)[0]:
                if re.search(rf"\b{name}\b", body):
                    graph[fq].add(target)
            elif re.search(rf"import\s+{re.escape(target)};", body):
                graph[fq].add(target)
    return graph


def reverse_closure(start: str, graph: dict[str, set[str]]) -> set[str]:
    """所有直接或间接引用 start 的类型（迁移必须带上）。"""
    reverse: dict[str, set[str]] = collections.defaultdict(set)
    for src, targets in graph.items():
        for dst in targets:
            reverse[dst].add(src)
    seen = {start}
    stack = [start]
    while stack:
        current = stack.pop()
        for previous in reverse.get(current, ()):
            if previous not in seen:
                seen.add(previous)
                stack.append(previous)
    return seen


def relocate_report(types: dict[str, pathlib.Path], wanted: str | None) -> int:
    """按「反向闭包 + main 消费域」判断能否整体还乡。

    判据：闭包内所有类型的 main 消费者只落在一个域/模块（装配层视为跟随）。
    两个及以上域共享即阻断——搬走会让其余域反向依赖目标域。
    """
    _, main_labels = scan_detailed(types)
    graph = reference_graph(types)
    targets = [fq for fq in types
               if wanted is None or fq == wanted or fq.endswith("." + wanted)]
    if wanted and not targets:
        print(f"✗ api 里没有 {wanted}")
        return 1

    rows = []
    for fq in sorted(targets):
        closure = reverse_closure(fq, graph)
        domains: set[str] = set()
        details = []
        for member in sorted(closure):
            labels = set(main_labels.get(member, set()))
            if labels:
                details.append((member, labels))
                domains |= labels
        rows.append((fq, closure, domains, details))

    if wanted:
        for fq, closure, domains, details in rows:
            name = fq.rsplit(".", 1)[-1]
            real = {d for d in domains if d not in ("app", "backend/app")}
            if not real:
                status = "可迁（仅装配层/测试消费）" if domains else "无 main 消费者"
            elif len(real) == 1:
                status = f"可迁 → {next(iter(real))}"
            else:
                status = "阻断 → 跨域 " + ", ".join(sorted(real))
            print(f"{name}：{status}（闭包 {len(closure)}）")
            companions = sorted(x.rsplit(".", 1)[-1] for x in closure - {fq})
            if companions:
                print("  同搬：" + ", ".join(companions))
            for member, labels in details:
                print(f"    {member.rsplit('.', 1)[-1]} ← {', '.join(sorted(labels))}")
        return 0

    movable = [r for r in rows if len({d for d in r[2] if d not in ("app", "backend/app")}) == 1]
    blocked = [r for r in rows if len({d for d in r[2] if d not in ("app", "backend/app")}) > 1]
    dead = [r for r in rows if not r[2]]
    print(f"还乡可行性：可迁 {len(movable)} · 阻断 {len(blocked)} · 无 main 消费者 {len(dead)}")
    for fq, closure, domains, _ in sorted(movable, key=lambda r: r[0]):
        target = next(iter({d for d in domains if d not in ("app", "backend/app")}))
        print(f"  可迁  {fq.rsplit('.', 1)[-1]:28s} → {target:22s} 闭包 {len(closure)}")
    for fq, closure, domains, _ in sorted(blocked, key=lambda r: r[0]):
        print(f"  阻断  {fq.rsplit('.', 1)[-1]:28s} ← {', '.join(sorted(domains))}")
    if dead:
        print("  无 main 消费者（死码候选）：" + ", ".join(
            r[0].rsplit(".", 1)[-1] for r in sorted(dead, key=lambda r: r[0])))
    return 0


def main() -> int:
    types = contract_types()
    consumers = scan(types)

    if "--relocate" in sys.argv:
        index = sys.argv.index("--relocate")
        wanted = None
        if len(sys.argv) > index + 1 and not sys.argv[index + 1].startswith("-"):
            wanted = sys.argv[index + 1]
        return relocate_report(types, wanted)

    if len(sys.argv) > 1 and not sys.argv[1].startswith("-"):
        wanted = sys.argv[1]
        matches = [fq for fq in types if fq.endswith("." + wanted) or fq == wanted]
        if not matches:
            print(f"✗ api 里没有 {wanted}")
            return 1
        for fq in matches:
            labels = consumers.get(fq, set())
            print(f"{fq}\n  声明：{types[fq].relative_to(ROOT)}")
            if labels:
                print("  消费者：" + " / ".join(sorted(labels)))
            else:
                print("  消费者：无（按标准应删除或并入其他类型）")
        return 0

    zero = sorted(fq for fq in types if not consumers.get(fq))
    single = sorted(fq for fq in types if len(consumers.get(fq, set())) == 1)
    print(f"契约类型 {len(types)} 个；无消费者 {len(zero)} 个；单消费者 {len(single)} 个")
    for fq in zero:
        print(f"   零消费者  {fq}")

    if "--check" in sys.argv:
        if zero:
            print(f"✗ 契约消费者答案：{len(zero)} 个类型无人使用——删除，或写进 known-gaps 说明计划")
            return 1
        print("✓ 契约消费者答案：所有类型至少有一个消费者")
    return 0


if __name__ == "__main__":
    sys.exit(main())
