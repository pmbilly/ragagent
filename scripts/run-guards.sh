#!/usr/bin/env bash
# 八守卫的**单一入口**（B168）——CI 的 guards job 与本地都跑这一份，口径不可能漂移。
#
# 为什么要有它：B168 实测漏检一次真失败 ✗——本地手敲 `python3 scripts/check-json-key-case.py`
# 少了 CI 的 `--strict`（该守卫默认只**报告**并退出 0，加了 --strict 才是闸门）⇒ 本地全绿、
# CI 全红。**教训固化**：凡是"本地怎么验证"，都必须能在这一份里找到同一行 —— 加守卫/改开关
# 只改这里，CI 与本地自动同频。
#
# 用法：bash scripts/run-guards.sh      （退出码非 0 = 有守卫失败；逐条都跑，不早退）
set -uo pipefail

cd "$(dirname "$0")/.." || exit 1

fail=0
run() {
    local name="$1"
    shift
    printf '\n▶ %s\n' "$name"
    if "$@"; then
        return 0
    fi
    printf '✗ 守卫失败：%s\n' "$name"
    fail=1
}

run '环与分层违例只许减不许增'        python3 scripts/check-package-cycles.py
run 'Go 锚点注释只许减不许增（B9）'    python3 scripts/check-go-anchors.py
run '前端契约键棘轮（前端 snake / 后端 camel）' python3 scripts/check-fe-contract-keys.py
run '换锚棘轮（--strict 闸门：非冻结面不许新增 snake JSON 键）' python3 scripts/check-json-key-case.py --strict
run '事件面/路由面命名口径（B93b）'    python3 scripts/check-event-face-case.py
run '大文件棘轮（B121：不得新增 >600 行主源码）' python3 scripts/check-file-size.py
run '目录卫生（B124：游离目录 / 死包目录）' python3 scripts/check-stray-dirs.py
run '死成员守卫（B131）'             python3 scripts/check-dead-members.py

echo
if [ "$fail" -ne 0 ]; then
    echo '✗ 守卫未全绿（逐条结果见上）'
    exit 1
fi
echo '✓ 八守卫全绿'
