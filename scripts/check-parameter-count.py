#!/usr/bin/env python3
"""参数数量棘轮：>7 个参数的方法/构造器只许减，新增即红。

判据（与 check-method-length.py 同一套，一字不改的语义）：
  R-a 新增长参数签名：基线里没有的 >7 参方法/构造器 → 红（先收拢，或手工登记理由）
  R-b 回涨：基线内签名比基线多出 > growthSlack（0）个参数 → 红
  R-c 基线失效：基线里的签名已不存在（改签名/删了）→ 红，跑 --write 收紧
  R-d 理由必填：基线每条必须写 reason；空理由 → 红
  R-e 只收紧：`--write` 只更新现有条目（取 min）并清理失效项，**不新增条目**

与 check-hygiene-budget.py 的关系：那边把「≥8 参构造器总数」当趋势计数（B-a 只看总数），
本守卫把每个超限签名逐条钉住（R-a 看具体条目）。构造器两边都覆盖，属双重信号非冲突；
方法参数只有本守卫管。

阈值取 7（蓝图 3.11：参数 5 是北极星；构造器依赖上限 7 是 Spring Bean 的装配边界，
统一取 7 分档逼近，2026-11-23 首次登记）。

计数口径：复用 _method_lengths 的词法遮罩（字符串/注释里的逗号不算），参数表按括号
配平取全文，再数顶层逗号（泛型 <>) 与注解括号内的逗号不计）。方法与构造器同查。

用法：
    python3 scripts/check-parameter-count.py
    python3 scripts/check-parameter-count.py --write   # 只收紧/清理，不新增
    python3 scripts/check-parameter-count.py --list    # 只列当前 >7 参签名
"""

import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from _method_lengths import DECL, MODULES, _mask  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parent.parent
BASELINE = ROOT / "scripts/parameter-count.baseline.json"
THRESHOLD = 7
GROWTH_SLACK = 0


def _parameter_count(lines: list[str], decl_idx: int) -> int:
    """从声明行的第一个 `(` 起配平括号取参数表，数顶层逗号（返回参数个数）。"""
    start = lines[decl_idx].find("(")
    if start < 0:
        return 0
    depth_paren = 0
    depth_angle = 0
    params = []
    buf = []
    for j in range(decl_idx, min(decl_idx + 40, len(lines))):
        for c in lines[j][start if j == decl_idx else 0:]:
            if c == "(":
                depth_paren += 1
                if depth_paren == 1:
                    continue
            elif c == ")":
                depth_paren -= 1
                if depth_paren == 0:
                    params.append("".join(buf))
                    return _count_top_level(params)
            elif c == "<":
                depth_angle += 1
            elif c == ">":
                depth_angle = max(0, depth_angle - 1)
            if depth_paren >= 2 or depth_angle > 0:
                continue
            if c == "," and depth_paren == 1 and depth_angle == 0:
                params.append("".join(buf))
                buf = []
                continue
            buf.append(c)
    return _count_top_level(params + ["".join(buf)])


def _count_top_level(params: list[str]) -> int:
    """非空片段数即参数个数（空参数表 → 0；`@Anno(x, y) String a` 已被顶层逗号切分）。"""
    return sum(1 for p in params if p.strip())


def _parameter_counts(path: pathlib.Path):
    """返回 [(参数个数, 相对路径, 方法名, 起始行号, 稳定键)]，>THRESHOLD 才上报。"""
    raw = path.read_text(encoding="utf-8", errors="ignore").splitlines()
    lines = _mask(raw)
    rel = str(path.relative_to(ROOT))
    out = []
    seen: dict[str, int] = {}
    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        match = DECL.match(line)
        if not match or "class " in line or "interface " in line or "record " in line \
                or "enum " in line:
            i += 1
            continue
        if "=" in match.group("sig"):
            i += 1
            continue
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
        parts = stripped.split("(")[0].split()
        if not parts:
            i += 1
            continue
        name = parts[-1]
        count = _parameter_count(lines, i)
        if count > THRESHOLD:
            seen[name] = seen.get(name, 0) + 1
            suffix = f"#{seen[name]}" if seen[name] > 1 else ""
            out.append((count, rel, name, i + 1, f"{rel}#{name}{suffix}"))
        i += 1
    return out


def scan(threshold: int):
    rows = []
    for module in MODULES:
        module_root = ROOT / module / "src/main/java"
        if not module_root.is_dir():
            continue
        for f in module_root.rglob("*.java"):
            rows.extend(_parameter_counts(f))
    rows = [r for r in rows if r[0] > threshold]
    rows.sort(reverse=True)
    return rows


def load_baseline():
    if not BASELINE.exists():
        return None
    data = json.loads(BASELINE.read_text(encoding="utf-8"))
    return {key: (int(value["count"]), value.get("reason", ""))
            for key, value in data.get("parameters", {}).items()}


def render(entries: dict[str, tuple[int, str]]) -> str:
    data = {
        "note": ">7 参签名基线（方法+构造器）：只许减；新增必须人工登记理由；--write 不新增条目。",
        "threshold": THRESHOLD,
        "growthSlack": GROWTH_SLACK,
        "parameters": {key: {"count": count, "reason": reason}
                       for key, (count, reason) in sorted(entries.items())},
    }
    return json.dumps(data, ensure_ascii=False, indent=2) + "\n"


def write_baseline(baseline, current) -> int:
    if baseline is None:
        entries = {key: (count, "存量基线（WPQ2c 首次登记，2026-11-23）")
                   for count, _, _, _, key in current}
        BASELINE.write_text(render(entries), encoding="utf-8")
        print(f"✓ 参数数量基线已建立：{len(entries)} 个 >{THRESHOLD} 参签名")
        return 0
    new_keys = sorted({key for *_, key in current} - set(baseline))
    if new_keys:
        print("✗ --write 拒绝新增条目：以下签名是基线外的新增长参数，"
              "请先收拢，或手工在 baseline 登记理由：")
        for count, rel, name, line, key in current:
            if key in new_keys:
                print(f"   {count:3d}参  {rel}:{line}  {name}")
        return 1
    entries = {}
    for key, (old_count, reason) in baseline.items():
        if key not in {k for *_, k in current}:
            continue
        now = next(count for count, _, _, _, k in current if k == key)
        entries[key] = (min(old_count, now), reason)
    BASELINE.write_text(render(entries), encoding="utf-8")
    dropped = len(baseline) - len(entries)
    print(f"✓ 参数数量基线已收紧：保留 {len(entries)} 个，清理失效 {dropped} 个")
    return 0


def main() -> int:
    current = scan(THRESHOLD)

    if "--list" in sys.argv:
        for count, rel, name, line, _ in current:
            print(f"  {count:3d}参  {rel}:{line}  {name}")
        print(f"共 {len(current)} 个 >{THRESHOLD} 参签名")
        return 0

    baseline = load_baseline()
    if "--write" in sys.argv:
        return write_baseline(baseline, current)

    if baseline is None:
        print(f"✗ 缺少基线：{BASELINE.relative_to(ROOT)}（先跑 --write 建立）")
        return 1

    problems = []
    current_by_key = {key: (count, rel, name, line) for count, rel, name, line, key in current}
    for key, (count, reason) in sorted(baseline.items()):
        if not reason.strip():
            problems.append(f"R-d 基线缺少理由：{key}")
        if key not in current_by_key:
            problems.append(f"R-c 基线失效 {key}：签名已不存在——跑 --write 收紧")
            continue
        now, rel, name, line = current_by_key[key]
        if now > count + GROWTH_SLACK:
            problems.append(
                f"R-b 参数回涨 {rel}:{line} {name}：{count} → {now} 参——请收拢为对象/分组成"
                f"（growthSlack={GROWTH_SLACK}）")
    for count, rel, name, line, key in current:
        if key not in baseline:
            problems.append(
                f"R-a 新增长参数 {rel}:{line} {name}（{count} 参）"
                f"——请收拢；确需保留则手工在 baseline 登记理由")

    if problems:
        print(f"\n✗ 参数数量棘轮失败：{len(problems)} 处")
        for problem in problems:
            print("   " + problem)
        return 1
    print(f"✓ 参数数量棘轮：{len(current)} 个 >{THRESHOLD} 参签名均在基线内；"
          f"无新增、无回涨")
    return 0


if __name__ == "__main__":
    sys.exit(main())
