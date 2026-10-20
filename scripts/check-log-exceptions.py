#!/usr/bin/env python3
"""日志异常守卫：log 调用里不得把 Throwable 转成字符串，必须传异常对象。

判据（硬零）：
  L1 log 参数里出现 `.getMessage()`（Throwable 的 message 会丢掉栈）。
  L2 log 参数里对异常变量调 `.toString()`（含 `e.getCause().toString()`）。
  L3 log 参数里出现 `xxx.error().toString()`。

非异常的 `raw.toString()` / `map.toString()` 不在此列；守卫只认 catch 变量、
声明为 Exception/Error/Throwable 的变量，以及 error-ish 命名。

红态探针：临时放一个 `catch (Exception e) { log.warn("x: {}", e.toString()); }`
的文件，本脚本必须非零退出；跑完删除。
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
ROOTS = [ROOT / name / "src" / "main" / "java" for name in (
    "domains", "agent-engine", "retrieval-engine", "model-runtime", "platform", "boot"
)]
LOG_RE = re.compile(r"\blog\.(trace|debug|info|warn|error)\s*\(")
THROW_DECL_RE = re.compile(
    r"\b([A-Z]\w*(?:Exception|Error|Throwable)|Exception|Error|Throwable)\s+(\w+)\b")
CATCH_RE = re.compile(
    r"catch\s*\(\s*(?:final\s+)?([\w.]+(?:\s*\|\s*[\w.]+)*)\s+(\w+)\s*\)")
ERROR_NAMES = {
    "e", "ex", "err", "error", "exception", "cause", "failure", "first",
    "callErr", "verifyErr", "runErr", "derr", "jerr", "delErr", "throwable",
}
COMMENT_BLOCK_RE = re.compile(r"/\*.*?\*/", re.S)
COMMENT_LINE_RE = re.compile(r"//[^\n]*")


def strip_comments(text: str) -> str:
    return COMMENT_LINE_RE.sub("", COMMENT_BLOCK_RE.sub("", text))


def close_paren(text: str, start: int) -> int:
    depth = 0
    i = start
    in_str = None
    esc = False
    while i < len(text):
        c = text[i]
        if in_str:
            if esc:
                esc = False
            elif c == "\\":
                esc = True
            elif c == in_str:
                in_str = None
        else:
            if c in "\"'":
                in_str = c
            elif c == "(":
                depth += 1
            elif c == ")":
                depth -= 1
                if depth == 0:
                    return i
        i += 1
    return -1


def split_args(s: str) -> list[str]:
    args = []
    cur = []
    depth = 0
    in_str = None
    esc = False
    for c in s:
        if in_str:
            cur.append(c)
            if esc:
                esc = False
            elif c == "\\":
                esc = True
            elif c == in_str:
                in_str = None
        else:
            if c in "\"'":
                in_str = c
                cur.append(c)
            elif c in "([{":
                depth += 1
                cur.append(c)
            elif c in ")]}":
                depth -= 1
                cur.append(c)
            elif c == "," and depth == 0:
                args.append("".join(cur).strip())
                cur = []
            else:
                cur.append(c)
    if "".join(cur).strip():
        args.append("".join(cur).strip())
    return args


def java_files() -> list[pathlib.Path]:
    files = []
    for root in ROOTS:
        files.extend(p for p in root.rglob("*.java") if "/build/" not in str(p) and "/target/" not in str(p))
    return sorted(files)


def main() -> int:
    problems = []
    scanned = 0
    for path in java_files():
        text = strip_comments(path.read_text(encoding="utf-8"))
        throws = {m.group(2) for m in THROW_DECL_RE.finditer(text)}
        throws |= {m.group(2) for m in CATCH_RE.finditer(text)}
        for match in LOG_RE.finditer(text):
            open_i = text.find("(", match.end() - 1)
            close_i = close_paren(text, open_i)
            if close_i < 0:
                continue
            scanned += 1
            body = text[open_i + 1:close_i]
            for arg in split_args(body)[1:]:
                line = text[:match.start()].count("\n") + 1
                rel = path.relative_to(ROOT)
                if ".getMessage(" in arg:
                    problems.append(f"{rel}:{line}: log 参数含 .getMessage()，改传异常对象")
                    continue
                if re.search(r"\.error\(\)\s*\.toString\(\)", arg):
                    problems.append(f"{rel}:{line}: log 参数含 error().toString()，改传异常对象")
                    continue
                m = re.search(r"\b(\w+)(?:\.getCause\(\))?\.toString\(\)", arg)
                if m and (m.group(1) in throws or m.group(1) in ERROR_NAMES):
                    problems.append(
                        f"{rel}:{line}: log 参数对 {m.group(1)} 调 .toString()，改传异常对象")

    if problems:
        print("✗ 日志异常守卫失败：")
        for problem in problems:
            print("   " + problem)
        print("  规则：log 的最后一个参数传 Throwable 本身，不要传 toString()/getMessage()。")
        return 1

    print(f"✓ 日志异常：{scanned} 条 log 调用未发现 Throwable 转字符串")
    return 0


if __name__ == "__main__":
    sys.exit(main())
