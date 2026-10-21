#!/usr/bin/env python3
"""契约消费者答案：每个 contract 类型必须能回答「谁在用它」。

## 为什么

contract 的准入标准是「跨域词汇表的唯一一处」，但类型一多就会出现第二种失守：
某个类只剩自己或某一只域在用，仍留在 contract 里，慢慢变成「第二个 common」。
本脚本把消费者算成可查的答案，并用 `--check` 拦住**零消费者**的公共类型。

## 口径

  * 类型：contract 主码里的顶层 class/interface/record/enum（去掉注释后按行首匹配，
    嵌套类型不计）；
  * 消费者：`import com.ragagent.contract...<Type>;`（各模块 main/test），
    以及 contract 内同包文件的简单名引用；
  * `--check` 只判「零消费者」——单域消费者是合法答案（是否回域由人工按形状判断）。

## 用法

    python3 scripts/gen-contract-consumers.py            # 汇总 + 零消费者清单
    python3 scripts/gen-contract-consumers.py --check    # 零消费者即红
    python3 scripts/gen-contract-consumers.py MemoryKeys # 单类：谁在消费我
"""

import collections
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MODULES = ["contract", "platform", "model-runtime", "retrieval-engine",
           "agent-engine", "domains", "boot"]
CONTRACT = ROOT / "contract/src/main/java"
TYPE = re.compile(r"^(?:public\s+)?(?:final\s+|abstract\s+|sealed\s+)?"
                  r"(?:class|interface|record|enum)\s+(\w+)", re.M)
IMPORT = re.compile(r"^import\s+(com\.ragagent\.contract\.[\w.]+);", re.M)


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


def contract_types() -> dict[str, pathlib.Path]:
    found = {}
    for path in CONTRACT.rglob("*.java"):
        text = strip_comments(path.read_text(encoding="utf-8", errors="ignore"))
        pkg = re.search(r"^package\s+([\w.]+);", text, re.M)
        if not pkg:
            continue
        for name in top_level_types(text):
            found[f"{pkg.group(1)}.{name}"] = path
    return found


def consumer_label(module: str, path: pathlib.Path) -> str:
    if module == "domains":
        match = re.match(r"domains/src/(?:main|test|testFixtures)/java/com/ragagent/(\w+)/",
                         path.as_posix())
        return f"domains:{match.group(1)}" if match else "domains"
    return module


def scan(types: dict[str, pathlib.Path]) -> dict[str, set[str]]:
    """类型 → 消费者标签集合（不含声明文件自身）。"""
    consumers = collections.defaultdict(set)
    simple = {fq.rsplit(".", 1)[-1]: fq for fq in types}
    for module in MODULES:
        for kind in ("main", "test", "testFixtures"):
            base = ROOT / module / "src" / kind / "java"
            if not base.exists():
                continue
            for path in base.rglob("*.java"):
                text = path.read_text(encoding="utf-8", errors="ignore")
                for imported in IMPORT.findall(text):
                    if imported in types and types[imported] != path:
                        consumers[imported].add(consumer_label(module, path))
                if module != "contract":
                    continue
                body = strip_comments(text)
                for name, fq in simple.items():
                    if types[fq] == path:
                        continue
                    if re.search(rf"\b{name}\b", body):
                        consumers[fq].add("contract")
    return consumers


def main() -> int:
    types = contract_types()
    consumers = scan(types)

    if len(sys.argv) > 1 and not sys.argv[1].startswith("-"):
        wanted = sys.argv[1]
        matches = [fq for fq in types if fq.endswith("." + wanted) or fq == wanted]
        if not matches:
            print(f"✗ contract 里没有 {wanted}")
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
