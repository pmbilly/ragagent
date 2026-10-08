#!/usr/bin/env python3
"""文件行数棘轮（B121 上线，B122 执行 §14.5 政策）。

五条规则：
  R-a 新文件硬门：主源码**不得新增 > threshold（600）行的文件**
  R-b 回涨门槛：基线内文件**不得比基线多出 > growthSlack（30）行**
      —— 判据来自实证：`im/service/ImService` 2026-10-01 切片到 664 行后「出榜」，
         7 天内被 im 域功能批次（Redis 面 → 跨实例 /stop → 附件面 → 选主广播 →
         附件异步入库）加到 **1,091 行（+427）**，而当时两套机制都看不见它：
         §14.5 只在「≥800」时登记（它出榜时 664），B121 只管新文件。
  R-c ≥ 800 必须登记：超过「例外判据」行的文件必须在 baseline 的 `exempt` 里出现
  R-d 例外必须自述：`exempt` 标 `accepted` 的文件，源码里必须有「规模例外」标记与理由
      —— 直接执行 HANDOFF §14.5「例外必须在类 javadoc 写明理由」
  R-e 自述数字必须相符：标记里若写了行数（`>800 行` / `~810 行`），须与实测相符，
      且**低于例外判据的文件不得再自称例外**
      —— 判据来自 `knowledge/service/FaqImportService`：切片后已 566 行，
         注释仍写着「规模例外（>800 行）」而无人察觉（B119 的 javadoc 守卫只管
         `{@link}` 引用，抓不到散文里的数字）。故 R-e **全量扫描**，不按阈值过滤。

设计取舍（为什么 R-b 留 30 行余量、而不是「一行都不许加」）：
  行数是**粗指标**，一处 bugfix 加两三行不该卡住 CI；但**一周涨 427 行**必须卡住。
  30 行 ≈ 两个方法，超过它就该顺手拆一刀，而不是继续往大文件里堆。

`--write` 只刷新 `limits` 并清理失效的 `exempt`；**新增 exempt 必须人工决策**
（拆分之后登记，或论证接缝后登记）——守卫不会替你豁免。
`exempt.status`：`accepted` = 已论证接缝；`pending` = 欠债（待拆 / 待复核），
守卫输出里标【待还债】，即后续批次的工作清单。
"""

import json
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import _source_roots as _sr  # noqa: E402

BASELINE = pathlib.Path("scripts/file-size.baseline.json")
THRESHOLD = 600            # 大文件判据（> 此值进入基线）
EXEMPT_LINE = 800          # §14.5 例外判据（≥ 此值必须登记）
GROWTH_SLACK = 30          # 基线内文件允许的增长余量
TOP_N = 12
TOL_LOW, TOL_HIGH = 0.8, 1.25   # R-e 自述容差

MARKER = re.compile(r"规模例外")
MARKER_NUM = re.compile(r"(\d{3,})\s*行")   # 只认「数字 + 行」，避免把 §14.5 的节号当行数


def scan() -> dict[str, tuple[int, bool, int | None]]:
    """全量主源码 → {路径: (行数, 是否有「规模例外」标记, 自述行数 or None)}。

    R-e 刻意**不**按 > threshold 过滤：过期自称最典型的样子就是「文件早被拆小了、
    注释还自称例外」（`FaqImportService` 566 行仍写「>800 行」）——只在基线集合里
    扫就永远抓不到这类漂移。
    """
    out = {}
    for p in _sr.all_java_files(sources=("main",)):
        lines = p.read_text(encoding="utf-8").splitlines()
        marked, claim = False, None
        for i, line in enumerate(lines[:120]):      # 标记写在类头附近
            if MARKER.search(line):
                m = MARKER_NUM.search("\n".join(lines[i:i + 3]))   # 标记可能跨行续写
                marked, claim = True, (int(m.group(1)) if m else None)
                break
        out[str(p.relative_to(_sr.REPO))] = (len(lines), marked, claim)
    return out


def main() -> int:
    write = "--write" in sys.argv
    all_files = scan()
    sizes = {f: n for f, (n, _, _) in all_files.items()}
    current = {f: n for f, n in sizes.items() if n > THRESHOLD}
    data = json.loads(BASELINE.read_text(encoding="utf-8")) if BASELINE.exists() else {}
    limits: dict[str, int] = data.get("limits", {})
    exempt: dict[str, dict] = data.get("exempt", {})

    if write:
        kept = {k: v for k, v in exempt.items() if sizes.get(k, 0) >= EXEMPT_LINE}
        dropped = sorted(set(exempt) - set(kept))
        BASELINE.write_text(json.dumps({
            "threshold": THRESHOLD, "growthSlack": GROWTH_SLACK, "exemptLine": EXEMPT_LINE,
            "limits": dict(sorted(current.items())), "exempt": dict(sorted(kept.items())),
        }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"✓ 基线已写入：> {THRESHOLD} 行 {len(current)} 个；例外登记 {len(kept)} 个"
              + (f"；清理失效登记 {dropped}" if dropped else ""))
        return 0

    new_files = sorted(set(current) - set(limits))
    exceeded = sorted(((f, current[f], limits[f]) for f in set(limits) & set(current)
                       if current[f] > limits[f] + GROWTH_SLACK), key=lambda x: -(x[1] - x[2]))
    shrunk = sorted(set(limits) - set(current))
    accepted = {f for f, v in exempt.items() if v.get("status") == "accepted"}
    pending = {f for f, v in exempt.items() if v.get("status") != "accepted"}

    print(f"大文件（> {THRESHOLD} 行）：{len(current)} 个；"
          f"其中 ≥ {EXEMPT_LINE} 行 {sum(1 for n in current.values() if n >= EXEMPT_LINE)} 个")
    for f, n in sorted(current.items(), key=lambda x: -x[1])[:TOP_N]:
        tag = "【登记例外】" if f in accepted else ("【待还债】" if f in pending else "")
        print(f"    {n:5}  {f} {tag}")
    if pending:
        print(f"  待还债（欠债登记，非已论证例外）：{len(pending)} 个")
        for f in sorted(pending):
            print(f"    {f}（{sizes.get(f, '?')} 行）—— {exempt[f].get('reason', '')[:76]}")

    problems = []
    for f in new_files:
        problems.append(f"R-a 新增超大文件 {f}（{current[f]} 行 > {THRESHOLD}）：请拆分，或论证内聚后登记")
    for f, n, base in exceeded:
        problems.append(f"R-b 回涨超阈 {f}：{base} → {n}（+{n - base} > {GROWTH_SLACK}）"
                        f"——请拆一刀，而不是继续往大文件里堆")
    for f, n in sorted(current.items()):
        if n >= EXEMPT_LINE and f not in exempt:
            problems.append(f"R-c ≥{EXEMPT_LINE} 行未登记 {f}（{n} 行）："
                            f"拆分，或按 §14.5 论证接缝后在 exempt 登记")
    for f in sorted(accepted):
        if not all_files.get(f, (0, False, None))[1]:
            problems.append(f"R-d 例外无自述 {f}：§14.5 要求例外在类 javadoc 写明理由"
                            f"（登记为 accepted 但源码里找不到「规模例外」标记）")
    for f, (n, marked, claim) in sorted(all_files.items()):
        if not marked:
            continue
        if n < EXEMPT_LINE:
            problems.append(f"R-e 过期自称 {f}：{n} 行（< {EXEMPT_LINE}）仍写着「规模例外"
                            f"{('（' + str(claim) + ' 行）') if claim else ''}」"
                            f"——例外已随切片解除，请删掉该自称")
        elif claim is not None and not (claim * TOL_LOW <= n <= claim * TOL_HIGH):
            problems.append(f"R-e 自述失真 {f}：标记称「{claim} 行」，实测 {n} 行")

    if shrunk:
        print(f"  已低于阈值：{len(shrunk)} 个 → 请 --write 收紧基线")

    if problems:
        print("\n✗ 守卫失败：")
        for p in problems:
            print("   " + p)
        return 1
    print(f"\n✓ 守卫通过：未新增超大文件、无回涨超阈、≥{EXEMPT_LINE} 行均已登记、自称无漂移。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
