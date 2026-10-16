#!/usr/bin/env bash
# 十二守卫的**单一入口**（B168；B183 加「统一响应外壳」；B263㉖ 加「路径/包对应」）——CI 的 guards job 与本地都跑这一份，口径不可能漂移。
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
ran=0
run() {
    local name="$1"
    shift
    ran=$((ran + 1))
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
run '换锚棘轮 + 冻结覆盖快照（--strict：非冻结面不许新增 snake 键；冻结面不许静默失去覆盖）' python3 scripts/check-json-key-case.py --strict
run '事件面/路由面命名口径（B93b）'    python3 scripts/check-event-face-case.py
run '大文件棘轮（B121：不得新增 >600 行主源码）' python3 scripts/check-file-size.py
run '目录卫生（B124：游离目录 / 死包目录）' python3 scripts/check-stray-dirs.py
run '死成员守卫（B131）'             python3 scripts/check-dead-members.py
run '统一响应外壳（B183：新控制器默认必须合规，清单只许减不许增）' python3 scripts/check-api-envelope.py
run '异常语义（B217：检索引擎层不得在 catch 里清空条件——边界失败即抛）' python3 scripts/check-exception-semantics.py
run '契约同步（B221：金片键签名变了必须签字，防"后端改契约前端不知情"）' python3 scripts/check-contract-sync.py
run '路径/包对应（B263㉖：源码目录必须与 package 声明一致，防"改包不搬目录"）' python3 scripts/check-package-dirs.py
run '域间只经契约（R10/A③：跨域只经 contract 或装配层，棘轮只许减）' python3 scripts/check-domain-contracts.py
run '契约层纯度（C1/C2/C3/C4：受控依赖根 · 持久化形状 · build 依赖 · 载荷落点，全部冻结只许减）' python3 scripts/check-contracts-purity.py
run '模块图（R6：主码矩阵 · 主码无同包 · 测试边登记 · 夹具不占别模块包）' python3 scripts/check-module-graph.py
run 'platform 业务形状（C8：只剩技术适配；红旗命名 View/Port/Request/Response/Payload/Facts 只许减不许增）' python3 scripts/check-platform-shapes.py

echo
# 汇总行**自计数**（B267 结项实测的漂移）：加第 12 个守卫时头注释/README/CI 都改了，唯独这里
# 的硬编码「十一守卫全绿」没人看（绿行的文案没人读）⇒ 数字漂了一个批次。此后 count 由 run()
# 自增，加守卫只需在下面加一行 run 调用，文案不用动。
if [ "$fail" -ne 0 ]; then
    echo '✗ 守卫未全绿（逐条结果见上）'
    exit 1
fi
echo "✓ ${ran} 守卫全绿"
