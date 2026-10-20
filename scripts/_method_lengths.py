#!/usr/bin/env python3
"""长方法扫描的共享实现（报告与棘轮守卫共用）。

先做一遍轻量词法遮罩：字符串、字符字面量、文本块（`\"\"\"`）、块注释、行注释里的
字符全部替换成空格（保留换行与列号），再配对花括号。这样 JSON/正则/SQL/文本块里的
`{` / `}` 不会把方法长度算虚高，也不会把文本块里的 Lua 片段误认成方法声明。

只认类体一级缩进（恰好 4 空格）且含参数表的声明；控制语句、注解、字段初始化
（`=` 出现在 `(` 之前）与类型声明排除。包级方法（无修饰符）同样要扫到——只认
public/private/protected 会漏掉门面里的大量包级方法（红态探针实测）。
"""

import pathlib
import re

MODULES = ["contract", "platform", "model-runtime", "retrieval-engine",
           "agent-engine", "domains", "boot"]

DECL = re.compile(r"^ {4}(?! )(?!if\b|for\b|while\b|switch\b|catch\b|try\b|else\b|return\b|"
                  r"new\b|throw\b|assert\b|case\b|default\b|yield\b|synchronized\s*\(|@|//|\*|/\*)"
                  r"(?P<sig>.+?)\(.*$")


def _mask_line(line: str, in_text: bool, in_block: bool):
    """把字面量/注释替换成空格；返回 (遮罩后的行, 文本块状态, 块注释状态)。"""
    out = []
    i = 0
    n = len(line)
    while i < n:
        if in_text:
            if line.startswith('"""', i):
                in_text = False
                out.append("   ")
                i += 3
            else:
                out.append(" ")
                i += 1
            continue
        if in_block:
            if line.startswith("*/", i):
                in_block = False
                out.append("  ")
                i += 2
            else:
                out.append(" ")
                i += 1
            continue

        c = line[i]
        if c == "/" and line.startswith("//", i):
            out.append(" " * (n - i))
            break
        if c == "/" and line.startswith("/*", i):
            in_block = True
            out.append("  ")
            i += 2
            continue
        if line.startswith('"""', i):
            in_text = True
            out.append("   ")
            i += 3
            continue
        if c in "\"'":
            quote = c
            out.append(" ")
            i += 1
            while i < n:
                if line[i] == "\\":
                    out.append("  ")
                    i += 2
                elif line[i] == quote:
                    out.append(" ")
                    i += 1
                    break
                else:
                    out.append(" ")
                    i += 1
            continue
        out.append(c)
        i += 1
    return "".join(out), in_text, in_block


def _mask(lines: list[str]) -> list[str]:
    masked = []
    in_text = in_block = False
    for line in lines:
        clean, in_text, in_block = _mask_line(line, in_text, in_block)
        masked.append(clean)
    return masked


def method_lengths(path: pathlib.Path):
    """返回 [(行数, 方法名, 起始行号)]。"""
    raw = path.read_text(encoding="utf-8", errors="ignore").splitlines()
    lines = _mask(raw)
    out = []
    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        match = DECL.match(line)
        if not match or "class " in line or "interface " in line or "record " in line \
                or "enum " in line:
            i += 1
            continue
        # 排除字段初始化（`private static final Logger log = X.y(...)`）与委托赋值
        if "=" in match.group("sig"):
            i += 1
            continue
        # 抽象/接口方法没有方法体：签名后的第一个有效符号是 `;` 而不是 `{`
        has_body = False
        for j in range(i, min(i + 12, len(lines))):
            seg = lines[j]
            brace = seg.find("{")
            semi = seg.find(";")
            if brace != -1 and (semi == -1 or brace < semi):
                has_body = True
                break
            if semi != -1:
                break
        if not has_body:
            i += 1
            continue
        depth = 0
        started = False
        end = None
        for j in range(i, min(i + 800, len(lines))):
            for c in lines[j]:
                if c == "{":
                    depth += 1
                    started = True
                elif c == "}":
                    depth -= 1
            if started and depth <= 0:
                end = j
                break
        if end is not None:
            parts = stripped.split("(")[0].split()
            if not parts:            # 续行/强转等以 `(` 开头的 4 空格行，不是声明
                i += 1
                continue
            name = parts[-1]
            out.append((end - i + 1, name, i + 1))
            i = end + 1
        else:
            i += 1
    return out


def scan(threshold: int, root: pathlib.Path):
    """全量主码扫描：返回 [(行数, 相对路径, 方法名, 起始行号, 稳定键)]。"""
    rows = []
    for module in MODULES:
        module_root = root / module / "src/main/java"
        if not module_root.is_dir():
            continue
        for f in module_root.rglob("*.java"):
            rel = str(f.relative_to(root))
            seen = {}
            for size, name, line in method_lengths(f):
                if size <= threshold:
                    continue
                # 重载同名方法用出现序号区分；方法改名会触发基线失效，这是有意语义
                seen[name] = seen.get(name, 0) + 1
                suffix = f"#{seen[name]}" if seen[name] > 1 else ""
                rows.append((size, rel, name, line, f"{rel}#{name}{suffix}"))
    rows.sort(reverse=True)
    return rows
