#!/usr/bin/env python3
"""事件面 / 路由面的命名口径守卫（B93b，2026-10-08）。

背景：阶段 3 把 HTTP 的 JSON 面、B93a 把 wiki 图片标记、B93b 把事件面（SSE/Redis 流）
与事件名/响应类型值全部 camel 化，并顺带统一了 URL 路径变量与自有查询参数名。
本脚本防四类回流（历史教训：键名失配在 Java/Jackson 侧**静默**，不报错）：

  ① 事件载荷类（`event/payload/*.java`）不得再出现 snake 值的 `@JsonProperty`
     —— 也允许显式 camel（与字段同名）注解，因为字段注解承载 include/顺序语义；
  ② 事件名（`EventType`）与响应类型（`ResponseType`）的**值**不得含 `_` 或 `.`
     —— 家族前缀保留为 camel 前缀（`agentStep`），不再用点号层级；
  ③ URL 路径模板里的**路径变量**不得含 `_`（`{sessionId}`；路径变量名不进具体 URL，
     改它对外零影响；`{id}`/`{key}` 这类单词不受限）；
  ④ `@RequestParam` 的显式名不得含 `_`，除白名单（OIDC 标准参数，属外部协议）。

豁免：
  · 供应商/平台线格式（`llm/chat/**`、`datasource/connector/**`、`im/feishu/**`）本脚本不扫；
  · 查询参数白名单见 QUERY_WHITELIST（每条须注明判定依据）。
"""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
# 多模块（B116）：event/* 与 common/llm/ResponseType 都随底座搬入了 contracts，
# 路径改成按模块查找（新增模块无需改这里）。
import _source_roots as _sr

PAYLOAD_DIR = _sr.find_pkg_path("event/payload")
EVENT_TYPE = _sr.find_pkg_path("event/EventType.java")
RESPONSE_TYPE = _sr.find_pkg_path("common/llm/ResponseType.java")

# 查询参数白名单（键 → 判定依据）。OIDC/OAuth2 标准参数，由外部协议决定，不得改名。
QUERY_WHITELIST = {
    "redirect_uri": "OIDC/OAuth2 标准参数（外部协议）",
    "error_description": "OIDC 标准参数（外部协议）",
    "response_types_supported": "OIDC 发现文档字段（外部协议）",
}

problems: list[str] = []


def check_payload_annotations() -> None:
    ann = re.compile(r'@JsonProperty\("([^"]+)"\)')
    for p in sorted(PAYLOAD_DIR.glob("*.java")):
        for i, line in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
            m = ann.search(line)
            if m and "_" in m.group(1):
                problems.append(
                    f"{p.relative_to(ROOT)}:{i} 事件载荷注解仍是 snake：{m.group(1)}"
                    "（载荷键一律 camel；字段注解值应等于 Java 字段名）"
                )


def check_enum_values() -> None:
    for path in (EVENT_TYPE, RESPONSE_TYPE):
        t = path.read_text(encoding="utf-8")
        for m in re.finditer(r'=\s*"([^"]+)"', t):
            v = m.group(1)
            if "_" in v or "." in v:
                problems.append(
                    f"{path.relative_to(ROOT)} 事件名/响应类型值含分隔符：{v}"
                    "（统一 camel、去点号，如 agent.step → agentStep）"
                )
        for m in re.finditer(r'\w+\("([^"]+)"\)', t):
            v = m.group(1)
            if "_" in v or "." in v:
                problems.append(f"{path.relative_to(ROOT)} 响应类型值含分隔符：{v}")


MAPPING = re.compile(r"@(?:Get|Post|Put|Delete|Patch|Request)Mapping\b([\s\S]*?)\)\s*(?:\{|;|public)")
PATH_VAR = re.compile(r"\{([A-Za-z_][A-Za-z0-9_]*)\}")
PATHVAR_ANN = re.compile(r'@PathVariable\(\s*(?:value\s*=\s*)?"([^"]+)"')
REQPARAM_ANN = re.compile(r'@RequestParam\(\s*(?:value\s*=\s*)?"([^"]+)"')


def check_routes() -> None:
    for _r in _sr.backend_pkg_roots():
        for p in sorted(_r.rglob("*.java")):
            rel = str(p.relative_to(ROOT))
            t = p.read_text(encoding="utf-8")
            for m in MAPPING.finditer(t):
                body = m.group(1)
                if '"' not in body:
                    continue
                for v in PATH_VAR.findall(body):
                    if "_" in v:
                        line = t[: m.start()].count("\n") + 1
                        problems.append(
                            f"{rel}:{line} 路径变量含下划线：{{{v}}}"
                            "（改 camel；路径变量名不进具体 URL，对外零影响）"
                        )
            for i, line in enumerate(t.splitlines(), 1):
                for rx, why in ((PATHVAR_ANN, "路径变量"), (REQPARAM_ANN, "查询参数")):
                    for m in rx.finditer(line):
                        name = m.group(1)
                        if "_" not in name:
                            continue
                        if why == "查询参数" and name in QUERY_WHITELIST:
                            continue
                        problems.append(f"{rel}:{i} {why}名含下划线：{name}（{why}名统一 camel）")


def main() -> int:
    check_payload_annotations()
    check_enum_values()
    check_routes()
    if problems:
        print("✗ 事件面/路由面命名口径违例：\n")
        for x in problems:
            print("    " + x)
        print("\n  若确属外部协议（OIDC/供应商/平台），在脚本白名单里登记判定依据。")
        return 1
    print("✓ 事件载荷注解 / 事件名与响应类型值 / 路径变量 / 查询参数：全部 camel（或已登记豁免）。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
