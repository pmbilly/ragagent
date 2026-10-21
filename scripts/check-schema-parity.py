#!/usr/bin/env python3
"""schema 对账：测试建表（TestSchema）vs 迁移脚本（migrations/versioned）。

为什么需要：测试库是 H2 内存库，表由 `TestSchema` 手写 DDL 建；生产表由 Flyway 跑 SQL 建。
两边是**两份手写事实**，迁移加了列而测试没跟时，测试仍会绿（H2 只建旧列），
直到某个断言碰到新列才炸——那时错误信息离根因很远（"column not found"）。本守卫把
这类漂移提前到闸门里，机制与 `check-version-consistency` 同款：解析 → 对比 → 红。

判据（三条，任何一条非空即红）：
  P1 迁移里有、测试里没有的表     ⇒ 测试库缺表
  P2 测试里有、所有迁移里都没有的表 ⇒ 测试里造了"假表"
  P3 同一张表：迁移的列在测试里缺失 ⇒ 测试库缺列（反向缺失只提示，不算红：
     测试可能为 H2 兼容多建辅助列，需人工确认）

用法：python3 scripts/check-schema-parity.py [--verbose]
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEST_SCHEMA = ROOT / "domains/src/testFixtures/java/com/ragagent/TestSchema.java"
MIGRATIONS = sorted((ROOT / "migrations/versioned").glob("V*.sql"))

CONSTRAINT_HEADS = {"primary", "unique", "constraint", "foreign", "key", "index", "check"}


def split_top_level(body: str) -> list[str]:
    """按顶层逗号切分（括号内的逗号不算）。"""
    parts, depth, current = [], 0, []
    for ch in body:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append("".join(current))
            current = []
        else:
            current.append(ch)
    if current:
        parts.append("".join(current))
    return parts


def parse_create_table(sql: str):
    """从一段 SQL 中取出 (表名, 列名列表)；不是 CREATE TABLE 则返回 None。"""
    m = re.search(
        r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(?:\w+\.)?[\"`]?(\w+)[\"`]?\s*\(",
        sql,
        re.IGNORECASE,
    )
    if not m:
        return None
    name = m.group(1).lower()
    body = sql[m.end():]
    # 列定义里的字符串字面量（DEFAULT '...' / JSON 默认值）含逗号与引号，会污染切分
    body = re.sub(r"'(?:[^']|'')*'", "''", body)
    # 括号配对，找到表定义的收尾
    depth, end = 1, None
    for i, ch in enumerate(body):
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
            if depth == 0:
                end = i
                break
    if end is None:
        return None
    cols = []
    for piece in split_top_level(body[:end]):
        piece = piece.strip()
        if not piece:
            continue
        head = re.split(r"\s+", piece)[0].strip("\"`").lower()
        if head in CONSTRAINT_HEADS:
            continue
        cols.append(head)
    return name, cols


def java_string_literal_text(src: str) -> str:
    """把 TestSchema 里所有 jdbc.execute("..." + "..." ...) 还原成纯 SQL 文本。"""
    chunks = []
    for m in re.finditer(r'jdbc\.execute\(((?:\s*"(?:[^"\\]|\\.)*"\s*\+?)+)\)', src):
        literals = re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1))
        chunks.append("".join(literals).replace('\\"', '"').replace("\\n", "\n"))
    return "\n;\n".join(chunks)


def java_tables() -> dict[str, list[str]]:
    src = TEST_SCHEMA.read_text(encoding="utf-8")
    out: dict[str, list[str]] = {}
    for sql in java_string_literal_text(src).split("\n;\n"):
        parsed = parse_create_table(sql)
        if parsed:
            out[parsed[0]] = parsed[1]
    return out


def sql_tables() -> tuple[dict[str, list[str]], dict[str, list[str]]]:
    """返回 (V1 定义的表, 全部迁移定义的表)。"""
    v1: dict[str, list[str]] = {}
    all_tables: dict[str, list[str]] = {}
    for path in MIGRATIONS:
        text = re.sub(r"--[^\n]*", "", path.read_text(encoding="utf-8"))
        for stmt in text.split(";"):
            parsed = parse_create_table(stmt)
            if parsed:
                name, cols = parsed
                all_tables[name] = list(dict.fromkeys(all_tables.get(name, []) + cols))
                if path.name.startswith("V1__"):
                    v1[name] = cols
    # 后续迁移的 ALTER TABLE ... ADD COLUMN 也算"迁移里存在的列"
    for path in MIGRATIONS:
        if path.name.startswith("V1__"):
            continue
        text = re.sub(r"--[^\n]*", "", path.read_text(encoding="utf-8"))
        for m in re.finditer(
            r"ALTER\s+TABLE\s+(?:\w+\.)?[\"`]?(\w+)[\"`]?[^;]*?ADD\s+COLUMN\s+(?:IF\s+NOT\s+EXISTS\s+)?[\"`]?(\w+)",
            text,
            re.IGNORECASE,
        ):
            table, col = m.group(1).lower(), m.group(2).lower()
            all_tables.setdefault(table, []).append(col)
    return v1, all_tables


def main() -> int:
    verbose = "--verbose" in sys.argv

    java = java_tables()
    v1, all_sql = sql_tables()

    missing_tables = sorted(set(v1) - set(java))
    fake_tables = sorted(set(java) - set(all_sql))
    missing_cols = []
    extra_cols = []
    # 列对比用**全部迁移**（V2+ 的 ALTER ADD COLUMN 也算），只看 V1 会把后续迁移加的列误报成"测试多建"
    for table in sorted(set(all_sql) & set(java)):
        sql_cols, java_cols = set(all_sql[table]), set(java[table])
        gone = sorted(sql_cols - java_cols)
        extra = sorted(java_cols - sql_cols)
        if gone:
            missing_cols.append((table, gone))
        if extra:
            extra_cols.append((table, extra))

    if verbose:
        print(f"  迁移表 {len(v1)}（全部 {len(all_sql)}）/ 测试表 {len(java)}")

    problems = 0
    for table in missing_tables:
        print(f"  P1 测试库缺表：{table}（迁移里有）")
        problems += 1
    for table in fake_tables:
        print(f"  P2 测试里造了迁移中不存在的表：{table}")
        problems += 1
    for table, cols in missing_cols:
        print(f"  P3 测试库缺列：{table}.{', '.join(cols)}")
        problems += 1
    for table, cols in extra_cols:
        print(f"  提示（不计红）：{table} 测试多建了列 {', '.join(cols)}，确认是否为 H2 兼容所需")

    if problems:
        print(f"✗ schema 对账失败：{problems} 处漂移（改 TestSchema 或迁移脚本后重跑）")
        return 1
    print(f"✓ schema 对账：迁移 {len(v1)} 张表 / 测试 {len(java)} 张表，表名与列名一致")
    return 0


if __name__ == "__main__":
    sys.exit(main())
